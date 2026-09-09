# Codex

Le versioni di Codex, dalla piu' recente. Quello che si legge qui e' quello che cambia **per chi
usa l'app**: il dettaglio tecnico sta nel diario del piano (`docs/PIANO.md`, §12).

<!-- nuove versioni qui sopra -->

## 1.0.0-beta.1 - 2026-09-09

La prima beta. Codex e' riscritta da zero: la vecchia 0.2.4 aveva una cifratura finta (uno XOR con
un generatore pseudocasuale) e mandava la chiave in chiaro attraverso il server. Di quella versione
resta l'idea — **i messaggi si aprono, non si leggono** — e niente altro.

### Come funziona

- **Ogni messaggio arriva chiuso in una forma**: rune da leggere, una roccia da spaccare, un quadro
  da guardare. Si apre con un tocco e resta aperto finche' la conversazione e' aperta. Tenendo
  premuto l'invio si sceglie la forma; di solito e' a sorpresa.
- **Cifratura vera, dal telefono al telefono**: X25519 per accordarsi, AES-256-GCM per il
  contenuto, Ed25519 per firmare chi sei. Le chiavi non passano dal server e non ci sono mai state:
  il server vede buste chiuse e non sa nemmeno se dentro c'e' una foto o una voce.
- **La tua identita' vive in questo telefono**, chiusa in un vault con il tuo PIN o la tua password
  (Argon2id) e, se vuoi, l'impronta. Non e' recuperabile: e' quello che la rende illeggibile a
  chiunque altro, noi compresi.
- **Ci si conosce guardandosi in faccia**: si scambia la scheda con un QR o un codice, e la chiave
  della conversazione nasce da una **parola d'ordine detta a voce**. Se la gemma che compare sui
  due schermi e' la stessa, nessuno si e' messo in mezzo.
- **Gruppi** con la chiave che cambia quando qualcuno esce: chi se ne va tiene quello che ha gia'
  letto e non legge quello che viene dopo.
- **Vicinanze**: se chi ti legge e' nella stanza, il messaggio passa da un telefono all'altro senza
  internet e senza server. Il nome che l'antenna annuncia cambia ogni giorno e non dice chi sei.
- **Foto e note vocali**, cifrate a blocchi e con la loro chiave; **visualizza una volta** con lo
  schermo protetto dagli screenshot; **messaggi a scadenza**; **storie** che durano un giorno.
- **Il quadro si puo' esportare**: e' l'unica forma che esce dall'app come immagine, con il
  messaggio nascosto dentro. Si manda con qualsiasi altra app, e Codex lo riapre.
- **Le origini**: da dove viene l'idea, con il cifrario che l'ha fatta nascere e un nomenclatore da
  provare.

### Cosa c'e' dentro l'APK

Trentasei riproduzioni di pubblico dominio dell'Art Institute of Chicago e il carattere delle rune
(Noto Sans Runic, OFL): si vedono senza rete, e nessun quadro sa che l'hai guardato. Tutti i
crediti sono in **Io -> Informazioni -> Crediti**.

### Quello che questa beta non fa ancora

- I suoni dei sigilli: si aprono in silenzio.
- Reazioni e ricerca dentro una conversazione.
- Il vault sul cloud: aprire Codex su un secondo telefono rifa' l'identita' da capo invece di
  riportarci quella che c'e' gia'.
