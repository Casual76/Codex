package dev.pampa.codex.data.contacts

import dev.pampa.codex.crypto.ChatSecret
import dev.pampa.codex.crypto.CodexId
import dev.pampa.codex.crypto.ContactCard
import dev.pampa.codex.crypto.ContactCardCode
import dev.pampa.codex.crypto.SignedContactCard
import dev.pampa.codex.data.chat.ChatRepository
import dev.pampa.codex.data.cloud.CloudDirectory
import dev.pampa.codex.data.cloud.DirectoryLookup
import dev.pampa.codex.data.cloud.NoDirectory
import dev.pampa.codex.data.db.ContactDao
import dev.pampa.codex.data.db.ContactEntity
import dev.pampa.codex.data.identity.CodexIdentitySource
import dev.pampa.codex.data.identity.Keyring
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Una persona, come la vede una schermata. Le chiavi restano di sotto. */
data class Contact(
  val codexId: String,
  val name: String,
  val avatarSeed: Long,
  val addedAt: Long,
  /** Dove sta (o starebbe) la conversazione con lei. */
  val chatId: String,
  /** Se il rito e' gia' stato fatto: senza, i messaggi non si aprirebbero. */
  val hasKey: Boolean,
)

/** Cosa e' successo provando ad aggiungere una scheda. Ogni caso ha una frase sua a schermo. */
sealed interface AddContactResult {
  data class Added(val contact: Contact) : AddContactResult

  /** C'era gia': non e' un errore, e va detto senza allarmare. */
  data class AlreadyKnown(val contact: Contact) : AddContactResult

  /** La scheda inquadrata e' la propria. Capita, e fa ridere: non deve sembrare un guasto. */
  data object Myself : AddContactResult

  /** Non e' una scheda Codex, o e' stata rovinata nel trasporto. */
  data object NotACard : AddContactResult

  /** L'app e' bloccata: senza identita' non si puo' nemmeno dire chi sta aggiungendo. */
  data object Locked : AddContactResult

  /** Un Codex ID che nella rubrica non c'e': o e' sbagliato, o quella persona non ha un account. */
  data object NotFound : AddContactResult

  /** Cercare richiede la rete e un accesso. Senza, restano il QR e il codice incollato. */
  data object Unreachable : AddContactResult
  data object NeedsAccount : AddContactResult
}

/**
 * I contatti e il modo in cui si diventa contatti.
 *
 * **Come funziona senza server.** Due telefoni si scambiano le schede: uno mostra il proprio codice
 * (QR o testo), l'altro lo legge, e poi si fa il contrario. Nessuna delle due schede e' un segreto
 * -- dentro ci sono solo chiavi pubbliche e una firma -- quindi possono passare da qualsiasi cosa,
 * anche da una chat di un'altra app. Quello che rende la conversazione privata viene dopo, ed e' il
 * rito della parola d'ordine, che non passa da nessun cavo.
 *
 * **Cosa non si puo' fare qui.** Una scheda arrivata da lontano non si puo' verificare "meglio":
 * la firma dice che chi l'ha emessa possedeva quella chiave, non che sia la persona che si crede.
 * Quella parte la fa il rito, e la fanno le due persone guardando la stessa gemma. E' voluto: non
 * c'e' nessuna autorita' da qualche parte che possa garantire al posto loro.
 */
class ContactRepository(
  private val contacts: ContactDao,
  private val identityRepository: CodexIdentitySource,
  private val chatRepository: ChatRepository,
  private val keyring: Keyring,
  private val directory: CloudDirectory = NoDirectory,
  private val clock: () -> Long = System::currentTimeMillis,
) {

  /**
   * Le persone, con lo stato aggiornato della loro conversazione.
   *
   * La lista si ricalcola anche quando cambiano le **chat**, non solo i contatti: senza, chi
   * finisce il rito e torna qui vedrebbe ancora "da aprire" accanto a una conversazione che ha
   * appena aperto, perche' la riga del contatto non e' stata toccata da nessuno.
   */
  fun observeContacts(): Flow<List<Contact>> =
    combine(contacts.observeAll(), chatRepository.observeChats()) { list, _ -> list }
      .map { list -> list.mapNotNull { entity -> toContact(entity) } }

  /** La propria scheda firmata, pronta da mostrare. `null` se l'app e' bloccata. */
  suspend fun myCard(): SignedContactCard? = identityRepository.signedCard()

  /**
   * Mette la propria scheda nella rubrica, se c'e' un accesso.
   *
   * Va rifatto quando il profilo cambia: nella scheda ci sono il nome e il minerale, e una rubrica
   * che risponde con il nome di ieri e' peggio di una rubrica che non risponde.
   */
  suspend fun publishMyCard(uid: String): Result<Unit> {
    val card = myCard() ?: return Result.failure(IllegalStateException("app bloccata"))
    return directory.publish(ContactCard.of(
      identity = identityRepository.requireIdentity(),
      name = card.card.name,
      avatarSeed = card.card.avatarSeed,
      uid = uid,
    ))
  }

  /** Il proprio codice: e' questo che finisce nel QR e nel pulsante "copia". */
  suspend fun myCode(): String? = myCard()?.let(ContactCardCode::encode)

  /**
   * Aggiunge una persona da un codice inquadrato o incollato.
   *
   * L'ordine dei controlli non e' casuale: prima si guarda se e' una scheda valida (altrimenti non
   * si sa nemmeno di chi parlare), poi se e' la propria, poi se e' gia' nota. Una scheda gia' nota
   * viene **riscritta**: se qualcuno ha cambiato nome o minerale, la seconda scansione lo aggiorna.
   */
  suspend fun add(code: String): AddContactResult {
    val identity = identityRepository.identityOrNull() ?: return AddContactResult.Locked

    // Un Codex ID e un codice-scheda non si confondono: il primo sono dodici caratteri, il secondo
    // trecento. Si prova prima il piu' corto, e se non lo e' si passa oltre senza toccare la rete.
    CodexId.normalize(code)?.let { return addFromDirectory(it, identity.codexId) }

    val signed = ContactCardCode.decode(code) ?: return AddContactResult.NotACard
    return store(signed, identity.codexId)
  }

  /**
   * Aggiunge qualcuno cercandolo per Codex ID.
   *
   * E' la strada per chi non e' nella stessa stanza. Quello che torna dal server e' una scheda
   * **firmata**, verificata prima di arrivare qui: il server puo' rifiutarsi di rispondere, non
   * puo' mentire su chi e' un Codex ID.
   */
  private suspend fun addFromDirectory(codexId: String, myCodexId: String): AddContactResult {
    if (codexId == myCodexId) return AddContactResult.Myself
    return when (val lookup = directory.lookup(codexId)) {
      is DirectoryLookup.Found -> store(lookup.card, myCodexId)
      DirectoryLookup.NotFound -> AddContactResult.NotFound
      // Una scheda che non regge la verifica non e' un guasto di rete e non e' "non c'e'": e'
      // qualcosa che non torna. Per chi guarda lo schermo pero' il risultato e' lo stesso --
      // quella persona non si puo' aggiungere -- e dirgli di piu' non lo aiuterebbe a fare niente.
      DirectoryLookup.Untrusted -> AddContactResult.NotFound
      DirectoryLookup.Unreachable -> AddContactResult.Unreachable
      DirectoryLookup.SignedOut -> AddContactResult.NeedsAccount
    }
  }

  private suspend fun store(signed: SignedContactCard, myCodexId: String): AddContactResult {
    val card = signed.card
    if (card.codexId == myCodexId) return AddContactResult.Myself

    val existing = contacts.byCodexId(card.codexId)
    val entity = ContactEntity(
      codexId = card.codexId,
      uid = card.uid,
      name = card.name,
      avatarSeed = card.avatarSeed,
      x25519Public = card.x25519Public,
      ed25519Public = card.ed25519Public,
      addedAt = existing?.addedAt ?: clock(),
    )
    contacts.upsert(entity)
    val contact = toContact(entity) ?: return AddContactResult.Locked
    // Se si e' cambiato nome, la conversazione lo segue subito: il nome non ha niente a che vedere
    // con la chiave, quindi non c'e' motivo di aspettare che si rifaccia il rito per aggiornarlo.
    if (existing != null && existing.name != card.name) {
      chatRepository.rename(contact.chatId, card.name)
    }
    return if (existing == null) {
      AddContactResult.Added(contact)
    } else {
      AddContactResult.AlreadyKnown(contact)
    }
  }

  suspend fun byCodexId(codexId: String): Contact? =
    contacts.byCodexId(codexId)?.let { toContact(it) }

  /**
   * L'account dell'altra persona, che serve per consegnarle qualcosa.
   *
   * Non e' detto che si sappia: due persone possono essersi scambiate le schede di persona, con il
   * QR, prima che una delle due avesse collegato un account. In quel caso lo si cerca nella rubrica
   * e si aggiorna la scheda -- e se non c'e' nemmeno li', si risponde `null` e il messaggio resta in
   * coda finche' quella persona un account non ce l'ha.
   */
  suspend fun peerUid(codexId: String): String? {
    val known = contacts.byCodexId(codexId) ?: return null
    if (known.uid.isNotBlank()) return known.uid
    val lookup = directory.lookup(codexId)
    if (lookup !is DirectoryLookup.Found) return null
    val uid = lookup.card.card.uid
    if (uid.isBlank()) return null
    // La scheda trovata sostituisce quella vecchia: e' firmata, e porta l'account.
    contacts.upsert(known.copy(uid = uid, name = lookup.card.card.name))
    return uid
  }

  /**
   * Il rito: da una parola detta a voce nasce la chiave della conversazione.
   *
   * Restituisce l'identificatore della chat, che i due lati calcolano uguale. Quello che i due
   * lati **non** possono sapere da qui e' se hanno capito la stessa parola: lo dice la gemma, che
   * e' identica se la parola era la stessa. Per questo l'esito include il seme dell'emblema.
   */
  suspend fun openConversation(codexId: String, passphrase: String): Result<Conversation> {
    val identity = identityRepository.identityOrNull()
      ?: return Result.failure(IllegalStateException("app bloccata"))
    val contact = contacts.byCodexId(codexId)
      ?: return Result.failure(IllegalStateException("contatto assente"))
    if (!ChatSecret.isAcceptable(passphrase)) {
      return Result.failure(IllegalArgumentException("parola d'ordine troppo corta"))
    }
    return runCatching {
      val agreement = ChatSecret.agree(
        myX25519Private = identity.x25519Private,
        myCodexId = identity.codexId,
        peerX25519Public = contact.x25519Public,
        peerCodexId = contact.codexId,
        passphrase = passphrase,
      )
      try {
        chatRepository.openDirect(
          chatId = agreement.chatId,
          title = contact.name,
          peerId = contact.codexId,
          emblemSeed = agreement.emblemSeed,
          key = agreement.key,
        )
        Conversation(chatId = agreement.chatId, emblemSeed = agreement.emblemSeed)
      } finally {
        agreement.key.fill(0)
      }
    }
  }

  /** Quello che serve a una schermata dopo il rito: dove andare, e che gemma mostrare. */
  data class Conversation(val chatId: String, val emblemSeed: Long)

  /**
   * Toglie una persona e tutto quello che ne discendeva.
   *
   * Va via anche la conversazione. Tenerla senza il contatto vorrebbe dire lasciare in lista una
   * riga con un nome e nessun modo di rispondere, e tenere in giro una chiave che non apre piu'
   * niente di utile.
   */
  suspend fun delete(codexId: String) {
    val contact = contacts.byCodexId(codexId) ?: return
    chatIdFor(contact)?.let { chatRepository.deleteChat(it) }
    contacts.delete(codexId)
  }

  private suspend fun toContact(entity: ContactEntity): Contact? {
    val chatId = chatIdFor(entity) ?: return null
    return Contact(
      codexId = entity.codexId,
      name = entity.name,
      avatarSeed = entity.avatarSeed,
      addedAt = entity.addedAt,
      chatId = chatId,
      hasKey = keyring.has(chatId),
    )
  }

  private fun chatIdFor(entity: ContactEntity): String? {
    val identity = identityRepository.identityOrNull() ?: return null
    return ChatSecret.chatIdFor(
      myX25519Private = identity.x25519Private,
      myCodexId = identity.codexId,
      peerX25519Public = entity.x25519Public,
      peerCodexId = entity.codexId,
    )
  }
}
