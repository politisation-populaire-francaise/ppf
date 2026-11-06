package org.politisationpopulairefrancaise.core

/** Minimal description of the platform the code is running on. */
data class PlatformInfo(val name: String, val version: String)

/**
 * Platform-specific implementation (expect/actual), the only allowed way to
 * reach platform APIs from shared code.
 */
expect fun currentPlatform(): PlatformInfo
