package dev.pampa.codex.data.media

import dev.pampa.codex.crypto.MediaCipher
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dove vivono i media su questo telefono.
 *
 * **Sul disco ci finisce solo il file cifrato.** Non e' una scelta di comodita': e' cio' che rende
 * vera la promessa del "visualizza una volta". Se Codex tenesse da qualche parte la copia in chiaro
 * di una foto -- anche solo nella cache, anche solo per disegnarla -- quella copia sopravviverebbe
 * al momento in cui il messaggio si consuma, e la promessa sarebbe mezza. Cosi' invece il chiaro
 * esiste soltanto in memoria, per il tempo in cui la foto e' sullo schermo.
 *
 * Il prezzo e' che riaprire una foto la ridecifra. Su un telefono e' qualche millisecondo, e vale
 * il fatto di non doversi mai chiedere "dove sono finite le copie".
 */
class MediaVault(private val directory: File) {

  private val root: File
    get() = directory.apply { mkdirs() }

  /** Il file cifrato di un media, esista o no. */
  fun file(mediaId: String): File = File(root, sanitize(mediaId))

  fun has(mediaId: String): Boolean = file(mediaId).isFile

  fun delete(mediaId: String) {
    file(mediaId).delete()
  }

  /** Quanto spazio occupano i media di questo telefono. Serve alle impostazioni, piu' avanti. */
  fun usedBytes(): Long = root.listFiles()?.sumOf { it.length() } ?: 0L

  /**
   * Cifra quello che arriva da fuori e lo mette via.
   *
   * `plainLength` va saputo prima: il formato lo scrive nell'intestazione, ed e' cosi' che un file
   * troncato viene riconosciuto invece che aperto a meta'.
   */
  suspend fun store(
    mediaId: String,
    key: ByteArray,
    source: InputStream,
    plainLength: Long,
  ): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      val destination = file(mediaId)
      destination.outputStream().use { output ->
        source.use { input -> MediaCipher.encrypt(key, input, output, plainLength) }
      }
      destination
    }
  }

  /**
   * Il contenuto in chiaro, **solo in memoria**.
   *
   * Restituisce `null` se il file non c'e' ancora (non e' stato scaricato) e lancia se non si apre:
   * le due cose sono diverse e a schermo si dicono in modo diverso -- "sto scaricando" non e' "non
   * si apre".
   */
  suspend fun open(mediaId: String, key: ByteArray): ByteArray? = withContext(Dispatchers.IO) {
    val source = file(mediaId)
    if (!source.isFile) return@withContext null
    val output = ByteArrayOutputStream(source.length().toInt().coerceAtLeast(32))
    source.inputStream().use { MediaCipher.decrypt(key, it, output) }
    output.toByteArray()
  }

  /**
   * Toglie di mezzo tutto: fa parte del "cancella tutto".
   *
   * I file sono cifrati con chiavi che discendono dall'identita', quindi cancellare il vault li
   * renderebbe comunque illeggibili -- ma lasciarli occuperebbe spazio per niente, e "cancella
   * tutto" deve voler dire tutto.
   */
  fun wipe() {
    root.deleteRecursively()
  }

  /**
   * Un identificatore diventa un nome di file, senza poter uscire dalla cartella.
   *
   * Il `mediaId` arriva **da dentro la busta di un messaggio**, cioe' da un'altra persona: e' un
   * dato autenticato, non uno di cui fidarsi. Un id fatto di `../` scriverebbe dove gli pare.
   */
  private fun sanitize(mediaId: String): String =
    mediaId.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }
      .joinToString("")
      .take(96)
      .ifBlank { "senza-nome" }

}
