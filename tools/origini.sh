#!/usr/bin/env bash
# Installa la build e arriva a "Le origini", che sta in fondo alla scheda "Io".
#
# Come `pg.sh`: le voci si cercano per testo, cosi' lo stesso script vale sul telefono e sul tablet,
# in italiano e in inglese.
set -u
cd "$(dirname "$0")/.." || exit 1
S="bash tools/shot.sh"

$S install > /dev/null
sleep 4
$S sblocca "${1:-123456}"
sleep 3

$S tocca-testo "Io" > /dev/null 2>&1 || $S tocca-testo "Me" > /dev/null 2>&1
sleep 2

for _ in 1 2 3 4 5 6; do
  if $S trova "origini" > /dev/null 2>&1 || $S trova "Origins" > /dev/null 2>&1; then break; fi
  $S scorri 540 1800 500 400
  sleep 0.8
done
$S tocca-testo "origini" > /dev/null 2>&1 || $S tocca-testo "Origins" > /dev/null
sleep 3
echo "origini"
