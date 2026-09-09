package dev.pampa.codex.data.contacts

import dev.pampa.codex.crypto.CodexIdentity
import dev.pampa.codex.crypto.ContactCard
import dev.pampa.codex.crypto.SignedContactCard
import dev.pampa.codex.data.FakeDatabase
import dev.pampa.codex.data.FakeIdentitySource
import dev.pampa.codex.data.chat.ChatRepository
import dev.pampa.codex.data.cloud.CloudDirectory
import dev.pampa.codex.data.cloud.DirectoryLookup
import dev.pampa.codex.data.identity.Keyring
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aggiungere qualcuno **cercandolo**, invece che inquadrandolo.
 *
 * E' la strada per chi non e' nella stessa stanza, ed e' anche l'unica che passa da un server:
 * quindi e' l'unica in cui vale la pena chiedersi cosa succede se il server mente, se non risponde,
 * o se non c'e' nessun accesso. Questi test rispondono a tutte e tre.
 */
class DirectoryTest {

  /** Una rubrica finta: si decide cosa risponde, compreso "una cosa che non torna". */
  private class Rubrica : CloudDirectory {
    var risposta: DirectoryLookup = DirectoryLookup.NotFound
    var pubblicate = mutableListOf<SignedContactCard>()
    var cercati = mutableListOf<String>()

    override suspend fun publish(card: SignedContactCard): Result<Unit> {
      pubblicate += card
      return Result.success(Unit)
    }

    override suspend fun lookup(codexId: String): DirectoryLookup {
      cercati += codexId
      return risposta
    }
  }

  private val db = FakeDatabase()
  private val identity: CodexIdentity = CodexIdentity.generate()
  private val source = FakeIdentitySource(identity, name = "Ada")
  private val keyring = Keyring(db.keyring, source)
  private val chats = ChatRepository(db.chats, db.messages, source, keyring, db.outbox)
  private val rubrica = Rubrica()
  private val contacts = ContactRepository(db.contacts, source, chats, keyring, rubrica)

  private val bruno = CodexIdentity.generate()
  private val schedaDiBruno = ContactCard.of(bruno, name = "Bruno", avatarSeed = 9)

  @Test
  fun `un Codex ID trovato diventa un contatto`() = runTest {
    rubrica.risposta = DirectoryLookup.Found(schedaDiBruno)

    val esito = contacts.add(bruno.codexId)

    assertTrue("esito inatteso: $esito", esito is AddContactResult.Added)
    assertEquals(listOf(bruno.codexId), rubrica.cercati)
    assertEquals("Bruno", contacts.observeContacts().first().single().name)
  }

  @Test
  fun `il Codex ID si accetta anche scritto male`() = runTest {
    rubrica.risposta = DirectoryLookup.Found(schedaDiBruno)
    // Minuscole, spazi al posto dei trattini, e la O al posto dello zero: e' come arriva un
    // identificatore dettato al telefono.
    val dettato = bruno.codexId.lowercase().replace("-", " ").replace('0', 'o')

    assertTrue(contacts.add(dettato) is AddContactResult.Added)
    assertEquals(listOf(bruno.codexId), rubrica.cercati)
  }

  @Test
  fun `una scheda che non e' quella chiesta non entra`() = runTest {
    // Il caso che conta davvero: il server risponde con una scheda valida, ma di un altro. La
    // verifica sta nel FirestoreDirectory; qui si prova che quello che arriva da una rubrica che
    // dice "non c'e'" o "non torna" finisce nello stesso posto, cioe' fuori.
    rubrica.risposta = DirectoryLookup.Untrusted
    assertEquals(AddContactResult.NotFound, contacts.add(bruno.codexId))
    assertTrue(db.contacts.rows.value.isEmpty())
  }

  @Test
  fun `senza rete e senza accesso lo dice, e non sono la stessa cosa`() = runTest {
    rubrica.risposta = DirectoryLookup.Unreachable
    assertEquals(AddContactResult.Unreachable, contacts.add(bruno.codexId))

    rubrica.risposta = DirectoryLookup.SignedOut
    assertEquals(AddContactResult.NeedsAccount, contacts.add(bruno.codexId))

    rubrica.risposta = DirectoryLookup.NotFound
    assertEquals(AddContactResult.NotFound, contacts.add(bruno.codexId))
  }

  @Test
  fun `il proprio Codex ID non si cerca nemmeno`() = runTest {
    assertEquals(AddContactResult.Myself, contacts.add(identity.codexId))
    assertTrue("non deve nemmeno chiedere al server", rubrica.cercati.isEmpty())
  }

  @Test
  fun `un codice-scheda non finisce nella rubrica`() = runTest {
    // Trecento caratteri non sono un Codex ID: si decodificano in casa, senza toccare la rete.
    val codice = dev.pampa.codex.crypto.ContactCardCode.encode(schedaDiBruno)

    assertTrue(contacts.add(codice) is AddContactResult.Added)
    assertTrue("la rubrica non c'entra niente qui", rubrica.cercati.isEmpty())
  }

  @Test
  fun `pubblicare la propria scheda porta il proprio Codex ID`() = runTest {
    assertTrue(contacts.publishMyCard("uid-di-prova").isSuccess)

    val pubblicata = rubrica.pubblicate.single()
    assertEquals(identity.codexId, pubblicata.card.codexId)
    assertEquals("Ada", pubblicata.card.name)
    assertEquals("uid-di-prova", pubblicata.card.uid)
  }
}
