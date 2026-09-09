package dev.pampa.codex.seal

import dev.pampa.codex.model.SealChoice
import dev.pampa.codex.model.SealSpec
import dev.pampa.codex.model.Technique

/**
 * Da seme e scelta del mittente al sigillo che entrambi disegnano.
 *
 * Il seme arriva dal modulo crypto (HKDF della chiave di chat e dell'id del messaggio): qui non si
 * sa da dove venga, si sa solo che e' lo stesso sui due telefoni. E' questo che rende "Sorpresa"
 * una sorpresa identica per chi manda e per chi riceve.
 */
object SealResolver {

  /**
   * @param allowRunes se le rune sono fra le forme possibili.
   *
   * Per una foto non lo sono, e non e' una limitazione tecnica: le rune **sono** il testo scritto in
   * un altro alfabeto, e si aprono sciogliendosi lettera per lettera. Davanti a un'immagine non
   * avrebbero niente da sciogliere. Una foto arriva chiusa in una roccia o in un quadro, che sono i
   * due sigilli che hanno una copertina da togliere.
   */
  fun resolve(seed: Long, choice: SealChoice, allowRunes: Boolean = true): SealSpec {
    val technique = (choice.technique ?: surprise(seed))
      .let { if (!allowRunes && it == Technique.RUNE) coverTechnique(seed) else it }
    val paintingId = when (technique) {
      Technique.PAINTING -> choice.paintingId
      else -> null
    }
    return SealSpec(technique = technique, seed = seed, paintingId = paintingId)
  }

  /** Una delle due forme che hanno una copertina, quando le rune non vanno bene. */
  fun coverTechnique(seed: Long): Technique =
    if (Math.floorMod(seed, 2L) == 0L) Technique.ROCK else Technique.PAINTING

  /** `seed mod 3`, con il modulo sempre positivo: un seme negativo non e' un'eccezione. */
  fun surprise(seed: Long): Technique {
    val entries = Technique.entries
    val index = Math.floorMod(seed, entries.size.toLong()).toInt()
    return entries[index]
  }
}
