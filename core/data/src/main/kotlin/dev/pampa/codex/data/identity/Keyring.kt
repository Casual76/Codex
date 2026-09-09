package dev.pampa.codex.data.identity

import dev.pampa.codex.crypto.ChatKeyring
import dev.pampa.codex.data.db.KeyringDao
import dev.pampa.codex.data.db.KeyringEntity

/**
 * Il portachiavi: le chiavi delle conversazioni, messe via in modo che le riapra solo il vault.
 *
 * E' l'unico punto dell'app in cui una chiave di chat viene scritta o riletta. Tutto quello che
 * entra passa da `ChatKeyring.wrap`, tutto quello che esce da `unwrap`, e nessuna delle due cose
 * funziona se l'app e' bloccata: a serratura chiusa questo oggetto non sa niente, ed e' esattamente
 * il comportamento che serve.
 */
class Keyring(
  private val dao: KeyringDao,
  private val identityRepository: CodexIdentitySource,
  private val clock: () -> Long = System::currentTimeMillis,
) {

  suspend fun store(chatId: String, key: ByteArray, epoch: Int = 0) {
    val keyringKey = identityRepository.requireIdentity().keyringKey()
    try {
      dao.upsert(
        KeyringEntity(
          chatId = chatId,
          wrapped = ChatKeyring.wrap(keyringKey, chatId, key),
          epoch = epoch,
          storedAt = clock(),
        ),
      )
    } finally {
      keyringKey.fill(0)
    }
  }

  /**
   * La chiave **corrente** di una conversazione, oppure `null`.
   *
   * `null` copre tre casi che per chi chiama sono lo stesso: la chiave non c'e', l'app e' bloccata,
   * l'involucro non si apre con questo portachiavi. In tutti e tre la conversazione non si legge, e
   * distinguere non cambierebbe cosa mostrare.
   */
  suspend fun key(chatId: String): ByteArray? = unwrap(chatId, dao.byChatId(chatId))

  /**
   * La chiave di una precisa epoca.
   *
   * Serve ad aprire un messaggio scritto **prima** che qualcuno uscisse dal gruppo: la busta dice
   * con quale epoca e' chiusa, e quella epoca qui c'e' ancora. Un gruppo in cui ogni uscita rende
   * illeggibile il passato non e' un gruppo di cui ci si fidi.
   */
  suspend fun key(chatId: String, epoch: Int): ByteArray? =
    unwrap(chatId, dao.byEpoch(chatId, epoch))

  /** A che epoca siamo, se questa conversazione ne ha una. */
  suspend fun epoch(chatId: String): Int? = dao.byChatId(chatId)?.epoch

  private suspend fun unwrap(chatId: String, entry: dev.pampa.codex.data.db.KeyringEntity?): ByteArray? {
    if (entry == null) return null
    val identity = identityRepository.identityOrNull() ?: return null
    val keyringKey = identity.keyringKey()
    return try {
      ChatKeyring.unwrap(keyringKey, chatId, entry.wrapped)
    } catch (error: ChatKeyring.Unwrappable) {
      null
    } finally {
      keyringKey.fill(0)
    }
  }

  /**
   * Tutte le chiavi di una conversazione, per epoca.
   *
   * Le chiavi vecchie non si buttano: sono l'unico modo di rileggere quello che si e' detto prima
   * che qualcuno uscisse dal gruppo.
   */
  suspend fun keys(chatId: String): Map<Int, ByteArray> {
    val entries = dao.allForChat(chatId)
    if (entries.isEmpty()) return emptyMap()
    val identity = identityRepository.identityOrNull() ?: return emptyMap()
    val keyringKey = identity.keyringKey()
    return try {
      entries.mapNotNull { entry ->
        runCatching { entry.epoch to ChatKeyring.unwrap(keyringKey, chatId, entry.wrapped) }.getOrNull()
      }.toMap()
    } finally {
      keyringKey.fill(0)
    }
  }

  /** Se il rito e' gia' stato fatto per questa conversazione. Non tocca nessun segreto. */
  suspend fun has(chatId: String): Boolean = dao.exists(chatId)

  suspend fun forget(chatId: String) = dao.delete(chatId)
}
