package dev.pampa.codex.data.nearby

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Nearby Connections, com'e' davvero.
 *
 * **Cosa fa Nearby e cosa no.** Trova dispositivi nel raggio, apre un canale e ci fa passare byte e
 * file, scegliendo da solo fra Bluetooth e Wi-Fi. Non dice chi sono le persone e non protegge niente
 * per noi: il canale e' cifrato fra i due endpoint, ma "l'endpoint" e' un telefono qualunque che si
 * e' fatto avanti. **Chi sia lo stabilisce la stretta di mano di Codex**, sopra questo strato, e per
 * questo qui si accetta ogni connessione senza guardare il token di verifica -- quel token servirebbe
 * a due persone che si confrontano un numero a voce, e noi abbiamo qualcosa di piu' forte: una firma.
 *
 * `P2P_CLUSTER` perche' serve parlare con piu' persone insieme, non con una sola: in una stanza con
 * tre contatti si vuole essere vicini a tutti e tre.
 *
 * **I file e la loro etichetta.** Nearby consegna un file come un pagamento senza causale: arriva un
 * flusso con un numero, e da nessuna parte c'e' scritto a quale messaggio appartenga. L'etichetta
 * viaggia percio' in un pacchetto di servizio, che questo strato manda e legge da solo: sopra, il
 * motore vede solo "e' arrivato il file di questo media".
 */
class GmsNearbyLink(private val context: Context) : NearbyLink {

  private val connections = Nearby.getConnectionsClient(context)

  /** Da numero di trasferimento a nome del media, in attesa che il file finisca di arrivare. */
  private val expectedFiles = ConcurrentHashMap<Long, String>()

  /** I file arrivati per intero ma senza etichetta ancora nota: capita, e l'ordine non e' garantito. */
  private val arrivedFiles = ConcurrentHashMap<Long, File>()

  /** I trasferimenti in corso: il flusso vero si prende da qui quando finiscono. */
  private val incoming = ConcurrentHashMap<Long, Payload>()

  /**
   * L'antenna accesa, e gli eventi che ne escono.
   *
   * **Il buffer non e' una precauzione: senza, la stretta di mano non finisce mai.** Un
   * `callbackFlow` senza capacita' consegna un evento solo se qualcuno lo sta aspettando in quel
   * preciso istante; qui invece chi raccoglie fa lavoro vero per ogni evento -- apre il database,
   * verifica una firma -- e tutto quello che arriva mentre e' occupato viene **buttato in
   * silenzio**. Con due telefoni veri succede subito: il saluto dell'altro arriva mentre si sta
   * ancora mandando il proprio, e l'autenticazione non parte. Sul filo finto dei test non si vede,
   * perche' li' gli eventi si consegnano a mano.
   */
  override fun start(localName: String): Flow<NearbyEvent> = callbackFlow {
    val payloads = object : PayloadCallback() {
      override fun onPayloadReceived(endpointId: String, payload: Payload) {
        when (payload.type) {
          Payload.Type.BYTES -> {
            val bytes = payload.asBytes() ?: return
            when (val frame = Frame.read(bytes)) {
              is Frame.App -> trySend(NearbyEvent.Received(endpointId, frame.bytes))
              is Frame.FileLabel -> {
                expectedFiles[frame.payloadId] = frame.mediaId
                // Puo' essere gia' arrivato: allora l'etichetta chiude il giro adesso.
                arrivedFiles.remove(frame.payloadId)?.let { file ->
                  trySend(NearbyEvent.FileReceived(endpointId, frame.mediaId, file))
                }
              }
              null -> Unit
            }
          }
          // Il payload si tiene da parte: il file dentro e' completo solo alla fine, e il modo di
          // arrivarci e' questo oggetto, non un nome di file da indovinare.
          Payload.Type.FILE -> incoming[payload.id] = payload
          else -> Unit
        }
      }

      override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
        if (update.status != PayloadTransferUpdate.Status.SUCCESS) return
        val payload = incoming.remove(update.payloadId) ?: return
        val file = materialize(payload) ?: return
        val mediaId = expectedFiles.remove(update.payloadId)
        if (mediaId == null) {
          // L'etichetta non e' ancora arrivata: il file aspetta lei.
          arrivedFiles[update.payloadId] = file
          return
        }
        trySend(NearbyEvent.FileReceived(endpointId, mediaId, file))
      }
    }

    val lifecycle = object : ConnectionLifecycleCallback() {
      override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
        // Si accetta senza confrontare il token: chi sia lo dira' la firma, subito dopo.
        connections.acceptConnection(endpointId, payloads)
      }

      override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
        if (resolution.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
          trySend(NearbyEvent.Connected(endpointId))
        } else {
          trySend(NearbyEvent.Disconnected(endpointId))
        }
      }

      override fun onDisconnected(endpointId: String) {
        trySend(NearbyEvent.Disconnected(endpointId))
      }
    }

    val discovery = object : EndpointDiscoveryCallback() {
      override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
        // Si chiede la connessione a chiunque offra lo stesso servizio: filtrare qui sul nome
        // annunciato non si puo', perche' quel nome non dice chi e' -- ed e' voluto.
        connections.requestConnection(localName, endpointId, lifecycle)
          .addOnFailureListener { errore ->
            // **"Sei gia' connesso" non e' un errore, e' un canale da adottare.** Nearby tiene le
            // connessioni fuori dal processo dell'app: dopo un riavvio -- o una reinstallazione --
            // il canale c'e' ancora e `onConnectionResult` non arriva mai piu'. Senza questa riga i
            // due telefoni restano collegati e muti, che e' il peggiore dei modi di non funzionare.
            if (errore is com.google.android.gms.common.api.ApiException &&
              errore.statusCode == ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT
            ) {
              trySend(NearbyEvent.Connected(endpointId))
            } else {
              Log.i(TAG, "connessione non richiesta: ${errore.message}")
            }
          }
      }

      override fun onEndpointLost(endpointId: String) {
        trySend(NearbyEvent.Disconnected(endpointId))
      }
    }

    // **Niente `stopAllEndpoints()` qui.** Sembrava la cosa pulita da fare -- si riparte da zero --
    // e invece spegneva l'antenna appena accesa: quelle chiamate sono asincrone e senza ordine fra
    // loro, quindi la pulizia arrivava dopo l'annuncio e lo cancellava. Il risultato era un'app che
    // cercava e non si faceva trovare. Le connessioni rimaste da una vita precedente si adottano
    // invece di buttarle, qui sotto.
    val strategy = Strategy.P2P_CLUSTER
    connections.startAdvertising(
      localName,
      SERVICE_ID,
      lifecycle,
      AdvertisingOptions.Builder().setStrategy(strategy).build(),
    ).addOnFailureListener {
      trySend(NearbyEvent.Unavailable(it.message.orEmpty()))
    }
    connections.startDiscovery(
      SERVICE_ID,
      discovery,
      DiscoveryOptions.Builder().setStrategy(strategy).build(),
    ).addOnFailureListener {
      trySend(NearbyEvent.Unavailable(it.message.orEmpty()))
    }

    awaitClose { stop() }
  }.buffer(Channel.UNLIMITED)

  override suspend fun send(endpointId: String, bytes: ByteArray): Result<Unit> =
    sendPayload(endpointId, Payload.fromBytes(Frame.app(bytes)))

  override suspend fun sendFile(endpointId: String, mediaId: String, file: File): Result<Unit> {
    val payload = runCatching { Payload.fromFile(file) }.getOrElse {
      return Result.failure(it)
    }
    // **L'etichetta prima del file.** Se arrivasse dopo, chi riceve avrebbe per un attimo un file
    // senza nome; funziona lo stesso -- lo tiene da parte -- ma un giro in meno e' un giro in meno.
    sendPayload(endpointId, Payload.fromBytes(Frame.fileLabel(payload.id, mediaId)))
      .onFailure { return Result.failure(it) }
    return sendPayload(endpointId, payload)
  }

  override fun disconnect(endpointId: String) {
    runCatching { connections.disconnectFromEndpoint(endpointId) }
  }

  override fun stop() {
    runCatching { connections.stopAdvertising() }
    runCatching { connections.stopDiscovery() }
    runCatching { connections.stopAllEndpoints() }
    expectedFiles.clear()
    arrivedFiles.clear()
  }

  private suspend fun sendPayload(endpointId: String, payload: Payload): Result<Unit> =
    suspendCancellableCoroutine { continuation ->
      connections.sendPayload(endpointId, payload)
        .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
        .addOnFailureListener { continuation.resume(Result.failure(it)) }
    }

  /**
   * Il file di un trasferimento finito, portato **dentro la cache privata dell'app**.
   *
   * Si passa dal descrittore e non dal nome del file: dove Nearby appoggi il flusso cambia con la
   * versione di Android, e su quelle recenti quel percorso non e' nemmeno leggibile. Il descrittore
   * invece c'e' sempre, ed e' l'unico modo che non dipende da dove.
   *
   * Quello che si copia e' **gia' cifrato**: e' il file `CDXM` com'era sull'altro telefono, e questa
   * copia non lo apre.
   */
  private fun materialize(payload: Payload): File? = runCatching {
    val asset = payload.asFile() ?: return null
    val folder = File(context.cacheDir, "vicino").apply { mkdirs() }
    val destination = File(folder, "in-${payload.id}")
    android.os.ParcelFileDescriptor.AutoCloseInputStream(asset.asParcelFileDescriptor()).use { input ->
      destination.outputStream().use { output -> input.copyTo(output) }
    }
    destination
  }.getOrElse {
    Log.w(TAG, "allegato vicino non recuperato", it)
    null
  }

  /**
   * L'involucro che distingue i byte dell'app da quelli di servizio.
   *
   * Sopra questo strato esiste un solo tipo di pacchetto -- quello di Codex -- e le etichette dei
   * file non devono comparirci: sono un problema di questo trasporto, e restano qui.
   */
  private sealed interface Frame {
    class App(val bytes: ByteArray) : Frame
    class FileLabel(val payloadId: Long, val mediaId: String) : Frame

    companion object {
      private const val TAG_APP = 0
      private const val TAG_FILE = 1

      fun app(bytes: ByteArray): ByteArray = byteArrayOf(TAG_APP.toByte()) + bytes

      fun fileLabel(payloadId: Long, mediaId: String): ByteArray =
        byteArrayOf(TAG_FILE.toByte()) + "$payloadId|$mediaId".toByteArray(Charsets.UTF_8)

      fun read(bytes: ByteArray): Frame? {
        if (bytes.isEmpty()) return null
        return when (bytes[0].toInt()) {
          TAG_APP -> App(bytes.copyOfRange(1, bytes.size))
          TAG_FILE -> {
            val testo = String(bytes, 1, bytes.size - 1, Charsets.UTF_8)
            val separatore = testo.indexOf('|')
            if (separatore <= 0) return null
            val id = testo.substring(0, separatore).toLongOrNull() ?: return null
            FileLabel(id, testo.substring(separatore + 1))
          }
          else -> null
        }
      }
    }
  }

  private companion object {
    const val TAG = "CodexNearby"

    /** Chi parla con chi: due app diverse con lo stesso identificatore si troverebbero a vicenda. */
    const val SERVICE_ID = "dev.pampa.codex"
  }
}
