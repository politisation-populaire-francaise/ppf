#!/usr/bin/env bash
# Checks that a built APK really contains what the application needs at runtime, because a
# packaging mistake is invisible to the unit tests (the shared UI tests run on the JVM):
#   - the Compose resources (interface strings, bundled contents) as Android assets;
#   - the native libsecp256k1 for every supported architecture;
#   - no native library for an architecture we did not ask for (third-party libraries are checked
#     at the dependency level by tools/check-dependencies.sh).
# Usage: tools/check-apk.sh android/build/outputs/apk/debug/android-debug.apk
set -euo pipefail

apk="${1:-android/build/outputs/apk/debug/android-debug.apk}"
[ -f "$apk" ] || { echo "ERROR: APK not found: $apk" >&2; exit 1; }

entries="$(unzip -Z1 "$apk")"
status=0

require() {
    local pattern="$1" label="$2"
    if grep -qE "$pattern" <<<"$entries"; then
        echo "ok   $label"
    else
        echo "MISSING  $label (expected an entry matching: $pattern)" >&2
        status=1
    fi
}

forbid() {
    local pattern="$1" label="$2"
    if grep -qE "$pattern" <<<"$entries"; then
        echo "UNEXPECTED  $label" >&2
        status=1
    else
        echo "ok   no $label"
    fi
}

require '^AndroidManifest\.xml$' "manifest"
require '^assets/composeResources/[^/]+/values/strings[^/]*\.cvr$' "interface strings (Compose resources)"
require '^assets/composeResources/[^/]+/files/contents/fiches\.json$' "bundled contents (fiches.json)"
require '^assets/composeResources/[^/]+/files/contents/fil\.json$' "bundled feed posts (fil.json)"
require '^assets/composeResources/[^/]+/files/contents/revendications\.json$' "bundled demands (revendications.json)"
require '^assets/composeResources/[^/]+/font/atkinson_hyperlegible_regular\.ttf$' "bundled font (Atkinson Hyperlegible)"
for abi in arm64-v8a armeabi-v7a x86_64; do
    require "^lib/$abi/libsecp256k1-jni\.so$" "libsecp256k1 for $abi"
done
forbid '^lib/x86/' "32-bit x86 libraries"

if [ "$status" -ne 0 ]; then
    echo "ERROR: the APK is incomplete, see above." >&2
    exit 1
fi
echo "OK: $apk contains the resources and native libraries it needs."
