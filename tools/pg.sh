#!/usr/bin/env bash
# Installa la build e arriva al Playground, che sta in fondo alla scheda "Io".
#
# Il giro -- sblocca, terza scheda, scorri, tocca -- si rifa' a ogni modifica di un'animazione, e
# sbagliarne un passo significa fotografare la schermata sbagliata.
#
# **Niente coordinate fisse dove si puo' evitarlo.** Le voci si cercano per testo, cosi' lo stesso
# script funziona sul telefono e sul tablet, in italiano e in inglese. Le sole coordinate fisse sono
# quelle del tastierino, che l'app disegna da se'.
set -u
cd "$(dirname "$0")/.." || exit 1
S="bash tools/shot.sh"

$S install > /dev/null
sleep 4
$S sblocca "${1:-123456}"
sleep 3

# La scheda "Io": si chiama cosi' in italiano e "Me" in inglese.
$S tocca-testo "Io" > /dev/null 2>&1 || $S tocca-testo "Me" > /dev/null 2>&1
sleep 2

# Il Playground sta in fondo: si scorre finche' non compare.
for _ in 1 2 3 4 5 6; do
  if $S trova "Playground" > /dev/null 2>&1; then break; fi
  $S scorri 540 1800 500 400
  sleep 0.8
done
$S tocca-testo "Playground" > /dev/null
sleep 3
echo "playground"
