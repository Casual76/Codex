package dev.pampa.codex.crypto

import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * La codifica con cui una scheda contatto esce da un telefono.
 *
 * Conta il giro completo: quello che entra deve tornare identico dopo essere passato per un QR,
 * per un messaggio su un'altra app, o per le mani di qualcuno che lo ricopia.
 */
class Base32Test {

  @Test
  fun `qualsiasi lunghezza torna indietro identica`() {
    val random = Random(7)
    for (length in 0..80) {
      val data = random.nextBytes(length)
      val decoded = Base32.decode(Base32.encode(data))
      assertNotNull(decoded, "lunghezza $length non decodificata")
      assertArrayEquals(data, decoded, "lunghezza $length cambiata nel giro")
    }
  }

  @Test
  fun `una scheda intera sopravvive al giro`() {
    val identity = CodexIdentity.generate()
    val card = ContactCard.of(identity, name = "Alessio", avatarSeed = 99).encode()
    assertArrayEquals(card, Base32.decode(Base32.encode(card)))
  }

  @Test
  fun `le lettere confondibili si ricompongono`() {
    val data = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    val text = Base32.encode(data)
    // Chi ricopia a mano scrive O per zero, I o L per uno, U per V: nessuna delle tre deve
    // trasformare una scheda valida in una illeggibile.
    val ricopiato = text.replace('0', 'O').replace('1', 'I').replace('V', 'U').lowercase()
    assertArrayEquals(data, Base32.decode(ricopiato))
  }

  @Test
  fun `spazi trattini e a capo non contano`() {
    val data = Random(11).nextBytes(40)
    val text = Base32.encode(data)
    assertArrayEquals(data, Base32.decode(Base32.grouped(text)))
    assertArrayEquals(data, Base32.decode(text.chunked(4).joinToString("-")))
    assertArrayEquals(data, Base32.decode("  " + text + "\n\t"))
  }

  @Test
  fun `un carattere che non esiste ferma tutto`() {
    val text = Base32.encode(byteArrayOf(1, 2, 3))
    assertNull(Base32.decode(text + "!"))
    assertNull(Base32.decode("€$text"))
  }

  @Test
  fun `il testo raggruppato resta leggibile a occhio`() {
    val text = Base32.encode(Random(3).nextBytes(50))
    val grouped = Base32.grouped(text)
    assertTrue(grouped.contains(' '), "senza gruppi non si ritrova il punto")
    assertTrue(grouped.contains('\n'), "senza righe non ci sta sullo schermo")
    assertEquals(text, grouped.filter { !it.isWhitespace() })
  }
}
