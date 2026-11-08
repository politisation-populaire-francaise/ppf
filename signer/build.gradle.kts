// signer: the signing service of the platform (contract docs/integration/service-signature.md, D-129).
// A separate jar, run in its own container with no Internet access, reachable only by the server on an
// internal network. It holds the working keys (encrypted at rest, never exported) and signs only what
// its rules allow, with the approvals of the team checked by itself.
//   ./gradlew :signer:test
//   ./gradlew :signer:buildFatJar   (build/libs/ppf-signer.jar)

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
    mainClass.set("org.politisationpopulairefrancaise.signer.SignerMainKt")
}

ktor {
    fatJar {
        archiveFileName.set("ppf-signer.jar")
    }
}

tasks.withType<ShadowJar>().configureEach {
    mergeServiceFiles()
}

// The service never opens the local database of the applications: neither SQLDelight nor the SQLite
// driver (and its native libraries) enter its jar, a smaller surface for the container holding the keys.
// Excluded from the tests too, so that they prove the service runs without them.
configurations.matching { it.name == "runtimeClasspath" || it.name == "testRuntimeClasspath" }.configureEach {
    exclude(group = "app.cash.sqldelight")
    exclude(group = "org.xerial")
}

// The tests never see the PPF_* settings of the machine running them.
tasks.withType<Test>().configureEach {
    environment = environment.filterKeys { !it.startsWith("PPF_") }
}

dependencies {
    implementation(project(":shared:core"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.logback.classic)
    // Verification of the team's passkey approvals (WebAuthn), the same library and version as the
    // back-office of the server (D-120).
    implementation(libs.webauthn4j.core)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(kotlin("test"))
}
