#!/usr/bin/env bash
# Fails if a forbidden dependency group appears in the resolved dependency graph of
# the applications and the server (see tools/forbidden-dependency-groups.txt).
# Usage: tools/check-dependencies.sh  (uses ./gradlew; run after a build for speed)
set -euo pipefail

cd "$(dirname "$0")/.."

groups_file="tools/forbidden-dependency-groups.txt"
report="$(mktemp)"
trap 'rm -f "$report"' EXIT

./gradlew -q --console=plain \
    :android:dependencies --configuration releaseRuntimeClasspath \
    :desktop:dependencies --configuration runtimeClasspath \
    :server:dependencies --configuration runtimeClasspath > "$report"

if grep -E -f <(grep -v '^#' "$groups_file" | grep -v '^$') "$report"; then
    echo "ERROR: forbidden dependency group in the dependency graph (see $groups_file)." >&2
    exit 1
fi
echo "OK: no forbidden dependency group in the resolved dependencies."
