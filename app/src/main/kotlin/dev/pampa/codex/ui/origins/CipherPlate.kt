package dev.pampa.codex.ui.origins

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * La foto della tavola.
 *
 * E' una fotografia di una fotocopia, ed e' il meglio che esista: quella che il prof ha fatto
 * vedere a lezione. Non e' stata ritoccata. Si legge, ma ci vuole lo zoom, ed e' per questo che
 * toccandola si apre a tutto schermo.
 */
@Composable
fun CipherPlate(
  onOpen: () -> Unit,
  modifier: Modifier = Modifier,
) {
  // Finche' la foto si decodifica, il suo posto e' gia' li'. Senza, la scheda nasce vuota e poi si
  // apre di colpo quando l'immagine arriva, e la pagina salta sotto le dita di chi sta scorrendo.
  val image = rememberCipherImage() ?: run {
    CipherPlatePlaceholder(modifier)
    return
  }
  Image(
    bitmap = image,
    contentDescription = null,
    modifier = modifier
      .fillMaxWidth()
      .aspectRatio(image.width.toFloat() / image.height)
      .clip(ContinuousCornerShape(FluidRadius.Card))
      .fluidPressable(onClick = onOpen),
    contentScale = ContentScale.Crop,
  )
}

/**
 * La tavola a tutto schermo, da ingrandire con le dita.
 *
 * Il doppio tocco alterna fra "tutta intera" e "tre volte piu' grande": e' il gesto che si prova
 * per primo su una foto, e su un documento serve a saltare direttamente alla misura in cui i
 * ghirigori si distinguono.
 */
@Composable
fun CipherViewer(modifier: Modifier = Modifier) {
  val image = rememberCipherImage() ?: return
  var scale by remember { mutableFloatStateOf(1f) }
  var offsetX by remember { mutableFloatStateOf(0f) }
  var offsetY by remember { mutableFloatStateOf(0f) }

  Box(
    modifier = modifier
      .fillMaxSize()
      .clip(ContinuousCornerShape(FluidRadius.Card))
      // Il fondo e' quello del pannello, non nero.
      //
      // Un documento verticale dentro un riquadro largo lascia due bande ai lati, e nere su un
      // pannello di vetro si leggono come un buco nell'interfaccia. Del colore del pannello
      // diventano semplicemente il margine intorno alla carta.
      .background(MaterialTheme.colorScheme.surfaceContainerLowest),
    contentAlignment = Alignment.Center,
  ) {
    Image(
      bitmap = image,
      contentDescription = null,
      contentScale = ContentScale.Fit,
      modifier = Modifier
        .fillMaxSize()
        .graphicsLayer {
          scaleX = scale
          scaleY = scale
          translationX = offsetX
          translationY = offsetY
        }
        .pointerInput(Unit) {
          detectTapGestures(
            onDoubleTap = {
              if (scale > 1.05f) {
                scale = 1f
                offsetX = 0f
                offsetY = 0f
              } else {
                scale = 3f
              }
            },
          )
        }
        .pointerInput(Unit) {
          detectTransformGestures { _, pan, zoom, _ ->
            scale = (scale * zoom).coerceIn(1f, 8f)
            // Lo spostamento si ferma al bordo: un documento che si puo' trascinare fuori dallo
            // schermo si perde, e ritrovarlo richiede di indovinare da che parte e' andato.
            val limitX = size.width * (scale - 1f) / 2f
            val limitY = size.height * (scale - 1f) / 2f
            offsetX = (offsetX + pan.x * scale).coerceIn(-limitX, limitX)
            offsetY = (offsetY + pan.y * scale).coerceIn(-limitY, limitY)
          }
        },
    )
  }
}

/**
 * La foto, caricata una volta.
 *
 * Sta negli asset e non fra le risorse perche' non e' un'illustrazione dell'interfaccia: e' un
 * documento, e vive accanto ai dipinti.
 */
@Composable
private fun rememberCipherImage(): ImageBitmap? {
  val context = LocalContext.current
  var image by remember { mutableStateOf<ImageBitmap?>(null) }
  LaunchedEffect(Unit) {
    image = withContext(Dispatchers.IO) {
      runCatching {
        context.assets.open(ASSET).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
      }.getOrNull()
    }
  }
  return image
}

private const val ASSET = "origini/cifrario.webp"

/** Un riquadro vuoto della forma della tavola: tiene il posto mentre la foto si carica. */
@Composable
private fun CipherPlatePlaceholder(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .aspectRatio(PlateAspect)
      .clip(ContinuousCornerShape(FluidRadius.Card))
      .background(MaterialTheme.colorScheme.surfaceContainerLowest),
  )
}

/** Le proporzioni della foto, scritte qui perche' servono prima di averla caricata. */
private const val PlateAspect = 1094f / 1264f
