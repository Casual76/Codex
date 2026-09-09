package dev.pampa.codex.ui.me

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pampa.codex.data.nearby.NearbyMode
import dev.pampa.codex.data.prefs.CodexPreferences
import dev.pampa.codex.nearby.NearbyService
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * L'interruttore delle vicinanze.
 *
 * Fa due cose e sono separate apposta: scrive la scelta, e accende o spegne il **servizio**. Il
 * motore delle vicinanze guarda da solo la scelta e non ha bisogno di essere avvisato; il servizio
 * invece serve solo a "sempre", ed e' l'unica parte che il sistema deve vedere.
 */
@HiltViewModel
class NearbyViewModel @Inject constructor(
  @ApplicationContext private val context: Context,
  private val preferences: CodexPreferences,
) : ViewModel() {

  val mode: StateFlow<NearbyMode> = preferences.nearbyMode
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NearbyMode.OFF)

  fun setMode(mode: NearbyMode) {
    viewModelScope.launch {
      preferences.setNearbyMode(mode)
      if (mode == NearbyMode.ALWAYS) {
        NearbyService.start(context)
      } else {
        NearbyService.stop(context)
      }
    }
  }
}
