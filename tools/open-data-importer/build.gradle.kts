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

dependencies {
    implementation(project(":shared:core"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}
