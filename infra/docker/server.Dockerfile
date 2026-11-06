# Server image. Built from the repository root:
#   docker build -f infra/docker/server.Dockerfile -t ppf-server:local .
#
# Stage 1: build the fat jar with the same JDK as everywhere else.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew :server:buildFatJar --no-daemon --console=plain

# Stage 2: minimal runtime, non-root user, read-only filesystem (see compose files).
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --user-group --no-create-home ppf
WORKDIR /app
COPY --from=build /src/server/build/libs/ppf-server.jar /app/ppf-server.jar
USER ppf
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/ppf-server.jar"]
