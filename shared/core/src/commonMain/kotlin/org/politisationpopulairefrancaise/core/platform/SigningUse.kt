package org.politisationpopulairefrancaise.core.platform

import org.politisationpopulairefrancaise.core.groups.RoomModeration
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent

/**
 * What a working key of the platform may sign (contract docs/integration/service-signature.md,
 * sections 2 and 4; decision D-129). Each use has its own working key, certified by the root for this
 * use only: a key compromised for one use is worth nothing for the others.
 *
 * [kinds] are the only event kinds a key of this use may sign, and [matches] tells, by the kind and the
 * tags that set the uses apart, whether an event has the form of this use: the applications check both,
 * so that a key certified for one use never validates the content of another use of the same kind. The
 * signing service enforces the whole shape (tags, approvals, rates). [automatic] uses are signed without
 * any human approval, under strict rules, and they are the only ones.
 *
 * A new use is a change of the contract: its form here, its rule in the signing service, its permission
 * in the back-office (`SigningApprovals`) for a human use, and its double validation ([DoubleValidation]).
 */
enum class SigningUse(val id: String, val kinds: Set<Int>, val automatic: Boolean) {
    /** Contents of « Comprendre » (sheets, files, fact checks, courses, glossary...), NIP-23. */
    EDITORIAL_COMPRENDRE("editorial.comprendre", setOf(EventKinds.LONG_FORM_ARTICLE), automatic = false),

    /** Sourced blocks of « Voir » (facts, people, texts, indicators...), NIP-23. */
    EDITORIAL_VOIR("editorial.voir", setOf(EventKinds.LONG_FORM_ARTICLE), automatic = false),

    /** Status labels of the demands (NIP-32, kind 1985, namespace `ppf-status`). */
    REVENDICATIONS_STATUS("revendications.status", setOf(EventKinds.LABEL), automatic = false),

    /** Sheets of the verified organisations of « Agir » (NIP-23, `ppf-type` = `organisation`). */
    AGIR_ORGANISATIONS("agir.organisations", setOf(EventKinds.LONG_FORM_ARTICLE), automatic = false),

    /**
     * The contents of « Décider » signed by the platform (NIP-23, one `ppf-type` of [PACTE_TYPES]): the public
     * table of the democratic Pact and the candidates' answers (`pacte`, `engagement`), the official
     * petitions relayed by the team with the link to the official site (`petition`), and the final texts of
     * a deliberation (`proposition-finale`).
     */
    DECIDER_PACTE("decider.pacte", setOf(EventKinds.LONG_FORM_ARTICLE), automatic = false),

    /** Timestamp proofs of the archives (D-111): OpenTimestamps attestations (NIP-03, kind 1040). */
    ARCHIVES_TIMESTAMPS("archives.timestamps", setOf(EventKinds.OPENTIMESTAMPS), automatic = true),

    /** The index of the open data packages (NIP-78, kind 30078, `d` = `ppf:parlement-index`). */
    OPENDATA_INDEX("opendata.index", setOf(EventKinds.APP_DATA), automatic = true),

    /** The closing statement of a ballot box (NIP-78, kind 30078, `d` = `ppf:urne-cloture:<n>`). */
    DECIDER_URN_CLOSURE("decider.urn_closure", setOf(EventKinds.APP_DATA), automatic = true),

    /**
     * The takeover of a public room left without administrator (D-144, S-146): a NIP-29 put-user (kind 9000)
     * signed by the platform that names one administrator, the reason as content ([GroupTakeover]). Human,
     * never automatic: a compromised main server must not be able to make the platform name an administrator
     * of any room. The member who asks in the back-office queue `groups.orphans` is the author of the draft
     * (always named, [AUTHOR_REQUIRED_USES]); another member approves it on the page of the signing service.
     */
    GROUPS_TAKEOVER("groups.takeover", setOf(RoomModeration.PUT_USER), automatic = false),
    ;

    /**
     * Whether an event of [kind] with [tags] and [content] has the form of this use: one of its kinds and the
     * tags that tell the uses apart. Editorial contents never carry the `ppf-type` of another use; an organisation
     * sheet has exactly `ppf-type` = `organisation`; a content of « Décider » exactly one of [PACTE_TYPES]; a status
     * label exactly the namespace `ppf-status`; the open data index exactly its `d`; a closing statement a
     * single `d` of the ballot boxes. An OpenTimestamps attestation is told apart by its kind alone. A takeover
     * of a room has the whole form of [GroupTakeover] (one `h`, one `p` with the role `admin`, no other tag),
     * and its [content], the reason, counts too: the other uses ignore [content], and without one a takeover
     * never matches (it fails closed).
     */
    fun matches(kind: Int, tags: List<List<String>>, content: String = ""): Boolean {
        if (kind !in kinds) return false
        fun values(name: String): List<String> = tags.filter { it.isNotEmpty() && it[0] == name }.map { it.getOrElse(1) { "" } }
        return when (this) {
            EDITORIAL_COMPRENDRE, EDITORIAL_VOIR -> values(TAG_TYPE).none { it in TYPES_OF_OTHER_USES }
            AGIR_ORGANISATIONS -> values(TAG_TYPE) == listOf(TYPE_ORGANISATION)
            DECIDER_PACTE -> values(TAG_TYPE).let { it.size == 1 && it[0] in PACTE_TYPES }
            REVENDICATIONS_STATUS -> values("L") == listOf(STATUS_NAMESPACE)
            ARCHIVES_TIMESTAMPS -> true
            OPENDATA_INDEX -> values("d") == listOf(OPENDATA_INDEX_IDENTIFIER)
            DECIDER_URN_CLOSURE -> values("d").let { it.size == 1 && it[0].startsWith(URN_CLOSURE_PREFIX) }
            GROUPS_TAKEOVER -> GroupTakeover.breach(kind, tags, content) == null
        }
    }

    fun matches(event: SignedEvent): Boolean = matches(event.kind, event.tags, event.content)

    fun matches(event: UnsignedEvent): Boolean = matches(event.kind, event.tags, event.content)

    override fun toString(): String = id

    companion object {
        const val TAG_TYPE: String = "ppf-type"
        const val TYPE_ORGANISATION: String = "organisation"
        const val STATUS_NAMESPACE: String = "ppf-status"
        const val OPENDATA_INDEX_IDENTIFIER: String = "ppf:parlement-index"
        const val URN_CLOSURE_PREFIX: String = "ppf:urne-cloture:"

        /**
         * The `ppf-type` values of [DECIDER_PACTE]: the Pact, the candidates' answers, the official petitions
         * relayed by the team and the final texts of a deliberation.
         */
        val PACTE_TYPES: Set<String> = setOf("pacte", "engagement", "petition", "proposition-finale")

        /** The `ppf-type` values that belong to a use other than the editorial ones. */
        val TYPES_OF_OTHER_USES: Set<String> = setOf(TYPE_ORGANISATION) + PACTE_TYPES

        /**
         * The uses whose drafts always name their author, in a single validation too (D-142): the editorial
         * spaces, where the reviewer who approves is never the author (« règle des deux personnes » of the
         * editorial circuit), and the takeover of a room (D-144: the member who asks in the queue is the
         * author and another one approves, so that two persons always take part). For the other human uses
         * the author is named for a double validation only. In every case a named author never approves
         * their own draft.
         */
        val AUTHOR_REQUIRED_USES: Set<SigningUse> = setOf(EDITORIAL_COMPRENDRE, EDITORIAL_VOIR, GROUPS_TAKEOVER)

        fun fromId(id: String): SigningUse? = entries.firstOrNull { it.id == id }
    }
}
