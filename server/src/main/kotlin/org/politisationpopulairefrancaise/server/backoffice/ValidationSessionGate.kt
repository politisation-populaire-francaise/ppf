package org.politisationpopulairefrancaise.server.backoffice

import org.politisationpopulairefrancaise.core.signing.SignedEvent
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

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
 * 60 seconds, `payload` tag when there is a body) and gives the signing key. Behind an interface because
 * Décider writes the shared implementation (`shared/core/.../core/urne/Nip98.kt`, with proof of work and
 * a replay guard): [MinimalNip98Verifier] is the stand-in of the back-office until that merge.
 */
fun interface SessionRequestVerifier {
    /**
     * Checks [request]; [acceptsSigner] is asked whether the signing key may be used at all (an open
     * session) before the signature is verified, so that unknown keys cost no signature check.
     */
    fun check(request: SignedSessionRequest, now: Instant, acceptsSigner: (String) -> Boolean): SessionRequestCheck
}

/**
 * TO BE REPLACED by `core/urne/Nip98.kt` (Décider) at the merge: the minimum NIP-98 verification needed by
 * the back-office and its tests. Uses the signature code of shared/core ([SignedEvent.verify]); refuses
 * an event seen in the last two minutes (replay), in memory only, and every event dated before [startedAt]
 * (the start of the process: the memory of seen events does not survive a restart).
 */
class MinimalNip98Verifier(
    private val startedAt: Instant = Instant.now(),
    private val maxSkewSeconds: Long = MAX_SKEW_SECONDS,
) : SessionRequestVerifier {
    private val seen = ConcurrentHashMap<String, Long>()

    override fun check(request: SignedSessionRequest, now: Instant, acceptsSigner: (String) -> Boolean): SessionRequestCheck {
        val header = request.authorization?.trim() ?: return SessionRequestCheck.Refused("missing_authorization")
        if (!header.startsWith(SCHEME, ignoreCase = true) || header.length > MAX_HEADER_LENGTH) return SessionRequestCheck.Refused("bad_scheme")
        val json = try {
            String(Base64.getDecoder().decode(header.substring(SCHEME.length).trim()), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            return SessionRequestCheck.Refused("bad_encoding")
        }
        val event = try {
            SignedEvent.fromJson(json)
        } catch (e: Exception) {
            return SessionRequestCheck.Refused("bad_event")
        }
        if (event.kind != KIND) return SessionRequestCheck.Refused("bad_kind")
        val nowSeconds = now.epochSecond
        if (event.createdAt < nowSeconds - maxSkewSeconds || event.createdAt > nowSeconds + maxSkewSeconds) return SessionRequestCheck.Refused("stale")
        if (event.createdAt < startedAt.epochSecond) return SessionRequestCheck.Refused("before_start")
        if (event.tagValues("u") != listOf(request.url)) return SessionRequestCheck.Refused("bad_url")
        if (event.tagValues("method").map { it.uppercase() } != listOf(request.method.uppercase())) return SessionRequestCheck.Refused("bad_method")
        val body = request.body
        if (body != null && body.isNotEmpty()) {
            if (event.tagValues("payload") != listOf(Tokens.sha256Hex(body))) return SessionRequestCheck.Refused("bad_payload")
        }
        if (!isHexKey(event.pubkey) || !acceptsSigner(event.pubkey)) return SessionRequestCheck.Refused("no_open_session")
        if (!event.verify()) return SessionRequestCheck.Refused("bad_signature")
        forgetOlderThan(nowSeconds - 2 * maxSkewSeconds)
        if (seen.putIfAbsent(event.id, nowSeconds) != null) return SessionRequestCheck.Refused("replayed")
        return SessionRequestCheck.Signed(event.pubkey, event.id)
    }

    private fun forgetOlderThan(limit: Long) {
        if (seen.size < 1_000) return
        seen.entries.removeIf { it.value < limit }
    }

    companion object {
        const val KIND: Int = 27235
        const val MAX_SKEW_SECONDS: Long = 60
        private const val SCHEME = "Nostr "
        private const val MAX_HEADER_LENGTH = 16_384
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
