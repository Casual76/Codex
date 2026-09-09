package dev.pampa.codex.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pampa.codex.data.cloud.CodexDevice
import dev.pampa.codex.data.cloud.DeviceRegistry
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** L'elenco dei dispositivi collegati, e il modo di toglierne uno. */
@HiltViewModel
class DevicesViewModel @Inject constructor(
  private val registry: DeviceRegistry,
) : ViewModel() {

  val devices: StateFlow<List<CodexDevice>> = registry.observe()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  private val _currentId = MutableStateFlow("")

  /** Quale di quelli in elenco e' questo telefono: si segna, e non si offre di revocarlo. */
  val currentId: StateFlow<String> = _currentId.asStateFlow()

  init {
    viewModelScope.launch {
      _currentId.value = runCatching { registry.currentDeviceId() }.getOrDefault("")
    }
  }

  fun revoke(deviceId: String) {
    viewModelScope.launch { runCatching { registry.revoke(deviceId) } }
  }
}
