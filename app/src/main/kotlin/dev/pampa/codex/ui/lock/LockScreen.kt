package dev.pampa.codex.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antigravity.fluidengine.ui.fluid.FluidAlert
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidAmbientCanvas
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.pampa.codex.R
import dev.pampa.codex.data.identity.IdentityRepository
import dev.pampa.codex.data.prefs.LockType
import dev.pampa.codex.seal.MineralGenerator
import dev.pampa.codex.ui.seal.MineralView
import kotlinx.coroutines.delay

/**
 * La serratura.
 *
 * Il minerale e il nome sono li' perche' vengono dal profilo, che sta fuori dal vault: una
 * schermata di sblocco che non sa nemmeno chi sta per entrare sembra la schermata di un'altra app.
 *
 * Se la biometria e' attiva il prompt di sistema parte da solo al primo ingresso, e non si
 * ripresenta se l'utente lo annulla: insistere sarebbe l'unico modo per renderlo fastidioso.
 */
@Composable
fun LockScreen(viewModel: LockViewModel = hiltViewModel()) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val entry by viewModel.entry.collectAsStateWithLifecycle()
  val authenticator = LocalBiometricAuthenticator.current
  var biometricAttempted by remember { mutableStateOf(false) }
  var showReset by remember { mutableStateOf(false) }
  var remainingWait by remember { mutableStateOf(0L) }

  val promptTitle = stringResource(R.string.lock_biometric_title)
  val promptSubtitle = stringResource(R.string.lock_biometric_subtitle)
  val promptNegative = stringResource(R.string.action_use_secret)

  fun runBiometricPrompt() {
    val gate = authenticator ?: return
    if (!gate.isAvailable()) return
    val cipher = viewModel.biometricCipher() ?: return
    gate.authenticate(
      cipher = cipher,
      title = promptTitle,
      subtitle = promptSubtitle,
      negativeButton = promptNegative,
      onSuccess = viewModel::unlockWithBiometrics,
      onError = { },
    )
  }

  LaunchedEffect(state.settings.biometricEnabled) {
    if (state.settings.biometricEnabled && !biometricAttempted) {
      biometricAttempted = true
      runBiometricPrompt()
    }
  }

  // Il conto alla rovescia dell'attesa: si aggiorna al secondo, e solo mentre serve.
  LaunchedEffect(entry.waitingUntil) {
    while (entry.waitingUntil > System.currentTimeMillis()) {
      remainingWait = entry.waitingUntil - System.currentTimeMillis()
      delay(1000)
    }
    remainingWait = 0
  }

  val waiting = remainingWait > 0

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    FluidAmbientCanvas(
      ambient = remember { FluidAmbient(tone = FluidHeroTone.Primary, motif = FluidHeroMotif.Glow) },
    )

    // In orizzontale il tastierino non ci sta sotto al ritratto: si affiancano.
    //
    // Non e' una rifinitura. Impilati, in orizzontale restava visibile la prima riga di tasti e
    // basta, senza modo di scorrere: l'app **non si poteva sbloccare**. Il ritratto (minerale,
    // nome, messaggio) sta a sinistra, il modo di entrare a destra, e sopra tutto c'e' comunque
    // uno scorrimento per gli schermi corti davvero.
    val sideBySide = maxWidth >= SideBySideWidth && maxHeight < maxWidth
    // Catturata qui: dentro le due lambde il ricevitore e' la colonna, non il riquadro.
    val available = maxHeight

    val portrait: @Composable ColumnScope.() -> Unit = {
      MineralView(
        mineral = remember(state.profile.avatarSeed) {
          MineralGenerator.avatar(state.profile.avatarSeed)
        },
        modifier = Modifier.size(if (sideBySide) 84.dp else 96.dp),
      )
      Spacer(Modifier.height(18.dp))
      Text(
        text = state.profile.name.ifBlank { stringResource(R.string.app_name) },
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(6.dp))
      Text(
        text = when {
          waiting -> stringResource(R.string.lock_wait, formatWait(remainingWait))
          entry.message != null -> entry.message.orEmpty()
          entry.error -> stringResource(R.string.lock_wrong)
          state.lockType == LockType.PIN -> stringResource(R.string.lock_enter_pin)
          else -> stringResource(R.string.lock_enter_password)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = if (entry.error || waiting) {
          MaterialTheme.colorScheme.error
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant
        },
        textAlign = TextAlign.Center,
      )
    }

    val unlock: @Composable ColumnScope.() -> Unit = {
      when (state.lockType) {
        LockType.PIN -> {
          PinDots(
            filled = entry.entry.length,
            total = IdentityRepository.PIN_LENGTH,
            error = entry.error,
          )
          Spacer(Modifier.height(28.dp))
          PinKeypad(
            onDigit = viewModel::appendDigit,
            onDelete = viewModel::deleteDigit,
            enabled = !waiting && !entry.busy,
            // In orizzontale il tastierino si stringe per starci: e' lui a sapere di quanto.
            availableHeight = if (sideBySide) available - KeypadReservedHeight else Dp.Unspecified,
            leadingKey = if (state.settings.biometricEnabled) {
              {
                Box(
                  modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .fluidPressable(onClick = ::runBiometricPrompt),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Fingerprint,
                    contentDescription = stringResource(R.string.lock_biometric_title),
                    tint = MaterialTheme.colorScheme.primary,
                  )
                }
              }
            } else {
              null
            },
          )
        }

        LockType.PASSWORD -> {
          FluidTextField(
            value = entry.entry,
            onValueChange = viewModel::setPassword,
            placeholder = stringResource(R.string.lock_password_placeholder),
            enabled = !waiting && !entry.busy,
            isError = entry.error,
            modifier = Modifier
              .fillMaxWidth()
              .widthIn(max = 360.dp),
          )
          Spacer(Modifier.height(16.dp))
          FluidButton(
            text = stringResource(R.string.action_unlock),
            onClick = viewModel::submitPassword,
            enabled = !waiting && !entry.busy && entry.entry.isNotEmpty(),
            loading = entry.busy,
          )
          if (state.settings.biometricEnabled) {
            Spacer(Modifier.height(8.dp))
            FluidButton(
              text = stringResource(R.string.lock_biometric_title),
              onClick = ::runBiometricPrompt,
              style = FluidButtonStyle.Plain,
            )
          }
        }
      }

      Spacer(Modifier.height(16.dp))
      FluidButton(
        text = stringResource(R.string.lock_forgot),
        onClick = { showReset = true },
        style = FluidButtonStyle.Plain,
      )
    }

    val scroll = rememberScrollState()
    if (sideBySide) {
      Row(
        modifier = Modifier
          .fillMaxSize()
          .safeDrawingPadding()
          .imePadding()
          .verticalScroll(scroll)
          .padding(horizontal = 28.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(
          modifier = Modifier.weight(1f),
          horizontalAlignment = Alignment.CenterHorizontally,
          content = portrait,
        )
        Column(
          modifier = Modifier.weight(1f),
          horizontalAlignment = Alignment.CenterHorizontally,
          content = unlock,
        )
      }
    } else {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .safeDrawingPadding()
          .imePadding()
          .verticalScroll(scroll)
          .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        portrait()
        Spacer(Modifier.height(28.dp))
        unlock()
      }
    }
  }

  if (showReset) {
    FluidAlert(
      onDismissRequest = { showReset = false },
      title = stringResource(R.string.lock_reset_title),
      message = stringResource(R.string.lock_reset_message),
      actions = listOf(
        FluidAlertAction(
          label = stringResource(R.string.action_cancel),
          onClick = { showReset = false },
        ),
        FluidAlertAction(
          label = stringResource(R.string.action_reset),
          emphasis = FluidAlertAction.Emphasis.Destructive,
          onClick = {
            showReset = false
            viewModel.resetEverything()
          },
        ),
      ),
    )
  }
}

/** Sopra questa larghezza, e solo in orizzontale, ritratto e tastierino si affiancano. */
private val SideBySideWidth: Dp = 600.dp

/** Quello che sta sopra e sotto il tastierino: pallini, spazi, e il tasto del segreto dimenticato. */
private val KeypadReservedHeight: Dp = 128.dp

private fun formatWait(millis: Long): String {
  val seconds = (millis / 1000).coerceAtLeast(1)
  return if (seconds < 60) {
    "${seconds}s"
  } else {
    val minutes = seconds / 60
    "${minutes}m"
  }
}
