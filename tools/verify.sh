#!/usr/bin/env bash
# Compila, prova, controlla, e riassume tutto in poche righe.
#
# E' il cancello prima di dire "e' a posto": build, test di unita', Android Lint e `engine-doctor`.
# Ognuno dei quattro ha gia' fermato qualcosa di vero -- lint un file di proprieta' scritto male,
# doctor una modifica dentro `engine/` che sarebbe sparita al primo aggiornamento -- quindi vale la
# pena aspettare il minuto che ci mette.
#
#   bash tools/verify.sh          tutto
#   bash tools/verify.sh veloce   solo build e test, per iterare
#
# Le cartelle di build stanno fuori dal progetto (vedi il commento in build.gradle.kts: Google Drive
# specchia `C:\VibeCoded Projects` e teneva aperti i file appena scritti). Questo script chiede al
# build dove sono invece di indovinarlo.
set -u
cd "$(dirname "$0")/.." || exit 1

VELOCE=${1:-}
# `:core:crypto` e' un modulo JVM puro: non ha `testDebugUnitTest`, quindi va chiamato per nome.
#
# Senza questa riga il cancello non provava **la crittografia**, cioe' l'unico modulo in cui un
# difetto non si vede a schermo. Peggio: il riassunto contava lo stesso i suoi test, perche' legge i
# referti rimasti su disco dall'ultima volta che erano stati eseguiti a mano. Diceva "125 test, 0
# falliti" mentre cinquantasei di quelli non erano stati eseguiti.
TASKS=(:app:assembleDebug testDebugUnitTest :core:crypto:test)
if [ "$VELOCE" != "veloce" ]; then
  TASKS+=(:app:lintDebug)
fi
LOG=build-verify.log

build_root() {
  # Il valore va de-escapato: in un file di proprieta' i due punti dell'unita' si scrivono `C\:/...`.
  local from_local
  from_local=$(python -c "import io;print(next((l.split(chr(61),1)[1].strip().replace(chr(92),'') for l in io.open('local.properties',encoding='utf-8') if l.startswith('codex.buildDir=')),''))" 2>/dev/null)
  echo "${from_local:-${TEMP:-/tmp}/codex-build}"
}

./gradlew.bat --console=plain --no-daemon "${TASKS[@]}" > "$LOG" 2>&1
status=$?

grep -E "^e: " "$LOG" | sed 's|file:///C:/VibeCoded%20Projects/Codex/||' | head -30
grep -v "^w: " "$LOG" | grep -E "BUILD (SUCCESSFUL|FAILED)|What went wrong" -A3 | head -8
grep -E "Lint found|lint-results" "$LOG" | head -3

ROOT=$(build_root)

# I test: solo quelli di Codex. Quelli dell'engine sono verdi per conto loro e li conta il suo repo.
python - "$ROOT" <<'PY'
import os, sys, xml.etree.ElementTree as E
root = sys.argv[1]
seen = {}
for dirpath, _, files in os.walk(root):
    for f in files:
        if f.startswith('TEST-dev.pampa') and f.endswith('.xml'):
            t = E.parse(os.path.join(dirpath, f)).getroot()
            seen[t.get('name')] = (
                int(t.get('tests')),
                int(t.get('failures')) + int(t.get('errors') or 0),
            )
falliti = sum(v[1] for v in seen.values())
print('test: %d eseguiti, %d falliti (%d classi)' % (
    sum(v[0] for v in seen.values()), falliti, len(seen)))
for nome, (n, f) in sorted(seen.items()):
    if f:
        print('  ROSSO  %s: %d su %d' % (nome, f, n))
PY

if [ "$VELOCE" != "veloce" ]; then
  powershell -ExecutionPolicy Bypass -File engine/tools/engine-doctor.ps1 -AppRoot . 2>&1 | tail -2
fi

apk="$ROOT/app/outputs/apk/debug/app-debug.apk"
[ -f "$apk" ] && echo "apk: $apk ($(du -m "$apk" | cut -f1) MB)"
exit "$status"
