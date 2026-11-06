package org.politisationpopulairefrancaise.core.crypto

import java.security.SecureRandom

private val generator = SecureRandom()

actual fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also(generator::nextBytes)
