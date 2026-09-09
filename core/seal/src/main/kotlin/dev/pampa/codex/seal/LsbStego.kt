package dev.pampa.codex.seal

import java.util.zip.CRC32

/**
 * Il messaggio nascosto dentro un dipinto.
 *
 * Ogni pixel ha tre canali da otto bit; l'ultimo bit di ognuno cambia il colore di una parte su
 * 255, cioe' di niente che un occhio possa vedere. In un dipinto da 1280x1000 pixel ci stanno cosi'
 * quasi cinquecentomila byte: molto piu' di qualsiasi messaggio.
 *
 * Due precisazioni su cosa questo e' e cosa non e':
 *
 * - **Non e' la sicurezza.** Il testo e' gia' cifrato quando arriva qui: nascondere non protegge,
 *   protegge la cifratura. Questo serve a rendere il messaggio *invisibile*, non illeggibile.
 * - **Non sopravvive alla ricompressione.** Un JPEG ricalcola i colori e i bit bassi spariscono, per
 *   questo l'immagine esportata e' un PNG e l'app avvisa di mandarla come file.
 *
 * L'ordine in cui i bit occupano l'immagine dipende dal seme: due messaggi nello stesso dipinto non
 * toccano gli stessi pixel nello stesso ordine.
 */
object LsbStego {

  private val MAGIC = byteArrayOf(
    'C'.code.toByte(),
    'D'.code.toByte(),
    'X'.code.toByte(),
    '3'.code.toByte(),
  )

  /** Firma, lunghezza e CRC32: dodici byte prima del messaggio. */
  private const val HEADER_SIZE = 12

  /** Quanti byte di messaggio entrano in un'immagine di questa misura. */
  fun capacityBytes(width: Int, height: Int): Int = (width * height * 3) / 8 - HEADER_SIZE

  fun fits(width: Int, height: Int, payloadSize: Int): Boolean =
    payloadSize in 0..capacityBytes(width, height)

  /**
   * Scrive il messaggio nei bit meno significativi di [pixels] (ARGB, modificato sul posto).
   *
   * Restituisce `false` se non ci sta: e' una decisione del chiamante, non un errore.
   */
  fun embed(pixels: IntArray, width: Int, height: Int, payload: ByteArray, seed: Long): Boolean {
    if (!fits(width, height, payload.size)) return false
    val checksum = CRC32().apply { update(payload) }.value
    val header = ByteArray(HEADER_SIZE)
    MAGIC.copyInto(header)
    writeInt(header, 4, payload.size)
    writeInt(header, 8, checksum.toInt())
    val bytes = header + payload

    val walk = SlotWalk(width * height * 3, seed)
    for (byte in bytes) {
      for (bit in 7 downTo 0) {
        val value = (byte.toInt() ushr bit) and 1
        val slot = walk.next()
        val pixelIndex = slot / 3
        val shift = (2 - slot % 3) * 8
        val mask = (1 shl shift).inv()
        pixels[pixelIndex] = (pixels[pixelIndex] and mask) or (value shl shift)
      }
    }
    return true
  }

  /** Rilegge il messaggio, o `null` se in quell'immagine non ce n'e' uno (o e' rovinato). */
  fun extract(pixels: IntArray, width: Int, height: Int, seed: Long): ByteArray? {
    val slots = width * height * 3
    if (slots < HEADER_SIZE * 8) return null
    val walk = SlotWalk(slots, seed)
    val header = readBytes(pixels, walk, HEADER_SIZE) ?: return null
    for (index in MAGIC.indices) if (header[index] != MAGIC[index]) return null
    val length = readInt(header, 4)
    if (length < 0 || length > capacityBytes(width, height)) return null
    val payload = readBytes(pixels, walk, length) ?: return null
    val checksum = CRC32().apply { update(payload) }.value.toInt()
    if (checksum != readInt(header, 8)) return null
    return payload
  }

  /** Se in questa immagine c'e' un messaggio di Codex, senza estrarlo tutto. */
  fun contains(pixels: IntArray, width: Int, height: Int, seed: Long): Boolean {
    val slots = width * height * 3
    if (slots < HEADER_SIZE * 8) return false
    val header = readBytes(pixels, SlotWalk(slots, seed), 4) ?: return false
    return header.contentEquals(MAGIC)
  }

  private fun readBytes(pixels: IntArray, walk: SlotWalk, count: Int): ByteArray? {
    if (count < 0 || count * 8 > walk.remaining) return null
    val bytes = ByteArray(count)
    for (index in 0 until count) {
      var value = 0
      repeat(8) {
        val slot = walk.next()
        val shift = (2 - slot % 3) * 8
        value = (value shl 1) or ((pixels[slot / 3] ushr shift) and 1)
      }
      bytes[index] = value.toByte()
    }
    return bytes
  }

  private fun writeInt(target: ByteArray, offset: Int, value: Int) {
    for (index in 0 until 4) target[offset + index] = (value ushr ((3 - index) * 8)).toByte()
  }

  private fun readInt(source: ByteArray, offset: Int): Int {
    var value = 0
    for (index in 0 until 4) value = (value shl 8) or (source[offset + index].toInt() and 0xFF)
    return value
  }

  /**
   * L'ordine in cui i bit occupano l'immagine.
   *
   * Un passo fisso e primo rispetto al numero di posizioni le tocca tutte una volta sola, e costa
   * una moltiplicazione invece di un array da milioni di elementi. Non e' una permutazione
   * qualsiasi, e' una progressione: non deve nascondere niente a nessuno, perche' quello che sta
   * dentro e' gia' cifrato. Serve a non mettere sempre i primi byte nello stesso angolo.
   */
  private class SlotWalk(private val total: Int, seed: Long) {
    private val step: Int = chooseStep(total, seed)
    private var current: Int = Math.floorMod(seed, total.toLong()).toInt()
    private var used = 0

    val remaining: Int get() = total - used

    fun next(): Int {
      val slot = current
      current += step
      if (current >= total) current -= total
      used += 1
      return slot
    }

    private companion object {
      /**
       * Un passo primo rispetto al numero di posizioni, cercato **saltando** invece che contando.
       *
       * La prima versione partiva dal seme e proseguiva di uno finche' non trovava un numero primo
       * rispetto al totale: con un'immagine da 64x64 (12288 posizioni, cioe' 2^12 per 3) tutti i
       * semi finivano sullo stesso 5, e due messaggi con semi diversi occupavano esattamente le
       * stesse posizioni nello stesso ordine. Un test lo ha scoperto. Adesso ogni tentativo e' un
       * altro numero derivato dal seme, quindi semi diversi convergono su passi diversi.
       */
      fun chooseStep(total: Int, seed: Long): Int {
        if (total <= 2) return 1
        var state = seed * 6364136223846793005L + 1442695040888963407L
        var candidate = Math.floorMod(state, (total - 1).toLong()).toInt() + 1
        var guard = 0
        while (gcd(candidate, total) != 1 && guard < 128) {
          state = state * 6364136223846793005L + 1442695040888963407L
          candidate = Math.floorMod(state, (total - 1).toLong()).toInt() + 1
          guard += 1
        }
        return if (gcd(candidate, total) == 1) candidate else 1
      }

      tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    }
  }
}
