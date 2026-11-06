#!/usr/bin/env bash
# Fails if any source, build or configuration file references a forbidden third party
# (trackers, analytics, advertising SDKs, CDNs, fonts, the abandoned project name).
# Documentation is not scanned: it legitimately names what is forbidden.
set -euo pipefail

cd "$(dirname "$0")/.."

patterns="tools/forbidden-patterns.txt"
scanned_dirs=(shared android ios desktop server web infra gradle .github tools)
scanned_root_files=(build.gradle.kts settings.gradle.kts gradle.properties)

status=0
for dir in "${scanned_dirs[@]}"; do
    [ -d "$dir" ] || continue
    if grep -rEin \
        --exclude-dir=build --exclude-dir=.gradle --exclude-dir=.kotlin \
        --exclude="forbidden-patterns.txt" --exclude="forbidden-dependency-groups.txt" \
        --exclude="*.lockfile" \
        -f "$patterns" "$dir"; then
        status=1
    fi
done
for file in "${scanned_root_files[@]}"; do
    [ -f "$file" ] || continue
    if grep -Ein -f "$patterns" "$file"; then
        status=1
    fi
done

if [ "$status" -ne 0 ]; then
    echo "ERROR: forbidden third-party reference found (see tools/forbidden-patterns.txt)." >&2
    exit 1
fi
echo "OK: no forbidden third party, tracker or CDN referenced."
