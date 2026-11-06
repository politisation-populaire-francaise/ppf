#!/usr/bin/env bash
# Deploys one environment on the server. Phase 0 skeleton: builds the image locally on the
# server and restarts the Compose project. Will evolve (image registry, migrations, smoke tests).
#
# Usage, on the server, from the repository checkout:
#   infra/scripts/deploy.sh preproduction
#   infra/scripts/deploy.sh production      (only after validation of the preproduction)
set -euo pipefail

environment="${1:-}"
case "$environment" in
    production|preproduction) ;;
    *) echo "usage: $0 production|preproduction" >&2; exit 2 ;;
esac

cd "$(dirname "$0")/../.."
tag="ppf-server:$(git rev-parse --short HEAD)"

echo "Building $tag"
docker build -f infra/docker/server.Dockerfile -t "$tag" .

echo "Deploying $environment"
export PPF_SERVER_IMAGE="$tag"
docker compose --project-directory "infra/$environment" up -d --remove-orphans

echo "Waiting for the health check"
for _ in $(seq 1 30); do
    if docker compose --project-directory "infra/$environment" exec -T server \
        sh -c 'wget -q -O - http://127.0.0.1:8080/health' 2>/dev/null | grep -q '"status":"ok"'; then
        echo "OK: $environment is healthy ($tag)"
        exit 0
    fi
    sleep 2
done
echo "ERROR: $environment did not become healthy" >&2
exit 1
