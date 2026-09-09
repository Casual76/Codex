package dev.pampa.codex.notifications

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import dev.pampa.codex.R
import dev.pampa.codex.data.chat.ChatRepository
import dev.pampa.codex.data.cloud.DeviceRegistry
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Quello che arriva quando Codex e' chiusa.
 *
 * **La spinta non porta niente**: solo `chatId` e `messageId`. Il messaggio vero arriva dal normale
 * ascolto della conversazione appena l'app si riapre, e questo servizio si limita a bussare. Non e'
 * una scorciatoia: a telefono bloccato l'identita' e' chiusa, quindi qui **non c'e' modo** di aprire
 * una busta nemmeno volendo -- ed e' esattamente la proprieta' che si vuole.
 *
 * L'unica cosa che legge dal database e' il **nome** della conversazione, che non e' un segreto e
 * sta in chiaro nella tabella delle chat: serve a poter dire "Marco" invece di "qualcuno".
 *
 * E' un componente Android e vive nell'app, quindi e' l'unico punto sopra `:core:data` che conosce
 * un tipo di Firebase. Non fa logica: la logica sta di sotto.
 */
@AndroidEntryPoint
class CodexMessagingService : FirebaseMessagingService() {

  @Inject lateinit var chats: ChatRepository

  @Inject lateinit var devices: DeviceRegistry

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  /**
   * Il gettone e' cambiato: si riscrive dove sta.
   *
   * Succede da solo ogni tanto, e sempre dopo una reinstallazione. Senza questa riga le notifiche
   * smetterebbero di arrivare mesi dopo, senza che niente lo dica.
   */
  override fun onNewToken(token: String) {
    scope.launch { devices.register() }
  }

  override fun onMessageReceived(message: RemoteMessage) {
    val chatId = message.data["chatId"] ?: return
    if (message.data["kind"] != "message") return

    // Se l'app e' davanti agli occhi, il messaggio sta gia' comparendo nella conversazione:
    // annunciarlo sarebbe rumore addosso a una cosa che si sta gia' vedendo.
    if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
      return
    }

    scope.launch {
      val chat = chats.chatById(chatId)
      // Una conversazione silenziata resta silenziata anche quando l'app e' chiusa: il filtro e'
      // qui e non sul server, perche' il server non deve sapere quali chat una persona silenzia.
      if (chat?.muted == true) return@launch
      // Una chat che in locale **non c'e' ancora** capita davvero: l'altra persona ha fatto il rito
      // per prima e ha scritto subito. Prima buttavo via la notifica, e chi riceveva non sapeva di
      // aver ricevuto. Meglio dire "qualcuno" che non dire niente.
      CodexNotifications.sealArrived(
        context = applicationContext,
        chatId = chatId,
        from = chat?.title?.takeIf { it.isNotBlank() }
          ?: getString(R.string.notification_from_unknown),
      )
    }
  }
}
