package dev.pampa.codex.ui.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

/** Cosa puo' fare questo telefono in fatto di impronte e volto. */
enum class BiometricStatus {
  /** C'e' un sensore e almeno un dito registrato: la scorciatoia si puo' usare. */
  AVAILABLE,

  /** Il sensore c'e' ma non e' registrato niente: si puo' mandare l'utente alle impostazioni. */
  NOT_ENROLLED,

  /** Niente sensore, o non utilizzabile: la scorciatoia non si offre nemmeno. */
  UNAVAILABLE,
}

/**
 * Il ponte fra Codex e il riconoscimento biometrico del sistema.
 *
 * Il punto delicato e' il [Cipher]: l'app non chiede "l'impronta e' giusta?" e poi si fida della
 * risposta. Consegna al sistema un cifrario legato a una chiave dell'Android Keystore, e lo
 * riottiene **autorizzato** solo se il riconoscimento e' andato a buon fine. Un'app che saltasse il
 * prompt si ritroverebbe con un cifrario che non decifra niente.
 *
 * Solo `BIOMETRIC_STRONG`: sotto API 30 il sistema non sa legare un cifrario alla credenziale del
 * dispositivo, e accettare una biometria debole per aprire l'identita' sarebbe una promessa che non
 * possiamo mantenere. Il PIN di Codex resta la strada sempre valida.
 */
class BiometricAuthenticator(private val activity: FragmentActivity) {

  fun status(): BiometricStatus =
    when (BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
      BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
      BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NOT_ENROLLED
      else -> BiometricStatus.UNAVAILABLE
    }

  fun isAvailable(): Boolean = status() == BiometricStatus.AVAILABLE

  /**
   * Mostra il prompt di sistema. [onSuccess] riceve il cifrario autorizzato; [onError] riceve un
   * messaggio da mostrare, oppure `null` quando e' stato l'utente ad annullare (che non e' un
   * errore e non va scritto da nessuna parte).
   */
  fun authenticate(
    cipher: Cipher,
    title: String,
    subtitle: String,
    negativeButton: String,
    onSuccess: (Cipher) -> Unit,
    onError: (String?) -> Unit,
  ) {
    val prompt = BiometricPrompt(
      activity,
      ContextCompat.getMainExecutor(activity),
      object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
          val authorized = result.cryptoObject?.cipher
          if (authorized == null) onError(null) else onSuccess(authorized)
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
          val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
            errorCode == BiometricPrompt.ERROR_CANCELED
          onError(if (cancelled) null else errString.toString())
        }
        // Un tentativo non riconosciuto non chiude il prompt: il sistema lo dice da solo e lascia
        // riprovare. Intervenire qui significherebbe raddoppiare il messaggio.
      },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
      .setTitle(title)
      .setSubtitle(subtitle)
      .setNegativeButtonText(negativeButton)
      .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
      .setConfirmationRequired(false)
      .build()
    prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
  }
}

/**
 * Un controllo di identita' **senza cifrario**.
 *
 * Il prompt del vault e' legato a una chiave dell'Android Keystore: senza l'impronta quella chiave
 * non si usa, e senza quella chiave l'identita' non si apre. Qui invece non c'e' niente da
 * decifrare -- la conversazione e' gia' aperta, la serratura sta **dentro** l'app -- e quello che
 * serve e' solo sapere se e' la stessa persona. Per questo si accetta anche il PIN del telefono:
 * chi non ha registrato un'impronta non deve restare senza serratura.
 */
fun BiometricAuthenticator.confirmIdentity(
  activity: FragmentActivity,
  title: String,
  subtitle: String,
  onSuccess: () -> Unit,
  onCancel: () -> Unit,
) {
  val prompt = BiometricPrompt(
    activity,
    ContextCompat.getMainExecutor(activity),
    object : BiometricPrompt.AuthenticationCallback() {
      override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()

      override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onCancel()
    },
  )
  val allowed = BiometricManager.Authenticators.BIOMETRIC_STRONG or
    BiometricManager.Authenticators.DEVICE_CREDENTIAL
  prompt.authenticate(
    BiometricPrompt.PromptInfo.Builder()
      .setTitle(title)
      .setSubtitle(subtitle)
      // Niente tasto di rifiuto: con il PIN del telefono fra gli autenticatori il sistema non lo
      // permette, e non serve -- si annulla con il gesto indietro.
      .setAllowedAuthenticators(allowed)
      .build(),
  )
}

/**
 * Se questo telefono sa dire "sei tu?".
 *
 * Impronta **oppure** PIN del telefono: basta una delle due. Un telefono senza nessuna delle due non
 * puo' rispondere a quella domanda, e su quel telefono la serratura di una conversazione non si puo'
 * offrire -- accenderla vorrebbe dire chiudere una porta e regalare la chiave a chiunque.
 */
fun canConfirmIdentity(context: android.content.Context): Boolean =
  BiometricManager.from(context).canAuthenticate(
    BiometricManager.Authenticators.BIOMETRIC_STRONG or
      BiometricManager.Authenticators.DEVICE_CREDENTIAL,
  ) == BiometricManager.BIOMETRIC_SUCCESS

/** Fuori da una `Activity` (anteprime, test) non c'e' biometria: e' un `null` esplicito. */
val LocalBiometricAuthenticator = staticCompositionLocalOf<BiometricAuthenticator?> { null }
