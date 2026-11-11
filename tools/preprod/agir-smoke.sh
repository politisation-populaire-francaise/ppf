#!/usr/bin/env bash
# Read-only checks of « Agir » on a running server (preproduction container, or :server:runDev).
# Usage: bash tools/preprod/agir-smoke.sh http://127.0.0.1:8080
# Writes nothing on the server: GET requests only. Each line prints the HTTP status and the path.
set -u
BASE="${1:-http://127.0.0.1:8080}"
fail=0
check() {
  local expected="$1" path="$2"
  local status
  status=$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 "$BASE$path")
  if [ "$status" = "$expected" ]; then echo "OK   $status $path"; else echo "FAIL $status (attendu $expected) $path"; fail=1; fi
}
check 200 /health
check 200 "/api/v1/agenda"
check 200 "/api/v1/agenda.ics"
check 200 "/api/v1/agir/slots?call=30023:$(printf '0%.0s' {1..64}):inconnu"
check 400 "/api/v1/agir/slots?call=pas-une-adresse"
check 200 /agenda
check 200 /agir/appels
check 200 /organisations
check 200 /agir/s-engager
check 200 /agir/kits
check 200 "/agir/kits?but=greve"
check 200 /agir/solidarite
check 200 /agir/collectifs
check 200 "/recherche?q=greve"
check 404 "/agir/appels/$(printf '0%.0s' {1..64})/inconnu"
exit $fail
