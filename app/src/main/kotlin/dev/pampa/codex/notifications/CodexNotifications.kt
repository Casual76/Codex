package dev.pampa.codex.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.pampa.codex.MainActivity
import dev.pampa.codex.R

/**
 * Le notifiche di Codex, e il patto che rispettano.
 *
 * **Una notifica non dice mai cosa c'e' scritto.** Non e' una limitazione tecnica da aggirare piu'
 * avanti: e' il punto dell'app. Un messaggio arriva sigillato e si apre con un gesto; una notifica
 * che ne mostrasse l'anteprima lo avrebbe gia' aperto sulla schermata di blocco, davanti a chiunque
 * guardi il telefono appoggiato sul tavolo.
 *
 * Quello che dice e' il minimo utile: **da chi**, e che e' arrivato un sigillo. Nemmeno la forma del
 * sigillo, perche' quella nasce dalla chiave della conversazione e a telefono bloccato non si puo'
 * calcolare -- e dirla solo qualche volta sarebbe peggio che non dirla mai.
 */
object CodexNotifications {

  private const val CHANNEL_MESSAGES = "codex_messages"

  /** Un identificatore per conversazione: due messaggi della stessa persona non si accatastano. */
  private fun notificationId(chatId: String): Int = chatId.hashCode()

  fun ensureChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(CHANNEL_MESSAGES) != null) return
    manager.createNotificationChannel(
      NotificationChannel(
        CHANNEL_MESSAGES,
        context.getString(R.string.notification_channel_messages),
        NotificationManager.IMPORTANCE_HIGH,
      ).apply {
        description = context.getString(R.string.notification_channel_messages_detail)
        // L'anteprima sulla schermata di blocco la disegna il sistema: gli si dice di non farlo.
        lockscreenVisibility = androidx.core.app.NotificationCompat.VISIBILITY_PRIVATE
      },
    )
  }

  /**
   * Annuncia che e' arrivato un sigillo.
   *
   * Se il permesso non c'e' non succede niente e non e' un errore: su Android 13 e oltre le
   * notifiche si chiedono, e chi ha detto di no ha detto di no.
   */
  fun sealArrived(context: Context, chatId: String, from: String) {
    if (
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
      PackageManager.PERMISSION_GRANTED
    ) {
      return
    }
    ensureChannel(context)

    val open = PendingIntent.getActivity(
      context,
      notificationId(chatId),
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_CHAT_ID, chatId)
      },
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
      .setSmallIcon(R.drawable.ic_launcher_monochrome)
      .setContentTitle(from)
      .setContentText(context.getString(R.string.notification_seal_arrived))
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setCategory(NotificationCompat.CATEGORY_MESSAGE)
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      .setAutoCancel(true)
      .setContentIntent(open)
      .build()

    NotificationManagerCompat.from(context).notify(notificationId(chatId), notification)
  }

  /** Quando si apre una conversazione, quello che era rimasto appeso per lei se ne va. */
  fun clear(context: Context, chatId: String) {
    NotificationManagerCompat.from(context).cancel(notificationId(chatId))
  }

  const val EXTRA_CHAT_ID = "codex.chatId"
}
