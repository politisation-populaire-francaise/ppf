// android: the Android application. It only hosts the shared Compose UI.
// AGP 9 compiles Kotlin itself (built-in Kotlin), so no kotlin-android plugin here.

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// Version code: the number of minutes between 1 January 2026 (UTC) and the last commit.
// It grows with every commit, so each new APK installs as an update over the previous one
// (Android refuses an older version code), and it is the same for every build of one commit,
// on the owner's computer as in continuous integration (reproducible builds).
// PPF_VERSION_CODE overrides it; without Git (source archive) it falls back to 1.
val versionCodeEpochSeconds = 1_767_225_600L // 2026-01-01T00:00:00Z
val commitVersionCode: Int = providers.environmentVariable("PPF_VERSION_CODE").orNull?.toInt()
    ?: runCatching {
        providers.exec {
            commandLine("git", "log", "-1", "--format=%ct")
            isIgnoreExitValue = true
        }.standardOutput.asText.get().trim().toLong()
    }.map { commitSeconds -> ((commitSeconds - versionCodeEpochSeconds) / 60).toInt().coerceAtLeast(1) }
        .getOrDefault(1)

android {
    namespace = "org.politisationpopulairefrancaise.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // Stable, public identifier of the app on Android. Never changed once published.
        applicationId = "org.politisationpopulairefrancaise.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = commitVersionCode
        versionName = "0.1.0"

        // Native libsecp256k1 is shipped per architecture; 32-bit x86 only exists on old emulators.
        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    // Test signing: the same key on the owner's computer and in continuous integration,
    // so that every APK can be installed over the previous one. The keystore and its
    // passwords live outside the repository (see docs/GUIDE_PROPRIETAIRE.md).
    // Release signing (store publication) will use a separate key kept offline.
    val testKeystorePath = providers.environmentVariable("PPF_TEST_KEYSTORE").orNull
    val testKeystorePassword = providers.environmentVariable("PPF_TEST_KEYSTORE_PASSWORD").orNull
    val hasTestKeystore = testKeystorePath != null && testKeystorePassword != null && file(testKeystorePath).exists()
    if (hasTestKeystore) {
        signingConfigs {
            getByName("debug") {
                storeFile = file(testKeystorePath!!)
                storePassword = testKeystorePassword
                keyAlias = "ppf-test"
                keyPassword = testKeystorePassword
            }
        }
    }

    buildTypes {
        // Development build, for the computer only: debuggable, never sent to a phone.
        debug {
            applicationIdSuffix = ".test"
            versionNameSuffix = "-test"
        }
        // Test version sent to phones (owner and testers). Same identifier and same test
        // signature as the debug build, so it installs over it as an update, but NOT debuggable:
        // on a debuggable app anyone holding the phone with a USB cable can read the app's
        // private files ("run-as") and attach a debugger (OWASP MASVS-RESILIENCE). Code shrinking
        // (R8) stays off here until it can be checked on a real phone: a shrinking mistake only
        // shows at runtime, and there is no Android emulator on the owner's computer.
        create("preview") {
            isDebuggable = false
            isMinifyEnabled = false
            applicationIdSuffix = ".test"
            versionNameSuffix = "-test"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
        // Native libraries compressed in the APK: the map engine (MapLibre, three architectures)
        // weighs about 40 MB uncompressed and 17 MB compressed. Downloading the APK matters more on
        // a weak connection than the one-time extraction at install (docs/integration/agir-carte.md).
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    implementation(project(":shared:ui"))
    implementation(libs.androidx.activity.compose)
}
