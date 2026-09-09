package dev.pampa.codex.ui.groups

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antigravity.fluidengine.ui.fluid.FluidAlert
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.pampa.codex.R
import dev.pampa.codex.ui.theme.codexHorizontalPadding

/**
 * Entrare in un gruppo.
 *
 * Si incolla l'invito e si bussa. Quello che succede dopo non dipende da questa schermata: chi ha
 * invitato deve aprire Codex una volta, perche' la chiave la consegna il suo telefono. E' scritto li'
 * sotto, perche' altrimenti l'attesa sembrerebbe un guasto.
 */
@Composable
fun JoinGroupScreen(
  onBack: () -> Unit,
  viewModel: JoinGroupViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val clipboard = LocalClipboardManager.current

  FluidScreen(
    title = stringResource(R.string.group_join),
    subtitle = stringResource(R.string.group_join_detail),
    ambient = remember { FluidAmbient(tone = FluidHeroTone.Primary, motif = FluidHeroMotif.Glow) },
    horizontalPadding = codexHorizontalPadding(),
    onBack = onBack,
  ) {
    item(key = "link") {
      FluidTextField(
        value = state.link,
        onValueChange = viewModel::setLink,
        placeholder = stringResource(R.string.group_join_hint),
        singleLine = false,
        maxLines = 3,
      )
    }

    item(key = "paste") {
      FluidListGroup(glass = true) {
        FluidListRow(
          title = stringResource(R.string.action_paste),
          subtitle = stringResource(R.string.group_join_hint),
          onClick = {
            clipboard.getText()?.text?.let(viewModel::setLink)
          },
        )
      }
    }

    item(key = "ask") {
      FluidButton(
        text = stringResource(R.string.group_join_action),
        onClick = viewModel::join,
        enabled = state.link.isNotBlank() && !state.asking,
        loading = state.asking,
        fillWidth = true,
        modifier = Modifier.padding(top = 12.dp),
      )
    }

    item(key = "note") {
      FluidSectionFootnote(text = stringResource(R.string.group_waiting_key_detail))
    }
  }

  val message = state.message
  if (message != null) {
    FluidAlert(
      onDismissRequest = viewModel::clearMessage,
      title = stringResource(message),
      actions = listOf(
        FluidAlertAction(
          label = stringResource(R.string.action_ok),
          onClick = {
            viewModel.clearMessage()
            if (state.done) onBack()
          },
        ),
      ),
    )
  }
}
