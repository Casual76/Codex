# Le impronte da registrare in Firebase

Il `google-services.json` del 2026-09-07 è valido e la build lo usa. Manca però una cosa, e senza
quella **l'accesso con Google non può funzionare**: nel file non c'è nessun `oauth_client`.

Non è un errore del file. Firebase mette gli `oauth_client` nel JSON solo quando l'app Android ha
almeno un'**impronta del certificato di firma** registrata. Finché non ce n'è nessuna, il file esce
senza, l'app non ottiene il `default_web_client_id` e il pulsante "Accedi con Google" non ha nulla a
cui agganciarsi.

## Cosa fare, in console

Firebase console → **Impostazioni progetto** (l'ingranaggio) → scheda **Generali** → in fondo, la
sezione **Le tue app**. Ci sono due app Android, e le impronte vanno messe **in tutte e due**.

Per `dev.pampa.codex.debug` (le build di lavoro, quelle che gira sull'emulatore e sul tablet):

```
SHA-1    80:1A:2D:0F:AC:4A:8B:07:89:96:0C:76:DC:48:88:EB:6F:87:A4:E4
SHA-256  D7:A4:09:A3:68:6E:E6:45:44:9D:08:74:1E:80:15:AF:3F:2F:EF:43:60:58:16:7B:0B:86:B6:00:3E:12:49:7E
```

Per `dev.pampa.codex` (la release firmata con `pampa.jks`, quella che finirà sul Pampa Store):

```
SHA-1    55:C6:ED:74:0C:A2:7D:6D:7B:A4:97:15:11:24:06:80:3D:AF:CB:EB
SHA-256  82:57:38:50:0C:E9:60:54:29:FF:BA:3C:40:19:22:8F:99:2F:2C:37:D1:B7:0A:22:30:A7:CE:2C:25:63:7E:43
```

Poi, sempre in console: **Authentication → Sign-in method → Google → Attiva**.

Infine **riscarica `google-services.json`** e rimettilo in `app/`. Si riconosce che è quello giusto
perché dentro compare `oauth_client` con delle voci, invece che una lista vuota.

## Da dove vengono queste impronte

Non le ho inventate: sono state lette dalle chiavi che ci sono su questa macchina.

- Quella di debug viene da `~/.android/debug.keystore`, la chiave che Android Studio crea da sé.
- Quella di release viene dall'APK di release appena compilato, firmato con `pampa.jks`
  (`CN=Pampa, O=PampaStore, C=IT`). La chiave viene trovata da sola tramite il
  `keystore.properties` di universal_converter: **per Codex non serve crearne uno**.
