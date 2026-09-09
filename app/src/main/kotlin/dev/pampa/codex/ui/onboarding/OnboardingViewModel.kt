package dev.pampa.codex.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pampa.codex.data.identity.IdentityRepository
import dev.pampa.codex.data.prefs.CodexPreferences
import dev.pampa.codex.data.prefs.LockType
import dev.pampa.codex.seal.Mineral
import dev.pampa.codex.seal.MineralGenerator
import javax.crypto.Cipher
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** I quattro passi dell'onboarding, nell'ordine in cui si incontrano. */
enum class OnboardingStep { WELCOME, NAME, SECRET, BIOMETRICS }

data class OnboardingUiState(
  val step: OnboardingStep = OnboardingStep.WELCOME,
  val name: String = "",
  val avatarSeed: Long = 0L,
  val lockType: LockType = LockType.PIN,
  val secret: String = "",
  val confirmation: String = "",
  val confirming: Boolean = false,
  val error: String? = null,
  val busy: Boolean = false,
  val biometricOffered: Boolean = false,
  val completed: Boolean = false,
) {
  val mineral: Mineral get() = MineralGenerator.avatar(avatarSeed)

  val nameIsValid: Boolean get() = name.trim().length >= 2

  val secretIsValid: Boolean
    get() = when (lockType) {
      LockType.PIN -> secret.length == IdentityRepository.PIN_LENGTH
      LockType.PASSWORD -> secret.length >= IdentityRepository.MIN_PASSWORD_LENGTH
    }
}

/**
 * L'onboarding: dare un nome, vederlo diventare un minerale, chiudere l'identita' a chiave.
 *
 * Il minerale non e' una decorazione dell'accoglienza: il seme che si sceglie qui e' quello che i
 * contatti vedranno per sempre accanto al nome, quindi vive nel profilo dal primo momento.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
  private val identityRepository: IdentityRepository,
  private val preferences: CodexPreferences,
) : ViewModel() {

  private val _uiState = MutableStateFlow(OnboardingUiState())
  val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

  init {
    // L'app e' stata chiusa fra la creazione dell'identita' e la fine dell'onboarding: si riprende
    // dall'ultimo passo invece di ricominciare, perche' ricominciare vorrebbe dire creare una
    // seconda identita' sopra la prima.
    if (identityRepository.identityOrNull() != null) {
      _uiState.value = OnboardingUiState(step = OnboardingStep.BIOMETRICS)
    }
  }

  /** Quante volte si e' chiesto "un altro": entra nel seme, cosi' resta deterministico dal nome. */
  private var reroll = 0

  fun setName(name: String) {
    _uiState.update {
      it.copy(name = name, avatarSeed = seedFor(name, reroll), error = null)
    }
  }

  fun rerollAvatar() {
    reroll += 1
    _uiState.update { it.copy(avatarSeed = seedFor(it.name, reroll)) }
  }

  fun setLockType(type: LockType) {
    _uiState.update {
      it.copy(lockType = type, secret = "", confirmation = "", confirming = false, error = null)
    }
  }

  fun setSecret(value: String) {
    _uiState.update { state ->
      val filtered = when (state.lockType) {
        LockType.PIN -> value.filter { it.isDigit() }.take(IdentityRepository.PIN_LENGTH)
        LockType.PASSWORD -> value
      }
      if (state.confirming) {
        state.copy(confirmation = filtered, error = null)
      } else {
        state.copy(secret = filtered, error = null)
      }
    }
  }

  /** Il tastierino: una cifra alla volta, e alla sesta si passa da solo al confronto. */
  fun appendDigit(digit: Char) {
    val state = _uiState.value
    val current = if (state.confirming) state.confirmation else state.secret
    if (current.length >= IdentityRepository.PIN_LENGTH) return
    setSecret(current + digit)
  }

  fun deleteDigit() {
    val state = _uiState.value
    val current = if (state.confirming) state.confirmation else state.secret
    setSecret(current.dropLast(1))
  }

  fun back() {
    _uiState.update { state ->
      when {
        state.confirming -> state.copy(confirming = false, confirmation = "", error = null)
        state.step == OnboardingStep.NAME -> state.copy(step = OnboardingStep.WELCOME)
        state.step == OnboardingStep.SECRET -> state.copy(step = OnboardingStep.NAME, secret = "")
        else -> state
      }
    }
  }

  fun goToName() {
    _uiState.update { it.copy(step = OnboardingStep.NAME, avatarSeed = seedFor(it.name, reroll)) }
  }

  fun goToSecret() {
    val state = _uiState.value
    if (!state.nameIsValid) return
    _uiState.update { it.copy(step = OnboardingStep.SECRET, error = null) }
  }

  /**
   * Conferma il segreto e crea l'identita'.
   *
   * Il secondo inserimento non e' burocrazia: se il primo aveva un refuso, l'identita' nasce chiusa
   * in un segreto che nessuno conosce, e non esiste modo di recuperarla.
   */
  fun confirmSecret(mismatchMessage: String) {
    val state = _uiState.value
    if (!state.confirming) {
      if (!state.secretIsValid) return
      _uiState.update { it.copy(confirming = true, confirmation = "") }
      return
    }
    if (state.confirmation != state.secret) {
      _uiState.update {
        it.copy(confirming = false, secret = "", confirmation = "", error = mismatchMessage)
      }
      return
    }
    createIdentity()
  }

  private fun createIdentity() {
    val state = _uiState.value
    if (state.busy) return
    _uiState.update { it.copy(busy = true, error = null) }
    viewModelScope.launch {
      runCatching {
        identityRepository.create(
          name = state.name,
          avatarSeed = state.avatarSeed,
          secret = state.secret,
          lockType = state.lockType,
        )
      }.onSuccess {
        // Il segreto sparisce dallo stato appena ha fatto il suo lavoro: non deve restare in un
        // oggetto che sopravvive alla schermata.
        _uiState.update {
          it.copy(
            busy = false,
            secret = "",
            confirmation = "",
            confirming = false,
            step = OnboardingStep.BIOMETRICS,
          )
        }
      }.onFailure { error ->
        _uiState.update { it.copy(busy = false, error = error.message) }
      }
    }
  }

  /** Il cifrario da far autorizzare al sistema per attivare la scorciatoia biometrica. */
  fun biometricEnrollCipher(): Cipher? = identityRepository.biometricEnrollCipher()

  fun enableBiometrics(cipher: Cipher) {
    viewModelScope.launch {
      identityRepository.enableBiometrics(cipher)
      _uiState.update { it.copy(biometricOffered = true) }
      finish()
    }
  }

  fun skipBiometrics() {
    _uiState.update { it.copy(biometricOffered = true) }
    finish()
  }

  private fun finish() {
    viewModelScope.launch {
      preferences.setOnboardingCompleted(true)
      _uiState.update { it.copy(completed = true) }
    }
  }

  private fun seedFor(name: String, reroll: Int): Long =
    MineralGenerator.seedOf(name) xor (reroll.toLong() * 0x9E3779B97F4A7C15uL.toLong())
}
