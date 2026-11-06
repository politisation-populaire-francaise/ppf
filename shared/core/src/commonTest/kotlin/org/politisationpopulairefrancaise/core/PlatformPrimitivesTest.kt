package org.politisationpopulairefrancaise.core

import org.politisationpopulairefrancaise.core.crypto.secureRandomBytes
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformPrimitivesTest {

    @Test
    fun secureRandomBytesAreFreshAndSized() {
        val first = secureRandomBytes(32)
        val second = secureRandomBytes(32)
        assertEquals(32, first.size)
        assertFalse(first.all { it == 0.toByte() })
        assertFalse(first.contentEquals(second))
        assertEquals(0, secureRandomBytes(0).size)
    }

    @Test
    fun clockIsAfter2023() {
        assertTrue(currentEpochSeconds() > 1_700_000_000)
    }
}
