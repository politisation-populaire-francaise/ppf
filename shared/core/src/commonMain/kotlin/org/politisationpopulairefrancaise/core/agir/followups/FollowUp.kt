package org.politisationpopulairefrancaise.core.agir.followups

import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.Correction
import org.politisationpopulairefrancaise.core.voir.FactStatus
import org.politisationpopulairefrancaise.core.voir.Source
import org.politisationpopulairefrancaise.core.voir.VoirTags
import org.politisationpopulairefrancaise.core.voir.VoirTags.corrections
import org.politisationpopulairefrancaise.core.voir.VoirTags.links
import org.politisationpopulairefrancaise.core.voir.VoirTags.sources
import org.politisationpopulairefrancaise.core.voir.VoirTags.value

/*
 * AG-62 « Et après ? » (cahier fonctionnel, line 367): the sourced follow-ups of a past mobilization, such as
 * the answers of elected officials, announcements, retreats and bills filed, linked to the demands, the calls
 * and the chronology of « Voir » (VO-06, M4). A follow-up is an editorial content (NIP-23, kind 30023,
 * `ppf-type` = `suite`) signed by an editorial key, described for the editorial engine by [FollowUpSchema];
 * format in docs/FORMATS.md (section 4 septdecies) and docs/integration/agir-et-apres.md. It reuses the
 * conventions of the sourced facts of « Voir »: `ppf-date`, `ppf-fact-status` (established, alleged, under
 * investigation, [FactStatus]), `ppf-source` (kind, name, address, date) and `ppf-correction`.
 */

/** What happened after the mobilization (closed list, `ppf-suite-kind`). The tag values are never renamed. */
enum class FollowUpKind(val tagValue: String) {
    /** An answer of an elected official (a letter, a statement, a written answer). */
    ELECTED_REPLY("reponse-elu"),

    /** An announcement (of the authorities, of an organisation, of the organisers). */
    ANNOUNCEMENT("annonce"),

    /** A retreat: a project withdrawn, suspended or changed. */
    RETREAT("recul"),

    /** A bill, an amendment or a motion filed. */
    BILL_FILED("texte-depose"),

    /** A court decision (a ban suspended, a ruling). */
    COURT_DECISION("decision-justice"),

    OTHER("autre"),
    ;

    companion object {
        fun fromTag(value: String?): FollowUpKind? = entries.firstOrNull { it.tagValue == value }
    }
}

/** What a follow-up links to besides the mobilizations it follows; the role of its `a` tag. */
enum class FollowUpLinkKind(val role: String) {
    /** A public person of « Voir » named by the follow-up: the right of reply of « Voir » applies (M17, VO-21). */
    PERSON(VoirTags.ROLE_PERSON),

    /** A demand of the wall (M11). */
    DEMAND("revendication"),

    /** A call of « Agir » (AP-01 to AP-10). */
    CALL("appel"),

    /** An item of the chronology of the movement in « Voir » (VO-06): a fact of a kind of the chronology. */
    CHRONOLOGY("chronologie"),
    ;

    companion object {
        fun fromRole(value: String?): FollowUpLinkKind? = entries.firstOrNull { it.role == value }
    }
}

/** One link of a follow-up: what it is, and the full address of the content (any author). */
data class FollowUpLink(val kind: FollowUpLinkKind, val address: ContentAddress)

/** The tags of a follow-up beyond the common ones of an editorial content (docs/FORMATS.md, section 4 septdecies). */
object FollowUpFormat {
    /** The `ppf-type` of a follow-up. */
    val TYPE: String = ArticleType.SUITE.tagValue

    const val TAG_KIND: String = "ppf-suite-kind"

    /** The same tags as a fact of « Voir »: its date, its status, its sources, its public corrections. */
    const val TAG_DATE: String = VoirTags.DATE
    const val TAG_STATUS: String = VoirTags.FACT_STATUS
    const val TAG_SOURCE: String = VoirTags.SOURCE
    const val TAG_CORRECTION: String = VoirTags.CORRECTION

    /** A link: `["a", "<kind>:<pubkey>:<d>", "", <role>]`. */
    const val TAG_LINK: String = VoirTags.LINK

    /** The role of the link to a mobilization of the agenda (`31923:<pubkey>:<d>`, required, one or more). */
    const val ROLE_MOBILIZATION: String = "mobilisation"

    /** A follow-up follows at most this many mobilizations, and has at most this many links of each kind. */
    const val MAX_LINKS: Int = 20
}

/**
 * A follow-up as the application shows it. Null from the readers when a required tag is missing or unknown: the
 * kind, the date, the status (the reader must always know what is established), at least one mobilization of
 * the agenda, at least one source with its address.
 */
data class FollowUp(
    val article: Article,
    val kind: FollowUpKind,
    val date: CivilDate,
    val status: FactStatus,
    /** The mobilizations it follows (kind 31923), in the order of the tags. */
    val mobilizations: List<ContentAddress>,
    /** Its primary sources, each with its address. */
    val sources: List<Source>,
    /** Persons, demands, calls and chronology items, in the order of [FollowUpLinkKind] then of the tags. */
    val links: List<FollowUpLink>,
    val corrections: List<Correction>,
) {
    val address: ContentAddress get() = article.address
    val title: String get() = article.title
    val summary: String? get() = article.summary
    val origin: ContentOrigin get() = article.origin

    /** The longer text (plain text), trimmed; may be empty. */
    val text: String get() = article.body.trim()

    /** Marked [EXEMPLE]: a demonstration content (the follow-up of an example mobilization). */
    val example: Boolean get() = title.trimStart().startsWith(Mobilization.EXAMPLE_MARKER)

    /** The title without the [EXEMPLE] mark, which the screens show as a badge. */
    val displayTitle: String get() = title.trimStart().removePrefix(Mobilization.EXAMPLE_MARKER).trim()

    fun linksOf(kind: FollowUpLinkKind): List<ContentAddress> = links.filter { it.kind == kind }.map { it.address }

    /** The public persons it names: the right of reply of « Voir » applies to them. */
    val persons: List<ContentAddress> get() = linksOf(FollowUpLinkKind.PERSON)

    fun follows(mobilization: ContentAddress): Boolean = mobilization in mobilizations

    companion object {
        const val KIND: Int = Article.KIND

        /** Reads a follow-up from an editorial content; null when it is not a well-formed follow-up. */
        fun from(article: Article): FollowUp? {
            if (article.type != ArticleType.SUITE) return null
            val event = article.event
            val kind = FollowUpKind.fromTag(event.value(FollowUpFormat.TAG_KIND)) ?: return null
            val date = CivilDate.parseIso(event.value(FollowUpFormat.TAG_DATE)) ?: return null
            val status = FactStatus.fromTag(event.value(FollowUpFormat.TAG_STATUS)) ?: return null
            val mobilizations = event.links(FollowUpFormat.ROLE_MOBILIZATION).filter { it.kind == Mobilization.KIND }
            if (mobilizations.isEmpty()) return null
            // Primary sources only, each with the address of the document: a follow-up without one is not shown.
            val sources = event.sources().filter { it.url != null }
            if (sources.isEmpty()) return null
            val linked = FollowUpLinkKind.entries.flatMap { linkKind ->
                event.links(linkKind.role).filter { it.kind == Article.KIND }.map { FollowUpLink(linkKind, it) }
            }
            return FollowUp(
                article = article,
                kind = kind,
                date = date,
                status = status,
                mobilizations = mobilizations,
                sources = sources,
                links = linked,
                corrections = event.corrections(),
            )
        }

        /** Reads a follow-up without any check of its author or signature: never show the result as such, use [from]. */
        fun parse(event: SignedEvent): FollowUp? {
            if (event.kind != KIND) return null
            val article = Article.from(event) ?: return null
            return from(article)
        }

        /** A follow-up signed by a trusted editorial key ([checkSignature]: and authentic), well-formed; null otherwise. */
        fun from(event: SignedEvent, trust: EditorialTrust, checkSignature: Boolean = true): FollowUp? {
            if (event.kind != KIND) return null
            if (!trust.isTrusted(event.pubkey)) return null
            if (checkSignature && !event.verify()) return null
            return parse(event)
        }
    }
}

/** The follow-ups of one mobilization, as the section « Et après ? » lists them. */
object FollowUps {
    /**
     * The newest version of each address (the most recent wins, the smaller id at the same second, as everywhere),
     * by date of the follow-up, then by date of the version, then by address: what happened first comes first.
     */
    fun sorted(followUps: List<FollowUp>): List<FollowUp> = followUps
        .groupBy { it.address.toString() }
        .values
        .map { versions -> versions.minWith(compareByDescending<FollowUp> { it.article.updatedAt }.thenBy { it.article.event.id }) }
        .sortedWith(compareBy<FollowUp> { it.date }.thenBy { it.article.updatedAt }.thenBy { it.address.toString() })

    /** The follow-ups of [mobilization] among [followUps], [sorted]. */
    fun of(followUps: List<FollowUp>, mobilization: ContentAddress): List<FollowUp> = sorted(followUps.filter { it.follows(mobilization) })
}
