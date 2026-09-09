package dev.pampa.codex.data.identity

import android.content.Context
import java.io.File

/**
 * Dove vivono i due involucri dell'identita'.
 *
 * - `vault.bin` e' quello vero: l'identita' chiusa nel segreto della persona. Senza di lui non si
 *   entra da nessuna parte, ed e' l'unico che si porta su un altro telefono.
 * - `vault.bio` e' la scorciatoia: la stessa identita' chiusa in una chiave dell'Android Keystore
 *   che il sistema apre solo dopo un'impronta. Si puo' cancellare in qualsiasi momento senza
 *   perdere niente, ed e' esattamente cio' che succede quando la biometria si disattiva o quando
 *   l'utente registra un dito nuovo.
 *
 * Stanno in `filesDir`, che e' privato dell'app e non finisce nei backup (`allowBackup="false"`).
 */
class VaultStore(context: Context) {

  private val directory = File(context.filesDir, "identity")
  private val secretVault = File(directory, "vault.bin")
  private val biometricVault = File(directory, "vault.bio")

  fun hasVault(): Boolean = secretVault.isFile && secretVault.length() > 0

  fun hasBiometricVault(): Boolean = biometricVault.isFile && biometricVault.length() > 0

  fun readVault(): ByteArray? = secretVault.takeIf { it.isFile }?.readBytes()

  fun writeVault(blob: ByteArray) {
    directory.mkdirs()
    // Scrittura in due tempi: un'interruzione mentre si cambia il PIN non deve lasciare un vault
    // mezzo scritto, che sarebbe un'identita' persa per sempre.
    val temporary = File(directory, "vault.tmp")
    temporary.writeBytes(blob)
    if (!temporary.renameTo(secretVault)) {
      secretVault.writeBytes(blob)
      temporary.delete()
    }
  }

  /** L'involucro biometrico: il vettore di inizializzazione davanti, poi il testo cifrato. */
  fun readBiometricVault(): BiometricBlob? {
    if (!hasBiometricVault()) return null
    val bytes = biometricVault.readBytes()
    if (bytes.size < 2) return null
    val ivLength = bytes[0].toInt() and 0xFF
    if (bytes.size < 1 + ivLength) return null
    return BiometricBlob(
      iv = bytes.copyOfRange(1, 1 + ivLength),
      ciphertext = bytes.copyOfRange(1 + ivLength, bytes.size),
    )
  }

  fun writeBiometricVault(blob: BiometricBlob) {
    directory.mkdirs()
    require(blob.iv.size in 1..255) { "vettore di inizializzazione non valido" }
    biometricVault.writeBytes(byteArrayOf(blob.iv.size.toByte()) + blob.iv + blob.ciphertext)
  }

  fun deleteBiometricVault() {
    biometricVault.delete()
  }

  /** Il reset totale: l'identita' non esiste piu' su questo dispositivo. */
  fun deleteAll() {
    secretVault.delete()
    biometricVault.delete()
    File(directory, "vault.tmp").delete()
  }

  class BiometricBlob(val iv: ByteArray, val ciphertext: ByteArray)
}
