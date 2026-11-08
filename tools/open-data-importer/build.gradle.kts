// tools/open-data-importer: builds the packages of parliamentary open data (D-064, contract
// docs/integration/voir-donnees-ouvertes.md) and signs their index with the dedicated import key.
// A one-shot command line tool, run by a systemd timer in its own container in production
// (memory limited to 256 MB: the official files are read entry by entry), by hand in development.
// Guide: docs/integration/voir-donnees-import.md.
//   ./gradlew :tools:open-data-importer:run --args="--out build/parlement"
//   ./gradlew :tools:open-data-importer:buildFatJar   (build/libs/ppf-open-data-importer.jar)

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("org.politisationpopulairefrancaise.tools.opendata.OpenDataImporterKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootDir
}

// The tests never see the PPF_* settings of the machine running them, and never reach the network.
tasks.withType<Test>().configureEach {
    environment = environment.filterKeys { !it.startsWith("PPF_") }
}

// One jar with every dependency (the native libsecp256k1 libraries included), for the container
// image `ppf-open-data-importer`: java -jar ppf-open-data-importer.jar --source official ...
val runtimeJars = configurations.runtimeClasspath
tasks.register<Jar>("buildFatJar") {
    group = "build"
    description = "Builds build/libs/ppf-open-data-importer.jar, the importer with all its dependencies."
    archiveFileName.set("ppf-open-data-importer.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes["Main-Class"] = "org.politisationpopulairefrancaise.tools.opendata.OpenDataImporterKt" }
    from(sourceSets.main.map { it.output })
    from(runtimeJars.map { classpath -> classpath.map { if (it.isDirectory) it else zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC", "module-info.class", "META-INF/versions/*/module-info.class")
}

// D-145: The open data importer never computes anonymous voting tokens. libsodium (the ristretto255 group of « une personne,
// une voix », in shared/core since D-110), its native loader and JNA stay out of this container holding a
// key; the check below fails the build if any of them comes back into ppf-open-data-importer.jar. JNA is excluded from the
// runtime only: libsodium was its only user there, and a test library may still need it.
configurations.matching { it.name == "runtimeClasspath" || it.name == "testRuntimeClasspath" }.configureEach {
    exclude(group = "com.ionspin.kotlin")
    exclude(group = "com.goterl")
}
configurations.matching { it.name == "runtimeClasspath" }.configureEach {
    exclude(group = "net.java.dev.jna")
}
val forbiddenInImporterJar = listOf("com/ionspin/", "com/goterl/", "com/sun/jna/", "libsodium")
val verifyImporterJar = tasks.register("verifyImporterJar") {
    group = "verification"
    description = "Checks that ppf-open-data-importer.jar holds neither libsodium, nor JNA, nor their native loader (D-145)."
    val jar = tasks.named<Jar>("buildFatJar").flatMap { it.archiveFile }
    inputs.file(jar)
    val forbidden = forbiddenInImporterJar
    doLast {
        java.util.zip.ZipFile(jar.get().asFile).use { zip ->
            val found = zip.entries().asSequence().map { it.name }.filter { name -> forbidden.any { name.contains(it) } }.toList()
            check(found.isEmpty()) { "ppf-open-data-importer.jar must not contain libsodium, JNA or their loader (D-145): ${found.take(5)}" }
        }
    }
}
tasks.named("check") { dependsOn(verifyImporterJar) }

dependencies {
    implementation(project(":shared:core"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}
