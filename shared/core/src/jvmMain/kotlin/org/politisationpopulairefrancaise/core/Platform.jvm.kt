package org.politisationpopulairefrancaise.core

actual fun currentPlatform(): PlatformInfo =
    PlatformInfo(
        name = System.getProperty("os.name") ?: "JVM",
        version = System.getProperty("os.version") ?: "",
    )
