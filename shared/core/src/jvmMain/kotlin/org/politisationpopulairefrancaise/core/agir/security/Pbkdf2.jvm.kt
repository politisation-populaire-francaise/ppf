package org.politisationpopulairefrancaise.core.agir.security

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** The JVM's own PBKDF2 (SunJCE): the code is made of ASCII digits, so its characters are its bytes. */
actual fun pbkdf2Sha256(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
    val chars = CharArray(password.size) { password[it].toInt().toChar() }
    val spec = PBEKeySpec(chars, salt, iterations, length * 8)
    try {
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    } finally {
        spec.clearPassword()
        chars.fill('\u0000')
    }
}
