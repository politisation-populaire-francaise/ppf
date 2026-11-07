#!/usr/bin/env bash
# Seeds of the vote keys (Décider, D-110). Installed as /usr/local/sbin/ppf-vote-seed, run as root:
#
#   ppf-vote-seed rotate <environment>            adds the next version (urns opened afterwards use it)
#   ppf-vote-seed retire <environment> <version>  destroys an old version, once its last urns are closed
#
# Each version is a file of 32 random bytes in /srv/ppf/<environment>/vote_seeds/<version>, read-only
# for the server's UID; the server reads the directory (PPF_VOTE_SEEDS_DIR) and the version of each urn.
# Rotation: once a year, and at once in case of doubt. The seeds are part of the encrypted off-site
# backup; an offline copy kept by the owner is recommended. Restart the server after a change.
set -euo pipefail

# shellcheck source=/dev/null
. /etc/ppf/deploy.conf   # PPF_DATA_DIR

fail() { echo "ERROR: $*" >&2; exit 1; }
[ "$(id -u)" -eq 0 ] || fail "run as root"
command="${1:-}"
env="${2:-}"
case "$env" in production|preproduction) ;; *) fail "usage: ppf-vote-seed rotate|retire <environment> [version]" ;; esac
dir="$PPF_DATA_DIR/$env/vote_seeds"
[ -d "$dir" ] || fail "no seed directory for $env (run bootstrap-server.sh)"
owner="$(stat -c %u "$dir")"

case "$command" in
    rotate)
        last="$(find "$dir" -maxdepth 1 -type f -regex '.*/[0-9]+' -printf '%f\n' | sort -n | tail -1)"
        next=$(( ${last:-0} + 1 ))
        (umask 077; head -c 32 /dev/urandom > "$dir/$next")
        chown "$owner:$owner" "$dir/$next"
        chmod 400 "$dir/$next"
        echo "Seed version $next created for $env; restart the server (ppf-deploy $env) so that new urns use it."
        ;;
    retire)
        version="${3:-}"
        [[ "$version" =~ ^[0-9]+$ ]] || fail "give the version to retire"
        [ -f "$dir/$version" ] || fail "no seed version $version in $env"
        count="$(find "$dir" -maxdepth 1 -type f -regex '.*/[0-9]+' | wc -l)"
        [ "$count" -gt 1 ] || fail "refusing to destroy the only seed of $env"
        shred -u "$dir/$version"
        echo "Seed version $version of $env destroyed."
        ;;
    *)
        fail "usage: ppf-vote-seed rotate|retire <environment> [version]"
        ;;
esac
