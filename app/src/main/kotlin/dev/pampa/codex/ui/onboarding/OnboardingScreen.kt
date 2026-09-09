package dev.pampa.codex.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidAmbientCanvas
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.rememberFluidHaptics
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.pampa.codex.R
import dev.pampa.codex.data.identity.IdentityRepository
import dev.pampa.codex.data.prefs.LockType
import dev.pampa.codex.ui.lock.LocalBiometricAuthenticator
import dev.pampa.codex.ui.lock.PinDots
import dev.pampa.codex.ui.lock.PinKeypad
import dev.pampa.codex.ui.seal.MineralView
import kotlinx.coroutines.launch

/**
 * L'onboarding: quattro passi, e in ognuno succede qualcosa invece di essere raccontato.
 *
 * Il primo sigillo si apre davvero, con lo stesso gesto e la stessa animazione dei messaggi: quando
 * arrivera' il primo messaggio vero, il gesto sara' gia' noto senza che nessuno lo abbia spiegato.
 */
@Composable
fun OnboardingScreen(
  onCompleted: () -> Unit,
  viewModel: OnboardingViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(state.completed) {
    if (state.completed) onCompleted()
  }

  Box(modifier = Modifier.fillMaxSize()) {
    FluidAmbientCanvas(
      ambient = remember { FluidAmbient(tone = FluidHeroTone.Primary, motif = FluidHeroMotif.Glow) },
    )
    AnimatedContent(
      targetState = state.step,
      transitionSpec = {
        // Laterale e opaco: due schermate leggibili sovrapposte fanno sembrare l'app un prototipo.
        val forward = targetState.ordinal > initialState.ordinal
        val distance = if (forward) 1 else -1
        (
          slideInHorizontally(FluidMotion.intOffset()) { it * distance } +
            fadeIn(tween(90))
          ) togetherWith (
          slideOutHorizontally(FluidMotion.intOffset()) { -it * distance } +
            fadeOut(tween(90))
          )
      },
      label = "onboarding",
      modifier = Modifier.fillMaxSize(),
    ) { step ->
      when (step) {
        OnboardingStep.WELCOME -> WelcomeStep(onContinue = viewModel::goToName)
        OnboardingStep.NAME -> NameStep(
          state = state,
          onNameChange = viewModel::setName,
          onReroll = viewModel::rerollAvatar,
          onContinue = viewModel::goToSecret,
        )
        OnboardingStep.SECRET -> SecretStep(state = state, viewModel = viewModel)
        OnboardingStep.BIOMETRICS -> BiometricsStep(state = state, viewModel = viewModel)
      }
    }
  }
}

// --- 1. Il primo sigillo ------------------------------------------------------------------------

@Composable
private fun WelcomeStep(onContinue: () -> Unit) {
  val haptics = rememberFluidHaptics()
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val fracture = remember { Animatable(0f) }
  val scope = rememberCoroutineScope()
  var cracked by remember { mutableStateOf(false) }
  // Il testo cambia quando le schegge se ne sono andate, non quando il dito tocca: cambiarlo
  // subito racconta la fine della storia mentre l'animazione la sta ancora raccontando.
  val opened by remember { derivedStateOf { fracture.value > 0.55f } }
  // Un minerale fisso: e' il sigillo di benvenuto, uguale per tutti, e nessuno lo rivedra' piu'.
  val mineral = remember { dev.pampa.codex.seal.MineralGenerator.generate(0x0C0DE5EAL) }

  StepScaffold(
    title = stringResource(if (opened) R.string.onboarding_welcome_title else R.string.onboarding_welcome_tap),
    body = stringResource(if (opened) R.string.onboarding_welcome_body else R.string.onboarding_welcome_hint),
    primary = if (opened) stringResource(R.string.action_continue) else null,
    onPrimary = onContinue,
  ) {
    Box(
      modifier = Modifier.size(220.dp),
      contentAlignment = Alignment.Center,
    ) {
      MineralView(
        mineral = mineral,
        modifier = Modifier
          .fillMaxSize()
          .fluidPressable(
            onClick = if (cracked) {
              null
            } else {
              {
                cracked = true
                haptics.play(FluidHapticEvent.Threshold)
                scope.launch {
                  fracture.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                      durationMillis = if (reducedMotion) 120 else 850,
                      easing = FluidMotion.EaseEmphasized,
                    ),
                  )
                  haptics.play(FluidHapticEvent.Success)
                }
              }
            },
            pressedScale = 0.96f,
            haptic = null,
          ),
        fracture = { fracture.value },
      )
    }
  }
}

// --- 2. Il nome diventa un minerale ---------------------------------------------------------------

@Composable
private fun NameStep(
  state: OnboardingUiState,
  onNameChange: (String) -> Unit,
  onReroll: () -> Unit,
  onContinue: () -> Unit,
) {
  StepScaffold(
    title = stringResource(R.string.onboarding_name_title),
    body = stringResource(R.string.onboarding_name_body),
    // Come nel passo del segreto: la domanda prima del campo. Sotto, si leggeva "Come ti chiami?"
    // dopo aver gia' visto una casella vuota e un minerale che non si sa cosa c'entri.
    titleOnTop = true,
    primary = stringResource(R.string.action_continue),
    primaryEnabled = state.nameIsValid,
    onPrimary = onContinue,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      MineralView(mineral = state.mineral, modifier = Modifier.size(148.dp))
      FluidButton(
        text = stringResource(R.string.onboarding_name_reroll),
        onClick = onReroll,
        style = FluidButtonStyle.Plain,
        enabled = state.name.isNotBlank(),
      )
      FluidTextField(
        value = state.name,
        onValueChange = onNameChange,
        placeholder = stringResource(R.string.onboarding_name_placeholder),
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}

// --- 3. La chiave -------------------------------------------------------------------------------

@Composable
private fun SecretStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
  val mismatch = stringResource(R.string.lock_secret_mismatch)
  val lockTypeLabels = mapOf(
    LockType.PIN to stringResource(R.string.lock_type_pin),
    LockType.PASSWORD to stringResource(R.string.lock_type_password),
  )
  val title = when {
    state.confirming -> stringResource(R.string.onboarding_secret_confirm)
    state.lockType == LockType.PIN -> stringResource(R.string.onboarding_secret_pin_title)
    else -> stringResource(R.string.onboarding_secret_password_title)
  }

  StepScaffold(
    title = title,
    body = state.error ?: stringResource(R.string.onboarding_secret_body),
    bodyIsError = state.error != null,
    // Qui il titolo va sopra: dice cosa digitare, e un'istruzione che arriva dopo la tastiera
    // e' un'istruzione che si legge dopo aver sbagliato.
    titleOnTop = true,
    primary = if (state.lockType == LockType.PASSWORD || state.confirming) {
      stringResource(if (state.confirming) R.string.action_confirm else R.string.action_continue)
    } else {
      null
    },
    primaryEnabled = state.secretIsValid && !state.busy,
    onPrimary = { viewModel.confirmSecret(mismatch) },
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
      FluidSegmentedControl(
        options = listOf(LockType.PIN, LockType.PASSWORD),
        selected = state.lockType,
        onSelect = viewModel::setLockType,
        enabled = !state.confirming && !state.busy,
        label = { lockTypeLabels.getValue(it) },
      )

      when (state.lockType) {
        LockType.PIN -> {
          val entered = if (state.confirming) state.confirmation else state.secret
          PinDots(
            filled = entered.length,
            total = IdentityRepository.PIN_LENGTH,
            error = state.error != null,
          )
          PinKeypad(
            onDigit = { digit ->
              viewModel.appendDigit(digit)
              val next = if (state.confirming) state.confirmation else state.secret
              if (next.length + 1 == IdentityRepository.PIN_LENGTH) viewModel.confirmSecret(mismatch)
            },
            onDelete = viewModel::deleteDigit,
            enabled = !state.busy,
          )
        }

        LockType.PASSWORD -> {
          FluidTextField(
            value = if (state.confirming) state.confirmation else state.secret,
            onValueChange = viewModel::setSecret,
            placeholder = stringResource(R.string.lock_password_placeholder),
            supportingText = stringResource(
              R.string.lock_password_hint,
              IdentityRepository.MIN_PASSWORD_LENGTH,
            ),
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }
  }
}

// --- 4. L'impronta ------------------------------------------------------------------------------

@Composable
private fun BiometricsStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
  val authenticator = LocalBiometricAuthenticator.current
  val title = stringResource(R.string.onboarding_biometrics_title)
  val subtitle = stringResource(R.string.onboarding_biometrics_prompt)
  val negative = stringResource(R.string.action_use_secret)
  val available = authenticator?.isAvailable() == true

  StepScaffold(
    title = title,
    body = stringResource(
      if (available) R.string.onboarding_biometrics_body else R.string.onboarding_biometrics_unavailable,
    ),
    primary = stringResource(if (available) R.string.action_enable else R.string.action_finish),
    onPrimary = {
      if (!available) {
        viewModel.skipBiometrics()
        return@StepScaffold
      }
      val cipher = viewModel.biometricEnrollCipher()
      if (cipher == null) {
        viewModel.skipBiometrics()
      } else {
        authenticator.authenticate(
          cipher = cipher,
          title = title,
          subtitle = subtitle,
          negativeButton = negative,
          onSuccess = viewModel::enableBiometrics,
          onError = { },
        )
      }
    },
    secondary = if (available) stringResource(R.string.action_not_now) else null,
    onSecondary = viewModel::skipBiometrics,
  ) {
    Icon(
      imageVector = Icons.Rounded.Fingerprint,
      contentDescription = null,
      modifier = Modifier.size(96.dp),
      tint = MaterialTheme.colorScheme.primary,
    )
  }
}

// --- Struttura comune ---------------------------------------------------------------------------

/**
 * Lo scheletro di un passo: contenuto al centro, testo sotto, azioni in fondo.
 *
 * Sempre lo stesso, cosi' passare da un passo all'altro sposta solo cio' che cambia davvero: il
 * titolo resta dov'e', i tasti restano dove il pollice li ha appena lasciati.
 */
@Composable
private fun StepScaffold(
  title: String,
  body: String,
  primary: String?,
  onPrimary: () -> Unit,
  modifier: Modifier = Modifier,
  bodyIsError: Boolean = false,
  titleOnTop: Boolean = false,
  primaryEnabled: Boolean = true,
  secondary: String? = null,
  onSecondary: () -> Unit = {},
  content: @Composable () -> Unit,
) {
  // Su uno schermo largo la colonna si ferma e sta in mezzo. Senza, sul tablet il campo del nome
  // diventava una riga da duemilacinquecento pixel: non e' un campo, e' un righello.
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
  Column(
    modifier = Modifier
      .widthIn(max = StepWidth)
      .fillMaxSize()
      .safeDrawingPadding()
      .imePadding()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 28.dp, vertical = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    val heading: @Composable () -> Unit = {
      Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(10.dp))
      Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = if (bodyIsError) {
          MaterialTheme.colorScheme.error
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant
        },
        textAlign = TextAlign.Center,
      )
    }

    Spacer(Modifier.height(16.dp))
    if (titleOnTop) {
      heading()
      Spacer(Modifier.height(28.dp))
      content()
    } else {
      content()
      Spacer(Modifier.height(32.dp))
      heading()
    }
    Spacer(Modifier.height(28.dp))
    Row(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (secondary != null) {
        FluidButton(text = secondary, onClick = onSecondary, style = FluidButtonStyle.Plain)
      }
      if (primary != null) {
        FluidButton(
          text = primary,
          onClick = onPrimary,
          style = FluidButtonStyle.Filled,
          enabled = primaryEnabled,
        )
      }
    }
    Spacer(Modifier.height(16.dp))
  }
  }
}

/** Quanto e' larga al massimo una schermata dell'onboarding. Oltre, i campi diventano righelli. */
private val StepWidth = 560.dp
