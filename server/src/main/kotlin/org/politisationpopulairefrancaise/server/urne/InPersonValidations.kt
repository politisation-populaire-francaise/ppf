package org.politisationpopulairefrancaise.server.urne

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.urne.Nip98ReplayGuard
import org.politisationpopulairefrancaise.core.urne.ValidationAnswer
import org.politisationpopulairefrancaise.core.urne.ValidationProof
import org.politisationpopulairefrancaise.core.urne.VoteLevel
import org.politisationpopulairefrancaise.server.backoffice.AuditSealer
import org.politisationpopulairefrancaise.server.backoffice.SealedForAudit
import org.politisationpopulairefrancaise.server.backoffice.SessionAccess
import org.politisationpopulairefrancaise.server.backoffice.SignedSessionRequest
import org.politisationpopulairefrancaise.server.backoffice.ValidationSessionGate
import java.time.Clock

/**
 * The in-person validation of an account by a validator (D-112, une-personne-une-voix.md 4.3): the
 * validator's application, signed (NIP-98) with the key of its open session ([ValidationSessionGate]),
 * sends the proof the citizen shows ([ValidationProof]).
 *
 * What the server keeps, and only that (decision of the Directeur technique of 8 November 2026, option B):
 * - the account waits for next Monday 00:00 UTC in `vote_validation_pending`, without session in clear,
 *   time or serial, with the link « account, session, week » sealed for the offline audit key;
 * - the session's counter, in the same transaction (both or neither);
 * - on Monday, [UrneService.applyValidationsDue] moves every waiting account at once.
 * Never a name, a document, a photo, a place or an event: nothing about the person is typed or received.
 */
class InPersonValidations(
    private val store: UrneStore,
    private val service: UrneService,
    private val gate: ValidationSessionGate,
    private val sealer: AuditSealer,
    private val clock: Clock = Clock.systemUTC(),
) {
    // A proof is accepted once; the memory starts with the process, so older proofs are refused.
    private val seen = Nip98ReplayGuard(windowSeconds = 2 * ValidationProof.MAX_AGE_SECONDS + 10)
    private val startedAt = clock.instant().epochSecond

    /** Validates the account whose proof is the body of [request]; throws [UrneRefusal] otherwise. */
    fun validate(request: SignedSessionRequest): ValidationAnswer {
        val session = when (val access = gate.authorize(request)) {
            is SessionAccess.Granted -> access.session
            is SessionAccess.Refused -> throw UrneRefusal(401, access.code)
        }
        val body = request.body?.takeIf { it.isNotEmpty() && it.size <= MAX_PROOF_BYTES } ?: throw UrneRefusal(400, ValidationProof.INVALID)
        val event = runCatching { SignedEvent.fromJson(body.decodeToString()) }.getOrNull() ?: throw UrneRefusal(400, ValidationProof.INVALID)
        val now = clock.instant()
        val proof = when (val check = ValidationProof.check(event, now.epochSecond)) {
            is ValidationProof.Check.Valid -> check
            is ValidationProof.Check.Invalid -> throw UrneRefusal(400, check.reason)
        }
        if (proof.createdAt < startedAt || !synchronized(seen) { seen.firstUse(proof.eventId, now.epochSecond) }) throw UrneRefusal(409, "proof_replayed")
        service.requirePerson(proof.pubkey)
        // What waits belongs to the current week: this Monday's batch runs first if it has not yet.
        service.applyValidationsDue()
        val account = store.account(proof.pubkey)
        if (account != null && account.level != VoteLevel.NON_VERIFIE) return ValidationAnswer(ValidationAnswer.ALREADY)
        val week = UrneService.mondayOf(now).plusWeeks(1)
        val sealed = sealer.seal(
            buildJsonObject {
                put("v", 1)
                put("account", proof.pubkey)
                put("session", session.id.toString())
                put("week", week.toString())
            }.toString(),
        )
        return when (store.addPendingValidation(proof.pubkey, Json.encodeToString(SealedForAudit.serializer(), sealed)) { c -> gate.recordValidation(c, session) }) {
            PendingValidation.ADDED -> ValidationAnswer(ValidationAnswer.PENDING, week.toString())
            PendingValidation.ALREADY -> ValidationAnswer(ValidationAnswer.ALREADY)
            PendingValidation.FULL -> throw UrneRefusal(409, "session_full")
        }
    }

    companion object {
        /** A proof is a few hundred bytes. */
        const val MAX_PROOF_BYTES: Int = 4_096
    }
}
