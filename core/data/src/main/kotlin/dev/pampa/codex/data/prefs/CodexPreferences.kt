package dev.pampa.codex.data.prefs

import dev.pampa.codex.data.nearby.NearbyMode
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.codexPreferencesStore: DataStore<Preferences> by preferencesDataStore(name = "codex_prefs")

/**
 * Cosa si lascia vedere agli altri.
 *
 * Non e' privacy contro il server -- quella la fa la crittografia -- e' privacy verso le persone con
 * cui si parla: se non voglio che si sappia quando sono online, non lo mando, e in cambio non lo
 * vedo nemmeno io.
 */
data class SocialSignals(
  val showOnline: Boolean = true,
  val showTyping: Boolean = true,
)

/** Come si sblocca Codex: il segreto che l'utente ha scelto quando ha creato l'identita'. */
enum class LockType {
  /** Sei cifre. Il default: veloce da digitare, robusto perche' Argon2id lo rende costoso. */
  PIN,

  /** Una frase. Per chi la preferisce; nessun limite di lunghezza oltre il minimo. */
  PASSWORD,
  ;

  companion object {
    fun fromStorage(value: String?): LockType = entries.firstOrNull { it.name == value } ?: PIN
  }
}

/** Dopo quanto Codex si richiude da sola quando esce dallo schermo. */
enum class LockTimeout(val millis: Long) {
  IMMEDIATE(0),
  ONE_MINUTE(60_000),
  FIVE_MINUTES(5 * 60_000),
  THIRTY_MINUTES(30 * 60_000),
  ;

  companion object {
    fun fromStorage(value: String?): LockTimeout = entries.firstOrNull { it.name == value } ?: IMMEDIATE
  }
}

/**
 * Il profilo pubblico di chi usa questo telefono.
 *
 * Sta fuori dal vault apposta: la schermata di sblocco saluta per nome e mostra il minerale prima
 * che l'identita' sia stata aperta, e per farlo questi tre dati devono essere leggibili a chiave
 * chiusa. Non sono segreti: il nome e il Codex ID li conoscono gia' tutti i contatti.
 */
data class CodexProfile(
  val name: String = "",
  val avatarSeed: Long = 0L,
  val codexId: String = "",
)

/** Come si presenta la serratura, senza aprire niente. */
data class LockSettings(
  val lockType: LockType = LockType.PIN,
  val biometricEnabled: Boolean = false,
  val timeout: LockTimeout = LockTimeout.IMMEDIATE,
  val failedAttempts: Int = 0,
  val lockedUntil: Long = 0L,
)

/**
 * Le preferenze non sensibili di Codex.
 *
 * Tutto cio' che e' segreto (identita', chiavi) **non** sta qui: sta nel vault cifrato. Qui ci sono
 * le cose che l'app deve sapere anche da bloccata. Le chiavi sono nomi stabili: cambiarne uno
 * azzera la preferenza per chi ha gia' l'app.
 */
class CodexPreferences(private val context: Context) {

  private val store get() = context.codexPreferencesStore

  val onboardingCompleted: Flow<Boolean> = store.data.map { it[Keys.OnboardingCompleted] ?: false }

  val profile: Flow<CodexProfile> = store.data.map { preferences ->
    CodexProfile(
      name = preferences[Keys.ProfileName].orEmpty(),
      avatarSeed = preferences[Keys.AvatarSeed] ?: 0L,
      codexId = preferences[Keys.CodexId].orEmpty(),
    )
  }

  val lockSettings: Flow<LockSettings> = store.data.map { preferences ->
    LockSettings(
      lockType = LockType.fromStorage(preferences[Keys.LockType]),
      biometricEnabled = preferences[Keys.BiometricEnabled] ?: false,
      timeout = LockTimeout.fromStorage(preferences[Keys.LockTimeout]),
      failedAttempts = preferences[Keys.FailedAttempts] ?: 0,
      lockedUntil = preferences[Keys.LockedUntil] ?: 0L,
    )
  }

  suspend fun currentProfile(): CodexProfile = profile.first()

  suspend fun currentLockSettings(): LockSettings = lockSettings.first()

  suspend fun isThemeBootstrapped(): Boolean = store.data.first()[Keys.ThemeBootstrapped] ?: false

  suspend fun markThemeBootstrapped() {
    store.edit { it[Keys.ThemeBootstrapped] = true }
  }

  suspend fun setOnboardingCompleted(completed: Boolean) {
    store.edit { it[Keys.OnboardingCompleted] = completed }
  }

  suspend fun setProfile(profile: CodexProfile) {
    store.edit {
      it[Keys.ProfileName] = profile.name
      it[Keys.AvatarSeed] = profile.avatarSeed
      it[Keys.CodexId] = profile.codexId
    }
  }

  suspend fun setName(name: String) {
    store.edit { it[Keys.ProfileName] = name }
  }

  suspend fun setAvatarSeed(seed: Long) {
    store.edit { it[Keys.AvatarSeed] = seed }
  }

  suspend fun setLockType(type: LockType) {
    store.edit { it[Keys.LockType] = type.name }
  }

  suspend fun setBiometricEnabled(enabled: Boolean) {
    store.edit { it[Keys.BiometricEnabled] = enabled }
  }

  suspend fun setLockTimeout(timeout: LockTimeout) {
    store.edit { it[Keys.LockTimeout] = timeout.name }
  }

  suspend fun setUnlockAttempts(failedAttempts: Int, lockedUntil: Long) {
    store.edit {
      it[Keys.FailedAttempts] = failedAttempts
      it[Keys.LockedUntil] = lockedUntil
    }
  }

  /** Cancella tutto: e' il "reset" delle impostazioni, e non tocca il vault (lo fa il repository). */
  suspend fun clear() {
    store.edit { it.clear() }
  }

  /** I due interruttori della presenza. Accesi di partenza: e' come si comporta una chat. */
  val socialSignals: Flow<SocialSignals> = store.data.map { preferences ->
    SocialSignals(
      showOnline = preferences[Keys.ShowOnline] ?: true,
      showTyping = preferences[Keys.ShowTyping] ?: true,
    )
  }

  suspend fun currentSocialSignals(): SocialSignals = socialSignals.first()

  suspend fun setShowOnline(enabled: Boolean) {
    store.edit { it[Keys.ShowOnline] = enabled }
  }

  suspend fun setShowTyping(enabled: Boolean) {
    store.edit { it[Keys.ShowTyping] = enabled }
  }

  /**
   * Quando le vicinanze sono accese.
   *
   * **Spente di partenza.** Un'antenna che si accende da sola alla prima apertura sarebbe una cosa
   * che nessuno ha chiesto, e la si scoprirebbe dal consumo della batteria. Chi le vuole le accende.
   */
  val nearbyMode: Flow<NearbyMode> = store.data.map { preferences ->
    runCatching { NearbyMode.valueOf(preferences[Keys.NearbyMode].orEmpty()) }
      .getOrDefault(NearbyMode.OFF)
  }

  suspend fun setNearbyMode(mode: NearbyMode) {
    store.edit { it[Keys.NearbyMode] = mode.name }
  }

  /**
   * I suggerimenti gia' detti, e se dirne ancora.
   *
   * Un suggerimento si segna visto **quando compare**, non quando lo si chiude: chi lo scaccia via
   * scorrendo l'ha comunque visto, e rivederlo al giro dopo lo trasformerebbe in un ostacolo. Chi
   * ha chiesto di non vederne piu' spegne [hintsSilent] e non lo riaccende nessuno: e' l'unica
   * promessa che questa parte dell'app fa.
   */
  val hintsSeen: Flow<Set<String>> = store.data.map { it[Keys.HintsSeen] ?: emptySet() }

  val hintsSilent: Flow<Boolean> = store.data.map { it[Keys.HintsSilent] ?: false }

  suspend fun markHintSeen(id: String) {
    store.edit { preferences ->
      preferences[Keys.HintsSeen] = (preferences[Keys.HintsSeen] ?: emptySet()) + id
    }
  }

  suspend fun silenceHints() {
    store.edit { it[Keys.HintsSilent] = true }
  }

  /** L'identificatore di questo dispositivo, creato al primo uso e poi sempre lo stesso. */
  suspend fun deviceId(): String {
    store.data.first()[Keys.DeviceId]?.let { return it }
    val fresh = java.util.UUID.randomUUID().toString()
    store.edit { it[Keys.DeviceId] = fresh }
    return fresh
  }

  object Keys {
    val ThemeBootstrapped = booleanPreferencesKey("theme_bootstrapped")
    val OnboardingCompleted = booleanPreferencesKey("onboarding_completed")
    val ProfileName = stringPreferencesKey("profile_name")
    val AvatarSeed = longPreferencesKey("avatar_seed")
    val CodexId = stringPreferencesKey("codex_id")
    val LockType = stringPreferencesKey("lock_type")
    val BiometricEnabled = booleanPreferencesKey("biometric_enabled")
    val LockTimeout = stringPreferencesKey("lock_timeout")
    val FailedAttempts = intPreferencesKey("failed_attempts")
    val LockedUntil = longPreferencesKey("locked_until")
    val NearbyMode = stringPreferencesKey("nearby_mode")
    val HintsSeen = stringSetPreferencesKey("hints_seen")
    val HintsSilent = booleanPreferencesKey("hints_silent")

    /**
     * Come si chiama questo telefono nell'elenco dei dispositivi.
     *
     * E' un numero casuale generato al primo uso e non un identificatore del dispositivo:
     * `ANDROID_ID` e simili seguono la persona attraverso le app, e Codex non ha nessun motivo di
     * sapere che due installazioni stanno sullo stesso telefono. Serve solo a distinguere "questo"
     * da "l'altro mio" quando si revoca un dispositivo.
     */
    val DeviceId = stringPreferencesKey("device_id")

    /**
     * Se far sapere quando si e' online e quando si sta scrivendo.
     *
     * Chi li spegne **non li manda e non li vede**: e' l'unica forma onesta di reciprocita'. Un
     * interruttore che ti lascia guardare gli altri mentre tu resti invisibile e' un vantaggio, e
     * un vantaggio in una conversazione fra due persone e' uno squilibrio.
     */
    val ShowOnline = booleanPreferencesKey("show_online")
    val ShowTyping = booleanPreferencesKey("show_typing")
  }
}
