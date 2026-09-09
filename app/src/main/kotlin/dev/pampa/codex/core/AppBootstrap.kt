package dev.pampa.codex.core

import dev.antigravity.fluidengine.foundation.AccentMode
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.storage.EngineSettingsStore
import dev.pampa.codex.data.prefs.CodexPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Le impostazioni con cui Codex nasce.
 *
 * L'engine parte in "sistema + colore dinamico", che va bene per un'app qualsiasi; Codex e' scura e
 * ametista per scelta (PIANO.md §9), quindi la prima volta scrive quei valori nello store
 * dell'engine e poi non li tocca piu': da li' in avanti decide l'utente dalla scheda Io.
 */
@Singleton
class AppBootstrap @Inject constructor(
  private val engineSettings: EngineSettingsStore,
  private val preferences: CodexPreferences,
) {
  suspend fun ensureDefaults() {
    if (preferences.isThemeBootstrapped()) return
    engineSettings.update { current ->
      current.copy(
        themeMode = ThemeMode.DARK,
        accentMode = AccentMode.BRAND,
        dynamicColorEnabled = false,
      )
    }
    preferences.markThemeBootstrapped()
  }
}
