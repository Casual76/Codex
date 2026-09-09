package dev.pampa.codex.data.cloud

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * La posta: buste indirizzate a **una persona**, fuori da ogni conversazione.
 *
 * Serve alle cose che vengono prima di una chat e non possono passarci dentro -- la chiave di un
 * gruppo, per esempio: chi entra non ha ancora la chiave, quindi non puo' ricevere niente dal
 * gruppo, quindi la chiave deve arrivargli per un'altra strada. Questa.
 *
 * Il server trasporta e non legge: dentro c'e' una busta [dev.pampa.codex.crypto.InboxParcel],
 * chiusa con la chiave di coppia. Quello che il server sa e' che qualcuno ha scritto a qualcun
 * altro, che e' la stessa cosa che sa di ogni messaggio.
 *
 * **Le buste si cancellano dopo averle lette.** Una chiave di gruppo consegnata due volte non fa
 * danno, ma una posta che non si svuota mai diventa un archivio di chi ha scritto a chi.
 */
interface InboxTransport {

  /** Mette una busta nella posta di qualcun altro. */
  suspend fun send(toUid: String, itemId: String, parcel: ByteArray): Result<Unit>

  /** Le buste arrivate a noi. */
  fun observe(uid: String): Flow<List<InboxItem>>

  /** Toglie una busta gia' letta. */
  suspend fun delete(uid: String, itemId: String): Result<Unit>
}

/** Una busta in arrivo, con chi dice di averla mandata. Chi lo dice davvero e' la firma. */
data class InboxItem(
  val id: String,
  val fromUid: String,
  val parcel: ByteArray,
  val createdAt: Long,
) {
  override fun equals(other: Any?): Boolean =
    other is InboxItem && id == other.id && fromUid == other.fromUid &&
      parcel.contentEquals(other.parcel) && createdAt == other.createdAt

  override fun hashCode(): Int = 31 * (31 * id.hashCode() + fromUid.hashCode()) + parcel.contentHashCode()
}

/** Nessuna posta: senza Firebase i gruppi restano quelli gia' ricevuti, e l'app non finge. */
object NoInbox : InboxTransport {
  override suspend fun send(toUid: String, itemId: String, parcel: ByteArray): Result<Unit> =
    Result.failure(IllegalStateException("nessuna posta"))

  override fun observe(uid: String): Flow<List<InboxItem>> = flowOf(emptyList())

  override suspend fun delete(uid: String, itemId: String): Result<Unit> = Result.success(Unit)
}
