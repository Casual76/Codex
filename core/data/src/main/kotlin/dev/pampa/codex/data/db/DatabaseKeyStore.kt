package dev.pampa.codex.data.db

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dev.pampa.codex.crypto.Digests
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * La chiave che apre il database.
 *
 * Trentadue byte casuali, generati una volta e conservati cifrati con una chiave dell'Android
 * Keystore. Il file `db.key` da solo non serve a niente: la chiave che lo apre non esiste come
 * byte da nessuna parte, sta nell'hardware sicuro del telefono e non ne esce.
 *
 * A differenza della chiave del vault, questa **non chiede l'impronta**: deve funzionare mentre il
 * telefono e' bloccato, altrimenti un messaggio in arrivo non potrebbe essere salvato.
 */
class DatabaseKeyStore(private val context: Context) {

  private val keyFile: File get() = File(context.filesDir, FILE_NAME)

  /** La frase del database: la crea la prima volta, poi la rilegge. */
  fun passphrase(): ByteArray {
    readExisting()?.let { return it }
    val fresh = Digests.randomBytes(PASSPHRASE_SIZE)
    store(fresh)
    return fresh
  }

  fun delete() {
    keyFile.delete()
    runCatching { keyStore().deleteEntry(KEY_ALIAS) }
  }

  private fun readExisting(): ByteArray? {
    if (!keyFile.isFile) return null
    val key = existingKey() ?: return null
    return runCatching {
      val bytes = keyFile.readBytes()
      val ivLength = bytes[0].toInt() and 0xFF
      val iv = bytes.copyOfRange(1, 1 + ivLength)
      val ciphertext = bytes.copyOfRange(1 + ivLength, bytes.size)
      Cipher.getInstance(TRANSFORMATION).run {
        init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        doFinal(ciphertext)
      }
    }.getOrNull()
  }

  private fun store(passphrase: ByteArray) {
    val cipher = Cipher.getInstance(TRANSFORMATION).apply {
      init(Cipher.ENCRYPT_MODE, getOrCreateKey())
    }
    val ciphertext = cipher.doFinal(passphrase)
    val iv = cipher.iv
    keyFile.writeBytes(byteArrayOf(iv.size.toByte()) + iv + ciphertext)
  }

  private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

  private fun existingKey(): SecretKey? =
    runCatching { keyStore().getKey(KEY_ALIAS, null) as? SecretKey }.getOrNull()

  private fun getOrCreateKey(): SecretKey {
    existingKey()?.let { return it }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
    generator.init(
      KeyGenParameterSpec.Builder(
        KEY_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
      )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build(),
    )
    return generator.generateKey()
  }

  private companion object {
    const val PROVIDER = "AndroidKeyStore"
    const val KEY_ALIAS = "codex_db_v1"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val TAG_BITS = 128
    const val FILE_NAME = "db.key"
    const val PASSPHRASE_SIZE = 32
  }
}
