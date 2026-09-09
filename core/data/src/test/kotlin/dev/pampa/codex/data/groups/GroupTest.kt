package dev.pampa.codex.data.groups

import dev.pampa.codex.crypto.CodexIdentity
import dev.pampa.codex.crypto.ContactCard
import dev.pampa.codex.crypto.ContactCardCode
import dev.pampa.codex.crypto.GroupSecret
import dev.pampa.codex.data.FakeDatabase
import dev.pampa.codex.data.FakeIdentitySource
import dev.pampa.codex.data.chat.ChatMessage
import dev.pampa.codex.data.chat.ChatRepository
import dev.pampa.codex.data.cloud.AccountState
import dev.pampa.codex.data.cloud.CloudAccount
import dev.pampa.codex.data.cloud.CloudMessage
import dev.pampa.codex.data.cloud.CloudTransport
import dev.pampa.codex.data.cloud.InboxItem
import dev.pampa.codex.data.cloud.InboxTransport
import dev.pampa.codex.data.cloud.Receipt
import dev.pampa.codex.data.contacts.ContactRepository
import dev.pampa.codex.data.identity.Keyring
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Un gruppo, con tre telefoni finti e una chiave che viaggia davvero.
 *
 * E' la milestone in cui **un segreto si sposta**: fra due persone la chiave nasce da un accordo e
 * non passa da nessuna parte, fra tre no. Tutto quello che puo' andare storto in un gruppo va storto
 * qui dentro, dove si vede: una chiave consegnata a chi non doveva, una rotazione che non arriva, un
 * passato che diventa illeggibile perche' qualcuno se n'e' andato.
 *
 * I tre "telefoni" sono tre magazzini in memoria con tre identita' diverse, legati da una posta e da
 * un corriere condivisi. Nessuno dei tre puo' leggere il database dell'altro.
 */
class GroupTest {

  /** La posta condivisa: le buste stanno li' finche' qualcuno non le raccoglie. */
  private class Posta : InboxTransport {
    val caselle = mutableMapOf<String, MutableList<InboxItem>>()
    var consegna = true

    override suspend fun send(toUid: String, itemId: String, parcel: ByteArray): Result<Unit> {
      if (!consegna) return Result.failure(java.io.IOException("rete assente"))
      caselle.getOrPut(toUid) { mutableListOf() } += InboxItem(itemId, mittente, parcel, 0)
      return Result.success(Unit)
    }

    /** Chi sta scrivendo adesso: nella realta' lo mette il server dall'account di chi scrive. */
    var mittente: String = ""

    override fun observe(uid: String): Flow<List<InboxItem>> = flowOf(caselle[uid].orEmpty())

    override suspend fun delete(uid: String, itemId: String): Result<Unit> {
      caselle[uid]?.removeAll { it.id == itemId }
      return Result.success(Unit)
    }

    fun perDi(uid: String): List<InboxItem> = caselle[uid].orEmpty().toList()
  }

  /** Il corriere dei messaggi: tiene quello che gli si da', per chat. */
  private class Corriere : CloudTransport {
    val messaggi = mutableMapOf<String, MutableList<CloudMessage>>()
    val gruppi = mutableMapOf<String, Pair<List<String>, List<String>>>()

    override suspend fun ensureChat(chatId: String, memberUids: List<String>) = Result.success(Unit)

    override suspend fun ensureGroup(
      chatId: String,
      memberUids: List<String>,
      adminUids: List<String>,
    ): Result<Unit> {
      gruppi[chatId] = memberUids to adminUids
      return Result.success(Unit)
    }

    override suspend fun send(message: CloudMessage): Result<Unit> {
      messaggi.getOrPut(message.chatId) { mutableListOf() } += message
      return Result.success(Unit)
    }

    override fun observe(chatId: String): Flow<List<CloudMessage>> =
      flowOf(messaggi[chatId].orEmpty())

    override suspend fun delete(chatId: String, messageId: String): Result<Unit> {
      messaggi[chatId]?.removeAll { it.messageId == messageId }
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

  private val posta = Posta()
  private val corriere = Corriere()

  /** Un telefono: la sua identita', il suo magazzino, i suoi repository. */
  private inner class Telefono(val nome: String, val uid: String) {
    val identity = CodexIdentity.generate()
    val db = FakeDatabase()
    val source = FakeIdentitySource(identity, name = nome)
    val keyring = Keyring(db.keyring, source)
    val conto = Conto(uid)
    val chats = ChatRepository(
      chats = db.chats,
      messages = db.messages,
      identityRepository = source,
      keyring = keyring,
      outbox = db.outbox,
      transport = corriere,
      account = conto,
    )
    val contacts = ContactRepository(db.contacts, source, chats, keyring)
    val groups = GroupRepository(
      members = db.groupMembers,
      chats = chats,
      contacts = contacts,
      contactRows = db.contacts,
      identityRepository = source,
      keyring = keyring,
      inbox = posta,
      transport = corriere,
      account = conto,
      // Le consegne, che nell'app vanno in background, qui succedono sul filo del test: cosi' un
      // test resta una sequenza di fatti invece che una gara con un orologio.
      scope = CoroutineScope(Dispatchers.Unconfined),
    )

    val codexId: String get() = identity.codexId

    suspend fun conosce(altro: Telefono) {
      contacts.add(
        ContactCardCode.encode(
          ContactCard.of(altro.identity, name = altro.nome, avatarSeed = 1, uid = altro.uid),
        ),
      )
    }

    /** Ritira la posta: e' quello che nell'app fa il motore, in un ciclo. */
    suspend fun ritiraPosta() {
      posta.perDi(uid).forEach { item ->
        if (groups.handle(item)) posta.delete(uid, item.id)
      }
    }

    /** Scrive nel gruppo, e il messaggio arriva al corriere come farebbe il motore. */
    suspend fun scrive(groupId: String, testo: String): String {
      val id = chats.send(groupId, testo).getOrThrow()
      chats.pendingDeliveries().filter { it.messageId == id }.forEach { uscente ->
        corriere.send(
          CloudMessage(
            chatId = uscente.chatId,
            messageId = uscente.messageId,
            senderUid = uid,
            senderCodexId = uscente.senderCodexId,
            envelope = uscente.envelope,
            viewOnce = uscente.viewOnce,
            createdAt = uscente.createdAt,
          ),
        )
        chats.deliverySucceeded(uscente.messageId)
      }
      return id
    }

    /** Tira dentro quello che c'e' sul corriere per questa conversazione. */
    suspend fun riceve(groupId: String) {
      corriere.messaggi[groupId].orEmpty().forEach { messaggio ->
        chats.receive(
          chatId = messaggio.chatId,
          messageId = messaggio.messageId,
          senderCodexId = messaggio.senderCodexId,
          envelope = messaggio.envelope,
          viewOnce = messaggio.viewOnce,
          createdAt = messaggio.createdAt,
        )
      }
    }

    suspend fun testi(groupId: String): List<String> =
      chats.observeMessages(groupId).first()
        .filter { it.content == ChatMessage.Content.OPEN && !it.burned }
        .map { it.text }

    suspend fun sigillati(groupId: String): Int =
      chats.observeMessages(groupId).first().count { it.content == ChatMessage.Content.SEALED }
  }

  private val ada = Telefono("Ada", "uid-ada")
  private val bruno = Telefono("Bruno", "uid-bruno")
  private val carla = Telefono("Carla", "uid-carla")

  /** Tutti si conoscono, e Ada fa il gruppo. Il punto di partenza di quasi ogni prova. */
  private suspend fun gruppoATre(): String {
    listOf(ada to bruno, ada to carla, bruno to ada, bruno to carla, carla to ada, carla to bruno)
      .forEach { (chi, quale) -> chi.conosce(quale) }
    posta.mittente = ada.uid
    val groupId = ada.groups.create("Quelli del giovedi'", listOf(bruno.codexId, carla.codexId))
      .getOrThrow()
    bruno.ritiraPosta()
    carla.ritiraPosta()
    return groupId
  }

  @Test
  fun `la chiave arriva a chi e' del gruppo`() = runTest {
    val groupId = gruppoATre()

    assertTrue(GroupSecret.isGroupId(groupId))
    // Tutti e tre hanno la stessa chiave: la prova e' che si leggono a vicenda.
    ada.scrive(groupId, "ci vediamo alle sei")
    bruno.riceve(groupId)
    carla.riceve(groupId)
    assertEquals(listOf("ci vediamo alle sei"), bruno.testi(groupId))
    assertEquals(listOf("ci vediamo alle sei"), carla.testi(groupId))
  }

  @Test
  fun `il nome del gruppo non passa dal server`() = runTest {
    val groupId = gruppoATre()

    // Il nome arriva **dentro** la busta della posta, che e' cifrata. Sul corriere ci sono solo gli
    // account dei membri: chi trasporta non sa di cosa si parla nemmeno dal titolo.
    assertEquals("Quelli del giovedi'", bruno.chats.chatById(groupId)?.title)
    val buste = posta.caselle.values.flatten().map { String(it.parcel, Charsets.ISO_8859_1) }
    assertTrue(buste.none { it.contains("giovedi") })
    assertEquals(setOf(ada.uid, bruno.uid, carla.uid), corriere.gruppi[groupId]?.first?.toSet())
  }

  @Test
  fun `chi non e' del gruppo non ci capisce niente`() = runTest {
    val groupId = gruppoATre()
    ada.scrive(groupId, "il posto di sempre")

    // Un quarto telefono che riesca a farsi passare i documenti del gruppo (per assurdo: le regole
    // non glielo lascerebbero fare) ha in mano delle buste e nessuna chiave.
    val dario = Telefono("Dario", "uid-dario")
    dario.riceve(groupId)
    assertEquals(emptyList<String>(), dario.testi(groupId))
  }

  @Test
  fun `chi viene tolto perde il futuro e tiene il passato`() = runTest {
    val groupId = gruppoATre()

    ada.scrive(groupId, "prima")
    bruno.riceve(groupId)
    carla.riceve(groupId)

    // Ada toglie Carla: nasce l'epoca 1, e la ricevono solo i rimasti.
    posta.mittente = ada.uid
    ada.groups.remove(groupId, carla.codexId).getOrThrow()
    bruno.ritiraPosta()
    carla.ritiraPosta()

    ada.scrive(groupId, "dopo")
    bruno.riceve(groupId)
    carla.riceve(groupId)

    // Bruno legge tutto.
    assertEquals(listOf("prima", "dopo"), bruno.testi(groupId))
    // Carla tiene quello che era suo e non legge quello che non lo e' piu'.
    assertEquals(listOf("prima"), carla.testi(groupId))
    assertEquals(1, carla.sigillati(groupId))
  }

  @Test
  fun `chi se ne va non legge piu', e chi resta cambia chiave`() = runTest {
    val groupId = gruppoATre()
    ada.scrive(groupId, "prima")
    bruno.riceve(groupId)
    carla.riceve(groupId)

    // Carla se ne va da sola: lo dice agli altri, e chi comanda rifa' la chiave.
    posta.mittente = carla.uid
    carla.groups.leave(groupId).getOrThrow()
    posta.mittente = ada.uid
    ada.ritiraPosta()
    bruno.ritiraPosta()
    bruno.ritiraPosta()

    ada.scrive(groupId, "dopo")
    bruno.riceve(groupId)
    carla.riceve(groupId)

    assertEquals(listOf("prima", "dopo"), bruno.testi(groupId))
    // Carla ha buttato via la chiave uscendo: nemmeno il passato le resta, ed e' voluto -- uscire da
    // un gruppo non e' tenerselo aperto in tasca.
    assertEquals(emptyList<String>(), carla.testi(groupId))
  }

  @Test
  fun `la gemma del gruppo non cambia quando qualcuno esce`() = runTest {
    val groupId = gruppoATre()
    val prima = bruno.chats.chatById(groupId)?.emblemSeed

    posta.mittente = ada.uid
    ada.groups.remove(groupId, carla.codexId).getOrThrow()
    bruno.ritiraPosta()

    // Un gruppo che cambia faccia a ogni uscita non si riconosce piu' nella lista.
    assertEquals(prima, bruno.chats.chatById(groupId)?.emblemSeed)
    assertNotEquals(0L, prima)
  }

  @Test
  fun `un invito consegna la chiave, e prima di allora si aspetta`() = runTest {
    val groupId = gruppoATre()
    ada.scrive(groupId, "il posto di sempre")

    val dario = Telefono("Dario", "uid-dario")
    dario.conosce(ada)
    ada.conosce(dario)
    val invito = ada.groups.invite(groupId).getOrThrow()

    // Dario bussa. Finche' Ada non risponde, del gruppo non sa niente.
    posta.mittente = dario.uid
    dario.groups.requestJoin(invito.groupId, invito.token, ada.codexId).getOrThrow()
    assertEquals(null, dario.chats.chatById(groupId))

    // Ada ritira la posta e consegna la chiave.
    posta.mittente = ada.uid
    ada.ritiraPosta()
    dario.ritiraPosta()

    dario.riceve(groupId)
    assertEquals(listOf("il posto di sempre"), dario.testi(groupId))
    assertTrue(dario.groups.membersOf(groupId).any { it.codexId == ada.codexId })
  }

  @Test
  fun `un invito a un gruppo lasciato non apre piu' niente`() = runTest {
    val groupId = gruppoATre()
    val invito = ada.groups.invite(groupId).getOrThrow()

    posta.mittente = ada.uid
    ada.groups.leave(groupId).getOrThrow()
    bruno.ritiraPosta()
    carla.ritiraPosta()

    val dario = Telefono("Dario", "uid-dario")
    dario.conosce(ada)
    ada.conosce(dario)
    posta.mittente = dario.uid
    dario.groups.requestJoin(invito.groupId, invito.token, ada.codexId).getOrThrow()
    posta.mittente = ada.uid
    ada.ritiraPosta()
    dario.ritiraPosta()

    // Un invito e' una persona che apre la porta, non un pezzo di carta che la apre da solo.
    assertEquals(null, dario.chats.chatById(groupId))
  }

  @Test
  fun `una chiave gia' vista non torna indietro`() = runTest {
    val groupId = gruppoATre()
    posta.mittente = ada.uid
    ada.groups.remove(groupId, carla.codexId).getOrThrow()
    bruno.ritiraPosta()

    // La posta puo' consegnare in disordine: la chiave dell'epoca zero, arrivando dopo quella
    // dell'epoca uno, non deve riportare Bruno indietro.
    posta.mittente = ada.uid
    ada.groups.resendKey(groupId, bruno.codexId)
    bruno.ritiraPosta()
    assertEquals(1, bruno.chats.chatById(groupId)?.keyEpoch)

    ada.scrive(groupId, "dopo")
    bruno.riceve(groupId)
    assertEquals(listOf("dopo"), bruno.testi(groupId))
  }

  @Test
  fun `una busta che non si apre non blocca la posta`() = runTest {
    gruppoATre()
    // Qualcuno scrive spazzatura nella casella di Bruno. Deve essere buttata, non tenuta in eterno:
    // una posta che non si svuota smette di consegnare quello che conta.
    posta.mittente = ada.uid
    posta.send(bruno.uid, "rotta", ByteArray(200) { 7 })
    bruno.ritiraPosta()
    assertTrue(posta.perDi(bruno.uid).none { it.id == "rotta" })
  }

  @Test
  fun `una busta da uno sconosciuto aspetta`() = runTest {
    val dario = Telefono("Dario", "uid-dario")
    dario.conosce(bruno)
    posta.mittente = dario.uid
    posta.send(bruno.uid, "da-dario", ByteArray(200) { 3 })

    // Bruno non sa chi sia Dario: la busta resta li'. La sua scheda potrebbe arrivare fra un minuto,
    // e buttarla adesso vorrebbe dire perdere un invito.
    bruno.ritiraPosta()
    assertTrue(posta.perDi(bruno.uid).any { it.id == "da-dario" })
  }

  @Test
  fun `un gruppo di una persona sola non e' un gruppo`() = runTest {
    ada.conosce(bruno)
    assertTrue(ada.groups.create("", listOf(bruno.codexId)).isFailure)
    assertTrue(ada.groups.create("Nessuno", emptyList()).isFailure)
  }

  @Test
  fun `solo chi comanda toglie qualcuno`() = runTest {
    val groupId = gruppoATre()
    posta.mittente = bruno.uid
    // Bruno non e' admin: togliere Carla non e' cosa sua, e il gruppo resta com'era.
    assertTrue(bruno.groups.remove(groupId, carla.codexId).isFailure)
    assertFalse(bruno.groups.membersOf(groupId).first { it.codexId == carla.codexId }.gone)
  }
}
