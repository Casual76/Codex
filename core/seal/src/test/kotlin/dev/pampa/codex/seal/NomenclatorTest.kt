package dev.pampa.codex.seal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La tavola ricostruita deve corrispondere a quella nella foto.
 *
 * Non e' pedanteria: la prima Codex aveva preso da quella tavola una corrispondenza numerica che ne
 * buttava via il funzionamento, e questi test sono il modo di non rifarlo per distrazione. Ogni
 * numero qui sotto e' stato **contato nella foto**.
 */
class NomenclatorTest {

  @Test
  fun `ogni voce della tavola ha un segno diverso`() {
    val all = Nomenclator.words + Nomenclator.doubles + Nomenclator.syllables +
      Nomenclator.letters + Nomenclator.nulls
    val symbols = all.map { it.symbol }
    assertEquals("due voci con lo stesso segno", symbols.size, symbols.toSet().size)
    assertEquals("la tavola non e' completa", symbols.size, Nomenclator.size)
  }

  @Test
  fun `la tavola ha le cinque parti del cifrario, nelle misure della foto`() {
    assertEquals("le parole del nomenclatore", 10, Nomenclator.words.size)
    // bb cc dd ff gg ll mm nn pp rr ss tt uu
    assertEquals("le doppie", 13, Nomenclator.doubles.size)
    // Quindici consonanti per cinque vocali, con la riga della q scritta qua que qui quo quu.
    assertEquals("le sillabe", 75, Nomenclator.syllables.size)
    // Ventidue lettere piu' gli omofoni che si contano sotto a, d, i, n, q.
    assertEquals("l'alfabeto con gli omofoni", 22 + 3 + 2 + 2 + 2 + 2, Nomenclator.letters.size)
    assertEquals("le nulle", 9, Nomenclator.nulls.size)
  }

  @Test
  fun `l'alfabeto e' quello della tavola, senza j k u w`() {
    val plain = Nomenclator.letters.map { it.plain }.toSet()
    assertEquals("abcdefghilmnopqrstvxyz".map { it.toString() }.toSet(), plain)
  }

  @Test
  fun `la riga della q e' scritta con la u dietro`() {
    val q = Nomenclator.syllables.filter { it.plain.startsWith("qu") }.map { it.plain }
    assertEquals(listOf("qua", "que", "qui", "quo", "quu"), q)
  }

  @Test
  fun `le lettere piu' frequenti hanno piu' di un segno`() {
    for (letter in listOf("a", "i", "n")) {
      val forLetter = Nomenclator.letters.filter { it.plain == letter }
      assertTrue("la lettera $letter non ha omofoni", forLetter.size >= 2)
    }
  }

  @Test
  fun `cifrare e' deterministico`() {
    val first = Nomenclator.encipher("Il papa scrive a Firenze", seed = 7L)
    val second = Nomenclator.encipher("Il papa scrive a Firenze", seed = 7L)
    assertEquals(Nomenclator.cipherText(first), Nomenclator.cipherText(second))
  }

  @Test
  fun `semi diversi danno cifrati diversi`() {
    // Gli omofoni e le nulle dipendono dal seme: senza questa differenza non servirebbero a niente.
    val first = Nomenclator.cipherText(Nomenclator.encipher("il papa scrive a firenze", seed = 1L))
    val second = Nomenclator.cipherText(Nomenclator.encipher("il papa scrive a firenze", seed = 2L))
    assertNotEquals(first, second)
  }

  @Test
  fun `le parole del nomenclatore vincono sulle sillabe`() {
    val tokens = Nomenclator.encipher("papa", seed = 3L)
    val meaningful = tokens.filter { it.kind != Nomenclator.Kind.NULL }
    assertEquals(1, meaningful.size)
    assertEquals(Nomenclator.Kind.WORD, meaningful.first().kind)
    assertEquals("papa", meaningful.first().plain)
  }

  @Test
  fun `anche una parola di tre pezzi vince, se sta nella tavola`() {
    val tokens = Nomenclator.encipher("re di francia", seed = 11L)
      .filter { it.kind != Nomenclator.Kind.NULL }
    assertEquals(1, tokens.size)
    assertEquals("re di francia", tokens.first().plain)
  }

  @Test
  fun `le doppie e le sillabe accorciano il testo`() {
    val tokens = Nomenclator.encipher("bocca", seed = 4L)
      .filter { it.kind != Nomenclator.Kind.NULL }
    // bo + cc + a: tre segni per cinque lettere.
    assertEquals(listOf("bo", "cc", "a"), tokens.map { it.plain })
  }

  @Test
  fun `la u resta una vocale e da sola prende il segno della v`() {
    // Nella tavola la `u` non ha una riga sua, ma c'e' nelle sillabe e nella doppia `uu`.
    val sillaba = Nomenclator.encipher("tu", seed = 6L).filter { it.kind != Nomenclator.Kind.NULL }
    assertEquals(listOf("tu"), sillaba.map { it.plain })

    val doppia = Nomenclator.encipher("uu", seed = 6L).filter { it.kind != Nomenclator.Kind.NULL }
    assertEquals(listOf("uu"), doppia.map { it.plain })

    // Una `u` isolata: nessuna sillaba la copre, e finisce sul segno della `v`.
    val sola = Nomenclator.encipher("au", seed = 6L).filter { it.kind != Nomenclator.Kind.NULL }
    assertEquals(2, sola.size)
    val segniDellaV = Nomenclator.letters.filter { it.plain == "v" }.map { it.symbol }
    assertTrue("la u isolata non usa il segno della v", sola.last().symbol in segniDellaV)
  }

  @Test
  fun `il testo si riduce a quello che la tavola conosce`() {
    assertEquals("iacopo di cappa", Nomenclator.fold("  Jàcopo   di  Kappa "))
    assertEquals("vestminster", Nomenclator.fold("Westminster"))
    // La x e la y nella tavola ci sono: restano.
    assertEquals("xanto", Nomenclator.fold("Xanto"))
    assertEquals("yole", Nomenclator.fold("Yole"))
    assertEquals("citta", Nomenclator.fold("Città"))
  }

  @Test
  fun `quello che la tavola non conosce passa in chiaro`() {
    val tokens = Nomenclator.encipher("a 1477", seed = 5L)
    assertTrue("le cifre sono sparite", tokens.any { it.symbol == "1" })
  }

  @Test
  fun `le nulle non finiscono mai a fine parola`() {
    // Una nulla appiccicata prima di uno spazio si riconosce a occhio, e una nulla riconoscibile
    // non serve a niente.
    for (seed in 0 until 200L) {
      val tokens = Nomenclator.encipher("il papa scrive domani a firenze", seed)
      for (index in 0 until tokens.size - 1) {
        if (tokens[index].kind == Nomenclator.Kind.NULL) {
          assertNotEquals(
            "nulla prima di uno spazio (seme $seed)",
            Nomenclator.Kind.SPACE,
            tokens[index + 1].kind,
          )
        }
      }
      assertNotEquals(Nomenclator.Kind.NULL, tokens.last().kind)
    }
  }
}
