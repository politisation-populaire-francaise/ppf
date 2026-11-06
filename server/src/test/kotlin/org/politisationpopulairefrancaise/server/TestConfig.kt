package org.politisationpopulairefrancaise.server

import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.relay.RelayConfig
import kotlin.time.Duration

/**
 * Explicit settings for the tests, so that they never depend on the environment variables of the
 * machine running them and never start the periodic envelope cleanup against the shared database.
 */
val TEST_RELAY: RelayConfig = RelayConfig(cleanupInterval = Duration.ZERO)

/** A policy without any editor key. */
val NO_EDITORS: PublicationPolicy = PublicationPolicy(emptySet())
