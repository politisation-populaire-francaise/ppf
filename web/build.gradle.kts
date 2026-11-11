// web: the server-rendered web site (HTML built with kotlinx.html, served by Ktor).
// Readable without JavaScript; every resource is self-hosted.

plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared:core"))
    api(libs.kotlinx.html)
    api(libs.ktor.server.core)
    api(libs.ktor.server.html.builder)

    testImplementation(kotlin("test"))
}

// The legal texts (content/legal, outside the signed contents) become resources of the site under legal/.
tasks.processResources {
    from(rootProject.layout.projectDirectory.dir("content/legal")) {
        include("*.md")
        exclude("README.md")
        into("legal")
    }
}
