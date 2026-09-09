package dev.pampa.codex.data.cloud

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import dev.pampa.codex.data.prefs.CodexPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Dove bussare quando arriva qualcosa.
 *
 * Registra il gettone di questo telefono sotto il proprio account, e lo toglie quando ci si
 * scollega. E' l'unica cosa che rende possibili le notifiche, ed e' anche l'unico pezzo di Codex
 * che dice a Google "questo telefono usa quest'app" -- inevitabile, se le notifiche le consegna
 * Google, e va detto invece che nascosto.
 *
 * **Il gettone non dice niente di chi lo possiede**: non e' un identificatore del dispositivo, si
 * rigenera da solo, e chi lo avesse potrebbe al massimo far comparire una notifica -- non leggere
 * un messaggio, che resta chiuso in una busta che nessun server apre.
 */
interface DeviceRegistry {
  suspend fun register(): Result<Unit>
  suspend fun unregister(): Result<Unit>

  /**
   * I dispositivi collegati a questo account.
   *
   * Serve a una domanda semplice e importante: **chi altro riceve le mie notifiche?** Un telefono
   * venduto, uno prestato, uno perso: finche' il suo gettone e' qui dentro, il server continua a
   * bussargli. Vederli e' il primo passo per toglierli.
   */
  fun observe(): Flow<List<CodexDevice>>

  /** Toglie un dispositivo dall'elenco: da li' in poi non riceve piu' niente. */
  suspend fun revoke(deviceId: String): Result<Unit>

  /** Quale di quelli in elenco e' questo. */
  suspend fun currentDeviceId(): String
}

/** Un dispositivo collegato, come lo vede una schermata. Nessun segreto: un nome e una data. */
data class CodexDevice(
  val id: String,
  val platform: String,
  val appVersion: String,
  val lastSeen: Long,
)

/** Nessuna registrazione: senza Firebase le notifiche non esistono, e l'app lo sa. */
object NoDevices : DeviceRegistry {
  override suspend fun register(): Result<Unit> =
    Result.failure(IllegalStateException("nessun servizio di notifica"))

  override suspend fun unregister(): Result<Unit> = Result.success(Unit)

  override fun observe(): Flow<List<CodexDevice>> = flowOf(emptyList())

  override suspend fun revoke(deviceId: String): Result<Unit> = Result.success(Unit)

  override suspend fun currentDeviceId(): String = ""
}

class FirebaseDevices(
  private val firestore: FirebaseFirestore,
  private val account: CloudAccount,
  private val preferences: CodexPreferences,
  private val messaging: FirebaseMessaging = FirebaseMessaging.getInstance(),
  private val platform: String = "android",
  private val appVersion: String = "",
) : DeviceRegistry {

  override suspend fun register(): Result<Unit> {
    val uid = account.uidOrNull() ?: return Result.failure(IllegalStateException("nessun accesso"))
    return runCatching {
      withTimeout(TIMEOUT) {
        val token = messaging.token.await()
        firestore.collection(USERS).document(uid)
          .collection(DEVICES).document(preferences.deviceId())
          .set(
            mapOf(
              FCM_TOKEN to token,
              PLATFORM to platform,
              APP_VERSION to appVersion,
              LAST_SEEN to com.google.firebase.Timestamp.now(),
            ),
          )
          .await()
      }
      Log.i(TAG, "dispositivo registrato")
    }
  }

  /**
   * Toglie questo telefono dall'elenco.
   *
   * Si fa **prima** di uscire dall'account, non dopo: dopo non ci sarebbero piu' i permessi per
   * scrivere, e resterebbe un gettone che riceve notifiche per un account che non c'e' piu'.
   *
   * **Ma con un tetto di tempo, e chi chiama non ci si appoggia.** Una scrittura Firestore che non
   * arriva al server non fallisce: resta in attesa, per sempre. Con un token di accesso scaduto --
   * cosa che capita, e capita proprio quando si vuole uscire -- questa riga bastava a rendere
   * "Disconnetti" un tasto che non faceva niente. Un gettone dimenticato e' un fastidio da poco: se
   * ne accorge la Function alla prima notifica non consegnata, e lo toglie lei.
   */
  override suspend fun unregister(): Result<Unit> {
    val uid = account.uidOrNull() ?: return Result.success(Unit)
    return runCatching {
      withTimeout(TIMEOUT) {
        firestore.collection(USERS).document(uid)
          .collection(DEVICES).document(preferences.deviceId())
          .delete()
          .await()
      }
      Unit
    }
  }

  override fun observe(): Flow<List<CodexDevice>> = callbackFlow {
    val uid = account.uidOrNull()
    if (uid == null) {
      trySend(emptyList())
      close()
      return@callbackFlow
    }
    val registration = firestore.collection(USERS).document(uid).collection(DEVICES)
      .addSnapshotListener { snapshot, error ->
        if (error != null) return@addSnapshotListener
        trySend(
          snapshot?.documents.orEmpty().map { document ->
            CodexDevice(
              id = document.id,
              platform = document.getString(PLATFORM).orEmpty(),
              appVersion = document.getString(APP_VERSION).orEmpty(),
              lastSeen = document.getTimestamp(LAST_SEEN)?.toDate()?.time ?: 0L,
            )
          },
        )
      }
    awaitClose { registration.remove() }
  }

  override suspend fun revoke(deviceId: String): Result<Unit> {
    val uid = account.uidOrNull() ?: return Result.failure(IllegalStateException("nessun accesso"))
    return runCatching {
      withTimeout(TIMEOUT) {
        firestore.collection(USERS).document(uid).collection(DEVICES).document(deviceId)
          .delete().await()
      }
      Unit
    }
  }

  override suspend fun currentDeviceId(): String = preferences.deviceId()

  private companion object {
    const val TAG = "CodexDevices"
    const val USERS = "users"
    const val DEVICES = "devices"
    const val FCM_TOKEN = "fcmToken"
    const val PLATFORM = "platform"
    const val APP_VERSION = "appVersion"
    const val LAST_SEEN = "lastSeen"

    /** Quanto si aspetta il server prima di lasciar perdere. */
    const val TIMEOUT = 8_000L
  }
}
