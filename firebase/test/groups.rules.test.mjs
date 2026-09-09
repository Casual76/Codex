import { readFileSync } from 'node:fs'
import { after, before, describe, it } from 'node:test'
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing'
import { Bytes, doc, deleteDoc, getDoc, setDoc, updateDoc } from 'firebase/firestore'

/**
 * Le regole dei gruppi e della posta.
 *
 * Un gruppo e' l'unico posto di Codex in cui **una lista di membri cambia nel tempo**, e quindi
 * l'unico in cui il server ha qualcosa da difendere oltre al "chi c'era all'inizio". La chiave non
 * la protegge lui -- viaggia cifrata nella posta, e lui non la vede mai -- ma se chiunque potesse
 * riscrivere la lista dei membri potrebbe svuotare il gruppo di qualcun altro, o infilarcisi.
 *
 * La posta e' l'opposto: ci scrive chiunque, apposta. E' spiegato nelle regole, e qui si prova.
 */

const PROJECT = 'codex-test'
const ADA = 'uid-ada'
const BRUNO = 'uid-bruno'
const CARLA = 'uid-carla'
const GRUPPO = 'g-prova'

const bytes = (array) => Bytes.fromUint8Array(array)

let env

before(async () => {
  env = await initializeTestEnvironment({
    projectId: PROJECT,
    firestore: {
      rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8'),
      host: '127.0.0.1',
      port: 8099,
    },
  })
})

after(async () => {
  await env?.cleanup()
})

const as = (uid) => env.authenticatedContext(uid).firestore()

/** Un gruppo che esiste gia', scritto scavalcando le regole. */
async function gruppo(membri = [ADA, BRUNO, CARLA], admin = [ADA]) {
  await env.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), 'chats', GRUPPO), {
      type: 'group',
      memberUids: membri,
      adminUids: admin,
      lastActivityAt: 1,
    })
  })
}

describe('chats/{chatId} — i gruppi', () => {
  it('lo crea chi ci sta dentro e lo comanda', async () => {
    await assertSucceeds(
      setDoc(doc(as(ADA), 'chats', 'g-nuovo'), {
        type: 'group',
        memberUids: [ADA, BRUNO],
        adminUids: [ADA],
        lastActivityAt: 1,
      }),
    )
  })

  it('un gruppo senza padrone non nasce', async () => {
    // Nessuno potrebbe piu' toglierne nessuno, e la rotazione della chiave -- che e' l'unica cosa
    // che protegge davvero chi resta -- non avrebbe piu' un responsabile.
    await assertFails(
      setDoc(doc(as(ADA), 'chats', 'g-senza-padrone'), {
        type: 'group',
        memberUids: [ADA, BRUNO],
        adminUids: [BRUNO],
        lastActivityAt: 1,
      }),
    )
  })

  it('non si fa un gruppo fra altri due', async () => {
    await assertFails(
      setDoc(doc(as(ADA), 'chats', 'g-altrui'), {
        type: 'group',
        memberUids: [BRUNO, CARLA],
        adminUids: [BRUNO],
        lastActivityAt: 1,
      }),
    )
  })

  it('i membri li cambia chi comanda', async () => {
    await gruppo()
    await assertSucceeds(
      updateDoc(doc(as(ADA), 'chats', GRUPPO), { memberUids: [ADA, BRUNO] }),
    )
  })

  it('chi e dentro ma non comanda non tocca la lista', async () => {
    await gruppo()
    // Senza questa riga chiunque potrebbe svuotare il gruppo di qualcun altro.
    await assertFails(
      updateDoc(doc(as(BRUNO), 'chats', GRUPPO), { memberUids: [ADA, BRUNO] }),
    )
  })

  it('il comando non si prende scrivendoselo', async () => {
    await gruppo()
    // Chi decide sono gli admin di **adesso**: se contasse la lista in arrivo, basterebbe mettersi
    // dentro quella per averne il diritto. E' l'errore piu' facile da fare in una regola cosi'.
    await assertFails(
      updateDoc(doc(as(BRUNO), 'chats', GRUPPO), { adminUids: [ADA, BRUNO] }),
    )
  })

  it("chi e' dentro segna l'ultima attivita', e nient'altro", async () => {
    await gruppo()
    await assertSucceeds(updateDoc(doc(as(BRUNO), 'chats', GRUPPO), { lastActivityAt: 2 }))
    await assertFails(updateDoc(doc(as(CARLA), 'chats', GRUPPO), { adminUids: [CARLA] }))
  })

  it('chi non ne fa parte non lo legge', async () => {
    await gruppo([ADA, BRUNO], [ADA])
    await assertSucceeds(getDoc(doc(as(BRUNO), 'chats', GRUPPO)))
    await assertFails(getDoc(doc(as(CARLA), 'chats', GRUPPO)))
  })

  it('i messaggi li scrive chi ne fa parte', async () => {
    await gruppo()
    await assertSucceeds(
      setDoc(doc(as(CARLA), 'chats', GRUPPO, 'messages', 'm-1'), {
        senderUid: CARLA,
        senderCodexId: 'CDX-CCCC-3333',
        envelope: bytes(new Uint8Array([9, 9])),
        viewOnce: false,
        createdAt: 1,
      }),
    )
  })

  it('chi e stato tolto non ci scrive piu', async () => {
    await gruppo([ADA, BRUNO], [ADA])
    // La chiave nuova non ce l'ha comunque: questa regola serve a non lasciarle nemmeno riempire
    // la conversazione di rumore.
    await assertFails(
      setDoc(doc(as(CARLA), 'chats', GRUPPO, 'messages', 'm-2'), {
        senderUid: CARLA,
        senderCodexId: 'CDX-CCCC-3333',
        envelope: bytes(new Uint8Array([9, 9])),
        viewOnce: false,
        createdAt: 1,
      }),
    )
  })
})

describe('inbox/{uid}/items — la posta', () => {
  const item = (fromUid, size = 32) => ({
    fromUid,
    parcel: bytes(new Uint8Array(size).fill(5)),
    createdAt: 1,
  })

  it('chi ha un account scrive a chiunque', async () => {
    // Sembra largo e non lo e': la busta e' cifrata con la chiave di coppia e firmata, quindi al
    // massimo si recapita del rumore che il destinatario buttera' via. Restringere ai soli contatti
    // vorrebbe dire tenere sul server chi conosce chi, che e' proprio cio' che Codex non gli lascia
    // sapere.
    await assertSucceeds(setDoc(doc(as(ADA), 'inbox', BRUNO, 'items', 'i-1'), item(ADA)))
  })

  it('non si scrive a nome di un altro', async () => {
    await assertFails(setDoc(doc(as(ADA), 'inbox', BRUNO, 'items', 'i-2'), item(BRUNO)))
  })

  it('la legge e la cancella solo il proprietario', async () => {
    await env.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), 'inbox', BRUNO, 'items', 'i-3'), item(ADA))
    })
    await assertSucceeds(getDoc(doc(as(BRUNO), 'inbox', BRUNO, 'items', 'i-3')))
    // Chi ha scritto la busta non puo' rileggerla: una volta consegnata non e' piu' sua.
    await assertFails(getDoc(doc(as(ADA), 'inbox', BRUNO, 'items', 'i-3')))
    await assertFails(deleteDoc(doc(as(ADA), 'inbox', BRUNO, 'items', 'i-3')))
    await assertSucceeds(deleteDoc(doc(as(BRUNO), 'inbox', BRUNO, 'items', 'i-3')))
  })

  it('una busta consegnata non si riscrive', async () => {
    await env.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), 'inbox', BRUNO, 'items', 'i-4'), item(ADA))
    })
    await assertFails(updateDoc(doc(as(ADA), 'inbox', BRUNO, 'items', 'i-4'), { createdAt: 2 }))
  })

  it('non si riempie la posta di qualcuno', async () => {
    // Ottomila byte bastano a una chiave di gruppo con dentro la lista dei membri, e non bastano a
    // usare la casella di qualcun altro come deposito.
    await assertFails(setDoc(doc(as(ADA), 'inbox', BRUNO, 'items', 'i-5'), item(ADA, 9000)))
  })
})
