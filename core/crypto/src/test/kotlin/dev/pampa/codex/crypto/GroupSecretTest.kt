package dev.pampa.codex.crypto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Le chiavi di un gruppo, e cosa viaggia insieme a loro. */
class GroupSecretTest {

  @Test
  fun `due gruppi non si chiamano mai uguale`() {
    val identificatori = (1..500).map { GroupSecret.newId() }.toSet()
    assertEquals(500, identificatori.size)
    assertTrue(identificatori.all { GroupSecret.isGroupId(it) })
  }

  @Test
  fun `un gruppo non si confonde con una chat a due`() {
    assertTrue(GroupSecret.isGroupId(GroupSecret.newId()))
    assertEquals(false, GroupSecret.isGroupId("d-abcdefghij"))
    assertEquals(false, GroupSecret.isGroupId("self-qualcosa"))
  }

  @Test
  fun `l'emblema segue la prima chiave, non l'ultima`() {
    val prima = GroupSecret.newKey()
    // Un gruppo che cambia faccia ogni volta che qualcuno se ne va non si riconosce piu' nella
    // lista: l'emblema nasce dall'epoca zero e li' resta.
    assertEquals(GroupSecret.emblemSeed(prima), GroupSecret.emblemSeed(prima))
    assertNotEquals(GroupSecret.emblemSeed(prima), GroupSecret.emblemSeed(GroupSecret.newKey()))
  }

  @Test
  fun `l'impronta di un invito non riporta al gettone`() {
    val gettone = GroupSecret.newInviteToken()
    val impronta = GroupSecret.inviteFingerprint(gettone)
    assertEquals(impronta, GroupSecret.inviteFingerprint(gettone))
    assertNotEquals(gettone, impronta)
    // Sul server finisce solo l'impronta: chi legge il database non ottiene un invito valido.
    assertEquals(false, impronta.contains(gettone))
  }

  @Test
  fun `la chiave di un gruppo torna indietro com'era`() {
    val payload = GroupKeyPayload(
      groupId = GroupSecret.newId(),
      epoch = 3,
      key = GroupSecret.newKey(),
      name = "Quelli del giovedi'",
      members = listOf(
        GroupKeyPayload.Member("CDX-AAAA-1111", "uid-ada", "Ada", admin = true),
        GroupKeyPayload.Member("CDX-BBBB-2222", "uid-bruno", "Bruno", admin = false),
      ),
      createdAt = 1_700_000_000_000,
    )
    assertEquals(payload, GroupPayloadCodec.decodeKey(GroupPayloadCodec.encode(payload)))
  }

  @Test
  fun `una richiesta di ingresso torna indietro com'era`() {
    val payload = GroupJoinPayload(
      groupId = GroupSecret.newId(),
      token = GroupSecret.newInviteToken(),
      card = ByteArray(120) { it.toByte() },
    )
    assertEquals(payload, GroupPayloadCodec.decodeJoin(GroupPayloadCodec.encode(payload)))
  }

  @Test
  fun `un'uscita torna indietro com'era`() {
    val payload = GroupLeftPayload(GroupSecret.newId(), "CDX-CCCC-3333")
    assertEquals(payload, GroupPayloadCodec.decodeLeft(GroupPayloadCodec.encode(payload)))
  }

  @Test
  fun `dei byte qualsiasi non diventano un gruppo`() {
    // Arrivano da fuori: qui il "no" deve essere un `null`, non un'eccezione che ferma la posta.
    assertNull(GroupPayloadCodec.decodeKey("qualsiasi cosa".toByteArray()))
    assertNull(GroupPayloadCodec.decodeJoin(ByteArray(0)))
    assertNull(GroupPayloadCodec.decodeLeft(byteArrayOf(9, 9, 9)))
  }
}
