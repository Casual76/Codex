package dev.pampa.codex.data.cloud

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Il trasporto su Firestore.
 *
 * **Il server e' un corriere, non un destinatario.** Quello che gli passa fra le mani e' una busta
 * `CDX3` che nessuna chiave sua puo' aprire: puo' rifiutarsi di consegnare, puo' sapere che due
 * persone si scrivono e quando, non puo' sapere cosa si dicono. E' il massimo che si possa ottenere
 * da un intermediario, e va detto con precisione invece di promettere di piu'.
 *
 * `expireAt` sta in ogni messaggio perche' la politica TTL di Firestore lo guardi: dopo trenta
 * giorni il server dimentica, e quello che resta e' solo sui telefoni delle due persone.
 */
class FirestoreTransport(
  private val firestore: FirebaseFirestore,
  private val account: CloudAccount,
  private val clock: () -> Long = System::currentTimeMillis,
) : CloudTransport {

  override suspend fun ensureChat(chatId: String, memberUids: List<String>): Result<Unit> {
    val uid = account.uidOrNull() ?: return Result.failure(IllegalStateException("nessun accesso"))
    if (uid !in memberUids) {
      return Result.failure(IllegalArgumentException("non si crea una chat fra altri due"))
    }
    return runCatching {
      // **Si scrive senza leggere prima.**
      //
      // Un `get` sulla conversazione non serve e non si potrebbe nemmeno fare la prima volta: le
      // regole non lasciano leggere un documento che non c'e'. Scrivere lo stesso contenuto due
      // volte invece va bene in tutti e due i casi -- la prima volta lo crea, le altre cambia solo
      // `lastActivityAt`, che e' esattamente cio' che la regola di aggiornamento consente.
      firestore.collection(CHATS).document(chatId).set(
        mapOf(
          TYPE to DIRECT,
          MEMBER_UIDS to memberUids.sorted(),
          LAST_ACTIVITY to clock(),
        ),
      ).await()
      Unit
    }.recoverCatching { throw it.asTransportFailure() }
  }

  override suspend fun ensureGroup(
    chatId: String,
    memberUids: List<String>,
    adminUids: List<String>,
  ): Result<Unit> {
    val uid = account.uidOrNull() ?: return Result.failure(IllegalStateException("nessun accesso"))
    if (uid !in memberUids) {
      return Result.failure(IllegalArgumentException("non si fa un gruppo di cui non si fa parte"))
    }
    return runCatching {
      firestore.collection(CHATS).document(chatId).set(
        mapOf(
          TYPE to GROUP,
          MEMBER_UIDS to memberUids.sorted(),
          ADMIN_UIDS to adminUids.sorted(),
          LAST_ACTIVITY to clock(),
        ),
      ).await()
      Unit
    }.recoverCatching { throw it.asTransportFailure() }
  }

  override suspend fun send(message: CloudMessage): Result<Unit> = runCatching {
    val chat = firestore.collection(CHATS).document(message.chatId)
    chat.collection(MESSAGES).document(message.messageId).set(
      mapOf(
        SENDER_UID to message.senderUid,
        SENDER_CODEX_ID to message.senderCodexId,
        ENVELOPE to Blob.fromBytes(message.envelope),
        VIEW_ONCE to message.viewOnce,
        CREATED_AT to message.createdAt,
        // La scadenza la scrive il mittente e la fa rispettare il server: trenta giorni, come dice
        // il piano. In locale non cambia niente -- li' i messaggi restano finche' non li si toglie.
        EXPIRE_AT to com.google.firebase.Timestamp(
          (message.createdAt + THIRTY_DAYS) / 1000,
          0,
        ),
      ),
    ).await()
    // L'ora dell'ultima attivita' serve alla lista delle chat di chi riceve. Se fallisce non e'
    // grave: il messaggio e' gia' consegnato, ed e' quello che conta.
    runCatching { chat.update(LAST_ACTIVITY, FieldValue.serverTimestamp()).await() }
    Unit
  }.recoverCatching { throw it.asTransportFailure() }

  override fun observe(chatId: String): Flow<List<CloudMessage>> = callbackFlow {
    val registration = firestore.collection(CHATS).document(chatId).collection(MESSAGES)
      .orderBy(CREATED_AT, Query.Direction.ASCENDING)
      .limitToLast(PAGE)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          // Un ascolto che muore non deve far morire l'app: si chiude il flusso e chi lo raccoglie
          // riproverà. Il caso tipico e' il permesso negato subito dopo essere usciti dall'account.
          close()
          return@addSnapshotListener
        }
        val messages = snapshot?.documents.orEmpty().mapNotNull { document ->
          val envelope = document.getBlob(ENVELOPE)?.toBytes() ?: return@mapNotNull null
          CloudMessage(
            chatId = chatId,
            messageId = document.id,
            senderUid = document.getString(SENDER_UID).orEmpty(),
            senderCodexId = document.getString(SENDER_CODEX_ID).orEmpty(),
            envelope = envelope,
            viewOnce = document.getBoolean(VIEW_ONCE) ?: false,
            createdAt = document.getLong(CREATED_AT) ?: 0L,
          )
        }
        trySend(messages)
      }
    awaitClose { registration.remove() }
  }

  override suspend fun delete(chatId: String, messageId: String): Result<Unit> = runCatching {
    firestore.collection(CHATS).document(chatId)
      .collection(MESSAGES).document(messageId)
      .delete()
      .await()
  }

  override suspend fun reportReceipt(
    chatId: String,
    uid: String,
    receipt: Receipt,
  ): Result<Unit> = runCatching {
    firestore.collection(CHATS).document(chatId)
      .collection(RECEIPTS).document(uid)
      .set(mapOf(DELIVERED_AT to receipt.deliveredAt, READ_AT to receipt.readAt))
      .await()
    Unit
  }.recoverCatching { throw it.asTransportFailure() }

  override fun observeReceipts(chatId: String): Flow<Map<String, Receipt>> = callbackFlow {
    val registration = firestore.collection(CHATS).document(chatId).collection(RECEIPTS)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          close()
          return@addSnapshotListener
        }
        trySend(
          snapshot?.documents.orEmpty().associate { document ->
            document.id to Receipt(
              deliveredAt = document.getLong(DELIVERED_AT) ?: 0L,
              readAt = document.getLong(READ_AT) ?: 0L,
            )
          },
        )
      }
    awaitClose { registration.remove() }
  }

  override suspend fun reportTyping(chatId: String, uid: String, until: Long): Result<Unit> =
    runCatching {
      firestore.collection(CHATS).document(chatId)
        .collection(TYPING).document(uid)
        .set(mapOf(UNTIL to until))
        .await()
      Unit
    }.recoverCatching { throw it.asTransportFailure() }

  override fun observeTyping(chatId: String): Flow<Map<String, Long>> = callbackFlow {
    val registration = firestore.collection(CHATS).document(chatId).collection(TYPING)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          close()
          return@addSnapshotListener
        }
        trySend(
          snapshot?.documents.orEmpty()
            .associate { it.id to (it.getLong(UNTIL) ?: 0L) },
        )
      }
    awaitClose { registration.remove() }
  }

  override suspend fun reportPresence(uid: String, online: Boolean): Result<Unit> = runCatching {
    firestore.collection(PRESENCE).document(uid)
      .set(mapOf(STATE to if (online) ONLINE else OFFLINE, LAST_SEEN to clock()))
      .await()
    Unit
  }.recoverCatching { throw it.asTransportFailure() }

  override fun observePresence(uid: String): Flow<Long> = callbackFlow {
    val registration = firestore.collection(PRESENCE).document(uid)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          close()
          return@addSnapshotListener
        }
        // Si manda **l'ultimo momento in cui c'era**, non "online": chi decide se e' ancora online
        // e' chi guarda, con il suo orologio. Un "online" scritto e mai piu' toccato -- perche'
        // l'app e' stata uccisa dal sistema -- resterebbe acceso per sempre.
        val online = snapshot?.getString(STATE) == ONLINE
        trySend(if (online) snapshot?.getLong(LAST_SEEN) ?: 0L else 0L)
      }
    awaitClose { registration.remove() }
  }

  private companion object {
    const val CHATS = "chats"
    const val MESSAGES = "messages"
    const val TYPE = "type"
    const val DIRECT = "direct"
    const val GROUP = "group"
    const val ADMIN_UIDS = "adminUids"
    const val MEMBER_UIDS = "memberUids"
    const val LAST_ACTIVITY = "lastActivityAt"
    const val SENDER_UID = "senderUid"
    const val SENDER_CODEX_ID = "senderCodexId"
    const val ENVELOPE = "envelope"
    const val VIEW_ONCE = "viewOnce"
    const val CREATED_AT = "createdAt"
    const val EXPIRE_AT = "expireAt"
    const val RECEIPTS = "receipts"
    const val DELIVERED_AT = "deliveredAt"
    const val READ_AT = "readAt"
    const val TYPING = "typing"
    const val UNTIL = "until"
    const val PRESENCE = "presence"
    const val STATE = "state"
    const val LAST_SEEN = "lastSeen"
    const val ONLINE = "online"
    const val OFFLINE = "offline"

    /** Quanti messaggi tenere sotto ascolto. Oltre, si pagina -- ma non in questa milestone. */
    const val PAGE = 200L
    const val THIRTY_DAYS = 30L * 24 * 60 * 60 * 1000
  }
}

/**
 * Traduce quello che lancia Firestore in quello che il resto dell'app sa gestire.
 *
 * Si fa **qui**, dove Firestore lo si conosce: piu' in la' un `PERMISSION_DENIED` sarebbe solo
 * un'eccezione qualunque, e verrebbe ritentata per sempre. E' `internal` perche' anche la posta
 * (`FirestoreInbox`) parla con Firestore e deve tradurre allo stesso modo.
 */
internal fun Throwable.asTransportFailure(): Throwable = when {
  this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
    TransportRefused("il server ha rifiutato la scrittura")
  this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.UNAUTHENTICATED ->
    TransportUnauthenticated()
  else -> this
}
