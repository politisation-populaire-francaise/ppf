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
        // Native artifacts chosen by the operating system of the build machine (Windows on
        // the owner's computer, Linux on the CI): their version follows the locked parent
        // (desktop-jvm, skiko, embedded-postgres), only the platform suffix differs.
        ignoredDependencies.add("org.jetbrains.compose.desktop:desktop-jvm-*")
        ignoredDependencies.add("org.jetbrains.skiko:skiko-awt-runtime-*")
        ignoredDependencies.add("io.zonky.test.postgres:embedded-postgres-binaries-*")
    }
}
