import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.hilt)
}

// Firebase: il plugin legge app/google-services.json, che e' git-ignorato e arriva dalla console.
// Senza il file il build non deve fermarsi: la build compila lo stesso, e lo dice.
if (file("google-services.json").exists()) {
  apply(plugin = libs.plugins.google.services.get().pluginId)
} else {
  logger.warn("Codex: app/google-services.json assente, Firebase non e' configurato in questa build.")
}

// Firma release. La chiave vera (pampa.jks) vive fuori dal repo. Si cerca, nell'ordine, in
// keystore.properties e local.properties di questo progetto e poi nei file che le altre app Pampa
// usano gia', cosi' una macchina che compila Universal Converter compila anche Codex senza
// ricopiare niente. Senza chiave la release ripiega sulla firma di debug, e lo dice.
val signingProperties = Properties().apply {
  listOf(
    rootProject.file("keystore.properties"),
    rootProject.file("local.properties"),
    file("C:/VibeCoded Projects/universal_converter/keystore.properties"),
    file("C:/VibeCoded Projects/Pampa-store-src/local.properties"),
  ).filter { it.isFile }.forEach { propertiesFile ->
    propertiesFile.inputStream().use(::load)
  }
}

fun signingProperty(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
  providers.gradleProperty(key).orNull
    ?: signingProperties.getProperty(key)
    ?: providers.environmentVariable(key).orNull
}?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingProperty(
  "storeFile", "pampa.storeFile", "pampa.release.storeFile", "PAMPA_RELEASE_STORE_FILE", "KEYSTORE_PATH",
)
val hasReleaseKey = releaseStoreFile != null

android {
  namespace = "dev.pampa.codex"
  compileSdk = 36

  defaultConfig {
    applicationId = "dev.pampa.codex"
    minSdk = 28
    targetSdk = 36
    // versionCode 1..99 erano le build di sviluppo; la prima beta sul Pampa Store parte da 100.
    versionCode = 100
    versionName = "1.0.0-beta.1"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  androidResources {
    localeFilters += listOf("it", "en")
  }

  signingConfigs {
    create("release") {
      if (hasReleaseKey) {
        storeFile = file(releaseStoreFile!!)
        storePassword = signingProperty(
          "storePassword", "pampa.storePassword", "pampa.release.storePassword",
          "PAMPA_RELEASE_STORE_PASSWORD", "KEYSTORE_PASSWORD",
        )
        keyAlias = signingProperty(
          "keyAlias", "pampa.keyAlias", "pampa.release.keyAlias", "PAMPA_RELEASE_KEY_ALIAS", "KEY_ALIAS",
        ) ?: "pampa"
        keyPassword = signingProperty(
          "keyPassword", "pampa.keyPassword", "pampa.release.keyPassword",
          "PAMPA_RELEASE_KEY_PASSWORD", "KEY_PASSWORD",
        )
        // La v1 va accesa a mano: AGP la toglie con minSdk >= 24, e il publisher del Pampa Store
        // convalida proprio la presenza della firma JAR prima di caricare.
        enableV1Signing = true
        enableV2Signing = true
        enableV3Signing = true
      }
    }
  }

  buildTypes {
    debug {
      // Un suffisso, cosi' una build di lavoro convive sul telefono con quella dello store.
      applicationIdSuffix = ".debug"
      versionNameSuffix = "-debug"
      // Dove sta l'emulatore di Firebase, visto dal dispositivo. Vuoto = si usa il progetto vero.
      //
      // Si accende scrivendo `codex.firebaseEmulator=10.0.2.2` in local.properties (che e'
      // git-ignorato): serve a provare accesso e rubrica senza toccare niente di reale, e senza
      // dover aspettare che in console sia acceso l'accesso con Google.
      buildConfigField(
        "String",
        "FIREBASE_EMULATOR",
        "\"${signingProperties.getProperty("codex.firebaseEmulator", "")}\"",
      )
    }
    release {
      // In release non esiste nessun emulatore: il campo c'e' perche' il codice lo legge, ed e'
      // vuoto perche' una build che va sullo store non deve poter parlare con un server di prova.
      buildConfigField("String", "FIREBASE_EMULATOR", "\"\"")
      isMinifyEnabled = true
      isShrinkResources = true
      // Solo le architetture dei telefoni veri.
      //
      // SQLCipher porta la sua libreria nativa per quattro architetture, e due -- x86 e x86_64 --
      // esistono solo negli emulatori: nessun telefono o tablet Android in circolazione le usa.
      // Sono 4,3 MB su 24 che ogni persona scaricherebbe per niente. La build di debug le tiene
      // tutte, altrimenti l'emulatore non parte.
      //
      // `-Pcodex.allAbis` le rimette: **la release su un emulatore non parte senza**, e R8 e'
      // esattamente la cosa che non si puo' provare leggendo -- una regola che manca si vede solo
      // quando la classe non c'e' piu' e l'app cade. Serve a provarla, non a spedirla.
      if (!providers.gradleProperty("codex.allAbis").isPresent) {
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
      }
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = if (hasReleaseKey) {
        signingConfigs.getByName("release")
      } else {
        logger.warn("Codex: nessuna chiave di firma trovata, la release e' firmata DEBUG e non e' pubblicabile.")
        signingConfigs.getByName("debug")
      }
    }
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  packaging {
    resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
  }

  testOptions {
    unitTests.isIncludeAndroidResources = true
  }

  /**
   * Due controlli portati a errore, ed e' un criterio di accettazione di M8.
   *
   * `MissingTranslation`: una stringa che esiste in italiano e non in inglese non e' un dettaglio da
   * sistemare dopo -- e' una frase che qualcuno leggera' nella lingua sbagliata, e nessuno se ne
   * accorge finche' non capita a lui. `HardcodedText`: un testo scritto dentro una schermata non si
   * traduce e non si trova piu'. Tutti e due sono facili da introdurre e invisibili a chi rilegge.
   */
  lint {
    error += listOf("MissingTranslation", "HardcodedText")
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_17)
    freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
  }
}

dependencies {
  // Fluid Engine: design system, impostazioni, config remota, aggiornamento in-app.
  implementation(project(":engine-ui"))
  implementation(project(":engine-storage"))
  implementation(project(":engine-config"))
  implementation(project(":engine-update"))

  // I moduli di Codex.
  implementation(project(":core:model"))
  implementation(project(":core:crypto"))
  implementation(project(":core:seal"))
  implementation(project(":core:data"))

  implementation(platform(libs.compose.bom))
  androidTestImplementation(platform(libs.compose.bom))
  implementation(libs.compose.ui)
  implementation(libs.compose.ui.graphics)
  implementation(libs.compose.ui.tooling.preview)
  implementation(libs.compose.foundation)
  implementation(libs.compose.runtime)
  implementation(libs.compose.material3)
  implementation(libs.compose.material3.window)
  implementation(libs.compose.material.icons.extended)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.lifecycle.process)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.datastore.preferences)
  // BiometricPrompt: e' il sistema a vedere l'impronta, non l'app.
  implementation(libs.androidx.biometric)

  // **App Check**: il server chiede anche "da dove arriva questa chiamata?", non solo "chi sei".
  // Il fornitore vero (Play Integrity) sta nel rilascio; quello di debug entra **solo** nelle build
  // di lavoro, dove serve perche' un emulatore non ha niente da attestare. Le due implementazioni
  // stanno in `src/release` e `src/debug`: cosi' la libreria del gettone di debug non finisce
  // nell'APK che le persone installano.
  implementation(platform(libs.firebase.bom))
  releaseImplementation(libs.firebase.appcheck.playintegrity)
  debugImplementation(libs.firebase.appcheck.debug)

  // L'accesso con Google passa da Credential Manager, non dal vecchio GoogleSignInClient: e' il
  // sistema a mostrare il selettore degli account e a restituire un token firmato, e l'app non vede
  // mai una password. Il `serverClientId` e' il client web che il plugin google-services genera in
  // `R.string.default_web_client_id`.
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)

  // Inquadrare la scheda di qualcuno: solo la fotocamera. Il riconoscitore di codici e' quello
  // che :core:seal usa gia' per **disegnarli** (zxing), quindi leggerli non aggiunge un byte
  // all'APK. ML Kit farebbe lo stesso lavoro un po' meglio e costerebbe dieci megabyte di modello
  // su una release che ne pesa venti: non e' un prezzo sensato per un quadrato disegnato dall'app
  // stessa, su uno schermo acceso, a venti centimetri dall'obiettivo.
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)

  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.androidx.hilt.navigation.compose)

  // La consegna che sopravvive all'app chiusa. Serve soprattutto agli allegati: un messaggio di
  // testo scritto in metropolitana parte alla prossima apertura e nessuno se ne accorge, ma una
  // foto mandata prima di mettere via il telefono deve partire quando torna la rete, non quando
  // qualcuno riapre Codex.
  implementation(libs.androidx.work.runtime)
  implementation(libs.androidx.hilt.work)
  ksp(libs.androidx.hilt.compiler)

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)

  debugImplementation(libs.compose.ui.tooling)
  debugImplementation(libs.compose.ui.test.manifest)

  testImplementation(libs.junit4)
  testImplementation(libs.kotlinx.coroutines.test)
  // Serve a una cosa sola: costruire un ViewModel senza tirarsi dietro un database. Le finte dei
  // DAO vivono nei test di `:core:data` e non si vedono da qui.
  testImplementation(libs.mockk)
  testImplementation(libs.turbine)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.espresso.core)
  androidTestImplementation(libs.compose.ui.test.junit4)
}
