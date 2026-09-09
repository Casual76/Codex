package dev.pampa.codex.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antigravity.fluidengine.foundation.AccentMode
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.storage.EngineSettingsStore
import dev.pampa.codex.data.identity.IdentityRepository
import dev.pampa.codex.data.identity.IdentityState
import dev.pampa.codex.data.prefs.CodexPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Cosa deve esserci sullo schermo: l'onboarding, la serratura, o l'app. */
data class GateState(
  val identity: IdentityState = IdentityState.Loading,
  val onboardingCompleted: Boolean = false,
)

/**
 * Lo stato che tiene in piedi la radice: il tema e la porta d'ingresso.
 *
 * Il valore iniziale del tema e' il default di Codex e non quello dell'engine, cosi' il primo
 * fotogramma e' gia' scuro invece di lampeggiare chiaro finche' DataStore non risponde.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
  private val engineSettingsStore: EngineSettingsStore,
  private val identityRepository: IdentityRepository,
  preferences: CodexPreferences,
) : ViewModel() {

  init {
    viewModelScope.launch { identityRepository.initialize() }
  }

  val engineSettings: StateFlow<EngineSettings> = engineSettingsStore.settings
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = EngineSettings(
        themeMode = ThemeMode.DARK,
        accentMode = AccentMode.BRAND,
        dynamicColorEnabled = false,
      ),
    )

  val gate: StateFlow<GateState> =
    combine(identityRepository.state, preferences.onboardingCompleted) { identity, onboarding ->
      GateState(identity = identity, onboardingCompleted = onboarding)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GateState())

  /** L'app e' uscita dallo schermo: da qui parte il conto per la richiusura automatica. */
  fun onBackgrounded() = identityRepository.onBackgrounded()

  /** L'app e' tornata: se e' passato troppo tempo, si richiude. */
  fun onForegrounded() {
    viewModelScope.launch { identityRepository.lockIfExpired() }
  }

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch { engineSettingsStore.setThemeMode(mode) }
  }

  fun setDynamicColorEnabled(enabled: Boolean) {
    viewModelScope.launch {
      engineSettingsStore.update { current ->
        current.copy(
          dynamicColorEnabled = enabled,
          accentMode = if (enabled) AccentMode.DYNAMIC else AccentMode.BRAND,
        )
      }
    }
  }

  fun setAmoledEnabled(enabled: Boolean) {
    viewModelScope.launch { engineSettingsStore.setAmoledEnabled(enabled) }
  }

  fun setHapticsEnabled(enabled: Boolean) {
    viewModelScope.launch { engineSettingsStore.setHapticsEnabled(enabled) }
  }
}
