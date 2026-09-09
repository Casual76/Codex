# Codex 2 — Piano di ricostruzione

Versione del piano: 1.0 · 2026-09-05 · Stato: **approvato nelle decisioni, da implementare**

> Codex è un'app di messaggistica Android in cui ogni messaggio arriva **sigillato** in una forma da
> aprire con un tocco (rune, roccia, quadro) mentre la crittografia vera lavora sotto senza farsi
> vedere. Pratica come un messenger, vissuta come un'avventura di cifratura.

Questo documento è la fonte di verità per lo sviluppo. Ogni sessione di lavoro parte da qui: legge la
milestone in corso (§12), fa il lavoro, aggiorna lo stato in fondo alla milestone. Se una decisione
cambia, si cambia **qui** prima che nel codice.

---

## 0. Le decisioni prese

Riassunto delle risposte date nella sessione di progettazione (10 blocchi di domande).

| Tema | Decisione |
|---|---|
| Stack | Kotlin + Jetpack Compose nativo, **Fluid Engine 1.23.0** come fondamenta (vetro, morphing, componenti) |
| Anima dell'app | Messaggistica pratica, ma la cifratura è **interattiva e divertente**: ogni messaggio è un sigillo da aprire |
| Tecniche v1 | **Rune** (simboli runici che si decifrano), **Roccia** (gemme e pietre che si spaccano), **Quadro** (dipinto con il messaggio nascosto nei pixel) |
| Scelta tecnica | **Casuale a sorpresa**; override tenendo premuto Invia |
| Gesto | Sempre **tocco**. Rune: i simboli cambiano rapidamente e diventano lettere; Quadro: i pixel si smuovono e poi diventa testo; Roccia: si spacca e dietro c'è il messaggio |
| Semantica sblocco | I ricevuti vanno toccati almeno una volta; gli inviati si sigillano da soli ma si possono aprire; tutto resta aperto finché la chat è aperta, poi si risigilla; tasto **"Rivela tutto"** in cima alla chat per i sigilli già aperti in passato |
| Gamification | **Leggera**: rituali e magia, niente punti |
| Cifrario storico | Simboli Unicode (rune e alfabeti antichi), non glifi disegnati; sezione **"Le origini"** con l'immagine originale e la ricerca storica |
| Pairing | Tre modi: **vicinanza** (Nearby Connections), **QR** (una scansione, scambio automatico), **manuale** (fallback) |
| Chiavi | Crittografia vera sempre; alla creazione di ogni chat una **parola d'ordine** detta a voce entra nella chiave |
| Account | **Solo Google**. Nome utente, multi-dispositivo, recupero pratico |
| Vault | L'utente sceglie **PIN o password**; **biometria** sempre attivabile e usata di default all'avvio |
| Firebase | **Nuovo progetto** (il vecchio `cifratoreweb` è stato eliminato perché compromesso). Cloud Functions + FCM, piano Blaze |
| Storico | **Ibrido**: cloud cifrato per 30 giorni, locale per sempre |
| Vicinanze | Stessa chat, **trasporto invisibile**: Nearby se il contatto è vicino, altrimenti cloud, sync al ritorno della rete |
| Gruppi | **Completi** in v1 (inviti, admin, abbandona, sciogli) |
| Contenuti | Testo, **foto** (niente video), **note vocali** |
| Chat | Risposte e reazioni; modifica ed elimina per tutti; sta scrivendo, online, spunte; ricerca e messaggi fissati |
| Privacy | Blocco app PIN/biometria; **blocco per singola chat**; messaggi a scadenza; **"visualizza una volta"** con FLAG_SECURE durante la visione |
| Condivisione esterna | Solo per la tecnica **Quadro** (PNG apribile con Codex) |
| Roccia | **Mix casuale** di gemme e pietre |
| Quadro | **Raccolta di dipinti** inclusa nell'APK |
| Onboarding | 3-4 schermate con momenti interattivi: il nome viene "cifrato" in un **minerale casuale che diventa l'avatar** |
| Icona | **Cristallo/geode con una runa incisa** |
| Look | **Minerale: ametista e ossidiana**, scuro di default |
| Engine | engine-ui + engine-update + engine-config (manifest remoto nel repo) |
| Extra v1 | Layout **tablet e pieghevoli**; **storie a tempo** (sigillo del giorno, 24 h) |
| Lingua | **Italiano e inglese** |
| Firma | Keystore vecchio perso → si firma con **pampa.jks**; chi ha la vecchia Codex la disinstalla |
| Pacchetto | **`dev.pampa.codex`** |
| Cartella | `C:\VibeCoded Projects\Codex` (repo GitHub Casual76/Codex, sorgenti finalmente versionati) |
| Test | Telefono + tablet (tablet da collegare più avanti) |
| Rilascio | Niente sul Pampa Store fino alla **prima beta con tutte le funzioni**; poi beta a ogni aggiornamento fino alla 1.0.0 decisa dall'utente |

---

## 1. Il prodotto

### 1.1 I pilastri

1. **Ogni messaggio è un sigillo.** Non si legge, si apre. La forma (rune, roccia, quadro) è scelta a
   sorte e vista identica da entrambe le parti. L'apertura è un'animazione curata al fotogramma.
2. **Pratica come un messenger.** Contatti, chat, gruppi, foto, vocali, risposte, reazioni, spunte,
   notifiche, ricerca. Niente che costringa a "fare fatica" per scrivere un messaggio.
3. **Crittografia vera, invisibile.** Chiavi generate sul telefono, mai in chiaro sul server. La sola
   cosa che l'utente fa è dire una parola d'ordine a voce quando crea una chat.
4. **Tre modi di incontrarsi.** Avvicinando i telefoni, con un QR, o a mano. E quando due contatti
   sono vicini, i messaggi passano direttamente tra i telefoni anche senza internet.
5. **Un omaggio alle origini.** Il cifrario del prof e la storia dell'app hanno una casa dentro
   Codex: la sezione "Le origini".

### 1.2 Non-obiettivi della v1

- Non è "l'app più sicura del mondo": è sicura sul serio, ma le scelte privilegiano l'esperienza
  (per esempio le rune mostrano quante lettere ha il messaggio).
- Niente video, niente iOS, niente web, niente widget, niente punti/livelli.
- Le tecniche Rune e Roccia non si esportano come immagini fuori dall'app; solo Quadro.
- Il cifrario storico non è una tecnica di cifratura "fedele" (era stato proposto, non scelto).

---

## 2. Esperienza utente

### 2.1 Navigazione

Barra in vetro (`FluidTabBar`; `FluidTabRail` su schermi larghi) con tre destinazioni:

| Tab | Contenuto |
|---|---|
| **Chat** | Lista conversazioni, ricerca, azioni "Il mio sigillo" (ID + QR) e "Aggiungi" (tasto `FluidMorphMenuButton` che si apre nel menù: Vicino · Scansiona QR · Manuale · Nuovo gruppo · Unisciti a gruppo) |
| **Storie** | I sigilli del giorno dei contatti (24 h) e il mio |
| **Io** | Profilo (nome, minerale-avatar, Codex ID), Sicurezza, Aspetto, Chat, Notifiche, Dati, **Le origini**, Informazioni |

Rotte secondarie: Chat singola, Dettagli chat/contatto, Info gruppo, Pairing vicino, Scanner QR, Il
mio QR, Inserimento manuale, Rito della parola d'ordine, Visualizzatore (foto/vocale/visualizza una
volta), Sblocco (app e chat), Onboarding.

Tutte le transizioni di rotta sono opache e laterali (`FluidRouteMotionHost`); mai un fade fra due
pagine (regola dell'engine).

### 2.2 Onboarding (4 schermate + collegamento nuovo dispositivo)

1. **"Un sigillo per te."** Al centro una gemma. Toccandola si spacca (stessa animazione della
   tecnica Roccia) e dietro compare "Benvenuto in Codex. Qui i messaggi si aprono." Si impara il gesto
   facendolo. Tasto "Continua".
2. **"Chi sei."** Accesso con Google (Credential Manager, un tocco). Se l'account ha già un vault,
   si salta al passo 5.
3. **"Il tuo nome diventa minerale."** Campo nome. Mentre si scrive, sotto si forma in tempo reale un
   minerale generato dal nome (procedurale, §4.10): è l'avatar. Tasto "Un altro" per rigenerarlo con
   un seme diverso. Il seme scelto viene salvato.
4. **"La tua chiave."** Scelta PIN (6 cifre) o password; poi la proposta "Usa l'impronta o il volto
   per aprire Codex" (attiva di default se il dispositivo lo supporta). Spiegazione in una riga:
   "Serve per aprire Codex e per collegare un altro telefono."
5. **(Solo nuovo dispositivo)** "Bentornato, {nome}. Inserisci il tuo PIN/password per riportare qui
   le tue chiavi e i tuoi contatti." Poi la proposta biometria.

I permessi (notifiche, fotocamera, microfono, Bluetooth/Wi-Fi vicini) si chiedono **quando servono**,
mai nell'onboarding. Il primo uso di ogni funzione ha il suo callout con `FluidTutorialHost`.

### 2.3 Schermate principali

**Lista chat.** `FluidScreen(title = "Chat")` con ricerca nel titolo, `FluidListGroup` di righe:
minerale-avatar (contatto) o gemma della chat (gruppo), nome, riga secondaria **mai in chiaro**: "Ti ha
mandato una roccia · 12:40", "3 sigilli da aprire", "Sta scrivendo…". Badge non letti. Menù
contestuale (`fluidContextMenu`): Fissa, Silenzia, Blocca chat, Elimina. Sezione "Fissate" in alto.

**Chat.** Barra in vetro con avatar, nome, stato ("Vicino" con icona quando il trasporto Nearby è
attivo, "online", "visto alle…", "sta scrivendo…"), azione **"Rivela tutto"** (`FluidBarAction`) e
menù (Info, Ricerca nella chat, Messaggi fissati, Scadenza messaggi, Blocca chat, Svuota). Lista
messaggi con bolle-sigillo (§4). Composer: campo testo, allegato (foto dalla galleria/fotocamera),
tasto vocale (tieni premuto per registrare), tasto **Invia** con chip della tecnica corrente
("Sorpresa" di default). **Tieni premuto Invia** → foglio (`FluidSheet`) con Sorpresa · Rune · Roccia
· Quadro (con scelta del dipinto) · e le opzioni del messaggio: Visualizza una volta, Scadenza.
Swipe per rispondere; long-press → menù contestuale (Rispondi, Reagisci, Copia se rivelato, Modifica,
Fissa, Elimina per me / per tutti, Info).

**Dettagli contatto/chat.** Minerale grande, nome, Codex ID, "Gemma della chat" (icona derivata dalla
chiave, identica sui due telefoni: serve anche a verificare che la cifratura combaci), impostazioni
della chat (scadenza, blocco, notifiche, tema di suoni), media condivisi, "Elimina contatto".

**Info gruppo.** Gemma del gruppo, nome, membri con minerale, admin, invita (QR/vicino/link),
abbandona, sciogli (admin).

**Pairing vicino.** "Avvicinate i telefoni": entrambi aprono la schermata; i dispositivi trovati
compaiono come minerali con il nome; si tocca l'altro; su entrambi appare **la stessa runa** e si
conferma "È la stessa runa?" → scambio schede → "Marco è ora un tuo contatto" → proposta "Crea la
chat" (rito della parola d'ordine).

**QR.** "Il mio sigillo": QR grande sopra il minerale, Codex ID copiabile, condividi. "Scansiona":
fotocamera con cornice; basta una scansione, il resto è automatico (§5.4).

**Manuale.** Campo Codex ID (`CDX-XXXX-XXXX`), anteprima del minerale e del nome trovati, "Invia
richiesta"; l'altro vede una richiesta con il minerale del richiedente e accetta.

**Rito della parola d'ordine.** Chi crea la chat sceglie una parola o frase e la dice a voce
all'altro. Schermata: due metà di una gemma; ognuno scrive la parola; quando le chiavi combaciano le
due metà si fondono nella gemma della chat (animazione Fluid-physics). Se non combaciano: "Il
sigillo non combacia. Riprovate con la stessa parola."

**Visualizzatore.** Foto e vocali rivelati si aprono a tutto schermo (zoom, salva, condividi se
consentito). In modalità "visualizza una volta": FLAG_SECURE, nessun salvataggio, timer opzionale,
alla chiusura "Sigillato per sempre".

**Storie.** Griglia di gemme/rune/quadri dei contatti (24 h). Tocco: si apre come un messaggio. Rispondi
→ messaggio nella chat. "Il mio sigillo del giorno": composer con le stesse tecniche.

**Io / Impostazioni.** Gruppi di righe:
- *Profilo*: nome, minerale (rigenera), Codex ID + QR, dispositivi collegati (revoca).
- *Sicurezza*: cambia PIN/password, biometria, blocco app (subito / 1 min / 5 min / 30 min), chat
  bloccate.
- *Aspetto*: tema (scuro di default / chiaro / sistema), accento (Ametista di marca / colore dinamico),
  vetro, aptica, animazioni ridotte segue il sistema.
- *Chat*: spunte di lettura, stato online, "rivelato" visibile agli altri, anteprime nelle notifiche,
  suoni dei sigilli, tastiera incognito.
- *Notifiche*: canali, silenzia tutto, orari.
- *Dati*: spazio locale, svuota cache media, "Cancella lo storico locale", "Elimina account".
- *Le origini*: la sezione dedicata (§11).
- *Informazioni*: versione, changelog, aggiornamento in-app, licenze (`fluidLicensesSection` +
  crediti dei dipinti e dei font).

### 2.4 Tablet e pieghevoli

`WindowSizeClass`: su larghezza *Expanded* la lista chat e la chat aperta stanno affiancate
(`FluidContainerScaffold`), la barra diventa `FluidTabRail`. Nei pieghevoli aperti lo stesso; da
chiusi il layout telefono. Le animazioni dei sigilli sono indipendenti dalla dimensione.

---

## 3. Vocabolario dei sigilli (glossario minimo)

- **Sigillo**: la forma visibile di un messaggio non ancora aperto (o richiuso).
- **Tecnica**: Rune, Roccia, Quadro. "Sorpresa" = una delle tre scelta dal seme del messaggio.
- **Rivelare**: aprire un sigillo con il tocco (animazione). **Rivela tutto**: riaprire senza
  animazione i sigilli già aperti in passato.
- **Risigillare**: alla chiusura della chat i messaggi tornano sigillati.
- **Visualizza una volta**: sigillo che dopo la prima visione resta chiuso per sempre.

---

## 4. I sigilli: specifica

### 4.1 Modello

Ogni messaggio ha un `SealSpec`:

```kotlin
data class SealSpec(
  val technique: Technique,      // RUNE, ROCK, PAINTING
  val seed: Long,                // deterministico: HKDF(chatKey, "seal|" + msgId)
  val paintingId: String?,       // solo PAINTING; scelto dal mittente o dal seme
)
```

Il seme deriva dalla chiave di chat e dall'id del messaggio, quindi **mittente e destinatario vedono
lo stesso sigillo** senza trasmettere nulla di visibile al server. Il mittente scrive nel corpo cifrato
solo l'eventuale override (`technique`, `paintingId`); "Sorpresa" viene risolta dal seme:
`technique = when (seed mod 3) { 0 → RUNE, 1 → ROCK, 2 → PAINTING }`. Un messaggio foto o vocale può
avere qualunque tecnica: quando si apre, dietro c'è la foto o il player.

Tutto il modulo dei sigilli (`:core:seal`) è puro Kotlin + `android.graphics`: nessun accesso a
rete, database o Compose. Il rendering Compose sta nell'app e legge le geometrie dal modulo.

### 4.2 Rune

- **Alfabeto**: Elder Futhark (U+16A0–U+16EA) e Ogham (U+1681–U+1694) da un font incluso
  nell'APK (*Noto Sans Runic* + *Noto Sans Ogham*, licenza OFL): non si può contare sui font di
  sistema.
- **Resa**: una runa per carattere del testo (gli spazi diventano uno spazio più largo), a capo come
  testo normale, massimo 160 rune (oltre, si tronca con "…" e il resto compare solo rivelato). Le
  rune sono scelte dal seme, quindi identiche sui due lati. Una foto/vocale sigillata in rune mostra
  una riga di 12 rune.
- **Rivelazione**: al tocco tutte le rune iniziano a cambiare ogni 40 ms; da sinistra a destra ogni
  posizione "si blocca" sulla lettera vera a ~28 caratteri/secondo (un po' più della velocità di
  lettura), con un micro-impulso di scala (1.0 → 1.12 → 1.0, molla di `FluidMotion`). Durata
  massima 6 s: oltre, la cadenza accelera. Aptica: `Tick` ogni ~6 blocchi (mai più fitto di 40 ms).
  Il colore del testo è quello del tema; le rune "instabili" al 70 % di opacità.

### 4.3 Roccia

- **Generatore** (`MineralGenerator`): dal seme sceglie la famiglia (60 % cristalli, 40 % pietre) e
  una tavolozza (ametista, quarzo fumé, ossidiana, smeraldo, citrino, acquamarina, granato, pietra
  grigia, arenaria, basalto con vene). I colori dei minerali sono **contenuto**, non UI: vivono nel
  modulo seal con un commento che lo dichiara; le sfumature si accordano all'accento del tema per il
  20 % (`tintTowards(accent, 0.2f)`).
  - *Cristallo*: 1–3 prismi convessi (6–8 vertici) con sfaccettature ottenute triangolando verso un
    vettore luce; bagliore speculare; base "geode" opzionale.
  - *Pietra*: poligono a 12–16 vertici deformato da rumore, puntinatura, 1–3 vene minerali (curve di
    Bézier).
- **Frattura**: cellule di Voronoi (7–12 siti dal seme) ritagliate sulla sagoma → schegge.
- **Rivelazione** (~900 ms): 0–120 ms compaiono le crepe lungo i bordi delle cellule; 120–700 ms le
  schegge partono radialmente (molla sottosmorzata di `FluidMotion`), ruotano di ±15°, sfumano; dal
  400 ms il pannello del testo emerge dietro (scala 0.94 → 1, opacità); 20–40 particelle di polvere.
  Aptica: `Threshold` alla crepa, `Success` all'apertura. Su API 33+ un lampo speculare AGSL sulle
  schegge; sotto, nessun lampo.
- **Dimensioni**: bolla quadrata 140 dp (max 200 dp su tablet); il pannello rivelato ha la larghezza
  di una bolla di testo normale.

### 4.4 Quadro

- **Raccolta**: ~30 dipinti di pubblico dominio da musei con licenza CC0 (National Gallery of Art,
  The Met, Art Institute of Chicago, Rijksmuseum, Cleveland Museum of Art), con attenzione al
  Rinascimento italiano e ai paesaggi. Formato WebP, lato lungo 1280 px, ~150–250 KB l'uno (≈ 6 MB
  totali). `paintings.json` con id, titolo, autore, anno, museo, licenza, URL: alimenta i crediti
  in Informazioni. Nessuna riproduzione da musei statali italiani (il Codice dei beni culturali
  limita il riuso anche di opere in pubblico dominio).
- **Il messaggio nel dipinto**: la busta cifrata (§5.6) è nascosta nei bit meno significativi dei
  canali RGB (1 bit/canale, ordine dei pixel permutato dal seme; header con magic `CDX3`, lunghezza,
  CRC). Capacità 1280×960 ≈ 460 KB: un testo entra sempre; una foto sigillata in un quadro non
  incorpora la foto, ma il suo riferimento cifrato (chi non è nella chat non la può scaricare).
  Dentro l'app il dipinto si rigenera localmente da `paintingId` + busta: non si trasmette il PNG.
- **Rivelazione** (~900 ms): al tocco i pixel dentro la cornice si smuovono: su API 33+ uno shader
  AGSL sposta i pixel con un campo di rumore la cui ampiezza cresce, mentre la griglia di
  pixelizzazione passa da 1 a 24 px; poi il dipinto si dissolve in un pannello di vetro con il testo
  (il quadro resta intravisto dietro, sfocato). Sotto API 33: pixelizzazione a scalini con scambio
  casuale di tessere su una copia ridotta del bitmap.
- **Esportazione** ("Condividi come immagine"): PNG senza perdita con la busta incorporata. "Apri
  con Codex" (intent-filter `image/png` + share target) legge i bit, trova il magic e apre il
  messaggio se la chat è sul dispositivo; altrimenti "Questo quadro appartiene a una chat che non
  hai". Avviso nell'app: se il PNG viene ricompresso (WhatsApp come foto) il messaggio si perde:
  inviarlo **come file/documento**.

### 4.5 Macchina degli stati di un messaggio in chat

```
SEALED ──tap──▶ REVEALING ──fine animazione──▶ REVEALED
  ▲                                               │
  └──────────── chiusura chat / 60 s in background ┘

"Rivela tutto": SEALED(revealedOnce = true) ──▶ REVEALED con dissolvenza di 150 ms, senza rituale.
Mai-aperti (revealedOnce = false): ignorati da "Rivela tutto".
Inviati: nascono SEALED con revealedOnce = true (li ho scritti io) e si aprono con il rituale.
Visualizza una volta: SEALED ──tap──▶ REVEALING ──▶ VIEWING (FLAG_SECURE) ──chiusura──▶ BURNED.
```

- Lo stato REVEALED vive in una `RevealSession` legata alla schermata chat: uscendo dalla chat si
  svuota; in background l'app la tiene 60 s (cambio rapido di app senza risigillare tutto).
- `revealedOnce` è persistito in locale per messaggio e mostrato all'altro come spunta "rivelato"
  (disattivabile, come le spunte blu).
- Anteprime in lista chat e nelle notifiche: **mai il testo**, sempre la tecnica ("Ti ha mandato una
  roccia"). Chi vuole l'anteprima nelle notifiche la può attivare: mostra solo "Nuovo sigillo".

### 4.6 Foto e note vocali

- Foto: ridimensionate a 2048 px lato lungo, WebP qualità 85, cifrate e caricate su Storage;
  miniatura 64 px cifrata dentro il corpo del messaggio (per l'anteprima nel visualizzatore). Il
  sigillo copre l'intera bolla; rivelato, mostra la foto con angoli continui; tocco → visualizzatore.
- Vocali: registrazione con `MediaRecorder` in AAC 48 kbps mono (Opus dove disponibile),
  massimo 5 minuti, forma d'onda calcolata in locale e salvata nel corpo cifrato; rivelato, mostra il
  player (`Media3`) con forma d'onda; riproduzione con sensore di prossimità → altoparlante
  auricolare.

### 4.7 Visualizza una volta e scadenza

- **Visualizza una volta** (foto, testo, vocale): il destinatario rivela, vede in un visualizzatore
  con `FLAG_SECURE`; alla chiusura il contenuto locale viene distrutto (file cancellati, testo
  rimosso), il documento cloud viene eliminato dal destinatario, resta il segnaposto "Sigillato per
  sempre" su entrambi i lati; il mittente vede la spunta "visto".
- **Scadenza** (per chat o per messaggio): 30 s, 1 min, 1 h, 24 h, 7 giorni. Il timer parte alla
  rivelazione (se mai rivelato, dall'invio + 7 giorni). Alla scadenza: cancellazione locale (alarm +
  WorkManager) e cloud (chi scade per primo cancella; il TTL server fa da rete di sicurezza).

### 4.8 Accessibilità e prestazioni

- `FluidMotionPolicy`: con le animazioni di sistema a zero, i sigilli si aprono all'istante (150 ms
  di dissolvenza) e nessuna particella viene disegnata.
- Tutte le animazioni dei sigilli sono misurate coi fotogrammi (metodo in `regole.md` dell'engine:
  `screenrecord` → `ffmpeg -fps_mode passthrough`). Obiettivo: nessun frame oltre 16 ms su un
  telefono di fascia media durante una rivelazione, nessun lampo al primo/ultimo frame.
- Le rocce sono disegnate su `Canvas` Compose con geometrie precalcolate al primo layout e messe in
  cache per messaggio; gli shader AGSL sono opzionali e sempre con fallback.
- Una chat con 50 sigilli visibili non deve costare più di una lista di 50 immagini: i sigilli non
  animati sono bitmap in cache (`drawWithCache`), non ridisegni per frame.

### 4.9 Suoni

Opzionali (spento di default sul telefono silenzioso): crepa e frantumazione (Roccia), fruscio
(Rune), soffio (Quadro). File CC0 o sintetizzati, < 40 KB l'uno.

### 4.10 Minerale-avatar

Lo stesso `MineralGenerator` (famiglia cristallo) genera l'avatar dal seme dell'utente, salvato nel
profilo (`avatarSeed`). Tutti gli utenti hanno un minerale: coerenza visiva ovunque (lista, chat,
pairing, notifiche come icona grande, storie). La gemma di una chat/gruppo deriva dall'impronta della
chiave di chat: identica per tutti i membri, è anche il modo "bello" di verificare la cifratura.

---

## 5. Sicurezza e crittografia

### 5.1 Principi

- Il server (Firebase) non può leggere né messaggi né chiavi di chat. Vede: chi parla con chi, quando,
  quanto pesa un messaggio, la tecnica **no** (è dentro la busta), le reazioni (in chiaro, scelta
  deliberata), i nomi e le chiavi pubbliche degli utenti.
- Una sola libreria crittografica, API "lightweight" di **Bouncy Castle** (`bcprov-jdk18on`), senza
  registrare il provider JCA (Android ne ha uno vecchio con lo stesso nome): X25519, Ed25519,
  HKDF-SHA256, AES-256-GCM, Argon2id. Tutto in `:core:crypto`, puro JVM, coperto da test con
  vettori noti.
- Nessuna primitiva scritta a mano. Nessun XOR "a xorshift".

### 5.2 Identità e vault

- Al primo avvio: `IdentityBundle = { x25519 (accordo), ed25519 (firma), keyringSeed (32 B) }`.
- **KEK** = Argon2id(segreto, salt 16 B, m = 64 MiB, t = 3, p = 2; su dispositivi lenti m = 32 MiB,
  parametri salvati nel vault). Il segreto è PIN (6 cifre) o password (≥ 8 caratteri).
- **Vault** = AES-256-GCM(KEK, IdentityBundle). Salvato in `users/{uid}/vault/main` e in locale.
- **Sblocco locale veloce**: il bundle è anche cifrato con una chiave AES dell'Android Keystore che
  richiede autenticazione dell'utente (biometria o credenziale del dispositivo). All'avvio parte il
  prompt biometrico; in fallback PIN/password → Argon2id.
- Tentativi: dopo 5 errori consecutivi, attesa crescente (Argon2 è già lento; l'attesa è UX).
- **Codex ID** = `CDX-` + 8 caratteri Crockford base32 da SHA-256("codex-id-v1" ‖ chiave pubblica
  Ed25519) (es. `CDX-7K3P-2M9Q`); scritto in `codexIds/{id} → uid` per la ricerca manuale.
  *Deciso in M1, correggendo il piano iniziale che lo derivava dall'uid Firebase*: nascendo dalla
  chiave di firma, l'ID esiste prima dell'account, non cambia quando l'account viene collegato, ed
  e' verificabile da chi riceve la scheda — un server ostile puo' consegnare la scheda sbagliata, ma
  non una il cui Codex ID corrisponda a quello detto a voce.

### 5.3 Dispositivi multipli

L'identità è **per account**, non per dispositivo. Un nuovo telefono: Google → scarica il vault →
PIN/password → ha le stesse chiavi. Il **keyring** (chiavi di chat, chiavi di gruppo per epoca,
schede dei contatti) è un documento cifrato con `keyringKey = HKDF(keyringSeed, "codex-keyring")`
in `users/{uid}/keyring/main`, aggiornato a ogni nuova chat e letto da ogni dispositivo. Ogni
dispositivo ha il proprio `deviceId`, token FCM e voce in "Dispositivi collegati" (revocabile:
cancella token e forza il ri-sblocco).

### 5.4 Pairing e scheda contatto

**Scheda** (`ContactCard`, ~220 byte, firmata Ed25519):
`{ uid, codexId, name, avatarSeed, x25519Pk, ed25519Pk, issuedAt, sig }`.

- **Vicinanza**: Nearby Connections in modalità pairing. Alla connessione Nearby fornisce un token
  di autenticazione: lo si mappa a una runa, entrambi vedono la stessa e confermano. Scambio schede
  sul canale, verifica firma, salvataggio contatto su entrambi. Funziona offline.
- **QR**: il QR contiene la scheda + un *invite token* monouso (32 B). Chi scansiona (B) ottiene la
  scheda di A subito; per far arrivare la propria ad A scrive in `inbox/{A}/items` una richiesta
  `pairing_auto` cifrata per A con il token: A la accetta senza chiedere nulla (il token prova che è
  stato scansionato il suo QR). Se B è offline, la richiesta parte alla prima rete; se sono entrambi
  offline compare "Avvicinate i telefoni" e lo scambio passa da Nearby.
- **Manuale**: B scrive il Codex ID di A; l'app legge la scheda pubblica di A da `users/{A}`
  (verifica che il Codex ID corrisponda e che la firma sia valida), mostra minerale e nome, invia una
  richiesta; A la vede, confronta il minerale e accetta. Qui il server potrebbe in teoria sostituire
  una scheda: è il caso in cui la **parola d'ordine** fa da difesa.

Un contatto può essere eliminato; l'altro riceve `peer_deleted` e vede la chat come "non più tra i
contatti".

### 5.5 Rito della parola d'ordine → chiave di chat

```
shared   = X25519(myX25519Sk, peerX25519Pk)
salt     = 16 B casuali scelti da chi crea la chat, scritti nel documento della chat
pp       = Argon2id(NFKC(parola in minuscolo, spazi collassati), salt, m = 32 MiB, t = 2, p = 1)
chatKey0 = HKDF-SHA256(ikm = shared ‖ pp, salt, info = "codex-chat-v1|" + uid minore + "|" + uid maggiore)
```

Verifica: chi crea scrive `handshake = AES-GCM(HKDF(chatKey0, "handshake"), "CODEX-OK|" + entrambi gli uid)`
nel documento della chat; l'altro decifra → parola giusta → scrive il proprio handshake → chat
`ready`. Errore → "Il sigillo non combacia". La parola non viene mai salvata; `chatKey0` va nel
keyring. La **gemma della chat** deriva da `HKDF(chatKey0, "emblem")`.

### 5.6 Busta del messaggio (formato CDX3)

```
"CDX3" (4 B) · versione u8 · epoca chiave u16 · lunghezza u32 · ciphertext+tag
mk    = HKDF-SHA256(chatKey[epoca], info = "codex-msg|" + msgId)
nonce = SHA-256(msgId)[0..12]
ct    = AES-256-GCM(mk, nonce, plaintext, aad = chatId ‖ msgId ‖ senderUid)
```

`msgId` è un UUID v4 generato dal mittente: la coppia chiave/nonce è unica per costruzione, senza
contatori da sincronizzare fra dispositivi. Il **plaintext** è CBOR (kotlinx-serialization):

```
{ t: "text"|"photo"|"voice"|"system", text?, media?: { ref, key, nonce, mime, size, w, h, dur, thumb },
  seal?: { technique, paintingId }, viewOnce?: bool, ttl?: sec, replyTo?: msgId, mentions?: [uid] }
```

### 5.7 Media

File cifrati con chiave casuale per file (AES-256-GCM a blocchi da 1 MiB, nonce contatore) caricati
su Cloud Storage in `media/{chatId}/{msgId}/{blobId}`; chiave e nonce dentro il corpo cifrato.
Regole Storage: lettura/scrittura solo ai membri della chat; ciclo di vita 31 giorni.

### 5.8 Gruppi

- Chi crea genera `groupKey[0]` (32 B). Ogni membro la riceve tramite `inbox/{uid}/items`
  (tipo `group_key`), cifrata con la chiave a coppia
  `pairKey = HKDF(X25519(mittente, membro), "codex-pair")` e firmata dal mittente.
- Chi entra deve avere la scheda di chi lo invita (è un contatto, oppure la riceve nel link/QR di
  invito e la verifica su `users/{uid}`). L'invitante consegna la chiave quando è online; finché non
  arriva, il nuovo membro vede "In attesa della chiave da {nome}".
- Rimozione o abbandono → l'admin (o il primo membro online) genera l'epoca successiva e la
  distribuisce; i messaggi vecchi restano leggibili con le epoche vecchie del keyring.
- Il nome del gruppo è cifrato con la chiave di gruppo; il server vede solo gli id.

### 5.9 Storie

Ogni storia è cifrata con una chiave casuale, avvolta per ciascun contatto con `pairKey` (massimo
200 contatti). Documento con TTL 24 h. Chi ha visto → lista viewer per l'autore.

### 5.10 Cosa vede il server

| Vede | Non vede |
|---|---|
| uid, nome, Codex ID, chiavi pubbliche, minerale | segreti di identità, chiavi di chat/gruppo |
| membri di ogni chat, orari, dimensioni | testo, tecnica, foto, vocali, nomi dei gruppi |
| reazioni (emoji/rune) e messaggi fissati (id) | contenuto delle risposte, menzioni |
| token FCM per dispositivo | parola d'ordine |

### 5.11 Blocco app e blocco chat

- **App**: dopo il timeout scelto, schermata di sblocco (biometria di default, PIN/password in
  fallback). Le notifiche non mostrano mai contenuti.
- **Chat bloccata**: apertura con biometria/PIN (BiometricPrompt con credenziale dispositivo in
  fallback); nella lista non ha anteprima; le notifiche dicono "Sigillo in una chat bloccata";
  la ricerca la ignora.
- **FLAG_SECURE**: sempre attivo nel visualizzatore "visualizza una volta" e nelle chat bloccate;
  opzione globale "Blocca screenshot in tutte le chat".

---

## 6. Backend Firebase

### 6.1 Servizi

Auth (Google), Firestore (regione `europe-west1` o multi-regione `eur3`, scelta alla creazione: non
si cambia dopo), Cloud Storage, Cloud Messaging, Cloud Functions (2ª gen., Node 22, TypeScript,
`europe-west1`), App Check (Play Integrity, in enforcement dalla prima beta).

### 6.2 Schema Firestore

```
users/{uid}
  name, codexId, avatarSeed, x25519Pk, ed25519Pk, cardSig, createdAt, updatedAt
  settings: { readReceipts, showOnline, showRevealed }
users/{uid}/vault/main         kdf { alg, m, t, p, salt }, wrapped (bytes), version
users/{uid}/keyring/main       wrapped (bytes), version, updatedAt
users/{uid}/devices/{deviceId} fcmToken, platform, appVersion, lastSeen
codexIds/{codexId}             uid
inbox/{uid}/items/{itemId}     type (pairing_request|pairing_auto|pairing_accept|group_key|
                               group_invite|peer_deleted|profile_update|story_key),
                               fromUid, payload (bytes, cifrato per uid), sig, createdAt, expireAt(7 g)
chats/{chatId}
  type (direct|group), memberUids [..], members { uid: { role, joinedAt, keyEpoch } },
  salt, handshakes { uid: bytes }, ready, keyEpoch, nameEnc (gruppi),
  pinned [msgId], createdAt, updatedAt, lastActivityAt, inviteTokens { hash: { byUid, expireAt } }
chats/{chatId}/messages/{msgId}
  senderUid, senderDeviceId, envelope (bytes), reactions { uid: str },
  editedAt?, deletedForAll?, createdAt (server), expireAt (createdAt + 30 g)
chats/{chatId}/receipts/{uid}  deliveredAt, readAt, revealed [msgId × 50], updatedAt
chats/{chatId}/typing/{uid}    until
presence/{uid}                 state, lastSeen
stories/{uid}/items/{storyId}  envelope, wrappedKeys { uid: bytes }, viewers [uid], createdAt, expireAt(24 h)
```

`chatId` diretto = `sha256(uid minore ‖ uid maggiore)` troncato a 20 byte in base32 (una sola chat
per coppia); gruppo = UUID.

Indici compositi: `messages` su `(createdAt desc)` per chat; `chats` su `memberUids array-contains +
lastActivityAt desc`; `stories` collection group su `expireAt`.

### 6.3 Regole (principi)

- Tutto richiede `request.auth != null` e App Check.
- `users/{uid}`: lettura a ogni utente autenticato (sono chiavi pubbliche), scrittura solo proprietario;
  `vault`, `keyring`, `devices`: solo proprietario.
- `inbox/{uid}/items`: creazione da qualunque utente autenticato con `fromUid == request.auth.uid`,
  lettura/cancellazione solo dal proprietario; dimensione payload ≤ 8 KB.
- `chats/{chatId}`: lettura/scrittura membri; creazione diretta solo se `memberUids` contiene
  entrambi e il creatore; gruppo: modifiche a membri solo dagli admin; `handshakes.{uid}` solo dal
  proprio uid.
- `messages`: creazione solo con `senderUid == auth.uid` e membro; modifica solo del mittente e solo
  di `envelope/editedAt/deletedForAll`; `reactions.{uid}` solo dal proprio uid; cancellazione dal
  mittente o dal destinatario di un "visualizza una volta" (flag nel doc: `viewOnce` in chiaro, sì:
  è l'unico flag visibile, serve alle regole).
- `receipts/{uid}`, `typing/{uid}`, `presence/{uid}`: solo il proprio uid.
- Test delle regole con l'emulatore (`@firebase/rules-unit-testing`) nel repo.

### 6.4 Cloud Functions

| Funzione | Trigger | Cosa fa |
|---|---|---|
| `onMessageCreated` | `chats/{c}/messages/{m}` create | push data-only ai dispositivi degli altri membri; aggiorna `lastActivityAt` |
| `onInboxItemCreated` | `inbox/{u}/items/{i}` create | push data-only al destinatario |
| `onStoryCreated` | `stories/{u}/items/{s}` create | push silenziosa ai contatti presenti in `wrappedKeys` |
| `onMemberRemoved` | `chats/{c}` update | rimuove receipts/typing del membro, notifica |
| `onUserDeleted` | Auth delete | cancella users/*, inbox, storie, media, esce dalle chat |
| `cleanupMedia` | schedulata ogni 6 h | rimuove media orfani e scaduti da Storage |
| `registerDevice` | callable | valida e registra token FCM (il client non scrive token a caso) |

### 6.5 Push

FCM **data-only**, priorità alta, payload `{ kind, chatId, msgId }`: il client costruisce la notifica
in locale ("Marco ti ha mandato una roccia") con canali per chat, anteprima mai in chiaro, raggruppata
per chat, azione "Apri". In Doze arriva comunque (alta priorità); il contenuto si scarica all'apertura
o in un `WorkManager` expedited. Nessuna notifica per le chat silenziate (filtro lato client) e
nessun nome per le chat bloccate.

### 6.6 Conservazione

Politica TTL Firestore sul campo `expireAt` (messaggi 30 giorni, inbox 7, storie 24 h). Storage con
regola di ciclo di vita a 31 giorni. In locale tutto resta finché l'utente non cancella. Il sync
scarica al massimo gli ultimi 30 giorni: un dispositivo nuovo parte da lì.

### 6.7 Presenza e "sta scrivendo"

`presence/{uid}` scritta in primo piano (heartbeat ogni 45 s) e in background (`offline`); "online"
= `lastSeen` < 90 s. `typing/{uid}.until` scritta al massimo ogni 3 s mentre si digita. Ambedue
disattivabili nelle impostazioni (chi le disattiva non le vede e non le manda).

### 6.8 Costi

Ascolti Firestore solo per: lista chat dell'utente (1 query), la chat aperta (messaggi + typing +
receipts), inbox, presenza dei contatti visibili. Un gruppo di amici sta ampiamente nel piano
gratuito; le Functions costano centesimi. Da monitorare: fan-out delle storie e i media.

---

## 7. Trasporto nelle vicinanze

- **Nearby Connections API** (`play-services-nearby`), strategia `P2P_CLUSTER`, service id
  `dev.pampa.codex`.
- **Scoperta di contatti**: quando la modalità vicinanze è attiva, il dispositivo fa advertising con
  un nome effimero (`HMAC(keyringSeed, giorno)` troncato). Alla connessione le due parti si
  autenticano a vicenda con una sfida firmata Ed25519 sulle rispettive schede: solo i contatti
  reciproci restano connessi.
- **Messaggi**: stessa busta CDX3, `Payload.BYTES` per testo, `Payload.FILE` per media (cifrati come
  in cloud). Ricevuti "da vicino" → salvati in Room con `origin = NEARBY, synced = false` e
  consegna confermata sul canale. Il **mittente** carica il messaggio su Firestore alla prima rete
  (stesso `msgId` → nessun duplicato: il documento esiste o no). Il destinatario non carica mai i
  messaggi altrui.
- **Quando è attivo**: mentre l'app è in primo piano; in background con un servizio in primo piano
  (`FOREGROUND_SERVICE_CONNECTED_DEVICE`) per la durata scelta: spento / 15 min dopo l'ultima
  chiusura / sempre. In chat l'icona "Vicino" dice quando il trasporto è diretto.
- **Pairing vicino** usa la stessa base (§5.4).
- Permessi: `BLUETOOTH_SCAN/CONNECT/ADVERTISE` (31+), `NEARBY_WIFI_DEVICES` (33+),
  `ACCESS_FINE_LOCATION` (< 31), `ACCESS/CHANGE_WIFI_STATE`. Richiesti la prima volta che si apre
  una funzione di vicinanza, con spiegazione.
- Rischi noti: Nearby è affidabile fra Android, meno su alcune ROM aggressive col Bluetooth; il
  piano prevede un `NearbyTransport` isolato dietro un'interfaccia `Transport`, così il cloud non ne
  dipende mai.

---

## 8. Architettura Android

### 8.1 Moduli

```
:app            UI Compose + Fluid Engine, navigazione, Hilt, servizi, notifiche, intent
:core:model     tipi puri condivisi (Contact, Chat, Message, SealSpec, …), nessuna dipendenza Android
:core:crypto    Bouncy Castle lightweight: identità, vault, chiavi di chat/gruppo, busta CDX3 (JVM, test)
:core:seal      generatore minerali, rune, stego LSB, catalogo dipinti, semi (Kotlin + android.graphics)
:core:data      Room (SQLCipher), Firestore/Storage/Functions, Nearby, sync engine, WorkManager
engine/*        Fluid Engine (submodule git, tag engine-1.23.0): engine-foundation, engine-ui,
                engine-storage, engine-net, engine-config, engine-update
functions/      Cloud Functions (TypeScript)
firebase/       firestore.rules, storage.rules, firestore.indexes.json, test delle regole
```

Dipendenze: `app → data, seal, crypto, model, engine-ui/config/update`; `data → crypto, model, seal`
(per i semi); `seal → model`; `crypto → model`. L'engine non conosce il dominio; nessun modulo core
conosce Compose.

### 8.2 Librerie e versioni (da confermare in M0 con le versioni del giorno)

| Cosa | Scelta |
|---|---|
| AGP / Kotlin | 8.13.2 / 2.2.20 + KSP 2.2.20-2.0.4 (la stessa catena di ClasseViva Expressive e dell'engine; Gradle wrapper 8.14.3) |
| Compose | BOM 2026.03.01, Material 3 1.5.0-alpha16 (Expressive), `compileSdk 36`, `targetSdk 36`, **`minSdk 28`** |
| DI | Hilt 2.58 |
| Navigazione | Navigation Compose 2.9.x + `FluidRouteMotionHost` |
| Dati | Room 2.8.x + SQLCipher for Android 4.6.x (chiave dal Keystore), DataStore 1.2.x |
| Serializzazione | kotlinx-serialization (JSON + CBOR) |
| Firebase | BOM ultima stabile: auth, firestore, storage, messaging, functions, appcheck-playintegrity |
| Accesso | `androidx.credentials` + `googleid` (Credential Manager) |
| Vicinanze | `play-services-nearby` 19.x |
| QR | ML Kit `barcode-scanning` (lettura) + CameraX 1.5; ZXing `core` (generazione) |
| Immagini | Coil 3 |
| Audio | `MediaRecorder` (registrazione), Media3 ExoPlayer (riproduzione) |
| Crittografia | `bcprov-jdk18on` 1.80 (API lightweight) |
| Background | WorkManager 2.10.x |
| Biometria | `androidx.biometric` 1.4.x |
| Test | JUnit 5 (core), Turbine, MockK, Compose UI test, Firebase Emulator Suite |

### 8.3 Dati locali (Room, SQLCipher)

```
contacts(uid PK, codexId, name, avatarSeed, x25519Pk, ed25519Pk, cardSig, addedAt, deletedByPeer)
chats(id PK, type, name, emblemSeed, keyEpoch, ready, pinnedMsgIds, ttlSeconds, locked, muted, pinnedAt, lastActivityAt, unread)
chat_members(chatId, uid, role, joinedAt, keyEpoch)  PK(chatId, uid)
messages(id PK, chatId, senderUid, type, bodyPlain (CBOR decifrato), envelope, technique, seed, paintingId,
         status (pending|sent|delivered|read|revealed|burned|error), revealedOnce, viewOnce, expiresAt,
         replyTo, editedAt, deletedForAll, origin (cloud|nearby|local), synced, createdAt)
message_fts(rowid → messages.id, text)   -- solo messaggi revealedOnce, FTS4
reactions(msgId, uid, value)  PK(msgId, uid)
media(msgId, blobId, localPath, key, nonce, mime, size, state)
outbox(msgId PK, chatId, attempts, nextAttemptAt, transportHint)
receipts(chatId, uid, deliveredAt, readAt)
stories(id PK, authorUid, envelope, key, createdAt, expireAt, viewed)
keyring(kind, id, epoch, key)   -- chiavi di chat/gruppo, cifrate a riposo
settings(DataStore)             -- preferenze non sensibili
```

La chiave del database e le chiavi del keyring sono avvolte da una chiave AES nell'Android Keystore
(non biometrica: serve alle notifiche in background). Il vault (identità) è a parte (§5.2).

### 8.4 Motore di sincronizzazione

- `SyncEngine` osserva: lista chat (Firestore), messaggi della chat aperta (ultimi 30 giorni,
  paginati a 80), inbox, presenza. Ogni documento ricevuto passa da `MessageDecryptor` e finisce in
  Room; la UI legge **solo Room** (una sola fonte di verità).
- `Outbox`: ogni invio scrive prima in Room (`pending`), poi `TransportRouter` sceglie Nearby (se il
  destinatario è connesso) o cloud; retry con backoff; `WorkManager` per l'invio con app chiusa.
- Ricevute: `deliveredAt` alla ricezione, `readAt` all'apertura della chat, `revealed` al
  rivelamento (se abilitato).
- Conflitti: i messaggi sono immutabili per id; modifica = nuova busta sullo stesso id con
  `editedAt` maggiore vince.

### 8.5 Processi in background

- `CodexMessagingService` (FCM) → scarica il documento → decifra → notifica locale.
- `NearbyService` (foreground, opzionale) → advertising/discovery/connessioni.
- Worker: `OutboxWorker`, `MediaDownloadWorker`, `ExpiryWorker`, `KeyringSyncWorker`,
  `ManifestRefreshWorker` (engine-config `refreshIfStale`).
- Boot receiver: ripianifica le scadenze.

### 8.6 Struttura delle cartelle dell'app

```
app/src/main/kotlin/dev/pampa/codex/
  CodexApp.kt · MainActivity.kt · di/ · navigation/
  ui/theme/CodexTheme.kt (FluidTheme + AccentPreset "codex")
  ui/seal/  (RuneSeal, RockSeal, PaintingSeal, RevealSession, SealBubble)
  ui/chat/  ui/chats/  ui/contacts/ (pairing, qr, nearby, manual, ritual)
  ui/groups/  ui/stories/  ui/settings/  ui/origins/  ui/onboarding/  ui/lock/  ui/viewer/
  notifications/  services/  share/ (Apri con Codex)
```

---

## 9. Design system e tema

- `FluidTheme` con `AccentPreset("codex", "Codex", light = ametista scuro, dark = ametista chiaro)`;
  i due valori esatti si fissano in M0 sul contrasto verificato da `FluidContrastTest`
  (punto di partenza: chiaro `#5E35A8`, scuro `#CBB1FF`). Tema **scuro di default**
  (`EngineThemeMode.DARK` nelle impostazioni iniziali), chiaro e sistema disponibili; colore dinamico
  come opzione.
- **Canvas ambientale** (`FluidScreen(ambient = …)`): fondale "ossidiana" con sfaccettature lente e
  vene di ametista, quasi ferme, che si spostano con lo scroll; è ciò che il vetro rifrange. Obbedisce
  a `FluidMotionPolicy`.
- Componenti: solo quelli dell'engine (`FluidScreen`, `FluidListGroup/Row`, `FluidSheet`,
  `FluidAlert`, `FluidTabBar/Rail`, `FluidMorphMenuButton`, `fluidContextMenu`, `FluidTextField`,
  `FluidButton`, `FluidNotificationHost`, `FluidTutorialHost`, `FluidEmptyState`, …). Nessun colore,
  dimensione o raggio scritto a mano nelle schermate; angoli `ContinuousCornerShape`/`FluidRadius`;
  molle `FluidMotion`; aptica `rememberFluidHaptics()`.
- Le bolle-sigillo sono l'unico componente "nuovo" e sono dell'app (conoscono il dominio):
  `SealBubble` con varianti per tecnica, sempre su `FluidCard`-like con vetro dove c'è canvas.
- Iconografia: Material Symbols (rounded); l'icona dell'app è un vettoriale adattivo: sfondo
  ossidiana, primo piano geode di ametista con una runa incisa luminosa (ᚲ o ᛟ, decisione in M0 con
  2-3 varianti da guardare), più il livello monocromatico per le icone a tema (API 33+).
- Tipografia dell'engine; per le rune il font incluso, dimensione da `MaterialTheme.typography`.

---

## 10. Contenuti e asset

| Asset | Fonte | Licenza | Dove |
|---|---|---|---|
| ~30 dipinti | NGA, The Met, AIC, Rijksmuseum, CMA (open access) | CC0 | `app/src/main/assets/paintings/*.webp` + `paintings.json` |
| Font rune/ogham | Noto Sans Runic, Noto Sans Ogham | OFL 1.1 | `res/font/` |
| Suoni | CC0 (Freesound) o sintetizzati | CC0 | `res/raw/` |
| Immagine del cifrario | fornita dall'utente (alta qualità) | uso nell'app con la citazione della fonte se identificata | `assets/origins/` |
| Icona | disegnata nel progetto | — | `res/mipmap-anydpi-v26/` |

I crediti dei dipinti e dei font compaiono in Informazioni accanto alle licenze dell'engine.
Dimensione APK obiettivo: < 25 MB (la vecchia era 24 MB con 45 MB di wasm decompressi).

---

## 11. "Le origini" e la ricerca storica

Contenuto della sezione:
1. **L'immagine originale** del cifrario, zoomabile, con etichette che spiegano le sue parti:
   alfabeto (A–Z con simboli), il nomenclatore (nomi con un simbolo solo), le sillabe (ba be bi bo bu …
   za ze zi zo zu), le doppie (bb cc dd … uu) e le **nulle** (simboli senza significato per confondere).
2. **Cos'è un nomenclatore** e come si usava nelle cancellerie del Quattro-Cinquecento (una pagina).
3. **La ricerca**: da dove viene questo cifrario (stato, epoca, archivio), quando l'avremo trovato.
4. Una riga sulle origini di Codex (la verifica in classe, il cifratore di due ore) — breve, nel tono
   dell'app.

Lettura provvisoria del nomenclatore nell'immagine: *Papa, Imperatore, Re di Francia, Pisa, Firenze,
[Ferrara?], [Domani?], [Salviati?/Sanesi?], [Doubr…?], [Some], Alamannia, Nulle*. La compresenza di
Pisa e Firenze come voci separate, con Re di Francia e Imperatore, indica una cancelleria toscana
nell'epoca delle guerre d'Italia (c. 1494–1530, Pisa ribelle 1494–1509).

Piste per la ricerca (attività di M8):
- **Chiedere al prof** da dove ha preso l'immagine (la pista più corta).
- Vito, *La crittografia diplomatica e militare nell'Italia del Quattrocento*, NAM n. 21 (2025), PDF:
  https://www.nam-sism.org/Articoli/Articoli%202025/NAM%20N.%2021.%207.%20VITO%20La%20crittografia%20diplomatica%20e%20militare%20nell'Italia%20del%20Quattrocento.pdf
- Somogyi, *Caratteristiche strutturali di cifrari monoalfabetici italiani*, Verbum 2016:
  https://www.epa.oszk.hu/05200/05289/00028/pdf/EPA05289_verbum_2016_1-2_195-217.pdf
- Meister, *Die Anfänge der modernen diplomatischen Geheimschrift* (1902, facsimili di chiavi
  fiorentine, milanesi e pontificie; su archive.org) e *Die Geheimschrift im Dienste der päpstlichen
  Kurie* (1906).
- Kahn, *The Codebreakers*, capitolo sui nomenclatori italiani.
- Archivio di Stato di Firenze: fondi *Dieci di Balia* e *Signori, Dieci di Balia, Otto di Pratica
  — Legazioni e commissarie*; Archivio di Stato di Siena e di Lucca per l'ipotesi non fiorentina.
- Pasini, *Delle scritture in cifra usate dalla Repubblica di Venezia* (per confronto).

Le ricerche fatte nella sessione di progettazione non hanno ancora identificato il documento: le
fonti sopra sono i punti di partenza.

---

## 12. Milestones

Ordine deciso per arrivare presto a **provare i sigilli sul telefono** (il cuore dell'app) e per
tenere il backend dietro interfacce. Ogni milestone finisce con: build di debug installata, test
verdi, `engine-doctor` pulito, stato aggiornato qui sotto. Taglie: S ≈ una sessione, M ≈ 2-3, L ≈ 4+.

### M0 · Fondamenta (M)

- Struttura repo: Android alla radice di `C:\VibeCoded Projects\Codex` (il `manifest.json` del Pampa
  Store resta alla radice; `docs/` con questo piano; `functions/`, `firebase/`).
- Engine come submodule `engine/` al tag `engine-1.23.0` (`git -c protocol.file.allow=always
  submodule add …`, poi `engine-install.ps1`), `engine.properties`, moduli: foundation, ui, storage,
  net, config, update.
- Gradle: catalogo versioni, moduli `:app`, `:core:model`, `:core:crypto`, `:core:seal`,
  `:core:data`; `applicationId dev.pampa.codex`, `.debug` in debug; firma release da
  `keystore.properties` (schema di Pampa Widgets); `.gitignore` con `google-services.json`,
  `keystore.properties`, `local.properties`.
- `CodexTheme` su `FluidTheme` con l'accento ametista; `MainActivity` con `FluidTabBar` a tre tab
  vuote; canvas ambientale ossidiana v1; icona adattiva (3 varianti da confrontare, poi una).
- engine-config e engine-update collegati al manifest remoto (raw GitHub di `manifest.json`
  con la sezione `engine`), pagina Informazioni con `fluidLicensesSection()`.
- Firebase: `google-services.json` **valido** per `dev.pampa.codex` e `dev.pampa.codex.debug`,
  Auth Google abilitata, SHA-1/SHA-256 di debug e di `pampa.jks` registrati, App Check in
  monitoraggio, progetto Functions inizializzato con `firebase.json` ed emulatori.
- Script: `tools/build-debug.ps1`, `tools/frames.ps1` (cattura fotogrammi per le animazioni).
- **Accettazione**: `gradlew :app:assembleDebug testDebugUnitTest` verde; app che si apre con le tre
  tab in vetro sul telefono; `engine-doctor` pulito; emulatori Firebase che partono.

### M1 · Identità, vault, onboarding (L)

- `:core:crypto`: generazione identità, Argon2id, vault, keyring, Codex ID, scheda firmata; test con
  vettori noti e round-trip.
- Accesso Google (Credential Manager); creazione/lettura `users/{uid}`, `vault`, `keyring`,
  `devices`, `codexIds`; regole Firestore v1 con test.
- Sblocco: Keystore + BiometricPrompt, PIN/password, timeout, tentativi.
- `MineralGenerator` v1 (cristalli) e minerale-avatar; onboarding 4 schermate + percorso "nuovo
  dispositivo"; schermata Io/Profilo e Sicurezza.
- **Accettazione**: nuovo utente in < 90 s; secondo dispositivo (emulatore va bene) che ritrova
  identità e Codex ID col PIN; avvio con prompt biometrico; nessun segreto in chiaro su disco (audit
  con `adb shell run-as` in debug).

### M2 · I sigilli (L)

- `:core:seal`: rune (alfabeto, mapping, semi), `MineralGenerator` completo (cristalli + pietre,
  frattura Voronoi), catalogo dipinti (prima 8 opere, poi 30), stego LSB con test di round-trip e
  capacità, `SealSpec` dal seme.
- App: `SealBubble` per le tre tecniche, `RevealSession`, le tre animazioni di rivelazione, shader
  AGSL con fallback, "Rivela tutto", risigillatura all'uscita/60 s, aptica, suoni, `FluidMotionPolicy`.
- **Playground** (solo build di debug, voce nascosta in Informazioni): una chat finta locale per
  provare tecniche, semi, testi lunghi/corti, foto, vocali. È qui che le animazioni diventano
  "perfette" prima che esista il backend.
- **Accettazione**: le tre rivelazioni misurate con `tools/frames.ps1` senza frame > 16 ms su
  telefono e senza lampi; un messaggio di 300 caratteri in rune si rivela in ≤ 6 s; il PNG esportato
  da Quadro e reimportato restituisce la stessa busta byte per byte.

### M3 · Contatti e pairing (L)

- Scheda contatto, verifica firma, `contacts` in Room + keyring; schermate Il mio sigillo (QR),
  Scanner (ML Kit + CameraX), Manuale, richieste in arrivo (inbox), Pairing vicino (Nearby in
  modalità pairing con la runa di conferma).
- Rito della parola d'ordine: derivazione chiave, handshake, gemma della chat, keyring sync.
- Eliminazione contatto e `peer_deleted`.
- **Accettazione**: QR con una sola scansione in entrambi i versi; manuale con accettazione; vicino
  fra telefono e un secondo dispositivo (o rinviato a M7 se il tablet non c'è ancora, con test di
  unità sul protocollo); parola sbagliata → "non combacia", giusta → gemme identiche sui due lati.

### M4 · Messaggistica cloud, testo (L)

- Firestore chat/messaggi/receipts/typing/presence; `SyncEngine`, `Outbox`, `TransportRouter`
  (solo cloud per ora), Room + SQLCipher, decifratura, FTS.
- Chat: lista, schermata chat, composer, invio con tecnica sorpresa/override, risposte, reazioni,
  modifica, elimina per me/per tutti, fissati, ricerca, bozze, "sta scrivendo", online, spunte,
  rivelato, silenzia, paginazione a 80.
- Functions v1 (`onMessageCreated`, `onInboxItemCreated`, `registerDevice`) + FCM + notifiche locali
  con canali; TTL policy 30 giorni; App Check enforcement in debug con token di debug.
- **Accettazione**: due account che chattano con l'app chiusa su uno dei due e notifica entro 5 s in
  rete buona; riavvio senza perdere nulla; modifica/elimina propagati; storico > 30 giorni solo in
  locale (simulato con `expireAt` corto).

### M5 · Foto, vocali, visualizza una volta, scadenza (M)

- Cifratura media a blocchi, Storage rules e lifecycle, upload/download con Worker, miniature,
  visualizzatore, registratore vocale e player, `FLAG_SECURE`, distruzione locale/cloud del
  "visualizza una volta", scadenze con alarm/Worker, `cleanupMedia`.
- **Accettazione**: foto da 12 MP inviata in < 4 s su Wi-Fi; vocale di 2 min; "una volta" che non
  lascia file sul disco (verifica con `run-as`); scadenza a 30 s che cancella su entrambi i lati.

### M6 · Gruppi (M)

- Creazione, chiave di gruppo per epoca via inbox, inviti QR/vicino/link, consegna chiave,
  admin, abbandona, sciogli, rotazione alla rimozione, info gruppo, menzioni, nome cifrato.
- **Accettazione**: gruppo a 3 con un membro rimosso che non legge più i nuovi messaggi ma conserva
  i vecchi; ingresso da link con invitante offline → "in attesa", poi consegna.

### M7 · Vicinanze (L)

- `NearbyTransport` completo: advertising effimero, autenticazione con sfida firmata, invio
  testo/media, dedup e sync al ritorno della rete, foreground service con durata scelta, icona
  "Vicino" in chat, permessi con spiegazioni.
- **Accettazione**: telefono e tablet in modalità aereo che si scambiano testo e foto; alla
  riaccensione della rete i messaggi compaiono in cloud una volta sola; batteria: < 3 %/h con
  vicinanze "sempre" a schermo spento (misura indicativa).

### M8 · Storie, Le origini, tablet, blocco chat, rifiniture (L)

- Storie 24 h con chiavi avvolte e `onStoryCreated`; sezione Le origini (immagine, testi, ricerca
  storica effettuata); layout Expanded e tab rail; blocco per chat; tastiera incognito; impostazioni
  complete; inglese completo (tutte le stringhe in `values`/`values-en`); callout `FluidTutorialHost`;
  "Apri con Codex" e share target; Informazioni con crediti; dispositivi collegati e revoca;
  eliminazione account.
- **Accettazione**: nessuna stringa hardcoded (lint `MissingTranslation` e `HardcodedText` a errore);
  tablet in orizzontale con lista + chat; storia che sparisce dopo 24 h.

### M9 · Prima beta e stabilizzazione (M, poi continua)

- Passata su tutti i criteri di accettazione; test delle regole Firebase; audit dei permessi; proguard/R8
  con `isMinifyEnabled = true` e regole per Bouncy Castle/SQLCipher/Firestore; App Check enforcement;
  changelog; `versionName 1.0.0-beta.1`, `versionCode 100`.
- Pubblicazione **beta** sul Pampa Store (skill `pampa-store-publish-direct`, canale beta, tag
  `beta-Codex-v1.0.0-beta.1`), manifest con sezione `engine`.
- Da qui: ogni aggiornamento è una beta (`beta.2`, `beta.3`, …) finché l'utente dichiara la 1.0.0;
  bug e rifiniture guidati dall'uso reale; il piano si aggiorna con una sezione "Post-beta".

**Stato**: M0 ✅ · M1 ◐ (locale + accesso Google; manca il vault sul cloud) · M2 ✅ (senza suoni) · M3 ✅ (contatti, QR, rito, chiavi, rubrica cercabile; vicinanze rinviate a M7) · M4 ✅ (consegna, notifiche, spunte, "sta scrivendo", presenza, pagine; reazioni e ricerca rinviate) · M5 ✅ (foto, note vocali, visualizzatore, scadenza, visualizza una volta con FLAG_SECURE, cleanupMedia, consegna in background) · M6 ✅ (gruppi, chiave per epoca, posta, inviti per link, regole e Function) · M7 ✅ (trasporto vicino, nome effimero, sfida firmata, servizio, provato fra due dispositivi) · M8 ✅ (storie con chiavi avvolte, blocco per chat, tastiera incognito, inglese completo, callout al primo uso, crediti, dispositivi collegati, eliminazione account, due pannelli sui tablet) · M9 ◐ (engine 1.30.1, R8 provato installando il rilascio, App Check nel codice, audit di permessi e disco, `1.0.0-beta.1`; restano App Check in console, la prova con due account veri sulla release, e la pubblicazione) — al 2026-09-09

### Chiusura M0 (2026-09-05)

Fatto: struttura del repo (radice Gradle, `app`, `core/model`, `core/crypto`, `core/seal`,
`core/data`, `functions/`, `firebase/`, `tools/`), engine come submodule al tag `engine-1.23.0`
(l'engine e' passato alla 1.23.0 il giorno stesso: rispetto alla 1.22.0 aggiunge solo `engine-ai`,
che Codex non include), `engine.properties`, tema `CodexTheme` con l'accento ametista, scuro di
default al primo avvio, radice con `FluidTabBar` (Chat · Storie · Io), host di modali/notifiche/
menu alla radice, scheda Io con Aspetto (tema, colore dinamico, nero assoluto, aptica),
Informazioni (versione, engine, aggiornamento in-app, sorgente), Le origini (segnaposto) e
`fluidLicensesSection`; icona adattiva (geode con runa Kaunan) con livello monocromatico;
stringhe IT/EN; scaffolding Firebase (regole chiuse, indici, `firebase.json`, Functions con
`health`); `manifest.json` con la sezione `engine`; script `tools/build-debug.ps1` e
`tools/frames.ps1`. Build e 8 test verdi; `engine-doctor` pulito; app provata sull'emulatore
`codex_api35_pixel` (screenshot in `tools/frames-out/`, git-ignorati).

In sospeso, a carico dell'utente (§15): `app/google-services.json` valido (Firebase non e' ancora
collegato: la build lo dice a ogni compilazione), `keystore.properties` (la release oggi si firma con
la chiave di debug), verifica sul telefono fisico, commit iniziale.

Decisioni prese in corso d'opera: versioni della toolchain allineate a ClasseViva (Kotlin 2.2.20 e
KSP, non 2.4: §8.2); `FileProvider` per gli aggiornamenti non serve (l'installatore dell'engine usa
`PackageInstaller`); la riga "Aggiornamenti" e' disattivata nelle build di debug perche' il manifest
descrive ancora la vecchia 0.2.4 e proporrebbe un "aggiornamento" sbagliato.

### Stato M1 (2026-09-07) — parte locale fatta, parte cloud in attesa di Firebase

**Fatto**: `:core:crypto` completo (Argon2id, AES-256-GCM, HKDF, X25519, Ed25519, identita', vault,
Codex ID, scheda contatto firmata) con vettori noti da RFC 5869/7748/8032/9106 e dalla specifica
GCM; generatore di minerali deterministico in `:core:seal` (SplitMix64 con valori di riferimento
bloccati in un test); vault su disco piu' chiave biometrica nell'Android Keystore
(`setUserAuthenticationRequired`, invalidata da nuove impronte) in `:core:data`; onboarding a
quattro passi con la gemma che si spacca davvero e il nome che diventa il minerale-avatar;
schermata di sblocco con tastierino, tentativi ed attesa crescente; scheda Io con profilo, Codex ID
copiabile, biometria, cambio segreto in tre passi, blocco automatico e cancellazione totale.
36 test verdi; flusso provato da capo a fondo sull'emulatore (screenshot in `tools/frames-out/`).

**Verificato**: creazione identita' in meno di 90 s; PIN sbagliato che scuote e non entra; PIN
giusto che riapre; `run-as` mostra un solo file (`vault.bin`, 175 byte, `-rw-------`) senza nessuna
stringa in chiaro.

**In attesa** (serve `google-services.json`, §15): accesso Google, `users/{uid}`, vault e keyring
sincronizzati, `devices`, `codexIds`, e i test delle regole Firestore. Il percorso "secondo
dispositivo" e' scritto e coperto dai test di unita' del vault, ma non e' provabile finche' il vault
non viaggia. **Il prompt biometrico all'avvio** e' implementato ma non verificabile su questo
emulatore (nessuna impronta registrabile): va provato sul telefono.

### Stato M2 (2026-09-07) — sigilli fatti, tranne i suoni

**Fatto**: le tre tecniche funzionano dentro una conversazione vera, non solo nel Playground.

- **Roccia**. Minerali deterministici (`:core:seal`), rottura in due tempi: le crepe si accendono
  lungo i bordi delle future schegge per il primo 14%, poi ogni scheggia parte verso l'esterno con
  una curva di scatto-e-rallentamento, ruota e sfuma. Le schegge **ritagliano l'immagine del
  minerale**, quindi portano via esattamente la superficie che avevano sopra.
- **Rune**. Elder Futhark, mappatura per seme, rivelazione lettera per lettera con posizionamento
  per glifo (`TextMeasurer`): il testo non si riflette mentre si scioglie.
- **Quadro**. Il dipinto si stacca a scaglie irregolari che cadono, ruotano e lasciano sotto
  l'impronta scura della stessa immagine. Il velo che rende leggibile il testo se ne va insieme
  alle scaglie, cosi' l'ultimo fotogramma della copertina e' gia' il fondo della bolla.

**La superficie dei minerali** e' stata rifatta: grana, inclusioni, spigoli fra le facce, ombra al
bordo, luce trasmessa e colpo di luce, tutto disegnato **una volta sola in un'immagine** e poi
copiato. E' la ragione per cui trenta schegge, ognuna con dentro l'intero disegno, costano quanto
trenta copie e non trenta disegni.

Tre difetti trovati guardando i fotogrammi e corretti:

1. le tessere del quadro erano una griglia regolare che spariva lasciando il colore della bolla: si
   leggeva come un JPEG rotto, non come un dipinto che si sfalda;
2. le placche della pietra partivano tutte dal centro, e con i bordi disegnati la pietra sembrava
   una fetta di limone;
3. l'ultima scheggia della roccia si fermava dentro il riquadro e restava li' a sbiadire, gialla,
   su un messaggio gia' leggibile.

**Chat locale**: chat con se stessi (chiave derivata dal portachiavi dell'identita'), invio, busta
`CDX3` sul database cifrato, sigillo chiuso in arrivo, apertura al tocco, richiusura all'uscita,
"Rivela tutto" per i soli sigilli gia' visti. La scelta della tecnica si apre tenendo premuto
Invia — e li' c'era un difetto: la pressione lunga era su un `fluidPressable` messo **sopra** il
bottone, che ha gia' un rilevatore di gesti suo, quindi non scattava mai e il messaggio partiva.

**Non fatto**: i suoni (non ci sono asset), l'esportazione del PNG con il messaggio dentro (il giro
completo e' provato dal Playground e da `LsbStegoTest`, manca l'interfaccia di condivisione), lo
shader AGSL per il tremolio del quadro (l'implementazione a scaglie non ne ha bisogno).

### Le origini (2026-09-07) — anticipata da M8

La sezione c'e', ed e' l'unica parte del piano che e' stata anticipata: non dipende da Firebase e
non dipende da un secondo telefono, quindi non aveva senso tenerla ferma.

Contiene la storia (la verifica, le due ore di pomeriggio), le **cinque parti** del cifrario
spiegate una per una, un banco di prova che cifra quello che si scrive, la tavola intera (119 voci)
e lo stato della ricerca con le fonti da cui ripartire.

`Nomenclator` in `:core:seal` mette in piedi il cifrario per quello che e' — alfabeto con omofoni
sulle vocali, 65 sillabe, 12 doppie, 10 voci di nomenclatore, 6 nulle — e cifra andando per il
boccone piu' lungo, come faceva un segretario. I segni non sono quelli del manoscritto: sono
composti (una forma piu' un punto o una barra) e scelti fra quelli che Android disegna sempre, per
non ritrovarsi una tavola di rettangoli vuoti. La sezione lo dice apertamente.

**In attesa**: la foto della tavola in buona qualita' (§15). Finche' non arriva, al posto
dell'immagine c'e' una nota che dice cosa manca.

## Da rifare quando arriva Firebase

Niente di quanto sopra: la chat locale usa lo stesso `ChatRepository`, la stessa busta e lo stesso
database della chat vera. Cambia solo da dove arriva la chiave di chat.

### Il quadro che esce e rientra (2026-09-07) — anticipato da M5

Il giro completo funziona ed e' stato provato sull'emulatore da capo a fondo: tenere premuto un
messaggio-quadro, "Condividi come immagine", il selettore di sistema, e da li' di nuovo dentro Codex
attraverso il proprio `intent-filter`.

- **`PaintingParcel` (`CDXP`)** in `:core:crypto`: chat, messaggio e mittente accanto alla busta.
  Servono perche' quei tre valori sono **dati autenticati** della busta: dentro l'app si sanno perche'
  sono righe di un database, dentro un PNG che gira per il mondo no. Nell'involucro non c'e' niente
  di segreto, e un test lo verifica cercando il testo in chiaro fra i byte.
- **`PaintingShare`** in `:app`: scrive il PNG in una cartella della cache che viene svuotata a ogni
  esportazione, e lo passa fuori con un `FileProvider` che espone quella cartella e nient'altro.
- **`ChatRepository.importPainting`**: la busta viene **aperta prima di essere salvata**. Un
  messaggio che non si apre non entra nella conversazione, e uno che si apre e' autentico per
  costruzione.
- Cinque esiti con cinque testi diversi, non un "non ha funzionato". Il piu' importante e' "dentro
  non c'e' niente": il caso vero non e' un'immagine qualsiasi, e' un quadro di Codex **inoltrato
  come foto**, e la ricompressione si porta via i bit bassi insieme al messaggio. Chi non lo sa non
  ha modo di indovinarlo, quindi lo dice l'app.

Provati sull'emulatore: consegna di un messaggio gia' presente ("ce l'hai gia'"), immagine senza
niente dentro, e la casella che tiene il quadro in attesa **mentre l'app e' bloccata** — scegliere
un file da un'altra app fa scattare il blocco automatico, e senza la casella il quadro si sarebbe
perso li'.

### Cronologia della chat (2026-09-07)

Orario sotto ogni bolla (fuori dalla bolla: la copertina di una roccia non ha un angolo libero, e
l'ora di un messaggio non e' il suo contenuto), separatori di giorno con "Oggi" e "Ieri", e il menu
della pressione lunga con condividi, copia ed elimina. `timelineOf` sta in un file suo con sei test:
i separatori hanno una regola, e una regola si prova senza accendere un telefono.

### Storie (2026-09-07) — anticipata da M8, parte locale

La scheda non e' piu' un segnaposto. Si pubblica il proprio sigillo del giorno, compare come anello
con il minerale di *quella* storia (non l'avatar: il seme e' il suo), dice quante ore le restano, si
apre a tocco con il rituale completo e si richiude uscendo.

- `stories` e' una tabella nuova, aggiunta con una **migrazione scritta a mano** invece di lasciar
  ricreare il database: i messaggi di qualcuno non si buttano via per aggiungere una tabella,
  nemmeno prima del rilascio.
- La busta e' la stessa dei messaggi, con un contesto autenticato suo (`codex-stories`): una busta
  di storia non si puo' far passare per un messaggio, ne' viceversa.
- La scadenza si filtra **nella query**. Una storia scaduta che resta in lista finche' qualcuno fa
  pulizia sarebbe una storia che dura piu' di un giorno, e la durata e' tutto quello che una storia
  e'.
- Le storie degli altri arriveranno con i contatti: cambiera' solo da dove viene la chiave.

### Scadenza e "visualizza una volta" (2026-09-07) — anticipati da M5

- **Impostazioni di una conversazione**: emblema, scadenza (per sempre / un'ora / un giorno / una
  settimana) e "svuota la conversazione".
- La scadenza parte **dall'apertura**, non dall'arrivo, ed e' scritto anche nell'interfaccia: un
  conto che parte da quando il messaggio e' arrivato farebbe sparire un messaggio mai letto, che e'
  il contrario di quello che serve. La pulizia si fa aprendo la chat, non con un processo in
  background per ogni conversazione.
- **"Visualizza una volta"** e' un interruttore nel foglio della tecnica e vale per il **prossimo**
  messaggio soltanto: lasciarlo acceso farebbe sparire il successivo senza che nessuno l'abbia
  chiesto. Il messaggio si consuma **uscendo dalla chat**, non alla fine dell'animazione: un testo
  che si cancella mentre lo si sta ancora leggendo non e' "visualizza una volta", e' "visualizza per
  un secondo". Mentre e' aperto la finestra ha `FLAG_SECURE`.
- Il messaggio bruciato resta come segnaposto ("Sigillato per sempre"): si vede che qualcosa c'e'
  stato.

### Schermi larghi (2026-09-07) — anticipati da M8

Un difetto vero, trovato ruotando l'emulatore: **in orizzontale la serratura era inutilizzabile.**
Restava visibile la prima fila di tasti, senza modo di scorrere, e l'app non si poteva sbloccare.

- La serratura in orizzontale affianca il ritratto (minerale, nome, messaggio) e il modo di entrare,
  e il tastierino si stringe da solo fino a un minimo sotto cui un tasto non si centra piu' col
  pollice. Sopra tutto c'e' comunque uno scorrimento.
- La navigazione passa **di lato** sopra i 600 dp di larghezza (`FluidTabRail`, la variante che
  l'engine ha gia'): in orizzontale la barra in basso mangiava proprio l'altezza che manca.
- La colonna dei messaggi si ferma a 620 dp e sta in mezzo: una bolla sola su una riga da duemila
  pixel non si legge come una conversazione.
- Il foglio del cambio segreto scorre, per lo stesso motivo della serratura.

### La raccolta di dipinti (2026-09-07)

Da 16 a 36 opere, 5,1 MB in tutto. Le nuove allargano il campo apposta: un olio olandese del
Seicento, una bufera di Turner, i covoni di Monet, un inchiostro cinese Ming. Due sigilli che si
somigliano sono due sigilli che non dicono niente, e con sedici opere il ripetersi si notava.

Insieme e' arrivato il **ritaglio centrato**: la raccolta ora ha rotoli quattro volte piu' larghi
che alti e tele verticali, e allungarle per farle entrare nella copertina le storpiava. Le scaglie
che cadono ritagliano la stessa finestra, non l'immagine intera — altrimenti porterebbero via un
pezzo di dipinto che nella copertina non si vedeva.

Un'opera e' stata scartata dopo averla guardata: la scansione aveva i margini bianchi del foglio, e
in una copertina si leggeva come un foglio invece che come un quadro.

### Suoni: non fatti apposta

Il piano li mette in M2 e restano fuori. Non per mancanza di asset — si possono sintetizzare — ma
perche' **non posso ascoltarli**. Un'animazione si giudica dai fotogrammi; un suono no, e un suono
sbagliato e' peggio del silenzio. Vanno fatti quando c'e' qualcuno che li sente.

### Tema chiaro, accessibilita', rifiniture (2026-09-07)

**Tema chiaro.** Provato per la prima volta, e aveva due difetti che sul fondo scuro non si vedono:

- il velo che scurisce il quadro mentre si sgretola era **nero fisso**. Serve a rendere leggibile un
  testo chiaro su un dipinto acceso; nel tema chiaro il testo e' scuro, e il velo lo cancellava. Ora
  il velo e' il **colore della bolla** in cui il sigillo sta: il dipinto si spegne verso il posto in
  cui si trova, e funziona in tutti e due i temi.
- la polvere e le crepe della roccia erano bianche, cioe' invisibili su fondo chiaro. Ora vengono
  dal tema.

**Un difetto che si e' visto solo disegnandolo.** Le crepe comparivano *fuori* dalla pietra, in un
reticolo piu' grande e spostato. Il ritaglio sulla sagoma non bastava a spiegarlo, e la causa e'
saltata fuori disegnando il contorno in rosso: l'immagine della superficie veniva ridotta a 448 px
per risparmiare memoria e poi ridisegnata alla misura della tela, ma finiva sullo schermo alla sua
misura naturale. Disegno a una scala, geometria a un'altra. Ora l'immagine e' **esattamente** della
misura della tela: uno a uno non c'e' niente da far combaciare.

**Accessibilita'.** Un sigillo chiuso ha una descrizione ("Messaggio chiuso in una roccia") e
l'azione "Apri il sigillo". Non dice il testo — sarebbe il modo piu' semplice di saltare il rituale
— ma senza descrizione TalkBack annunciava un riquadro senza nome e la conversazione era illeggibile.

**Onboarding.** Nel passo del nome la domanda e' passata sopra al campo, come nel passo del segreto:
sotto, si leggeva "Come ti chiami?" dopo aver gia' visto una casella vuota e un minerale che non si
sa cosa c'entri. Il giro completo (cancella tutto → onboarding → chat) e' stato rifatto da zero:
verifica anche il database creato alla versione 2 senza passare dalla migrazione.

**Lint** passa. L'unico errore era `local.properties` con i due punti dell'unita' non protetti — un
file della macchina, non del progetto, ma faceva fallire il controllo.

### Il cifrario vero (2026-09-07)

La foto e' arrivata: `cifrario.jpeg`, quella che il prof ha fatto vedere a lezione. E' una
fotografia di una fotocopia e non ce n'e' una migliore, ma **si legge**. Sta nell'app, negli asset
accanto ai dipinti, non ritoccata, e si apre a tutto schermo con lo zoom a due dita.

Letta la tavola, `Nomenclator` e' stato rifatto perche' combaci con quella, non con una
ricostruzione plausibile:

| | prima | adesso, contato sulla foto |
|---|---|---|
| alfabeto | 21 lettere italiane | **22**: `a b c d e f g h i l m n o p q r s t v x y z` |
| omofoni | due per ogni vocale | quelli che si contano sotto `a`, `d`, `i`, `n`, `q` |
| sillabe | 13 consonanti × 5 | **15** × 5 = 75, con la riga della `q` scritta `qua que qui quo quu` |
| doppie | 12 | **13**, `uu` compresa |
| nomenclatore | 10 voci provvisorie | Papa, Imperatore, Re di Francia, Pisa, Firenze, **Esercito**, Domani, **Salvestro**, **Somme**, **Alamannia** |
| nulle | 6 | **9** |

Due cose che la foto ha corretto e che nessuna ricostruzione avrebbe indovinato:

- **la `u` non ha una riga sua** nell'alfabeto: la `v` faceva per due, come si scriveva allora. Ma
  la `u` c'e' eccome fra le vocali delle sillabe e nella doppia `uu`, quindi non si puo' cancellare
  dal testo: si lascia dov'e', e quando resta da sola prende il segno della `v`;
- ci sono **`x` e `y`**, che l'italiano non usa ma i nomi stranieri si'.

Il piano leggeva *Ferrara* dove c'e' scritto **Esercito**, e *Salviati/Sanesi* dove c'e'
**Saluestro** (Salvestro: la `u` per la `v` un'altra volta). La voce fra *Nulle* e *Somme* non si
legge e resta fuori invece di essere indovinata.

La lettura storica non cambia, anzi si rafforza: *Pisa* e *Firenze* come voci distinte, accanto a
*Imperatore*, *Re di Francia*, *Esercito* e *Alamannia*, e un nome di battesimo fiorentino. Una
cancelleria toscana ai tempi delle guerre d'Italia. Resta una lettura, non un'attribuzione.

### Il tablet (2026-09-07)

Provato su un Galaxy Tab S9 vero (2560×1600, 340 dpi, Android 16), non su un emulatore. Funziona:
guida di lato, colonna del contenuto ferma in mezzo, serratura in orizzontale con il ritratto a
sinistra e il tastierino a destra.

Due difetti trovati li' e corretti:

- **l'onboarding si allargava per tutto lo schermo**: il campo del nome era una riga da
  duemilacinquecento pixel. Non e' un campo, e' un righello.
- **le liste facevano lo stesso**. Ora la spaziatura laterale si allarga da sola sopra i 720 dp
  (`codexHorizontalPadding`), che e' il parametro che `FluidScreen` ha gia': cosi' tutto quello che
  sta nella lista si stringe insieme senza toccare una riga per volta.

Sul tablet si e' visto per la prima volta **il passo della biometria** con un sensore vero: l'app lo
propone. Attivarlo richiede il dito di chi possiede il tablet, quindi resta da provare a mano.

### Firebase (2026-09-07)

Il `google-services.json` valido e' arrivato (progetto `codex-e4795`) e la build lo usa. **Manca
ancora l'accesso Google**: nel file `oauth_client` e' vuoto per tutte e due le app, e Firebase lo
riempie solo quando l'app Android ha almeno un'impronta del certificato registrata **e** il
provider Google e' attivo in Authentication. Le due impronte, lette dalle chiavi di questa macchina,
sono in `docs/FIREBASE-IMPRONTE.md`.

**`keystore.properties` non serve**: la release si firma gia' con `pampa.jks`, trovato tramite il
`keystore.properties` di universal_converter. Verificato compilando una release e leggendo il
certificato dell'APK (`CN=Pampa, O=PampaStore, C=IT`). La voce va tolta dai prerequisiti in §15.

### Rifiniture (2026-09-07, mattina)

Niente di grosso, e questo e' il punto: sono le cose che separano un'app che funziona da un'app che
sembra finita. Tutte provate sul tablet vero.

**La cornice del quadro segue il dipinto.** Con trentasei opere che vanno dal rotolo cinese alla
tela verticale, una cornice sempre 224 x 152 buttava via meta' di quelle. Ora la forma la decide il
quadro e l'**area** resta la stessa: due sigilli nella stessa conversazione pesano uguale anche se
uno e' largo e l'altro alto, che e' cio' che tiene la lista ordinata invece di farla sembrare un
collage. Agli estremi si taglia, e il taglio lo fa gia' il ritaglio centrato.

**Le spunte di consegna.** In attesa, inviato, consegnato, letto, non inviato, accanto all'ora.
L'accento lo prende **solo "letto"**: e' l'unico stato che interessa davvero, e tingere anche gli
altri toglierebbe a quello il suo momento. Ognuna ha la sua descrizione per chi non vede lo schermo.

**L'ora nella lista delle chat.** Di oggi si scrive l'ora, di prima la data. Una lista di
conversazioni senza non si legge: non si capisce quale sia viva e quale ferma da una settimana.

**Il vicolo cieco delle chat, detto.** Non c'era modo di aggiungere nessuno e non c'era scritto
perche': sembrava rotta. Ora c'e' una riga che dice cosa manca e che la chat con se stessi e' una
chat vera.

**Una domanda prima di cancellare.** "Elimina" toglieva un messaggio per sempre con una pressione
lunga e un tocco. Una domanda in mezzo e' l'unica cosa che sta fra una svista e una perdita.

**L'anello di una storia non vista prende il colore della pietra che contiene**, sfumato
nell'accento: due storie si distinguono da lontano prima ancora di guardare cosa c'e' dentro.

**Il tasto di invio si spegne** quando non c'e' niente da mandare, e la pressione lunga su un
messaggio ha la sua vibrazione: un tasto che si preme e non fa niente insegna a non fidarsi dei
tasti.

**I pannelli si fermano** alla stessa larghezza delle schermate (`CodexSheetColumn`). Su un tablet
un foglio di vetro e' largo quanto lo schermo, e dentro ci finivano quattro righe di scelta lunghe
due metri. Il pannello resta largo, quello che c'e' dentro no. Stessa cosa per il visore del
cifrario, che aveva anche due bande nere ai lati: ora il margine e' del colore del pannello.

**La barra di scrittura si prende 104 dp** invece di 88: da quando sotto ogni bolla c'e' anche la
riga dell'ora, l'ultimo messaggio finiva sotto il tasto di invio.

**Le rune hanno una superficie.** Erano l'unica tecnica senza niente da guardare: la roccia ha una
gemma, il quadro ha un quadro, le rune avevano un rettangolo lavanda. Ora finche' il messaggio e'
chiuso la bolla e' una lastra -- un filo di luce sul bordo alto, un'ombra su quello basso, una
velatura che scende -- e la lastra se ne va man mano che il testo si scioglie.

**L'aptica delle rune era scritta e non collegata.** `RuneRevealHaptics` esisteva dal primo giorno e
non la chiamava nessuno: un tocco leggero ogni centocinquanta millisecondi mentre le lettere si
fermano. E' l'unica cosa che rende le rune un oggetto che si apre invece che un testo che cambia.

**La foto del cifrario tiene il posto mentre carica.** Anche `CipherPlatePlaceholder` era scritto e
mai usato: senza, la scheda nasceva vuota e si apriva di colpo, e la pagina saltava sotto le dita di
chi stava scorrendo.

**Gli script di navigazione non usano piu' coordinate fisse.** `tools/pg.sh` e `tools/origini.sh`
cercano le voci per testo: lo stesso script vale sul telefono e sul tablet, in italiano e in
inglese. Le sole coordinate fisse rimaste sono quelle del tastierino, che l'app disegna da se'.

**La cornice del quadro ha sei test.** Ha una regola -- stessa area, forma dal dipinto, limiti agli
estremi -- e una regola si prova: senza, la prima modifica distratta ai limiti farebbe due sigilli
di peso diverso, e a occhio non si nota finche' non se ne vedono cinque di fila.

**Quattro megabyte in meno da scaricare.** SQLCipher porta la sua libreria nativa per quattro
architetture, e due -- `x86` e `x86_64` -- esistono solo negli emulatori: nessun telefono o tablet
in circolazione le usa. La release ora ne porta due e pesa **19,7 MB invece di 23,8**; la build di
debug le tiene tutte, altrimenti l'emulatore non parte.

**`tools/verify.sh` fa il giro intero**: build, test, lint e `engine-doctor` in un comando, con il
riassunto in tre righe. `bash tools/verify.sh veloce` salta lint e doctor per iterare.

**Le prove manuali sono scritte.** `docs/TEST-MANUALI.md`, a cui §13 rimandava senza che esistesse:
solo quello che non si puo' automatizzare e non si puo' fare da soli, diviso fra cio' che si prova
adesso, cio' che serve un secondo dispositivo, cio' che serve un dito, e cio' che serve una persona
che non vede lo schermo.

### Una nota sull'emulatore

Durante la mattina l'emulatore ha dato un ANR e poi un "Process system isn't responding", che e'
il **sistema** e non l'app. Sulla macchina c'erano due emulatori, il tablet e le compilazioni. Ho
spento il mio emulatore e sono passato al tablet: l'app li' non ha mai avuto un fermo.

`tools/shot.sh` e `tools/frames.sh` ora scelgono il dispositivo da soli e **non toccano mai
`emulator-5556`**, che e' dell'utente e non del progetto. Si forza con `CODEX_DEVICE=<seriale>`.

### M3 · Contatti e pairing (2026-09-08) — tutta la parte che vive senza server

Fatto mentre l'utente era via, con la consegna di proseguire da solo. **Nessuna riga di questa
milestone dipende da Firebase**: due telefoni si aggiungono, si accordano su una chiave e si
scrivono senza che esista un server. Il cloud, quando arrivera', portera' la consegna dei messaggi,
non il modo di conoscersi.

**Il rito della parola d'ordine** (`ChatSecret`). La chiave di una conversazione nasce da due cose
insieme: l'accordo X25519 fra le due identita' -- che chiude fuori chiunque trasporti le schede,
perche' non ha nessuna delle due meta' private -- e una parola detta a voce, che chiude fuori
chiunque sia riuscito a farsi passare per l'altro mentre le schede si scambiavano. La parola non
passa mai da uno schermo, ed e' l'unica cosa che un server non potrebbe comunque intercettare.

**Come si accorgono di aver sbagliato, e perche' non lo dice l'app.** Non c'e' nessun confronto e
nessun messaggio "parola errata": dalla chiave nasce il seme dell'emblema, e l'emblema si vede. Due
persone che hanno detto la stessa parola vedono **la stessa gemma** sui due schermi. E' la verifica
piu' onesta che si possa costruire senza un'autorita' in mezzo -- non la fa l'app, la fanno loro --
ed e' anche la piu' adatta a Codex, perche' usa la cosa che l'app sa gia' fare: disegnare minerali
da un seme.

**L'identificatore della chat non dipende dalla parola**, e nasce dal segreto in comune invece che
dai due Codex ID pubblici. Le due scelte hanno lo stesso scopo: due che sbagliano parola restano
nella **stessa** conversazione, quindi i messaggi arrivano e restano li' chiusi, e rifare il rito
li riapre. Se l'id dipendesse dalla parola, ognuno finirebbe in una conversazione tutta sua e non
capirebbe mai perche' l'altro non risponde. E derivarlo dal DH invece che dagli id pubblici fa si'
che nessun estraneo possa indovinare dove vive una conversazione pur sapendo chi sono i due.

**Il portachiavi.** Le chiavi di chat stanno in `chat_keys` (migrazione 2 → 3), **avvolte** con una
chiave che discende dal seme del portachiavi, che esiste solo dentro il vault. Serve a non perdere
la promessa nel punto in cui conta: il database e' cifrato, ma la sua chiave sta nel Keystore senza
impronta -- deve poterci scrivere una notifica con l'app chiusa -- quindi una chiave di chat lasciata
li' in chiaro renderebbe leggibili i messaggi a chiunque apra il file. Cosi' invece il file cifrato
contiene tutto tranne il modo di leggerlo. A serratura chiusa le chiavi si **azzerano**, non si
dimenticano soltanto.

**Il QR e' disegnato da Codex** (`QrMatrix` + `QrPlate`): pastiglie con l'angolo continuo, i tre
occhi agli angoli come anelli con la pupilla d'ametista, e il minerale di chi lo mostra al centro,
dentro il trenta per cento che la correzione d'errore alta ricostruisce. Foglio chiaro e inchiostro
scuro **sempre**, tema notte compreso: un QR non e' una superficie dell'app, e' un bersaglio ottico,
e mezzo dei lettori in circolazione rifiuta un codice invertito. E' l'unica eccezione dichiarata alla
regola "nessun colore scritto a mano", e sta nel file del tema per restare tale.

**Il lettore e' lo stesso zxing che disegna** (`QrReader`), non ML Kit. Il riconoscitore di Google
farebbe lo stesso lavoro un po' meglio e costava, misurato: release da 19,74 a **31,67 MB**. Per un
quadrato disegnato dall'app stessa, su uno schermo acceso, a venti centimetri dall'obiettivo, non e'
un prezzo sensato. Con zxing la release e' **20,86 MB**: tutta la milestone e' costata 1,1 MB, ed e'
solo CameraX.

**Cosa e' provato, e come.** Le due cose che su un dispositivo solo non si vedrebbero mai:

- `ChatSecretTest` (11): i due lati arrivano alla stessa chiave; parole diverse danno gemme diverse
  ma la stessa conversazione; accenti, maiuscole e punteggiatura non contano; l'involucro non si apre
  con un altro portachiavi ne' in un'altra chat, e cambia a ogni giro.
- `PairingTest` (13): **due dispositivi finti**, due identita', due magazzini separati, e fra loro
  passa solo un codice. Si scambiano le schede, fanno il rito, uno scrive e l'altro apre. Con la
  parola sbagliata il messaggio arriva e **non** si apre; rifatto il rito con quella giusta, si apre
  senza che nel frattempo sia sparito. Le impostazioni della chat sopravvivono a un secondo rito, e
  a serratura chiusa non si legge e non si aggiunge niente.
- `QrReaderTest` (5) e `QrMatrixTest` (5): il codice disegnato da Codex viene stampato come lo
  stamperebbe uno schermo e riletto come lo leggerebbe la fotocamera, **compreso il passo delle
  righe** -- il difetto che, sbagliato, non somiglia a un bug ma a "il lettore non va".
- `Base32Test` (6): il giro della scheda, anche ricopiata a mano con una O al posto dello zero.

In tutto: **130 test, 0 falliti**, lint pulito, `engine-doctor` pulito.

**Un buco nel cancello, trovato per caso.** `tools/verify.sh` chiamava `testDebugUnitTest`, che
esiste in ogni modulo Android e **non** in `:core:crypto`, che e' JVM puro. Quindi il cancello non
provava la crittografia -- l'unico modulo in cui un difetto non si vede a schermo. E lo faceva in
silenzio: il riassunto contava lo stesso quei test, perche' legge i referti rimasti su disco
dall'ultima esecuzione a mano. Ha detto "125 test, 0 falliti" mentre cinquantasei di quelli non
erano stati eseguiti in quella build. Adesso `:core:crypto:test` e' chiamato per nome.

**Cosa manca a M3.** Il pairing per vicinanze (Nearby) resta a M7, come il piano prevedeva. La
"sola scansione in entrambi i versi" resta a M4: senza server servono due scansioni, e la schermata
lo dice invece di far finta. `peer_deleted` non ha ancora un canale su cui viaggiare.

**Provato su un dispositivo, tranne la fotocamera.** Sull'emulatore, su un'installazione che aveva
gia' un'identita' e dei messaggi -- quindi la migrazione 2 → 3 e' passata su dati veri. Il giro
fatto a mano: incollare una scheda firmata, vederla verificata, arrivare al rito, dire la parola,
vedere la gemma, entrare nella conversazione e **mandare un messaggio che si sigilla in rune**. Il
contatto e lo stato "aperta" sopravvivono a una reinstallazione, il che vuol dire che la chiave
avvolta ha fatto il giro completo: vault → involucro → database → riapertura.

Due cose non si potevano provare senza qualcuno in carne e ossa: **inquadrare** un codice con una
fotocamera vera, e le due gemme affiancate su due telefoni. Stanno in `docs/TEST-MANUALI.md`,
gruppo B.

**Un difetto trovato guardando lo schermo, non il codice.** La gemma del rito era quasi invisibile:
un quarzo scuro su fondo nero. I minerali di Codex nascono da un seme e fra loro ci sono ossidiane
quasi nere; in una conversazione stanno dentro una bolla e si vedono, sulla pagina no. Adesso sta su
una superficie, come le bolle. Era proprio la cosa che due persone devono confrontare a distanza di
un braccio per sapere se qualcuno si e' messo in mezzo.

### Firebase, dalla riga di comando (2026-09-08)

L'utente ha fatto `firebase login` e mi ha lasciato proseguire. Quasi tutto quello che sembrava
richiedere la console si e' rivelato fattibile da CLI.

**Le impronte c'erano davvero.** `apps:android:sha:list` le mostra registrate su tutte e due le
app dal giorno prima. Quindi il motivo per cui `oauth_client` era vuoto **non erano le impronte**:
e' che l'accesso con Google non e' acceso, e sono quelle credenziali a creare i client OAuth. Un
sospetto sbagliato che e' costato un giro: la prossima volta si guarda prima lo stato, poi si
accusa. Aggiunte anche le **SHA-256** (servono a Play Integrity e ad App Check):

- release `82:57:38:50:…:7E:43` · debug `D7:A4:09:A3:…:49:7E`

**Firestore c'e', ed e' in Europa.** `firebase deploy --only firestore` ha creato il database da
solo -- e lo ha creato in `nam5`, Stati Uniti, senza chiedere niente. La posizione di un database
Firestore **non si cambia**, e il piano dice EU. L'ho cancellato mentre era ancora vuoto (dieci
minuti di vita, zero documenti) e rifatto in `eur3`. Il nome `(default)` resta bloccato cinque
minuti dopo una cancellazione: se ricapita, si aspetta.

Regole e indici sono in produzione sul database nuovo.

**Le regole adesso hanno i loro test** (`firebase/test`, 11 prove, verdi), che §13 chiedeva dal
primo giorno e che non erano mai stati scritti. Provano soprattutto **quello che le regole
negano**, che e' la meta' che rileggendo il file non si vede: che nessuno legga il vault di un
altro, che un Codex ID non si possa dirottare su un altro account, che uno gia' preso non si
sovrascriva nemmeno dal proprietario, e che tutto il resto sia chiuso. Si eseguono con
`bash tools/regole.sh`.

Lo script esiste per una ragione noiosa e vera: l'emulatore Firestore vuole **Java 21**, la
macchina ha il 17 perche' e' quello che Gradle vuole per Android, e cambiarlo romperebbe il build.
Il 21 pero' c'e' gia', dentro Android Studio (`jbr/`): lo script lo trova e lo mette davanti nel
PATH solo per quel comando.

**Cosa resta alla console** (la CLI non ci arriva):

- ~~Authentication → Google~~ **fatto dall'utente la sera stessa**, e con quello e' arrivato il
  client OAuth: vedi il blocco "L'accesso con Google, e Blaze".
- ~~Blaze~~ era gia' attivo.
- **Storage → Inizia**: crea il bucket. Da CLI non si puo'. Serve a M5.

### La rubrica: il Codex ID diventa cercabile (2026-09-08)

Il primo pezzo di cloud, e chiude anche l'ultimo buco di M3: "Manuale" non e' piu' incollare
trecento caratteri, e' dire dodici. Nello stesso campo entrano tutti e due -- se quello che c'e'
dentro somiglia a un Codex ID si cerca, altrimenti si decodifica in casa senza toccare la rete.

**Il server non e' creduto sulla parola.** Quello che restituisce e' una scheda **firmata**, e
prima di risalire viene verificata due volte: la firma, e che il Codex ID che porta sia proprio
quello che si era chiesto. Un server ostile puo' non rispondere o dire "non c'e'"; non puo'
consegnare la scheda sbagliata sotto l'identificatore giusto, perche' quell'identificatore nasce
dalla chiave che firma. Quello che pero' **puo'** fare, e va detto invece che nascosto, e' sapere
chi cerca chi: e' il prezzo di poter aggiungere qualcuno che sta lontano, e chi non lo vuole pagare
ha ancora il QR e il codice incollato, che non passano di qui.

**Codex continua a funzionare senza Firebase**, e non come modalita' ridotta: senza account restano
le note a se stessi, il pairing con il QR, il rito, le conversazioni e tutti i sigilli. Se
`google-services.json` manca o i servizi non ci sono, `CodexCloud` ripiega su un account sempre
disconnesso e una rubrica che dice sempre "non c'e'". Un'app di messaggistica che non parte per un
file di configurazione si rompe nel modo peggiore: in mano a chi non puo' ripararla.

**Provato davvero, contro l'emulatore di Firebase.** Il codice che parla con un server e' quello che
i test di unita' non toccano: le finte provano cosa succede quando il server non risponde, non che
la chiamata funzioni. Quindi c'e' un banco di prova nel Playground (solo build di lavoro) e il giro
e' stato fatto a mano sull'emulatore Android:

1. accesso anonimo → `uid gQeFLWqh…`;
2. pubblicazione → in Firestore compaiono `codexIds/CDX-J55J-MG2M` e `users/{uid}` con la scheda
   firmata dentro, **passando dalle regole vere**;
3. ricerca di un secondo Codex ID (una identita' firmata messa nella rubrica dell'emulatore) scritto
   in minuscolo e con i trattini → trovato, verificato, risolto nel contatto giusto.

L'accesso anonimo esiste **solo** contro l'emulatore, e c'e' un controllo che lo impedisce altrove:
un account anonimo non si recupera, e chi ci finisse dentro per sbaglio perderebbe tutto al primo
cambio di telefono.

Due cose sistemate lungo la strada, tutte e due vere:

- il manifest vieta il traffico in chiaro (giusto), e l'emulatore Firebase e' HTTP. L'eccezione
  adesso c'e' ma vive **solo in `app/src/debug`** e vale solo per `10.0.2.2`, `localhost` e
  `127.0.0.1`. Nella release il divieto resta secco;
- `codex.firebaseEmulator` in `local.properties` e' commentato per difetto. Lasciarlo acceso senza
  emulatore in ascolto renderebbe il cloud irraggiungibile senza dire perche'.

`DirectoryTest` (7 prove) copre quello che l'emulatore non fa vedere: cosa succede se il server non
risponde, se non c'e' accesso, se la scheda che torna non e' quella chiesta, e che un codice lungo
non passi mai dalla rete. In tutto **138 test**, piu' gli 11 delle regole.

Release: da 20,86 a **23,14 MB** (Auth + Firestore).

**Cosa manca ancora, e perche'.** Il trasporto dei messaggi (M4 vero), le Functions e le notifiche.
E soprattutto l'accesso con Google, che e' un interruttore in console: finche' e' spento, di tutto
questo si puo' provare solo la versione con l'emulatore.

### L'accesso con Google, e Blaze (2026-09-08, sera)

L'utente ha acceso l'accesso con Google e ha riscaricato `google-services.json`: adesso
`oauth_client` ha il client Android (`type 1`, uno per app, legato alla SHA-1) e il **client web**
(`type 3`), che e' quello che serve come `serverClientId`. Il plugin lo scrive in
`R.string.default_web_client_id`, quindi non c'e' nessuna costante da copiare a mano.

**L'accesso passa da Credential Manager**, non dal vecchio `GoogleSignInClient`: il selettore lo
disegna Android, la scelta la fa la persona, e all'app torna un'asserzione firmata da Google. Codex
non vede mai una password e non avrebbe modo di vederla -- che e' la proprieta' giusta per un'app
costruita intorno al non fidarsi di nessuno. Appena l'accesso riesce, la scheda viene **pubblicata**:
un account senza scheda nella rubrica non serve a niente, perche' nessuno potrebbe cercarti.

La sezione sta in "Io", **sotto il profilo e sopra la sicurezza**, e la posizione e' un'affermazione:
l'account non e' chi sei per Codex, e' solo il modo in cui il server sa a chi consegnare. Il testo lo
dice per esteso — *l'account non apre la tua identita': quella sta nel vault e si apre con il tuo
segreto* — e scollegarsi non cancella niente di locale.

**Provato fino al confine.** Sull'emulatore: la sezione compare, il collegamento parte, Credential
Manager chiama Play Services e si arriva alla schermata di Google. Li' mi sono fermato: da quel
punto si inserisce un account vero, e non e' una cosa che faccio io. Arrivarci pero' dimostra la
parte che poteva essere sbagliata — pacchetto e impronta accettati dal client OAuth: se non
combaciassero, la chiamata morirebbe prima con un errore di configurazione. Annullando, l'app torna
al suo stato senza lasciare errori.

**Le Functions sono in piedi.** Con Blaze attivo, `firebase deploy --only functions` ha creato
`health` in `europe-west1` e risponde davvero. Non fa niente di utile -- e' proprio il punto: serve a
sapere che la catena (build TypeScript, upload, 2ª generazione, regione europea) funziona **prima**
che M4 ne abbia bisogno. Impostata anche la politica di pulizia delle immagini dei container a 3
giorni: senza, si accumulano in Artifact Registry e diventano una piccola voce di spesa mensile per
niente.

**Una trappola da ricordare.** Passando dall'emulatore Firebase al progetto vero, la sessione
anonima resta salvata in locale e l'app mostra "account collegato" per un utente che in produzione
non esiste. Capita solo a chi sviluppa e si risolve scollegandosi; su un'installazione vera non
succede.

Storage resta l'unica cosa che la CLI non sa fare: il bucket vuole il "Inizia" della console. Serve
a M5, quindi non blocca niente adesso.

Release: **23,22 MB**.

### M4 · La consegna nel cloud (2026-09-09)

La spina dorsale di M4: un messaggio scritto qui arriva davvero dall'altra parte. Restano fuori le
notifiche, le ricevute, "sta scrivendo" e la paginazione, che sono il resto di M4.

**Il server e' un corriere, non un destinatario.** Quello che passa da Firestore e' una busta `CDX3`
che nessuna sua chiave apre. Il documento porta solo cio' che serve a consegnare: mittente, busta,
quando, e `viewOnce`. **Non** porta la tecnica ne' il seme del sigillo -- quelli nascono dalla
chiave della chat e dall'id del messaggio, e chi riceve li ricalcola. Regalarli al server non
darebbe niente in cambio, e permetterebbe a chi trasporta di far sembrare un quadro una roccia.

**Lo `chatId` resta quello di M3**, derivato dal segreto in comune e non dagli uid come diceva §6.2.
E' una deviazione voluta: cosi' funziona anche senza account, e nessun estraneo puo' indovinare dove
vive una conversazione pur sapendo chi sono i due. Le regole non ne risentono -- guardano
`memberUids` dentro il documento, non il nome del documento.

**La posta in uscita e' una tabella, non una coda in memoria.** Chi scrive in metropolitana ha
scritto: il messaggio esiste, sta nella conversazione, ha la sua spunta d'attesa. Se la coda vivesse
in RAM, il sistema che chiude l'app per fare spazio lo cancellerebbe **in silenzio**, che e' il
difetto peggiore che un'app di messaggistica possa avere. Un rifiuto del server invece esce dalla
coda e prende la spunta rossa: insistere su un "no" definitivo e' solo batteria.

**Un difetto vero, trovato solo perche' l'ho fatto girare.** Le prime consegne venivano tutte
rifiutate. Il motivo stava nelle regole: `get()` su un documento che **non esiste ancora**
restituisce null, `.data` manda in errore la valutazione, e Firestore un errore lo tratta come un
rifiuto. Quindi il primo messaggio di ogni conversazione era condannato, e tutti quelli dopo
sarebbero passati. I 19 test sulle regole non lo vedevano perche' partivano tutti da una chat che
c'era gia'. Adesso c'e' `exists()` prima di ogni `get()`, il trasporto **non legge piu' prima di
scrivere** (non potrebbe: le regole non lasciano leggere un documento assente), e c'e' il test che
mancava: "la prima consegna, da zero".

**Provato sul dispositivo, nei due versi**, contro l'emulatore di Firebase:

1. cercato Bruno per Codex ID, fatto il rito, scritto un messaggio;
2. in Firestore compaiono la chat con i due `memberUids` e il messaggio: **busta `CDX3` di 52 byte,
   il testo non compare, nessun campo che riveli il sigillo**;
3. la consegna e' avvenuta **dopo un riavvio dell'app** -- che e' esattamente cio' per cui la coda
   sta su disco -- e la spunta e' passata da attesa a inviato;
4. scritto un messaggio da fuori a nome di Bruno: e' arrivato nella lista come "una roccia da
   aprire", con il sigillo **ricavato in locale**, e aprendolo dice "questo non si apre", perche'
   quella busta finta non e' cifrata con la chiave della conversazione.

Test: **147** di unita' (fra cui `DeliveryTest`, 9: cosa succede senza rete, con un rifiuto, con un
destinatario senza account, con lo stesso messaggio consegnato due volte) e **22** sulle regole.
Release: **23,25 MB**.

**Una porta spostata.** L'emulatore Firestore stava sulla 8080, che su questa macchina occupa NVIDIA
Broadcast. Adesso l'app usa la 8088 e i test delle regole la 8099, con un `firebase.test.json` loro:
un test che fallisce perche' un'altra applicazione era aperta non e' un test.

### Notifiche e spunte (2026-09-09, sera)

**Una notifica non dice mai cosa c'e' scritto.** Non e' un limite tecnico da aggirare piu' avanti: e'
il punto dell'app. Un'anteprima sulla schermata di blocco avrebbe gia' aperto il messaggio davanti a
chiunque guardi il telefono appoggiato sul tavolo. Quello che dice e' il minimo utile — **da chi**, e
che e' arrivato un sigillo. Nemmeno la forma del sigillo: quella nasce dalla chiave della
conversazione, e a telefono bloccato non si puo' calcolare. Dirla solo qualche volta sarebbe peggio
che non dirla mai.

La spinta e' **data-only** e porta solo `chatId` e `messageId`. Una notifica `notification` la
disegnerebbe il sistema con il testo che arriva dal server, e il server un testo da mostrare non ce
l'ha e non deve averlo.

`onMessageCreated` (Cloud Function, `europe-west1`) legge i membri della chat, salta chi ha scritto,
raccoglie i gettoni dei dispositivi degli altri e manda. Toglie da sola i gettoni morti: un'app
disinstallata ne lascia uno che fallisce per sempre, e senza pulizia ogni notifica pagherebbe il
costo di provarci.

**Provato davvero, fino alla notifica sullo schermo**: creato un messaggio nell'emulatore Firestore →
la Function ha risposto *"1 consegnati, 0 no"* → sul dispositivo e' comparsa la notifica, canale
`codex_messages`, importanza alta, visibilita' **privata**, con scritto "Qualcuno · Ti ha mandato un
sigillo" e nient'altro.

Due difetti trovati proprio perche' l'ho fatto girare:

- **l'app non chiedeva mai il permesso di notificare.** Da Android 13 va concesso, e `sealArrived`
  usciva in silenzio. Adesso si chiede **quando si collega un account** — cioe' quando la domanda ha
  una risposta ovvia, non all'avvio, che e' il momento in cui la gente rifiuta per riflesso;
- **una notifica per una conversazione che in locale non esiste ancora veniva buttata.** Capita
  davvero: l'altra persona fa il rito per primo e scrive subito. Meglio dire "Qualcuno" che non dire
  niente.

**Le spunte adesso dicono la verita'.** Erano disegnate da giorni e si fermavano a "inviato". Ora
`chats/{c}/receipts/{uid}` porta due numeri — fin dove ho ricevuto, fin dove ho letto — e non una
bandierina per messaggio: una conversazione si legge dall'inizio alla fine, quindi due numeri dicono
la stessa cosa di mille, costano una scrittura invece di mille, e promettono meno (nessuno tiene
traccia di **quali** messaggi hai guardato).

I due momenti si registrano **in casa** e poi partono: chi apre una conversazione in aereo l'ha letta
comunque, e la ricevuta deve partire quando torna la rete invece di perdersi. E gli stati vanno solo
in avanti: una ricevuta vecchia che arriva in ritardo non deve riportare "letto" a "consegnato", o
chi guarda smette di fidarsi di tutte le spunte.

Migrazioni 3→4 (`outbox`) e 4→5 (le due colonne delle ricevute), tutte e due scritte a mano.

**Una trappola di Gradle.** `api(firebase-messaging)` con il BOM dichiarato solo su `implementation`
lascia la dipendenza **senza versione**: il build dell'app non se ne accorge, lint si', molto dopo.
Il BOM adesso sta su `api`.

Test: **150** di unita', **22** sulle regole. Release: **23,43 MB**.

**Cosa manca a M4**: "sta scrivendo", la presenza, la paginazione oltre i 200 messaggi, e la
cancellazione propagata.

### M4 chiuso (2026-09-09, notte)

Gli ultimi quattro pezzi.

**"Sta scrivendo" e "online" sono reciproci.** Chi li spegne **non li manda e non li vede**: un
interruttore che ti lascia guardare gli altri restando invisibile e' un vantaggio, e un vantaggio in
una conversazione fra due persone e' uno squilibrio. Stanno in "Io → Cosa lasci vedere", con scritto
sotto che non c'entrano niente con il contenuto dei messaggi — quello resta illeggibile comunque.

Due dettagli che sembrano piccoli e non lo sono:

- **"sto scrivendo" e' una scadenza, non un interruttore.** Un'app chiusa a meta' frase lascerebbe
  l'altro a guardare per sempre un "sta scrivendo" che non finisce. Un istante che passa si spegne
  da solo, e non serve nessuna pulizia;
- **la presenza e' un battito, non uno stato.** Si riscrive ogni 45 secondi e chi guarda decide con
  il proprio orologio se e' ancora valido (90 secondi). Un "online" acceso e mai spento — perche' il
  sistema ha ucciso l'app — resterebbe acceso per sempre;
- il "sto scrivendo" parte dal tasto premuto ma **passa al massimo una volta ogni tre secondi**: una
  frase di quaranta caratteri non sono quaranta scritture sul server.

**La cancellazione arriva fino in fondo.** Cancellare un messaggio lo toglie **anche dal server**:
la copia rimasta nel cloud e' proprio quella che una persona pensava di aver tolto. Lo stesso per un
"visualizza una volta" consumato — ed e' il destinatario a cancellarlo, cosa che le regole
permettono apposta, perche' aspettare una funzione lato server vorrebbe dire mantenere quella
promessa "fra poco".

**La conversazione si legge a pagine di 80, dal fondo.** Una chat si apre dove si era rimasti, non
nel 2019: si prendono gli ultimi e si risale scorrendo. La finestra cresce e non torna mai indietro,
perche' vedersi riaccorciare la lista sotto le dita mentre si scorre e' peggio che aspettare.

Regole nuove per `typing` e `presence`, con i loro test: **25** in tutto. Il caso che conta e' far
comparire "Ada sta scrivendo" sullo schermo di qualcun altro — una bugia convincente e a costo zero,
che la regola non lascia scrivere.

Test: **153** di unita', **25** sulle regole. Release: **23,45 MB**.

**M4 e' chiuso.** Restano fuori le reazioni, le risposte, la modifica e la ricerca, che il piano
elenca ma che sono comodita' di una chat matura, non il suo scheletro.

### M5 comincia: i media cifrati a blocchi (2026-09-09, notte)

Il pezzo che sta sotto a foto e note vocali, e che va fatto prima di qualsiasi schermata.

**Perche' a blocchi.** La busta di un messaggio (`CDX3`) cifra tutto in un colpo, e per del testo va
benissimo. Una foto da dodici megapixel sono venti megabyte: caricarli interi in memoria per cifrarli
e' il modo piu' rapido di farsi chiudere l'app dal sistema su un telefono che ha altro da fare.
`MediaCipher` legge 64 KiB per volta, cifra, scrive, e la memoria occupata resta quella di **un
blocco** qualunque sia la dimensione del file.

**Il problema che i blocchi creano, e come si chiude.** Un file cifrato a blocchi indipendenti si
puo' rimescolare **senza conoscere nessuna chiave**: togliere un pezzo, ripeterne uno, invertirne
due. Non produrrebbe niente di leggibile, ma produrrebbe *qualcosa* — e un'app che apre "qualcosa" al
posto di una foto ha gia' perso. Quindi ogni blocco autentica il proprio **numero** e **l'intera
intestazione**, e l'intestazione dice quanto e' lungo l'originale. Un file rimescolato, ripetuto,
troncato o con l'intestazione riscritta non da' un'immagine strana: da' un errore.

Meta' dei dodici test provano esattamente quei rimescolamenti, fatti a mano sui byte cifrati.

Il nonce di ogni blocco nasce dal nonce di base in XOR con il numero del blocco: dentro un file non
si ripete mai, e fra file diversi nemmeno, perche' **ogni media ha la sua chiave** derivata dalla
chiave della chat e dall'identificatore del file. Chi rompesse una foto non avrebbe fatto un passo
verso la successiva.

**Le regole di Storage** sono in produzione. L'appartenenza alla conversazione si chiede a Firestore
dalle regole di Storage (`firestore.exists` **prima** di `firestore.get`, che e' lo stesso difetto di
prima e qui non l'ho rifatto). Un media si carica una volta e non si riscrive: il suo identificatore
nasce dal messaggio che lo porta, e poter sostituire il contenuto di un file gia' consegnato vorrebbe
dire cambiare un messaggio dopo che e' stato letto.

Tutti i file sono `application/octet-stream`: **il server non puo' sapere se e' una foto o una nota
vocale**. Il tipo vero sta nella busta del messaggio, cifrato.

Le prove delle regole di Storage non ci sono ancora — vogliono l'emulatore di Storage acceso insieme
a quello di Firestore, e arrivano con la schermata che carica il primo file. Restano da fare: upload
e download con un Worker, le miniature, il visualizzatore, il registratore, e la distruzione del
"visualizza una volta" anche in Storage.

### M5 chiuso: foto, voce, e due protezioni che non c'erano davvero (2026-09-09)

Il giro completo, provato sull'emulatore da capo a fondo: si sceglie una foto dal selettore di
sistema, arriva come **roccia** (mai come rune: le rune sono il testo in un altro alfabeto, e davanti
a un'immagine non avrebbero niente da sciogliere), si apre con un tocco, si tocca ancora e si vede a
tutto schermo. Poi il microfono, la nota vocale con la sua forma d'onda, e l'ascolto.

**Quello che non tocca mai il disco.** La foto viene cifrata mentre la si legge dal selettore: sul
telefono esiste solo il file `CDXM`. Verificato con `run-as` sul dispositivo -- il file salvato
comincia per `CDXM` e non contiene la firma `RIFF`/`WEBP` dell'originale da nessuna parte. La nota
vocale e' l'unica eccezione, e vale dirla: il registratore di sistema produce un MP4, e un MP4 si
chiude riscrivendo l'indice in testa al file, quindi un flusso non basta. Il file in chiaro vive
nella cache privata dell'app per il tempo della registrazione e viene cancellato subito dopo essere
stato cifrato -- anche se l'invio va storto, anche se si esce dalla chat, e all'apertura della
schermata si spazza via quello che fosse rimasto da un'app morta a meta'.

L'audio si **ascolta dalla memoria**: `MediaDataSource` invece di un file temporaneo. Dieci righe
invece di un file che poi qualcuno dimentica di cancellare.

**Le foto non si decodificano mai intere.** Dodici megapixel diventano cinquanta megabyte di bitmap;
si leggono prima le misure e poi si decodifica saltando pixel. Nella bolla al massimo 900 px di lato,
nel visualizzatore 2400.

#### Le due cose che sembravano fatte e non lo erano

Sono le uniche due davvero interessanti di questo blocco, e nessuna delle due si vedeva leggendo il
codice.

**`FLAG_SECURE` non si e' mai acceso.** La schermata chiedeva al ViewModel "c'e' un visualizza una
volta aperto?" con una **funzione**, durante la composizione. Ma la schermata non leggeva nessuno
stato che cambiasse quando un sigillo si apre -- quella lettura avveniva dentro le righe della lista,
che sono un ambito di ricomposizione a parte. Nessuna ricomposizione, nessun ricalcolo: il flag
restava spento per sempre. Si e' visto solo aprendo un "visualizza una volta" sull'emulatore e
riuscendo a fotografarlo. Adesso e' un `StateFlow`, e la prova e' che lo screenshot esce nero e
`dumpsys window` dice `fl=... SECURE ...`. E il visualizzatore a tutto schermo e' una **finestra
diversa**: il flag dell'Activity non la copre, e va rimesso li' sopra.

**Un media si poteva riscrivere.** Le regole di Storage dicevano `allow update: if false`, e
leggendole si crede che basti. Il primo test scritto contro l'emulatore ha detto di no: un
caricamento su un percorso gia' occupato passava lo stesso, valutato come una creazione. La riga che
rende vera l'immutabilita' e' `resource == null` dentro `allow create`. Undici prove di Storage nuove
(36 in tutto con quelle di Firestore), e girano insieme a quelle di Firestore perche' devono: la
regola chiede a Firestore chi sta nella conversazione, e una regola che attraversa due servizi non si
legge, si prova.

#### Il resto

- **La scadenza si portava dietro il file.** Cancellare la riga dal database non toglie l'allegato, e
  finche' la chiave della conversazione c'e' quel file **si apre ancora**: una foto "scaduta" che
  resta leggibile e' una scadenza che non e' successa. Ora `sweepExpired`, `clearChat` e `deleteChat`
  portano via anche i file, e il documento sul server, perche' un altro dispositivo che si riattacca
  se lo riporterebbe in casa.
- **`cleanupMedia`**, in produzione: i messaggi scadono da soli con la politica TTL di Firestore, i
  file no. Toglie quelli oltre i trentuno giorni (il messaggio che li nominava non esiste piu' per
  costruzione) e gli orfani recenti -- un invio interrotto fra il file e il messaggio, che con
  l'ordine giusto ("prima il file") e' l'unico modo di restare a meta'. Gli orfani costano una
  lettura ciascuno, quindi se ne guarda un numero fisso per giro.
- **`DeliveryWorker`**: la coda si svuotava solo con l'app aperta. Per un testo non se ne accorge
  nessuno; per una foto mandata prima di mettere via il telefono se ne accorgono tutti. Nella coda ci
  sono buste gia' chiuse, quindi il lavoro non ha bisogno che l'app sia sbloccata -- consegnare non
  vuol dire leggere.
- **La forma d'onda** viaggia dentro la busta (poche decine di byte): si disegna prima di scaricare
  l'audio, e distingue una nota vocale da una barra grigia. Il formato del corpo la legge solo se
  c'e', quindi le buste scritte prima continuano a leggersi.
- Il microfono prende il posto dell'invio quando non c'e' niente da mandare, e tenendolo premuto apre
  la scelta della tecnica: era l'unico modo di accendere "visualizza una volta" **prima** di allegare
  una foto, che con la casella vuota non si raggiungeva piu'.

Restano fuori da M5, e sono scelte: le miniature non hanno un formato proprio (si ridimensiona
l'originale, che per 30 MB al massimo va bene), e non c'e' una barra di avanzamento per il
caricamento -- la spunta del messaggio dice gia' dove sta.

### M6 · I gruppi: l'unico segreto che si sposta (2026-09-09)

Fra due persone la chiave di una conversazione **non viaggia**: nasce da X25519 più la parola detta a
voce, e i due lati ci arrivano da soli. Fra tre no — non esiste niente che tre persone possano
calcolare in comune senza essersi mai parlate. Quindi la chiave di un gruppo la genera chi lo crea e
la **consegna**, una busta per ciascuno. È l'unico punto di Codex in cui un segreto si sposta, ed è
il motivo per cui questa milestone ha un formato tutto suo.

**La busta della posta (`CDXB`).** Cifrata con la chiave di coppia (X25519 fra le due identità, che
esiste solo sui due telefoni) **e firmata** con Ed25519. Servono tutte e due: la cifratura dice che
nessun altro può leggerla, la firma dice chi l'ha scritta — senza, una chiave di gruppo verrebbe
accettata da chiunque riuscisse a calcolare quella chiave di coppia. Mittente e destinatario stanno
dentro i dati autenticati: una busta destinata a qualcuno non si può riconsegnare a un altro né
rimandare indietro a chi l'ha scritta.

**Le epoche, e perché il portachiavi è cambiato.** Quando qualcuno esce, chi comanda genera una
chiave nuova e la consegna a chi resta. Chi è uscito **tiene il passato e perde il futuro**: i
messaggi di ieri erano suoi. Perché questo funzioni il portachiavi conserva *tutte* le epoche, non
solo l'ultima — la chiave primaria di `chat_keys` è diventata la coppia (conversazione, epoca), con
la migrazione 5→6 che ricopia le righe esistenti a epoca zero. Ogni busta `CDX3` dice già con quale
epoca è chiusa: quel campo c'era da M4 e serviva a questo.

Togliere un nome da una lista non toglie niente a nessuno: chi è uscito ha ancora la chiave in mano.
**La rotazione è il punto; la lista è contorno.**

**Gli inviti.** Il gettone vive solo nel link (`codex://gruppo/{id}/{gettone}/{chi invita}`), e sul
server non finisce nemmeno la sua impronta: chi lo presenta bussa a chi lo ha dato, e quello consegna
la chiave dal suo telefono. Un invito a un gruppo che l'invitante ha lasciato non apre più niente —
ed è giusto: **una chiave la dà una persona, non un pezzo di carta.** Toccare il link apre Codex sulla
schermata giusta, già piena, e non fa entrare in nulla da solo.

**Cosa vede il server**: gli account dei membri e chi comanda, perché deve sapere a chi consegnare e
chi può cambiare la lista. Non il nome del gruppo, che viaggia dentro la busta della chiave insieme
all'elenco dei membri — un nome di gruppo dice quasi sempre di cosa si parla.

Tredici prove con **tre telefoni finti** (tre magazzini in memoria, tre identità, una posta e un
corriere condivisi) coprono il criterio di accettazione e il suo contrario: la chiave arriva a chi è
del gruppo, un quarto che intercettasse i documenti non ci capisce niente, chi viene tolto legge il
passato e non il futuro, chi se ne va perde anche il passato (la chiave la butta via lui, uscendo),
la gemma non cambia faccia a ogni uscita, una chiave arrivata in ritardo non riporta indietro
un'epoca, una busta illeggibile non blocca la posta e una da uno sconosciuto aspetta invece di
essere buttata.

#### Due cose scoperte scrivendo, che valgono più del resto

**Creare un gruppo aspettava la rete.** Sull'emulatore il pulsante è rimasto su "sto consegnando la
chiave…" per sempre. Il motivo non era un errore di rete: **una scrittura Firestore offline non
fallisce, resta in attesa** dell'acknowledgement del server, e il `Task` non si completa mai. Il
gruppo era già nato in locale un millesimo di secondo dopo il tocco, ma la schermata aspettava la
consegna. Ora la creazione ritorna appena il gruppo esiste e le chiavi partono in background; e la
posta ha comunque un tetto di dodici secondi sull'attesa, perché "non adesso" è una risposta e
"aspetta per sempre" no.

**E "Disconnetti" non disconnetteva.** Stessa famiglia, trovata dall'utente sull'emulatore: prima di
uscire si toglie il gettone delle notifiche — una scrittura Firestore — e con il token di accesso
scaduto quella scrittura restava in attesa per sempre. Il tasto non faceva niente, e non c'era niente
che lo dicesse. Ora il registro dei dispositivi ha un tetto di otto secondi e chi esce non ci si
appoggia: **uscire è la richiesta, togliere il gettone è cortesia** — un gettone dimenticato lo
raccoglie `onMessageCreated` alla prima notifica non consegnata, un tasto che non disconnette non lo
raccoglie nessuno. Tre prove in `:app` lo tengono fermo (ed è la prima volta che un ViewModel di
Codex ha un test: è servito aggiungere mockk, che era già nel catalogo e non usava nessuno).

La regola che viene fuori da tutte e tre: **davanti a un gesto di chi usa l'app non ci va mai
un'attesa di rete senza tetto.**

**Chi comanda si guarda in `resource`, non in `request.resource`.** Nelle regole di Firestore è
l'errore più facile da fare: se la condizione leggesse la lista di amministratori del documento *in
arrivo*, basterebbe scriversi dentro quella lista per averne il diritto. C'è un test apposta, e
quella riga senza il test sarebbe passata per corretta.

#### Il resto della milestone

- `inbox/{uid}/items`: ci **scrive chiunque abbia un account**, apposta. Sembra largo e non lo è —
  ogni busta è cifrata e firmata, quindi al massimo si recapita del rumore. Restringere ai soli
  contatti vorrebbe dire tenere sul server chi conosce chi, cioè il grafo sociale, che è proprio la
  cosa che Codex non gli lascia sapere. Il tetto di 8 KB e la scadenza a sette giorni fanno il resto.
- `onInboxItemCreated`, in produzione: senza una spinta, chi ha chiesto di entrare aspetterebbe che
  l'invitante riapra Codex per conto suo.
- Quindici prove nuove sulle regole (36 → 51 in tutto), e i file dei test girano ora **uno alla
  volta**: due file che aprono lo stesso progetto sull'emulatore in parallelo si negavano a vicenda
  scritture legittime, e il sintomo era un test che falliva solo insieme agli altri.
- L'interfaccia: nuovo gruppo (nome + contatti già in rubrica), informazioni del gruppo dentro la
  schermata che c'era già (membri, chi comanda, invita, esci, togli con conferma), "N persone" sotto
  il nome, e "In attesa della chiave" quando non è ancora arrivata — che è un'informazione, non un
  guasto.

Restano fuori, e sono scelte dichiarate: l'invito via QR e via vicinanze (il QR c'è per le schede
contatto, e le vicinanze sono M7), le menzioni, e la riconsegna automatica di una chiave che non è
partita — per adesso c'è "Rimanda la chiave", che è esplicito e visibile invece che silenzioso.

### M7 · Le vicinanze: due telefoni e nessun server (2026-09-09)

Il criterio di accettazione è stato **provato davvero**, con due dispositivi: un messaggio scritto su
un telefono è comparso sull'altro, aperto correttamente, con la doppia spunta tornata indietro — e
sul telefono che riceveva **non c'era nessun account**, quindi non esisteva nessuna strada verso il
cloud. È la prima volta in tutto il progetto che due istanze di Codex si parlano davvero.

I due dispositivi sono due emulatori sulla stessa macchina: Nearby Connections funziona fra loro
(passa da `ENCRYPTED_BLUETOOTH`), e questo cambia le cose per il futuro — le vicinanze si possono
provare senza avere due telefoni in mano. Sono lenti e la radio virtuale ogni tanto dà
`STATUS_RADIO_ERROR`, ma reggono.

**Cosa si annuncia, e cosa no.** L'antenna grida a chiunque sia nel raggio, quindi quello che dice
non deve dire niente: il nome annunciato è `HMAC(seme del portachiavi, giorno)` troncato a otto byte.
Cambia a mezzanotte, e chi non ha quel seme non può né ricostruirlo né legare quello di oggi a quello
di ieri. Il Codex ID viaggia **dopo**, sul canale già aperto fra due soli dispositivi.

**Chi si collega non è ancora nessuno.** Diventa una persona quando firma una sfida con la chiave
Ed25519 della sua identità, e resta connesso solo se quella persona è già in rubrica. La sfida
contiene il Codex ID di chi risponde: senza, una firma ottenuta in una conversazione varrebbe in
tutte le altre, e basterebbe rigirare la sfida a un terzo per farsi dare la risposta giusta da lui.
Dieci prove in `:core:crypto` coprono proprio quel giro.

**Non ci si conosce da vicino.** Chi non è già in rubrica viene scollegato: le vicinanze servono a
parlarsi, non a conoscersi. Per conoscersi ci sono il QR e il codice, guardandosi in faccia. È anche
il motivo per cui il "pairing per vicinanza" del piano non c'è: sarebbe un modo di accettare la
scheda di un dispositivo che passa per strada, e quello che ci si guadagna non vale quello che ci si
perde.

Quello che passa sul filo è la **stessa busta `CDX3`** che passerebbe dal cloud, con intorno solo
l'indirizzo — chat, messaggio, mittente — cioè esattamente quello che il server saprebbe comunque. Il
messaggio consegnato di persona **resta in coda** per il cloud: l'altra persona può avere un secondo
dispositivo, e lo stesso identificatore fa da protezione contro il doppione.

#### Quattro difetti che solo due dispositivi potevano mostrare

Il protocollo aveva quattordici prove verdi con due motori su un filo finto, e le quattro cose che
seguono sono passate lo stesso. Vale la pena averle scritte.

1. **`callbackFlow` senza buffer butta gli eventi.** Un flusso di callback consegna solo se qualcuno
   sta aspettando in quel preciso istante; qui chi raccoglie apre il database e verifica firme, e
   tutto ciò che arriva mentre è occupato spariva **in silenzio**. Il saluto dell'altro arrivava
   mentre si mandava il proprio, e l'autenticazione non partiva mai.
2. **L'identità si legge una volta sola, e l'app parte bloccata.** Il motore guardava se c'era
   un'identità all'avvio, la trovava chiusa, e non riprovava più: l'antenna non si accendeva per
   tutta la sessione. Ora guarda il modo **e la serratura** insieme, e riparte allo sblocco.
3. **Consegnare solo alla stretta di mano non basta.** Un messaggio scritto mentre l'altra persona è
   già lì restava fermo con la spunta di attesa. Nei test non si vedeva perché lì la consegna la
   chiedeva il test. Ora il motore guarda anche la coda.
4. **`stopAllEndpoints()` all'avvio spegne l'antenna che stai accendendo.** Sembrava la cosa pulita
   ("si riparte da zero") ed era una corsa fra due chiamate asincrone: la pulizia arrivava dopo
   l'annuncio e lo cancellava. Le connessioni ereditate da una vita precedente dell'app si
   **adottano** invece (`STATUS_ALREADY_CONNECTED_TO_ENDPOINT` non è un errore, è un canale già
   aperto: senza, dopo un riavvio i due restano collegati e muti).

La cosa che le lega: **un test con una finta al posto del mondo prova il protocollo, non
l'ambiente.** Tutti e quattro erano nell'ambiente.

#### Il resto

- Tre modi e non un interruttore: spente, mentre Codex è aperta, sempre (con servizio in primo piano
  e notifica visibile — un'antenna accesa a schermo spento è una cosa che chi ha il telefono deve
  vedere). Il piano diceva "15 min dopo l'ultima chiusura"; tre stati sono più facili da spiegare e
  da prevedere.
- I permessi si chiedono quando si accende, **con la spiegazione prima**: quello sulla posizione, che
  Android pretende sotto il 12, senza una frase intorno sembra una bugia. `neverForLocation` sui
  permessi Bluetooth e Wi-Fi dice al sistema che di quella posizione non ce ne facciamo niente.
- In chat "Vicino" viene prima di "online": è più vero.
- `play-services-nearby` è fermo alla **19.3.0**: la 19.5.0 porta metadati Kotlin 2.4 e questo
  progetto compila con la 2.2.20.
- I gruppi restano sul cloud: fra i membri di un gruppo qualcuno è nella stanza e qualcuno no, e
  tenere due strade allineate per un guadagno che nessuno vedrebbe non vale il rischio.
- **Le vicinanze vogliono l'app sbloccata**, ed è una conseguenza e non una scelta: il nome
  annunciato e la firma nascono dall'identità, che vive nel vault. "Sempre" tiene acceso finché la
  serratura è aperta.

### Il telefono di qualcun altro (2026-09-08)

Mentre lavoravo si e' collegato al PC un telefono fisico. `tools/shot.sh` sceglieva il dispositivo
da solo -- "il primo che non sia `emulator-5556`" -- e ha scelto quello: ci ha installato sopra la
build di lavoro, l'ha avviata, e i tocchi successivi sono finiti li'. Uno di quelli ha creato
un'identita' Codex sul telefono personale dell'utente.

Non e' un difetto dell'app, ed e' peggio: e' uno strumento che decideva da solo su quale telefono
agire. Adesso, se ne trova piu' di uno e `CODEX_DEVICE` non e' impostato, **si ferma e li elenca**.
Indovinare quale telefono di qualcun altro toccare non e' una comodita' che valga il rischio.

### M8 · Le rifiniture, che non sono rifiniture (2026-09-09)

M8 è la milestone che sulla carta sembra la meno interessante — impostazioni, traduzioni, crediti —
e in pratica è quella in cui l'app smette di essere una dimostrazione. Sono le cose che una persona
tocca il primo giorno: cosa vede quando apre una chat che non ha mai visto, come dice all'app di
non tenere più il suo account, chi ha dipinto il quadro che gli è appena arrivato addosso.

#### I due gesti che nessuno indovina

Codex ne ha due che non esistono altrove: **si tocca il sigillo per aprirlo**, e **si tiene premuto
l'invio per scegliere la forma in cui parte il messaggio**. Il primo si scopre per caso, il secondo
non si scopre. Il piano (§2.2) dice come vanno spiegati: non con un tour all'avvio, ma la prima
volta che si incontra la cosa, con una frase e accanto all'elemento. È `FluidTutorialHost`, e ora
c'è: tre suggerimenti (`seal-tap`, `send-hold`, `nearby`), quello che si è già visto non torna, e
chi dice "basta suggerimenti" non ne vede più — è l'unica promessa che questa parte fa, e sta in
una preferenza che nessuno riaccende.

Due difetti sono usciti solo sull'emulatore, e sono istruttivi tutti e due:

1. **Il padrone di casa nasceva e moriva a ogni ricomposizione.**
   `rememberFluidTutorialHostState()` costruisce la politica come argomento di default, e
   `FluidTutorialPolicy` non ha uguaglianza di valore: ogni ricomposizione ne produce una nuova, il
   `remember(policy)` dell'engine la legge come chiave nuova e rifà lo stato da capo. Ogni
   suggerimento offerto finiva in un'istanza destinata a essere buttata. Non compariva **niente**, e
   dai log sembrava che tutto funzionasse: l'offerta partiva, la coda restava vuota. La cura
   nell'app è tenere la politica in un `remember`; il difetto però è dell'engine, e va portato lì.
2. **Il suggerimento si toglieva di scena da solo.** Si segna visto nell'istante in cui compare —
   giusto: chi lo scaccia scorrendo l'ha comunque visto — e quel segno rientrava dalla porta come
   "non offrirlo più", che diventava un ritiro, che toglieva di scena il callout appena apparso.
   Restava l'alone intorno al tasto indicato, senza più niente che lo spiegasse. Adesso la regola sta
   in una funzione pura (`hintAction`) con sei prove: **quello in scena non si ritira mai**.

#### I due pannelli

Il criterio di accettazione diceva "tablet in orizzontale con lista + chat", e fino a ieri c'era
solo la guida di lato. Ora sopra gli 840 dp la lista **non se ne va più**: resta a sinistra, larga
340 dp, e la conversazione si apre accanto. La riga aperta si riconosce (occhiello "Aperta"), aprire
una seconda conversazione **sostituisce** la prima nella pila invece di impilarla, e la lista vive
fuori dal `NavHost` così non si ricompone — e non perde il punto in cui era arrivata — a ogni chat
aperta.

Due misure sbagliate, trovate guardando lo schermo:

- `fillMaxSize()` dentro una `Row` si prende tutta la larghezza in un colpo, e al pannello a misura
  fissa, misurato dopo, non resta niente. Si vedeva: una colonna di lettere in verticale.
- `codexHorizontalPadding()` calcolava i margini sulla **larghezza dello schermo**: dentro un
  pannello da 340 dp se ne metteva novantasette per lato. Ora chi affianca due pannelli dice quanto
  è largo il suo (`LocalCodexPaneWidth`), e il pannello del contenuto la sua larghezza la
  **misura** invece di dedurla.

#### I crediti

Trentasei riproduzioni in CC0 dell'Art Institute of Chicago e un font in OFL: nessuna delle due
licenze obbligherebbe a scrivere quella pagina. C'è lo stesso, con titolo originale, autore, anno e
la riga che si apre sulla scheda del museo — per lo stesso motivo per cui quelle immagini stanno in
rete. Le licenze dell'engine si sono spostate lì dentro (in fondo alla schermata "Io", dopo la zona
rossa, non le leggeva nessuno) e ora il titolo e la nota arrivano dalle stringhe dell'app: i valori
di fabbrica dell'engine sono in italiano, e su un telefono in inglese erano due frasi italiane in
mezzo a una pagina tradotta. **Resta in italiano una voce**, il testo della licenza di "Square", che
è un dato dell'engine e va corretto lì.

#### Il resto, in breve

- **Eliminazione account**: prima i documenti, poi l'account. Dopo `user.delete()` non ci sono più i
  permessi per toccare niente, e quello che fosse rimasto resterebbe lì per sempre — nemmeno la
  persona a cui apparteneva potrebbe più portarlo via. Quello che non si cancella è scritto nella
  richiesta di conferma: le buste già consegnate stanno nelle conversazioni di altre persone, e
  scadono da sole a trenta giorni.
- **Dispositivi collegati**: chi altro riceve le notifiche di questo account. Togliere un
  dispositivo non lo disconnette — si riregistrerà alla prossima apertura, se è ancora tuo — ed è
  scritto sotto il tasto, perché una revoca che sembra definitiva e non lo è è peggio di nessuna
  revoca.
- **Il Codex ID nelle preferenze è una copia**, non l'originale: sta fuori dal vault perché la
  schermata di sblocco deve poter salutare per nome a identità ancora chiusa. Se quella copia si
  perde, adesso lo sblocco la riscrive. L'ho scoperto perché l'ho persa io, cancellando a mano il
  file delle preferenze sull'emulatore per riazzerare i suggerimenti: l'identità era intatta e "La
  mia scheda" mostrava un vuoto al posto dell'identificatore.

**Verificato sull'emulatore**: la pagina dei crediti in inglese (dipinti, font, librerie), il
callout "Choose the shape" ancorato al tasto di invio con il gesto disegnato, i due pannelli in
orizzontale con la conversazione aperta accanto alla lista. 253 test verdi, lint pulito con
`MissingTranslation` e `HardcodedText` a errore.

### M9 · Verso la beta: quello che si vede solo facendolo girare (2026-09-09)

#### I difetti dell'engine, corretti dove stavano

I due trovati in M8 erano dell'engine e sono stati portati lì: **engine 1.30.1**, tagliata nel repo
dell'engine (commit e tag locali, non spinti).

- `FluidTutorialPolicy` è una `data class`. `rememberFluidTutorialHostState` la costruisce come
  argomento di default e ci fa sopra `remember(policy)`: senza uguaglianza di valore ogni
  ricomposizione ne produceva una diversa, il `remember` la leggeva come chiave nuova e **rifaceva
  il padrone di casa da zero**. Un test lega adesso quell'uguaglianza, con scritto perché.
- Nei crediti la licenza di "Square" è `GPL-3.0 (no code taken)`: la qualifica era in italiano ed
  era l'unica riga non tradotta di una pagina inglese. La KDoc di `fluidLicensesSection` dice che i
  valori di fabbrica italiani vanno sostituiti da chi traduce l'app.

Codex è passata da 1.23.0 a 1.30.1 in un colpo: fra le due non c'è **niente** che la riguardi — le
sette versioni in mezzo sono tutte `engine-ai` e `engine-ai-bridge`, moduli che Codex non include.
La soluzione temporanea nell'app (tenere la politica in un `remember`) è stata tolta: l'engine
adesso è giusto da solo.

#### "Non c'è niente" e "non lo so ancora" sono due cose diverse

Le liste partivano da `emptyList()` e mostravano il cartello del vuoto — *Nessuna chat*, *Nessun
messaggio* — per l'istante prima della prima emissione. Su un telefono è un lampo; sui due pannelli
del tablet, dove la lista resta in scena, si legge benissimo. Adesso il valore iniziale è `null`:
il cartello compare solo quando la risposta è arrivata **ed è vuota**. Vale per chat, messaggi,
contatti e storie.

#### R8 non si legge, si fa girare

`isMinifyEnabled` era acceso da M0, ma una regola che manca non si vede compilando: si vede quando
la classe non c'è più e l'app cade. La release è stata **installata e usata**: identità creata
(Argon2id + AES-GCM dentro Bouncy Castle), database aperto (SQLCipher), un messaggio scritto,
sigillato in un quadro, aperto e riletto, i crediti e le impostazioni scorse. Nessun crollo,
nessuna classe mancante. L'APK pesa **25,1 MB**.

Per farlo servivano le architetture degli emulatori, che il rilascio esclude apposta (x86 e x86_64
non esistono su nessun telefono, e SQLCipher se le porta dietro): `-Pcodex.allAbis` le rimette per
la prova e non tocca quello che si spedisce.

**Quello che R8 non ha ancora attraversato** è una sessione Firebase vera: per averla serve un
accesso Google, e quello lo fa l'utente. È l'ultima prova prima di pubblicare.

#### App Check

C'era la libreria nel catalogo e non c'era il codice: adesso l'attestazione si installa
all'avvio del processo, **prima** di qualsiasi chiamata a Firebase (installarla alla prima richiesta
vuol dire che la prima parte senza gettone, e in enforcement viene rifiutata). Il fornitore cambia
con la build e vive in due file diversi, `src/debug` e `src/release`: così la libreria del gettone
di debug — che esiste solo per essere usata al posto di un'attestazione vera — **non entra
nell'APK che le persone installano**.

Il gettone di debug lo stampa Firebase nel logcat al primo avvio di una build di lavoro
(`adb logcat | grep DebugAppCheckProvider`), e va incollato una volta nella console. Fallire
l'attestazione non ferma l'app: senza gettone il server dice di no alle chiamate protette, e tutto
quello che vive sul telefono continua a funzionare.

#### L'audit: cosa c'è davvero su disco

Con `run-as`, sulla build di lavoro:

| file | cosa si legge |
|---|---|
| `databases/codex.db` | byte casuali. Nessuna intestazione `SQLite format 3`: SQLCipher sta cifrando |
| `files/identity/vault.bin` | `CDXVLT` e poi rumore (Argon2id + AES-GCM) |
| `files/db.key` | la frase del database, cifrata con una chiave dell'Android Keystore che non esce dall'hardware |
| `files/media/*` | `CDXM` e poi rumore |
| `files/datastore/codex_prefs` | in chiaro **di proposito**: nome, seme del minerale, Codex ID, tipo di serratura, interruttori. Niente di segreto: la schermata di sblocco deve poter salutare per nome a vault chiuso |

I permessi dell'APK di rilascio, letti con `aapt2 dump permissions`, sono venti e nessuno è di
troppo: i nostri (rete, notifiche, fotocamera per il QR, microfono per le note vocali,
Bluetooth/Wi-Fi vicini con `neverForLocation`, posizione **solo fino ad Android 11** perché sotto il
12 Nearby la pretende, servizio in primo piano) e quelli che arrivano dalle librerie (FCM, biometria,
vibrazione, `RECEIVE_BOOT_COMPLETED` di WorkManager). L'unico che merita di essere detto ad alta
voce è **`REQUEST_INSTALL_PACKAGES`**: lo porta `engine-update`, ed è come Codex si aggiorna dal
Pampa Store.

Nel registro di sistema non finisce niente di segreto, e da questa versione nemmeno i nomi: le due
righe che ne portavano uno (`vicino: <nome>`, la chiave di un gruppo) sono scese a `Log.d`, e una
regola R8 toglie `d` e `v` dal rilascio insieme alle stringhe che le costruiscono.

#### Dove siamo

`1.0.0-beta.1`, versionCode 100, con il changelog per chi la installa in `CHANGELOG.md`. 253 prove
di unità, 59 sulle regole di Firestore e Storage, lint con `MissingTranslation` e `HardcodedText` a
errore, `engine-doctor` pulito.

**Prima di pubblicare servono due cose che non posso fare io**: l'App Check acceso in console (Play
Integrity per il rilascio, il gettone di debug in lista) e una prova con due account veri sulla
build di rilascio, che è l'unico pezzo che R8 non ha ancora attraversato.

#### Le tre decisioni, prese (2026-09-09)

- **Non si pubblica ancora.** L'APK firmato, il changelog e il manifest restano pronti sul disco.
- **La voce sul Pampa Store resta quella**, con il pacchetto nuovo: `manifest.json` punta a
  `dev.pampa.codex` e la descrizione racconta l'app riscritta invece della vecchia. Va detto per
  intero: chi ha ancora la 0.2.4 (`com.codex.app`) **non la aggiorna** con questa — sono due
  applicazioni diverse per Android, e finché il canale stabile serve il vecchio APK la voce ne
  contiene due. Si risolve da sé quando la 1.0.0 esce anche su stabile.
- **La correzione dell'engine è su GitHub**: `engine-1.30.1` spinta su `Casual76/fluid-engine`, così
  ce l'hanno anche le altre app.

#### Il primo commit (2026-09-09)

Il sorgente è su `Casual76/Codex`: 291 file, quarantatremila righe, un commit solo. Fino a ieri il
repo conteneva **soltanto** il manifest del Pampa Store, mentre la schermata "Informazioni" diceva
già a tutti che il sorgente era lì. Adesso è vero. Niente segreti: `google-services.json`,
`keystore.properties`, i keystore e `local.properties` erano già esclusi, e il `firestore-debug.log`
che l'emulatore lascia accanto ai test delle regole è stato tolto dall'indice e aggiunto al
`.gitignore` prima di committare.

Serviva anche per pubblicare: `publisher.py` rifiuta una release con l'albero sporco
(«Live publish preflight is not ready: source worktree is not clean»), ed è una buona regola —
un APK sullo store senza il codice che gli corrisponde è un APK di cui nessuno può più dire da dove
viene.

#### App Check, e perché la beta aspetta (2026-09-09)

Acceso l'enforcement, **ogni** lettura e scrittura di Firestore dalla build di lavoro torna
`PERMISSION_DENIED`. Non è un difetto: è App Check che fa il suo mestiere, e nessuno dei due
fornitori può dargli un gettone valido.

- In sviluppo serve registrare il **gettone di debug** che Firebase stampa nel logcat al primo
  avvio. Senza, l'app di lavoro non parla più col progetto vero.
- In rilascio c'è un problema più profondo: **Play Integrity attesta le app che passano dalla Play
  Console**, e Codex esce dal Pampa Store. La documentazione dice che si può fare lo stesso, a due
  condizioni: l'app va registrata nella Play Console (Release → App integrity → Play Integrity API,
  collegata al progetto Firebase), e in *App Check → Apps* i verdetti `PLAY_RECOGNIZED` e `LICENSED`
  vanno messi su **non richiesti**. Senza, il gettone non arriva e ogni chiamata viene rifiutata:
  la beta sarebbe un'app che funziona solo sul telefono di chi la installa.

Decisione: per la beta **App Check resta in monitoraggio** (acceso, misura, non blocca). Il codice
che installa l'attestazione resta dov'è: quando i verdetti diranno che i gettoni arrivano davvero,
l'enforcement si accende senza toccare una riga.

---

## 13. Test e qualità

| Livello | Cosa | Strumento |
|---|---|---|
| Unità | crypto (KAT, round-trip, parola sbagliata, epoche), seal (semi deterministici, stego, capacità, Voronoi valido), sync (dedup, outbox, ordini) | JUnit 5, MockK, Turbine |
| Regole | Firestore/Storage: accessi consentiti e negati per ogni collezione | Emulator + rules-unit-testing |
| Functions | push corrette, cleanup | Emulator, Jest |
| UI | macchina degli stati dei sigilli, "Rivela tutto", lock/unlock, onboarding | Compose UI test |
| Animazioni | fotogrammi e luminanza per le tre rivelazioni | `tools/frames.ps1` (screenrecord + ffmpeg) |
| Manuale | due dispositivi: pairing nei 3 modi, vicinanze, gruppi, notifiche a app chiusa, Doze | checklist in `docs/TEST-MANUALI.md` |
| Sicurezza | nulla in chiaro su disco, `FLAG_SECURE`, log senza segreti, App Check | audit a M5 e M9 |
| Lint | `HardcodedText`, `MissingTranslation`, permessi inutilizzati a errore | Android Lint |

Regola: una milestone non si chiude con test rossi o con `engine-doctor` che segnala modifiche non
committate dentro `engine/`.

---

## 14. Rilascio e operazioni

- **Versioni**: `1.0.0-beta.N` durante la beta, `1.0.0` stabile; `versionCode` incrementale da 100.
- **Firma**: `pampa.jks` tramite `keystore.properties` (mai nel repo).
- **Canali Pampa Store**: beta per ogni build fino alla stabile; il `manifest.json` alla radice del
  repo è anche il manifest remoto dell'engine (sezione `engine`: `minimumVersion`, `flags`,
  `killSwitch`).
- **Feature flag** iniziali (default = comportamento della build): `nearbyBackground`,
  `storiesEnabled`, `paintingExport`.
- **Aggiornamento engine**: `engine-update.ps1 -AppRoot . -Version X`, poi build, poi commit di
  `engine` + `engine.properties` insieme.
- **Backup/ripristino utente**: coperto da vault + keyring (nessun file da esportare).
- **Chi ha la vecchia Codex 0.2.4**: disinstallare (firma diversa, pacchetto diverso). La voce
  `Codex` sul Pampa Store viene aggiornata al nuovo `packageName` alla prima beta.

---

## 15. Prerequisiti a carico tuo (checklist)

- [x] **Firebase**: progetto `codex-e4795` creato, tutte e due le app Android aggiunte,
  `google-services.json` valido in `app/` dal 2026-09-07 (quello del 2026-09-05 era di 0 byte).
  La build lo usa.
- [ ] Firebase: abilitare Authentication → Google; Firestore (scegliere la regione europea, non si
  cambia dopo); Storage; Cloud Messaging; Cloud Functions (piano **Blaze**); App Check.
- [ ] Firebase: registrare SHA-1 e SHA-256 di debug e di `pampa.jks` su **tutte e due** le app,
  poi **riscaricare** `google-services.json`. Finche' `oauth_client` nel file resta vuoto, l'accesso
  con Google non ha nulla a cui agganciarsi. Le impronte sono gia' estratte, in
  `docs/FIREBASE-IMPRONTE.md`.
- [x] ~~`keystore.properties`~~ **non serve**: la release si firma gia' con `pampa.jks` trovato tramite il `keystore.properties` di universal_converter (verificato il 2026-09-07 leggendo il certificato dell'APK di release).
- [x] L'immagine del cifrario: **arrivata** il 2026-09-07 (`cifrario.jpeg`, foto di una fotocopia, la migliore che esista). È in `app/src/main/assets/origini/` e la tavola dell'app è stata rifatta su quella.
- [ ] Il nome che vuoi vedere nel profilo dei crediti ("Pampa"?) e, se vuoi, il testo sulle origini.
- [x] Un secondo dispositivo: **collegato** il 2026-09-07 (Galaxy Tab S9, `R52X50DKRBW`).
  L'app ci gira, e il layout per schermi larghi e' stato verificato li'. Resta da provare a mano il
  passo della biometria, che richiede il dito di chi possiede il tablet.
- [ ] Il consenso a creare il branch/commit iniziale nel repo `Casual76/Codex` con la struttura Android.

## 16. Rischi e mitigazioni

| Rischio | Mitigazione |
|---|---|
| Nearby instabile su alcune ROM | trasporto isolato; il cloud non dipende mai da Nearby; fallback automatico |
| Argon2id lento sui telefoni vecchi | parametri adattivi memorizzati nel vault; sblocco quotidiano via Keystore/biometria, Argon2 solo al primo avvio/nuovo dispositivo |
| PNG del Quadro ricompresso fuori dall'app | avviso chiaro, "invia come file", rilevamento del fallimento con messaggio comprensibile |
| Costi Firestore con fan-out delle storie | limite 200 contatti, push silenziose, TTL 24 h |
| APK grande (dipinti + font) | WebP 1280 px, budget 25 MB, `splits` per ABI se SQLCipher pesa |
| Animazioni belle ma lente | Playground in M2, misura sui fotogrammi, cache dei sigilli fermi |
| Feature creep (ha ucciso la v1) | tutto ciò che non è in §0 va in "Fuori dalla v1"; il piano si cambia prima del codice |

## 17. Fuori dalla v1 (idee raccolte, non promesse)

Video; widget Glance; tecnica "cifrario storico fedele"; sfide di decifrazione tra amici; sigilli e
sfondi collezionabili; esportazione di Rune/Roccia come immagini; iOS/web; chiamate; backup su file;
temi stagionali dei minerali; "Vicini" per sconosciuti.

---

## Appendice A · Archeologia della vecchia Codex 0.2.4

Recuperata dall'APK (Capacitor + React + Vite, codice JS in chiaro). Serve come riferimento, **non**
come base di codice.

- Identità senza account (ID casuale 8 caratteri + nickname); pairing via QR/manuale/file con
  chiave condivisa inserita a mano; chat 1:1 e gruppi (ID + chiave); risposte, reazioni, spunte,
  typing, presenza, elimina per tutti, bozze, allegati con "riconversione" formato (ffmpeg +
  ImageMagick in wasm, 45 MB); PIN 4 cifre/biometria; tastiera incognito; push predisposte ma
  spente.
- Backend: Firebase Realtime Database (`inbox/{peer}/messages` come corriere, `chats/{id}` per
  gruppi/cloud, `peer_status`, `tokens`), **senza autenticazione**.
- Crittografia: XOR con keystream xorshift32 seminato da FNV-1a(chiave + salt 8 B) — non sicura;
  chiave condivisa inviata **in chiaro** nel pairing (`plainKey`). Formato `CDX2`.
- "Steganografia": i byte cifrati disposti come pixel di un BMP (rumore), non nascosti in una foto.
- Presente ma inutilizzata: una tabella "cifrario sillabico" (a=10, b=11 … ba=…, bb=170 …) derivata
  dal cifrario del prof: l'origine dell'idea che ora diventa "Le origini".
- Design: Material 3 + vetro CSS + blob liquidi, Inter/JetBrains Mono, teal; barra in vetro con Chat ·
  QR · Impostazioni; icona "C" con lucchetto su sfondo sfaccettato.
- Firma: certificato `CN=Cifratura, O=Antigravity` (keystore perso). Pacchetto `com.codex.app`,
  versionCode 2.
- Le stringhe italiane dell'interfaccia (≈ 290) sono in `docs/archeologia/stringhe-codex-0.2.4.txt`:
  utili come copy di partenza per i testi della nuova app.

## Appendice B · Glossario tecnico

- **Scheda (ContactCard)**: identità pubblica firmata di un utente.
- **Vault**: identità privata cifrata con PIN/password (Argon2id + AES-GCM).
- **Keyring**: chiavi di chat/gruppo e contatti, cifrati con una chiave derivata dall'identità,
  sincronizzati fra i dispositivi.
- **Busta (CDX3)**: il messaggio cifrato così com'è trasmesso e, per Quadro, incorporato nel PNG.
- **Epoca**: numero di versione di una chiave di gruppo (cambia quando qualcuno esce).
- **Seme**: numero derivato da chiave e id del messaggio che decide l'aspetto del sigillo.
