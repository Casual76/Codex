package dev.pampa.codex.data.identity

import dev.pampa.codex.crypto.CodexIdentity
import dev.pampa.codex.crypto.SignedContactCard

/**
 * Chi sa qual e' l'identita' aperta in questo momento.
 *
 * E' l'unica cosa che i repository dei dati chiedono davvero a [IdentityRepository]: non il vault,
 * non Argon2id, non l'Android Keystore. Averla scritta come interfaccia non e' architettura per
 * gusto -- e' cio' che permette di provare **il pairing intero** su una macchina da compilazione,
 * con due identita' finte al posto di due telefoni.
 *
 * Senza, l'unico modo di sapere se due lati arrivano alla stessa chiave sarebbe avere due
 * dispositivi in mano e due persone che si parlano; con, e' un test che gira in mezzo secondo a
 * ogni build.
 */
interface CodexIdentitySource {

  /** L'identita' aperta, oppure `null` se l'app e' bloccata. */
  fun identityOrNull(): CodexIdentity?

  /** L'identita' aperta; lancia se non c'e'. Per i punti in cui la serratura e' gia' stata forzata. */
  fun requireIdentity(): CodexIdentity

  /** La scheda pubblica firmata di chi usa questo dispositivo. */
  suspend fun signedCard(uid: String = ""): SignedContactCard?

  /**
   * Quando l'identita' si apre e quando si richiude.
   *
   * Serve a chi deve **ripartire allo sblocco** invece di guardare una volta sola e rassegnarsi.
   * Le vicinanze sono il caso da cui e' nato: il motore leggeva l'identita' all'avvio, la trovava
   * chiusa -- l'app parte sempre bloccata -- e non riprovava piu'. L'antenna non si accendeva mai,
   * e non c'era niente che lo dicesse.
   *
   * Il valore e' `true` quando c'e' un'identita' aperta. Non esce niente di segreto: e' un
   * interruttore.
   */
  val unlocked: kotlinx.coroutines.flow.Flow<Boolean>
}
