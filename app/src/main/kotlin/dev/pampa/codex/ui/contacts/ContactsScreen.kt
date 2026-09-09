package dev.pampa.codex.ui.contacts

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antigravity.fluidengine.ui.fluid.FluidAlert
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.pampa.codex.R
import dev.pampa.codex.data.contacts.Contact
import dev.pampa.codex.seal.MineralGenerator
import dev.pampa.codex.ui.seal.MineralView
import dev.pampa.codex.ui.theme.codexHorizontalPadding

/**
 * Le persone con cui si puo' parlare.
 *
 * In cima c'e' sempre **il proprio sigillo**, non in fondo e non dentro un menu: aggiungere
 * qualcuno e' un gesto a due, e meta' del gesto e' mostrare la propria scheda. Chi apre questa
 * schermata di solito ha l'altra persona accanto.
 *
 * Ogni contatto dice se la conversazione e' gia' stata aperta. Un contatto senza chiave non e' un
 * errore ne' un mezzo contatto: e' una persona con cui le schede sono state scambiate ma il rito
 * non e' ancora stato fatto, e finche' non lo si fa non c'e' niente da leggere.
 */
@Composable
fun ContactsScreen(
  onOpenChat: (String) -> Unit,
  onMySeal: () -> Unit,
  onAdd: () -> Unit,
  onRitual: (String) -> Unit,
  onBack: () -> Unit,
  viewModel: ContactsViewModel = hiltViewModel(),
) {
  val contacts by viewModel.contacts.collectAsStateWithLifecycle()
  val profile by viewModel.profile.collectAsStateWithLifecycle()
  var pendingDeletion by remember { mutableStateOf<Contact?>(null) }

  FluidScreen(
    title = stringResource(R.string.contacts_title),
    subtitle = stringResource(R.string.contacts_subtitle),
    ambient = remember { FluidAmbient(tone = FluidHeroTone.Primary, motif = FluidHeroMotif.Figures) },
    horizontalPadding = codexHorizontalPadding(),
    onBack = onBack,
  ) {
    item(key = "me") {
      FluidListGroup(glass = true) {
        FluidListRow(
          title = stringResource(R.string.contacts_my_seal),
          subtitle = profile.codexId.ifBlank { stringResource(R.string.contacts_my_seal_hint) },
          leading = {
            MineralView(
              mineral = remember(profile.avatarSeed) { MineralGenerator.avatar(profile.avatarSeed) },
              modifier = Modifier.size(30.dp),
              showGlow = false,
            )
          },
          onClick = onMySeal,
        )
        FluidListDivider()
        FluidListRow(
          title = stringResource(R.string.contacts_add),
          subtitle = stringResource(R.string.contacts_add_detail),
          onClick = onAdd,
        )
      }
    }

    if (contacts?.isEmpty() == true) {
      item(key = "empty") {
        FluidEmptyState(
          title = stringResource(R.string.contacts_empty_title),
          detail = stringResource(R.string.contacts_empty_detail),
        )
      }
      return@FluidScreen
    }

    item(key = "people-header") {
      FluidSectionHeader(title = stringResource(R.string.contacts_people))
    }

    item(key = "people") {
      val openLabel = stringResource(R.string.contacts_state_open)
      val sealedLabel = stringResource(R.string.contacts_state_sealed)
      val ritualLabel = stringResource(R.string.contacts_action_ritual)
      val deleteLabel = stringResource(R.string.contacts_action_delete)
      FluidListGroup(glass = true) {
        contacts.orEmpty().forEachIndexed { index, contact ->
          if (index > 0) FluidListDivider()
          FluidListRow(
            title = contact.name,
            subtitle = contact.codexId,
            meta = if (contact.hasKey) openLabel else sealedLabel,
            leading = {
              MineralView(
                mineral = remember(contact.avatarSeed) { MineralGenerator.avatar(contact.avatarSeed) },
                modifier = Modifier.size(30.dp),
                showGlow = false,
              )
            },
            onClick = {
              if (contact.hasKey) onOpenChat(contact.chatId) else onRitual(contact.codexId)
            },
            contextActions = {
              listOf(
                FluidContextAction(
                  label = ritualLabel,
                  icon = Icons.Rounded.Key,
                  onClick = { onRitual(contact.codexId) },
                ),
                FluidContextAction(
                  label = deleteLabel,
                  icon = Icons.Rounded.Delete,
                  destructive = true,
                  onClick = { pendingDeletion = contact },
                ),
              )
            },
          )
        }
      }
    }

    item(key = "footnote") {
      FluidSectionFootnote(text = stringResource(R.string.contacts_footnote))
    }
  }

  pendingDeletion?.let { contact ->
    FluidAlert(
      onDismissRequest = { pendingDeletion = null },
      title = stringResource(R.string.contacts_delete_title, contact.name),
      message = stringResource(R.string.contacts_delete_message),
      actions = listOf(
        FluidAlertAction(
          label = stringResource(R.string.action_cancel),
          onClick = { pendingDeletion = null },
        ),
        FluidAlertAction(
          label = stringResource(R.string.contacts_action_delete),
          emphasis = FluidAlertAction.Emphasis.Destructive,
          onClick = {
            viewModel.delete(contact.codexId)
            pendingDeletion = null
          },
        ),
      ),
    )
  }
}
