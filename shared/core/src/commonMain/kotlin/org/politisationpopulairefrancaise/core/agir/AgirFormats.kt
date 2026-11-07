package org.politisationpopulairefrancaise.core.agir

/**
 * The shared vocabulary of the "Agir" space, version 1 of the contract written in
 * docs/integration/agir-contrat.md. Every block of the space (agenda, calls, directory of
 * organisations, server) uses these names, so that the device and the server read the same signed
 * events. Wire values are lowercase ASCII and never change once published; labels live in the
 * string resources of the interface.
 */
object AgirFormats {
    /** Version of this contract, for the integration notes. */
    const val CONTRACT_VERSION: String = "1"

    // Kinds and content types

    /** NIP-52 time-based calendar event: a mobilization of the agenda (M1, AG-01). */
    const val KIND_MOBILIZATION: Int = 31923

    /** `ppf-type` of a call (AP-01 to AP-10), published as a kind 30023 event like the demands. */
    const val TYPE_CALL: String = "appel"

    /** `ppf-type` of an entry of the directory of organisations (M3), kind 30023 signed by an editorial key. */
    const val TYPE_ORGANISATION: String = "organisation"

    // Tags shared by several blocks

    const val TAG_TYPE: String = "ppf-type"
    const val TAG_TERRITORY: String = "ppf-territory"
    const val TAG_CHARTER: String = "ppf-charter"
    const val CHARTER_VERSION: String = "1.0"
    const val TAG_TITLE: String = "title"
    const val TAG_SUMMARY: String = "summary"
    const val TAG_LOCATION: String = "location"
    const val TAG_GEOHASH: String = "g"
    const val TAG_LINK: String = "r"
    const val TAG_START: String = "start"
    const val TAG_END: String = "end"
    const val TAG_START_TZID: String = "start_tzid"
    const val TAG_PUBLISHED_AT: String = "published_at"
    const val TIME_ZONE: String = "Europe/Paris"

    /** Reference to another addressable content (`kind:pubkey:d`), e.g. the call an event comes from (AP-07). */
    const val TAG_ADDRESS: String = "a"

    /** A participant key; on a mobilization, the fourth value "organizer" marks an organiser (NIP-52). */
    const val TAG_PUBKEY: String = "p"
    const val ROLE_ORGANIZER: String = "organizer"

    /** Longest title, in characters, for events, calls and directory entries. */
    const val MAX_TITLE: Int = 140

    /** Longest description (event content), in characters. */
    const val MAX_TEXT: Int = 8000

    /** Longest value of any other tag, in characters (the server refuses longer ones with `too_long`). */
    const val MAX_TAG_VALUE: Int = 1000

    /** Longest report of a closed call (`ppf-report`, AP-09), in characters. */
    const val MAX_REPORT: Int = 2000

    // Mobilizations (kind 31923)

    const val TAG_EVENT_TYPE: String = "ppf-event-type"
    const val TAG_EVENT_STATUS: String = "ppf-event-status"
    const val TAG_DECLARATION: String = "ppf-declaration"

    /** M1: demonstrations, blockades, gatherings, strikes, general assemblies, meetings. */
    val EVENT_TYPES: List<String> = listOf("manifestation", "blocage", "rassemblement", "greve", "assemblee-generale", "reunion", "autre")

    /** Absent means "prevu". */
    val EVENT_STATUSES: List<String> = listOf("prevu", "annule", "reporte")

    /** Declaration of a demonstration to the prefecture (AP-10): done, in progress, not done. */
    val DECLARATION_STATES: List<String> = listOf("faite", "en-cours", "non-faite")

    // Calls (kind 30023, ppf-type = appel)

    const val TAG_CALL_TYPE: String = "ppf-call-type"
    const val TAG_CALL_STATUS: String = "ppf-call-status"
    const val TAG_GOAL: String = "ppf-goal"
    const val TAG_DEADLINE: String = "ppf-deadline"
    const val TAG_REPORT: String = "ppf-report"

    /** AP-01, in the order of the inventory. */
    val CALL_TYPES: List<String> = listOf(
        "se-reunir", "debattre", "s-organiser", "manifester", "soutenir", "petition", "interpeller",
        "boycotter", "collecte", "benevoles", "information", "temoins", "autre",
    )

    /** Absent means "ouvert". A closed call keeps its address and may carry its report (AP-09). */
    val CALL_STATUSES: List<String> = listOf("ouvert", "clos")

    /** Only verified organisations may publish this type (AP-08, point of attention 12). */
    const val CALL_TYPE_FUNDRAISING: String = "collecte"

    /** Always held for review, whoever publishes it (AP-02). */
    const val CALL_TYPE_WITNESSES: String = "temoins"

    /**
     * AP-10: the tags a call of each type must carry, on top of the common ones (d, title,
     * ppf-type, ppf-call-type, ppf-territory, ppf-charter). "location|r" means one of the two.
     * The device refuses to send a call that lacks one; the server refuses it with
     * `400 missing_field` and the name of the first missing tag in the message.
     */
    val CALL_REQUIRED_TAGS: Map<String, List<String>> = mapOf(
        "se-reunir" to listOf("ppf-object", TAG_START, "location|r", "ppf-organizer", "ppf-accessibility"),
        "debattre" to listOf("ppf-object", TAG_START, "location|r", "ppf-organizer", "ppf-accessibility"),
        "s-organiser" to listOf("ppf-object", TAG_START, "location|r", "ppf-organizer", "ppf-accessibility"),
        "manifester" to listOf(TAG_START, TAG_LOCATION, "ppf-organizer", TAG_DECLARATION),
        "soutenir" to listOf("ppf-cause", "ppf-action", TAG_DEADLINE),
        "petition" to listOf(TAG_LINK, "ppf-platform", TAG_GOAL, TAG_DEADLINE),
        "interpeller" to listOf("ppf-targets", "ppf-request", TAG_DEADLINE),
        "boycotter" to listOf("ppf-company", "ppf-source", "ppf-alternatives", "ppf-boycott-duration"),
        "collecte" to listOf("ppf-beneficiary", TAG_GOAL, "ppf-use", TAG_LINK, TAG_DEADLINE),
        "benevoles" to listOf("ppf-mission", "ppf-skills", "ppf-people", "location|ppf-remote", "ppf-period"),
        "information" to listOf("ppf-source"),
        "temoins" to listOf(TAG_START, TAG_END, TAG_LOCATION, "ppf-evidence", "ppf-recipient"),
        "autre" to emptyList(),
    )

    /** `ppf-platform` of a petition: an official platform (Assemblée, Sénat, ICE) or another one. */
    val PETITION_PLATFORMS: List<String> = listOf("assemblee-nationale", "senat", "initiative-citoyenne-europeenne", "autre")

    // Directory of organisations (kind 30023, ppf-type = organisation, editorial keys only)

    const val TAG_ORG_TYPE: String = "ppf-org-type"
    const val TAG_VERIFIED: String = "ppf-verified"
    const val TAG_CONTACT_PHONE: String = "ppf-contact-phone"
    const val TAG_CONTACT_EMAIL: String = "ppf-contact-email"
    const val TAG_CONTACT_ADDRESS: String = "ppf-contact-address"

    /** M3. */
    val ORG_TYPES: List<String> = listOf("association", "syndicat", "collectif", "legal-team", "street-medics", "avocats-benevoles", "autre")
}
