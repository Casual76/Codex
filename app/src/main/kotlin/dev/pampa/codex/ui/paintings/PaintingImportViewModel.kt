package dev.pampa.codex.ui.paintings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pampa.codex.data.CodexStore
import dev.pampa.codex.data.chat.PaintingImport
import dev.pampa.codex.seal.PaintingShare
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Cosa sta succedendo a un quadro arrivato da fuori. */
sealed interface ImportState {
  data object Idle : ImportState
  data object Working : ImportState
  data class Done(val outcome: PaintingImport) : ImportState
}

/**
 * Apre un quadro ricevuto.
 *
 * Legge l'immagine, ne tira fuori quello che c'e' nascosto e lo consegna al repository, che decide
 * se e' un messaggio nuovo, uno che c'era gia', o roba di una conversazione che non abbiamo. Qui
 * non si decide niente: si mostra.
 */
@HiltViewModel
class PaintingImportViewModel @Inject constructor(
  private val inbox: PaintingInbox,
  private val share: PaintingShare,
  private val store: CodexStore,
) : ViewModel() {

  private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
  val state: StateFlow<ImportState> = _state.asStateFlow()

  val pending: StateFlow<Uri?> = inbox.pending

  fun open(uri: Uri) {
    if (_state.value == ImportState.Working) return
    _state.value = ImportState.Working
    viewModelScope.launch {
      val payload = share.readPayload(uri)
      _state.value = ImportState.Done(store.chats.importPainting(payload))
      // La casella si svuota qui e non alla chiusura del pannello: se l'app venisse ricreata con
      // il quadro ancora dentro, lo aprirebbe una seconda volta.
      inbox.clear()
    }
  }

  fun dismiss() {
    _state.value = ImportState.Idle
    inbox.clear()
  }
}
