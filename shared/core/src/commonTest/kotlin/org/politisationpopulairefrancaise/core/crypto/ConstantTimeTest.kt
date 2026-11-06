package org.politisationpopulairefrancaise.core.crypto

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConstantTimeTest {

    @Test
    fun equalArraysAreEqual() {
        assertTrue(constantTimeEquals(ByteArray(0), ByteArray(0)))
        assertTrue(constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3)))
        assertTrue(constantTimeEquals(ByteArray(32) { 0xff.toByte() }, ByteArray(32) { 0xff.toByte() }))
    }

    @Test
    fun anyDifferenceIsDetected() {
        assertFalse(constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 4)))
        assertFalse(constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(0, 2, 3)))
        assertFalse(constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2)))
        assertFalse(constantTimeEquals(ByteArray(0), byteArrayOf(0)))
        // The highest bit alone must count (a sign-extension mistake would miss it).
        assertFalse(constantTimeEquals(byteArrayOf(0x7f), byteArrayOf(0xff.toByte())))
    }
}
