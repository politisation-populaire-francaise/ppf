package org.politisationpopulairefrancaise.core.publication

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Contents held for review by a person before publication (mobilizations proposed by contributors,
 * calls of accounts that are not verified organisations, calls for witnesses): the server answers
 * `202` to `POST /api/v1/events` ([PublicationResult.Accepted] with `heldForReview`), then tells the
 * author where each one stands through `GET /api/v1/held?id=…` (contract of the Agir space,
 * docs/integration/agir-contrat.md). The demands of the wall keep their own route
 * (`/api/v1/revendications/proposals`) because their report of automatic checks is richer.
 */
@Serializable
enum class HeldStatus {
    @SerialName("pending") PENDING,
    @SerialName("approved") APPROVED,
    @SerialName("rejected") REJECTED,
}

@Serializable
data class HeldState(
    /** The id of the signed event that was sent. */
    val id: String,
    val status: HeldStatus,
    /** Seconds since 1970. */
    @SerialName("received_at") val receivedAt: Long,
    @SerialName("decided_at") val decidedAt: Long? = null,
    /** The team's reason, for a rejection, shown to the author. */
    val reason: String? = null,
)

sealed interface HeldStatuses {
    /** The held contents the server knows; an id it does not know is simply absent (deleted 30 days after the decision). */
    data class Known(val states: List<HeldState>) : HeldStatuses

    data class Unavailable(val cause: Throwable? = null) : HeldStatuses
}

/** Implemented by the server adapter ([ServerPublicationPort]); a fake in tests. */
interface HeldStatusSource {
    suspend fun heldStatuses(ids: List<String>): HeldStatuses

    companion object {
        /** The server answers for at most this many ids per request. */
        const val MAX_IDS: Int = 20
    }
}
