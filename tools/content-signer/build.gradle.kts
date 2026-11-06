// tools/content-signer: signs the editorial contents of content/ into events bundled with the app.
// Run from the repository root: ./gradlew :tools:content-signer:run

plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("org.politisationpopulairefrancaise.tools.content.ContentSignerKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootDir
    args("content", "shared/ui/src/commonMain/composeResources/files/contents")
}

dependencies {
    implementation(project(":shared:core"))
}
