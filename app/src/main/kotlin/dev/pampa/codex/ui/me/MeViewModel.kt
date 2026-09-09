package dev.pampa.codex.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antigravity.fluidengine.foundation.AppUpdateInstallState
import dev.antigravity.fluidengine.foundation.AppUpdater
import dev.antigravity.fluidengine.foundation.AvailableAppUpdate
import dev.antigravity.fluidengine.foundation.UpdateChannel
import dev.pampa.codex.BuildConfig
import dev.pampa.codex.data.identity.IdentityRepository
import dev.pampa.codex.data.prefs.CodexPreferences
import dev.pampa.codex.data.prefs.CodexProfile
import dev.pampa.codex.data.prefs.LockSettings
import dev.pampa.codex.data.prefs.LockTimeout
import dev.pampa.codex.data.prefs.LockType
import dev.pampa.codex.data.prefs.SocialSignals
import dev.pampa.codex.seal.MineralGenerator
import javax.crypto.Cipher
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Lo stato della riga "Aggiornamenti": un valore per ogni cosa che la riga puo' dire. */
sealed interface UpdateUiState {
  data object Idle : UpdateUiState
  data object Checking : UpdateUiState
  data object Latest : UpdateUiState
  data class Available(val version: String) : UpdateUiState
  data object CheckFailed : UpdateUiState
  data class Downloading(val percent: Int) : UpdateUiState

  /** Un messaggio dell'installatore (verifica, installazione, attesa dell'utente). */
  data class Installing(val message: String) : UpdateUiState
  data object Installed : UpdateUiState
  data class Error(val message: String) : UpdateUiState
}

data class MeUiState(
  val profile: CodexProfile = CodexProfile(),
  val lock: LockSettings = LockSettings(),
  val signals: SocialSignals = SocialSignals(),
)

/** I tre momenti del cambio di segreto: il vecchio, il nuovo, la conferma. */
enum class ChangeSecretPhase { CURRENT, NEW, CONFIRM }

data class ChangeSecretState(
  val open: Boolean = false,
  val phase: ChangeSecretPhase = ChangeSecretPhase.CURRENT,
  val lockType: LockType = LockType.PIN,
  val current: String = "",
  val next: String = "",
  val confirmation: String = "",
  val error: Boolean = false,
  val busy: Boolean = false,
) {
  val entry: String
    get() = when (phase) {
      ChangeSecretPhase.CURRENT -> current
      ChangeSecretPhase.NEW -> next
      ChangeSecretPhase.CONFIRM -> confirmation
    }
}

/**
 * La scheda Io: profilo, sicurezza, aspetto, informazioni.
 *
 * Il cambio di segreto vive qui e non in una schermata a se' perche' e' una conversazione breve
 * (vecchio, nuovo, conferma) che non merita di far perdere il posto in cui si stava.
 */
@HiltViewModel
class MeViewModel @Inject constructor(
  private val updater: AppUpdater,
  private val identityRepository: IdentityRepository,
  private val preferences: CodexPreferences,
) : ViewModel() {

  val uiState: StateFlow<MeUiState> =
    combine(
      preferences.profile,
      preferences.lockSettings,
      preferences.socialSignals,
    ) { profile, lock, signals ->
      MeUiState(profile, lock, signals)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeUiState())

  private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
  val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

  private val _changeSecret = MutableStateFlow(ChangeSecretState())
  val changeSecret: StateFlow<ChangeSecretState> = _changeSecret.asStateFlow()

  private var available: AvailableAppUpdate? = null
  private var busy = false

  /** Con che cosa e' chiuso il vault in questo momento: serve a filtrare il segreto vecchio. */
  private val currentLockTypeIsPin: Boolean
    get() = uiState.value.lock.lockType == LockType.PIN

  // --- Profilo ---------------------------------------------------------------------------------

  fun rename(name: String) {
    if (name.isBlank()) return
    viewModelScope.launch { identityRepository.rename(name) }
  }

  /** Un altro minerale: cambia solo il seme, l'identita' resta la stessa. */
  fun rerollAvatar() {
    viewModelScope.launch {
      val current = preferences.currentProfile()
      val next = MineralGenerator.seedOf(current.name) xor (System.nanoTime() * 0x2545F4914F6CDD1DL)
      identityRepository.setAvatarSeed(next)
    }
  }

  // --- Cosa si lascia vedere ---------------------------------------------------------------

  fun setShowOnline(enabled: Boolean) {
    viewModelScope.launch { preferences.setShowOnline(enabled) }
  }

  fun setShowTyping(enabled: Boolean) {
    viewModelScope.launch { preferences.setShowTyping(enabled) }
  }

  // --- Sicurezza -------------------------------------------------------------------------------

  fun setLockTimeout(timeout: LockTimeout) {
    viewModelScope.launch { preferences.setLockTimeout(timeout) }
  }

  fun biometricEnrollCipher(): Cipher? = identityRepository.biometricEnrollCipher()

  fun enableBiometrics(cipher: Cipher) {
    viewModelScope.launch { identityRepository.enableBiometrics(cipher) }
  }

  fun disableBiometrics() {
    viewModelScope.launch { identityRepository.disableBiometrics() }
  }

  fun lockNow() = identityRepository.lock()

  fun resetEverything() {
    viewModelScope.launch { identityRepository.reset() }
  }

  fun openChangeSecret(lockType: LockType) {
    _changeSecret.value = ChangeSecretState(open = true, lockType = lockType)
  }

  fun closeChangeSecret() {
    _changeSecret.value = ChangeSecretState()
  }

  fun setChangeSecretType(type: LockType) {
    _changeSecret.update { it.copy(lockType = type, next = "", confirmation = "", error = false) }
  }

  fun changeSecretInput(value: String) {
    _changeSecret.update { state ->
      // Il segreto vecchio segue il tipo con cui il vault e' chiuso oggi; quello nuovo segue il
      // tipo che si sta scegliendo adesso. Sono due cose diverse e vanno filtrate diversamente.
      val digitsOnly = when (state.phase) {
        ChangeSecretPhase.CURRENT -> currentLockTypeIsPin
        else -> state.lockType == LockType.PIN
      }
      val filtered = if (digitsOnly) {
        value.filter { it.isDigit() }.take(IdentityRepository.PIN_LENGTH)
      } else {
        value
      }
      when (state.phase) {
        ChangeSecretPhase.CURRENT -> state.copy(current = filtered, error = false)
        ChangeSecretPhase.NEW -> state.copy(next = filtered, error = false)
        ChangeSecretPhase.CONFIRM -> state.copy(confirmation = filtered, error = false)
      }
    }
  }

  fun appendChangeSecretDigit(digit: Char) {
    val state = _changeSecret.value
    if (state.entry.length >= IdentityRepository.PIN_LENGTH) return
    val next = state.entry + digit
    changeSecretInput(next)
    if (next.length == IdentityRepository.PIN_LENGTH) advanceChangeSecret()
  }

  fun deleteChangeSecretDigit() {
    changeSecretInput(_changeSecret.value.entry.dropLast(1))
  }

  /** Avanza di un passo, e all'ultimo riscrive davvero il vault. */
  fun advanceChangeSecret() {
    val state = _changeSecret.value
    when (state.phase) {
      ChangeSecretPhase.CURRENT -> {
        if (state.current.isEmpty()) return
        _changeSecret.update { it.copy(phase = ChangeSecretPhase.NEW) }
      }

      ChangeSecretPhase.NEW -> {
        val valid = when (state.lockType) {
          LockType.PIN -> state.next.length == IdentityRepository.PIN_LENGTH
          LockType.PASSWORD -> state.next.length >= IdentityRepository.MIN_PASSWORD_LENGTH
        }
        if (!valid) return
        _changeSecret.update { it.copy(phase = ChangeSecretPhase.CONFIRM) }
      }

      ChangeSecretPhase.CONFIRM -> {
        if (state.confirmation != state.next) {
          _changeSecret.update {
            it.copy(phase = ChangeSecretPhase.NEW, next = "", confirmation = "", error = true)
          }
          return
        }
        _changeSecret.update { it.copy(busy = true) }
        viewModelScope.launch {
          val changed = identityRepository.changeSecret(
            currentSecret = state.current,
            newSecret = state.next,
            lockType = state.lockType,
          )
          if (changed) {
            _changeSecret.value = ChangeSecretState()
          } else {
            // Il segreto vecchio era sbagliato: si torna al primo passo, non si perde il resto.
            _changeSecret.value = ChangeSecretState(
              open = true,
              lockType = state.lockType,
              error = true,
            )
          }
        }
      }
    }
  }

  // --- Aggiornamenti ---------------------------------------------------------------------------

  fun checkOrInstall() {
    if (busy) return
    val pending = available
    viewModelScope.launch {
      busy = true
      try {
        if (pending == null) check() else install(pending)
      } finally {
        busy = false
      }
    }
  }

  private suspend fun check() {
    _updateState.value = UpdateUiState.Checking
    val channel = if (BuildConfig.VERSION_NAME.contains("beta")) UpdateChannel.BETA else UpdateChannel.STABLE
    updater.check(currentVersionName = BuildConfig.VERSION_NAME, channel = channel)
      .onSuccess { found ->
        available = found
        _updateState.value = if (found == null) UpdateUiState.Latest else UpdateUiState.Available(found.version)
      }
      .onFailure { _updateState.value = UpdateUiState.CheckFailed }
  }

  private suspend fun install(update: AvailableAppUpdate) {
    updater.install(update).collect { state ->
      _updateState.value = when (state) {
        is AppUpdateInstallState.Downloading -> UpdateUiState.Downloading((state.progress * 100).toInt())
        is AppUpdateInstallState.Verifying -> UpdateUiState.Installing(state.message)
        is AppUpdateInstallState.Installing -> UpdateUiState.Installing(state.message)
        is AppUpdateInstallState.AwaitingUserAction -> UpdateUiState.Installing(state.message)
        is AppUpdateInstallState.Installed -> UpdateUiState.Installed
        is AppUpdateInstallState.Error -> UpdateUiState.Error(state.message)
      }
    }
    available = null
  }
}
