package dev.pampa.codex.ui.chat

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import dev.pampa.codex.R
import dev.pampa.codex.crypto.MessageBody
import kotlinx.coroutines.delay

/**
 * Una foto dentro una bolla.
 *
 * **Non si decodifica mai a piena risoluzione.** Una foto da 12 megapixel diventa in memoria una
 * bitmap da quasi cinquanta megabyte, e una conversazione con dieci foto aperte finirebbe con
 * l'app chiusa dal sistema. Qui si legge prima quanto e' grande e poi si chiede al decodificatore
 * di saltare pixel: quello che si vede in una bolla larga trecento punti non ha bisogno di piu'.
 *
 * L'originale intero esiste comunque, in memoria, quando si apre il visualizzatore -- e li' e'
 * giusto, perche' li' lo si sta guardando.
 */
@Composable
fun PhotoBody(bytes: ByteArray?, media: MessageBody.Media, onOpen: () -> Unit) {
  if (bytes == null) {
    // Il posto e' gia' quello giusto: le misure viaggiano nella busta apposta, e senza di loro la
    // conversazione salterebbe sotto le dita ogni volta che una foto finisce di arrivare.
    Box(
      modifier = Modifier
        .widthIn(min = 220.dp)
        .then(
          if (media.width > 0 && media.height > 0) {
            Modifier.fillMaxWidth().aspectRatio(media.width.toFloat() / media.height)
          } else {
            Modifier.heightIn(min = 160.dp)
          },
        ),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = stringResource(R.string.media_loading),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    return
  }

  val thumbnail = remember(bytes) { decodeSampled(bytes, maxSide = 900) }
  if (thumbnail == null) {
    Text(
      text = stringResource(R.string.media_broken),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(14.dp),
    )
    return
  }
  Image(
    bitmap = thumbnail.asImageBitmap(),
    contentDescription = stringResource(R.string.media_open),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onOpen),
    contentScale = ContentScale.FillWidth,
  )
}

/**
 * La foto a tutto schermo, con lo zoom.
 *
 * Sta in una finestra sua, e questo ha una conseguenza che non si vede finche' non e' troppo tardi:
 * **`FLAG_SECURE` messo sull'Activity non protegge questa finestra.** Un "visualizza una volta"
 * aperto qui si lascerebbe fotografare, dopo tutto il lavoro fatto perche' non si potesse. Quindi
 * il flag si rimette qui, sulla finestra del dialogo.
 */
@Composable
fun PhotoViewer(bytes: ByteArray, secure: Boolean, onDismiss: () -> Unit) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    val view = LocalView.current
    DisposableEffect(secure) {
      // La finestra del dialogo si raggiunge dal suo `DialogWindowProvider`. Se un giorno non ci
      // fosse, meglio proteggere quella dell'Activity che niente.
      val finestra = (view.parent as? DialogWindowProvider)?.window
        ?: view.context.findActivity()?.window
      if (secure) finestra?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
      onDispose { if (secure) finestra?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    val full = remember(bytes) { decodeSampled(bytes, maxSide = 2400) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
      modifier = Modifier
        .fillMaxSize()
        // Nero, non il colore del tema: una foto si guarda su un fondo che non le fa concorrenza.
        .background(Color.Black)
        .pointerInput(Unit) {
          detectTransformGestures { _, pan, zoom, _ ->
            scale = (scale * zoom).coerceIn(1f, 6f)
            if (scale > 1f) {
              offsetX += pan.x
              offsetY += pan.y
            } else {
              offsetX = 0f
              offsetY = 0f
            }
          }
        },
      contentAlignment = Alignment.Center,
    ) {
      if (full == null) {
        Text(
          text = stringResource(R.string.media_broken),
          style = MaterialTheme.typography.bodyMedium,
          color = Color.White,
        )
      } else {
        Image(
          bitmap = full.asImageBitmap(),
          contentDescription = stringResource(R.string.media_photo),
          modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
              scaleX = scale,
              scaleY = scale,
              translationX = offsetX,
              translationY = offsetY,
            ),
          contentScale = ContentScale.Fit,
        )
      }
      IconButton(
        onClick = onDismiss,
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(16.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.Close,
          contentDescription = stringResource(R.string.media_close),
          tint = Color.White,
        )
      }
    }
  }
}

/**
 * Una nota vocale: la forma d'onda e un tasto.
 *
 * L'audio **non tocca il disco**. Arriva qui come byte gia' decifrati e viene dato al lettore
 * attraverso una sorgente che legge dalla memoria: e' la stessa promessa delle foto, e per l'audio
 * costa una classe di dieci righe invece di un file temporaneo che poi qualcuno dimentica.
 */
@Composable
fun VoiceBody(media: MessageBody.Media, bytes: ByteArray?) {
  val player = remember { MediaPlayer() }
  var playing by remember { mutableStateOf(false) }
  var position by remember { mutableIntStateOf(0) }
  var ready by remember(bytes) { mutableStateOf(false) }

  DisposableEffect(bytes) {
    if (bytes != null) {
      runCatching {
        player.reset()
        player.setDataSource(MemorySource(bytes))
        player.prepare()
        player.setOnCompletionListener {
          playing = false
          position = 0
        }
        ready = true
      }
    }
    onDispose { }
  }

  DisposableEffect(Unit) {
    onDispose {
      runCatching { player.release() }
    }
  }

  // Mentre suona, la barra avanza. Sessanta millisecondi sono abbastanza per non vedere gli scatti
  // e abbastanza pochi da non tenere sveglio il telefono per niente.
  LaunchedEffect(playing) {
    while (playing) {
      position = runCatching { player.currentPosition }.getOrDefault(0)
      delay(60)
    }
  }

  val durata = if (media.durationMs > 0) media.durationMs else 0L
  val avanzamento = when {
    durata <= 0 -> 0f
    else -> (position / durata.toFloat()).coerceIn(0f, 1f)
  }

  Row(
    modifier = Modifier
      .widthIn(min = 220.dp)
      .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    IconButton(
      onClick = {
        if (!ready) return@IconButton
        if (playing) {
          runCatching { player.pause() }
          playing = false
        } else {
          runCatching { player.start() }
          playing = true
        }
      },
      enabled = ready,
      modifier = Modifier.size(38.dp),
    ) {
      Icon(
        imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
        contentDescription = stringResource(if (playing) R.string.voice_pause else R.string.voice_play),
      )
    }
    Column(modifier = Modifier.weight(1f)) {
      Waveform(
        waveform = media.waveform,
        progress = avanzamento,
        played = MaterialTheme.colorScheme.primary,
        pending = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
          .fillMaxWidth()
          .height(28.dp),
      )
      Text(
        text = if (bytes == null) {
          stringResource(R.string.media_loading)
        } else {
          formatDuration(if (playing || position > 0) position.toLong() else durata)
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/**
 * Le colonnine di una nota vocale.
 *
 * Sono la parte gia' ascoltata e quella che resta, in due colori. Se la forma d'onda non c'e' --
 * una nota registrata da una versione che non la mandava -- si disegna una fila piatta: e' onesto,
 * dice che il suono c'e' ma non si sa che faccia ha.
 */
@Composable
private fun Waveform(
  waveform: ByteArray,
  progress: Float,
  played: Color,
  pending: Color,
  modifier: Modifier = Modifier,
) {
  val colonne = remember(waveform) {
    if (waveform.isEmpty()) IntArray(24) { 60 } else IntArray(waveform.size) { waveform[it].toInt() and 0xFF }
  }
  Canvas(modifier = modifier) {
    val spazio = size.width / colonne.size
    val larghezza = (spazio * 0.55f).coerceAtLeast(1.5f)
    colonne.forEachIndexed { indice, valore ->
      val altezza = (size.height * (0.18f + 0.82f * valore / 255f))
      val x = indice * spazio + (spazio - larghezza) / 2f
      drawRoundRect(
        color = if ((indice + 0.5f) / colonne.size <= progress) played else pending.copy(alpha = 0.45f),
        topLeft = androidx.compose.ui.geometry.Offset(x, (size.height - altezza) / 2f),
        size = androidx.compose.ui.geometry.Size(larghezza, altezza),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(larghezza / 2f),
      )
    }
  }
}

/** Un audio letto dalla memoria: cosi' il chiaro non passa mai da un file. */
private class MemorySource(private val bytes: ByteArray) : MediaDataSource() {
  override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
    if (position >= bytes.size) return -1
    val quanti = minOf(size.toLong(), bytes.size - position).toInt()
    System.arraycopy(bytes, position.toInt(), buffer, offset, quanti)
    return quanti
  }

  override fun getSize(): Long = bytes.size.toLong()

  override fun close() = Unit
}

/**
 * Decodifica una foto **piu' piccola di com'e'**.
 *
 * `inSampleSize` deve essere una potenza di due: il decodificatore lo arrotonda comunque, e
 * calcolarlo cosi' evita di scoprire a posteriori che ha fatto di testa sua.
 */
private fun decodeSampled(bytes: ByteArray, maxSide: Int): Bitmap? {
  val misure = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeByteArray(bytes, 0, bytes.size, misure)
  if (misure.outWidth <= 0 || misure.outHeight <= 0) return null

  var passo = 1
  while (maxOf(misure.outWidth, misure.outHeight) / passo > maxSide) passo *= 2

  val opzioni = BitmapFactory.Options().apply { inSampleSize = passo }
  return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opzioni) }.getOrNull()
}

/** `1:07`, come su qualsiasi lettore. */
fun formatDuration(millis: Long): String {
  val secondi = (millis / 1000).coerceAtLeast(0)
  return "%d:%02d".format(secondi / 60, secondi % 60)
}

/**
 * L'Activity dietro un Context, srotolando gli involucri.
 *
 * `LocalContext.current` sembra l'Activity e quasi mai lo e': Hilt, i temi e le finestre di dialogo
 * ne mettono ciascuno uno intorno. Il cast diretto restituisce `null` **senza dire niente**, e
 * quello che dipendeva da lui semplicemente non succede: e' cosi' che `FLAG_SECURE` e' rimasto
 * spento su un "visualizza una volta" per tutto il tempo in cui sembrava acceso.
 */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}
