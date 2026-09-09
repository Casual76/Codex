package dev.pampa.codex.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Il rito della parola d'ordine, provato dai due lati.
 *
 * E' il test che conta di piu' di M3: se i due lati non arrivano alla stessa chiave, due persone
 * si scrivono e non si leggono, e non c'e' nessuna schermata che possa rimediare.
 */
class ChatSecretTest {

  private val ada = CodexIdentity.generate()
  private val bruno = CodexIdentity.generate()

  private fun fromAda(word: String) = ChatSecret.agree(
    myX25519Private = ada.x25519Private,
    myCodexId = ada.codexId,
    peerX25519Public = bruno.x25519Public,
    peerCodexId = bruno.codexId,
    passphrase = word,
  )

  private fun fromBruno(word: String) = ChatSecret.agree(
    myX25519Private = bruno.x25519Private,
    myCodexId = bruno.codexId,
    peerX25519Public = ada.x25519Public,
    peerCodexId = ada.codexId,
    passphrase = word,
  )

  @Test
  fun `la stessa parola porta i due lati alla stessa chiave`() {
    val mia = fromAda("ponte di pietra")
    val sua = fromBruno("ponte di pietra")
    assertEquals(mia.chatId, sua.chatId)
    assertArrayEquals(mia.key, sua.key)
    assertEquals(mia.emblemSeed, sua.emblemSeed, "le due gemme devono essere la stessa gemma")
  }

  @Test
  fun `una parola diversa lascia la conversazione al suo posto ma non la apre`() {
    val mia = fromAda("ponte di pietra")
    val sua = fromBruno("ponte di ferro")
    // Stessa conversazione: i messaggi arrivano dove devono, e il guaio si vede.
    assertEquals(mia.chatId, sua.chatId)
    assertFalse(mia.key.contentEquals(sua.key), "parole diverse non possono dare la stessa chiave")
    assertNotEquals(mia.emblemSeed, sua.emblemSeed, "le gemme devono divergere, e' come lo si scopre")
  }

  @Test
  fun `l'identificatore della chat non dipende dalla parola`() {
    assertEquals(fromAda("una parola").chatId, fromAda("tutta un'altra").chatId)
  }

  @Test
  fun `due coppie diverse non si incontrano mai`() {
    val carla = CodexIdentity.generate()
    val conBruno = fromAda("stessa parola")
    val conCarla = ChatSecret.agree(
      myX25519Private = ada.x25519Private,
      myCodexId = ada.codexId,
      peerX25519Public = carla.x25519Public,
      peerCodexId = carla.codexId,
      passphrase = "stessa parola",
    )
    assertNotEquals(conBruno.chatId, conCarla.chatId)
    assertFalse(conBruno.key.contentEquals(conCarla.key))
  }

  @Test
  fun `accenti maiuscole e punteggiatura non cambiano la parola`() {
    val detta = fromAda("Perché No!")
    val battuta = fromBruno("  perche   no ")
    assertArrayEquals(detta.key, battuta.key)
    assertEquals("perche no", ChatSecret.normalize("Perché  No!"))
    assertEquals("l alba", ChatSecret.normalize("L'alba"))
    assertEquals("citta 12", ChatSecret.normalize("Città-12"))
  }

  @Test
  fun `una parola troppo corta non chiude niente`() {
    assertFalse(ChatSecret.isAcceptable("ab"))
    assertFalse(ChatSecret.isAcceptable("  !  "))
    assertTrue(ChatSecret.isAcceptable("alba"))
    assertThrows<IllegalArgumentException> { fromAda("ab") }
  }

  @Test
  fun `non ci si accorda con se stessi`() {
    assertThrows<IllegalArgumentException> {
      ChatSecret.agree(
        myX25519Private = ada.x25519Private,
        myCodexId = ada.codexId,
        peerX25519Public = ada.x25519Public,
        peerCodexId = ada.codexId,
        passphrase = "una parola",
      )
    }
  }

  @Test
  fun `la chiave avvolta si riapre solo con il suo portachiavi e nella sua chat`() {
    val agreement = fromAda("ponte di pietra")
    val portachiavi = ada.keyringKey()
    val avvolta = ChatKeyring.wrap(portachiavi, agreement.chatId, agreement.key)

    assertFalse(
      avvolta.asList().windowed(agreement.key.size).any { it.toByteArray().contentEquals(agreement.key) },
      "la chiave non deve comparire in chiaro dentro il suo involucro",
    )
    assertArrayEquals(agreement.key, ChatKeyring.unwrap(portachiavi, agreement.chatId, avvolta))
    assertThrows<ChatKeyring.Unwrappable> {
      ChatKeyring.unwrap(portachiavi, "d-unaltrachat", avvolta)
    }
    assertThrows<ChatKeyring.Unwrappable> {
      ChatKeyring.unwrap(bruno.keyringKey(), agreement.chatId, avvolta)
    }
  }

  @Test
  fun `l'involucro cambia a ogni giro`() {
    val agreement = fromAda("ponte di pietra")
    val portachiavi = ada.keyringKey()
    val prima = ChatKeyring.wrap(portachiavi, agreement.chatId, agreement.key)
    val poi = ChatKeyring.wrap(portachiavi, agreement.chatId, agreement.key)
    assertFalse(prima.contentEquals(poi), "due involucri identici vorrebbero dire nonce riusato")
  }

  @Test
  fun `l'identificatore si sa anche prima del rito`() {
    val prima = ChatSecret.chatIdFor(
      myX25519Private = ada.x25519Private,
      myCodexId = ada.codexId,
      peerX25519Public = bruno.x25519Public,
      peerCodexId = bruno.codexId,
    )
    assertEquals(fromAda("una parola").chatId, prima)
    assertEquals(
      prima,
      ChatSecret.chatIdFor(
        myX25519Private = bruno.x25519Private,
        myCodexId = bruno.codexId,
        peerX25519Public = ada.x25519Public,
        peerCodexId = ada.codexId,
      ),
    )
  }

  @Test
  fun `l'identificatore ha una forma stabile`() {
    val chatId = fromAda("una parola").chatId
    assertTrue(chatId.startsWith("d-"), "id inatteso: $chatId")
    assertEquals(18, chatId.length, "id inatteso: $chatId")
  }
}
