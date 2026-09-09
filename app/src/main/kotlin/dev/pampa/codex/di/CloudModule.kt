package dev.pampa.codex.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.pampa.codex.BuildConfig
import dev.pampa.codex.data.cloud.CloudAccount
import dev.pampa.codex.data.cloud.CloudDirectory
import dev.pampa.codex.data.cloud.CloudSettings
import dev.pampa.codex.data.cloud.CloudStories
import dev.pampa.codex.data.cloud.CloudTransport
import dev.pampa.codex.data.cloud.CodexCloud
import dev.pampa.codex.data.cloud.DeviceRegistry
import dev.pampa.codex.data.cloud.InboxTransport
import dev.pampa.codex.data.media.MediaStore
import dev.pampa.codex.data.nearby.GmsNearbyLink
import dev.pampa.codex.data.nearby.NearbyLink
import dev.pampa.codex.data.prefs.CodexPreferences
import javax.inject.Singleton

/**
 * Il cloud, quando c'e'.
 *
 * Qui non compare nessun tipo di Firebase: la fabbrica sta in `:core:data`, e da lei escono solo le
 * due interfacce. Se il progetto non e' configurato, quello che torna e' un account sempre
 * disconnesso e una rubrica che dice sempre "non c'e'", e l'app continua a fare tutto il resto.
 */
@Module
@InstallIn(SingletonComponent::class)
object CloudModule {

  @Provides
  @Singleton
  fun cloudSettings(): CloudSettings = CloudSettings(emulatorHost = BuildConfig.FIREBASE_EMULATOR)

  @Provides
  @Singleton
  fun cloudAccount(
    @ApplicationContext context: Context,
    settings: CloudSettings,
  ): CloudAccount = CodexCloud.account(context, settings)

  @Provides
  @Singleton
  fun cloudDirectory(
    @ApplicationContext context: Context,
    settings: CloudSettings,
    account: CloudAccount,
  ): CloudDirectory = CodexCloud.directory(context, settings, account)

  @Provides
  @Singleton
  fun cloudTransport(
    @ApplicationContext context: Context,
    settings: CloudSettings,
    account: CloudAccount,
  ): CloudTransport = CodexCloud.transport(context, settings, account)

  @Provides
  @Singleton
  fun inboxTransport(
    @ApplicationContext context: Context,
    settings: CloudSettings,
    account: CloudAccount,
  ): InboxTransport = CodexCloud.inbox(context, settings, account)

  @Provides
  @Singleton
  fun cloudStories(
    @ApplicationContext context: Context,
    settings: CloudSettings,
    account: CloudAccount,
  ): CloudStories = CodexCloud.stories(context, settings, account)

  @Provides
  @Singleton
  fun deviceRegistry(
    @ApplicationContext context: Context,
    settings: CloudSettings,
    account: CloudAccount,
    preferences: CodexPreferences,
  ): DeviceRegistry = CodexCloud.devices(
    context = context,
    settings = settings,
    account = account,
    preferences = preferences,
    appVersion = BuildConfig.VERSION_NAME,
  )

  /**
   * L'antenna delle vicinanze.
   *
   * Non ha niente a che vedere con Firebase e sta qui lo stesso: e' un trasporto, e i trasporti si
   * costruiscono in un posto solo. A differenza degli altri non ha bisogno di un account -- serve
   * proprio quando non c'e' niente.
   */
  @Provides
  @Singleton
  fun nearbyLink(@ApplicationContext context: Context): NearbyLink = GmsNearbyLink(context)

  @Provides
  @Singleton
  fun mediaStore(
    @ApplicationContext context: Context,
    settings: CloudSettings,
    account: CloudAccount,
  ): MediaStore = CodexCloud.mediaStore(context, settings, account)
}
