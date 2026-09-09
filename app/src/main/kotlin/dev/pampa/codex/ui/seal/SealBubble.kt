package dev.pampa.codex.ui.seal

import androidx.compose.animation.core.Animatable
import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.rememberFluidHaptics
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.pampa.codex.BuildConfig
import dev.pampa.codex.R
import dev.pampa.codex.model.SealSpec
import dev.pampa.codex.model.Technique
import dev.pampa.codex.seal.MineralGenerator
import dev.pampa.codex.seal.PaintingCatalog
import androidx.compose.ui.platform.LocalContext
import kotlin.math.sqrt
import kotlinx.coroutines.delay

/**
 * Un messaggio, nella forma in cui arriva: chiuso.
 *
 * Le tre tecniche condividono lo stesso contratto -- una copertina che occupa un posto fisso, un
 * testo che affiora da dietro, un tocco che avvia il tutto -- e differiscono solo in come si aprono.
 * Tenerle qui insieme e' quello che impedisce a una di prendere una strada sua: la roccia, il
 * quadro e le rune si comportano allo stesso modo su tutto tranne che sull'animazione.
 *
 * Quando la rivelazione e' finita la copertina esce dal layout e resta una bolla di testo normale.
 * Il testo che affiorava era gia' allineato come quello finale, quindi al cambio non salta.
 */
@Composable
fun SealBubble(
  spec: SealSpec,
  text: String,
  revealed: Boolean,
  onReveal: () -> Unit,
  modifier: Modifier = Modifier,
  onLongPress: (() -> Unit)? = null,
  /** Senza rituale: e' cosi' che "Rivela tutto" riapre i sigilli gia' visti. */
  instant: Boolean = false,
  containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
  contentColor: Color = MaterialTheme.colorScheme.onSurface,
  /**
   * Cosa compare al posto del testo quando il sigillo si apre.
   *
   * Serve alle foto e alle note vocali: il rituale e' lo stesso -- la copertina si spacca o si
   * sbriciola allo stesso modo -- e cambia soltanto **cosa c'e' sotto**. Un messaggio non e' il suo
   * testo, e' quello che qualcuno ha mandato.
   */
  body: (@Composable () -> Unit)? = null,
) {
  val haptics = rememberFluidHaptics()
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val progress = remember(spec.seed) { Animatable(if (revealed) 1f else 0f) }
  val currentRevealed by rememberUpdatedState(revealed)

  LaunchedEffect(revealed, instant, reducedMotion) {
    val target = if (currentRevealed) 1f else 0f
    if (progress.value == target) return@LaunchedEffect
    val duration = when {
      instant || reducedMotion -> INSTANT_MILLIS
      !currentRevealed -> RESEAL_MILLIS
      spec.technique == Technique.RUNE -> runeRevealMillis(text.length)
      else -> COVER_MILLIS
    }
    if (currentRevealed && !instant && !reducedMotion) haptics.play(FluidHapticEvent.Threshold)
    val startedAt = System.nanoTime()
    progress.animateTo(target, tween(durationMillis = duration))
    if (BuildConfig.DEBUG) {
      // Quanto e' durata davvero. Un'animazione che finisce in un quarto del tempo chiesto e' un
      // difetto che non si vede guardando: si vede solo misurando, e su un emulatore lento la
      // differenza fra "poche immagini" e "troppo veloce" e' impossibile da giudicare a occhio.
      val elapsed = (System.nanoTime() - startedAt) / 1_000_000
      Log.d("CodexSeal", "sigillo ${spec.technique}: chiesti ${duration}ms, durata ${elapsed}ms")
    }
    if (currentRevealed && !instant && !reducedMotion) haptics.play(FluidHapticEvent.Success)
  }

  val amount = progress.value
  val shape = ContinuousCornerShape(FluidRadius.Card)

  // Cosa legge chi non vede lo schermo.
  //
  // Un sigillo chiuso non deve dire il testo -- sarebbe il modo piu' semplice di saltare il rituale
  // -- ma deve dire **che c'e' un messaggio, di che forma, e che si apre toccandolo**: senza,
  // TalkBack annuncia un riquadro senza nome e la conversazione diventa illeggibile.
  val label = if (revealed) {
    text
  } else {
    stringResource(
      when (spec.technique) {
        Technique.RUNE -> R.string.seal_closed_rune
        Technique.ROCK -> R.string.seal_closed_rock
        Technique.PAINTING -> R.string.seal_closed_painting
      },
    )
  }
  val openLabel = stringResource(R.string.seal_open_action)

  Box(
    modifier = modifier
      // La copertina e la bolla di testo hanno misure diverse: senza, all'ultimo fotogramma la
      // bolla salta dalla dimensione della gemma a quella del messaggio.
      .animateContentSize(FluidMotion.intSize())
      .clip(shape)
      .background(containerColor)
      .semantics(mergeDescendants = true) {
        contentDescription = label
        if (!revealed) onClick(label = openLabel, action = null)
      }
      .fluidPressable(
        onClick = if (revealed) null else onReveal,
        onLongClick = onLongPress,
        haptic = null,
      ),
  ) {
    when (spec.technique) {
      // Le rune non hanno copertina: sono gia' il testo, in un altro alfabeto. La bolla ha la
      // stessa misura prima e dopo, e non c'e' niente da far comparire.
      // Una foto non arriva mai in rune (vedi `SealResolver.resolve`), ma se capitasse -- un
      // messaggio vecchio, un formato futuro -- si mostra il corpo invece di sciogliere lettere che
      // non ci sono.
      Technique.RUNE if body != null && amount >= 1f -> body()

      Technique.RUNE -> {
        // Un tocco leggero ogni tanto mentre le lettere si fermano. E' l'unica cosa che rende le
        // rune un oggetto che si apre invece che un testo che cambia: la roccia e il quadro hanno
        // qualcosa da guardare, qui c'e' solo da sentire.
        RuneRevealHaptics(active = amount > 0f && amount < 1f && !instant && !reducedMotion)
        RuneRevealText(
          text = text,
          seed = spec.seed,
          progress = { amount },
          modifier = Modifier
            .engravedSurface(contentColor) { 1f - amount }
            .padding(BubblePadding),
          color = contentColor,
        )
      }

      Technique.ROCK -> CoveredSeal(
        amount = amount,
        text = text,
        body = body,
        contentColor = contentColor,
        coverWidth = RockSize,
        coverHeight = RockSize,
      ) {
        val mineral = remember(spec.seed) { MineralGenerator.generate(spec.seed) }
        MineralView(
          mineral = mineral,
          modifier = Modifier.fillMaxSize(),
          spark = contentColor,
          fracture = { amount },
        )
      }

      Technique.PAINTING -> {
        val context = LocalContext.current
        val catalog = remember(context) { PaintingCatalog(context.assets) }
        val painting = remember(spec.seed, spec.paintingId) {
          catalog.byId(spec.paintingId) ?: catalog.pick(spec.seed)
        }
        val frame = remember(painting?.id) { paintingFrame(painting?.aspectRatio ?: 0f) }
        CoveredSeal(
          amount = amount,
          text = text,
          body = body,
          contentColor = contentColor,
          coverWidth = frame.width,
          coverHeight = frame.height,
        ) {
          if (painting != null) {
            PaintingSealSurface(
              painting = painting,
              seed = spec.seed,
              progress = { amount },
              modifier = Modifier.fillMaxSize(),
              // Il dipinto si spegne verso il fondo della bolla in cui sta.
              veil = containerColor,
            )
          }
        }
      }
    }
  }
}

/**
 * La struttura comune di roccia e quadro: una copertina di misura fissa, il testo che affiora da
 * dietro, e alla fine la sola bolla di testo.
 *
 * Il testo comincia a comparire a poco meno di meta' strada, quando le schegge (o le tessere) hanno
 * gia' lasciato scoperto abbastanza spazio. Prima sarebbe leggibile attraverso la copertina, e
 * l'apertura perderebbe il suo momento.
 */
@Composable
private fun CoveredSeal(
  amount: Float,
  text: String,
  contentColor: Color,
  coverWidth: Dp,
  coverHeight: Dp,
  body: (@Composable () -> Unit)? = null,
  cover: @Composable BoxScope.() -> Unit,
) {
  if (amount >= 1f) {
    if (body != null) {
      body()
      // La didascalia, se c'e', sta sotto: e' un commento alla foto, non il messaggio.
      if (text.isNotBlank()) {
        Text(
          text = text,
          modifier = Modifier.padding(BubblePadding),
          style = MaterialTheme.typography.bodyMedium,
          color = contentColor,
        )
      }
      return
    }
    Text(
      text = text,
      modifier = Modifier
        .widthIn(min = coverWidth)
        .padding(BubblePadding),
      style = MaterialTheme.typography.bodyLarge,
      color = contentColor,
    )
    return
  }

  Box(
    modifier = Modifier
      .width(coverWidth)
      .height(coverHeight),
    contentAlignment = Alignment.Center,
  ) {
    val emerging = ((amount - TEXT_APPEARS_AT) / (1f - TEXT_APPEARS_AT)).coerceIn(0f, 1f)
    if (emerging > 0f) {
      Text(
        text = text,
        modifier = Modifier
          .padding(BubblePadding)
          .alpha(emerging)
          .scale(0.94f + 0.06f * emerging),
        style = MaterialTheme.typography.bodyLarge,
        color = contentColor,
      )
    }
    cover()
  }
}

/**
 * La superficie su cui le rune sono incise.
 *
 * Finche' il messaggio e' chiuso la bolla non e' una bolla: e' una lastra. Un filo di luce sul
 * bordo alto, un'ombra su quello basso, e una velatura che scende. Costa tre righe e mette le rune
 * alla pari con la roccia e il quadro, che qualcosa da guardare ce l'hanno.
 *
 * Se ne va man mano che il testo si scioglie: quando il messaggio e' leggibile la lastra non c'e'
 * piu', e resta la bolla di sempre.
 */
private fun Modifier.engravedSurface(tint: Color, amount: () -> Float): Modifier = drawBehind {
  val strength = amount().coerceIn(0f, 1f)
  if (strength <= 0.01f) return@drawBehind
  drawRect(
    brush = Brush.verticalGradient(
      listOf(tint.copy(alpha = 0.05f * strength), Color.Transparent),
    ),
  )
  val hairline = 1.dp.toPx()
  drawRect(
    color = tint.copy(alpha = 0.16f * strength),
    size = androidx.compose.ui.geometry.Size(size.width, hairline),
  )
  drawRect(
    color = Color.Black.copy(alpha = 0.16f * strength),
    topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - hairline),
    size = androidx.compose.ui.geometry.Size(size.width, hairline),
  )
}

/**
 * L'aptica delle rune: un tocco leggero ogni tanto mentre le lettere si fermano.
 *
 * Separata dal disegno perche' la cadenza e' la stessa qualunque sia la lunghezza del messaggio: un
 * tick per lettera su un testo lungo sarebbe un ronzio.
 */
@Composable
private fun RuneRevealHaptics(active: Boolean) {
  val haptics = rememberFluidHaptics()
  LaunchedEffect(active) {
    while (active) {
      delay(150)
      haptics.play(FluidHapticEvent.Tick)
    }
  }
}

/** La cornice di un quadro: quanto e' larga e quanto e' alta la copertina. */
internal data class PaintingFrame(val width: Dp, val height: Dp)

/**
 * La cornice segue il dipinto.
 *
 * Una cornice sempre della stessa forma butta via meta' di una tela verticale, e con trentasei
 * opere che vanno dal rotolo cinese lungo il quadruplo della sua altezza al ritratto in piedi si
 * nota subito. Qui la forma la decide il quadro, ma l'**area** resta la stessa: due sigilli nella
 * stessa conversazione pesano uguale anche se uno e' largo e l'altro alto, che e' cio' che tiene la
 * lista ordinata invece di farla sembrare un collage.
 *
 * I limiti servono agli estremi: un rotolo 4:1 diventerebbe una fessura, e una tela strettissima
 * una colonna. Fuori da quelli si taglia, e il taglio lo fa gia' [PaintingSealSurface].
 */
internal fun paintingFrame(aspectRatio: Float): PaintingFrame {
  val ratio = if (aspectRatio <= 0f) DefaultPaintingRatio else aspectRatio.coerceIn(0.72f, 1.9f)
  val width = sqrt(PaintingArea * ratio)
  return PaintingFrame(width = width.dp, height = (width / ratio).dp)
}

private val BubblePadding = 14.dp
private val RockSize = 148.dp

/** L'area della copertina di un quadro, in dp quadrati: e' 224 x 152, la misura di sempre. */
internal const val PaintingArea = 224f * 152f
internal const val DefaultPaintingRatio = 224f / 152f

/** Quando il testo comincia ad affiorare da dietro la copertina. */
private const val TEXT_APPEARS_AT = 0.42f

private const val COVER_MILLIS = 900
private const val RESEAL_MILLIS = 260
private const val INSTANT_MILLIS = 150
