package org.politisationpopulairefrancaise.core.contact

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The vocabulary of the cases (docs/integration/contact-reclamations.md, section 3): stable English words, shared by the app, the server and the team. */
@Serializable
enum class CaseType(val wire: String) {
    /** S-102: a bug, a suggestion or a report. */
    @SerialName("ticket")
    TICKET("ticket"),

    /** S-103: a message to the team, with a declared category of sender. */
    @SerialName("contact")
    CONTACT("contact"),

    /** S-104: a claim (réclamation), with a motif. */
    @SerialName("claim")
    CLAIM("claim"),
    ;

    companion object {
        fun fromWire(value: String?): CaseType? = entries.firstOrNull { it.wire == value }
    }
}

/** What a ticket is. A security flaw opens no ticket: the app sends the person to `/securite` (S-92). */
@Serializable
enum class TicketSubtype(val wire: String) {
    @SerialName("bug")
    BUG("bug"),

    @SerialName("suggestion")
    SUGGESTION("suggestion"),

    /** The report of a content or an account: it also goes to the moderation queue. */
    @SerialName("report")
    REPORT("report"),
    ;

    companion object {
        fun fromWire(value: String?): TicketSubtype? = entries.firstOrNull { it.wire == value }
    }
}

/** Who writes, as they declare it (S-103). */
@Serializable
enum class ContactCategory(val wire: String) {
    @SerialName("citizen")
    CITIZEN("citizen"),

    @SerialName("association")
    ASSOCIATION("association"),

    @SerialName("union")
    UNION("union"),

    @SerialName("journalist")
    JOURNALIST("journalist"),

    @SerialName("elected")
    ELECTED("elected"),

    @SerialName("institution")
    INSTITUTION("institution"),

    @SerialName("company")
    COMPANY("company"),

    @SerialName("other")
    OTHER("other"),
    ;

    companion object {
        fun fromWire(value: String?): ContactCategory? = entries.firstOrNull { it.wire == value }
    }
}

/** Why a claim is made (S-104). */
@Serializable
enum class ClaimMotif(
    val wire: String,
    /** Reserved to the administrators of the team: the use of funds and the behaviour of a team member. */
    val restricted: Boolean = false,
    /** Whether the person may link their account to the case (contract, section 4: only when the motif needs it). */
    val accountLinkable: Boolean = false,
) {
    /** Contesting a moderation decision (DSA, article 20, applied by choice). */
    @SerialName("moderation_decision")
    MODERATION_DECISION("moderation_decision", accountLinkable = true),

    /** Exercising one's rights on one's data (RGPD, articles 12 to 22). */
    @SerialName("data_rights")
    DATA_RIGHTS("data_rights", accountLinkable = true),

    /** Asking for a right of reply (LCEN, article 1-1, III). */
    @SerialName("right_of_reply")
    RIGHT_OF_REPLY("right_of_reply", accountLinkable = true),

    @SerialName("funds")
    FUNDS("funds", restricted = true),

    @SerialName("team_member")
    TEAM_MEMBER("team_member", restricted = true),

    @SerialName("other")
    OTHER("other"),
    ;

    companion object {
        fun fromWire(value: String?): ClaimMotif? = entries.firstOrNull { it.wire == value }
    }
}

/** Where a case stands (S-106). */
@Serializable
enum class CaseStatus(val wire: String) {
    @SerialName("received")
    RECEIVED("received"),

    @SerialName("in_progress")
    IN_PROGRESS("in_progress"),

    /** A reply was sent. */
    @SerialName("answered")
    ANSWERED("answered"),

    /** The team waits for the person; closed automatically after 60 days without news. */
    @SerialName("awaiting_you")
    AWAITING_YOU("awaiting_you"),

    @SerialName("closed")
    CLOSED("closed"),
    ;

    companion object {
        fun fromWire(value: String?): CaseStatus? = entries.firstOrNull { it.wire == value }
    }
}

/** A deadline set by the law, or an internal goal of the team (shown as a goal, never as a guarantee). */
@Serializable
enum class DeadlineKind(val wire: String) {
    @SerialName("legal")
    LEGAL("legal"),

    @SerialName("goal")
    GOAL("goal"),
    ;

    companion object {
        fun fromWire(value: String?): DeadlineKind? = entries.firstOrNull { it.wire == value }
    }
}

/** What a case is about: its type and the one qualifier of that type (sub-type, category or motif). */
sealed interface CaseNature {
    val type: CaseType

    /** The wire value of the qualifier. */
    val qualifier: String

    val subtype: TicketSubtype? get() = null
    val category: ContactCategory? get() = null
    val motif: ClaimMotif? get() = null

    data class Ticket(override val subtype: TicketSubtype) : CaseNature {
        override val type: CaseType get() = CaseType.TICKET
        override val qualifier: String get() = subtype.wire
    }

    data class Contact(override val category: ContactCategory) : CaseNature {
        override val type: CaseType get() = CaseType.CONTACT
        override val qualifier: String get() = category.wire
    }

    data class Claim(override val motif: ClaimMotif) : CaseNature {
        override val type: CaseType get() = CaseType.CLAIM
        override val qualifier: String get() = motif.wire
    }

    companion object {
        /**
         * The nature given by a type and exactly the qualifier of that type (a ticket has a sub-type and no
         * category nor motif, and so on); null for anything else.
         */
        fun of(type: CaseType?, subtype: TicketSubtype?, category: ContactCategory?, motif: ClaimMotif?): CaseNature? = when (type) {
            CaseType.TICKET -> if (subtype != null && category == null && motif == null) Ticket(subtype) else null
            CaseType.CONTACT -> if (category != null && subtype == null && motif == null) Contact(category) else null
            CaseType.CLAIM -> if (motif != null && subtype == null && category == null) Claim(motif) else null
            null -> null
        }

        /** Same from wire values; an unknown value gives null. */
        fun fromWire(type: String?, subtype: String?, category: String?, motif: String?): CaseNature? {
            val knownType = CaseType.fromWire(type) ?: return null
            val knownSubtype = subtype?.let { TicketSubtype.fromWire(it) ?: return null }
            val knownCategory = category?.let { ContactCategory.fromWire(it) ?: return null }
            val knownMotif = motif?.let { ClaimMotif.fromWire(it) ?: return null }
            return of(knownType, knownSubtype, knownCategory, knownMotif)
        }
    }
}
