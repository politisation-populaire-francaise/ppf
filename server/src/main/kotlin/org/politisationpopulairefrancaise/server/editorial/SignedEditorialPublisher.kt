package org.politisationpopulairefrancaise.server.editorial

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialApprovals
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialEngine
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSchemas
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSpace
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.platform.DraftAuthor
import org.politisationpopulairefrancaise.core.publication.PublicationPort
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.server.events.EventFilter
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.signing.DraftSubmission
import org.politisationpopulairefrancaise.server.signing.HttpPlatformSigner
import org.politisationpopulairefrancaise.server.signing.PlatformSigner
import org.politisationpopulairefrancaise.server.signing.SigningStatus
import org.politisationpopulairefrancaise.server.signing.SigningTicket
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * The editorial publisher of step 5 on the platform signature ([PlatformSigner], contract 1.2, D-129;
 * docs/integration/circuit-editorial.md, section 6). Never a content published unsigned, never a signed
 * event changed:
 *
 * - [handOver]: `currentKey(use)` (null: [PublisherAnswer.KeyUnavailable], nothing sent); the unsigned event
 *   built by [EditorialEngine.build] from the approved version with that key, `created_at` strictly after the
 *   published version of the address and after every earlier submission; for an update, the `ppf-correction`
 *   tags of the published version followed by the new one (date, summary of the editor, nature); then
 *   `submitDraft(unsigned, use, doubleValidation, author)`, kept in `editorial_signing`: one approval for an
 *   ordinary content, two for a content of M17 ([ReadyToSign.approvalsFor]), the author always named;
 * - [state], at each opening of the review page (no background loop): `draftStatus`, then, when ready,
 *   `request` and `status`; a signed event is checked (same key and id as submitted, valid signature, exactly
 *   the content submitted) and published unchanged through the single [PublicationPort];
 * - [retry]: after an expiry or a refusal, a new event (new `created_at`).
 *
 * An update keeps the address `30023:<key>:<slug>` of the published content: a version of another key, or
 * a published version as recent as the new one, is refused. A transient failure of the service or of the
 * port leaves everything in place for the next reload.
 */
class SignedEditorialPublisher(
    private val signer: PlatformSigner,
    private val drafts: EditorialDrafts,
    private val signing: EditorialSigning,
    private val events: EventRepository,
    private val port: PublicationPort,
    private val clock: Clock = Clock.systemUTC(),
    /** After a publication (the caches of the published contents). */
    private val onPublished: () -> Unit = {},
    /** A refusal of the signature (once per refusal, never on a reload): an alert on the dashboard (level 5). */
    private val onRefused: (EditorialSpace, UUID) -> Unit = { _, _ -> },
) : EditorialPublisher {
    override suspend fun handOver(version: ReadyToSign): PublisherAnswer {
        val living = io { signing.latest(version.draftId, version.version) }?.takeIf { it.state.open }
        if (living != null) return follow(version, living)
        return submitNew(version)
    }

    override suspend fun state(version: ReadyToSign): PublisherAnswer {
        val attempt = io { signing.latest(version.draftId, version.version) }
        if (attempt != null) return follow(version, attempt)
        if (io { drafts.find(version.draftId) }?.status == DraftStatus.SIGNING_REFUSED) {
            // Refused by this server before anything was sent: say why again; only `retry_signing` submits it.
            val key = signer.currentKey(version.use) ?: return PublisherAnswer.KeyUnavailable
            return when (val built = build(version, key)) {
                is Built.Refused -> PublisherAnswer.Refused(built.code)
                is Built.Event -> PublisherAnswer.Refused(CODE_REFUSED)
            }
        }
        // Approved, but the hand-over never reached the signing service: try again now.
        return submitNew(version)
    }

    override suspend fun retry(version: ReadyToSign): PublisherAnswer {
        io { signing.reopen(version.draftId) }
        return submitNew(version)
    }

    // Submission ------------------------------------------------------------------------------------

    private suspend fun submitNew(version: ReadyToSign): PublisherAnswer {
        val key = signer.currentKey(version.use) ?: return PublisherAnswer.KeyUnavailable
        val unsigned = when (val built = build(version, key)) {
            is Built.Event -> built.unsigned
            is Built.Refused -> {
                // Nothing was sent: the refusal is this server's, kept with a fixed code.
                return refuseBuild(version, built.code)
            }
        }
        if (!version.use.matches(unsigned)) return refuseBuild(version, CODE_WRONG_USE)
        return when (val submission = submit(version, unsigned)) {
            is DraftSubmission.Submitted -> {
                if (submission.draftId != unsigned.idHex()) return refuseBuild(version, HttpPlatformSigner.BAD_ANSWER_FROM_SIGNER)
                io { signing.recordSubmitted(version.draftId, version.version, version.use, unsigned, submission.draftId, submission.approvalUrl) }
                PublisherAnswer.AwaitingApproval(submission.draftId, submission.approvalUrl, approvals = 0, required = version.approvalsFor(unsigned))
            }
            is DraftSubmission.Refused -> {
                if (submission.code == HttpPlatformSigner.SIGNER_UNAVAILABLE) return PublisherAnswer.Unavailable
                io { signing.recordRefusedSubmission(version.draftId, version.version, version.use, unsigned, submission.code) }
                onRefused(version.space, version.draftId)
                PublisherAnswer.Refused(EditorialSigning.safeCode(submission.code))
            }
        }
    }

    private suspend fun refuseBuild(version: ReadyToSign, code: String): PublisherAnswer {
        // A refusal of this server before anything is sent: the draft goes to signing_refused, no submission row
        // (the same refusal is found again on a reload, without a second alert).
        if (io { signing.refuseUnsent(version.draftId) }) onRefused(version.space, version.draftId)
        return PublisherAnswer.Refused(code)
    }

    private sealed interface Built {
        data class Event(val unsigned: UnsignedEvent) : Built
        data class Refused(val code: String) : Built
    }

    /** The unsigned event of [version] under [key], or why it cannot be built. */
    private suspend fun build(version: ReadyToSign, key: String): Built {
        val schema = EditorialSchemas.of(version.type) ?: return Built.Refused(CODE_UNKNOWN_TYPE)
        val content = io { drafts.version(version.draftId, version.version) }?.content ?: return Built.Refused(CODE_UNKNOWN_VERSION)
        val slug = EditorialDrafts.slugOf(content) ?: return Built.Refused(CODE_UNKNOWN_VERSION)
        val published = latestAt(key, slug)
        if (version.baseEventId == null && published != null) return Built.Refused(CODE_ADDRESS_TAKEN)
        if (version.baseEventId != null) {
            // An update keeps the address of the published content: same kind, same key, same slug.
            if (published == null) return Built.Refused(CODE_ADDRESS_MISMATCH)
            if (content.changeNote.isNullOrBlank() || content.changeKind == null) return Built.Refused(CODE_CORRECTION_REQUIRED)
        }
        val earlier = io { signing.attempts(version.draftId) }.maxOfOrNull { it.unsigned.createdAt } ?: 0L
        val createdAt = maxOf(clock.instant().epochSecond, (published?.createdAt ?: 0L) + 1, earlier + 1)
        val base = try {
            EditorialEngine.build(schema, content.draft(schema.type), PublicKey.fromHex(key), createdAt)
        } catch (e: IllegalArgumentException) {
            return Built.Refused(CODE_UNBUILDABLE)
        }
        val tags = if (version.baseEventId == null) {
            base.tags
        } else {
            // The public journal of corrections: the earlier lines are kept, never removed; the new one last.
            base.tags + published!!.tags.filter { it.firstOrNull() == TAG_CORRECTION } +
                listOf(listOf(TAG_CORRECTION, parisDate(createdAt), content.changeNote!!.trim(), content.changeKind!!.db))
        }
        return Built.Event(base.copy(tags = tags))
    }

    /**
     * Submits [unsigned] for [version]: one approval for an ordinary content, two for a content of M17
     * ([ReadyToSign.approvalsFor], computed on this very event, as the signing service does); the author is
     * always named, in a single validation too, so that the service can refuse the author as an approver.
     */
    private suspend fun submit(version: ReadyToSign, unsigned: UnsignedEvent): DraftSubmission =
        signer.submitDraft(unsigned, version.use, doubleValidation = version.approvalsFor(unsigned) >= EditorialApprovals.M17, author = author(version))

    private fun author(version: ReadyToSign): DraftAuthor = DraftAuthor(account = version.author.toString(), pubkey = version.authorPubkey)

    // Follow-up -------------------------------------------------------------------------------------

    private suspend fun follow(version: ReadyToSign, attempt: SigningAttempt): PublisherAnswer {
        return when (attempt.state) {
            SigningState.PUBLISHED -> PublisherAnswer.Published(attempt.eventId)
            SigningState.REFUSED -> PublisherAnswer.Refused(attempt.refusalCode ?: CODE_REFUSED)
            SigningState.EXPIRED, SigningState.WITHDRAWN -> PublisherAnswer.Expired
            SigningState.SIGNED -> publish(attempt, requireNotNull(attempt.signed))
            SigningState.SUBMITTED -> followSubmitted(version, attempt)
        }
    }

    private suspend fun followSubmitted(version: ReadyToSign, attempt: SigningAttempt): PublisherAnswer {
        val unsigned = attempt.unsigned
        // The row holds exactly the event submitted: anything else is never asked for.
        if (unsigned.idHex() != attempt.eventId) return refuse(attempt, CODE_SIGNED_WRONG_ID)
        if (!clock.instant().isBefore(attempt.expiresAt)) return expire(attempt)
        // A signature already asked: its ticket first (the signing service forgets the draft once it signs).
        val asked = attempt.ticket?.let { signer.status(SigningTicket(it)) }
        if (asked != null && !(asked is SigningStatus.Refused && asked.code == HttpPlatformSigner.UNKNOWN_TICKET)) return outcome(attempt, unsigned, asked)
        val status = signer.draftStatus(attempt.use, attempt.eventId)
        if (status == null) {
            // Unknown to the service (restarted) or no answer: submitting the same event again restores it.
            return when (val again = submit(version, unsigned)) {
                is DraftSubmission.Submitted -> PublisherAnswer.AwaitingApproval(again.draftId, again.approvalUrl, approvals = 0, required = version.approvalsFor(unsigned))
                is DraftSubmission.Refused ->
                    if (again.code == HttpPlatformSigner.SIGNER_UNAVAILABLE) PublisherAnswer.Unavailable else refuse(attempt, again.code)
            }
        }
        if (status.expiresAt in 1..clock.instant().epochSecond) return expire(attempt)
        if (!status.ready) {
            val url = status.approvalUrl ?: attempt.approvalUrl.orEmpty()
            return PublisherAnswer.AwaitingApproval(attempt.eventId, url, approvals = status.approvals.size, required = status.required)
        }
        val ticket = signer.request(unsigned, attempt.use)
        io { signing.setTicket(attempt.eventId, ticket.id) }
        return outcome(attempt, unsigned, signer.status(ticket))
    }

    private suspend fun outcome(attempt: SigningAttempt, unsigned: UnsignedEvent, outcome: SigningStatus): PublisherAnswer {
        return when (outcome) {
            SigningStatus.Pending -> PublisherAnswer.SignaturePending
            is SigningStatus.Refused -> if (outcome.code == HttpPlatformSigner.SIGNER_UNAVAILABLE) {
                io { signing.setTicket(attempt.eventId, null) }
                PublisherAnswer.Unavailable
            } else {
                refuse(attempt, outcome.code)
            }
            is SigningStatus.Signed -> {
                val problem = checkSigned(outcome.event, unsigned)
                if (problem != null) return refuse(attempt, problem)
                io { signing.markSigned(attempt.eventId, outcome.event) }
                publish(attempt, outcome.event)
            }
        }
    }

    /** Publishes [signed], unchanged, once it is checked to be the newest version of its address. */
    private suspend fun publish(attempt: SigningAttempt, signed: SignedEvent): PublisherAnswer {
        val slug = signed.firstTagValue("d") ?: return refuse(attempt, CODE_SIGNED_WRONG_ID)
        val stored = latestAt(signed.pubkey, slug)
        if (stored != null && stored.id != signed.id && stored.createdAt >= signed.createdAt) return refuse(attempt, CODE_STALE_UPDATE)
        return when (val result = port.publish(signed)) {
            is PublicationResult.Accepted -> {
                io { signing.markPublished(signed.id) }
                onPublished()
                PublisherAnswer.Published(signed.id)
            }
            is PublicationResult.Rejected -> refuse(attempt, "publication_" + result.reason.name.lowercase())
            // Kept signed: published on the next reload.
            is PublicationResult.Unavailable -> PublisherAnswer.Unavailable
        }
    }

    private suspend fun refuse(attempt: SigningAttempt, code: String): PublisherAnswer {
        if (io { signing.markRefused(attempt.eventId, code) }) io { drafts.find(attempt.draftId) }?.let { onRefused(it.space, it.id) }
        return PublisherAnswer.Refused(EditorialSigning.safeCode(code))
    }

    private suspend fun expire(attempt: SigningAttempt): PublisherAnswer {
        io { signing.markExpired(attempt.eventId) }
        return PublisherAnswer.Expired
    }

    /** The latest stored version of the address `30023:<pubkey>:<slug>`, if any. */
    private suspend fun latestAt(pubkey: String, slug: String): SignedEvent? = io {
        events.query(EventFilter(kinds = listOf(Article.KIND), authors = listOf(pubkey), identifiers = listOf(slug), limit = 10))
            .filter { it.firstTagValue("d") == slug }
            .maxWithOrNull(compareBy<SignedEvent> { it.createdAt }.thenByDescending { it.id })
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun parisDate(epochSeconds: Long): String = Instant.ofEpochSecond(epochSeconds).atZone(PARIS).toLocalDate().toString()

    companion object {
        /** The public journal of corrections: `["ppf-correction", <date AAAA-MM-JJ>, <summary>, <correction | mise-a-jour>]`. */
        const val TAG_CORRECTION: String = "ppf-correction"

        const val CODE_UNKNOWN_TYPE: String = "unknown_type"
        const val CODE_UNKNOWN_VERSION: String = "unknown_version"
        const val CODE_UNBUILDABLE: String = "unbuildable"
        const val CODE_WRONG_USE: String = "wrong_use"
        const val CODE_ADDRESS_TAKEN: String = "address_taken"
        const val CODE_ADDRESS_MISMATCH: String = "address_mismatch"
        const val CODE_CORRECTION_REQUIRED: String = "correction_required"
        const val CODE_SIGNED_WRONG_ID: String = "signed_wrong_id"
        const val CODE_SIGNED_WRONG_KEY: String = "signed_wrong_key"
        const val CODE_SIGNED_BAD_SIGNATURE: String = "signed_bad_signature"
        const val CODE_SIGNED_CHANGED: String = "signed_changed"
        const val CODE_STALE_UPDATE: String = "stale_update"
        const val CODE_REFUSED: String = "refused"

        private val PARIS: ZoneId = ZoneId.of("Europe/Paris")

        /**
         * Why [signed] may not be published for [unsigned], or null: the key of the event submitted, the same id,
         * a valid signature, and exactly the content submitted (nothing added, nothing changed). Not the current
         * key of the use: after a rotation the service still signs, while it is valid, an event built with the
         * previous key; the trust of the key for the use (certificates, revocations) is checked by
         * [HttpPlatformSigner] (`untrusted_key_from_signer`).
         */
        fun checkSigned(signed: SignedEvent, unsigned: UnsignedEvent): String? = when {
            signed.pubkey != unsigned.pubkey -> CODE_SIGNED_WRONG_KEY
            signed.id != unsigned.idHex() -> CODE_SIGNED_WRONG_ID
            !signed.verify() -> CODE_SIGNED_BAD_SIGNATURE
            signed.unsigned() != unsigned -> CODE_SIGNED_CHANGED
            else -> null
        }
    }
}
