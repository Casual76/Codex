package dev.pampa.codex.ui.account

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** Cosa e' successo chiedendo un accesso al sistema. Tre casi, e vanno detti in modo diverso. */
sealed interface GoogleIdOutcome {
  data class Token(val idToken: String) : GoogleIdOutcome

  /** L'utente ha chiuso il selettore. Non e' un errore e non va mostrato come tale. */
  data object Cancelled : GoogleIdOutcome

  /** Nessun account Google sul dispositivo, o i servizi non ci sono. */
  data object NoAccount : GoogleIdOutcome

  data class Failed(val message: String) : GoogleIdOutcome
}

/**
 * Chiede al **sistema** un'identita' Google, e ne restituisce il token.
 *
 * Passa da Credential Manager e non dal vecchio `GoogleSignInClient`: il selettore degli account lo
 * disegna Android, la scelta la fa la persona, e quello che torna all'app e' un'asserzione firmata
 * da Google. **Codex non vede mai una password**, e non ha modo di vederla nemmeno volendo -- che e'
 * esattamente la proprieta' che si vuole in un'app costruita intorno al non fidarsi di nessuno.
 *
 * Il `serverClientId` e' il client web che il plugin `google-services` scrive in
 * `R.string.default_web_client_id` leggendo `google-services.json`. Non e' un segreto: identifica il
 * progetto a cui il token e' destinato, ed e' cio' che impedisce di riusare altrove un token
 * ottenuto per Codex.
 */
suspend fun requestGoogleId(context: Context, serverClientId: String): GoogleIdOutcome {
  if (serverClientId.isBlank()) {
    return GoogleIdOutcome.Failed("progetto Firebase non configurato")
  }
  val request = GetCredentialRequest.Builder()
    .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
    .build()
  return try {
    val response = CredentialManager.create(context).getCredential(context, request)
    val credential = response.credential
    if (
      credential is CustomCredential &&
      credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
      GoogleIdOutcome.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
    } else {
      // Il sistema ha risposto con qualcosa che non e' un'identita' Google: non si tira a indovinare.
      GoogleIdOutcome.Failed("credenziale di tipo inatteso")
    }
  } catch (error: GetCredentialCancellationException) {
    GoogleIdOutcome.Cancelled
  } catch (error: NoCredentialException) {
    GoogleIdOutcome.NoAccount
  } catch (error: GetCredentialException) {
    GoogleIdOutcome.Failed(error.message ?: error::class.simpleName.orEmpty())
  }
}
