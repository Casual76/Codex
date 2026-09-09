# Le prove che deve fare una persona

Il piano (§13) rimanda a questo file. Qui ci sono solo le prove che **non si possono automatizzare
e non si possono fare da soli**: tutto il resto e' nei 130 test di unita' e nei provini a contatto
di `tools/frames.sh`.

Chi legge questo file ha in mano un telefono, non una tastiera. Ogni voce dice cosa fare, cosa deve
succedere, e — dove non e' ovvio — **perche' quella cosa e' li'**.

Stato al 2026-09-08: le prove del gruppo A sono state fatte sull'emulatore e sul tablet e sono
verdi. **B1 e' diventato possibile con M3**: il pairing non passa da nessun server, quindi bastano
due dispositivi e nessuna rete. B2, C e D aspettano ancora il cloud, un dito e una persona che non
vede lo schermo.

---

## A. Quello che si puo' provare adesso, su un dispositivo solo

### A1 · Il primo sigillo

1. Installa su un dispositivo senza Codex e apri.
2. **La gemma si spacca al tocco**: prima si accendono le crepe *dentro* la pietra, poi le schegge
   partono verso l'esterno e resta la polvere.
3. Il testo cambia in "Benvenuto in Codex" **quando le schegge se ne sono andate**, non prima.

Se le crepe compaiono fuori dalla sagoma, il disegno della superficie e la geometria non stanno piu'
alla stessa scala: e' il difetto del 2026-09-07, ed e' descritto nel piano.

### A2 · Identita' e serratura

1. Scrivi un nome: **il minerale cambia mentre scrivi**.
2. Scegli un PIN, ripetilo.
3. Sbaglia il PIN alla riapertura: i pallini **scuotono la testa** e diventano rossi.
4. Sbaglia cinque volte: compare l'attesa, e cresce.
5. Riapri con il PIN giusto.

### A3 · I tre sigilli

Manda tre messaggi nella chat con te stesso, uno per tecnica (tieni premuto Invia per scegliere).

1. **Roccia**: crepe, schegge che portano via la superficie che avevano sopra, polvere.
2. **Rune**: il testo si scioglie da sinistra, e la parte ancora chiusa continua a cambiare segno.
   Con il telefono in mano si sente un tocco leggero mentre le lettere si fermano.
3. **Quadro**: si stacca a scaglie che **cadono**, e sotto resta l'ombra scura dello stesso dipinto.
4. Esci dalla chat e rientra: **sono tutti richiusi**.
5. "Rivela tutto" riapre solo quelli gia' visti.

### A4 · Il quadro che esce e rientra

1. Tieni premuto un messaggio-quadro → "Condividi come immagine" → scegli Codex stesso.
2. Deve dire **"Ce l'hai gia'"**.
3. Rimanda l'immagine a te su una chat di un'altra app **come foto** (non come file), salvala, poi
   aprila da Codex con "Apri un quadro".
4. Deve dire **"Dentro non c'e' niente"**: la ricompressione ha cancellato il messaggio, ed e'
   esattamente il caso per cui quel testo esiste.

### A5 · Le origini

1. Io → Le origini.
2. La foto della tavola c'e' e si apre a tutto schermo: **allarga con due dita**, doppio tocco per
   saltare all'ingrandimento.
3. Nel banco di prova, scrivi `il papa scrive domani a firenze`: *papa*, *domani* e *firenze* devono
   diventare **un segno solo l'una**.
4. "Mostra cosa vuol dire" scopre sotto ogni segno quello che vale.

### A6 · Schermi larghi

1. Gira il telefono in orizzontale **sulla serratura**: il ritratto sta a sinistra, il tastierino a
   destra, e tutti i tasti si vedono. Questa e' la prova che conta: impilati, l'app non si sbloccava.
2. Su un tablet: la navigazione e' di lato, e la colonna del contenuto **si ferma e sta in mezzo**.
3. Sopra gli 840 dp (un tablet, o questo telefono in orizzontale) la lista delle chat **resta a
   sinistra** e la conversazione si apre accanto. Guarda tre cose: la riga aperta porta l'occhiello
   "Aperta"; il testo dentro il pannello stretto **non va a capo lettera per lettera** (era il
   difetto: i margini si calcolavano sullo schermo intero); aprendo una seconda conversazione e poi
   tornando indietro si arriva al pannello vuoto, non alla conversazione precedente.

### A7 · Tema chiaro

Io → Aspetto → Chiaro, poi riapri un quadro e una roccia.

1. Il testo del quadro resta leggibile mentre le scaglie cadono.
2. La polvere della roccia si vede (e' scura su fondo chiaro, non bianca).

### A8 · Scadenza e "visualizza una volta"

1. Chat → impostazioni (l'icona a destra) → scadenza "Un'ora".
2. Manda un messaggio, aprilo, esci e rientra: c'e' ancora. La scadenza parte **dall'apertura**.
3. Attiva "Visualizza una volta" nel foglio della tecnica, manda, apri, **esci dalla chat**.
4. Rientrando resta il segnaposto "Sigillato per sempre".
5. Mentre e' aperto, prova a fare uno screenshot: il sistema deve rifiutarsi.

### A9 · I suggerimenti al primo uso

Valgono **una volta sola per installazione**: per rivederli serve reinstallare l'app da capo
(`bash tools/shot.sh reinstalla`), e questo cancella identita' e messaggi. Cancellare a mano il file
delle preferenze **non** e' una scorciatoia: si porta via anche nome, minerale e Codex ID.

1. Apri una chat, scrivi qualcosa nella casella e **togli il dito**. Dopo mezzo secondo compare
   "Scegli la forma", agganciato al tasto di invio, con il gesto della pressione lunga disegnato.
2. Il callout **non e' un modale**: la pagina sotto resta leggibile, e un tocco fuori lo chiude
   senza consumare il tocco -- quello che c'e' sotto risponde lo stesso.
3. Chiudilo con "Ho capito". Non deve ricomparire, nemmeno riaprendo la chat.
4. In una chat con un sigillo **ricevuto e ancora chiuso** compare invece "Toccalo".
5. "Basta suggerimenti" li spegne tutti, per sempre. E' l'unica promessa che questa parte fa: se
   dopo averlo toccato ne compare un altro, e' un difetto.

### A10 · Storie, blocco per chat, dispositivi, crediti

1. **Storia**: Storie -> "Il tuo sigillo del giorno". Va sul cloud con una chiave avvolta per ogni
   contatto, e sull'altro dispositivo si apre. Dopo 24 h sparisce da sola (la Function la cancella;
   in locale la spazza via anche l'app all'avvio).
2. **Blocco per chat**: chat -> impostazioni -> "Blocca questa chat". Uscendo e rientrando, la
   conversazione chiede il PIN **del telefono** (non quello di Codex). Se togli il blocco schermo
   dal telefono, la chat deve restare sbloccabile: una serratura che dipende da una cosa che non c'e'
   piu' e' una chat persa.
3. **Tastiera incognito**: in una chat, la tastiera non deve suggerire quello che hai gia' scritto.
   Da riga di comando: `adb shell dumpsys input_method | grep imeOptions` -> deve contenere
   `0x42000000` (`IME_FLAG_NO_PERSONALIZED_LEARNING`).
4. **Dispositivi collegati**: Io -> Dispositivi collegati. "Questo telefono" non si revoca da qui
   (si usa "Disconnetti"). Revocando un altro dispositivo, quello smette di ricevere le notifiche --
   ma se lo riapri si registra di nuovo, ed e' scritto nella conferma.
5. **Crediti**: Io -> Informazioni -> Crediti. Trentasei dipinti con titolo originale, autore e
   anno; toccando una riga si apre la scheda del museo. Sotto, il font delle rune e le librerie
   dell'engine. Con il telefono in inglese **tutto** deve essere in inglese: l'unica voce che resta
   in italiano e' il testo della licenza di "Square", che e' un dato dell'engine.
6. **Eliminazione account**: da provare **solo su un account di prova**. Dopo, l'app continua a
   funzionare su questo telefono e i messaggi restano leggibili; sul server non c'e' piu' niente.

---

## B. Quello che serve un secondo dispositivo

Da qui in poi servono **due identita' diverse**. Da M3 non serve piu' Firebase: due telefoni si
aggiungono e si accordano da soli, quindi il gruppo B1 si puo' fare **adesso**, con due dispositivi
e nessuna rete.

### B1 · Il pairing, senza server (si puo' fare oggi)

1. Su tutti e due: Chat → l'icona con la persona in alto a destra → Contatti.
2. Sul primo: **Il mio sigillo**. Sul secondo: **Aggiungi una persona** → *Inquadra*, e punta la
   fotocamera sul codice del primo. Deve entrare **al primo colpo**, da venti-trenta centimetri,
   anche in una stanza poco illuminata: se serve avvicinarsi fino a toccare lo schermo, il mirino
   e' troppo stretto o il codice troppo fitto.
3. Poi si scambiano i ruoli. Serve inquadrare tutte e due le volte: senza server le schede non
   viaggiano da sole, e la schermata lo dice in fondo.
4. Provate anche l'altra strada: **Copia il codice** sul primo, mandarselo su un'altra app, e
   *Incolla* sul secondo. Deve funzionare uguale.
5. Inquadra il **tuo** codice: deve dire "Questo e' il tuo sigillo", senza sembrare un guasto.
   Inquadra un QR qualsiasi (un pacco, un menu): deve dire che non e' una scheda Codex.
6. **Il rito.** Ditevi una parola a voce. Ognuno la scrive sul suo telefono e tocca "Apri la
   conversazione". **Le due gemme devono essere identiche**: stessa forma, stesso colore, stesse
   crepe. Guardatele affiancate, non a memoria.
7. Rifatelo di proposito con **due parole diverse**: le gemme devono essere diverse in modo ovvio.
   Poi rifatelo con quella giusta: la conversazione e' la stessa, e non deve aver perso niente.
8. Con la parola sbagliata, mandate un messaggio: dall'altra parte deve **arrivare** e non aprirsi
   ("Questo non si apre"). Rifatto il rito, quello stesso messaggio deve aprirsi.
9. Elimina il contatto da uno dei due: se ne vanno la persona, la conversazione e la chiave.

### B2 · Il resto, quando c'e' il cloud

- Pairing per vicinanze (M7).
- Un messaggio mandato con l'app **chiusa** dall'altra parte: la notifica entro cinque secondi.
- I due telefoni in modalita' aereo, poi il ritorno della rete: **niente messaggi doppi**.
- Lo stesso sigillo, con lo stesso seme, disegnato uguale su tutti e due gli schermi. E' il punto di
  tutta la parte deterministica: se qui i minerali sono diversi, c'e' un generatore che non lo e'.
- Il quadro esportato da un telefono e aperto sull'altro: deve entrare nella conversazione giusta.

### B3 · Un gruppo, e chi ne esce (M6)

Serve un terzo dispositivo, o tre account: e' l'unica parte di Codex in cui **un segreto viaggia**, e
le prove automatiche lo fanno con tre telefoni finti. Qui si guarda che succeda anche con tre veri.

1. Su A: **Nuovo gruppo**, un nome, B e C. Sui due telefoni deve comparire con lo stesso nome e la
   **stessa gemma**: nasce dalla chiave, quindi se sono diverse la chiave e' arrivata sbagliata.
2. Prima che B apra Codex, guardate il gruppo su B: se la chiave non e' ancora arrivata deve dire
   "In attesa della chiave", non un errore.
3. Tutti e tre si scrivono. Ogni messaggio si apre da tutte le parti.
4. Da A: **Invita qualcuno**, mandate il link a un quarto telefono D (per messaggio, per email, come
   volete). Su D toccarlo deve aprire Codex sulla schermata gia' piena, **senza far entrare in
   niente**. "Chiedi di entrare", poi aprire Codex su A: la chiave parte, e su D compare il gruppo
   con dentro **i messaggi vecchi che il gruppo ha ancora sul server**.
5. Da A, tenendo premuto su C: **Togli dal gruppo**. Poi scrivete qualcosa.
   - Su B e D: si legge.
   - Su C: il messaggio nuovo **arriva chiuso e resta chiuso**, e quelli di prima si aprono ancora.
     E' il criterio di accettazione di M6, e va guardato proprio cosi': non "non arriva", ma "arriva
     e non si apre".
6. Da B: **Esci dal gruppo**. Su B nemmeno i messaggi vecchi si aprono piu' -- la chiave se ne va
   uscendo, ed e' voluto. Su A e D il gruppo continua, con una persona in meno.
7. Con A **offline**, provate a creare un gruppo: deve nascere subito e comparire nella lista. Le
   chiavi partono quando torna la rete; nel frattempo gli altri vedono "In attesa della chiave".
8. Un invito a un gruppo che chi ha invitato **ha lasciato**: non deve aprire niente.

### B4 · Le vicinanze (M7)

**Due emulatori bastano.** Nearby funziona anche fra due AVD sulla stessa macchina (passa da
Bluetooth virtuale): non servono per forza due telefoni, anche se due telefoni restano la prova che
conta -- l'emulatore non ha una radio vera e la sua ogni tanto risponde `STATUS_RADIO_ERROR`.

Fatto una volta il 2026-09-09 fra `codex_api35_pixel` e `Medium_Phone`, con esito buono.

1. Sui due dispositivi: identita' diverse, e scambio delle schede (QR o codice incollato). Poi il
   rito con **la stessa parola**: le due gemme devono essere uguali.
2. Su tutti e due: Io -> Vicinanze -> "Mentre Codex e' aperta", accettando i permessi. La
   spiegazione arriva **prima** della finestra di Android: se non la si vede, e' un difetto.
3. Aprite la conversazione: sotto il nome deve comparire **"Vicino"**. Se non compare entro una
   ventina di secondi, guardate `adb logcat | grep CodexNearby`: "vicino: <nome>" e' la stretta di
   mano riuscita.
4. **Togliete di mezzo il cloud.** Il modo piu' pulito e' che il dispositivo che riceve non abbia
   nessun account collegato: cosi' se il messaggio arriva, non puo' essere arrivato da li'. In
   alternativa, scollegate l'account da tutti e due.
5. Scrivete un messaggio. Deve comparire sull'altro dispositivo **sigillato**, aprirsi con il testo
   giusto, e la spunta sul primo deve diventare doppia (consegnato).
6. Mandate una **foto**: arriva sul canale dei file, e sull'altro telefono si apre. Con `run-as`,
   il file salvato comincia per `CDXM`.
7. Ricollegate la rete e gli account: il messaggio passa anche dal server e **non si sdoppia** --
   stesso identificatore, chi ce l'ha gia' non lo riprende.
8. Bloccate l'app (Io -> Blocca adesso): le vicinanze devono spegnersi. Sbloccando, devono ripartire
   da sole -- e' il difetto piu' insidioso di tutta la milestone, ed e' gia' capitato una volta.
9. Con "Sempre": il sistema mostra una notifica fissa finche' l'antenna e' accesa. Se non la mostra,
   il servizio non sta girando.

---

## C. Quello che serve un dito

- **Impronta o volto**: l'emulatore non ce l'ha. Sul tablet il passo dell'onboarding lo propone e va
  provato a mano, insieme al prompt all'avvio e al caso "impronta nuova registrata dopo", che deve
  invalidare la chiave e chiedere il segreto.

## D. Quello che serve una persona che non vede lo schermo

- Con TalkBack acceso, una conversazione deve essere percorribile: un sigillo chiuso si annuncia
  come "Messaggio chiuso in una roccia" con l'azione "Apri il sigillo", **senza dire il testo**.
- Le spunte di consegna hanno la loro descrizione.
- Con le animazioni di sistema disattivate, i sigilli si aprono lo stesso, in un lampo, e
  l'informazione non dipende mai dal movimento.
