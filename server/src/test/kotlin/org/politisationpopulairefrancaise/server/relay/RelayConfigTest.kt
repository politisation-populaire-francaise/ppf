package org.politisationpopulairefrancaise.server.relay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class RelayConfigTest {

    @Test
    fun defaultsFollowDecisionD028() {
        val config = RelayConfig.fromEnvironment { null }
        assertEquals(90.days, config.wrapRetention, "three months at least")
        assertEquals(365.days, config.wrapRetentionMax)
        assertEquals(5_000, config.wrapMaxPerRecipient)
        assertEquals(1.hours, config.cleanupInterval)
        assertEquals(60, config.postRatePerMinute)
        assertEquals(600, config.globalPostRatePerMinute)
    }

    @Test
    fun readsTheEnvironmentAndIgnoresNonsense() {
        val values = mapOf(
            RelayConfig.WRAP_RETENTION_VARIABLE to "120",
            RelayConfig.WRAP_RETENTION_MAX_VARIABLE to "400",
            RelayConfig.CLEANUP_INTERVAL_VARIABLE to "0",
            RelayConfig.WRAP_MAX_PER_RECIPIENT_VARIABLE to "abc",
            RelayConfig.POST_RATE_VARIABLE to "-3",
            RelayConfig.GLOBAL_POST_RATE_VARIABLE to "1200",
        )
        val config = RelayConfig.fromEnvironment(values::get)
        assertEquals(120.days, config.wrapRetention)
        assertEquals(400.days, config.wrapRetentionMax)
        assertEquals(0.minutes, config.cleanupInterval)
        assertEquals(5_000, config.wrapMaxPerRecipient, "invalid: default")
        assertEquals(60, config.postRatePerMinute, "invalid: default")
        assertEquals(1200, config.globalPostRatePerMinute)
    }

    @Test
    fun aMaximumBelowTheMinimumIsRaisedToIt() {
        val values = mapOf(RelayConfig.WRAP_RETENTION_VARIABLE to "500", RelayConfig.WRAP_RETENTION_MAX_VARIABLE to "365")
        val config = RelayConfig.fromEnvironment(values::get)
        assertEquals(500.days, config.wrapRetention)
        assertEquals(500.days, config.wrapRetentionMax)
        assertFailsWith<IllegalArgumentException> { RelayConfig(wrapRetention = 90.days, wrapRetentionMax = 30.days) }
    }
}
