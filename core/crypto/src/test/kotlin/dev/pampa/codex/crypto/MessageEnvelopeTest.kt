package dev.pampa.codex.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessageEnvelopeTest {

  private val chatKey = ByteArray(32) { it.toByte() }
  private val chatId = "chat-1"
  private val senderId = "alice"
  private val text = "Ci vediamo alle sei sotto il portico.".toByteArray()

  @Test
  fun `una busta si riapre con la stessa chiave`() {
    val sealed = MessageEnvelope.seal(chatKey, chatId, "m1", senderId, text)
    assertTrue(MessageEnvelope.isEnvelope(sealed))
    assertArrayEquals(text, MessageEnvelope.open(chatKey, chatId, "m1", senderId, sealed))
  }

  @Test
  fun `spostare una busta in un'altra chat la rende illeggibile`() {
    val sealed = MessageEnvelope.seal(chatKey, chatId, "m1", senderId, text)
    // I dati associati non sono cifrati ma sono autenticati: cambiarli e' come cambiare la chiave.
    assertThrows(MessageEnvelope.Malformed::class.java) {
      MessageEnvelope.open(chatKey, "chat-2", "m1", senderId, sealed)
    }
    assertThrows(MessageEnvelope.Malformed::class.java) {
      MessageEnvelope.open(chatKey, chatId, "m2", senderId, sealed)
    }
    assertThrows(MessageEnvelope.Malformed::class.java) {
      MessageEnvelope.open(chatKey, chatId, "m1", "mallory", sealed)
    }
  }

  @Test
  fun `con la chiave sbagliata non si apre`() {
    val sealed = MessageEnvelope.seal(chatKey, chatId, "m1", senderId, text)
    val otherKey = ByteArray(32) { (it + 1).toByte() }
    assertThrows(MessageEnvelope.Malformed::class.java) {
      MessageEnvelope.open(otherKey, chatId, "m1", senderId, sealed)
    }
  }

  @Test
  fun `due messaggi diversi non condividono la coppia chiave-nonce`() {
    // Il punto piu' delicato di AES-GCM. Due buste dello stesso testo con id diversi devono avere
    // testi cifrati diversi; se fossero uguali, chiave e nonce si starebbero ripetendo.
    val first = MessageEnvelope.seal(chatKey, chatId, "m1", senderId, text)
    val second = MessageEnvelope.seal(chatKey, chatId, "m2", senderId, text)
    assertNotEquals(first.toList(), second.toList())
  }

  @Test
  fun `lo stesso messaggio si richiude identico`() {
    // Deterministico apposta: il mittente puo' ricostruire la busta di un messaggio gia' inviato
    // (per un reinvio) senza che il risultato differisca da quello che il destinatario ha gia'.
    val first = MessageEnvelope.seal(chatKey, chatId, "m1", senderId, text)
    val second = MessageEnvelope.seal(chatKey, chatId, "m1", senderId, text)
    assertArrayEquals(first, second)
  }

  @Test
  fun `un byte cambiato fa fallire l'apertura`() {
    val sealed = MessageEnvelope.seal(chatKey, chatId, "m1", senderId, text)
    val tampered = sealed.copyOf()
    tampered[tampered.size - 1] = (tampered.last().toInt() xor 1).toByte()
    assertThrows(MessageEnvelope.Malformed::class.java) {
      MessageEnvelope.open(chatKey, chatId, "m1", senderId, tampered)
    }
  }

  @Test
  fun `l'epoca della chiave si legge senza aprire`() {
    val sealed = MessageEnvelope.seal(chatKey, chatId, "m1", senderId, text, keyEpoch = 7)
    assertEquals(7, MessageEnvelope.keyEpochOf(sealed))
  }

  @Test
  fun `il seme del sigillo e' lo stesso per chi manda e per chi riceve`() {
    val seed = MessageEnvelope.sealSeed(chatKey, "m1")
    assertEquals(seed, MessageEnvelope.sealSeed(chatKey.copyOf(), "m1"))
    assertNotEquals(seed, MessageEnvelope.sealSeed(chatKey, "m2"))
  }

  @Test
  fun `qualcosa che non e' una busta viene riconosciuto subito`() {
    assertFalse(MessageEnvelope.isEnvelope("ciao".toByteArray()))
    assertFalse(MessageEnvelope.isEnvelope(ByteArray(0)))
    assertThrows(MessageEnvelope.Malformed::class.java) {
      MessageEnvelope.open(chatKey, chatId, "m1", senderId, "non una busta".toByteArray())
    }
  }
}
