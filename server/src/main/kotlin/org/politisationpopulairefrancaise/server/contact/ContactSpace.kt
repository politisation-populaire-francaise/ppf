package org.politisationpopulairefrancaise.server.contact

import io.ktor.server.application.Application
import io.ktor.server.application.log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.politisationpopulairefrancaise.core.contact.CaseAccess
import org.politisationpopulairefrancaise.core.contact.ReportReason
import org.politisationpopulairefrancaise.core.contact.ContentReports
import org.politisationpopulairefrancaise.core.contact.TicketSubtype
import org.politisationpopulairefrancaise.core.contact.CaseNature
import org.politisationpopulairefrancaise.core.contact.CaseApi
import org.politisationpopulairefrancaise.core.contact.CaseCreated
import org.politisationpopulairefrancaise.core.contact.CaseCreation
import org.politisationpopulairefrancaise.core.contact.CaseDeadlines
import org.politisationpopulairefrancaise.core.contact.CaseEnvelopes
import org.politisationpopulairefrancaise.core.contact.CaseFollowUp
import org.politisationpopulairefrancaise.core.contact.CaseMessageAccepted
import org.politisationpopulairefrancaise.core.contact.CaseNumber
import org.politisationpopulairefrancaise.core.contact.CaseState
import org.politisationpopulairefrancaise.core.contact.CaseStatus
import org.politisationpopulairefrancaise.core.contact.ClaimMotif
import org.politisationpopulairefrancaise.core.crypto.constantTimeEquals
import org.politisationpopulairefrancaise.core.crypto.secureRandomBytes
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.backoffice.nowSeconds
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.sql.DataSource
import kotlin.time.Duration.Companion.days

/** A refusal of the API of the cases: HTTP status, stable code, English message. */
data class ContactRefusal(val status: Int, val code: String, val message: String)

sealed interface CreateOutcome {
    data class Created(val answer: CaseCreated) : CreateOutcome

    /** The same creation sent again: answered `200` with the case as it stands. */
    data class Again(val answer: CaseCreated) : CreateOutcome

    data class Refused(val refusal: ContactRefusal) : CreateOutcome
}

sealed interface ReadOutcome {
    data class Found(val state: CaseState) : ReadOutcome

    data class Refused(val refusal: ContactRefusal, val retryAfterSeconds: Long? = null) : ReadOutcome
}

sealed interface FollowUpOutcome {
    data class Accepted(val answer: CaseMessageAccepted, val created: Boolean) : FollowUpOutcome

    data class Refused(val refusal: ContactRefusal, val retryAfterSeconds: Long? = null) : FollowUpOutcome
}

/** What a retention pass did (counts only: nothing else is ever logged). */
data class RetentionReport(
    val closed: Int = 0,
    val requestsExpired: Int = 0,
    val holdsDue: Int = 0,
    val holdsLifted: Int = 0,
    val deleted: Int = 0,
    val anonymized: Int = 0,
) {
    val total: Int get() = closed + requestsExpired + holdsDue + holdsLifted + deleted + anonymized
}

/**
 * The cases on the server (M30; docs/integration/contact-reclamations.md, sections 4 to 6): checks of the
 * public API, the access by the token derived from the secret code (constant time, same answer for an
 * unknown number and a wrong token, a number locked 15 minutes after 5 wrong tries), and the retention
 * rules. The server reads no text of a case; the logs receive counts and class names only.
 */
class ContactSpace(
    private val store: ContactStore,
    val config: ContactConfig,
    private val clock: Clock,
) {
    /** The inbox of the environment (the space exists only when it is set). */
    val inbox: PublicKey = requireNotNull(config.inbox) { "The cases are open only with an inbox key" }

    /** In memory only: the wrong tries per number. */
    val locks: CaseLocks = CaseLocks(clock)

    /**
     * Called by the retention pass with the number of legal holds whose review date has come (for an alert of
     * the dashboard of the back-office); null without back-office.
     */
    @Volatile
    var onHoldsDue: ((Int) -> Unit)? = null

    /** What a wrong number is compared against, so that it takes as long as a wrong token. */
    private val decoy: ByteArray = CaseAccess.hash(secureRandomBytes(32))

    private fun now(): Instant = clock.nowSeconds()

    fun store(): ContactStore = store

    // Public API ---------------------------------------------------------------------------------------

    /** `POST /api/v1/cases`. Blocking (database). */
    fun create(request: CaseCreation): CreateOutcome {
        val now = now()
        val number = CaseNumber.fromDisplay(request.number) ?: return refused(BAD_NUMBER)
        val nature = request.nature ?: return refused(BAD_NATURE)
        if (!CaseAccess.isValidHash(request.accessHash)) return refused(BAD_ACCESS)
        if (!CaseAccess.isValidCaseKey(request.casePubkey)) return refused(BAD_CASE_KEY)
        val moderation = nature.motif == ClaimMotif.MODERATION_DECISION
        val decisionAt = request.decisionAt
        when {
            moderation && decisionAt == null -> return refused(DECISION_REQUIRED)
            !moderation && decisionAt != null -> return refused(BAD_DECISION)
            moderation && !CaseDeadlines.moderationClaimOpen(decisionAt!!, now.epochSecond) -> return refused(DECISION_OUT_OF_TIME)
        }
        // A report (S-38, D-234): its content and reason, in clear, for this subtype only and well formed.
        val isReport = nature == CaseNature.Ticket(TicketSubtype.REPORT)
        val reportContent = request.reportContent
        val reportReason = request.reportReason
        if (reportContent != null || reportReason != null) {
            if (!isReport || reportContent == null || !ContentReports.isContentAddress(reportContent) || ReportReason.fromWire(reportReason) == null) {
                return refused(BAD_REPORT)
            }
        }
        CaseEnvelopes.check(request.envelope, inbox.hex, now.epochSecond, config.workBits)?.let { return refused(ContactRefusal(422, it.code, it.message)) }
        val deadline = CaseDeadlines.of(nature, now.epochSecond, config.goals)
        val case = StoredCase(
            number = number.display,
            nature = nature,
            status = CaseStatus.RECEIVED,
            priority = CasePriority.NORMAL,
            assignedTo = null,
            receivedAt = now,
            deadlineAt = deadline?.let { Instant.ofEpochSecond(it.at) },
            deadlineKind = deadline?.kind,
            extendedAt = null,
            extensionReason = null,
            decisionAt = decisionAt?.let(Instant::ofEpochSecond),
            decisionAuthor = null,
            concerns = null,
            closedAt = null,
            legalHoldReason = null,
            legalHoldReviewAt = null,
            legalHoldAlertedAt = null,
            anonymizedAt = null,
            accessHash = request.accessHash,
            casePubkey = request.casePubkey,
            lastActivityAt = now,
            reportContent = reportContent,
            reportReason = reportReason,
        )
        return when (val stored = store.create(case, request.envelope)) {
            ContactStore.Created.Stored -> CreateOutcome.Created(created(case))
            is ContactStore.Created.Again -> CreateOutcome.Again(created(stored.case))
            ContactStore.Created.NumberTaken -> refused(NUMBER_TAKEN)
            ContactStore.Created.EnvelopeTaken -> refused(ENVELOPE_TAKEN)
        }
    }

    /** `GET /api/v1/cases/{number}`. Blocking (database). */
    fun state(numberText: String, authorization: String?, after: String?): ReadOutcome {
        val case = when (val access = authorize(numberText, authorization)) {
            is Access.Granted -> access.case
            is Access.Denied -> return ReadOutcome.Refused(access.refusal, access.retryAfter)
        }
        val afterId = after?.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
        val (envelopes, more) = store.outgoing(case.number, afterId, CaseApi.MAX_ENVELOPES_PER_ANSWER)
        return ReadOutcome.Found(
            CaseState(
                number = case.number,
                status = case.status,
                deadlineAt = case.deadlineAt?.epochSecond,
                deadlineKind = case.deadlineKind,
                extended = case.extendedAt != null,
                envelopes = envelopes,
                more = more,
            ),
        )
    }

    /** `POST /api/v1/cases/{number}/messages`. Blocking (database). */
    fun followUp(numberText: String, authorization: String?, request: CaseFollowUp): FollowUpOutcome {
        val case = when (val access = authorize(numberText, authorization)) {
            is Access.Granted -> access.case
            is Access.Denied -> return FollowUpOutcome.Refused(access.refusal, access.retryAfter)
        }
        if (case.isClosed) return FollowUpOutcome.Refused(CASE_CLOSED)
        val now = now()
        CaseEnvelopes.check(request.envelope, inbox.hex, now.epochSecond)?.let { return FollowUpOutcome.Refused(ContactRefusal(422, it.code, it.message)) }
        val added = store.addIncoming(case.number, request.envelope, now) { current ->
            val status = if (current.status == CaseStatus.ANSWERED || current.status == CaseStatus.AWAITING_YOU) CaseStatus.IN_PROGRESS else current.status
            current.copy(status = status, lastActivityAt = now)
        }
        return when (added) {
            is ContactStore.Added.Stored -> FollowUpOutcome.Accepted(CaseMessageAccepted(request.envelope.id, added.case.status), created = true)
            is ContactStore.Added.Again -> FollowUpOutcome.Accepted(CaseMessageAccepted(request.envelope.id, added.case.status), created = false)
            ContactStore.Added.Closed -> FollowUpOutcome.Refused(CASE_CLOSED)
            ContactStore.Added.EnvelopeTaken -> FollowUpOutcome.Refused(ENVELOPE_TAKEN)
            ContactStore.Added.Gone -> FollowUpOutcome.Refused(NOT_FOUND)
        }
    }

    private sealed interface Access {
        data class Granted(val case: StoredCase) : Access

        data class Denied(val refusal: ContactRefusal, val retryAfter: Long? = null) : Access
    }

    /**
     * The case of [numberText] when [authorization] carries its token. An unknown number, an anonymised claim
     * and a wrong token give the same answer, after the same comparison; 5 wrong tries lock the number (known
     * or not) for 15 minutes.
     */
    private fun authorize(numberText: String, authorization: String?): Access {
        val number = CaseNumber.fromDisplay(numberText) ?: return Access.Denied(NOT_FOUND)
        locks.lockedFor(number.display)?.let { return Access.Denied(LOCKED, it) }
        val token = CaseAccess.tokenFromAuthorization(authorization)
        val presented = token?.let(CaseAccess::hash) ?: ByteArray(decoy.size)
        val case = store.find(number.display)
        val expected = case?.accessHash?.hexToByteArray() ?: decoy
        val matches = constantTimeEquals(presented, expected)
        if (!matches || token == null || case?.accessHash == null) {
            locks.failed(number.display)
            return Access.Denied(NOT_FOUND)
        }
        locks.succeeded(number.display)
        return Access.Granted(case)
    }

    private fun created(case: StoredCase) = CaseCreated(case.number, case.status, case.deadlineAt?.epochSecond, case.deadlineKind)

    private fun refused(refusal: ContactRefusal) = CreateOutcome.Refused(refusal)

    // Retention ----------------------------------------------------------------------------------------

    /**
     * One pass of the retention rules (contract, section 6; S-109), in this order: cases awaiting the person
     * for 60 days closed; hold requests older than 7 days dropped; holds whose review date has come marked
     * (once, with an alert); holds 30 days past their review date and not renewed lifted; tickets and contacts
     * closed 3 years ago deleted; claims closed 5 years ago anonymised. Nothing under an active hold is
     * deleted nor anonymised.
     */
    fun retentionOnce(): RetentionReport {
        val now = now()
        val utc = now.atOffset(ZoneOffset.UTC)
        val report = RetentionReport(
            closed = store.closeSilent(now.minusSeconds(SILENCE_BEFORE_CLOSING.inWholeSeconds), now),
            requestsExpired = store.expireHoldRequests(now.minusSeconds(HOLD_REQUEST_LIFETIME.inWholeSeconds)),
            holdsDue = store.markHoldsDue(now),
            holdsLifted = store.liftLapsedHolds(now.minusSeconds(HOLD_RENEWAL_GRACE.inWholeSeconds)),
            deleted = store.deleteClosedMessages(utc.minusYears(MESSAGES_KEPT_YEARS).toInstant()),
            anonymized = store.anonymizeClosedClaims(utc.minusYears(CLAIMS_KEPT_YEARS).toInstant(), now),
        )
        if (report.holdsDue > 0) onHoldsDue?.invoke(report.holdsDue)
        return report
    }

    companion object {
        /** Body of a creation: an envelope of 96 KiB at most, written by another JSON library, and a few fields. */
        const val MAX_CREATION_BYTES: Long = 128 * 1024
        const val MAX_MESSAGE_BYTES: Long = 128 * 1024

        val SILENCE_BEFORE_CLOSING = 60.days
        val HOLD_REQUEST_LIFETIME = 7.days

        /** A hold past its review date still protects the case this long, for the team to renew it. */
        val HOLD_RENEWAL_GRACE = 30.days
        const val MESSAGES_KEPT_YEARS: Long = 3
        const val CLAIMS_KEPT_YEARS: Long = 5

        val NOT_FOUND = ContactRefusal(404, "not_found", "No case with this number and this code.")
        val LOCKED = ContactRefusal(429, "locked", "Too many wrong codes for this number; try again later.")
        val BAD_NUMBER = ContactRefusal(422, "bad_number", "A case number is PPF-XXXXX-XXXXX in Crockford's base 32.")
        val BAD_REPORT = ContactRefusal(422, "bad_report", "report_content and report_reason belong to a report only: the address of a content and a known reason.")
        val BAD_NATURE = ContactRefusal(422, "bad_nature", "A case has a type and exactly the sub-type, category or motif of that type.")
        val BAD_ACCESS = ContactRefusal(422, "bad_access_hash", "access_hash is a SHA-256 in lowercase hexadecimal.")
        val BAD_CASE_KEY = ContactRefusal(422, "bad_case_pubkey", "case_pubkey is an x-only secp256k1 public key in lowercase hexadecimal.")
        val DECISION_REQUIRED = ContactRefusal(422, "decision_required", "A claim against a moderation decision gives the date of the decision.")
        val BAD_DECISION = ContactRefusal(422, "bad_decision", "Only a claim against a moderation decision gives a decision date.")
        val DECISION_OUT_OF_TIME = ContactRefusal(422, "decision_out_of_time", "A moderation decision may be contested until six months after it.")
        val NUMBER_TAKEN = ContactRefusal(409, "number_taken", "This number is taken: draw another one.")
        val ENVELOPE_TAKEN = ContactRefusal(409, "envelope_taken", "This envelope belongs to another case.")
        val CASE_CLOSED = ContactRefusal(409, "case_closed", "This case is closed.")

        fun start(application: Application, dataSource: DataSource, setup: ContactSetup): ContactSpace? {
            val config = setup.config
            if (!config.open) {
                application.log.info("Contact and claims: closed (${config.closedReason ?: "no inbox key"}).")
                return null
            }
            val space = ContactSpace(ContactStore(dataSource), config, setup.clock)
            if (config.isExample) application.log.warn("Contact and claims: the EXAMPLE inbox, which anybody can read. Development only.")
            val interval = config.retentionInterval
            if (interval.isPositive()) {
                application.launch(Dispatchers.IO) {
                    while (isActive) {
                        try {
                            val report = space.retentionOnce()
                            if (report.total > 0) {
                                application.log.info(
                                    "Contact retention: ${report.closed} closed, ${report.deleted} deleted, ${report.anonymized} anonymised, " +
                                        "${report.holdsDue} hold(s) to review, ${report.holdsLifted} hold(s) lifted, ${report.requestsExpired} request(s) expired.",
                                )
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            application.log.warn("Contact retention failed (${e::class.simpleName}); next attempt in $interval.")
                        }
                        delay(interval)
                    }
                }
            }
            return space
        }
    }
}
