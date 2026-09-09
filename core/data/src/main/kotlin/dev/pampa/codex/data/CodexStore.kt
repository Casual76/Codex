package dev.pampa.codex.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import dev.pampa.codex.data.chat.ChatRepository
import dev.pampa.codex.data.cloud.CloudAccount
import dev.pampa.codex.data.cloud.CloudDirectory
import dev.pampa.codex.data.cloud.CloudStories
import dev.pampa.codex.data.cloud.CloudTransport
import dev.pampa.codex.data.cloud.DeviceRegistry
import dev.pampa.codex.data.cloud.NoDevices
import dev.pampa.codex.data.cloud.InboxTransport
import dev.pampa.codex.data.cloud.NoDirectory
import dev.pampa.codex.data.cloud.NoInbox
import dev.pampa.codex.data.cloud.NoStories
import dev.pampa.codex.data.cloud.NoTransport
import dev.pampa.codex.data.cloud.SyncEngine
import dev.pampa.codex.data.contacts.ContactRepository
import dev.pampa.codex.data.db.CodexDatabase
import dev.pampa.codex.data.db.DatabaseKeyStore
import dev.pampa.codex.data.groups.GroupRepository
import dev.pampa.codex.data.identity.IdentityRepository
import dev.pampa.codex.data.media.MediaStore
import dev.pampa.codex.data.media.MediaVault
import dev.pampa.codex.data.media.NoMediaStore
import dev.pampa.codex.data.nearby.NearbyEngine
import dev.pampa.codex.data.nearby.NearbyLink
import dev.pampa.codex.data.nearby.NoNearby
import dev.pampa.codex.data.identity.Keyring
import dev.pampa.codex.data.stories.StoryRepository

/**
 * Il magazzino locale: il database cifrato e i repository che ci lavorano sopra.
 *
 * Esiste per una ragione di confine, non di comodita': **fuori da `:core:data` non deve comparire
 * nessun tipo di Room**. Senza questa classe, l'app dovrebbe conoscere `CodexDatabase` per poterlo
 * costruire, e da li' in poi qualsiasi schermata potrebbe farsi dare un DAO e scrivere una query.
 * Da qui escono solo [chats], [contacts] e [stories], che parlano di persone, di conversazioni e di
 * sigilli.
 */
class CodexStore(
  private val context: Context,
  identityRepository: IdentityRepository,
  preferences: dev.pampa.codex.data.prefs.CodexPreferences,
  directory: CloudDirectory = NoDirectory,
  transport: CloudTransport = NoTransport,
  account: CloudAccount? = null,
  devices: DeviceRegistry = NoDevices,
  mediaStore: MediaStore = NoMediaStore,
  inbox: InboxTransport = NoInbox,
  nearby: NearbyLink = NoNearby,
  cloudStories: CloudStories = NoStories,
  scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

  private val keyStore = DatabaseKeyStore(context)

  /** I media di questo telefono, sempre cifrati su disco. */
  val media = MediaVault(java.io.File(context.filesDir, "media"))
  private val database = CodexDatabase.open(context, keyStore.passphrase())

  /** Le chiavi delle conversazioni: una sola istanza, perche' una sola e' la tabella. */
  private val keyring = Keyring(database.keyring(), identityRepository)

  val chats: ChatRepository = ChatRepository(
    chats = database.chats(),
    messages = database.messages(),
    identityRepository = identityRepository,
    keyring = keyring,
    outbox = database.outbox(),
    vault = media,
    mediaStore = mediaStore,
    transport = transport,
    account = account,
  )

  val contacts: ContactRepository = ContactRepository(
    contacts = database.contacts(),
    identityRepository = identityRepository,
    chatRepository = chats,
    keyring = keyring,
    directory = directory,
  )

  /**
   * I gruppi.
   *
   * Sta sopra a [chats] e a [contacts] perche' li usa tutti e due: un gruppo e' una conversazione
   * con dentro delle persone, e la sua chiave viaggia da una persona all'altra.
   */
  val groups: GroupRepository = GroupRepository(
    members = database.groupMembers(),
    chats = chats,
    contacts = contacts,
    contactRows = database.contacts(),
    identityRepository = identityRepository,
    keyring = keyring,
    inbox = inbox,
    transport = transport,
    account = account,
    scope = scope,
  )

  /**
   * Le vicinanze.
   *
   * Vive **fuori** dal motore del cloud, e non per ordine: le vicinanze devono funzionare quando non
   * c'e' niente -- niente rete, niente account, due telefoni in aereo. Il motore del cloud esiste
   * solo quando c'e' un accesso, e metterle dentro vorrebbe dire spegnerle proprio nel momento per
   * cui esistono.
   */
  val nearbyEngine: NearbyEngine = NearbyEngine(
    link = nearby,
    chats = chats,
    contacts = database.contacts(),
    identityRepository = identityRepository,
    vault = media,
    mode = preferences.nearbyMode,
    scope = scope,
  ).apply { start() }

  val stories: StoryRepository = StoryRepository(
    stories = database.stories(),
    identityRepository = identityRepository,
    contacts = database.contacts(),
    cloud = cloudStories,
    account = account,
    keyring = keyring,
    scope = scope,
  )

  /**
   * Il motore della consegna.
   *
   * Parte subito e sta fermo finche' non c'e' un accesso: e' lui a decidere quando c'e' qualcosa da
   * fare, non chi lo costruisce. Senza account (o senza Firebase) non esiste proprio, e l'app resta
   * quella locale, che funziona per intero.
   */
  val sync: SyncEngine? = account?.let {
    SyncEngine(
      chats = chats,
      contacts = contacts,
      transport = transport,
      mediaStore = mediaStore,
      account = it,
      devices = devices,
      showOnline = preferences.socialSignals.map { it.showOnline },
      scope = scope,
      inbox = inbox,
      groups = groups,
      cloudStories = cloudStories,
      stories = stories,
    ).apply { start() }
  }


  /**
   * Cancella tutto quello che sta su disco.
   *
   * Chiude il database prima di cancellarlo: su Android un file aperto si cancella lo stesso, ma
   * lascia dietro i giornali del write-ahead e la prossima apertura li ritrova.
   */
  fun wipe() {
    sync?.stop()
    nearbyEngine.stop()
    runCatching { database.close() }
    CodexDatabase.delete(context)
    media.wipe()
    keyStore.delete()
  }
}
