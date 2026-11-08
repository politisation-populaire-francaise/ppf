// server: the Ktor server. Health check, the web site skeleton and the signed-events API
// backed by PostgreSQL (plain JDBC, HikariCP pool, Flyway migrations).

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktor)
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("org.politisationpopulairefrancaise.server.ApplicationKt")
}

ktor {
    fatJar {
        archiveFileName.set("ppf-server.jar")
    }
}

// Flyway and the JDBC driver register themselves through META-INF/services: the fat jar must
// merge those files, otherwise PostgreSQL support is silently missing at runtime.
tasks.withType<ShadowJar>().configureEach {
    mergeServiceFiles()
}

// The tests never see the PPF_* settings of the machine running them (an AI model configured for
// the development server, editor keys...): each test states its own configuration. The periodic
// cleanup of demand proposals stays off, as the envelope cleanup does with TEST_RELAY, and so do
// the automatic imports of the directory of elected officials (the tests of the directory run their own),
// and the retention of the cases of contact (the tests of the cases run it by hand).
tasks.withType<Test>().configureEach {
    environment = environment.filterKeys { !it.startsWith("PPF_") } +
        mapOf(
            "PPF_PROPOSALS_CLEANUP_INTERVAL_MINUTES" to "0", "PPF_RNE_REFRESH_DAYS" to "0", "PPF_TERRITOIRES_REFRESH_DAYS" to "0",
            "PPF_CONTACT_RETENTION_INTERVAL_MINUTES" to "0",
        )
}

// Development only: the server with an in-process PostgreSQL 17 and the bundled example contents.
tasks.register<JavaExec>("runDev") {
    group = "application"
    description = "Starts the server on http://localhost:8080 with an embedded PostgreSQL (development only)."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.politisationpopulairefrancaise.server.DevServerKt")
    workingDir = rootDir
}

dependencies {
    implementation(project(":shared:core"))
    implementation(project(":web"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.default.headers)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.logback.classic)
    implementation(libs.hikaricp)
    implementation(libs.postgresql.jdbc)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    // HTTP client towards the AI model that reads proposed demands (off unless configured). Already
    // in the runtime classpath through shared/core; declared here because the server builds its own client.
    implementation(libs.ktor.client.cio)
    // Verification of the passkeys and security keys of the team back-office (WebAuthn, D-120).
    implementation(libs.webauthn4j.core)

    // The development server and the tests of /api/v1/parlement run the example import (fictional data).
    testImplementation(project(":tools:open-data-importer"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.mock)
    testImplementation(kotlin("test"))
    // A real PostgreSQL 17 started by the tests themselves: no Docker, no installation needed.
    testImplementation(libs.zonky.embedded.postgres)
    testImplementation(enforcedPlatform(libs.zonky.postgres.binaries.bom))
}
