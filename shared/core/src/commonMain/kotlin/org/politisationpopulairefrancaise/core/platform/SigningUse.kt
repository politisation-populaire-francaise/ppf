package org.politisationpopulairefrancaise.core.platform

import org.politisationpopulairefrancaise.core.signing.EventKinds

/**
 * What a working key of the platform may sign (contract docs/integration/service-signature.md,
 * sections 2 and 4; decision D-129). Each use has its own working key, certified by the root for this
 * use only: a key compromised for one use is worth nothing for the others.
 *
 * [kinds] are the only event kinds a key of this use may sign; the signing service enforces finer
 * rules (tags, approvals, rates), the applications check at least the kind. [automatic] uses are signed
 * without any human approval, under strict rules, and they are the only ones.
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

    /** The public table of the democratic Pact and the candidates' answers (NIP-23, `pacte`, `engagement`). */
    DECIDER_PACTE("decider.pacte", setOf(EventKinds.LONG_FORM_ARTICLE), automatic = false),

    /** Timestamp proofs of the archives (D-111): OpenTimestamps attestations (NIP-03, kind 1040). */
    ARCHIVES_TIMESTAMPS("archives.timestamps", setOf(EventKinds.OPENTIMESTAMPS), automatic = true),

    /** The index of the open data packages (NIP-78, kind 30078, `d` = `ppf:parlement-index`). */
    OPENDATA_INDEX("opendata.index", setOf(EventKinds.APP_DATA), automatic = true),

    /** The closing statement of a ballot box (NIP-78, kind 30078, `d` = `ppf:urne-cloture:<n>`). */
    DECIDER_URN_CLOSURE("decider.urn_closure", setOf(EventKinds.APP_DATA), automatic = true),
    ;

    override fun toString(): String = id

    companion object {
        fun fromId(id: String): SigningUse? = entries.firstOrNull { it.id == id }
    }
}
