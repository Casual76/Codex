package dev.pampa.codex.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.antigravity.fluidengine.config.EngineConfigSource
import dev.antigravity.fluidengine.config.EngineRemoteConfig
import dev.antigravity.fluidengine.foundation.AppUpdater
import dev.antigravity.fluidengine.net.EngineHttp
import dev.antigravity.fluidengine.storage.EngineConfigCache
import dev.antigravity.fluidengine.storage.EngineSettingsStore
import dev.antigravity.fluidengine.update.AndroidAppUpdateInstaller
import dev.antigravity.fluidengine.update.EngineAppUpdater
import dev.antigravity.fluidengine.update.UpdateSource
import dev.pampa.codex.BuildConfig
import javax.inject.Singleton

/**
 * L'unico file dell'app che sa come si mette in piedi l'engine.
 *
 * L'engine non dipende da Hilt: le sue classi hanno costruttori normali. Il collegamento e' qui.
 */
@Module
@InstallIn(SingletonComponent::class)
object EngineModule {

  /**
   * Lo stesso documento serve sia gli aggiornamenti sia i flag: sezioni diverse, un URL solo. E' il
   * manifest.json che il Pampa Store legge, alla radice del repo.
   */
  const val ManifestUrl = "https://raw.githubusercontent.com/Casual76/Codex/main/manifest.json"

  @Provides
  @Singleton
  fun engineHttp(): EngineHttp = EngineHttp(
    userAgent = "Codex/${BuildConfig.VERSION_NAME}",
  )

  @Provides
  @Singleton
  fun engineSettingsStore(@ApplicationContext context: Context): EngineSettingsStore =
    EngineSettingsStore(context)

  @Provides
  @Singleton
  fun engineRemoteConfig(
    http: EngineHttp,
    @ApplicationContext context: Context,
  ): EngineRemoteConfig = EngineRemoteConfig(
    http = http,
    cache = EngineConfigCache(context),
    source = EngineConfigSource(
      manifestUrl = ManifestUrl,
      // Passato invece di leggerlo dal package, cosi' una build di debug puo' fingersi la release
      // mentre si prova un flag.
      applicationId = BuildConfig.APPLICATION_ID,
    ),
  )

  @Provides
  @Singleton
  fun appUpdater(
    http: EngineHttp,
    @ApplicationContext context: Context,
  ): AppUpdater = EngineAppUpdater(
    http = http,
    source = UpdateSource(
      manifestUrl = ManifestUrl,
      applicationId = BuildConfig.APPLICATION_ID,
    ),
    installer = AndroidAppUpdateInstaller(context, http),
  )
}
