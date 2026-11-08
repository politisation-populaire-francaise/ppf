package org.politisationpopulairefrancaise.core.platform

import org.politisationpopulairefrancaise.core.signing.EventReplacement
import org.politisationpopulairefrancaise.core.signing.SignedEvent

/** Why an event is, or is not, a valid content of the platform for a use. */
enum class TrustVerdict {
    TRUSTED,

    /** No root key is configured: nothing is trusted (never "everything"). */
    NO_ROOT,

    /** The id or the signature of the event is wrong. */
    NOT_AUTHENTIC,

    /** The root only signs certificates, revocations and the team list, never a content. */
    ROOT_NEVER_SIGNS_CONTENTS,

    /** The kind is not one the use may sign. */
    KIND_NOT_ALLOWED,

    /** The kind is one of the use, but the tags that tell the uses apart are not this use's ([SigningUse.matches]). */
    FORM_OF_ANOTHER_USE,

    /** No certificate of the root is known for the key. */
    UNKNOWN_KEY,

    /** The key is certified, but not for this use. */
    NOT_CERTIFIED_FOR_USE,

    /** The content is dated before the start of the certificate. */
    NOT_YET_VALID,

    /** The content is dated at or after the end of the certificate. */
    EXPIRED,

    /** The key was revoked at the date of the content (or one of its certificates could not be read). */
    REVOKED,
}

/** What happened to a certificate offered to [PlatformTrust.offer]. */
enum class CertificateOffer {
    /**
     * The trust changed, keep the event: a new certificate, a newer version than the one known for its
     * key, or a revocation taking effect earlier than the one known (whatever its date of signature).
     */
    STORED,

    /** The very version already known. */
    DUPLICATE,

    /** Older than the version known for its key, and no earlier revocation: ignored. */
    SUPERSEDED,

    /** Not a certificate signed by the root (or no root configured): ignored. */
    REFUSED,
}

/**
 * Which contents speak for the platform (contract docs/integration/service-signature.md, section 2;
 * D-129), the same rule on the devices, on the server and in the signing service: an event is a valid
 * platform content for a use when its key is certified by the root for that use, at the event's
 * `created_at`, and not revoked at that date, and when it has the form of the use (its kind, and the tags
 * that tell the uses apart, [SigningUse.matches]).
 *
 * Immutable: [offer] and [plus] return a new trust. Two things are kept per working key:
 * - the latest certificate (latest `created_at`, the lowest id on equal dates), which gives the uses and
 *   the validity;
 * - the revocation that takes effect the earliest among all the root-signed revocations ever seen for the
 *   key (a certificate that cannot be read counts as a revocation from the start). Revocations are
 *   monotonic: once seen, a revocation is kept whatever the order in which the events arrive, and a later
 *   `active` certificate never un-revokes the key.
 *
 * Without a root key nothing is trusted.
 */
class PlatformTrust private constructor(
    /** The root public key (lowercase hex), null when none is configured. */
    val root: String?,
    private val latest: Map<String, KeyCertificate>,
    private val revocations: Map<String, KeyCertificate>,
) {
    val isConfigured: Boolean get() = root != null

    /** The latest certificate of each working key. */
    val certificates: List<KeyCertificate> get() = latest.values.sortedBy { it.key }

    /**
     * Every event this trust rests on, to keep on the device, to store or to serve: the latest certificate
     * of each key and, when it is another event, the revocation kept for that key. Offered again in any
     * order, they give back the same trust.
     */
    val events: List<SignedEvent> get() = latest.keys.sorted().flatMap(::eventsOf)

    fun certificate(key: String): KeyCertificate? = latest[key]

    /** The revocation kept for [key] (the earliest effective one ever seen), null when it was never revoked. */
    fun revocation(key: String): KeyCertificate? = revocations[key]

    /** The events kept for [key]: its latest certificate, then its revocation when that is another event. */
    fun eventsOf(key: String): List<SignedEvent> {
        val current = latest[key] ?: return emptyList()
        val revoked = revocations[key]?.takeIf { it.event.id != current.event.id }
        return listOfNotNull(current.event, revoked?.event)
    }

    /** Whether [key] is revoked for a content dated [time], by any revocation ever seen. */
    fun isRevokedAt(key: String, time: Long): Boolean = revocations[key]?.isRevokedAt(time) == true

    /** Adds [event] if it is a certificate of the root that changes the trust (see [CertificateOffer.STORED]). */
    fun offer(event: SignedEvent): Pair<PlatformTrust, CertificateOffer> {
        val root = root ?: return this to CertificateOffer.REFUSED
        val certificate = KeyCertificate.read(event, root) ?: return this to CertificateOffer.REFUSED
        val key = certificate.key
        val known = latest[key]
        val kept = revocations[key]
        val newer = known == null || EventReplacement.supersedes(event, known.event)
        val earlierRevocation = certificate.status == KeyStatus.REVOKED && (kept == null || takesEffectBefore(certificate, kept))
        if (!newer && !earlierRevocation) {
            val duplicate = known?.event?.id == event.id || kept?.event?.id == event.id
            return this to if (duplicate) CertificateOffer.DUPLICATE else CertificateOffer.SUPERSEDED
        }
        val nextLatest = if (newer) latest + (key to certificate) else latest
        val nextRevocations = if (earlierRevocation) revocations + (key to certificate) else revocations
        return PlatformTrust(root, nextLatest, nextRevocations) to CertificateOffer.STORED
    }

    fun plus(event: SignedEvent): PlatformTrust = offer(event).first

    fun plusAll(events: Iterable<SignedEvent>): PlatformTrust = events.fold(this) { trust, event -> trust.plus(event) }

    /** Whether [event] is a valid content of the platform for [use], at its own date. */
    fun isPlatformContent(event: SignedEvent, use: SigningUse): Boolean = check(event, use) == TrustVerdict.TRUSTED

    /** The detailed verdict behind [isPlatformContent]. */
    fun check(event: SignedEvent, use: SigningUse): TrustVerdict {
        val root = root ?: return TrustVerdict.NO_ROOT
        if (!event.verify()) return TrustVerdict.NOT_AUTHENTIC
        if (event.pubkey == root) return TrustVerdict.ROOT_NEVER_SIGNS_CONTENTS
        if (event.kind !in use.kinds) return TrustVerdict.KIND_NOT_ALLOWED
        if (!use.matches(event)) return TrustVerdict.FORM_OF_ANOTHER_USE
        return keyVerdict(event.pubkey, use, event.createdAt)
    }

    /** Whether [key] speaks for the platform for [use] at [time] (the signing service asks it before signing). */
    fun keyVerdict(key: String, use: SigningUse, time: Long): TrustVerdict {
        val root = root ?: return TrustVerdict.NO_ROOT
        if (key == root) return TrustVerdict.ROOT_NEVER_SIGNS_CONTENTS
        val certificate = latest[key] ?: return TrustVerdict.UNKNOWN_KEY
        return when {
            use !in certificate.uses -> TrustVerdict.NOT_CERTIFIED_FOR_USE
            certificate.isRevokedAt(time) || isRevokedAt(key, time) -> TrustVerdict.REVOKED
            time < certificate.notBefore -> TrustVerdict.NOT_YET_VALID
            time >= certificate.notAfter -> TrustVerdict.EXPIRED
            else -> TrustVerdict.TRUSTED
        }
    }

    companion object {
        /**
         * The trust of [root] (lowercase hex; null, empty or malformed: nothing is trusted) with the
         * certificates and revocations among [events] (anything else is ignored).
         */
        fun of(root: String?, events: Iterable<SignedEvent> = emptyList()): PlatformTrust {
            val valid = root?.takeIf(::isHexKey)
            return PlatformTrust(valid, emptyMap(), emptyMap()).plusAll(events)
        }

        /** The first date [revocation] no longer covers (no date: from the very start). */
        private fun effectiveFrom(revocation: KeyCertificate): Long = revocation.revokedFrom ?: Long.MIN_VALUE

        /** Whether [candidate] takes effect before [kept]; on equal dates the lowest id, so that every order converges. */
        private fun takesEffectBefore(candidate: KeyCertificate, kept: KeyCertificate): Boolean {
            val a = effectiveFrom(candidate)
            val b = effectiveFrom(kept)
            return a < b || (a == b && candidate.event.id < kept.event.id)
        }
    }
}

/**
 * The root key of the platform (contract, section 3: it lives on the owner's phone, option A). Its
 * public key is the only constant of trust written in the applications and on the site; it stays empty
 * until the owner creates the root (step 5 of the contract), and then nothing is trusted as a platform
 * content. The example contents keep their own example key (EditorialTrust) until step 6.
 */
object PlatformRoot {
    /** Lowercase hex public key of the root; empty until the root exists. */
    const val PUBLIC_KEY: String = ""

    fun trust(events: Iterable<SignedEvent> = emptyList()): PlatformTrust = PlatformTrust.of(PUBLIC_KEY.ifEmpty { null }, events)
}
