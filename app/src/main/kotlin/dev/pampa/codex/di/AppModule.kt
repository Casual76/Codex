package dev.pampa.codex.di

import android.app.ActivityManager
import android.content.Context
import androidx.core.content.getSystemService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.pampa.codex.crypto.Kdf
import dev.pampa.codex.data.CodexStore
import dev.pampa.codex.data.chat.ChatRepository
import dev.pampa.codex.data.cloud.CloudAccount
import dev.pampa.codex.data.cloud.CloudDirectory
import dev.pampa.codex.data.cloud.CloudTransport
import dev.pampa.codex.data.cloud.DeviceRegistry
import dev.pampa.codex.data.cloud.CloudStories
import dev.pampa.codex.data.cloud.InboxTransport
import dev.pampa.codex.data.groups.GroupRepository
import dev.pampa.codex.data.nearby.NearbyEngine
import dev.pampa.codex.data.nearby.NearbyLink
import dev.pampa.codex.data.media.MediaStore
import dev.pampa.codex.data.contacts.ContactRepository
import dev.pampa.codex.data.stories.StoryRepository
import dev.pampa.codex.data.identity.IdentityRepository
import dev.pampa.codex.data.identity.KeystoreVaultCipher
import dev.pampa.codex.data.identity.VaultStore
import dev.pampa.codex.data.prefs.CodexPreferences
import javax.inject.Singleton

/** I servizi dell'app che non appartengono all'engine. Crescera' con le milestone. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

  @Provides
  @Singleton
  fun codexPreferences(@ApplicationContext context: Context): CodexPreferences =
    CodexPreferences(context)

  @Provides
  @Singleton
  fun vaultStore(@ApplicationContext context: Context): VaultStore = VaultStore(context)

  @Provides
  @Singleton
  fun keystoreVaultCipher(): KeystoreVaultCipher = KeystoreVaultCipher()

  /**
   * Il magazzino locale. Un solo esemplare per processo: due istanze aprirebbero due volte lo
   * stesso file cifrato, che SQLite non gradisce e SQLCipher gradisce ancora meno.
   */
  @Provides
  @Singleton
  fun codexStore(
    @ApplicationContext context: Context,
    identityRepository: IdentityRepository,
    preferences: CodexPreferences,
    directory: CloudDirectory,
    transport: CloudTransport,
    account: CloudAccount,
    devices: DeviceRegistry,
    mediaStore: MediaStore,
    inbox: InboxTransport,
    nearby: NearbyLink,
    cloudStories: CloudStories,
  ): CodexStore = CodexStore(
    context = context,
    identityRepository = identityRepository,
    preferences = preferences,
    directory = directory,
    transport = transport,
    account = account,
    devices = devices,
    mediaStore = mediaStore,
    inbox = inbox,
    nearby = nearby,
    cloudStories = cloudStories,
  )

  @Provides
  @Singleton
  fun chatRepository(store: CodexStore): ChatRepository = store.chats

  @Provides
  @Singleton
  fun contactRepository(store: CodexStore): ContactRepository = store.contacts

  @Provides
  @Singleton
  fun groupRepository(store: CodexStore): GroupRepository = store.groups

  @Provides
  @Singleton
  fun nearbyEngine(store: CodexStore): NearbyEngine = store.nearbyEngine

  @Provides
  @Singleton
  fun storyRepository(store: CodexStore): StoryRepository = store.stories

  @Provides
  @Singleton
  fun identityRepository(
    @ApplicationContext context: Context,
    preferences: CodexPreferences,
    vaultStore: VaultStore,
    keystore: KeystoreVaultCipher,
  ): IdentityRepository = IdentityRepository(
    preferences = preferences,
    vaultStore = vaultStore,
    keystore = keystore,
    // Su un dispositivo con poca memoria, 64 MiB per aprire il vault sono un rischio di
    // terminazione, non una difesa: i parametri finiscono nel vault, quindi la scelta di oggi
    // resta leggibile domani su qualsiasi telefono.
    kdfParams = if (context.getSystemService<ActivityManager>()?.isLowRamDevice == true) {
      Kdf.LIGHT
    } else {
      Kdf.Params()
    },
  )
}
