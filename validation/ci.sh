#!/usr/bin/env bash
set -euo pipefail

# Provider-independent CobbleTowers validation entrypoint.
# Optional first argument: exact remapped CobbleRaids JAR to compile against.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

pass() { printf '[PASS] %s\n' "$1"; }
run() {
  local label="$1"; shift
  printf '\n==> %s\n' "$label"
  "$@"
  pass "$label"
}

run "CobbleTowers architecture boundary" python3 validation/validate_architecture.py

if [[ $# -gt 0 ]]; then
  COBBLERAIDS_JAR="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
  test -f "$COBBLERAIDS_JAR" || { echo "[FAIL] CobbleRaids JAR not found: $COBBLERAIDS_JAR" >&2; exit 1; }
  run "CobbleTowers compile/tests against exact CobbleRaids JAR" \
    gradle --no-daemon clean build --stacktrace --warning-mode all -Pcobbleraids_jar="$COBBLERAIDS_JAR"
else
  run "CobbleTowers standalone compile/tests" \
    gradle --no-daemon clean build --stacktrace --warning-mode all
fi

mapfile -t jars < <(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-sources.jar' | sort)
if [[ ${#jars[@]} -eq 0 ]]; then
  echo '[FAIL] CobbleTowers build produced no runtime JAR.' >&2
  exit 1
fi

printf '\nCOBBLETOWERS VALIDATION: PASSED\n'
printf 'Runtime JAR: %s\n' "${jars[0]}"
