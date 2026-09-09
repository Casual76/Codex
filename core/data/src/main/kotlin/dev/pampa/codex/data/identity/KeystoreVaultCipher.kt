package dev.pampa.codex.data.identity

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * La chiave che il sistema apre solo dopo un'impronta.
 *
 * Vive nell'Android Keystore, quindi **non esiste come byte dentro l'app**: si puo' chiedere al
 * sistema di cifrare o decifrare con lei, non di consegnarla. Il `Cipher` che questa classe
 * restituisce non e' ancora autorizzato: lo diventa quando `BiometricPrompt` lo accetta, ed e' per
 * questo che un'app non puo' saltare il riconoscimento chiamando direttamente `doFinal`.
 *
 * `setInvalidatedByBiometricEnrollment` e' la parte che conta: se qualcuno aggiunge un proprio dito
 * al telefono, la chiave viene distrutta dal sistema e la scorciatoia sparisce. Il vault vero,
 * protetto dal PIN, resta intatto -- ed e' esattamente il comportamento che si vuole.
 */
class KeystoreVaultCipher {

  class Unavailable(message: String, cause: Throwable? = null) : Exception(message, cause)

  /** La chiave e' stata invalidata (nuove impronte, o schermata di blocco rimossa). */
  class Invalidated(cause: Throwable? = null) : Exception("chiave biometrica non piu' valida", cause)

  fun deleteKey() {
    runCatching { keyStore().deleteEntry(KEY_ALIAS) }
  }

  fun hasKey(): Boolean = runCatching { keyStore().containsAlias(KEY_ALIAS) }.getOrDefault(false)

  /** Il cifrario per **creare** la scorciatoia: crea la chiave se non c'e'. */
  fun encryptCipher(): Cipher {
    val cipher = newCipher()
    try {
      cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
    } catch (error: KeyPermanentlyInvalidatedException) {
      // Una chiave invalidata non torna: si butta e se ne fa una nuova, che l'utente autorizzera'.
      deleteKey()
      cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
    }
    return cipher
  }

  /** Il cifrario per **usare** la scorciatoia, con il vettore salvato accanto al testo cifrato. */
  fun decryptCipher(iv: ByteArray): Cipher {
    val key = existingKey() ?: throw Unavailable("nessuna chiave biometrica")
    val cipher = newCipher()
    try {
      cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
    } catch (error: KeyPermanentlyInvalidatedException) {
      throw Invalidated(error)
    }
    return cipher
  }

  private fun newCipher(): Cipher = try {
    Cipher.getInstance("$ALGORITHM/$BLOCK_MODE/$PADDING")
  } catch (error: Exception) {
    throw Unavailable("cifrario non disponibile", error)
  }

  private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

  private fun existingKey(): SecretKey? = runCatching {
    keyStore().getKey(KEY_ALIAS, null) as? SecretKey
  }.getOrNull()

  private fun getOrCreateKey(): SecretKey {
    existingKey()?.let { return it }
    return try {
      val generator = KeyGenerator.getInstance(ALGORITHM, PROVIDER)
      generator.init(
        KeyGenParameterSpec.Builder(
          KEY_ALIAS,
          KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
          .setBlockModes(BLOCK_MODE)
          .setEncryptionPaddings(PADDING)
          .setKeySize(256)
          .setUserAuthenticationRequired(true)
          // -1 significa "un'autenticazione per ogni uso", che e' l'unica impostazione onesta per
          // una chiave che apre l'identita': una finestra di validita' a tempo lascerebbe aperta
          // la porta anche dopo che lo schermo si e' spento.
          .setUserAuthenticationValidityDurationSeconds(-1)
          .setInvalidatedByBiometricEnrollment(true)
          .build(),
      )
      generator.generateKey()
    } catch (error: Exception) {
      throw Unavailable("impossibile creare la chiave biometrica", error)
    }
  }

  private companion object {
    const val PROVIDER = "AndroidKeyStore"
    const val KEY_ALIAS = "codex_vault_bio_v1"
    const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
    const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
    const val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
    const val TAG_BITS = 128
  }
}
