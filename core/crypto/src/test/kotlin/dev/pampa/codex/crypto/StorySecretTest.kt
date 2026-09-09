package dev.pampa.codex.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Le chiavi delle storie.
 *
 * Una storia va a **tutti i contatti**, che fra loro non si conoscono e non devono nemmeno sapere
 * chi altro c'e'. E' l'unico posto di Codex in cui la stessa cosa viene chiusa per venti persone
 * diverse, e i modi di sbagliare sono due: dare a qualcuno piu' di quello che gli spetta, o lasciare
 * che un involucro valga per un altro.
 */
class StorySecretTest {

  private val ada = CodexIdentity.generate()
  private val bruno = CodexIdentity.generate()
  private val carla = CodexIdentity.generate()

  @Test
  fun `ogni storia ha la sua chiave`() {
    val prima = StorySecret.keyFor(ada.keyringSeed, "s-1")
    val seconda = StorySecret.keyFor(ada.keyringSeed, "s-2")
    // Con una chiave sola per autore, consegnarla a un contatto vorrebbe dire dargli anche quelle
    // di domani e quelle di ieri.
    assertNotEquals(prima.toList(), seconda.toList())
    assertEquals(Aead.KEY_SIZE, prima.size)
  }

  @Test
  fun `chi la scrive la ricalcola sempre uguale`() {
    // Non si conserva niente: la chiave nasce dal seme e dall'identificatore.
    assertArrayEquals(
      StorySecret.keyFor(ada.keyringSeed, "s-1"),
      StorySecret.keyFor(ada.keyringSeed, "s-1"),
    )
  }

  @Test
  fun `chi riceve apre il proprio involucro`() {
    val chiave = StorySecret.keyFor(ada.keyringSeed, "s-1")
    val involucro = StorySecret.wrapFor(
      pairKey = ada.pairKey(bruno.x25519Public),
      storyId = "s-1",
      authorCodexId = ada.codexId,
      recipientCodexId = bruno.codexId,
      storyKey = chiave,
    )
    val aperta = StorySecret.unwrap(
      pairKey = bruno.pairKey(ada.x25519Public),
      storyId = "s-1",
      authorCodexId = ada.codexId,
      myCodexId = bruno.codexId,
      wrapped = involucro,
    )
    assertArrayEquals(chiave, aperta)
  }

  @Test
  fun `l'involucro di un altro non si apre`() {
    val chiave = StorySecret.keyFor(ada.keyringSeed, "s-1")
    val perBruno = StorySecret.wrapFor(
      pairKey = ada.pairKey(bruno.x25519Public),
      storyId = "s-1",
      authorCodexId = ada.codexId,
      recipientCodexId = bruno.codexId,
      storyKey = chiave,
    )
    // Carla ha il documento intero -- gli involucri stanno tutti nello stesso posto -- e prova con
    // il suo. Non e' suo.
    assertNull(
      StorySecret.unwrap(
        pairKey = carla.pairKey(ada.x25519Public),
        storyId = "s-1",
        authorCodexId = ada.codexId,
        myCodexId = carla.codexId,
        wrapped = perBruno,
      ),
    )
  }

  @Test
  fun `un involucro non vale per un'altra storia`() {
    val involucro = StorySecret.wrapFor(
      pairKey = ada.pairKey(bruno.x25519Public),
      storyId = "s-1",
      authorCodexId = ada.codexId,
      recipientCodexId = bruno.codexId,
      storyKey = StorySecret.keyFor(ada.keyringSeed, "s-1"),
    )
    assertNull(
      StorySecret.unwrap(
        pairKey = bruno.pairKey(ada.x25519Public),
        storyId = "s-2",
        authorCodexId = ada.codexId,
        myCodexId = bruno.codexId,
        wrapped = involucro,
      ),
    )
  }

  @Test
  fun `un involucro non si riattribuisce a un altro autore`() {
    val involucro = StorySecret.wrapFor(
      pairKey = ada.pairKey(bruno.x25519Public),
      storyId = "s-1",
      authorCodexId = ada.codexId,
      recipientCodexId = bruno.codexId,
      storyKey = StorySecret.keyFor(ada.keyringSeed, "s-1"),
    )
    // Spacciare una storia di Ada per una di Carla non funziona nemmeno se si copia l'involucro.
    assertNull(
      StorySecret.unwrap(
        pairKey = bruno.pairKey(ada.x25519Public),
        storyId = "s-1",
        authorCodexId = carla.codexId,
        myCodexId = bruno.codexId,
        wrapped = involucro,
      ),
    )
  }

  @Test
  fun `dei byte qualsiasi non sono un involucro`() {
    assertNull(
      StorySecret.unwrap(
        pairKey = bruno.pairKey(ada.x25519Public),
        storyId = "s-1",
        authorCodexId = ada.codexId,
        myCodexId = bruno.codexId,
        wrapped = ByteArray(8),
      ),
    )
  }
}
