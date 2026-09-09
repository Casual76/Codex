package dev.pampa.codex.ui.stories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pampa.codex.data.stories.Story
import dev.pampa.codex.data.stories.StoryRepository
import dev.pampa.codex.model.SealChoice
import dev.pampa.codex.model.Technique
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Cosa si sta facendo nella scheda delle storie. */
data class StoriesState(
  val composing: Boolean = false,
  val draft: String = "",
  val technique: Technique? = null,
  /** La storia aperta a tutto schermo. */
  val viewing: Story? = null,
)

/**
 * Il sigillo del giorno.
 *
 * Le storie hanno una regola che i messaggi non hanno -- **durano un giorno** -- e la scheda deve
 * poterlo mostrare senza aspettare che qualcuno la ricarichi. Per questo la pulizia si fa
 * all'apertura e non a scadenza: un timer che gira per far sparire una riga costerebbe batteria
 * per un effetto che si vede comunque solo guardando.
 */
@HiltViewModel
class StoriesViewModel @Inject constructor(
  private val repository: StoryRepository,
) : ViewModel() {

  /**
   * `null` finche' non si sa: e' diverso da "vuoto".
   *
   * Con `emptyList()` come valore di partenza, ogni apertura mostrava per un istante il cartello
   * "non c'e' niente" prima che arrivasse la prima emissione. Su un telefono e' un lampo; sui due
   * pannelli del tablet, dove la lista resta in scena, si legge benissimo. Il cartello adesso
   * compare solo quando la risposta e' arrivata **ed e' vuota**.
   */
  val stories: StateFlow<List<Story>?> = repository.observe()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  private val _state = MutableStateFlow(StoriesState())
  val state: StateFlow<StoriesState> = _state.asStateFlow()

  /**
   * Quante persone hanno visto la storia aperta adesso.
   *
   * Solo per le proprie: per quelle degli altri non e' una domanda che ci riguarda, e infatti non
   * si fa. Il numero e non i nomi -- i nomi sarebbero una lista di chi c'era, e per una storia che
   * dura un giorno non serve a nessuno.
   */
  val viewers: StateFlow<Int> = _state
    .map { it.viewing }
    .distinctUntilChanged()
    .flatMapLatest { story ->
      if (story?.mine == true) repository.observeViewers(story.id).map { it.size } else flowOf(0)
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

  init {
    viewModelScope.launch { runCatching { repository.sweep() } }
  }

  fun startComposing() = _state.update { it.copy(composing = true, draft = "") }

  fun cancelComposing() = _state.update { it.copy(composing = false, draft = "") }

  fun setDraft(text: String) = _state.update { it.copy(draft = text) }

  fun setTechnique(technique: Technique?) = _state.update { it.copy(technique = technique) }

  fun post() {
    val current = _state.value
    if (current.draft.isBlank()) return
    _state.update { it.copy(composing = false, draft = "") }
    viewModelScope.launch {
      repository.post(current.draft, SealChoice(technique = current.technique))
    }
  }

  fun open(story: Story) {
    _state.update { it.copy(viewing = story) }
    viewModelScope.launch { repository.markSeen(story.id) }
  }

  fun close() = _state.update { it.copy(viewing = null) }

  fun deleteViewed() {
    val story = _state.value.viewing ?: return
    _state.update { it.copy(viewing = null) }
    viewModelScope.launch { repository.delete(story.id) }
  }
}
