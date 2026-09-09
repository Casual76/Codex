package dev.pampa.codex.crypto

import java.text.Normalizer

/**
 * Il rito della parola d'ordine: come nasce la chiave di una conversazione fra due persone.
 *
 * Servono **due cose insieme**, e nessuna delle due basta da sola:
 *
 * 1. l'accordo Diffie-Hellman fra le due identita' — che chiude fuori il server, perche' chi
 *    trasporta le schede non ha nessuna delle due chiavi private;
 * 2. una parola detta a voce — che chiude fuori chiunque sia riuscito a farsi passare per l'altro
 *    durante lo scambio delle schede, perche' quella parola non e' mai passata da un cavo.
 *
 * **Come si accorgono di aver sbagliato.** Non c'e' nessun confronto e nessun messaggio "parola
 * errata" che arrivi da qualche parte: dalla chiave nasce il seme dell'emblema, e l'emblema si
 * vede. Due persone che hanno detto la stessa parola vedono **la stessa gemma** sui due schermi;
 * due che hanno capito parole diverse vedono due gemme diverse e lo capiscono da sole in un
 * secondo. E' la verifica piu' onesta possibile: non la fa l'app, la fanno loro.
 *
 * L'identificatore della chat invece **non dipende dalla parola**. E' voluto: se dipendesse, chi
 * sbaglia parola si troverebbe in una conversazione tutta sua e non capirebbe mai perche' l'altro
 * non risponde. Cosi' invece la conversazione e' una sola, i messaggi arrivano, e non si aprono —
 * che e' un guaio visibile e riparabile rifacendo il rito.
 */
object ChatSecret {

  /** Il minimo perche' una parola d'ordine sia una parola e non un rumore. */
  const val MIN_LENGTH = 3

  /**
   * Quello che serve per far vivere una conversazione: dove sta, come si apre, che faccia ha.
   *
   * [key] va azzerata da chi la riceve appena l'ha messa via: e' un segreto, e resta in memoria
   * finche' qualcuno non decide il contrario.
   */
  class Agreement(val chatId: String, val key: ByteArray, val emblemSeed: Long)

  /**
   * Deriva tutto, da tutte e due le parti, con lo stesso risultato.
   *
   * Chi chiama passa la propria meta' privata e la meta' pubblica dell'altro: i due lati partono
   * da materiali diversi e arrivano allo stesso segreto, che e' esattamente il punto di X25519.
   */
  fun agree(
    myX25519Private: ByteArray,
    myCodexId: String,
    peerX25519Public: ByteArray,
    peerCodexId: String,
    passphrase: String,
  ): Agreement {
    val word = normalize(passphrase)
    require(word.length >= MIN_LENGTH) { "parola d'ordine troppo corta" }
    require(myCodexId != peerCodexId) { "non ci si accorda con se stessi" }

    val shared = IdentityKeys.agree(myX25519Private, peerX25519Public)
    val wordBytes = word.toByteArray(Charsets.UTF_8)
    try {
      // Il sale e' lo stesso dai due lati perche' i due identificatori vengono messi in ordine:
      // senza, A e B deriverebbero due chiavi diverse e non lo scoprirebbero mai.
      val salt = pairSalt(myCodexId, peerCodexId)
      val key = Hkdf.derive(shared + wordBytes, "codex-chatkey-v1", Aead.KEY_SIZE, salt)
      return Agreement(
        chatId = chatId(shared, salt),
        key = key,
        emblemSeed = Hkdf.deriveSeed(key, "codex-emblem-v1"),
      )
    } finally {
      shared.fill(0)
      wordBytes.fill(0)
    }
  }

  /**
   * Dove vivrebbe la conversazione con questa persona, parola d'ordine o no.
   *
   * Si puo' chiedere prima del rito, ed e' quello che permette di sapere se una conversazione con
   * un contatto e' gia' stata aperta senza tenere da nessuna parte una tabella che lo dica.
   */
  fun chatIdFor(
    myX25519Private: ByteArray,
    myCodexId: String,
    peerX25519Public: ByteArray,
    peerCodexId: String,
  ): String {
    val shared = IdentityKeys.agree(myX25519Private, peerX25519Public)
    try {
      return chatId(shared, pairSalt(myCodexId, peerCodexId))
    } finally {
      shared.fill(0)
    }
  }

  /**
   * Dove vive la conversazione fra due persone.
   *
   * Nasce dal segreto in comune e non dagli identificatori pubblici: due che si conoscono ci
   * arrivano tutti e due, e nessun altro puo' indovinarlo pur sapendo chi sono.
   */
  private fun chatId(shared: ByteArray, salt: ByteArray): String =
    "d-" + Base32.encode(Hkdf.derive(shared, "codex-chatid-v1", 10, salt)).lowercase()

  private fun pairSalt(a: String, b: String): ByteArray =
    Digests.sha256((if (a <= b) "$a|$b" else "$b|$a").toByteArray(Charsets.UTF_8))

  /**
   * La parola come la sente l'app, non come e' stata battuta.
   *
   * Una parola detta a voce arriva ai due telefoni con maiuscole diverse, con o senza accento, con
   * o senza apostrofo, magari con uno spazio in piu'. Nessuna di queste differenze e' quello che
   * l'utente intendeva dire, quindi nessuna deve cambiare la chiave: restano le lettere e le cifre,
   * separate da spazi singoli.
   */
  fun normalize(passphrase: String): String {
    val stripped = Normalizer.normalize(passphrase, Normalizer.Form.NFD)
      .replace(COMBINING_MARKS, "")
      .lowercase()
    val out = StringBuilder(stripped.length)
    for (character in stripped) {
      if (character.isLetterOrDigit()) {
        out.append(character)
      } else if (out.isNotEmpty() && out.last() != ' ') {
        out.append(' ')
      }
    }
    return out.toString().trim()
  }

  /** Se la parola battuta e' abbastanza da poter chiudere una conversazione. */
  fun isAcceptable(passphrase: String): Boolean = normalize(passphrase).length >= MIN_LENGTH

  private val COMBINING_MARKS = Regex("\\p{Mn}+")
}
