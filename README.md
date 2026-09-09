# Codex

Messaggistica Android in cui ogni messaggio arriva **sigillato** in una forma da aprire con un tocco
(rune, roccia, quadro) mentre la crittografia vera lavora sotto senza farsi vedere. Pratica come un
messenger, vissuta come un'avventura di cifratura.

- Piano completo e fonte di verita': [docs/PIANO.md](docs/PIANO.md)
- Archeologia della vecchia Codex 0.2.4: [docs/archeologia](docs/archeologia)
- Fondamenta: [Fluid Engine](https://github.com/Casual76/fluid-engine) (submodule in `engine/`)

## Struttura

```
app/          l'app (Kotlin + Jetpack Compose + Fluid Engine), package dev.pampa.codex
core/model    tipi puri condivisi
core/crypto   crittografia (Bouncy Castle lightweight), JVM puro, test con vettori noti
core/seal     sigilli: semi, rune, minerali, steganografia, dipinti
core/data     preferenze, database locale, Firebase, vicinanze, sincronizzazione
engine/       Fluid Engine (submodule agganciato a un tag, vedi engine.properties)
functions/    Cloud Functions (TypeScript)
firebase/     regole Firestore/Storage e indici
tools/        build-debug.ps1, frames.ps1 (cattura fotogrammi delle animazioni)
manifest.json manifest del Pampa Store e config remota dell'engine
```

## Compilare

Serve JDK 17, l'SDK Android (API 36) e un `local.properties` con `sdk.dir`. Poi:

```bash
./gradlew.bat --no-daemon :app:assembleDebug testDebugUnitTest
```

oppure `powershell -ExecutionPolicy Bypass -File tools\build-debug.ps1` che compila e installa sul
telefono collegato. Prima di dire "fatto", sempre:

```bash
powershell -ExecutionPolicy Bypass -File engine\tools\engine-doctor.ps1 -AppRoot .
```

Dopo un clone: `git submodule update --init` per scaricare l'engine.

## Firebase

`app/google-services.json` e' git-ignorato: si scarica dalla console del progetto Firebase di Codex
(app Android `dev.pampa.codex` e `dev.pampa.codex.debug`). Senza il file l'app compila lo stesso,
ma Firebase non e' configurato. Le regole e le funzioni si provano con l'Emulator Suite
(`firebase emulators:start`).

## Firma

La release si firma con `pampa.jks` tramite `keystore.properties` (git-ignorato): chiavi
`storeFile`, `storePassword`, `keyAlias`, `keyPassword`. Senza chiave la release viene firmata con
la chiave di debug e non e' pubblicabile.
