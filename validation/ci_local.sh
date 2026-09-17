#!/usr/bin/env bash
#
# The whole CI sequence, runnable on a developer machine.
#
# .git/hooks/pre-push runs it (install with `bash validation/hooks/install.sh`), and
# `bash validation/ci_local.sh` runs it by hand. Keep it in step with
# .github/workflows/build.yml.
#
# GitHub Actions was disabled account-wide while this repo was started, which is why the whole
# workflow is duplicated here. Actions came back on 2026-09-17, so this is now the pre-push gate
# rather than the only one -- but it stays, because it is faster than a push and because the first
# step below is one the workflow structurally cannot perform.
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

step "Check git can see every source file"
# A .gitignore pattern without a leading slash matches a directory of that name at ANY depth. The
# entry meant for the dev server's run/ directory also matched src/main/java/com/cobbletowers/run,
# hiding the entire run-state package -- while the build stayed green, because Gradle compiles what
# is on disk and does not care what git can see. A fresh clone would not have had the files.
#
# The GitHub workflow cannot catch this: it builds a clone, where an ignored file simply is not
# there. This check only ever bites here, before the push that would lose the file.
hidden="$(git ls-files --others --ignored --exclude-standard -- src/ validation/   | grep -Ev '(^|/)__pycache__/|[.]pyc$' || true)"
if [ -n "$hidden" ]; then
  echo "ci_local: git ignores these source files, so a commit would silently leave them behind:" >&2
  echo "$hidden" | sed 's/^/  /' >&2
  echo "ci_local: check .gitignore for an unanchored pattern; anchor it with a leading slash." >&2
  exit 1
fi

step "Validate the bundled tower definitions"
# Before the build: this reads the shipped JSON, and a dangling reference in our own content should
# fail here rather than be skipped at runtime by the registry's malformed-file safety net.
"$py" validation/validate_definitions.py

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

step "Validate public API boundary (bytecode)"
"$py" validation/validate_api_boundary.py

step "Validate persisted state holds no live references (bytecode)"
"$py" validation/validate_persistence.py

step "Validate architecture boundary (jar)"
"$py" validation/validate_architecture.py "$jar"

step "Validate public API boundary (jar)"
"$py" validation/validate_api_boundary.py "$jar"

step "Validate persisted state holds no live references (jar)"
"$py" validation/validate_persistence.py "$jar"

printf '\n\033[32mci_local: all checks passed for %s\033[0m\n' "$version"
