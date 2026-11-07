#!/usr/bin/env bash
# Deploys PPF on the server. Installed as /usr/local/sbin/ppf-deploy by bootstrap-server.sh,
# run as root (or ppf-admin with sudo):
#
#   ppf-deploy preproduction [--if-changed]   newest image of main, signature checked
#   ppf-deploy production --from-preproduction --confirm
#   ppf-deploy production sha256:<digest> --confirm
#   ppf-deploy status
#
# Chain of trust: the image is built and signed (cosign, keyless) by the continuous integration
# on the main branch only; this script refuses any image whose signature does not name that
# workflow. The image carries the commit it was built from; the Compose and Caddy files of that
# very commit are extracted from the public repository (content-addressed by Git) and used to
# start it. Production accepts only a digest that has already run on the preproduction
# (validation by the owner), and saves its database first.
set -euo pipefail

readonly OIDC_ISSUER="https://token.actions.githubusercontent.com"
readonly KEEP_RELEASES=5

# shellcheck source=/dev/null
. /etc/ppf/deploy.conf   # PPF_GITHUB PPF_IMAGE_REPO PPF_SIGNER_IDENTITY PPF_REPO_DIR PPF_DATA_DIR

usage() {
    sed -n '2,9p' "$0" >&2
    exit 2
}

fail() { echo "ERROR: $*" >&2; exit 1; }
say() { echo "== $*"; }

[ "$(id -u)" -eq 0 ] || fail "run as root (sudo ppf-deploy ...)"

# One deployment at a time (the timer of the preproduction included).
exec 9>/run/lock/ppf-deploy.lock
flock -n 9 || fail "another deployment is running"

# --- Images ---------------------------------------------------------------------------------------

# The digest reference (repo@sha256:...) of the newest image of main.
resolve_main() {
    docker pull --quiet "$PPF_IMAGE_REPO:main" >/dev/null || fail "cannot pull $PPF_IMAGE_REPO:main"
    docker image inspect --format '{{range .RepoDigests}}{{println .}}{{end}}' "$PPF_IMAGE_REPO:main" \
        | grep -m1 "^$PPF_IMAGE_REPO@sha256:[0-9a-f]\{64\}$" || fail "no digest for $PPF_IMAGE_REPO:main"
}

# Refuses an image not signed by the CI workflow of the main branch.
verify_image() {
    local ref="$1" output
    if ! output="$(cosign verify --certificate-identity "$PPF_SIGNER_IDENTITY" \
        --certificate-oidc-issuer "$OIDC_ISSUER" "$ref" 2>&1)"; then
        echo "$output" >&2
        fail "signature check failed for $ref"
    fi
}

revision_of() {
    local ref="$1" revision
    docker pull --quiet "$ref" >/dev/null || fail "cannot pull $ref"
    revision="$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$ref")"
    [[ "$revision" =~ ^[0-9a-f]{40}$ ]] || fail "image $ref carries no commit (label org.opencontainers.image.revision)"
    echo "$revision"
}

# --- Configuration files of a commit ----------------------------------------------------------------

# Extracts infra/ of a commit into $PPF_DATA_DIR/<env>/releases/<commit> and prints that path.
export_release() {
    local env="$1" revision="$2" dir
    dir="$PPF_DATA_DIR/$env/releases/$revision"
    if [ ! -d "$dir/infra" ]; then
        git -C "$PPF_REPO_DIR" fetch --quiet origin
        git -C "$PPF_REPO_DIR" cat-file -e "$revision^{commit}" 2>/dev/null || fail "commit $revision not in the repository"
        rm -rf "$dir.tmp"
        mkdir -p "$dir.tmp"
        git -C "$PPF_REPO_DIR" archive "$revision" infra | tar -x -C "$dir.tmp"
        mv "$dir.tmp" "$dir"
    fi
    echo "$dir"
}

prune_releases() {
    local env="$1" current
    current="$(cat "$PPF_DATA_DIR/$env/deployed-revision" 2>/dev/null || true)"
    # Newest first; keep a few for a rollback.
    find "$PPF_DATA_DIR/$env/releases" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %f\n' 2>/dev/null \
        | sort -rn | awk -v keep="$KEEP_RELEASES" 'NR > keep {print $2}' \
        | while read -r old; do
            [ "$old" = "$current" ] || rm -rf "${PPF_DATA_DIR:?}/$env/releases/$old"
        done
}

# --- Containers ---------------------------------------------------------------------------------

compose_env() {
    local env="$1" release="$2" image="$3"
    shift 3
    PPF_SERVER_IMAGE="$image" docker compose -f "$release/infra/$env/compose.yml" \
        --env-file "$PPF_DATA_DIR/$env/.env" "$@"
}

container_of() {
    case "$1" in
        production) echo "ppf-prod-server-1" ;;
        preproduction) echo "ppf-preprod-server-1" ;;
    esac
}

wait_healthy() {
    local container="$1" status=""
    for _ in $(seq 1 60); do
        status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}' "$container" 2>/dev/null || true)"
        [ "$status" = "healthy" ] && return 0
        sleep 3
    done
    echo "health of $container: ${status:-absent}" >&2
    return 1
}

# Copies the Caddy files of a release and reloads the proxy (started if needed). A configuration
# that Caddy refuses leaves the running one in place and the previous files back.
proxy_update() {
    local release="$1" env="$2" conf="$PPF_DATA_DIR/proxy/conf" backup
    backup="$(mktemp -d)"
    cp -a "$conf/." "$backup/"
    install -m 0644 "$release/infra/proxy/Caddyfile" "$conf/Caddyfile"
    install -m 0644 "$release/infra/proxy/sites/$env.caddy" "$conf/sites/$env.caddy"
    local proxy=(docker compose -f "$release/infra/proxy/compose.yml" --env-file "$PPF_DATA_DIR/proxy/.env")
    "${proxy[@]}" up -d --remove-orphans
    local reloaded=false
    for _ in $(seq 1 10); do
        if "${proxy[@]}" exec -T caddy caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile; then
            reloaded=true
            break
        fi
        sleep 2
    done
    if [ "$reloaded" != true ]; then
        rm -rf "${conf:?}/"*
        cp -a "$backup/." "$conf/"
        rm -rf "$backup"
        fail "Caddy refused the new configuration; the previous one is kept"
    fi
    rm -rf "$backup"
}

# Starts one environment on an image digest; rolls back to the previous one if it never gets healthy.
deploy() {
    local env="$1" ref="$2" revision release previous_ref previous_revision
    revision="$(revision_of "$ref")"
    release="$(export_release "$env" "$revision")"
    previous_ref="$(cat "$PPF_DATA_DIR/$env/deployed-image" 2>/dev/null || true)"
    previous_revision="$(cat "$PPF_DATA_DIR/$env/deployed-revision" 2>/dev/null || true)"

    say "$env: $ref (commit ${revision:0:12})"
    # The database first, then its migrations with the owner role (one-shot container), then the
    # server with the restricted role, which refuses to start if a migration is still pending.
    compose_env "$env" "$release" "$ref" up -d db
    if ! compose_env "$env" "$release" "$ref" run --rm -T migrate; then
        fail "$env: the database migrations failed with $ref; nothing else was changed"
    fi
    compose_env "$env" "$release" "$ref" up -d --remove-orphans
    if ! wait_healthy "$(container_of "$env")"; then
        if [ -n "$previous_ref" ] && [ -d "$PPF_DATA_DIR/$env/releases/$previous_revision" ]; then
            say "$env is not healthy: back to $previous_ref"
            compose_env "$env" "$PPF_DATA_DIR/$env/releases/$previous_revision" "$previous_ref" up -d --remove-orphans
        fi
        fail "$env did not become healthy with $ref"
    fi
    echo "$ref" > "$PPF_DATA_DIR/$env/deployed-image"
    echo "$revision" > "$PPF_DATA_DIR/$env/deployed-revision"
    printf '%s %s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$ref" "$revision" >> "$PPF_DATA_DIR/$env/history"
    # ntfy refuses everything but the random UnifiedPush topics "up*" (D-078); idempotent.
    compose_env "$env" "$release" "$ref" exec -T ntfy ntfy access everyone 'up*' read-write >/dev/null
    proxy_update "$release" "$env"
    prune_releases "$env"
    docker image prune --force --filter "until=168h" >/dev/null || true
    say "OK: $env runs $ref"
}

# Local dump of the production database before any change (off-site backups: infra/scripts/backup.md).
backup_production() {
    local dir="$PPF_DATA_DIR/production/backups" file
    docker inspect ppf-prod-db-1 >/dev/null 2>&1 || return 0
    install -d -m 0700 "$dir"
    file="$dir/before-deploy-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"
    say "production: database saved to $file"
    # shellcheck disable=SC2016
    docker exec ppf-prod-db-1 sh -c 'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB"' | gzip > "$file"
    chmod 600 "$file"
    find "$dir" -name 'before-deploy-*.sql.gz' -mtime +30 -delete
}

# --- Commands -----------------------------------------------------------------------------------

command="${1:-}"
[ $# -gt 0 ] && shift
case "$command" in
    preproduction)
        if_changed=false
        [ "${1:-}" = "--if-changed" ] && if_changed=true
        ref="$(resolve_main)"
        if [ "$if_changed" = true ] && [ "$ref" = "$(cat "$PPF_DATA_DIR/preproduction/deployed-image" 2>/dev/null || true)" ]; then
            exit 0
        fi
        verify_image "$ref"
        deploy preproduction "$ref"
        ;;
    production)
        source_arg="${1:-}"
        confirm="${2:-}"
        [ "$confirm" = "--confirm" ] || fail "production needs --confirm (and the owner's go-ahead)"
        case "$source_arg" in
            --from-preproduction) ref="$(cat "$PPF_DATA_DIR/preproduction/deployed-image" 2>/dev/null || true)" ;;
            sha256:*) ref="$PPF_IMAGE_REPO@$source_arg" ;;
            *) usage ;;
        esac
        [[ "$ref" =~ ^.+@sha256:[0-9a-f]{64}$ ]] || fail "no image digest to deploy"
        # Only what has run on the preproduction (validated there by the owner).
        grep -q " $ref " "$PPF_DATA_DIR/preproduction/history" 2>/dev/null \
            || fail "$ref never ran on the preproduction: deploy it there and have it validated first"
        verify_image "$ref"
        backup_production
        deploy production "$ref"
        ;;
    status)
        for env in preproduction production; do
            printf '%-14s %s (commit %s) %s\n' "$env" \
                "$(cat "$PPF_DATA_DIR/$env/deployed-image" 2>/dev/null || echo 'not deployed')" \
                "$(cut -c1-12 "$PPF_DATA_DIR/$env/deployed-revision" 2>/dev/null || echo '-')" \
                "$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}' "$(container_of "$env")" 2>/dev/null || true)"
        done
        ;;
    *)
        usage
        ;;
esac
