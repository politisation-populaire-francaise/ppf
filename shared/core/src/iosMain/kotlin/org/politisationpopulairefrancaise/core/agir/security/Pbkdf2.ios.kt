package org.politisationpopulairefrancaise.core.agir.security

/**
 * The portable version until iOS is built (CommonCrypto's `CCKeyDerivationPBKDF` will be faster:
 * to switch to once the Apple Developer account exists and the unlock time can be measured).
 */
actual fun pbkdf2Sha256(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray =
    Pbkdf2Sha256.derive(password, salt, iterations, length)
