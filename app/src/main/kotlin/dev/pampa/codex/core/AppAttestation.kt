package dev.pampa.codex.core

import android.util.Log
import com.google.firebase.appcheck.FirebaseAppCheck

/**
 * Dice al server che dall'altra parte c'e' **questa** app, e non un `curl`.
 *
 * Le regole di Firestore sanno chi sei (`request.auth`), non da dove stai chiamando: con il solo
 * accesso, chiunque abbia un token Google valido puo' parlare al database di Codex da uno script.
 * App Check aggiunge la seconda domanda -- questa app, su un dispositivo vero, non manomessa --
 * e la risposta la firma Google, non noi.
 *
 * **Non e' crittografia e non protegge i messaggi**: quelli sono gia' illeggibili al server. Serve
 * a tenere fuori chi vorrebbe frugare nella rubrica o riempire la posta di qualcuno; e' una
 * questione di conto da pagare e di rumore, non di segreti.
 *
 * Il fornitore cambia con la build ([attestationProvider], una versione per `debug` e una per
 * `release`) perche' Play Integrity su un emulatore non ha niente da attestare: in sviluppo si usa
 * il gettone di debug, che va registrato una volta nella console.
 *
 * Fallire qui **non ferma l'app**: senza gettone il server risponde di no alle chiamate protette,
 * e tutto quello che vive su questo telefono -- identita', conversazioni, sigilli gia' arrivati --
 * continua a funzionare come sempre.
 */
object AppAttestation {

  fun install() {
    runCatching {
      FirebaseAppCheck.getInstance().installAppCheckProviderFactory(attestationProvider())
    }.onFailure { Log.w(TAG, "App Check non installato", it) }
  }

  private const val TAG = "CodexAppCheck"
}
