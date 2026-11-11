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

/*
 * The quick entry check of the owner's computer (D-189): every test, the Android test version, the
 * desktop application, without what does not serve today (the Android publication build and its R8,
 * the full Android lint, the iOS targets, the archives of the server and of the signer). The three
 * scripts (check-apk, check-no-trackers, check-dependencies) follow it. The continuous integration
 * on GitHub keeps the full `build`; the publication, iOS and the full lint are also run there.
 * Run with: gradle-safe.ps1 ... -Tasks "ppfGate --continue"
 */
tasks.register("ppfGate") {
    group = "verification"
    description = "Every test, the Android test version and the desktop application, without publication, iOS nor full lint."
    dependsOn(
        // Multiplatform modules: the JVM tests only (their `check` would also build the iOS targets).
        ":shared:core:jvmTest",
        ":shared:ui:jvmTest",
        // JVM modules: `check` = their tests plus their own verifications (signed jars of D-145).
        ":server:check",
        ":signer:check",
        ":web:check",
        ":tools:content-signer:check",
        ":tools:open-data-importer:check",
        ":tools:map-data:check",
        ":desktop:check",
        ":desktop:compileKotlin",
        // The test version sent to phones, with its « lint vital » checks (Android has no unit test today).
        ":android:assemblePreview",
    )
}
