package dev.pampa.codex.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pampa.codex.data.contacts.AddContactResult
import dev.pampa.codex.data.contacts.Contact
import dev.pampa.codex.data.contacts.ContactRepository
import dev.pampa.codex.data.prefs.CodexPreferences
import dev.pampa.codex.data.prefs.CodexProfile
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Cosa e' successo all'ultimo tentativo di aggiungere qualcuno.
 *
 * E' un esito da mostrare una volta, non uno stato in cui restare: la schermata lo legge, lo dice,
 * e chiama [ContactsViewModel.clearOutcome].
 */
sealed interface AddOutcome {
  data class Added(val contact: Contact) : AddOutcome
  data class AlreadyKnown(val contact: Contact) : AddOutcome
  data object Myself : AddOutcome
  data object NotACard : AddOutcome

  /** Un Codex ID cercato e non trovato. */
  data object NotFound : AddOutcome

  /** La ricerca per Codex ID passa da un server: senza rete o senza accesso non si puo' fare. */
  data object Unreachable : AddOutcome
  data object NeedsAccount : AddOutcome
}

/**
 * I contatti: la lista, la propria scheda, e i due gesti che la cambiano.
 */
@HiltViewModel
class ContactsViewModel @Inject constructor(
  private val contactRepository: ContactRepository,
  preferences: CodexPreferences,
) : ViewModel() {

  /**
   * `null` finche' non si sa: e' diverso da "vuoto".
   *
   * Con `emptyList()` come valore di partenza, ogni apertura mostrava per un istante il cartello
   * "non c'e' niente" prima che arrivasse la prima emissione. Su un telefono e' un lampo; sui due
   * pannelli del tablet, dove la lista resta in scena, si legge benissimo. Il cartello adesso
   * compare solo quando la risposta e' arrivata **ed e' vuota**.
   */
  val contacts: StateFlow<List<Contact>?> = contactRepository.observeContacts()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  val profile: StateFlow<CodexProfile> = preferences.profile
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CodexProfile())

  /**
   * Il codice del proprio sigillo, **rifatto quando il profilo cambia**.
   *
   * Nella scheda ci sono il nome e il seme del minerale, quindi cambiare nome in "Io" o rilanciare
   * il proprio minerale rende vecchio il codice: chi lo inquadrasse aggiungerebbe una persona che
   * si chiama come ti chiamavi prima. Calcolarlo una volta sola in `init` era piu' economico e
   * sbagliato. Firmare costa qualche decina di microsecondi e succede quando il profilo cambia,
   * cioe' quasi mai.
   */
  val myCode: StateFlow<String?> = preferences.profile
    .map { contactRepository.myCode() }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  private val _outcome = MutableStateFlow<AddOutcome?>(null)
  val outcome: StateFlow<AddOutcome?> = _outcome.asStateFlow()

  fun add(code: String) {
    if (code.isBlank()) return
    viewModelScope.launch {
      _outcome.value = when (val result = contactRepository.add(code)) {
        is AddContactResult.Added -> AddOutcome.Added(result.contact)
        is AddContactResult.AlreadyKnown -> AddOutcome.AlreadyKnown(result.contact)
        AddContactResult.Myself -> AddOutcome.Myself
        AddContactResult.NotACard -> AddOutcome.NotACard
        AddContactResult.NotFound -> AddOutcome.NotFound
        AddContactResult.Unreachable -> AddOutcome.Unreachable
        AddContactResult.NeedsAccount -> AddOutcome.NeedsAccount
        // A serratura chiusa questa schermata non esiste: se succede, e' una scheda che non entra.
        AddContactResult.Locked -> AddOutcome.NotACard
      }
    }
  }

  fun clearOutcome() {
    _outcome.value = null
  }

  fun delete(codexId: String) {
    viewModelScope.launch { contactRepository.delete(codexId) }
  }
}
