import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// La crittografia di Codex, in JVM puro: identita', vault, chiavi di chat, busta CDX3. Una sola
// libreria (Bouncy Castle, API lightweight, nessun provider JCA registrato) e test con vettori noti.
plugins {
  alias(libs.plugins.kotlin.jvm)
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
  compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
  implementation(project(":core:model"))
  implementation(libs.bouncycastle.prov)
  implementation(libs.kotlinx.coroutines.core)

  testImplementation(libs.junit5.api)
  testImplementation(libs.junit5.params)
  testRuntimeOnly(libs.junit5.engine)
  testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
}
