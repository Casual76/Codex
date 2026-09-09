package dev.pampa.codex.crypto

/**
 * Il portachiavi: come una chiave di chat sopravvive allo spegnimento senza tradire il resto.
 *
 * Il database locale e' gia' cifrato, ma la sua chiave sta nell'Android Keystore **senza
 * impronta**, perche' una notifica deve poter scrivere un messaggio con l'app chiusa. Se la chiave
 * di una conversazione stesse li' dentro in chiaro, chi apre il database aprirebbe anche i
 * messaggi, e la promessa di Codex — "anche a database aperto, senza la chiave della chat non si
 * legge niente" — smetterebbe di essere vera nel punto esatto in cui conta.
 *
 * Quindi la chiave viene **avvolta** con una chiave che discende dal seme del portachiavi, che
 * esiste solo dentro il vault, che si apre solo con il segreto o con l'impronta. Il risultato e'
 * che il file cifrato contiene tutto tranne il modo di leggerlo.
 *
 * L'involucro e' legato all'identificatore della conversazione: spostare una riga da una chat
 * all'altra non produce una chiave valida, produce un errore.
 */
object ChatKeyring {

  class Unwrappable(message: String) : Exception(message)

  fun wrap(keyringKey: ByteArray, chatId: String, chatKey: ByteArray): ByteArray {
    require(chatKey.size == Aead.KEY_SIZE) { "chiave di chat non valida" }
    val nonce = Aead.randomNonce()
    return nonce + Aead.encrypt(
      key = keyringKey,
      nonce = nonce,
      plaintext = chatKey,
      associatedData = chatId.toByteArray(Charsets.UTF_8),
    )
  }

  fun unwrap(keyringKey: ByteArray, chatId: String, wrapped: ByteArray): ByteArray {
    if (wrapped.size <= Aead.NONCE_SIZE) throw Unwrappable("involucro troppo corto")
    return try {
      Aead.decrypt(
        key = keyringKey,
        nonce = wrapped.copyOfRange(0, Aead.NONCE_SIZE),
        ciphertext = wrapped.copyOfRange(Aead.NONCE_SIZE, wrapped.size),
        associatedData = chatId.toByteArray(Charsets.UTF_8),
      )
    } catch (error: Aead.DecryptionFailed) {
      throw Unwrappable("involucro non apribile con questo portachiavi")
    }
  }
}
