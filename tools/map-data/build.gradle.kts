// tools/map-data: prepares the data of the map of the agenda, by a developer, once in a while:
// the glyphs of the labels (from the bundled Atkinson Hyperlegible fonts) and the low zoom
// background of France bundled with the application (from an OpenStreetMap build). Never run by
// the application nor by continuous integration. Guide: tools/map-data/README.md.
//   ./gradlew :tools:map-data:run --args="glyphes"
//   ./gradlew :tools:map-data:run --args="fond <source.pmtiles or https URL>"

plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("org.politisationpopulairefrancaise.tools.map.MapDataKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootDir
}

dependencies {
    implementation(project(":shared:core"))
    testImplementation(kotlin("test"))
}
