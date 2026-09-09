package dev.pampa.codex.seal

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pampa.codex.BuildConfig
import dev.pampa.codex.data.chat.ExportablePainting
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Il quadro che esce dall'app, e quello che rientra.
 *
 * E' l'unico ponte fra Codex e il resto del telefono, ed e' anche la cosa che la primissima
 * versione dell'app faceva -- male, con un BMP di rumore -- e che qui torna fatta sul serio: un
 * dipinto vero, con dentro una busta vera.
 *
 * **PNG, e mandato come file.** Qualunque canale che ricomprime l'immagine come foto ricalcola i
 * colori, e i bit bassi -- cioe' il messaggio -- se ne vanno con la ricompressione. L'app lo dice
 * quando condivide, e quando gliene arriva uno vuoto sa dire perche'.
 */
@Singleton
class PaintingShare @Inject constructor(
  @ApplicationContext private val context: Context,
) {

  private val catalog = PaintingCatalog(context.assets)
  private val stego = PaintingStego(catalog)

  /**
   * Scrive il PNG e restituisce l'indirizzo con cui gli altri programmi possono leggerlo.
   *
   * I file finiscono in una cartella della cache: sono copie, l'originale e' la riga nel database,
   * e se il sistema li cancella non si e' perso niente. La cartella viene ripulita a ogni
   * esportazione, cosi' non resta in giro un archivio di messaggi in chiaro sul disco -- che in
   * chiaro non sono, ma restare non devono lo stesso.
   */
  suspend fun writePng(export: ExportablePainting): Result<Uri> = withContext(Dispatchers.IO) {
    runCatching {
      val painting = catalog.byId(export.paintingId) ?: catalog.pick(export.seed)
        ?: error("nessun dipinto nella raccolta")
      val bitmap = stego.embed(painting, export.parcel)
      val folder = File(context.cacheDir, FOLDER).apply {
        deleteRecursively()
        mkdirs()
      }
      val file = File(folder, "codex-${painting.id}.png")
      file.outputStream().use { output ->
        if (!stego.writePng(bitmap, output)) error("il PNG non si e' scritto")
      }
      bitmap.recycle()
      FileProvider.getUriForFile(context, AUTHORITY, file)
    }
  }

  /** L'intento di condivisione, con il permesso di lettura attaccato all'indirizzo. */
  fun shareIntent(uri: Uri, subject: String): Intent = Intent(Intent.ACTION_SEND).apply {
    type = MIME
    putExtra(Intent.EXTRA_STREAM, uri)
    putExtra(Intent.EXTRA_SUBJECT, subject)
    // Senza la ClipData il permesso non viaggia su alcune app: la flag da sola non basta.
    clipData = ClipData.newRawUri(subject, uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }

  /** Quello che c'e' nascosto in un'immagine, o `null` se non c'e' niente. */
  suspend fun readPayload(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
      context.contentResolver.openInputStream(uri)?.use { stego.extract(it) }
    }.getOrNull()
  }

  private companion object {
    const val FOLDER = "quadri-condivisi"
    const val MIME = "image/png"
    val AUTHORITY = BuildConfig.APPLICATION_ID + ".quadri"
  }
}
