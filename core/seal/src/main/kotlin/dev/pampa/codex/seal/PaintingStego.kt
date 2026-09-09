package dev.pampa.codex.seal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.InputStream
import java.io.OutputStream

/**
 * Mettere un messaggio dentro un dipinto, e ritirarlo fuori.
 *
 * E' l'unica tecnica che esce da Codex: un PNG che si manda su qualsiasi canale e che chi ha
 * l'app riapre. Le altre due (rune e roccia) vivono solo dentro una chat.
 *
 * **L'ordine dei pixel qui e' pubblico** ([EXPORT_SEED]), e deve esserlo: chi riceve l'immagine non
 * ha modo di sapere quale seme avesse chi l'ha creata. Non toglie niente, perche' cio' che sta
 * dentro e' gia' cifrato — nascondere non e' proteggere, e la protezione l'ha gia' fatta la busta.
 *
 * **Il formato deve restare senza perdita.** Un JPEG ricalcola i colori e i bit bassi spariscono
 * insieme al messaggio: per questo si scrive PNG, e per questo l'app dice di mandarlo come file e
 * non come foto.
 */
class PaintingStego(private val catalog: PaintingCatalog) {

  class TooLarge(val capacity: Int, val required: Int) :
    Exception("il messaggio non entra in questo dipinto: $required byte su $capacity")

  /** Il dipinto con il messaggio dentro, pronto da scrivere in PNG. */
  fun embed(painting: Painting, payload: ByteArray): Bitmap {
    val bitmap = catalog.loadBitmap(painting)
      ?: throw IllegalStateException("dipinto non caricabile: ${painting.id}")
    val width = bitmap.width
    val height = bitmap.height
    val capacity = LsbStego.capacityBytes(width, height)
    if (payload.size > capacity) throw TooLarge(capacity, payload.size)

    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    LsbStego.embed(pixels, width, height, payload, EXPORT_SEED)
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap
  }

  /** Scrive il PNG. PNG e non JPEG: vedi la nota in cima. */
  fun writePng(bitmap: Bitmap, destination: OutputStream): Boolean =
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, destination)

  /** Legge un'immagine e ne estrae il messaggio, o `null` se non ne contiene uno. */
  fun extract(source: InputStream): ByteArray? {
    val bitmap = BitmapFactory.decodeStream(
      source,
      null,
      BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 },
    ) ?: return null
    return extract(bitmap)
  }

  fun extract(bitmap: Bitmap): ByteArray? {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return null
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    return LsbStego.extract(pixels, width, height, EXPORT_SEED)
  }

  /** Quanti byte entrano in questo dipinto. */
  fun capacityOf(painting: Painting): Int =
    LsbStego.capacityBytes(painting.width, painting.height)

  companion object {
    /**
     * Il seme dell'ordine dei pixel per le immagini esportate.
     *
     * Fisso e conosciuto: chi apre l'immagine non ha altro da cui ricavarlo. Cambiarlo rende
     * illeggibile tutto quello che e' gia' stato mandato in giro, quindi non si cambia.
     */
    const val EXPORT_SEED: Long = 0x43_44_58_33 // "CDX3"
  }
}
