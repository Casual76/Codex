package dev.pampa.codex.seal

import kotlin.math.hypot

/**
 * Come si rompe una pietra.
 *
 * Le facce del minerale sono un modo di disegnarlo, non un modo di romperlo: un cristallo che si
 * spacca esattamente lungo le proprie sfaccettature sembra smontato, non frantumato. Le schegge
 * nascono invece da un diagramma di Voronoi — si scelgono dei punti dentro la sagoma e ogni scheggia
 * e' la regione piu' vicina al proprio punto. E' il modo in cui si rompe il vetro, ed e' anche il
 * motivo per cui il risultato non ha mai due schegge uguali.
 *
 * Il calcolo e' un ritaglio successivo: si parte dal contorno e lo si taglia con l'asse fra il
 * proprio punto e ogni altro punto. Nessuna struttura dati sofisticata, e con una dozzina di punti
 * costa meno di un millisecondo.
 */
object Fracture {

  /** Una scheggia: il poligono, il colore che aveva li', e da dove parte quando vola via. */
  data class Shard(
    val points: List<MineralPoint>,
    val color: Int,
    val centerX: Float,
    val centerY: Float,
  ) {
    /** Distanza del baricentro dal centro della sagoma: le schegge esterne partono per prime. */
    val distanceFromCenter: Float get() = hypot(centerX - 0.5f, centerY - 0.5f)
  }

  /**
   * Rompe un minerale in [count] schegge.
   *
   * Il colore di ogni scheggia e' quello della faccia che occupava quel punto, quindi la gemma
   * frantumata ha ancora le sue luci e le sue ombre invece di diventare una macchia uniforme.
   */
  fun shatter(mineral: Mineral, count: Int = 9): List<Shard> {
    val random = SeededRandom(mineral.seed xor 0x5A17)
    val sites = ArrayList<MineralPoint>(count)
    // I punti si distribuiscono con un rifiuto sui troppo vicini: due punti a contatto producono
    // una scheggia grande quanto una scaglia, che a schermo sparisce e lascia un buco.
    var attempts = 0
    while (sites.size < count && attempts < count * 40) {
      attempts += 1
      val candidate = MineralPoint(random.nextFloat(0.16f, 0.84f), random.nextFloat(0.16f, 0.84f))
      if (sites.none { hypot(it.x - candidate.x, it.y - candidate.y) < 0.13f }) sites += candidate
    }

    val outline = mineral.outline
    return sites.mapNotNull { site ->
      var cell: List<MineralPoint> = outline
      for (other in sites) {
        if (other === site) continue
        cell = clipToHalfPlane(cell, site, other)
        if (cell.size < 3) break
      }
      if (cell.size < 3) return@mapNotNull null
      var sumX = 0f
      var sumY = 0f
      for (point in cell) {
        sumX += point.x
        sumY += point.y
      }
      val centerX = sumX / cell.size
      val centerY = sumY / cell.size
      Shard(
        points = cell,
        color = colorAt(mineral, centerX, centerY),
        centerX = centerX,
        centerY = centerY,
      )
    }
  }

  /**
   * Taglia il poligono tenendo la meta' piu' vicina a [keep] che a [drop] (Sutherland-Hodgman
   * contro l'asse del segmento fra i due punti).
   */
  private fun clipToHalfPlane(
    polygon: List<MineralPoint>,
    keep: MineralPoint,
    drop: MineralPoint,
  ): List<MineralPoint> {
    if (polygon.isEmpty()) return polygon
    // L'asse: normale (drop - keep), passante per il punto di mezzo.
    val normalX = drop.x - keep.x
    val normalY = drop.y - keep.y
    val midX = (keep.x + drop.x) / 2f
    val midY = (keep.y + drop.y) / 2f
    fun side(point: MineralPoint) = (point.x - midX) * normalX + (point.y - midY) * normalY

    val result = ArrayList<MineralPoint>(polygon.size + 2)
    for (index in polygon.indices) {
      val current = polygon[index]
      val next = polygon[(index + 1) % polygon.size]
      val currentSide = side(current)
      val nextSide = side(next)
      if (currentSide <= 0f) result += current
      if ((currentSide > 0f) != (nextSide > 0f)) {
        val t = currentSide / (currentSide - nextSide)
        result += MineralPoint(
          x = current.x + (next.x - current.x) * t,
          y = current.y + (next.y - current.y) * t,
        )
      }
    }
    return result
  }

  /** Il colore della faccia che contiene quel punto; se nessuna lo contiene, la piu' vicina. */
  private fun colorAt(mineral: Mineral, x: Float, y: Float): Int {
    for (facet in mineral.facets) {
      if (contains(facet.points, x, y)) return facet.color
    }
    return mineral.facets.minByOrNull { facet ->
      var sumX = 0f
      var sumY = 0f
      for (point in facet.points) {
        sumX += point.x
        sumY += point.y
      }
      hypot(sumX / facet.points.size - x, sumY / facet.points.size - y)
    }?.color ?: mineral.palette.light
  }

  /** Punto dentro poligono, con il conteggio degli attraversamenti. */
  private fun contains(polygon: List<MineralPoint>, x: Float, y: Float): Boolean {
    var inside = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
      val a = polygon[i]
      val b = polygon[j]
      if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) {
        inside = !inside
      }
      j = i
    }
    return inside
  }
}
