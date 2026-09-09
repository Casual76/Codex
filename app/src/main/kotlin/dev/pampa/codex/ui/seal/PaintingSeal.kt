package dev.pampa.codex.ui.seal

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.pampa.codex.seal.Painting
import dev.pampa.codex.seal.PaintingCatalog
import dev.pampa.codex.seal.SeededRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Il dipinto che si sgretola.
 *
 * L'immagine e' una crosta di colore su una tela: al tocco la crosta si stacca a **scaglie** e
 * cade, e sotto resta l'impronta scura di quello che c'era, con il messaggio sopra.
 *
 * Tre scelte fanno la differenza fra questo e un'immagine che si rompe a quadretti:
 *
 * - **le scaglie non sono una griglia.** I tagli sono sfalsati riga per riga, quindi nessuna
 *   cucitura attraversa il quadro da parte a parte e il pezzo che si stacca ha una forma sua.
 * - **sotto non c'e' il vuoto.** Dietro le scaglie c'e' lo stesso dipinto, scurito: quello che si
 *   apre e' un'ombra dell'immagine, non un buco del colore della bolla. E' anche quello che rende
 *   leggibile il testo senza metterci sopra un velo che si vede.
 * - **cadono.** Le direzioni sono verso il basso a ventaglio e ogni scaglia gira su se' stessa: e'
 *   la differenza fra intonaco che viene giu' e pixel che si dissolvono.
 *
 * L'ordine e' un'onda: le scaglie si staccano da un lato e la rottura attraversa il quadro, invece
 * di bucarlo a caso in cento punti insieme.
 */
@Composable
fun PaintingSealSurface(
  painting: Painting,
  seed: Long,
  progress: () -> Float,
  modifier: Modifier = Modifier,
  /**
   * Il colore verso cui il dipinto si spegne.
   *
   * E' il fondo della bolla in cui sta, e deve venire da fuori: un velo **nero** fisso funziona
   * finche' il testo e' chiaro, e nel tema chiaro mette testo scuro sopra un velo scuro. Cosi'
   * invece il dipinto sfuma verso il posto in cui si trova, in tutti e due i temi.
   */
  veil: Color = Color.Black,
) {
  val context = LocalContext.current
  var image by remember(painting.id) { mutableStateOf<ImageBitmap?>(null) }

  LaunchedEffect(painting.id) {
    // Il dipinto e' 1280 px: decodificarlo sul thread dell'interfaccia si vede, e a schermo ne
    // servono si e no 400. `inSampleSize = 2` costa un quarto della memoria.
    image = withContext(Dispatchers.Default) {
      PaintingCatalog(context.assets).loadBitmap(painting, sampleSize = 2)?.asImageBitmap()
    }
  }

  val flakes = remember(seed) { Flakes.of(seed) }
  val bitmap = image

  Canvas(modifier = modifier) {
    if (bitmap == null) return@Canvas
    val amount = progress().coerceIn(0f, 1f)
    val window = windowOf(bitmap, size)
    if (amount <= 0f) {
      drawWhole(bitmap, window, alpha = 1f)
    } else {
      drawCrumbling(bitmap, window, flakes, amount, veil)
    }
  }
}

/**
 * La finestra sul dipinto: la parte che si vede nella copertina.
 *
 * La raccolta ha rotoli cinesi lunghi quattro volte la loro altezza e tele verticali: allungarle
 * per farle entrare in un riquadro largo le storpia, e un dipinto storpiato non e' piu' un dipinto.
 * Si prende invece il rettangolo centrale con le proporzioni della copertina -- e' il taglio che
 * farebbe chiunque appendendo un quadro in una cornice sbagliata.
 */
private class Window(val left: Float, val top: Float, val width: Float, val height: Float)

private fun windowOf(image: ImageBitmap, size: Size): Window {
  val imageWidth = image.width.toFloat()
  val imageHeight = image.height.toFloat()
  if (size.width <= 0f || size.height <= 0f) return Window(0f, 0f, imageWidth, imageHeight)
  val wanted = size.width / size.height
  val actual = imageWidth / imageHeight
  return if (actual > wanted) {
    // Piu' larga del riquadro: si taglia ai lati.
    val width = imageHeight * wanted
    Window((imageWidth - width) / 2f, 0f, width, imageHeight)
  } else {
    // Piu' alta: si taglia sopra e sotto.
    val height = imageWidth / wanted
    Window(0f, (imageHeight - height) / 2f, imageWidth, height)
  }
}

private fun DrawScope.drawWhole(image: ImageBitmap, window: Window, alpha: Float) {
  if (alpha <= 0.004f) return
  drawImage(
    image = image,
    srcOffset = IntOffset(window.left.roundToInt(), window.top.roundToInt()),
    srcSize = IntSize(
      window.width.roundToInt().coerceAtLeast(1),
      window.height.roundToInt().coerceAtLeast(1),
    ),
    dstOffset = IntOffset.Zero,
    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
    alpha = alpha,
    filterQuality = FilterQuality.Medium,
  )
}

/**
 * Un fotogramma della caduta.
 *
 * L'impronta sotto e il velo che la scurisce se ne vanno insieme alle scaglie, cosi' l'ultimo
 * fotogramma della copertina e' gia' il fondo della bolla: al cambio di layout non c'e' niente da
 * far sparire e non si vede nessuno scatto.
 */
private fun DrawScope.drawCrumbling(
  image: ImageBitmap,
  window: Window,
  flakes: Flakes,
  amount: Float,
  veil: Color,
) {
  // L'impronta: il dipinto stesso, che si spegne mentre la crosta se ne va.
  val ghost = 1f - amount
  drawWhole(image, window, alpha = ghost * ghost * 0.62f)
  // Il velo serve solo finche' sotto c'e' ancora colore acceso, e sparisce con lui.
  drawRect(color = veil, alpha = 0.62f * ghost * (0.35f + 0.65f * amount))

  val tremor = sin(amount * Math.PI).toFloat()
  val jitter = size.minDimension * 0.012f * tremor
  val fall = size.minDimension * 2.4f

  for (index in flakes.delays.indices) {
    val delay = flakes.delays[index]
    val life = ((amount - delay) / (1f - delay)).coerceIn(0f, 1f)
    // Resta piena quasi fino alla fine e poi se ne va in fretta. Con una sfumatura lineare la
    // scaglia passava meta' della sua vita da rettangolo semitrasparente fermo a mezz'aria, ed era
    // il difetto che faceva sembrare il tutto un'immagine che si dissolve invece di pezzi che
    // cadono.
    val alpha = (1f - life * life * life * 1.3f).coerceIn(0f, 1f)
    if (alpha <= 0.01f) continue

    val left = flakes.left[index]
    val top = flakes.top[index]
    val width = flakes.width[index]
    val height = flakes.height[index]

    val dstLeft = left * size.width
    val dstTop = top * size.height
    val dstWidth = width * size.width
    val dstHeight = height * size.height
    val center = Offset(dstLeft + dstWidth / 2f, dstTop + dstHeight / 2f)

    // Prima di staccarsi la scaglia trema sul posto; poi accelera verso il basso.
    val phase = flakes.phases[index]
    val shiverX = cos(phase + amount * 11f) * jitter
    val shiverY = sin(phase * 1.7f + amount * 13f) * jitter
    // Parte subito e accelera: il termine lineare e' lo strappo del distacco, il quadrato e' il
    // peso. Solo il quadrato faceva galleggiare la scaglia per mezza animazione.
    val glide = life * 0.45f + life * life * 0.55f
    val driftX = cos(flakes.angles[index]) * fall * glide * 0.45f
    val driftY = sin(flakes.angles[index]) * fall * glide
    // Cadendo si allontana: rimpicciolisce quel tanto che basta a leggerla come profondita'.
    val shrink = 1f - 0.26f * glide

    withTransform({
      translate(shiverX + driftX, shiverY + driftY)
      rotate(flakes.spins[index] * glide * 1.4f, center)
      scale(shrink, shrink, center)
    }) {
      drawImage(
        image = image,
        // Le coordinate della scaglia sono relative alla **finestra**, non all'immagine intera:
        // altrimenti la scaglia porterebbe via un pezzo di dipinto che nella copertina non si
        // vedeva nemmeno.
        srcOffset = IntOffset(
          (window.left + left * window.width).roundToInt(),
          (window.top + top * window.height).roundToInt(),
        ),
        srcSize = IntSize(
          (width * window.width).roundToInt().coerceAtLeast(1),
          (height * window.height).roundToInt().coerceAtLeast(1),
        ),
        dstOffset = IntOffset(dstLeft.roundToInt(), dstTop.roundToInt()),
        // Un pixel in piu' per lato: senza, fra una scaglia e l'altra si vede la riga del fondo.
        dstSize = IntSize(dstWidth.roundToInt() + 1, dstHeight.roundToInt() + 1),
        alpha = alpha,
        filterQuality = FilterQuality.None,
      )
    }
  }
}

/**
 * La geometria delle scaglie: rettangoli sfalsati, con direzione, rotazione e ritardo.
 *
 * Dipende solo dal seme, mai dal fotogramma: due telefoni che aprono lo stesso messaggio vedono la
 * stessa crosta rompersi allo stesso modo, ed e' la ragione per cui in questo file non c'e' un
 * `Random` di libreria.
 */
private class Flakes(
  val left: FloatArray,
  val top: FloatArray,
  val width: FloatArray,
  val height: FloatArray,
  val delays: FloatArray,
  val angles: FloatArray,
  val spins: FloatArray,
  val phases: FloatArray,
) {
  companion object {
    private const val COLUMNS = 9
    private const val ROWS = 7

    fun of(seed: Long): Flakes {
      val random = SeededRandom(seed xor 0x7111)
      val count = COLUMNS * ROWS

      // La direzione dell'onda che rompe il quadro: una qualsiasi, ma sempre la stessa per questo
      // messaggio. Le scaglie dal lato da cui arriva partono per prime.
      val waveAngle = random.nextFloat(0f, 6.2831855f)
      val waveX = cos(waveAngle)
      val waveY = sin(waveAngle)

      val left = FloatArray(count)
      val top = FloatArray(count)
      val width = FloatArray(count)
      val height = FloatArray(count)
      val delays = FloatArray(count)
      val angles = FloatArray(count)
      val spins = FloatArray(count)
      val phases = FloatArray(count)

      // I tagli verticali, sfalsati riga per riga: e' l'unica differenza fra "si sbriciola" e "e' a
      // quadretti", e costa tre righe.
      val rowCuts = Array(ROWS) { _ ->
        val offset = random.nextFloat(-0.5f, 0.5f) / COLUMNS
        FloatArray(COLUMNS + 1) { column ->
          when (column) {
            0 -> 0f
            COLUMNS -> 1f
            else -> (column.toFloat() / COLUMNS + offset + random.nextFloat(-0.35f, 0.35f) / COLUMNS)
              .coerceIn(0.01f, 0.99f)
          }
        }.also { it.sort() }
      }
      val bandCuts = FloatArray(ROWS + 1) { row ->
        when (row) {
          0 -> 0f
          ROWS -> 1f
          else -> (row.toFloat() / ROWS + random.nextFloat(-0.3f, 0.3f) / ROWS).coerceIn(0.01f, 0.99f)
        }
      }.also { it.sort() }

      for (row in 0 until ROWS) {
        for (column in 0 until COLUMNS) {
          val index = row * COLUMNS + column
          val x0 = rowCuts[row][column]
          val x1 = rowCuts[row][column + 1]
          left[index] = x0
          width[index] = (x1 - x0).coerceAtLeast(0.005f)
          top[index] = bandCuts[row]
          height[index] = (bandCuts[row + 1] - bandCuts[row]).coerceAtLeast(0.005f)

          val centerX = x0 + width[index] / 2f - 0.5f
          val centerY = bandCuts[row] + height[index] / 2f - 0.5f
          // 0 dal lato da cui parte la rottura, 1 dal lato opposto.
          val front = ((centerX * waveX + centerY * waveY) / 1.4142f + 0.5f).coerceIn(0f, 1f)
          delays[index] = (0.04f + 0.46f * front + random.nextFloat(-0.08f, 0.13f))
            .coerceIn(0.02f, 0.72f)

          // Verso il basso, a ventaglio: mai verso l'alto, o non e' piu' una caduta.
          angles[index] = random.nextFloat(0.55f, 2.60f)
          spins[index] = random.nextFloat(-46f, 46f)
          phases[index] = random.nextFloat(0f, 6.2831855f)
        }
      }
      return Flakes(left, top, width, height, delays, angles, spins, phases)
    }
  }
}
