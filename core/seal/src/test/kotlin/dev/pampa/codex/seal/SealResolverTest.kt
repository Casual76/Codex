package dev.pampa.codex.seal

import dev.pampa.codex.model.SealChoice
import dev.pampa.codex.model.Technique
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SealResolverTest {

  @Test
  fun `la sorpresa e' deterministica e copre le tre tecniche`() {
    assertEquals(Technique.RUNE, SealResolver.surprise(0L))
    assertEquals(Technique.ROCK, SealResolver.surprise(1L))
    assertEquals(Technique.PAINTING, SealResolver.surprise(2L))
    assertEquals(Technique.RUNE, SealResolver.surprise(3L))
    // Un seme negativo non deve mai produrre un indice negativo.
    assertEquals(Technique.PAINTING, SealResolver.surprise(-1L))
  }

  @Test
  fun `una scelta esplicita vince sul seme`() {
    val spec = SealResolver.resolve(seed = 0L, choice = SealChoice(technique = Technique.PAINTING, paintingId = "p1"))
    assertEquals(Technique.PAINTING, spec.technique)
    assertEquals("p1", spec.paintingId)
  }

  @Test
  fun `il dipinto si scarta se la tecnica non e' Quadro`() {
    val spec = SealResolver.resolve(seed = 7L, choice = SealChoice(technique = Technique.ROCK, paintingId = "p1"))
    assertNull(spec.paintingId)
  }
}
