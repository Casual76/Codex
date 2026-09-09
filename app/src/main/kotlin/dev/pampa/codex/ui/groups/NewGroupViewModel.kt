package dev.pampa.codex.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pampa.codex.R
import dev.pampa.codex.data.contacts.Contact
import dev.pampa.codex.data.contacts.ContactRepository
import dev.pampa.codex.data.groups.GroupRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Fare un gruppo.
 *
 * **Si sceglie fra i contatti che ci sono gia'**, e non e' una limitazione dell'implementazione: la
 * chiave di un gruppo viaggia dentro una busta chiusa con la chiave di coppia, e una chiave di
 * coppia esiste solo fra due persone che si sono gia' scambiate le schede. Mettere in un gruppo
 * qualcuno che non si conosce vorrebbe dire mandargli un segreto senza sapere a chi.
 */
@HiltViewModel
class NewGroupViewModel @Inject constructor(
  private val groups: GroupRepository,
  contacts: ContactRepository,
) : ViewModel() {

  data class State(
    val name: String = "",
    val chosen: Set<String> = emptySet(),
    val creating: Boolean = false,
    /** Cosa e' andato storto, come frase gia' pronta. `null` se non e' andato storto niente. */
    val problem: Int? = null,
    /** Il gruppo appena creato: la schermata ci entra dentro. */
    val created: String? = null,
  )

  val contacts: StateFlow<List<Contact>> = contacts.observeContacts()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  private val _state = MutableStateFlow(State())
  val state: StateFlow<State> = _state.asStateFlow()

  fun setName(name: String) = _state.update { it.copy(name = name, problem = null) }

  fun toggle(codexId: String) = _state.update { current ->
    val chosen = if (codexId in current.chosen) current.chosen - codexId else current.chosen + codexId
    current.copy(chosen = chosen, problem = null)
  }

  fun create() {
    val current = _state.value
    if (current.creating) return
    if (current.name.isBlank()) {
      _state.update { it.copy(problem = R.string.group_needs_name) }
      return
    }
    if (current.chosen.isEmpty()) {
      _state.update { it.copy(problem = R.string.group_needs_two) }
      return
    }
    _state.update { it.copy(creating = true, problem = null) }
    viewModelScope.launch {
      groups.create(current.name.trim(), current.chosen.toList()).fold(
        onSuccess = { id -> _state.update { it.copy(creating = false, created = id) } },
        // Il gruppo puo' essere nato lo stesso e la consegna della chiave essere fallita: e' il
        // caso in cui chi entra vedra' "in attesa della chiave", non un guasto da mostrare qui.
        onFailure = { _state.update { it.copy(creating = false, problem = R.string.group_needs_two) } },
      )
    }
  }

  fun clearProblem() = _state.update { it.copy(problem = null) }
}
