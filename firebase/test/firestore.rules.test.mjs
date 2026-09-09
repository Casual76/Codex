import { readFileSync } from 'node:fs'
import { after, before, describe, it } from 'node:test'
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing'
import { doc, getDoc, setDoc, deleteDoc, updateDoc, Bytes } from 'firebase/firestore'

/**
 * Le regole di Firestore, provate come le prova un attaccante.
 *
 * La porta e' la 8088 e non la 8080 di prestampa: quest'ultima e' la piu' contesa che ci sia su una
 * macchina da sviluppo -- su questa la occupa NVIDIA Broadcast -- e un test che fallisce perche'
 * un'altra applicazione era aperta non e' un test.
 *
 * Un file di regole si legge e sembra sempre giusto: dice quello che voleva dire chi lo ha
 * scritto. Questi test dicono quello che fa davvero, e soprattutto **quello che nega** -- che e'
 * la meta' che non si vede rileggendo, e l'unica che protegge qualcuno.
 *
 * Girano contro l'emulatore, quindi non toccano il progetto vero:
 *
 *   cd firebase/test && npm install && npm run emulate
 */

const PROJECT = 'codex-test'

/** I byte come li vuole Firestore. La busta di un messaggio e' sempre `bytes`, mai una stringa. */
const bytes = (array) => Bytes.fromUint8Array(array)
const ADA = 'uid-ada'
const BRUNO = 'uid-bruno'

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

/** Il database come lo vede una persona autenticata. */
const as = (uid) => env.authenticatedContext(uid).firestore()

/** Il database come lo vede chi non ha fatto l'accesso. */
const anonymous = () => env.unauthenticatedContext().firestore()

describe('users/{uid} — il profilo pubblico', () => {
  it('chi non ha fatto l\'accesso non legge niente', async () => {
    await assertFails(getDoc(doc(anonymous(), 'users', ADA)))
  })

  it('chi ha fatto l\'accesso legge il profilo di chiunque', async () => {
    // E' voluto: per aggiungere qualcuno serve poter leggere la sua scheda pubblica, che contiene
    // solo cio' che quella persona mette in un QR.
    await assertSucceeds(getDoc(doc(as(BRUNO), 'users', ADA)))
  })

  it('ognuno scrive solo il proprio profilo', async () => {
    await assertSucceeds(setDoc(doc(as(ADA), 'users', ADA), { name: 'Ada' }))
    await assertFails(setDoc(doc(as(BRUNO), 'users', ADA), { name: 'non io' }))
  })

  it('il proprio profilo si cancella, quello di un altro no', async () => {
    // **Cambiato in M8**: prima nessuno poteva cancellare un profilo, nemmeno il suo. Un account che
    // non si puo' eliminare tiene in ostaggio chi lo ha aperto, e le chiavi pubbliche che ci stanno
    // dentro sono le sue. Adesso lo cancella chi lo possiede, e continua a non poterlo fare nessun
    // altro.
    await assertFails(deleteDoc(doc(as(BRUNO), 'users', ADA)))
    await assertSucceeds(deleteDoc(doc(as(ADA), 'users', ADA)))
  })
})

describe('users/{uid}/vault, keyring, devices — la roba privata', () => {
  for (const collection of ['vault', 'keyring', 'devices']) {
    it(`${collection}: solo il proprietario, in lettura e in scrittura`, async () => {
      await assertSucceeds(setDoc(doc(as(ADA), 'users', ADA, collection, 'x'), { a: 1 }))
      await assertSucceeds(getDoc(doc(as(ADA), 'users', ADA, collection, 'x')))
      await assertFails(getDoc(doc(as(BRUNO), 'users', ADA, collection, 'x')))
      await assertFails(setDoc(doc(as(BRUNO), 'users', ADA, collection, 'x'), { a: 2 }))
      await assertFails(getDoc(doc(anonymous(), 'users', ADA, collection, 'x')))
    })
  }
})

describe('codexIds/{codexId} — la rubrica pubblica', () => {
  it('si registra solo puntando a se stessi', async () => {
    await assertSucceeds(
      setDoc(doc(as(ADA), 'codexIds', 'CDX-AAAA-1111'), { uid: ADA }),
    )
    // Il caso che conta: dirottare un Codex ID sul proprio account per farsi scrivere al posto di
    // qualcun altro. La regola lo impedisce guardando *il contenuto*, non chi scrive.
    await assertFails(
      setDoc(doc(as(BRUNO), 'codexIds', 'CDX-BBBB-2222'), { uid: ADA }),
    )
  })

  it('un Codex ID gia\' preso non si sovrascrive', async () => {
    await env.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), 'codexIds', 'CDX-CCCC-3333'), { uid: ADA })
    })
    // Nemmeno il proprietario: un identificatore che cambia padrone e' esattamente il modo in cui
    // qualcuno si prende le conversazioni di un altro.
    await assertFails(setDoc(doc(as(ADA), 'codexIds', 'CDX-CCCC-3333'), { uid: ADA }))
    await assertFails(setDoc(doc(as(BRUNO), 'codexIds', 'CDX-CCCC-3333'), { uid: BRUNO }))
    // **Toglierla si puo'**, e solo a chi ci sta dentro: serve a chi elimina l'account. Che poi
    // qualcun altro possa prendersi quel nome non apre niente -- il Codex ID nasce dalla chiave di
    // firma, e una scheda che non torna con quella chiave viene rifiutata da chi la cerca.
    await assertFails(deleteDoc(doc(as(BRUNO), 'codexIds', 'CDX-CCCC-3333')))
    await assertSucceeds(deleteDoc(doc(as(ADA), 'codexIds', 'CDX-CCCC-3333')))
  })

  it('chi non ha fatto l\'accesso non cerca nessuno', async () => {
    await assertFails(getDoc(doc(anonymous(), 'codexIds', 'CDX-AAAA-1111')))
  })
})

describe('chats/{chatId} — le conversazioni', () => {
  const CHAT = 'd-prova'

  /** Una chat gia' esistente fra Ada e Bruno, scritta scavalcando le regole. */
  const esistente = async (id = CHAT, membri = [ADA, BRUNO]) => {
    await env.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), 'chats', id), {
        type: 'direct',
        memberUids: membri,
        lastActivityAt: 1,
      })
    })
  }

  it('si crea solo standoci dentro, in due, e diretta', async () => {
    await assertSucceeds(
      setDoc(doc(as(ADA), 'chats', 'd-uno'), {
        type: 'direct', memberUids: [ADA, BRUNO], lastActivityAt: 1,
      }),
    )
    // Fra altri due: e' il modo in cui si infilerebbe una conversazione addosso a qualcuno.
    await assertFails(
      setDoc(doc(as(ADA), 'chats', 'd-due'), {
        type: 'direct', memberUids: [BRUNO, 'uid-carla'], lastActivityAt: 1,
      }),
    )
    // In tre: i gruppi arriveranno con la loro regola, non da questa porta.
    await assertFails(
      setDoc(doc(as(ADA), 'chats', 'd-tre'), {
        type: 'direct', memberUids: [ADA, BRUNO, 'uid-carla'], lastActivityAt: 1,
      }),
    )
  })

  it('la prima consegna, da zero, funziona', async () => {
    // **Il test che mancava.** Tutti gli altri partono da una chat che esiste gia', e cosi' non
    // vedevano il caso vero: la prima volta la conversazione **non c'e'**, e una regola che chiama
    // `get()` su un documento assente non risponde "no", va in errore -- e Firestore un errore lo
    // tratta come un rifiuto. Risultato: il primo messaggio di ogni conversazione veniva negato.
    const nuova = 'd-da-zero'
    await assertSucceeds(
      setDoc(doc(as(ADA), 'chats', nuova), {
        type: 'direct', memberUids: [ADA, BRUNO], lastActivityAt: 1,
      }),
    )
    await assertSucceeds(
      setDoc(doc(as(ADA), 'chats', nuova, 'messages', 'primo'), {
        senderUid: ADA,
        senderCodexId: 'CDX-AAAA-1111',
        envelope: bytes(new Uint8Array([9, 9])),
        viewOnce: false,
        createdAt: 1,
      }),
    )
  })

  it('scrivere due volte la stessa chat non e\' un errore', async () => {
    // Chi scrive non sa se l'altro l'ha gia' creata, e chiederlo prima non si puo': quindi scrive
    // sempre lo stesso contenuto, e la seconda volta deve passare come aggiornamento.
    const doppia = 'd-doppia'
    const contenuto = { type: 'direct', memberUids: [ADA, BRUNO], lastActivityAt: 1 }
    await assertSucceeds(setDoc(doc(as(ADA), 'chats', doppia), contenuto))
    await assertSucceeds(setDoc(doc(as(BRUNO), 'chats', doppia), { ...contenuto, lastActivityAt: 2 }))
  })

  it('di una chat che non esiste non si legge niente, ma non e\' un errore', async () => {
    await assertSucceeds(getDoc(doc(as(ADA), 'chats', 'd-non-esiste')))
    // I messaggi di una chat che non c'e' restano negati: li' non c'e' nessun membro da verificare.
    await assertFails(getDoc(doc(as(ADA), 'chats', 'd-non-esiste', 'messages', 'x')))
  })

  it('la leggono i membri e nessun altro', async () => {
    await esistente()
    await assertSucceeds(getDoc(doc(as(ADA), 'chats', CHAT)))
    await assertSucceeds(getDoc(doc(as(BRUNO), 'chats', CHAT)))
    await assertFails(getDoc(doc(as('uid-carla'), 'chats', CHAT)))
    await assertFails(getDoc(doc(anonymous(), 'chats', CHAT)))
  })

  it('i membri non si cambiano piu\'', async () => {
    await esistente()
    // Il caso che conta: aggiungere un terzo a una conversazione a due. Dallo schermo non si
    // vedrebbe, e da quel momento tutto quello che passa lo legge anche lui.
    await assertFails(
      updateDoc(doc(as(ADA), 'chats', CHAT), { memberUids: [ADA, BRUNO, 'uid-carla'] }),
    )
    await assertSucceeds(updateDoc(doc(as(ADA), 'chats', CHAT), { lastActivityAt: 2 }))
  })
})

describe('chats/{chatId}/messages — i messaggi', () => {
  const CHAT = 'd-messaggi'
  const busta = new Uint8Array([1, 2, 3, 4])

  before(async () => {
    await env.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), 'chats', CHAT), {
        type: 'direct', memberUids: [ADA, BRUNO], lastActivityAt: 1,
      })
    })
  })

  const messaggio = (senderUid, extra = {}) => ({
    senderUid,
    senderCodexId: 'CDX-AAAA-1111',
    envelope: bytes(busta),
    viewOnce: false,
    createdAt: 1,
    ...extra,
  })

  it('si scrive solo a nome proprio, e solo da dentro', async () => {
    await assertSucceeds(
      setDoc(doc(as(ADA), 'chats', CHAT, 'messages', 'm1'), messaggio(ADA)),
    )
    // Firmare un messaggio col nome di un altro dentro la propria chat.
    await assertFails(
      setDoc(doc(as(ADA), 'chats', CHAT, 'messages', 'm2'), messaggio(BRUNO)),
    )
    // Chi non e' membro non entra nemmeno a nome proprio.
    await assertFails(
      setDoc(doc(as('uid-carla'), 'chats', CHAT, 'messages', 'm3'), messaggio('uid-carla')),
    )
  })

  it('li leggono i membri e nessun altro', async () => {
    await assertSucceeds(getDoc(doc(as(BRUNO), 'chats', CHAT, 'messages', 'm1')))
    await assertFails(getDoc(doc(as('uid-carla'), 'chats', CHAT, 'messages', 'm1')))
  })

  it('una busta enorme non passa', async () => {
    const troppa = new Uint8Array(70000)
    await assertFails(
      setDoc(doc(as(ADA), 'chats', CHAT, 'messages', 'm-grosso'), messaggio(ADA, {
        envelope: bytes(troppa),
      })),
    )
  })

  it('il destinatario cancella solo i "visualizza una volta"', async () => {
    await env.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore()
      await setDoc(doc(db, 'chats', CHAT, 'messages', 'normale'), messaggio(ADA))
      await setDoc(doc(db, 'chats', CHAT, 'messages', 'unavolta'), messaggio(ADA, { viewOnce: true }))
    })
    // Un messaggio normale di Ada, Bruno non lo tocca: sparirebbe anche dalla parte di chi
    // l'ha scritto, e non e' una cosa che il destinatario possa decidere.
    await assertFails(deleteDoc(doc(as(BRUNO), 'chats', CHAT, 'messages', 'normale')))
    // Il "visualizza una volta" invece si': e' l'unico modo di mantenere quella promessa subito,
    // ed e' il motivo per cui `viewOnce` e' l'unico campo in chiaro del documento.
    await assertSucceeds(deleteDoc(doc(as(BRUNO), 'chats', CHAT, 'messages', 'unavolta')))
    // Il mittente cancella il suo comunque.
    await assertSucceeds(deleteDoc(doc(as(ADA), 'chats', CHAT, 'messages', 'normale')))
  })

  it('le ricevute sono ognuno la sua', async () => {
    await assertSucceeds(setDoc(doc(as(ADA), 'chats', CHAT, 'receipts', ADA), { readAt: 1 }))
    await assertFails(setDoc(doc(as(ADA), 'chats', CHAT, 'receipts', BRUNO), { readAt: 1 }))
    await assertSucceeds(getDoc(doc(as(ADA), 'chats', CHAT, 'receipts', BRUNO)))
  })

  it('"sta scrivendo" lo scrive solo chi sta scrivendo', async () => {
    await assertSucceeds(setDoc(doc(as(ADA), 'chats', CHAT, 'typing', ADA), { until: 1 }))
    // Far comparire "Ada sta scrivendo" sullo schermo di qualcun altro sarebbe una bugia
    // convincente e a costo zero: la regola non la lascia scrivere.
    await assertFails(setDoc(doc(as(BRUNO), 'chats', CHAT, 'typing', ADA), { until: 1 }))
    await assertSucceeds(getDoc(doc(as(BRUNO), 'chats', CHAT, 'typing', ADA)))
    await assertFails(getDoc(doc(as('uid-carla'), 'chats', CHAT, 'typing', ADA)))
  })
})

describe("presence/{uid} — chi c'e' adesso", () => {
  it('ognuno dice solo di se stesso', async () => {
    await assertSucceeds(setDoc(doc(as(ADA), 'presence', ADA), { state: 'online', lastSeen: 1 }))
    await assertFails(setDoc(doc(as(BRUNO), 'presence', ADA), { state: 'online', lastSeen: 1 }))
  })

  it("la legge chi ha fatto l'accesso, e nessun altro", async () => {
    await assertSucceeds(getDoc(doc(as(BRUNO), 'presence', ADA)))
    await assertFails(getDoc(doc(anonymous(), 'presence', ADA)))
  })
})

describe('tutto il resto', () => {
  it('e\' chiuso finche\' una milestone non lo apre', async () => {
    for (const path of [['groups', 'x']]) {
      await assertFails(getDoc(doc(as(ADA), ...path)))
      await assertFails(setDoc(doc(as(ADA), ...path), { a: 1 }))
    }
  })
})
