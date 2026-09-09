package dev.pampa.codex.ui.paintings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPresentation
import dev.pampa.codex.ui.theme.CodexSheetColumn
import dev.pampa.codex.R
import dev.pampa.codex.data.chat.PaintingImport

/**
 * Il pannello che compare quando arriva un quadro.
 *
 * Sta alla radice e non dentro una schermata perche' un'immagine puo' arrivare in qualunque
 * momento, anche mentre si sta guardando tutt'altro.
 *
 * I cinque esiti hanno cinque testi diversi e non un "non ha funzionato": il caso piu' frequente
 * -- l'immagine inoltrata come foto invece che come file -- si risolve rimandandola nel modo
 * giusto, e chi non lo sa non ha modo di indovinarlo.
 */
@Composable
fun PaintingImportHost(
  onOpenChat: (String) -> Unit,
  viewModel: PaintingImportViewModel = hiltViewModel(),
) {
  val pending by viewModel.pending.collectAsStateWithLifecycle()
  val state by viewModel.state.collectAsStateWithLifecycle()

  LaunchedEffect(pending) {
    pending?.let(viewModel::open)
  }

  val done = state as? ImportState.Done
  FluidGlassModalPortal(
    visible = done != null,
    onDismissRequest = viewModel::dismiss,
    presentation = FluidGlassModalPresentation.Sheet,
    paneTitle = stringResource(R.string.painting_import_pane),
  ) {
    val outcome = done?.outcome ?: return@FluidGlassModalPortal
    val (title, detail) = when (outcome) {
      is PaintingImport.Delivered -> R.string.painting_in_title to R.string.painting_in_detail
      is PaintingImport.AlreadyHere -> R.string.painting_known_title to R.string.painting_known_detail
      PaintingImport.UnknownChat -> R.string.painting_foreign_title to R.string.painting_foreign_detail
      PaintingImport.NothingInside -> R.string.painting_empty_title to R.string.painting_empty_detail
      PaintingImport.Unreadable -> R.string.painting_locked_title to R.string.painting_locked_detail
    }
    val chatId = when (outcome) {
      is PaintingImport.Delivered -> outcome.chatId
      is PaintingImport.AlreadyHere -> outcome.chatId
      else -> null
    }

    CodexSheetColumn(
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier
        .padding(horizontal = 24.dp)
        .padding(bottom = 28.dp),
    ) {
      Text(
        text = stringResource(title),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = stringResource(detail),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (chatId != null) {
        FluidButton(
          text = stringResource(R.string.painting_open_chat),
          onClick = {
            viewModel.dismiss()
            onOpenChat(chatId)
          },
          fillWidth = true,
        )
      }
      FluidButton(
        text = stringResource(R.string.action_close),
        onClick = viewModel::dismiss,
        style = FluidButtonStyle.Tinted,
        fillWidth = true,
      )
    }
  }
}
