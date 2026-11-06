// Root build file: declares plugin versions once (applied in modules) and
// enables dependency locking everywhere so that every build resolves the
// exact same dependency versions (see gradle.lockfile in each module).

plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.sqldelight) apply false
}

subprojects {
    dependencyLocking {
        lockAllConfigurations()
    }
}
