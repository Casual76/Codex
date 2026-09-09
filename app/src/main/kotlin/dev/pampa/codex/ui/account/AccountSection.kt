package dev.pampa.codex.ui.account

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.antigravity.fluidengine.ui.fluid.FluidAlert
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.theme.FluidTone
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.pampa.codex.R
import dev.pampa.codex.data.cloud.AccountState
import kotlinx.coroutines.launch

/**
 * L'account, nella scheda "Io".
 *
 * Sta **sotto il profilo e sopra la sicurezza**, e la posizione e' un'affermazione: l'account non e'
 * chi sei per Codex, e' solo il modo in cui il server sa a chi consegnare. Chi non lo collega perde
 * la ricerca per Codex ID e la consegna; non perde niente di quello che ha in casa.
 */
@Composable
fun AccountSection(viewModel: AccountViewModel = hiltViewModel()) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  // Il client web del progetto: lo scrive il plugin google-services leggendo google-services.json.
  // Se il file non c'e', la risorsa non esiste e la stringa resta vuota: la sezione lo dice invece
  // di provarci e fallire.
  val serverClientId = runCatching { stringResource(R.string.default_web_client_id) }.getOrDefault("")

  // **Il permesso di notificare si chiede qui, e non prima.**
  //
  // Da Android 13 le notifiche vanno concesse, e una richiesta che compare all'avvio -- prima che
  // l'app abbia qualcosa da notificare -- e' quella che la gente rifiuta per riflesso e poi non sa
  // piu' come riattivare. Appena c'e' un account invece la domanda ha una risposta ovvia: e' il
  // momento in cui qualcuno puo' cominciare a scriverti.
  val askNotifications = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { }
  LaunchedEffect(state.message) {
    if (state.message == AccountMessage.Published && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  var confirmDelete by remember { mutableStateOf(false) }

  // Eliminare l'account non e' scollegarsi, e la differenza si spiega **prima**: qui sparisce quello
  // che il server sapeva, e non sparisce niente di quello che sta su questo telefono.
  if (confirmDelete) {
    FluidAlert(
      onDismissRequest = { confirmDelete = false },
      title = stringResource(R.string.account_delete),
      message = stringResource(R.string.account_delete_confirm),
      actions = listOf(
        FluidAlertAction(
          label = stringResource(R.string.action_cancel),
          onClick = { confirmDelete = false },
        ),
        FluidAlertAction(
          label = stringResource(R.string.account_delete),
          emphasis = FluidAlertAction.Emphasis.Destructive,
          onClick = {
            confirmDelete = false
            viewModel.deleteAccount()
          },
        ),
      ),
    )
  }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    FluidListGroup(glass = true) {
      when (val account = state.account) {
        is AccountState.SignedIn -> {
          FluidListRow(
            title = stringResource(R.string.account_connected),
            subtitle = account.uid,
          )
          FluidListDivider()
          FluidListRow(
            title = stringResource(R.string.account_delete),
            subtitle = if (state.busy) {
              stringResource(R.string.account_deleting)
            } else {
              stringResource(R.string.account_delete_detail)
            },
            tone = FluidTone.Danger,
            onClick = { confirmDelete = true },
          )
          FluidListDivider()
          FluidListRow(
            title = stringResource(R.string.account_sign_out),
            // Mentre ci prova lo dice: l'uscita passa da una scrittura sul server, e senza rete
            // puo' metterci qualche secondo. Un tasto premuto che non risponde sembra rotto.
            subtitle = if (state.busy) {
              stringResource(R.string.account_working)
            } else {
              stringResource(R.string.account_sign_out_detail)
            },
            onClick = viewModel::signOut,
          )
        }
        else -> {
          FluidListRow(
            title = stringResource(R.string.account_sign_in),
            subtitle = if (state.busy) {
              stringResource(R.string.account_working)
            } else {
              stringResource(R.string.account_sign_in_detail)
            },
            onClick = {
              if (state.busy) return@FluidListRow
              viewModel.startingSignIn()
              scope.launch { viewModel.signIn(requestGoogleId(context, serverClientId)) }
            },
          )
        }
      }
    }

    val message = when (state.message) {
      AccountMessage.Cancelled -> stringResource(R.string.account_cancelled)
      AccountMessage.NoAccount -> stringResource(R.string.account_no_account)
      AccountMessage.Failed -> stringResource(R.string.account_failed)
      AccountMessage.Published -> stringResource(R.string.account_published)
      AccountMessage.NotPublished -> stringResource(R.string.account_not_published)
      AccountMessage.Deleted -> stringResource(R.string.account_deleted)
      AccountMessage.DeleteFailed -> stringResource(R.string.account_delete_failed)
      null -> null
    }
    AnimatedVisibility(visible = message != null) {
      Text(
        text = message.orEmpty(),
        style = MaterialTheme.typography.bodySmall,
        color = if (state.message == AccountMessage.Published) {
          MaterialTheme.colorScheme.primary
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.padding(horizontal = 4.dp),
      )
    }

    FluidSectionFootnote(text = stringResource(R.string.account_footnote))
  }
}
