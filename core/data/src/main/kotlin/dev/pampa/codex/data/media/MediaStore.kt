package dev.pampa.codex.data.media

import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import dev.pampa.codex.data.cloud.CloudAccount
import dev.pampa.codex.data.cloud.TransportRefused
import java.io.File
import kotlinx.coroutines.tasks.await

/**
 * Il deposito dei media, dalla parte del server.
 *
 * Quello che passa di qui e' **rumore**: un file cifrato a blocchi con una chiave che nasce dalla
 * conversazione. Il deposito non sa cosa contiene, e -- visto che tutto sale come
 * `application/octet-stream` -- non sa nemmeno se e' una foto o una voce. Sa quanto pesa e a quale
 * conversazione appartiene, e quelle due cose non si possono nascondere a chi trasporta.
 */
interface MediaStore {
  suspend fun upload(chatId: String, mediaId: String, encrypted: File): Result<Unit>
  suspend fun download(chatId: String, mediaId: String, into: File): Result<Unit>
  suspend fun delete(chatId: String, mediaId: String): Result<Unit>
}

/** Nessun deposito: i media restano su questo telefono, e l'app non finge di poterli mandare. */
object NoMediaStore : MediaStore {
  override suspend fun upload(chatId: String, mediaId: String, encrypted: File): Result<Unit> =
    Result.failure(IllegalStateException("nessun deposito"))

  override suspend fun download(chatId: String, mediaId: String, into: File): Result<Unit> =
    Result.failure(IllegalStateException("nessun deposito"))

  override suspend fun delete(chatId: String, mediaId: String): Result<Unit> =
    Result.success(Unit)
}

class FirebaseMediaStore(
  private val storage: FirebaseStorage,
  private val account: CloudAccount,
) : MediaStore {

  override suspend fun upload(chatId: String, mediaId: String, encrypted: File): Result<Unit> {
    if (account.uidOrNull() == null) {
      return Result.failure(IllegalStateException("nessun accesso"))
    }
    return runCatching {
      // Il tipo e' sempre lo stesso, per tutti: e' cio' che impedisce a chi guarda il deposito di
      // dividere le foto dalle note vocali senza aprire niente.
      val metadata = com.google.firebase.storage.storageMetadata {
        contentType = "application/octet-stream"
      }
      reference(chatId, mediaId).putFile(android.net.Uri.fromFile(encrypted), metadata).await()
      Unit
    }.recoverCatching { throw it.asMediaFailure() }
  }

  override suspend fun download(chatId: String, mediaId: String, into: File): Result<Unit> =
    runCatching {
      into.parentFile?.mkdirs()
      reference(chatId, mediaId).getFile(into).await()
      Unit
    }.recoverCatching {
      // Un file scaricato a meta' e' peggio di uno assente: sembra esserci e non si apre. Si toglie.
      into.delete()
      throw it.asMediaFailure()
    }

  override suspend fun delete(chatId: String, mediaId: String): Result<Unit> = runCatching {
    reference(chatId, mediaId).delete().await()
    Unit
  }.recoverCatching { throw it.asMediaFailure() }

  private fun reference(chatId: String, mediaId: String) =
    storage.reference.child("media").child(chatId).child(mediaId)

  private fun Throwable.asMediaFailure(): Throwable = when {
    this is StorageException &&
      errorCode == StorageException.ERROR_NOT_AUTHORIZED -> TransportRefused("deposito negato")
    else -> this
  }
}
