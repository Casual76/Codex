package dev.pampa.codex.crypto

/**
 * Una busta indirizzata a **una persona sola**: il formato CDXB.
 *
 * Serve alle cose che non stanno dentro una conversazione perche' vengono prima di essa -- la
 * chiave di un gruppo, una richiesta di entrarci. Il server la trasporta e non puo' aprirla: e'
 * chiusa con la chiave di coppia, che nasce da X25519 fra le due identita' e che quindi esiste solo
 * sui due telefoni.
 *
 * ```
 * CDXB | versione(1) | tipo(1) | mittente(string) | destinatario(string) | nonce(12) |
 *   testo cifrato+tag | firma(64)
 * ```
 *
 * **Due protezioni diverse, e servono entrambe.** La chiave di coppia dice che il contenuto e'
 * illeggibile a chiunque altro; la firma Ed25519 dice **chi l'ha scritto**, e senza di lei una
 * chiave di gruppo sarebbe accettata da chiunque riuscisse a calcolare quella chiave di coppia --
 * cioe' dall'altra persona della coppia, il che va bene, ma anche da chiunque le rubasse
 * l'identita' senza le sue chiavi di firma. Costa 64 byte.
 *
 * Mittente e destinatario stanno **dentro i dati autenticati**: una busta destinata a qualcuno non
 * si puo' riconsegnare a un altro, e non si puo' rimandare indietro a chi l'ha scritta.
 */
object InboxParcel {

  private val MAGIC = "CDXB".toByteArray(Charsets.US_ASCII)
  private const val VERSION = 1

  class Malformed(message: String) : Exception(message)

  /** Di cosa parla una busta. Il numero e' il formato: non si cambia, si aggiunge in fondo. */
  enum class Kind {
    /** La chiave di un gruppo, a una certa epoca. */
    GROUP_KEY,

    /** "Fammi entrare": chi ha un invito lo presenta a chi glielo ha dato. */
    GROUP_JOIN,

    /** "Me ne sono andato" oppure "ti ho tolto": chi resta deve rifare la chiave. */
    GROUP_LEFT,
    ;

    companion object {
      fun of(ordinal: Int): Kind? = entries.getOrNull(ordinal)
    }
  }

  class Opened(val kind: Kind, val senderCodexId: String, val payload: ByteArray)

  /**
   * Chiude un contenuto per una persona sola.
   *
   * [pairKey] e' `CodexIdentity.pairKey(destinatario)`: chi chiama ce l'ha gia', e passarla qui
   * invece di ricavarla dentro tiene questo oggetto ignaro delle identita'.
   */
  fun seal(
    pairKey: ByteArray,
    signingPrivateKey: ByteArray,
    kind: Kind,
    senderCodexId: String,
    recipientCodexId: String,
    payload: ByteArray,
  ): ByteArray {
    val nonce = Digests.randomBytes(Aead.NONCE_SIZE)
    val header = ByteWriter(64)
      .bytes(MAGIC)
      .u8(VERSION)
      .u8(kind.ordinal)
      .string(senderCodexId)
      .string(recipientCodexId)
      .bytes(nonce)
      .build()
    val ciphertext = Aead.encrypt(
      key = pairKey,
      nonce = nonce,
      plaintext = payload,
      // Tutta l'intestazione: chi, a chi, di che tipo. Cambiarne un byte rende la busta illeggibile
      // invece che credibile.
      associatedData = header,
    )
    val body = header + ciphertext
    return body + IdentityKeys.sign(signingPrivateKey, body)
  }

  /**
   * Riapre una busta indirizzata a noi.
   *
   * Vuole la chiave pubblica di firma di chi dice di averla scritta: chi chiama la prende dalla
   * scheda del contatto, che e' firmata a sua volta. Senza quel controllo il mittente sarebbe solo
   * una stringa scritta dentro la busta.
   */
  fun open(
    pairKey: ByteArray,
    senderEd25519Public: ByteArray,
    recipientCodexId: String,
    parcel: ByteArray,
  ): Opened {
    if (parcel.size <= SIGNATURE_SIZE) throw Malformed("busta troppo corta")
    val body = parcel.copyOf(parcel.size - SIGNATURE_SIZE)
    val signature = parcel.copyOfRange(parcel.size - SIGNATURE_SIZE, parcel.size)
    if (!IdentityKeys.verify(senderEd25519Public, body, signature)) {
      throw Malformed("firma non valida")
    }

    val reader = ByteReader(body)
    try {
      reader.expectMagic(MAGIC)
    } catch (error: ByteReader.Malformed) {
      throw Malformed("non e' una busta di Codex")
    }
    val version = reader.u8()
    if (version != VERSION) throw Malformed("busta di versione $version")
    val kind = Kind.of(reader.u8()) ?: throw Malformed("tipo sconosciuto")
    val sender = reader.string()
    val recipient = reader.string()
    if (recipient != recipientCodexId) throw Malformed("busta non indirizzata a noi")
    val nonce = reader.bytes(Aead.NONCE_SIZE)
    val header = body.copyOf(reader.position)
    val ciphertext = reader.rest()

    val payload = try {
      Aead.decrypt(
        key = pairKey,
        nonce = nonce,
        ciphertext = ciphertext,
        associatedData = header,
      )
    } catch (error: Aead.DecryptionFailed) {
      throw Malformed("busta non apribile con questa chiave")
    }
    return Opened(kind, sender, payload)
  }

  /** Che tipo di busta e', senza aprirla: serve a decidere se vale la pena cercare la chiave. */
  fun kindOf(parcel: ByteArray): Kind? = runCatching {
    val reader = ByteReader(parcel)
    reader.expectMagic(MAGIC)
    reader.u8()
    Kind.of(reader.u8())
  }.getOrNull()

  private const val SIGNATURE_SIZE = 64
}
