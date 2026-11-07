// Gradle settings for the Politisation Populaire Française platform.
// One build, several modules: shared Kotlin Multiplatform code, Android app,
// desktop app, Ktor server and server-rendered web site.

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Every module uses the same repositories; no module may add its own.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "politisation-populaire-francaise"

include(":shared:core")
include(":shared:ui")
include(":android")
include(":desktop")
include(":server")
include(":web")
include(":tools:content-signer")
include(":tools:map-data")
