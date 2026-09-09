package dev.pampa.codex.seal

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * Da un numero a un minerale.
 *
 * Tutto quello che si vede -- famiglia, tavolozza, numero di facce, sagoma, luce -- esce dal seme.
 * Due telefoni con lo stesso seme disegnano la stessa gemma senza scambiarsi niente: e' quello che
 * rende possibile il sigillo "a sorpresa" identico per chi manda e per chi riceve, e l'avatar che
 * nasce dal nome e resta quello per sempre.
 *
 * La luce arriva sempre da in alto a sinistra. Una gemma illuminata a caso non sembra una gemma:
 * sembra un poligono colorato, ed e' la differenza fra le due che questo file cerca di ottenere.
 */
object MineralGenerator {

  private const val CENTER = 0.5f
  private const val MAX_RADIUS = 0.46f

  /** Direzione della luce, normalizzata: da in alto a sinistra. */
  private const val LIGHT_X = -0.55f
  private const val LIGHT_Y = -0.83f

  /** L'avatar di una persona e' sempre un cristallo: le pietre sono per i sigilli. */
  fun avatar(seed: Long): Mineral = generate(seed, MineralFamily.CRYSTAL)

  fun generate(seed: Long, family: MineralFamily? = null): Mineral {
    val random = SeededRandom(seed)
    // Il primo tiro decide la famiglia anche quando e' imposta, cosi' lo stesso seme produce la
    // stessa gemma sia che passi da `avatar` sia che passi da `generate`.
    val rolled = if (random.nextFloat() < 0.6f) MineralFamily.CRYSTAL else MineralFamily.STONE
    val chosen = family ?: rolled
    val palette = random.pick(MineralPalette.ALL)
    return when (chosen) {
      MineralFamily.CRYSTAL -> crystal(seed, random, palette)
      MineralFamily.STONE -> stone(seed, random, palette)
    }
  }

  // --- Cristallo -------------------------------------------------------------------------------

  private fun crystal(seed: Long, random: SeededRandom, palette: MineralPalette): Mineral {
    val sides = random.nextInt(6, 8)
    val radius = random.nextFloat(0.86f, 1f) * MAX_RADIUS
    // Un cristallo e' piu' alto che largo: e' il primo segnale che non e' un bottone rotondo.
    val stretchX = random.nextFloat(0.86f, 0.94f)
    val stretchY = random.nextFloat(1f, 1.08f)
    val startAngle = -PI.toFloat() / 2f + random.nextFloat(-0.2f, 0.2f)

    val outer = ArrayList<MineralPoint>(sides)
    val angles = ArrayList<Float>(sides)
    for (index in 0 until sides) {
      val angle = startAngle + index * 2f * PI.toFloat() / sides + random.nextFloat(-0.09f, 0.09f)
      val length = radius * random.nextFloat(0.88f, 1f)
      angles += angle
      outer += point(angle, length * stretchX, length * stretchY)
    }

    // La tavola: la faccia piatta in cima che i cristalli tagliati hanno. Senza, il prisma resta
    // una piramide vista di fronte, che e' molto meno interessante da guardare.
    val hasTable = random.nextFloat() < 0.65f
    val facets = ArrayList<MineralFacet>(sides + 1)

    if (hasTable) {
      val tableScale = random.nextFloat(0.3f, 0.42f)
      val offsetY = -random.nextFloat(0.02f, 0.06f)
      val inner = List(sides) { index ->
        val base = outer[index]
        MineralPoint(
          x = CENTER + (base.x - CENTER) * tableScale,
          y = CENTER + (base.y - CENTER) * tableScale + offsetY,
        )
      }
      for (index in 0 until sides) {
        val next = (index + 1) % sides
        val quad = listOf(inner[index], inner[next], outer[next], outer[index])
        facets += MineralFacet(quad, shade(palette, quad, random, contrast = 1f))
      }
      // La tavola prende la luce piena: e' la faccia rivolta verso chi guarda.
      facets += MineralFacet(inner, MineralPalette.mix(palette.light, WHITE, 0.22f))
    } else {
      val core = MineralPoint(
        x = CENTER + random.nextFloat(-0.03f, 0.03f),
        y = CENTER + random.nextFloat(-0.08f, -0.01f),
      )
      for (index in 0 until sides) {
        val next = (index + 1) % sides
        val triangle = listOf(core, outer[index], outer[next])
        facets += MineralFacet(triangle, shade(palette, triangle, random, contrast = 1f))
      }
    }

    return Mineral(
      seed = seed,
      family = MineralFamily.CRYSTAL,
      palette = palette,
      outline = outer,
      facets = facets,
      highlight = highlightOn(outer, random),
    )
  }

  /** Il riflesso: un triangolo stretto sulla spalla in luce, mai al centro. */
  private fun highlightOn(outline: List<MineralPoint>, random: SeededRandom): List<MineralPoint> {
    val lit = outline.minByOrNull { it.x + it.y } ?: return emptyList()
    val toward = MineralPoint(CENTER, CENTER)
    val tip = lerp(lit, toward, random.nextFloat(0.12f, 0.2f))
    val wing = lerp(lit, toward, random.nextFloat(0.42f, 0.55f))
    val side = MineralPoint(wing.x + 0.06f, wing.y + 0.02f)
    return listOf(tip, side, wing)
  }

  // --- Pietra ----------------------------------------------------------------------------------

  private fun stone(seed: Long, random: SeededRandom, palette: MineralPalette): Mineral {
    val vertices = random.nextInt(12, 16)
    val radius = MAX_RADIUS * random.nextFloat(0.9f, 1f)

    // Raggi casuali, poi lisciati con i vicini: senza il lisciamento viene una stella, non un sasso.
    val rough = FloatArray(vertices) { random.nextFloat(0.72f, 1f) }
    val smooth = FloatArray(vertices) { index ->
      val previous = rough[(index - 1 + vertices) % vertices]
      val next = rough[(index + 1) % vertices]
      (previous + rough[index] * 2f + next) / 4f
    }
    val outline = List(vertices) { index ->
      val angle = index * 2f * PI.toFloat() / vertices + random.nextFloat(-0.05f, 0.05f)
      point(angle, radius * smooth[index] * 0.98f, radius * smooth[index])
    }

    // Il corpo: una tinta sola. E' il fondo su cui sta tutto il resto, e la sfumatura che il
    // renderer gli mette sopra basta a darle il volume.
    val facets = ArrayList<MineralFacet>(12)
    facets += MineralFacet(outline, MineralPalette.mix(palette.dark, palette.light, 0.54f))

    // Le chiazze: macchie sparse, di tono leggermente diverso, che non convergono da nessuna parte.
    //
    // Il primo tentativo divideva la pietra a spicchi con il vertice al centro, e il risultato non
    // sembrava una pietra: sembrava una fetta di limone. Una superficie minerale ha bande e
    // macchie, non un punto in cui tutto si incontra.
    repeat(random.nextInt(4, 7)) {
      val centerX = random.nextFloat(0.26f, 0.74f)
      val centerY = random.nextFloat(0.26f, 0.74f)
      val spread = random.nextFloat(0.11f, 0.26f)
      // Tanti angoli e poco scarto: e' quello che rende il bordo della macchia una curva sporca
      // invece di un pentagono appoggiato sulla pietra.
      val corners = random.nextInt(10, 14)
      val start = random.nextFloat(0f, 2f * PI.toFloat())
      // Ovale e storto: una macchia tonda si vede che e' un cerchio.
      val squashX = random.nextFloat(0.7f, 1.3f)
      val squashY = random.nextFloat(0.7f, 1.3f)
      val blob = List(corners) { corner ->
        val angle = start + corner * 2f * PI.toFloat() / corners + random.nextFloat(-0.09f, 0.09f)
        val reach = spread * random.nextFloat(0.74f, 1f)
        // Sconfina volentieri fuori dal contorno: il renderer ritaglia sulla sagoma, e una macchia
        // che tocca il bordo e' cio' che le impedisce di sembrare appoggiata sopra.
        MineralPoint(
          x = (CENTER + (centerX - CENTER) + cos(angle) * reach * squashX).coerceIn(0f, 1f),
          y = (CENTER + (centerY - CENTER) + sin(angle) * reach * squashY).coerceIn(0f, 1f),
        )
      }
      facets += MineralFacet(blob, shade(palette, blob, random, contrast = 0.5f))
    }

    // Le venature: quello che fa leggere una macchia grigia come minerale.
    repeat(random.nextInt(1, 3)) {
      val start = random.nextInt(vertices)
      val end = (start + vertices / 2 + random.nextInt(3)) % vertices
      facets += vein(outline[start], outline[end], random, palette)
    }

    return Mineral(
      seed = seed,
      family = MineralFamily.STONE,
      palette = palette,
      outline = outline,
      facets = facets,
      highlight = emptyList(),
    )
  }

  /** Una vena: un quadrilatero sottile che attraversa la pietra passando per un punto spostato. */
  private fun vein(
    from: MineralPoint,
    to: MineralPoint,
    random: SeededRandom,
    palette: MineralPalette,
  ): MineralFacet {
    val middle = MineralPoint(
      x = (from.x + to.x) / 2f + random.nextFloat(-0.08f, 0.08f),
      y = (from.y + to.y) / 2f + random.nextFloat(-0.08f, 0.08f),
    )
    val thickness = random.nextFloat(0.0035f, 0.008f)
    val start = lerp(from, middle, 0.1f)
    val finish = lerp(to, middle, 0.1f)
    // Bassa apposta: una vena chiara e netta si legge come una crepa, e le crepe in questo
    // disegno vogliono dire un'altra cosa (la gemma che sta per rompersi).
    val alpha = (random.nextFloat(0.06f, 0.12f) * 255).toInt()
    return MineralFacet(
      points = listOf(
        MineralPoint(start.x, start.y - thickness),
        MineralPoint(middle.x, middle.y - thickness),
        MineralPoint(finish.x, finish.y - thickness),
        MineralPoint(finish.x, finish.y + thickness),
        MineralPoint(middle.x, middle.y + thickness),
        MineralPoint(start.x, start.y + thickness),
      ),
      color = (palette.edge and 0x00FFFFFF) or (alpha shl 24),
    )
  }

  // --- Comune ----------------------------------------------------------------------------------

  private const val WHITE = 0xFFFFFFFF.toInt()

  private fun point(angle: Float, radiusX: Float, radiusY: Float) = MineralPoint(
    x = (CENTER + cos(angle) * radiusX).coerceIn(0f, 1f),
    y = (CENTER + sin(angle) * radiusY).coerceIn(0f, 1f),
  )

  private fun lerp(from: MineralPoint, to: MineralPoint, amount: Float) = MineralPoint(
    x = from.x + (to.x - from.x) * amount,
    y = from.y + (to.y - from.y) * amount,
  )

  /**
   * Il colore di una faccia: quanto guarda verso la luce.
   *
   * La normale di una faccia piatta vista di fronte non basta a distinguerla dalle altre, quindi si
   * usa la direzione dal centro al suo baricentro: e' una finzione, ma e' la stessa finzione che i
   * disegni di gemme usano da sempre, e a occhio funziona meglio di una normale vera.
   */
  private fun shade(
    palette: MineralPalette,
    polygon: List<MineralPoint>,
    random: SeededRandom,
    contrast: Float,
  ): Int {
    var sumX = 0f
    var sumY = 0f
    for (vertex in polygon) {
      sumX += vertex.x
      sumY += vertex.y
    }
    val centroidX = sumX / polygon.size - CENTER
    val centroidY = sumY / polygon.size - CENTER
    val length = hypot(centroidX, centroidY).coerceAtLeast(0.0001f)
    val dot = (centroidX / length) * LIGHT_X + (centroidY / length) * LIGHT_Y
    // Da [-1, 1] a [0, 1], con una curva che tiene le ombre profonde e i colpi di luce stretti.
    val lit = ((dot + 1f) / 2f).pow(1.35f)
    val jitter = random.nextFloat(-0.05f, 0.05f)
    val amount = (0.5f + (lit - 0.5f) * contrast + jitter).coerceIn(0f, 1f)
    return MineralPalette.mix(palette.dark, palette.light, amount)
  }

  /** L'angolo del baricentro di una faccia: serve al renderer per far volare via le schegge. */
  fun directionOf(facet: MineralFacet): Float {
    var sumX = 0f
    var sumY = 0f
    for (vertex in facet.points) {
      sumX += vertex.x
      sumY += vertex.y
    }
    val x = sumX / facet.points.size - CENTER
    val y = sumY / facet.points.size - CENTER
    return kotlin.math.atan2(y, x)
  }

  /** Quanto dista dal centro il baricentro di una faccia, in unita' normalizzate. */
  fun distanceOf(facet: MineralFacet): Float {
    var sumX = 0f
    var sumY = 0f
    for (vertex in facet.points) {
      sumX += vertex.x
      sumY += vertex.y
    }
    return hypot(sumX / facet.points.size - CENTER, sumY / facet.points.size - CENTER)
  }

  /** Il seme che una stringa produce: l'avatar nasce cosi' dal nome scritto nell'onboarding. */
  fun seedOf(text: String): Long {
    var hash = -0x340d631b7bdddcdbL // FNV-1a a 64 bit
    for (character in text.trim().lowercase()) {
      hash = hash xor character.code.toLong()
      hash *= 0x100000001b3L
    }
    return hash
  }
}
