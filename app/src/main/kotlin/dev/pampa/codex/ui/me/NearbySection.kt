package dev.pampa.codex.ui.me

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antigravity.fluidengine.ui.fluid.FluidAlert
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.pampa.codex.R
import dev.pampa.codex.data.nearby.NearbyMode

/**
 * Le vicinanze, come scelta di chi usa l'app.
 *
 * Tre possibilita' e non un interruttore, perche' "acceso" vuol dire due cose molto diverse: mentre
 * guardo lo schermo, oppure anche di notte in tasca. La seconda costa batteria e mette una notifica
 * fissa; sceglierla dev'essere un gesto, non un effetto collaterale.
 *
 * I permessi si chiedono **qui**, quando si accende, con la spiegazione accanto. Chiederli all'avvio
 * -- prima che si sappia a cosa servono -- e' il modo piu' sicuro di farseli negare, e quello sulla
 * posizione, che Android pretende sui telefoni piu' vecchi, senza spiegazione sembra una bugia.
 */
@Composable
fun NearbySection(viewModel: NearbyViewModel = hiltViewModel()) {
  val mode by viewModel.mode.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var explain by remember { mutableStateOf<NearbyMode?>(null) }
  var refused by remember { mutableStateOf(false) }

  val ask = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions(),
  ) { esiti ->
    val scelta = explain
    explain = null
    if (esiti.values.all { it } && scelta != null) viewModel.setMode(scelta) else refused = true
  }

  fun choose(scelta: NearbyMode) {
    if (scelta == NearbyMode.OFF || hasPermissions(context)) {
      viewModel.setMode(scelta)
      return
    }
    explain = scelta
  }

  FluidListGroup(glass = true) {
    NearbyMode.entries.forEachIndexed { index, scelta ->
      if (index > 0) FluidListDivider()
      FluidListRow(
        title = stringResource(
          when (scelta) {
            NearbyMode.OFF -> R.string.nearby_off
            NearbyMode.FOREGROUND -> R.string.nearby_foreground
            NearbyMode.ALWAYS -> R.string.nearby_always
          },
        ),
        subtitle = stringResource(
          when (scelta) {
            NearbyMode.OFF -> R.string.nearby_off_detail
            NearbyMode.FOREGROUND -> R.string.nearby_foreground_detail
            NearbyMode.ALWAYS -> R.string.nearby_always_detail
          },
        ),
        meta = if (mode == scelta) stringResource(R.string.nearby_current) else null,
        onClick = { choose(scelta) },
      )
    }
  }

  // La spiegazione **prima** della finestra di sistema: quella di Android dice solo il nome del
  // permesso, e "posizione" accanto a un'app di messaggi, senza una frase intorno, e' un no.
  val richiesta = explain
  if (richiesta != null) {
    FluidAlert(
      onDismissRequest = { explain = null },
      title = stringResource(R.string.nearby_permission_title),
      message = stringResource(R.string.nearby_permission_detail),
      actions = listOf(
        FluidAlertAction(
          label = stringResource(R.string.action_cancel),
          onClick = { explain = null },
        ),
        FluidAlertAction(
          label = stringResource(R.string.action_continue),
          onClick = { ask.launch(neededPermissions()) },
        ),
      ),
    )
  }

  if (refused) {
    FluidAlert(
      onDismissRequest = { refused = false },
      title = stringResource(R.string.nearby_permission_refused),
      actions = listOf(
        FluidAlertAction(
          label = stringResource(R.string.action_ok),
          onClick = { refused = false },
        ),
      ),
    )
  }
}

/**
 * I permessi che servono, che cambiano con la versione di Android.
 *
 * Fino al 30 cercare dispositivi Bluetooth **era** un permesso di posizione: non e' una stranezza di
 * Codex, e' come Android ha fatto le cose per anni. Dal 31 esistono i permessi giusti e quello sulla
 * posizione non si chiede piu'.
 */
private fun neededPermissions(): Array<String> = when {
  Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.BLUETOOTH_ADVERTISE,
    Manifest.permission.NEARBY_WIFI_DEVICES,
  )
  Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> arrayOf(
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.BLUETOOTH_ADVERTISE,
  )
  else -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
}

private fun hasPermissions(context: android.content.Context): Boolean =
  neededPermissions().all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
  }
