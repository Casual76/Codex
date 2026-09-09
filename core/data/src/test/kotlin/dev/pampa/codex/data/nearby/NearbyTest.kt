package dev.pampa.codex.data.nearby

import dev.pampa.codex.crypto.CodexIdentity
import dev.pampa.codex.crypto.ContactCard
import dev.pampa.codex.crypto.ContactCardCode
import dev.pampa.codex.crypto.MessageBody
import dev.pampa.codex.data.FakeDatabase
import dev.pampa.codex.data.FakeIdentitySource
import dev.pampa.codex.data.chat.ChatMessage
import dev.pampa.codex.data.chat.ChatRepository
import dev.pampa.codex.data.contacts.ContactRepository
import dev.pampa.codex.data.db.MessageStatus
import dev.pampa.codex.data.identity.Keyring
import dev.pampa.codex.data.media.MediaVault
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Due telefoni vicini, in aereo.
 *
 * E' la milestone che **non si puo' provare da soli**: Nearby vuole due dispositivi veri, con due
 * antenne vere. Quello che si puo' fare -- e che vale piu' di quanto sembri -- e' provare il
 * **protocollo**: chi si collega a chi, chi viene lasciato fuori, cosa passa sul filo. Un filo finto
 * mette due motori nello stesso test e li fa parlare davvero, con le firme vere e le buste vere.
 *
 * Quello che resta da provare con due telefoni in mano e' che l'antenna si accenda. Il resto e' qui.
 */
class NearbyTest {

  @get:Rule
  val folder = TemporaryFolder()

  /**
   * Un filo che collega due motori.
   *
   * Ognuno dei due vede l'altro come un endpoint. I byte passano subito, nello stesso filo di
   * esecuzione: un test resta una sequenza di fatti invece che una gara con un orologio.
   */
  private inner class Filo {

    /**
     * Quello che e' partito e non e' ancora arrivato.
     *
     * **Il filo consegna in ordine, uno alla volta, e non dentro la consegna di un altro.** Il primo
     * filo finto consegnava subito, in modo ricorsivo: la risposta a un pacchetto arrivava mentre il
     * destinatario stava ancora leggendo il pacchetto precedente. Nearby non fa cosi' -- i pacchetti
     * su un canale sono una fila -- e la stretta di mano falliva per un intreccio che nella realta'
     * non capita mai. Un filo finto che mente cosi' e' peggio di nessun filo finto.
     */
    val coda = ArrayDeque<suspend () -> Unit>()

    suspend fun consegna() {
      var giri = 0
      while (coda.isNotEmpty() && giri < 200) {
        coda.removeFirst().invoke()
        giri += 1
      }
    }

    /** Un capo del filo: quello che il motore vede come `NearbyLink`. */
    inner class Capo(private val nome: String) : NearbyLink {
      var altro: Capo? = null
      var motore: NearbyEngine? = null
      var attaccato = true
      val mandati = mutableListOf<NearbyPacket>()

      override fun start(localName: String): Flow<NearbyEvent> = flowOf()

      override suspend fun send(endpointId: String, bytes: ByteArray): Result<Unit> {
        if (!attaccato) return Result.failure(java.io.IOException("scollegato"))
        NearbyPacketCodec.decode(bytes)?.let { mandati += it }
        val destinatario = altro ?: return Result.failure(IllegalStateException("nessuno"))
        coda += { destinatario.motore?.handle(NearbyEvent.Received(nome, bytes)) }
        return Result.success(Unit)
      }

      override suspend fun sendFile(endpointId: String, mediaId: String, file: File): Result<Unit> {
        if (!attaccato) return Result.failure(java.io.IOException("scollegato"))
        val destinatario = altro ?: return Result.failure(IllegalStateException("nessuno"))
        // Il file arriva **gia' cifrato**, com'e' su disco: il filo non lo apre e non lo tocca.
        val copia = folder.newFile("transito-${mediaId.take(8)}-${System.nanoTime()}")
        file.copyTo(copia, overwrite = true)
        coda += { destinatario.motore?.handle(NearbyEvent.FileReceived(nome, mediaId, copia)) }
        return Result.success(Unit)
      }

      override fun disconnect(endpointId: String) {
        attaccato = false
      }

      override fun stop() {
        attaccato = false
      }
    }
  }

  private val filo = Filo()

  /** Un telefono: magazzino, identita', motore delle vicinanze. */
  private inner class Telefono(val nome: String, capo: Filo.Capo) {
    val identity = CodexIdentity.generate()
    val db = FakeDatabase()
    val source = FakeIdentitySource(identity, name = nome)
    val keyring = Keyring(db.keyring, source)
    val vault = MediaVault(folder.newFolder("media-$nome-${System.nanoTime()}"))
    val chats = ChatRepository(
      chats = db.chats,
      messages = db.messages,
      identityRepository = source,
      keyring = keyring,
      outbox = db.outbox,
      vault = vault,
    )
    val contacts = ContactRepository(db.contacts, source, chats, keyring)
    val link = capo
    val engine = NearbyEngine(
      link = capo,
      chats = chats,
      contacts = db.contacts,
      identityRepository = source,
      vault = vault,
      mode = flowOf(NearbyMode.FOREGROUND),
      scope = CoroutineScope(Dispatchers.Unconfined),
    )

    init {
      capo.motore = engine
    }

    val codexId: String get() = identity.codexId

    suspend fun conosce(altro: Telefono) {
      contacts.add(
        ContactCardCode.encode(
          ContactCard.of(altro.identity, name = altro.nome, avatarSeed = 2),
        ),
      )
    }

    suspend fun testi(chatId: String): List<String> =
      chats.observeMessages(chatId).first()
        .filter { it.content == ChatMessage.Content.OPEN && !it.burned }
        .map { it.text }
  }

  private lateinit var ada: Telefono
  private lateinit var bruno: Telefono

  /**
   * I due telefoni si costruiscono **qui** e non fra i campi della classe.
   *
   * La cartella temporanea di JUnit non esiste ancora mentre i campi si inizializzano -- la crea la
   * regola, che gira dopo -- e i magazzini dei media hanno bisogno di una cartella vera.
   */
  @Before
  fun before() {
    val capoAda = filo.Capo("ada")
    val capoBruno = filo.Capo("bruno")
    capoAda.altro = capoBruno
    capoBruno.altro = capoAda
    ada = Telefono("Ada", capoAda)
    bruno = Telefono("Bruno", capoBruno)
  }

  /** I due si conoscono e hanno fatto il rito: e' il punto di partenza di quasi tutto. */
  private suspend fun siConoscono(): String {
    ada.conosce(bruno)
    bruno.conosce(ada)
    val chatId = ada.contacts.openConversation(bruno.codexId, "ponte di pietra").getOrThrow().chatId
    bruno.contacts.openConversation(ada.codexId, "ponte di pietra").getOrThrow()
    return chatId
  }

  /** La stretta di mano, dai due lati: e' quello che nell'app fa l'evento di connessione. */
  private suspend fun siCollegano() {
    ada.engine.handle(NearbyEvent.Connected("bruno"))
    bruno.engine.handle(NearbyEvent.Connected("ada"))
    filo.consegna()
  }

  @Test
  fun `due che si conoscono si riconoscono`() = runTest {
    siConoscono()
    siCollegano()

    assertEquals(setOf(bruno.codexId), ada.engine.nearbyPeople.value)
    assertEquals(setOf(ada.codexId), bruno.engine.nearbyPeople.value)
  }

  @Test
  fun `uno sconosciuto viene scollegato`() = runTest {
    // Nessuno dei due ha la scheda dell'altro: le vicinanze non servono a **conoscersi**, servono a
    // parlarsi. Per conoscersi ci sono il QR e il codice, guardandosi in faccia.
    siCollegano()

    assertTrue(ada.engine.nearbyPeople.value.isEmpty())
    assertTrue(bruno.engine.nearbyPeople.value.isEmpty())
    assertFalse(ada.link.attaccato)
  }

  @Test
  fun `chi conosce solo da una parte non passa`() = runTest {
    // Ada ha la scheda di Bruno, Bruno non ha quella di Ada. Bruno chiude, e nessuno dei due finisce
    // per considerare l'altro autenticato.
    ada.conosce(bruno)
    siCollegano()

    assertTrue(bruno.engine.nearbyPeople.value.isEmpty())
    assertTrue(ada.engine.nearbyPeople.value.isEmpty())
  }

  @Test
  fun `un messaggio passa senza rete`() = runTest {
    val chatId = siConoscono()
    siCollegano()

    // Nessun trasporto verso il cloud: questi due repository non ne hanno uno. E' l'aereo.
    ada.chats.send(chatId, "sono al binario tre").getOrThrow()
    ada.engine.deliverPending()
    filo.consegna()

    assertEquals(listOf("sono al binario tre"), bruno.testi(chatId))
  }

  @Test
  fun `la spunta arriva a chi ha mandato`() = runTest {
    val chatId = siConoscono()
    siCollegano()
    val messageId = ada.chats.send(chatId, "ci sei?").getOrThrow()
    ada.engine.deliverPending()
    filo.consegna()

    assertEquals(MessageStatus.DELIVERED, ada.db.messages.rows.value.getValue(messageId).status)
  }

  @Test
  fun `il messaggio resta in coda per il cloud`() = runTest {
    val chatId = siConoscono()
    siCollegano()
    val messageId = ada.chats.send(chatId, "ci vediamo dopo").getOrThrow()
    ada.engine.deliverPending()
    filo.consegna()

    // Consegnato di persona **e** ancora da portare al server: l'altra persona puo' avere un secondo
    // dispositivo, e la conversazione dev'essere intera anche riaperta altrove.
    assertEquals(listOf(messageId), ada.chats.pendingDeliveries().map { it.messageId })
  }

  @Test
  fun `quando torna la rete il messaggio non si sdoppia`() = runTest {
    val chatId = siConoscono()
    siCollegano()
    val messageId = ada.chats.send(chatId, "una volta sola").getOrThrow()
    ada.engine.deliverPending()
    filo.consegna()

    // Torna la rete: lo stesso messaggio arriva a Bruno anche dal cloud. Ha lo stesso
    // identificatore, e chi ce l'ha gia' non lo prende due volte.
    val uscente = ada.chats.pendingDeliveries().single()
    bruno.chats.receive(
      chatId = uscente.chatId,
      messageId = uscente.messageId,
      senderCodexId = uscente.senderCodexId,
      envelope = uscente.envelope,
      viewOnce = uscente.viewOnce,
      createdAt = uscente.createdAt,
    )

    assertEquals(listOf("una volta sola"), bruno.testi(chatId))
    assertEquals(1, bruno.db.messages.rows.value.size)
    assertEquals(messageId, bruno.db.messages.rows.value.keys.single())
  }

  @Test
  fun `la spunta non torna indietro quando passa anche dal cloud`() = runTest {
    val chatId = siConoscono()
    siCollegano()
    val messageId = ada.chats.send(chatId, "prima di persona").getOrThrow()
    ada.engine.deliverPending()
    filo.consegna()

    ada.chats.deliverySucceeded(messageId)

    // "Spedito" dopo "consegnato" sarebbe un passo indietro sotto gli occhi di chi ha scritto.
    assertEquals(MessageStatus.DELIVERED, ada.db.messages.rows.value.getValue(messageId).status)
    assertTrue(ada.chats.pendingDeliveries().isEmpty())
  }

  @Test
  fun `una foto passa dal filo dei file, e non in chiaro`() = runTest {
    val chatId = siConoscono()
    siCollegano()

    val foto = "FOTO-RICONOSCIBILE-DA-VICINO".repeat(200).toByteArray()
    val messageId = ada.chats.sendMedia(
      chatId = chatId,
      kind = MessageBody.Media.Kind.PHOTO,
      mime = "image/jpeg",
      source = ByteArrayInputStream(foto),
      plainLength = foto.size.toLong(),
      width = 640,
      height = 480,
      caption = "il binario",
    ).getOrThrow()
    ada.engine.deliverPending()
    filo.consegna()

    // Il messaggio e' arrivato con la sua didascalia...
    assertEquals(listOf("il binario"), bruno.testi(chatId))
    // ...e il file e' su disco da Bruno, **cifrato**, e si riapre con la chiave della conversazione.
    assertTrue(bruno.chats.hasMedia(messageId))
    val salvato = bruno.vault.file(messageId)
    assertFalse(String(salvato.readBytes(), Charsets.ISO_8859_1).contains("RICONOSCIBILE"))
    assertTrue(bruno.chats.openMedia(messageId)!!.contentEquals(foto))
  }

  @Test
  fun `non si consegna a nome di un altro`() = runTest {
    val chatId = siConoscono()
    siCollegano()

    // Bruno prova a infilare nella conversazione un messaggio che dice di venire da Ada. La busta
    // sarebbe pure apribile -- la chiave di quella chat ce l'ha -- ma il mittente non e' lui.
    val messageId = bruno.chats.send(chatId, "questo non lo ho scritto io").getOrThrow()
    val busta = bruno.db.messages.rows.value.getValue(messageId).envelope
    ada.engine.handle(
      NearbyEvent.Received(
        "bruno",
        NearbyPacketCodec.encode(
          NearbyPacket.Message(
            chatId = chatId,
            messageId = "inventato",
            senderCodexId = ada.codexId,
            envelope = busta,
            viewOnce = false,
            createdAt = 1,
          ),
        ),
      ),
    )
    filo.consegna()

    assertFalse(ada.db.messages.rows.value.containsKey("inventato"))
  }

  @Test
  fun `un pacchetto senza stretta di mano non entra`() = runTest {
    val chatId = siConoscono()
    // Collegato ma non ancora autenticato: nessuna firma, nessun messaggio.
    ada.engine.handle(NearbyEvent.Connected("bruno"))

    ada.engine.handle(
      NearbyEvent.Received(
        "bruno",
        NearbyPacketCodec.encode(
          NearbyPacket.Message(
            chatId = chatId,
            messageId = "prima-della-firma",
            senderCodexId = bruno.codexId,
            envelope = ByteArray(64),
            viewOnce = false,
            createdAt = 1,
          ),
        ),
      ),
    )

    assertFalse(ada.db.messages.rows.value.containsKey("prima-della-firma"))
  }

  @Test
  fun `dei byte qualsiasi non chiudono il canale`() = runTest {
    siConoscono()
    siCollegano()

    // Sul filo passa di tutto: una cosa che non si capisce si butta, e i due restano collegati.
    ada.engine.handle(NearbyEvent.Received("bruno", ByteArray(50) { 9 }))
    filo.consegna()

    assertEquals(setOf(bruno.codexId), ada.engine.nearbyPeople.value)
  }

  @Test
  fun `chi se ne va sparisce dai vicini`() = runTest {
    siConoscono()
    siCollegano()
    assertEquals(setOf(bruno.codexId), ada.engine.nearbyPeople.value)

    ada.engine.handle(NearbyEvent.Disconnected("bruno"))

    assertTrue(ada.engine.nearbyPeople.value.isEmpty())
  }

  @Test
  fun `un pacchetto torna indietro com'era`() {
    val messaggio = NearbyPacket.Message(
      chatId = "d-prova",
      messageId = "m-1",
      senderCodexId = "CDX-AAAA-1111",
      envelope = ByteArray(300) { it.toByte() },
      viewOnce = true,
      createdAt = 1_700_000_000_000,
      mediaId = "m-1",
    )
    assertEquals(messaggio, NearbyPacketCodec.decode(NearbyPacketCodec.encode(messaggio)))
    assertEquals(
      NearbyPacket.Ack("m-2"),
      NearbyPacketCodec.decode(NearbyPacketCodec.encode(NearbyPacket.Ack("m-2"))),
    )
    assertEquals(null, NearbyPacketCodec.decode("niente".toByteArray()))
  }
}
