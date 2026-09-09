package dev.pampa.codex.ui.tutorial

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antigravity.fluidengine.ui.tutorial.FluidGestureHint
import dev.antigravity.fluidengine.ui.tutorial.FluidTutorial
import dev.antigravity.fluidengine.ui.tutorial.FluidTutorialLabels
import dev.antigravity.fluidengine.ui.tutorial.LocalFluidTutorialHostState
import dev.pampa.codex.R
import dev.pampa.codex.data.prefs.CodexPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * I suggerimenti al primo uso.
 *
 * Codex ha due gesti che nessuna app ha e che quindi nessuno si aspetta: **si tocca il sigillo per
 * aprirlo**, e **si tiene premuto l'invio per scegliere in che forma parte il messaggio**. Senza
 * una parola, il primo si scopre per caso e il secondo non si scopre affatto.
 *
 * La risposta ovvia sarebbe un tour all'avvio. Il piano dice l'opposto (§2.2): nell'onboarding non
 * ci va niente, e ogni funzione si spiega **la prima volta che la si incontra**, con una frase e
 * accanto alla cosa. Un tour insegna cose che non servono ancora, e le insegna tutte insieme a chi
 * non ha ancora visto un messaggio.
 *
 * Da qui passa solo *quali* suggerimenti esistono e *quando* sono pronti. Il momento in cui
 * compaiono lo decide `FluidTutorialPolicy`: dito fermo, niente caricamenti, nessun pannello in
 * scena, e un'interazione fra uno e l'altro.
 */
object CodexHint {

  /** Il gesto dell'app. Priorita' piu' alta: se in una chat sono pronti tutti e due, parla lui. */
  const val SealTap = "seal-tap"

  /** La scelta della tecnica, che vive tutta dentro una pressione lunga. */
  const val SendHold = "send-hold"

  /** Che i messaggi possano passare senza rete non lo immagina nessuno: va detto. */
  const val Nearby = "nearby"
}

/**
 * Cosa sa l'app dei suggerimenti, in un posto solo.
 *
 * [screen] e' la rotta in cima alla pila e serve a [CodexHintOffer]: l'engine accetta un candidato
 * solo se dice la stessa schermata che il padrone di casa ha in scena, e farlo passare da qui
 * significa che un suggerimento si offre **dopo** che il cambio di schermata e' stato registrato,
 * invece che nello stesso fotogramma e con l'ordine deciso dal caso.
 */
@Immutable
data class CodexHintState(
  val screen: String = "",
  val seen: Set<String> = emptySet(),
  val silent: Boolean = false,
)

val LocalCodexHints: ProvidableCompositionLocal<CodexHintState> =
  compositionLocalOf { CodexHintState() }

/** Cosa farne di un suggerimento adesso. */
enum class HintAction { Offri, Ritira, Lascia }

/**
 * La regola, fuori dalla composizione perche' si possa provare senza uno schermo.
 *
 * Due condizioni non sono ovvie e le ha trovate l'emulatore, non il ragionamento:
 *
 * - **senza una schermata non si offre niente.** Il padrone di casa accetta un candidato solo se
 *   dice la stessa pagina che ha in scena; offrirlo prima che il cambio di rotta sia arrivato
 *   vuol dire buttarlo via in silenzio.
 * - **non si ritira quello che si sta leggendo.** Un suggerimento si segna visto nell'istante in
 *   cui compare, e quel segno torna qui come [CodexHintState.seen]: ritirarlo senza guardare chi e'
 *   in scena lo toglie dopo un lampo, lasciando solo l'alone sull'elemento indicato.
 */
fun hintAction(
  id: String,
  ready: Boolean,
  hints: CodexHintState,
  presentingId: String?,
): HintAction = when {
  ready && !hints.silent && id !in hints.seen && hints.screen.isNotEmpty() -> HintAction.Offri
  presentingId == id -> HintAction.Lascia
  else -> HintAction.Ritira
}

/**
 * Offre un suggerimento per l'elemento marcato con `Modifier.fluidTutorialAnchor(id)`.
 *
 * [ready] e' la condizione per cui la frase ha senso adesso: un "toccalo" senza un sigillo chiuso
 * sullo schermo non indica niente. Quando diventa falsa il candidato si ritira, cosi' un
 * suggerimento non resta in coda ad aspettare una cosa che non c'e' piu'.
 */
@Composable
fun CodexHintOffer(
  id: String,
  priority: Int,
  title: String,
  text: String,
  gesture: FluidGestureHint? = null,
  ready: Boolean = true,
) {
  val host = LocalFluidTutorialHostState.current ?: return
  val hints = LocalCodexHints.current
  val action = hintAction(id, ready, hints, host.presenting?.id)
  LaunchedEffect(host, hints.screen, action, id, title, text) {
    when (action) {
      HintAction.Offri -> host.offer(
        FluidTutorial(id = id, priority = priority, title = title, text = text, hint = gesture),
        hints.screen,
      )
      HintAction.Ritira -> host.withdraw(id)
      HintAction.Lascia -> Unit
    }
  }
}

/** Le parole dei tasti del callout, tradotte come il resto dell'app. */
@Composable
fun codexHintLabels(): FluidTutorialLabels = FluidTutorialLabels(
  dismiss = stringResource(R.string.hint_got_it),
  next = stringResource(R.string.hint_next),
  optOut = stringResource(R.string.hint_enough),
)

/**
 * Quello che resta dei suggerimenti fra un'apertura e l'altra.
 *
 * Sta in un ViewModel della radice e non in ogni schermata: la lista dei visti e' una sola, e due
 * copie che si scrivono a vicenda finirebbero per far ricomparire una frase gia' letta.
 */
@HiltViewModel
class CodexHintsViewModel @Inject constructor(
  private val preferences: CodexPreferences,
) : ViewModel() {

  val state: StateFlow<CodexHintState> =
    combine(preferences.hintsSeen, preferences.hintsSilent) { seen, silent ->
      CodexHintState(seen = seen, silent = silent)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CodexHintState())

  fun markSeen(id: String) {
    viewModelScope.launch { preferences.markHintSeen(id) }
  }

  fun silence() {
    viewModelScope.launch { preferences.silenceHints() }
  }
}
