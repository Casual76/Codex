package dev.pampa.codex.data.nearby

import dev.pampa.codex.crypto.ByteReader
import dev.pampa.codex.crypto.ByteWriter

/**
 * Cosa passa su un filo fra due telefoni vicini.
 *
 * **Non e' un secondo protocollo di messaggistica.** Quello che viaggia e' la stessa busta `CDX3`
 * che andrebbe nel cloud: chi riceve la apre con la stessa chiave e ottiene lo stesso messaggio.
 * Qui intorno c'e' solo l'indirizzo -- in quale conversazione va, quale messaggio e', chi l'ha
 * scritto -- che il cloud tiene nei campi del documento e qui va scritto a mano.
 *
 * Che questi campi siano in chiaro sul filo non aggiunge niente a chi ascolta: e' esattamente quello
 * che sa il server quando il messaggio passa da li'. Il contenuto resta chiuso in tutti e due i casi.
 */
sealed interface NearbyPacket {

  /** Mi presento e ti do una sfida. Il primo pacchetto, sempre. */
  data class Hello(val payload: ByteArray) : NearbyPacket {
    override fun equals(other: Any?): Boolean = other is Hello && payload.contentEquals(other.payload)
    override fun hashCode(): Int = payload.contentHashCode()
  }

  /** La risposta firmata alla tua sfida. */
  data class Auth(val signature: ByteArray) : NearbyPacket {
    override fun equals(other: Any?): Boolean = other is Auth && signature.contentEquals(other.signature)
    override fun hashCode(): Int = signature.contentHashCode()
  }

  /** Un messaggio: la busta, e dove va. */
  data class Message(
    val chatId: String,
    val messageId: String,
    val senderCodexId: String,
    val envelope: ByteArray,
    val viewOnce: Boolean,
    val createdAt: Long,
    /** Se c'e' un file che lo accompagna: arriva sul canale dei file, con questo nome. */
    val mediaId: String = "",
  ) : NearbyPacket {
    override fun equals(other: Any?): Boolean =
      other is Message && chatId == other.chatId && messageId == other.messageId &&
        senderCodexId == other.senderCodexId && envelope.contentEquals(other.envelope) &&
        viewOnce == other.viewOnce && createdAt == other.createdAt && mediaId == other.mediaId

    override fun hashCode(): Int = 31 * messageId.hashCode() + envelope.contentHashCode()
  }

  /**
   * "Ce l'ho."
   *
   * Serve a chi ha mandato: senza, un messaggio consegnato di persona resterebbe con la spunta di
   * attesa finche' non passa anche dal cloud, e chi guarda lo schermo penserebbe di non averlo
   * mandato.
   */
  data class Ack(val messageId: String) : NearbyPacket
}

/** Da pacchetto a byte e ritorno. Un byte di tipo davanti, e i campi in ordine fisso. */
object NearbyPacketCodec {

  private val MAGIC = "CDXV".toByteArray(Charsets.US_ASCII)
  private const val VERSION = 1

  private const val HELLO = 0
  private const val AUTH = 1
  private const val MESSAGE = 2
  private const val ACK = 3

  fun encode(packet: NearbyPacket): ByteArray {
    val writer = ByteWriter(256).bytes(MAGIC).u8(VERSION)
    return when (packet) {
      is NearbyPacket.Hello -> writer.u8(HELLO).blob(packet.payload)
      is NearbyPacket.Auth -> writer.u8(AUTH).blob(packet.signature)
      is NearbyPacket.Ack -> writer.u8(ACK).string(packet.messageId)
      is NearbyPacket.Message -> writer
        .u8(MESSAGE)
        .string(packet.chatId)
        .string(packet.messageId)
        .string(packet.senderCodexId)
        .u8(if (packet.viewOnce) 1 else 0)
        .i64(packet.createdAt)
        .string(packet.mediaId)
        // La busta per ultima: e' l'unico campo che puo' essere grande, e cosi' leggere gli altri
        // non dipende dalla sua lunghezza.
        .u32(packet.envelope.size.toLong())
        .bytes(packet.envelope)
    }.build()
  }

  /**
   * `null` se non si capisce.
   *
   * Su un filo aperto a chiunque sia nel raggio puo' arrivare qualsiasi cosa, e la risposta giusta e'
   * buttarla: un'eccezione qui fermerebbe il canale per un pacchetto che non era nemmeno nostro.
   */
  fun decode(bytes: ByteArray): NearbyPacket? = runCatching {
    val reader = ByteReader(bytes)
    reader.expectMagic(MAGIC)
    if (reader.u8() != VERSION) return null
    when (reader.u8()) {
      HELLO -> NearbyPacket.Hello(reader.blob())
      AUTH -> NearbyPacket.Auth(reader.blob())
      ACK -> NearbyPacket.Ack(reader.string())
      MESSAGE -> {
        val chatId = reader.string()
        val messageId = reader.string()
        val senderCodexId = reader.string()
        val viewOnce = reader.u8() == 1
        val createdAt = reader.i64()
        val mediaId = reader.string()
        val size = reader.u32().toInt()
        NearbyPacket.Message(
          chatId = chatId,
          messageId = messageId,
          senderCodexId = senderCodexId,
          envelope = reader.bytes(size),
          viewOnce = viewOnce,
          createdAt = createdAt,
          mediaId = mediaId,
        )
      }
      else -> null
    }
  }.getOrNull()
}
