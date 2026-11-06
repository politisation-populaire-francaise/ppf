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

    testImplementation(libs.ktor.server.test.host)
    testImplementation(kotlin("test"))
    // A real PostgreSQL 17 started by the tests themselves: no Docker, no installation needed.
    testImplementation(libs.zonky.embedded.postgres)
    testImplementation(enforcedPlatform(libs.zonky.postgres.binaries.bom))
}
