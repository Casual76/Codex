package dev.pampa.codex.ui.me

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import dev.antigravity.fluidengine.foundation.EngineBuild
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.fluidLicensesSection
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.pampa.codex.R
import dev.pampa.codex.seal.PaintingCatalog
import dev.pampa.codex.ui.theme.codexHorizontalPadding

/**
 * Di chi e' quello che si vede.
 *
 * Codex si porta dentro l'APK trentasei dipinti e un font: non sono decorazione presa in prestito,
 * sono la tecnica Quadro e la tecnica Rune, cioe' meta' dell'app. Le riproduzioni sono in CC0 e il
 * font e' in OFL, quindi nessuna delle due licenze **obbligherebbe** a scrivere questa pagina --
 * il pubblico dominio non chiede niente in cambio. Si scrive lo stesso, e per il motivo per cui
 * l'Art Institute of Chicago quelle immagini le ha messe in rete: chi guarda un quadro ha diritto
 * di sapere di chi e' e dove sta l'originale.
 *
 * Le righe si aprono sulla scheda del museo. Un sigillo che si spacca e mostra un Monet senza
 * poter dire che e' un Monet e' meta' del regalo.
 */
@Composable
fun CreditsScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val uriHandler = LocalUriHandler.current
  // Il catalogo si legge una volta: sono trentasei voci di JSON, e l'unica cosa che costa e'
  // aprire l'asset.
  val paintings = remember(context) { PaintingCatalog(context.assets).paintings }
  // Queste due si leggono qui e non piu' in basso: la sezione delle licenze non e' una composable,
  // e' un pezzo di lista, e li' dentro `stringResource` non si puo' chiamare.
  val licencesTitle = stringResource(R.string.credits_engine)
  val licencesNote = stringResource(R.string.credits_engine_note, EngineBuild.VERSION)

  FluidScreen(
    title = stringResource(R.string.credits_title),
    subtitle = stringResource(R.string.credits_subtitle),
    onBack = onBack,
    ambient = remember { FluidAmbient(tone = FluidHeroTone.PrimaryToSecondary, motif = FluidHeroMotif.Cards) },
    horizontalPadding = codexHorizontalPadding(),
  ) {
    if (paintings.isNotEmpty()) {
      item(key = "paintings-header") {
        FluidSectionHeader(
          title = stringResource(R.string.credits_paintings),
          detail = stringResource(R.string.credits_paintings_detail),
        )
      }
      item(key = "paintings") {
        FluidListGroup(glass = true) {
          paintings.forEachIndexed { index, painting ->
            if (index > 0) FluidListDivider()
            FluidListRow(
              // Il titolo originale sopra a quello italiano: e' quello con cui l'opera sta nel
              // museo, ed e' quello che serve a chi la cerca.
              eyebrow = painting.originalTitle.takeIf { it.isNotBlank() && it != painting.title },
              title = painting.title,
              subtitle = painting.artist,
              meta = painting.year,
              onClick = painting.source
                .takeIf { it.isNotBlank() }
                ?.let { url -> ({ runCatching { uriHandler.openUri(url) }; Unit }) },
            )
          }
        }
      }
      item(key = "paintings-note") {
        FluidSectionFootnote(
          text = stringResource(
            R.string.credits_paintings_note,
            paintings.size,
            paintings.first().museum,
          ),
        )
      }
    }

    item(key = "fonts-header") {
      FluidSectionHeader(title = stringResource(R.string.credits_fonts))
    }
    item(key = "fonts") {
      FluidListGroup(glass = true) {
        FluidListRow(
          title = stringResource(R.string.credits_font_runic),
          subtitle = stringResource(R.string.credits_font_runic_detail),
          onClick = { runCatching { uriHandler.openUri(NOTO_RUNIC) }; Unit },
        )
      }
    }
    item(key = "fonts-note") {
      FluidSectionFootnote(text = stringResource(R.string.credits_fonts_note))
    }

    // I crediti di terze parti che l'engine porta dentro l'APK: l'Apache-2.0 del vetro chiede che
    // l'avviso viaggi con la distribuzione. Titolo e nota arrivano da qui e non dai valori di
    // fabbrica dell'engine, che sono in italiano: su un telefono in inglese sarebbero due frasi
    // italiane in mezzo a una pagina tradotta.
    fluidLicensesSection(title = licencesTitle, footnote = licencesNote)
  }
}

/** Da dove viene il font delle rune, per chi vuole andarselo a prendere. */
private const val NOTO_RUNIC = "https://fonts.google.com/noto/specimen/Noto+Sans+Runic/about"
