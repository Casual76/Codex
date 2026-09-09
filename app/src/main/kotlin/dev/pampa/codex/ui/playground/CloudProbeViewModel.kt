package dev.pampa.codex.ui.playground

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pampa.codex.data.cloud.AccountState
import dev.pampa.codex.data.cloud.CloudAccount
import dev.pampa.codex.data.cloud.CloudSettings
import dev.pampa.codex.data.cloud.FirebaseAccount
import dev.pampa.codex.data.contacts.AddContactResult
import dev.pampa.codex.data.contacts.ContactRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CloudProbeState(
  val emulator: String = "",
  val account: String = "…",
  val log: String = "",
  val busy: Boolean = false,
)

/**
 * Il banco di prova del livello cloud. Solo build di lavoro.
 *
 * Esiste perche' il codice che parla con un server e' quello che un test di unita' non tocca: le
 * finte provano la **logica** (cosa succede se non c'e', se non torna, se manca l'accesso), non
 * che le chiamate vere funzionino. Queste tre prove -- entra, pubblica, cerca -- si fanno contro
 * l'emulatore di Firebase e chiudono quel buco senza toccare il progetto vero.
 */
@HiltViewModel
class CloudProbeViewModel @Inject constructor(
  private val account: CloudAccount,
  private val contacts: ContactRepository,
  private val settings: CloudSettings,
) : ViewModel() {

  private val _state = MutableStateFlow(CloudProbeState(emulator = settings.emulatorHost))
  val state: StateFlow<CloudProbeState> = _state.asStateFlow()

  init {
    viewModelScope.launch {
      account.state.collect { current ->
        _state.update {
          it.copy(
            account = when (current) {
              AccountState.Unknown -> "in attesa"
              AccountState.SignedOut -> "nessun accesso"
              is AccountState.SignedIn -> "uid ${current.uid}"
            },
          )
        }
      }
    }
  }

  fun signIn() = run("accesso") {
    val firebase = account as? FirebaseAccount ?: return@run "nessun Firebase su questo dispositivo"
    firebase.signInAnonymouslyForTesting(settings).fold(
      onSuccess = { "entrato come $it" },
      onFailure = { "rifiutato: ${it.message}" },
    )
  }

  fun publish() = run("pubblicazione") {
    val uid = account.uidOrNull() ?: return@run "serve prima l'accesso"
    contacts.publishMyCard(uid).fold(
      onSuccess = { "scheda pubblicata" },
      onFailure = { "non pubblicata: ${it.message}" },
    )
  }

  fun lookup(codexId: String) = run("ricerca") {
    when (val esito = contacts.add(codexId)) {
      is AddContactResult.Added -> "trovato e aggiunto: ${esito.contact.name}"
      is AddContactResult.AlreadyKnown -> "gia' in rubrica: ${esito.contact.name}"
      AddContactResult.Myself -> "sei tu"
      AddContactResult.NotFound -> "nessuno con quel Codex ID"
      AddContactResult.NotACard -> "non e' ne' un Codex ID ne' una scheda"
      AddContactResult.Unreachable -> "server irraggiungibile"
      AddContactResult.NeedsAccount -> "serve l'accesso"
      AddContactResult.Locked -> "app bloccata"
    }
  }

  private fun run(what: String, block: suspend () -> String) {
    if (_state.value.busy) return
    _state.update { it.copy(busy = true) }
    viewModelScope.launch {
      val outcome = runCatching { block() }.getOrElse { "$what: eccezione ${it.message}" }
      _state.update { it.copy(busy = false, log = "$what → $outcome") }
    }
  }
}
