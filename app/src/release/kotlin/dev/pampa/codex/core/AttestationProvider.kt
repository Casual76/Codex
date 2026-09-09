package dev.pampa.codex.core

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * In rilascio si attesta con Play Integrity.
 *
 * E' Google a rispondere alla domanda "questa app, su questo dispositivo, e' quella vera?", e la
 * risposta non passa da niente che stia dentro l'APK: non c'e' un segreto da estrarre.
 *
 * Vuole due cose fuori dal codice, ed e' bene che stiano scritte accanto: l'impronta **SHA-256**
 * della chiave di firma registrata nel progetto Firebase, e Play Integrity abilitato in App Check.
 * Senza, il gettone non arriva e le chiamate protette vengono rifiutate.
 */
internal fun attestationProvider(): AppCheckProviderFactory =
  PlayIntegrityAppCheckProviderFactory.getInstance()
