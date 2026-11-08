package org.politisationpopulairefrancaise.server.contact

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

/** The wrong tries of the access token: a lock after five, forgotten on success or after the delay, and a bounded table. */
class CaseLocksTest {

    private class MovableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }

    private val clock = MovableClock(Instant.parse("2026-11-08T10:00:00Z"))

    @Test
    fun fiveWrongTriesLockTheNumberForFifteenMinutes() {
        val locks = CaseLocks(clock)
        repeat(4) { locks.failed("PPF-AAAAA-AAAAA") }
        assertNull(locks.lockedFor("PPF-AAAAA-AAAAA"))
        locks.failed("PPF-AAAAA-AAAAA")
        assertEquals(15 * 60L, locks.lockedFor("PPF-AAAAA-AAAAA"))
        clock.now = clock.now.plusSeconds(15 * 60L)
        assertNull(locks.lockedFor("PPF-AAAAA-AAAAA"))
    }

    @Test
    fun aRightTokenForgetsTheWrongTries() {
        val locks = CaseLocks(clock)
        repeat(4) { locks.failed("PPF-BBBBB-BBBBB") }
        locks.succeeded("PPF-BBBBB-BBBBB")
        locks.failed("PPF-BBBBB-BBBBB")
        assertNull(locks.lockedFor("PPF-BBBBB-BBBBB"))
    }

    @Test
    fun aTableFullOfLocksStopsGrowing() {
        val locks = CaseLocks(clock, maxFailures = 1, lockFor = 15.minutes, maxTracked = 3)
        listOf("PPF-00000-00001", "PPF-00000-00002", "PPF-00000-00003").forEach(locks::failed)
        assertEquals(3, locks.tracked)
        locks.failed("PPF-00000-00004")
        assertEquals(3, locks.tracked)
        assertNull(locks.lockedFor("PPF-00000-00004"))
        assertNotNull(locks.lockedFor("PPF-00000-00001"))
        // Once the locks are over, new numbers are tracked again.
        clock.now = clock.now.plusSeconds(15 * 60L)
        locks.failed("PPF-00000-00005")
        assertEquals(1, locks.tracked)
    }
}
