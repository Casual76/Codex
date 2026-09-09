package dev.pampa.codex.ui.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidAlert
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPresentation
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.pampa.codex.ui.theme.CodexSheetColumn
import dev.pampa.codex.ui.theme.codexHorizontalPadding
import dev.pampa.codex.R
import dev.pampa.codex.data.stories.Story
import dev.pampa.codex.model.Technique
import dev.pampa.codex.seal.MineralGenerator
import dev.pampa.codex.ui.seal.MineralView
import dev.pampa.codex.ui.seal.SealBubble
import java.util.concurrent.TimeUnit

/**
 * Le storie: il sigillo del giorno.
 *
 * Una storia e' un messaggio senza destinatario che dura ventiquattr'ore. Come tutto il resto
 * dell'app arriva chiusa: l'anello dice **che forma ha** e quanto le resta, il contenuto si vede
 * solo entrandoci e aprendola.
 *
 * Finche' non ci sono contatti si vedono solo le proprie, e va detto invece che lasciare una
 * scheda vuota che sembra rotta.
 */
@Composable
fun StoriesScreen(viewModel: StoriesViewModel = hiltViewModel()) {
  val stories by viewModel.stories.collectAsStateWithLifecycle()
  val state by viewModel.state.collectAsStateWithLifecycle()

  FluidScreen(
    title = stringResource(R.string.stories_title),
    subtitle = stringResource(R.string.stories_subtitle),
    ambient = remember { FluidAmbient(tone = FluidHeroTone.Secondary, motif = FluidHeroMotif.Dots) },
    horizontalPadding = codexHorizontalPadding(),
    actions = {
      FluidBarAction(
        icon = Icons.Rounded.Add,
        contentDescription = stringResource(R.string.story_new),
        onClick = viewModel::startComposing,
      )
    },
  ) {
    if (stories?.isEmpty() == true) {
      item(key = "empty") {
        FluidEmptyState(
          title = stringResource(R.string.stories_empty_title),
          detail = stringResource(R.string.stories_empty_detail),
        )
      }
      item(key = "empty-action") {
        FluidButton(
          text = stringResource(R.string.story_new),
          onClick = viewModel::startComposing,
          fillWidth = true,
        )
      }
      return@FluidScreen
    }

    item(key = "ring") {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        val elenco = stories.orEmpty()
        items(elenco.size, key = { elenco[it].id }) { index ->
          StoryRing(story = elenco[index], onClick = { viewModel.open(elenco[index]) })
        }
      }
    }
    item(key = "note") {
      FluidListGroup(glass = true) {
        FluidListRow(
          title = stringResource(R.string.story_new),
          subtitle = stringResource(R.string.story_new_detail),
          onClick = viewModel::startComposing,
        )
        FluidListDivider()
        FluidListRow(
          title = stringResource(R.string.story_alone_title),
          subtitle = stringResource(R.string.story_alone_detail),
        )
      }
    }
  }

  StoryComposer(
    open = state.composing,
    draft = state.draft,
    technique = state.technique,
    onDraftChange = viewModel::setDraft,
    onTechnique = viewModel::setTechnique,
    onDismiss = viewModel::cancelComposing,
    onPost = viewModel::post,
  )

  var confirmDelete by remember { mutableStateOf(false) }
  val viewers by viewModel.viewers.collectAsStateWithLifecycle()
  StoryViewer(
    story = state.viewing,
    viewers = viewers,
    onClose = viewModel::close,
    onDelete = { confirmDelete = true },
  )

  // Come per un messaggio: una storia si cancella per sempre, e una domanda in mezzo costa un
  // tocco. Che duri comunque un giorno non e' una ragione per non chiedere.
  if (confirmDelete) {
    FluidAlert(
      onDismissRequest = { confirmDelete = false },
      title = stringResource(R.string.story_delete),
      message = stringResource(R.string.story_delete_confirm),
      actions = listOf(
        FluidAlertAction(
          label = stringResource(R.string.action_cancel),
          onClick = { confirmDelete = false },
        ),
        FluidAlertAction(
          label = stringResource(R.string.story_delete),
          emphasis = FluidAlertAction.Emphasis.Destructive,
          onClick = {
            confirmDelete = false
            viewModel.deleteViewed()
          },
        ),
      ),
    )
  }
}

/**
 * L'anello di una storia.
 *
 * Il minerale non e' un avatar: e' **il sigillo di quella storia**, disegnato dal suo seme. Due
 * storie della stessa persona hanno due minerali diversi, ed e' il modo in cui si vede che ce n'e'
 * una nuova senza che nessuno lo scriva.
 */
@Composable
private fun StoryRing(story: Story, onClick: () -> Unit) {
  val mineral = remember(story.spec.seed) { MineralGenerator.generate(story.spec.seed) }

  // L'anello di una storia non vista prende **il colore della pietra che contiene**, sfumato
  // nell'accento: due storie diverse hanno due anelli diversi, e si distinguono da lontano prima
  // ancora di guardare cosa c'e' dentro. Vista, l'anello si spegne in una riga sola.
  val accent = MaterialTheme.colorScheme.primary
  val stone = Color(mineral.palette.glow)
  val ring = if (story.seen) {
    SolidColor(MaterialTheme.colorScheme.outlineVariant)
  } else {
    Brush.sweepGradient(listOf(accent, stone, accent, stone, accent))
  }
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .size(width = 92.dp, height = 128.dp)
      .clip(ContinuousCornerShape(FluidRadius.Card))
      .fluidPressable(onClick = onClick),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Box(
      modifier = Modifier
        .size(78.dp)
        .border(width = 2.dp, brush = ring, shape = ContinuousCornerShape(FluidRadius.Card))
        .padding(7.dp),
      contentAlignment = Alignment.Center,
    ) {
      MineralView(mineral = mineral, modifier = Modifier.fillMaxSize())
    }
    Text(
      // Di chi e', se non e' la propria. Il tempo che resta vale per tutte, ma su una fila di
      // anelli la prima domanda e' "di chi e' questo".
      text = story.authorName.ifBlank { remainingLabel(story) },
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/**
 * Quanto le resta, all'ora piena **per eccesso**.
 *
 * Al minuto sarebbe un conto alla rovescia, e una storia non lo e'. Per difetto invece una storia
 * appena pubblicata direbbe "ancora 23 ore", che e' vero per un pelo e sbagliato per chi legge.
 */
@Composable
private fun remainingLabel(story: Story): String {
  val remaining = story.remaining(System.currentTimeMillis())
  val hour = TimeUnit.HOURS.toMillis(1)
  val hours = ((remaining + hour - 1) / hour).toInt()
  return if (hours >= 1) {
    pluralStringResource(R.plurals.story_hours_left, hours, hours)
  } else {
    stringResource(R.string.story_minutes_left)
  }
}

/** Il pannello per pubblicare: testo, tecnica, e via. */
@Composable
private fun StoryComposer(
  open: Boolean,
  draft: String,
  technique: Technique?,
  onDraftChange: (String) -> Unit,
  onTechnique: (Technique?) -> Unit,
  onDismiss: () -> Unit,
  onPost: () -> Unit,
) {
  // Le stesse quattro descrizioni della chat: una tecnica non cambia significato perche' cambia la
  // schermata, e ripetere quattro volte la stessa riga generica non dice niente a nessuno.
  val options: List<Triple<Technique?, String, String>> = listOf(
    Triple(
      null,
      stringResource(R.string.technique_surprise),
      stringResource(R.string.technique_surprise_detail),
    ),
    Triple(
      Technique.RUNE,
      stringResource(R.string.technique_rune),
      stringResource(R.string.technique_rune_detail),
    ),
    Triple(
      Technique.ROCK,
      stringResource(R.string.technique_rock),
      stringResource(R.string.technique_rock_detail),
    ),
    Triple(
      Technique.PAINTING,
      stringResource(R.string.technique_painting),
      stringResource(R.string.technique_painting_detail),
    ),
  )

  FluidGlassModalPortal(
    visible = open,
    onDismissRequest = onDismiss,
    presentation = FluidGlassModalPresentation.Sheet,
    paneTitle = stringResource(R.string.story_new),
  ) {
    CodexSheetColumn(
      verticalArrangement = Arrangement.spacedBy(14.dp),
      modifier = Modifier
        .padding(horizontal = 20.dp)
        .padding(bottom = 28.dp),
    ) {
      Text(
        text = stringResource(R.string.story_new),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
      FluidTextField(
        value = draft,
        onValueChange = onDraftChange,
        label = stringResource(R.string.story_placeholder),
        singleLine = false,
        minLines = 2,
        maxLines = 4,
        modifier = Modifier.fillMaxWidth(),
      )
      FluidListGroup {
        options.forEachIndexed { index, (option, label, detail) ->
          if (index > 0) FluidListDivider()
          FluidListRow(
            title = label,
            subtitle = detail,
            meta = if (option == technique) stringResource(R.string.technique_current) else null,
            onClick = { onTechnique(option) },
          )
        }
      }
      FluidButton(
        text = stringResource(R.string.story_post),
        onClick = onPost,
        enabled = draft.isNotBlank(),
        fillWidth = true,
      )
      FluidButton(
        text = stringResource(R.string.action_cancel),
        onClick = onDismiss,
        style = FluidButtonStyle.Tinted,
        fillWidth = true,
      )
    }
  }
}

/**
 * La storia a tutto schermo.
 *
 * Su fondo pieno e senza barre: una storia si guarda, non si consulta. Il sigillo si apre con lo
 * stesso tocco di sempre, ed e' il motivo per cui non serve spiegare come si fa.
 */
@Composable
private fun StoryViewer(
  story: Story?,
  viewers: Int,
  onClose: () -> Unit,
  onDelete: () -> Unit,
) {
  FluidGlassModalPortal(
    visible = story != null,
    onDismissRequest = onClose,
    presentation = FluidGlassModalPresentation.Sheet,
    paneTitle = stringResource(R.string.stories_title),
  ) {
    val current = story ?: return@FluidGlassModalPortal
    CodexSheetColumn(
      verticalArrangement = Arrangement.spacedBy(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .padding(horizontal = 20.dp)
        .padding(bottom = 28.dp),
    ) {
      Text(
        text = if (current.mine && viewers > 0) {
          pluralStringResource(R.plurals.story_seen_by, viewers, viewers)
        } else if (current.authorName.isNotBlank()) {
          current.authorName
        } else {
          remainingLabel(current)
        },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(ContinuousCornerShape(FluidRadius.Card))
          .background(MaterialTheme.colorScheme.surfaceContainerLow)
          .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
      ) {
        StorySeal(story = current)
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        FluidButton(
          text = stringResource(R.string.action_close),
          onClick = onClose,
          style = FluidButtonStyle.Tinted,
          modifier = Modifier.weight(1f),
        )
        if (current.mine) {
          FluidButton(
            text = stringResource(R.string.story_delete),
            onClick = onDelete,
            style = FluidButtonStyle.Tinted,
            modifier = Modifier.weight(1f),
          )
        }
      }
      Text(
        text = stringResource(R.string.story_tap_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
  }
}

/**
 * Il sigillo di una storia, con il suo tocco.
 *
 * Lo stato dell'apertura vive qui e muore con il pannello: chiudere una storia e riaprirla vuol
 * dire rifare il rituale, esattamente come uscire da una chat richiude i messaggi.
 */
@Composable
private fun StorySeal(story: Story) {
  var revealed by remember(story.id) { mutableStateOf(false) }
  SealBubble(
    spec = story.spec,
    text = story.text,
    revealed = revealed,
    onReveal = { revealed = true },
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor = MaterialTheme.colorScheme.onSurface,
  )
}
