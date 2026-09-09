package dev.pampa.codex.crypto

/**
 * Chi sei, per Codex: due chiavi private e il seme da cui nasce il portachiavi.
 *
 * Vive **solo in memoria** mentre l'app e' sbloccata; su disco esiste unicamente dentro il vault
 * cifrato ([Vault]). Alla chiusura si chiama [wipe], che azzera gli array invece di lasciarli al
 * garbage collector: un heap dump di un'app bloccata non deve contenere una chiave privata.
 *
 * L'identita' appartiene alla **persona**, non al telefono: e' la stessa su ogni dispositivo
 * collegato, ed e' per questo che il vault si puo' riaprire altrove con il proprio segreto.
 */
class CodexIdentity(
  val x25519Private: ByteArray,
  val ed25519Private: ByteArray,
  val keyringSeed: ByteArray,
  val createdAt: Long,
) {
  init {
    require(x25519Private.size == IdentityKeys.KEY_SIZE) { "chiave X25519 non valida" }
    require(ed25519Private.size == IdentityKeys.KEY_SIZE) { "chiave Ed25519 non valida" }
    require(keyringSeed.size == KEYRING_SEED_SIZE) { "seme del portachiavi non valido" }
  }

  val x25519Public: ByteArray by lazy { IdentityKeys.x25519PublicKey(x25519Private) }
  val ed25519Public: ByteArray by lazy { IdentityKeys.ed25519PublicKey(ed25519Private) }

  /** L'identificatore leggibile della persona, derivato dalla sua chiave pubblica di firma. */
  val codexId: String by lazy { CodexId.fromSigningKey(ed25519Public) }

  /** La chiave che protegge il portachiavi (chiavi di chat e di gruppo) su ogni dispositivo. */
  fun keyringKey(): ByteArray = Hkdf.derive(keyringSeed, "codex-keyring-v1", Aead.KEY_SIZE)

  /** Il segreto in comune con un altro utente, gia' derivato: la chiave "a coppia" del piano. */
  fun pairKey(peerX25519Public: ByteArray): ByteArray = Hkdf.derive(
    ikm = IdentityKeys.agree(x25519Private, peerX25519Public),
    info = "codex-pair-v1",
    length = Aead.KEY_SIZE,
  )

  fun sign(message: ByteArray): ByteArray = IdentityKeys.sign(ed25519Private, message)

  fun encode(): ByteArray = ByteWriter(96)
    .bytes(MAGIC)
    .u8(VERSION)
    .bytes(x25519Private)
    .bytes(ed25519Private)
    .bytes(keyringSeed)
    .i64(createdAt)
    .build()

  /** Azzera i segreti. Dopo questa chiamata l'oggetto non e' piu' utilizzabile. */
  fun wipe() {
    x25519Private.fill(0)
    ed25519Private.fill(0)
    keyringSeed.fill(0)
  }

  companion object {
    const val KEYRING_SEED_SIZE = 32
    private const val VERSION = 1
    private val MAGIC = "CDXID".toByteArray(Charsets.US_ASCII)

    fun generate(createdAt: Long = System.currentTimeMillis()): CodexIdentity = CodexIdentity(
      x25519Private = IdentityKeys.randomX25519Private(),
      ed25519Private = IdentityKeys.randomEd25519Private(),
      keyringSeed = Digests.randomBytes(KEYRING_SEED_SIZE),
      createdAt = createdAt,
    )

    fun decode(bytes: ByteArray): CodexIdentity {
      val reader = ByteReader(bytes)
      reader.expectMagic(MAGIC)
      val version = reader.u8()
      if (version != VERSION) throw ByteReader.Malformed("identita' di versione $version")
      return CodexIdentity(
        x25519Private = reader.bytes(IdentityKeys.KEY_SIZE),
        ed25519Private = reader.bytes(IdentityKeys.KEY_SIZE),
        keyringSeed = reader.bytes(KEYRING_SEED_SIZE),
        createdAt = reader.i64(),
      )
    }
  }
}
