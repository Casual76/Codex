package dev.pampa.codex.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.theme.AccentPreset
import dev.antigravity.fluidengine.ui.theme.FluidTheme

/**
 * L'ametista di Codex, nelle due versioni che le servono.
 *
 * Non e' lo stesso colore usato due volte: su ossidiana e su avorio lo stesso RGB non mantiene ne'
 * il carattere ne' il contrasto. Da questa coppia l'engine deriva l'intera scala di superfici,
 * quindi cambiare qui cambia tutta l'app in modo coerente. I valori si verificano con il test di
 * contrasto dell'engine prima della prima beta (PIANO.md §9).
 */
val CodexBrand = AccentPreset(
  name = "codex",
  label = "Codex",
  light = Color(0xFF5E35A8),
  dark = Color(0xFFCBB1FF),
)

/**
 * I tre colori del QR, e perche' non seguono il tema.
 *
 * Un QR non e' una superficie dell'app: e' un **bersaglio ottico**. Una fotocamera lo legge dal
 * contrasto fra il foglio e l'inchiostro, e un codice che si fa chiaro su fondo scuro perche'
 * l'utente ha scelto il tema notte e' un codice che meta' dei lettori in circolazione rifiuta di
 * riconoscere. Quindi il foglio resta chiaro sempre, l'inchiostro resta scuro sempre, e l'ametista
 * compare solo nei tre occhi agli angoli, dove il contrasto rimane largo.
 *
 * E' l'unica eccezione alla regola "nessun colore scritto a mano in una schermata", e sta qui nel
 * file del tema invece che nella schermata proprio perche' resti un'eccezione dichiarata.
 */
val CodexQrPaper = Color(0xFFF7F4FB)
val CodexQrInk = Color(0xFF17131D)
val CodexQrEye = Color(0xFF5E35A8)

/** Il tema dell'app: `FluidTheme` con il marchio di Codex. Nessun colore scritto altrove. */
@Composable
fun CodexTheme(
  settings: EngineSettings,
  content: @Composable () -> Unit,
) {
  FluidTheme(
    settings = settings,
    brand = CodexBrand,
    content = content,
  )
}

/** Se, con queste impostazioni, il tema risolto e' scuro. Serve alle barre di sistema. */
@Composable
fun EngineSettings.resolvesToDark(): Boolean = when (themeMode) {
  ThemeMode.SYSTEM -> isSystemInDarkTheme()
  ThemeMode.LIGHT -> false
  ThemeMode.DARK, ThemeMode.AMOLED -> true
}
