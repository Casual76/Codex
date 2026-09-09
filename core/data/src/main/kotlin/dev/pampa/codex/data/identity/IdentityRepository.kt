package dev.pampa.codex.data.identity

import dev.pampa.codex.crypto.CodexIdentity
import dev.pampa.codex.crypto.ContactCard
import dev.pampa.codex.crypto.Kdf
import dev.pampa.codex.crypto.SignedContactCard
import dev.pampa.codex.crypto.Vault
import dev.pampa.codex.data.prefs.CodexPreferences
import dev.pampa.codex.data.prefs.CodexProfile
import dev.pampa.codex.data.prefs.LockType
import javax.crypto.Cipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Dov'e' l'identita' in questo momento. L'oggetto vero non passa mai da qui. */
sealed interface IdentityState {
  /** Non si sa ancora: il repository non ha guardato su disco. */
  data object Loading : IdentityState

  /** Nessuna identita' su questo dispositivo: si va all'onboarding. */
  data object Absent : IdentityState

  /** C'e' un vault, ma e' chiuso. */
  data object Locked : IdentityState

  /** L'identita' e' in memoria e l'app puo' lavorare. */
  data object Unlocked : IdentityState
}

/** L'esito di un tentativo di sblocco, con quello che la schermata deve poter dire. */
sealed interface UnlockResult {
  data object Success : UnlockResult
  data class Wrong(val failedAttempts: Int, val attemptsBeforeWait: Int) : UnlockResult
  data class Waiting(val until: Long) : UnlockResult
  data class Failed(val reason: String) : UnlockResult
}

/**
 * L'identita' di chi usa questo telefono: crearla, aprirla, richiuderla.
 *
 * E' l'unico posto in cui una [CodexIdentity] esiste come oggetto vivo. Chi ha bisogno di firmare o
 * di derivare una chiave passa da qui; nessun altro modulo tiene un riferimento, cosi' `lock()`
 * basta davvero a togliere i segreti dalla memoria.
 *
 * Le operazioni costose (Argon2id: mezzo secondo e 64 MiB) girano su [Dispatchers.Default] e sono
 * serializzate da un mutex: due tentativi di sblocco insieme allocherebbero 128 MiB per niente.
 */
class IdentityRepository(
  private val preferences: CodexPreferences,
  private val vaultStore: VaultStore,
  private val keystore: KeystoreVaultCipher,
  private val kdfParams: Kdf.Params = Kdf.Params(),
  private val clock: () -> Long = System::currentTimeMillis,
) : CodexIdentitySource {

  private val mutex = Mutex()
  private val _state = MutableStateFlow<IdentityState>(IdentityState.Loading)
  val state: StateFlow<IdentityState> = _state.asStateFlow()

  override val unlocked: kotlinx.coroutines.flow.Flow<Boolean> =
    _state.map { it == IdentityState.Unlocked }

  @Volatile
  private var identity: CodexIdentity? = null

  /** Quando l'app e' passata in secondo piano. Serve al timeout della serratura. */
  @Volatile
  private var backgroundedAt: Long = 0

  /** Guarda su disco e decide se si va all'onboarding o alla schermata di sblocco. */
  suspend fun initialize() {
    if (_state.value != IdentityState.Loading) return
    _state.value = if (vaultStore.hasVault()) IdentityState.Locked else IdentityState.Absent
  }

  override fun identityOrNull(): CodexIdentity? = identity

  override fun requireIdentity(): CodexIdentity =
    identity ?: error("identita' non disponibile: l'app e' bloccata")

  /**
   * Crea l'identita' e la chiude nel segreto scelto. Da qui in poi il dispositivo ha un vault, e
   * l'app resta sbloccata fino alla prima chiusura.
   */
  suspend fun create(
    name: String,
    avatarSeed: Long,
    secret: String,
    lockType: LockType,
  ): CodexIdentity = mutex.withLock {
    withContext(Dispatchers.Default) {
      // Sovrascrivere un vault esistente significa perdere un'identita' per sempre. Se c'e' gia',
      // la strada e' [reset], che e' un gesto esplicito con il suo avviso.
      check(!vaultStore.hasVault()) { "esiste gia' un'identita' su questo dispositivo" }
      val fresh = CodexIdentity.generate()
      vaultStore.writeVault(Vault.seal(fresh, secret, kdfParams))
      preferences.setProfile(
        CodexProfile(name = name.trim(), avatarSeed = avatarSeed, codexId = fresh.codexId),
      )
      preferences.setLockType(lockType)
      preferences.setUnlockAttempts(failedAttempts = 0, lockedUntil = 0)
      identity = fresh
      _state.value = IdentityState.Unlocked
      fresh
    }
  }

  /** Apre il vault con il segreto. Conta i tentativi e impone l'attesa quando sono troppi. */
  suspend fun unlock(secret: String): UnlockResult = mutex.withLock {
    withContext(Dispatchers.Default) {
      val settings = preferences.currentLockSettings()
      val now = clock()
      if (settings.lockedUntil > now) return@withContext UnlockResult.Waiting(settings.lockedUntil)

      val blob = vaultStore.readVault() ?: return@withContext UnlockResult.Failed("vault assente")
      try {
        val opened = Vault.open(blob, secret)
        preferences.setUnlockAttempts(failedAttempts = 0, lockedUntil = 0)
        identity?.wipe()
        identity = opened
        restoreCodexId(opened)
        _state.value = IdentityState.Unlocked
        UnlockResult.Success
      } catch (error: Vault.WrongSecret) {
        val attempts = settings.failedAttempts + 1
        val wait = waitAfter(attempts)
        preferences.setUnlockAttempts(
          failedAttempts = attempts,
          lockedUntil = if (wait > 0) now + wait else 0,
        )
        if (wait > 0) {
          UnlockResult.Waiting(now + wait)
        } else {
          UnlockResult.Wrong(attempts, ATTEMPTS_BEFORE_WAIT - attempts)
        }
      } catch (error: Exception) {
        UnlockResult.Failed(error.message ?: "vault illeggibile")
      }
    }
  }

  /**
   * Rimette a posto la copia del Codex ID nelle preferenze, se manca.
   *
   * Quella copia non e' l'originale: l'originale sta nell'identita', dentro il vault. Esiste
   * perche' la schermata di sblocco deve poter salutare per nome e mostrare il minerale **a vault
   * ancora chiuso**, e per farlo quei tre dati devono stare fuori. Se si perdono -- succede solo se
   * qualcuno tocca i file dell'app, ma succede -- l'identita' resta intatta e "La mia scheda"
   * mostra un vuoto al posto dell'identificatore. Lo sblocco e' il primo momento in cui l'originale
   * torna leggibile, ed e' quindi il posto giusto per riscriverla.
   */
  private suspend fun restoreCodexId(opened: CodexIdentity) {
    val profile = preferences.currentProfile()
    if (profile.codexId.isBlank()) preferences.setProfile(profile.copy(codexId = opened.codexId))
  }

  /**
   * Apre il vault con la scorciatoia biometrica.
   *
   * Il `cipher` arriva **gia' autorizzato** da `BiometricPrompt`: e' il sistema ad aver visto
   * l'impronta, non l'app. Se la chiave e' stata invalidata nel frattempo, la scorciatoia si
   * cancella e si torna al segreto, che e' l'unica strada sempre valida.
   */
  suspend fun unlockWithBiometrics(cipher: Cipher): UnlockResult = mutex.withLock {
    withContext(Dispatchers.Default) {
      val blob = vaultStore.readBiometricVault()
        ?: return@withContext UnlockResult.Failed("scorciatoia assente")
      try {
        val plaintext = cipher.doFinal(blob.ciphertext)
        val opened = try {
          CodexIdentity.decode(plaintext)
        } finally {
          plaintext.fill(0)
        }
        preferences.setUnlockAttempts(failedAttempts = 0, lockedUntil = 0)
        identity?.wipe()
        identity = opened
        restoreCodexId(opened)
        _state.value = IdentityState.Unlocked
        UnlockResult.Success
      } catch (error: Exception) {
        disableBiometricsInternal()
        UnlockResult.Failed("scorciatoia non valida")
      }
    }
  }

  /** Il cifrario da passare a `BiometricPrompt` per aprire, o `null` se la scorciatoia non c'e'. */
  fun biometricUnlockCipher(): Cipher? {
    val blob = vaultStore.readBiometricVault() ?: return null
    return try {
      keystore.decryptCipher(blob.iv)
    } catch (error: KeystoreVaultCipher.Invalidated) {
      vaultStore.deleteBiometricVault()
      keystore.deleteKey()
      null
    } catch (error: KeystoreVaultCipher.Unavailable) {
      null
    }
  }

  /** Il cifrario da passare a `BiometricPrompt` per **attivare** la scorciatoia. */
  fun biometricEnrollCipher(): Cipher? = try {
    keystore.encryptCipher()
  } catch (error: KeystoreVaultCipher.Unavailable) {
    null
  }

  /** Attiva la scorciatoia: richiede l'app sbloccata e un cifrario appena autorizzato. */
  suspend fun enableBiometrics(cipher: Cipher): Boolean = mutex.withLock {
    val current = identity ?: return@withLock false
    val encoded = current.encode()
    try {
      val ciphertext = cipher.doFinal(encoded)
      val iv = cipher.iv ?: return@withLock false
      vaultStore.writeBiometricVault(VaultStore.BiometricBlob(iv = iv, ciphertext = ciphertext))
      preferences.setBiometricEnabled(true)
      true
    } catch (error: Exception) {
      false
    } finally {
      encoded.fill(0)
    }
  }

  suspend fun disableBiometrics() = mutex.withLock { disableBiometricsInternal() }

  private suspend fun disableBiometricsInternal() {
    vaultStore.deleteBiometricVault()
    keystore.deleteKey()
    preferences.setBiometricEnabled(false)
  }

  /**
   * Cambia il segreto: riapre il vault con quello vecchio e lo richiude con quello nuovo.
   *
   * La scorciatoia biometrica viene disattivata, non ricreata di nascosto: riattivarla e' un gesto
   * che l'utente deve rifare, perche' e' lui a dover sapere cosa apre il suo telefono.
   */
  suspend fun changeSecret(
    currentSecret: String,
    newSecret: String,
    lockType: LockType,
  ): Boolean = mutex.withLock {
    withContext(Dispatchers.Default) {
      val blob = vaultStore.readVault() ?: return@withContext false
      val opened = try {
        Vault.open(blob, currentSecret)
      } catch (error: Exception) {
        return@withContext false
      }
      vaultStore.writeVault(Vault.seal(opened, newSecret, kdfParams))
      preferences.setLockType(lockType)
      disableBiometricsInternal()
      identity?.wipe()
      identity = opened
      _state.value = IdentityState.Unlocked
      true
    }
  }

  /** Chiude l'app: i segreti vengono azzerati, non solo dimenticati. */
  fun lock() {
    identity?.wipe()
    identity = null
    if (_state.value == IdentityState.Unlocked) _state.value = IdentityState.Locked
  }

  fun onBackgrounded() {
    backgroundedAt = clock()
  }

  /** Al ritorno in primo piano: richiude se e' passato piu' tempo del consentito. */
  suspend fun lockIfExpired() {
    if (_state.value != IdentityState.Unlocked) return
    val timeout = preferences.currentLockSettings().timeout
    val since = clock() - backgroundedAt
    if (backgroundedAt > 0 && since >= timeout.millis) lock()
  }

  /** La scheda pubblica di questo utente, firmata. Serve al QR e al pairing. */
  override suspend fun signedCard(uid: String): SignedContactCard? {
    val current = identity ?: return null
    val profile = preferences.currentProfile()
    return ContactCard.of(
      identity = current,
      name = profile.name,
      avatarSeed = profile.avatarSeed,
      uid = uid,
    )
  }

  suspend fun rename(name: String) = preferences.setName(name.trim())

  suspend fun setAvatarSeed(seed: Long) = preferences.setAvatarSeed(seed)

  /** Cancella identita' e preferenze: il dispositivo torna come appena installato. */
  suspend fun reset() = mutex.withLock {
    identity?.wipe()
    identity = null
    vaultStore.deleteAll()
    keystore.deleteKey()
    preferences.clear()
    _state.value = IdentityState.Absent
  }

  private fun waitAfter(attempts: Int): Long = when {
    attempts < ATTEMPTS_BEFORE_WAIT -> 0
    attempts == ATTEMPTS_BEFORE_WAIT -> 30_000
    attempts == ATTEMPTS_BEFORE_WAIT + 1 -> 120_000
    attempts == ATTEMPTS_BEFORE_WAIT + 2 -> 600_000
    else -> 3_600_000
  }

  companion object {
    /** Quanti tentativi prima che l'attesa cominci. Argon2id e' gia' lento; questa e' cortesia. */
    const val ATTEMPTS_BEFORE_WAIT = 5

    /** La lunghezza minima dei due segreti: sotto, non si accetta. */
    const val PIN_LENGTH = 6
    const val MIN_PASSWORD_LENGTH = 8
  }
}
