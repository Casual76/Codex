package dev.pampa.codex.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pampa.codex.data.identity.IdentityRepository
import dev.pampa.codex.data.identity.UnlockResult
import dev.pampa.codex.data.prefs.CodexPreferences
import dev.pampa.codex.data.prefs.CodexProfile
import dev.pampa.codex.data.prefs.LockSettings
import dev.pampa.codex.data.prefs.LockType
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

data class LockUiState(
  val profile: CodexProfile = CodexProfile(),
  val settings: LockSettings = LockSettings(),
) {
  val lockType: LockType get() = settings.lockType
}

data class LockEntryState(
  val entry: String = "",
  val error: Boolean = false,
  val message: String? = null,
  val waitingUntil: Long = 0L,
  val busy: Boolean = false,
)

/**
 * La schermata di sblocco.
 *
 * Divisa in due flussi apposta: quello che viene dal disco (profilo e serratura) e quello che
 * l'utente sta digitando. Il secondo non deve mai finire in `DataStore`, nemmeno per sbaglio.
 */
@HiltViewModel
class LockViewModel @Inject constructor(
  private val identityRepository: IdentityRepository,
  preferences: CodexPreferences,
) : ViewModel() {

  val uiState: StateFlow<LockUiState> =
    combine(preferences.profile, preferences.lockSettings) { profile, settings ->
      LockUiState(profile, settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LockUiState())

  private val _entry = MutableStateFlow(LockEntryState())
  val entry: StateFlow<LockEntryState> = _entry.asStateFlow()

  fun appendDigit(digit: Char) {
    val state = _entry.value
    if (state.busy || state.entry.length >= IdentityRepository.PIN_LENGTH) return
    val next = state.entry + digit
    _entry.update { it.copy(entry = next, error = false, message = null) }
    if (next.length == IdentityRepository.PIN_LENGTH) submit(next)
  }

  fun deleteDigit() {
    _entry.update { it.copy(entry = it.entry.dropLast(1), error = false, message = null) }
  }

  fun setPassword(value: String) {
    _entry.update { it.copy(entry = value, error = false, message = null) }
  }

  fun submitPassword() {
    val current = _entry.value
    if (current.busy || current.entry.isEmpty()) return
    submit(current.entry)
  }

  private fun submit(secret: String) {
    _entry.update { it.copy(busy = true) }
    viewModelScope.launch {
      when (val result = identityRepository.unlock(secret)) {
        UnlockResult.Success -> _entry.value = LockEntryState()
        is UnlockResult.Wrong -> _entry.value = LockEntryState(error = true)
        is UnlockResult.Waiting -> _entry.value = LockEntryState(
          error = true,
          waitingUntil = result.until,
        )
        is UnlockResult.Failed -> _entry.value = LockEntryState(error = true, message = result.reason)
      }
    }
  }

  /** Il cifrario per il prompt di sistema, o `null` se la scorciatoia non c'e' piu'. */
  fun biometricCipher(): Cipher? = identityRepository.biometricUnlockCipher()

  fun unlockWithBiometrics(cipher: Cipher) {
    viewModelScope.launch {
      when (identityRepository.unlockWithBiometrics(cipher)) {
        UnlockResult.Success -> _entry.value = LockEntryState()
        else -> _entry.update { it.copy(error = true) }
      }
    }
  }

  /** L'unica via d'uscita da un segreto dimenticato: cancellare tutto e ricominciare. */
  fun resetEverything() {
    viewModelScope.launch { identityRepository.reset() }
  }
}
