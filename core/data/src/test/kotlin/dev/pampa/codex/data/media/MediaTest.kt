package dev.pampa.codex.data.media

import dev.pampa.codex.crypto.CodexIdentity
import dev.pampa.codex.crypto.ContactCard
import dev.pampa.codex.crypto.ContactCardCode
import dev.pampa.codex.crypto.MessageBody
import dev.pampa.codex.data.FakeDatabase
import dev.pampa.codex.data.FakeIdentitySource
import dev.pampa.codex.data.chat.ChatRepository
import dev.pampa.codex.data.cloud.AccountState
import dev.pampa.codex.data.cloud.CloudAccount
import dev.pampa.codex.data.cloud.CloudMessage
import dev.pampa.codex.data.cloud.CloudTransport
import dev.pampa.codex.data.cloud.NoDevices
import dev.pampa.codex.data.cloud.Receipt
import dev.pampa.codex.data.cloud.SyncEngine
import dev.pampa.codex.data.contacts.ContactRepository
import dev.pampa.codex.data.identity.Keyring
import dev.pampa.codex.model.Technique
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Le foto: dove finiscono e, soprattutto, **dove non finiscono**.
 *
 * La promessa di Codex sugli allegati e' che una copia in chiaro non esista da nessuna parte se non
 * nella memoria di chi la sta guardando. E' una promessa che si scrive in una riga e si perde in un
 * pomeriggio -- basta una cache "per andare piu' veloce" -- quindi qui non si controlla leggendo il
 * codice: si guardano **i byte del file che finisce sul disco**.
 *
 * L'altra meta' e' l'ordine della consegna. Un messaggio che nomina una foto non ancora caricata
 * arriva all'altra persona come una foto che non si scarichera' mai: con la rete che funziona non
 * succede mai, ed e' esattamente per questo che va provato qui.
 */
class MediaTest {

  @get:Rule
  val folder = TemporaryFolder()

  /** Chi ha fatto cosa, in ordine: e' l'unica cosa che il test sulla consegna deve sapere. */
  private val diario = mutableListOf<String>()

  /** Il deposito dei file, tenuto in memoria e con la manopola per farlo fallire. */
  private inner class Deposito : MediaStore {
    val caricati = mutableMapOf<String, ByteArray>()
    val cancellati = mutableListOf<String>()
    var esito: Result<Unit> = Result.success(Unit)

    override suspend fun upload(chatId: String, mediaId: String, encrypted: File): Result<Unit> {
      if (esito.isFailure) return esito
      diario += "file"
      caricati[mediaId] = encrypted.readBytes()
      return Result.success(Unit)
    }

    override suspend fun download(chatId: String, mediaId: String, into: File): Result<Unit> {
      val bytes = caricati[mediaId] ?: return Result.failure(IOException("non c'e'"))
      into.parentFile?.mkdirs()
      into.writeBytes(bytes)
      return Result.success(Unit)
    }

    override suspend fun delete(chatId: String, mediaId: String): Result<Unit> {
      cancellati += mediaId
      caricati.remove(mediaId)
      return Result.success(Unit)
    }
  }

  /** Il server dei messaggi, ridotto a quello che serve qui. */
  private inner class Corriere : CloudTransport {
    val consegnati = mutableListOf<CloudMessage>()
    val cancellati = mutableListOf<Pair<String, String>>()

    override suspend fun ensureGroup(
      chatId: String,
      memberUids: List<String>,
      adminUids: List<String>,
    ) = Result.success(Unit)

    override suspend fun ensureChat(chatId: String, memberUids: List<String>) = Result.success(Unit)

    override suspend fun send(message: CloudMessage): Result<Unit> {
      diario += "messaggio"
      consegnati += message
      return Result.success(Unit)
    }

    override fun observe(chatId: String): Flow<List<CloudMessage>> = flowOf(emptyList())

    override suspend fun delete(chatId: String, messageId: String): Result<Unit> {
      cancellati += chatId to messageId
      return Result.success(Unit)
    }

    override suspend fun reportReceipt(chatId: String, uid: String, receipt: Receipt) =
      Result.success(Unit)

    override fun observeReceipts(chatId: String): Flow<Map<String, Receipt>> = flowOf(emptyMap())
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
  private val deposito = Deposito()
  private val corriere = Corriere()
  private val conto = Conto(MIO_UID)
  private val vault by lazy { MediaVault(folder.newFolder("media")) }

  /** Un orologio che si puo' spingere avanti: le scadenze si provano cosi', non aspettando. */
  private var orologio = 1_700_000_000_000L

  private val chats by lazy {
    ChatRepository(
      chats = db.chats,
      messages = db.messages,
      identityRepository = source,
      keyring = keyring,
      outbox = db.outbox,
      vault = vault,
      mediaStore = deposito,
      transport = corriere,
      account = conto,
      clock = { orologio },
    )
  }
  private val contacts by lazy { ContactRepository(db.contacts, source, chats, keyring) }
  private val motore by lazy {
    SyncEngine(
      chats = chats,
      contacts = contacts,
      transport = corriere,
      mediaStore = deposito,
      account = conto,
      devices = NoDevices,
      showOnline = flowOf(false),
      scope = TestScope() as CoroutineScope,
    )
  }

  private val bruno = CodexIdentity.generate()

  private suspend fun conversazione(): String {
    val card = ContactCard.of(bruno, name = "Bruno", avatarSeed = 4, uid = SUO_UID)
    contacts.add(ContactCardCode.encode(card))
    return contacts.openConversation(bruno.codexId, "ponte di pietra").getOrThrow().chatId
  }

  /** Byte riconoscibili: se finissero in chiaro sul disco, si vedrebbero a occhio. */
  private val foto = "QUESTA-E-UNA-FOTO-RICONOSCIBILE".repeat(300).toByteArray()

  private suspend fun mandaFoto(chatId: String, viewOnce: Boolean = false): String =
    chats.sendMedia(
      chatId = chatId,
      kind = MessageBody.Media.Kind.PHOTO,
      mime = "image/jpeg",
      source = ByteArrayInputStream(foto),
      plainLength = foto.size.toLong(),
      width = 800,
      height = 600,
      caption = "il portico",
      viewOnce = viewOnce,
    ).getOrThrow()

  @Test
  fun `sul disco non finisce mai la foto in chiaro`() = runTest {
    val chatId = conversazione()
    val messageId = mandaFoto(chatId)

    val salvato = vault.file(messageId)
    assertTrue("il file cifrato deve esserci", salvato.isFile)
    assertFalse(
      "la foto non deve comparire in chiaro sul disco",
      String(salvato.readBytes(), Charsets.ISO_8859_1).contains("RICONOSCIBILE"),
    )
    // E si riapre: cifrato non vuol dire perso.
    assertTrue(chats.openMedia(messageId)!!.contentEquals(foto))
  }

  @Test
  fun `una foto e' un messaggio, con la sua didascalia`() = runTest {
    val chatId = conversazione()
    val messageId = mandaFoto(chatId)

    val messaggio = chats.observeMessages(chatId).first().single()
    assertEquals(messageId, messaggio.id)
    val media = requireNotNull(messaggio.media)
    assertEquals(MessageBody.Media.Kind.PHOTO, media.kind)
    assertEquals("image/jpeg", media.mime)
    assertEquals(800, media.width)
    assertEquals(foto.size.toLong(), media.size)
    assertEquals("il portico", messaggio.text)
  }

  @Test
  fun `il tipo del file non esce dalla busta`() = runTest {
    val chatId = conversazione()
    val messageId = mandaFoto(chatId)

    // Quello che il server vede e' la busta. Se dentro ci fosse "image/jpeg" in chiaro, chi
    // trasporta saprebbe chi si manda foto e chi si manda parole.
    val busta = String(db.messages.rows.value.getValue(messageId).envelope, Charsets.ISO_8859_1)
    assertFalse(busta.contains("image/jpeg"))
    assertFalse(busta.contains("portico"))
  }

  @Test
  fun `una foto non arriva mai chiusa nelle rune`() = runTest {
    val chatId = conversazione()
    // Le rune **sono** il testo in un altro alfabeto: davanti a un'immagine non avrebbero niente da
    // sciogliere. Si prova su molte foto perche' la tecnica nasce da un seme casuale.
    repeat(25) {
      val messageId = mandaFoto(chatId)
      val messaggio = db.messages.rows.value.getValue(messageId)
      assertFalse(
        "una foto chiusa nelle rune: $messageId",
        messaggio.technique == Technique.RUNE.name,
      )
    }
  }

  @Test
  fun `il file parte prima del messaggio`() = runTest {
    val chatId = conversazione()
    val messageId = mandaFoto(chatId)

    val inCoda = chats.pendingDeliveries().single()
    assertEquals(messageId, inCoda.messageId)
    assertNotNull("chi consegna deve sapere che c'e' un file da caricare", inCoda.mediaFile)

    motore.deliverPending(MIO_UID)
    assertEquals(listOf("file", "messaggio"), diario)
  }

  @Test
  fun `se il file non parte, non parte nemmeno il messaggio`() = runTest {
    val chatId = conversazione()
    val messageId = mandaFoto(chatId)
    deposito.esito = Result.failure(IOException("rete assente"))

    motore.deliverPending(MIO_UID)

    assertTrue("il messaggio non deve partire da solo", corriere.consegnati.isEmpty())
    assertEquals(
      "e deve restare in coda, per riprovare",
      messageId,
      chats.pendingDeliveries().single().messageId,
    )

    // Torna la rete: parte tutto, nell'ordine giusto.
    deposito.esito = Result.success(Unit)
    motore.deliverPending(MIO_UID)
    assertEquals(listOf("file", "messaggio"), diario)
    assertTrue(chats.pendingDeliveries().isEmpty())
  }

  @Test
  fun `chi riceve non scarica finche' non apre`() = runTest {
    val chatId = conversazione()
    val messageId = mandaFoto(chatId)
    motore.deliverPending(MIO_UID)
    // Si simula l'altro telefono: il messaggio c'e', il file no.
    vault.delete(messageId)

    assertFalse("il file non deve essere gia' qui", chats.hasMedia(messageId))
    assertTrue("aprendo, si scarica", chats.ensureMedia(messageId))
    assertTrue(chats.openMedia(messageId)!!.contentEquals(foto))
  }

  @Test
  fun `un visualizza una volta consumato non lascia niente`() = runTest {
    val chatId = conversazione()
    val messageId = mandaFoto(chatId, viewOnce = true)
    motore.deliverPending(MIO_UID)

    chats.burn(messageId)

    assertFalse("il file locale se ne va", vault.has(messageId))
    assertTrue("e anche quello del deposito", deposito.cancellati.contains(messageId))
    assertNull(chats.openMedia(messageId))
  }

  @Test
  fun `una foto scaduta non resta sul disco`() = runTest {
    val chatId = conversazione()
    chats.setTtl(chatId, 30)
    val messageId = mandaFoto(chatId)
    motore.deliverPending(MIO_UID)
    // La scadenza parte dall'apertura, non dall'invio: un messaggio mai letto non deve sparire.
    chats.markRevealed(messageId)

    orologio += 31_000
    chats.sweepExpired()

    assertFalse("il file se ne va con il messaggio", vault.has(messageId))
    assertTrue("e anche quello del deposito", deposito.cancellati.contains(messageId))
    assertTrue("il messaggio sul server non deve sopravvivere", corriere.cancellati.isNotEmpty())
    assertTrue(db.messages.rows.value.isEmpty())
  }

  @Test
  fun `una conversazione svuotata non lascia allegati`() = runTest {
    val chatId = conversazione()
    val messageId = mandaFoto(chatId)

    chats.clearChat(chatId)

    // Qui la chiave della conversazione **resta**: un file dimenticato sarebbe ancora apribile.
    assertFalse(vault.has(messageId))
  }

  @Test
  fun `un identificatore malizioso non esce dalla cartella`() {
    // Il `mediaId` arriva **dalla busta di un altro**: e' un dato autenticato, non uno di cui
    // fidarsi. Un id fatto di `../` scriverebbe dove gli pare.
    val cattivo = vault.file("../../fuori")
    assertEquals(
      folder.root.resolve("media").canonicalFile,
      cattivo.parentFile!!.canonicalFile,
    )
  }
}

private const val MIO_UID = "uid-ada"
private const val SUO_UID = "uid-bruno"
