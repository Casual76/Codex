package dev.pampa.codex.core

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * In sviluppo si attesta con un gettone di debug.
 *
 * Play Integrity vuole un dispositivo vero, un'app firmata con la chiave giusta e installata da un
 * canale riconosciuto: su un emulatore non ha niente da guardare, e in enforcement bloccherebbe
 * ogni chiamata durante il lavoro. Il gettone lo stampa Firebase nel logcat al primo avvio
 * (`Enter this debug secret into the allow list...`) e va incollato una volta nella console, in
 * App Check -> app di debug.
 *
 * **Sta in `src/debug` apposta**: la libreria del gettone di debug non entra nell'APK di rilascio,
 * dove esisterebbe solo per essere abusata.
 */
internal fun attestationProvider(): AppCheckProviderFactory =
  DebugAppCheckProviderFactory.getInstance()
