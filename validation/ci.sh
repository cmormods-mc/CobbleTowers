#!/usr/bin/env bash
set -euo pipefail

# Provider-independent CobbleTowers validation entrypoint.
# Required first argument: exact remapped CobbleRaids JAR to compile against.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

GRADLE=(gradle)
if [[ -x ./gradlew ]]; then GRADLE=(./gradlew); fi

pass() { printf '[PASS] %s\n' "$1"; }
run() {
  local label="$1"; shift
  printf '\n==> %s\n' "$label"
  "$@"
  pass "$label"
}

run "CobbleTowers architecture boundary" python3 validation/validate_architecture.py
run "CobbleTowers V1 dimension/gameplay contract" python3 validation/validate_tower_contract.py

if [[ $# -lt 1 ]]; then
  echo '[FAIL] CobbleTowers now imports the typed CobbleRaids public API.' >&2
  echo 'Usage: validation/ci.sh /path/to/CobbleRaids-runtime.jar' >&2
  exit 2
fi

COBBLERAIDS_JAR="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
test -f "$COBBLERAIDS_JAR" || { echo "[FAIL] CobbleRaids JAR not found: $COBBLERAIDS_JAR" >&2; exit 1; }

run "CobbleTowers compile/tests against exact CobbleRaids JAR" \
  "${GRADLE[@]}" --no-daemon clean build --stacktrace --warning-mode all -Pcobbleraids_jar="$COBBLERAIDS_JAR"

mapfile -t jars < <(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-sources.jar' | sort)
if [[ ${#jars[@]} -eq 0 ]]; then
  echo '[FAIL] CobbleTowers build produced no runtime JAR.' >&2
  exit 1
fi

printf '\nCOBBLETOWERS VALIDATION: PASSED\n'
printf 'CobbleRaids API JAR: %s\n' "$COBBLERAIDS_JAR"
printf 'Runtime JAR: %s\n' "${jars[0]}"
