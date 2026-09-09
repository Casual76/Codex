package dev.pampa.codex.data.groups

import android.util.Log
import dev.pampa.codex.crypto.ContactCardCode
import dev.pampa.codex.crypto.GroupJoinPayload
import dev.pampa.codex.crypto.GroupKeyPayload
import dev.pampa.codex.crypto.GroupLeftPayload
import dev.pampa.codex.crypto.GroupPayloadCodec
import dev.pampa.codex.crypto.GroupSecret
import dev.pampa.codex.crypto.InboxParcel
import dev.pampa.codex.data.chat.ChatRepository
import dev.pampa.codex.data.cloud.CloudAccount
import dev.pampa.codex.data.cloud.CloudTransport
import dev.pampa.codex.data.cloud.InboxItem
import dev.pampa.codex.data.cloud.InboxTransport
import dev.pampa.codex.data.contacts.ContactRepository
import dev.pampa.codex.data.db.ChatEntity
import dev.pampa.codex.data.db.ChatKind
import dev.pampa.codex.data.db.ContactDao
import dev.pampa.codex.data.db.ContactEntity
import dev.pampa.codex.data.db.GroupMemberDao
import dev.pampa.codex.data.db.GroupMemberEntity
import dev.pampa.codex.data.identity.CodexIdentitySource
import dev.pampa.codex.data.identity.Keyring
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * I gruppi: crearli, consegnarne la chiave, entrarci, uscirne.
 *
 * **La differenza con una chat a due sta tutta nella chiave.** Fra due persone la chiave nasce da un
 * accordo: nessuno la manda a nessuno, e non esiste un momento in cui viaggia. Fra tre no -- non c'e'
 * niente che tre persone possano calcolare in comune senza essersi mai parlate -- quindi la chiave
 * la genera chi crea il gruppo e la **consegna**, una busta per ciascuno, chiusa con la chiave di
 * coppia. E' l'unico segreto che in Codex si sposta, ed e' il motivo per cui la busta della posta e'
 * firmata oltre che cifrata.
 *
 * **Le epoche.** Quando qualcuno esce, chi comanda genera una chiave nuova e la consegna a chi resta.
 * Chi e' uscito conserva i messaggi vecchi -- erano suoi -- e non legge i nuovi. Il portachiavi
 * tiene tutte le epoche, e la busta di ogni messaggio dice con quale e' chiusa.
 */
class GroupRepository(
  private val members: GroupMemberDao,
  private val chats: ChatRepository,
  private val contacts: ContactRepository,
  /**
   * La rubrica cruda.
   *
   * Serve perche' qui non bastano nome e Codex ID: per chiudere una busta ci vuole la **chiave
   * pubblica** dell'altra persona, e quella non compare nel modello che vede una schermata -- ed e'
   * giusto che non ci compaia.
   */
  private val contactRows: ContactDao,
  private val identityRepository: CodexIdentitySource,
  private val keyring: Keyring,
  private val inbox: InboxTransport,
  private val transport: CloudTransport,
  private val account: CloudAccount? = null,
  /**
   * Dove vanno le consegne.
   *
   * **Non si aspetta la rete tenendo ferma una schermata.** Un gruppo nasce in locale in un
   * millesimo di secondo; consegnarne la chiave e' una scrittura su un server che puo' non
   * rispondere -- e con Firestore una scrittura offline non fallisce, resta in attesa. Chi ha
   * appena premuto "crea" si troverebbe davanti "sto consegnando la chiave..." per sempre.
   */
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
  private val clock: () -> Long = System::currentTimeMillis,
) {

  /** Un invito da mandare: il gettone vero, che non passa mai dal server. */
  data class Invite(val groupId: String, val token: String, val link: String)

  fun observeMembers(chatId: String): Flow<List<GroupMemberEntity>> = members.observeForChat(chatId)

  suspend fun membersOf(chatId: String): List<GroupMemberEntity> = members.forChat(chatId)

  /**
   * Crea un gruppo e consegna la chiave a chi ne fa parte.
   *
   * La conversazione nasce **subito** e in locale, prima di qualsiasi consegna: chi crea un gruppo
   * lo vede nella lista anche in aereo, e le chiavi partono quando c'e' rete. Se la consegna a
   * qualcuno fallisce, il gruppo esiste comunque e quella persona vedra' "in attesa della chiave"
   * finche' non riprova.
   */
  suspend fun create(name: String, memberCodexIds: List<String>): Result<String> {
    val identity = identityRepository.identityOrNull()
      ?: return Result.failure(IllegalStateException("app bloccata"))
    if (name.isBlank()) return Result.failure(IllegalArgumentException("un gruppo ha un nome"))

    val rubrica = memberCodexIds.mapNotNull { contactRows.byCodexId(it) }
    if (rubrica.isEmpty()) {
      return Result.failure(IllegalArgumentException("un gruppo di una persona sola e' una chat"))
    }

    val groupId = GroupSecret.newId()
    val key = GroupSecret.newKey()
    val now = clock()
    val io = GroupMemberEntity(
      chatId = groupId,
      codexId = identity.codexId,
      uid = account?.uidOrNull().orEmpty(),
      name = mioNome(),
      admin = true,
      joinedAt = now,
    )
    val righe = listOf(io) + rubrica.map { contatto ->
      GroupMemberEntity(
        chatId = groupId,
        codexId = contatto.codexId,
        uid = contatto.uid,
        name = contatto.name,
        admin = false,
        joinedAt = now,
      )
    }

    return runCatching {
      adopt(groupId, name, key, epoch = 0, members = righe, createdAt = now)
      // Da qui in poi e' rete, e la rete non tiene ferma una schermata: il gruppo c'e' gia'. Chi
      // non riceve la chiave adesso la ricevera' con "Rimanda la chiave", e nel frattempo vede
      // "in attesa della chiave", che e' esattamente cio' che sta succedendo.
      scope.launch {
        deliverKey(groupId, name, key, epoch = 0, to = rubrica, createdAt = now)
        ensureOnServer(groupId)
      }
      groupId
    }
  }

  /**
   * Fa arrivare la chiave corrente a chi ancora non ce l'ha.
   *
   * Serve a due momenti: quando qualcuno entra, e quando una consegna era fallita. E' sempre
   * l'ultima epoca: consegnare una chiave vecchia darebbe accesso a messaggi che nel frattempo
   * sono stati scritti per un gruppo diverso da questo.
   */
  suspend fun resendKey(groupId: String, toCodexId: String): Result<Unit> {
    val chat = chats.chatById(groupId) ?: return Result.failure(IllegalStateException("gruppo assente"))
    val key = keyring.key(groupId) ?: return Result.failure(IllegalStateException("chiave assente"))
    val epoch = keyring.epoch(groupId) ?: 0
    val contatto = contactRows.byCodexId(toCodexId)
      ?: return Result.failure(IllegalStateException("non so chi sia"))
    return runCatching {
      deliverKey(groupId, chat.title, key, epoch, listOf(contatto), chat.createdAt)
    }
  }

  /**
   * Un invito da mandare a chi non e' ancora nel gruppo.
   *
   * Il gettone vive **solo nel link**: sul server ne finisce l'impronta, cosi' chi guardasse il
   * database non otterrebbe un invito valido. Chi lo presenta bussa a chi lo ha creato, e quello
   * gli consegna la chiave -- il che vuol dire che un invito non funziona se chi lo ha dato non si
   * fa piu' vivo, e va bene cosi': una chiave la da' una persona, non un pezzo di carta.
   */
  suspend fun invite(groupId: String): Result<Invite> {
    val identity = identityRepository.identityOrNull()
      ?: return Result.failure(IllegalStateException("app bloccata"))
    if (chats.chatById(groupId) == null) {
      return Result.failure(IllegalStateException("gruppo assente"))
    }
    val token = GroupSecret.newInviteToken()
    return Result.success(
      Invite(
        groupId = groupId,
        token = token,
        link = "codex://gruppo/$groupId/$token/${identity.codexId}",
      ),
    )
  }

  /**
   * Bussa a un gruppo con un invito in mano.
   *
   * Non entra: **chiede**. Finche' chi ha invitato non consegna la chiave, la conversazione esiste
   * in locale e i messaggi che arrivano restano sigillati. E' la verita' e va mostrata cosi' -- "in
   * attesa della chiave da Ada" e' un'informazione, "questa chat non funziona" no.
   */
  suspend fun requestJoin(groupId: String, token: String, inviterCodexId: String): Result<Unit> {
    val identity = identityRepository.identityOrNull()
      ?: return Result.failure(IllegalStateException("app bloccata"))
    val uid = account?.uidOrNull()
      ?: return Result.failure(IllegalStateException("serve un account per entrare in un gruppo"))
    val inviter = contactRows.byCodexId(inviterCodexId)
      ?: return Result.failure(IllegalStateException("non conosco chi invita"))
    val card = identityRepository.signedCard(uid)
      ?: return Result.failure(IllegalStateException("nessuna scheda da mandare"))

    val payload = GroupPayloadCodec.encode(
      GroupJoinPayload(
        groupId = groupId,
        token = token,
        card = ContactCardCode.encode(card).toByteArray(Charsets.UTF_8),
      ),
    )
    return sendParcel(inviter, InboxParcel.Kind.GROUP_JOIN, payload)
  }

  /**
   * Se ne va: lo dice a tutti, poi si toglie tutto di dosso.
   *
   * La chiave se ne va **davvero** -- si esce da un gruppo, non lo si tiene aperto in tasca -- e i
   * messaggi restano, sigillati. Chi resta rifara' la chiave: non e' questo telefono a poterlo
   * fare, perche' la chiave nuova non deve passare da chi e' uscito.
   */
  suspend fun leave(groupId: String): Result<Unit> {
    val identity = identityRepository.identityOrNull()
      ?: return Result.failure(IllegalStateException("app bloccata"))
    val rimasti = members.forChat(groupId).filter { !it.gone && it.codexId != identity.codexId }
    val payload = GroupPayloadCodec.encode(GroupLeftPayload(groupId, identity.codexId))
    rimasti.forEach { membro ->
      contactRows.byCodexId(membro.codexId)?.let { contatto ->
        sendParcel(contatto, InboxParcel.Kind.GROUP_LEFT, payload)
      }
    }
    members.markGone(groupId, identity.codexId)
    keyring.forget(groupId)
    return Result.success(Unit)
  }

  /**
   * Toglie qualcuno dal gruppo, e **rifa' la chiave**.
   *
   * Togliere un nome da una lista non toglie niente a nessuno: chi e' uscito ha ancora la chiave, e
   * senza una rotazione continuerebbe a leggere tutto quello che arriva -- basta un documento che
   * gli passi davanti. La rotazione e' il punto; la lista e' contorno.
   */
  suspend fun remove(groupId: String, codexId: String): Result<Unit> {
    val identity = identityRepository.identityOrNull()
      ?: return Result.failure(IllegalStateException("app bloccata"))
    val chat = chats.chatById(groupId) ?: return Result.failure(IllegalStateException("gruppo assente"))
    val tutti = members.forChat(groupId)
    if (tutti.none { it.codexId == identity.codexId && it.admin && !it.gone }) {
      return Result.failure(IllegalStateException("solo chi comanda toglie qualcuno"))
    }

    members.markGone(groupId, codexId)
    return rotate(groupId, chat)
  }

  /**
   * Un'epoca nuova per chi resta.
   *
   * La chiave nuova nasce qui e viene consegnata a ognuno. Quelle vecchie **restano nel
   * portachiavi**: i messaggi di ieri erano di ieri, e continuano a leggersi.
   */
  private suspend fun rotate(groupId: String, chat: ChatEntity): Result<Unit> {
    val epoch = (keyring.epoch(groupId) ?: 0) + 1
    val key = GroupSecret.newKey()
    val rimasti = members.forChat(groupId).filter { !it.gone }
    keyring.store(groupId, key, epoch)
    chats.setKeyEpoch(groupId, epoch)

    val identity = identityRepository.requireIdentity()
    val destinatari = rimasti.filter { it.codexId != identity.codexId }
      .mapNotNull { contactRows.byCodexId(it.codexId) }
    return runCatching {
      deliverKey(groupId, chat.title, key, epoch, destinatari, chat.createdAt)
      ensureOnServer(groupId)
    }
  }

  // --- Quello che arriva per posta ------------------------------------------------------------

  /**
   * Una busta arrivata: la apre, capisce di cosa parla, e agisce.
   *
   * Restituisce `true` se la busta e' stata gestita e si puo' togliere dalla posta. Una busta che
   * **non si apre** si toglie lo stesso: reggerla in eterno vorrebbe dire riprovare per sempre su
   * qualcosa che non migliorera'. Una busta di cui non si conosce ancora il mittente invece resta:
   * la sua scheda potrebbe arrivare fra un minuto.
   */
  suspend fun handle(item: InboxItem): Boolean {
    val identity = identityRepository.identityOrNull() ?: return false
    val mittente = contactRows.byUid(item.fromUid)
    if (mittente == null) {
      // Non lo conosciamo: puo' essere qualcuno che ci invita a un gruppo e la cui scheda arriva
      // insieme. Si prova a cercarlo nella rubrica pubblica; se non c'e', la busta aspetta.
      return false
    }

    val aperta = runCatching {
      InboxParcel.open(
        pairKey = identity.pairKey(mittente.x25519Public),
        senderEd25519Public = mittente.ed25519Public,
        recipientCodexId = identity.codexId,
        parcel = item.parcel,
      )
    }.getOrElse {
      Log.w(TAG, "busta non apribile da ${item.fromUid}: ${it.message}")
      return true
    }
    if (aperta.senderCodexId != mittente.codexId) {
      Log.w(TAG, "busta firmata da qualcun altro")
      return true
    }

    return when (aperta.kind) {
      InboxParcel.Kind.GROUP_KEY -> onGroupKey(aperta.payload, mittente)
      InboxParcel.Kind.GROUP_JOIN -> onGroupJoin(aperta.payload, mittente)
      InboxParcel.Kind.GROUP_LEFT -> onGroupLeft(aperta.payload, mittente)
    }
  }

  private suspend fun onGroupKey(payload: ByteArray, from: ContactEntity): Boolean {
    val chiave = GroupPayloadCodec.decodeKey(payload) ?: return true
    val esistente = keyring.epoch(chiave.groupId)
    if (esistente != null && esistente >= chiave.epoch) {
      // Gia' vista, o piu' vecchia di quella che abbiamo: le buste possono arrivare in disordine, e
      // tornare indietro di un'epoca vorrebbe dire non leggere piu' quello che arriva adesso.
      return true
    }
    val righe = chiave.members.map { membro ->
      GroupMemberEntity(
        chatId = chiave.groupId,
        codexId = membro.codexId,
        uid = membro.uid,
        name = membro.name,
        admin = membro.admin,
        joinedAt = chiave.createdAt,
      )
    }
    adopt(chiave.groupId, chiave.name, chiave.key, chiave.epoch, righe, chiave.createdAt)
    Log.d(TAG, "chiave del gruppo ${chiave.groupId} epoca ${chiave.epoch} da ${from.name}")
    return true
  }

  /**
   * Qualcuno bussa con un invito che abbiamo dato noi.
   *
   * Il gettone non si verifica contro il server -- non c'e' niente da verificare, l'abbiamo dato
   * noi -- ma **contro il gruppo**: si consegna la chiave solo se il gruppo esiste qui e se ne
   * facciamo parte. Un invito a un gruppo che abbiamo lasciato non apre piu' niente.
   */
  private suspend fun onGroupJoin(payload: ByteArray, from: ContactEntity): Boolean {
    val richiesta = GroupPayloadCodec.decodeJoin(payload) ?: return true
    val chat = chats.chatById(richiesta.groupId) ?: return true
    val identity = identityRepository.identityOrNull() ?: return false
    val miei = members.forChat(richiesta.groupId)
    if (miei.none { it.codexId == identity.codexId && !it.gone }) return true

    // La scheda arriva dentro la richiesta: chi entra puo' non essere ancora un contatto.
    runCatching { contacts.add(String(richiesta.card, Charsets.UTF_8)) }

    val key = keyring.key(richiesta.groupId) ?: return true
    val epoch = keyring.epoch(richiesta.groupId) ?: 0
    val nuovo = GroupMemberEntity(
      chatId = richiesta.groupId,
      codexId = from.codexId,
      uid = from.uid,
      name = from.name,
      admin = false,
      joinedAt = clock(),
    )
    members.upsert(nuovo)
    ensureOnServer(richiesta.groupId)
    deliverKey(richiesta.groupId, chat.title, key, epoch, listOf(from), chat.createdAt)
    // Anche gli altri devono sapere che c'e' una persona in piu': la lista dei membri viaggia
    // insieme alla chiave, quindi si riconsegna quella che c'e' gia'.
    val altri = members.forChat(richiesta.groupId)
      .filter { !it.gone && it.codexId != identity.codexId && it.codexId != from.codexId }
      .mapNotNull { contactRows.byCodexId(it.codexId) }
    deliverKey(richiesta.groupId, chat.title, key, epoch, altri, chat.createdAt)
    return true
  }

  /**
   * Qualcuno se n'e' andato.
   *
   * Chi comanda rifa' la chiave; gli altri si limitano a prenderne atto. Se comandassero tutti, ogni
   * uscita produrrebbe tante epoche quante sono le persone rimaste, e nessuno saprebbe quale sia
   * quella buona.
   */
  private suspend fun onGroupLeft(payload: ByteArray, from: ContactEntity): Boolean {
    val uscita = GroupPayloadCodec.decodeLeft(payload) ?: return true
    if (uscita.codexId != from.codexId) return true
    val chat = chats.chatById(uscita.groupId) ?: return true
    members.markGone(uscita.groupId, uscita.codexId)

    val identity = identityRepository.identityOrNull() ?: return false
    val io = members.byCodexId(uscita.groupId, identity.codexId)
    if (io?.admin == true && !io.gone) rotate(uscita.groupId, chat)
    return true
  }

  // --- Il lavoro sporco ------------------------------------------------------------------------

  /** Fa esistere il gruppo su questo telefono: chat, chiave, membri. */
  private suspend fun adopt(
    groupId: String,
    name: String,
    key: ByteArray,
    epoch: Int,
    members: List<GroupMemberEntity>,
    createdAt: Long,
  ) {
    keyring.store(groupId, key, epoch)
    // L'emblema segue la **prima** chiave: un gruppo che cambia gemma a ogni uscita non si
    // riconosce piu' nella lista. Alle epoche successive l'emblema resta quello di prima.
    val emblema = chats.chatById(groupId)?.emblemSeed?.takeIf { it != 0L }
      ?: GroupSecret.emblemSeed(key)
    chats.openGroup(
      chatId = groupId,
      title = name,
      emblemSeed = emblema,
      keyEpoch = epoch,
      createdAt = createdAt,
    )
    this.members.upsertAll(members)
  }

  private suspend fun deliverKey(
    groupId: String,
    name: String,
    key: ByteArray,
    epoch: Int,
    to: List<ContactEntity>,
    createdAt: Long,
  ) {
    if (to.isEmpty()) return
    val righe = members.forChat(groupId).filter { !it.gone }.map { membro ->
      GroupKeyPayload.Member(
        codexId = membro.codexId,
        uid = membro.uid,
        name = membro.name,
        admin = membro.admin,
      )
    }
    val payload = GroupPayloadCodec.encode(
      GroupKeyPayload(
        groupId = groupId,
        epoch = epoch,
        key = key,
        name = name,
        members = righe,
        createdAt = createdAt,
      ),
    )
    to.forEach { contatto -> sendParcel(contatto, InboxParcel.Kind.GROUP_KEY, payload) }
  }

  private suspend fun sendParcel(
    to: ContactEntity,
    kind: InboxParcel.Kind,
    payload: ByteArray,
  ): Result<Unit> {
    val identity = identityRepository.identityOrNull()
      ?: return Result.failure(IllegalStateException("app bloccata"))
    val uid = to.uid.ifBlank { contacts.peerUid(to.codexId).orEmpty() }
    if (uid.isBlank()) return Result.failure(IllegalStateException("${to.name} non ha un account"))

    val parcel = InboxParcel.seal(
      pairKey = identity.pairKey(to.x25519Public),
      signingPrivateKey = identity.ed25519Private,
      kind = kind,
      senderCodexId = identity.codexId,
      recipientCodexId = to.codexId,
      payload = payload,
    )
    return inbox.send(uid, UUID.randomUUID().toString(), parcel)
  }

  /** Dice al server chi c'e' dentro: gli serve per sapere a chi consegnare. */
  private suspend fun ensureOnServer(groupId: String) {
    val uid = account?.uidOrNull() ?: return
    val righe = members.forChat(groupId).filter { !it.gone }
    val uids = righe.mapNotNull { it.uid.takeIf(String::isNotBlank) }.distinct()
    if (uid !in uids) return
    val admin = righe.filter { it.admin }.mapNotNull { it.uid.takeIf(String::isNotBlank) }
    runCatching { transport.ensureGroup(groupId, uids, admin.ifEmpty { listOf(uid) }) }
  }

  /** Come mi chiamo: sta nella scheda, che e' l'unico posto in cui il nome e' gia' firmato. */
  private suspend fun mioNome(): String =
    identityRepository.signedCard(account?.uidOrNull().orEmpty())?.card?.name.orEmpty()

  private companion object {
    const val TAG = "GroupRepository"
  }
}
