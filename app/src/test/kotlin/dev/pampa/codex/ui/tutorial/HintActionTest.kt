package dev.pampa.codex.ui.tutorial

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Le regole dei suggerimenti al primo uso.
 *
 * Sono quattro righe di codice e hanno gia' sbagliato due volte sull'emulatore: la prima volta
 * nessun callout compariva, la seconda compariva e spariva dentro lo stesso battito. Da qui in poi
 * a dirlo e' un test invece di uno screenshot.
 */
class HintActionTest {

  private val schermata = CodexHintState(screen = "chat/{chatId}")

  @Test
  fun `un suggerimento nuovo si offre`() {
    assertEquals(
      HintAction.Offri,
      hintAction(CodexHint.SealTap, ready = true, hints = schermata, presentingId = null),
    )
  }

  @Test
  fun `senza una schermata non si offre niente`() {
    // Il padrone di casa accetta un candidato solo per la pagina che ha in scena: offrirlo prima
    // che il cambio di rotta sia arrivato lo fa sparire senza dire niente a nessuno.
    assertEquals(
      HintAction.Ritira,
      hintAction(CodexHint.SealTap, ready = true, hints = CodexHintState(), presentingId = null),
    )
  }

  @Test
  fun `quello gia' visto non si ripete`() {
    val visto = schermata.copy(seen = setOf(CodexHint.SealTap))
    assertEquals(
      HintAction.Ritira,
      hintAction(CodexHint.SealTap, ready = true, hints = visto, presentingId = null),
    )
  }

  @Test
  fun `quello in scena non si toglie di mezzo`() {
    // Compare, e nello stesso istante viene segnato visto: se quel segno lo ritirasse, il callout
    // durerebbe un fotogramma.
    val visto = schermata.copy(seen = setOf(CodexHint.SealTap))
    assertEquals(
      HintAction.Lascia,
      hintAction(CodexHint.SealTap, ready = true, hints = visto, presentingId = CodexHint.SealTap),
    )
  }

  @Test
  fun `chi ha detto basta non ne vede piu'`() {
    val zitto = schermata.copy(silent = true)
    assertEquals(
      HintAction.Ritira,
      hintAction(CodexHint.SendHold, ready = true, hints = zitto, presentingId = null),
    )
  }

  @Test
  fun `un suggerimento senza piu' senso si ritira`() {
    // Il sigillo e' stato aperto mentre il suggerimento aspettava in coda: indicarlo adesso
    // vorrebbe dire indicare un messaggio gia' letto.
    assertEquals(
      HintAction.Ritira,
      hintAction(CodexHint.SealTap, ready = false, hints = schermata, presentingId = null),
    )
  }
}
