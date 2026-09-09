#!/usr/bin/env bash
# Prova le regole di Firestore e dello Storage contro gli emulatori, senza toccare il progetto vero.
#
#   bash tools/regole.sh
#
# Girano insieme perche' devono: le regole dello Storage chiedono a Firestore chi sta in una
# conversazione, e una regola che attraversa due servizi si puo' provare solo con tutti e due accesi.
#
# Perche' esiste uno script invece di un `npm test`: l'emulatore Firestore vuole **Java 21 o piu'**,
# e la macchina di sviluppo di Codex ha il 17 -- e' quello che vuole Gradle per Android, e cambiarlo
# romperebbe il build dell'app. Il 21 pero' c'e' gia': lo porta con se' Android Studio, in `jbr/`.
# Questo script lo trova e lo mette davanti nel PATH **solo per questo comando**, senza installare
# niente e senza spostare il JDK dell'app.
set -u
cd "$(dirname "$0")/.." || exit 1

trova_java_21() {
  local candidati=(
    "/c/Program Files/Android/Android Studio/jbr/bin"
    "$LOCALAPPDATA/Programs/Android Studio/jbr/bin"
    "$PROGRAMFILES/Eclipse Adoptium/jdk-21/bin"
  )
  for dir in "${candidati[@]}"; do
    if [ -x "$dir/java.exe" ] || [ -x "$dir/java" ]; then
      local versione
      versione=$("$dir/java" -version 2>&1 | head -1 | grep -oE '"[0-9]+' | tr -d '"')
      if [ -n "$versione" ] && [ "$versione" -ge 21 ]; then
        echo "$dir"
        return 0
      fi
    fi
  done
  # Se quello di sistema basta gia', va benissimo.
  local sistema
  sistema=$(java -version 2>&1 | head -1 | grep -oE '"[0-9]+' | tr -d '"')
  if [ -n "$sistema" ] && [ "$sistema" -ge 21 ]; then
    echo ""
    return 0
  fi
  return 1
}

BIN=$(trova_java_21) || {
  echo "Serve un JDK 21 o superiore per l'emulatore Firestore, e non ne ho trovato nessuno."
  echo "Ne porta uno Android Studio, in 'jbr'. Se sta altrove, aggiungilo a questo script."
  exit 1
}
[ -n "$BIN" ] && export PATH="$BIN:$PATH"

export PATH="$PATH:$APPDATA/npm"

if [ ! -d firebase/test/node_modules ]; then
  echo "Prima volta: installo le dipendenze dei test delle regole."
  (cd firebase/test && npm install --no-audit --no-fund) || exit 1
fi

cd firebase/test || exit 1
npm run emulate 2>&1 | grep -vE "GrpcConnection|false for '" | tail -25
