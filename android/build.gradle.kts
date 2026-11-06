// android: the Android application. It only hosts the shared Compose UI.
// AGP 9 compiles Kotlin itself (built-in Kotlin), so no kotlin-android plugin here.

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "org.politisationpopulairefrancaise.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // Stable, public identifier of the app on Android. Never changed once published.
        applicationId = "org.politisationpopulairefrancaise.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
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
        debug {
            applicationIdSuffix = ".test"
            versionNameSuffix = "-test"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    implementation(project(":shared:ui"))
    implementation(libs.androidx.activity.compose)
}
