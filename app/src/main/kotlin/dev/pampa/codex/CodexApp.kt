package dev.pampa.codex

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import dev.antigravity.fluidengine.config.EngineRemoteConfig
import dev.antigravity.fluidengine.foundation.EngineCompatibility
import dev.antigravity.fluidengine.foundation.EngineFlag
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dev.pampa.codex.core.AppAttestation
import dev.pampa.codex.core.AppBootstrap
import dev.pampa.codex.core.DeliveryWorker
import dev.pampa.codex.data.CodexStore
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * I feature flag di Codex. Si dichiarano dove servono, con il default che descrive come si comporta
 * la build di oggi: senza rete l'app deve fare esattamente quello che faceva il giorno in cui e'
 * uscita. Le voci nel manifest remoto si aggiungono solo quando serve accenderle.
 */
object CodexFlags {
  /** Cerca i contatti vicini anche con l'app chiusa (servizio in primo piano). */
  val NearbyBackground = EngineFlag(key = "nearbyBackground", default = false)

  /** La scheda Storie e la pubblicazione del sigillo del giorno. */
  val Stories = EngineFlag(key = "storiesEnabled", default = true)

  /** "Condividi come immagine" per la tecnica Quadro. */
  val PaintingExport = EngineFlag(key = "paintingExport", default = true)
}

@HiltAndroidApp
class CodexApp : Application(), Configuration.Provider {

  @Inject lateinit var remoteConfig: EngineRemoteConfig
  @Inject lateinit var bootstrap: AppBootstrap
  @Inject lateinit var store: CodexStore
  @Inject lateinit var workerFactory: HiltWorkerFactory

  /** I lavori in background sanno costruirsi con Hilt: la consegna ha bisogno del magazzino. */
  override val workManagerConfiguration: Configuration
    get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

  /** Vive quanto il processo: niente di quello che parte qui ha qualcosa da cui essere cancellato. */
  private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  override fun onCreate() {
    super.onCreate()

    // **Prima di ogni chiamata a Firebase**: l'attestazione va installata sul processo, non sulla
    // prima richiesta, altrimenti la prima parte senza gettone e in enforcement viene rifiutata.
    AppAttestation.install()

    // Le impostazioni con cui Codex nasce (tema scuro, accento di marca): una volta sola.
    applicationScope.launch { bootstrap.ensureDefaults() }

    // Il file di controllo, se la copia in cache e' vecchia. Non blocca niente: finche' non arriva,
    // l'app usa l'ultima risposta valida o i default compilati.
    applicationScope.launch {
      runCatching { remoteConfig.refreshIfStale() }
        .onFailure { Log.w(TAG, "Manifest remoto non aggiornato", it) }
    }

    // Se c'e' qualcosa in coda, si chiede al sistema di consegnarlo appena c'e' rete -- anche se
    // l'app viene chiusa un secondo dopo. E' l'unica strada per cui una foto mandata prima di
    // mettere via il telefono parte davvero.
    applicationScope.launch {
      store.chats.observeOutboxSize().collect { quanti ->
        if (quanti > 0) DeliveryWorker.schedule(this@CodexApp)
      }
    }

    // Cosa fare se questa build e' rimasta indietro rispetto a quello che il manifest chiede. Per
    // ora si registra e basta: la schermata che lo mostra arriva con le impostazioni complete.
    applicationScope.launch {
      runCatching {
        when (remoteConfig.compatibility()) {
          EngineCompatibility.OK -> Unit
          EngineCompatibility.UPDATE_RECOMMENDED -> Log.i(TAG, "Aggiornamento consigliato")
          EngineCompatibility.UPDATE_REQUIRED -> Log.w(TAG, "Aggiornamento necessario")
        }
        val kill = remoteConfig.current().killSwitch
        if (kill.enabled) Log.e(TAG, "Kill switch attivo: ${kill.message}")
      }
    }
  }

  private companion object {
    const val TAG = "CodexApp"
  }
}
