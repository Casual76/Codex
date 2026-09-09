package dev.pampa.codex.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IdentityTest {

  @Test
  fun `l'identita' sopravvive alla scrittura e alla rilettura`() {
    val identity = CodexIdentity.generate(createdAt = 42)
    val decoded = CodexIdentity.decode(identity.encode())
    assertArrayEquals(identity.x25519Private, decoded.x25519Private)
    assertArrayEquals(identity.ed25519Private, decoded.ed25519Private)
    assertArrayEquals(identity.keyringSeed, decoded.keyringSeed)
    assertEquals(42L, decoded.createdAt)
  }

  @Test
  fun `il Codex ID ha la forma attesa ed e' stabile`() {
    val identity = CodexIdentity.generate()
    val id = identity.codexId
    assertTrue(id.matches(Regex("CDX-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}")), id)
    // Non dipende dal telefono ne' dal momento: la stessa identita' da' sempre lo stesso ID.
    assertEquals(id, CodexIdentity.decode(identity.encode()).codexId)
  }

  @Test
  fun `la normalizzazione perdona i modi in cui un ID viene ridettato`() {
    val id = CodexIdentity.generate().codexId
    val body = id.removePrefix("CDX-").replace("-", "")
    assertEquals(id, CodexId.normalize(id.lowercase()))
    assertEquals(id, CodexId.normalize(" $id "))
    assertEquals(id, CodexId.normalize(body))
    assertNull(CodexId.normalize("CDX-123"))
    assertFalse(CodexId.isValid("non un id"))
  }

  @Test
  fun `due identita' arrivano alla stessa chiave a coppia`() {
    val alice = CodexIdentity.generate()
    val bob = CodexIdentity.generate()
    assertArrayEquals(alice.pairKey(bob.x25519Public), bob.pairKey(alice.x25519Public))
  }

  @Test
  fun `la scheda contatto si verifica, e una manomessa no`() {
    val identity = CodexIdentity.generate()
    val signed = ContactCard.of(identity, name = "Alessio", avatarSeed = 7L, issuedAt = 1000)
    val bytes = signed.encode()

    val verified = ContactCard.decodeVerified(bytes)
    assertNotNull(verified)
    assertEquals("Alessio", verified!!.card.name)
    assertEquals(identity.codexId, verified.card.codexId)

    // Cambiare il nome dentro i byte firmati: la firma non torna piu'.
    val tampered = bytes.copyOf()
    val nameIndex = String(bytes, Charsets.ISO_8859_1).indexOf("Alessio")
    tampered[nameIndex] = 'B'.code.toByte()
    assertNull(ContactCard.decodeVerified(tampered))
  }

  @Test
  fun `una scheda con un Codex ID che non nasce dalla sua chiave viene rifiutata`() {
    val identity = CodexIdentity.generate()
    val other = CodexIdentity.generate()
    // Una scheda che dichiara l'ID di qualcun altro ma e' firmata correttamente da chi la emette:
    // e' esattamente cio' che un server ostile proverebbe a consegnare.
    val forged = ContactCard(
      uid = "",
      codexId = other.codexId,
      name = "Falso",
      avatarSeed = 0,
      x25519Public = identity.x25519Public,
      ed25519Public = identity.ed25519Public,
      issuedAt = 0,
    ).sign(identity)
    assertNull(ContactCard.decodeVerified(forged.encode()))
  }

  @Test
  fun `il seme derivato e' deterministico e cambia con l'etichetta`() {
    val material = ByteArray(32) { it.toByte() }
    assertEquals(Hkdf.deriveSeed(material, "seal|1"), Hkdf.deriveSeed(material, "seal|1"))
    assertTrue(Hkdf.deriveSeed(material, "seal|1") != Hkdf.deriveSeed(material, "seal|2"))
  }
}
