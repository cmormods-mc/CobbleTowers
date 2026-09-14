#!/usr/bin/env bash
set -euo pipefail

# Full local integration validation.
# Usage:
#   ./validation/full-integration.sh /path/to/SnobblemonRaids
#
# The supplied CobbleRaids checkout is built exactly as-is. CobbleTowers then compiles
# against the remapped runtime JAR produced by that same validation run.

TOWER_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RAIDS_ROOT="${1:-}"

if [[ -z "$RAIDS_ROOT" ]]; then
  echo 'Usage: validation/full-integration.sh /path/to/SnobblemonRaids' >&2
  exit 2
fi
RAIDS_ROOT="$(cd "$RAIDS_ROOT" && pwd)"

test -f "$RAIDS_ROOT/validation/ci.sh" || {
  echo "[FAIL] CobbleRaids validation/ci.sh not found under $RAIDS_ROOT" >&2
  exit 2
}

printf '=== CobbleRaids validation ===\n'
bash "$RAIDS_ROOT/validation/ci.sh"

version=$(sed -n "s/^version = '\(.*\)'$/\1/p" "$RAIDS_ROOT/build.gradle")
RAIDS_JAR="$RAIDS_ROOT/build/libs/CobbleRaids-${version}.jar"
test -f "$RAIDS_JAR" || {
  echo "[FAIL] Expected CobbleRaids runtime JAR was not produced: $RAIDS_JAR" >&2
  exit 1
}

printf '\n=== CobbleTowers validation against exact CobbleRaids artifact ===\n'
bash "$TOWER_ROOT/validation/ci.sh" "$RAIDS_JAR"

printf '\n========================================\n'
printf '[PASS] CobbleRaids validation suite\n'
printf '[PASS] CobbleRaids runtime JAR\n'
printf '[PASS] CobbleRaids public API boundary\n'
printf '[PASS] CobbleTowers architecture boundary\n'
printf '[PASS] CobbleTowers compile against exact CobbleRaids JAR\n'
printf '[PASS] CobbleTowers tests and runtime JAR\n'
printf 'FULL INTEGRATION VALIDATION: PASSED\n'
printf '========================================\n'
