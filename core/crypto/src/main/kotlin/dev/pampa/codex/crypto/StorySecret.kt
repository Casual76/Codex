package dev.pampa.codex.crypto

/**
 * Le chiavi di una storia.
 *
 * Una storia e' un messaggio senza destinatario: la scrive una persona e la vedono tutti i suoi
 * contatti. Non c'e' quindi una chiave di conversazione da usare -- ce ne sarebbero venti diverse --
 * e nemmeno una chiave di gruppo, perche' i contatti di qualcuno non sono un gruppo: non si
 * conoscono fra loro e non devono nemmeno sapere chi altro c'e'.
 *
 * Quindi ogni storia ha **una chiave sua**, e quella chiave viene avvolta una volta per ciascun
 * contatto con la chiave di coppia. Chi riceve apre solo il proprio involucro; gli altri involucri,
 * per lui, sono rumore. Il server li porta tutti e non ne apre nessuno.
 *
 * **Una chiave per storia e non una per autore.** Sembra un dettaglio e non lo e': con una chiave
 * sola, consegnarla a un contatto vorrebbe dire dargli quella di tutte le storie -- comprese quelle
 * che verranno pubblicate domani, e comprese quelle di ieri che lui non doveva vedere. La chiave
 * nasce invece dal seme dell'identita' **e dall'identificatore della storia**, quindi chi la scrive
 * la puo' sempre ricalcolare senza conservare niente, e chi la riceve ne ha una e una sola.
 */
object StorySecret {

  /** La chiave di una storia, come la ricava chi l'ha scritta. */
  fun keyFor(keyringSeed: ByteArray, storyId: String): ByteArray =
    Hkdf.derive(keyringSeed, "codex-story-v1|$storyId", Aead.KEY_SIZE)

  /**
   * La chiave, avvolta per una persona sola.
   *
   * L'involucro e' legato a **questa** storia, a chi la scrive e a chi la riceve: passarlo a un
   * altro contatto non produce una chiave, produce un errore. Senza quel legame, un involucro
   * intercettato varrebbe per chiunque riuscisse a calcolare la stessa chiave di coppia.
   */
  fun wrapFor(
    pairKey: ByteArray,
    storyId: String,
    authorCodexId: String,
    recipientCodexId: String,
    storyKey: ByteArray,
  ): ByteArray = ChatKeyring.wrap(pairKey, scope(storyId, authorCodexId, recipientCodexId), storyKey)

  /**
   * L'involucro riaperto, oppure `null`.
   *
   * `null` copre tutti i modi in cui puo' non funzionare -- non e' per noi, e' rovinato, non e' di
   * chi dice -- e per chi guarda lo schermo sono la stessa cosa: quella storia non si apre.
   */
  fun unwrap(
    pairKey: ByteArray,
    storyId: String,
    authorCodexId: String,
    myCodexId: String,
    wrapped: ByteArray,
  ): ByteArray? = runCatching {
    ChatKeyring.unwrap(pairKey, scope(storyId, authorCodexId, myCodexId), wrapped)
  }.getOrNull()

  /** Dove vive una storia dentro la busta: e' l'ambito che la busta autentica. */
  const val ENVELOPE_SCOPE = "codex-story"

  private fun scope(storyId: String, author: String, recipient: String): String =
    "codex-story|$storyId|$author|$recipient"
}
