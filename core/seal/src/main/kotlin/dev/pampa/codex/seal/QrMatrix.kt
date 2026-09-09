package dev.pampa.codex.seal

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Un QR ridotto all'osso: quanti moduli per lato, e quali sono neri.
 *
 * Qui non si disegna niente. Il disegno e' dell'app, che ha i colori del tema e sa fare gli angoli
 * continui; questo modulo sa solo **dove vanno i quadratini**, e questo si puo' provare senza un
 * emulatore — un QR che non si decodifica e' un difetto che si scopre con una fotocamera in mano
 * il giorno del rilascio, oppure con un test il giorno in cui lo si scrive.
 */
class QrMatrix(val size: Int, private val dark: BooleanArray) {

  operator fun get(x: Int, y: Int): Boolean =
    x in 0 until size && y in 0 until size && dark[y * size + x]

  /**
   * Se il modulo fa parte di uno dei tre quadrati agli angoli.
   *
   * Servono all'app per disegnarli diversi dagli altri: sono l'unica parte del codice che una
   * persona riconosce come "un QR", e arrotondarli come tutto il resto lo rende un tappeto.
   */
  fun isFinder(x: Int, y: Int): Boolean {
    val edge = size - FINDER
    return (x < FINDER && y < FINDER) || (x >= edge && y < FINDER) || (x < FINDER && y >= edge)
  }

  companion object {
    /** Il lato dei quadrati d'angolo, in moduli: sette, e non e' negoziabile, lo dice lo standard. */
    const val FINDER = 7

    /**
     * Il codice per un testo.
     *
     * Correzione d'errore **alta** di proposito: costa una manciata di moduli e in cambio il
     * codice regge un dito sopra, un riflesso e -- soprattutto -- il minerale che l'app gli
     * appoggia in mezzo, che senza questo livello lo renderebbe illeggibile.
     */
    fun of(text: String, margin: Int = 0): QrMatrix {
      val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
        EncodeHintType.CHARACTER_SET to "ISO-8859-1",
        EncodeHintType.MARGIN to margin,
      )
      // Le dimensioni richieste sono un minimo: zxing sceglie la versione e poi scala. Chiedendo
      // 1x1 si ottiene la matrice piu' piccola che contiene il testo, che e' quello che serve --
      // la scala la decide lo schermo, non questo modulo.
      val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 1, 1, hints)
      val size = matrix.width
      val dark = BooleanArray(size * size)
      for (y in 0 until size) {
        for (x in 0 until size) {
          dark[y * size + x] = matrix.get(x, y)
        }
      }
      return QrMatrix(size, dark)
    }
  }
}
