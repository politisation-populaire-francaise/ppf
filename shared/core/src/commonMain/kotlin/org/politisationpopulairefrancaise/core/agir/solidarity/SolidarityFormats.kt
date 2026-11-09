package org.politisationpopulairefrancaise.core.agir.solidarity

/**
 * The vocabulary of the solidarity rubric of "Agir" (M35: AG-10 solidarity funds, AG-12 volunteer
 * missions), docs/integration/agir-solidarite.md, section 2. Both contents are kind 30023 events
 * published by a key of the editorial team only (the server already refuses any other key for a
 * kind 30023 that is not a call). Wire values are lowercase ASCII and never change once published;
 * labels live in the string resources of the interface.
 *
 * Kept apart from `AgirFormats` so that the other blocks of "Agir" are not touched; the shared tags
 * (`ppf-type`, `ppf-territory`, `title`, `summary`, `published_at`, `ppf-verified`, `location`,
 * `ppf-deadline`, `r`, `a`, `g`) are those of `AgirFormats`.
 */
object SolidarityFormats {
    // Solidarity funds (AG-10)

    /** `ppf-type` of an entry of the directory of solidarity funds. */
    const val TYPE_FUND: String = "caisse"

    const val TAG_FUND_TYPE: String = "ppf-fund-type"
    const val FUND_STRIKE: String = "greve"
    const val FUND_LEGAL: String = "juridique"
    const val FUND_OTHER: String = "autre"

    /** Strike funds, legal solidarity funds (lawyers, fines, people prosecuted), anything else. */
    val FUND_TYPES: List<String> = listOf(FUND_STRIKE, FUND_LEGAL, FUND_OTHER)

    /** Who the fund serves, in words (the same tag as the fundraising calls, AP-10). */
    const val TAG_BENEFICIARY: String = "ppf-beneficiary"

    const val TAG_SECTOR: String = "ppf-sector"
    const val TAG_STRIKE: String = "ppf-strike"

    /** The name of whoever manages the fund, in words. */
    const val TAG_MANAGER: String = "ppf-manager"

    /**
     * The team checked that the fund serves neither a candidate nor a party (point of attention 12,
     * D-039): required, with the value [NO_PARTY_YES]; an entry without it is never shown.
     */
    const val TAG_NO_PARTY: String = "ppf-no-party"
    const val NO_PARTY_YES: String = "oui"

    const val TAG_FUND_STATUS: String = "ppf-fund-status"
    const val FUND_OPEN: String = "ouverte"
    const val FUND_CLOSED: String = "close"

    /** The team withdrew the entry: still readable, never a donation link. */
    const val FUND_WITHDRAWN: String = "retiree"
    val FUND_STATUSES: List<String> = listOf(FUND_OPEN, FUND_CLOSED, FUND_WITHDRAWN)

    // Volunteer missions (AG-12)

    /** `ppf-type` of a volunteer mission published by the team. */
    const val TYPE_MISSION: String = "mission"

    /** One tag per skill; at least one. */
    const val TAG_SKILL: String = "ppf-skill"
    const val SKILL_OTHER: String = "autre"

    /** AG-12: lawyers, translators, developers, designers, fact-checkers, moderators, hosts (S-147), others. */
    val SKILLS: List<String> = listOf("juriste", "traduction", "developpement", "graphisme", "verification", "moderation", "accueil", SKILL_OTHER)

    /** `oui` when the mission is done remotely (the same value as the calls for volunteers). */
    const val TAG_REMOTE: String = "ppf-remote"
    const val REMOTE_YES: String = "oui"

    /** The time asked, in words ("Deux heures par semaine"). */
    const val TAG_COMMITMENT: String = "ppf-commitment"

    /** How many people are wanted (a positive number). */
    const val TAG_PEOPLE: String = "ppf-people"

    const val TAG_MISSION_STATUS: String = "ppf-mission-status"
    const val MISSION_OPEN: String = "ouverte"
    const val MISSION_FILLED: String = "pourvue"
    const val MISSION_CLOSED: String = "close"
    val MISSION_STATUSES: List<String> = listOf(MISSION_OPEN, MISSION_FILLED, MISSION_CLOSED)

    /** Longest value of a free text tag written by the tools (beneficiary, sector, strike, manager, commitment). */
    const val MAX_SHORT_TEXT: Int = 300
}
