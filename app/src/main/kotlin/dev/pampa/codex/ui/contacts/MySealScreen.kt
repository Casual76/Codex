package dev.pampa.codex.ui.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.rememberFluidHaptics
import dev.pampa.codex.R
import dev.pampa.codex.seal.MineralGenerator
import dev.pampa.codex.ui.seal.MineralView
import dev.pampa.codex.ui.theme.codexHorizontalPadding

/** Quanto puo' crescere il codice: oltre, su un tablet, diventa un manifesto. */
private val MaxPlateWidth = 320.dp

/**
 * Il proprio sigillo: la scheda pubblica, da far inquadrare.
 *
 * La schermata dice **una cosa sola**, e la dice grande, perche' esiste per essere puntata verso
 * un'altra persona: il codice al centro, il Codex ID sotto perche' si possa anche dettare, e il
 * bottone per copiarlo quando l'altra persona non e' nella stessa stanza.
 *
 * Quello che non fa e' altrettanto voluto: non chiede permessi, non accende niente, non manda
 * niente da nessuna parte. Mostrare la propria scheda e' un gesto che deve funzionare anche in
 * mezzo a un bosco, e infatti funziona.
 */
@Composable
fun MySealScreen(onBack: () -> Unit, viewModel: ContactsViewModel = hiltViewModel()) {
  val profile by viewModel.profile.collectAsStateWithLifecycle()
  val code by viewModel.myCode.collectAsStateWithLifecycle()
  val clipboard = LocalClipboardManager.current
  val haptics = rememberFluidHaptics()
  val plateDescription = stringResource(R.string.my_seal_qr_description)

  FluidScreen(
    title = stringResource(R.string.my_seal_title),
    subtitle = stringResource(R.string.my_seal_subtitle),
    horizontalPadding = codexHorizontalPadding(),
    onBack = onBack,
  ) {
    item(key = "plate") {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        val payload = code
        if (payload == null) {
          // Un rettangolo vuoto delle stesse misure: il codice arriva in un battito, e senza
          // segnaposto la pagina salterebbe sotto le dita di chi ha appena toccato la riga.
          MineralView(
            mineral = remember(profile.avatarSeed) { MineralGenerator.avatar(profile.avatarSeed) },
            modifier = Modifier.size(96.dp),
          )
        } else {
          QrPlate(
            payload = payload,
            contentDescription = plateDescription,
            modifier = Modifier
              .widthIn(max = MaxPlateWidth)
              .fillMaxWidth(),
            center = {
              MineralView(
                mineral = remember(profile.avatarSeed) { MineralGenerator.avatar(profile.avatarSeed) },
                modifier = Modifier.size(52.dp),
                showGlow = false,
              )
            },
          )
        }

        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = profile.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          // Il Codex ID e' piu' grande del nome perche' e' la parte che si detta al telefono, e chi
          // la detta la sta leggendo da lontano, con il braccio teso verso l'altra persona.
          Text(
            text = profile.codexId,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
          )
        }

        FluidButton(
          text = stringResource(R.string.my_seal_copy),
          style = FluidButtonStyle.Tinted,
          enabled = code != null,
          leading = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
          onClick = {
            code?.let {
              clipboard.setText(AnnotatedString(it))
              haptics.play(FluidHapticEvent.Confirm)
            }
          },
        )
      }
    }

    item(key = "footnote") {
      FluidSectionFootnote(text = stringResource(R.string.my_seal_footnote))
    }
  }
}
