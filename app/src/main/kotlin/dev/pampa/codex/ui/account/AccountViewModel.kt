package dev.pampa.codex.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pampa.codex.data.cloud.AccountState
import dev.pampa.codex.data.cloud.CloudAccount
import dev.pampa.codex.data.cloud.DeviceRegistry
import dev.pampa.codex.data.contacts.ContactRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Cosa dire sotto la riga dell'account. `null` quando non c'e' niente da dire. */
enum class AccountMessage { Cancelled, NoAccount, Failed, Published, NotPublished, Deleted, DeleteFailed }

data class AccountUiState(
  val account: AccountState = AccountState.Unknown,
  val busy: Boolean = false,
  val message: AccountMessage? = null,
) {
  val signedIn: Boolean get() = account is AccountState.SignedIn
}

/**
 * L'account, dalla parte dell'interfaccia.
 *
 * **Cosa fa l'accesso, e cosa non fa.** Collega Codex a un account Google perche' il server sappia
 * a chi consegnare, e perche' il proprio Codex ID diventi cercabile. Non fa entrare nell'identita':
 * quella sta nel vault e si apre con il segreto, non con Google. Uscire dall'account non cancella
 * niente di locale, e infatti dopo si continua a leggere tutto.
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
  private val account: CloudAccount,
  private val contacts: ContactRepository,
  private val devices: DeviceRegistry,
) : ViewModel() {

  private val _state = MutableStateFlow(AccountUiState())
  val state: StateFlow<AccountUiState> = _state.asStateFlow()

  val accountState: StateFlow<AccountState> = account.state
    .stateIn(viewModelScope, SharingStarted.Eagerly, AccountState.Unknown)

  init {
    viewModelScope.launch {
      account.state.collect { current -> _state.update { it.copy(account = current) } }
    }
  }

  /**
   * Prende il token che il sistema ha rilasciato e lo porta a Firebase.
   *
   * Subito dopo pubblica la propria scheda: e' il momento giusto, perche' un account senza scheda
   * nella rubrica e' un account che non serve a niente -- nessuno potrebbe cercarti.
   */
  fun signIn(outcome: GoogleIdOutcome) {
    when (outcome) {
      GoogleIdOutcome.Cancelled -> {
        _state.update { it.copy(busy = false, message = AccountMessage.Cancelled) }
        return
      }
      GoogleIdOutcome.NoAccount -> {
        _state.update { it.copy(busy = false, message = AccountMessage.NoAccount) }
        return
      }
      is GoogleIdOutcome.Failed -> {
        _state.update { it.copy(busy = false, message = AccountMessage.Failed) }
        return
      }
      is GoogleIdOutcome.Token -> Unit
    }
    _state.update { it.copy(busy = true, message = null) }
    viewModelScope.launch {
      account.signInWithGoogle((outcome as GoogleIdOutcome.Token).idToken).fold(
        onSuccess = { uid ->
          val published = contacts.publishMyCard(uid).isSuccess
          // Il gettone delle notifiche si registra qui e non prima: senza account non c'e' un posto
          // dove scriverlo, e senza scheda pubblicata non ci sarebbe comunque niente da consegnare.
          devices.register()
          _state.update {
            it.copy(
              busy = false,
              message = if (published) AccountMessage.Published else AccountMessage.NotPublished,
            )
          }
        },
        onFailure = { _state.update { it.copy(busy = false, message = AccountMessage.Failed) } },
      )
    }
  }

  fun startingSignIn() {
    _state.update { it.copy(busy = true, message = null) }
  }

  /**
   * Esce dall'account.
   *
   * **Uscire e' la richiesta; togliere il gettone e' cortesia.** Si prova prima a toglierlo -- dopo
   * non ci sarebbero piu' i permessi per scrivere -- ma se non riesce si esce lo stesso: un tasto
   * "Disconnetti" che non disconnette perche' il server non risponde e' peggio di un gettone
   * dimenticato, e quel gettone lo toglie da solo `onMessageCreated` alla prima notifica che non
   * arriva a destinazione.
   */
  fun signOut() {
    if (_state.value.busy) return
    _state.update { it.copy(busy = true, message = null) }
    viewModelScope.launch {
      runCatching { devices.unregister() }
      account.signOut()
      _state.update { it.copy(busy = false, message = null) }
    }
  }

  /**
   * Elimina l'account.
   *
   * Non tocca niente su questo telefono, ed e' la cosa piu' importante da dire: identita', contatti
   * e messaggi restano dove sono e restano leggibili. Quello che se ne va e' cio' che il **server**
   * sapeva di questa persona.
   */
  fun deleteAccount() {
    if (_state.value.busy) return
    _state.update { it.copy(busy = true, message = null) }
    viewModelScope.launch {
      runCatching { devices.unregister() }
      val esito = account.deleteAccount()
      _state.update {
        it.copy(
          busy = false,
          message = if (esito.isSuccess) AccountMessage.Deleted else AccountMessage.DeleteFailed,
        )
      }
    }
  }

  fun clearMessage() {
    _state.update { it.copy(message = null) }
  }
}
