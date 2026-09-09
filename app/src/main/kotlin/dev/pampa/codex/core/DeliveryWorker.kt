package dev.pampa.codex.core

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.pampa.codex.data.CodexStore
import java.util.concurrent.TimeUnit

/**
 * Consegna quello che era rimasto in coda, anche con l'app chiusa.
 *
 * Senza di questo, la posta in uscita si svuota **solo mentre Codex e' aperta**: un messaggio
 * scritto in metropolitana parte alla prossima apertura, e nessuno se ne accorge. Con una foto
 * pero' si accorgono tutti -- si manda una foto e si mette via il telefono, e quella resta ferma
 * fino a chissa' quando.
 *
 * Due cose lo rendono possibile e vale nominarle:
 *
 * - nella coda ci sono **buste gia' chiuse**, quindi consegnare non richiede l'app sbloccata: il
 *   lavoro non ha bisogno di nessun segreto che non sia gia' sul telefono;
 * - il vincolo di rete lo fa partire il sistema quando la connessione torna, il che e' meglio di
 *   qualsiasi ciclo che potremmo scrivere noi -- e non consuma batteria per aspettare.
 */
@HiltWorker
class DeliveryWorker @AssistedInject constructor(
  @Assisted context: Context,
  @Assisted parameters: WorkerParameters,
  private val store: CodexStore,
) : CoroutineWorker(context, parameters) {

  override suspend fun doWork(): Result {
    val sync = store.sync ?: return Result.success()
    val rimasti = runCatching { sync.deliverOnce() }.getOrElse {
      Log.w(TAG, "consegna in background non riuscita", it)
      return Result.retry()
    }
    // Se e' rimasto qualcosa non e' un guasto: puo' essere l'altra persona che non ha ancora un
    // account. Si riprova piu' tardi, con l'attesa che raddoppia -- insistere ogni minuto per un
    // messaggio che non puo' partire e' solo batteria buttata.
    return if (rimasti > 0) Result.retry() else Result.success()
  }

  companion object {
    private const val TAG = "DeliveryWorker"
    private const val NAME = "codex-consegna"

    /**
     * Chiede al sistema di consegnare appena c'e' rete.
     *
     * E' un lavoro **unico**: chiamarlo dieci volte di fila mentre si scrive non ne accoda dieci.
     * `KEEP` e non `REPLACE` perche' un lavoro gia' in corso sta gia' facendo esattamente questo,
     * e sostituirlo vorrebbe dire ricominciare da capo l'attesa.
     */
    fun schedule(context: Context) {
      val request = OneTimeWorkRequestBuilder<DeliveryWorker>()
        .setConstraints(
          Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build(),
        )
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build()
      WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
    }
  }
}
