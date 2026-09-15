#!/usr/bin/env bash
#
# The whole CI sequence, runnable on a developer machine.
#
# GitHub Actions is disabled account-wide, so this script IS the gate: .git/hooks/pre-push runs it
# (install with `bash validation/hooks/install.sh`), and `bash validation/ci_local.sh` runs it by
# hand. Keep it in step with .github/workflows/pr-build.yml.
set -euo pipefail

project_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$project_root"

if command -v python3 >/dev/null 2>&1; then
  py=python3
elif command -v python >/dev/null 2>&1; then
  py=python
else
  echo "ci_local: no python interpreter on PATH" >&2
  exit 1
fi

step() { printf '\n\033[1m== %s\033[0m\n' "$1"; }

# Said up front rather than left to Gradle, whose "Could not resolve" is easy to misread as a
# network problem.
cobbleraids_version="$(sed -n 's/^cobbleraids_version=//p' gradle.properties)"
m2="${HOME}/.m2/repository/com/cobbleraids/cobbleraids/${cobbleraids_version}"
if [ ! -d "$m2" ]; then
  echo "ci_local: CobbleRaids ${cobbleraids_version} is not in Maven Local (${m2})." >&2
  echo "ci_local: run 'gradlew publishToMavenLocal' in SnobblemonRaids at that version first." >&2
  exit 1
fi

step "Compile, test and remap"
./gradlew clean build --stacktrace --warning-mode all

# After the build on purpose: the check reads compiled bytecode.
step "Validate architecture boundary (bytecode)"
"$py" validation/validate_architecture.py

version="$(sed -n "s/^version = '\(.*\)'$/\1/p" build.gradle)"
jar="build/libs/CobbleTowers-${version}.jar"
if [ ! -f "$jar" ]; then
  echo "ci_local: missing $jar" >&2
  ls -la build/libs >&2 || true
  exit 1
fi

step "Validate architecture boundary (jar)"
"$py" validation/validate_architecture.py "$jar"

printf '\n\033[32mci_local: all checks passed for %s\033[0m\n' "$version"
