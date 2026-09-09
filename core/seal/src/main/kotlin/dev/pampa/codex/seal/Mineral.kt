package dev.pampa.codex.seal

/**
 * Un minerale: la geometria e i colori di una gemma o di una pietra, in coordinate normalizzate.
 *
 * Non e' un'immagine e non conosce Compose. Il generatore produce numeri, il renderer li disegna:
 * cosi' lo stesso minerale finisce in un avatar da 40 dp, in una bolla di chat da 140 dp e in una
 * notifica, senza tre implementazioni che divergono.
 *
 * Le coordinate stanno in `[0, 1]` con l'origine in alto a sinistra, come in ogni tela.
 */
data class Mineral(
  val seed: Long,
  val family: MineralFamily,
  val palette: MineralPalette,
  /** Il contorno esterno, in senso orario. Serve al bordo e all'alone. */
  val outline: List<MineralPoint>,
  /** Le facce, gia' in ordine di disegno: la prima sta dietro. */
  val facets: List<MineralFacet>,
  /** Il riflesso speculare, quando la famiglia lo prevede. */
  val highlight: List<MineralPoint>,
)

data class MineralPoint(val x: Float, val y: Float)

/** Una faccia: un poligono e la sua tinta, gia' scurita o schiarita dalla luce. */
data class MineralFacet(
  val points: List<MineralPoint>,
  /** Colore ARGB. Vedi la nota sui colori in [MineralPalette]. */
  val color: Int,
)

enum class MineralFamily {
  /** Prismi con facce nette: il cristallo di rocca, l'ametista, l'ossidiana lucidata. */
  CRYSTAL,

  /** Sagome irregolari, opache, con venature: il sasso, l'arenaria, il basalto. */
  STONE,
}

/**
 * Le tinte di un minerale.
 *
 * **Questi colori sono contenuto, non interfaccia.** La regola del design system dice che nessuna
 * schermata scrive un colore a mano, e resta vera: un'ametista pero' e' viola perche' e' un'ametista,
 * non perche' il tema di oggi e' viola, e un basalto grigio in un tema verde deve restare grigio.
 * Vivono qui, in un modulo che non conosce il tema, apposta. Il renderer puo' accordarli
 * all'accento con [MineralPalette.tinted] quando la schermata lo chiede.
 */
data class MineralPalette(
  val name: String,
  /** Il colore pieno della faccia in luce. */
  val light: Int,
  /** Il colore della faccia in ombra. */
  val dark: Int,
  /** Il bordo e le venature. */
  val edge: Int,
  /** L'alone dietro la sagoma. */
  val glow: Int,
) {

  /**
   * La stessa tavolozza avvicinata a un altro colore. `amount` a 0.2 e' quanto basta perche' i
   * minerali appartengano al tema senza smettere di essere minerali.
   */
  fun tinted(target: Int, amount: Float): MineralPalette = copy(
    light = mix(light, target, amount),
    dark = mix(dark, target, amount),
    edge = mix(edge, target, amount),
    glow = mix(glow, target, amount),
  )

  companion object {
    /** Le tavolozze di Codex: sette minerali che si riconoscono a colpo d'occhio. */
    val ALL: List<MineralPalette> = listOf(
      MineralPalette("ametista", 0xFFB68BEA.toInt(), 0xFF4A2A8A.toInt(), 0xFFEADCFF.toInt(), 0xFF8E63D9.toInt()),
      MineralPalette("quarzo fumé", 0xFFB9A79C.toInt(), 0xFF4A3C34.toInt(), 0xFFF0E6DE.toInt(), 0xFF8A756A.toInt()),
      MineralPalette("ossidiana", 0xFF6E6A78.toInt(), 0xFF17141E.toInt(), 0xFFCFC9DA.toInt(), 0xFF3B3646.toInt()),
      MineralPalette("smeraldo", 0xFF6FD3A6.toInt(), 0xFF17563F.toInt(), 0xFFDCFFF0.toInt(), 0xFF2E8C68.toInt()),
      MineralPalette("citrino", 0xFFE8C069.toInt(), 0xFF7A5312.toInt(), 0xFFFFF1D6.toInt(), 0xFFC29233.toInt()),
      MineralPalette("acquamarina", 0xFF7FC9E4.toInt(), 0xFF1B5468.toInt(), 0xFFDDF6FF.toInt(), 0xFF3C8CA8.toInt()),
      MineralPalette("granato", 0xFFD97A83.toInt(), 0xFF6B1F2A.toInt(), 0xFFFFE1E4.toInt(), 0xFFA33F4C.toInt()),
    )

    internal fun mix(from: Int, to: Int, amount: Float): Int {
      val t = amount.coerceIn(0f, 1f)
      fun channel(shift: Int): Int {
        val a = (from ushr shift) and 0xFF
        val b = (to ushr shift) and 0xFF
        return (a + (b - a) * t).toInt().coerceIn(0, 255)
      }
      // L'alpha resta quello della tavolozza: accordare la tinta non deve renderla trasparente.
      return (((from ushr 24) and 0xFF) shl 24) or
        (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
  }
}
