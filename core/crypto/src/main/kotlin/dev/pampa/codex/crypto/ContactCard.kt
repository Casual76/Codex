package dev.pampa.codex.crypto

/**
 * La carta d'identita' pubblica di una persona: quello che viaggia in un QR, per Bluetooth o
 * attraverso il server quando due utenti si aggiungono.
 *
 * E' **firmata da chi la emette**, e il Codex ID nasce dalla stessa chiave che firma. Le due cose
 * insieme chiudono la porta al server: un server ostile puo' consegnare la scheda sbagliata, ma non
 * puo' costruirne una che passi la verifica e che porti il Codex ID che l'altra persona ti ha detto
 * a voce.
 */
data class ContactCard(
  /** L'account Firebase, quando c'e'. Vuoto finche' l'identita' e' solo locale. */
  val uid: String,
  val codexId: String,
  val name: String,
  val avatarSeed: Long,
  val x25519Public: ByteArray,
  val ed25519Public: ByteArray,
  val issuedAt: Long,
) {

  /** I byte firmati: tutto il contenuto, nell'ordine in cui e' scritto. */
  private fun body(): ByteArray = ByteWriter(192)
    .bytes(MAGIC)
    .u8(VERSION)
    .string(uid)
    .string(codexId)
    .string(name)
    .i64(avatarSeed)
    .bytes(x25519Public)
    .bytes(ed25519Public)
    .i64(issuedAt)
    .build()

  fun sign(identity: CodexIdentity): SignedContactCard {
    require(identity.ed25519Public.contentEquals(ed25519Public)) {
      "la scheda non appartiene a questa identita'"
    }
    return SignedContactCard(this, identity.sign(body()))
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ContactCard) return false
    return uid == other.uid &&
      codexId == other.codexId &&
      name == other.name &&
      avatarSeed == other.avatarSeed &&
      x25519Public.contentEquals(other.x25519Public) &&
      ed25519Public.contentEquals(other.ed25519Public) &&
      issuedAt == other.issuedAt
  }

  override fun hashCode(): Int {
    var result = uid.hashCode()
    result = 31 * result + codexId.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + avatarSeed.hashCode()
    result = 31 * result + x25519Public.contentHashCode()
    result = 31 * result + ed25519Public.contentHashCode()
    result = 31 * result + issuedAt.hashCode()
    return result
  }

  companion object {
    internal val MAGIC = "CDXC".toByteArray(Charsets.US_ASCII)
    internal const val VERSION = 1

    /** La scheda di chi sta usando questo telefono. */
    fun of(
      identity: CodexIdentity,
      name: String,
      avatarSeed: Long,
      uid: String = "",
      issuedAt: Long = System.currentTimeMillis(),
    ): SignedContactCard = ContactCard(
      uid = uid,
      codexId = identity.codexId,
      name = name,
      avatarSeed = avatarSeed,
      x25519Public = identity.x25519Public,
      ed25519Public = identity.ed25519Public,
      issuedAt = issuedAt,
    ).sign(identity)

    /**
     * Legge una scheda ricevuta e la verifica: firma valida **e** Codex ID che corrisponde alla
     * chiave. Restituisce `null` invece di lanciare: una scheda malformata e' un dato di rete
     * sbagliato, non un errore di programmazione.
     */
    fun decodeVerified(bytes: ByteArray): SignedContactCard? = try {
      val reader = ByteReader(bytes)
      reader.expectMagic(MAGIC)
      val version = reader.u8()
      if (version != VERSION) throw ByteReader.Malformed("scheda di versione $version")
      val card = ContactCard(
        uid = reader.string(),
        codexId = reader.string(),
        name = reader.string(),
        avatarSeed = reader.i64(),
        x25519Public = reader.bytes(IdentityKeys.KEY_SIZE),
        ed25519Public = reader.bytes(IdentityKeys.KEY_SIZE),
        issuedAt = reader.i64(),
      )
      val bodyLength = reader.position
      val signature = reader.bytes(IdentityKeys.SIGNATURE_SIZE)
      val body = bytes.copyOfRange(0, bodyLength)
      val valid = IdentityKeys.verify(card.ed25519Public, body, signature) &&
        card.codexId == CodexId.fromSigningKey(card.ed25519Public)
      if (valid) SignedContactCard(card, signature) else null
    } catch (error: ByteReader.Malformed) {
      null
    }
  }
}

/** Una scheda con la sua firma: l'unica forma in cui una scheda esce da un dispositivo. */
class SignedContactCard(val card: ContactCard, val signature: ByteArray) {

  fun encode(): ByteArray = ByteWriter(256)
    .bytes(MAGIC)
    .u8(VERSION)
    .string(card.uid)
    .string(card.codexId)
    .string(card.name)
    .i64(card.avatarSeed)
    .bytes(card.x25519Public)
    .bytes(card.ed25519Public)
    .i64(card.issuedAt)
    .bytes(signature)
    .build()

  private companion object {
    val MAGIC = ContactCard.MAGIC
    const val VERSION = ContactCard.VERSION
  }
}
