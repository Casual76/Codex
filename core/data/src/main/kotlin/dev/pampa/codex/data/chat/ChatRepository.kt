package dev.pampa.codex.data.chat

import dev.pampa.codex.crypto.CodexIdentity
import dev.pampa.codex.crypto.Hkdf
import dev.pampa.codex.crypto.MediaCipher
import dev.pampa.codex.crypto.MessageBody
import dev.pampa.codex.crypto.MessageBodyCodec
import dev.pampa.codex.crypto.MessageEnvelope
import dev.pampa.codex.crypto.PaintingParcel
import dev.pampa.codex.data.db.ChatDao
import dev.pampa.codex.data.db.ChatEntity
import dev.pampa.codex.data.db.ChatKind
import dev.pampa.codex.data.db.MessageDao
import dev.pampa.codex.data.db.MessageEntity
import dev.pampa.codex.data.db.MessageStatus
import dev.pampa.codex.data.db.OutboxDao
import dev.pampa.codex.data.db.OutboxEntity
import dev.pampa.codex.data.cloud.CloudAccount
import dev.pampa.codex.data.cloud.CloudTransport
import dev.pampa.codex.data.cloud.NoTransport
import dev.pampa.codex.data.identity.CodexIdentitySource
import dev.pampa.codex.data.identity.Keyring
import dev.pampa.codex.data.media.MediaStore
import dev.pampa.codex.data.media.MediaVault
import dev.pampa.codex.data.media.NoMediaStore
import java.io.File
import java.io.InputStream
import dev.pampa.codex.model.SealChoice
import dev.pampa.codex.model.SealSpec
import dev.pampa.codex.model.Technique
import dev.pampa.codex.seal.SealResolver
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Un messaggio come lo vede una schermata: gia' aperto, con il suo sigillo risolto.
 *
 * Il testo c'e' perche' l'app e' sbloccata; se non lo fosse, il repository non avrebbe la chiave e
 * non ci sarebbe niente da mostrare.
 */
data class ChatMessage(
  val id: String,
  val outgoing: Boolean,
  val text: String,
  val spec: SealSpec,
  val createdAt: Long,
  val status: String,
  val revealedOnce: Boolean,
  val viewOnce: Boolean,
  val burned: Boolean,
  val content: Content = Content.OPEN,
  /** Se questo messaggio **e'** una foto o una voce. Il testo, se c'e', e' la didascalia. */
  val media: MessageBody.Media? = null,
) {

  /**
   * In che stato e' il contenuto di questo messaggio.
   *
   * Qui non c'e' il **testo** dei due casi storti, solo il caso: le parole da mostrare sono
   * dell'app, che sa in che lingua sta parlando. Un modulo di dati che scrive frasi in italiano e'
   * un modulo che va riaperto il giorno in cui l'app parla inglese.
   */
  enum class Content {
    /** Si e' aperto: [text] contiene quello che c'era scritto. */
    OPEN,

    /** La chiave di questa conversazione non c'e': il rito non e' stato fatto, o non qui. */
    SEALED,

    /** La chiave c'e' ma la busta non si apre: dati rovinati, o non erano per noi. */
    UNREADABLE,
  }

  /** Un messaggio che non si puo' aprire: resta il posto che occupava, e si dice perche'. */
  val isPlaceholder: Boolean get() = burned || content != Content.OPEN

  /** Solo il quadro esce da Codex: le rune e la roccia vivono dentro una chat. */
  val exportable: Boolean
    get() = !isPlaceholder && spec.technique == Technique.PAINTING
}

/** Quanti messaggi si tengono a schermo prima di doverne chiedere altri. */
const val DEFAULT_PAGE = 80

/** Ogni quanto si puo' dire "sto scrivendo", e per quanto vale una volta detto. */
private const val TYPING_THROTTLE = 3_000L
private const val TYPING_WINDOW = 6_000L

/**
 * Le conversazioni e i loro messaggi.
 *
 * E' il punto in cui la crittografia incontra l'interfaccia: sopra questo strato non esiste una
 * busta, sotto non esiste un testo. Tutto quello che finisce su disco passa da
 * [MessageEnvelope.seal], tutto quello che risale passa da [MessageEnvelope.open].
 *
 * **Le note a se stessi sono una chat vera.** Non un caso particolare con meno cifratura: hanno una
 * chiave derivata dall'identita', le stesse buste e gli stessi sigilli. E' anche il modo in cui
 * tutto il resto si puo' provare prima che esista un secondo telefono.
 */
class ChatRepository(
  private val chats: ChatDao,
  private val messages: MessageDao,
  private val identityRepository: CodexIdentitySource,
  private val keyring: Keyring,
  private val outbox: OutboxDao,
  private val vault: MediaVault? = null,
  private val mediaStore: MediaStore = NoMediaStore,
  private val transport: CloudTransport = NoTransport,
  private val account: CloudAccount? = null,
  private val clock: () -> Long = System::currentTimeMillis,
) {

  /**
   * I corpi gia' aperti, per id.
   *
   * Aprire una busta costa poco, ma la lista dei messaggi si ridisegna a ogni cambiamento e senza
   * questa mappa si riaprirebbe l'intera conversazione a ogni tocco. Vive in memoria e sparisce con
   * il processo, come l'identita'.
   *
   * Ci sta il **corpo** e non il testo: il corpo di un allegato e' binario, e farlo passare da una
   * stringa lo rovinerebbe in silenzio -- i byte che non sono UTF-8 valido tornano indietro come
   * punti interrogativi, e l'allegato diventa illeggibile senza che niente lo dica.
   */
  private val opened = ConcurrentHashMap<String, MessageBody>()

  /**
   * Le chiavi di chat gia' scartate dall'involucro.
   *
   * Aprire il portachiavi e' una lettura sul database piu' un AES-GCM: farlo per **ogni messaggio**
   * a ogni ridisegno della conversazione sarebbe assurdo. Vive in memoria come i testi aperti, e
   * come loro se ne va quando la serratura si chiude.
   */
  private val chatKeys = ConcurrentHashMap<String, ByteArray>()

  /** Quando si e' detto l'ultima volta "sto scrivendo", per non ripeterlo a ogni tasto. */
  private val typingSentAt = ConcurrentHashMap<String, Long>()

  fun observeChats(): Flow<List<ChatEntity>> = chats.observeAll()

  fun observeChat(chatId: String): Flow<ChatEntity?> = chats.observe(chatId)

  /**
   * Una conversazione, una volta sola.
   *
   * La usa il servizio delle notifiche, che a telefono bloccato puo' leggere **solo questo**: il
   * nome e le impostazioni della chat, che non sono segreti. Le buste restano chiuse.
   */
  suspend fun chatById(chatId: String): ChatEntity? = chats.byId(chatId)

  /**
   * I messaggi di una chat, gia' aperti.
   *
   * La chat viaggia insieme ai messaggi perche' **da lei dipende la chiave**: senza, aprire una
   * busta significherebbe indovinare il tipo di conversazione, e indovinare da' la risposta giusta
   * finche' esiste un solo tipo.
   */
  fun observeMessages(chatId: String, limit: Int = DEFAULT_PAGE): Flow<List<ChatMessage>> =
    combine(chats.observe(chatId), messages.observeForChat(chatId, limit)) { chat, list ->
      val keys = chat?.let { keysFor(it) }.orEmpty()
      // Il DAO li da' dal piu' recente perche' e' da li' che si taglia; qui tornano nel verso in
      // cui si leggono.
      list.asReversed().map { entity -> toChatMessage(entity, keys) }
    }

  /** La chat con se stessi: la crea la prima volta e poi la ritrova. */
  suspend fun ensureSelfChat(title: String): String {
    chats.firstOfKind(ChatKind.SELF)?.let { return it.id }
    val identity = identityRepository.requireIdentity()
    val id = "self-" + identity.codexId.removePrefix("CDX-").replace("-", "").lowercase()
    val now = clock()
    chats.upsert(
      ChatEntity(
        id = id,
        kind = ChatKind.SELF,
        title = title,
        peerId = identity.codexId,
        emblemSeed = Hkdf.deriveSeed(selfChatKey(identity), "codex-emblem-v1"),
        createdAt = now,
        lastActivityAt = now,
      ),
    )
    return id
  }

  /**
   * Apre (o riapre) la conversazione con un contatto, con la chiave nata dal rito.
   *
   * Non e' qui che si fa la crittografia: qui arriva una chiave gia' concordata, e questo metodo
   * decide solo **cosa succede ai messaggi che ci sono gia'**. Se la conversazione esiste, i
   * messaggi restano dove sono e cambiano soltanto l'emblema e la chiave: rifare il rito con la
   * parola giusta dopo averla sbagliata deve riaprire quello che era rimasto chiuso, non
   * cancellarlo.
   *
   * Le impostazioni della chat -- fissata, silenziata, scadenza, non letti -- sopravvivono per lo
   * stesso motivo: nessuno si aspetta di perderle perche' ha ripetuto una parola.
   */
  suspend fun openDirect(
    chatId: String,
    title: String,
    peerId: String,
    emblemSeed: Long,
    key: ByteArray,
  ): String {
    val now = clock()
    val existing = chats.byId(chatId)
    if (existing == null) {
      chats.upsert(
        ChatEntity(
          id = chatId,
          kind = ChatKind.DIRECT,
          title = title,
          peerId = peerId,
          emblemSeed = emblemSeed,
          createdAt = now,
          lastActivityAt = now,
        ),
      )
    } else {
      chats.reseal(chatId, title, emblemSeed)
    }
    keyring.store(chatId, key)
    forgetCached(chatId)
    // I testi gia' aperti con la chiave di prima non valgono piu': vanno riaperti con questa.
    opened.clear()
    return chatId
  }

  /**
   * Fa esistere un gruppo su questo telefono, o ne aggiorna nome ed epoca.
   *
   * A differenza di una chat a due, qui la chiave **non si concorda**: e' arrivata per posta e la
   * mette via chi l'ha ricevuta. Questo metodo si occupa solo della riga nella lista -- e di non
   * cancellare quello che c'era: un gruppo che cambia epoca non e' un gruppo nuovo, e i messaggi di
   * ieri restano dove sono, leggibili con la chiave di ieri.
   */
  suspend fun openGroup(
    chatId: String,
    title: String,
    emblemSeed: Long,
    keyEpoch: Int,
    createdAt: Long,
  ): String {
    val now = clock()
    val existing = chats.byId(chatId)
    if (existing == null) {
      chats.upsert(
        ChatEntity(
          id = chatId,
          kind = ChatKind.GROUP,
          title = title,
          emblemSeed = emblemSeed,
          keyEpoch = keyEpoch,
          createdAt = if (createdAt > 0) createdAt else now,
          lastActivityAt = if (createdAt > 0) createdAt else now,
        ),
      )
    } else {
      chats.reseal(chatId, title, emblemSeed)
      chats.setKeyEpoch(chatId, keyEpoch)
    }
    forgetCached(chatId)
    return chatId
  }

  /**
   * Se questa conversazione ha una chiave.
   *
   * Non la tira fuori: dice solo se c'e'. Per un gruppo appena raggiunto da un invito la risposta e'
   * "non ancora", ed e' un'informazione da mostrare -- "in attesa della chiave" e' una frase, "non
   * si apre niente" e' un guasto.
   */
  suspend fun hasKey(chatId: String): Boolean = keyring.has(chatId)

  /** L'epoca con cui si scrive da adesso in poi. */
  suspend fun setKeyEpoch(chatId: String, epoch: Int) {
    chats.setKeyEpoch(chatId, epoch)
    forgetCached(chatId)
  }

  /**
   * Butta via le chiavi tenute in memoria per una conversazione.
   *
   * Si chiama quando la chiave **cambia**: la cache e' per epoca, e quella senza epoca ("ora")
   * punterebbe ancora alla chiave di prima. Un gruppo che continua a scrivere con la chiave vecchia
   * dopo una rotazione manderebbe messaggi che chi e' uscito legge ancora.
   */
  private fun forgetCached(chatId: String) {
    chatKeys.keys.filter { it == chatId || it.startsWith("$chatId|") }
      .forEach { chatKeys.remove(it)?.fill(0) }
  }

  /**
   * Manda un messaggio.
   *
   * L'ordine conta: prima si sceglie l'id, poi da quello nascono la chiave del messaggio, il suo
   * nonce e il seme del sigillo. E' cosi' che il destinatario ottiene lo stesso sigillo senza che
   * gli venga detto quale.
   */
  suspend fun send(
    chatId: String,
    text: String,
    choice: SealChoice = SealChoice(),
    viewOnce: Boolean = false,
  ): Result<String> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("messaggio vuoto"))
    val chat = chats.byId(chatId) ?: return Result.failure(IllegalStateException("chat assente"))
    val identity = identityRepository.identityOrNull()
      ?: return Result.failure(IllegalStateException("app bloccata"))

    return runCatching {
      val messageId = UUID.randomUUID().toString()
      val chatKey = keyFor(chatId, chat.kind) ?: error("conversazione senza chiave")
      val seed = MessageEnvelope.sealSeed(chatKey, messageId)
      val spec = SealResolver.resolve(seed, choice)
      val envelope = MessageEnvelope.seal(
        chatKey = chatKey,
        chatId = chatId,
        messageId = messageId,
        senderId = identity.codexId,
        plaintext = trimmed.toByteArray(Charsets.UTF_8),
        keyEpoch = chat.keyEpoch,
      )
      val now = clock()
      messages.insert(
        MessageEntity(
          id = messageId,
          chatId = chatId,
          senderId = identity.codexId,
          outgoing = true,
          envelope = envelope,
          technique = spec.technique.name,
          seed = spec.seed,
          paintingId = spec.paintingId,
          createdAt = now,
          // Una chat con se stessi non ha nessuno a cui consegnare: e' gia' arrivata.
          status = if (chat.kind == ChatKind.SELF) MessageStatus.READ else MessageStatus.PENDING,
          // I messaggi che ho scritto io li ho gia' letti: si richiudono, ma il rituale completo
          // non mi serve per riaprirli.
          revealedOnce = true,
          viewOnce = viewOnce,
        ),
      )
      opened[messageId] = MessageBody.Text(trimmed)
      chats.touch(chatId, now, spec.technique.name, fromMe = true)
      // Le note a se stessi non vanno consegnate a nessuno: sono gia' arrivate. Tutto il resto
      // entra in coda, e ci resta finche' il server non conferma -- anche se l'app si chiude.
      if (chat.kind != ChatKind.SELF) {
        outbox.upsert(OutboxEntity(messageId = messageId, chatId = chatId, queuedAt = now))
      }
      messageId
    }
  }

  // --- La consegna ---------------------------------------------------------------------------

  /**
   * Quello che serve per consegnare un messaggio: la busta, e chi l'ha scritto.
   *
   * Non c'e' il testo e non c'e' la chiave. Chi consegna non deve poter aprire niente, e questo
   * tipo esiste proprio per rendere quella frase vera invece che promessa.
   */
  data class Outgoing(
    val chatId: String,
    val messageId: String,
    val peerId: String,
    /**
     * Che tipo di conversazione e'.
     *
     * Chi consegna deve saperlo: a una persona sola si consegna cercando **il suo** account, a un
     * gruppo si consegna al gruppo, e chi lo compone lo sa il server. Sono due strade diverse, e
     * senza questo campo la seconda finirebbe nella prima -- un gruppo non ha un `peerId`, quindi
     * il messaggio resterebbe in coda per sempre senza che niente lo dica.
     */
    val kind: String,
    val senderCodexId: String,
    val envelope: ByteArray,
    val viewOnce: Boolean,
    val createdAt: Long,
    /**
     * Il file cifrato da caricare **prima** del messaggio.
     *
     * L'ordine non e' un dettaglio: un messaggio che nomina un allegato non ancora caricato arriva
     * all'altra persona come una foto che non si scarichera' mai.
     */
    val mediaFile: File? = null,
  )

  /** Quanti messaggi aspettano di partire. Serve al motore per svegliarsi appena ce n'e' uno. */
  fun observeOutboxSize(): Flow<Int> = outbox.observeAll().map { it.size }

  /** Cosa c'e' da consegnare adesso, in ordine di scrittura. */
  suspend fun pendingDeliveries(limit: Int = 20): List<Outgoing> =
    outbox.pending(limit).mapNotNull { entry ->
      val message = messages.byId(entry.messageId) ?: run {
        // Il messaggio non c'e' piu' (cancellato mentre era in coda): la riga in coda nemmeno.
        outbox.delete(entry.messageId)
        return@mapNotNull null
      }
      val chat = chats.byId(entry.chatId) ?: return@mapNotNull null
      Outgoing(
        chatId = entry.chatId,
        messageId = entry.messageId,
        peerId = chat.peerId.orEmpty(),
        kind = chat.kind,
        senderCodexId = message.senderId,
        envelope = message.envelope,
        viewOnce = message.viewOnce,
        createdAt = message.createdAt,
        mediaFile = vault?.file(entry.messageId)?.takeIf { it.isFile },
      )
    }

  suspend fun deliverySucceeded(messageId: String) {
    outbox.delete(messageId)
    // **Solo in avanti.** Un messaggio gia' consegnato di persona -- da vicino, senza rete -- e' a
    // "consegnato": quando piu' tardi passa anche dal server, riportarlo a "spedito" lo farebbe
    // tornare indietro sotto gli occhi di chi lo ha scritto.
    messages.advanceToSent(messageId)
  }

  /**
   * Consegnato **di persona**, da vicino.
   *
   * La riga in coda **resta**: il cloud lo vuole comunque. L'altra persona puo' avere un secondo
   * dispositivo, e una conversazione dev'essere intera anche quando la si riapre altrove. Lo stesso
   * identificatore fa da protezione contro il doppione: chi ce l'ha gia' non lo prende di nuovo.
   */
  suspend fun nearbyDelivered(messageId: String) {
    messages.setStatus(messageId, MessageStatus.DELIVERED)
  }

  /**
   * Una consegna non riuscita.
   *
   * `permanent` distingue "il server non risponde" da "il server ha detto di no". Nel primo caso il
   * messaggio resta in coda e ci riprova; nel secondo esce dalla coda e prende la spunta rossa,
   * perche' insistere consumerebbe batteria per un rifiuto che non cambiera'.
   */
  suspend fun deliveryFailed(messageId: String, permanent: Boolean) {
    if (permanent) {
      outbox.delete(messageId)
      messages.setStatus(messageId, MessageStatus.FAILED)
    } else {
      outbox.failed(messageId, clock())
    }
  }

  /**
   * Un messaggio arrivato dall'altra parte.
   *
   * La busta viene messa via **cosi' com'e'**: non si apre qui e non si controlla che si apra. Un
   * messaggio che non si apre resta nella conversazione e lo dice (vedi [ChatMessage.Content]) --
   * buttarlo via vorrebbe dire che chi ha sbagliato la parola d'ordine non se ne accorge mai, e che
   * rifare il rito non recupera niente.
   *
   * Restituisce `false` se c'era gia': gli ascolti di Firestore riconsegnano tutto a ogni riavvio, e
   * senza questo la conversazione si riempirebbe di doppioni.
   */
  suspend fun receive(
    chatId: String,
    messageId: String,
    senderCodexId: String,
    envelope: ByteArray,
    viewOnce: Boolean,
    createdAt: Long,
  ): Boolean {
    if (messages.byId(messageId) != null) return false
    val chat = chats.byId(chatId) ?: return false
    val identity = identityRepository.identityOrNull() ?: return false

    // La tecnica e il seme non viaggiano: nascono dalla chiave e dall'id, quindi chi riceve li
    // ricalcola. E' anche cio' che impedisce a chi trasporta di far sembrare un quadro una roccia.
    // L'epoca serve solo ai gruppi, e va letta **senza fidarsi**: questa busta arriva dal server, e
    // una busta rovinata deve diventare un messaggio che non si apre, non un'eccezione che ferma la
    // ricezione di tutti gli altri.
    val epoch = if (chat.kind == ChatKind.GROUP) epochOf(envelope) else null
    val key = keyFor(chatId, chat.kind, epoch)
    // Chi riceve non sa ancora se dentro c'e' un testo o una foto: per saperlo deve aprire la
    // busta, e qui la busta si apre davvero (la chiave c'e' o il messaggio resta chiuso).
    val body = key?.let { chatKey ->
      runCatching {
        MessageBodyCodec.decode(
          MessageEnvelope.open(chatKey, chatId, messageId, senderCodexId, envelope),
        )
      }.getOrNull()
    }
    val spec = key?.let {
      SealResolver.resolve(
        MessageEnvelope.sealSeed(it, messageId),
        SealChoice(),
        allowRunes = body !is MessageBody.Media,
      )
    }

    messages.insert(
      MessageEntity(
        id = messageId,
        chatId = chatId,
        senderId = senderCodexId,
        outgoing = senderCodexId == identity.codexId,
        envelope = envelope,
        technique = (spec?.technique ?: Technique.RUNE).name,
        seed = spec?.seed ?: 0L,
        paintingId = spec?.paintingId,
        createdAt = createdAt,
        status = MessageStatus.DELIVERED,
        viewOnce = viewOnce,
      ),
    )
    chats.touch(chatId, createdAt, (spec?.technique ?: Technique.RUNE).name, fromMe = false)
    // Ricevuto: si registra subito in casa, e il motore lo portera' fuori quando c'e' rete.
    chats.setDelivered(chatId, createdAt)
    return true
  }

  /**
   * Fin dove l'altra persona e' arrivata, applicato alle spunte.
   *
   * Solo in avanti: uno stato non torna mai indietro. Una ricevuta che arriva in ritardo e
   * riportasse "letto" a "consegnato" farebbe lampeggiare la spunta senza motivo, e chi guarda
   * finirebbe per non fidarsi di nessuna.
   */
  suspend fun applyPeerReceipt(chatId: String, deliveredAt: Long, readAt: Long) {
    if (deliveredAt > 0) messages.markDeliveredUpTo(chatId, deliveredAt)
    if (readAt > 0) messages.markReadUpTo(chatId, readAt)
  }

  // --- "Sta scrivendo" -----------------------------------------------------------------------

  /**
   * Dice che si sta scrivendo, per i prossimi secondi.
   *
   * Chi chiama lo fa a ogni tasto: qui si lascia passare **al massimo una volta ogni tre secondi**.
   * Senza, una frase di quaranta caratteri sarebbe quaranta scritture sul server per dire quaranta
   * volte la stessa cosa.
   */
  suspend fun reportTyping(chatId: String) {
    val uid = account?.uidOrNull() ?: return
    val now = clock()
    val last = typingSentAt[chatId] ?: 0L
    if (now - last < TYPING_THROTTLE) return
    typingSentAt[chatId] = now
    runCatching { transport.reportTyping(chatId, uid, now + TYPING_WINDOW) }
  }

  /**
   * Se l'altra persona sta scrivendo **adesso**.
   *
   * La scadenza la giudica chi guarda, con il proprio orologio: cosi' un'app chiusa a meta' frase
   * non lascia acceso un "sta scrivendo" che non finisce mai.
   */
  fun observePeerTyping(chatId: String): Flow<Boolean> {
    val uid = account?.uidOrNull()
    return transport.observeTyping(chatId).map { typing ->
      typing.filterKeys { it != uid }.values.any { until -> until > clock() }
    }
  }

  /** L'ultima volta che l'altra persona e' stata vista, se lo lascia sapere. */
  fun observePeerPresence(peerUid: String): Flow<Long> = transport.observePresence(peerUid)

  /** Le conversazioni con un'altra persona: sono quelle che hanno qualcosa da consegnare. */
  suspend fun directChats(): List<ChatEntity> =
    chats.observeAll().first().filter { it.kind == ChatKind.DIRECT }

  /**
   * Manda una foto o una nota vocale.
   *
   * L'ordine e' quello che rende la cosa onesta: **prima il file finisce cifrato su disco**, poi
   * nasce il messaggio che lo nomina. Se qualcosa va storto a meta', quello che resta e' un file
   * illeggibile in una cartella, non un messaggio che promette un allegato che non esiste.
   *
   * L'identificatore del media e' quello del messaggio: uno per uno, niente da tenere allineato. La
   * chiave pero' e' sua, derivata da quella della conversazione **e** dall'identificatore.
   */
  suspend fun sendMedia(
    chatId: String,
    kind: MessageBody.Media.Kind,
    mime: String,
    source: InputStream,
    plainLength: Long,
    width: Int = 0,
    height: Int = 0,
    durationMs: Long = 0,
    caption: String = "",
    waveform: ByteArray = ByteArray(0),
    choice: SealChoice = SealChoice(),
    viewOnce: Boolean = false,
  ): Result<String> {
    val store = vault ?: return Result.failure(IllegalStateException("nessun magazzino dei media"))
    val chat = chats.byId(chatId) ?: return Result.failure(IllegalStateException("chat assente"))
    val identity = identityRepository.identityOrNull()
      ?: return Result.failure(IllegalStateException("app bloccata"))
    val chatKey = keyFor(chatId, chat.kind)
      ?: return Result.failure(IllegalStateException("conversazione senza chiave"))

    val messageId = UUID.randomUUID().toString()
    val mediaKey = MediaCipher.key(chatKey, messageId)
    store.store(messageId, mediaKey, source, plainLength).getOrElse {
      return Result.failure(it)
    }

    return runCatching {
      val body = MessageBody.Media(
        kind = kind,
        mediaId = messageId,
        mime = mime,
        size = plainLength,
        width = width,
        height = height,
        durationMs = durationMs,
        caption = caption,
        waveform = waveform,
      )
      val seed = MessageEnvelope.sealSeed(chatKey, messageId)
      val spec = SealResolver.resolve(seed, choice, allowRunes = false)
      val envelope = MessageEnvelope.seal(
        chatKey = chatKey,
        chatId = chatId,
        messageId = messageId,
        senderId = identity.codexId,
        plaintext = MessageBodyCodec.encode(body),
        keyEpoch = chat.keyEpoch,
      )
      val now = clock()
      messages.insert(
        MessageEntity(
          id = messageId,
          chatId = chatId,
          senderId = identity.codexId,
          outgoing = true,
          envelope = envelope,
          technique = spec.technique.name,
          seed = spec.seed,
          paintingId = spec.paintingId,
          createdAt = now,
          status = if (chat.kind == ChatKind.SELF) MessageStatus.READ else MessageStatus.PENDING,
          revealedOnce = true,
          viewOnce = viewOnce,
        ),
      )
      chats.touch(chatId, now, spec.technique.name, fromMe = true)
      if (chat.kind != ChatKind.SELF) {
        outbox.upsert(OutboxEntity(messageId = messageId, chatId = chatId, queuedAt = now))
      }
      messageId
    }
  }

  /**
   * Il contenuto in chiaro di un allegato, **solo in memoria**.
   *
   * `null` se il file non e' ancora su questo telefono: non e' un errore, e' "sto ancora
   * scaricando". Chi guarda lo schermo vede due cose diverse.
   */
  suspend fun openMedia(messageId: String): ByteArray? {
    val store = vault ?: return null
    val entity = messages.byId(messageId) ?: return null
    val chat = chats.byId(entity.chatId) ?: return null
    // La chiave del file discende da quella della **conversazione al momento dell'invio**: per un
    // gruppo puo' essere di un'epoca fa, e chiedere quella di adesso darebbe byte che non aprono
    // niente.
    val chatKey = keyFor(entity.chatId, chat.kind, epochOf(entity)) ?: return null
    return runCatching { store.open(messageId, MediaCipher.key(chatKey, messageId)) }.getOrNull()
  }

  /** Se il file di un allegato e' gia' su questo telefono. */
  fun hasMedia(messageId: String): Boolean = vault?.has(messageId) == true

  /**
   * Porta su questo telefono il file di un allegato, se non c'e' gia'.
   *
   * Si scarica **quando serve**, non appena arriva il messaggio: una conversazione con duecento foto
   * non deve tirarsele giu' tutte per il fatto di essere stata aperta una volta.
   */
  suspend fun ensureMedia(messageId: String): Boolean {
    val store = vault ?: return false
    if (store.has(messageId)) return true
    val entity = messages.byId(messageId) ?: return false
    return mediaStore.download(entity.chatId, messageId, store.file(messageId)).isSuccess
  }

  // --- Il quadro che esce e rientra ----------------------------------------------------------

  /**
   * Quello che serve per trasformare un messaggio in un PNG condivisibile.
   *
   * Non produce l'immagine: qui non c'e' Android, e un modulo che sa cos'e' un `Bitmap` non puo'
   * essere provato senza un emulatore. Produce **cosa** va nascosto, e chi ha la tela lo nasconde.
   */
  suspend fun exportable(messageId: String): Result<ExportablePainting> = runCatching {
    val entity = messages.byId(messageId) ?: error("messaggio assente")
    require(entity.technique == Technique.PAINTING.name) { "solo il quadro si esporta" }
    require(!entity.burned) { "questo messaggio non esiste piu'" }
    ExportablePainting(
      paintingId = entity.paintingId,
      seed = entity.seed,
      parcel = PaintingParcel.pack(
        PaintingParcel.Content(
          chatId = entity.chatId,
          messageId = entity.id,
          senderId = entity.senderId,
          envelope = entity.envelope,
        ),
      ),
    )
  }

  /**
   * Un quadro arrivato da fuori.
   *
   * La busta viene **aperta prima di essere salvata**: un messaggio che non si apre non ha motivo
   * di entrare nella conversazione, e uno che si apre e' autentico per costruzione — chat,
   * messaggio e mittente sono dati autenticati, quindi un PNG rimaneggiato fallisce qui invece di
   * comparire come un messaggio credibile che non e' mai stato scritto.
   */
  suspend fun importPainting(payload: ByteArray?): PaintingImport {
    if (payload == null || !PaintingParcel.looksLikeParcel(payload)) return PaintingImport.NothingInside
    val content = try {
      PaintingParcel.unpack(payload)
    } catch (error: PaintingParcel.Malformed) {
      return PaintingImport.NothingInside
    }

    messages.byId(content.messageId)?.let { existing ->
      return PaintingImport.AlreadyHere(existing.chatId, existing.id)
    }

    val chat = chats.byId(content.chatId) ?: return PaintingImport.UnknownChat
    val identity = identityRepository.identityOrNull() ?: return PaintingImport.Unreadable
    val chatKey = keyFor(chat.id, chat.kind) ?: return PaintingImport.Unreadable

    val text = try {
      MessageEnvelope.open(
        chatKey = chatKey,
        chatId = content.chatId,
        messageId = content.messageId,
        senderId = content.senderId,
        envelope = content.envelope,
      ).decodeToString()
    } catch (error: Exception) {
      return PaintingImport.Unreadable
    }

    val now = clock()
    val spec = SealResolver.resolve(
      MessageEnvelope.sealSeed(chatKey, content.messageId),
      SealChoice(technique = Technique.PAINTING),
    )
    messages.insert(
      MessageEntity(
        id = content.messageId,
        chatId = content.chatId,
        senderId = content.senderId,
        // Se l'ho scritto io e me lo sono rimandato, resta mio: e' il mittente a dirlo, non da
        // dove arriva il file.
        outgoing = content.senderId == identity.codexId,
        envelope = content.envelope,
        technique = Technique.PAINTING.name,
        seed = spec.seed,
        paintingId = spec.paintingId,
        createdAt = now,
        status = MessageStatus.DELIVERED,
      ),
    )
    opened[content.messageId] = MessageBody.Text(text)
    chats.touch(content.chatId, now, Technique.PAINTING.name, fromMe = false)
    return PaintingImport.Delivered(content.chatId, content.messageId)
  }

  // --- Il resto ------------------------------------------------------------------------------

  /**
   * Segna un messaggio come gia' aperto almeno una volta, e da li' fa partire la scadenza.
   *
   * La scadenza di una chat conta **dall'apertura**: un messaggio che nessuno ha ancora letto non
   * deve sparire, altrimenti la funzione non protegge una conversazione, la fa perdere.
   */
  suspend fun markRevealed(messageId: String) {
    messages.markRevealed(messageId)
    val entity = messages.byId(messageId) ?: return
    if (entity.expiresAt != null) return
    val ttl = chats.byId(entity.chatId)?.ttlSeconds ?: 0
    if (ttl > 0) messages.setExpiry(messageId, clock() + ttl * 1000L)
  }

  /**
   * Toglie da disco i messaggi scaduti. Da chiamare quando si apre una conversazione.
   *
   * Con gli allegati la scadenza ha acquistato una seconda meta': **il file**. Il file non sta nel
   * database, quindi cancellare la riga non lo tocca -- e finche' la chiave della conversazione c'e'
   * ancora, quel file si apre. Una foto "scaduta" che resta leggibile sul disco e' una scadenza che
   * non e' successa.
   */
  suspend fun sweepExpired() {
    val now = clock()
    val scaduti = messages.expired(now)
    scaduti.forEach { entity ->
      val allegato = hasAttachment(entity)
      opened.remove(entity.id)
      forgetMedia(entity.chatId, entity.id, remote = allegato)
      // Scade **da tutte le parti**: se il documento restasse sul server, un altro dispositivo che
      // si riattacca se lo riporterebbe in casa, e la scadenza sarebbe durata quanto una sincronia.
      runCatching { transport.delete(entity.chatId, entity.id) }
    }
    messages.deleteExpired(now)
  }

  /**
   * Il file di un messaggio che se ne va: da qui e, se c'e' davvero un allegato, dal deposito.
   *
   * Il deposito e' rete: chiedergli di cancellare qualcosa per **ogni** messaggio scaduto vorrebbe
   * dire una chiamata per ogni riga di testo, e le conversazioni sono fatte quasi solo di quelle.
   * Per questo si chiede soltanto quando si sa che un file c'era.
   */
  private suspend fun forgetMedia(chatId: String, messageId: String, remote: Boolean) {
    vault?.delete(messageId)
    if (remote) runCatching { mediaStore.delete(chatId, messageId) }
  }

  /**
   * Se un messaggio ha un file accanto.
   *
   * Lo dice il **corpo**, che sta dentro la busta: da fuori un allegato e un saluto sono la stessa
   * cosa. Se la busta non si apre -- app bloccata, chiave non ancora concordata -- resta il fatto
   * che il file sia gia' su questo telefono, che e' meno preciso ma non chiede niente a nessuno.
   */
  private suspend fun hasAttachment(entity: MessageEntity): Boolean {
    opened[entity.id]?.let { return it is MessageBody.Media }
    if (vault?.has(entity.id) == true) return true
    val chat = chats.byId(entity.chatId) ?: return false
    val key = keyFor(entity.chatId, chat.kind, epochOf(entity)) ?: return false
    val body = openEnvelope(entity, key)?.let(MessageBodyCodec::decode) ?: return false
    return body is MessageBody.Media
  }

  /**
   * Consuma i "visualizza una volta" che sono stati aperti.
   *
   * Si chiama uscendo dalla chat, non appena finita l'animazione: il messaggio va letto, e un
   * testo che si cancella mentre lo si sta ancora leggendo non e' "visualizza una volta", e'
   * "visualizza per un secondo".
   */
  suspend fun burnViewed(ids: Collection<String>) {
    ids.forEach { id ->
      val entity = messages.byId(id) ?: return@forEach
      if (entity.viewOnce && !entity.burned) burn(id)
    }
  }

  /**
   * Un "visualizza una volta" che si e' consumato: il contenuto sparisce, il posto resta.
   *
   * Sparisce **anche dal server**, e sono le regole a permetterlo al destinatario: e' l'unico modo
   * di mantenere quella promessa subito, senza aspettare che una funzione lato server si svegli.
   */
  suspend fun burn(messageId: String) {
    val entity = messages.byId(messageId)
    opened.remove(messageId)
    messages.burn(messageId)
    // Il file se ne va da questo telefono **e** dal deposito: un "visualizza una volta" che lascia
    // dietro la foto e' un "visualizza quante volte vuoi, purche' tu sappia dove guardare".
    vault?.delete(messageId)
    if (entity != null) {
      runCatching { transport.delete(entity.chatId, messageId) }
      runCatching { mediaStore.delete(entity.chatId, messageId) }
    }
  }

  /**
   * La conversazione e' stata aperta: non ci sono piu' non letti, e l'altra persona puo' saperlo.
   *
   * Il momento si scrive in casa e non direttamente sul server: chi apre una chat in aereo l'ha
   * letta comunque, e la ricevuta deve partire quando torna la rete, non andare persa.
   */
  suspend fun clearUnread(chatId: String) {
    chats.clearUnread(chatId)
    chats.setRead(chatId, clock())
  }

  /**
   * Il nome che una conversazione porta nella lista.
   *
   * Serve quando la scheda di qualcuno viene riletta e nel frattempo si e' cambiato nome: senza,
   * la lista continuerebbe a mostrare il nome vecchio finche' non si rifa' il rito, che con il
   * nome non c'entra niente.
   */
  suspend fun rename(chatId: String, title: String) {
    val chat = chats.byId(chatId) ?: return
    if (chat.title == title) return
    chats.reseal(chatId, title, chat.emblemSeed)
  }

  suspend fun setTtl(chatId: String, seconds: Int) = chats.setTtl(chatId, seconds)

  /** Mette (o toglie) la serratura a una conversazione. */
  suspend fun setLocked(chatId: String, locked: Boolean) = chats.setLocked(chatId, locked)

  /**
   * Cancella un messaggio, **anche dal server**.
   *
   * Cancellarlo solo in locale sarebbe una mezza promessa: la copia rimasta nel cloud e' proprio
   * quella che una persona pensava di aver tolto. Le regole lasciano cancellare a chi ha scritto,
   * quindi il gesto arriva fino in fondo o non arriva affatto -- e se la rete non c'e', la riga
   * locale se ne va comunque: quello che si vede e' quello che si e' chiesto.
   */
  suspend fun deleteMessage(messageId: String) {
    val entity = messages.byId(messageId)
    opened.remove(messageId)
    messages.delete(messageId)
    outbox.delete(messageId)
    vault?.delete(messageId)
    if (entity != null && entity.outgoing) {
      runCatching { transport.delete(entity.chatId, messageId) }
      runCatching { mediaStore.delete(entity.chatId, messageId) }
    }
  }

  /**
   * Svuota una conversazione, senza toglierla dalla lista.
   *
   * I file degli allegati se ne vanno con i messaggi. La chiave invece resta -- la conversazione
   * c'e' ancora -- e per questo un file dimenticato qui **sarebbe ancora apribile**: e' la
   * differenza fra svuotare una chat e credere di averla svuotata.
   */
  suspend fun clearChat(chatId: String) {
    messages.idsForChat(chatId).forEach { vault?.delete(it) }
    messages.deleteForChat(chatId)
    opened.clear()
  }

  /**
   * Toglie di mezzo un'intera conversazione: messaggi, riga nella lista, chiave.
   *
   * La chiave se ne va per ultima e se ne va davvero. Lasciarla nel portachiavi dopo aver
   * cancellato i messaggi non servirebbe a niente e sarebbe un segreto in piu' custodito per
   * sbaglio: le cose che non servono piu' non si conservano "nel caso".
   */
  suspend fun deleteChat(chatId: String) {
    outbox.deleteForChat(chatId)
    messages.idsForChat(chatId).forEach { vault?.delete(it) }
    messages.deleteForChat(chatId)
    chats.delete(chatId)
    keyring.forget(chatId)
    chatKeys.remove(chatId)?.fill(0)
    opened.clear()
  }

  /**
   * Da chiamare quando l'app si blocca: i testi aperti non devono sopravvivere alla serratura.
   *
   * Le chiavi non si dimenticano soltanto, si **azzerano**: dimenticarle lascerebbe trentadue byte
   * di segreto in giro per l'heap fino al prossimo passaggio del garbage collector, che e' proprio
   * il periodo in cui qualcuno potrebbe fotografare la memoria di un'app appena bloccata.
   */
  fun forgetOpened() {
    opened.clear()
    chatKeys.values.forEach { it.fill(0) }
    chatKeys.clear()
  }

  private fun toChatMessage(entity: MessageEntity, keys: Map<Int, ByteArray>): ChatMessage {
    // La busta dice con quale epoca e' chiusa. Se quella chiave non c'e' -- si e' entrati nel
    // gruppo dopo -- il messaggio resta sigillato, che e' la verita': non era per noi.
    val key = keys[epochOf(entity)]
    var content = ChatMessage.Content.OPEN
    var text = ""
    var media: MessageBody.Media? = null
    when {
      // Un "visualizza una volta" gia' consumato ha il suo segnaposto: non e' un guasto.
      entity.burned -> Unit
      key == null -> content = ChatMessage.Content.SEALED
      else -> {
        val body = opened[entity.id]
          ?: openEnvelope(entity, key)
            ?.let(MessageBodyCodec::decode)
            ?.also { opened[entity.id] = it }
        // Il corpo dice **di che cosa si tratta**, e lo dice da dentro la busta: il server non lo
        // sa. Per un allegato il testo mostrato e' la didascalia, se c'e'.
        when (body) {
          null -> content = ChatMessage.Content.UNREADABLE
          is MessageBody.Text -> text = body.text
          is MessageBody.Media -> {
            media = body
            text = body.caption
          }
        }
      }
    }
    return ChatMessage(
      id = entity.id,
      outgoing = entity.outgoing,
      text = text,
      content = content,
      media = media,
      spec = SealSpec(
        technique = runCatching { Technique.valueOf(entity.technique) }.getOrDefault(Technique.RUNE),
        seed = entity.seed,
        paintingId = entity.paintingId,
      ),
      createdAt = entity.createdAt,
      status = entity.status,
      revealedOnce = entity.revealedOnce,
      viewOnce = entity.viewOnce,
      burned = entity.burned,
    )
  }

  /**
   * Se una busta non si apre, `null`: il messaggio resta nella lista e lo dice l'app.
   *
   * Restituisce **byte** e non una stringa: quello che c'e' dentro puo' essere il corpo binario di
   * un allegato, e passare da `String` lo rovinerebbe.
   */
  private fun openEnvelope(entity: MessageEntity, key: ByteArray): ByteArray? = runCatching {
    MessageEnvelope.open(
      chatKey = key,
      chatId = entity.chatId,
      messageId = entity.id,
      senderId = entity.senderId,
      envelope = entity.envelope,
    )
  }.getOrNull()

  /**
   * La chiave di una chat, oppure `null` se questa conversazione non si puo' aprire.
   *
   * Per le note a se stessi nasce dal seme del portachiavi: e' un segreto che esiste solo dentro il
   * vault, quindi quelle note sono illeggibili quanto qualsiasi altro messaggio. Per una chat con
   * un'altra persona la chiave viene dal rito della parola d'ordine e sta nel portachiavi, avvolta.
   *
   * `null` non e' un errore: e' la conversazione con qualcuno con cui il rito non e' ancora stato
   * fatto, o e' stato fatto su un altro dispositivo. I messaggi restano li', chiusi, e si aprono il
   * giorno in cui la chiave arriva.
   */
  private suspend fun keyFor(chatId: String, kind: String, epoch: Int? = null): ByteArray? {
    val cacheKey = "$chatId|" + (epoch?.toString() ?: "ora")
    chatKeys[cacheKey]?.let { return it }
    val key = when (kind) {
      // Le note a se stessi non hanno epoche: la chiave nasce dal seme e non cambia mai.
      ChatKind.SELF -> identityRepository.identityOrNull()?.let(::selfChatKey)
      else -> if (epoch == null) keyring.key(chatId) else keyring.key(chatId, epoch)
    } ?: return null
    return chatKeys.putIfAbsent(cacheKey, key) ?: key
  }

  /**
   * Tutte le chiavi con cui una conversazione si puo' aprire, per epoca.
   *
   * Una chat a due ne ha una. Un gruppo ne ha una per ogni volta che qualcuno e' uscito, e servono
   * **tutte**: la busta di ogni messaggio dice con quale e' chiusa, e un gruppo in cui il passato
   * diventa illeggibile a ogni uscita non e' un gruppo di cui ci si fidi.
   */
  private suspend fun keysFor(chat: ChatEntity): Map<Int, ByteArray> = when (chat.kind) {
    ChatKind.SELF -> keyFor(chat.id, chat.kind)?.let { mapOf(0 to it) }.orEmpty()
    else -> keyring.keys(chat.id)
  }

  /**
   * La chiave delle note a se stessi.
   *
   * Non passa dal portachiavi perche' non ha bisogno di essere conservata: nasce ogni volta dal
   * seme dell'identita', che sta nel vault. Una nota a se stessi scritta su un telefono si rilegge
   * su un altro dispositivo della stessa persona senza dover sincronizzare niente.
   */
  /** Con che epoca e' chiusa questa busta. Zero se non si riesce a leggerlo. */
  private fun epochOf(entity: MessageEntity): Int = epochOf(entity.envelope)

  private fun epochOf(envelope: ByteArray): Int =
    runCatching { MessageEnvelope.keyEpochOf(envelope) }.getOrDefault(0)

  private fun selfChatKey(identity: CodexIdentity): ByteArray =
    Hkdf.derive(identity.keyringSeed, "codex-selfchat-v1", 32)

}
