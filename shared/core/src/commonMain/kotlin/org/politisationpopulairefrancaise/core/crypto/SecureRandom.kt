package org.politisationpopulairefrancaise.core.crypto

/**
 * Returns [size] cryptographically secure random bytes from the operating system's generator.
 * This is the only source of randomness allowed for keys and recovery phrases
 * (kotlin.random.Random is NOT suitable for secrets).
 */
expect fun secureRandomBytes(size: Int): ByteArray
