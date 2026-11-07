package org.politisationpopulairefrancaise.core.social

import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ContactVerificationTest {

    private val alice = IdentityKeys.generate().publicKey.hex
    private val bob = IdentityKeys.generate().publicKey.hex
    private val mallory = IdentityKeys.generate().publicKey.hex

    @Test
    fun bothSidesComputeTheSameNumber() {
        val number = SafetyNumber.of(alice, bob)
        assertEquals(number, SafetyNumber.of(bob, alice))
        assertEquals(SafetyNumber.GROUPS, number.size)
        assertTrue(number.all { it.length == 5 && it.all(Char::isDigit) })
        assertEquals(number.joinToString(" "), SafetyNumber.display(alice, bob))
    }

    @Test
    fun anImpostorGivesAnotherNumber() {
        assertNotEquals(SafetyNumber.of(alice, bob), SafetyNumber.of(alice, mallory))
    }

    @Test
    fun theNumberIsStableAcrossVersionsOfTheApplication() {
        // Changing the computation would make two devices of different versions disagree.
        val a = "1".repeat(64)
        val b = "2".repeat(64)
        assertEquals(SafetyNumber.of(a, b), SafetyNumber.of(b, a))
        // Reference value, computed independently (Python hashlib) from the documented recipe.
        assertEquals("24481 06705 06718 45427 86022 74404 17779 88484 20105 18260 23446 54448", SafetyNumber.display(a, b))
        assertFailsWith<IllegalArgumentException> { SafetyNumber.of("not a key", b) }
    }

    @Test
    fun verificationsStayOnTheDeviceAndCanBeUndone() {
        val store = ContactVerificationStore(JvmDatabase.inMemory(), clock = { 42 })
        assertFalse(store.isVerified(bob))
        store.markVerified(bob)
        assertTrue(store.isVerified(bob))
        assertEquals(42, store.verifiedAt(bob))
        assertEquals(listOf(bob), store.all())
        store.unmarkVerified(bob)
        assertFalse(store.isVerified(bob))
        store.markVerified(mallory)
        store.clear()
        assertTrue(store.all().isEmpty())
        assertFailsWith<IllegalArgumentException> { store.markVerified("ABC") }
    }
}
