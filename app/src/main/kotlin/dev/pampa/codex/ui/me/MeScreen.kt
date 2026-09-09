package dev.pampa.codex.ui.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antigravity.fluidengine.foundation.EngineBuild
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.FluidAlert
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidSwitch
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.theme.FluidCard
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTone
import dev.pampa.codex.ui.theme.codexHorizontalPadding
import dev.pampa.codex.BuildConfig
import dev.antigravity.fluidengine.ui.tutorial.FluidGestureHint
import dev.antigravity.fluidengine.ui.tutorial.fluidTutorialAnchor
import dev.pampa.codex.ui.tutorial.CodexHint
import dev.pampa.codex.ui.tutorial.CodexHintOffer
import dev.pampa.codex.R
import dev.pampa.codex.ui.account.AccountSection
import dev.pampa.codex.ui.account.DevicesSection
import dev.pampa.codex.data.prefs.LockTimeout
import dev.pampa.codex.data.prefs.LockType
import dev.pampa.codex.seal.MineralGenerator
import dev.pampa.codex.ui.lock.BiometricStatus
import dev.pampa.codex.ui.lock.LocalBiometricAuthenticator
import dev.pampa.codex.ui.seal.MineralView

/**
 * La scheda Io: chi sei, come si chiude l'app, come si vede, e cosa c'e' sotto il cofano.
 */
@Composable
fun MeScreen(
  settings: EngineSettings,
  onThemeModeChange: (ThemeMode) -> Unit,
  onDynamicColorChange: (Boolean) -> Unit,
  onAmoledChange: (Boolean) -> Unit,
  onHapticsChange: (Boolean) -> Unit,
  onOpenOrigins: () -> Unit = {},
  onOpenCredits: () -> Unit = {},
  onOpenPlayground: () -> Unit = {},
  viewModel: MeViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val updateState by viewModel.updateState.collectAsStateWithLifecycle()
  val changeSecret by viewModel.changeSecret.collectAsStateWithLifecycle()
  val clipboard = LocalClipboardManager.current
  val authenticator = LocalBiometricAuthenticator.current

  var renaming by remember { mutableStateOf(false) }
  var confirmingReset by remember { mutableStateOf(false) }

  val lockTypeLabels = mapOf(
    LockType.PIN to stringResource(R.string.lock_type_pin),
    LockType.PASSWORD to stringResource(R.string.lock_type_password),
  )
  val timeoutLabels = mapOf(
    LockTimeout.IMMEDIATE to stringResource(R.string.security_timeout_immediate),
    LockTimeout.ONE_MINUTE to stringResource(R.string.security_timeout_1m),
    LockTimeout.FIVE_MINUTES to stringResource(R.string.security_timeout_5m),
    LockTimeout.THIRTY_MINUTES to stringResource(R.string.security_timeout_30m),
  )
  val themeLabels = mapOf(
    ThemeMode.LIGHT to stringResource(R.string.theme_light),
    ThemeMode.DARK to stringResource(R.string.theme_dark),
    ThemeMode.SYSTEM to stringResource(R.string.theme_system),
  )
  val selectedTheme = if (settings.themeMode == ThemeMode.AMOLED) ThemeMode.DARK else settings.themeMode
  val isDarkChoice = selectedTheme != ThemeMode.LIGHT

  // Che i messaggi possano attraversare una stanza senza rete e' la cosa che nessuno va a
  // cercare in un'impostazione: si dice qui, indicando l'interruttore.
  CodexHintOffer(
    id = CodexHint.Nearby,
    priority = 10,
    title = stringResource(R.string.hint_nearby_title),
    text = stringResource(R.string.hint_nearby_text),
  )

  val biometricTitle = stringResource(R.string.security_biometrics)
  val biometricPrompt = stringResource(R.string.onboarding_biometrics_prompt)
  val biometricNegative = stringResource(R.string.action_use_secret)

  FluidScreen(
    title = stringResource(R.string.me_title),
    subtitle = stringResource(R.string.me_subtitle),
    ambient = remember { FluidAmbient(tone = FluidHeroTone.Tertiary, motif = FluidHeroMotif.Cards) },
    horizontalPadding = codexHorizontalPadding(),
  ) {
    item(key = "profile") {
      FluidCard(glass = true) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(16.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          MineralView(
            mineral = remember(state.profile.avatarSeed) {
              MineralGenerator.avatar(state.profile.avatarSeed)
            },
            modifier = Modifier.size(72.dp),
          )
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = state.profile.name.ifBlank { stringResource(R.string.profile_no_name) },
              style = MaterialTheme.typography.titleLarge,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = state.profile.codexId,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
    item(key = "profile-actions") {
      FluidListGroup(glass = true) {
        FluidListRow(
          title = stringResource(R.string.profile_name),
          subtitle = state.profile.name.ifBlank { stringResource(R.string.profile_no_name) },
          onClick = { renaming = true },
        )
        FluidListDivider()
        FluidListRow(
          title = stringResource(R.string.profile_codex_id),
          subtitle = state.profile.codexId,
          meta = stringResource(R.string.action_copy),
          onClick = { clipboard.setText(AnnotatedString(state.profile.codexId)) },
        )
        FluidListDivider()
        FluidListRow(
          title = stringResource(R.string.profile_reroll),
          subtitle = stringResource(R.string.profile_reroll_detail),
          onClick = viewModel::rerollAvatar,
        )
      }
    }

    item(key = "account-header") {
      FluidSectionHeader(title = stringResource(R.string.section_account))
    }
    item(key = "account") { AccountSection() }

    item(key = "devices-header") {
      FluidSectionHeader(
        title = stringResource(R.string.section_devices),
        detail = stringResource(R.string.devices_detail),
      )
    }
    item(key = "devices") { DevicesSection() }

    item(key = "visibility-header") {
      FluidSectionHeader(title = stringResource(R.string.section_visibility))
    }
    item(key = "visibility") {
      FluidListGroup(glass = true) {
        FluidListRow(
          title = stringResource(R.string.visibility_online),
          subtitle = stringResource(R.string.visibility_online_detail),
          badge = {
            FluidSwitch(
              checked = state.signals.showOnline,
              onCheckedChange = viewModel::setShowOnline,
            )
          },
          onClick = { viewModel.setShowOnline(!state.signals.showOnline) },
        )
        FluidListDivider()
        FluidListRow(
          title = stringResource(R.string.visibility_typing),
          subtitle = stringResource(R.string.visibility_typing_detail),
          badge = {
            FluidSwitch(
              checked = state.signals.showTyping,
              onCheckedChange = viewModel::setShowTyping,
            )
          },
          onClick = { viewModel.setShowTyping(!state.signals.showTyping) },
        )
      }
    }
    item(key = "visibility-note") {
      FluidSectionFootnote(text = stringResource(R.string.visibility_footnote))
    }

    item(key = "nearby-header") {
      FluidSectionHeader(
        title = stringResource(R.string.section_nearby),
        detail = stringResource(R.string.nearby_detail),
      )
    }
    item(key = "nearby") {
      Box(modifier = Modifier.fluidTutorialAnchor(CodexHint.Nearby)) { NearbySection() }
    }

    item(key = "security-header") {
      FluidSectionHeader(title = stringResource(R.string.section_security))
    }
    item(key = "security") {
      FluidListGroup(glass = true) {
        val biometricStatus = authenticator?.status() ?: BiometricStatus.UNAVAILABLE
        SettingSwitchRow(
          title = biometricTitle,
          subtitle = when (biometricStatus) {
            BiometricStatus.AVAILABLE -> stringResource(R.string.security_biometrics_detail)
            BiometricStatus.NOT_ENROLLED -> stringResource(R.string.security_biometrics_not_enrolled)
            BiometricStatus.UNAVAILABLE -> stringResource(R.string.security_biometrics_unavailable)
          },
          checked = state.lock.biometricEnabled,
          enabled = biometricStatus == BiometricStatus.AVAILABLE,
          onCheckedChange = { enabled ->
            if (!enabled) {
              viewModel.disableBiometrics()
            } else {
              val cipher = viewModel.biometricEnrollCipher()
              if (cipher != null) {
                authenticator?.authenticate(
                  cipher = cipher,
                  title = biometricTitle,
                  subtitle = biometricPrompt,
                  negativeButton = biometricNegative,
                  onSuccess = viewModel::enableBiometrics,
                  onError = { },
                )
              }
            }
          },
        )
        FluidListDivider()
        FluidListRow(
          title = stringResource(R.string.security_change_title),
          subtitle = lockTypeLabels.getValue(state.lock.lockType),
          onClick = { viewModel.openChangeSecret(state.lock.lockType) },
        )
        FluidListDivider()
        FluidListRow(
          title = stringResource(R.string.security_lock_now),
          subtitle = stringResource(R.string.security_lock_now_detail),
          onClick = viewModel::lockNow,
        )
      }
    }
    item(key = "timeout") {
      FluidCard(glass = true) {
        Text(
          text = stringResource(R.string.security_timeout),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        FluidSegmentedControl(
          options = LockTimeout.entries.toList(),
          selected = state.lock.timeout,
          onSelect = viewModel::setLockTimeout,
          label = { timeoutLabels.getValue(it) },
        )
      }
    }

    item(key = "appearance-header") {
      FluidSectionHeader(title = stringResource(R.string.section_appearance))
    }
    item(key = "theme") {
      FluidCard(glass = true) {
        FluidSegmentedControl(
          options = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM),
          selected = selectedTheme,
          onSelect = onThemeModeChange,
          label = { themeLabels.getValue(it) },
        )
      }
    }
    item(key = "toggles") {
      FluidListGroup(glass = true) {
        SettingSwitchRow(
          title = stringResource(R.string.row_dynamic_color),
          subtitle = stringResource(R.string.row_dynamic_color_detail),
          checked = settings.dynamicColorEnabled,
          onCheckedChange = onDynamicColorChange,
        )
        FluidListDivider()
        SettingSwitchRow(
          title = stringResource(R.string.row_amoled),
          subtitle = stringResource(R.string.row_amoled_detail),
          checked = settings.amoledEnabled,
          enabled = isDarkChoice,
          onCheckedChange = onAmoledChange,
        )
        FluidListDivider()
        SettingSwitchRow(
          title = stringResource(R.string.row_haptics),
          subtitle = stringResource(R.string.row_haptics_detail),
          checked = settings.hapticsEnabled,
          onCheckedChange = onHapticsChange,
        )
      }
    }

    item(key = "about-header") {
      FluidSectionHeader(title = stringResource(R.string.section_about))
    }
    item(key = "about") {
      FluidListGroup(glass = true) {
        FluidListRow(
          title = stringResource(R.string.row_version),
          subtitle = "Codex ${BuildConfig.VERSION_NAME}",
        )
        FluidListDivider()
        FluidListRow(title = stringResource(R.string.row_engine), subtitle = EngineBuild.VERSION)
        FluidListDivider()
        UpdateRow(state = updateState, onClick = viewModel::checkOrInstall)
        FluidListDivider()
        FluidListRow(
          title = stringResource(R.string.row_source),
          subtitle = stringResource(R.string.row_source_detail),
        )
        FluidListDivider()
        FluidListRow(
          title = stringResource(R.string.row_credits),
          subtitle = stringResource(R.string.row_credits_detail),
          onClick = onOpenCredits,
        )
      }
    }
    item(key = "origins") {
      FluidListGroup(glass = true) {
        FluidListRow(
          title = stringResource(R.string.row_origins),
          subtitle = stringResource(R.string.row_origins_detail),
          onClick = onOpenOrigins,
        )
      }
    }

    if (BuildConfig.DEBUG) {
      item(key = "playground") {
        FluidListGroup(glass = true) {
          FluidListRow(
            title = stringResource(R.string.row_playground),
            subtitle = stringResource(R.string.row_playground_detail),
            onClick = onOpenPlayground,
          )
        }
      }
    }

    item(key = "danger-header") {
      FluidSectionHeader(title = stringResource(R.string.section_danger))
    }
    item(key = "danger") {
      FluidListGroup(glass = true) {
        FluidListRow(
          title = stringResource(R.string.danger_reset),
          subtitle = stringResource(R.string.danger_reset_detail),
          tone = FluidTone.Danger,
          onClick = { confirmingReset = true },
        )
      }
    }
    item(key = "danger-note") {
      FluidSectionFootnote(text = stringResource(R.string.danger_reset_note))
    }
  }

  if (renaming) {
    RenameDialog(
      initialName = state.profile.name,
      onDismiss = { renaming = false },
      onConfirm = { name ->
        viewModel.rename(name)
        renaming = false
      },
    )
  }

  if (confirmingReset) {
    FluidAlert(
      onDismissRequest = { confirmingReset = false },
      title = stringResource(R.string.danger_reset),
      message = stringResource(R.string.danger_reset_message),
      actions = listOf(
        FluidAlertAction(
          label = stringResource(R.string.action_cancel),
          onClick = { confirmingReset = false },
        ),
        FluidAlertAction(
          label = stringResource(R.string.action_reset),
          emphasis = FluidAlertAction.Emphasis.Destructive,
          onClick = {
            confirmingReset = false
            viewModel.resetEverything()
          },
        ),
      ),
    )
  }

  ChangeSecretSheet(
    state = changeSecret,
    currentLockType = state.lock.lockType,
    onDismiss = viewModel::closeChangeSecret,
    onTypeChange = viewModel::setChangeSecretType,
    onInput = viewModel::changeSecretInput,
    onDigit = viewModel::appendChangeSecretDigit,
    onDelete = viewModel::deleteChangeSecretDigit,
    onAdvance = viewModel::advanceChangeSecret,
  )
}

@Composable
private fun RenameDialog(
  initialName: String,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var name by remember { mutableStateOf(initialName) }
  FluidAlert(
    onDismissRequest = onDismiss,
    title = stringResource(R.string.profile_name),
    message = stringResource(R.string.profile_name_detail),
    actions = listOf(
      FluidAlertAction(label = stringResource(R.string.action_cancel), onClick = onDismiss),
      FluidAlertAction(
        label = stringResource(R.string.action_save),
        onClick = { onConfirm(name) },
      ),
    ),
    content = {
      FluidTextField(
        value = name,
        onValueChange = { name = it },
        placeholder = stringResource(R.string.onboarding_name_placeholder),
        modifier = Modifier.fillMaxWidth(),
      )
    },
  )
}

@Composable
private fun UpdateRow(state: UpdateUiState, onClick: () -> Unit) {
  val subtitle = when (state) {
    UpdateUiState.Idle -> stringResource(R.string.update_tap_to_check)
    UpdateUiState.Checking -> stringResource(R.string.update_checking)
    UpdateUiState.Latest -> stringResource(R.string.update_latest)
    is UpdateUiState.Available -> stringResource(R.string.update_available, state.version)
    UpdateUiState.CheckFailed -> stringResource(R.string.update_failed)
    is UpdateUiState.Downloading -> stringResource(R.string.update_downloading, state.percent)
    is UpdateUiState.Installing -> state.message
    UpdateUiState.Installed -> stringResource(R.string.update_installed)
    is UpdateUiState.Error -> stringResource(R.string.update_error, state.message)
  }
  if (BuildConfig.DEBUG) {
    // Una build di lavoro non si aggiorna dallo store: sarebbe sostituita dalla release.
    FluidListRow(
      title = stringResource(R.string.row_updates),
      subtitle = stringResource(R.string.update_debug_unavailable),
    )
  } else {
    FluidListRow(
      title = stringResource(R.string.row_updates),
      subtitle = subtitle,
      onClick = onClick,
    )
  }
}

/**
 * Una riga con un interruttore, nello stesso schema della galleria dell'engine: la riga intera e'
 * il bersaglio, l'interruttore mostra lo stato.
 */
@Composable
private fun SettingSwitchRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  enabled: Boolean = true,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    FluidSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
  }
}
