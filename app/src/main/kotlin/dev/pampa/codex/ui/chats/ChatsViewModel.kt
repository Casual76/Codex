package dev.pampa.codex.ui.chats

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pampa.codex.data.chat.ChatRepository
import dev.pampa.codex.data.db.ChatEntity
import dev.pampa.codex.ui.paintings.PaintingInbox
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * La lista delle conversazioni.
 *
 * Si assicura che le note a se stessi esistano: e' la prima chat di chiunque, e non richiede
 * nessuno dall'altra parte. Il nome arriva dalla schermata perche' e' una stringa tradotta, e i
 * repository non conoscono le risorse.
 */
@HiltViewModel
class ChatsViewModel @Inject constructor(
  private val chatRepository: ChatRepository,
  private val paintingInbox: PaintingInbox,
) : ViewModel() {

  /**
   * `null` finche' non si sa: e' diverso da "vuoto".
   *
   * Con `emptyList()` come valore di partenza, ogni apertura mostrava per un istante il cartello
   * "non c'e' niente" prima che arrivasse la prima emissione. Su un telefono e' un lampo; sui due
   * pannelli del tablet, dove la lista resta in scena, si legge benissimo. Il cartello adesso
   * compare solo quando la risposta e' arrivata **ed e' vuota**.
   */
  val chats: StateFlow<List<ChatEntity>?> = chatRepository.observeChats()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  fun ensureSelfChat(title: String) {
    viewModelScope.launch { runCatching { chatRepository.ensureSelfChat(title) } }
  }

  /**
   * Un quadro scelto a mano dal telefono.
   *
   * Finisce nella stessa casella degli intenti "apri con Codex": chi lo ha salvato dalle chat di
   * un'altra app e chi lo ha aperto direttamente devono vedere le stesse cose.
   */
  fun openPainting(uri: Uri) = paintingInbox.offer(uri)
}
