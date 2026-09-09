import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// I sigilli: semi, rune, minerali, steganografia, catalogo dei dipinti. Kotlin piu' android.graphics
// per le bitmap; niente Compose, niente rete, niente database. Il rendering Compose sta nell'app.
plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "dev.pampa.codex.seal"
  compileSdk = 36

  defaultConfig {
    minSdk = 28
    consumerProguardFiles("consumer-rules.pro")
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

kotlin {
  compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
  implementation(project(":core:model"))
  implementation(libs.kotlinx.serialization.json)
  // Il QR della scheda contatto. zxing core e' JVM puro: entra qui e non nell'app perche' cosi'
  // la matrice si prova con un test invece che con una fotocamera.
  implementation(libs.zxing.core)

  testImplementation(libs.junit4)
}
