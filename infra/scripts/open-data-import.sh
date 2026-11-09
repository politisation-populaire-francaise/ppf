#!/usr/bin/env bash
# Runs the importer of the parliamentary open data (Voir, D-064) once, in its own one-shot container.
# Installed as /usr/local/sbin/ppf-open-data-import by bootstrap-server.sh and started by the timers
# ppf-open-data-daily@<environment> (--only an,senat,cc,an-barometre) and ppf-open-data-weekly@<environment> (--only pe);
# `cc` is the presentations of the presidential election (VO-37), asked only once PPF_SPONSORSHIPS_DATASET is set;
# `an-barometre` is the application of the laws (VO-36). A failure of either keeps their previous packages and never
# stops the import of the Assemblée and the Sénat:
#
#   ppf-open-data-import preproduction --only an,senat,cc,an-barometre
#   ppf-open-data-import production keygen      (once: creates the import key, prints its public key)
#
# Exit codes of the importer (docs/integration/voir-donnees-import.md): 0 done, 1 usage, 2 download
# failed, 3 refused by the guard (drop of more than 20 %), 4 unreadable source data, 5 key error. On
# any failure the previous set of packages is untouched.
set -euo pipefail

# shellcheck source=/dev/null
. /etc/ppf/deploy.conf   # PPF_DATA_DIR

[ "$(id -u)" -eq 0 ] || { echo "Run as root." >&2; exit 1; }
env="${1:-}"
case "$env" in production|preproduction) shift ;; *) echo "usage: $0 production|preproduction <importer arguments>|keygen" >&2; exit 2 ;; esac
image="$(cat "$PPF_DATA_DIR/$env/deployed-image" 2>/dev/null || true)"
revision="$(cat "$PPF_DATA_DIR/$env/deployed-revision" 2>/dev/null || true)"
release="$PPF_DATA_DIR/$env/releases/$revision"
[ -n "$image" ] && [ -d "$release/infra" ] || { echo "The $env is not deployed yet." >&2; exit 1; }
compose=(docker compose -f "$release/infra/$env/compose.yml" --env-file "$PPF_DATA_DIR/$env/.env")

if [ "${1:-}" = "keygen" ]; then
    key="$PPF_DATA_DIR/$env/secrets/import_key"
    [ ! -s "$key" ] || { echo "An import key already exists for $env: refusing to replace it." >&2; exit 1; }
    # The key is created inside the importer's container, written to a temporary bind mount owned by its
    # UID, then moved into place; only its public key is printed (to put into OpenDataKeys).
    tmp="$(mktemp -d)"
    # UID of the importer of this environment (compose.yml): 10003 in production, 10004 in preproduction.
    uid=10003; [ "$env" = preproduction ] && uid=10004
    chown "$uid:$uid" "$tmp"
    PPF_SERVER_IMAGE="$image" "${compose[@]}" run --rm --no-deps -T -v "$tmp:/keygen" open-data-importer keygen --out /keygen/import_key
    install -m 400 -o "$uid" -g "$uid" "$tmp/import_key" "$key"
    rm -rf "$tmp"
    exit 0
fi

PPF_SERVER_IMAGE="$image" "${compose[@]}" run --rm --no-deps -T open-data-importer "$@"
