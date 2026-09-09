package dev.pampa.codex.ui.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPresentation
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.pampa.codex.ui.theme.CodexSheetColumn
import dev.pampa.codex.R
import dev.pampa.codex.data.identity.IdentityRepository
import dev.pampa.codex.data.prefs.LockType
import dev.pampa.codex.ui.lock.PinDots
import dev.pampa.codex.ui.lock.PinKeypad

/**
 * Il cambio di segreto, in un pannello di vetro ancorato al bordo.
 *
 * Tre passi e nessuna scorciatoia: il vecchio segreto serve davvero, perche' cambiare la chiave
 * significa riaprire il vault e richiuderlo, non riscrivere un'impostazione. Chi ha in mano un
 * telefono sbloccato ma non conosce il PIN non puo' cambiarlo.
 */
@Composable
fun ChangeSecretSheet(
  state: ChangeSecretState,
  currentLockType: LockType,
  onDismiss: () -> Unit,
  onTypeChange: (LockType) -> Unit,
  onInput: (String) -> Unit,
  onDigit: (Char) -> Unit,
  onDelete: () -> Unit,
  onAdvance: () -> Unit,
) {
  val phaseTitle = when (state.phase) {
    ChangeSecretPhase.CURRENT -> stringResource(R.string.security_change_current)
    ChangeSecretPhase.NEW -> stringResource(R.string.security_change_new)
    ChangeSecretPhase.CONFIRM -> stringResource(R.string.security_change_confirm)
  }
  val lockTypeLabels = mapOf(
    LockType.PIN to stringResource(R.string.lock_type_pin),
    LockType.PASSWORD to stringResource(R.string.lock_type_password),
  )
  // Il segreto vecchio si digita con la serratura di adesso; quello nuovo con quella che si sceglie.
  val typeInUse = if (state.phase == ChangeSecretPhase.CURRENT) currentLockType else state.lockType

  FluidGlassModalPortal(
    visible = state.open,
    onDismissRequest = onDismiss,
    presentation = FluidGlassModalPresentation.Sheet,
    paneTitle = stringResource(R.string.security_change_title),
  ) {
    CodexSheetColumn(
      verticalArrangement = Arrangement.spacedBy(18.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        // In orizzontale il tastierino non ci sta nell'altezza di un pannello: senza scorrimento
        // resterebbero visibili due file di tasti e il segreto non si potrebbe cambiare.
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp)
        .padding(bottom = 28.dp),
    ) {
      Text(
        text = phaseTitle,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
      )
      if (state.error) {
        Text(
          text = stringResource(R.string.security_change_error),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          textAlign = TextAlign.Center,
        )
      }

      if (state.phase == ChangeSecretPhase.NEW) {
        FluidSegmentedControl(
          options = listOf(LockType.PIN, LockType.PASSWORD),
          selected = state.lockType,
          onSelect = onTypeChange,
          label = { lockTypeLabels.getValue(it) },
        )
      }

      if (typeInUse == LockType.PIN) {
        PinDots(
          filled = state.entry.length,
          total = IdentityRepository.PIN_LENGTH,
          error = state.error,
        )
        PinKeypad(onDigit = onDigit, onDelete = onDelete, enabled = !state.busy)
      } else {
        FluidTextField(
          value = state.entry,
          onValueChange = onInput,
          placeholder = stringResource(R.string.lock_password_placeholder),
          enabled = !state.busy,
          modifier = Modifier.fillMaxWidth(),
        )
        FluidButton(
          text = stringResource(R.string.action_continue),
          onClick = onAdvance,
          enabled = !state.busy && state.entry.isNotEmpty(),
          loading = state.busy,
        )
      }
    }
  }
}
