package dev.pampa.codex.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
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
import dev.antigravity.fluidengine.ui.fluid.FluidSwitch
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTone
import dev.pampa.codex.ui.theme.codexHorizontalPadding
import dev.pampa.codex.R
import dev.pampa.codex.data.db.ChatKind
import dev.pampa.codex.data.db.GroupMemberEntity
import dev.pampa.codex.seal.MineralGenerator
import dev.pampa.codex.ui.lock.canConfirmIdentity
import dev.pampa.codex.ui.seal.MineralView

/**
 * Le impostazioni di una conversazione.
 *
 * Poche cose, e sono tutte sulla stessa domanda: **quanto resta**. La scadenza vale da quando un
 * messaggio viene aperto, non da quando arriva, ed e' scritto li' sotto perche' e' l'unica cosa che
 * si puo' sbagliare a intuito.
 */
@Composable
fun ChatInfoScreen(
  onBack: () -> Unit,
  onCleared: () -> Unit,
  viewModel: ChatInfoViewModel = hiltViewModel(),
) {
  val chat by viewModel.chat.collectAsStateWithLifecycle()
  val members by viewModel.members.collectAsStateWithLifecycle()
  val invite by viewModel.invite.collectAsStateWithLifecycle()
  var confirmClear by remember { mutableStateOf(false) }
  var confirmLeave by remember { mutableStateOf(false) }
  var pendingRemoval by remember { mutableStateOf<GroupMemberEntity?>(null) }
  val clipboard = LocalClipboardManager.current
  val isGroup = chat?.kind == ChatKind.GROUP
  // Chi comanda vede due cose in piu': invitare e togliere. Chi non comanda non le vede proprio,
  // invece di vederle e sentirsi dire di no dopo averle toccate.
  val iAmAdmin = members.any { it.admin && !it.gone && it.codexId == viewModel.myCodexId }

  val options = listOf(
    0 to stringResource(R.string.ttl_never),
    3_600 to stringResource(R.string.ttl_hour),
    86_400 to stringResource(R.string.ttl_day),
    604_800 to stringResource(R.string.ttl_week),
  )

  FluidScreen(
    title = chat?.title.orEmpty(),
    subtitle = stringResource(R.string.chat_info_subtitle),
    onBack = onBack,
    ambient = remember { FluidAmbient(tone = FluidHeroTone.Primary, motif = FluidHeroMotif.Glow) },
    horizontalPadding = codexHorizontalPadding(),
  ) {
    item(key = "emblem") {
      Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        MineralView(
          mineral = remember(chat?.emblemSeed, chat?.kind) {
            val seed = chat?.emblemSeed ?: 0L
            if (chat?.kind == ChatKind.SELF) {
              MineralGenerator.avatar(seed)
            } else {
              MineralGenerator.generate(seed)
            }
          },
          modifier = Modifier
            .padding(vertical = 8.dp)
            .size(112.dp),
        )
      }
    }

    item(key = "ttl-header") {
      FluidSectionHeader(
        title = stringResource(R.string.ttl_title),
        detail = stringResource(R.string.ttl_detail),
      )
    }
    item(key = "ttl") {
      FluidListGroup(glass = true) {
        options.forEachIndexed { index, (seconds, label) ->
          if (index > 0) FluidListDivider()
          FluidListRow(
            title = label,
            subtitle = if (seconds == 0) {
              stringResource(R.string.ttl_never_detail)
            } else {
              stringResource(R.string.ttl_from_opening)
            },
            meta = if (chat?.ttlSeconds == seconds) stringResource(R.string.technique_current) else null,
            onClick = { viewModel.setTtl(seconds) },
          )
        }
      }
    }

    if (isGroup) {
      item(key = "members-header") {
        FluidSectionHeader(
          title = stringResource(R.string.group_members),
          detail = stringResource(R.string.group_people, members.count { !it.gone }),
        )
      }
      item(key = "members") {
        FluidListGroup(glass = true) {
          members.forEachIndexed { index, membro ->
            if (index > 0) FluidListDivider()
            FluidListRow(
              title = membro.name.ifBlank { membro.codexId },
              subtitle = when {
                membro.gone -> stringResource(R.string.group_gone)
                membro.admin -> stringResource(R.string.group_admin)
                else -> membro.codexId
              },
              leading = {
                MineralView(
                  mineral = remember(membro.codexId) {
                    MineralGenerator.avatar(membro.codexId.hashCode().toLong())
                  },
                  modifier = Modifier.size(30.dp),
                  showGlow = false,
                )
              },
              // Tenendo premuto: le azioni su una persona. Un tasto per ciascuna, su ogni riga,
              // riempirebbe la lista di cose che si usano una volta l'anno.
              contextActions = azioniSuMembro(
                mostra = iAmAdmin && !membro.gone && membro.codexId != viewModel.myCodexId,
                rimanda = { viewModel.resendKey(membro.codexId) },
                togli = { pendingRemoval = membro },
              ),
            )
          }
        }
      }
      if (iAmAdmin) {
        item(key = "invite") {
          FluidListGroup(glass = true) {
            FluidListRow(
              title = stringResource(R.string.group_invite),
              subtitle = stringResource(R.string.group_invite_detail),
              onClick = viewModel::createInvite,
            )
          }
        }
      }
      item(key = "leave") {
        FluidListGroup(glass = true) {
          FluidListRow(
            title = stringResource(R.string.group_leave),
            subtitle = stringResource(R.string.group_leave_detail),
            tone = FluidTone.Danger,
            onClick = { confirmLeave = true },
          )
        }
      }
    }

    item(key = "lock") {
      // **La serratura di Codex si appoggia a quella del telefono.** Se il telefono non ne ha una,
      // non c'e' niente a cui chiedere "sei tu?", e un interruttore che si accende e non protegge
      // sarebbe peggio di un interruttore assente: chiuderebbe la conversazione a chi la possiede
      // senza chiuderla a nessun altro.
      val bloccata = chat?.locked == true
      // **Togliere la serratura si puo' sempre.** Metterla richiede che il telefono sappia dire "sei
      // tu?"; toglierla no, altrimenti una conversazione bloccata su un telefono che poi perde il
      // suo PIN resterebbe chiusa per sempre, e l'interruttore per riaprirla sarebbe spento.
      val puoBloccare = bloccata || canConfirmIdentity(LocalContext.current)
      FluidListGroup(glass = true) {
        FluidListRow(
          title = stringResource(R.string.chat_lock),
          subtitle = if (puoBloccare) {
            stringResource(R.string.chat_lock_detail)
          } else {
            stringResource(R.string.chat_lock_unavailable)
          },
          badge = {
            FluidSwitch(
              checked = bloccata,
              enabled = puoBloccare,
              onCheckedChange = viewModel::setLocked,
            )
          },
          onClick = { if (puoBloccare) viewModel.setLocked(!bloccata) },
        )
      }
    }

    item(key = "danger") {
      FluidListGroup(glass = true) {
        FluidListRow(
          title = stringResource(R.string.chat_clear),
          subtitle = stringResource(R.string.chat_clear_detail),
          tone = FluidTone.Danger,
          onClick = { confirmClear = true },
        )
      }
    }
    item(key = "note") {
      FluidSectionFootnote(text = stringResource(R.string.chat_info_note))
    }
  }

  val link = invite
  if (link != null) {
    FluidAlert(
      onDismissRequest = viewModel::clearInvite,
      title = stringResource(R.string.group_invite),
      message = stringResource(R.string.group_invite_copied),
      actions = listOf(
        FluidAlertAction(
          label = stringResource(R.string.action_copy),
          onClick = {
            clipboard.setText(AnnotatedString(link))
            viewModel.clearInvite()
          },
        ),
      ),
    )
  }

  if (confirmLeave) {
    FluidAlert(
      onDismissRequest = { confirmLeave = false },
      title = stringResource(R.string.group_leave),
      message = stringResource(R.string.group_leave_confirm),
      actions = listOf(
        FluidAlertAction(
          label = stringResource(R.string.action_cancel),
          onClick = { confirmLeave = false },
        ),
        FluidAlertAction(
          label = stringResource(R.string.group_leave),
          emphasis = FluidAlertAction.Emphasis.Destructive,
          onClick = {
            confirmLeave = false
            viewModel.leave(onCleared)
          },
        ),
      ),
    )
  }

  val removal = pendingRemoval
  if (removal != null) {
    FluidAlert(
      onDismissRequest = { pendingRemoval = null },
      title = stringResource(R.string.group_remove),
      message = stringResource(R.string.group_remove_confirm, removal.name),
      actions = listOf(
        FluidAlertAction(
          label = stringResource(R.string.action_cancel),
          onClick = { pendingRemoval = null },
        ),
        FluidAlertAction(
          label = stringResource(R.string.group_remove),
          emphasis = FluidAlertAction.Emphasis.Destructive,
          onClick = {
            viewModel.remove(removal.codexId)
            pendingRemoval = null
          },
        ),
      ),
    )
  }

  if (confirmClear) {
    FluidAlert(
      onDismissRequest = { confirmClear = false },
      title = stringResource(R.string.chat_clear),
      message = stringResource(R.string.chat_clear_message),
      actions = listOf(
        FluidAlertAction(
          label = stringResource(R.string.action_cancel),
          onClick = { confirmClear = false },
        ),
        FluidAlertAction(
          label = stringResource(R.string.chat_clear),
          emphasis = FluidAlertAction.Emphasis.Destructive,
          onClick = {
            confirmClear = false
            viewModel.clear()
            onCleared()
          },
        ),
      ),
    )
  }
}

/**
 * Le azioni su una persona di un gruppo, come le vuole `FluidListRow`.
 *
 * Sta fuori dalla riga perche' `contextActions` e' una **funzione** che verra' chiamata quando il
 * menu si apre, e le stringhe si leggono solo dentro una composizione: prenderle qui e chiuderle
 * nella lambda e' l'unico modo di avere tutte e due le cose.
 */
@Composable
private fun azioniSuMembro(
  mostra: Boolean,
  rimanda: () -> Unit,
  togli: () -> Unit,
): (() -> List<FluidContextAction>)? {
  if (!mostra) return null
  val etichettaRimanda = stringResource(R.string.group_resend_key)
  val etichettaTogli = stringResource(R.string.group_remove)
  return {
    listOf(
      FluidContextAction(label = etichettaRimanda, onClick = rimanda),
      FluidContextAction(label = etichettaTogli, destructive = true, onClick = togli),
    )
  }
}
