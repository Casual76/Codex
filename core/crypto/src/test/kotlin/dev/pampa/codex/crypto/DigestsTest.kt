package dev.pampa.codex.crypto

import dev.pampa.codex.crypto.Digests.toHex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class DigestsTest {

  @Test
  fun `sha256 di "abc" e' il vettore noto FIPS 180`() {
    assertEquals(
      "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
      Digests.sha256("abc".toByteArray()).toHex(),
    )
  }

  @Test
  fun `sha256 della stringa vuota e' il vettore noto`() {
    assertEquals(
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
      Digests.sha256(ByteArray(0)).toHex(),
    )
  }

  @Test
  fun `i byte casuali hanno la lunghezza chiesta e non si ripetono`() {
    val a = Digests.randomBytes(32)
    val b = Digests.randomBytes(32)
    assertEquals(32, a.size)
    assertFalse(a.contentEquals(b))
  }
}
