package org.politisationpopulairefrancaise.core

import kotlinx.serialization.Serializable

/**
 * Answer of the server health check endpoint (GET /health).
 * Shared with the applications so that both sides use the same schema.
 */
@Serializable
data class HealthStatus(
    val status: String,
    val service: String,
    val version: String,
    val environment: String,
) {
    companion object {
        const val OK: String = "ok"
    }
}
