package dev.pampa.codex.data.nearby

import android.util.Log
import dev.pampa.codex.crypto.NearbyHandshake
import dev.pampa.codex.data.chat.ChatRepository
import dev.pampa.codex.data.db.ChatKind
import dev.pampa.codex.data.db.ContactDao
import dev.pampa.codex.data.identity.CodexIdentitySource
import dev.pampa.codex.data.media.MediaVault
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * I messaggi che passano da un telefono all'altro senza passare da nessun server.
 *
 * **E' un motore a parte, e non un pezzo di quello del cloud.** Le vicinanze devono funzionare
 * quando non c'e' niente: niente rete, niente account, due telefoni in aereo su un treno. Il motore
 * del cloud invece esiste solo quando c'e' un accesso -- se le vicinanze ci stessero dentro,
 * smetterebbero di funzionare proprio nel momento per cui esistono.
 *
 * **Chi si collega non e' ancora nessuno.** Un endpoint che si connette e' solo un dispositivo nel
 * raggio; diventa una persona quando firma una sfida con la chiave della sua identita', e resta
 * connesso solo se quella persona e' gia' in rubrica. Nessuna scheda si scambia qui: chi non si
 * conosce non si parla, e per conoscersi ci sono il QR e il codice.
 *
 * Quello che passa e' la **stessa busta** che passerebbe dal cloud. Il messaggio consegnato di
 * persona resta comunque in coda per il server: il mittente ce lo portera' alla prima rete, con lo
 * stesso identificatore, e chi lo ha gia' lo riconosce e non lo prende due volte. E' l'unico modo
 * perche' la conversazione sia intera anche sull'altro dispositivo di quella persona.
 */
class NearbyEngine(
  private val link: NearbyLink,
  private val chats: ChatRepository,
  private val contacts: ContactDao,
  private val identityRepository: CodexIdentitySource,
  private val vault: MediaVault?,
  private val mode: Flow<NearbyMode>,
  private val scope: CoroutineScope,
  private val clock: () -> Long = System::currentTimeMillis,
) {

  /** Un dispositivo nel raggio, e quel che se ne sa. */
  private class Peer(
    /** La sfida che gli abbiamo mandato: la sua risposta si controlla contro questa. */
    val myChallenge: ByteArray,
    var codexId: String = "",
    var authenticated: Boolean = false,
    var greeted: Boolean = false,
  )

  private val peers = mutableMapOf<String, Peer>()

  private val _nearbyPeople = MutableStateFlow<Set<String>>(emptySet())

  /**
   * Chi e' qui adesso, per Codex ID.
   *
   * Lo guarda la chat per accendere l'icona "Vicino". E' una lista di persone gia' autenticate:
   * finche' una sfida non e' stata firmata, quel dispositivo non e' nessuno.
   */
  val nearbyPeople: StateFlow<Set<String>> = _nearbyPeople.asStateFlow()

  private var job: Job? = null

  fun start() {
    if (job != null) return
    job = scope.launch {
      // **Il modo e la serratura, insieme.** Guardare solo il modo sembrava bastare, e non bastava:
      // l'app parte sempre bloccata, quindi alla prima lettura l'identita' non c'e' -- e senza
      // identita' non c'e' nome da annunciare ne' chiave con cui firmare. Il motore si fermava li' e
      // non riprovava piu': l'antenna non si accendeva mai piu', per tutta la sessione.
      combine(mode, identityRepository.unlocked) { current, aperta -> current to aperta }
        .distinctUntilChanged()
        .collectLatest { (current, aperta) ->
        if (current == NearbyMode.OFF || !aperta) {
          clearPeers()
          return@collectLatest
        }
        val identity = identityRepository.identityOrNull() ?: return@collectLatest
        val name = NearbyHandshake.advertisedName(identity.keyringSeed, NearbyHandshake.dayOf(clock()))
        try {
          coroutineScope {
            launch { link.start(name).collect { event -> handle(event) } }
            // **Anche quello che si scrive adesso.** Consegnare solo quando qualcuno si autentica
            // basta per i messaggi gia' in coda e non per quelli scritti mentre l'altra persona e'
            // gia' li' -- cioe' il caso normale. Nei test non si vedeva, perche' li' la consegna la
            // chiedeva il test; con due telefoni in mano il messaggio restava fermo con la sua
            // spunta di attesa.
            launch {
              chats.observeOutboxSize().distinctUntilChanged().collect { quanti ->
                if (quanti > 0) deliverPending()
              }
            }
          }
        } finally {
          // Si spegne l'antenna anche quando il flusso viene interrotto da fuori: il modo piu'
          // rapido di scaricare una batteria e' lasciare acceso qualcosa che nessuno guarda piu'.
          link.stop()
          clearPeers()
        }
      }
    }
  }

  fun stop() {
    job?.cancel()
    job = null
    link.stop()
    clearPeers()
  }

  /**
   * Cosa fare di quello che succede sul filo.
   *
   * E' `internal` e non privata perche' e' **tutto il protocollo**: con due motori e un filo finto
   * si prova in mezzo secondo cio' che altrimenti si scoprirebbe rotto in un parcheggio, con due
   * telefoni in mano e nessun modo di guardarci dentro.
   */
  internal suspend fun handle(event: NearbyEvent) {
    when (event) {
      is NearbyEvent.Connected -> greet(event.endpointId)
      is NearbyEvent.Received -> onPacket(event.endpointId, event.bytes)
      is NearbyEvent.FileReceived -> onFile(event)
      is NearbyEvent.Disconnected -> forget(event.endpointId)
      is NearbyEvent.Unavailable -> Log.w(TAG, "vicinanze non disponibili: ${event.reason}")
    }
  }

  /**
   * Si presenta a un dispositivo nel raggio, una volta sola.
   *
   * **Si chiama anche quando arriva un pacchetto da uno che non conosciamo ancora**, e non e' una
   * gentilezza: i due lati si collegano nello stesso istante, e chi si sveglia per primo saluta
   * mentre l'altro non ha ancora ricevuto il suo evento di connessione. Buttare quel saluto vorrebbe
   * dire che la stretta di mano riesce solo quando i tempi cadono bene -- cioe' quasi mai, e in modo
   * diverso ogni volta.
   */
  private suspend fun greet(endpointId: String): Peer? {
    val identity = identityRepository.identityOrNull() ?: return null
    val peer = peers.getOrPut(endpointId) { Peer(myChallenge = NearbyHandshake.newChallenge()) }
    if (peer.greeted) return peer
    peer.greeted = true
    link.send(
      endpointId,
      NearbyPacketCodec.encode(
        NearbyPacket.Hello(NearbyHandshake.hello(identity.codexId, peer.myChallenge)),
      ),
    )
    return peer
  }

  private suspend fun onPacket(endpointId: String, bytes: ByteArray) {
    val peer = greet(endpointId) ?: return
    when (val packet = NearbyPacketCodec.decode(bytes)) {
      null -> Unit // Non e' roba nostra: si butta e il canale resta aperto.
      is NearbyPacket.Hello -> onHello(endpointId, peer, packet)
      is NearbyPacket.Auth -> onAuth(endpointId, peer, packet)
      is NearbyPacket.Message -> onMessage(peer, packet, endpointId)
      is NearbyPacket.Ack -> chats.nearbyDelivered(packet.messageId)
    }
  }

  /**
   * Si e' presentato: gli si risponde firmando la sua sfida.
   *
   * **Chi non e' in rubrica viene scollegato subito.** Non si scambiano schede qui: una scheda
   * accettata da un dispositivo che passa per strada sarebbe un contatto che nessuno ha voluto.
   */
  private suspend fun onHello(endpointId: String, peer: Peer, packet: NearbyPacket.Hello) {
    val identity = identityRepository.identityOrNull() ?: return
    val hello = runCatching { NearbyHandshake.readHello(packet.payload) }.getOrElse {
      link.disconnect(endpointId)
      return
    }
    if (hello.codexId == identity.codexId) {
      // Se stessi, da un altro dispositivo: per ora non e' previsto, e collegarsi a se' non serve.
      link.disconnect(endpointId)
      return
    }
    if (contacts.byCodexId(hello.codexId) == null) {
      link.disconnect(endpointId)
      return
    }
    peer.codexId = hello.codexId
    link.send(
      endpointId,
      NearbyPacketCodec.encode(
        NearbyPacket.Auth(
          NearbyHandshake.answer(hello.challenge, identity.codexId, identity.ed25519Private),
        ),
      ),
    )
  }

  /** Ha firmato: se la firma e' sua davvero, da adesso e' una persona. */
  private suspend fun onAuth(endpointId: String, peer: Peer, packet: NearbyPacket.Auth) {
    val contatto = contacts.byCodexId(peer.codexId)
    if (contatto == null) {
      link.disconnect(endpointId)
      return
    }
    val ok = NearbyHandshake.verify(
      challenge = peer.myChallenge,
      peerCodexId = contatto.codexId,
      peerEd25519Public = contatto.ed25519Public,
      signature = packet.signature,
    )
    if (!ok) {
      Log.w(TAG, "firma non valida da ${contatto.codexId}: chiudo")
      link.disconnect(endpointId)
      forget(endpointId)
      return
    }
    peer.authenticated = true
    _nearbyPeople.value = _nearbyPeople.value + contatto.codexId
    // `d` e non `i`: nel rilascio R8 lo toglie del tutto, e il nome di una persona non finisce
    // nel registro di sistema. Nelle build di lavoro resta, ed e' la riga con cui si prova che la
    // stretta di mano e' andata a buon fine (vedi docs/TEST-MANUALI.md, B4).
    Log.d(TAG, "vicino: ${contatto.name}")
    deliverPending()
  }

  /**
   * Un messaggio arrivato di persona.
   *
   * Due controlli, e non sono formalita': deve venire da qualcuno che ha firmato, e deve essere
   * **suo** -- il mittente scritto nel pacchetto deve essere chi lo sta consegnando. Senza il
   * secondo, un contatto potrebbe infilare in una conversazione un messaggio a nome di un terzo, e
   * la busta non lo tradirebbe: e' cifrata con la chiave di quella chat, che lui ha.
   */
  private suspend fun onMessage(peer: Peer, packet: NearbyPacket.Message, endpointId: String) {
    if (!peer.authenticated) return
    if (packet.senderCodexId != peer.codexId) {
      Log.w(TAG, "messaggio a nome di un altro: buttato")
      return
    }
    chats.receive(
      chatId = packet.chatId,
      messageId = packet.messageId,
      senderCodexId = packet.senderCodexId,
      envelope = packet.envelope,
      viewOnce = packet.viewOnce,
      createdAt = packet.createdAt,
    )
    // La ricevuta parte comunque, anche se il messaggio c'era gia': chi ha mandato deve poter
    // smettere di aspettare, e un duplicato per lui e' una consegna riuscita lo stesso.
    link.send(endpointId, NearbyPacketCodec.encode(NearbyPacket.Ack(packet.messageId)))
  }

  /** Un file: e' gia' cifrato, e va messo dov'e' che l'app cerca gli allegati. */
  private fun onFile(event: NearbyEvent.FileReceived) {
    val store = vault ?: return
    val peer = peers[event.endpointId]
    if (peer?.authenticated != true) return
    runCatching {
      val destination = store.file(event.mediaId)
      destination.parentFile?.mkdirs()
      event.file.copyTo(destination, overwrite = true)
      event.file.delete()
    }.onFailure { Log.w(TAG, "allegato vicino non salvato", it) }
  }

  /**
   * Manda a chi e' qui quello che aspetta di partire.
   *
   * Solo le conversazioni a due: un gruppo ha membri che possono non essere nella stanza, e
   * consegnarne una parte di persona e una parte dal cloud vorrebbe dire due strade da tenere
   * allineate per un guadagno che nessuno vedrebbe. I gruppi passano dal server, come prima.
   *
   * Il messaggio **resta in coda** dopo essere stato consegnato: il cloud lo vuole comunque, perche'
   * l'altra persona puo' avere un secondo dispositivo, e perche' una conversazione dev'essere intera
   * anche quando la si riapre altrove. Lo stesso identificatore fa da protezione: chi ce l'ha gia'
   * non lo prende due volte.
   */
  internal suspend fun deliverPending() {
    if (_nearbyPeople.value.isEmpty()) return
    val pending = chats.pendingDeliveries()
    for (outgoing in pending) {
      if (outgoing.kind != ChatKind.DIRECT) continue
      if (outgoing.peerId !in _nearbyPeople.value) continue
      val endpointId = peers.entries
        .firstOrNull { it.value.authenticated && it.value.codexId == outgoing.peerId }
        ?.key ?: continue

      // **Prima il file, poi il messaggio**, come nel cloud e per la stessa ragione: un messaggio
      // che nomina un allegato non ancora arrivato e' una foto che non si scarichera' mai.
      val media = outgoing.mediaFile
      if (media != null && !sendFile(endpointId, outgoing.messageId, media)) continue

      link.send(
        endpointId,
        NearbyPacketCodec.encode(
          NearbyPacket.Message(
            chatId = outgoing.chatId,
            messageId = outgoing.messageId,
            senderCodexId = outgoing.senderCodexId,
            envelope = outgoing.envelope,
            viewOnce = outgoing.viewOnce,
            createdAt = outgoing.createdAt,
            mediaId = if (media != null) outgoing.messageId else "",
          ),
        ),
      )
    }
  }

  private suspend fun sendFile(endpointId: String, mediaId: String, file: File): Boolean =
    link.sendFile(endpointId, mediaId, file).isSuccess

  private fun forget(endpointId: String) {
    val peer = peers.remove(endpointId) ?: return
    if (peer.codexId.isNotBlank()) {
      _nearbyPeople.value = _nearbyPeople.value - peer.codexId
    }
  }

  private fun clearPeers() {
    peers.clear()
    _nearbyPeople.value = emptySet()
  }

  private companion object {
    const val TAG = "CodexNearby"
  }
}
