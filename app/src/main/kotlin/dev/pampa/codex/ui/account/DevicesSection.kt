package dev.pampa.codex.ui.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antigravity.fluidengine.ui.fluid.FluidAlert
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.pampa.codex.R
import dev.pampa.codex.data.cloud.CodexDevice
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * I telefoni che ricevono le notifiche di questo account.
 *
 * Serve a una domanda che nessuna app fa spontaneamente e che tutti dovrebbero potersi fare: **chi
 * altro riceve le mie notifiche?** Un telefono venduto, uno prestato, uno perso: finche' il suo
 * gettone e' registrato, il server continua a bussargli. Non gli fa leggere niente -- la notifica e'
 * vuota e la busta e' cifrata -- ma gli dice che e' arrivato qualcosa, e anche quello e' qualcosa.
 *
 * Togliere un dispositivo qui non lo disconnette: quel telefono si riregistrera' alla prossima
 * apertura, se e' ancora tuo e ha ancora la tua identita' aperta. E' scritto sotto, perche' una
 * revoca che sembra definitiva e non lo e' e' peggio di nessuna revoca.
 */
@Composable
fun DevicesSection(viewModel: DevicesViewModel = hiltViewModel()) {
  val devices by viewModel.devices.collectAsStateWithLifecycle()
  val current by viewModel.currentId.collectAsStateWithLifecycle()
  var pending by remember { mutableStateOf<CodexDevice?>(null) }

  if (devices.isEmpty()) return

  FluidListGroup(glass = true) {
    devices.forEachIndexed { index, device ->
      if (index > 0) FluidListDivider()
      val isCurrent = device.id == current
      FluidListRow(
        title = if (isCurrent) {
          stringResource(R.string.devices_this_one)
        } else {
          device.platform.ifBlank { stringResource(R.string.devices_unknown) }
        },
        subtitle = lastSeenLabel(device),
        meta = device.appVersion.takeIf { it.isNotBlank() },
        // Questo telefono non si revoca da qui: si usa "Disconnetti", che e' il gesto giusto e dice
        // cosa succede. Un tasto che ti toglie le tue notifiche senza spiegarlo e' una trappola.
        onClick = if (isCurrent) null else ({ pending = device }),
      )
    }
  }

  val toRevoke = pending
  if (toRevoke != null) {
    FluidAlert(
      onDismissRequest = { pending = null },
      title = stringResource(R.string.devices_revoke),
      message = stringResource(R.string.devices_revoke_detail),
      actions = listOf(
        FluidAlertAction(
          label = stringResource(R.string.action_cancel),
          onClick = { pending = null },
        ),
        FluidAlertAction(
          label = stringResource(R.string.devices_revoke),
          emphasis = FluidAlertAction.Emphasis.Destructive,
          onClick = {
            viewModel.revoke(toRevoke.id)
            pending = null
          },
        ),
      ),
    )
  }
}

/** L'ultima volta che si e' fatto vivo. Una data, non un conto alla rovescia. */
@Composable
private fun lastSeenLabel(device: CodexDevice): String {
  if (device.lastSeen <= 0) return stringResource(R.string.devices_unknown_time)
  val formatter = remember {
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
  }
  val date = Instant.ofEpochMilli(device.lastSeen).atZone(ZoneId.systemDefault()).toLocalDate()
  return stringResource(R.string.devices_last_seen, formatter.format(date))
}
