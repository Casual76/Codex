package dev.pampa.codex.ui.contacts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.theme.FluidCard
import dev.pampa.codex.R
import dev.pampa.codex.ui.theme.codexHorizontalPadding

/** I due modi di ricevere una scheda quando non c'e' un server in mezzo. */
private enum class AddMode { SCAN, PASTE }

/** Quanto puo' crescere il mirino: piu' largo di cosi', su un tablet, e' una finestra. */
private val MaxViewfinder = 360.dp

/**
 * Aggiungere una persona.
 *
 * Due strade, e sono davvero due: **inquadrare** quando si e' nella stessa stanza, **incollare**
 * quando non lo si e'. La seconda non e' un ripiego per quando la fotocamera non va: una scheda
 * Codex e' un testo che non contiene segreti, quindi puo' passare tranquillamente da un messaggio,
 * da una mail o da un foglietto, e chi vive lontano da chi vuole aggiungere usa quella.
 *
 * Quando la scheda entra, la schermata non torna indietro dicendo "fatto": porta al rito. Avere un
 * contatto e poter parlare con lui sono due cose diverse, e la seconda e' quella che l'utente
 * voleva.
 */
@Composable
fun AddContactScreen(
  onRitual: (String) -> Unit,
  onBack: () -> Unit,
  viewModel: ContactsViewModel = hiltViewModel(),
) {
  var mode by remember { mutableStateOf(AddMode.SCAN) }
  var typed by remember { mutableStateOf("") }
  val outcome by viewModel.outcome.collectAsStateWithLifecycle()
  val clipboard = LocalClipboardManager.current

  // Una scheda che entra porta al rito: sia se e' nuova, sia se era gia' nota (in quel caso di
  // solito si sta rifacendo il giro proprio perche' qualcosa non aveva funzionato).
  LaunchedEffect(outcome) {
    val codexId = when (val current = outcome) {
      is AddOutcome.Added -> current.contact.codexId
      is AddOutcome.AlreadyKnown -> current.contact.codexId
      else -> null
    } ?: return@LaunchedEffect
    viewModel.clearOutcome()
    onRitual(codexId)
  }

  val modeLabels = mapOf(
    AddMode.SCAN to stringResource(R.string.add_mode_scan),
    AddMode.PASTE to stringResource(R.string.add_mode_paste),
  )

  FluidScreen(
    title = stringResource(R.string.add_title),
    subtitle = stringResource(R.string.add_subtitle),
    horizontalPadding = codexHorizontalPadding(),
    onBack = onBack,
  ) {
    item(key = "mode") {
      FluidSegmentedControl(
        options = listOf(AddMode.SCAN, AddMode.PASTE),
        selected = mode,
        onSelect = { mode = it },
        label = { modeLabels.getValue(it) },
      )
    }

    item(key = "body") {
      when (mode) {
        AddMode.SCAN -> Viewfinder(onCode = viewModel::add)
        AddMode.PASTE -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          FluidTextField(
            value = typed,
            onValueChange = { typed = it },
            label = stringResource(R.string.add_paste_label),
            placeholder = stringResource(R.string.add_paste_placeholder),
            supportingText = stringResource(R.string.add_paste_support),
            singleLine = false,
            minLines = 3,
            maxLines = 6,
          )
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FluidButton(
              text = stringResource(R.string.add_paste_from_clipboard),
              style = FluidButtonStyle.Plain,
              fillWidth = true,
              onClick = { clipboard.getText()?.let { typed = it.text } },
            )
            FluidButton(
              text = stringResource(R.string.add_paste_confirm),
              enabled = typed.isNotBlank(),
              fillWidth = true,
              onClick = { viewModel.add(typed) },
            )
          }
        }
      }
    }

    item(key = "outcome") {
      // Il messaggio compare solo quando c'e' qualcosa da dire, e i due casi buoni non lo
      // raggiungono mai: quelli portano via da questa schermata.
      val message = when (outcome) {
        AddOutcome.Myself -> stringResource(R.string.add_error_myself)
        AddOutcome.NotACard -> stringResource(R.string.add_error_not_a_card)
        AddOutcome.NotFound -> stringResource(R.string.add_error_not_found)
        AddOutcome.Unreachable -> stringResource(R.string.add_error_unreachable)
        AddOutcome.NeedsAccount -> stringResource(R.string.add_error_needs_account)
        else -> null
      }
      AnimatedVisibility(visible = message != null) {
        Text(
          text = message.orEmpty(),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.padding(horizontal = 4.dp),
        )
      }
    }

    item(key = "footnote") {
      FluidSectionFootnote(text = stringResource(R.string.add_footnote))
    }
  }
}

/**
 * Il mirino, o la ragione per cui non c'e'.
 *
 * Il permesso non si chiede all'apertura della schermata ma dietro un bottone, con scritto sopra a
 * cosa serve: una richiesta di sistema che compare da sola, senza contesto, e' quella che la gente
 * rifiuta per riflesso e poi non sa piu' come riattivare.
 */
@Composable
private fun Viewfinder(onCode: (String) -> Unit) {
  val (granted, request) = rememberCameraPermission()
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    if (granted) {
      QrScannerPreview(
        onCode = onCode,
        modifier = Modifier
          .widthIn(max = MaxViewfinder)
          .fillMaxWidth()
          .aspectRatio(1f)
          .clip(ContinuousCornerShape(FluidRadius.Card)),
      )
    } else {
      FluidCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            text = stringResource(R.string.add_camera_why),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          FluidButton(
            text = stringResource(R.string.add_camera_allow),
            fillWidth = true,
            onClick = request,
          )
        }
      }
    }
  }
}
