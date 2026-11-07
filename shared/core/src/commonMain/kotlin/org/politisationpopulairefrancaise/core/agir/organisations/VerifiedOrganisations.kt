package org.politisationpopulairefrancaise.core.agir.organisations

import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.signing.SignedEvent

/**
 * The keys of the verified organisations (contract of the Agir space, section 4): a key is
 * verified when it appears in the `p` tag of a directory entry signed by a trusted editorial key.
 * It is the same rule on the device and on the server, and the one the agenda and the calls use
 * to tell an organisation's publications apart (AG-02, AP-02, AP-08).
 *
 * An editorial key is not an organisation: whoever needs "editorial key or verified organisation"
 * asks [EditorialTrust.isTrusted] or [isVerified].
 *
 * When two entries name the same key, the most recently updated one describes it (on equal dates,
 * the lowest event id, as for replaceable events). An entry withdrawn by the team (a newer version
 * without the key, or without its verification) no longer verifies anything.
 */
class VerifiedOrganisations private constructor(private val byKey: Map<String, Organisation>) {

    /** The verified keys (lowercase hex). */
    val keys: Set<String> get() = byKey.keys

    val size: Int get() = byKey.size

    /** True when [pubkeyHex] is the key of a verified organisation. */
    fun isVerified(pubkeyHex: String): Boolean = pubkeyHex in byKey

    /** The directory entry of the organisation whose key is [pubkeyHex], or null when it is not verified. */
    fun organisationFor(pubkeyHex: String): Organisation? = byKey[pubkeyHex]

    override fun equals(other: Any?): Boolean = other is VerifiedOrganisations && other.byKey == byKey

    override fun hashCode(): Int = byKey.hashCode()

    override fun toString(): String = "VerifiedOrganisations(${byKey.size} keys)"

    companion object {
        val EMPTY: VerifiedOrganisations = VerifiedOrganisations(emptyMap())

        /**
         * From entries already accepted by [Organisation.from] (trusted author, valid form). The
         * caller is responsible for that check; [fromEvents] does it.
         */
        fun of(organisations: List<Organisation>): VerifiedOrganisations {
            val byKey = HashMap<String, Organisation>()
            for (organisation in organisations) {
                val key = organisation.accountKey ?: continue
                val current = byKey[key]
                if (current == null || describesBetter(organisation, current)) byKey[key] = organisation
            }
            return VerifiedOrganisations(byKey)
        }

        /** From signed events of any kind: only well-formed entries signed by a key of [trust] count. */
        fun fromEvents(events: List<SignedEvent>, trust: EditorialTrust, checkSignature: Boolean = true): VerifiedOrganisations =
            of(events.mapNotNull { Organisation.from(it, trust, checkSignature) })

        private fun describesBetter(candidate: Organisation, current: Organisation): Boolean =
            candidate.updatedAt > current.updatedAt || (candidate.updatedAt == current.updatedAt && candidate.event.id < current.event.id)
    }
}
