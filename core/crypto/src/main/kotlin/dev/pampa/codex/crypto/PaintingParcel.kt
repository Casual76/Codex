package dev.pampa.codex.crypto

/**
 * Quello che viaggia dentro un dipinto esportato: il formato CDXP.
 *
 * La busta (`CDX3`) da sola non basta a essere riaperta altrove. Dentro l'app chat, messaggio e
 * mittente si sanno perche' sono righe di un database; in un PNG che gira per il mondo no, e senza
 * quei tre valori la busta e' indecifrabile anche per chi ha la chiave giusta — sono dati
 * autenticati, e sbagliarne uno fa fallire l'apertura invece che darne una sbagliata.
 *
 * **Qui dentro non c'e' niente di segreto**, ed e' voluto: chi trova il PNG legge che esiste una
 * chat con un certo id e un mittente con un certo Codex ID, e non legge una sola parola del
 * messaggio. La riservatezza sta nella busta; questo involucro serve solo a consegnarla.
 *
 * Il formato non cambia senza cambiare la versione: un PNG mandato oggi deve aprirsi fra un anno.
 */
object PaintingParcel {

  private val MAGIC = "CDXP".toByteArray(Charsets.US_ASCII)
  private const val VERSION = 1

  class Malformed(message: String) : Exception(message)

  /** Un messaggio pronto a lasciare l'app. */
  data class Content(
    val chatId: String,
    val messageId: String,
    val senderId: String,
    val envelope: ByteArray,
  ) {
    // ByteArray si confronta per riferimento: senza questi due, due involucri identici risultano
    // diversi e i test lo scoprono nel modo piu' noioso possibile.
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is Content) return false
      return chatId == other.chatId &&
        messageId == other.messageId &&
        senderId == other.senderId &&
        envelope.contentEquals(other.envelope)
    }

    override fun hashCode(): Int {
      var result = chatId.hashCode()
      result = 31 * result + messageId.hashCode()
      result = 31 * result + senderId.hashCode()
      result = 31 * result + envelope.contentHashCode()
      return result
    }
  }

  fun pack(content: Content): ByteArray = ByteWriter(content.envelope.size + 96)
    .bytes(MAGIC)
    .u8(VERSION)
    .string(content.chatId)
    .string(content.messageId)
    .string(content.senderId)
    .bytes(content.envelope)
    .build()

  fun unpack(bytes: ByteArray): Content {
    val reader = ByteReader(bytes)
    try {
      reader.expectMagic(MAGIC)
      val version = reader.u8()
      if (version != VERSION) throw Malformed("involucro di versione $version")
      val chatId = reader.string()
      val messageId = reader.string()
      val senderId = reader.string()
      val envelope = reader.rest()
      if (!MessageEnvelope.isEnvelope(envelope)) throw Malformed("dentro non c'e' una busta")
      return Content(chatId, messageId, senderId, envelope)
    } catch (error: ByteReader.Malformed) {
      throw Malformed("involucro incompleto: ${error.message}")
    }
  }

  /** Se questi byte hanno l'aria di essere un involucro. Non garantisce che si apra. */
  fun looksLikeParcel(bytes: ByteArray): Boolean {
    if (bytes.size < MAGIC.size) return false
    for (index in MAGIC.indices) if (bytes[index] != MAGIC[index]) return false
    return true
  }
}
