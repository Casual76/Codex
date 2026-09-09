package dev.pampa.codex.data.chat

/** Un messaggio pronto a diventare un PNG: quale dipinto, e cosa ci va dentro. */
data class ExportablePainting(
  val paintingId: String?,
  /** Il seme del sigillo: serve solo a scegliere il dipinto quando l'id manca. */
  val seed: Long,
  /** L'involucro CDXP con dentro la busta. */
  val parcel: ByteArray,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ExportablePainting) return false
    return paintingId == other.paintingId && seed == other.seed && parcel.contentEquals(other.parcel)
  }

  override fun hashCode(): Int {
    var result = paintingId?.hashCode() ?: 0
    result = 31 * result + seed.hashCode()
    result = 31 * result + parcel.contentHashCode()
    return result
  }
}

/**
 * Cosa e' successo aprendo un quadro arrivato da fuori.
 *
 * Sono quattro esiti e non due perche' quello che l'utente deve fare cambia in tutti e quattro i
 * casi: aprire la chat, aprire la chat sul messaggio che c'era gia', farsi aggiungere come
 * contatto, o rendersi conto che l'immagine e' stata ricompressa per strada.
 */
sealed interface PaintingImport {

  /** Il messaggio non c'era e ora c'e': la chat lo mostra chiuso, da aprire. */
  data class Delivered(val chatId: String, val messageId: String) : PaintingImport

  /** C'era gia': non si duplica, si va dove sta. */
  data class AlreadyHere(val chatId: String, val messageId: String) : PaintingImport

  /** La busta e' per una conversazione che questo telefono non ha. */
  data object UnknownChat : PaintingImport

  /**
   * Dentro l'immagine non c'era niente da leggere.
   *
   * Il caso piu' comune non e' un'immagine qualsiasi: e' un quadro di Codex **ricompresso** da chi
   * l'ha inoltrato come foto invece che come file. I bit bassi spariscono con la ricompressione, e
   * con loro il messaggio.
   */
  data object NothingInside : PaintingImport

  /** L'involucro c'era ma la busta non si apre: chiave sbagliata, o immagine manomessa. */
  data object Unreadable : PaintingImport
}
