package dev.pampa.codex.ui.account

import dev.pampa.codex.data.cloud.AccountState
import dev.pampa.codex.data.cloud.CloudAccount
import dev.pampa.codex.data.cloud.CodexDevice
import dev.pampa.codex.data.cloud.DeviceRegistry
import dev.pampa.codex.data.contacts.ContactRepository
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Uscire dall'account, quando il server non collabora.
 *
 * Il tasto "Disconnetti" non faceva niente, e il motivo non era nell'account: prima di uscire si
 * toglie il gettone delle notifiche, che e' una scrittura su Firestore -- e **una scrittura
 * Firestore che non arriva al server non fallisce, resta in attesa**. Con un token di accesso
 * scaduto (cosa che capita, e capita proprio quando uno vuole uscire) quella riga aspettava per
 * sempre, e l'uscita non arrivava mai.
 *
 * Questo test tiene ferma la regola che ne e' venuta fuori: **uscire e' la richiesta, togliere il
 * gettone e' cortesia.** Un gettone dimenticato lo raccoglie la Function alla prima notifica non
 * consegnata; un tasto che non disconnette non lo raccoglie nessuno.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SignOutTest {

  private val dispatcher = StandardTestDispatcher()

  private class Conto : CloudAccount {
    private val _state = MutableStateFlow<AccountState>(AccountState.SignedIn("uid-ada"))
    override val state: StateFlow<AccountState> = _state
    override fun uidOrNull(): String? = (_state.value as? AccountState.SignedIn)?.uid
    override suspend fun signInWithGoogle(idToken: String) = Result.success("uid-ada")
    override suspend fun signOut() {
      _state.value = AccountState.SignedOut
    }

    override suspend fun deleteAccount(): Result<Unit> {
      _state.value = AccountState.SignedOut
      return Result.success(Unit)
    }
  }

  /** Il registro dei dispositivi che non risponde: e' il caso che rompeva tutto. */
  private class RegistroMuto(private val esito: Result<Unit>) : DeviceRegistry {
    var provato = false

    override suspend fun register(): Result<Unit> = esito

    override suspend fun unregister(): Result<Unit> {
      provato = true
      return esito
    }

    override fun observe(): Flow<List<CodexDevice>> = flowOf(emptyList())
    override suspend fun revoke(deviceId: String): Result<Unit> = Result.success(Unit)
    override suspend fun currentDeviceId(): String = "questo"
  }

  @Before
  fun before() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun after() {
    Dispatchers.resetMain()
  }

  @Test
  fun `si esce anche se il gettone non si riesce a togliere`() = runTest(dispatcher) {
    val conto = Conto()
    val registro = RegistroMuto(Result.failure(java.io.IOException("il server non risponde")))
    val viewModel = AccountViewModel(conto, contactsStub(), registro)

    viewModel.signOut()
    testScheduler.advanceUntilIdle()

    assertTrue("ci deve avere provato", registro.provato)
    assertEquals(AccountState.SignedOut, conto.state.value)
    assertEquals(false, viewModel.state.value.busy)
  }

  @Test
  fun `si esce anche quando togliere il gettone lancia`() = runTest(dispatcher) {
    val conto = Conto()
    val registro = object : DeviceRegistry {
      override suspend fun register(): Result<Unit> = Result.success(Unit)
      override suspend fun unregister(): Result<Unit> = throw IllegalStateException("scaduto")
      override fun observe(): Flow<List<CodexDevice>> = flowOf(emptyList())
      override suspend fun revoke(deviceId: String): Result<Unit> = Result.success(Unit)
      override suspend fun currentDeviceId(): String = "questo"
    }
    val viewModel = AccountViewModel(conto, contactsStub(), registro)

    viewModel.signOut()
    testScheduler.advanceUntilIdle()

    assertEquals(AccountState.SignedOut, conto.state.value)
  }

  @Test
  fun `due tocchi non fanno due uscite`() = runTest(dispatcher) {
    val conto = Conto()
    val registro = RegistroMuto(Result.success(Unit))
    val viewModel = AccountViewModel(conto, contactsStub(), registro)

    viewModel.signOut()
    // Il secondo tocco arriva mentre il primo sta ancora provando: deve cadere nel vuoto, non
    // accodare una seconda uscita.
    viewModel.signOut()
    testScheduler.advanceUntilIdle()

    assertEquals(AccountState.SignedOut, conto.state.value)
  }

  /**
   * La rubrica non serve a uscire.
   *
   * Passarne una vera vorrebbe dire costruire un database: qui basta che esista, perche' `signOut`
   * non la tocca -- ed e' giusto che non la tocchi, visto che uscire dall'account non cancella
   * niente di quello che sta su questo telefono.
   */
  private fun contactsStub(): ContactRepository = mockk(relaxed = true)
}
