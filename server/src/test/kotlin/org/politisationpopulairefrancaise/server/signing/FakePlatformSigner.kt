package org.politisationpopulairefrancaise.server.signing

import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.platform.DraftApproval
import org.politisationpopulairefrancaise.core.platform.DoubleValidation
import org.politisationpopulairefrancaise.core.platform.DraftAuthor
import org.politisationpopulairefrancaise.core.platform.DraftStatusResponse
import org.politisationpopulairefrancaise.core.platform.KeyCertificate
import org.politisationpopulairefrancaise.core.platform.PlatformTrust
import org.politisationpopulairefrancaise.core.platform.SigningRefusals
import org.politisationpopulairefrancaise.core.platform.SigningUse
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import java.util.concurrent.atomic.AtomicLong

/**
 * The platform signer of the tests and of development (contract, section 5): a root and one working key
 * per use generated at each start, in memory. It checks what the poles must get right (the key and the
 * form of the use, a draft for a human use, the double validation the service requires for the contents
 * of M17 ([DoubleValidation]), the number of approvals, the author named for an editorial use or a double
 * validation and never approving (D-142)); the
 * approvals themselves are given by the tests with [approve] (the passkeys are covered by the tests of
 * the signing service). [trust] accepts what it signs.
 */
class FakePlatformSigner(private val now: () -> Long = { System.currentTimeMillis() / 1000 }) : PlatformSigner, PlatformKeys {
    private val root = IdentityKeys.generate()
    private val keys: Map<SigningUse, IdentityKeys> = SigningUse.entries.associateWith { IdentityKeys.generate() }
    private val tickets = AtomicLong()
    private val outcomes = mutableMapOf<String, SigningStatus>()
    private val drafts = mutableMapOf<String, FakeDraft>()
    private val signed = mutableSetOf<String>()

    private class FakeDraft(val use: SigningUse, val doubleValidation: Boolean, val author: DraftAuthor?, val submittedAt: Long) {
        val approvals = mutableListOf<DraftApproval>()
        val required: Int get() = if (doubleValidation) 2 else 1
    }

    /** Every signature request received, in order. */
    val requests: MutableList<Pair<UnsignedEvent, SigningUse>> = mutableListOf()

    /** The trust of the fake root with the certificates of every key (valid 90 days from creation). */
    val trust: PlatformTrust = run {
        val start = now() - 60
        val certificates = keys.map { (use, key) ->
            val unsigned = KeyCertificate.unsigned(root.publicKey.hex, key.publicKey.hex, listOf(use), start, start + KeyCertificate.MAX_VALIDITY_SECONDS, start)
            val id = unsigned.id()
            SignedEvent(id.toHexString(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, root.sign(id).toHexString())
        }
        PlatformTrust.of(root.publicKey.hex, certificates)
    }

    override fun trust(): PlatformTrust = trust

    override suspend fun currentKey(use: SigningUse): String = keys.getValue(use).publicKey.hex

    override suspend fun submitDraft(unsigned: UnsignedEvent, use: SigningUse, doubleValidation: Boolean, author: DraftAuthor?): DraftSubmission {
        val id = unsigned.idHex()
        val named = author?.takeIf { it.isNamed }
        val refusal = when {
            use.automatic -> SigningRefusals.AUTOMATIC_USE
            unsigned.pubkey != keys.getValue(use).publicKey.hex -> SigningRefusals.WRONG_KEY
            unsigned.kind !in use.kinds -> SigningRefusals.KIND_NOT_ALLOWED
            !use.matches(unsigned) -> SigningRefusals.BAD_TAG
            "${use.id}:$id" in signed -> SigningRefusals.ALREADY_SIGNED
            !doubleValidation && DoubleValidation.requiredFor(use, unsigned) -> SigningRefusals.DOUBLE_VALIDATION_REQUIRED
            named == null && (doubleValidation || use in SigningUse.AUTHOR_REQUIRED_USES) -> SigningRefusals.AUTHOR_REQUIRED
            else -> null
        }
        if (refusal != null) return DraftSubmission.Refused(refusal)
        synchronized(drafts) {
            val existing = drafts["${use.id}:$id"]
            if (existing != null && (existing.doubleValidation != doubleValidation || existing.author != named)) return DraftSubmission.Refused(SigningRefusals.DRAFT_CONFLICT)
            if (existing == null) drafts["${use.id}:$id"] = FakeDraft(use, doubleValidation, named, now())
        }
        return DraftSubmission.Submitted(id, approvalUrl(use, id))
    }

    /**
     * The approval of the draft [eventId] by [account] (whose Nostr key is [pubkey], if the account has one), as
     * the signing service records it after checking a passkey on its own page; the refusal code, or null when
     * recorded. The author never approves (D-142), in a single validation too.
     */
    fun approve(use: SigningUse, eventId: String, account: String, pubkey: String? = null): String? = synchronized(drafts) {
        val draft = drafts["${use.id}:$eventId"] ?: return SigningRefusals.UNKNOWN_DRAFT
        val author = draft.author
        if (author != null && (author.account == account || (author.pubkey != null && author.pubkey == pubkey))) return SigningRefusals.AUTHOR_CANNOT_APPROVE
        if (draft.approvals.any { it.account == account }) return SigningRefusals.ALREADY_APPROVED
        draft.approvals += DraftApproval(account, now())
        null
    }

    override suspend fun draftStatus(use: SigningUse, eventId: String): DraftStatusResponse? = synchronized(drafts) {
        val draft = drafts["${use.id}:$eventId"] ?: return null
        DraftStatusResponse(
            use = use.id,
            draft = eventId,
            doubleValidation = draft.doubleValidation,
            required = draft.required,
            approvals = draft.approvals.toList(),
            ready = draft.approvals.size >= draft.required,
            contentKnown = true,
            submittedAt = draft.submittedAt,
            expiresAt = draft.submittedAt + 7 * 86_400,
            approvalUrl = approvalUrl(use, eventId),
        )
    }

    override suspend fun request(unsigned: UnsignedEvent, use: SigningUse): SigningTicket {
        requests += unsigned to use
        val id = unsigned.idHex()
        val refusal: SigningStatus.Refused? = synchronized(drafts) {
            val draft = drafts["${use.id}:$id"]
            when {
                unsigned.pubkey != keys.getValue(use).publicKey.hex -> SigningStatus.Refused(SigningRefusals.WRONG_KEY)
                unsigned.kind !in use.kinds -> SigningStatus.Refused(SigningRefusals.KIND_NOT_ALLOWED)
                !use.matches(unsigned) -> SigningStatus.Refused(SigningRefusals.BAD_TAG)
                "${use.id}:$id" in signed -> SigningStatus.Refused(SigningRefusals.ALREADY_SIGNED)
                !use.automatic && draft == null -> SigningStatus.Refused(SigningRefusals.UNKNOWN_DRAFT)
                !use.automatic && !draft!!.doubleValidation && DoubleValidation.requiredFor(use, unsigned) -> SigningStatus.Refused(SigningRefusals.DOUBLE_VALIDATION_REQUIRED)
                !use.automatic && draft!!.approvals.size < draft.required -> SigningStatus.Refused(SigningRefusals.APPROVALS_MISSING)
                else -> {
                    signed += "${use.id}:$id"
                    drafts.remove("${use.id}:$id")
                    null
                }
            }
        }
        val status = refusal ?: SigningStatus.Signed(LocalEventSigner(keys.getValue(use)).sign(unsigned))
        val ticket = SigningTicket("fake-${tickets.incrementAndGet()}")
        synchronized(outcomes) { outcomes[ticket.id] = status }
        return ticket
    }

    override suspend fun status(ticket: SigningTicket): SigningStatus =
        synchronized(outcomes) { outcomes[ticket.id] } ?: SigningStatus.Refused(HttpPlatformSigner.UNKNOWN_TICKET)

    private fun approvalUrl(use: SigningUse, eventId: String) = "$APPROVAL_ORIGIN/approbation/${use.id}/$eventId"

    companion object {
        /** A fictitious origin (`.invalid` is reserved and never resolves). */
        const val APPROVAL_ORIGIN: String = "https://signature.exemple.invalid"
    }
}
