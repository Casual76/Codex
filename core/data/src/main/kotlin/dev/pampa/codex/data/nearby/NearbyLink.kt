package dev.pampa.codex.data.nearby

import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Il filo fra due telefoni vicini, ridotto all'osso.
 *
 * Sotto c'e' Nearby Connections, che e' Bluetooth, Wi-Fi Direct e un po' di magia di Google; qui
 * sopra c'e' un'interfaccia con cinque metodi. Non e' architettura per gusto: **Nearby e' la parte
 * meno affidabile di tutto il sistema** -- dipende dai Play Services, da ROM che spengono il
 * Bluetooth per conto loro, da permessi che cambiano a ogni versione di Android -- e tenerla dietro
 * una porta stretta e' cio' che permette al resto di Codex di non dipenderne mai.
 *
 * E' anche l'unico modo di provare il protocollo senza due telefoni in mano: un filo finto che
 * collega due motori dentro lo stesso test dice, in mezzo secondo, cose che altrimenti si
 * scoprirebbero in un parcheggio con due dispositivi e nessun log.
 */
interface NearbyLink {

  /**
   * Comincia ad annunciarsi e a cercare, e restituisce quello che succede.
   *
   * [localName] e' il nome effimero del giorno: non dice chi si e'. Il flusso vive finche' qualcuno
   * lo raccoglie; smettere di raccoglierlo spegne l'antenna.
   */
  fun start(localName: String): Flow<NearbyEvent>

  /** Manda dei byte. Poca roba: le buste di testo ci stanno, i file no. */
  suspend fun send(endpointId: String, bytes: ByteArray): Result<Unit>

  /**
   * Manda un file gia' cifrato.
   *
   * Nearby ha un tetto stretto sui byte diretti (decine di kilobyte); una foto passa da qui, che e'
   * un canale a parte e riprende da dove si era interrotto.
   */
  suspend fun sendFile(endpointId: String, mediaId: String, file: File): Result<Unit>

  fun disconnect(endpointId: String)

  fun stop()
}

/** Cosa puo' succedere su un filo. */
sealed interface NearbyEvent {

  /** Un altro dispositivo si e' collegato. Chi sia non si sa ancora: lo dira' la stretta di mano. */
  data class Connected(val endpointId: String) : NearbyEvent

  data class Received(val endpointId: String, val bytes: ByteArray) : NearbyEvent {
    override fun equals(other: Any?): Boolean =
      other is Received && endpointId == other.endpointId && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = 31 * endpointId.hashCode() + bytes.contentHashCode()
  }

  /** Un file e' arrivato per intero ed e' gia' su disco, cifrato com'era. */
  data class FileReceived(val endpointId: String, val mediaId: String, val file: File) : NearbyEvent

  data class Disconnected(val endpointId: String) : NearbyEvent

  /** L'antenna non e' partita: permesso negato, Bluetooth spento, Play Services assenti. */
  data class Unavailable(val reason: String) : NearbyEvent
}

/** Nessuna vicinanza: l'app funziona per intero, e non finge di avere un'antenna. */
object NoNearby : NearbyLink {
  override fun start(localName: String): Flow<NearbyEvent> = emptyFlow()

  override suspend fun send(endpointId: String, bytes: ByteArray): Result<Unit> =
    Result.failure(IllegalStateException("nessuna vicinanza"))

  override suspend fun sendFile(endpointId: String, mediaId: String, file: File): Result<Unit> =
    Result.failure(IllegalStateException("nessuna vicinanza"))

  override fun disconnect(endpointId: String) = Unit

  override fun stop() = Unit
}

/** Quando le vicinanze sono accese. E' una scelta di chi usa l'app, e si vede nelle impostazioni. */
enum class NearbyMode {
  /** Mai. */
  OFF,

  /** Solo mentre Codex e' aperta: nessun servizio, nessuna icona fissa, nessuna batteria di notte. */
  FOREGROUND,

  /** Sempre, con un servizio in primo piano che il sistema mostra. */
  ALWAYS,
}
