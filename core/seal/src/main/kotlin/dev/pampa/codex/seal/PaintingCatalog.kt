package dev.pampa.codex.seal

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Un dipinto della raccolta inclusa nell'APK.
 *
 * I metadati non sono decorazione: [artist], [museum] e [license] finiscono nei crediti dell'app,
 * ed e' la condizione con cui queste riproduzioni si possono usare.
 */
@Serializable
data class Painting(
  val id: String,
  val file: String,
  /** Il titolo in italiano, quello che l'app mostra. */
  val title: String,
  val originalTitle: String = "",
  val artist: String = "",
  val year: String = "",
  val museum: String = "",
  val license: String = "",
  val source: String = "",
  val width: Int = 0,
  val height: Int = 0,
) {
  /**
   * Quanto e' largo rispetto a quanto e' alto.
   *
   * Serve alla copertina del sigillo: la raccolta ha rotoli cinesi lunghi il quadruplo della loro
   * altezza e tele verticali, e una cornice sempre della stessa forma butta via meta' di quelle.
   * Zero quando le misure mancano, e chi lo usa ripiega sulla forma standard.
   */
  val aspectRatio: Float get() = if (width > 0 && height > 0) width.toFloat() / height else 0f
}

/**
 * La raccolta di dipinti dentro l'app.
 *
 * Trentasei opere di pubblico dominio, gia' ridotte a 1280 px e in WebP: cinque megabyte in tutto,
 * disponibili senza rete e senza chiedere l'accesso alle foto di nessuno. Il file `paintings.json`
 * lo scrive `tools/assets/fetch_paintings.py`, che e' anche l'unico posto dove sta scritto da dove
 * vengono.
 */
class PaintingCatalog(private val assets: AssetManager) {

  private val json = Json { ignoreUnknownKeys = true }

  val paintings: List<Painting> by lazy {
    runCatching {
      assets.open("$DIRECTORY/paintings.json").use { stream ->
        json.decodeFromString<List<Painting>>(stream.readBytes().decodeToString())
      }
    }.getOrDefault(emptyList())
  }

  fun byId(id: String?): Painting? = id?.let { wanted -> paintings.firstOrNull { it.id == wanted } }

  /** Il dipinto di un sigillo: sempre lo stesso per lo stesso seme, su ogni telefono. */
  fun pick(seed: Long): Painting? {
    if (paintings.isEmpty()) return null
    return paintings[Math.floorMod(seed, paintings.size.toLong()).toInt()]
  }

  /**
   * Carica il dipinto.
   *
   * `ARGB_8888` e' obbligatorio, non una preferenza: la steganografia scrive nei bit bassi dei
   * canali, e un formato compresso come `RGB_565` quei bit non li ha proprio.
   */
  fun loadBitmap(painting: Painting, sampleSize: Int = 1): Bitmap? = runCatching {
    assets.open("$DIRECTORY/${painting.file}").use { stream ->
      BitmapFactory.decodeStream(
        stream,
        null,
        BitmapFactory.Options().apply {
          inPreferredConfig = Bitmap.Config.ARGB_8888
          inSampleSize = sampleSize.coerceAtLeast(1)
          inMutable = true
        },
      )
    }
  }.getOrNull()

  private companion object {
    const val DIRECTORY = "paintings"
  }
}
