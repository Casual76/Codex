package dev.pampa.codex.data.cloud

import dev.pampa.codex.crypto.CodexIdentity
import dev.pampa.codex.crypto.ContactCard
import dev.pampa.codex.crypto.ContactCardCode
import dev.pampa.codex.data.FakeDatabase
import dev.pampa.codex.data.FakeIdentitySource
import dev.pampa.codex.data.chat.ChatMessage
import dev.pampa.codex.data.chat.ChatRepository
import dev.pampa.codex.data.contacts.ContactRepository
import dev.pampa.codex.data.db.MessageStatus
import dev.pampa.codex.data.identity.Keyring
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La consegna: cosa succede a un messaggio dopo che e' stato scritto.
 *
 * E' la parte in cui un'app di messaggistica puo' fare il danno peggiore -- **perdere qualcosa in
 * silenzio** -- e quella che con un telefono solo in mano non si vede mai, perche' con la rete che
 * funziona va sempre bene. Qui il server e' finto e si puo' far rispondere come si vuole: che non
 * risponde, che rifiuta, che l'altra persona non esiste.
 */
class DeliveryTest {

  /** Un server finto, con la manopola per decidere come reagisce. */
  private class Corriere : CloudTransport {
    var esito: Result<Unit> = Result.success(Unit)
    var chatCreate: Result<Unit> = Result.success(Unit)
    val consegnati = mutableListOf<CloudMessage>()
    val chatPreparate = mutableListOf<Pair<String, List<String>>>()

    override suspend fun ensureGroup(
      chatId: String,
      memberUids: List<String>,
      adminUids: List<String>,
    ) = Result.success(Unit)

    override suspend fun ensureChat(chatId: String, memberUids: List<String>): Result<Unit> {
      chatPreparate += chatId to memberUids
      return chatCreate
    }

    override suspend fun send(message: CloudMessage): Result<Unit> {
      if (esito.isSuccess) consegnati += message
      return esito
    }

    override fun observe(chatId: String): Flow<List<CloudMessage>> = flowOf(emptyList())

    override suspend fun delete(chatId: String, messageId: String): Result<Unit> {
      cancellati += chatId to messageId
      return Result.success(Unit)
    }

    val ricevute = mutableListOf<Triple<String, String, Receipt>>()

    override suspend fun reportReceipt(chatId: String, uid: String, receipt: Receipt): Result<Unit> {
      ricevute += Triple(chatId, uid, receipt)
      return Result.success(Unit)
    }

    override fun observeReceipts(chatId: String): Flow<Map<String, Receipt>> = flowOf(emptyMap())

    val cancellati = mutableListOf<Pair<String, String>>()

    override suspend fun reportTyping(chatId: String, uid: String, until: Long) = Result.success(Unit)
    override fun observeTyping(chatId: String): Flow<Map<String, Long>> = flowOf(emptyMap())
    override suspend fun reportPresence(uid: String, online: Boolean) = Result.success(Unit)
    override fun observePresence(uid: String): Flow<Long> = flowOf(0L)
  }

  private class Conto(private val uid: String) : CloudAccount {
    override val state: StateFlow<AccountState> = MutableStateFlow(AccountState.SignedIn(uid))
    override fun uidOrNull(): String = uid
    override suspend fun signInWithGoogle(idToken: String) = Result.success(uid)
    override suspend fun signOut() = Unit
    override suspend fun deleteAccount() = Result.success(Unit)
  }

  private val db = FakeDatabase()
  private val identity = CodexIdentity.generate()
  private val source = FakeIdentitySource(identity, name = "Ada")
  private val keyring = Keyring(db.keyring, source)
  private val corriere = Corriere()
  private val conto = Conto(MIO_UID)
  private val chats = ChatRepository(
    chats = db.chats,
    messages = db.messages,
    identityRepository = source,
    keyring = keyring,
    outbox = db.outbox,
    transport = corriere,
    mediaStore = dev.pampa.codex.data.media.NoMediaStore,
    account = conto,
  )
  private val contacts = ContactRepository(db.contacts, source, chats, keyring)
  private val motore = SyncEngine(
    chats = chats,
    contacts = contacts,
    transport = corriere,
    mediaStore = dev.pampa.codex.data.media.NoMediaStore,
    account = conto,
    devices = NoDevices,
    showOnline = flowOf(true),
    scope = TestScope() as CoroutineScope,
  )

  private val bruno = CodexIdentity.generate()

  /** Bruno con un account: e' la condizione perche' gli si possa consegnare qualcosa. */
  private suspend fun brunoConAccount(uid: String = SUO_UID): String {
    val card = ContactCard.of(bruno, name = "Bruno", avatarSeed = 4, uid = uid)
    contacts.add(ContactCardCode.encode(card))
    return contacts.openConversation(bruno.codexId, "ponte di pietra").getOrThrow().chatId
  }

  @Test
  fun `un messaggio scritto entra in coda e poi parte`() = runTest {
    val chatId = brunoConAccount()
    val messageId = chats.send(chatId, "ci vediamo alle sei").getOrThrow()
    assertEquals("deve essere in coda subito", 1, db.outbox.rows.value.size)

    motore.deliverPending(MIO_UID)

    assertTrue("la coda deve essersi svuotata", db.outbox.rows.value.isEmpty())
    assertEquals(MessageStatus.SENT, db.messages.rows.value.getValue(messageId).status)
    val consegnato = corriere.consegnati.single()
    assertEquals(messageId, consegnato.messageId)
    assertEquals(identity.codexId, consegnato.senderCodexId)
    assertEquals(listOf(MIO_UID, SUO_UID), corriere.chatPreparate.single().second)
  }

  @Test
  fun `la busta che parte non contiene il testo`() = runTest {
    val chatId = brunoConAccount()
    chats.send(chatId, "ci vediamo alle sei").getOrThrow()
    motore.deliverPending(MIO_UID)

    val busta = corriere.consegnati.single().envelope
    assertFalse(
      "il testo non deve comparire in chiaro nella busta",
      String(busta, Charsets.ISO_8859_1).contains("vediamo"),
    )
  }

  @Test
  fun `senza rete il messaggio resta in coda e riprova`() = runTest {
    val chatId = brunoConAccount()
    val messageId = chats.send(chatId, "ciao").getOrThrow()
    corriere.esito = Result.failure(java.io.IOException("rete assente"))

    motore.deliverPending(MIO_UID)

    val inCoda = db.outbox.rows.value.getValue(messageId)
    assertEquals("il tentativo deve essere contato", 1, inCoda.attempts)
    assertEquals(MessageStatus.PENDING, db.messages.rows.value.getValue(messageId).status)

    // Torna la rete: al giro dopo parte, senza che nessuno abbia dovuto riscriverlo.
    corriere.esito = Result.success(Unit)
    motore.deliverPending(MIO_UID)
    assertTrue(db.outbox.rows.value.isEmpty())
    assertEquals(MessageStatus.SENT, db.messages.rows.value.getValue(messageId).status)
  }

  @Test
  fun `un rifiuto del server non si ritenta all'infinito`() = runTest {
    val chatId = brunoConAccount()
    val messageId = chats.send(chatId, "ciao").getOrThrow()
    // Il server dice di no e continuera' a dirlo: insistere sarebbe solo batteria.
    corriere.esito = Result.failure(TransportRefused("il server ha rifiutato la scrittura"))

    motore.deliverPending(MIO_UID)

    assertTrue("fuori dalla coda", db.outbox.rows.value.isEmpty())
    assertEquals(MessageStatus.FAILED, db.messages.rows.value.getValue(messageId).status)
  }

  @Test
  fun `a chi non ha ancora un account il messaggio resta in attesa`() = runTest {
    // Due persone che si sono scambiate le schede di persona, prima che una collegasse un account.
    val chatId = brunoConAccount(uid = "")
    val messageId = chats.send(chatId, "ciao").getOrThrow()

    motore.deliverPending(MIO_UID)

    // Non e' un fallimento: e' un'attesa. Il messaggio resta in coda **senza** spunta rossa, e
    // partira' da solo il giorno in cui quella persona collega Codex a un account.
    assertEquals(1, db.outbox.rows.value.size)
    assertEquals(MessageStatus.PENDING, db.messages.rows.value.getValue(messageId).status)
    assertTrue("non si deve nemmeno provare a consegnare", corriere.consegnati.isEmpty())
  }

  @Test
  fun `le note a se stessi non passano dal server`() = runTest {
    val selfId = chats.ensureSelfChat("Note")
    chats.send(selfId, "promemoria").getOrThrow()

    assertTrue("non c'e' niente da consegnare a nessuno", db.outbox.rows.value.isEmpty())
    motore.deliverPending(MIO_UID)
    assertTrue(corriere.consegnati.isEmpty())
  }

  @Test
  fun `lo stesso messaggio non entra due volte`() = runTest {
    val chatId = brunoConAccount()
    suspend fun arrivato(): Boolean = chats.receive(
      chatId = chatId,
      messageId = "m-1",
      senderCodexId = bruno.codexId,
      envelope = ByteArray(32) { 7 },
      viewOnce = false,
      createdAt = 100,
    )

    assertTrue("la prima volta entra", arrivato())
    // Firestore riconsegna tutto a ogni riattacco: senza questo controllo la conversazione si
    // riempirebbe di doppioni a ogni avvio dell'app.
    assertFalse("la seconda no", arrivato())
    assertEquals(1, db.messages.rows.value.size)
  }

  @Test
  fun `un messaggio ricevuto e' chiuso e non e' mio`() = runTest {
    val chatId = brunoConAccount()
    chats.receive(
      chatId = chatId,
      messageId = "m-1",
      senderCodexId = bruno.codexId,
      envelope = ByteArray(32) { 7 },
      viewOnce = false,
      createdAt = 100,
    )

    val riga = db.messages.rows.value.getValue("m-1")
    assertFalse("arriva da un altro", riga.outgoing)
    assertEquals(MessageStatus.DELIVERED, riga.status)
    // La busta e' finta, quindi non si apre: deve dirlo, non sparire.
    val visto = chats.observeMessages(chatId).first().single()
    assertEquals(ChatMessage.Content.UNREADABLE, visto.content)
  }

  @Test
  fun `cancellare una conversazione svuota anche la sua coda`() = runTest {
    val chatId = brunoConAccount()
    chats.send(chatId, "ciao").getOrThrow()
    assertEquals(1, db.outbox.rows.value.size)

    chats.deleteChat(chatId)

    assertTrue("una coda per una chat che non c'e' piu' non ha senso", db.outbox.rows.value.isEmpty())
    assertNull(db.chats.rows.value[chatId])
  }

  @Test
  fun `le spunte seguono fin dove l'altro e' arrivato`() = runTest {
    val chatId = brunoConAccount()
    val primo = chats.send(chatId, "primo").getOrThrow()
    motore.deliverPending(MIO_UID)
    assertEquals(MessageStatus.SENT, db.messages.rows.value.getValue(primo).status)

    val quando = db.messages.rows.value.getValue(primo).createdAt

    chats.applyPeerReceipt(chatId, deliveredAt = quando, readAt = 0)
    assertEquals(MessageStatus.DELIVERED, db.messages.rows.value.getValue(primo).status)

    chats.applyPeerReceipt(chatId, deliveredAt = quando, readAt = quando)
    assertEquals(MessageStatus.READ, db.messages.rows.value.getValue(primo).status)

    // Una ricevuta vecchia che arriva in ritardo non deve **disfare** una spunta gia' data.
    chats.applyPeerReceipt(chatId, deliveredAt = quando, readAt = 0)
    assertEquals(MessageStatus.READ, db.messages.rows.value.getValue(primo).status)
  }

  @Test
  fun `una ricevuta non tocca i messaggi che non ho scritto io`() = runTest {
    val chatId = brunoConAccount()
    chats.receive(
      chatId = chatId,
      messageId = "suo",
      senderCodexId = bruno.codexId,
      envelope = ByteArray(16) { 3 },
      viewOnce = false,
      createdAt = 500,
    )

    chats.applyPeerReceipt(chatId, deliveredAt = 1_000, readAt = 1_000)

    // "Letto" e' una cosa che dice **l'altro dei miei**: sui suoi non significherebbe niente.
    assertEquals(MessageStatus.DELIVERED, db.messages.rows.value.getValue("suo").status)
  }

  @Test
  fun `ricevere registra subito che e' arrivato, anche senza rete`() = runTest {
    val chatId = brunoConAccount()
    chats.receive(
      chatId = chatId,
      messageId = "suo",
      senderCodexId = bruno.codexId,
      envelope = ByteArray(16) { 3 },
      viewOnce = false,
      createdAt = 700,
    )
    assertEquals(700, db.chats.rows.value.getValue(chatId).myDeliveredAt)

    // Aprire la conversazione segna la lettura: la ricevuta partira' quando c'e' rete, non ora.
    chats.clearUnread(chatId)
    assertTrue(db.chats.rows.value.getValue(chatId).myReadAt > 0)
  }

  @Test
  fun `cancellare un mio messaggio lo toglie anche dal server`() = runTest {
    val chatId = brunoConAccount()
    val messageId = chats.send(chatId, "ci ripenso").getOrThrow()
    motore.deliverPending(MIO_UID)

    chats.deleteMessage(messageId)

    assertEquals(chatId to messageId, corriere.cancellati.single())
    assertTrue(db.messages.rows.value.isEmpty())
  }

  @Test
  fun `un visualizza una volta consumato sparisce anche dal server`() = runTest {
    val chatId = brunoConAccount()
    chats.receive(
      chatId = chatId,
      messageId = "una-volta",
      senderCodexId = bruno.codexId,
      envelope = ByteArray(16) { 5 },
      viewOnce = true,
      createdAt = 100,
    )

    chats.burnViewed(listOf("una-volta"))

    // La copia sul server e' proprio quella che chi ha scritto pensava di aver reso effimera:
    // lasciarla li' renderebbe la promessa mezza vera, che e' peggio che non farla.
    assertEquals(chatId to "una-volta", corriere.cancellati.single())
    assertTrue(db.messages.rows.value.getValue("una-volta").burned)
  }

  @Test
  fun `la conversazione si legge a pagine, dal fondo`() = runTest {
    val chatId = brunoConAccount()
    repeat(5) { indice ->
      chats.receive(
        chatId = chatId,
        messageId = "m-$indice",
        senderCodexId = bruno.codexId,
        envelope = ByteArray(8) { 1 },
        viewOnce = false,
        createdAt = (indice + 1) * 100L,
      )
    }

    val ultimi = chats.observeMessages(chatId, limit = 2).first()

    // Due soli, e sono **gli ultimi due**, nell'ordine in cui si leggono: una chat si apre dove si
    // era rimasti, non all'inizio di tutto.
    assertEquals(listOf("m-3", "m-4"), ultimi.map { it.id })
  }

  private companion object {
    const val MIO_UID = "uid-ada"
    const val SUO_UID = "uid-bruno"
  }
}
