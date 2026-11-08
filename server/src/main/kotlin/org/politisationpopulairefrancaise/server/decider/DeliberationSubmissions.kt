package org.politisationpopulairefrancaise.server.decider

import kotlinx.serialization.Serializable
import org.politisationpopulairefrancaise.core.decider.Amendment
import org.politisationpopulairefrancaise.core.decider.Deliberation
import org.politisationpopulairefrancaise.core.decider.DeliberationFormats
import org.politisationpopulairefrancaise.core.decider.DeliberationPhase
import org.politisationpopulairefrancaise.core.decider.Proposition
import org.politisationpopulairefrancaise.core.revendications.checks.DraftRules
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.events.EventFilter
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.StoreOutcome
import java.sql.Connection
import java.sql.ResultSet
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import javax.sql.DataSource

/** One contribution held for review (table deliberation_submission). */
data class Submission(
    val id: String,
    val pubkey: String,
    val identifier: String,
    val type: String,
    val deliberation: String,
    val parent: String,
    val event: SignedEvent,
    val status: String,
    val receivedDay: LocalDate,
    val decidedDay: LocalDate?,
    val reason: String?,
)

/** What the author reads of their contribution (`GET /api/v1/deliberations/submissions/{id}`). */
@Serializable
data class SubmissionStatus(val id: String, val status: String, val reason: String? = null)

/**
 * The contributions of citizens to a deliberation (DE-25, docs/integration/deliberation.md, section 2):
 * a proposition (during the phase of propositions) or an amendment (during the phase of amendments),
 * signed by a key outside the editorial team, waits for the review of a person before publication, like
 * the demands. The reviewer checks the charter and the legal feasibility, never the team's preferences.
 *
 * Refused at once: a malformed contribution, personal data in it (the same rule as the demands), a
 * deliberation that is not the team's or not in the right phase, a proposition passed off as PPF's starting
 * one, a second proposition of the same account in a deliberation, a fourth amendment of the same account,
 * too many contributions waiting. Nothing about the request is kept, only the day of reception.
 */
class DeliberationSubmissions(
    private val dataSource: DataSource,
    private val events: EventRepository,
    private val isEditor: (String) -> Boolean,
    private val clock: Clock = Clock.systemUTC(),
) {
    sealed interface Outcome {
        data object Queued : Outcome

        data object AlreadyPending : Outcome

        /** [code] is stable and safe to return; never a value of the contribution. */
        data class Refused(val status: Int, val code: String, val message: String) : Outcome
    }

    private fun today(): LocalDate = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC)

    /** Receives [event], already verified (signature) by the API. */
    fun submit(event: SignedEvent): Outcome {
        val type = event.firstTagValue(DeliberationFormats.TAG_TYPE) ?: return MALFORMED
        val identifier = event.firstTagValue("d")?.takeIf { it.isNotEmpty() && it.length <= MAX_IDENTIFIER }
            ?: return Outcome.Refused(400, "malformed_contribution", "A contribution carries a d tag of 1 to $MAX_IDENTIFIER characters.")
        val now = clock.instant().epochSecond
        val (deliberation, parent, phase, texts) = when (type) {
            DeliberationFormats.TYPE_PROPOSITION -> {
                val proposition = Proposition.parse(event) ?: return MALFORMED
                if (proposition.isStart) return Outcome.Refused(403, "starting_proposal_reserved", "Only the team publishes the starting proposal of a deliberation.")
                val deliberation = deliberation(proposition.deliberation) ?: return UNKNOWN
                Quad(deliberation, deliberation.address.toString(), DeliberationPhase.PROPOSITIONS, listOfNotNull(proposition.title, proposition.text, proposition.justification))
            }
            DeliberationFormats.TYPE_AMENDMENT -> {
                val amendment = Amendment.parse(event) ?: return MALFORMED
                val proposition = published(amendment.proposition)?.let(Proposition::parse) ?: return UNKNOWN
                val deliberation = deliberation(proposition.deliberation) ?: return UNKNOWN
                Quad(deliberation, proposition.address.toString(), DeliberationPhase.AMENDMENTS, listOfNotNull(amendment.text, amendment.justification))
            }
            else -> return MALFORMED
        }
        if (deliberation.phaseAt(now) != phase) return Outcome.Refused(409, "phase_closed", "This deliberation does not take this contribution now (see its phases).")
        // The identifier becomes part of the public address: no personal data there either.
        if ((texts + identifier).any(DraftRules::containsPersonalData)) {
            return Outcome.Refused(400, "personal_data", "A contribution never carries personal data (phone, e-mail, address, identity number).")
        }
        return transaction { c ->
            lock(c, "deliberation_submission:${event.pubkey}")
            existing(c, event.id)?.let { status -> return@transaction if (status == PENDING) Outcome.AlreadyPending else Outcome.Refused(409, "already_decided", "This contribution was already decided.") }
            val others = countOthers(c, deliberation.address.toString(), event.pubkey, type, identifier)
            if (type == DeliberationFormats.TYPE_PROPOSITION && others >= DeliberationFormats.PROPOSITIONS_PER_ACCOUNT) {
                return@transaction Outcome.Refused(409, "one_proposition_per_account", "An account makes one proposition per deliberation.")
            }
            if (type == DeliberationFormats.TYPE_AMENDMENT && others >= DeliberationFormats.AMENDMENTS_PER_ACCOUNT) {
                return@transaction Outcome.Refused(409, "three_amendments_per_account", "An account makes at most ${DeliberationFormats.AMENDMENTS_PER_ACCOUNT} amendments per deliberation.")
            }
            val previous = pendingVersion(c, event.pubkey, identifier)
            if (previous != null && previous.second >= event.createdAt) return@transaction Outcome.Refused(409, "superseded", "A newer version of this contribution is already waiting.")
            if (previous == null) {
                if (countPending(c, event.pubkey) >= MAX_PENDING_PER_KEY) return@transaction Outcome.Refused(429, "too_many_pending", "Too many contributions of this account are waiting for review.")
                lock(c, GLOBAL_LOCK)
                if (countPending(c, null) >= MAX_PENDING) return@transaction Outcome.Refused(429, "too_many_pending", "Too many contributions are waiting for review; try again later.")
            } else {
                c.prepareStatement("DELETE FROM deliberation_submission WHERE id = ?").use { s ->
                    s.setString(1, previous.first)
                    s.executeUpdate()
                }
            }
            c.prepareStatement(
                """
                INSERT INTO deliberation_submission (id, pubkey, identifier, type, deliberation, parent, created_at, event, status, received_day)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'pending', ?)
                """.trimIndent(),
            ).use { s ->
                s.setString(1, event.id)
                s.setString(2, event.pubkey)
                s.setString(3, identifier)
                s.setString(4, type)
                s.setString(5, deliberation.address.toString())
                s.setString(6, parent)
                s.setLong(7, event.createdAt)
                s.setString(8, event.toJson())
                s.setObject(9, today())
                s.executeUpdate()
            }
            Outcome.Queued
        }
    }

    /** The deliberation of the editorial keys at [address], or null. */
    fun deliberation(address: ContentAddress): Deliberation? {
        if (!isEditor(address.pubkey)) return null
        return published(address)?.let(Deliberation::parse)
    }

    private fun published(address: ContentAddress): SignedEvent? =
        events.query(EventFilter(kinds = listOf(address.kind), authors = listOf(address.pubkey), identifiers = listOf(address.identifier), limit = 1)).firstOrNull()

    fun find(id: String): Submission? = dataSource.connection.use { c ->
        c.prepareStatement("SELECT * FROM deliberation_submission WHERE id = ?").use { s ->
            s.setString(1, id)
            s.executeQuery().use { r -> if (r.next()) r.submission() else null }
        }
    }

    fun pending(offset: Int, limit: Int): List<Submission> = dataSource.connection.use { c ->
        c.prepareStatement("SELECT * FROM deliberation_submission WHERE status = 'pending' ORDER BY received_day, created_at, id OFFSET ? LIMIT ?").use { s ->
            s.setInt(1, offset)
            s.setInt(2, limit)
            s.executeQuery().use { r -> buildList { while (r.next()) add(r.submission()) } }
        }
    }

    fun pendingCount(): Int = dataSource.connection.use { c -> countPending(c, null) }

    /** What the author may read: the status and, when refused, the reason. */
    fun status(id: String): SubmissionStatus? = find(id)?.let { SubmissionStatus(it.id, it.status, it.reason) }

    sealed interface Decision {
        data object Done : Decision

        data object NotFound : Decision

        data object AlreadyDecided : Decision

        data class Refused(val message: String) : Decision
    }

    /**
     * Publishes a contribution exactly as its author signed it, while its phase is still open (after it, it
     * would get no ballot box: the reviewer refuses it with a reason instead).
     */
    fun approve(id: String): Decision {
        val submission = find(id) ?: return Decision.NotFound
        if (submission.status != PENDING) return Decision.AlreadyDecided
        val deliberation = runCatching { ContentAddress.parse(submission.deliberation) }.getOrNull()?.let(::deliberation)
            ?: return Decision.Refused("Cette délibération n'existe plus.")
        val phase = if (submission.type == DeliberationFormats.TYPE_PROPOSITION) DeliberationPhase.PROPOSITIONS else DeliberationPhase.AMENDMENTS
        if (deliberation.phaseAt(clock.instant().epochSecond) != phase) {
            return Decision.Refused("La phase de cette contribution est terminée : elle n'aurait pas d'urne. Refusez-la en le disant à son auteur.")
        }
        return transaction { c ->
            if (!decide(c, id, APPROVED, null)) return@transaction Decision.AlreadyDecided
            when (events.store(submission.event)) {
                StoreOutcome.STORED, StoreOutcome.DUPLICATE -> Decision.Done
                StoreOutcome.SUPERSEDED -> {
                    c.rollback()
                    Decision.Refused("Une version plus récente de cette contribution est déjà publiée.")
                }
            }
        }
    }

    fun reject(id: String, reason: String): Decision {
        find(id) ?: return Decision.NotFound
        return dataSource.connection.use { c -> if (decide(c, id, REJECTED, reason)) Decision.Done else Decision.AlreadyDecided }
    }

    /** Deletes the contributions decided more than [RETENTION_DAYS] days ago; returns how many. */
    fun purgeDecided(): Int = dataSource.connection.use { c ->
        c.prepareStatement("DELETE FROM deliberation_submission WHERE status <> 'pending' AND decided_day < ?").use { s ->
            s.setObject(1, today().minusDays(RETENTION_DAYS))
            s.executeUpdate()
        }
    }

    private fun decide(c: Connection, id: String, status: String, reason: String?): Boolean =
        c.prepareStatement("UPDATE deliberation_submission SET status = ?, reason = ?, decided_day = ? WHERE id = ? AND status = 'pending'").use { s ->
            s.setString(1, status)
            s.setString(2, reason)
            s.setObject(3, today())
            s.setString(4, id)
            s.executeUpdate() == 1
        }

    private fun existing(c: Connection, id: String): String? =
        c.prepareStatement("SELECT status FROM deliberation_submission WHERE id = ?").use { s ->
            s.setString(1, id)
            s.executeQuery().use { r -> if (r.next()) r.getString(1) else null }
        }

    /** The other contributions (other addresses) of [pubkey] of [type] in [deliberation], waiting or approved. */
    private fun countOthers(c: Connection, deliberation: String, pubkey: String, type: String, identifier: String): Int =
        c.prepareStatement(
            "SELECT count(DISTINCT identifier) FROM deliberation_submission WHERE deliberation = ? AND pubkey = ? AND type = ? AND identifier <> ? AND status IN ('pending', 'approved')",
        ).use { s ->
            s.setString(1, deliberation)
            s.setString(2, pubkey)
            s.setString(3, type)
            s.setString(4, identifier)
            s.executeQuery().use { r -> r.next(); r.getInt(1) }
        }

    private fun pendingVersion(c: Connection, pubkey: String, identifier: String): Pair<String, Long>? =
        c.prepareStatement("SELECT id, created_at FROM deliberation_submission WHERE pubkey = ? AND identifier = ? AND status = 'pending' FOR UPDATE").use { s ->
            s.setString(1, pubkey)
            s.setString(2, identifier)
            s.executeQuery().use { r -> if (r.next()) r.getString(1) to r.getLong(2) else null }
        }

    private fun countPending(c: Connection, pubkey: String?): Int =
        c.prepareStatement("SELECT count(*) FROM deliberation_submission WHERE status = 'pending'" + if (pubkey != null) " AND pubkey = ?" else "").use { s ->
            if (pubkey != null) s.setString(1, pubkey)
            s.executeQuery().use { r -> r.next(); r.getInt(1) }
        }

    private fun lock(c: Connection, name: String) {
        c.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))").use { s ->
            s.setString(1, name)
            s.executeQuery().close()
        }
    }

    private fun <T> transaction(block: (Connection) -> T): T = dataSource.connection.use { c ->
        val autoCommit = c.autoCommit
        c.autoCommit = false
        try {
            block(c).also { if (!c.autoCommit) c.commit() }
        } catch (e: Exception) {
            c.rollback()
            throw e
        } finally {
            c.autoCommit = autoCommit
        }
    }

    private fun ResultSet.submission() = Submission(
        id = getString("id"),
        pubkey = getString("pubkey"),
        identifier = getString("identifier"),
        type = getString("type"),
        deliberation = getString("deliberation"),
        parent = getString("parent"),
        event = SignedEvent.fromJson(getString("event")),
        status = getString("status"),
        receivedDay = getObject("received_day", LocalDate::class.java),
        decidedDay = getObject("decided_day", LocalDate::class.java),
        reason = getString("reason"),
    )

    private data class Quad(val deliberation: Deliberation, val parent: String, val phase: DeliberationPhase, val texts: List<String>)

    companion object {
        const val PENDING: String = "pending"
        const val APPROVED: String = "approved"
        const val REJECTED: String = "rejected"
        const val MAX_IDENTIFIER: Int = 128
        const val MAX_PENDING_PER_KEY: Int = 5
        const val MAX_PENDING: Int = 2_000
        const val RETENTION_DAYS: Long = 30
        private const val GLOBAL_LOCK = "deliberation_submission:all"
        private val MALFORMED = Outcome.Refused(400, "malformed_contribution", "Not a proposition or an amendment of a deliberation (see docs/FORMATS.md).")
        private val UNKNOWN = Outcome.Refused(404, "unknown_deliberation", "No deliberation of the team for this contribution.")

        /** A proposition or an amendment of a deliberation signed by a key outside the editorial team. */
        fun isCitizenContribution(event: SignedEvent, isEditor: (String) -> Boolean): Boolean =
            event.kind == EventKinds.LONG_FORM_ARTICLE && !isEditor(event.pubkey) &&
                event.firstTagValue(DeliberationFormats.TAG_TYPE) in setOf(DeliberationFormats.TYPE_PROPOSITION, DeliberationFormats.TYPE_AMENDMENT)
    }
}
