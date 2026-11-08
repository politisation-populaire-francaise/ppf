// signer: the signing service of the platform (contract docs/integration/service-signature.md, D-129).
// A separate jar, run in its own container with no Internet access, reachable only by the server on an
// internal network, and serving its own approval pages on a second port and origin. It holds the working
// keys (encrypted at rest, never exported) and signs only what its rules allow, with the approvals of the
// team collected and checked by itself.
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

// D-145: The signing service never computes anonymous voting tokens. libsodium (the ristretto255 group of « une personne,
// une voix », in shared/core since D-110), its native loader and JNA stay out of this container holding a
// key; the check below fails the build if any of them comes back into ppf-signer.jar. JNA is excluded from the
// runtime only: libsodium was its only user there, and a test library may still need it.
configurations.matching { it.name == "runtimeClasspath" || it.name == "testRuntimeClasspath" }.configureEach {
    exclude(group = "com.ionspin.kotlin")
    exclude(group = "com.goterl")
}
configurations.matching { it.name == "runtimeClasspath" }.configureEach {
    exclude(group = "net.java.dev.jna")
}
val forbiddenInSignerJar = listOf("com/ionspin/", "com/goterl/", "com/sun/jna/", "libsodium")
val verifySignerJar = tasks.register("verifySignerJar") {
    group = "verification"
    description = "Checks that ppf-signer.jar holds neither libsodium, nor JNA, nor their native loader (D-145)."
    val jar = tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile }
    inputs.file(jar)
    val forbidden = forbiddenInSignerJar
    doLast {
        java.util.zip.ZipFile(jar.get().asFile).use { zip ->
            val found = zip.entries().asSequence().map { it.name }.filter { name -> forbidden.any { name.contains(it) } }.toList()
            check(found.isEmpty()) { "ppf-signer.jar must not contain libsodium, JNA or their loader (D-145): ${found.take(5)}" }
        }
    }
}
tasks.named("check") { dependsOn(verifySignerJar) }

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
    // The approval page, rendered by the service itself from the draft it holds (contract 1.2): the same
    // HTML builder as the site and the back-office, escaping every text.
    implementation(libs.kotlinx.html)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(kotlin("test"))
}
