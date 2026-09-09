import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// I dati di Codex: preferenze, database locale, Firebase, vicinanze, sincronizzazione. In M0 ci
// sono solo le preferenze; Room, Firestore e Nearby arrivano con le milestone che li usano.
plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
}

android {
  namespace = "dev.pampa.codex.data"
  compileSdk = 36

  // Room scrive lo schema su disco: serve a vedere in una revisione cosa cambia in una migrazione,
  // che e' l'unico momento in cui i dati di qualcuno si possono perdere davvero.
  ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
  }

  defaultConfig {
    minSdk = 28
    consumerProguardFiles("consumer-rules.pro")
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  testOptions {
    unitTests {
      // `android.util.Log` non esiste su una JVM da sola, e senza questa riga ogni riga di log
      // dentro il codice provato fa fallire il test con un errore che non parla di niente.
      isReturnDefaultValues = true
    }
  }
}

kotlin {
  compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
  // `api`: il repository dell'identita' espone CodexIdentity e le schede firmate nella
  // propria firma pubblica, quindi chi dipende da :core:data deve vederle senza dichiararlo.
  api(project(":core:model"))
  api(project(":core:crypto"))
  api(project(":core:seal"))

  implementation(libs.androidx.datastore.preferences)
  // Serve al battito della presenza: "online" vuol dire che l'app e' davanti agli occhi, e chi lo
  // sa e' il ciclo di vita del processo, non una schermata.
  implementation(libs.androidx.lifecycle.process)
  implementation(libs.room.runtime)
  implementation(libs.room.ktx)
  ksp(libs.room.compiler)
  // Il database e' cifrato a riposo: la chiave sta nell'Android Keystore, non nel file.
  implementation(libs.sqlcipher.android)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)

  // Il cloud. Sta qui e non nell'app perche' e' un magazzino come Room: sopra questo modulo
  // nessuno deve sapere che esiste Firestore, allo stesso modo in cui nessuno sa che esiste un DAO.
  //
  // `play-services` per le coroutine: le API di Firebase restituiscono `Task`, e senza `await()`
  // il codice tornerebbe a essere una catena di callback annidati.
  // Il BOM va su `api` e non su `implementation`: le versioni delle librerie Firebase arrivano da
  // li', e una dipendenza esposta con `api` senza il BOM sulla stessa configurazione resta **senza
  // versione**. Non se ne accorge il build dell'app -- se ne accorge lint, molto dopo.
  api(platform(libs.firebase.bom))
  implementation(libs.firebase.auth)
  implementation(libs.firebase.firestore)
  // Il deposito dei media: quello che ci passa e' rumore cifrato, e sale sempre come
  // `application/octet-stream` -- il server non sa nemmeno se e' una foto o una voce.
  implementation(libs.firebase.storage)
  // `api`: il servizio che riceve le notifiche e' un componente Android e vive nell'app, quindi
  // l'app deve poter vedere `FirebaseMessagingService`. E' l'unica eccezione al confine.
  api(libs.firebase.messaging)
  implementation(libs.kotlinx.coroutines.play.services)

  // Le vicinanze: Bluetooth e Wi-Fi Direct dietro un'API sola. Sta qui e non in `:app` perche' e' un
  // trasporto, come Firestore -- e come Firestore vive dietro un'interfaccia, cosi' il resto
  // dell'app non sa che esiste.
  implementation(libs.play.services.nearby)

  testImplementation(libs.junit4)
  testImplementation(libs.kotlinx.coroutines.test)
}
