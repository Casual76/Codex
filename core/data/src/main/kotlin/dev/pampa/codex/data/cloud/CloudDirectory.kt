package dev.pampa.codex.data.cloud

import dev.pampa.codex.crypto.SignedContactCard

/** Chi sei per il server, se lo sei. */
sealed interface AccountState {
  /** Non si sa ancora: il livello cloud non ha ancora guardato. */
  data object Unknown : AccountState

  /** Nessun accesso: l'app funziona lo stesso, ma solo con quello che ha in casa. */
  data object SignedOut : AccountState

  data class SignedIn(val uid: String) : AccountState
}

/**
 * L'accesso, per chi sta sopra.
 *
 * E' un'interfaccia e non la classe di Firebase per la stessa ragione di
 * [dev.pampa.codex.data.identity.CodexIdentitySource]: sopra questo strato nessuno deve sapere che
 * l'accesso lo fa Google, e sotto si puo' mettere una finta per provare tutto il resto.
 *
 * **Non e' l'identita'.** L'account dice al server chi sei; l'identita' Codex dice a un'altra
 * persona chi sei, e vive nel vault. Le due cose si incontrano in un punto solo: la scheda
 * pubblicata nella rubrica, che l'account trasporta e l'identita' firma.
 */
interface CloudAccount {
  val state: kotlinx.coroutines.flow.StateFlow<AccountState>

  fun uidOrNull(): String?

  /**
   * Entra con un token di identita' rilasciato da Google.
   *
   * Il token arriva dal **sistema**, non dall'app: e' Credential Manager a mostrare il selettore
   * degli account e a firmare l'asserzione. Codex non vede mai una password, e non potrebbe --
   * ed e' il motivo per cui questa firma prende un token e non delle credenziali.
   */
  suspend fun signInWithGoogle(idToken: String): Result<String>

  suspend fun signOut()

  /**
   * Cancella l'account, e con lui quello che il server sa di questa persona.
   *
   * **Non e' scollegarsi.** Scollegarsi lascia tutto dov'e' e smette di parlare; questo toglie il
   * profilo pubblico, la voce nella rubrica, i dispositivi registrati, la posta e le storie. Quello
   * che resta -- le buste gia' consegnate nelle conversazioni -- scade da solo dopo trenta giorni:
   * cancellarle vorrebbe dire entrare nelle conversazioni di altre persone, che e' proprio la cosa
   * che a Codex non e' permessa.
   *
   * Su questo telefono non tocca niente: identita', contatti e messaggi restano, e restano
   * leggibili. Chi vuole cancellare anche quelli ha "Cancella tutto", che e' un altro gesto.
   */
  suspend fun deleteAccount(): Result<Unit>
}

/** Cosa e' venuto fuori cercando un Codex ID. Ogni caso ha una frase sua a schermo. */
sealed interface DirectoryLookup {
  data class Found(val card: SignedContactCard) : DirectoryLookup

  /** Nessuno con quel Codex ID, o non ha ancora collegato un account. */
  data object NotFound : DirectoryLookup

  /** C'era qualcosa, ma la scheda non regge la verifica: si butta e si dice che non c'e'. */
  data object Untrusted : DirectoryLookup

  /** Rete assente o server irraggiungibile. */
  data object Unreachable : DirectoryLookup

  /** Senza accesso non si cerca: le regole non lasciano leggere niente a chi non ha fatto login. */
  data object SignedOut : DirectoryLookup
}

/**
 * La rubrica pubblica: dire al mondo il proprio Codex ID, e cercare quello di qualcun altro.
 *
 * **Il server non e' creduto sulla parola.** Quello che restituisce e' una scheda **firmata**, e
 * chi la riceve controlla firma e Codex ID prima di farne qualcosa: un server ostile puo' non
 * rispondere o rispondere "non c'e'", ma non puo' consegnare la scheda sbagliata sotto il Codex ID
 * giusto, perche' quell'identificatore nasce dalla chiave che firma. E' il motivo per cui questa
 * rubrica puo' esistere senza indebolire niente.
 *
 * Quello che il server puo' fare -- e va detto -- e' sapere **chi cerca chi**. E' il prezzo di
 * poter aggiungere qualcuno che sta lontano, e per chi non lo vuole pagare restano il QR e il
 * codice incollato, che non passano da qui.
 */
interface CloudDirectory {

  /** Mette (o aggiorna) la propria scheda nella rubrica. Da rifare quando il profilo cambia. */
  suspend fun publish(card: SignedContactCard): Result<Unit>

  suspend fun lookup(codexId: String): DirectoryLookup
}

/** Una rubrica che non c'e': l'app compila e funziona lo stesso, offline. */
object NoDirectory : CloudDirectory {
  override suspend fun publish(card: SignedContactCard): Result<Unit> =
    Result.failure(IllegalStateException("nessuna rubrica"))

  override suspend fun lookup(codexId: String): DirectoryLookup = DirectoryLookup.SignedOut
}
