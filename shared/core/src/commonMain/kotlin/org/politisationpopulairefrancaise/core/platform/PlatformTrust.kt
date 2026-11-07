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

    /** No certificate of the root is known for the key. */
    UNKNOWN_KEY,

    /** The key is certified, but not for this use. */
    NOT_CERTIFIED_FOR_USE,

    /** The content is dated before the start of the certificate. */
    NOT_YET_VALID,

    /** The content is dated at or after the end of the certificate. */
    EXPIRED,

    /** The key was revoked at the date of the content (or its certificate could not be read). */
    REVOKED,
}

/** What happened to a certificate offered to [PlatformTrust.offer]. */
enum class CertificateOffer {
    /** New, or newer than the version known for its key. */
    STORED,

    /** The very version already known. */
    DUPLICATE,

    /** Older than the version known for its key: ignored. */
    SUPERSEDED,

    /** Not a certificate signed by the root (or no root configured): ignored. */
    REFUSED,
}

/**
 * Which contents speak for the platform (contract docs/integration/service-signature.md, section 2;
 * D-129), the same rule on the devices, on the server and in the signing service: an event is a valid
 * platform content for a use when its key is certified by the root for that use, at the event's
 * `created_at`, and not revoked at that date, and when its kind is one the use may sign.
 *
 * Immutable: [offer] and [plus] return a new trust. Only the latest version of each certificate is kept
 * (latest `created_at`, the lowest id on equal dates), so a revocation once received is never undone by
 * an older certificate served again. Without a root key nothing is trusted.
 */
class PlatformTrust private constructor(
    /** The root public key (lowercase hex), null when none is configured. */
    val root: String?,
    private val latest: Map<String, KeyCertificate>,
) {
    val isConfigured: Boolean get() = root != null

    /** The latest certificates known, one per working key (to keep on the device). */
    val certificates: List<KeyCertificate> get() = latest.values.sortedBy { it.key }

    fun certificate(key: String): KeyCertificate? = latest[key]

    /** Adds [event] if it is a certificate of the root newer than the one known for its key. */
    fun offer(event: SignedEvent): Pair<PlatformTrust, CertificateOffer> {
        val root = root ?: return this to CertificateOffer.REFUSED
        val certificate = KeyCertificate.read(event, root) ?: return this to CertificateOffer.REFUSED
        val known = latest[certificate.key]
        return when {
            known == null || EventReplacement.supersedes(event, known.event) ->
                PlatformTrust(root, latest + (certificate.key to certificate)) to CertificateOffer.STORED
            known.event.id == event.id -> this to CertificateOffer.DUPLICATE
            else -> this to CertificateOffer.SUPERSEDED
        }
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
        return keyVerdict(event.pubkey, use, event.createdAt)
    }

    /** Whether [key] speaks for the platform for [use] at [time] (the signing service asks it before signing). */
    fun keyVerdict(key: String, use: SigningUse, time: Long): TrustVerdict {
        val root = root ?: return TrustVerdict.NO_ROOT
        if (key == root) return TrustVerdict.ROOT_NEVER_SIGNS_CONTENTS
        val certificate = latest[key] ?: return TrustVerdict.UNKNOWN_KEY
        return when {
            use !in certificate.uses -> TrustVerdict.NOT_CERTIFIED_FOR_USE
            certificate.isRevokedAt(time) -> TrustVerdict.REVOKED
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
            return PlatformTrust(valid, emptyMap()).plusAll(events)
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
