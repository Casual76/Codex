package dev.pampa.codex.crypto

/**
 * Le chiavi di un gruppo.
 *
 * Un gruppo non puo' avere la chiave di una chat a due: quella nasce da X25519 fra due identita', e
 * fra tre persone non esiste. Quindi la chiave di gruppo e' **trentadue byte a caso**, generati da
 * chi crea il gruppo, e consegnati a ciascun membro dentro una busta [InboxParcel] chiusa con la
 * chiave di coppia. Il server la trasporta senza poterla leggere.
 *
 * **Le epoche.** Quando qualcuno esce -- se ne va o viene tolto -- la chiave cambia: chi resta ne
 * riceve una nuova, con l'epoca successiva. Chi e' uscito continua a leggere i messaggi vecchi,
 * perche' quelli erano suoi, e non legge quelli nuovi, perche' quelli non lo sono piu'. E' la
 * ragione per cui il portachiavi conserva **tutte** le epoche e non solo l'ultima: buttare via una
 * chiave vecchia vorrebbe dire rendere illeggibile un anno di conversazione a chi c'era.
 *
 * L'identificatore di un gruppo non nasce da un segreto -- non ce n'e' uno in comune prima che il
 * gruppo esista -- quindi e' casuale. Il prefisso lo distingue da quello di una chat a due.
 */
object GroupSecret {

  const val ID_PREFIX = "g-"

  /** Una chiave nuova di zecca. */
  fun newKey(): ByteArray = Digests.randomBytes(Aead.KEY_SIZE)

  /** Un identificatore di gruppo: casuale, e riconoscibile a colpo d'occhio. */
  fun newId(): String = ID_PREFIX + Base32.encode(Digests.randomBytes(10)).lowercase()

  fun isGroupId(chatId: String): Boolean = chatId.startsWith(ID_PREFIX)

  /**
   * L'emblema di un gruppo: la gemma che tutti vedono uguale.
   *
   * Nasce dalla chiave della **prima** epoca e non da quella corrente: se cambiasse a ogni uscita,
   * il gruppo cambierebbe faccia ogni volta che qualcuno se ne va, e nessuno lo riconoscerebbe
   * piu' nella lista.
   */
  fun emblemSeed(firstEpochKey: ByteArray): Long = Hkdf.deriveSeed(firstEpochKey, "codex-emblem-v1")

  /**
   * Il gettone di un invito, e la sua impronta.
   *
   * Chi invita crea un gettone casuale e ne mette **l'impronta** sul server: il gettone vero viaggia
   * nel link o nel QR e non passa mai da Firestore. Chi lo presenta dimostra di aver ricevuto
   * l'invito da qualcuno che era li'; chi guardasse il database vedrebbe soltanto degli hash.
   */
  fun newInviteToken(): String = Base32.encode(Digests.randomBytes(15)).lowercase()

  fun inviteFingerprint(token: String): String =
    Base32.encode(Digests.sha256(("codex-invite-v1|" + token).toByteArray(Charsets.UTF_8)).copyOf(16))
      .lowercase()
}

/**
 * Cosa c'e' dentro una busta che porta una chiave di gruppo.
 *
 * Non e' solo la chiave: chi entra in un gruppo deve sapere **come si chiama**, chi c'e' dentro e
 * chi comanda, e nessuna di queste cose puo' arrivare dal server in chiaro -- il nome di un gruppo
 * dice quasi sempre di cosa si parla. Quindi viaggiano qui, dentro la parte cifrata.
 */
data class GroupKeyPayload(
  val groupId: String,
  val epoch: Int,
  val key: ByteArray,
  val name: String,
  /** I membri: Codex ID, uid dell'account e nome, come li conosce chi manda. */
  val members: List<Member>,
  val createdAt: Long,
) {
  data class Member(
    val codexId: String,
    val uid: String,
    val name: String,
    val admin: Boolean,
  )

  override fun equals(other: Any?): Boolean =
    other is GroupKeyPayload && groupId == other.groupId && epoch == other.epoch &&
      key.contentEquals(other.key) && name == other.name && members == other.members &&
      createdAt == other.createdAt

  override fun hashCode(): Int {
    var result = groupId.hashCode()
    result = 31 * result + epoch
    result = 31 * result + key.contentHashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + members.hashCode()
    return result
  }
}

/** Chi bussa a un gruppo con un invito in mano. */
data class GroupJoinPayload(
  val groupId: String,
  val token: String,
  /** La propria scheda, firmata: chi invita non e' detto che ci conosca gia'. */
  val card: ByteArray,
) {
  override fun equals(other: Any?): Boolean =
    other is GroupJoinPayload && groupId == other.groupId && token == other.token &&
      card.contentEquals(other.card)

  override fun hashCode(): Int = 31 * (31 * groupId.hashCode() + token.hashCode()) + card.contentHashCode()
}

/** Chi se n'e' andato da solo. Chi resta ne prende atto e rifa' la chiave. */
data class GroupLeftPayload(val groupId: String, val codexId: String)

/** Da oggetti a byte e ritorno, per quello che viaggia dentro una busta di posta. */
object GroupPayloadCodec {

  private const val VERSION = 1

  fun encode(payload: GroupKeyPayload): ByteArray {
    val writer = ByteWriter(256)
      .u8(VERSION)
      .string(payload.groupId)
      .u16(payload.epoch)
      .blob(payload.key)
      .string(payload.name)
      .i64(payload.createdAt)
      .u16(payload.members.size)
    payload.members.forEach { member ->
      writer.string(member.codexId).string(member.uid).string(member.name).u8(if (member.admin) 1 else 0)
    }
    return writer.build()
  }

  fun decodeKey(bytes: ByteArray): GroupKeyPayload? = runCatching {
    val reader = ByteReader(bytes)
    if (reader.u8() != VERSION) return null
    val groupId = reader.string()
    val epoch = reader.u16()
    val key = reader.blob()
    val name = reader.string()
    val createdAt = reader.i64()
    val members = (0 until reader.u16()).map {
      GroupKeyPayload.Member(
        codexId = reader.string(),
        uid = reader.string(),
        name = reader.string(),
        admin = reader.u8() == 1,
      )
    }
    GroupKeyPayload(groupId, epoch, key, name, members, createdAt)
  }.getOrNull()

  fun encode(payload: GroupJoinPayload): ByteArray = ByteWriter(256)
    .u8(VERSION)
    .string(payload.groupId)
    .string(payload.token)
    .u16(payload.card.size)
    .bytes(payload.card)
    .build()

  fun decodeJoin(bytes: ByteArray): GroupJoinPayload? = runCatching {
    val reader = ByteReader(bytes)
    if (reader.u8() != VERSION) return null
    GroupJoinPayload(
      groupId = reader.string(),
      token = reader.string(),
      card = reader.bytes(reader.u16()),
    )
  }.getOrNull()

  fun encode(payload: GroupLeftPayload): ByteArray = ByteWriter(64)
    .u8(VERSION)
    .string(payload.groupId)
    .string(payload.codexId)
    .build()

  fun decodeLeft(bytes: ByteArray): GroupLeftPayload? = runCatching {
    val reader = ByteReader(bytes)
    if (reader.u8() != VERSION) return null
    GroupLeftPayload(groupId = reader.string(), codexId = reader.string())
  }.getOrNull()
}
