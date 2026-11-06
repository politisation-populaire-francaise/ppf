package org.politisationpopulairefrancaise.core.revendications

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.politisationpopulairefrancaise.core.revendications.checks.ProposalReport

/** Where a citizen demand sent for review stands on the server (decision of the owner, 2026-11-05: review before publication). */
@Serializable
enum class ProposalStatus {
    /** Waiting for the team's review. */
    @SerialName("pending") PENDING,

    /** Published on the wall. */
    @SerialName("approved") APPROVED,

    /** Not published; the reason says why, and the author may contest (DSA). */
    @SerialName("rejected") REJECTED,
}

/** What the server says about one proposal (GET /api/v1/revendications/proposals, see docs/FORMATS.md). */
@Serializable
data class ProposalState(
    /** The id of the signed demand event. */
    val id: String,
    val status: ProposalStatus,
    /** Seconds since 1970. */
    @SerialName("received_at") val receivedAt: Long,
    @SerialName("decided_at") val decidedAt: Long? = null,
    /** The team's reason, for a rejection. */
    val reason: String? = null,
    /** The automatic checks the server ran (null until they ran): the AI model's opinions appear here. */
    val report: ProposalReport? = null,
)

sealed interface ProposalStatuses {
    /** The proposals the server knows; an id it does not know is simply absent (deleted 30 days after the decision). */
    data class Known(val states: List<ProposalState>) : ProposalStatuses

    data class Unavailable(val cause: Throwable? = null) : ProposalStatuses
}

/**
 * Asks the server where the author's proposals stand. Only the ids travel: the server knows them
 * already, and an id says nothing to anyone who has not seen the event.
 */
interface ProposalStatusSource {
    suspend fun proposalStatuses(ids: List<String>): ProposalStatuses

    companion object {
        /** The server answers for at most this many ids per request. */
        const val MAX_IDS: Int = 20
    }
}
