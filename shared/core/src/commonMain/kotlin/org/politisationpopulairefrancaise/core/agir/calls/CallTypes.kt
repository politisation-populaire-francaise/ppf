package org.politisationpopulairefrancaise.core.agir.calls

import org.politisationpopulairefrancaise.core.agir.AgirFormats

/** What answering a call means, by type (AP-03). The answer stays on the device (contract, section 6). */
enum class CallResponse {
    /** « J'y serai »: a meeting, a debate, a demonstration. */
    ATTEND,

    /** « Je soutiens »: a cause, an interpellation, a boycott, an information, anything else. */
    SUPPORT,

    /** « J'ai signé »: a petition. */
    SIGNED,

    /** « J'aide »: volunteers, a fundraising. */
    HELP,

    /** « Je témoigne »: waits for the secure channel of testimonies (AG-07, not built yet). */
    TESTIFY,
}

/** The thirteen types of calls of AP-01, with their wire values ([AgirFormats.CALL_TYPES]). */
enum class CallType(val tagValue: String, val response: CallResponse) {
    SE_REUNIR("se-reunir", CallResponse.ATTEND),
    DEBATTRE("debattre", CallResponse.ATTEND),
    S_ORGANISER("s-organiser", CallResponse.ATTEND),
    MANIFESTER("manifester", CallResponse.ATTEND),
    SOUTENIR("soutenir", CallResponse.SUPPORT),
    PETITION("petition", CallResponse.SIGNED),
    INTERPELLER("interpeller", CallResponse.SUPPORT),
    BOYCOTTER("boycotter", CallResponse.SUPPORT),
    COLLECTE("collecte", CallResponse.HELP),
    BENEVOLES("benevoles", CallResponse.HELP),
    INFORMATION("information", CallResponse.SUPPORT),
    TEMOINS("temoins", CallResponse.TESTIFY),
    AUTRE("autre", CallResponse.SUPPORT),
    ;

    /** AP-10: what this type requires on top of the common tags; each entry lists its alternatives. */
    val requirements: List<CallRequirement>
        get() = AgirFormats.CALL_REQUIRED_TAGS[tagValue].orEmpty().map { CallRequirement(it.split('|')) }

    /** True when [tag] is one of the alternatives of a requirement of this type. */
    fun requires(tag: String): Boolean = requirements.any { tag in it.alternatives }

    /** Only verified organisations may publish it (AP-08, point of attention 12). */
    val isFundraising: Boolean get() = tagValue == AgirFormats.CALL_TYPE_FUNDRAISING

    /** Always held for review, whoever publishes it (AP-02). */
    val isWitnesses: Boolean get() = tagValue == AgirFormats.CALL_TYPE_WITNESSES

    companion object {
        fun fromTag(value: String): CallType? = entries.firstOrNull { it.tagValue == value }
    }
}

/** One required piece of information: any of [alternatives] will do ("location|r": a place or a link). */
data class CallRequirement(val alternatives: List<String>) {
    /** The form of [AgirFormats.CALL_REQUIRED_TAGS]: "location|r". */
    val key: String get() = alternatives.joinToString("|")
}

/** Open (the default, absent tag) or closed with its report (AP-09). */
enum class CallStatus(val tagValue: String) {
    OPEN("ouvert"),
    CLOSED("clos"),
    ;

    companion object {
        fun fromTag(value: String?): CallStatus = entries.firstOrNull { it.tagValue == value } ?: OPEN
    }
}

/** How a field of the form is typed and checked. */
enum class CallFieldKind {
    /** One line of text. */
    TEXT,

    /** Several lines of text. */
    LONG_TEXT,

    /** A positive whole number. */
    NUMBER,

    /** A date and an hour in Paris, written as seconds since 1970. */
    DATE_TIME,

    /** One value of [CallField.choices]. */
    CHOICE,

    /** Yes or nothing: written "oui" when set (`ppf-remote`, the mission can be done remotely). */
    FLAG,
}

/** A field proper to some types of calls (AP-10), written as one tag. */
data class CallField(val tag: String, val kind: CallFieldKind, val choices: List<String> = emptyList()) {
    val maxLength: Int get() = when (kind) {
        CallFieldKind.LONG_TEXT -> CallRules.LONG_FIELD_MAX
        else -> CallRules.FIELD_MAX
    }
}

/**
 * The tags of the calls and the fields of the form. The common fields (title, text, summary,
 * territory, goal, deadline, place, link) are on every form; the specific ones appear for the
 * types that require them (AP-10), in the order of [AgirFormats.CALL_REQUIRED_TAGS].
 */
object CallFields {
    const val TAG_D: String = "d"
    const val TAG_ORIGIN: String = "ppf-origin"
    const val ORIGIN_HUMAN: String = "human"

    // Specific tags (AP-10), named as in AgirFormats.CALL_REQUIRED_TAGS.
    const val OBJECT: String = "ppf-object"
    const val ORGANIZER: String = "ppf-organizer"
    const val ACCESSIBILITY: String = "ppf-accessibility"
    const val CAUSE: String = "ppf-cause"
    const val ACTION: String = "ppf-action"
    const val PLATFORM: String = "ppf-platform"
    const val TARGETS: String = "ppf-targets"
    const val REQUEST: String = "ppf-request"
    const val COMPANY: String = "ppf-company"
    const val SOURCE: String = "ppf-source"
    const val ALTERNATIVES: String = "ppf-alternatives"
    /** Duration of a boycott (contract version 1, fixed on 2026-11-06: `ppf-duration` is the length of a learning path). */
    const val DURATION: String = "ppf-boycott-duration"
    const val BENEFICIARY: String = "ppf-beneficiary"
    const val USE: String = "ppf-use"
    const val MISSION: String = "ppf-mission"
    const val SKILLS: String = "ppf-skills"
    const val PEOPLE: String = "ppf-people"
    const val REMOTE: String = "ppf-remote"
    const val PERIOD: String = "ppf-period"
    const val EVIDENCE: String = "ppf-evidence"
    const val RECIPIENT: String = "ppf-recipient"

    /** The value of [REMOTE] when the mission can be done remotely. */
    const val YES: String = "oui"

    /** Tags of the common part of the form, even when a type requires them. */
    val COMMON_TAGS: Set<String> = setOf(AgirFormats.TAG_LOCATION, AgirFormats.TAG_LINK, AgirFormats.TAG_GOAL, AgirFormats.TAG_DEADLINE)

    /** Tags holding a date (seconds since 1970). */
    val DATE_TAGS: Set<String> = setOf(AgirFormats.TAG_START, AgirFormats.TAG_END, AgirFormats.TAG_DEADLINE)

    /** The fields proper to [type], in the order of the contract. */
    fun specific(type: CallType): List<CallField> =
        type.requirements.flatMap { it.alternatives }.filter { it !in COMMON_TAGS }.distinct().map(::field)

    /** How the tag [tag] is typed. */
    fun field(tag: String): CallField = when (tag) {
        AgirFormats.TAG_START, AgirFormats.TAG_END, AgirFormats.TAG_DEADLINE -> CallField(tag, CallFieldKind.DATE_TIME)
        AgirFormats.TAG_GOAL, PEOPLE -> CallField(tag, CallFieldKind.NUMBER)
        PLATFORM -> CallField(tag, CallFieldKind.CHOICE, AgirFormats.PETITION_PLATFORMS)
        AgirFormats.TAG_DECLARATION -> CallField(tag, CallFieldKind.CHOICE, AgirFormats.DECLARATION_STATES)
        REMOTE -> CallField(tag, CallFieldKind.FLAG)
        ACTION, REQUEST, USE, MISSION, ALTERNATIVES, ACCESSIBILITY -> CallField(tag, CallFieldKind.LONG_TEXT)
        else -> CallField(tag, CallFieldKind.TEXT)
    }

    /** Every specific tag a call may carry, whatever its type: what [Call.fields] keeps. */
    val SPECIFIC_TAGS: Set<String> = CallType.entries.flatMap { type -> specific(type).map { it.tag } }.toSet() - DATE_TAGS
}
