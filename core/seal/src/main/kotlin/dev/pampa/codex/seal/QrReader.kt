package dev.pampa.codex.seal

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

/**
 * Leggere un QR da un fotogramma della fotocamera.
 *
 * **Perche' non ML Kit.** Il riconoscitore di Google e' piu' robusto, e costa dieci megabyte di
 * modello dentro l'APK -- su una release che ne pesa venti. Qui il codice da leggere non e' una
 * etichetta stropicciata in penombra: e' un quadrato disegnato da Codex, su uno schermo acceso, a
 * venti centimetri. Per quel caso basta zxing, che nell'app c'e' gia' perche' e' lo stesso che
 * quel quadrato lo disegna, e chi scarica l'app non paga niente per averlo anche in lettura.
 *
 * Lavora sul **piano della luminanza** e basta: di un fotogramma YUV il colore non serve, e
 * ignorarlo evita la conversione piu' costosa della catena.
 */
object QrReader {

  private val reader = QRCodeReader()

  private val hints = mapOf<DecodeHintType, Any>(
    // Il fotogramma e' pieno di roba che non e' il codice: vale la pena insistere.
    DecodeHintType.TRY_HARDER to true,
  )

  /**
   * Il testo del primo QR trovato nel fotogramma, oppure `null`.
   *
   * `rowStride` non e' un dettaglio da ignorare: la fotocamera consegna righe **allineate**, quindi
   * spesso piu' lunghe della larghezza dell'immagine. Passare la larghezza al posto del passo
   * produce un'immagine inclinata di qualche pixel a ogni riga, che a occhio nudo sembrerebbe
   * quasi giusta e non si decodifica mai.
   */
  @Synchronized
  fun decode(
    luminance: ByteArray,
    width: Int,
    height: Int,
    rowStride: Int = width,
  ): String? {
    if (width <= 0 || height <= 0 || rowStride < width) return null
    if (luminance.size < rowStride * height) return null
    val source = PlanarYUVLuminanceSource(
      luminance,
      rowStride,
      height,
      0,
      0,
      width,
      height,
      false,
    )
    return try {
      reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text
    } catch (error: NotFoundException) {
      // Nessun codice in questo fotogramma: e' il caso normale, trenta volte al secondo.
      null
    } catch (error: Exception) {
      // Un codice rovinato o mezzo fuori: il fotogramma dopo andra' meglio.
      null
    } finally {
      reader.reset()
    }
  }
}
