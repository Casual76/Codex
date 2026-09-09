package dev.pampa.codex.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pampa.codex.R
import dev.pampa.codex.data.groups.GroupRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Entrare in un gruppo con un invito.
 *
 * **L'invito non apre niente da solo.** Dentro c'e' l'indirizzo del gruppo, un gettone e il Codex ID
 * di chi lo ha dato; presentarlo significa bussare a quella persona, che consegnera' la chiave dal
 * suo telefono. Se non apre Codex, non entra nessuno -- e non e' un difetto: una chiave la da' una
 * persona, non un pezzo di carta.
 */
@HiltViewModel
class JoinGroupViewModel @Inject constructor(
  private val groups: GroupRepository,
  private val inbox: GroupInviteInbox,
) : ViewModel() {

  data class State(
    val link: String = "",
    val asking: Boolean = false,
    /** Una frase gia' pronta: com'e' andata, o cosa manca. */
    val message: Int? = null,
    val done: Boolean = false,
  )

  private val _state = MutableStateFlow(State())
  val state: StateFlow<State> = _state.asStateFlow()

  init {
    // Se si arriva qui toccando un link, il campo e' gia' pieno: ricopiarlo a mano sarebbe un
    // passaggio inventato da noi.
    inbox.pending.value?.let { link ->
      _state.update { it.copy(link = link) }
      inbox.clear()
    }
  }

  fun setLink(link: String) = _state.update { it.copy(link = link, message = null) }

  fun join() {
    val invito = parse(_state.value.link)
    if (invito == null) {
      _state.update { it.copy(message = R.string.group_join_bad) }
      return
    }
    _state.update { it.copy(asking = true, message = null) }
    viewModelScope.launch {
      groups.requestJoin(invito.groupId, invito.token, invito.inviter).fold(
        onSuccess = {
          _state.update { it.copy(asking = false, message = R.string.group_join_sent, done = true) }
        },
        onFailure = { errore ->
          // Due motivi diversi, due frasi diverse: chi non ha un account non ha sbagliato niente,
          // gli manca un pezzo. Dirgli "non ha funzionato" lo manderebbe a cercare un guasto.
          val frase = when {
            errore.message?.contains("account") == true -> R.string.group_join_needs_account
            else -> R.string.group_join_unknown
          }
          _state.update { it.copy(asking = false, message = frase) }
        },
      )
    }
  }

  fun clearMessage() = _state.update { it.copy(message = null) }

  private data class Invito(val groupId: String, val token: String, val inviter: String)

  /** `codex://gruppo/{id}/{gettone}/{chi invita}` — e niente altro. */
  private fun parse(link: String): Invito? {
    val pulito = link.trim()
    if (!pulito.startsWith(PREFIX)) return null
    val parti = pulito.removePrefix(PREFIX).split("/")
    if (parti.size != 3 || parti.any { it.isBlank() }) return null
    return Invito(parti[0], parti[1], parti[2])
  }

  private companion object {
    const val PREFIX = "codex://gruppo/"
  }
}
