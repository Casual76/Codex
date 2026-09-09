package dev.pampa.codex.crypto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Cosa c'e' dentro una busta.
 *
 * Il test che conta di piu' e' il piu' noioso: **un messaggio scritto ieri deve leggersi domani**.
 * Le buste gia' su disco contengono testo puro, e un formato nuovo che non le riconoscesse
 * trasformerebbe le vecchie conversazioni in spazzatura senza che nessuno se ne accorga finche' non
 * le riapre.
 */
class MessageBodyTest {

  @Test
  fun `il testo di ieri si legge ancora`() {
    // Come lo scriveva M4: UTF-8 e basta, nessuna intestazione.
    val vecchia = "ci vediamo alle sei".toByteArray(Charsets.UTF_8)
    val letto = MessageBodyCodec.decode(vecchia)
    assertEquals(MessageBody.Text("ci vediamo alle sei"), letto)
  }

  @Test
  fun `il testo resta testo puro anche adesso`() {
    val codificato = MessageBodyCodec.encode(MessageBody.Text("perche' no"))
    assertEquals("perche' no", codificato.decodeToString())
  }

  @Test
  fun `una foto torna indietro identica`() {
    val foto = MessageBody.Media(
      kind = MessageBody.Media.Kind.PHOTO,
      mediaId = "3f2a-88",
      mime = "image/jpeg",
      size = 2_400_000,
      width = 4032,
      height = 3024,
      caption = "il portico",
    )
    assertEquals(foto, MessageBodyCodec.decode(MessageBodyCodec.encode(foto)))
  }

  @Test
  fun `una nota vocale torna indietro identica`() {
    val vocale = MessageBody.Media(
      kind = MessageBody.Media.Kind.VOICE,
      mediaId = "voce-1",
      mime = "audio/mp4",
      size = 180_000,
      durationMs = 12_500,
      waveform = byteArrayOf(3, 40, 120, -1, 0, 77),
    )
    assertEquals(vocale, MessageBodyCodec.decode(MessageBodyCodec.encode(vocale)))
  }

  @Test
  fun `una foto scritta prima della forma d'onda si legge ancora`() {
    // La busta com'era prima che le note vocali esistessero: finisce con la didascalia, e basta.
    val vecchia = ByteWriter(64)
      .bytes("CDXA".toByteArray(Charsets.US_ASCII))
      .u8(1)
      .u8(MessageBody.Media.Kind.PHOTO.ordinal)
      .string("3f2a-88")
      .string("image/jpeg")
      .i64(2_400_000)
      .i64(4032)
      .i64(3024)
      .i64(0)
      .string("il portico")
      .build()

    val letta = MessageBodyCodec.decode(vecchia) as MessageBody.Media
    assertEquals("3f2a-88", letta.mediaId)
    assertEquals("il portico", letta.caption)
    assertEquals(0, letta.waveform.size)
  }

  @Test
  fun `il tipo dell'allegato non esce dalla busta`() {
    // Sta dentro il testo cifrato: se stesse nel documento su Firestore, il server saprebbe chi si
    // manda foto e chi si manda vocali.
    val foto = MessageBody.Media(
      kind = MessageBody.Media.Kind.PHOTO,
      mediaId = "x",
      mime = "image/jpeg",
      size = 1,
    )
    val chatKey = Digests.randomBytes(Aead.KEY_SIZE)
    val busta = MessageEnvelope.seal(
      chatKey = chatKey,
      chatId = "d-1",
      messageId = "m-1",
      senderId = "CDX-AAAA-1111",
      plaintext = MessageBodyCodec.encode(foto),
    )
    assertTrue(
      !String(busta, Charsets.ISO_8859_1).contains("image/jpeg"),
      "il tipo del file non deve comparire in chiaro nella busta",
    )
  }

  @Test
  fun `un allegato rovinato non fa sparire il messaggio`() {
    val foto = MessageBody.Media(
      kind = MessageBody.Media.Kind.PHOTO,
      mediaId = "x",
      mime = "image/jpeg",
      size = 1,
    )
    val tagliato = MessageBodyCodec.encode(foto).copyOf(8)
    // Resta un messaggio, vuoto: la riga nella conversazione non si perde.
    assertTrue(MessageBodyCodec.decode(tagliato) is MessageBody.Text)
  }

  @Test
  fun `un testo che comincia per caso come un allegato resta testo`() {
    // "CDXA" e' quattro lettere: qualcuno prima o poi le scrivera'.
    val insidioso = "CDXA"
    assertEquals(
      MessageBody.Text(insidioso),
      MessageBodyCodec.decode(insidioso.toByteArray(Charsets.UTF_8)),
    )
  }
}
