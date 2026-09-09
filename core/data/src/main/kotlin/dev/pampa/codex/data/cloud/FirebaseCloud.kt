package dev.pampa.codex.data.cloud

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.SetOptions
import dev.pampa.codex.crypto.ContactCard
import dev.pampa.codex.crypto.SignedContactCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Dove sta il server, e se e' quello vero.
 *
 * `emulatorHost` non e' vuoto solo nelle build di lavoro, e serve a poter costruire e provare tutto
 * il livello cloud **prima** che il progetto vero sia pronto: l'emulatore di Firebase fa
 * autenticazione e database in locale, senza rete e senza toccare niente di reale.
 */
data class CloudSettings(
  /** L'indirizzo dell'emulatore visto dal dispositivo (`10.0.2.2` per l'emulatore Android). */
  val emulatorHost: String = "",
  val authPort: Int = 9099,
  val firestorePort: Int = 8088,
  val storagePort: Int = 9199,
) {
  val usesEmulator: Boolean get() = emulatorHost.isNotBlank()
}

/**
 * L'accesso, appoggiato a Firebase.
 *
 * Non fa il login da solo: l'accesso e' un gesto dell'utente, e il gesto vero -- Google -- vive
 * nell'app, che ha le schermate. Qui c'e' il pezzo che serve a tutto il resto: **chi sei adesso**,
 * e come smettere di esserlo.
 */
class FirebaseAccount(
  private val auth: FirebaseAuth,
) : CloudAccount {

  private val _state = MutableStateFlow<AccountState>(AccountState.Unknown)
  override val state: StateFlow<AccountState> = _state.asStateFlow()

  init {
    // Il listener risponde anche al primo controllo, quindi non serve leggere `currentUser` a mano.
    auth.addAuthStateListener { current ->
      val user = current.currentUser
      _state.value = if (user == null) AccountState.SignedOut else AccountState.SignedIn(user.uid)
    }
  }

  override fun uidOrNull(): String? = auth.currentUser?.uid

  override suspend fun signInWithGoogle(idToken: String): Result<String> = runCatching {
    val credential = GoogleAuthProvider.getCredential(idToken, null)
    auth.signInWithCredential(credential).await().user!!.uid
  }

  override suspend fun signOut() {
    auth.signOut()
  }

  /**
   * Se ne va, e porta via quello che il server sapeva.
   *
   * **L'ordine conta e non e' quello che verrebbe da scrivere.** Prima si cancellano i documenti,
   * poi l'account: dopo `delete()` non ci sono piu' i permessi per toccare niente, e quello che
   * fosse rimasto resterebbe li' per sempre, senza nessuno che possa piu' portarlo via -- nemmeno
   * la persona a cui apparteneva.
   *
   * Quello che non si cancella e' scritto nel commento dell'interfaccia, e vale la pena ripeterlo
   * qui: le buste gia' consegnate stanno nelle conversazioni di altre persone, e quelle non sono
   * nostre da cancellare. Scadono da sole a trenta giorni.
   */
  override suspend fun deleteAccount(): Result<Unit> {
    val user = auth.currentUser ?: return Result.failure(IllegalStateException("nessun accesso"))
    val uid = user.uid
    return runCatching {
      withTimeout(DELETE_TIMEOUT) {
        val db = firestore ?: return@withTimeout
        // La voce nella rubrica per prima: e' l'unica cosa che rende trovabile una persona, e se
        // qualcosa andasse storto a meta' e' quella che non deve restare.
        runCatching {
          val mie = db.collection("codexIds").whereEqualTo("uid", uid).get().await()
          mie.documents.forEach { it.reference.delete().await() }
        }
        listOf("devices", "vault", "keyring").forEach { sotto ->
          runCatching {
            db.collection("users").document(uid).collection(sotto).get().await()
              .documents.forEach { it.reference.delete().await() }
          }
        }
        runCatching {
          db.collection("inbox").document(uid).collection("items").get().await()
            .documents.forEach { it.reference.delete().await() }
        }
        runCatching {
          db.collection("stories").document(uid).collection("items").get().await()
            .documents.forEach { it.reference.delete().await() }
        }
        runCatching { db.collection("presence").document(uid).delete().await() }
        runCatching { db.collection("users").document(uid).delete().await() }
      }
      user.delete().await()
      Unit
    }
  }

  /**
   * Il database, quando c'e'.
   *
   * L'account sa vivere anche senza -- e' il caso in cui Firebase non e' configurato -- quindi qui
   * non si costruisce niente: si prende quello gia' acceso, o si lascia perdere.
   */
  private val firestore: com.google.firebase.firestore.FirebaseFirestore?
    get() = runCatching { com.google.firebase.firestore.FirebaseFirestore.getInstance() }.getOrNull()

  private companion object {
    /** Quanto si aspetta il server mentre si cancella: oltre, si dice che non ha funzionato. */
    const val DELETE_TIMEOUT = 30_000L
  }

  /**
   * L'accesso anonimo, **solo contro l'emulatore**.
   *
   * Esiste per una ragione sola: poter provare la rubrica e il trasporto mentre l'accesso con
   * Google e' ancora spento in console. In produzione non deve poter succedere -- un account
   * anonimo non e' recuperabile, e una persona che ci finisse dentro per sbaglio perderebbe le
   * conversazioni al primo cambio di telefono -- quindi qui c'e' un controllo, non una promessa.
   */
  suspend fun signInAnonymouslyForTesting(settings: CloudSettings): Result<String> {
    if (!settings.usesEmulator) {
      return Result.failure(IllegalStateException("accesso anonimo consentito solo con l'emulatore"))
    }
    return runCatching { auth.signInAnonymously().await().user!!.uid }
  }
}

/**
 * La rubrica, su Firestore.
 *
 * Due documenti per persona, e la ragione della coppia e' che servono due domande diverse:
 * `codexIds/{codexId}` risponde a "chi e' questo Codex ID?", `users/{uid}` a "dammi la sua scheda".
 * Tenerli separati permette alle regole di dire che il primo si scrive **una volta sola** -- un
 * identificatore che cambia padrone e' il modo piu' pulito di rubare le conversazioni di qualcuno.
 */
class FirestoreDirectory(
  private val firestore: FirebaseFirestore,
  private val account: CloudAccount,
) : CloudDirectory {

  override suspend fun publish(card: SignedContactCard): Result<Unit> {
    val uid = account.uidOrNull()
      ?: return Result.failure(IllegalStateException("nessun accesso"))
    return runCatching {
      firestore.collection(USERS).document(uid).set(
        mapOf(
          CODEX_ID to card.card.codexId,
          CARD to Blob.fromBytes(card.encode()),
          UPDATED_AT to com.google.firebase.Timestamp.now(),
        ),
        SetOptions.merge(),
      ).await()

      // Il legame Codex ID → account si scrive una volta e non si tocca piu': le regole rifiutano
      // gli aggiornamenti, quindi riscriverlo fallisce ed e' giusto cosi'. L'errore si ignora
      // apposta: chi ha gia' pubblicato una volta non deve vedere un guasto a ogni avvio.
      runCatching {
        firestore.collection(CODEX_IDS).document(card.card.codexId)
          .set(mapOf(UID to uid))
          .await()
      }
      Unit
    }
  }

  override suspend fun lookup(codexId: String): DirectoryLookup {
    if (account.uidOrNull() == null) return DirectoryLookup.SignedOut
    return try {
      val pointer = firestore.collection(CODEX_IDS).document(codexId).get().await()
      val uid = pointer.getString(UID) ?: return DirectoryLookup.NotFound
      val profile = firestore.collection(USERS).document(uid).get().await()
      val bytes = profile.getBlob(CARD)?.toBytes() ?: return DirectoryLookup.NotFound

      // La verifica e' qui, non piu' in la': una scheda che non regge non deve nemmeno risalire.
      val card = ContactCard.decodeVerified(bytes) ?: return DirectoryLookup.Untrusted
      // E deve essere **quella che si era chiesta**: un server che restituisce una scheda valida ma
      // di un'altra persona sarebbe l'attacco piu' ovvio, e costa una riga fermarlo.
      if (card.card.codexId != codexId) return DirectoryLookup.Untrusted
      DirectoryLookup.Found(card)
    } catch (error: FirebaseFirestoreException) {
      if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
        DirectoryLookup.SignedOut
      } else {
        DirectoryLookup.Unreachable
      }
    } catch (error: FirebaseNetworkException) {
      DirectoryLookup.Unreachable
    } catch (error: Exception) {
      DirectoryLookup.Unreachable
    }
  }

  private companion object {
    const val USERS = "users"
    const val CODEX_IDS = "codexIds"
    const val CODEX_ID = "codexId"
    const val CARD = "card"
    const val UID = "uid"
    const val UPDATED_AT = "updatedAt"
  }
}

/**
 * Accende il cloud, e decide cosa fare quando non si accende.
 *
 * **Nessun tipo di Firebase esce da qui.** Sopra questo modulo esistono solo [CloudAccount] e
 * [CloudDirectory], come sopra esistono solo i repository e non i DAO di Room: e' la stessa regola,
 * e vale doppio per una libreria che puo' non esserci.
 *
 * **Codex funziona senza Firebase.** Non come modalita' ridotta d'emergenza: senza account restano
 * le note a se stessi, il pairing con il QR, il rito, le conversazioni locali e tutti i sigilli. Il
 * cloud aggiunge la ricerca per Codex ID e, piu' avanti, la consegna. Per questo qui non si lancia
 * mai: se `google-services.json` manca o i servizi non ci sono, si ripiega e si va avanti. Un'app
 * di messaggistica che non parte per un file di configurazione si rompe nel modo peggiore -- in
 * mano a chi non puo' ripararla.
 */
object CodexCloud {

  private const val TAG = "CodexCloud"

  fun account(context: Context, settings: CloudSettings): CloudAccount {
    if (!available(context)) return OfflineAccount
    return runCatching {
      FirebaseAccount(
        FirebaseAuth.getInstance().apply {
          if (settings.usesEmulator) useEmulator(settings.emulatorHost, settings.authPort)
        },
      ) as CloudAccount
    }.getOrElse {
      Log.w(TAG, "accesso non disponibile: ${it.message}")
      OfflineAccount
    }
  }

  fun directory(
    context: Context,
    settings: CloudSettings,
    account: CloudAccount,
  ): CloudDirectory {
    if (!available(context) || account === OfflineAccount) return NoDirectory
    return runCatching { FirestoreDirectory(firestore(settings), account) as CloudDirectory }
      .getOrElse {
        Log.w(TAG, "rubrica non disponibile: ${it.message}")
        NoDirectory
      }
  }

  fun transport(
    context: Context,
    settings: CloudSettings,
    account: CloudAccount,
  ): CloudTransport {
    if (!available(context) || account === OfflineAccount) return NoTransport
    return runCatching { FirestoreTransport(firestore(settings), account) as CloudTransport }
      .getOrElse {
        Log.w(TAG, "trasporto non disponibile: ${it.message}")
        NoTransport
      }
  }

  /**
   * La posta: le buste indirizzate a una persona sola.
   *
   * Senza Firebase non esiste, e i gruppi restano quelli gia' ricevuti -- una chiave che deve
   * viaggiare ha bisogno di qualcosa che la trasporti, e fingere di averlo sarebbe peggio che
   * dire di no.
   */
  fun inbox(
    context: Context,
    settings: CloudSettings,
    account: CloudAccount,
  ): InboxTransport {
    if (!available(context) || account === OfflineAccount) return NoInbox
    return runCatching { FirestoreInbox(firestore(settings), account) as InboxTransport }
      .getOrElse {
        Log.w(TAG, "posta non disponibile: ${it.message}")
        NoInbox
      }
  }

  /** Le storie: un documento per storia, sotto chi l'ha scritta. */
  fun stories(
    context: Context,
    settings: CloudSettings,
    account: CloudAccount,
  ): CloudStories {
    if (!available(context) || account === OfflineAccount) return NoStories
    return runCatching { FirestoreStories(firestore(settings), account) as CloudStories }
      .getOrElse {
        Log.w(TAG, "storie non disponibili: ${it.message}")
        NoStories
      }
  }

  fun devices(
    context: Context,
    settings: CloudSettings,
    account: CloudAccount,
    preferences: dev.pampa.codex.data.prefs.CodexPreferences,
    appVersion: String,
  ): DeviceRegistry {
    if (!available(context) || account === OfflineAccount) return NoDevices
    return runCatching {
      FirebaseDevices(
        firestore = firestore(settings),
        account = account,
        preferences = preferences,
        appVersion = appVersion,
      ) as DeviceRegistry
    }.getOrElse {
      Log.w(TAG, "notifiche non disponibili: ${it.message}")
      NoDevices
    }
  }

  fun mediaStore(
    context: Context,
    settings: CloudSettings,
    account: CloudAccount,
  ): dev.pampa.codex.data.media.MediaStore {
    if (!available(context) || account === OfflineAccount) {
      return dev.pampa.codex.data.media.NoMediaStore
    }
    return runCatching {
      val storage = com.google.firebase.storage.FirebaseStorage.getInstance().apply {
        if (settings.usesEmulator) useEmulator(settings.emulatorHost, settings.storagePort)
      }
      dev.pampa.codex.data.media.FirebaseMediaStore(storage, account)
        as dev.pampa.codex.data.media.MediaStore
    }.getOrElse {
      Log.w(TAG, "deposito media non disponibile: ${it.message}")
      dev.pampa.codex.data.media.NoMediaStore
    }
  }

  /**
   * Firestore, acceso una volta sola.
   *
   * Puntarlo all'emulatore **dopo** la prima query lancia, quindi rubrica e trasporto devono
   * ricevere lo stesso oggetto gia' configurato invece di costruirsene uno per uno.
   */
  private val firestoreOnce = java.util.concurrent.atomic.AtomicReference<FirebaseFirestore?>(null)

  private fun firestore(settings: CloudSettings): FirebaseFirestore {
    firestoreOnce.get()?.let { return it }
    val instance = FirebaseFirestore.getInstance().apply {
      if (settings.usesEmulator) {
        useEmulator(settings.emulatorHost, settings.firestorePort)
        firestoreSettings = FirebaseFirestoreSettings.Builder(firestoreSettings)
          // Contro l'emulatore la cache locale confonde le prove: si vuole vedere cosa risponde
          // il server, non cosa ricorda il telefono.
          .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
          .build()
      }
    }
    return if (firestoreOnce.compareAndSet(null, instance)) instance else firestoreOnce.get()!!
  }

  /**
   * Se Firebase c'e' davvero su questo dispositivo.
   *
   * `initializeApp` restituisce `null` quando non trova la configurazione e lancia se i servizi
   * Google non ci sono: tutti e due i casi qui diventano "no", che e' una risposta con cui il resto
   * dell'app sa convivere.
   */
  private fun available(context: Context): Boolean = try {
    FirebaseApp.initializeApp(context) != null
  } catch (error: Exception) {
    Log.w(TAG, "Firebase non disponibile: ${error.message}")
    false
  }
}

/** Nessun account, e non ce ne sara' uno: e' cosi' che l'app gira senza Firebase. */
private object OfflineAccount : CloudAccount {
  override val state: StateFlow<AccountState> = MutableStateFlow(AccountState.SignedOut)
  override fun uidOrNull(): String? = null
  override suspend fun signInWithGoogle(idToken: String): Result<String> =
    Result.failure(IllegalStateException("Firebase non disponibile su questo dispositivo"))
  override suspend fun signOut() = Unit
  override suspend fun deleteAccount(): Result<Unit> =
    Result.failure(IllegalStateException("Firebase non disponibile su questo dispositivo"))
}
