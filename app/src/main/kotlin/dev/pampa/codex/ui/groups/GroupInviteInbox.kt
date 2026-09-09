package dev.pampa.codex.ui.groups

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * L'invito a un gruppo in attesa di essere presentato.
 *
 * Come per i quadri, e' una **casella e non un evento**: un invito puo' arrivare toccando un link
 * mentre l'app e' bloccata, e in quel caso deve restare li' ad aspettare che qualcuno la sblocchi.
 * Un evento consumato a schermo spento sarebbe un invito perso senza che nessuno lo sappia.
 */
@Singleton
class GroupInviteInbox @Inject constructor() {

  private val _pending = MutableStateFlow<String?>(null)

  val pending: StateFlow<String?> = _pending.asStateFlow()

  fun offer(link: String) {
    _pending.value = link
  }

  fun clear() {
    _pending.value = null
  }
}

/**
 * La casella, come la vede la radice dell'app.
 *
 * Esiste per una ragione noiosa e legittima: un oggetto iniettato non si prende dentro una
 * composizione senza passare da un ViewModel. Non fa altro che affacciare la casella.
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class GroupInviteViewModel @Inject constructor(
  inbox: GroupInviteInbox,
) : androidx.lifecycle.ViewModel() {
  val pending: StateFlow<String?> = inbox.pending
}
