package dev.pampa.codex.ui.chat

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Registra una nota vocale.
 *
 * **Il file temporaneo e' un compromesso, e vale dirlo.** Tutto il resto di Codex non scrive mai un
 * media in chiaro su disco: la foto che scegli viene letta e cifrata al volo. Qui non si puo': il
 * registratore di sistema produce un MP4, e un MP4 si chiude scrivendo l'indice in testa al file --
 * cioe' tornando indietro. Un flusso che non si puo' riavvolgere non va bene, e un tubo nemmeno.
 *
 * Quindi il file esiste, ma: sta nella cache privata dell'app (nessun'altra app lo vede, non e'
 * nella galleria, non finisce nei backup), vive quanto la registrazione, e viene **cancellato
 * subito dopo essere stato cifrato**. Se l'app muore nel mezzo, chi torna trova la cartella
 * ripulita: [discardLeftovers] gira all'avvio della schermata.
 *
 * L'ampiezza viene campionata mentre si registra: e' cio' che disegna la forma d'onda. Non e' una
 * decorazione -- una nota vocale senza forma e' una barra grigia, e non si vede dove si e' parlato.
 */
class VoiceRecorder(private val context: Context) {

  data class Recording(val file: File, val durationMs: Long, val waveform: ByteArray) {
    override fun equals(other: Any?): Boolean =
      other is Recording && file == other.file && durationMs == other.durationMs &&
        waveform.contentEquals(other.waveform)

    override fun hashCode(): Int =
      31 * (31 * file.hashCode() + durationMs.hashCode()) + waveform.contentHashCode()
  }

  private var recorder: MediaRecorder? = null
  private var target: File? = null
  private var startedAt = 0L
  private val samples = mutableListOf<Int>()

  val isRecording: Boolean get() = recorder != null

  /** La forma d'onda finora, per disegnarla mentre si parla. */
  fun currentWaveform(): ByteArray = compress(samples, columns = BARS)

  fun start(): Boolean {
    if (recorder != null) return true
    val folder = File(context.cacheDir, "voce").apply { mkdirs() }
    val file = File(folder, "reg-${System.currentTimeMillis()}.m4a")
    val instance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      MediaRecorder(context)
    } else {
      @Suppress("DEPRECATION")
      MediaRecorder()
    }
    return runCatching {
      instance.apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        // Una voce non ha bisogno di piu' di questo, e ogni kilobyte in piu' e' un kilobyte da
        // cifrare, caricare e scaricare.
        setAudioChannels(1)
        setAudioSamplingRate(44_100)
        setAudioEncodingBitRate(64_000)
        setOutputFile(file.absolutePath)
        prepare()
        start()
      }
      recorder = instance
      target = file
      startedAt = System.currentTimeMillis()
      samples.clear()
      true
    }.getOrElse {
      runCatching { instance.release() }
      file.delete()
      false
    }
  }

  /** Da chiamare a intervalli regolari mentre si registra: e' cosi' che nasce la forma d'onda. */
  fun sample() {
    val current = recorder ?: return
    samples += runCatching { current.maxAmplitude }.getOrDefault(0)
  }

  /**
   * Chiude la registrazione e restituisce il file.
   *
   * `null` se non c'era niente o se e' durata troppo poco: una nota vocale di due decimi e' quasi
   * sempre un tocco per sbaglio, e mandarla e' peggio che non mandarla.
   */
  fun stop(): Recording? {
    val current = recorder ?: return null
    val file = target
    recorder = null
    target = null
    val durata = System.currentTimeMillis() - startedAt
    val chiuso = runCatching {
      current.stop()
      true
    }.getOrDefault(false)
    runCatching { current.release() }
    if (file == null || !chiuso || durata < MINIMA || !file.isFile || file.length() == 0L) {
      file?.delete()
      return null
    }
    return Recording(file, durata, compress(samples, columns = BARS))
  }

  /** Butta via quello che si stava registrando. */
  fun cancel() {
    val current = recorder ?: return
    recorder = null
    runCatching { current.stop() }
    runCatching { current.release() }
    target?.delete()
    target = null
  }

  /**
   * Toglie i file rimasti da una registrazione interrotta male.
   *
   * Se l'app muore mentre si registra, il pezzo di audio gia' scritto resta nella cache. E' audio
   * di chi usa il telefono, in chiaro: non deve sopravvivere alla sessione in cui e' nato.
   */
  fun discardLeftovers() {
    File(context.cacheDir, "voce").listFiles()?.forEach { it.delete() }
  }

  private companion object {
    /** Quante colonnine ha una forma d'onda: abbastanza da vedersi, poche da stare nella busta. */
    const val BARS = 48
    const val MINIMA = 700L

    /**
     * Da tanti campioni a poche colonnine, da 0 a 255.
     *
     * Si prende il **massimo** di ogni gruppo e non la media: una voce e' fatta di picchi corti fra
     * silenzi, e la media li appiattirebbe tutti alla stessa altezza.
     */
    fun compress(samples: List<Int>, columns: Int): ByteArray {
      if (samples.isEmpty()) return ByteArray(0)
      val picco = samples.max().coerceAtLeast(1)
      val quanti = minOf(columns, samples.size)
      val perColonna = samples.size.toDouble() / quanti
      return ByteArray(quanti) { colonna ->
        val da = (colonna * perColonna).toInt()
        val a = minOf(samples.size, ((colonna + 1) * perColonna).toInt().coerceAtLeast(da + 1))
        val massimo = samples.subList(da, a).max()
        (massimo * 255L / picco).toInt().coerceIn(0, 255).toByte()
      }
    }
  }
}
