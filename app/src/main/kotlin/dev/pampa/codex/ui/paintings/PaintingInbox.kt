package dev.pampa.codex.ui.paintings

import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * I quadri in attesa di essere aperti.
 *
 * Ce ne arrivano da due strade -- un intento "apri con Codex" e la scelta manuale di un file -- e
 * tutte e due devono finire nello stesso posto, altrimenti la stessa cosa si comporta in due modi
 * a seconda di come e' cominciata.
 *
 * E' una casella e non un evento: un'immagine puo' arrivare mentre l'app e' bloccata, e in quel
 * caso deve restare li' ad aspettare che qualcuno la sblocchi invece che perdersi.
 */
@Singleton
class PaintingInbox @Inject constructor() {

  private val _pending = MutableStateFlow<Uri?>(null)

  val pending: StateFlow<Uri?> = _pending.asStateFlow()

  fun offer(uri: Uri) {
    _pending.value = uri
  }

  /** Da chiamare quando il quadro e' stato trattato, comunque sia andata. */
  fun clear() {
    _pending.value = null
  }
}
