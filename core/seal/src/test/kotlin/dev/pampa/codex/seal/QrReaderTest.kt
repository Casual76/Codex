package dev.pampa.codex.seal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Il giro completo del pairing, in memoria: si disegna un codice e lo si rilegge come farebbe la
 * fotocamera.
 *
 * Quello che questo test copre e' la parte che, sbagliata, non si vede: il **passo delle righe**.
 * Una fotocamera consegna righe allineate a otto o a sedici byte, quindi quasi sempre piu' lunghe
 * dell'immagine, e chi passa la larghezza al posto del passo ottiene un'immagine inclinata che non
 * si decodifica mai e non somiglia a un difetto di codice: somiglia a "il lettore non va".
 */
class QrReaderTest {

  private val payload = "CDX1:" + "0123456789ABCDEFGHJKMNPQRSTVWXYZ".repeat(8)

  /**
   * Il codice come lo vedrebbe l'obiettivo: moduli ingranditi, bordo chiaro, e righe eventualmente
   * piu' lunghe della larghezza utile.
   */
  private fun frame(
    text: String,
    scale: Int = 6,
    quiet: Int = 4,
    padding: Int = 0,
  ): Triple<ByteArray, Int, Int> {
    val matrix = QrMatrix.of(text)
    val side = (matrix.size + quiet * 2) * scale
    val stride = side + padding
    val buffer = ByteArray(stride * side) { 0 }
    for (y in 0 until side) {
      for (x in 0 until side) {
        val dark = matrix[x / scale - quiet, y / scale - quiet]
        buffer[y * stride + x] = if (dark) 0 else 255.toByte()
      }
      // La zona di riempimento in fondo alla riga: la fotocamera ci lascia quello che capita.
      for (x in side until stride) buffer[y * stride + x] = 90
    }
    return Triple(buffer, side, stride)
  }

  @Test
  fun `un codice disegnato da Codex si rilegge`() {
    val (buffer, side, stride) = frame(payload)
    assertEquals(payload, QrReader.decode(buffer, side, side, stride))
  }

  @Test
  fun `si rilegge anche con le righe allineate`() {
    // 1280 di larghezza utile con passo 1344 e' una consegna del tutto ordinaria.
    for (padding in listOf(0, 8, 64)) {
      val (buffer, side, stride) = frame(payload, padding = padding)
      assertEquals("passo $padding", payload, QrReader.decode(buffer, side, side, stride))
    }
  }

  @Test
  fun `un fotogramma senza codice non inventa niente`() {
    val vuoto = ByteArray(320 * 240) { 200.toByte() }
    assertNull(QrReader.decode(vuoto, 320, 240))
  }

  @Test
  fun `misure impossibili non fanno esplodere niente`() {
    val piccolo = ByteArray(100)
    assertNull(QrReader.decode(piccolo, 0, 10))
    assertNull(QrReader.decode(piccolo, 10, 0))
    // Passo minore della larghezza: dati incoerenti, non un'immagine.
    assertNull(QrReader.decode(piccolo, 10, 10, 5))
    // Buffer piu' corto di quello che le misure promettono.
    assertNull(QrReader.decode(piccolo, 100, 100))
  }

  @Test
  fun `due letture di fila non si disturbano`() {
    val primo = frame("CDX1:AAAAAAAA")
    val secondo = frame("CDX1:BBBBBBBB")
    assertEquals("CDX1:AAAAAAAA", QrReader.decode(primo.first, primo.second, primo.second, primo.third))
    assertEquals("CDX1:BBBBBBBB", QrReader.decode(secondo.first, secondo.second, secondo.second, secondo.third))
    assertEquals("CDX1:AAAAAAAA", QrReader.decode(primo.first, primo.second, primo.second, primo.third))
  }
}
