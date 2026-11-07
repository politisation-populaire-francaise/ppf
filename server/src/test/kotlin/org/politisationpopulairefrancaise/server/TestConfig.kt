package org.politisationpopulairefrancaise.server

import org.politisationpopulairefrancaise.core.revendications.checks.ProposalReviewModel
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.relay.RelayConfig
import org.politisationpopulairefrancaise.server.revendications.RevendicationsConfig
import org.politisationpopulairefrancaise.server.revendications.RevendicationsSetup
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Explicit settings for the tests, so that they never depend on the environment variables of the
 * machine running them and never start the periodic envelope cleanup against the shared database.
 */
// The creation of a room waits up to 2 minutes for its turn: on a loaded machine (several builds at once,
// continuous integration), eight simultaneous creations can wait more than the 10 seconds of production,
// and the tests check the order of admission, not that timeout.
val TEST_RELAY: RelayConfig = RelayConfig(cleanupInterval = Duration.ZERO, groupLockWait = 2.minutes)

/** A policy without any editor key. */
val NO_EDITORS: PublicationPolicy = PublicationPolicy(emptySet())

/** The review queue of demands without the periodic task, without AI model, with the default limits. */
val TEST_REVENDICATIONS_CONFIG: RevendicationsConfig = RevendicationsConfig(cleanupInterval = Duration.ZERO)

/** Settings of the review queue for a test: never read from the environment of the machine. */
fun testRevendications(
    config: RevendicationsConfig = TEST_REVENDICATIONS_CONFIG,
    model: ProposalReviewModel? = null,
    clock: Clock = Clock.systemUTC(),
): RevendicationsSetup = RevendicationsSetup(config, model, clock)
