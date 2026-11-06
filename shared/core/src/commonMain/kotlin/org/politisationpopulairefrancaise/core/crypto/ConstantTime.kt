package org.politisationpopulairefrancaise.core.crypto

/**
 * Compares two byte arrays without short-circuiting, so that the time taken does not reveal where
 * they first differ. Required when checking authentication codes (NIP-44, step 5 of decryption).
 * The lengths themselves are not secret: arrays of different lengths are simply not equal.
 */
fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
    if (a.size != b.size) return false
    var difference = 0
    for (i in a.indices) difference = difference or (a[i].toInt() xor b[i].toInt())
    return difference == 0
}
