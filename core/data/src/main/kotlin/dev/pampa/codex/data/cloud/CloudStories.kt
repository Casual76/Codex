package dev.pampa.codex.data.cloud

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Le storie, dalla parte del server.
 *
 * Una storia sta sotto **chi l'ha scritta** (`stories/{uid}/items/{storyId}`) e non sotto chi la
 * riceve: e' una cosa sola pubblicata una volta, non venti copie. Chi la guarda la legge da li', e
 * apre l'unico involucro che porta il suo nome.
 *
 * Il server vede: chi ha pubblicato, quando, e **quanti** involucri ci sono. Non vede il testo, non
 * vede la chiave, e non puo' dire se due involucri appartengono alla stessa persona. Che il numero
 * di contatti si intuisca dal numero di involucri e' il prezzo di non fare venti copie, e va detto.
 */
interface CloudStories {

  suspend fun publish(story: CloudStory): Result<Unit>

  /** Le storie vive di una persona. Si ascolta un contatto per volta: sono pochi e cambiano poco. */
  fun observe(authorUid: String): Flow<List<CloudStory>>

  /** "L'ho vista": lo sa solo chi l'ha scritta, ed e' l'unica cosa che torna indietro. */
  suspend fun markSeen(authorUid: String, storyId: String, uid: String): Result<Unit>

  /** Chi ha visto una mia storia. */
  fun observeViewers(authorUid: String, storyId: String): Flow<List<String>>

  suspend fun delete(authorUid: String, storyId: String): Result<Unit>
}

/**
 * Una storia come viaggia.
 *
 * `wrappedKeys` va da **uid a chiave avvolta**: l'uid perche' e' l'unico nome che il server conosce,
 * la chiave avvolta perche' e' l'unica forma in cui puo' passargli davanti.
 */
data class CloudStory(
  val id: String,
  val authorUid: String,
  val authorCodexId: String,
  val envelope: ByteArray,
  val wrappedKeys: Map<String, ByteArray>,
  val createdAt: Long,
) {
  override fun equals(other: Any?): Boolean =
    other is CloudStory && id == other.id && authorUid == other.authorUid &&
      authorCodexId == other.authorCodexId && envelope.contentEquals(other.envelope) &&
      createdAt == other.createdAt &&
      wrappedKeys.keys == other.wrappedKeys.keys

  override fun hashCode(): Int = 31 * id.hashCode() + envelope.contentHashCode()
}

/** Nessuna storia sul server: restano quelle di questo telefono, e l'app non finge il contrario. */
object NoStories : CloudStories {
  override suspend fun publish(story: CloudStory): Result<Unit> =
    Result.failure(IllegalStateException("nessun servizio storie"))

  override fun observe(authorUid: String): Flow<List<CloudStory>> = flowOf(emptyList())

  override suspend fun markSeen(authorUid: String, storyId: String, uid: String): Result<Unit> =
    Result.success(Unit)

  override fun observeViewers(authorUid: String, storyId: String): Flow<List<String>> =
    flowOf(emptyList())

  override suspend fun delete(authorUid: String, storyId: String): Result<Unit> =
    Result.success(Unit)
}
