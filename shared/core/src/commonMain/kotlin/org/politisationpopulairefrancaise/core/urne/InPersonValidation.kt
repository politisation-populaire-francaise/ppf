package org.politisationpopulairefrancaise.core.urne

import kotlinx.serialization.Serializable
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import kotlin.math.abs

/**
 * What a citizen shows a validator to have their account verified in person (D-112,
 * une-personne-une-voix.md 4.3): a QR code holding an event signed with the account's own key, which
 * proves that the person in front of the validator holds it. Valid [MAX_AGE_SECONDS] and accepted once;
 * never published, never stored: the server keeps only the sealed link « account, session, week » and the
 * session's counter. Nothing about the person: the validator looks, nothing is typed nor photographed.
 *
 * An ephemeral event of PPF (kinds 20000 to 29999 are never stored by relays):
 * ```
 * {"kind":22243,"tags":[["ppf-proof","validation"]],"content":""}
 * ```
 * The validator's application sends it, as the body, to `POST /api/v1/urnes/validations`, signed (NIP-98)
 * with the key of its open validation session.
 */
object ValidationProof {
    const val KIND: Int = 22_243
    const val TAG: String = "ppf-proof"
    const val PURPOSE: String = "validation"

    /** A proof older or younger than this is refused: the application draws a new one every minute. */
    const val MAX_AGE_SECONDS: Long = 300

    /** The proof of the account of [signer] at [createdAt] (seconds), as its QR code holds it ([SignedEvent.toJson]). */
    suspend fun sign(signer: EventSigner, createdAt: Long): SignedEvent =
        signer.sign(kind = KIND, tags = listOf(listOf(TAG, PURPOSE)), createdAt = createdAt)

    sealed interface Check {
        data class Valid(val pubkey: String, val eventId: String, val createdAt: Long) : Check

        /** [reason]: `invalid_proof` or `expired_proof`, safe to return; never a value of the proof. */
        data class Invalid(val reason: String) : Check
    }

    /** Checks [event] at [now] (seconds): kind, its one tag, empty content, age, then the signature. */
    fun check(event: SignedEvent, now: Long): Check {
        if (event.kind != KIND || event.tags != listOf(listOf(TAG, PURPOSE)) || event.content.isNotEmpty()) return Check.Invalid(INVALID)
        if (abs(now - event.createdAt) > MAX_AGE_SECONDS) return Check.Invalid(EXPIRED)
        if (!event.verify()) return Check.Invalid(INVALID)
        return Check.Valid(event.pubkey, event.id, event.createdAt)
    }

    const val INVALID: String = "invalid_proof"
    const val EXPIRED: String = "expired_proof"
}

/** The answer of `POST /api/v1/urnes/validations` to the validator's application. */
@Serializable
data class ValidationAnswer(
    /** [PENDING]: counted in the session, applied on [effectiveWeek]; [ALREADY]: nothing to do (already verified, pending or elected). */
    val status: String,
    /** Monday (AAAA-MM-JJ) from which the account counts as verified; null when [ALREADY]. */
    val effectiveWeek: String? = null,
) {
    companion object {
        const val PENDING: String = "pending"
        const val ALREADY: String = "already"
    }
}
