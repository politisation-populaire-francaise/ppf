#!/usr/bin/env bash
# Runs the importer of the parliamentary open data (Voir, D-064) once, in the production, in its own
# one-shot container. Installed as /usr/local/sbin/ppf-open-data-import by bootstrap-server.sh and
# started by the timers ppf-open-data-daily (--only an,senat) and ppf-open-data-weekly (--only pe):
#
#   ppf-open-data-import --only an,senat
#
# Exit codes of the importer (docs/integration/voir-donnees-import.md): 0 done, 1 usage, 2 download
# failed, 3 refused by the guard (drop of more than 20 %), 4 unreadable source data, 5 key error. On
# any failure the previous set of packages is untouched. The preproduction never runs it: it serves
# the example packages of the image.
set -euo pipefail

# shellcheck source=/dev/null
. /etc/ppf/deploy.conf   # PPF_DATA_DIR

[ "$(id -u)" -eq 0 ] || { echo "Run as root." >&2; exit 1; }
image="$(cat "$PPF_DATA_DIR/production/deployed-image" 2>/dev/null || true)"
revision="$(cat "$PPF_DATA_DIR/production/deployed-revision" 2>/dev/null || true)"
release="$PPF_DATA_DIR/production/releases/$revision"
[ -n "$image" ] && [ -d "$release/infra" ] || { echo "The production is not deployed yet." >&2; exit 1; }

PPF_SERVER_IMAGE="$image" docker compose -f "$release/infra/production/compose.yml" \
    --env-file "$PPF_DATA_DIR/production/.env" \
    run --rm --no-deps -T open-data-importer "$@"
