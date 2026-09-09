package dev.pampa.codex.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * La cifratura dei media, e soprattutto **cosa non lascia passare**.
 *
 * Un file cifrato a blocchi indipendenti si potrebbe rimescolare senza conoscere nessuna chiave:
 * togliere un pezzo, ripeterne uno, invertirne due. Non produrrebbe niente di leggibile, ma
 * produrrebbe **qualcosa** -- e un'app che apre "qualcosa" al posto di una foto ha gia' perso. Meta'
 * di questi test provano esattamente quei rimescolamenti.
 */
class MediaCipherTest {

  private val key = Digests.randomBytes(Aead.KEY_SIZE)

  private fun cifra(data: ByteArray, key: ByteArray = this.key): ByteArray {
    val out = ByteArrayOutputStream()
    MediaCipher.encrypt(key, ByteArrayInputStream(data), out, data.size.toLong())
    return out.toByteArray()
  }

  private fun decifra(data: ByteArray, key: ByteArray = this.key): ByteArray {
    val out = ByteArrayOutputStream()
    MediaCipher.decrypt(key, ByteArrayInputStream(data), out)
    return out.toByteArray()
  }

  @Test
  fun `un file torna indietro identico, a qualsiasi misura`() {
    val random = Random(4)
    // Zero byte, meno di un blocco, esattamente un blocco, uno e mezzo, tre e spiccioli.
    for (size in listOf(0, 1, 1000, MediaCipher.CHUNK, MediaCipher.CHUNK + 1, MediaCipher.CHUNK * 3 + 77)) {
      val original = random.nextBytes(size)
      assertArrayEquals(original, decifra(cifra(original)), "misura $size")
    }
  }

  @Test
  fun `il contenuto non compare in chiaro`() {
    val original = "ci vediamo alle sei sotto il portico".repeat(50).toByteArray()
    val cifrato = cifra(original)
    assertFalse(String(cifrato, Charsets.ISO_8859_1).contains("portico"))
  }

  @Test
  fun `la misura si sa prima di cominciare`() {
    for (size in listOf(0L, 10L, MediaCipher.CHUNK.toLong(), MediaCipher.CHUNK * 2L + 5)) {
      assertEquals(
        MediaCipher.encryptedSize(size),
        cifra(Random(1).nextBytes(size.toInt())).size.toLong(),
        "misura $size",
      )
    }
  }

  @Test
  fun `con un'altra chiave non si apre`() {
    val cifrato = cifra(Random(2).nextBytes(5000))
    assertThrows<MediaCipher.Malformed> { decifra(cifrato, Digests.randomBytes(Aead.KEY_SIZE)) }
  }

  @Test
  fun `due blocchi scambiati non passano`() {
    val original = Random(3).nextBytes(MediaCipher.CHUNK * 2)
    val cifrato = cifra(original)
    val header = MediaCipher.encryptedSize(0).toInt()
    val blocco = MediaCipher.CHUNK + Aead.TAG_SIZE

    // Si invertono i due blocchi. Nessuna chiave, nessun tag toccato: solo l'ordine.
    val rimescolato = cifrato.copyOf()
    System.arraycopy(cifrato, header + blocco, rimescolato, header, blocco)
    System.arraycopy(cifrato, header, rimescolato, header + blocco, blocco)

    assertThrows<MediaCipher.Malformed> { decifra(rimescolato) }
  }

  @Test
  fun `un blocco ripetuto non passa`() {
    val original = Random(5).nextBytes(MediaCipher.CHUNK * 2)
    val cifrato = cifra(original)
    val header = MediaCipher.encryptedSize(0).toInt()
    val blocco = MediaCipher.CHUNK + Aead.TAG_SIZE

    val ripetuto = cifrato.copyOf()
    System.arraycopy(cifrato, header, ripetuto, header + blocco, blocco)

    assertThrows<MediaCipher.Malformed> { decifra(ripetuto) }
  }

  @Test
  fun `un file troncato non passa`() {
    val cifrato = cifra(Random(6).nextBytes(MediaCipher.CHUNK * 2))
    assertThrows<MediaCipher.Malformed> { decifra(cifrato.copyOf(cifrato.size - 500)) }
  }

  @Test
  fun `l'intestazione non si puo' riscrivere`() {
    val cifrato = cifra(Random(7).nextBytes(3000))
    // Dichiarare meno byte di quelli che ci sono taglierebbe il file **senza** rompere nessun tag,
    // se l'intestazione non fosse autenticata insieme a ogni blocco. Lo e', quindi non si apre.
    // Byte 20: l'ultimo della lunghezza dichiarata (4 di magia + 1 di versione + 8 di blocco + 8).
    val bugiardo = cifrato.copyOf()
    bugiardo[20] = 0
    assertThrows<MediaCipher.Malformed> { decifra(bugiardo) }
  }

  @Test
  fun `un byte cambiato non passa`() {
    val cifrato = cifra(Random(8).nextBytes(2000))
    val rovinato = cifrato.copyOf()
    rovinato[rovinato.size / 2] = (rovinato[rovinato.size / 2] + 1).toByte()
    assertThrows<MediaCipher.Malformed> { decifra(rovinato) }
  }

  @Test
  fun `qualcosa che non e' un media di Codex lo dice subito`() {
    assertThrows<MediaCipher.Malformed> { decifra(ByteArray(4)) }
    assertThrows<MediaCipher.Malformed> { decifra("non sono un file".toByteArray()) }
  }

  @Test
  fun `due media della stessa chat non condividono la chiave`() {
    val chatKey = Digests.randomBytes(Aead.KEY_SIZE)
    val prima = MediaCipher.key(chatKey, "foto-1")
    val seconda = MediaCipher.key(chatKey, "foto-2")
    assertFalse(prima.contentEquals(seconda), "due media non devono avere la stessa chiave")
    assertArrayEquals(prima, MediaCipher.key(chatKey, "foto-1"))
    assertTrue(prima.size == Aead.KEY_SIZE)
  }
}
