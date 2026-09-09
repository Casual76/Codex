import { readFileSync } from 'node:fs'
import { after, before, describe, it } from 'node:test'
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing'
import { Bytes, doc, deleteDoc, getDoc, setDoc, updateDoc } from 'firebase/firestore'

/**
 * Le regole delle storie.
 *
 * Una storia e' l'unica cosa di Codex che una persona pubblica **verso molte**, e il documento
 * contiene una chiave avvolta per ciascuna di loro. Le due cose da difendere sono quindi diverse dal
 * solito: che chi non e' nella lista non sappia nemmeno che la storia esiste, e che chi c'e' possa
 * dire "l'ho vista" senza poter toccare nient'altro -- ne' il contenuto, ne' chi l'ha vista prima.
 */

const PROJECT = 'codex-test'
const ADA = 'uid-ada'
const BRUNO = 'uid-bruno'
const CARLA = 'uid-carla'
const STORIA = 's-prova'

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

/** Una storia di Ada, avvolta per Bruno. Scritta scavalcando le regole. */
async function storiaDiAda(perChi = [BRUNO], viewers = []) {
  await env.withSecurityRulesDisabled(async (context) => {
    const involucri = {}
    for (const uid of perChi) involucri[uid] = bytes(new Uint8Array([1, 2, 3]))
    await setDoc(doc(context.firestore(), 'stories', ADA, 'items', STORIA), {
      authorCodexId: 'CDX-AAAA-1111',
      envelope: bytes(new Uint8Array([9, 9, 9])),
      wrappedKeys: involucri,
      viewers,
      createdAt: 1,
    })
  })
}

const storiaNuova = (perChi = [BRUNO], viewers = []) => {
  const involucri = {}
  for (const uid of perChi) involucri[uid] = bytes(new Uint8Array([1, 2, 3]))
  return {
    authorCodexId: 'CDX-AAAA-1111',
    envelope: bytes(new Uint8Array([9, 9, 9])),
    wrappedKeys: involucri,
    viewers,
    createdAt: 1,
  }
}

describe('stories/{uid}/items — le storie', () => {
  it('la pubblica solo chi la scrive', async () => {
    await assertSucceeds(
      setDoc(doc(as(ADA), 'stories', ADA, 'items', 's-mia'), storiaNuova()),
    )
    // Pubblicare sotto il nome di un altro sarebbe far comparire una storia che non ha scritto.
    await assertFails(
      setDoc(doc(as(BRUNO), 'stories', ADA, 'items', 's-falsa'), storiaNuova()),
    )
  })

  it('non nasce con degli spettatori', async () => {
    await assertFails(
      setDoc(doc(as(ADA), 'stories', ADA, 'items', 's-vista'), storiaNuova([BRUNO], [BRUNO])),
    )
  })

  it('la legge chi ha un involucro col suo nome', async () => {
    await storiaDiAda([BRUNO])
    await assertSucceeds(getDoc(doc(as(BRUNO), 'stories', ADA, 'items', STORIA)))
    // E chi l'ha scritta, ovviamente.
    await assertSucceeds(getDoc(doc(as(ADA), 'stories', ADA, 'items', STORIA)))
  })

  it('chi non e nella lista non sa nemmeno che esiste', async () => {
    await storiaDiAda([BRUNO])
    // Non "la legge e non la apre": non la legge proprio. La chiave non ce l'avrebbe comunque, ma
    // sapere che una persona ha pubblicato qualcosa e a quante persone e' gia' qualcosa.
    await assertFails(getDoc(doc(as(CARLA), 'stories', ADA, 'items', STORIA)))
  })

  it('"l\'ho vista" si aggiunge, e solo su di se', async () => {
    await storiaDiAda([BRUNO, CARLA])
    await assertSucceeds(
      updateDoc(doc(as(BRUNO), 'stories', ADA, 'items', STORIA), { viewers: [BRUNO] }),
    )
    // Mettere qualcun altro nella lista sarebbe dire a chi ha scritto una cosa che non e' successa.
    await assertFails(
      updateDoc(doc(as(BRUNO), 'stories', ADA, 'items', STORIA), { viewers: [BRUNO, CARLA] }),
    )
  })

  it('chi guarda non tocca il contenuto', async () => {
    await storiaDiAda([BRUNO])
    await assertFails(
      updateDoc(doc(as(BRUNO), 'stories', ADA, 'items', STORIA), {
        envelope: bytes(new Uint8Array([7])),
      }),
    )
  })

  it('nessuno cancella chi ha guardato prima', async () => {
    await storiaDiAda([BRUNO, CARLA], [CARLA])
    await assertFails(
      updateDoc(doc(as(BRUNO), 'stories', ADA, 'items', STORIA), { viewers: [BRUNO] }),
    )
    await assertSucceeds(
      updateDoc(doc(as(BRUNO), 'stories', ADA, 'items', STORIA), { viewers: [CARLA, BRUNO] }),
    )
  })

  it('la cancella solo chi la scritta', async () => {
    await storiaDiAda([BRUNO])
    await assertFails(deleteDoc(doc(as(BRUNO), 'stories', ADA, 'items', STORIA)))
    await assertSucceeds(deleteDoc(doc(as(ADA), 'stories', ADA, 'items', STORIA)))
  })
})
