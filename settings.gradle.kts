pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "Codex"

include(":app")
include(":core:model")
include(":core:crypto")
include(":core:seal")
include(":core:data")

// --- fluid-engine (inizio) ---
val engineDir = file("engine")
if (engineDir.exists()) {
  listOf(
  "engine-foundation",
  "engine-ui",
  "engine-storage",
  "engine-net",
  "engine-config",
  "engine-update"
  ).forEach { name ->
    include(":$name")
    project(":$name").projectDir = engineDir.resolve(name)
  }
}
// --- fluid-engine (fine) ---
