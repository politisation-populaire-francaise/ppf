#!/usr/bin/env bash
# Checks that a built APK really contains what the application needs at runtime, because a
# packaging mistake is invisible to the unit tests (the shared UI tests run on the JVM):
#   - the Compose resources (interface strings, bundled contents) as Android assets;
#   - the native libsecp256k1 for every supported architecture;
#   - no native library for an architecture we did not ask for (third-party libraries are checked
#     at the dependency level by tools/check-dependencies.sh).
# Optional checks for an APK that is sent to phones (they need the Android SDK build tools,
# found through ANDROID_HOME, ANDROID_SDK_ROOT or the default Windows location):
#   --not-debuggable       the APK must not be debuggable (a debuggable app lets anyone with a
#                          USB cable read its private files and attach a debugger);
#   --cert-sha256 <digest> the APK must be signed with exactly this certificate (SHA-256 of the
#                          certificate, as printed by "apksigner verify --print-certs"), so that
#                          it installs over the previous test version instead of being refused.
# Usage: tools/check-apk.sh <apk> [--not-debuggable] [--cert-sha256 <digest>]
set -euo pipefail

apk="${1:-android/build/outputs/apk/debug/android-debug.apk}"
[ "$#" -gt 0 ] && shift
not_debuggable=0
expected_cert=""
while [ "$#" -gt 0 ]; do
    case "$1" in
        --not-debuggable) not_debuggable=1; shift ;;
        --cert-sha256) expected_cert="$(tr 'A-F' 'a-f' <<<"${2:?--cert-sha256 needs a digest}" | tr -d ': ')"; shift 2 ;;
        *) echo "ERROR: unknown option $1" >&2; exit 2 ;;
    esac
done
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
require '^assets/composeResources/[^/]+/files/contents/parcours\.json$' "bundled courses (parcours.json)"
require '^assets/composeResources/[^/]+/files/contents/revendications\.json$' "bundled demands (revendications.json)"
require '^assets/composeResources/[^/]+/font/atkinson_hyperlegible_regular\.ttf$' "bundled font (Atkinson Hyperlegible)"
for abi in arm64-v8a armeabi-v7a x86_64; do
    require "^lib/$abi/libsecp256k1-jni\.so$" "libsecp256k1 for $abi"
done
forbid '^lib/x86/' "32-bit x86 libraries"

# Newest build-tools directory of the Android SDK, for aapt2 and apksigner.
build_tools() {
    local sdk
    for sdk in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "${LOCALAPPDATA:+$LOCALAPPDATA/Android/Sdk}"; do
        [ -n "$sdk" ] && [ -d "$sdk/build-tools" ] || continue
        local newest
        newest="$(ls -1 "$sdk/build-tools" | sort -V | tail -n 1)"
        [ -n "$newest" ] && { echo "$sdk/build-tools/$newest"; return 0; }
    done
    return 1
}

tool() {
    local dir="$1" name="$2"
    if [ -x "$dir/$name" ]; then echo "$dir/$name"
    elif [ -f "$dir/$name.exe" ]; then echo "$dir/$name.exe"
    elif [ -f "$dir/$name.bat" ]; then echo "$dir/$name.bat"
    else return 1
    fi
}

if [ "$not_debuggable" -eq 1 ] || [ -n "$expected_cert" ]; then
    bt="$(build_tools)" || { echo "ERROR: Android SDK build tools not found (set ANDROID_HOME)." >&2; exit 1; }
fi

if [ "$not_debuggable" -eq 1 ]; then
    aapt2="$(tool "$bt" aapt2)" || { echo "ERROR: aapt2 not found in $bt" >&2; exit 1; }
    badging="$("$aapt2" dump badging "$apk")"
    sed -n "s/^package: \(name='[^']*' versionCode='[^']*' versionName='[^']*'\).*/info \1/p" <<<"$badging"
    if grep -q '^application-debuggable' <<<"$badging"; then
        echo "UNEXPECTED  debuggable APK (this APK must not be sent to phones)" >&2
        status=1
    else
        echo "ok   not debuggable"
    fi
fi

if [ -n "$expected_cert" ]; then
    apksigner="$(tool "$bt" apksigner)" || { echo "ERROR: apksigner not found in $bt" >&2; exit 1; }
    certs="$("$apksigner" verify --print-certs "$apk" | tr -d '\r')"
    # Depending on its version, apksigner prints "Signer #1 certificate ..." or one line per
    # signature scheme ("V2 Signer: certificate ..."): keep the distinct certificate digests.
    digests="$(sed -nE 's/^(Signer #[0-9]+|V[0-9.]+ Signer( #[0-9]+)?):? certificate SHA-256 digest: ([0-9a-fA-F]+)$/\3/p' <<<"$certs" \
        | tr 'A-F' 'a-f' | sort -u)"
    signers="$(grep -c . <<<"$digests" || true)"
    actual_cert="$digests"
    if [ "$signers" = "1" ] && [ "$actual_cert" = "$expected_cert" ]; then
        echo "ok   signed with the expected certificate ($expected_cert)"
    else
        echo "UNEXPECTED  signing certificate: '$actual_cert' ($signers signer(s)), expected '$expected_cert'" >&2
        status=1
    fi
fi

if [ "$status" -ne 0 ]; then
    echo "ERROR: the APK is incomplete or wrongly built, see above." >&2
    exit 1
fi
echo "OK: $apk contains the resources and native libraries it needs."
