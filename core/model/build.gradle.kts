import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// I tipi puri di Codex: contatti, chat, messaggi, sigilli. Niente Android, niente Compose, niente
// rete: e' il vocabolario che tutti gli altri moduli condividono.
plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
  compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
  api(libs.kotlinx.serialization.core)

  testImplementation(libs.junit5.api)
  testImplementation(libs.junit5.params)
  testRuntimeOnly(libs.junit5.engine)
  testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
}
