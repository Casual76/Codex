package dev.pampa.codex.data.contacts

import dev.pampa.codex.crypto.CodexIdentity
import dev.pampa.codex.data.FakeDatabase
import dev.pampa.codex.data.FakeIdentitySource
import dev.pampa.codex.data.chat.ChatMessage
import dev.pampa.codex.data.chat.ChatRepository
import dev.pampa.codex.data.db.ChatKind
import dev.pampa.codex.data.db.MessageStatus
import dev.pampa.codex.data.identity.Keyring
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Due telefoni che si aggiungono a vicenda, senza server e senza telefoni.
 *
 * E' il test che copre il buco piu' pericoloso di M3: tutto il pairing funziona **a coppie**, e un
 * errore in cui i due lati non arrivano allo stesso risultato non si vede su un dispositivo solo.
 * Qui i due dispositivi sono due oggetti [Device] con due identita' diverse e due magazzini
 * separati, e l'unica cosa che passa fra loro e' quello che passerebbe davvero: un codice.
 */
class PairingTest {

  /** Un telefono: la sua identita', il suo magazzino, i suoi repository. */
  private class Device(name: String) {
    val db = FakeDatabase()
    val identity: CodexIdentity = CodexIdentity.generate()
    val source = FakeIdentitySource(identity, name = name)
    private val keyring = Keyring(db.keyring, source)
    val chats = ChatRepository(db.chats, db.messages, source, keyring, db.outbox)
    val contacts = ContactRepository(db.contacts, source, chats, keyring)

    val codexId: String get() = identity.codexId

    suspend fun code(): String = contacts.myCode()!!
  }

  private val ada = Device("Ada")
  private val bruno = Device("Bruno")

  /** Le due schede si scambiano, come farebbero due fotocamere una davanti all'altra. */
  private suspend fun scambiaLeSchede() {
    assertTrue(ada.contacts.add(bruno.code()) is AddContactResult.Added)
    assertTrue(bruno.contacts.add(ada.code()) is AddContactResult.Added)
  }

  @Test
  fun `la stessa parola porta i due telefoni alla stessa conversazione`() = runTest {
    scambiaLeSchede()
    val mia = ada.contacts.openConversation(bruno.codexId, "ponte di pietra").getOrThrow()
    val sua = bruno.contacts.openConversation(ada.codexId, "Ponte di Pietra!").getOrThrow()

    assertEquals(mia.chatId, sua.chatId)
    assertEquals("le due gemme devono coincidere", mia.emblemSeed, sua.emblemSeed)
  }

  @Test
  fun `parole diverse lasciano la stessa conversazione con due gemme diverse`() = runTest {
    scambiaLeSchede()
    val mia = ada.contacts.openConversation(bruno.codexId, "ponte di pietra").getOrThrow()
    val sua = bruno.contacts.openConversation(ada.codexId, "ponte di ferro").getOrThrow()

    assertEquals(mia.chatId, sua.chatId)
    assertNotEquals(mia.emblemSeed, sua.emblemSeed)
  }

  @Test
  fun `un messaggio scritto da una parte si apre dall'altra`() = runTest {
    scambiaLeSchede()
    val chatId = ada.contacts.openConversation(bruno.codexId, "ponte di pietra").getOrThrow().chatId
    bruno.contacts.openConversation(ada.codexId, "ponte di pietra").getOrThrow()

    val messageId = ada.chats.send(chatId, "ci vediamo alle sei").getOrThrow()
    consegna(messageId, chatId)

    val arrivato = bruno.chats.observeMessages(chatId).first().single()
    assertEquals("ci vediamo alle sei", arrivato.text)
    assertEquals(ChatMessage.Content.OPEN, arrivato.content)
    assertFalse("un messaggio ricevuto non e' mio", arrivato.outgoing)
  }

  @Test
  fun `con la parola sbagliata il messaggio arriva e non si apre`() = runTest {
    scambiaLeSchede()
    val chatId = ada.contacts.openConversation(bruno.codexId, "ponte di pietra").getOrThrow().chatId
    bruno.contacts.openConversation(ada.codexId, "ponte di ferro").getOrThrow()

    val messageId = ada.chats.send(chatId, "ci vediamo alle sei").getOrThrow()
    consegna(messageId, chatId)

    val arrivato = bruno.chats.observeMessages(chatId).first().single()
    assertEquals(ChatMessage.Content.UNREADABLE, arrivato.content)
    assertEquals("", arrivato.text)
    assertTrue(arrivato.isPlaceholder)
  }

  @Test
  fun `rifare il rito con la parola giusta riapre quello che era rimasto chiuso`() = runTest {
    scambiaLeSchede()
    val chatId = ada.contacts.openConversation(bruno.codexId, "ponte di pietra").getOrThrow().chatId
    bruno.contacts.openConversation(ada.codexId, "ponte di ferro").getOrThrow()
    val messageId = ada.chats.send(chatId, "ci vediamo alle sei").getOrThrow()
    consegna(messageId, chatId)
    assertEquals(
      ChatMessage.Content.UNREADABLE,
      bruno.chats.observeMessages(chatId).first().single().content,
    )

    // Si sono accorti che le gemme erano diverse e hanno rifatto il rito.
    bruno.contacts.openConversation(ada.codexId, "ponte di pietra").getOrThrow()

    val riletto = bruno.chats.observeMessages(chatId).first().single()
    assertEquals("ci vediamo alle sei", riletto.text)
    assertEquals("il messaggio non deve essere sparito nel frattempo", 1, bruno.db.messages.rows.value.size)
  }

  @Test
  fun `senza rito i messaggi restano chiusi e lo dicono`() = runTest {
    scambiaLeSchede()
    val chatId = ada.contacts.openConversation(bruno.codexId, "ponte di pietra").getOrThrow().chatId
    val messageId = ada.chats.send(chatId, "ci vediamo alle sei").getOrThrow()

    // Bruno non ha ancora fatto il rito: la chat esiste per lui solo perche' e' arrivato qualcosa.
    bruno.db.chats.upsert(ada.db.chats.rows.value.getValue(chatId).copy(title = "Ada"))
    consegna(messageId, chatId)

    val arrivato = bruno.chats.observeMessages(chatId).first().single()
    assertEquals(ChatMessage.Content.SEALED, arrivato.content)
    assertTrue(arrivato.isPlaceholder)
  }

  @Test
  fun `dopo il rito il contatto risulta aperto e la chat esiste`() = runTest {
    scambiaLeSchede()
    assertFalse(ada.contacts.observeContacts().first().single().hasKey)

    val chatId = ada.contacts.openConversation(bruno.codexId, "ponte di pietra").getOrThrow().chatId

    val contatto = ada.contacts.observeContacts().first().single()
    assertTrue("il rito e' stato fatto, deve vedersi", contatto.hasKey)
    assertEquals(chatId, contatto.chatId)

    val chat = ada.db.chats.rows.value.getValue(chatId)
    assertEquals(ChatKind.DIRECT, chat.kind)
    assertEquals("Bruno", chat.title)
    assertEquals(bruno.codexId, chat.peerId)
  }

  @Test
  fun `le impostazioni della chat sopravvivono a un secondo rito`() = runTest {
    scambiaLeSchede()
    val chatId = ada.contacts.openConversation(bruno.codexId, "prima parola").getOrThrow().chatId
    ada.chats.setTtl(chatId, 3600)

    ada.contacts.openConversation(bruno.codexId, "seconda parola").getOrThrow()

    assertEquals(3600, ada.db.chats.rows.value.getValue(chatId).ttlSeconds)
  }

  @Test
  fun `eliminare una persona porta via conversazione e chiave`() = runTest {
    scambiaLeSchede()
    val chatId = ada.contacts.openConversation(bruno.codexId, "ponte di pietra").getOrThrow().chatId
    ada.chats.send(chatId, "ciao").getOrThrow()

    ada.contacts.delete(bruno.codexId)

    assertTrue(ada.contacts.observeContacts().first().isEmpty())
    assertNull(ada.db.chats.rows.value[chatId])
    assertTrue(ada.db.messages.rows.value.isEmpty())
    assertTrue(
      "la chiave non si conserva 'nel caso'",
      ada.db.keyring.rows.value.keys.none { it.first == chatId },
    )
  }

  @Test
  fun `la propria scheda non si aggiunge`() = runTest {
    assertEquals(AddContactResult.Myself, ada.contacts.add(ada.code()))
    assertTrue(ada.db.contacts.rows.value.isEmpty())
  }

  @Test
  fun `un codice qualsiasi non entra`() = runTest {
    for (spazzatura in listOf("", "ciao", "CDX1:ZZZZ", "https://example.com/qr")) {
      assertEquals(
        "e' entrato: $spazzatura",
        AddContactResult.NotACard,
        ada.contacts.add(spazzatura),
      )
    }
  }

  @Test
  fun `una scheda gia' nota aggiorna il nome invece di duplicare`() = runTest {
    ada.contacts.add(bruno.code())
    val rinominato = FakeIdentitySource(bruno.identity, name = "Bruno II")
    val risultato = ada.contacts.add(dev.pampa.codex.crypto.ContactCardCode.encode(rinominato.signedCard()!!))

    assertTrue(risultato is AddContactResult.AlreadyKnown)
    assertEquals(1, ada.db.contacts.rows.value.size)
    assertEquals("Bruno II", ada.contacts.observeContacts().first().single().name)
  }

  @Test
  fun `chi cambia nome lo cambia anche nella lista delle chat`() = runTest {
    scambiaLeSchede()
    val chatId = ada.contacts.openConversation(bruno.codexId, "ponte di pietra").getOrThrow().chatId
    ada.chats.setTtl(chatId, 60)
    assertEquals("Bruno", ada.db.chats.rows.value.getValue(chatId).title)

    val rinominato = FakeIdentitySource(bruno.identity, name = "Bruno Rossi")
    ada.contacts.add(dev.pampa.codex.crypto.ContactCardCode.encode(rinominato.signedCard()!!))

    val chat = ada.db.chats.rows.value.getValue(chatId)
    assertEquals("Bruno Rossi", chat.title)
    // Il nome non c'entra con la chiave: l'emblema e le impostazioni non si toccano.
    assertEquals(60, chat.ttlSeconds)
  }

  @Test
  fun `a serratura chiusa non si legge e non si aggiunge`() = runTest {
    scambiaLeSchede()
    val chatId = ada.contacts.openConversation(bruno.codexId, "ponte di pietra").getOrThrow().chatId
    ada.chats.send(chatId, "un segreto").getOrThrow()
    ada.chats.forgetOpened()
    ada.source.locked = true

    assertEquals(AddContactResult.Locked, ada.contacts.add(bruno.code()))
    assertTrue(ada.contacts.openConversation(bruno.codexId, "ponte di pietra").isFailure)
    val messaggio = ada.chats.observeMessages(chatId).first().single()
    assertEquals(ChatMessage.Content.SEALED, messaggio.content)
    assertEquals("", messaggio.text)
  }

  /**
   * Il trasporto che ancora non c'e'.
   *
   * Copia la busta dal magazzino di chi ha scritto a quello di chi riceve, che e' esattamente
   * quello che fara' la sincronizzazione: **la busta viaggia chiusa**, e da questa parte nessuno
   * sa se si aprira'.
   */
  private suspend fun consegna(messageId: String, chatId: String) {
    val partito = ada.db.messages.rows.value.getValue(messageId)
    if (bruno.db.chats.rows.value[chatId] == null) {
      bruno.db.chats.upsert(ada.db.chats.rows.value.getValue(chatId))
    }
    bruno.db.messages.insert(
      partito.copy(outgoing = false, status = MessageStatus.DELIVERED, revealedOnce = false),
    )
  }
}
