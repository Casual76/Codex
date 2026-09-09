package dev.pampa.codex.crypto

/**
 * Cosa c'e' dentro una busta, una volta aperta.
 *
 * Fino a M4 dentro una busta c'era del testo e basta, scritto in UTF-8. Con le foto e le note vocali
 * serve dire **di che cosa si tratta**, e dirlo in un posto che il server non possa leggere: se il
 * tipo del contenuto stesse nel documento su Firestore, chi trasporta saprebbe chi si manda foto e
 * chi si manda vocali, che e' esattamente il genere di cosa che Codex non gli lascia sapere.
 *
 * Quindi sta **dentro la busta**, cifrato insieme al resto.
 */
sealed interface MessageBody {

  data class Text(val text: String) : MessageBody

  /**
   * Un allegato: il file vero sta altrove, cifrato con una chiave sua.
   *
   * Qui c'e' solo come ritrovarlo e come mostrarlo. Le misure servono a disegnare il posto giusto
   * **prima** di aver scaricato il file: senza, la conversazione salterebbe sotto le dita ogni volta
   * che una foto finisce di arrivare.
   */
  data class Media(
    val kind: Kind,
    val mediaId: String,
    val mime: String,
    /** Quanto pesa l'originale: si dice "3,2 MB" prima di cominciare a scaricare. */
    val size: Long,
    val width: Int = 0,
    val height: Int = 0,
    /** Per le note vocali. Zero per le foto. */
    val durationMs: Long = 0,
    /** Due parole scritte insieme alla foto. Vuoto se non ce ne sono. */
    val caption: String = "",
    /**
     * La forma di una nota vocale: un byte per colonnina, da 0 a 255.
     *
     * Si disegna **prima** di scaricare l'audio, ed e' l'unica cosa che distingue una nota vocale
     * da una barra grigia: si vede dove si e' parlato e dove si e' stati zitti. Sta qui e non in un
     * file a parte perche' sono poche decine di byte -- un secondo file da caricare, scaricare e
     * cancellare per disegnare una riga non varrebbe la pena.
     */
    val waveform: ByteArray = ByteArray(0),
  ) : MessageBody {
    enum class Kind { PHOTO, VOICE }

    // `waveform` e' un array, e per un array `equals` guarda l'identita': senza queste due, due
    // corpi identici risulterebbero diversi e i test lo direbbero in modo incomprensibile.
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is Media) return false
      return kind == other.kind &&
        mediaId == other.mediaId &&
        mime == other.mime &&
        size == other.size &&
        width == other.width &&
        height == other.height &&
        durationMs == other.durationMs &&
        caption == other.caption &&
        waveform.contentEquals(other.waveform)
    }

    override fun hashCode(): Int {
      var result = kind.hashCode()
      result = 31 * result + mediaId.hashCode()
      result = 31 * result + mime.hashCode()
      result = 31 * result + size.hashCode()
      result = 31 * result + width
      result = 31 * result + height
      result = 31 * result + durationMs.hashCode()
      result = 31 * result + caption.hashCode()
      result = 31 * result + waveform.contentHashCode()
      return result
    }
  }
}

/**
 * Come un corpo diventa byte e torna indietro.
 *
 * **Il testo resta testo puro.** Un messaggio scritto prima che gli allegati esistessero e' UTF-8 e
 * basta, e deve continuare a leggersi: quindi il formato nuovo si riconosce dalla magia in testa, e
 * tutto quello che non ce l'ha e' testo. Nessuna migrazione, nessun messaggio perso.
 */
object MessageBodyCodec {

  private val MAGIC = "CDXA".toByteArray(Charsets.US_ASCII)
  private const val VERSION = 1

  fun encode(body: MessageBody): ByteArray = when (body) {
    is MessageBody.Text -> body.text.toByteArray(Charsets.UTF_8)
    is MessageBody.Media -> ByteWriter(256)
      .bytes(MAGIC)
      .u8(VERSION)
      .u8(body.kind.ordinal)
      .string(body.mediaId)
      .string(body.mime)
      .i64(body.size)
      .i64(body.width.toLong())
      .i64(body.height.toLong())
      .i64(body.durationMs)
      .string(body.caption)
      .blob(body.waveform)
      .build()
  }

  fun decode(bytes: ByteArray): MessageBody {
    if (!looksLikeMedia(bytes)) return MessageBody.Text(bytes.decodeToString())
    return try {
      val reader = ByteReader(bytes)
      reader.expectMagic(MAGIC)
      if (reader.u8() != VERSION) return MessageBody.Text(bytes.decodeToString())
      val kind = MessageBody.Media.Kind.entries.getOrNull(reader.u8())
        ?: return MessageBody.Text(bytes.decodeToString())
      MessageBody.Media(
        kind = kind,
        mediaId = reader.string(),
        mime = reader.string(),
        size = reader.i64(),
        width = reader.i64().toInt(),
        height = reader.i64().toInt(),
        durationMs = reader.i64(),
        caption = reader.string(),
        // La forma d'onda e' arrivata dopo le foto: una busta scritta prima finisce qui, e finire
        // qui deve voler dire "nessuna forma", non "busta rotta".
        waveform = if (reader.remaining > 0) reader.blob() else ByteArray(0),
      )
    } catch (error: ByteReader.Malformed) {
      // Un allegato che non si legge non deve far sparire il messaggio: resta, come testo vuoto,
      // e la conversazione mostra che qualcosa c'era.
      MessageBody.Text("")
    }
  }

  private fun looksLikeMedia(bytes: ByteArray): Boolean =
    bytes.size > MAGIC.size && MAGIC.indices.all { bytes[it] == MAGIC[it] }
}
