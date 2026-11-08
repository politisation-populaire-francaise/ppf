package org.politisationpopulairefrancaise.server.backoffice

import org.politisationpopulairefrancaise.core.urne.Nip98
import org.politisationpopulairefrancaise.core.urne.Nip98ReplayGuard
import java.time.Clock
import java.time.Instant

/**
 * A request of a validator's application, as the server received it. The caller builds [url] from the
 * configured public origin of the endpoint and the request path (never from the `Host` header), and
 * always passes the [body] it received, so that a signature cannot be reused for another address or body.
 */
class SignedSessionRequest(
    /** The `Authorization` header, `Nostr <base64 of the signed event>`. */
    val authorization: String?,
    /** The absolute address of the request: configured origin + path (and query), as the application signed it. */
    val url: String,
    val method: String,
    /** The body received, when the request has one (its SHA-256 must then be in the `payload` tag). */
    val body: ByteArray? = null,
)

/** Why a signed request is refused, in stable codes (never echoed with the request's content). */
sealed interface SessionRequestCheck {
    /** The request is signed by [pubkey]: the event is well formed, fresh and its signature valid. */
    data class Signed(val pubkey: String, val eventId: String) : SessionRequestCheck

    data class Refused(val code: String) : SessionRequestCheck
}

/**
 * Checks the NIP-98 signature of a request (kind 27235, `u` and `method` tags, `created_at` within
 * 60 seconds, `payload` tag when there is a body) and gives the signing key: [Nip98SessionVerifier], over the
 * shared implementation of the ballot boxes; behind an interface for the tests.
 */
fun interface SessionRequestVerifier {
    /**
     * Checks [request]; [acceptsSigner] is asked whether the signing key may be used at all (an open
     * session) before the signature is verified, so that unknown keys cost no signature check.
     */
    fun check(request: SignedSessionRequest, now: Instant, acceptsSigner: (String) -> Boolean): SessionRequestCheck
}

/**
 * The NIP-98 verification of the back-office, over the shared implementation of the ballot boxes
 * (`core/urne/Nip98.kt`): kind 27235, exactly one `u` (the configured origin and the path) and one `method`,
 * `payload` when there is a body and only then, `created_at` within [maxSkewSeconds]. Two properties of its own:
 * - the session is looked up by key BEFORE the signature is verified: an unknown key costs no signature check
 *   (`no_open_session`);
 * - an event dated before [startedAt] (the start of the process) is refused (`before_start`): the memory of the
 *   events already seen (`replayed`, two minutes, in memory only) does not survive a restart.
 * The codes are those of [Nip98.verify] (`missing_authorization`, `malformed_authorization`,
 * `expired_authorization`, `wrong_url`, `wrong_method`, `wrong_payload`, `invalid_signature`) and the three above.
 */
class Nip98SessionVerifier(
    private val startedAt: Instant = Instant.now(),
    private val maxSkewSeconds: Long = Nip98.DEFAULT_MAX_SKEW_SECONDS,
) : SessionRequestVerifier {
    private val replay = Nip98ReplayGuard(windowSeconds = 2 * maxSkewSeconds + 10)

    override fun check(request: SignedSessionRequest, now: Instant, acceptsSigner: (String) -> Boolean): SessionRequestCheck {
        val seconds = now.epochSecond
        val result = Nip98.verify(
            request.authorization?.trim(),
            { it == request.url },
            request.method,
            request.body,
            seconds,
            maxSkewSeconds = maxSkewSeconds,
            acceptsSigner = acceptsSigner,
        )
        return when (result) {
            is Nip98.Result.Invalid -> SessionRequestCheck.Refused(if (result.reason == Nip98.UNKNOWN_SIGNER) NO_OPEN_SESSION else result.reason)
            is Nip98.Result.Valid -> when {
                result.createdAt < startedAt.epochSecond -> SessionRequestCheck.Refused(BEFORE_START)
                !synchronized(replay) { replay.firstUse(result.eventId, seconds) } -> SessionRequestCheck.Refused(REPLAYED)
                else -> SessionRequestCheck.Signed(result.pubkey, result.eventId)
            }
        }
    }

    companion object {
        const val KIND: Int = Nip98.KIND
        const val NO_OPEN_SESSION: String = "no_open_session"
        const val BEFORE_START: String = "before_start"
        const val REPLAYED: String = "replayed"
    }
}

/** What the gate answers to Décider. */
sealed interface SessionAccess {
    data class Granted(val session: ValidationSession) : SessionAccess

    data class Refused(val code: String) : SessionAccess
}

/**
 * The door of Décider's validation endpoints (contract, section 8): a request signed (NIP-98) with the key
 * of a validation session that is open, not revoked, not past its expiry, whose validator grant is live,
 * whose account is active and whose designating organisation is still verified. Anything else is refused
 * at once, whatever the periodic task has or has not closed yet.
 */
class ValidationSessionGate(
    private val sessions: ValidationSessions,
    private val verifier: SessionRequestVerifier,
    private val isVerifiedOrganisation: (String) -> Boolean,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun authorize(request: SignedSessionRequest): SessionAccess {
        val now = clock.instant()
        var found: Pair<ValidationSession, RoleGrant>? = null
        val check = verifier.check(request, now) { signer -> sessions.usableByKey(signer, now)?.also { found = it } != null }
        if (check is SessionRequestCheck.Refused) return SessionAccess.Refused(check.code)
        val (session, grant) = found ?: return SessionAccess.Refused("no_open_session")
        val organisation = grant.organisationPubkey ?: return SessionAccess.Refused("no_organisation")
        if (!runCatching { isVerifiedOrganisation(organisation) }.getOrDefault(false)) return SessionAccess.Refused("organisation_not_verified")
        return SessionAccess.Granted(session)
    }

    /** Counts one validation in [session]; false when it is full or no longer usable. */
    fun recordValidation(session: ValidationSession): Boolean = sessions.recordValidation(session.id, clock.instant())
}
