package dev.pampa.codex.crypto

import dev.pampa.codex.crypto.Digests.toHex
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Vettori noti dagli standard.
 *
 * Servono a una cosa sola, ed e' quella che conta: dire che stiamo calcolando **la** funzione e non
 * qualcosa che le somiglia. Un errore in un parametro (la versione di Argon2, la lunghezza del tag,
 * l'ordine dei byte) produce risultati dall'aria perfettamente ragionevole, e senza questi confronti
 * si scoprirebbe il giorno in cui un altro dispositivo non riesce a decifrare.
 */
class PrimitivesKatTest {

  @Test
  fun `HKDF-SHA256, RFC 5869 caso 1`() {
    val okm = Hkdf.deriveRaw(
      ikm = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b"),
      info = hex("f0f1f2f3f4f5f6f7f8f9"),
      length = 42,
      salt = hex("000102030405060708090a0b0c"),
    )
    assertEquals(
      "3cb25f25faacd57a90434f64d0362f2a" +
        "2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
        "34007208d5b887185865",
      okm.toHex(),
    )
  }

  @Test
  fun `AES-256-GCM, vettori 13 e 14 della specifica GCM`() {
    val key = ByteArray(32)
    val nonce = ByteArray(12)

    // Caso 13: niente da cifrare, resta solo il tag.
    val onlyTag = Aead.encrypt(key, nonce, ByteArray(0))
    assertEquals("530f8afbc74536b9a963b4f1c4cb738b", onlyTag.toHex())

    // Caso 14: un blocco di zeri.
    val sealed = Aead.encrypt(key, nonce, ByteArray(16))
    assertEquals(
      "cea7403d4d606b6e074ec5d3baf39d18d0d1c8a799996bf0265b98b5d48ab919",
      sealed.toHex(),
    )
    assertArrayEquals(ByteArray(16), Aead.decrypt(key, nonce, sealed))
  }

  @Test
  fun `un bit cambiato fa fallire la decifratura, non la falsa`() {
    val key = Digests.randomBytes(32)
    val nonce = Aead.randomNonce()
    val sealed = Aead.encrypt(key, nonce, "ciao".toByteArray(), "aad".toByteArray())

    val tampered = sealed.copyOf()
    tampered[0] = (tampered[0].toInt() xor 1).toByte()
    assertThrows(Aead.DecryptionFailed::class.java) {
      Aead.decrypt(key, nonce, tampered, "aad".toByteArray())
    }
    // Anche cambiare i soli dati associati deve far fallire.
    assertThrows(Aead.DecryptionFailed::class.java) {
      Aead.decrypt(key, nonce, sealed, "altro".toByteArray())
    }
    assertArrayEquals("ciao".toByteArray(), Aead.decrypt(key, nonce, sealed, "aad".toByteArray()))
  }

  @Test
  fun `Argon2id, vettore di RFC 9106`() {
    val output = Kdf.deriveWithExtras(
      password = ByteArray(32) { 0x01 },
      salt = ByteArray(16) { 0x02 },
      secret = ByteArray(8) { 0x03 },
      additional = ByteArray(12) { 0x04 },
      params = Kdf.Params(memoryKiB = 32, iterations = 3, parallelism = 4),
      length = 32,
    )
    assertEquals("0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659", output.toHex())
  }

  @Test
  fun `X25519, vettore di RFC 7748`() {
    val alicePrivate = hex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a")
    val bobPrivate = hex("5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb")
    val alicePublic = hex("8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a")
    val bobPublic = hex("de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f")
    val expectedShared = "4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742"

    assertEquals(alicePublic.toHex(), IdentityKeys.x25519PublicKey(alicePrivate).toHex())
    assertEquals(bobPublic.toHex(), IdentityKeys.x25519PublicKey(bobPrivate).toHex())
    assertEquals(expectedShared, IdentityKeys.agree(alicePrivate, bobPublic).toHex())
    // Le due parti arrivano allo stesso segreto: e' tutto il punto dell'accordo.
    assertEquals(expectedShared, IdentityKeys.agree(bobPrivate, alicePublic).toHex())
  }

  @Test
  fun `Ed25519, vettore 1 di RFC 8032`() {
    val privateKey = hex("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60")
    val publicKey = hex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a")
    assertEquals(publicKey.toHex(), IdentityKeys.ed25519PublicKey(privateKey).toHex())

    val signature = IdentityKeys.sign(privateKey, ByteArray(0))
    assertEquals(
      "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b",
      signature.toHex(),
    )
    assertTrue(IdentityKeys.verify(publicKey, ByteArray(0), signature))
    assertFalse(IdentityKeys.verify(publicKey, "altro".toByteArray(), signature))
  }
}
