package dev.pampa.codex.ui.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import dev.pampa.codex.seal.QrReader
import java.util.concurrent.Executors

/** Se la fotocamera e' gia' stata concessa. Si chiede a ogni composizione: l'utente puo' revocarla. */
internal fun hasCameraPermission(context: Context): Boolean =
  ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
    PackageManager.PERMISSION_GRANTED

/**
 * Ricorda se il permesso c'e', e sa come chiederlo.
 *
 * Il valore si aggiorna da solo quando l'utente risponde alla richiesta di sistema, cosi' la
 * schermata passa dalla spiegazione all'anteprima senza che nessuno debba tornare indietro e
 * rientrare.
 */
@Composable
internal fun rememberCameraPermission(): Pair<Boolean, () -> Unit> {
  val context = LocalContext.current
  var granted by remember { mutableStateOf(hasCameraPermission(context)) }
  val launcher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted = it }
  return granted to { launcher.launch(Manifest.permission.CAMERA) }
}

/**
 * L'anteprima della fotocamera che cerca un codice.
 *
 * **Guarda e basta.** Non registra, non salva un fotogramma, non manda niente da nessuna parte:
 * ogni immagine viene letta sul telefono e chiusa subito. Il riconoscitore e' quello che l'app usa
 * anche per **disegnare** i codici, quindi il pairing funziona senza rete, senza servizi Google e
 * senza un modello da scaricare -- che e' esattamente la situazione di due persone che si
 * scambiano un sigillo di persona.
 *
 * [onCode] scatta una volta sola: il primo codice buono chiude l'analisi, cosi' la stessa scheda
 * non entra due volte mentre la mano si sposta.
 */
@Composable
internal fun QrScannerPreview(
  onCode: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val currentOnCode by rememberUpdatedState(onCode)

  val controller = remember { LifecycleCameraController(context) }
  val previewView = remember {
    PreviewView(context).apply {
      // FILL_CENTER: l'inquadratura riempie il riquadro. Con FIT restano due bande nere e la gente
      // avvicina il telefono al codice invece di allontanarlo, che e' il modo di non farlo leggere.
      scaleType = PreviewView.ScaleType.FILL_CENTER
    }
  }

  DisposableEffect(controller, lifecycleOwner) {
    // Un thread solo, e i fotogrammi vecchi si buttano: leggere un QR costa qualche millisecondo e
    // accodarli farebbe scorrere l'anteprima in ritardo sulla mano che si muove.
    val executor = Executors.newSingleThreadExecutor()
    var done = false
    controller.imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
    controller.setImageAnalysisAnalyzer(executor) { image ->
      if (!done) {
        readCode(image)?.let { code ->
          done = true
          ContextCompat.getMainExecutor(context).execute { currentOnCode(code) }
        }
      }
      image.close()
    }
    controller.bindToLifecycle(lifecycleOwner)
    previewView.controller = controller
    onDispose {
      controller.clearImageAnalysisAnalyzer()
      controller.unbind()
      previewView.controller = null
      executor.shutdown()
    }
  }

  Box(modifier = modifier) {
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
  }
}

/**
 * Il piano della luminanza del fotogramma, cosi' com'e'.
 *
 * Il buffer arriva **con il passo della fotocamera**, non con la larghezza dell'immagine, e viene
 * passato cosi': ricopiarlo riga per riga per "sistemarlo" sarebbe un megabyte di copie trenta
 * volte al secondo per un'informazione che il lettore sa gia' gestire.
 */
private fun readCode(image: ImageProxy): String? {
  val plane = image.planes.firstOrNull() ?: return null
  val buffer = plane.buffer
  val bytes = ByteArray(buffer.remaining())
  buffer.get(bytes)
  return QrReader.decode(
    luminance = bytes,
    width = image.width,
    height = image.height,
    rowStride = plane.rowStride,
  )
}
