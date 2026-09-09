import { readFileSync } from 'node:fs'
import { after, before, describe, it } from 'node:test'
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing'
import { doc, setDoc } from 'firebase/firestore'
import { deleteObject, getBytes, ref, uploadBytes } from 'firebase/storage'

/**
 * Le regole dello Storage, provate come le prova qualcuno che ci sta girando intorno.
 *
 * Qui dentro passa **rumore**: ogni file e' cifrato a blocchi con una chiave che nasce dalla
 * conversazione, e nemmeno noi possiamo aprirlo. Quindi queste regole non difendono il contenuto:
 * difendono chi puo' scaricare e chi puo' caricare. Sono due cose diverse, e la seconda e' quella
 * che tiene lo spazio di qualcuno al riparo da chiunque abbia un account.
 *
 * La parte che vale la pena provare davvero e' che l'appartenenza alla conversazione viene chiesta
 * **a Firestore**: una regola che attraversa due servizi non si legge, si prova. Per questo qui
 * gira anche l'emulatore Firestore, e ogni prova comincia scrivendoci la chat.
 *
 *   bash tools/regole.sh
 */

const PROJECT = 'codex-test'
const ADA = 'uid-ada'
const BRUNO = 'uid-bruno'
const CARLA = 'uid-carla'
const CHAT = 'd-prova'

/** Un file cifrato qualsiasi: qui conta quanto pesa e come si chiama, non cosa contiene. */
const file = (size = 32) => new Uint8Array(size).fill(7)
const OCTET = { contentType: 'application/octet-stream' }

let env

before(async () => {
  env = await initializeTestEnvironment({
    projectId: PROJECT,
    firestore: {
      rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8'),
      host: '127.0.0.1',
      port: 8099,
    },
    storage: {
      rules: readFileSync(new URL('../storage.rules', import.meta.url), 'utf8'),
      host: '127.0.0.1',
      port: 9299,
    },
  })
})

after(async () => {
  await env?.cleanup()
})

const as = (uid) => env.authenticatedContext(uid).storage()
const anonymous = () => env.unauthenticatedContext().storage()

/** La conversazione com'e' scritta su Firestore: e' da li' che le regole leggono i membri. */
async function chatWith(members) {
  await env.clearStorage()
  await env.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), 'chats', CHAT), {
      memberUids: members,
      createdAt: Date.now(),
    })
  })
}

/** Un file gia' caricato, messo li' senza passare dalle regole. */
async function existingFile(mediaId = 'm-1') {
  await env.withSecurityRulesDisabled(async (context) => {
    await uploadBytes(ref(context.storage(), `media/${CHAT}/${mediaId}`), file(), OCTET)
  })
}

describe("media/{chatId}/{mediaId} — chi puo' entrare", () => {
  it('un membro carica il suo file', async () => {
    await chatWith([ADA, BRUNO])
    await assertSucceeds(
      uploadBytes(ref(as(ADA), `media/${CHAT}/m-1`), file(), OCTET),
    )
  })

  it('un membro scarica quello che gli hanno mandato', async () => {
    await chatWith([ADA, BRUNO])
    await existingFile()
    await assertSucceeds(getBytes(ref(as(BRUNO), `media/${CHAT}/m-1`)))
  })

  it("chi non e' della conversazione non scarica niente", async () => {
    await chatWith([ADA, BRUNO])
    await existingFile()
    // Sarebbe illeggibile comunque: la chiave non ce l'ha. Ma "illeggibile" non e' "non l'ho
    // avuto", e la differenza si vede il giorno in cui una chiave viene fuori.
    await assertFails(getBytes(ref(as(CARLA), `media/${CHAT}/m-1`)))
  })

  it("chi non e' della conversazione non ci carica dentro", async () => {
    await chatWith([ADA, BRUNO])
    await assertFails(uploadBytes(ref(as(CARLA), `media/${CHAT}/m-2`), file(), OCTET))
  })

  it('senza accesso non si fa niente', async () => {
    await chatWith([ADA, BRUNO])
    await existingFile()
    await assertFails(getBytes(ref(anonymous(), `media/${CHAT}/m-1`)))
    await assertFails(uploadBytes(ref(anonymous(), `media/${CHAT}/m-3`), file(), OCTET))
  })

  it('una conversazione che non esiste non lascia caricare niente', async () => {
    // La regola chiede a Firestore, e su un documento che non c'e' `get` manda in errore la
    // valutazione: `exists` prima di `get` e' cio' che rende questo un "no" e non un guasto.
    await env.clearStorage()
    await assertFails(uploadBytes(ref(as(ADA), 'media/d-inventata/m-1'), file(), OCTET))
  })
})

describe("media — cosa si puo' mettere", () => {
  it("un file che non e' rumore non entra", async () => {
    await chatWith([ADA, BRUNO])
    // Tutto sale come `application/octet-stream`. E' quello che impedisce a chi guarda il deposito
    // di dividere le foto dalle note vocali senza aprire niente -- e non si puo' lasciare che sia
    // il client a decidere di essere sincero.
    await assertFails(
      uploadBytes(ref(as(ADA), `media/${CHAT}/m-4`), file(), { contentType: 'image/jpeg' }),
    )
  })

  it('oltre trenta megabyte si dice di no', async () => {
    await chatWith([ADA, BRUNO])
    await assertFails(
      uploadBytes(ref(as(ADA), `media/${CHAT}/m-5`), file(31 * 1024 * 1024), OCTET),
    )
  })

  it("un file gia' caricato non si riscrive", async () => {
    await chatWith([ADA, BRUNO])
    await existingFile('m-6')
    // Un media e' immutabile per costruzione: il suo nome e' quello del messaggio che lo porta.
    // Poterlo sostituire vorrebbe dire cambiare un messaggio dopo che e' stato letto.
    await assertFails(uploadBytes(ref(as(ADA), `media/${CHAT}/m-6`), file(64), OCTET))
  })

  it('un membro cancella: serve al "visualizza una volta"', async () => {
    await chatWith([ADA, BRUNO])
    await existingFile('m-7')
    // Lo cancella **chi riceve**, non solo chi ha mandato: e' l'unico modo di mantenere subito la
    // promessa, senza aspettare che una funzione lato server si svegli.
    await assertSucceeds(deleteObject(ref(as(BRUNO), `media/${CHAT}/m-7`)))
  })

  it('fuori da media non esiste niente', async () => {
    await chatWith([ADA, BRUNO])
    await assertFails(uploadBytes(ref(as(ADA), 'altro/qualcosa'), file(), OCTET))
    await assertFails(uploadBytes(ref(as(ADA), `media/${CHAT}/sotto/ancora`), file(), OCTET))
  })
})
