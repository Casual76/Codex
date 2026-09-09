package dev.pampa.codex.seal

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il QR viene riletto da chi lo legge davvero.
 *
 * Non si prova che la matrice "sembra giusta": si stampa come la stamperebbe lo schermo -- ingrandita,
 * con il suo bordo bianco -- e si rida' in pasto al lettore di zxing. E' l'unico modo di sapere prima
 * del rilascio che una fotocamera puntata su quel disegno ci trova dentro una scheda.
 */
class QrMatrixTest {

  /** Come il codice finisce su uno schermo: moduli ingranditi e un bordo chiaro attorno. */
  private class Stampa(matrix: QrMatrix, val scale: Int = 4, val quiet: Int = 4) : LuminanceSource(
    (matrix.size + quiet * 2) * scale,
    (matrix.size + quiet * 2) * scale,
  ) {
    private val pixels = ByteArray(width * height).also { buffer ->
      for (y in 0 until height) {
        for (x in 0 until width) {
          val moduleX = x / scale - quiet
          val moduleY = y / scale - quiet
          buffer[y * width + x] = if (matrix[moduleX, moduleY]) 0 else 255.toByte()
        }
      }
    }

    override fun getRow(y: Int, row: ByteArray?): ByteArray {
      val out = if (row != null && row.size >= width) row else ByteArray(width)
      System.arraycopy(pixels, y * width, out, 0, width)
      return out
    }

    override fun getMatrix(): ByteArray = pixels
  }

  private fun rileggi(text: String): String {
    val source = Stampa(QrMatrix.of(text))
    val result = QRCodeReader().decode(
      BinaryBitmap(HybridBinarizer(source)),
      mapOf(DecodeHintType.TRY_HARDER to true),
    )
    return result.text
  }

  @Test
  fun `un codice corto si rilegge`() {
    assertEquals("CDX1:ABCDEFGH", rileggi("CDX1:ABCDEFGH"))
  }

  @Test
  fun `una scheda intera ci sta e si rilegge`() {
    // Una scheda firmata sono circa trecento caratteri in base32: e' il caso vero, ed e' quello
    // che deve stare in un quadrato che si inquadra da mezzo metro.
    val payload = "CDX1:" + "0123456789ABCDEFGHJKMNPQRSTVWXYZ".repeat(10)
    assertEquals(payload, rileggi(payload))
    assertTrue("il codice e' troppo fitto per uno schermo", QrMatrix.of(payload).size <= 81)
  }

  @Test
  fun `la matrice non ha bordo e i tre angoli sono al loro posto`() {
    val matrix = QrMatrix.of("CDX1:ABCDEFGH")
    // Senza bordo la disegna l'app, che sa di quale colore e' il suo foglio.
    assertTrue("l'angolo in alto a sinistra deve essere pieno", matrix[0, 0])
    assertTrue(matrix.isFinder(0, 0))
    assertTrue(matrix.isFinder(matrix.size - 1, 0))
    assertTrue(matrix.isFinder(0, matrix.size - 1))
    assertTrue("il centro non e' un angolo", !matrix.isFinder(matrix.size / 2, matrix.size / 2))
  }

  @Test
  fun `due schede diverse danno due codici diversi`() {
    val uno = QrMatrix.of("CDX1:AAAAAAAA")
    val due = QrMatrix.of("CDX1:BBBBBBBB")
    val differenze = (0 until uno.size).sumOf { y ->
      (0 until uno.size).count { x -> uno[x, y] != due[x, y] }
    }
    assertNotEquals(0, differenze)
  }

  @Test
  fun `fuori dalla matrice non c'e' niente`() {
    val matrix = QrMatrix.of("CDX1:ABCDEFGH")
    assertTrue(!matrix[-1, 0] && !matrix[0, -1] && !matrix[matrix.size, 0])
  }
}
