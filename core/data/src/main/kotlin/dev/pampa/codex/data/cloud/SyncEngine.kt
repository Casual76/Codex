package dev.pampa.codex.data.cloud

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import dev.pampa.codex.data.chat.ChatRepository
import dev.pampa.codex.data.contacts.ContactRepository
import dev.pampa.codex.data.db.ChatKind
import dev.pampa.codex.data.media.MediaStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Il motore della consegna: porta fuori quello che e' stato scritto e porta dentro quello che arriva.
 *
 * **Vive solo mentre c'e' un accesso.** Senza account non ha niente da fare -- e non e' un guasto:
 * Codex scritta e letta in locale funziona per intero, e questo motore aggiunge il pezzo che
 * riguarda un'altra persona. Quando l'accesso arriva parte da solo, e quando se ne va si ferma senza
 * lasciare niente a meta'.
 *
 * Due mestieri, e sono opposti:
 *
 * - **portare fuori**: svuotare la posta in uscita, che e' su disco proprio perche' possa
 *   sopravvivere a un'app chiusa in metropolitana;
 * - **portare dentro**: tenere un ascolto per ogni conversazione e infilare in casa le buste che
 *   compaiono, senza aprirle e senza chiedersi se si aprono.
 */
class SyncEngine(
  private val chats: ChatRepository,
  private val contacts: ContactRepository,
  private val transport: CloudTransport,
  private val mediaStore: MediaStore,
  private val account: CloudAccount,
  private val devices: DeviceRegistry,
  /**
   * Se far sapere che si e' online.
   *
   * E' un flusso di verita'/falsita' e non l'intero magazzino delle preferenze: il motore ha bisogno
   * di **questo**, e chiedere di piu' lo renderebbe impossibile da provare senza un dispositivo.
   */
  private val showOnline: Flow<Boolean>,
  private val scope: CoroutineScope,
  private val inbox: InboxTransport = NoInbox,
  /**
   * Chi sa cosa farsene di una busta di posta.
   *
   * E' opzionale perche' il motore deve poter girare anche senza gruppi -- e perche' i test della
   * consegna non hanno niente a che vedere con loro.
   */
  private val groups: dev.pampa.codex.data.groups.GroupRepository? = null,
  private val cloudStories: CloudStories = NoStories,
  private val stories: dev.pampa.codex.data.stories.StoryRepository? = null,
) {

  private var job: Job? = null

  fun start() {
    if (job != null) return
    job = scope.launch {
      account.state.collectLatest { state ->
        if (state !is AccountState.SignedIn) return@collectLatest
        coroutineScope {
          // Il gettone si riscrive a **ogni** accesso, non solo al primo: cambia da solo ogni
          // tanto, e se si registrasse una volta sola le notifiche smetterebbero di arrivare mesi
          // dopo senza che niente lo dica.
          launch { devices.register() }
          launch { deliverLoop(state.uid) }
          launch { listenLoop() }
          launch { inboxLoop(state.uid) }
          launch { storyLoop() }
          launch { receiptLoop(state.uid) }
          launch { presenceLoop(state.uid) }
        }
      }
    }
  }

  fun stop() {
    job?.cancel()
    job = null
  }

  /**
   * Un giro di consegne e basta, chiamato da fuori.
   *
   * Lo usa il lavoro programmato: l'app puo' essere stata chiusa dal sistema mentre una foto era
   * ancora in coda, e quando la rete torna deve partire senza che nessuno riapra Codex. Restituisce
   * quanto e' rimasto da fare, che e' cio' che decide se vale la pena riprovare.
   *
   * Non serve che l'app sia sbloccata: nella coda ci sono **buste gia' chiuse**. Consegnare non
   * significa leggere, ed e' per questo che la coda contiene il messaggio sigillato e non il testo.
   */
  suspend fun deliverOnce(): Int {
    val uid = account.uidOrNull() ?: return 0
    deliverPending(uid)
    return chats.pendingDeliveries().size
  }

  // --- Portare fuori -------------------------------------------------------------------------

  /**
   * Svuota la coda, e riprova.
   *
   * Il ciclo si sveglia sia quando qualcosa entra in coda sia a intervalli: la prima cosa serve a
   * consegnare **subito** quello che si scrive adesso, la seconda a riprendere quello che era
   * rimasto indietro quando la rete non c'era. Un ciclo solo delle due non basterebbe.
   */
  private suspend fun deliverLoop(uid: String) = coroutineScope {
    launch {
      chats.observeOutboxSize().distinctUntilChanged().collectLatest { pending ->
        if (pending > 0) deliverPending(uid)
      }
    }
    launch {
      while (true) {
        delay(RETRY_INTERVAL)
        deliverPending(uid)
      }
    }
  }

  /**
   * Un giro solo di consegne.
   *
   * E' `internal` e non privata perche' e' **la parte che vale la pena provare**: cosa succede se il
   * server rifiuta, se la rete non c'e', se l'altra persona non ha ancora un account. I due cicli
   * qui sopra sono solo un orologio, e un orologio non ha bisogno di test.
   */
  internal suspend fun deliverPending(uid: String) {
    val pending = chats.pendingDeliveries()
    for (outgoing in pending) {
      // Un gruppo si consegna **al gruppo**: chi ne fa parte lo sa gia' il server, e il documento
      // esiste da quando il gruppo e' nato. Una chat a due invece va preparata ogni volta, perche'
      // la prima consegna e' anche il momento in cui la conversazione nasce dalla parte del server.
      val prepared = if (outgoing.kind == ChatKind.GROUP) {
        Result.success(Unit)
      } else {
        val peerUid = contacts.peerUid(outgoing.peerId)
        if (peerUid == null) {
          // L'altra persona non ha ancora un account, o non la si e' potuta cercare. Il messaggio
          // resta in coda: quando collega Codex a un account, parte da solo.
          continue
        }
        transport.ensureChat(outgoing.chatId, listOf(uid, peerUid))
      }
      if (prepared.isFailure) {
        chats.deliveryFailed(outgoing.messageId, permanent = false)
        continue
      }
      // **Prima il file, poi il messaggio.** Un messaggio che nomina un allegato non ancora
      // caricato arriva all'altra persona come una foto che non si scarichera' mai.
      val media = outgoing.mediaFile
      if (media != null) {
        val uploaded = mediaStore.upload(outgoing.chatId, outgoing.messageId, media)
        if (uploaded.isFailure) {
          val failure = uploaded.exceptionOrNull()?.toSendFailure() ?: SendFailure.Retry
          chats.deliveryFailed(outgoing.messageId, permanent = failure is SendFailure.Refused)
          continue
        }
      }
      val sent = transport.send(
        CloudMessage(
          chatId = outgoing.chatId,
          messageId = outgoing.messageId,
          senderUid = uid,
          senderCodexId = outgoing.senderCodexId,
          envelope = outgoing.envelope,
          viewOnce = outgoing.viewOnce,
          createdAt = outgoing.createdAt,
        ),
      )
      sent.fold(
        onSuccess = { chats.deliverySucceeded(outgoing.messageId) },
        onFailure = { error ->
          val failure = error.toSendFailure()
          Log.w(TAG, "consegna non riuscita (${outgoing.messageId}): $failure")
          chats.deliveryFailed(outgoing.messageId, permanent = failure is SendFailure.Refused)
        },
      )
    }
  }

  // --- Portare dentro ------------------------------------------------------------------------

  /**
   * Un ascolto per conversazione, rifatto quando la lista cambia.
   *
   * Firestore riconsegna **tutto** quello che c'e' a ogni riattacco, non solo le novita': e' il
   * motivo per cui `receive` deve saper riconoscere un messaggio che ha gia'. Ed e' anche il motivo
   * per cui non serve ricordarsi dove si era arrivati -- chi ha fatto il rito dopo, riattaccando si
   * ritrova la conversazione intera.
   */
  private suspend fun listenLoop() {
    chats.observeChats()
      // I gruppi si ascoltano come le chat a due: non hanno un `peerId`, ma hanno messaggi.
      .map { list ->
        list.filter { (it.peerId != null && it.kind == ChatKind.DIRECT) || it.kind == ChatKind.GROUP }
          .map { it.id }
      }
      .distinctUntilChanged()
      .collectLatest { chatIds ->
        coroutineScope {
          chatIds.forEach { chatId ->
            launch {
              transport.observe(chatId).collectLatest { messages ->
                messages.forEach { message ->
                  chats.receive(
                    chatId = message.chatId,
                    messageId = message.messageId,
                    senderCodexId = message.senderCodexId,
                    envelope = message.envelope,
                    viewOnce = message.viewOnce,
                    createdAt = message.createdAt,
                  )
                }
              }
            }
          }
        }
      }
  }

  // --- La posta ------------------------------------------------------------------------------

  /**
   * Le buste indirizzate a noi: le apre chi sa cosa farci.
   *
   * Una busta gestita si **cancella**: le chiavi di gruppo non hanno bisogno di essere consegnate
   * due volte, e una posta che non si svuota diventa un archivio di chi ha scritto a chi. Una busta
   * che non si e' potuta gestire resta -- di solito perche' il mittente non e' ancora un contatto, e
   * la sua scheda potrebbe arrivare fra un minuto.
   */
  private suspend fun inboxLoop(uid: String) {
    val posta = groups ?: return
    inbox.observe(uid).collectLatest { items ->
      items.forEach { item ->
        val gestita = runCatching { posta.handle(item) }.getOrElse {
          Log.w(TAG, "busta non gestita (${item.id})", it)
          false
        }
        if (gestita) runCatching { inbox.delete(uid, item.id) }
      }
    }
  }

  // --- Le storie -----------------------------------------------------------------------------

  /**
   * Le storie dei contatti.
   *
   * Un ascolto per persona, e non una domanda sola per tutti: una storia sta sotto chi l'ha scritta,
   * ed e' quello che permette alle regole di dire "la legge chi ha un involucro col suo nome".
   * L'alternativa -- una collezione unica di tutte le storie del mondo -- costringerebbe il server a
   * lasciar leggere a tutti e a filtrare dopo, che e' esattamente il contrario.
   *
   * Chi non ha ancora un account non ha storie da ascoltare, e non e' un errore: e' una persona con
   * cui ci si scrive e basta.
   */
  private suspend fun storyLoop() {
    val storie = stories ?: return
    contacts.observeContacts()
      .map { runCatching { storie.authorsToWatch() }.getOrDefault(emptyList()) }
      .distinctUntilChanged()
      .collectLatest { autori ->
        coroutineScope {
          autori.forEach { autore ->
            launch {
              cloudStories.observe(autore).collectLatest { list ->
                list.forEach { storia -> runCatching { storie.receive(storia) } }
              }
            }
          }
        }
      }
  }

  // --- Le ricevute ---------------------------------------------------------------------------

  /**
   * Porta fuori "ho ricevuto" e "ho letto", e porta dentro quelle dell'altra persona.
   *
   * Le mie partono da quello che e' gia' scritto nella riga della conversazione, non da un evento:
   * cosi' una lettura avvenuta senza rete non si perde, parte al primo momento utile.
   */
  private suspend fun receiptLoop(uid: String) {
    chats.observeChats()
      .map { list ->
        list.filter { it.kind == "direct" && it.peerId != null }
          .map { Triple(it.id, it.myDeliveredAt, it.myReadAt) }
      }
      .distinctUntilChanged()
      .collectLatest { stati ->
        coroutineScope {
          stati.forEach { (chatId, deliveredAt, readAt) ->
            if (deliveredAt > 0 || readAt > 0) {
              launch { transport.reportReceipt(chatId, uid, Receipt(deliveredAt, readAt)) }
            }
            launch {
              transport.observeReceipts(chatId).collectLatest { receipts ->
                // Solo quelle **degli altri**: la propria e' gia' applicata in casa, e riapplicarla
                // trasformerebbe i messaggi che ho scritto io in "letti da me".
                receipts.filterKeys { it != uid }.values.forEach { receipt ->
                  chats.applyPeerReceipt(chatId, receipt.deliveredAt, receipt.readAt)
                }
              }
            }
          }
        }
      }
  }

  // --- La presenza ---------------------------------------------------------------------------

  /**
   * Il battito di "ci sono".
   *
   * Si riscrive ogni tanto invece di accendere un interruttore: un'app uccisa dal sistema non fa in
   * tempo a spegnerlo, e resterebbe "online" per sempre. Un istante che smette di essere recente
   * invece si spegne da solo, senza che nessuno debba ricordarsene.
   *
   * Chi ha spento "fammi vedere online" **non lo manda affatto**, e in cambio non lo vede: e'
   * l'unica forma di reciprocita' onesta.
   */
  private suspend fun presenceLoop(uid: String) {
    showOnline.distinctUntilChanged().collectLatest { visible ->
      if (!visible) {
        transport.reportPresence(uid, online = false)
        return@collectLatest
      }
      ProcessLifecycleOwner.get().lifecycle.currentStateFlow.collectLatest { state ->
        if (state.isAtLeast(Lifecycle.State.STARTED)) {
          while (true) {
            transport.reportPresence(uid, online = true)
            delay(HEARTBEAT)
          }
        } else {
          transport.reportPresence(uid, online = false)
        }
      }
    }
  }

  private companion object {
    const val TAG = "CodexSync"

    /** Ogni quanto si dice "ci sono ancora". Sotto i novanta secondi con cui si giudica online. */
    const val HEARTBEAT = 45_000L

    /** Ogni quanto riprovare quello che era rimasto indietro. */
    const val RETRY_INTERVAL = 30_000L
  }
}
