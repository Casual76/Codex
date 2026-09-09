package dev.pampa.codex.seal

import java.util.Random
import kotlin.math.abs
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LsbStegoTest {

  private fun image(width: Int, height: Int, seed: Long = 1): IntArray {
    val random = Random(seed)
    return IntArray(width * height) { (0xFF shl 24) or random.nextInt(0xFFFFFF) }
  }

  @Test
  fun `quello che entra e' quello che esce`() {
    val width = 64
    val height = 48
    val pixels = image(width, height)
    val payload = ByteArray(500) { (it * 7).toByte() }

    assertTrue(LsbStego.embed(pixels, width, height, payload, seed = 99))
    assertArrayEquals(payload, LsbStego.extract(pixels, width, height, seed = 99))
  }

  @Test
  fun `un messaggio vuoto e' comunque un messaggio`() {
    val pixels = image(32, 32)
    assertTrue(LsbStego.embed(pixels, 32, 32, ByteArray(0), seed = 3))
    assertArrayEquals(ByteArray(0), LsbStego.extract(pixels, 32, 32, seed = 3))
  }

  @Test
  fun `con il seme sbagliato non si legge niente`() {
    val pixels = image(64, 64)
    LsbStego.embed(pixels, 64, 64, "segreto".toByteArray(), seed = 1)
    // Non e' una difesa (il contenuto e' gia' cifrato): e' la prova che l'ordine dipende davvero
    // dal seme, e quindi che due messaggi non occupano gli stessi pixel nello stesso ordine.
    assertNull(LsbStego.extract(pixels, 64, 64, seed = 2))
  }

  @Test
  fun `un'immagine qualsiasi non contiene un messaggio`() {
    val pixels = image(64, 64, seed = 77)
    assertNull(LsbStego.extract(pixels, 64, 64, seed = 5))
    assertFalse(LsbStego.contains(pixels, 64, 64, seed = 5))
  }

  @Test
  fun `un pixel rovinato fa fallire il controllo invece di restituire spazzatura`() {
    val pixels = image(64, 64)
    LsbStego.embed(pixels, 64, 64, ByteArray(200) { 42 }, seed = 11)
    assertNotNull(LsbStego.extract(pixels, 64, 64, seed = 11))
    pixels[1234] = pixels[1234] xor 0x010101
    assertNull(LsbStego.extract(pixels, 64, 64, seed = 11))
  }

  @Test
  fun `la capienza e' quella dichiarata, e oltre non si va`() {
    val width = 40
    val height = 40
    val capacity = LsbStego.capacityBytes(width, height)
    assertEquals((40 * 40 * 3) / 8 - 12, capacity)

    val pixels = image(width, height)
    assertTrue(LsbStego.embed(pixels, width, height, ByteArray(capacity), seed = 8))
    assertFalse(LsbStego.embed(pixels, width, height, ByteArray(capacity + 1), seed = 8))
  }

  @Test
  fun `un dipinto della raccolta regge un messaggio lungo`() {
    assertTrue(LsbStego.capacityBytes(1280, 1000) > 450_000)
  }

  @Test
  fun `l'immagine cambia solo nei bit bassi`() {
    val width = 32
    val height = 32
    val original = image(width, height)
    val pixels = original.copyOf()
    LsbStego.embed(pixels, width, height, ByteArray(60) { 0xFF.toByte() }, seed = 4)
    for (index in pixels.indices) {
      for (shift in listOf(0, 8, 16)) {
        val before = (original[index] ushr shift) and 0xFF
        val after = (pixels[index] ushr shift) and 0xFF
        assertTrue("canale cambiato troppo: $before verso $after", abs(before - after) <= 1)
      }
      assertEquals("l'alpha non deve cambiare", original[index] ushr 24, pixels[index] ushr 24)
    }
  }

  @Test
  fun `ogni posizione viene usata una volta sola`() {
    // Se il passo non fosse primo rispetto al numero di posizioni, la scrittura tornerebbe sui
    // propri passi e i byte gia' scritti verrebbero sovrascritti a meta' messaggio.
    val width = 37
    val height = 21
    val pixels = image(width, height)
    val payload = ByteArray(LsbStego.capacityBytes(width, height)) { (it % 251).toByte() }
    assertTrue(LsbStego.embed(pixels, width, height, payload, seed = -12345))
    assertArrayEquals(payload, LsbStego.extract(pixels, width, height, seed = -12345))
  }

  @Test
  fun `semi vicini danno ordini diversi`() {
    // La prima versione della passeggiata cercava il passo contando da uno: con 12288 posizioni
    // tutti i semi finivano sullo stesso passo, e due messaggi diversi si sovrascrivevano
    // perfettamente. Questo test tiene il difetto lontano.
    val width = 64
    val height = 64
    val first = image(width, height)
    LsbStego.embed(first, width, height, ByteArray(64) { 1 }, seed = 1)
    for (seed in 2L..12L) {
      assertNull("il seme $seed legge il messaggio del seme 1", LsbStego.extract(first, width, height, seed))
    }
  }
}
