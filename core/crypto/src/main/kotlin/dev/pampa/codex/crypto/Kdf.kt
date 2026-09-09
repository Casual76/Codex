package dev.pampa.codex.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/**
 * Argon2id: da un PIN di sei cifre a una chiave che non si prova a indovinare.
 *
 * Un PIN ha un milione di combinazioni, che un computer prova in un istante se derivarne la chiave
 * costa poco. Argon2id costa memoria (64 MiB) oltre che tempo, e la memoria e' quello che rende
 * inutile una scheda grafica. E' il motivo per cui il vault di Codex regge un PIN corto.
 *
 * I parametri vengono **salvati insieme al vault**: un telefono lento puo' usarne di piu' leggeri,
 * e un vault creato oggi deve restare apribile anche se domani i valori consigliati cambiano.
 */
object Kdf {

  /** Quanto costa derivare la chiave. `memoryKiB` e' la difesa vera; il resto la accompagna. */
  data class Params(
    val memoryKiB: Int = DEFAULT_MEMORY_KIB,
    val iterations: Int = DEFAULT_ITERATIONS,
    val parallelism: Int = DEFAULT_PARALLELISM,
  ) {
    init {
      require(memoryKiB >= 8) { "memoria troppo bassa: $memoryKiB KiB" }
      require(iterations >= 1) { "iterazioni troppo poche: $iterations" }
      require(parallelism >= 1) { "parallelismo non valido: $parallelism" }
    }
  }

  const val DEFAULT_MEMORY_KIB = 64 * 1024
  const val DEFAULT_ITERATIONS = 3
  const val DEFAULT_PARALLELISM = 2

  /** I parametri ridotti per i dispositivi che non reggono 64 MiB senza farsi sentire. */
  val LIGHT = Params(memoryKiB = 32 * 1024, iterations = 3, parallelism = 2)

  const val SALT_SIZE = 16

  fun randomSalt(): ByteArray = Digests.randomBytes(SALT_SIZE)

  /**
   * La chiave che protegge il vault (KEK), dal segreto scelto dall'utente.
   *
   * Il segreto e' normalizzato in NFC prima di essere codificato: la stessa frase digitata con una
   * tastiera diversa deve produrre la stessa chiave, e su Android succede davvero che una "e'"
   * arrivi come lettera piu' accento invece che come singolo carattere.
   */
  fun deriveKey(
    secret: String,
    salt: ByteArray,
    params: Params = Params(),
    length: Int = Aead.KEY_SIZE,
  ): ByteArray = deriveKey(Normalize.nfc(secret).toByteArray(Charsets.UTF_8), salt, params, length)

  fun deriveKey(
    secret: ByteArray,
    salt: ByteArray,
    params: Params,
    length: Int,
  ): ByteArray {
    require(salt.size >= 8) { "salt troppo corto: ${salt.size} byte" }
    val generator = Argon2BytesGenerator()
    generator.init(
      Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
        .withVersion(Argon2Parameters.ARGON2_VERSION_13)
        .withSalt(salt)
        .withMemoryAsKB(params.memoryKiB)
        .withIterations(params.iterations)
        .withParallelism(params.parallelism)
        .build(),
    )
    return ByteArray(length).also { generator.generateBytes(secret, it, 0, length) }
  }

  /** La forma completa di Argon2id, con segreto e dati associati: serve ai vettori di RFC 9106. */
  fun deriveWithExtras(
    password: ByteArray,
    salt: ByteArray,
    secret: ByteArray,
    additional: ByteArray,
    params: Params,
    length: Int,
  ): ByteArray {
    val generator = Argon2BytesGenerator()
    generator.init(
      Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
        .withVersion(Argon2Parameters.ARGON2_VERSION_13)
        .withSalt(salt)
        .withSecret(secret)
        .withAdditional(additional)
        .withMemoryAsKB(params.memoryKiB)
        .withIterations(params.iterations)
        .withParallelism(params.parallelism)
        .build(),
    )
    return ByteArray(length).also { generator.generateBytes(password, it, 0, length) }
  }
}

/** La normalizzazione Unicode, isolata perche' e' l'unica cosa che il modulo prende dalla JDK. */
internal object Normalize {
  fun nfc(text: String): String =
    java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC)
}
