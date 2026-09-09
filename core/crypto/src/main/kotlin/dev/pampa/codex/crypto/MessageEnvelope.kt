package dev.pampa.codex.crypto

/**
 * La busta di un messaggio: il formato CDX3.
 *
 * E' l'unica forma in cui un messaggio esiste fuori dalla memoria dell'app — nel database, nel
 * server, dentro un dipinto. Chi la guarda vede la firma, la versione, l'epoca della chiave, la
 * lunghezza e un blocco di byte indistinguibili da rumore.
 *
 * **Chiave e nonce nascono dall'id del messaggio.** Non c'e' nessun contatore da tenere allineato
 * fra due telefoni e nessun nonce casuale da ricordare: l'id e' un UUID, quindi unico per
 * costruzione, e da li' si derivano sia la chiave del singolo messaggio sia il suo nonce. E' il
 * modo piu' semplice di rispettare la regola che con AES-GCM conta piu' di ogni altra: **la stessa
 * coppia chiave-nonce non deve capitare due volte.**
 *
 * I dati associati (chat, messaggio, mittente) non sono cifrati ma sono autenticati: spostare una
 * busta in un'altra chat, o attribuirla a un altro mittente, la rende illeggibile invece che
 * credibile.
 */
object MessageEnvelope {

  private val MAGIC = "CDX3".toByteArray(Charsets.US_ASCII)
  private const val VERSION = 1

  class Malformed(message: String) : Exception(message)

  /**
   * Chiude un messaggio.
   *
   * [messageId] deve essere unico dentro la chat: e' l'unica cosa che tiene separate due buste.
   */
  fun seal(
    chatKey: ByteArray,
    chatId: String,
    messageId: String,
    senderId: String,
    plaintext: ByteArray,
    keyEpoch: Int = 0,
  ): ByteArray {
    val messageKey = messageKey(chatKey, messageId)
    return try {
      val ciphertext = Aead.encrypt(
        key = messageKey,
        nonce = nonceFor(messageId),
        plaintext = plaintext,
        associatedData = associatedData(chatId, messageId, senderId),
      )
      ByteWriter(ciphertext.size + 16)
        .bytes(MAGIC)
        .u8(VERSION)
        .u16(keyEpoch)
        .u32(plaintext.size.toLong())
        .bytes(ciphertext)
        .build()
    } finally {
      messageKey.fill(0)
    }
  }

  /** Riapre un messaggio. Fallisce se la chiave, la chat, l'id o il mittente non combaciano. */
  fun open(
    chatKey: ByteArray,
    chatId: String,
    messageId: String,
    senderId: String,
    envelope: ByteArray,
  ): ByteArray {
    val reader = ByteReader(envelope)
    try {
      reader.expectMagic(MAGIC)
    } catch (error: ByteReader.Malformed) {
      throw Malformed("non e' una busta di Codex")
    }
    val version = reader.u8()
    if (version != VERSION) throw Malformed("busta di versione $version")
    reader.u16() // epoca: la chiave giusta la sceglie il chiamante, qui si legge e basta
    val declaredLength = reader.u32().toInt()
    val ciphertext = reader.rest()

    val messageKey = messageKey(chatKey, messageId)
    val plaintext = try {
      Aead.decrypt(
        key = messageKey,
        nonce = nonceFor(messageId),
        ciphertext = ciphertext,
        associatedData = associatedData(chatId, messageId, senderId),
      )
    } catch (error: Aead.DecryptionFailed) {
      throw Malformed("busta non apribile con questa chiave")
    } finally {
      messageKey.fill(0)
    }
    if (plaintext.size != declaredLength) throw Malformed("lunghezza dichiarata non corrispondente")
    return plaintext
  }

  /** L'epoca della chiave con cui una busta e' stata chiusa, senza aprirla. */
  fun keyEpochOf(envelope: ByteArray): Int {
    val reader = ByteReader(envelope)
    reader.expectMagic(MAGIC)
    reader.u8()
    return reader.u16()
  }

  fun isEnvelope(bytes: ByteArray): Boolean {
    if (bytes.size < MAGIC.size) return false
    for (index in MAGIC.indices) if (bytes[index] != MAGIC[index]) return false
    return true
  }

  /**
   * Il seme del sigillo di questo messaggio.
   *
   * Nasce dalla chiave di chat e dall'id, quindi mittente e destinatario ottengono lo stesso numero
   * senza scambiarsi niente: e' cio' che rende la "sorpresa" identica per tutti e due.
   */
  fun sealSeed(chatKey: ByteArray, messageId: String): Long =
    Hkdf.deriveSeed(chatKey, "codex-seal-v1|$messageId")

  private fun messageKey(chatKey: ByteArray, messageId: String): ByteArray =
    Hkdf.derive(chatKey, "codex-msg-v1|$messageId", Aead.KEY_SIZE)

  private fun nonceFor(messageId: String): ByteArray =
    Digests.sha256(messageId.toByteArray(Charsets.UTF_8)).copyOf(Aead.NONCE_SIZE)

  private fun associatedData(chatId: String, messageId: String, senderId: String): ByteArray =
    "$chatId|$messageId|$senderId".toByteArray(Charsets.UTF_8)
}
