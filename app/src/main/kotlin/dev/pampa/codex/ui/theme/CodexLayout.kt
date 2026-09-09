package dev.pampa.codex.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidScreenDefaults

/**
 * Quanto respiro lasciare ai lati di una schermata.
 *
 * Su un telefono e' quello di sempre. Su un tablet o in orizzontale la colonna del contenuto si
 * **ferma** e sta in mezzo: una riga di lista larga duemila pixel non si legge, si scorre con gli
 * occhi da un capo all'altro, e il titolo finisce a mezzo metro dal suo valore.
 *
 * Si ottiene allargando la spaziatura laterale invece che mettendo un tetto dentro ogni riga:
 * `FluidScreen` ha gia' il parametro giusto, e cosi' tutto quello che sta nella lista -- gruppi,
 * schede, testi -- si stringe insieme senza che nessuno se ne debba accorgere.
 */
@Composable
fun codexHorizontalPadding(maxContentWidth: Dp = CodexContentWidth): Dp {
  val available = LocalCodexPaneWidth.current ?: LocalConfiguration.current.screenWidthDp.dp
  val extra = ((available - maxContentWidth) / 2).coerceAtLeast(0.dp)
  return FluidScreenDefaults.HorizontalPadding + extra
}

/**
 * Quanto e' larga la colonna in cui si sta disegnando, quando non e' lo schermo intero.
 *
 * Sui due pannelli del tablet la larghezza dello schermo e' la misura sbagliata: una schermata che
 * la usa per calcolare i propri margini se ne mette novanta per lato dentro un pannello che ne ha
 * trecentoquaranta in tutto, e il risultato e' una colonna di lettere in verticale. L'ha fatto
 * davvero. Chi affianca due pannelli dice qui quanto e' largo il suo, e i margini tornano quelli di
 * un telefono.
 */
val LocalCodexPaneWidth: ProvidableCompositionLocal<Dp?> = compositionLocalOf { null }

/** La larghezza oltre la quale una colonna di contenuto smette di essere leggibile. */
val CodexContentWidth: Dp = 720.dp

/**
 * Il contenuto di un pannello, con la stessa larghezza massima delle schermate.
 *
 * Un foglio di vetro su un tablet e' largo quanto lo schermo, e dentro ci finiscono quattro righe
 * di scelta lunghe due metri. Il pannello resta largo -- e' lui a fare da fondo -- ma **quello che
 * c'e' dentro** si ferma e sta in mezzo, come nelle liste.
 */
@Composable
fun CodexSheetColumn(
  verticalArrangement: Arrangement.Vertical,
  modifier: Modifier = Modifier,
  horizontalAlignment: Alignment.Horizontal = Alignment.Start,
  content: @Composable ColumnScope.() -> Unit,
) {
  Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
    Column(
      // Prima il tetto, poi il riempimento: al contrario `fillMaxWidth` fissa la larghezza minima
      // a quella dello schermo e il tetto non ha piu' niente da limitare.
      modifier = modifier
        .widthIn(max = CodexContentWidth)
        .fillMaxWidth(),
      verticalArrangement = verticalArrangement,
      horizontalAlignment = horizontalAlignment,
      content = content,
    )
  }
}
