// desktop: the Windows, macOS and Linux application. It only hosts the shared Compose UI.

import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared:ui"))
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "org.politisationpopulairefrancaise.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Dmg)
            // The packaged Java runtime only ships the modules listed here (output of
            // `gradlew :desktop:suggestRuntimeModules`): java.sql for the local SQLite database.
            // Re-run that task after adding a dependency; a missing module makes the app fail at launch.
            modules("java.instrument", "java.management", "java.sql", "jdk.unsupported")
            packageName = "PolitisationPopulaireFrancaise"
            // Installer version. Windows installers refuse a major version of 0,
            // so installers are numbered from 1.0.0 while the app itself reports AppInfo.VERSION.
            packageVersion = "1.0.0"
            description = "Politisation Populaire Française"
            vendor = "Politisation Populaire Française"
            licenseFile.set(rootProject.file("LICENSE"))

            windows {
                // The symbol of PPF (design/identite, D-185): PNG images of the pack in one .ico file.
                iconFile.set(project.file("icons/ppf.ico"))
                menuGroup = "Politisation Populaire Française"
                perUserInstall = true
                // Stable identifier so that a new installer upgrades the previous one.
                upgradeUuid = "7C2A1F6E-4B0D-4E0B-9C7D-5A3F1E2B8D01"
            }
            linux {
                iconFile.set(project.file("icons/ppf.png"))
                packageName = "politisation-populaire-francaise"
            }
            macOS {
                bundleID = "org.politisationpopulairefrancaise.desktop"
            }
        }
    }
}
