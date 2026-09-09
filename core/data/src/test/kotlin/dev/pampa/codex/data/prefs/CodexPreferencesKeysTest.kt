package dev.pampa.codex.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class CodexPreferencesKeysTest {

  @Test
  fun `i nomi delle chiavi sono stabili`() {
    // Rinominare una chiave azzera la preferenza per chi ha gia' l'app: il test rende la cosa
    // deliberata invece che accidentale. Per la serratura sarebbe peggio di una preferenza persa:
    // un tipo di blocco dimenticato manderebbe una persona alla schermata sbagliata.
    assertEquals("theme_bootstrapped", CodexPreferences.Keys.ThemeBootstrapped.name)
    assertEquals("onboarding_completed", CodexPreferences.Keys.OnboardingCompleted.name)
    assertEquals("profile_name", CodexPreferences.Keys.ProfileName.name)
    assertEquals("avatar_seed", CodexPreferences.Keys.AvatarSeed.name)
    assertEquals("codex_id", CodexPreferences.Keys.CodexId.name)
    assertEquals("lock_type", CodexPreferences.Keys.LockType.name)
    assertEquals("biometric_enabled", CodexPreferences.Keys.BiometricEnabled.name)
    assertEquals("lock_timeout", CodexPreferences.Keys.LockTimeout.name)
    assertEquals("failed_attempts", CodexPreferences.Keys.FailedAttempts.name)
    assertEquals("locked_until", CodexPreferences.Keys.LockedUntil.name)
    // I suggerimenti gia' visti: rinominare questa chiave vuol dire rimostrarli tutti a chi li ha
    // gia' letti, e riaccendere quelli di chi aveva chiesto di non vederne piu'.
    assertEquals("hints_seen", CodexPreferences.Keys.HintsSeen.name)
    assertEquals("hints_silent", CodexPreferences.Keys.HintsSilent.name)
  }

  @Test
  fun `i valori salvati si rileggono anche quando non li riconosciamo`() {
    // Un'installazione futura potrebbe scrivere un valore che questa versione non conosce: deve
    // ripiegare sul default invece di far cadere l'app all'avvio.
    assertEquals(LockType.PIN, LockType.fromStorage(null))
    assertEquals(LockType.PIN, LockType.fromStorage("QUALCOSA_DI_NUOVO"))
    assertEquals(LockType.PASSWORD, LockType.fromStorage("PASSWORD"))
    assertEquals(LockTimeout.IMMEDIATE, LockTimeout.fromStorage("BOH"))
    assertEquals(LockTimeout.FIVE_MINUTES, LockTimeout.fromStorage("FIVE_MINUTES"))
  }
}
