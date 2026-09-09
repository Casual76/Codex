package dev.pampa.codex.data.cloud

import kotlinx.coroutines.flow.Flow

/**
 * Un messaggio come viaggia: **una busta e il minimo per consegnarla**.
 *
 * Quello che non c'e' dentro conta quanto quello che c'e'. Non c'e' il testo, ovviamente. Ma non ci
 * sono nemmeno la tecnica del sigillo ne' il suo seme: nascono dalla chiave della chat e dall'id del
 * messaggio, quindi chi riceve li ricalcola da se'. Metterli qui li regalerebbe al server senza dare
 * niente in cambio, e permetterebbe a chi trasporta di dire "questo e' un quadro" di un messaggio
 * che non lo e'.
 *
 * `viewOnce` invece e' in chiaro, ed e' l'unica eccezione: serve alle regole, perche' e' cio' che
 * autorizza **il destinatario** a cancellare un messaggio che non ha scritto lui. Senza, quella
 * promessa dipenderebbe da una funzione lato server che potrebbe non girare mai.
 */
data class CloudMessage(
  val chatId: String,
  val messageId: String,
  val senderUid: String,
  /** Il Codex ID di chi scrive: serve ad aprire la busta, che lo autentica. */
  val senderCodexId: String,
  val envelope: ByteArray,
  val viewOnce: Boolean,
  val createdAt: Long,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is CloudMessage) return false
    return messageId == other.messageId &&
      chatId == other.chatId &&
      senderUid == other.senderUid &&
      senderCodexId == other.senderCodexId &&
      envelope.contentEquals(other.envelope) &&
      viewOnce == other.viewOnce &&
      createdAt == other.createdAt
  }

  override fun hashCode(): Int {
    var result = messageId.hashCode()
    result = 31 * result + chatId.hashCode()
    result = 31 * result + envelope.contentHashCode()
    return result
  }
}

/**
 * Fin dove l'altra persona e' arrivata.
 *
 * Due numeri e non una spunta per messaggio: una conversazione si legge dall'inizio alla fine,
 * quindi "ho letto fino a qui" dice la stessa cosa di mille bandierine e costa una scrittura invece
 * di mille. E fa anche una promessa piu' onesta: nessuno tiene traccia di **quali** messaggi hai
 * guardato, solo di dove sei arrivato.
 */
data class Receipt(val deliveredAt: Long, val readAt: Long)

/**
 * Il server ha detto di no, e continuera' a dirlo.
 *
 * E' un tipo di Codex e non di Firestore per una ragione di confine: sopra `:core:data` nessuno deve
 * conoscere la libreria che sta sotto, e senza questo un test avrebbe bisogno di costruire
 * un'eccezione di Firebase -- cosa che su una JVM da sola non si puo' nemmeno fare.
 */
class TransportRefused(message: String) : Exception(message)

/** L'accesso non c'e' o non vale piu': si riprova appena si rientra. */
class TransportUnauthenticated : Exception("nessun accesso valido")

/** Perche' una consegna non e' riuscita. La differenza decide se ritentare o smettere. */
sealed interface SendFailure {
  /** Rete assente o server lento: si ritenta. */
  data object Retry : SendFailure

  /** Il server ha detto di no e continuera' a dirlo: ritentare e' solo consumare batteria. */
  data class Refused(val reason: String) : SendFailure

  /** Senza accesso non si consegna niente, ma appena si entra si riprova. */
  data object SignedOut : SendFailure
}

/**
 * Il trasporto: come una busta arriva dall'altra parte.
 *
 * E' un'interfaccia perche' i trasporti saranno due -- il cloud e le vicinanze (M7) -- e perche' il
 * resto dell'app non deve accorgersi di quale sta usando. Sopra questo strato una conversazione e'
 * la stessa cosa che ci si scriva attraverso mezzo mondo o attraverso un tavolo.
 */
interface CloudTransport {

  /**
   * Assicura che la conversazione esista dalla parte del server.
   *
   * Non porta nessun contenuto: porta **chi sono i membri**, che e' l'unica cosa che il server deve
   * sapere per decidere chi puo' scrivere e chi puo' leggere. I membri si scrivono una volta e le
   * regole non ne lasciano piu' cambiare la lista.
   */
  suspend fun ensureChat(chatId: String, memberUids: List<String>): Result<Unit>

  /**
   * Assicura che un **gruppo** esista dalla parte del server.
   *
   * Un gruppo non e' una chat a due con piu' gente: la sua lista di membri cambia nel tempo, e chi
   * puo' cambiarla e' scritto nel documento. Il server non sa cosa sia una chiave di gruppo -- quella
   * viaggia per posta, cifrata -- sa solo a chi consegnare.
   */
  suspend fun ensureGroup(
    chatId: String,
    memberUids: List<String>,
    adminUids: List<String>,
  ): Result<Unit>

  suspend fun send(message: CloudMessage): Result<Unit>

  /** I messaggi di una conversazione, man mano che arrivano. */
  fun observe(chatId: String): Flow<List<CloudMessage>>

  /** Toglie un messaggio dal server: il mittente sempre, il destinatario per i "una volta". */
  suspend fun delete(chatId: String, messageId: String): Result<Unit>

  /** Dice fin dove si e' arrivati. Scrive solo la propria riga: lo impongono anche le regole. */
  suspend fun reportReceipt(chatId: String, uid: String, receipt: Receipt): Result<Unit>

  /** Le ricevute degli altri membri, man mano che cambiano. */
  fun observeReceipts(chatId: String): Flow<Map<String, Receipt>>

  /**
   * "Sto scrivendo", valido fino a un istante.
   *
   * Si manda una scadenza e non un interruttore acceso/spento: un'app chiusa a meta' frase
   * lascerebbe l'altro a guardare per sempre un "sta scrivendo" che non finisce. Cosi' invece
   * scade da sola, e non serve nessuna pulizia.
   */
  suspend fun reportTyping(chatId: String, uid: String, until: Long): Result<Unit>

  fun observeTyping(chatId: String): Flow<Map<String, Long>>

  /** Che c'e' qualcuno, adesso. Si riscrive ogni tanto: e' un battito, non un evento. */
  suspend fun reportPresence(uid: String, online: Boolean): Result<Unit>

  fun observePresence(uid: String): Flow<Long>
}

/** Se valga la pena riprovare. */
internal fun Throwable.toSendFailure(): SendFailure = when (this) {
  is TransportRefused -> SendFailure.Refused(message.orEmpty())
  is TransportUnauthenticated -> SendFailure.SignedOut
  is IllegalStateException -> SendFailure.SignedOut
  else -> SendFailure.Retry
}

/** Nessun trasporto: l'app resta locale e non finge il contrario. */
object NoTransport : CloudTransport {
  override suspend fun ensureGroup(
    chatId: String,
    memberUids: List<String>,
    adminUids: List<String>,
  ): Result<Unit> = Result.failure(IllegalStateException("nessun trasporto"))

  override suspend fun ensureChat(chatId: String, memberUids: List<String>): Result<Unit> =
    Result.failure(IllegalStateException("nessun trasporto"))

  override suspend fun send(message: CloudMessage): Result<Unit> =
    Result.failure(IllegalStateException("nessun trasporto"))

  override fun observe(chatId: String): Flow<List<CloudMessage>> =
    kotlinx.coroutines.flow.emptyFlow()

  override suspend fun delete(chatId: String, messageId: String): Result<Unit> =
    Result.failure(IllegalStateException("nessun trasporto"))

  override suspend fun reportReceipt(chatId: String, uid: String, receipt: Receipt): Result<Unit> =
    Result.failure(IllegalStateException("nessun trasporto"))

  override fun observeReceipts(chatId: String): Flow<Map<String, Receipt>> =
    kotlinx.coroutines.flow.emptyFlow()

  override suspend fun reportTyping(chatId: String, uid: String, until: Long): Result<Unit> =
    Result.failure(IllegalStateException("nessun trasporto"))

  override fun observeTyping(chatId: String): Flow<Map<String, Long>> =
    kotlinx.coroutines.flow.emptyFlow()

  override suspend fun reportPresence(uid: String, online: Boolean): Result<Unit> =
    Result.failure(IllegalStateException("nessun trasporto"))

  override fun observePresence(uid: String): Flow<Long> = kotlinx.coroutines.flow.emptyFlow()
}
