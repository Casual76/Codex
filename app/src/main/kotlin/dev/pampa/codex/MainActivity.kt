package dev.pampa.codex

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.antigravity.fluidengine.ui.theme.FluidScreenSurface
import dev.pampa.codex.data.identity.IdentityState
import dev.pampa.codex.ui.MainViewModel
import dev.pampa.codex.ui.lock.BiometricAuthenticator
import dev.pampa.codex.ui.lock.LocalBiometricAuthenticator
import dev.pampa.codex.ui.lock.LockScreen
import dev.pampa.codex.ui.navigation.CodexRoot
import dev.pampa.codex.ui.onboarding.OnboardingScreen
import dev.pampa.codex.ui.paintings.PaintingInbox
import dev.pampa.codex.ui.theme.CodexTheme
import dev.pampa.codex.ui.theme.resolvesToDark
import javax.inject.Inject

/**
 * L'unica Activity.
 *
 * E' una `FragmentActivity` perche' `BiometricPrompt` lo richiede: il prompt di sistema si aggancia
 * al gestore dei fragment per sopravvivere alla rotazione dello schermo mentre il dito e' ancora
 * sul sensore.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

  /**
   * Dove finiscono i quadri che arrivano da fuori.
   *
   * Iniettata qui e non presa da un ViewModel perche' l'intento arriva prima che esista una
   * schermata: la casella deve poterlo accogliere anche se l'app e' bloccata, e restituirlo quando
   * qualcuno la sblocca.
   */
  @Inject lateinit var paintingInbox: PaintingInbox

  /** Dove finiscono gli inviti a un gruppo toccati da fuori. Stessa ragione: puo' arrivare da app bloccata. */
  @Inject lateinit var inviteInbox: dev.pampa.codex.ui.groups.GroupInviteInbox

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    receivePainting(intent)
    receiveInvite(intent)
    enableEdgeToEdge()
    setContent {
      val viewModel: MainViewModel = hiltViewModel()
      val settings by viewModel.engineSettings.collectAsStateWithLifecycle()
      val gate by viewModel.gate.collectAsStateWithLifecycle()

      // Le icone delle barre seguono il tema *dell'app*, non quello del sistema: Codex e' scura per
      // scelta anche su un telefono chiaro, e le icone bianche su fondo bianco sono un bug.
      val isDark = settings.resolvesToDark()
      val barStyle = if (isDark) {
        SystemBarStyle.dark(Color.TRANSPARENT)
      } else {
        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
      }
      enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)

      LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onBackgrounded() }
      LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForegrounded() }

      val authenticator = remember { BiometricAuthenticator(this) }

      CodexTheme(settings = settings) {
        CompositionLocalProvider(LocalBiometricAuthenticator provides authenticator) {
          FluidScreenSurface(modifier = Modifier.fillMaxSize()) {
            when {
              // Un fondo vuoto invece di uno spinner: la decisione arriva in pochi millisecondi,
              // e un indicatore che lampeggia costa piu' attenzione di quanta ne risparmi.
              gate.identity is IdentityState.Loading -> Box(Modifier.fillMaxSize())

              gate.identity is IdentityState.Absent -> OnboardingScreen(onCompleted = { })

              // C'e' un vault: si apre sempre da li'. Anche se l'onboarding non era stato finito,
              // il modo di entrare e' il segreto, non un secondo giro di creazione.
              gate.identity is IdentityState.Locked -> LockScreen()

              !gate.onboardingCompleted -> OnboardingScreen(onCompleted = { })

              else -> CodexRoot(
                settings = settings,
                onThemeModeChange = viewModel::setThemeMode,
                onDynamicColorChange = viewModel::setDynamicColorEnabled,
                onAmoledChange = viewModel::setAmoledEnabled,
                onHapticsChange = viewModel::setHapticsEnabled,
              )
            }
          }
        }
      }
    }
  }

  /**
   * L'app era gia' aperta e qualcuno le ha passato un quadro.
   *
   * Con `launchMode="singleTask"` il secondo intento arriva qui invece di ricreare l'Activity, e
   * senza questo passaggio l'immagine verrebbe semplicemente ignorata.
   */
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    receivePainting(intent)
    receiveInvite(intent)
  }

  /**
   * L'invito a un gruppo dentro un link toccato altrove.
   *
   * Non entra in nessun gruppo da solo: mette il link nella casella, e la schermata che lo sa
   * leggere chiedera' a chi ha invitato di consegnare la chiave. Un link che facesse entrare da
   * solo sarebbe una chiave data a chiunque lo inoltri.
   */
  private fun receiveInvite(intent: Intent?) {
    if (intent?.action != Intent.ACTION_VIEW) return
    val data = intent.data ?: return
    if (data.scheme != "codex" || data.host != "gruppo") return
    inviteInbox.offer(data.toString())
  }

  /** L'immagine dentro un intento "apri con" o "condividi con", se ce n'e' una. */
  private fun receivePainting(intent: Intent?) {
    if (intent == null) return
    if (intent.type?.startsWith("image/") != true) return
    val uri = when (intent.action) {
      Intent.ACTION_VIEW -> intent.data
      Intent.ACTION_SEND -> intent.streamExtra()
      else -> null
    } ?: return
    paintingInbox.offer(uri)
  }

  @Suppress("DEPRECATION")
  private fun Intent.streamExtra(): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
      getParcelableExtra(Intent.EXTRA_STREAM)
    }
}
