package dev.pampa.codex.data.stories

import dev.pampa.codex.crypto.ChatKeyring
import dev.pampa.codex.crypto.Hkdf
import dev.pampa.codex.crypto.MessageEnvelope
import dev.pampa.codex.crypto.StorySecret
import dev.pampa.codex.data.cloud.CloudAccount
import dev.pampa.codex.data.cloud.CloudStories
import dev.pampa.codex.data.cloud.CloudStory
import dev.pampa.codex.data.cloud.NoStories
import dev.pampa.codex.data.db.ContactDao
import dev.pampa.codex.data.identity.Keyring
import dev.pampa.codex.data.db.StoryDao
import dev.pampa.codex.data.db.StoryEntity
import dev.pampa.codex.data.identity.IdentityRepository
import dev.pampa.codex.model.SealChoice
import dev.pampa.codex.model.SealSpec
import dev.pampa.codex.model.Technique
import dev.pampa.codex.seal.SealResolver
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Una storia come la vede una schermata. */
data class Story(
  val id: String,
  val authorId: String,
  /** Come si chiama chi l'ha scritta. Vuoto per le proprie: il proprio nome non si annuncia. */
  val authorName: String = "",
  val mine: Boolean,
  val text: String,
  val spec: SealSpec,
  val createdAt: Long,
  val expiresAt: Long,
  val seen: Boolean,
) {
  /** Quanto le resta da vivere, in millisecondi. Mai negativo. */
  fun remaining(now: Long): Long = (expiresAt - now).coerceAtLeast(0)
}

/**
 * Le storie: sigilli che durano un giorno.
 *
 * Sono messaggi senza destinatario, e usano la stessa macchina: busta CDX3, tecnica e seme che
 * nascono dall'id. La differenza sta tutta in due cose -- **nessuno in particolare le riceve**, e
 * **scadono** -- e sono le due che rendono una storia una storia.
 *
 * La chiave e' derivata dal portachiavi dell'identita', come le note a se stessi. Quando ci saranno
 * i contatti, ogni storia viaggera' con la propria chiave avvolta per chi puo' vederla, e questo
 * strato non cambiera': cambiera' solo [keyFor].
 */
class StoryRepository(
  private val stories: StoryDao,
  private val identityRepository: IdentityRepository,
  /**
   * La rubrica cruda.
   *
   * Serve la **chiave pubblica** di ogni contatto: una storia si chiude una volta e si avvolge una
   * volta per ciascuno, e senza quelle chiavi non c'e' niente da avvolgere.
   */
  private val contacts: ContactDao,
  private val cloud: CloudStories = NoStories,
  private val account: CloudAccount? = null,
  private val keyring: Keyring? = null,
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
  private val clock: () -> Long = System::currentTimeMillis,
) {

  private val opened = ConcurrentHashMap<String, String>()

  /** Fino a quante persone raggiunge una storia. Oltre, sono tanti involucri e tanto documento. */
  private val maxRecipients = 200

  /**
   * Le storie ancora vive.
   *
   * L'istante di riferimento si prende all'iscrizione. Una storia non sparisce sotto gli occhi di
   * chi la sta guardando: sparisce alla prossima apertura della scheda, che e' anche il modo in cui
   * funzionano tutte le storie che la gente conosce.
   */
  fun observe(): Flow<List<Story>> {
    val since = clock()
    // I nomi arrivano dalla rubrica e non dalla storia: chi la scrive manda il suo Codex ID, e come
    // si chiama lo sa solo chi lo ha in rubrica. E' anche cio' che impedisce a qualcuno di
    // presentarsi con il nome di un altro.
    return combine(stories.observeLive(since), contacts.observeAll()) { list, rubrica ->
      val nomi = rubrica.associate { it.codexId to it.name }
      list.map { entity -> toStory(entity, nomi) }
    }
  }

  /** Pubblica il sigillo del giorno. */
  suspend fun post(text: String, choice: SealChoice = SealChoice()): Result<String> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("storia vuota"))
    val identity = identityRepository.identityOrNull()
      ?: return Result.failure(IllegalStateException("app bloccata"))

    return runCatching {
      val id = UUID.randomUUID().toString()
      val key = keyFor(id)
      val spec = SealResolver.resolve(MessageEnvelope.sealSeed(key, id), choice)
      val now = clock()
      stories.insert(
        StoryEntity(
          id = id,
          authorId = identity.codexId,
          mine = true,
          envelope = MessageEnvelope.seal(
            chatKey = key,
            chatId = STORY_SCOPE,
            messageId = id,
            senderId = identity.codexId,
            plaintext = trimmed.toByteArray(Charsets.UTF_8),
          ),
          technique = spec.technique.name,
          seed = spec.seed,
          paintingId = spec.paintingId,
          createdAt = now,
          expiresAt = now + LIFETIME_MILLIS,
        ),
      )
      opened[id] = trimmed
      // Il giro fuori non tiene ferma la schermata: la storia c'e' gia', e le chiavi partono da
      // sole. Se la rete non c'e', resta una storia che vedi solo tu -- ed e' meglio di un tasto
      // che non risponde.
      scope.launch { publish(id, key, identity.codexId) }
      id
    }
  }

  /**
   * Chiude la chiave di questa storia per ciascun contatto, e la manda.
   *
   * Un involucro per persona, e nessuno apre quello di un altro. Chi non ha ancora un account resta
   * fuori: non c'e' un posto dove consegnarglielo, e fingere che ci sia vorrebbe dire far credere
   * che l'abbia vista.
   */
  private suspend fun publish(storyId: String, key: ByteArray, authorCodexId: String) {
    val uid = account?.uidOrNull() ?: return
    val identity = identityRepository.identityOrNull() ?: return
    val destinatari = contacts.observeAll().first()
      .filter { it.uid.isNotBlank() }
      .take(maxRecipients)
    if (destinatari.isEmpty()) return

    val involucri = destinatari.associate { contatto ->
      contatto.uid to StorySecret.wrapFor(
        pairKey = identity.pairKey(contatto.x25519Public),
        storyId = storyId,
        authorCodexId = authorCodexId,
        recipientCodexId = contatto.codexId,
        storyKey = key,
      )
    }
    val entity = stories.byId(storyId) ?: return
    runCatching {
      cloud.publish(
        CloudStory(
          id = storyId,
          authorUid = uid,
          authorCodexId = authorCodexId,
          envelope = entity.envelope,
          wrappedKeys = involucri,
          createdAt = entity.createdAt,
        ),
      )
    }
  }

  /**
   * Una storia di qualcun altro, arrivata dal server.
   *
   * Restituisce `true` se e' entrata. **L'involucro va aperto prima di salvarla**: una storia che
   * non si apre non e' una storia da tenere -- non era per noi, o non e' quello che dice di essere.
   */
  suspend fun receive(story: CloudStory): Boolean {
    val identity = identityRepository.identityOrNull() ?: return false
    val uid = account?.uidOrNull() ?: return false
    if (story.authorCodexId == identity.codexId) return false
    if (stories.byId(story.id) != null) return true

    val contatto = contacts.byCodexId(story.authorCodexId) ?: return false
    val involucro = story.wrappedKeys[uid] ?: return false
    val key = StorySecret.unwrap(
      pairKey = identity.pairKey(contatto.x25519Public),
      storyId = story.id,
      authorCodexId = story.authorCodexId,
      myCodexId = identity.codexId,
      wrapped = involucro,
    ) ?: return false

    val testo = runCatching {
      MessageEnvelope.open(
        chatKey = key,
        chatId = STORY_SCOPE,
        messageId = story.id,
        senderId = story.authorCodexId,
        envelope = story.envelope,
      ).decodeToString()
    }.getOrNull() ?: return false

    // La tecnica non viaggia: nasce dalla chiave e dall'identificatore, quindi chi riceve la
    // ricalcola uguale. E' anche cio' che impedisce al server di far sembrare un quadro una roccia.
    val spec = SealResolver.resolve(MessageEnvelope.sealSeed(key, story.id), SealChoice())
    stories.insert(
      StoryEntity(
        id = story.id,
        authorId = story.authorCodexId,
        mine = false,
        envelope = story.envelope,
        technique = spec.technique.name,
        seed = spec.seed,
        paintingId = spec.paintingId,
        createdAt = story.createdAt,
        expiresAt = story.createdAt + LIFETIME_MILLIS,
        wrappedKey = wrapForDisk(story.id, key),
        authorUid = story.authorUid,
      ),
    )
    opened[story.id] = testo
    return true
  }

  /**
   * Vista.
   *
   * Si scrive in casa e si dice anche a chi l'ha scritta -- e' l'unica cosa che torna indietro da
   * una storia. Se la rete non c'e', in casa resta segnata comunque: quello che si e' visto lo si e'
   * visto.
   */
  suspend fun markSeen(id: String) {
    stories.markSeen(id, clock())
    val entity = stories.byId(id) ?: return
    val uid = account?.uidOrNull() ?: return
    if (entity.mine || entity.authorUid.isBlank()) return
    scope.launch { runCatching { cloud.markSeen(entity.authorUid, id, uid) } }
  }

  /** Chi ha visto una mia storia. Per le altre non ha senso chiederlo, e infatti non si chiede. */
  fun observeViewers(storyId: String): Flow<List<String>> {
    val uid = account?.uidOrNull() ?: return kotlinx.coroutines.flow.flowOf(emptyList())
    return cloud.observeViewers(uid, storyId)
  }

  /** Gli account dei contatti di cui vale la pena ascoltare le storie. */
  suspend fun authorsToWatch(): List<String> =
    contacts.observeAll().first().mapNotNull { it.uid.takeIf(String::isNotBlank) }.take(maxRecipients)

  suspend fun delete(id: String) {
    val entity = stories.byId(id)
    opened.remove(id)
    stories.delete(id)
    val uid = account?.uidOrNull()
    if (entity?.mine == true && uid != null) {
      scope.launch { runCatching { cloud.delete(uid, id) } }
    }
  }

  /** Toglie da disco quelle che nessuno vedra' piu'. Da chiamare quando l'app si apre. */
  suspend fun sweep() = stories.deleteExpired(clock())

  fun forgetOpened() = opened.clear()

  private fun toStory(entity: StoryEntity, names: Map<String, String>): Story = Story(
    id = entity.id,
    authorId = entity.authorId,
    authorName = if (entity.mine) "" else names[entity.authorId].orEmpty(),
    mine = entity.mine,
    text = opened.getOrPut(entity.id) { open(entity) },
    spec = SealSpec(
      technique = runCatching { Technique.valueOf(entity.technique) }.getOrDefault(Technique.RUNE),
      seed = entity.seed,
      paintingId = entity.paintingId,
    ),
    createdAt = entity.createdAt,
    expiresAt = entity.expiresAt,
    seen = entity.seenAt != null,
  )

  private fun open(entity: StoryEntity): String = runCatching {
    val key = keyOf(entity) ?: return UNREADABLE
    MessageEnvelope.open(
      chatKey = key,
      chatId = STORY_SCOPE,
      messageId = entity.id,
      senderId = entity.authorId,
      envelope = entity.envelope,
    ).decodeToString()
  }.getOrDefault(UNREADABLE)

  /**
   * La chiave di una storia.
   *
   * Le proprie si ricalcolano dal seme e non si conservano; quelle ricevute stanno nella riga,
   * avvolte. C'e' anche la strada vecchia -- una chiave sola per tutte le storie -- perche' le
   * storie scritte prima di questa milestone continuino ad aprirsi invece di diventare spazzatura.
   */
  private fun keyOf(entity: StoryEntity): ByteArray? {
    val identity = identityRepository.identityOrNull() ?: return null
    entity.wrappedKey?.let { avvolta ->
      val keyringKey = identity.keyringKey()
      return try {
        ChatKeyring.unwrap(keyringKey, entity.id, avvolta)
      } catch (error: ChatKeyring.Unwrappable) {
        null
      } finally {
        keyringKey.fill(0)
      }
    }
    val perStoria = StorySecret.keyFor(identity.keyringSeed, entity.id)
    if (opensWith(entity, perStoria)) return perStoria
    return Hkdf.derive(identity.keyringSeed, "codex-story-v1", 32)
  }

  private fun opensWith(entity: StoryEntity, key: ByteArray): Boolean = runCatching {
    MessageEnvelope.open(key, STORY_SCOPE, entity.id, entity.authorId, entity.envelope)
    true
  }.getOrDefault(false)

  private fun keyFor(storyId: String): ByteArray {
    val identity = identityRepository.requireIdentity()
    return StorySecret.keyFor(identity.keyringSeed, storyId)
  }

  /** La chiave di una storia ricevuta, chiusa come quelle delle conversazioni. */
  private fun wrapForDisk(storyId: String, key: ByteArray): ByteArray? {
    val identity = identityRepository.identityOrNull() ?: return null
    val keyringKey = identity.keyringKey()
    return try {
      ChatKeyring.wrap(keyringKey, storyId, key)
    } finally {
      keyringKey.fill(0)
    }
  }

  private companion object {
    /**
     * Il contesto autenticato delle storie.
     *
     * Fisso e diverso da qualunque id di chat: e' cio' che impedisce di prendere la busta di una
     * storia e farla passare per un messaggio, o viceversa. La busta e' legata al posto in cui e'
     * nata.
     */
    const val STORY_SCOPE = "codex-stories"

    val LIFETIME_MILLIS = TimeUnit.HOURS.toMillis(24)

    const val UNREADABLE = "⚠ Storia non leggibile"
  }
}
