package dev.pampa.codex.seal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MineralGeneratorTest {

  @Test
  fun `lo stesso seme produce lo stesso minerale`() {
    // E' la proprieta' su cui si regge tutto: due telefoni disegnano la stessa gemma senza
    // scambiarsi un pixel. Se questo test cade, i sigilli non combaciano piu' fra mittente e
    // destinatario, e nessuno dei due se ne accorgerebbe subito.
    for (seed in listOf(0L, 1L, -1L, 42L, Long.MAX_VALUE, Long.MIN_VALUE, 1_234_567_890L)) {
      assertEquals(MineralGenerator.generate(seed), MineralGenerator.generate(seed))
    }
  }

  @Test
  fun `l'avatar e' sempre un cristallo`() {
    for (seed in 0 until 200L) {
      assertEquals(MineralFamily.CRYSTAL, MineralGenerator.avatar(seed).family)
    }
  }

  @Test
  fun `la geometria resta dentro il riquadro`() {
    for (seed in 0 until 300L) {
      val mineral = MineralGenerator.generate(seed)
      val points = mineral.outline + mineral.facets.flatMap { it.points } + mineral.highlight
      for (point in points) {
        assertTrue("x fuori dal riquadro: ${point.x} (seme $seed)", point.x in -0.001f..1.001f)
        assertTrue("y fuori dal riquadro: ${point.y} (seme $seed)", point.y in -0.001f..1.001f)
      }
    }
  }

  @Test
  fun `ogni minerale ha un contorno e delle facce disegnabili`() {
    for (seed in 0 until 300L) {
      val mineral = MineralGenerator.generate(seed)
      assertTrue("contorno troppo corto", mineral.outline.size >= 6)
      assertTrue("nessuna faccia", mineral.facets.isNotEmpty())
      assertTrue("faccia degenere", mineral.facets.all { it.points.size >= 3 })
    }
  }

  @Test
  fun `semi diversi danno minerali diversi`() {
    val minerals = (0 until 200L).map { MineralGenerator.generate(it) }
    // Non si pretende che siano tutti unici (le tavolozze sono sette), ma la geometria deve
    // cambiare: un generatore che ignorasse il seme passerebbe tutti gli altri test.
    assertTrue(minerals.map { it.outline }.distinct().size > 190)
    assertTrue(minerals.map { it.palette.name }.distinct().size >= 5)
    assertTrue(minerals.any { it.family == MineralFamily.STONE })
    assertTrue(minerals.any { it.family == MineralFamily.CRYSTAL })
  }

  @Test
  fun `il seme di un nome ignora spazi e maiuscole`() {
    assertEquals(MineralGenerator.seedOf("Alessio"), MineralGenerator.seedOf("  alessio "))
    assertNotEquals(MineralGenerator.seedOf("Alessio"), MineralGenerator.seedOf("Alessia"))
  }

  @Test
  fun `il generatore e' SplitMix64, non qualcosa che gli somiglia`() {
    // I primi tre valori per il seme 0 sono quelli della definizione di riferimento
    // (0xE220A8397B1DCDAF, 0x6E789E6AA1B965F4, 0x06C45D188009454F). Sono qui perche' un giorno
    // qualcuno potrebbe "sistemare" uno shift: il codice continuerebbe a girare, e ogni sigillo
    // gia' inviato cambierebbe aspetto.
    val random = SeededRandom(0)
    assertEquals(-0x1DDF57C684E23251L, random.nextLong())
    assertEquals(0x6E789E6AA1B965F4L, random.nextLong())
    assertEquals(0x06C45D188009454FL, random.nextLong())
  }

  @Test
  fun `i valori casuali restano nei limiti dichiarati`() {
    val random = SeededRandom(12345)
    repeat(1000) {
      val f = random.nextFloat()
      assertTrue("$f fuori da [0,1)", f >= 0f && f < 1f)
      val ranged = random.nextFloat(-2f, 5f)
      assertTrue("$ranged fuori intervallo", ranged >= -2f && ranged < 5f)
      val bounded = random.nextInt(7)
      assertTrue("$bounded fuori da [0,7)", bounded in 0..6)
      val inclusive = random.nextInt(3, 6)
      assertTrue("$inclusive fuori da [3,6]", inclusive in 3..6)
    }
  }
}
