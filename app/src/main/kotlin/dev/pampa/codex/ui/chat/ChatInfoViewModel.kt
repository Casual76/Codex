package dev.pampa.codex.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pampa.codex.data.chat.ChatRepository
import dev.pampa.codex.data.db.ChatEntity
import dev.pampa.codex.data.db.ChatKind
import dev.pampa.codex.data.db.GroupMemberEntity
import dev.pampa.codex.data.groups.GroupRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Le impostazioni di una conversazione: la scadenza, lo svuotamento, e -- se e' un gruppo -- chi c'e'. */
@HiltViewModel
class ChatInfoViewModel @Inject constructor(
  private val chatRepository: ChatRepository,
  private val groups: GroupRepository,
  private val identityRepository: dev.pampa.codex.data.identity.IdentityRepository,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {

  private val chatId: String = checkNotNull(savedStateHandle["chatId"])

  val chat: StateFlow<ChatEntity?> = chatRepository.observeChat(chatId)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  /** Chi c'e' nel gruppo. Per una chat a due resta vuota, e la schermata non mostra la sezione. */
  val members: StateFlow<List<GroupMemberEntity>> = groups.observeMembers(chatId)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  /** L'invito appena creato, da copiare. Vive solo qui: sul server ne va solo l'impronta. */
  private val _invite = MutableStateFlow<String?>(null)
  val invite: StateFlow<String?> = _invite.asStateFlow()

  val isGroup: Boolean get() = chat.value?.kind == ChatKind.GROUP

  /**
   * Il proprio Codex ID.
   *
   * Serve alla lista dei membri per due cose: non offrirsi di togliere se stessi, e capire se si
   * comanda. E' pubblico -- sta nella scheda che si fa inquadrare -- quindi non c'e' niente da
   * proteggere nel tenerlo qui.
   */
  val myCodexId: String = identityRepository.identityOrNull()?.codexId.orEmpty()

  fun setTtl(seconds: Int) {
    viewModelScope.launch { runCatching { chatRepository.setTtl(chatId, seconds) } }
  }

  fun setLocked(locked: Boolean) {
    viewModelScope.launch { runCatching { chatRepository.setLocked(chatId, locked) } }
  }

  fun clear() {
    viewModelScope.launch { runCatching { chatRepository.clearChat(chatId) } }
  }

  fun createInvite() {
    viewModelScope.launch {
      groups.invite(chatId).onSuccess { invito -> _invite.value = invito.link }
    }
  }

  fun clearInvite() {
    _invite.value = null
  }

  fun leave(onDone: () -> Unit) {
    viewModelScope.launch {
      runCatching { groups.leave(chatId) }
      onDone()
    }
  }

  fun remove(codexId: String) {
    viewModelScope.launch { runCatching { groups.remove(chatId, codexId) } }
  }

  fun resendKey(codexId: String) {
    viewModelScope.launch { runCatching { groups.resendKey(chatId, codexId) } }
  }
}
