package org.politisationpopulairefrancaise.core.agir.agenda

import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/** AG-49: the user's answer to a mobilization, kept on the device only. */
enum class AgendaResponse(val value: String) {
    /** "J'y serai". */
    GOING("going"),

    /** "Intéressé". */
    INTERESTED("interested"),
    ;

    companion object {
        fun fromValue(value: String): AgendaResponse? = entries.firstOrNull { it.value == value }
    }
}

/** Where one of the user's own proposals stands, as the server last said. */
enum class ProposalReview(val value: String) {
    /** Held for the team's review (answer 202, or `pending`). */
    REVIEW("review"),

    /** On the agenda (answer 201, or approved by the team). */
    PUBLISHED("published"),

    /** The team did not publish it. */
    REJECTED("rejected"),
    ;

    companion object {
        fun fromValue(value: String): ProposalReview? = entries.firstOrNull { it.value == value }
    }
}

/** The last known review of a proposal, with the team's reason for a rejection. */
data class ProposalRecord(val review: ProposalReview, val reason: String? = null)

/**
 * What the device alone knows about the agenda: the user's answers ("J'y serai", "Intéressé") and
 * the review of the user's own proposals. Answers reveal political opinions (GDPR, article 9):
 * they never leave the device, and [purgeResponses] erases them shortly after the event is over.
 */
interface AgendaState {
    /** Every answer, by address of mobilization. */
    fun responses(): Map<String, AgendaResponse>

    /** Records an answer, or removes it when [response] is null. [endsAt] is when the event is over. */
    fun respond(address: String, response: AgendaResponse?, endsAt: Long)

    /** Keeps the end of answered events up to date when a newer version moves them. */
    fun updateEnd(address: String, endsAt: Long)

    /** Erases the answers of events over before [before]. */
    fun purgeResponses(before: Long)

    fun clearResponses()

    /** The review of the user's proposals, by event id. */
    fun proposals(): Map<String, ProposalRecord>

    fun recordProposal(eventId: String, record: ProposalRecord)

    fun forgetProposal(eventId: String)
}

/** [AgendaState] in the local database (tables `agenda_response` and `agenda_proposal`). */
class AgendaStore(private val database: PpfDatabase, private val clock: () -> Long = ::currentEpochSeconds) : AgendaState {

    private val queries get() = database.agendaQueries

    override fun responses(): Map<String, AgendaResponse> =
        queries.selectResponses().executeAsList().mapNotNull { row -> AgendaResponse.fromValue(row.response)?.let { row.address to it } }.toMap()

    override fun respond(address: String, response: AgendaResponse?, endsAt: Long) {
        if (response == null) queries.deleteResponse(address) else queries.upsertResponse(address, response.value, endsAt, clock())
    }

    override fun updateEnd(address: String, endsAt: Long) {
        queries.updateResponseEnd(endsAt, address)
    }

    override fun purgeResponses(before: Long) {
        queries.deleteResponsesEndedBefore(before)
    }

    override fun clearResponses() {
        queries.deleteAllResponses()
    }

    fun countResponses(): Long = queries.countResponses().executeAsOne()

    override fun proposals(): Map<String, ProposalRecord> =
        queries.selectProposals().executeAsList().mapNotNull { row ->
            ProposalReview.fromValue(row.state)?.let { row.event_id to ProposalRecord(it, row.reason) }
        }.toMap()

    override fun recordProposal(eventId: String, record: ProposalRecord) {
        queries.upsertProposal(eventId, record.review.value, record.reason, clock())
    }

    override fun forgetProposal(eventId: String) {
        queries.deleteProposal(eventId)
    }

    fun countProposals(): Long = queries.countProposals().executeAsOne()
}

/** [AgendaState] in memory, for the life of the process (tests, previews, platforms without database). */
class InMemoryAgendaState : AgendaState {
    private val answers = LinkedHashMap<String, Pair<AgendaResponse, Long>>()
    private val reviews = LinkedHashMap<String, ProposalRecord>()

    override fun responses(): Map<String, AgendaResponse> = answers.mapValues { it.value.first }

    override fun respond(address: String, response: AgendaResponse?, endsAt: Long) {
        if (response == null) answers.remove(address) else answers[address] = response to endsAt
    }

    override fun updateEnd(address: String, endsAt: Long) {
        answers[address]?.let { answers[address] = it.first to endsAt }
    }

    override fun purgeResponses(before: Long) {
        answers.entries.removeAll { it.value.second < before }
    }

    override fun clearResponses() {
        answers.clear()
    }

    override fun proposals(): Map<String, ProposalRecord> = reviews.toMap()

    override fun recordProposal(eventId: String, record: ProposalRecord) {
        reviews[eventId] = record
    }

    override fun forgetProposal(eventId: String) {
        reviews.remove(eventId)
    }
}
