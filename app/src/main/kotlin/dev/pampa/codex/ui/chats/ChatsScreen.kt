package dev.pampa.codex.ui.chats

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTone
import dev.pampa.codex.ui.theme.codexHorizontalPadding
import dev.pampa.codex.R
import dev.pampa.codex.data.db.ChatKind
import dev.pampa.codex.model.Technique
import dev.pampa.codex.seal.MineralGenerator
import dev.pampa.codex.ui.seal.MineralView
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * La lista delle chat.
 *
 * L'anteprima non dice mai cosa c'e' scritto: dice **che forma ha** il messaggio che aspetta.
 * "Una roccia da aprire" e' l'unica cosa che serve sapere prima di entrare, e nessuno che sbirci lo
 * schermo da sopra la spalla legge qualcosa.
 */
@Composable
fun ChatsScreen(
  onOpenChat: (String) -> Unit,
  onOpenContacts: () -> Unit,
  onNewGroup: () -> Unit = {},
  onJoinGroup: () -> Unit = {},
  /**
   * La conversazione aperta nell'altro pannello, sui due pannelli del tablet.
   *
   * Su un telefono e' sempre `null` e non cambia niente: la lista esce di scena quando si entra in
   * una chat. Affiancate, invece, la riga toccata deve restare riconoscibile, altrimenti si legge
   * una conversazione senza vedere piu' quale delle dieci sia.
   */
  selectedChatId: String? = null,
  viewModel: ChatsViewModel = hiltViewModel(),
) {
  val chats by viewModel.chats.collectAsStateWithLifecycle()
  val selfTitle = stringResource(R.string.chat_self_title)

  val openNowLabel = stringResource(R.string.chats_open_now)

  LaunchedEffect(Unit) { viewModel.ensureSelfChat(selfTitle) }

  // Il quadro salvato da un'altra app. `OpenDocument` e non `GetContent`: serve un permesso di
  // lettura che regga finche' il file non e' stato letto davvero.
  val pickPainting = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri -> uri?.let(viewModel::openPainting) }

  val previews = mapOf(
    Technique.RUNE to stringResource(R.string.preview_rune),
    Technique.ROCK to stringResource(R.string.preview_rock),
    Technique.PAINTING to stringResource(R.string.preview_painting),
  )
  val emptyPreview = stringResource(R.string.preview_empty)

  FluidScreen(
    title = stringResource(R.string.chats_title),
    subtitle = stringResource(R.string.chats_subtitle),
    ambient = remember { FluidAmbient(tone = FluidHeroTone.Primary, motif = FluidHeroMotif.Figures) },
    horizontalPadding = codexHorizontalPadding(),
    actions = {
      FluidBarAction(
        icon = Icons.Rounded.ImageSearch,
        contentDescription = stringResource(R.string.painting_open),
        onClick = { pickPainting.launch(arrayOf("image/png")) },
      )
      FluidBarAction(
        icon = Icons.Rounded.PersonAdd,
        contentDescription = stringResource(R.string.contacts_title),
        onClick = onOpenContacts,
      )
    },
  ) {
    if (chats?.isEmpty() == true) {
      item(key = "empty") {
        FluidEmptyState(
          title = stringResource(R.string.chats_empty_title),
          detail = stringResource(R.string.chats_empty_detail),
        )
      }
      return@FluidScreen
    }

    item(key = "chats") {
      FluidListGroup(glass = true) {
        chats.orEmpty().forEachIndexed { index, chat ->
          if (index > 0) FluidListDivider()
          val technique = chat.lastSealTechnique?.let { name ->
            runCatching { Technique.valueOf(name) }.getOrNull()
          }
          FluidListRow(
            eyebrow = if (chat.id == selectedChatId) openNowLabel else null,
            tone = if (chat.id == selectedChatId) FluidTone.Primary else FluidTone.Neutral,
            title = chat.title,
            // Una conversazione bloccata non dice nemmeno **che forma** ha l'ultimo sigillo: sapere
            // che e' arrivata una roccia e' poco, ma la serratura serve proprio ai trenta secondi in
            // cui lo schermo e' in mano a un altro, e "poco" li' e' comunque qualcosa.
            subtitle = when {
              chat.locked -> stringResource(R.string.chat_locked_preview)
              technique != null -> previews.getValue(technique)
              else -> emptyPreview
            },
            // L'ora dell'ultima cosa successa. Una lista di conversazioni senza non si legge: non
            // si capisce quale sia viva e quale ferma da una settimana.
            meta = remember(chat.lastActivityAt) { lastActivityLabel(chat.lastActivityAt) },
            leading = {
              MineralView(
                mineral = remember(chat.emblemSeed, chat.kind) {
                  if (chat.kind == ChatKind.SELF) {
                    MineralGenerator.avatar(chat.emblemSeed)
                  } else {
                    MineralGenerator.generate(chat.emblemSeed)
                  }
                },
                modifier = Modifier.size(30.dp),
                showGlow = false,
              )
            },
            onClick = { onOpenChat(chat.id) },
          )
        }
      }
    }

    // La strada per uscire dalla lista.
    //
    // Sta in fondo **e** in cima nella barra: in cima perche' e' un'azione, in fondo perche' e' il
    // punto in cui uno arriva scorrendo quando si accorge di non avere nessuno con cui parlare.
    item(key = "add") {
      FluidListGroup(glass = true) {
        FluidListRow(
          title = stringResource(R.string.action_add_contact),
          subtitle = stringResource(R.string.contacts_add_detail),
          onClick = onOpenContacts,
        )
        FluidListDivider()
        FluidListRow(
          title = stringResource(R.string.group_new),
          subtitle = stringResource(R.string.group_new_detail),
          onClick = onNewGroup,
        )
        FluidListDivider()
        FluidListRow(
          title = stringResource(R.string.group_join),
          subtitle = stringResource(R.string.group_join_detail),
          onClick = onJoinGroup,
        )
      }
    }
  }
}

/**
 * Quando e' successa l'ultima cosa in una conversazione.
 *
 * Di oggi si scrive l'ora, di prima la data: e' quello che fa qualsiasi lista di messaggi, ed e'
 * quello che la gente si aspetta di leggere li'. Formato e lingua li decide il telefono.
 */
private fun lastActivityLabel(at: Long): String {
  if (at <= 0L) return ""
  val zone = ZoneId.systemDefault()
  val moment = Instant.ofEpochMilli(at).atZone(zone)
  val locale = Locale.getDefault()
  val style = if (moment.toLocalDate() == LocalDate.now(zone)) {
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
  } else {
    DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
  }
  return moment.format(style.withLocale(locale))
}
