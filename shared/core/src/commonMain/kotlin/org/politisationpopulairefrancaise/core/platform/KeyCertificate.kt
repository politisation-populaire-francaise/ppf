package org.politisationpopulairefrancaise.core.platform

import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent

/** The state a certificate gives its working key: `active`, or `revoquee` from an effective date. */
enum class KeyStatus(val tagValue: String) {
    ACTIVE("active"),
    REVOKED("revoquee"),
}

/**
 * The certificate of a working key of the platform (docs/FORMATS.md, section 4 decies; D-129): an
 * addressable event of kind [EventKinds.PLATFORM_KEY_CERTIFICATE] signed by the root, whose `d` is the
 * working key. "This key speaks for PPF, for these uses, from [notBefore] until [notAfter] excluded."
 * A revocation is the same address published again with the status `revoquee` and the date from
 * which the key is no longer trusted. The latest version of an address wins, as for every
 * addressable event.
 *
 * Reading fails closed: a certificate whose status, dates or validity are not understood revokes its
 * key from the start, so that a mistake or a future version never extends trust.
 */
class KeyCertificate private constructor(
    val event: SignedEvent,
    /** The working key, lowercase hex. */
    val key: String,
    /** The known uses it is certified for (unknown uses are ignored). */
    val uses: Set<SigningUse>,
    val notBefore: Long,
    val notAfter: Long,
    val status: KeyStatus,
    /** For a revoked key, the first date it no longer covers; null when it covers nothing at all. */
    val revokedFrom: Long?,
    /** False when the certificate could not be read entirely (then it revokes its key from the start). */
    val wellFormed: Boolean,
) {
    fun isRevokedAt(time: Long): Boolean = status == KeyStatus.REVOKED && (revokedFrom == null || time >= revokedFrom)

    /** Whether the key speaks for the platform at [time], whatever the use. */
    fun validAt(time: Long): Boolean = time >= notBefore && time < notAfter && !isRevokedAt(time)

    /** Whether the key speaks for the platform for [use] at [time]. */
    fun covers(use: SigningUse, time: Long): Boolean = use in uses && validAt(time)

    override fun toString(): String = "KeyCertificate(key=$key, uses=$uses, notBefore=$notBefore, notAfter=$notAfter, status=$status)"

    companion object {
        const val TAG_USE: String = "ppf-key-use"
        const val TAG_NOT_BEFORE: String = "ppf-not-before"
        const val TAG_NOT_AFTER: String = "ppf-not-after"
        const val TAG_STATUS: String = "ppf-key-status"

        /** A working key is certified for 90 days at most (contract, section 2). */
        const val MAX_VALIDITY_SECONDS: Long = 90L * 86_400

        /** Shown by Nostr clients that do not know the kind (NIP-31). */
        const val ALT: String = "Certificat d'une clé de travail de la plateforme PPF"

        /**
         * Reads [event] as a certificate signed by [root] (lowercase hex). Null when it is not one: another
         * kind, another author, an invalid signature, no single valid `d`, or the root itself as working key
         * (the root never signs contents).
         */
        fun read(event: SignedEvent, root: String): KeyCertificate? {
            if (event.kind != EventKinds.PLATFORM_KEY_CERTIFICATE || event.pubkey != root || !event.verify()) return null
            val key = event.tags.singleValue("d") ?: return null
            if (!isHexKey(key) || key == root) return null
            val uses = event.tagValues(TAG_USE).mapNotNull(SigningUse::fromId).toSet()
            val notBefore = event.tags.singleValue(TAG_NOT_BEFORE)?.let(::parseSeconds)
            val notAfter = event.tags.singleValue(TAG_NOT_AFTER)?.let(::parseSeconds)
            val validity = notBefore != null && notAfter != null && notAfter > notBefore && notAfter - notBefore <= MAX_VALIDITY_SECONDS
            val statusTags = event.tags.filter { it.isNotEmpty() && it[0] == TAG_STATUS }
            val statusTag = statusTags.singleOrNull()
            val status = when {
                statusTag == null || statusTag.size < 2 -> null
                statusTag[1] == KeyStatus.ACTIVE.tagValue -> KeyStatus.ACTIVE
                statusTag[1] == KeyStatus.REVOKED.tagValue -> KeyStatus.REVOKED
                else -> null
            }
            val revokedFrom = if (status == KeyStatus.REVOKED) statusTag?.getOrNull(2)?.let(::parseSeconds) else null
            if (!validity || status == null) {
                return KeyCertificate(event, key, uses, 0, 0, KeyStatus.REVOKED, null, wellFormed = false)
            }
            return KeyCertificate(event, key, uses, notBefore!!, notAfter!!, status, revokedFrom, wellFormed = true)
        }

        /**
         * The unsigned certificate (or revocation, with [status] `REVOKED` and [revokedFrom]) the root signs:
         * prepared by the back-office, shown in clear on the root's phone, signed there.
         */
        fun unsigned(
            root: String,
            key: String,
            uses: Collection<SigningUse>,
            notBefore: Long,
            notAfter: Long,
            createdAt: Long,
            status: KeyStatus = KeyStatus.ACTIVE,
            revokedFrom: Long? = null,
        ): UnsignedEvent {
            require(isHexKey(key) && key != root) { "The working key is a lowercase hex key other than the root" }
            require(uses.isNotEmpty()) { "A certificate names at least one use" }
            require(notAfter > notBefore && notAfter - notBefore <= MAX_VALIDITY_SECONDS) { "A certificate is valid 90 days at most" }
            require(status == KeyStatus.REVOKED || revokedFrom == null) { "Only a revocation has an effective date" }
            val statusTag = if (status == KeyStatus.REVOKED && revokedFrom != null) {
                listOf(TAG_STATUS, status.tagValue, revokedFrom.toString())
            } else {
                listOf(TAG_STATUS, status.tagValue)
            }
            val tags = buildList {
                add(listOf("d", key))
                uses.map { it.id }.distinct().sorted().forEach { add(listOf(TAG_USE, it)) }
                add(listOf(TAG_NOT_BEFORE, notBefore.toString()))
                add(listOf(TAG_NOT_AFTER, notAfter.toString()))
                add(statusTag)
                add(listOf("alt", ALT))
            }
            return UnsignedEvent(root, createdAt, EventKinds.PLATFORM_KEY_CERTIFICATE, tags, "")
        }
    }
}

/** The value of the only tag named [name], null when there is none, several, or one without value. */
internal fun List<List<String>>.singleValue(name: String): String? =
    filter { it.isNotEmpty() && it[0] == name }.singleOrNull()?.getOrNull(1)

/** A date in seconds since 1970 written in decimal digits only, as in the tags of the contents. */
internal fun parseSeconds(value: String): Long? =
    if (value.isEmpty() || value.length > 18 || !value.all { it in '0'..'9' }) null else value.toLongOrNull()

internal fun isHexKey(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
