// Cloud Functions di Codex. Regione europea, 2ª generazione.
//
// **Cosa possono e cosa non possono fare.** Girano con i privilegi dell'Admin SDK, quindi passano
// sopra le regole: sono l'unico posto del sistema che vede tutti i documenti. Proprio per questo qui
// non si apre niente e non si legge niente di ciò che conta — una busta `CDX3` è illeggibile anche
// da qui, e deve restare così. Quello che una Function fa è **spostare metadati**: chi ha scritto a
// chi, e a quali dispositivi bussare.
//
// Vedi PIANO.md §6.4.

import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { getStorage } from "firebase-admin/storage";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { onRequest } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { setGlobalOptions } from "firebase-functions/v2";
import { logger } from "firebase-functions";

initializeApp();

setGlobalOptions({ region: "europe-west1", maxInstances: 10 });

/** Risponde con la versione delle funzioni: serve a controllare che l'ambiente sia in piedi. */
export const health = onRequest((_request, response) => {
  response.json({ ok: true, service: "codex-functions", version: "0.2.0" });
});

/**
 * Un messaggio è arrivato sul server: bussa ai dispositivi dell'altra persona.
 *
 * **La spinta non contiene niente.** Nessun testo (non esiste in chiaro nemmeno qui), ma nemmeno il
 * nome di chi scrive e nemmeno che forma ha il sigillo: solo `chatId` e `messageId`. È il client a
 * costruire la notifica, perché è l'unico che può — la tecnica del sigillo nasce dalla chiave della
 * conversazione, che sta sul telefono e da nessun'altra parte.
 *
 * È `data-only` di proposito: una notifica `notification` la disegnerebbe il sistema con il testo
 * che gli arriva dal server, e il server un testo da mostrare non ce l'ha e non deve averlo.
 */
export const onMessageCreated = onDocumentCreated(
  "chats/{chatId}/messages/{messageId}",
  async (event) => {
    const message = event.data?.data();
    if (!message) return;

    const { chatId, messageId } = event.params;
    const senderUid: string = message.senderUid ?? "";

    const firestore = getFirestore();
    const chat = await firestore.doc(`chats/${chatId}`).get();
    const members: string[] = chat.get("memberUids") ?? [];

    // A chi ha scritto non si bussa: ce l'ha già sullo schermo.
    const recipients = members.filter((uid) => uid !== senderUid);
    if (recipients.length === 0) return;

    const tokens = await tokensFor(recipients);
    if (tokens.length === 0) {
      logger.info(`nessun dispositivo registrato per ${recipients.join(", ")}`);
      return;
    }

    const response = await getMessaging().sendEachForMulticast({
      tokens,
      data: { kind: "message", chatId, messageId },
      android: {
        // Alta priorità: in Doze una notifica normale può aspettare ore, e un messaggio che arriva
        // domani mattina non è un messaggio.
        priority: "high",
      },
    });

    await forgetDeadTokens(tokens, response.responses);
    logger.info(
      `messaggio ${messageId}: ${response.successCount} consegnati, ${response.failureCount} no`,
    );
  },
);

/** I token di tutti i dispositivi di queste persone. */
async function tokensFor(uids: string[]): Promise<string[]> {
  const firestore = getFirestore();
  const perUser = await Promise.all(
    uids.map(async (uid) => {
      const devices = await firestore.collection(`users/${uid}/devices`).get();
      return devices.docs
        .map((device) => device.get("fcmToken") as string | undefined)
        .filter((token): token is string => !!token);
    }),
  );
  return [...new Set(perUser.flat())];
}

/**
 * Toglie i token che non esistono più.
 *
 * Un'app disinstallata lascia dietro un token che fallisce a ogni messaggio, per sempre. Senza
 * questa pulizia la collezione dei dispositivi cresce e ogni notifica paga il costo di provarci.
 */
async function forgetDeadTokens(
  tokens: string[],
  responses: { success: boolean; error?: { code: string } }[],
): Promise<void> {
  const dead = tokens.filter((_, index) => {
    const error = responses[index]?.error?.code;
    return (
      error === "messaging/registration-token-not-registered" ||
      error === "messaging/invalid-registration-token"
    );
  });
  if (dead.length === 0) return;

  const firestore = getFirestore();
  const stale = await firestore
    .collectionGroup("devices")
    .where("fcmToken", "in", dead.slice(0, 10))
    .get();
  await Promise.all(stale.docs.map((document) => document.ref.delete()));
  logger.info(`tolti ${stale.size} dispositivi che non rispondono piu'`);
}

/**
 * Una busta e' arrivata nella posta di qualcuno: bussa ai suoi dispositivi.
 *
 * Serve ai gruppi. La chiave di un gruppo la consegna una persona, e quella persona deve essere
 * **sveglia** per farlo: senza una spinta, chi ha appena chiesto di entrare aspetterebbe che chi
 * l'ha invitato riapra Codex per conto suo. Con la spinta, l'app dell'invitante si sveglia, ritira
 * la posta e consegna la chiave in pochi secondi.
 *
 * Come per i messaggi, la spinta non porta niente: solo "c'e' posta". Il contenuto e' cifrato con
 * la chiave di coppia e nemmeno da qui si puo' aprire.
 */
export const onInboxItemCreated = onDocumentCreated(
  "inbox/{uid}/items/{itemId}",
  async (event) => {
    const { uid, itemId } = event.params;
    const tokens = await tokensFor([uid]);
    if (tokens.length === 0) return;

    const response = await getMessaging().sendEachForMulticast({
      tokens,
      data: { kind: "inbox", itemId },
      android: { priority: "high" },
    });
    await forgetDeadTokens(tokens, response.responses);
    logger.info(`posta ${itemId}: ${response.successCount} consegnati`);
  },
);

/**
 * Una storia e' stata pubblicata: bussa a chi puo' vederla.
 *
 * Chi puo' vederla e' scritto nel documento, ed e' l'unica cosa che da qui si capisce: le chiavi
 * avvolte sono una per destinatario, quindi i **nomi** dei destinatari sono le chiavi della mappa.
 * Il contenuto no: la busta e' cifrata con una chiave che sta dentro quegli involucri, e nessuno di
 * essi si apre da qui.
 *
 * La spinta non dice nemmeno chi ha pubblicato: solo "c'e' una storia nuova". Il nome lo mette il
 * telefono, che quella persona la conosce.
 */
export const onStoryCreated = onDocumentCreated(
  "stories/{uid}/items/{storyId}",
  async (event) => {
    const story = event.data?.data();
    if (!story) return;

    const { uid, storyId } = event.params;
    const wrapped = (story.wrappedKeys ?? {}) as Record<string, unknown>;
    const recipients = Object.keys(wrapped).filter((to) => to !== uid);
    if (recipients.length === 0) return;

    const tokens = await tokensFor(recipients);
    if (tokens.length === 0) return;

    const response = await getMessaging().sendEachForMulticast({
      tokens,
      data: { kind: "story", storyId },
      android: {
        // Una storia non e' un messaggio: puo' aspettare il prossimo risveglio del telefono, e non
        // vale la batteria di una consegna immediata.
        priority: "normal",
      },
    });
    await forgetDeadTokens(tokens, response.responses);
    logger.info(`storia ${storyId}: ${response.successCount} avvisati su ${recipients.length}`);
  },
);

/**
 * La pulizia del deposito: i file che nessun messaggio nomina piu'.
 *
 * Su Firestore i messaggi se ne vanno da soli dopo trenta giorni -- e' la politica TTL sul campo
 * `expireAt`, scritta dal mittente. I **file** no: uno spazio di archiviazione non ha una politica
 * che sappia cos'e' un messaggio, e senza qualcuno che li tolga resterebbero li' per sempre,
 * pagati al mese, illeggibili da chiunque compreso noi.
 *
 * Toglie due cose, e sono diverse:
 *
 * - i file **vecchi**: oltre i trentuno giorni il messaggio che li nominava non esiste piu' per
 *   costruzione, quindi il file e' spazzatura certa;
 * - gli **orfani** recenti: un file sale prima del messaggio (e' l'ordine giusto, cosi' non arriva
 *   mai un messaggio che promette una foto assente), quindi un invio interrotto a meta' lascia un
 *   file che nessuno nominera' mai. Si guarda solo dopo un giorno, per non correre dietro a un
 *   invio ancora in corso.
 *
 * Anche qui non si apre niente: un nome di file e una data bastano per decidere.
 */
export const cleanupMedia = onSchedule(
  {
    schedule: "every day 04:15",
    timeZone: "Europe/Rome",
    retryCount: 1,
  },
  async () => {
    const bucket = getStorage().bucket();
    const now = Date.now();
    const vecchi = now - 31 * GIORNO;
    const recenti = now - GIORNO;

    let esaminati = 0;
    let perEta = 0;
    let orfani = 0;
    let controlli = 0;

    let pageToken: string | undefined;
    let pagine = 0;
    do {
      const [files, next] = (await bucket.getFiles({
        prefix: "media/",
        maxResults: 1000,
        pageToken,
        autoPaginate: false,
      })) as unknown as [Array<{ name: string; metadata: { timeCreated?: string } }>, { pageToken?: string } | null];

      for (const file of files) {
        esaminati += 1;
        const nato = Date.parse(file.metadata.timeCreated ?? "");
        if (!Number.isFinite(nato)) continue;

        if (nato < vecchi) {
          await elimina(bucket, file.name);
          perEta += 1;
          continue;
        }
        // Gli orfani costano una lettura ciascuno: se ne guarda un numero fisso per giro, e il
        // resto aspetta domani. Una pulizia che si mangia il budget non e' una pulizia.
        if (nato < recenti && controlli < MASSIMI_CONTROLLI) {
          controlli += 1;
          if (!(await esisteIlMessaggio(file.name))) {
            await elimina(bucket, file.name);
            orfani += 1;
          }
        }
      }

      pageToken = next?.pageToken;
      pagine += 1;
    } while (pageToken && pagine < MASSIME_PAGINE);

    logger.info(
      `pulizia media: ${esaminati} esaminati, ${perEta} scaduti, ${orfani} orfani su ${controlli} controlli`,
    );
  },
);

const GIORNO = 24 * 60 * 60 * 1000;
/** Quante letture al giorno si spendono per cercare orfani recenti. */
const MASSIMI_CONTROLLI = 500;
/** Un tetto al giro: meglio finire domani che restare accesi a vuoto. */
const MASSIME_PAGINE = 20;

/** `media/{chatId}/{messageId}` esiste ancora come messaggio? */
async function esisteIlMessaggio(nome: string): Promise<boolean> {
  const parti = nome.split("/");
  if (parti.length !== 3) return true; // Un nome che non riconosco non lo tocco.
  const [, chatId, messageId] = parti;
  const documento = await getFirestore().doc(`chats/${chatId}/messages/${messageId}`).get();
  return documento.exists;
}

async function elimina(
  bucket: { file: (nome: string) => { delete: (opzioni: { ignoreNotFound: boolean }) => Promise<unknown> } },
  nome: string,
): Promise<void> {
  try {
    await bucket.file(nome).delete({ ignoreNotFound: true });
  } catch (errore) {
    logger.warn(`non ho potuto togliere ${nome}: ${errore}`);
  }
}
