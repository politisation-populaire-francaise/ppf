package org.politisationpopulairefrancaise.server.signing

import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.platform.ApprovalProof
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
 * per use generated at each start, in memory. It checks what the poles must get right (the key of the use,
 * the kind, the number of approvals and the author of a double validation) but not the passkeys
 * themselves, which the tests of the signing service cover. [trust] accepts what it signs.
 */
class FakePlatformSigner(private val now: () -> Long = { System.currentTimeMillis() / 1000 }) : PlatformSigner {
    private val root = IdentityKeys.generate()
    private val keys: Map<SigningUse, IdentityKeys> = SigningUse.entries.associateWith { IdentityKeys.generate() }
    private val tickets = AtomicLong()
    private val outcomes = mutableMapOf<String, SigningStatus>()

    /** Every request received, in order. */
    val requests: MutableList<Triple<UnsignedEvent, SigningUse, ApprovalProof>> = mutableListOf()

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

    override suspend fun currentKey(use: SigningUse): String = keys.getValue(use).publicKey.hex

    override suspend fun request(unsigned: UnsignedEvent, use: SigningUse, approval: ApprovalProof): SigningTicket {
        requests += Triple(unsigned, use, approval)
        val required = when {
            use.automatic -> 0
            approval.doubleValidation -> 2
            else -> 1
        }
        val accounts = approval.assertions.map { it.account }
        val author = approval.author
        val status = when {
            unsigned.pubkey != keys.getValue(use).publicKey.hex -> SigningStatus.Refused(SigningRefusals.WRONG_KEY)
            unsigned.kind !in use.kinds -> SigningStatus.Refused(SigningRefusals.KIND_NOT_ALLOWED)
            accounts.size != required -> SigningStatus.Refused(if (use.automatic) SigningRefusals.UNEXPECTED_APPROVAL else SigningRefusals.APPROVALS_MISSING)
            accounts.distinct().size != accounts.size -> SigningStatus.Refused(SigningRefusals.SAME_ACCOUNT_TWICE)
            approval.doubleValidation && (author?.account == null && author?.pubkey == null) -> SigningStatus.Refused(SigningRefusals.AUTHOR_REQUIRED)
            approval.doubleValidation && author?.account != null && author.account in accounts -> SigningStatus.Refused(SigningRefusals.AUTHOR_CANNOT_APPROVE)
            else -> SigningStatus.Signed(LocalEventSigner(keys.getValue(use)).sign(unsigned))
        }
        val ticket = SigningTicket("fake-${tickets.incrementAndGet()}")
        synchronized(outcomes) { outcomes[ticket.id] = status }
        return ticket
    }

    override suspend fun status(ticket: SigningTicket): SigningStatus =
        synchronized(outcomes) { outcomes[ticket.id] } ?: SigningStatus.Refused(HttpPlatformSigner.UNKNOWN_TICKET)
}
