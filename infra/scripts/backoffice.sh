#!/usr/bin/env bash
# Command line of the team back-office on the server. Installed as /usr/local/sbin/ppf-backoffice by
# bootstrap-server.sh, run as root:
#
#   ppf-backoffice preproduction bootstrap <pseudonyme>
#   ppf-backoffice production bootstrap <pseudonyme>
#
# `bootstrap` creates the invitation of a super administrator and prints its link once (valid 48 hours,
# to open through the WireGuard tunnel). It works only while fewer than two super administrators exist
# (docs/integration/back-office.md, section 4). The link is a one-time secret: it is written to a file
# readable by root only, never kept in a shell history, and the file is deleted once the invitation is
# used.
#
# The command runs in a one-shot container of the image deployed in that environment, with its
# settings and secrets, so that it never takes memory from the running server.
set -euo pipefail

# shellcheck source=/dev/null
. /etc/ppf/deploy.conf   # PPF_DATA_DIR

fail() { echo "ERROR: $*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || fail "run as root (sudo ppf-backoffice ...)"
env="${1:-}"
command="${2:-}"
pseudonym="${3:-}"
case "$env" in production|preproduction) ;; *) fail "usage: ppf-backoffice production|preproduction bootstrap <pseudonyme>" ;; esac
[ "$command" = "bootstrap" ] || fail "usage: ppf-backoffice $env bootstrap <pseudonyme>"
[ -n "$pseudonym" ] || fail "give the pseudonym of the first administrator"

image="$(cat "$PPF_DATA_DIR/$env/deployed-image" 2>/dev/null || true)"
revision="$(cat "$PPF_DATA_DIR/$env/deployed-revision" 2>/dev/null || true)"
release="$PPF_DATA_DIR/$env/releases/$revision"
[ -n "$image" ] && [ -d "$release/infra" ] || fail "$env is not deployed yet (ppf-deploy $env)"

out_dir=/root/ppf-backoffice
install -d -m 700 "$out_dir"
out_file="$out_dir/$env-invitation.txt"
umask 077
PPF_SERVER_IMAGE="$image" docker compose -f "$release/infra/$env/compose.yml" \
    --env-file "$PPF_DATA_DIR/$env/.env" \
    run --rm --no-deps -T --entrypoint java server \
    -Xmx128m -cp /app/ppf-server.jar \
    org.politisationpopulairefrancaise.server.backoffice.BackOfficeCli bootstrap "$pseudonym" > "$out_file"
echo "Invitation written to $out_file (root only). Give it to the person, then delete the file."
