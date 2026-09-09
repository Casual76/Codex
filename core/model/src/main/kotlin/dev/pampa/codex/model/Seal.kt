package dev.pampa.codex.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * La forma con cui un messaggio si presenta prima di essere aperto (PIANO.md §4).
 *
 * L'ordine e' significativo: `Sorpresa` pesca fra le tre con `seed mod 3`, quindi aggiungere una
 * tecnica va fatto **in coda** e con un nuovo `SealSpec.version`, altrimenti i messaggi vecchi
 * cambierebbero aspetto sui dispositivi aggiornati.
 */
@Serializable
enum class Technique {
  @SerialName("rune") RUNE,
  @SerialName("rock") ROCK,
  @SerialName("painting") PAINTING,
}

/**
 * Quello che il mittente sceglie per un messaggio: una tecnica precisa, oppure la sorpresa.
 * Viaggia dentro il corpo cifrato; il server non lo vede.
 */
@Serializable
data class SealChoice(
  /** `null` = Sorpresa: la tecnica la decide il seme del messaggio. */
  val technique: Technique? = null,
  /** Solo per [Technique.PAINTING] scelta a mano: l'id del dipinto nella raccolta inclusa. */
  val paintingId: String? = null,
)

/**
 * Il sigillo risolto: cio' che entrambi i lati disegnano. Deterministico dal seme (derivato dalla
 * chiave di chat e dall'id del messaggio) e dalla scelta del mittente.
 */
data class SealSpec(
  val technique: Technique,
  val seed: Long,
  val paintingId: String?,
)
