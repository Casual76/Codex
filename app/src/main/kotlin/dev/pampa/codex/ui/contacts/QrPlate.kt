package dev.pampa.codex.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.pampa.codex.seal.QrMatrix
import dev.pampa.codex.ui.theme.CodexQrEye
import dev.pampa.codex.ui.theme.CodexQrInk
import dev.pampa.codex.ui.theme.CodexQrPaper

/**
 * Il proprio sigillo, stampato.
 *
 * Un QR disegnato a quadratini secchi e' il pezzo piu' brutto di qualsiasi app che ne mostri uno, e
 * in Codex sarebbe fuori posto due volte: questa e' la schermata in cui una persona **si presenta**.
 * Quindi i moduli sono pastiglie con l'angolo continuo come tutto il resto, i tre occhi agli angoli
 * sono anelli invece di scacchiere, e in mezzo c'e' il minerale di chi lo mostra.
 *
 * Nessuna di queste libertà tocca la leggibilità: la posizione dei moduli non cambia di un pixel,
 * il contrasto resta quello del foglio bianco, e il buco al centro sta dentro il trenta per cento
 * che la correzione d'errore alta ricostruisce da sola. Che il codice si rilegga davvero non e' un
 * atto di fede: `QrMatrixTest` lo ristampa e lo rida' in pasto al lettore di zxing.
 */
@Composable
fun QrPlate(
  payload: String,
  contentDescription: String,
  modifier: Modifier = Modifier,
  center: (@Composable () -> Unit)? = null,
) {
  val matrix = remember(payload) { QrMatrix.of(payload) }
  Box(
    modifier = modifier
      .aspectRatio(1f)
      .clip(ContinuousCornerShape(FluidRadius.Card))
      .background(CodexQrPaper)
      // Il margine chiaro attorno non e' respiro estetico: senza, molti lettori non trovano dove
      // comincia il codice. Quattro moduli sono il minimo dello standard, qui sono di piu'.
      .padding(20.dp)
      .semantics { this.contentDescription = contentDescription }
      .drawBehind { drawMatrix(matrix, hasCenter = center != null) },
    contentAlignment = Alignment.Center,
    content = { center?.invoke() },
  )
}

/** Quanto del lato occupa il minerale in mezzo. Oltre, la correzione d'errore non basta piu'. */
private const val CENTER_FRACTION = 0.22f

private fun DrawScope.drawMatrix(matrix: QrMatrix, hasCenter: Boolean) {
  val module = size.minDimension / matrix.size
  val dot = module * 0.9f
  val radius = CornerRadius(dot * 0.34f, dot * 0.34f)
  val inset = (module - dot) / 2f

  // Il cerchio lasciato libero al centro, in moduli: si svuota prima di disegnare, cosi' il
  // minerale non appoggia su mezze pastiglie che spuntano da sotto.
  val hole = if (hasCenter) matrix.size * CENTER_FRACTION / 2f + 0.6f else 0f
  val middle = (matrix.size - 1) / 2f

  for (y in 0 until matrix.size) {
    for (x in 0 until matrix.size) {
      if (!matrix[x, y] || matrix.isFinder(x, y)) continue
      if (hole > 0f) {
        val dx = x - middle
        val dy = y - middle
        if (dx * dx + dy * dy < hole * hole) continue
      }
      drawRoundRect(
        color = CodexQrInk,
        topLeft = Offset(x * module + inset, y * module + inset),
        size = Size(dot, dot),
        cornerRadius = radius,
      )
    }
  }

  val edge = (matrix.size - QrMatrix.FINDER) * module
  listOf(Offset(0f, 0f), Offset(edge, 0f), Offset(0f, edge)).forEach { corner ->
    drawFinder(corner, module)
  }
}

/**
 * Un occhio: l'anello esterno di sette moduli, e la pupilla di tre.
 *
 * Il quadrato bianco in mezzo non si disegna, e' il foglio che si vede: un anello e un punto sono
 * esattamente gli stessi pixel scuri di una scacchiera sette per sette, con gli angoli tondi.
 */
private fun DrawScope.drawFinder(corner: Offset, module: Float) {
  val side = QrMatrix.FINDER * module
  drawRoundRect(
    color = CodexQrInk,
    topLeft = Offset(corner.x + module / 2f, corner.y + module / 2f),
    size = Size(side - module, side - module),
    cornerRadius = CornerRadius(module * 1.9f, module * 1.9f),
    style = Stroke(width = module),
  )
  drawRoundRect(
    color = CodexQrEye,
    topLeft = Offset(corner.x + module * 2f, corner.y + module * 2f),
    size = Size(module * 3f, module * 3f),
    cornerRadius = CornerRadius(module * 1.1f, module * 1.1f),
  )
}
