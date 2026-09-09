package dev.pampa.codex.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class VaultTest {

  // Parametri leggeri: i test devono restare veloci, e cio' che si verifica qui e' il formato e il
  // comportamento, non il costo. Il costo vero e' quello del vettore in PrimitivesKatTest.
  private val fastParams = Kdf.Params(memoryKiB = 64, iterations = 1, parallelism = 1)

  @Test
  fun `si riapre con il segreto giusto e restituisce la stessa identita'`() {
    val identity = CodexIdentity.generate(createdAt = 1_700_000_000_000)
    val blob = Vault.seal(identity, "123456", fastParams)

    val reopened = Vault.open(blob, "123456")
    assertArrayEquals(identity.x25519Private, reopened.x25519Private)
    assertArrayEquals(identity.ed25519Private, reopened.ed25519Private)
    assertArrayEquals(identity.keyringSeed, reopened.keyringSeed)
    assertEquals(identity.createdAt, reopened.createdAt)
    assertEquals(identity.codexId, reopened.codexId)
  }

  @Test
  fun `con il segreto sbagliato dice solo che e' sbagliato`() {
    val blob = Vault.seal(CodexIdentity.generate(), "123456", fastParams)
    assertThrows(Vault.WrongSecret::class.java) { Vault.open(blob, "123457") }
  }

  @Test
  fun `due vault dallo stesso segreto sono diversi`() {
    val identity = CodexIdentity.generate()
    val first = Vault.seal(identity, "segreto", fastParams)
    val second = Vault.seal(identity, "segreto", fastParams)
    // Salt e nonce nuovi ogni volta: due file identici direbbero a chi guarda che l'identita' non
    // e' cambiata, e con un nonce ripetuto GCM perde ogni garanzia.
    assertNotEquals(first.toList(), second.toList())
    assertArrayEquals(
      Vault.open(first, "segreto").ed25519Private,
      Vault.open(second, "segreto").ed25519Private,
    )
  }

  @Test
  fun `i parametri di costo si leggono senza aprire, e sono protetti dal tag`() {
    val params = Kdf.Params(memoryKiB = 128, iterations = 2, parallelism = 1)
    val blob = Vault.seal(CodexIdentity.generate(), "pw", params)
    assertEquals(params, Vault.paramsOf(blob))

    // Abbassare a mano il costo nel file (128 KiB -> 64 nell'ultimo byte del campo memoria):
    // l'intestazione e' dato associato, quindi il tag non torna e il vault non apre.
    val tampered = blob.copyOf()
    tampered[11] = 64
    assertThrows(Vault.WrongSecret::class.java) { Vault.open(tampered, "pw") }
  }

  @Test
  fun `la stessa frase in forme Unicode diverse apre lo stesso vault`() {
    // La stessa parola scritta in due modi che una persona non distingue: la "e" accentata come
    // singolo carattere, e la "e" seguita dall'accento combinante.
    val composed = "perché"
    val decomposed = "perché"
    assertNotEquals(composed, decomposed)

    val blob = Vault.seal(CodexIdentity.generate(), composed, fastParams)
    Vault.open(blob, decomposed)
  }

  @Test
  fun `wipe azzera i segreti`() {
    val identity = CodexIdentity.generate()
    identity.wipe()
    assertFalse(identity.x25519Private.any { it != 0.toByte() })
    assertFalse(identity.ed25519Private.any { it != 0.toByte() })
    assertFalse(identity.keyringSeed.any { it != 0.toByte() })
  }
}
