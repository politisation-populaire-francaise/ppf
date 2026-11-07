// shared/core: platform-independent models and business logic.
// No UI framework here, so the server can depend on it too.
// Targets: Android, JVM (desktop and server), iOS (declared, compiled only on macOS).

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "org.politisationpopulairefrancaise.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    jvm()

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
            // Identities and signatures: libsecp256k1 (Schnorr BIP-340) and the BIP-39/BIP-32/bech32
            // building blocks. Kept as implementation details: no type of these libraries is exposed.
            implementation(libs.secp256k1.kmp)
            implementation(libs.bitcoin.kmp)
            // Anonymous voting tokens (D-110): the ristretto255 group of libsodium, behind our own
            // `Ristretto255` interface (core/urne); no type of the library is exposed.
            implementation(libs.libsodium.bindings)
            // Local database (offline-first copy of the contents) and HTTP client towards the server.
            api(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            api(libs.ktor.client.core)
        }
        // Native bindings of libsecp256k1, loaded at runtime (iOS links the library statically),
        // SQLite driver and HTTP engine of each platform.
        jvmMain.dependencies {
            implementation(libs.secp256k1.kmp.jni.jvm)
            implementation(libs.sqldelight.sqlite.driver)
            implementation(libs.ktor.client.cio)
        }
        androidMain.dependencies {
            implementation(libs.secp256k1.kmp.jni.android)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.ktor.client.cio)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

sqldelight {
    databases {
        create("PpfDatabase") {
            packageName.set("org.politisationpopulairefrancaise.core.db")
        }
    }
    // iOS: link the system SQLite into the framework.
    linkSqlite.set(true)
}
