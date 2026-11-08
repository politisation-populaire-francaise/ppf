package org.politisationpopulairefrancaise.server.contact

import org.politisationpopulairefrancaise.core.contact.CaseDeadlines
import org.politisationpopulairefrancaise.core.contact.CaseStatus
import org.politisationpopulairefrancaise.core.contact.ClaimMotif
import org.politisationpopulairefrancaise.core.contact.DeadlineKind
import org.politisationpopulairefrancaise.server.backoffice.Permissions
import org.politisationpopulairefrancaise.server.backoffice.TeamActor
import org.politisationpopulairefrancaise.server.backoffice.nowSeconds
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * Who may see and handle a case in the back-office (contract, section 6), checked by the section before it
 * shows anything and again by [ContactTeam] before any change:
 *
 * - the section is open to `contact.triage` (moderators and administrators);
 * - the claims about the use of funds or a team member need `contact.restricted` (administrators);
 * - a member never sees a case of which they are the object (`concerns`), whatever their roles;
 * - the requests on one's data are handled (status, closing, extension) with `contact.data_rights`;
 * - the author of a contested moderation decision never handles the claim (PPF's rule, stricter than DSA,
 *   article 20, paragraph 6) and cannot be assigned it.
 */
object ContactRules {
    fun canSee(case: StoredCase, actor: TeamActor): Boolean =
        actor.has(Permissions.CONTACT_TRIAGE) &&
            (case.motif?.restricted != true || actor.has(Permissions.CONTACT_RESTRICTED)) &&
            case.concerns != actor.accountId

    fun canHandle(case: StoredCase, actor: TeamActor): Boolean =
        canSee(case, actor) &&
            (case.motif != ClaimMotif.DATA_RIGHTS || actor.has(Permissions.CONTACT_DATA_RIGHTS)) &&
            !(case.motif == ClaimMotif.MODERATION_DECISION && case.decisionAuthor == actor.accountId)
}

/** What a change of the team led to: done (with the journal details), or refused with a stable code ([ContactTexts.ERRORS]). */
sealed interface TeamOutcome {
    data class Done(val case: StoredCase, val details: Map<String, String> = emptyMap()) : TeamOutcome

    data class Refused(val code: String) : TeamOutcome
}

/**
 * The actions of the team on a case (section `contact` of the back-office), each in one transaction with the
 * row locked. They change metadata only; the journal line is written by the section, through the base. The
 * texts of the team (reasons) are bounded to 1,000 characters, like the reasons of the journal.
 */
class ContactTeam(private val store: ContactStore, private val clock: Clock) {

    private fun now(): Instant = clock.nowSeconds()

    private fun refused(code: String): Pair<StoredCase?, TeamOutcome> = null to TeamOutcome.Refused(code)

    private fun done(case: StoredCase, details: Map<String, String> = emptyMap()): Pair<StoredCase?, TeamOutcome> = case to TeamOutcome.Done(case, details)

    private fun change(number: String, block: (java.sql.Connection, StoredCase) -> Pair<StoredCase?, TeamOutcome>): TeamOutcome =
        store.change(number, block) ?: TeamOutcome.Refused("not_found")

    /** Assigns the case to [target] (whose permissions are [targetActor]), or to nobody. */
    fun assign(actor: TeamActor, number: String, target: UUID?, targetActor: TeamActor?): TeamOutcome = change(number) { _, case ->
        when {
            !ContactRules.canSee(case, actor) -> refused("forbidden")
            case.isAnonymized -> refused("anonymized")
            target != null && (targetActor == null || targetActor.accountId != target || !ContactRules.canHandle(case, targetActor)) -> refused("not_eligible")
            target == case.assignedTo -> refused("unchanged")
            else -> done(case.copy(assignedTo = target), mapOf("to" to (target?.toString() ?: "none")))
        }
    }

    fun setPriority(actor: TeamActor, number: String, priority: CasePriority): TeamOutcome = change(number) { _, case ->
        when {
            !ContactRules.canSee(case, actor) -> refused("forbidden")
            case.isAnonymized -> refused("anonymized")
            priority == case.priority -> refused("unchanged")
            else -> done(case.copy(priority = priority), mapOf("from" to case.priority.db, "to" to priority.db))
        }
    }

    /** Changes the status; `closed` closes the case (the durations of retention count from now), leaving it reopens it. */
    fun setStatus(actor: TeamActor, number: String, status: CaseStatus): TeamOutcome = change(number) { _, case ->
        val now = now()
        when {
            !ContactRules.canHandle(case, actor) -> refused(if (ContactRules.canSee(case, actor) && case.decisionAuthor == actor.accountId) "decision_author" else "forbidden")
            case.isAnonymized -> refused("anonymized")
            status == case.status -> refused("unchanged")
            else -> done(
                case.copy(status = status, closedAt = if (status == CaseStatus.CLOSED) now else null, lastActivityAt = now),
                mapOf("from" to case.status.wire, "to" to status.wire),
            )
        }
    }

    /** Extends a request on one's data by two months, within the first month, once, with a reason. */
    fun extend(actor: TeamActor, number: String, reason: String?): TeamOutcome = change(number) { _, case ->
        val now = now()
        val text = reason?.trim().orEmpty()
        when {
            !ContactRules.canHandle(case, actor) || !actor.has(Permissions.CONTACT_DATA_RIGHTS) -> refused("forbidden")
            case.isAnonymized -> refused("anonymized")
            !CaseDeadlines.canExtend(case.nature, case.receivedAt.epochSecond, now.epochSecond, case.extendedAt != null) || case.isClosed -> refused("not_extendable")
            text.isEmpty() || text.length > MAX_REASON -> refused("reason_required")
            else -> {
                val deadline = Instant.ofEpochSecond(CaseDeadlines.extendedDataRights(case.receivedAt.epochSecond))
                done(
                    case.copy(deadlineAt = deadline, deadlineKind = DeadlineKind.LEGAL, extendedAt = now, extensionReason = text),
                    mapOf("deadline" to deadline.epochSecond.toString()),
                )
            }
        }
    }

    /** Records the member who took the contested moderation decision (or nobody). */
    fun setDecisionAuthor(actor: TeamActor, number: String, author: UUID?): TeamOutcome = change(number) { _, case ->
        when {
            !ContactRules.canSee(case, actor) || case.motif != ClaimMotif.MODERATION_DECISION -> refused("forbidden")
            // The author of the decision cannot change who the author is.
            case.decisionAuthor == actor.accountId -> refused("decision_author")
            case.isAnonymized -> refused("anonymized")
            author == case.decisionAuthor -> refused("unchanged")
            author != null && author == case.assignedTo -> refused("assigned_to_member")
            else -> done(case.copy(decisionAuthor = author), mapOf("author" to (author?.toString() ?: "none")))
        }
    }

    /** Records the member a claim about a team member concerns (or nobody); that member then never sees the case. */
    fun setConcerns(actor: TeamActor, number: String, member: UUID?): TeamOutcome = change(number) { _, case ->
        when {
            !ContactRules.canSee(case, actor) || !actor.has(Permissions.CONTACT_RESTRICTED) || case.motif != ClaimMotif.TEAM_MEMBER -> refused("forbidden")
            member == actor.accountId -> refused("own_target")
            case.isAnonymized -> refused("anonymized")
            member == case.concerns -> refused("unchanged")
            member != null && member == case.assignedTo -> refused("assigned_to_member")
            // The journal says that the field changed, never whom it names: journal readers include administrators.
            else -> done(case.copy(concerns = member), mapOf("concerns" to if (member == null) "cleared" else "set"))
        }
    }

    // Legal hold (two people) ----------------------------------------------------------------------------

    /** Asks for a legal hold (or its renewal) until [reviewAt], one year at most from now; a second member confirms it. */
    fun requestHold(actor: TeamActor, number: String, reason: String?, reviewAt: Instant?): TeamOutcome = change(number) { connection, case ->
        val now = now()
        val text = reason?.trim().orEmpty()
        when {
            !ContactRules.canSee(case, actor) || !actor.has(Permissions.CONTACT_LEGAL_HOLD) -> refused("forbidden")
            case.isAnonymized -> refused("anonymized")
            text.isEmpty() || text.length > MAX_REASON -> refused("reason_required")
            reviewAt == null || !reviewAt.isAfter(now) || reviewAt.isAfter(latestReview(now)) -> refused("review_date")
            !store.insertHoldRequest(connection, HoldRequest(case.number, release = false, text, reviewAt, actor.accountId, now)) -> refused("already_requested")
            // The case itself does not change until a second member confirms.
            else -> null to TeamOutcome.Done(case, mapOf("review" to reviewAt.epochSecond.toString()))
        }
    }

    /** Asks for the release of the legal hold; a second member confirms it. */
    fun requestRelease(actor: TeamActor, number: String, reason: String?): TeamOutcome = change(number) { connection, case ->
        val text = reason?.trim().orEmpty()
        when {
            !ContactRules.canSee(case, actor) || !actor.has(Permissions.CONTACT_LEGAL_HOLD) -> refused("forbidden")
            !case.hasLegalHold -> refused("no_hold")
            text.isEmpty() || text.length > MAX_REASON -> refused("reason_required")
            !store.insertHoldRequest(connection, HoldRequest(case.number, release = true, text, null, actor.accountId, now())) -> refused("already_requested")
            else -> null to TeamOutcome.Done(case)
        }
    }

    /** A second member accepts the pending request: the hold is set (or lifted). */
    fun confirmHold(actor: TeamActor, number: String): TeamOutcome = change(number) { connection, case ->
        val now = now()
        val request = store.holdRequest(connection, number)
        when {
            !ContactRules.canSee(case, actor) || !actor.has(Permissions.CONTACT_LEGAL_HOLD) -> refused("forbidden")
            request == null || request.requestedAt.isBefore(now.minusSeconds(ContactSpace.HOLD_REQUEST_LIFETIME.inWholeSeconds)) -> refused("no_request")
            request.requestedBy == actor.accountId -> refused("own_request")
            case.isAnonymized -> refused("anonymized")
            request.release -> {
                store.deleteHoldRequest(connection, number)
                done(case.copy(legalHoldReason = null, legalHoldReviewAt = null, legalHoldAlertedAt = null), mapOf("action" to "release", "requested_by" to request.requestedBy.toString()))
            }
            !requireNotNull(request.reviewAt).isAfter(now) -> refused("review_date")
            else -> {
                val review = requireNotNull(request.reviewAt)
                store.deleteHoldRequest(connection, number)
                done(
                    case.copy(legalHoldReason = request.reason, legalHoldReviewAt = review, legalHoldAlertedAt = null),
                    mapOf("action" to "hold", "requested_by" to request.requestedBy.toString(), "review" to review.epochSecond.toString()),
                )
            }
        }
    }

    /** A second member refuses the pending request, or its author withdraws it. */
    fun dropHoldRequest(actor: TeamActor, number: String, own: Boolean): TeamOutcome = change(number) { connection, case ->
        val request = store.holdRequest(connection, number)
        when {
            !ContactRules.canSee(case, actor) || !actor.has(Permissions.CONTACT_LEGAL_HOLD) -> refused("forbidden")
            request == null -> refused("no_request")
            own && request.requestedBy != actor.accountId -> refused("forbidden")
            !own && request.requestedBy == actor.accountId -> refused("own_request")
            else -> {
                store.deleteHoldRequest(connection, number)
                null to TeamOutcome.Done(case, mapOf("action" to if (request.release) "release" else "hold"))
            }
        }
    }

    companion object {
        const val MAX_REASON: Int = 1_000

        /** A review date is one year after now at most. */
        fun latestReview(now: Instant): Instant = now.atOffset(ZoneOffset.UTC).plusYears(1).toInstant()
    }
}
