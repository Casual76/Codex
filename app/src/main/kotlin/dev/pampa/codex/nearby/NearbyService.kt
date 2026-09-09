package dev.pampa.codex.nearby

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.AndroidEntryPoint
import dev.pampa.codex.MainActivity
import dev.pampa.codex.R
import dev.pampa.codex.data.nearby.NearbyEngine
import javax.inject.Inject

/**
 * Le vicinanze mentre l'app e' chiusa.
 *
 * Esiste **solo** se si sceglie "sempre" nelle impostazioni, e mentre gira il sistema mostra una
 * notifica che non si toglie. Non e' una scocciatura da nascondere: e' un'antenna accesa a schermo
 * spento, e chi ha il telefono in tasca ha diritto di vederlo scritto da qualche parte.
 *
 * Il servizio non fa il lavoro: lo fa [NearbyEngine], che vive nel processo e sa gia' cosa fare. Il
 * servizio serve a **tenere vivo il processo**, che e' l'unica cosa che senza di lui verrebbe a
 * mancare -- e a dire al sistema che quel processo sta usando un dispositivo collegato, invece di
 * far finta di niente.
 */
@AndroidEntryPoint
class NearbyService : Service() {

  @Inject lateinit var engine: NearbyEngine

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    ensureChannel(this)
    ServiceCompat.startForeground(
      this,
      NOTIFICATION_ID,
      notification(),
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
      } else {
        0
      },
    )
    // Il motore e' gia' partito con l'app e guarda da solo l'impostazione: qui non si accende
    // niente, si tiene solo il processo in piedi.
    return START_STICKY
  }

  private fun notification() = NotificationCompat.Builder(this, CHANNEL)
    .setSmallIcon(R.drawable.ic_launcher_monochrome)
    .setContentTitle(getString(R.string.nearby_service_title))
    .setContentText(getString(R.string.nearby_service_detail))
    .setOngoing(true)
    .setSilent(true)
    .setContentIntent(
      android.app.PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        android.app.PendingIntent.FLAG_IMMUTABLE,
      ),
    )
    .build()

  companion object {
    private const val CHANNEL = "codex_nearby"
    private const val NOTIFICATION_ID = 4201

    fun start(context: Context) {
      val intent = Intent(context, NearbyService::class.java)
      runCatching { context.startForegroundService(intent) }
    }

    fun stop(context: Context) {
      runCatching { context.stopService(Intent(context, NearbyService::class.java)) }
    }

    /**
     * Un canale a parte da quello dei messaggi, e **silenzioso**.
     *
     * Metterla insieme ai messaggi vorrebbe dire che chi silenzia questa silenzia anche quelli.
     */
    private fun ensureChannel(context: Context) {
      val manager = context.getSystemService<NotificationManager>() ?: return
      if (manager.getNotificationChannel(CHANNEL) != null) return
      manager.createNotificationChannel(
        NotificationChannel(
          CHANNEL,
          context.getString(R.string.nearby_channel),
          NotificationManager.IMPORTANCE_LOW,
        ).apply {
          description = context.getString(R.string.nearby_channel_detail)
          setShowBadge(false)
        },
      )
    }
  }
}
