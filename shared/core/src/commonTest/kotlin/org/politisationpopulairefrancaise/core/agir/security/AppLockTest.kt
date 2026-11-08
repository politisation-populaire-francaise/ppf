package org.politisationpopulairefrancaise.core.agir.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A clock the test moves by hand. */
class ManualLockClock(var seconds: Long = 1_800_000_000, var millis: Long = 0) : LockClock {
    override fun epochSeconds(): Long = seconds

    override fun monotonicMillis(): Long = millis

    fun advance(seconds: Long) {
        this.seconds += seconds
        millis += seconds * 1000
    }
}

class AppLockTest {
    private fun lock(storage: DeviceSecurityStorage = InMemoryDeviceSecurityStorage(), clock: LockClock = ManualLockClock()) =
        AppLock(storage, clock, iterations = 10)

    @Test
    fun pbkdf2MatchesTheRfc7914TestVector() {
        // RFC 7914, section 11: PBKDF2-HMAC-SHA256 (P="passwd", S="salt", c=1, dkLen=64).
        val derived = Pbkdf2Sha256.derive("passwd".encodeToByteArray(), "salt".encodeToByteArray(), 1, 64)
        assertEquals(
            "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783",
            derived.toHexString(),
        )
        // The platform's provider gives the same bytes.
        assertEquals(derived.toHexString(), pbkdf2Sha256("passwd".encodeToByteArray(), "salt".encodeToByteArray(), 1, 64).toHexString())
        assertEquals(
            Pbkdf2Sha256.derive("123456".encodeToByteArray(), "sel".encodeToByteArray(), 50, 32).toHexString(),
            pbkdf2Sha256("123456".encodeToByteArray(), "sel".encodeToByteArray(), 50, 32).toHexString(),
        )
    }

    @Test
    fun theCodeHasSixToSixteenDigitsAndIsNotTrivial() {
        assertEquals(PinProblem.TOO_SHORT, PinRules.check("12345"))
        assertEquals(PinProblem.TOO_LONG, PinRules.check("1".repeat(17)))
        assertEquals(PinProblem.NOT_DIGITS, PinRules.check("12a456"))
        listOf("000000", "123456", "987654", "890123", "121212", "123123", "2580025800").forEach {
            assertEquals(PinProblem.TOO_SIMPLE, PinRules.check(it), it)
        }
        assertEquals(PinProblem.MISMATCH, PinRules.check("471905", "471906"))
        assertNull(PinRules.check("471905", "471905"))
        assertNull(PinRules.check("4719052688"))
    }

    @Test
    fun offByDefaultAndContentHiddenByDefault() {
        val lock = lock()
        assertFalse(lock.settings.lockEnabled)
        assertFalse(lock.isLocked)
        assertTrue(lock.settings.hideContent, "the most protective default (principle 10)")
        assertEquals(0, lock.settings.lockAfterSeconds)
    }

    @Test
    fun onceOnTheApplicationStartsLockedAndOnlyTheRightCodeOpensIt() {
        val storage = InMemoryDeviceSecurityStorage()
        assertNull(lock(storage).enable("471905", "471905"))
        val stored = assertNotNull(storage.value)
        // Never the code itself, never its length.
        assertFalse(stored.contains("471905"))
        assertFalse(stored.contains("\"length\""))
        assertTrue(stored.contains(PinRecord.ALGORITHM))

        val restarted = lock(storage)
        assertTrue(restarted.isLocked)
        assertIs<UnlockResult.Wrong>(restarted.unlock("471906"))
        assertTrue(restarted.isLocked)
        assertEquals(UnlockResult.Unlocked, restarted.unlock("471905"))
        assertFalse(restarted.isLocked)
        assertEquals(0, restarted.settings.failedAttempts)
    }

    @Test
    fun twoDevicesWithTheSameCodeStoreDifferentHashes() {
        val a = InMemoryDeviceSecurityStorage()
        val b = InMemoryDeviceSecurityStorage()
        lock(a).enable("471905", "471905")
        lock(b).enable("471905", "471905")
        val hashA = DeviceSecuritySettings.decode(a.value).lock!!.hashHex
        val hashB = DeviceSecuritySettings.decode(b.value).lock!!.hashHex
        assertTrue(hashA != hashB, "a random salt per device")
    }

    @Test
    fun wrongCodesWaitLongerAndLongerEvenAfterARestart() {
        val storage = InMemoryDeviceSecurityStorage()
        val clock = ManualLockClock()
        lock(storage, clock).enable("471905", "471905")
        var lock = lock(storage, clock)
        repeat(4) { assertEquals(0, (lock.unlock("000001") as UnlockResult.Wrong).waitSeconds) }
        assertEquals(30, (lock.unlock("000001") as UnlockResult.Wrong).waitSeconds)
        // Even the right code is not checked before the end of the wait.
        assertEquals(UnlockResult.TooEarly(30), lock.unlock("471905"))
        // Closing and reopening the application does not reset the counter.
        lock = lock(storage, clock)
        assertEquals(30, lock.waitSeconds())
        clock.advance(30)
        assertEquals(60, (lock.unlock("000001") as UnlockResult.Wrong).waitSeconds)
        clock.advance(60)
        assertEquals(300, (lock.unlock("000001") as UnlockResult.Wrong).waitSeconds)
        // A clock set back does not stretch the wait beyond the delay.
        clock.seconds -= 10_000
        assertEquals(300, lock.waitSeconds())
        clock.seconds += 10_000 + 300
        assertEquals(UnlockResult.Unlocked, lock.unlock("471905"))
        assertEquals(0, lock.settings.failedAttempts)
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L, 30L, 60L, 300L, 900L, 3600L, 3600L), (0..10).map(UnlockDelays::afterFailures))
    }

    @Test
    fun itLocksAgainAfterTheChosenTimeInTheBackground() {
        val storage = InMemoryDeviceSecurityStorage()
        val clock = ManualLockClock()
        val lock = lock(storage, clock)
        lock.enable("471905", "471905")
        lock.setLockAfter(300)
        lock.onBackground()
        clock.advance(299)
        lock.onForeground()
        assertFalse(lock.isLocked)
        lock.onBackground()
        clock.advance(300)
        lock.onForeground()
        assertTrue(lock.isLocked)
        lock.unlock("471905")

        // A phone asleep: the monotonic clock stood still, the wall clock went on.
        lock.onBackground()
        clock.seconds += 600
        lock.onForeground()
        assertTrue(lock.isLocked)
        lock.unlock("471905")

        // « Immédiatement »: any visit elsewhere locks.
        lock.setLockAfter(0)
        lock.onBackground()
        lock.onForeground()
        assertTrue(lock.isLocked)
    }

    @Test
    fun turningTheLockOffOrChangingTheCodeNeedsTheCurrentCode() {
        val lock = lock()
        lock.enable("471905", "471905")
        assertFalse(lock.disable("000001"))
        assertEquals(1, lock.settings.failedAttempts)
        assertEquals(ChangeResult.WrongCurrent, lock.change("000001", "582016", "582016"))
        assertEquals(ChangeResult.Refused(PinProblem.TOO_SIMPLE), lock.change("471905", "111111", "111111"))
        assertEquals(ChangeResult.Changed, lock.change("471905", "582016", "582016"))
        assertTrue(lock.disable("582016"))
        assertFalse(lock.settings.lockEnabled)
        assertFalse(lock.isLocked)
    }

    @Test
    fun forgettingGoesBackToTheDefaults() {
        val storage = InMemoryDeviceSecurityStorage()
        val lock = lock(storage)
        lock.enable("471905", "471905")
        lock.setHideContent(false)
        lock.forget()
        assertNull(storage.value)
        assertEquals(DeviceSecuritySettings(), lock.settings)
        assertEquals(DeviceSecuritySettings(), DeviceSecuritySettings.decode("not json"))
    }

    @Test
    fun aPepperedHashNeedsThePepper() {
        val peppered = object : DeviceSecurityStorage by InMemoryDeviceSecurityStorage() {
            var key = 1
            override fun pepper(derived: ByteArray): ByteArray = ByteArray(derived.size) { (derived[it].toInt() xor key).toByte() }
        }
        val lock = lock(peppered)
        lock.enable("471905", "471905")
        lock.lockNow()
        peppered.key = 2
        assertIs<UnlockResult.Wrong>(lock.unlock("471905"), "without the Keystore key, the right code no longer matches")
        peppered.key = 1
        assertEquals(UnlockResult.Unlocked, lock.unlock("471905"))
    }
}
