package dev.pampa.codex.seal

import java.text.Normalizer

/**
 * Il cifrario del prof, ricostruito dalla foto della tavola.
 *
 * Questa e' la cosa che la prima Codex aveva sbagliato. Da quella tavola era stata ricavata una
 * corrispondenza numerica (a=10, b=11, ba=..., bb=170...) che ne prendeva la forma e ne buttava via
 * il funzionamento. Un nomenclatore delle cancellerie italiane non e' un alfabeto sostituito: e'
 * cinque cose insieme, e sono le cinque che si vedono nella foto.
 *
 * 1. **L'alfabeto**, ventidue lettere: `a b c d e f g h i l m n o p q r s t v x y z`. Non c'e' la
 *    `u` — la `v` faceva per due, come si scriveva allora — e non ci sono `j`, `k`, `w`. Ci sono
 *    invece `x` e `y`, che l'italiano non usa ma i nomi stranieri si'. Diverse lettere hanno piu'
 *    di un segno, uno sotto l'altro: sono gli omofoni, la prima difesa contro chi conta le
 *    ricorrenze.
 * 2. **Le sillabe**: quindici consonanti per cinque vocali, `ba be bi bo bu` fino a `za ze zi zo
 *    zu`, con la riga della `q` scritta `qua que qui quo quu`. Settantacinque segni che valgono due
 *    lettere l'uno.
 * 3. **Le doppie**: `bb cc dd ff gg ll mm nn pp rr ss tt uu`. Tredici, e in italiano tornano
 *    continuamente.
 * 4. **Il nomenclatore**: le parole che tornano in ogni dispaccio, ognuna con il suo segno. E' la
 *    parte che da' il nome a tutto il sistema.
 * 5. **Le nulle**: nove segni che non vogliono dire niente, sparsi nel testo per far contare male.
 *
 * I segni qui non sono i ghirigori del manoscritto: sono segni tipografici assegnati in modo fisso,
 * che ne rispettano la **struttura**. La tavola vera si guarda nella sezione "Le origini", che ce
 * l'ha dentro.
 *
 * Tutto e' deterministico: lo stesso testo e lo stesso seme danno la stessa cifratura, sempre.
 */
object Nomenclator {

  enum class Kind {
    /** Una parola intera del nomenclatore. */
    WORD,

    /** Una doppia: `bb`, `cc`, ... */
    DOUBLE,

    /** Una sillaba: `ba`, `be`, ... */
    SYLLABLE,

    /** Una lettera sola. */
    LETTER,

    /** Una nulla: non vuole dire niente. */
    NULL,

    /** Lo spazio fra due parole. */
    SPACE,
  }

  /** Una voce della tavola: cosa vale, e con quale segno si scrive. */
  data class Entry(val plain: String, val symbol: String, val kind: Kind)

  /** Un pezzo di testo cifrato: il segno, e cosa nasconde. */
  data class Token(val plain: String, val symbol: String, val kind: Kind)

  /**
   * Le righe delle sillabe, come stanno nella tavola.
   *
   * La `q` ha la sua riga scritta `qua que qui quo quu`: nella tavola le sillabe della `q` portano
   * sempre la `u` dietro, perche' in italiano la `q` da sola non esiste.
   */
  private val SYLLABLE_HEADS =
    listOf("b", "c", "d", "f", "g", "l", "m", "n", "p", "qu", "r", "s", "t", "v", "z")

  private const val VOWELS = "aeiou"

  /** L'alfabeto della tavola: ventidue lettere. Niente `j`, `k`, `u`, `w`. Vedi [fold]. */
  private const val ALPHABET = "abcdefghilmnopqrstvxyz"

  /**
   * Le lettere che nella foto hanno piu' di un segno, uno sotto l'altro.
   *
   * Sono quelle su cui la tavola spende segni in piu', e non a caso: `a` e `i` sono le lettere piu'
   * frequenti dell'italiano. Il numero e' quello che si conta nella foto; l'assegnazione dei
   * singoli ghirigori no, quella richiederebbe l'originale e non una fotocopia fotografata.
   */
  private val HOMOPHONES = mapOf('a' to 4, 'd' to 3, 'i' to 3, 'n' to 3, 'q' to 3)

  private val DOUBLES =
    listOf("bb", "cc", "dd", "ff", "gg", "ll", "mm", "nn", "pp", "rr", "ss", "tt", "uu")

  /**
   * Le parole del nomenclatore, lette dalla foto.
   *
   * Le prime sette si leggono senza dubbi. *Salvestro* e' scritto `Saluestro` — la `u` per la `v`,
   * di nuovo — ed e' un nome di battesimo fiorentino. *Somme* e *Alamannia* stanno nella riga sotto;
   * la voce che le precede non si legge, e resta fuori invece di essere indovinata.
   *
   * Il fatto che *Pisa* e *Firenze* siano voci **distinte**, accanto a *Imperatore*, *Re di Francia*
   * ed *Esercito*, colloca la tavola in una cancelleria toscana ai tempi delle guerre d'Italia.
   * Resta una lettura, non un'attribuzione: il documento non e' ancora stato trovato in nessun
   * archivio.
   */
  private val WORDS = listOf(
    "papa",
    "imperatore",
    "re di francia",
    "pisa",
    "firenze",
    "esercito",
    "domani",
    "salvestro",
    "somme",
    "alamannia",
  )

  /**
   * I segni.
   *
   * Sono composti: una forma di base piu' un segno diacritico, esattamente come faceva la tavola --
   * lo stesso ghirigoro con un punto sopra, una barra, un punto sotto valeva tre cose diverse. Cosi'
   * bastano una quarantina di forme per riempire una tavola di centoquaranta voci senza che due si
   * somiglino al punto da confondersi.
   *
   * Sono scelti fra quelli che qualunque telefono Android sa disegnare: un segno che esce come un
   * rettangolo vuoto non insegna niente a nessuno.
   */
  private val BASE_SHAPES = listOf(
    "Γ", "Δ", "Θ", "Λ", "Ξ", "Π", "Σ", "Φ", "Ψ", "Ω",
    "α", "β", "γ", "δ", "ε", "ζ", "η", "θ", "λ", "μ",
    "ξ", "π", "ρ", "σ", "τ", "φ", "χ", "ψ", "ω",
    "†", "‡", "§", "¶", "¤", "‰",
    "∆", "∇", "∈", "∋", "∏", "∑", "∫", "≈", "≠", "∞",
    "⊕", "⊗", "⊙", "⊥", "⊤", "√",
  )

  /** Niente, punto sopra, barra sopra, punto sotto. */
  private val MARKS = listOf("", "̇", "̄", "̣")

  /** Tutti i segni disponibili, le forme nude per prime. */
  private val SYMBOLS: List<String> = MARKS.flatMap { mark -> BASE_SHAPES.map { it + mark } }

  /** Le voci della tavola, nell'ordine in cui prendono i segni. */
  private val table: List<Entry> = buildTable()

  /** Il nomenclatore: parole intere. */
  val words: List<Entry> = table.filter { it.kind == Kind.WORD }

  /** Le doppie. */
  val doubles: List<Entry> = table.filter { it.kind == Kind.DOUBLE }

  /** Le sillabe, in ordine: `ba be bi bo bu`, `ca ce ci co cu`, ... */
  val syllables: List<Entry> = table.filter { it.kind == Kind.SYLLABLE }

  /** L'alfabeto. Alcune lettere compaiono piu' volte: sono i loro omofoni. */
  val letters: List<Entry> = table.filter { it.kind == Kind.LETTER }

  /** Le nulle. */
  val nulls: List<Entry> = table.filter { it.kind == Kind.NULL }

  /** Quante voci ha la tavola. */
  val size: Int get() = table.size

  private fun buildTable(): List<Entry> {
    val entries = ArrayList<Entry>(SYMBOLS.size)
    var next = 0
    fun take(plain: String, kind: Kind) {
      entries += Entry(plain, SYMBOLS[next], kind)
      next++
    }

    WORDS.forEach { take(it, Kind.WORD) }
    DOUBLES.forEach { take(it, Kind.DOUBLE) }
    for (head in SYLLABLE_HEADS) {
      for (vowel in VOWELS) take("$head$vowel", Kind.SYLLABLE)
    }
    for (letter in ALPHABET) {
      repeat(HOMOPHONES[letter] ?: 1) { take(letter.toString(), Kind.LETTER) }
    }
    repeat(NULL_COUNT) { take("", Kind.NULL) }
    return entries
  }

  /**
   * Le voci raggiungibili per una data forma in chiaro.
   *
   * La `u` non ha una riga sua nell'alfabeto: chi ha scritto la tavola usava la `v` per tutte e
   * due, come si faceva. Ma la `u` c'e' eccome fra le vocali delle sillabe (`bu`, `cu`, `vu`) e fra
   * le doppie (`uu`), quindi non si puo' semplicemente cancellarla dal testo: si lascia dov'e', e
   * quando resta da sola prende il segno della `v`.
   */
  private val byPlain: Map<String, List<Entry>> = buildMap {
    putAll(table.filter { it.kind != Kind.NULL }.groupBy { it.plain })
    get("v")?.let { put("u", it) }
  }

  private val orderedPlains: List<String> =
    byPlain.keys.sortedByDescending { it.length }

  /**
   * Cifra un testo.
   *
   * Va sempre per il boccone piu' lungo: prima una parola del nomenclatore, poi una doppia o una
   * sillaba, e solo alla fine una lettera. E' come lo faceva un segretario, ed e' anche il motivo
   * per cui il cifrato e' piu' corto del chiaro.
   *
   * Le nulle entrano ogni tanto, con la cadenza decisa dal seme. Quello che non si riconosce
   * (numeri, punteggiatura) passa cosi' com'e': una tavola del Cinquecento non aveva un segno per
   * la virgola, e fingere il contrario sarebbe la stessa scorciatoia della prima Codex.
   */
  fun encipher(text: String, seed: Long): List<Token> {
    val folded = fold(text)
    val random = SeededRandom(seed xor 0x4E4F4DL)
    val tokens = ArrayList<Token>(folded.length)
    var index = 0

    while (index < folded.length) {
      val character = folded[index]
      if (character == ' ') {
        tokens += Token(" ", " ", Kind.SPACE)
        index++
        continue
      }

      val match = orderedPlains.firstOrNull { plain ->
        plain.isNotEmpty() && folded.startsWith(plain, index)
      }
      if (match == null) {
        // Fuori tavola: resta com'e'. Meglio un carattere in chiaro che un segno inventato.
        tokens += Token(character.toString(), character.toString(), Kind.LETTER)
        index++
        continue
      }

      val choices = byPlain.getValue(match)
      val entry = choices[random.nextInt(choices.size)]
      tokens += Token(entry.plain, entry.symbol, entry.kind)
      index += match.length

      // Una nulla ogni tanto, mai due di fila e mai a fine parola: li' salterebbe all'occhio.
      if (nulls.isNotEmpty() && index < folded.length && folded[index] != ' ' &&
        random.nextFloat() < NULL_CHANCE
      ) {
        tokens += Token("", nulls[random.nextInt(nulls.size)].symbol, Kind.NULL)
      }
    }
    return tokens
  }

  /** Il cifrato come si legge: i segni attaccati, gli spazi dove stavano. */
  fun cipherText(tokens: List<Token>): String = tokens.joinToString("") { it.symbol }

  /**
   * Il testo ridotto a quello che la tavola conosce.
   *
   * Minuscole, accenti tolti, e le tre lettere che quella tavola proprio non ha portate alla piu'
   * vicina: `j` diventa `i`, `k` diventa `c`, `w` diventa `v`. La `x` e la `y` restano, perche' la
   * tavola ce le ha. La `u` resta, perche' senza non si potrebbero scrivere ne' le sillabe ne' la
   * doppia `uu`. Non e' una comodita': e' quello che faceva chi cifrava, che con *Jacopo* non
   * aveva nessun segno da mettere sotto la `j`.
   */
  fun fold(text: String): String {
    val stripped = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
      .replace(COMBINING, "")
    val builder = StringBuilder(stripped.length)
    var lastWasSpace = true
    for (character in stripped) {
      val mapped = when (character) {
        'j' -> 'i'
        'k' -> 'c'
        'w' -> 'v'
        else -> character
      }
      if (mapped.isWhitespace()) {
        if (!lastWasSpace) {
          builder.append(' ')
          lastWasSpace = true
        }
      } else {
        builder.append(mapped)
        lastWasSpace = false
      }
    }
    return builder.toString().trim()
  }

  private val COMBINING = Regex("\\p{Mn}+")

  /** Quante ne conto nella foto, nella riga che comincia con "Nulle". */
  private const val NULL_COUNT = 9

  private const val NULL_CHANCE = 0.16f
}
