package dev.pampa.codex.ui.contacts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pampa.codex.crypto.ChatSecret
import dev.pampa.codex.data.contacts.Contact
import dev.pampa.codex.data.contacts.ContactRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RitualState(
  val contact: Contact? = null,
  val word: String = "",
  val busy: Boolean = false,
  /** Il seme della gemma nata dal rito: c'e' solo dopo, ed e' tutta la verifica che serve. */
  val emblemSeed: Long? = null,
  val chatId: String? = null,
  val failed: Boolean = false,
) {
  val canProceed: Boolean get() = !busy && ChatSecret.isAcceptable(word)

  /** Il rito e' compiuto e si sta guardando la gemma. */
  val showingGem: Boolean get() = emblemSeed != null
}

/**
 * Il rito della parola d'ordine.
 *
 * Non c'e' niente da "verificare" qui dentro, e non e' una mancanza: l'app non puo' sapere se le
 * due persone hanno detto la stessa parola, perche' la parola non le e' mai stata data da nessun
 * canale che possa confrontarle. Quello che l'app puo' fare e' **mostrare la gemma** che nasce da
 * quella parola, e lasciare che siano loro a guardare i due schermi. Se sono uguali, hanno finito.
 */
@HiltViewModel
class RitualViewModel @Inject constructor(
  private val contactRepository: ContactRepository,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {

  private val codexId: String = savedStateHandle.get<String>("codexId").orEmpty()

  private val _state = MutableStateFlow(RitualState())
  val state: StateFlow<RitualState> = _state.asStateFlow()

  init {
    viewModelScope.launch {
      _state.update { it.copy(contact = contactRepository.byCodexId(codexId)) }
    }
  }

  fun setWord(word: String) {
    _state.update { it.copy(word = word, failed = false) }
  }

  fun perform() {
    val current = _state.value
    if (!current.canProceed) return
    _state.update { it.copy(busy = true, failed = false) }
    viewModelScope.launch {
      val result = contactRepository.openConversation(codexId, current.word)
      _state.update { state ->
        result.fold(
          onSuccess = {
            state.copy(busy = false, emblemSeed = it.emblemSeed, chatId = it.chatId)
          },
          onFailure = { state.copy(busy = false, failed = true) },
        )
      }
    }
  }

  /**
   * "No, le gemme sono diverse": si torna alla parola.
   *
   * La conversazione **resta** e la chiave sbagliata resta al suo posto finche' non ne arriva una
   * giusta. Cancellarla qui sarebbe la cosa comoda da programmare e la cosa sbagliata da usare: se
   * nel frattempo era arrivato un messaggio, quel messaggio deve restare li' ad aspettare la parola
   * buona, non sparire perche' due persone si sono capite male una volta.
   */
  fun tryAgain() {
    _state.update { it.copy(emblemSeed = null, chatId = null, word = "") }
  }
}
