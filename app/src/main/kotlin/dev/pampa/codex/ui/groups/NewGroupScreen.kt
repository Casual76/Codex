package dev.pampa.codex.ui.groups

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.pampa.codex.R
import dev.pampa.codex.seal.MineralGenerator
import dev.pampa.codex.ui.seal.MineralView
import dev.pampa.codex.ui.theme.codexHorizontalPadding

/**
 * Un gruppo nuovo: un nome e delle persone.
 *
 * Le persone sono **quelle gia' in rubrica**, e la ragione non e' di comodita': la chiave del
 * gruppo arriva a ciascuno dentro una busta chiusa con la chiave di coppia, che esiste solo fra due
 * che si sono gia' scambiati le schede. Un gruppo con dentro uno sconosciuto sarebbe un segreto
 * mandato a un indirizzo.
 */
@Composable
fun NewGroupScreen(
  onCreated: (String) -> Unit,
  onBack: () -> Unit,
  viewModel: NewGroupViewModel = hiltViewModel(),
) {
  val contacts by viewModel.contacts.collectAsStateWithLifecycle()
  val state by viewModel.state.collectAsStateWithLifecycle()

  LaunchedEffect(state.created) {
    state.created?.let(onCreated)
  }

  FluidScreen(
    title = stringResource(R.string.group_new),
    subtitle = stringResource(R.string.group_new_detail),
    ambient = remember { FluidAmbient(tone = FluidHeroTone.Primary, motif = FluidHeroMotif.Figures) },
    horizontalPadding = codexHorizontalPadding(),
    onBack = onBack,
  ) {
    item(key = "name") {
      FluidTextField(
        value = state.name,
        onValueChange = viewModel::setName,
        placeholder = stringResource(R.string.group_name_hint),
        singleLine = true,
        modifier = Modifier.padding(bottom = 8.dp),
      )
    }

    item(key = "members-header") {
      FluidSectionHeader(
        title = stringResource(R.string.group_members),
        detail = stringResource(R.string.group_members_detail),
      )
    }

    if (contacts.isEmpty()) {
      item(key = "empty") {
        FluidEmptyState(
          title = stringResource(R.string.group_members),
          detail = stringResource(R.string.group_no_contacts),
        )
      }
      return@FluidScreen
    }

    item(key = "members") {
      FluidListGroup(glass = true) {
        contacts.forEachIndexed { index, contact ->
          if (index > 0) FluidListDivider()
          val chosen = contact.codexId in state.chosen
          FluidListRow(
            title = contact.name,
            // Un contatto senza chiave si puo' mettere in un gruppo lo stesso: la chiave di coppia
            // c'e' comunque -- nasce dalle schede -- ed e' quella che serve per consegnargli quella
            // del gruppo. Il rito riguarda la chat a due, che e' un'altra conversazione.
            subtitle = contact.codexId,
            leading = {
              MineralView(
                mineral = remember(contact.avatarSeed) { MineralGenerator.avatar(contact.avatarSeed) },
                modifier = Modifier.size(30.dp),
                showGlow = false,
              )
            },
            badge = {
              if (chosen) {
                Icon(
                  imageVector = Icons.Rounded.Check,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(20.dp),
                )
              }
            },
            onClick = { viewModel.toggle(contact.codexId) },
          )
        }
      }
    }

    item(key = "create") {
      FluidButton(
        text = if (state.creating) {
          stringResource(R.string.group_creating)
        } else {
          stringResource(R.string.group_create)
        },
        onClick = viewModel::create,
        enabled = !state.creating,
        loading = state.creating,
        fillWidth = true,
        modifier = Modifier.padding(top = 12.dp),
      )
    }
  }

  val problem = state.problem
  if (problem != null) {
    FluidAlert(
      onDismissRequest = viewModel::clearProblem,
      title = stringResource(problem),
      actions = listOf(
        FluidAlertAction(
          label = stringResource(R.string.action_ok),
          onClick = viewModel::clearProblem,
        ),
      ),
    )
  }
}
