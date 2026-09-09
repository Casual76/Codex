package dev.pampa.codex.crypto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * La stretta di mano fra due telefoni vicini.
 *
 * E' l'unico posto di Codex in cui qualcosa viene **gridato a chiunque sia nel raggio**. Un annuncio
 * Bluetooth lo sente il bar intero: se dentro ci fosse un nome, o un Codex ID, l'app diventerebbe un
 * modo per sapere chi c'e' in una stanza. Meta' di questi test guardano proprio questo -- cosa esce
 * dall'antenna -- e l'altra meta' guardano che nessuno possa farsi passare per un altro.
 */
class NearbyHandshakeTest {

  private val ada = CodexIdentity.generate()
  private val bruno = CodexIdentity.generate()
  private val carla = CodexIdentity.generate()

  private val oggi = NearbyHandshake.dayOf(1_757_000_000_000)

  @Test
  fun `il nome che si annuncia non dice chi si e'`() {
    val nome = NearbyHandshake.advertisedName(ada.keyringSeed, oggi)
    // Nessun pezzo dell'identita' ci finisce dentro.
    assertFalse(nome.contains(ada.codexId.lowercase()))
    assertFalse(nome.contains(ada.codexId.removePrefix("CDX-").take(4).lowercase()))
    assertEquals(NearbyHandshake.advertisedName(ada.keyringSeed, oggi), nome)
  }

  @Test
  fun `domani e' un altro nome`() {
    // Senza questo, un annuncio diventerebbe un identificatore stabile: chi passa due giorni di fila
    // nello stesso bar saprebbe che c'e' la stessa persona.
    val oggi2 = NearbyHandshake.advertisedName(ada.keyringSeed, oggi)
    val domani = NearbyHandshake.advertisedName(ada.keyringSeed, oggi + 1)
    assertNotEquals(oggi2, domani)
  }

  @Test
  fun `due persone diverse hanno nomi diversi`() {
    assertNotEquals(
      NearbyHandshake.advertisedName(ada.keyringSeed, oggi),
      NearbyHandshake.advertisedName(bruno.keyringSeed, oggi),
    )
  }

  @Test
  fun `chi ha la chiave passa`() {
    val sfida = NearbyHandshake.newChallenge()
    val risposta = NearbyHandshake.answer(sfida, ada.codexId, ada.ed25519Private)
    assertTrue(NearbyHandshake.verify(sfida, ada.codexId, ada.ed25519Public, risposta))
  }

  @Test
  fun `chi non ha la chiave non passa`() {
    val sfida = NearbyHandshake.newChallenge()
    // Carla firma dicendo di essere Ada: la firma e' buona, ma non con la chiave di Ada.
    val risposta = NearbyHandshake.answer(sfida, ada.codexId, carla.ed25519Private)
    assertFalse(NearbyHandshake.verify(sfida, ada.codexId, ada.ed25519Public, risposta))
  }

  @Test
  fun `una scheda che non corrisponde al suo Codex ID non passa`() {
    val sfida = NearbyHandshake.newChallenge()
    val risposta = NearbyHandshake.answer(sfida, carla.codexId, carla.ed25519Private)
    // Il Codex ID nasce dalla chiave di firma: presentare la chiave di Carla dicendo di essere Ada
    // non funziona nemmeno prima di guardare la firma.
    assertFalse(NearbyHandshake.verify(sfida, ada.codexId, carla.ed25519Public, risposta))
  }

  @Test
  fun `una risposta non vale per un'altra sfida`() {
    val prima = NearbyHandshake.newChallenge()
    val seconda = NearbyHandshake.newChallenge()
    val risposta = NearbyHandshake.answer(prima, ada.codexId, ada.ed25519Private)
    // E' cio' che impedisce di riusare una risposta sentita ieri: la sfida di oggi e' un'altra.
    assertFalse(NearbyHandshake.verify(seconda, ada.codexId, ada.ed25519Public, risposta))
  }

  @Test
  fun `una risposta non si rigira a un terzo`() {
    // L'attacco vero: Carla e' in mezzo. Riceve la sfida di Bruno, la rigira ad Ada, e prova a
    // presentare la risposta di Ada come propria. Non funziona, perche' quello che Ada ha firmato
    // contiene **il Codex ID di Ada**, non quello di Carla.
    val sfidaDiBruno = NearbyHandshake.newChallenge()
    val rispostaDiAda = NearbyHandshake.answer(sfidaDiBruno, ada.codexId, ada.ed25519Private)
    assertFalse(
      NearbyHandshake.verify(sfidaDiBruno, carla.codexId, carla.ed25519Public, rispostaDiAda),
    )
    // Alla persona giusta invece torna.
    assertTrue(NearbyHandshake.verify(sfidaDiBruno, ada.codexId, ada.ed25519Public, rispostaDiAda))
  }

  @Test
  fun `due sfide di fila non sono mai uguali`() {
    val sfide = (1..500).map { NearbyHandshake.newChallenge().toList() }.toSet()
    assertEquals(500, sfide.size)
  }

  @Test
  fun `il saluto porta chi sono e cosa devi firmare`() {
    val sfida = NearbyHandshake.newChallenge()
    val letto = NearbyHandshake.readHello(NearbyHandshake.hello(ada.codexId, sfida))
    assertEquals(ada.codexId, letto.codexId)
    assertTrue(sfida.contentEquals(letto.challenge))
  }

  @Test
  fun `dei byte qualsiasi non sono un saluto`() {
    // Sul canale puo' arrivare di tutto: qui il "no" deve essere un rifiuto, non un'eccezione
    // qualsiasi che nessuno ha previsto.
    assertThrows(NearbyHandshake.Refused::class.java) {
      NearbyHandshake.readHello("ciao come stai".toByteArray())
    }
    assertThrows(NearbyHandshake.Refused::class.java) {
      NearbyHandshake.readHello(ByteArray(0))
    }
  }
}
