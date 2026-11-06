package org.politisationpopulairefrancaise.core.revendications.checks

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.politisationpopulairefrancaise.core.editorial.RevendicationStatus

/**
 * A problem the automatic checks found in a proposed demand. None of them decides anything on its
 * own: the author sees them while writing, and the team reads them during the review that every
 * demand goes through before publication (decision of the owner, 2026-11-05). The serial names are
 * stable: reports travel between the server and the device and are stored by the server.
 */
@Serializable
enum class IssueKind {
    /** An existing demand seems to say the same thing: better support it than split the votes (DE-18, DE-19). */
    @SerialName("duplicate") DUPLICATE,

    /** Close demands worth reading before sending. */
    @SerialName("similar") SIMILAR,

    /** A close demand is already retained in the Programme du peuple or has become a campaign. */
    @SerialName("already_advanced") ALREADY_ADVANCED,

    /** A law or a rule in force seems to already provide what is asked (a demand may then ask for it to be applied). */
    @SerialName("existing_law") EXISTING_LAW,

    /** A bill under discussion in Parliament covers the same subject. */
    @SerialName("pending_bill") PENDING_BILL,

    /** The demand asks the opposite of an existing one: both may exist, the vote decides. */
    @SerialName("contradiction") CONTRADICTION,

    /** More than one idea: one demand, one idea (DE-18). */
    @SerialName("several_ideas") SEVERAL_IDEAS,

    @SerialName("too_short") TOO_SHORT,
    @SerialName("too_long") TOO_LONG,
    @SerialName("all_caps") ALL_CAPS,
    @SerialName("missing_theme") MISSING_THEME,
    @SerialName("too_many_themes") TOO_MANY_THEMES,
    @SerialName("missing_territory") MISSING_TERRITORY,

    /** An e-mail address, a phone number, a bank or social security number: never published. */
    @SerialName("personal_data") PERSONAL_DATA,

    /** Aims at a named person rather than at a change of rules (charter, risk of defamation). */
    @SerialName("targets_person") TARGETS_PERSON,

    /** Against the content charter (cahier des charges, 9.3): call to violence, hatred, discrimination. */
    @SerialName("charter") CHARTER,

    /** Not a demand of public policy (an advertisement, a private dispute, a question). */
    @SerialName("out_of_scope") OUT_OF_SCOPE,

    /** Hard to understand as it is written. */
    @SerialName("unclear") UNCLEAR,
}

@Serializable
enum class IssueSeverity {
    /** The draft cannot be sent as it is (deterministic rules only: never an AI opinion). */
    @SerialName("blocking") BLOCKING,

    /** Worth correcting; the author may still send. */
    @SerialName("warning") WARNING,

    /** For information. */
    @SerialName("info") INFO,
}

/** Who raised the issue: shown to people, because an automated tool must say so (DSA, article 17). */
@Serializable
enum class IssueSource {
    @SerialName("rules") RULES,
    @SerialName("similarity") SIMILARITY,
    @SerialName("legal") LEGAL,
    @SerialName("ai") AI,
}

/**
 * The two alert levels of the AI moderation (cahier des charges, 5.6): red for content estimated
 * grave and manifestly illicite, orange for borderline content. Demands are always reviewed by a
 * person before publication, so the alert only orders the team's work.
 */
@Serializable
enum class ModerationAlert {
    @SerialName("red") RED,
    @SerialName("orange") ORANGE,
}

/** An existing demand an issue refers to. */
@Serializable
data class RelatedDemand(
    /** The content address `kind:pubkey:identifier`. */
    val address: String,
    val title: String,
    /** Closeness between 0 and 1 (words for [IssueSource.SIMILARITY], the model's estimate for [IssueSource.AI]). */
    val score: Double,
    /** The tag value of its status ("nouvelle", "campagne"...), as written in the event. */
    @SerialName("status") val statusTag: String? = null,
    val territory: String? = null,
) {
    val status: RevendicationStatus? get() = statusTag?.let(RevendicationStatus::fromTag)
}

@Serializable
enum class LegalNature {
    @SerialName("constitution") CONSTITUTION,
    @SerialName("code") CODE,
    @SerialName("loi") LOI,
    @SerialName("ordonnance") ORDONNANCE,
    @SerialName("decret") DECRET,
    @SerialName("arrete") ARRETE,
    @SerialName("union_europeenne") UNION_EUROPEENNE,
    @SerialName("autre") AUTRE,
}

@Serializable
enum class LegalStatus {
    @SerialName("en_vigueur") EN_VIGUEUR,
    @SerialName("a_venir") A_VENIR,
    @SerialName("abroge") ABROGE,
    @SerialName("en_discussion") EN_DISCUSSION,
    @SerialName("inconnu") INCONNU,
}

/**
 * A legal text an issue refers to. [verified] is true only when the reference comes from an
 * official source the server queried (the legal corpus); a reference written by the AI model from
 * memory stays unverified until a person checks it, and is shown as such.
 */
@Serializable
data class LegalReference(
    val title: String,
    val nature: LegalNature = LegalNature.AUTRE,
    val status: LegalStatus = LegalStatus.INCONNU,
    /** Official identifier when known (Légifrance: LEGITEXT…, LEGIARTI…, JORFTEXT…). */
    val identifier: String? = null,
    /** Official page, only on the hosts of [OFFICIAL_HOSTS]. */
    val url: String? = null,
    val excerpt: String? = null,
    val verified: Boolean = false,
) {
    companion object {
        /** The only sites a legal reference may link to: official publications of the State and of Parliament. */
        val OFFICIAL_HOSTS: Set<String> = setOf("www.legifrance.gouv.fr", "www.assemblee-nationale.fr", "www.senat.fr", "eur-lex.europa.eu")

        fun isOfficialUrl(url: String): Boolean {
            if (!url.startsWith("https://")) return false
            val host = url.removePrefix("https://").substringBefore('/').substringBefore('?').substringBefore('#').lowercase()
            return host in OFFICIAL_HOSTS
        }
    }
}

@Serializable
data class ProposalIssue(
    val kind: IssueKind,
    val severity: IssueSeverity,
    val source: IssueSource,
    /** The field to correct, when one is at fault. */
    val field: DraftField? = null,
    val related: List<RelatedDemand> = emptyList(),
    val references: List<LegalReference> = emptyList(),
    /**
     * A short explanation written by the AI model (at most [MAX_DETAIL_LENGTH] characters), shown
     * with its origin. Rules never fill it: they never echo what they found (a phone number, an
     * address) back into a report.
     */
    val detail: String? = null,
    /** Only for [IssueKind.CHARTER], [IssueKind.TARGETS_PERSON] and [IssueKind.PERSONAL_DATA] raised by the AI. */
    val alert: ModerationAlert? = null,
) {
    companion object {
        const val MAX_DETAIL_LENGTH: Int = 400
    }
}

/** The checks a report is made of. */
@Serializable
enum class CheckKind {
    @SerialName("rules") RULES,
    @SerialName("similarity") SIMILARITY,
    @SerialName("legal") LEGAL,
    @SerialName("ai") AI,
}

@Serializable
enum class CheckState {
    @SerialName("done") DONE,

    /** Runs on the server once the demand is sent (the device has neither the model nor the legal corpus). */
    @SerialName("after_sending") AFTER_SENDING,

    /** Not in service yet (no legal corpus, no model configured). */
    @SerialName("unavailable") UNAVAILABLE,

    /** Tried and failed (model unreachable, answer unreadable): the team checks by hand. */
    @SerialName("failed") FAILED,
}

@Serializable
data class CheckStatus(
    val kind: CheckKind,
    val state: CheckState,
    /** For [CheckKind.SIMILARITY]: how many existing demands were compared. */
    val compared: Int? = null,
    /** For [CheckKind.AI]: the model that answered, as the server names it. */
    val model: String? = null,
)

/** Everything the automatic checks have to say about a draft. */
@Serializable
data class ProposalReport(
    val issues: List<ProposalIssue>,
    val checks: List<CheckStatus>,
    /** Seconds since 1970. */
    @SerialName("checked_at") val checkedAt: Long,
) {
    /** False while a blocking issue remains (deterministic rules only). */
    val canSubmit: Boolean get() = issues.none { it.severity == IssueSeverity.BLOCKING }

    fun issues(kind: IssueKind): List<ProposalIssue> = issues.filter { it.kind == kind }

    fun state(kind: CheckKind): CheckState? = checks.firstOrNull { it.kind == kind }?.state
}
