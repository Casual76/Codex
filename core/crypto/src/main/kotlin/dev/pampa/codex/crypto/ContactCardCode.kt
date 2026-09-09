package dev.pampa.codex.crypto

/**
 * La scheda contatto in una forma che si puo' inquadrare, incollare o mandare per messaggio.
 *
 * E' un solo testo, con un prefisso davanti perche' chi lo trova sappia cos'e' senza doverlo
 * indovinare — e perche' uno scanner che inquadra il QR sbagliato se ne accorga subito invece di
 * provare a decodificare la carta fedelta' del supermercato.
 *
 * **Quello che entra da qui non e' affidabile finche' la firma non torna.** [decode] restituisce
 * `null` per qualsiasi cosa non sia una scheda intera, firmata dalla chiave che dichiara, con il
 * Codex ID che da quella chiave discende. Non c'e' modo di far entrare in Codex una scheda
 * costruita a mano, nemmeno passando dal QR.
 */
object ContactCardCode {

  /** Il prefisso sta anche nell'alfabeto ristretto dei QR: il codice resta piccolo e nitido. */
  const val PREFIX = "CDX1:"

  fun encode(card: SignedContactCard): String = PREFIX + Base32.encode(card.encode())

  /** Lo stesso codice a gruppi, per chi deve leggerlo su uno schermo invece che inquadrarlo. */
  fun encodeReadable(card: SignedContactCard): String =
    PREFIX + "\n" + Base32.grouped(Base32.encode(card.encode()))

  /**
   * Legge un codice e ne verifica la firma, oppure `null`.
   *
   * Il prefisso e' tollerato in qualsiasi combinazione di maiuscole e puo' mancare: chi incolla da
   * una chat spesso porta via mezza riga, e rifiutare una scheda valida per un due punti perso
   * sarebbe una crudelta' senza nessun guadagno.
   */
  fun decode(text: String): SignedContactCard? {
    val cleaned = text.trim().let { candidate ->
      val prefix = candidate.take(PREFIX.length)
      if (prefix.equals(PREFIX, ignoreCase = true)) candidate.drop(PREFIX.length) else candidate
    }
    if (cleaned.isBlank()) return null
    val bytes = Base32.decode(cleaned) ?: return null
    return ContactCard.decodeVerified(bytes)
  }

  /** Se somiglia a una scheda: serve allo scanner per ignorare i codici di tutto il resto. */
  fun looksLikeCard(text: String): Boolean =
    text.trim().take(PREFIX.length).equals(PREFIX, ignoreCase = true)
}
