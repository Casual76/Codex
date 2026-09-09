package dev.pampa.codex.ui.seal

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.core.content.res.ResourcesCompat
import dev.pampa.codex.R
import dev.pampa.codex.seal.RuneAlphabet
import dev.pampa.codex.seal.SeededRandom
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** Il font runico incluso nell'APK: sul font di sistema quei glifi non ci sono. */
val RunicFontFamily = FontFamily(Font(R.font.noto_sans_runic))

/**
 * Il testo che si rivela una lettera alla volta, da sinistra a destra.
 *
 * **Ogni runa sta esattamente dove stara' la sua lettera.** E' la ragione per cui questo componente
 * non e' un semplice `Text` con una stringa che cambia: le rune sono piu' larghe delle lettere
 * latine, e sostituendole man mano il testo gia' fermo si riorganizzava a ogni fotogramma -- le
 * righe si spezzavano in punti diversi e la parte gia' letta si spostava sotto gli occhi.
 *
 * Qui il testo vero viene misurato una volta sola. Da quella misura si prendono le posizioni: la
 * parte gia' rivelata si disegna ritagliando il testo vero (quindi e' *esattamente* il testo
 * finale, con le sue interruzioni di riga), e per ogni carattere non ancora fermo si disegna una
 * runa centrata nella casella che quella lettera occupera'. Niente si sposta mai.
 *
 * Il tremolio e il progresso si leggono dentro la fase di disegno, non nella composizione: cambiare
 * runa venti volte al secondo non fa ricomporre niente, ridisegna e basta.
 */
@Composable
fun RuneRevealText(
  text: String,
  seed: Long,
  progress: () -> Float,
  modifier: Modifier = Modifier,
  style: TextStyle = MaterialTheme.typography.bodyLarge,
  color: Color = LocalContentColor.current,
) {
  val measurer = rememberTextMeasurer()
  val context = LocalContext.current
  val runicTypeface: Typeface = remember(context) {
    ResourcesCompat.getFont(context, R.font.noto_sans_runic) ?: Typeface.DEFAULT
  }
  val holder = remember { LayoutHolder() }
  val sealedText = remember(text, seed) { RuneAlphabet.transcribe(text, seed) }

  var tick by remember { mutableIntStateOf(0) }
  val running = progress() > 0f && progress() < 1f
  LaunchedEffect(running) {
    while (running) {
      delay(SCRAMBLE_MILLIS)
      tick += 1
    }
  }

  val runePaint = remember(runicTypeface) {
    Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = runicTypeface }
  }

  Layout(
    modifier = modifier.drawBehind {
      val layout = holder.layout ?: return@drawBehind
      val amount = progress().coerceIn(0f, 1f)
      // Letto qui dentro apposta: e' una lettura di stato in fase di disegno, quindi invalida solo
      // il disegno. Se stesse nella composizione, ogni 40 ms si ricomporrebbe l'intera bolla.
      val currentTick = tick
      drawRunicReveal(
        layout = layout,
        text = text,
        sealedText = sealedText,
        amount = amount,
        seed = seed,
        tick = currentTick,
        color = color,
        paint = runePaint,
      )
    },
  ) { _, constraints ->
    val layout = measurer.measure(
      text = AnnotatedString(text),
      style = style.copy(color = color),
      constraints = constraints,
    )
    holder.layout = layout
    layout(layout.size.width, layout.size.height) {}
  }
}

/**
 * Dove il testo e' finito dopo la misura.
 *
 * Una variabile normale e non uno stato: il disegno avviene dopo il layout nello stesso fotogramma,
 * quindi il valore e' sempre fresco, e uno stato scritto durante la misura farebbe ricomporre a
 * ogni misura.
 */
private class LayoutHolder {
  var layout: TextLayoutResult? = null
}

private fun DrawScope.drawRunicReveal(
  layout: TextLayoutResult,
  text: String,
  sealedText: String,
  amount: Float,
  seed: Long,
  tick: Int,
  color: Color,
  paint: Paint,
) {
  // La lettera in corso vale una frazione, non zero o uno.
  //
  // Con l'arrotondamento una lettera compariva di colpo a meta' del suo tempo, e a ventotto lettere
  // al secondo quel salto si vede: e' la differenza fra un testo che si scioglie e uno che scatta.
  // Con il troncamento piu' la frazione, la lettera di frontiera sfuma dentro mentre la sua runa
  // sfuma fuori, e le due cose si scambiano il posto invece di sostituirsi.
  val exact = if (amount >= 1f) text.length.toFloat() else text.length * amount
  val locked = floor(exact).toInt().coerceIn(0, text.length)
  val fraction = (exact - locked).coerceIn(0f, 1f)

  // 1. La parte gia' ferma: il testo vero, ritagliato riga per riga fino al punto raggiunto.
  //    Ritagliare invece di ridisegnare una sottostringa garantisce che sia identico al finale.
  if (locked > 0) {
    if (locked >= text.length) {
      drawText(layout)
    } else {
      clipPath(lockedRegion(layout, locked)) { drawText(layout) }
    }
  }

  if (locked >= text.length) return
  val canvas = drawContext.canvas.nativeCanvas

  // 2. La lettera di frontiera, che sta arrivando: lo stesso testo, ritagliato alla sua sola
  //    casella e disegnato dentro uno strato trasparente.
  if (fraction > 0.01f && !text[locked].isWhitespace()) {
    val box = layout.getBoundingBox(locked)
    val saved = canvas.saveLayerAlpha(
      box.left, box.top, box.right, box.bottom,
      (fraction * 255).roundToInt().coerceIn(0, 255),
    )
    clipPath(Path().apply { addRect(box) }) { drawText(layout) }
    canvas.restoreToCount(saved)
  }

  // 3. Le rune: una per ogni carattere non ancora fermo, centrata nella casella della sua lettera.
  val scramble = SeededRandom(seed xor (tick.toLong() * 0x9E3779B1L))
  paint.textSize = layout.layoutInput.style.fontSize.toPx()
  val baseAlpha = if (amount <= 0f) 1f else 0.72f

  val glyph = CharArray(1)
  for (index in locked until text.length) {
    val character = text[index]
    if (character.isWhitespace()) continue
    // Quella di frontiera si spegne man mano che la lettera prende il suo posto.
    val alpha = if (index == locked) baseAlpha * (1f - fraction) else baseAlpha
    if (alpha <= 0.01f) continue
    paint.color = color.copy(alpha = alpha).toArgb()
    glyph[0] = if (amount <= 0f) sealedText[index] else RuneAlphabet.randomRune(scramble)
    val box = layout.getBoundingBox(index)
    val line = layout.getLineForOffset(index)
    val baseline = layout.getLineBaseline(line)
    val width = paint.measureText(glyph, 0, 1)
    // Centrata: una runa e' quasi sempre piu' larga di una lettera, e appoggiarla a sinistra la
    // farebbe sconfinare sulla vicina.
    canvas.drawText(glyph, 0, 1, box.left + (box.width - width) / 2f, baseline, paint)
  }
}

/** Le righe gia' rivelate, come area da ritagliare: una per riga, l'ultima tagliata a meta'. */
private fun lockedRegion(layout: TextLayoutResult, locked: Int): Path {
  val path = Path()
  val lastLine = layout.getLineForOffset((locked - 1).coerceAtLeast(0))
  for (line in 0..lastLine) {
    val top = layout.getLineTop(line)
    val bottom = layout.getLineBottom(line)
    val left = layout.getLineLeft(line)
    val right = if (line < lastLine) {
      layout.getLineRight(line)
    } else {
      // La riga in corso si taglia dove arriva la rivelazione.
      layout.getBoundingBox((locked - 1).coerceAtLeast(0)).right
    }
    path.addRect(androidx.compose.ui.geometry.Rect(left, top, right, bottom))
  }
  return path
}

/** Quanto dura la rivelazione di un testo: piu' e' lungo, piu' la cadenza accelera. */
fun runeRevealMillis(length: Int): Int {
  if (length <= 0) return MIN_MILLIS
  val perSecond = max(CHARACTERS_PER_SECOND, length / MAX_SECONDS)
  return ((length.toFloat() / perSecond) * 1000).roundToInt().coerceIn(MIN_MILLIS, MAX_SECONDS * 1000)
}

/** Poco piu' della velocita' di lettura: chi guarda non aspetta e non perde niente. */
private const val CHARACTERS_PER_SECOND = 28

/** Oltre questo, la cadenza accelera invece di far aspettare. */
private const val MAX_SECONDS = 6

private const val MIN_MILLIS = 420
private const val SCRAMBLE_MILLIS = 45L
