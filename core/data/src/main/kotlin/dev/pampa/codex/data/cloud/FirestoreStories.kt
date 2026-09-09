package dev.pampa.codex.data.cloud

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Le storie su Firestore.
 *
 * Un documento per storia, sotto chi l'ha scritta, con dentro la busta e **una chiave avvolta per
 * ciascun contatto**. Le regole lasciano leggere solo a chi ha un involucro col suo nome: chi non
 * c'e' dentro non vede nemmeno che quella storia esiste.
 *
 * `expireAt` a ventiquattro ore, che le fa sparire da sole con la politica TTL. Una storia che
 * resta e' una storia che non era una storia.
 */
class FirestoreStories(
  private val firestore: FirebaseFirestore,
  private val account: CloudAccount,
  private val clock: () -> Long = System::currentTimeMillis,
) : CloudStories {

  override suspend fun publish(story: CloudStory): Result<Unit> {
    val uid = account.uidOrNull() ?: return Result.failure(IllegalStateException("nessun accesso"))
    if (uid != story.authorUid) {
      return Result.failure(IllegalArgumentException("non si pubblica a nome di un altro"))
    }
    return runCatching {
      withTimeout(TIMEOUT) {
        firestore.collection(STORIES).document(uid).collection(ITEMS).document(story.id).set(
          mapOf(
            AUTHOR_CODEX_ID to story.authorCodexId,
            ENVELOPE to Blob.fromBytes(story.envelope),
            WRAPPED to story.wrappedKeys.mapValues { Blob.fromBytes(it.value) },
            VIEWERS to emptyList<String>(),
            CREATED_AT to story.createdAt,
            EXPIRE_AT to Timestamp((story.createdAt + ONE_DAY) / 1000, 0),
          ),
        ).await()
      }
      Unit
    }.recoverCatching { throw it.asTransportFailure() }
  }

  override fun observe(authorUid: String): Flow<List<CloudStory>> = callbackFlow {
    val registration = firestore.collection(STORIES).document(authorUid).collection(ITEMS)
      .addSnapshotListener { snapshot, error ->
        if (error != null) return@addSnapshotListener
        val storie = snapshot?.documents.orEmpty().mapNotNull { document ->
          val envelope = document.getBlob(ENVELOPE)?.toBytes() ?: return@mapNotNull null
          @Suppress("UNCHECKED_CAST")
          val involucri = (document.get(WRAPPED) as? Map<String, Blob>).orEmpty()
          CloudStory(
            id = document.id,
            authorUid = authorUid,
            authorCodexId = document.getString(AUTHOR_CODEX_ID).orEmpty(),
            envelope = envelope,
            wrappedKeys = involucri.mapValues { it.value.toBytes() },
            createdAt = document.getLong(CREATED_AT) ?: clock(),
          )
        }
        trySend(storie)
      }
    awaitClose { registration.remove() }
  }

  /**
   * "L'ho vista."
   *
   * `arrayUnion` e non una riscrittura: due persone che aprono la stessa storia nello stesso istante
   * non devono cancellarsi a vicenda, e nessuna delle due deve poter riscrivere la lista intera.
   */
  override suspend fun markSeen(authorUid: String, storyId: String, uid: String): Result<Unit> =
    runCatching {
      withTimeout(TIMEOUT) {
        firestore.collection(STORIES).document(authorUid).collection(ITEMS).document(storyId)
          .update(VIEWERS, FieldValue.arrayUnion(uid))
          .await()
      }
      Unit
    }.recoverCatching { throw it.asTransportFailure() }

  override fun observeViewers(authorUid: String, storyId: String): Flow<List<String>> = callbackFlow {
    val registration = firestore.collection(STORIES).document(authorUid).collection(ITEMS)
      .document(storyId)
      .addSnapshotListener { snapshot, error ->
        if (error != null) return@addSnapshotListener
        @Suppress("UNCHECKED_CAST")
        trySend((snapshot?.get(VIEWERS) as? List<String>).orEmpty())
      }
    awaitClose { registration.remove() }
  }

  override suspend fun delete(authorUid: String, storyId: String): Result<Unit> = runCatching {
    withTimeout(TIMEOUT) {
      firestore.collection(STORIES).document(authorUid).collection(ITEMS).document(storyId)
        .delete().await()
    }
    Unit
  }.recoverCatching { throw it.asTransportFailure() }

  private companion object {
    const val STORIES = "stories"
    const val ITEMS = "items"
    const val AUTHOR_CODEX_ID = "authorCodexId"
    const val ENVELOPE = "envelope"
    const val WRAPPED = "wrappedKeys"
    const val VIEWERS = "viewers"
    const val CREATED_AT = "createdAt"
    const val EXPIRE_AT = "expireAt"
    const val ONE_DAY = 24L * 60 * 60 * 1000
    const val TIMEOUT = 12_000L
  }
}
