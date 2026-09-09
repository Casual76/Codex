package dev.pampa.codex.ui.origins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPresentation
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.theme.FluidCard
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.pampa.codex.ui.theme.CodexSheetColumn
import dev.pampa.codex.ui.theme.codexHorizontalPadding
import dev.pampa.codex.R
import dev.pampa.codex.seal.Nomenclator

/**
 * "Le origini": da dove viene Codex, e da dove viene il cifrario.
 *
 * Non e' una schermata di crediti. Codex esiste per una tavola cifrata mostrata a lezione di
 * educazione civica, e la prima versione dell'app quella tavola l'aveva ridotta a una
 * corrispondenza numerica che ne buttava via il funzionamento. Questa sezione e' il posto in cui
 * la si rimette in piedi per quello che e': **si legge come funzionava, e la si prova**.
 *
 * La parte interattiva non e' un ornamento. Un nomenclatore si capisce quando si vede una parola
 * intera diventare un segno solo e una doppia diventarne un altro: leggerlo scritto non basta, e
 * questa e' un'app che sull'aprire le cose ci ha costruito tutto.
 */
@Composable
fun OriginsScreen(onBack: () -> Unit) {
  var plateOpen by remember { mutableStateOf(false) }

  FluidScreen(
    title = stringResource(R.string.origins_title),
    subtitle = stringResource(R.string.origins_subtitle),
    onBack = onBack,
    ambient = remember { FluidAmbient(tone = FluidHeroTone.SecondaryToTertiary, motif = FluidHeroMotif.Ripples) },
    horizontalPadding = codexHorizontalPadding(),
  ) {
    item(key = "story") {
      Prose(
        stringResource(R.string.origins_story_1),
        stringResource(R.string.origins_story_2),
        stringResource(R.string.origins_story_3),
      )
    }

    item(key = "cipher-header") {
      FluidSectionHeader(
        title = stringResource(R.string.origins_cipher_title),
        detail = stringResource(R.string.origins_cipher_detail),
      )
    }
    item(key = "cipher-parts") {
      FluidListGroup(glass = true) {
        val parts = listOf(
          R.string.origins_part_alphabet to R.string.origins_part_alphabet_detail,
          R.string.origins_part_syllables to R.string.origins_part_syllables_detail,
          R.string.origins_part_doubles to R.string.origins_part_doubles_detail,
          R.string.origins_part_names to R.string.origins_part_names_detail,
          R.string.origins_part_nulls to R.string.origins_part_nulls_detail,
        )
        parts.forEachIndexed { index, (title, detail) ->
          if (index > 0) FluidListDivider()
          FluidListRow(
            title = stringResource(title),
            subtitle = stringResource(detail),
            eyebrow = (index + 1).toString(),
          )
        }
      }
    }
    item(key = "plate") {
      FluidCard(glass = true) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          CipherPlate(onOpen = { plateOpen = true })
          Text(
            text = stringResource(R.string.origins_image_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    item(key = "try-header") {
      FluidSectionHeader(
        title = stringResource(R.string.origins_try_title),
        detail = stringResource(R.string.origins_try_detail),
      )
    }
    item(key = "try") { CipherWorkbench() }

    item(key = "table-header") {
      FluidSectionHeader(
        title = stringResource(R.string.origins_table_title),
        detail = stringResource(R.string.origins_table_detail, Nomenclator.size),
      )
    }
    item(key = "table-names") {
      TablePlate(
        heading = stringResource(R.string.origins_part_names),
        entries = Nomenclator.words,
        columns = 2,
      )
    }
    item(key = "table-letters") {
      TablePlate(
        heading = stringResource(R.string.origins_part_alphabet),
        entries = Nomenclator.letters,
        columns = 4,
      )
    }
    item(key = "table-syllables") {
      TablePlate(
        heading = stringResource(R.string.origins_part_syllables),
        entries = Nomenclator.syllables,
        columns = 5,
      )
    }
    item(key = "table-doubles") {
      TablePlate(
        heading = stringResource(R.string.origins_part_doubles),
        entries = Nomenclator.doubles,
        columns = 4,
      )
    }
    item(key = "table-nulls") {
      TablePlate(
        heading = stringResource(R.string.origins_part_nulls),
        entries = Nomenclator.nulls,
        columns = 6,
      )
    }
    item(key = "table-note") {
      FluidSectionFootnote(text = stringResource(R.string.origins_table_note))
    }

    item(key = "research-header") {
      FluidSectionHeader(title = stringResource(R.string.origins_research_title))
    }
    item(key = "research") {
      Prose(
        stringResource(R.string.origins_research_1),
        stringResource(R.string.origins_research_2),
        stringResource(R.string.origins_research_3),
      )
    }
    item(key = "sources") {
      FluidListGroup(glass = true) {
        Sources.forEachIndexed { index, source ->
          if (index > 0) FluidListDivider()
          FluidListRow(title = source.title, subtitle = source.detail)
        }
      }
    }
    item(key = "research-note") {
      FluidSectionFootnote(text = stringResource(R.string.origins_research_note))
    }
  }

  FluidGlassModalPortal(
    visible = plateOpen,
    onDismissRequest = { plateOpen = false },
    presentation = FluidGlassModalPresentation.Sheet,
    paneTitle = stringResource(R.string.origins_cipher_title),
  ) {
    CodexSheetColumn(
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier
        .padding(horizontal = 16.dp)
        .padding(bottom = 24.dp),
    ) {
      Box(modifier = Modifier.fillMaxWidth().height(460.dp)) {
        CipherViewer()
      }
      Text(
        text = stringResource(R.string.origins_image_zoom),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
      )
      FluidButton(
        text = stringResource(R.string.action_close),
        onClick = { plateOpen = false },
        style = FluidButtonStyle.Tinted,
        fillWidth = true,
      )
    }
  }
}

/** Qualche paragrafo di testo, con l'aria giusta intorno. */
@Composable
private fun Prose(vararg paragraphs: String) {
  FluidCard(glass = true) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      paragraphs.forEach { paragraph ->
        Text(
          text = paragraph,
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

/**
 * Il banco: si scrive una frase e si vede cosa ne fa la tavola.
 *
 * I segni stanno da soli finche' non si chiede cosa vogliono dire. E' la stessa regola dei
 * sigilli, e qui serve a due cose: la prima volta si guarda una riga di segni senza capirci
 * niente -- che e' il punto -- e la seconda si vede che *sotto* un segno ci sono due lettere, o
 * una parola intera, o niente.
 */
@Composable
private fun CipherWorkbench() {
  var plain by remember { mutableStateOf("") }
  var showMeaning by remember { mutableStateOf(false) }
  val sample = stringResource(R.string.origins_try_sample)
  val text = plain.ifBlank { sample }
  // Il seme e' fisso: cambiando quello che si scrive non deve cambiare tutto il resto della riga,
  // o l'effetto di "vedo cosa succede a questa parola" si perde.
  val tokens = remember(text) { Nomenclator.encipher(text, seed = 0x43444558L) }

  FluidCard(glass = true) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
      FluidTextField(
        value = plain,
        onValueChange = { plain = it },
        label = stringResource(R.string.origins_try_label),
        placeholder = sample,
        singleLine = false,
        maxLines = 3,
        modifier = Modifier.fillMaxWidth(),
      )

      CipherLine(tokens = tokens, showMeaning = showMeaning)

      FluidListGroup {
        FluidListRow(
          title = stringResource(
            if (showMeaning) R.string.origins_try_hide else R.string.origins_try_show,
          ),
          subtitle = stringResource(R.string.origins_try_show_detail),
          onClick = { showMeaning = !showMeaning },
        )
      }

      val meaningful = tokens.count { it.kind != Nomenclator.Kind.SPACE && it.kind != Nomenclator.Kind.NULL }
      val nulls = tokens.count { it.kind == Nomenclator.Kind.NULL }
      val plainLetters = Nomenclator.fold(text).count { !it.isWhitespace() }
      Text(
        // Il conto delle nulle e' quasi sempre uno o due su una frase corta, quindi il singolare
        // qui non e' un caso raro da ignorare: "piu' 1 nulle" si legge una volta su tre.
        text = if (nulls == 0) {
          stringResource(R.string.origins_try_stats_zero, plainLetters, meaningful)
        } else {
          pluralStringResource(R.plurals.origins_try_stats, nulls, plainLetters, meaningful, nulls)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** La riga cifrata: un segno per voce, e sotto -- se richiesto -- quello che vale. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CipherLine(tokens: List<Nomenclator.Token>, showMeaning: Boolean) {
  FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(if (showMeaning) 8.dp else 3.dp),
    verticalArrangement = Arrangement.spacedBy(if (showMeaning) 10.dp else 4.dp),
  ) {
    tokens.forEach { token ->
      if (token.kind == Nomenclator.Kind.SPACE) {
        // Lo spazio fra due parole: nella tavola non c'e' un segno, quindi qui c'e' un vuoto.
        Text(
          text = "  ",
          style = MaterialTheme.typography.headlineSmall,
        )
        return@forEach
      }
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = token.symbol,
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
        if (showMeaning) {
          Text(
            text = token.plain.ifEmpty { NULL_MARK },
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = if (token.kind == Nomenclator.Kind.NULL) {
              MaterialTheme.colorScheme.outline
            } else {
              MaterialTheme.colorScheme.primary
            },
          )
        }
      }
    }
  }
}

/** Una parte della tavola, disegnata come una tavola: la voce sopra, il segno sotto. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TablePlate(
  heading: String,
  entries: List<Nomenclator.Entry>,
  columns: Int,
) {
  FluidCard(glass = true) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(
        text = heading,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = columns,
      ) {
        entries.forEach { entry ->
          Column(
            modifier = Modifier
              .weight(1f)
              .padding(vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
              text = entry.symbol,
              style = MaterialTheme.typography.titleLarge,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = entry.plain.ifEmpty { NULL_MARK },
              style = MaterialTheme.typography.labelSmall,
              textAlign = TextAlign.Center,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

/** Una fonte da cui potrebbe venire la tavola. Titolo e riferimento, senza link cliccabili. */
private data class Source(val title: String, val detail: String)

/**
 * Le piste della ricerca.
 *
 * Stanno nel codice e non nelle risorse perche' non sono testo dell'interfaccia: sono riferimenti
 * bibliografici, identici in qualunque lingua sia il telefono, e tradurli sarebbe sbagliato.
 */
private val Sources = listOf(
  Source(
    "Vito, La crittografia diplomatica e militare nell'Italia del Quattrocento",
    "NAM · Nuova Antologia Militare, n. 21, 2025",
  ),
  Source(
    "Somogyi, Caratteristiche strutturali di cifrari monoalfabetici italiani",
    "Verbum · Analecta Neolatina, 2016",
  ),
  Source(
    "Meister, Die Anfänge der modernen diplomatischen Geheimschrift",
    "1902 · facsimili di chiavi fiorentine, milanesi e pontificie",
  ),
  Source(
    "Kahn, The Codebreakers",
    "il capitolo sui nomenclatori italiani",
  ),
  Source(
    "Archivio di Stato di Firenze",
    "fondi Dieci di Balìa e Otto di Pratica · Legazioni e commissarie",
  ),
  Source(
    "Pasini, Delle scritture in cifra usate dalla Repubblica di Venezia",
    "per confronto con la scuola veneziana",
  ),
)

/** Come si scrive "questo segno non vuol dire niente" in una tavola. */
private const val NULL_MARK = "—"
