package dev.pampa.codex.crypto

/**
 * Il vault: l'identita' chiusa dentro il segreto scelto dalla persona (PIN o password).
 *
 * Il file contiene **anche i parametri di Argon2id** con cui e' stato creato. Sembra una stranezza
 * -- perche' scrivere in chiaro quanto costa aprirlo? -- ma e' l'unico modo perche' un vault fatto
 * su un telefono lento resti apribile su uno veloce, e perche' domani si possano alzare i costi
 * senza rendere illeggibile quello che c'e' gia'.
 *
 * Cosa protegge davvero: chi prende il telefono spento, o il file di backup, ha solo questo blob.
 * Senza il segreto deve provare Argon2id per ogni tentativo, che a 64 MiB e tre passate significa
 * decine di anni per un PIN di sei cifre su hardware normale.
 */
object Vault {

  private val MAGIC = "CDXVLT".toByteArray(Charsets.US_ASCII)
  private const val VERSION = 1
  private const val KDF_ARGON2ID = 1

  class WrongSecret(message: String = "segreto errato") : Exception(message)

  /** Chiude l'identita' con il segreto. Il salt e il nonce sono nuovi a ogni chiamata. */
  fun seal(
    identity: CodexIdentity,
    secret: String,
    params: Kdf.Params = Kdf.Params(),
  ): ByteArray {
    val salt = Kdf.randomSalt()
    val nonce = Aead.randomNonce()
    val key = Kdf.deriveKey(secret, salt, params)
    return try {
      val header = header(params, salt, nonce)
      // L'intestazione entra come dato associato: cambiare i parametri di costo nel file, per
      // esempio per farlo aprire piu' in fretta, invalida il tag invece di riuscire.
      val ciphertext = Aead.encrypt(key, nonce, identity.encode(), header)
      header + ciphertext
    } finally {
      key.fill(0)
    }
  }

  /** Riapre il vault. [WrongSecret] e' l'unica cosa che dice; non distingue i motivi. */
  fun open(blob: ByteArray, secret: String): CodexIdentity {
    val parsed = parse(blob)
    val key = Kdf.deriveKey(secret, parsed.salt, parsed.params)
    return try {
      val plaintext = try {
        Aead.decrypt(key, parsed.nonce, parsed.ciphertext, parsed.header)
      } catch (error: Aead.DecryptionFailed) {
        throw WrongSecret()
      }
      try {
        CodexIdentity.decode(plaintext)
      } finally {
        plaintext.fill(0)
      }
    } finally {
      key.fill(0)
    }
  }

  /** I parametri con cui questo vault e' stato creato, senza aprirlo. */
  fun paramsOf(blob: ByteArray): Kdf.Params = parse(blob).params

  private class Parsed(
    val params: Kdf.Params,
    val salt: ByteArray,
    val nonce: ByteArray,
    val header: ByteArray,
    val ciphertext: ByteArray,
  )

  private fun header(params: Kdf.Params, salt: ByteArray, nonce: ByteArray): ByteArray =
    ByteWriter(48)
      .bytes(MAGIC)
      .u8(VERSION)
      .u8(KDF_ARGON2ID)
      .u32(params.memoryKiB.toLong())
      .u32(params.iterations.toLong())
      .u8(params.parallelism)
      .blob(salt)
      .blob(nonce)
      .build()

  private fun parse(blob: ByteArray): Parsed {
    val reader = ByteReader(blob)
    reader.expectMagic(MAGIC)
    val version = reader.u8()
    if (version != VERSION) throw ByteReader.Malformed("vault di versione $version")
    val kdf = reader.u8()
    if (kdf != KDF_ARGON2ID) throw ByteReader.Malformed("derivazione sconosciuta: $kdf")
    val params = Kdf.Params(
      memoryKiB = reader.u32().toInt(),
      iterations = reader.u32().toInt(),
      parallelism = reader.u8(),
    )
    val salt = reader.blob()
    val nonce = reader.blob()
    val headerLength = reader.position
    return Parsed(
      params = params,
      salt = salt,
      nonce = nonce,
      header = blob.copyOfRange(0, headerLength),
      ciphertext = reader.rest(),
    )
  }
}
