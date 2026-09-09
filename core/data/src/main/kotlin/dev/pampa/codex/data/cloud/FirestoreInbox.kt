package dev.pampa.codex.data.cloud

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * La posta, su Firestore: `inbox/{uid}/items`.
 *
 * Ogni busta e' cifrata per il destinatario e firmata da chi la manda, quindi il documento e'
 * rumore con sopra due nomi: chi scrive e chi riceve. Le regole lasciano **scrivere a chiunque**
 * abbia un account (altrimenti nessuno potrebbe farsi consegnare la chiave di un gruppo da qualcuno
 * che non lo conosce ancora) e **leggere solo al proprietario**.
 *
 * `expireAt` a sette giorni: una chiave di gruppo non consegnata in una settimana non e' piu' una
 * consegna in ritardo, e' una consegna da rifare.
 */
class FirestoreInbox(
  private val firestore: FirebaseFirestore,
  private val account: CloudAccount,
  private val clock: () -> Long = System::currentTimeMillis,
) : InboxTransport {

  override suspend fun send(toUid: String, itemId: String, parcel: ByteArray): Result<Unit> {
    val uid = account.uidOrNull() ?: return Result.failure(IllegalStateException("nessun accesso"))
    return runCatching {
      val now = clock()
      // **Con un tetto di tempo.** Una scrittura Firestore offline non fallisce: resta in attesa
      // dell'acknowledgement del server, e il `Task` non si completa mai. Chi ha chiamato questo
      // metodo aspetterebbe per sempre. Il documento resta comunque in coda dentro l'SDK e partira'
      // quando la rete torna: qui si smette solo di **aspettarlo**.
      withTimeout(SEND_TIMEOUT) {
        firestore.collection(INBOX).document(toUid).collection(ITEMS).document(itemId).set(
          mapOf(
            FROM_UID to uid,
            PARCEL to Blob.fromBytes(parcel),
            CREATED_AT to now,
            EXPIRE_AT to Timestamp((now + SEVEN_DAYS) / 1000, 0),
          ),
        ).await()
      }
      Unit
    }.recoverCatching { throw it.asTransportFailure() }
  }

  override fun observe(uid: String): Flow<List<InboxItem>> = callbackFlow {
    val registration = firestore.collection(INBOX).document(uid).collection(ITEMS)
      .addSnapshotListener { snapshot, error ->
        if (error != null) return@addSnapshotListener
        val items = snapshot?.documents.orEmpty().mapNotNull { document ->
          val parcel = document.getBlob(PARCEL)?.toBytes() ?: return@mapNotNull null
          InboxItem(
            id = document.id,
            fromUid = document.getString(FROM_UID).orEmpty(),
            parcel = parcel,
            createdAt = document.getLong(CREATED_AT) ?: 0L,
          )
        }
        trySend(items)
      }
    awaitClose { registration.remove() }
  }

  override suspend fun delete(uid: String, itemId: String): Result<Unit> = runCatching {
    firestore.collection(INBOX).document(uid).collection(ITEMS).document(itemId).delete().await()
    Unit
  }.recoverCatching { throw it.asTransportFailure() }

  private companion object {
    const val INBOX = "inbox"
    const val ITEMS = "items"
    const val FROM_UID = "fromUid"
    const val PARCEL = "parcel"
    const val CREATED_AT = "createdAt"
    const val EXPIRE_AT = "expireAt"
    const val SEVEN_DAYS = 7L * 24 * 60 * 60 * 1000

    /** Quanto si aspetta che il server confermi, prima di dire "non adesso". */
    const val SEND_TIMEOUT = 12_000L
  }
}
