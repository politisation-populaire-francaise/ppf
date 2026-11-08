// shared/ui: the Compose Multiplatform user interface shared by Android,
// desktop and iOS. Everything that is visible lives here; the application
// modules only host it.

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "org.politisationpopulairefrancaise.ui"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        // Required for the Compose resources (strings, bundled contents) to be packaged as Android
        // assets: the Android KMP library plugin disables Android resources by default, and without
        // this the APK ships no resource at all and the app cannot even display its first screen.
        androidResources {
            enable = true
        }
    }

    jvm()

    // Declared so that the shared code stays iOS-compatible. Compiled on macOS only.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "PpfUi"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.material.icons.core)
            implementation(libs.compose.components.resources)
            implementation(libs.navigationevent.compose)
        }
        // The map view of the agenda is drawn by MapLibre on Android only (see MapRenderer): the
        // library and its OpenGL ES runtime. Desktop and iOS show the list (docs/integration/agir-carte.md).
        androidMain.dependencies {
            // The system photo picker of "Témoigner" (ui/media): the same artifact and version the
            // Android host already uses and that the runtime classpath already resolves.
            implementation(libs.androidx.activity.compose)
            implementation(libs.maplibre.compose)
            runtimeOnly(libs.maplibre.compose.runtime.opengl.android)
            // QR codes (components/qr): ZXing draws and reads them, CameraX shows the camera of « Valider une
            // personne » (permission asked only there). Never ML Kit.
            implementation(libs.zxing.core)
            implementation(libs.androidx.camera.core)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.view)
        }
        // On the computer, QR codes are drawn (ZXing); reading one needs a phone's camera.
        jvmMain.dependencies {
            implementation(libs.zxing.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.compose.ui.test)
        }
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "org.politisationpopulairefrancaise.ui.resources"
    generateResClass = always
}
