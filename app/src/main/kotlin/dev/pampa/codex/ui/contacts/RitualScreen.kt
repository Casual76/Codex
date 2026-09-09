package dev.pampa.codex.ui.contacts

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.rememberFluidHaptics
import dev.antigravity.fluidengine.ui.theme.FluidCard
import dev.pampa.codex.R
import dev.pampa.codex.seal.MineralGenerator
import dev.pampa.codex.ui.seal.MineralView
import dev.pampa.codex.ui.theme.codexHorizontalPadding

/** La gemma va vista da lontano: due persone la confrontano tenendo i telefoni affiancati. */
private val GemSize = 168.dp

/**
 * Il rito della parola d'ordine.
 *
 * E' la schermata piu' importante di tutta l'app, e non fa quasi niente: prende una parola e
 * mostra una gemma. Il punto e' che quella gemma **non e' decorazione**. Nasce dalla chiave, la
 * chiave nasce dalla parola, e due telefoni che mostrano la stessa gemma stanno dicendo, senza
 * bisogno di credere a nessun server, che hanno la stessa chiave e che nessuno si e' infilato in
 * mezzo.
 *
 * Per questo il testo non dice mai "verifica riuscita": non e' l'app ad averla fatta. Chiede se le
 * due gemme sono uguali, e aspetta che siano le persone a rispondere.
 */
@Composable
fun RitualScreen(
  onOpenChat: (String) -> Unit,
  onBack: () -> Unit,
  viewModel: RitualViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val haptics = rememberFluidHaptics()

  // Un tocco quando la gemma compare: e' il momento in cui la conversazione esiste.
  LaunchedEffect(state.emblemSeed) {
    if (state.emblemSeed != null) haptics.play(FluidHapticEvent.Confirm)
  }

  FluidScreen(
    title = stringResource(R.string.ritual_title),
    subtitle = state.contact?.name ?: stringResource(R.string.ritual_subtitle),
    horizontalPadding = codexHorizontalPadding(),
    onBack = onBack,
  ) {
    item(key = "body") {
      AnimatedContent(targetState = state.showingGem, label = "rito") { showingGem ->
        if (showingGem) {
          Gem(
            emblemSeed = state.emblemSeed ?: 0L,
            onEnter = { state.chatId?.let(onOpenChat) },
            onRetry = viewModel::tryAgain,
          )
        } else {
          Word(
            word = state.word,
            busy = state.busy,
            failed = state.failed,
            canProceed = state.canProceed,
            onWordChange = viewModel::setWord,
            onPerform = viewModel::perform,
          )
        }
      }
    }

    item(key = "footnote") {
      FluidSectionFootnote(
        text = stringResource(
          if (state.showingGem) R.string.ritual_footnote_gem else R.string.ritual_footnote_word,
        ),
      )
    }
  }
}

@Composable
private fun Word(
  word: String,
  busy: Boolean,
  failed: Boolean,
  canProceed: Boolean,
  onWordChange: (String) -> Unit,
  onPerform: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    FluidCard {
      Text(
        text = stringResource(R.string.ritual_explain),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    FluidTextField(
      value = word,
      onValueChange = onWordChange,
      label = stringResource(R.string.ritual_word_label),
      placeholder = stringResource(R.string.ritual_word_placeholder),
      isError = failed,
      supportingText = stringResource(
        if (failed) R.string.ritual_failed else R.string.ritual_word_support,
      ),
    )
    FluidButton(
      text = stringResource(R.string.ritual_perform),
      enabled = canProceed,
      loading = busy,
      fillWidth = true,
      onClick = onPerform,
    )
  }
}

@Composable
private fun Gem(emblemSeed: Long, onEnter: () -> Unit, onRetry: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    // La gemma sta su una superficie, non sul fondo della pagina.
    //
    // Non e' un vezzo: i minerali di Codex nascono da un seme, e fra loro ci sono ossidiane quasi
    // nere. In una conversazione stanno sempre dentro una bolla, quindi si vedono; qui, appoggiate
    // al fondo, una su dieci sparirebbe -- e sarebbe proprio quella che due persone devono
    // confrontare a un metro di distanza per sapere se qualcuno si e' messo in mezzo.
    FluidCard {
      Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
      ) {
        MineralView(
          mineral = remember(emblemSeed) { MineralGenerator.generate(emblemSeed) },
          modifier = Modifier.size(GemSize),
        )
      }
    }
    Text(
      text = stringResource(R.string.ritual_same_gem),
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
      textAlign = TextAlign.Center,
    )
    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      FluidButton(
        text = stringResource(R.string.ritual_yes),
        fillWidth = true,
        onClick = onEnter,
      )
      FluidButton(
        text = stringResource(R.string.ritual_no),
        style = FluidButtonStyle.Plain,
        fillWidth = true,
        onClick = onRetry,
      )
    }
  }
}
