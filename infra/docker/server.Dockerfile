# Server image: the runtime only. The jar is built beforehand by Gradle (continuous integration,
# or `gradlew :server:buildFatJar` on a computer), never on the server: the build needs the whole
# Kotlin Multiplatform toolchain (Android SDK included), far too heavy for a 1 vCPU / 4 GB machine.
#
# Built from the repository root, after `gradlew :server:buildFatJar`:
#   docker build -f infra/docker/server.Dockerfile -t ppf-server:local .
# The root .dockerignore sends only the jar and the example contents to the builder.
#
# The same image runs in preproduction and in production (promotion of a verified digest).
# The example contents ([EXEMPLE]) are inside, but only the preproduction loads them
# (PPF_EXAMPLES_DIR); the server refuses to start in production with that variable.

# Eclipse Temurin 21 JRE on Ubuntu 24.04 (glibc: the native libsecp256k1 of the server needs it).
# Pinned by digest; Dependabot proposes updates.
FROM eclipse-temurin:25-jre-noble@sha256:a98128a57de944a72bd31fbc79b898edd5a5864d3652071d5bbd2160ab7d7dce

ARG PPF_REVISION=unknown
LABEL org.opencontainers.image.title="ppf-server" \
      org.opencontainers.image.description="Serveur de la plateforme Politisation Populaire Française" \
      org.opencontainers.image.licenses="AGPL-3.0-or-later" \
      org.opencontainers.image.revision="${PPF_REVISION}"

# A system user without home nor shell; the compose files may run it under another UID
# (one per environment), which works because nothing here is written outside /tmp.
RUN useradd --system --uid 10001 --user-group --no-create-home --shell /usr/sbin/nologin ppf

WORKDIR /app
COPY server/build/libs/ppf-server.jar /app/ppf-server.jar
COPY shared/ui/src/commonMain/composeResources/files/contents/ /app/examples/
# Parliamentary open data (Voir, D-064): the signed example packages, served as they are by the
# preproduction (no import key on the server there), and the one-shot importer of the production.
COPY shared/ui/src/commonMain/composeResources/files/parlement/ /app/parlement-examples/
COPY tools/open-data-importer/build/libs/ppf-open-data-importer.jar /app/ppf-open-data-importer.jar
# The separate signing service (D-129), run as its own container from the same signed image.
COPY signer/build/libs/ppf-signer.jar /app/ppf-signer.jar

USER 10001
ENV PORT=8080
EXPOSE 8080

# Health check without any extra package: bash opens a TCP connection to the server itself.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=3 \
    CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /health HTTP/1.0\\r\\nHost: localhost\\r\\n\\r\\n' >&3 && grep -q '\"status\":\"ok\"' <&3"]

# The heap follows the memory limit of the container (compose files).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/ppf-server.jar"]
