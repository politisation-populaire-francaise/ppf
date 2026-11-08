#!/usr/bin/env bash
# Makes the bundle of one commit for the preproduction, before GitHub is linked (infra/README.md):
# the infra/ files, .dockerignore and the bundled contents of the commit (git archive, so exactly what
# is committed), plus the two jars built from that commit, and a REVISION file. Run from the root of a
# clean checkout, after:
#   gradle-safe.ps1 ... -Tasks ":server:buildFatJar :tools:open-data-importer:buildFatJar"
#
#   bash tools/make-preprod-bundle.sh <output.tar.gz>
#
# Then: scp the file to the server and run `sudo ppf-deploy preproduction-bundle <file>` there.
set -euo pipefail

out="${1:?usage: tools/make-preprod-bundle.sh <output.tar.gz>}"
cd "$(git rev-parse --show-toplevel)"
[ -z "$(git status --porcelain --untracked-files=no)" ] || { echo "Commit first: the bundle must match one commit." >&2; exit 1; }
revision="$(git rev-parse HEAD)"
server_jar=server/build/libs/ppf-server.jar
importer_jar=tools/open-data-importer/build/libs/ppf-open-data-importer.jar
for jar in "$server_jar" "$importer_jar"; do
    [ -f "$jar" ] || { echo "Missing $jar: build it first." >&2; exit 1; }
    # A jar older than the commit was not built from it.
    [ "$(stat -c %Y "$jar")" -ge "$(git log -1 --format=%ct)" ] || { echo "$jar is older than the commit: build it again." >&2; exit 1; }
done
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
git archive "$revision" infra .dockerignore shared/ui/src/commonMain/composeResources/files/contents \
    shared/ui/src/commonMain/composeResources/files/parlement | tar -x -C "$work"
mkdir -p "$work/server/build/libs" "$work/tools/open-data-importer/build/libs"
cp "$server_jar" "$work/$server_jar"
cp "$importer_jar" "$work/$importer_jar"
echo "$revision" > "$work/REVISION"
tar -czf "$out" -C "$work" .
echo "Bundle of commit ${revision:0:12}: $out ($(du -h "$out" | cut -f1))"
