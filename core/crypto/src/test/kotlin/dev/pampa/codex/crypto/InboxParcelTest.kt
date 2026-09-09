package dev.pampa.codex.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * La busta indirizzata a una persona sola.
 *
 * E' quella che porta la chiave di un gruppo, ed e' l'unico posto del sistema in cui **un segreto
 * viaggia**. Tutto il resto e' gia' condiviso o nasce da un accordo; qui invece trentadue byte
 * partono da un telefono e arrivano a un altro. Se questa busta si sbaglia, il gruppo intero e'
 * leggibile da qualcun altro.
 */
class InboxParcelTest {

  private val ada = CodexIdentity.generate()
  private val bruno = CodexIdentity.generate()
  private val carla = CodexIdentity.generate()

  private val segreto = "la chiave del gruppo".toByteArray()

  private fun adaScriveABruno(payload: ByteArray = segreto): ByteArray = InboxParcel.seal(
    pairKey = ada.pairKey(bruno.x25519Public),
    signingPrivateKey = ada.ed25519Private,
    kind = InboxParcel.Kind.GROUP_KEY,
    senderCodexId = ada.codexId,
    recipientCodexId = bruno.codexId,
    payload = payload,
  )

  @Test
  fun `chi e' il destinatario la apre`() {
    val busta = adaScriveABruno()
    val aperta = InboxParcel.open(
      pairKey = bruno.pairKey(ada.x25519Public),
      senderEd25519Public = ada.ed25519Public,
      recipientCodexId = bruno.codexId,
      parcel = busta,
    )
    assertEquals(InboxParcel.Kind.GROUP_KEY, aperta.kind)
    assertEquals(ada.codexId, aperta.senderCodexId)
    assertArrayEquals(segreto, aperta.payload)
  }

  @Test
  fun `il contenuto non si vede da fuori`() {
    val busta = adaScriveABruno()
    val leggibile = String(busta, Charsets.ISO_8859_1)
    assertEquals(false, leggibile.contains("la chiave del gruppo"))
  }

  @Test
  fun `un terzo non la apre`() {
    val busta = adaScriveABruno()
    // Carla ha una chiave di coppia con Ada, ma non **quella** chiave di coppia: X25519 fra Ada e
    // Bruno non lo sanno calcolare che Ada e Bruno.
    assertThrows(InboxParcel.Malformed::class.java) {
      InboxParcel.open(
        pairKey = carla.pairKey(ada.x25519Public),
        senderEd25519Public = ada.ed25519Public,
        recipientCodexId = carla.codexId,
        parcel = busta,
      )
    }
  }

  @Test
  fun `una busta per un altro non si accetta`() {
    val busta = adaScriveABruno()
    // Anche avendo per assurdo la chiave giusta: il destinatario e' dentro i dati autenticati, e
    // rispedire a Carla una busta destinata a Bruno non funziona.
    assertThrows(InboxParcel.Malformed::class.java) {
      InboxParcel.open(
        pairKey = bruno.pairKey(ada.x25519Public),
        senderEd25519Public = ada.ed25519Public,
        recipientCodexId = carla.codexId,
        parcel = busta,
      )
    }
  }

  @Test
  fun `una firma di qualcun altro non passa`() {
    val busta = adaScriveABruno()
    assertThrows(InboxParcel.Malformed::class.java) {
      InboxParcel.open(
        pairKey = bruno.pairKey(ada.x25519Public),
        senderEd25519Public = carla.ed25519Public,
        recipientCodexId = bruno.codexId,
        parcel = busta,
      )
    }
  }

  @Test
  fun `cambiare il tipo la rompe`() {
    val busta = adaScriveABruno()
    // Il tipo sta nell'intestazione, che e' firmata **e** autenticata: trasformare una richiesta di
    // ingresso in una consegna di chiave non e' una cosa che si possa fare per strada.
    val manomessa = busta.copyOf()
    manomessa[5] = InboxParcel.Kind.GROUP_JOIN.ordinal.toByte()
    assertThrows(InboxParcel.Malformed::class.java) {
      InboxParcel.open(
        pairKey = bruno.pairKey(ada.x25519Public),
        senderEd25519Public = ada.ed25519Public,
        recipientCodexId = bruno.codexId,
        parcel = manomessa,
      )
    }
  }

  @Test
  fun `il tipo si legge senza aprirla`() {
    assertEquals(InboxParcel.Kind.GROUP_KEY, InboxParcel.kindOf(adaScriveABruno()))
    assertEquals(null, InboxParcel.kindOf("non una busta".toByteArray()))
  }

  @Test
  fun `la chiave di coppia e' la stessa da tutte e due le parti`() {
    assertArrayEquals(ada.pairKey(bruno.x25519Public), bruno.pairKey(ada.x25519Public))
  }
}
