package dev.pampa.codex.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PaintingParcelTest {

  private val chatKey = ByteArray(32) { it.toByte() }
  private val chatId = "self-0avkbspy"
  private val messageId = "5f0f7f0e-1b2c-4d3e-9a8b-7c6d5e4f3a2b"
  private val senderId = "CDX-0AVK-BSPY"

  private fun envelope(text: String = "Il libro sta nello scaffale in fondo") = MessageEnvelope.seal(
    chatKey = chatKey,
    chatId = chatId,
    messageId = messageId,
    senderId = senderId,
    plaintext = text.toByteArray(),
  )

  private fun content(text: String = "Il libro sta nello scaffale in fondo") = PaintingParcel.Content(
    chatId = chatId,
    messageId = messageId,
    senderId = senderId,
    envelope = envelope(text),
  )

  @Test
  fun `andata e ritorno`() {
    val original = content()
    val recovered = PaintingParcel.unpack(PaintingParcel.pack(original))
    assertEquals(original, recovered)
  }

  @Test
  fun `il messaggio si riapre dopo il giro completo`() {
    val recovered = PaintingParcel.unpack(PaintingParcel.pack(content()))
    val plaintext = MessageEnvelope.open(
      chatKey = chatKey,
      chatId = recovered.chatId,
      messageId = recovered.messageId,
      senderId = recovered.senderId,
      envelope = recovered.envelope,
    )
    assertEquals("Il libro sta nello scaffale in fondo", plaintext.decodeToString())
  }

  @Test
  fun `un involucro si riconosce senza aprirlo`() {
    assertTrue(PaintingParcel.looksLikeParcel(PaintingParcel.pack(content())))
    assertFalse(PaintingParcel.looksLikeParcel(envelope()))
    assertFalse(PaintingParcel.looksLikeParcel(ByteArray(2)))
  }

  @Test
  fun `dentro deve esserci una busta`() {
    val fake = ByteWriter()
      .bytes("CDXP".toByteArray())
      .u8(1)
      .string(chatId)
      .string(messageId)
      .string(senderId)
      .bytes("non sono una busta".toByteArray())
      .build()
    assertThrows(PaintingParcel.Malformed::class.java) { PaintingParcel.unpack(fake) }
  }

  @Test
  fun `un involucro troncato non si apre`() {
    val packed = PaintingParcel.pack(content())
    assertThrows(PaintingParcel.Malformed::class.java) {
      PaintingParcel.unpack(packed.copyOf(packed.size / 2))
    }
  }

  @Test
  fun `un involucro di un'altra versione non si apre`() {
    val packed = PaintingParcel.pack(content())
    // Il byte dopo il magic e' la versione.
    packed[4] = 9
    assertThrows(PaintingParcel.Malformed::class.java) { PaintingParcel.unpack(packed) }
  }

  @Test
  fun `l'involucro non porta il testo in chiaro`() {
    // Il punto di tutto: quello che gira per il mondo dentro un PNG non deve contenere il
    // messaggio. Gli id si vedono, il testo no.
    val packed = PaintingParcel.pack(content("appuntamento alle sei sotto il portico"))
    val asText = String(packed, Charsets.ISO_8859_1)
    assertFalse(asText.contains("appuntamento"))
    assertFalse(asText.contains("portico"))
    assertTrue(asText.contains(chatId))
  }

  @Test
  fun `cambiare mittente rende la busta illeggibile`() {
    // Non "leggibile in modo diverso": proprio illeggibile. I tre id sono dati autenticati.
    val recovered = PaintingParcel.unpack(PaintingParcel.pack(content()))
    assertThrows(MessageEnvelope.Malformed::class.java) {
      MessageEnvelope.open(
        chatKey = chatKey,
        chatId = recovered.chatId,
        messageId = recovered.messageId,
        senderId = "CDX-ALTR-OUNO",
        envelope = recovered.envelope,
      )
    }
  }

  @Test
  fun `la busta esce identica a com'era entrata`() {
    val original = envelope()
    val recovered = PaintingParcel.unpack(
      PaintingParcel.pack(PaintingParcel.Content(chatId, messageId, senderId, original)),
    )
    assertArrayEquals(original, recovered.envelope)
  }
}
