#!/usr/bin/env bash
# Installa la build di debug su un dispositivo e la avvia, poi mette a disposizione qualche comodita':
#
#   bash tools/shot.sh install            installa e avvia
#   bash tools/shot.sh cattura nome       salva uno screenshot in tools/frames-out/nome.png
#   bash tools/shot.sh tocca x y          tocca (coordinate del dispositivo)
#   bash tools/shot.sh premi x y          pressione lunga
#   bash tools/shot.sh trova testo        le coordinate di chi contiene quel testo
#   bash tools/shot.sh tocca-testo testo  trova e tocca in un colpo solo
#   bash tools/shot.sh sblocca [pin]      digita il PIN sulla serratura (solo sul telefono)
#   bash tools/shot.sh scorri x y1 y2     trascina da y1 a y2
#   bash tools/shot.sh dispositivi        cosa c'e' attaccato, e quale sto usando
#   bash tools/shot.sh log                gli errori dell'app nel logcat
#
# **Quale dispositivo.** Se ne e' attaccato uno solo si usa quello. Se ce n'e' piu' di uno lo
# script si ferma e li elenca: su questa macchina passano il telefono personale dell'utente, il
# tablet e degli emulatori che non sono del progetto, e sceglierne uno a caso vuol dire installare
# roba sul telefono di qualcuno. Si dice quale con `CODEX_DEVICE`:
#
#   CODEX_DEVICE=R52X50DKRBW bash tools/shot.sh install   # il tablet
#   CODEX_DEVICE=emulator-5554 bash tools/shot.sh install # l'emulatore del progetto
#
# Le coordinate sono quelle vere del dispositivo. Uno screenshot preso dagli strumenti di lettura
# viene mostrato ridotto: sul telefono da 1080x2400 si moltiplica per 1.2.
set -u
cd "$(dirname "$0")/.." || exit 1

ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"

# **Quale dispositivo, e perche' non si indovina.**
#
# La regola di prima era "prendi il primo che non sia `emulator-5556`". Ha funzionato finche' gli
# unici attaccati erano roba di lavoro; il giorno in cui e' comparso il **telefono personale**
# dell'utente, questo script ci ha installato sopra l'app e ci ha mandato dei tocchi, dentro un'app
# che non era la sua. Un difetto che non e' nel codice dell'app e che ha toccato una cosa vera.
#
# Adesso: se non c'e' `CODEX_DEVICE` e ne trova piu' di uno, si ferma e chiede. Scegliere per conto
# proprio quale telefono di qualcun altro toccare non e' una comodita' che valga il rischio.
DEVICE="${CODEX_DEVICE:-}"
if [ -z "$DEVICE" ]; then
  CANDIDATI=$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')
  QUANTI=$(echo "$CANDIDATI" | grep -c .)
  if [ "$QUANTI" -gt 1 ]; then
    echo "Attaccati piu' dispositivi. Scegli tu quale, non lo indovino:" >&2
    "$ADB" devices -l | awk 'NR>1 && NF' >&2
    echo >&2
    echo "  CODEX_DEVICE=<seriale> bash tools/shot.sh $*" >&2
    exit 2
  fi
  DEVICE=$(echo "$CANDIDATI" | head -1)
fi
if [ -z "$DEVICE" ]; then
  echo "Nessun dispositivo attaccato." >&2
  exit 2
fi
PACKAGE=dev.pampa.codex.debug
ACTIVITY=dev.pampa.codex.MainActivity
OUT=tools/frames-out
mkdir -p "$OUT"

adb_() { "$ADB" -s "$DEVICE" "$@"; }

build_root() {
  # Il valore va **de-escapato**: in un file di proprieta' i due punti dell'unita' si scrivono
  # `C\:/...`, e passando quella stringa ad adb si cerca un percorso che non esiste.
  local from_local
  from_local=$(python -c "import io;print(next((l.split(chr(61),1)[1].strip().replace(chr(92),'') for l in io.open('local.properties',encoding='utf-8') if l.startswith('codex.buildDir=')),''))" 2>/dev/null)
  echo "${from_local:-${TEMP:-/tmp}/codex-build}"
}

case "${1:-}" in
  dispositivi)
    "$ADB" devices -l
    echo "in uso: $DEVICE"
    ;;
  install)
    adb_ wait-for-device
    adb_ install -r "$(build_root)/app/outputs/apk/debug/app-debug.apk" | tail -1
    adb_ shell am start -n "$PACKAGE/$ACTIVITY" > /dev/null
    ;;
  reinstalla)
    adb_ uninstall "$PACKAGE" > /dev/null 2>&1
    adb_ install -r "$(build_root)/app/outputs/apk/debug/app-debug.apk" | tail -1
    adb_ shell am start -n "$PACKAGE/$ACTIVITY" > /dev/null
    ;;
  cattura)
    adb_ exec-out screencap -p > "$OUT/${2:-shot}.png"
    echo "$OUT/${2:-shot}.png"
    ;;
  tocca)
    adb_ shell input tap "$2" "$3"
    ;;
  sblocca)
    # Il PIN della build di prova. La tastiera numerica e' disegnata da noi, quindi le coordinate
    # sono fisse -- ma solo sullo schermo del telefono da 1080x2400. Altrove si usa `tocca-testo`.
    pin=${2:-123456}
    for (( i=0; i<${#pin}; i++ )); do
      case "${pin:$i:1}" in
        1) x=302; y=1136 ;; 2) x=540; y=1136 ;; 3) x=778; y=1136 ;;
        4) x=302; y=1354 ;; 5) x=540; y=1354 ;; 6) x=778; y=1354 ;;
        7) x=302; y=1568 ;; 8) x=540; y=1568 ;; 9) x=778; y=1568 ;;
        0) x=540; y=1784 ;; *) continue ;;
      esac
      adb_ shell input tap "$x" "$y"
      sleep 0.25
    done
    ;;
  trova)
    # Dove sta, sullo schermo, l'elemento che contiene un certo testo.
    #
    # Prendere le coordinate a occhio da uno screenshot e poi toccarle e' il modo piu' rapido di
    # toccare la cosa sbagliata: basta che la lista si sia mossa di trenta pixel e il tocco finisce
    # sulla riga accanto. L'albero di accessibilita' sa dove sono le cose davvero.
    # `MSYS_NO_PATHCONV`: Git Bash trasforma `/sdcard/...` in un percorso di Windows prima ancora
    # che adb lo veda, e il dump finiva in "Files/Git/sdcard/...", cioe' da nessuna parte. Il
    # sintomo era "dump illeggibile", che non fa pensare a questo nemmeno lontanamente.
    MSYS_NO_PATHCONV=1 adb_ shell uiautomator dump /sdcard/codex-ui.xml > /dev/null 2>&1
    MSYS_NO_PATHCONV=1 adb_ exec-out cat /sdcard/codex-ui.xml 2>/dev/null > "$OUT/ui.xml"
    python tools/trova.py "$OUT/ui.xml" "$2"
    ;;
  tocca-testo)
    coords=$(bash "$0" trova "$2" | head -1)
    if [ -z "$coords" ]; then
      echo "non trovato: $2"
      exit 1
    fi
    echo "tocco $coords"
    adb_ shell input tap $coords
    ;;
  premi)
    # Pressione lunga: `input swipe` sullo stesso punto, tenuto abbastanza da far scattare il
    # gesto lungo di Compose (la soglia sta sotto il mezzo secondo).
    adb_ shell input swipe "$2" "$3" "$2" "$3" "${4:-700}"
    ;;
  scorri)
    adb_ shell input swipe "$2" "$3" "$2" "$4" "${5:-300}"
    ;;
  testo)
    adb_ shell input text "$2"
    ;;
  log)
    adb_ logcat -d 2>/dev/null | grep -E "FATAL|E AndroidRuntime|E Codex" | head -20
    ;;
  pulisci-log)
    adb_ logcat -c
    ;;
  *)
    echo "uso: dispositivi | install | reinstalla | cattura <nome> | tocca <x> <y> |" \
      "tocca-testo <t> | trova <t> | premi <x> <y> | scorri <x> <y1> <y2> | testo <t> |" \
      "sblocca [pin] | log"
    echo "dispositivo: CODEX_DEVICE=<seriale> (adesso: $DEVICE)"
    ;;
esac
