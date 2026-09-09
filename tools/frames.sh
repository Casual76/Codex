#!/usr/bin/env bash
# Registra un'animazione dal dispositivo e ne fa un provino a contatto.
#
# Un'animazione non si giudica da uno screenshot: fra il tocco e la cattura passano centinaia di
# millisecondi, e quello che si vede e' quasi sempre la fine. Questo registra lo schermo, estrae i
# fotogrammi **senza reinterpretarne la cadenza** (`-fps_mode passthrough`: `fps=30` su un video a
# frequenza variabile riordina e duplica, e una cronologia sbagliata porta a conclusioni sbagliate)
# e ne affianca trenta in un'unica immagine, che e' l'unico modo per guardarli tutti insieme.
#
#   bash tools/frames.sh <nome> <x> <y> [secondi] [ritaglio]
#
# `ritaglio` e' un'espressione di ffmpeg (w:h:x:y); senza, prende tutto lo schermo.
set -u
cd "$(dirname "$0")/.." || exit 1

NAME=${1:?serve un nome}
TAP_X=${2:?servono le coordinate del tocco}
TAP_Y=${3:?servono le coordinate del tocco}
SECONDS_TO_RECORD=${4:-3}
CROP=${5:-}

ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
# Quale dispositivo: vedi la nota in tools/shot.sh.
DEVICE="${CODEX_DEVICE:-}"
if [ -z "$DEVICE" ]; then
  DEVICE=$("$ADB" devices | awk 'NR>1 && $2=="device" && $1!="emulator-5556" {print $1; exit}')
fi
# Git Bash traduce i percorsi che cominciano con / in percorsi Windows prima di passarli a un
# programma nativo: senza questo, adb riceve "C:/Program Files/Git/sdcard/..." e non trova niente.
export MSYS_NO_PATHCONV=1
OUT="tools/frames-out/$NAME"
rm -rf "$OUT"
mkdir -p "$OUT"

DELAY=0.6

"$ADB" -s "$DEVICE" shell screenrecord --time-limit "$SECONDS_TO_RECORD" --bit-rate 16000000 /sdcard/codex-cap.mp4 &
RECORDER=$!
sleep "$DELAY"
"$ADB" -s "$DEVICE" shell input tap "$TAP_X" "$TAP_Y"
wait $RECORDER
"$ADB" -s "$DEVICE" pull /sdcard/codex-cap.mp4 "$OUT/capture.mp4" > /dev/null 2>&1
"$ADB" -s "$DEVICE" shell rm /sdcard/codex-cap.mp4

# I trenta fotogrammi che partono un attimo prima del tocco, in ordine. `-ss` lavora sul tempo del
# video e non sulla numerazione dei file, quindi il taglio resta giusto anche a cadenza variabile.
if [ -n "$CROP" ]; then
  ffmpeg -loglevel error -y -ss 0.45 -i "$OUT/capture.mp4" -fps_mode passthrough -frames:v 30 \
    -vf "crop=$CROP" "$OUT/s-%03d.png"
else
  ffmpeg -loglevel error -y -ss 0.45 -i "$OUT/capture.mp4" -fps_mode passthrough -frames:v 30 \
    "$OUT/s-%03d.png"
fi

COUNT=$(ls "$OUT"/s-*.png 2>/dev/null | wc -l)
echo "fotogrammi nel provino: $COUNT"

ffmpeg -loglevel error -y -i "$OUT/s-%03d.png" -frames:v 1 \
  -vf "scale=300:-1,tile=6x5:margin=6:padding=4:color=0x101014" \
  "$OUT/provino.png"

if [ -f "$OUT/provino.png" ]; then
  echo "provino: $OUT/provino.png"
else
  echo "provino non creato"
fi
