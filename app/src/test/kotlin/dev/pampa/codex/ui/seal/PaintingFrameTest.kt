package dev.pampa.codex.ui.seal

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La cornice del quadro ha una regola, e una regola si prova.
 *
 * La forma la decide il dipinto, ma l'**area** resta la stessa: e' cio' che tiene la conversazione
 * ordinata quando un sigillo e' largo e quello sotto e' alto. Senza questo test la prima modifica
 * distratta ai limiti farebbe due sigilli di peso diverso, e a occhio non si nota finche' non se ne
 * vedono cinque di fila.
 */
class PaintingFrameTest {

  private fun area(ratio: Float): Float {
    val frame = paintingFrame(ratio)
    return frame.width.value * frame.height.value
  }

  @Test
  fun `senza misure ripiega sulla cornice di sempre`() {
    val frame = paintingFrame(0f)
    assertEquals(224f, frame.width.value, 0.5f)
    assertEquals(152f, frame.height.value, 0.5f)
  }

  @Test
  fun `l'area non cambia con la forma`() {
    for (ratio in listOf(0.8f, 1f, 1.2f, DefaultPaintingRatio, 1.6f, 1.85f)) {
      assertEquals("area cambiata per il rapporto $ratio", PaintingArea, area(ratio), 1f)
    }
  }

  @Test
  fun `la cornice segue davvero il dipinto`() {
    val ritratto = paintingFrame(0.8f)
    val paesaggio = paintingFrame(1.8f)
    assertTrue("un ritratto deve essere piu' alto che largo", ritratto.height > ritratto.width)
    assertTrue("un paesaggio deve essere piu' largo che alto", paesaggio.width > paesaggio.height)
  }

  @Test
  fun `gli estremi si fermano ai limiti`() {
    // Un rotolo cinese e' quattro volte piu' largo che alto: senza limite la copertina diventa una
    // fessura, e il taglio centrato non basterebbe a salvarla.
    val rotolo = paintingFrame(3.9f)
    val colonna = paintingFrame(0.35f)
    val rapportoRotolo = rotolo.width.value / rotolo.height.value
    val rapportoColonna = colonna.width.value / colonna.height.value
    assertTrue("il rotolo non e' stato limitato: $rapportoRotolo", rapportoRotolo <= 1.9f + 0.01f)
    assertTrue("la colonna non e' stata limitata: $rapportoColonna", rapportoColonna >= 0.72f - 0.01f)
    assertEquals(PaintingArea, area(3.9f), 1f)
    assertEquals(PaintingArea, area(0.35f), 1f)
  }

  @Test
  fun `nessuna cornice diventa minuscola o enorme`() {
    for (ratio in listOf(0.2f, 0.72f, 1f, 1.9f, 6f)) {
      val frame = paintingFrame(ratio)
      assertTrue("larghezza fuori scala per $ratio: ${frame.width}", frame.width.value in 140f..280f)
      assertTrue("altezza fuori scala per $ratio: ${frame.height}", frame.height.value in 120f..260f)
    }
  }

  @Test
  fun `rapporti vicini danno cornici vicine`() {
    // Nessuno scalino: due dipinti quasi uguali non devono avere copertine visibilmente diverse.
    val a = paintingFrame(1.40f)
    val b = paintingFrame(1.42f)
    assertTrue(abs(a.width.value - b.width.value) < 3f)
    assertTrue(abs(a.height.value - b.height.value) < 3f)
  }
}
