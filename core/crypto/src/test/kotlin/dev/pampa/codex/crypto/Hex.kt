package dev.pampa.codex.crypto

/** Da esadecimale a byte, per scrivere i vettori di prova come stanno negli RFC. */
internal fun hex(text: String): ByteArray {
  val cleaned = text.filterNot { it.isWhitespace() }
  require(cleaned.length % 2 == 0) { "esadecimale di lunghezza dispari" }
  return ByteArray(cleaned.length / 2) { index ->
    cleaned.substring(index * 2, index * 2 + 2).toInt(16).toByte()
  }
}
