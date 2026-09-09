import java.util.Properties

// Radice del build. I moduli dell'engine applicano `com.android.library`, `kotlin.android` e
// `kotlin.plugin.compose` senza versione: e' qui che le versioni vengono decise, per tutti.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.hilt) apply false
  alias(libs.plugins.google.services) apply false
}

// Le cartelle di build vivono FUORI dal progetto.
//
// `C:\VibeCoded Projects` e' una cartella specchiata da Google Drive: il client apre ogni file che
// il build scrive per caricarlo, e mentre lo tiene aperto Gradle non riesce piu' a cancellarlo. Il
// sintomo era una build su tre che moriva con "Unable to delete directory ...", sempre in un punto
// diverso e senza nessun rapporto con il codice. Il contenuto di `build/` e' comunque materiale
// derivato: sincronizzarlo non serve a nessuno e consuma spazio e banda.
//
// Si sceglie dove con `codex.buildDir` in local.properties, o con -Pcodex.buildDir=... Senza
// nessuna delle due, finisce nella cartella temporanea dell'utente.
val codexBuildRoot: String = run {
  val fromCommandLine = providers.gradleProperty("codex.buildDir").orNull
  val fromLocal = rootProject.file("local.properties").takeIf { it.isFile }?.let { file ->
    Properties().apply { file.inputStream().use(::load) }.getProperty("codex.buildDir")
  }
  fromCommandLine ?: fromLocal ?: "${System.getProperty("java.io.tmpdir")}/codex-build"
}

allprojects {
  val relative = project.path.removePrefix(":").replace(':', '/').ifEmpty { "root" }
  layout.buildDirectory.set(File("$codexBuildRoot/$relative"))
}

/** Dove sono finite le cartelle di build: lo chiedono gli script invece di indovinarlo. */
tasks.register("buildRoot") {
  group = "help"
  description = "Stampa la cartella in cui finiscono gli artefatti di build."
  val root = codexBuildRoot
  doLast { println(root) }
}
