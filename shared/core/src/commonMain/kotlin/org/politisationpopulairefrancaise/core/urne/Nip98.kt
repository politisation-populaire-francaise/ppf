package org.politisationpopulairefrancaise.core.urne

import fr.acinq.bitcoin.Crypto
import org.politisationpopulairefrancaise.core.crypto.secureRandomBytes
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * HTTP authentication by a signed Nostr event (NIP-98): an event of kind 27235 with the exact URL
 * (`u`), the method and, for a body, its SHA-256 (`payload`), sent as `Authorization: Nostr <base64>`.
 * Used to ask for voting tokens with one's account; never for a ballot, which travels without any
 * account (D-110). NIP-98 alone does not stop a replay within its time window: the server keeps the
 * ids it has seen ([Nip98ReplayGuard]).
 */
object Nip98 {
    const val KIND: Int = 27235
    const val SCHEME: String = "Nostr"

    /** The value of the Authorization header, with a NIP-13 proof of work of [difficulty] bits. */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun authorization(
        signer: EventSigner,
        url: String,
        method: String,
        body: ByteArray?,
        createdAt: Long,
        difficulty: Int = 0,
    ): String {
        val tags = buildList {
            add(listOf("u", url))
            add(listOf("method", method.uppercase()))
            if (body != null && body.isNotEmpty()) add(listOf("payload", Crypto.sha256(body).toHexString()))
        }
        // A random starting counter: two authorizations of the same second never share an id, so
        // neither is taken for a replay of the other.
        val start = secureRandomBytes(6).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xff) }
        val unsigned = ProofOfWork.mine(UnsignedEvent(signer.publicKey.hex, createdAt, KIND, tags, ""), difficulty, start)
        return "$SCHEME " + Base64.Default.encode(signer.sign(unsigned).toJson().encodeToByteArray())
    }

    sealed interface Result {
        data class Valid(val pubkey: String, val eventId: String) : Result

        /** [reason] is a fixed code, safe to return to the client; never a value of the request. */
        data class Invalid(val reason: String) : Result
    }

    /**
     * Checks [header] for a request to [url] with [method] and [body] at [now] (seconds):
     * signature, kind, exact URL and method, body digest, time within [maxSkewSeconds], and at
     * least [difficulty] bits of proof of work.
     */
    fun verify(
        header: String?,
        url: String,
        method: String,
        body: ByteArray?,
        now: Long,
        difficulty: Int = 0,
        maxSkewSeconds: Long = DEFAULT_MAX_SKEW_SECONDS,
    ): Result = verify(header, { it == url }, method, body, now, difficulty, maxSkewSeconds)

    /** Same, with [urlMatches] deciding whether the `u` tag names this request (path only in development). */
    @OptIn(ExperimentalEncodingApi::class)
    fun verify(
        header: String?,
        urlMatches: (String) -> Boolean,
        method: String,
        body: ByteArray?,
        now: Long,
        difficulty: Int = 0,
        maxSkewSeconds: Long = DEFAULT_MAX_SKEW_SECONDS,
    ): Result {
        if (header == null || !header.startsWith("$SCHEME ")) return Result.Invalid("missing_authorization")
        val event = runCatching {
            SignedEvent.fromJson(Base64.Default.decode(header.removePrefix("$SCHEME ").trim()).decodeToString())
        }.getOrNull() ?: return Result.Invalid("malformed_authorization")
        if (event.kind != KIND || !event.verify()) return Result.Invalid("invalid_signature")
        if (kotlin.math.abs(now - event.createdAt) > maxSkewSeconds) return Result.Invalid("expired_authorization")
        if (event.firstTagValue("u")?.let(urlMatches) != true) return Result.Invalid("wrong_url")
        if (event.firstTagValue("method")?.uppercase() != method.uppercase()) return Result.Invalid("wrong_method")
        val payload = event.firstTagValue("payload")
        if (body != null && body.isNotEmpty()) {
            if (payload == null || payload != Crypto.sha256(body).toHexString()) return Result.Invalid("wrong_payload")
        } else if (payload != null) {
            return Result.Invalid("wrong_payload")
        }
        if (!ProofOfWork.meets(event, difficulty)) return Result.Invalid("proof_of_work_too_low")
        return Result.Valid(event.pubkey, event.id)
    }

    const val DEFAULT_MAX_SKEW_SECONDS: Long = 60
}

/**
 * The ids of the NIP-98 events already accepted during their time window, in memory only: an
 * intercepted authorization cannot be used twice. Bounded; nothing about the client is kept.
 */
class Nip98ReplayGuard(private val windowSeconds: Long = 2 * Nip98.DEFAULT_MAX_SKEW_SECONDS + 10, private val maxEntries: Int = 100_000) {
    private val seen = LinkedHashMap<String, Long>()

    /** True the first time [eventId] is seen within the window, false afterwards. */
    fun firstUse(eventId: String, now: Long): Boolean {
        val iterator = seen.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value >= now - windowSeconds && seen.size <= maxEntries) break
            iterator.remove()
        }
        if (eventId in seen) return false
        seen[eventId] = now
        return true
    }
}
