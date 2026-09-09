package dev.pampa.codex.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TechniqueTest {

  @Test
  fun `l'ordine delle tecniche e' quello che Sorpresa usa`() {
    // Cambiare quest'ordine cambia l'aspetto di ogni messaggio gia' inviato: il test lo blocca.
    assertEquals(listOf(Technique.RUNE, Technique.ROCK, Technique.PAINTING), Technique.entries.toList())
  }
}
