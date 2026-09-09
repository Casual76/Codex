package dev.pampa.codex.ui.lock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.rememberFluidHaptics
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import kotlin.math.roundToInt

/**
 * I pallini del PIN.
 *
 * Quando il PIN e' sbagliato non compare un messaggio rosso: i pallini **scuotono la testa**. E' il
 * gesto che tutti conoscono, dice la stessa cosa in un quarto del tempo e non toglie spazio alla
 * tastiera. Con le animazioni di sistema disattivate resta il colore, perche' l'informazione non
 * puo' dipendere dal movimento.
 */
@Composable
fun PinDots(
  filled: Int,
  total: Int,
  error: Boolean,
  modifier: Modifier = Modifier,
) {
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val shake = remember { Animatable(0f) }

  LaunchedEffect(error) {
    if (error && !reducedMotion) {
      shake.animateTo(
        targetValue = 0f,
        animationSpec = keyframes {
          durationMillis = 420
          0f at 0
          -12f at 60
          12f at 140
          -8f at 220
          6f at 300
          0f at 420
        },
      )
    }
  }

  Row(
    modifier = modifier.offset { androidx.compose.ui.unit.IntOffset(shake.value.roundToInt(), 0) },
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    repeat(total) { index ->
      val isFilled = index < filled
      val diameter by animateDpAsState(
        targetValue = if (isFilled) 13.dp else 11.dp,
        animationSpec = FluidMotion.dp(stiffness = FluidMotion.ResponseSnappy),
        label = "pin-dot",
      )
      // Con l'errore si tinge tutta la fila, ma i pallini vuoti restano smorzati: dopo un
      // tentativo sbagliato il campo e' vuoto, e sei pallini rossi pieni direbbero il contrario.
      val color = when {
        error && isFilled -> MaterialTheme.colorScheme.error
        error -> MaterialTheme.colorScheme.error.copy(alpha = 0.28f)
        isFilled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
      }
      Box(
        modifier = Modifier
          .size(diameter)
          .clip(CircleShape)
          .background(color),
      )
    }
  }
}

/**
 * Il tastierino numerico.
 *
 * Ha una tastiera propria invece del campo di sistema perche' un PIN di sei cifre digitato su una
 * tastiera piena di suggerimenti e correzioni automatiche non e' la stessa cosa: qui i tasti sono
 * grandi, fissi, e non c'e' niente che impari cosa e' stato scritto.
 */
@Composable
fun PinKeypad(
  onDigit: (Char) -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  /**
   * Quanto e' alto lo spazio disponibile, quando chi chiama lo sa.
   *
   * Serve in orizzontale, dove quattro file di tasti da 68 dp non ci stanno: il tastierino si
   * stringe da solo invece di finire mezzo fuori dallo schermo. Nel caso normale resta 68 dp.
   */
  availableHeight: Dp = Dp.Unspecified,
  leadingKey: (@Composable () -> Unit)? = null,
) {
  val keySize = keySizeFor(availableHeight)
  val gap = keySize * GapRatio
  Column(
    modifier = modifier.widthIn(max = 300.dp),
    verticalArrangement = Arrangement.spacedBy(gap),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    listOf("123", "456", "789").forEach { row ->
      Row(horizontalArrangement = Arrangement.spacedBy(gap * 1.6f)) {
        row.forEach { digit -> DigitKey(digit, keySize, enabled, onDigit) }
      }
    }
    Row(
      horizontalArrangement = Arrangement.spacedBy(gap * 1.6f),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(modifier = Modifier.size(keySize), contentAlignment = Alignment.Center) {
        leadingKey?.invoke()
      }
      DigitKey('0', keySize, enabled, onDigit)
      val haptics = rememberFluidHaptics()
      Box(
        modifier = Modifier
          .size(keySize)
          .clip(CircleShape)
          .fluidPressable(
            onClick = if (enabled) {
              {
                haptics.play(FluidHapticEvent.Tap)
                onDelete()
              }
            } else {
              null
            },
            haptic = null,
          ),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.Backspace,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun DigitKey(digit: Char, size: Dp, enabled: Boolean, onDigit: (Char) -> Unit) {
  val haptics = rememberFluidHaptics()
  Box(
    modifier = Modifier
      .size(size)
      .clip(CircleShape)
      .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.06f else 0.03f))
      .fluidPressable(
        onClick = if (enabled) {
          {
            haptics.play(FluidHapticEvent.Tap)
            onDigit(digit)
          }
        } else {
          null
        },
        pressedScale = 0.92f,
        haptic = null,
      ),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = digit.toString(),
      style = MaterialTheme.typography.headlineSmall,
      color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f),
    )
  }
}

/**
 * La misura di un tasto, dato lo spazio in altezza.
 *
 * Quattro file piu' tre spazi: se non ci stanno, si stringe fino al minimo sotto cui un tasto non
 * si centra piu' col pollice. Sotto quello non si scende, e chi chiama fa scorrere.
 */
private fun keySizeFor(availableHeight: Dp): Dp {
  if (availableHeight == Dp.Unspecified) return KeySize
  val needed = 4 + 3 * GapRatio
  val fitting = availableHeight / needed
  return fitting.coerceIn(MinKeySize, KeySize)
}

private val KeySize = 68.dp
private val MinKeySize = 52.dp

/** Lo spazio fra due tasti, in frazione del tasto: cosi' stringendosi resta proporzionato. */
private const val GapRatio = 14f / 68f

/** Lo spazio che il tastierino occupa alla misura piena: serve a chi deve lasciargli posto. */
val PinKeypadHeight = (68 * 4 + 14 * 3).dp
