package dev.pampa.codex.ui.seal

import android.util.Log
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.pampa.codex.BuildConfig
import dev.pampa.codex.seal.Fracture
import dev.pampa.codex.seal.Mineral
import dev.pampa.codex.seal.MineralFacet
import dev.pampa.codex.seal.MineralFamily
import dev.pampa.codex.seal.MineralPoint
import dev.pampa.codex.seal.SeededRandom
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Disegna un minerale, intero o mentre si rompe.
 *
 * Il modulo `:core:seal` produce numeri e questo file li mette su una tela: e' l'unico punto in cui
 * un minerale diventa pixel, quindi l'avatar da 30 dp e la gemma da 148 dp di un messaggio sono
 * garantiti identici nella forma.
 *
 * **Il minerale intero viene disegnato una volta sola in un'immagine**, e da li' in poi si copia. E'
 * la ragione per cui la superficie puo' permettersi grana, inclusioni, ombra al bordo e colpo di
 * luce senza che il fotogramma della rottura -- trenta schegge, ognuna con dentro tutto il disegno
 * -- costi trenta volte tanto. Ed e' anche cio' che garantisce che la scheggia porti via
 * *esattamente* la superficie che aveva sopra: non e' un disegno simile, e' la stessa immagine
 * ritagliata.
 *
 * [fracture] apre la gemma, e lo fa in due tempi come si rompe una cosa vera:
 *
 * 1. **le crepe** (primo 14%): la sagoma resta ferma e lungo i bordi delle future schegge si accende
 *    una linea chiara. E' il fotogramma che fa capire che sta per succedere;
 * 2. **le schegge** (il resto): ogni pezzo parte verso l'esterno, ruota un poco e sfuma. Quelli
 *    lontani dal centro partono prima, ed e' cio' che distingue una rottura da un'esplosione.
 *
 * Le schegge non sono le facce del disegno: vengono da un diagramma di Voronoi (vedi [Fracture]).
 * Un cristallo che si spacca lungo le proprie sfaccettature sembra smontato, non rotto.
 */
@Composable
fun MineralView(
  mineral: Mineral,
  modifier: Modifier = Modifier,
  showGlow: Boolean = true,
  showEdge: Boolean = true,
  /**
   * Il colore delle crepe e della polvere.
   *
   * Viene dal tema e non e' bianco fisso: la polvere vola **sopra il fondo della bolla**, e nel
   * tema chiaro una polvere bianca su un fondo chiaro non si vede affatto.
   */
  spark: Color = MaterialTheme.colorScheme.onSurface,
  fracture: () -> Float = { 0f },
) {
  val pieces = remember(mineral) { Pieces.of(mineral) }
  val layoutDirection = LocalLayoutDirection.current

  Spacer(
    modifier = modifier.drawWithCache {
      val width = size.width.roundToInt()
      val height = size.height.roundToInt()
      val body = if (width >= 1 && height >= 1) {
        renderBody(mineral, pieces, width, height, this, layoutDirection, showEdge)
      } else {
        null
      }

      onDrawBehind {
        val amount = fracture().coerceIn(0f, 1f)
        val crack = (amount / CRACK_PHASE).coerceIn(0f, 1f)
        val fly = ((amount - CRACK_PHASE) / (1f - CRACK_PHASE)).coerceIn(0f, 1f)

        if (showGlow) drawGlow(mineral, amount)
        if (body == null) return@onDrawBehind

        if (fly <= 0f) {
          drawImage(body, topLeft = Offset.Zero)
          if (crack > 0f) drawCracks(mineral, pieces, crack, spark)
        } else {
          pieces.shards.forEach { shard -> drawShard(body, shard, fly, spark) }
          drawDust(pieces, fly, spark)
        }
      }
    },
  )
}

// --- Geometria preparata una volta sola ----------------------------------------------------------

/**
 * Tutto quello che si puo' calcolare senza sapere quanto e' grande la tela: poligoni in coordinate
 * normalizzate, direzioni di volo, granelli di polvere, grana della superficie.
 */
private class Pieces(
  val facets: List<FacetPiece>,
  val shards: List<ShardPiece>,
  val dust: List<DustGrain>,
  val grain: List<Speck>,
  val inclusions: List<Inclusion>,
) {
  companion object {
    fun of(mineral: Mineral): Pieces {
      val random = SeededRandom(mineral.seed xor 0x0D05)
      val stone = mineral.family == MineralFamily.STONE
      return Pieces(
        facets = mineral.facets.map { FacetPiece(it) },
        shards = Fracture.shatter(mineral).map { shard ->
          ShardPiece(
            points = shard.points,
            angle = kotlin.math.atan2(shard.centerY - 0.5f, shard.centerX - 0.5f),
            distance = shard.distanceFromCenter,
            spin = random.nextFloat(-22f, 22f),
          )
        },
        dust = List(26) {
          DustGrain(
            angle = random.nextFloat(0f, 6.2831855f),
            speed = random.nextFloat(0.45f, 1.15f),
            radius = random.nextFloat(0.004f, 0.013f),
            delay = random.nextFloat(0f, 0.25f),
          )
        },
        // La grana e' quello che separa una pietra da una macchia grigia. Su un cristallo sarebbe
        // sporcizia, quindi ce n'e' molta meno e serve solo a togliere la piattezza.
        grain = List(if (stone) 300 else 22) {
          Speck(
            x = random.nextFloat(0.06f, 0.94f),
            y = random.nextFloat(0.06f, 0.94f),
            // Fitta e minuta. Granelli grossi e chiari facevano sembrare la pietra una spugna:
            // la ruvidezza si legge dalla densita', non dalla misura del singolo punto.
            radius = random.nextFloat(0.003f, if (stone) 0.008f else 0.009f),
            // Chiaro o scuro: senza i due segni la grana si legge come una velatura, non come
            // superficie ruvida. Piu' scuri che chiari, come su qualsiasi superficie porosa.
            light = random.nextFloat() < 0.35f,
            // Su un cristallo va tenuta al limite del visibile: appena si distinguono i singoli
            // granelli non sembra piu' minerale, sembra polvere sull'obiettivo.
            alpha = random.nextFloat(0.03f, if (stone) 0.15f else 0.055f),
          )
        },
        // Le inclusioni: i "giardini" dentro un cristallo vero. Righe pallide, mai nette.
        inclusions = if (stone) {
          emptyList()
        } else {
          List(random.nextInt(2, 4)) {
            val angle = random.nextFloat(0f, 6.2831855f)
            // Corte e pallide: una riga lunga e netta dentro un cristallo non si legge come
            // inclusione, si legge come un graffio sullo schermo. Il primo tentativo era lungo il
            // doppio e sembrava esattamente quello.
            val length = random.nextFloat(0.09f, 0.19f)
            val x = 0.5f + random.nextFloat(-0.18f, 0.18f)
            val y = 0.5f + random.nextFloat(-0.18f, 0.18f)
            Inclusion(
              fromX = x - cos(angle) * length / 2f,
              fromY = y - sin(angle) * length / 2f,
              toX = x + cos(angle) * length / 2f,
              toY = y + sin(angle) * length / 2f,
              alpha = random.nextFloat(0.045f, 0.09f),
            )
          }
        },
      )
    }
  }
}

private class FacetPiece(facet: MineralFacet) {
  val points: List<MineralPoint> = facet.points
  val color: Color = Color(facet.color)

  /** Il riquadro della faccia: serve a orientare la sfumatura lungo la luce, non lungo la tela. */
  val bounds: Rect = run {
    var minX = 1f
    var minY = 1f
    var maxX = 0f
    var maxY = 0f
    for (point in points) {
      if (point.x < minX) minX = point.x
      if (point.y < minY) minY = point.y
      if (point.x > maxX) maxX = point.x
      if (point.y > maxY) maxY = point.y
    }
    Rect(minX, minY, maxX, maxY)
  }
}

private class ShardPiece(
  val points: List<MineralPoint>,
  val angle: Float,
  val distance: Float,
  val spin: Float,
)

private class DustGrain(
  val angle: Float,
  val speed: Float,
  val radius: Float,
  val delay: Float,
)

private class Speck(
  val x: Float,
  val y: Float,
  val radius: Float,
  val light: Boolean,
  val alpha: Float,
)

private class Inclusion(
  val fromX: Float,
  val fromY: Float,
  val toX: Float,
  val toY: Float,
  val alpha: Float,
)

// --- La superficie, disegnata una volta ----------------------------------------------------------

/**
 * Il minerale intero, in un'immagine **esattamente della misura della tela**.
 *
 * "Esattamente" e' la parte importante, e costa un difetto per impararla. Prima l'immagine veniva
 * ridotta a 448 px per risparmiare memoria e poi ridisegnata piu' grande: il disegno finiva a una
 * misura, e le crepe e le schegge -- che si calcolano sul contorno in coordinate normalizzate --
 * a un'altra. Il risultato erano crepe sospese fuori dalla pietra, e non si capiva da dove
 * venissero finche' non si e' disegnato il contorno in rosso.
 *
 * Uno a uno non c'e' niente da far combaciare. La memoria resta sotto controllo da sola: la misura
 * la decide il chiamante in dp, e la piu' grande dell'app e' la gemma dell'onboarding.
 */
private fun renderBody(
  mineral: Mineral,
  pieces: Pieces,
  width: Int,
  height: Int,
  density: Density,
  layoutDirection: LayoutDirection,
  showEdge: Boolean,
): ImageBitmap {
  val startedAt = if (BuildConfig.DEBUG) System.nanoTime() else 0L
  val bitmap = ImageBitmap(width, height)
  val canvas = Canvas(bitmap)
  CanvasDrawScope().draw(density, layoutDirection, canvas, Size(width.toFloat(), height.toFloat())) {
    drawBody(mineral, pieces, showEdge)
  }
  if (BuildConfig.DEBUG) {
    // Quanto costa davvero disegnare la superficie. Succede sul thread principale, una volta per
    // misura: se costasse decine di millisecondi si vedrebbe come uno scatto entrando in una chat,
    // e a occhio non si distingue da un emulatore lento.
    val micros = (System.nanoTime() - startedAt) / 1000
    Log.d("CodexSeal", "superficie ${width}x$height: ${micros}us")
  }
  return bitmap
}

/**
 * La superficie: facce, spigoli, grana, ombra al bordo, colpo di luce.
 *
 * L'ordine conta piu' dei singoli strati. La grana va **sotto** l'ombra e la luce, altrimenti si
 * vede che e' appiccicata sopra; l'ombra al bordo va prima del colpo di luce, altrimenti spegne
 * proprio la parte che dovrebbe brillare.
 */
private fun DrawScope.drawBody(mineral: Mineral, pieces: Pieces, showEdge: Boolean) {
  val outline = mineral.outline.toPath(size)
  val fine = size.minDimension >= FINE_DETAIL_PIXELS

  clipPath(outline) {
    pieces.facets.forEach { facet -> drawFacet(facet) }

    if (fine) {
      // Lo spigolo fra due facce, **solo sul cristallo**. Appena visibile: un filo netto
      // trasformerebbe la gemma in un disegno tecnico, e quello che serve e' solo che il taglio si
      // legga. Su una pietra non ci vanno proprio: le sue placche partono tutte dal centro, e
      // disegnarne i bordi la faceva sembrare una fetta di limone.
      if (mineral.family == MineralFamily.CRYSTAL) {
        val hairline = size.minDimension * 0.0035f
        val edge = Color(mineral.palette.edge).copy(alpha = 0.13f)
        pieces.facets.forEach { facet ->
          drawPath(facet.points.toPath(size), edge, style = Stroke(width = hairline))
        }
      }
      pieces.grain.forEach { speck ->
        drawCircle(
          color = if (speck.light) Color.White else Color.Black,
          alpha = speck.alpha,
          radius = size.minDimension * speck.radius,
          center = Offset(speck.x * size.width, speck.y * size.height),
        )
      }
      pieces.inclusions.forEach { inclusion ->
        drawLine(
          color = Color.White.copy(alpha = inclusion.alpha),
          start = Offset(inclusion.fromX * size.width, inclusion.fromY * size.height),
          end = Offset(inclusion.toX * size.width, inclusion.toY * size.height),
          strokeWidth = size.minDimension * 0.005f,
        )
      }
    }

    // L'ombra al bordo: qualunque solido e' piu' scuro dove gira via dalla luce. Il raggio sta
    // dentro la sagoma apposta -- con un raggio piu' largo il tono piu' scuro non viene mai
    // raggiunto e l'ombra non si vede, che e' esattamente l'errore del primo tentativo.
    drawRect(
      brush = Brush.radialGradient(
        colorStops = arrayOf(
          0f to Color.Transparent,
          0.52f to Color.Transparent,
          1f to Color.Black.copy(alpha = 0.52f),
        ),
        center = Offset(size.width * 0.38f, size.height * 0.34f),
        radius = size.minDimension * 0.60f,
      ),
    )

    // La luce che attraversa la gemma e si raccoglie in basso, dove il taglio la rimanda
    // indietro. E' la cosa che distingue una pietra preziosa da un sasso colorato, e va **dopo**
    // l'ombra al bordo: messa prima se la mangiava proprio dove doveva accendersi.
    if (mineral.family == MineralFamily.CRYSTAL) {
      drawRect(
        brush = Brush.radialGradient(
          colors = listOf(Color(mineral.palette.glow).copy(alpha = 0.42f), Color.Transparent),
          center = Offset(size.width * 0.56f, size.height * 0.78f),
          radius = size.minDimension * 0.50f,
        ),
      )
    }

    // Il colpo di luce: una fascia stretta sulla spalla illuminata. Sta a un terzo del percorso e
    // non a meta' perche' una banda che passa per il centro si legge come un graffio nel vetro,
    // non come una superficie lucidata.
    drawRect(
      brush = Brush.linearGradient(
        colorStops = arrayOf(
          0f to Color.Transparent,
          0.22f to Color.White.copy(alpha = 0.015f),
          0.31f to Color.White.copy(alpha = if (mineral.family == MineralFamily.CRYSTAL) 0.15f else 0.07f),
          0.40f to Color.White.copy(alpha = 0.02f),
          1f to Color.Transparent,
        ),
        start = Offset(0f, size.height),
        end = Offset(size.width, 0f),
      ),
    )
  }

  if (showEdge) {
    drawHighlight(mineral)
    drawOutline(mineral)
  }
}

// --- Disegno di ogni fotogramma ------------------------------------------------------------------

private fun DrawScope.drawGlow(mineral: Mineral, fracture: Float) {
  val radius = size.minDimension * 0.62f
  drawCircle(
    brush = Brush.radialGradient(
      colors = listOf(
        Color(mineral.palette.glow).copy(alpha = 0.34f * (1f - fracture)),
        Color.Transparent,
      ),
      center = center,
      radius = radius,
    ),
    radius = radius,
    center = center,
  )
}

/**
 * Una faccia con una sfumatura invece di una tinta piatta.
 *
 * Costa lo stesso e cambia tutto: una gemma di poligoni pieni si legge come un disegno vettoriale,
 * mentre due toni sulla stessa faccia bastano a farla sembrare una superficie che riceve la luce.
 * La direzione e' la stessa per tutte (dall'alto a sinistra), altrimenti diventa un caleidoscopio.
 */
private fun DrawScope.drawFacet(facet: FacetPiece) {
  val path = facet.points.toPath(size)
  val start = Offset(facet.bounds.left * size.width, facet.bounds.top * size.height)
  val end = Offset(facet.bounds.right * size.width, facet.bounds.bottom * size.height)
  drawPath(
    path = path,
    brush = Brush.linearGradient(
      colors = listOf(facet.color.lighten(0.13f), facet.color, facet.color.darken(0.10f)),
      start = start,
      end = end,
    ),
  )
}

/** Il bordo: piu' chiaro dove prende la luce, quasi spento dove la gemma gira via. */
private fun DrawScope.drawOutline(mineral: Mineral) {
  if (mineral.outline.isEmpty()) return
  val edge = Color(mineral.palette.edge)
  drawPath(
    path = mineral.outline.toPath(size),
    brush = Brush.linearGradient(
      colors = listOf(edge.copy(alpha = 0.72f), edge.copy(alpha = 0.34f), edge.copy(alpha = 0.14f)),
      start = Offset(0f, 0f),
      end = Offset(size.width, size.height),
    ),
    style = Stroke(width = size.minDimension * 0.016f),
  )
}

private fun DrawScope.drawHighlight(mineral: Mineral) {
  if (mineral.highlight.size < 3) return
  drawPath(mineral.highlight.toPath(size), Color.White.copy(alpha = 0.26f))
}

/**
 * Le crepe: le linee lungo cui la gemma si spacchera', un istante prima che succeda.
 *
 * Ritagliate sulla sagoma. Le schegge del Voronoi coprono tutto il riquadro, non solo la pietra, e
 * senza il ritaglio le crepe uscivano dal minerale e restavano sospese nel vuoto intorno.
 */
private fun DrawScope.drawCracks(mineral: Mineral, pieces: Pieces, crack: Float, spark: Color) {
  if (mineral.outline.isEmpty()) return
  val width = size.minDimension * (0.004f + 0.006f * crack)
  clipPath(mineral.outline.toPath(size)) {
    pieces.shards.forEach { shard ->
      drawPath(
        path = shard.points.toPath(size),
        color = spark.copy(alpha = 0.55f * crack),
        style = Stroke(width = width),
      )
    }
  }
}

/**
 * Una scheggia in volo: **il disegno del minerale ritagliato dentro la sagoma della scheggia**.
 *
 * La differenza si vede nel fotogramma in cui la gemma si spacca -- con le schegge dipinte a tinta
 * unita, in quell'istante spariscono venature, grana e sfumature e la gemma sembra cambiare
 * oggetto. Ritagliando l'immagine, ogni pezzo porta via esattamente cio' che aveva sopra.
 */
private fun DrawScope.drawShard(body: ImageBitmap, shard: ShardPiece, fly: Float, spark: Color) {
  // Le schegge lontane dal centro partono prima: e' quello che fa sembrare la rottura una rottura
  // e non un'esplosione simmetrica.
  val eagerness = 0.55f + shard.distance
  // Scatto e poi rallentamento: una scheggia parte veloce e perde spinta, non viaggia a velocita'
  // costante. Senza questa curva la rottura sembra un'animazione, con sembra un fatto.
  val burst = 1f - (1f - fly) * (1f - fly)
  // La corsa deve portare la scheggia **fuori** dal riquadro della bolla anche dopo il
  // rallentamento finale. Con un tragitto piu' corto la scheggia si fermava appena dentro il bordo
  // e restava li' a sbiadire per mezzo secondo, gialla su un messaggio gia' leggibile.
  val travel = size.minDimension * 1.55f * burst * eagerness
  // Sfuma tardi ma finisce prima della fine: con una sfumatura lineare le schegge sparivano prima
  // di essere state viste, e con l'esponente da solo l'ultima restava incastrata in un angolo della
  // bolla fino all'ultimo fotogramma, gialla su un messaggio gia' leggibile.
  val alpha = (1f - fly * fly * fly * 1.45f).coerceIn(0f, 1f)
  if (alpha <= 0.004f) return
  translate(left = cos(shard.angle) * travel, top = sin(shard.angle) * travel) {
    rotate(degrees = shard.spin * fly, pivot = center) {
      val path = shard.points.toPath(size)
      clipPath(path) {
        drawImage(image = body, topLeft = Offset.Zero, alpha = alpha)
      }
      // Il taglio fresco: un filo di luce sul bordo, che sparisce prima del pezzo.
      drawPath(
        path = path,
        color = spark.copy(alpha = alpha * 0.32f * (1f - fly)),
        style = Stroke(width = size.minDimension * 0.005f),
      )
    }
  }
}

/** La polvere: quel poco che resta sospeso e dice che qualcosa si e' appena rotto. */
private fun DrawScope.drawDust(pieces: Pieces, fly: Float, spark: Color) {
  pieces.dust.forEach { grain ->
    val life = ((fly - grain.delay) / (1f - grain.delay)).coerceIn(0f, 1f)
    if (life <= 0f) return@forEach
    val travel = size.minDimension * 0.6f * life * grain.speed
    drawCircle(
      color = spark.copy(alpha = 0.34f * (1f - life)),
      radius = size.minDimension * grain.radius * (1f - life * 0.5f),
      center = center + Offset(cos(grain.angle) * travel, sin(grain.angle) * travel),
    )
  }
}

// --- Utilita' -------------------------------------------------------------------------------------

private fun List<MineralPoint>.toPath(size: Size): Path = Path().apply {
  val first = first()
  moveTo(first.x * size.width, first.y * size.height)
  for (index in 1 until this@toPath.size) {
    val point = this@toPath[index]
    lineTo(point.x * size.width, point.y * size.height)
  }
  close()
}

private fun Color.lighten(amount: Float) = Color(
  red = red + (1f - red) * amount,
  green = green + (1f - green) * amount,
  blue = blue + (1f - blue) * amount,
  alpha = alpha,
)

private fun Color.darken(amount: Float) = Color(
  red = red * (1f - amount),
  green = green * (1f - amount),
  blue = blue * (1f - amount),
  alpha = alpha,
)

private val DrawScope.center: Offset get() = Offset(size.width / 2f, size.height / 2f)

/** Quanto dura la fase delle crepe, in frazione dell'animazione. */
private const val CRACK_PHASE = 0.14f

/** Sotto questa misura in pixel la grana e gli spigoli diventano sporco: si disegna la forma e basta. */
private const val FINE_DETAIL_PIXELS = 120f

/** La misura dell'avatar nelle liste e nelle barre. */
val MineralAvatarSize: Dp = 40.dp
