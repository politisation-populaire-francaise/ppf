package org.politisationpopulairefrancaise.core.voir

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.VoirTags.archives
import org.politisationpopulairefrancaise.core.voir.VoirTags.at
import org.politisationpopulairefrancaise.core.voir.VoirTags.corrections
import org.politisationpopulairefrancaise.core.voir.VoirTags.fields
import org.politisationpopulairefrancaise.core.voir.VoirTags.links
import org.politisationpopulairefrancaise.core.voir.VoirTags.nextSteps
import org.politisationpopulairefrancaise.core.voir.VoirTags.sources
import org.politisationpopulairefrancaise.core.voir.VoirTags.value

/**
 * A sourced fact (D-034, "fait sourcé"): one statement with its date, its place, its sources,
 * its status (established, alleged, under investigation) and the people and texts it concerns.
 * The same fact appears everywhere it belongs (the chronology, a person's sheet, a bill, the
 * observatory of freedoms) without being copied. Kind-specific fields are null when they do not
 * apply.
 */
data class Fact(
    val article: Article,
    val kind: FactKind,
    val date: CivilDate,
    val place: String?,
    val status: FactStatus,
    val sources: List<Source>,
    val archives: List<ArchivedCopy>,
    val persons: List<ContentAddress>,
    val texts: List<ContentAddress>,
    val corrections: List<Correction>,
    val nextSteps: List<NextStep>,
    /** Cases: where the procedure stands (always shown). */
    val procedure: ProcedureStage?,
    /** Cases: the sentence pronounced, and the one actually served (VO-14). */
    val sentence: String?,
    val sentenceServed: String?,
    /** Promises: what became of it. */
    val promise: PromiseStatus?,
    /** Votes, and statements set against a vote: how the person voted. */
    val vote: VotePosition?,
    /** The exact words, for a statement set against a vote or contradicted by the facts. */
    val quote: String?,
    /** Observatory of freedoms. */
    val liberty: LibertyCategory?,
    val recourse: RecourseStatus?,
) {
    val address: ContentAddress get() = article.address
    val title: String get() = article.title
    val axis: VoirAxis get() = kind.axis

    companion object {
        fun from(article: Article): Fact? {
            if (article.type != ArticleType.FAIT) return null
            val event = article.event
            val kind = FactKind.fromTag(event.value(VoirTags.FACT)) ?: return null
            val date = CivilDate.parseIso(event.value(VoirTags.DATE)) ?: return null
            // A fact without its status cannot be shown: the reader must always know what is established.
            val status = FactStatus.fromTag(event.value(VoirTags.FACT_STATUS)) ?: return null
            val procedure = ProcedureStage.fromTag(event.value(VoirTags.PROCEDURE))
            // A case is never shown without the stage of its procedure (presumption of innocence).
            if (kind == FactKind.CASE && procedure == null) return null
            return Fact(
                article = article,
                kind = kind,
                date = date,
                place = event.value(VoirTags.PLACE),
                status = status,
                sources = event.sources(),
                archives = event.archives(),
                persons = event.links(VoirTags.ROLE_PERSON),
                texts = event.links(VoirTags.ROLE_TEXT),
                corrections = event.corrections(),
                nextSteps = event.nextSteps(),
                procedure = procedure,
                sentence = event.value(VoirTags.SENTENCE),
                sentenceServed = event.value(VoirTags.SENTENCE_SERVED),
                promise = PromiseStatus.fromTag(event.value(VoirTags.PROMISE)),
                vote = VotePosition.fromTag(event.value(VoirTags.VOTE)),
                quote = event.value(VoirTags.QUOTE),
                liberty = LibertyCategory.fromTag(event.value(VoirTags.LIBERTY)) ?: LibertyCategory.OTHER.takeIf { kind == FactKind.LIBERTY },
                recourse = RecourseStatus.fromTag(event.value(VoirTags.RECOURSE)),
            )
        }
    }
}

/** A mandate or a past function of a public person. */
data class Mandate(val title: String, val territory: String?, val start: CivilDate?, val end: CivilDate?) {
    val current: Boolean get() = end == null
}

/**
 * A public person (VO-10): name, current function, mandates, group. Every sheet shows the same
 * sections in the same order, whoever the person is (règle « Traitement identique »).
 */
data class PublicPerson(
    val article: Article,
    val roles: List<PublicRole>,
    val group: String?,
    val territory: String?,
    val mandates: List<Mandate>,
    val sources: List<Source>,
    val corrections: List<Correction>,
) {
    val address: ContentAddress get() = article.address
    val name: String get() = article.title
    val function: String? get() = article.summary

    companion object {
        fun from(article: Article): PublicPerson? {
            if (article.type != ArticleType.PERSONNE) return null
            val event = article.event
            return PublicPerson(
                article = article,
                roles = event.fields(VoirTags.ROLE).mapNotNull { PublicRole.fromTag(it.at(0)) }.distinct().ifEmpty { listOf(PublicRole.OTHER) },
                group = event.value(VoirTags.GROUP),
                territory = event.value(Article.TAG_TERRITORY),
                mandates = event.fields(VoirTags.MANDATE).mapNotNull { f ->
                    val title = f.at(0) ?: return@mapNotNull null
                    Mandate(title, f.at(1), CivilDate.parseIso(f.at(2)), CivilDate.parseIso(f.at(3)))
                }.sortedWith(compareBy<Mandate> { !it.current }.thenByDescending { it.start }),
                sources = event.sources(),
                corrections = event.corrections(),
            )
        }
    }
}

/** A dated stage reached by a bill. */
data class StageReached(val stage: TextStage, val date: CivilDate)

/** The next vote planned on a bill. */
data class PlannedVote(val date: CivilDate, val label: String)

/** Who gains and who loses with a law (VO-19): a group, the direction, the sourced effect. */
data class Impact(val group: String, val direction: ImpactDirection, val description: String, val source: Source?)

/**
 * A bill (législomètre, VO-20): where it stands, its next vote, the uses of article 49.3, its
 * effects on the population. The votes and the meetings with lobbies are facts pointing to it.
 */
data class LegislativeText(
    val article: Article,
    /** `projet` (from the government) or `proposition` (from members of Parliament). */
    val textKind: String?,
    val stage: TextStage,
    val history: List<StageReached>,
    val nextVote: PlannedVote?,
    val uses493: List<CivilDate>,
    val impacts: List<Impact>,
    val sources: List<Source>,
    val corrections: List<Correction>,
    val nextSteps: List<NextStep>,
) {
    val address: ContentAddress get() = article.address
    val title: String get() = article.title

    companion object {
        fun from(article: Article): LegislativeText? {
            if (article.type != ArticleType.TEXTE) return null
            val event = article.event
            val stage = TextStage.fromTag(event.value(VoirTags.STAGE)) ?: return null
            return LegislativeText(
                article = article,
                textKind = event.value(VoirTags.TEXT_KIND),
                stage = stage,
                history = event.fields(VoirTags.STAGE_HISTORY).mapNotNull { f ->
                    val reached = TextStage.fromTag(f.at(0)) ?: return@mapNotNull null
                    val date = CivilDate.parseIso(f.at(1)) ?: return@mapNotNull null
                    StageReached(reached, date)
                }.sortedBy { it.date },
                nextVote = event.fields(VoirTags.NEXT_VOTE).firstNotNullOfOrNull { f ->
                    val date = CivilDate.parseIso(f.at(0)) ?: return@firstNotNullOfOrNull null
                    PlannedVote(date, f.at(1) ?: "")
                },
                uses493 = event.fields(VoirTags.ARTICLE_49_3).mapNotNull { CivilDate.parseIso(it.at(0)) }.sorted(),
                impacts = event.fields(VoirTags.IMPACT).mapNotNull { f ->
                    val group = f.at(0) ?: return@mapNotNull null
                    val direction = ImpactDirection.fromTag(f.at(1)) ?: return@mapNotNull null
                    val description = f.at(2) ?: return@mapNotNull null
                    val source = f.at(3)?.let { name -> Source(SourceKind.REPORT, name, f.at(4)?.takeIf(VoirTags::isWebAddress), null) }
                    Impact(group, direction, description, source)
                },
                sources = event.sources(),
                corrections = event.corrections(),
                nextSteps = event.nextSteps(),
            )
        }
    }
}

/** A social movement elsewhere in the world (M9): country, period, demands, results. */
data class WorldMovement(
    val article: Article,
    val country: String,
    val start: CivilDate?,
    val end: CivilDate?,
    val claims: List<String>,
    val results: List<String>,
    val sources: List<Source>,
) {
    val address: ContentAddress get() = article.address
    val title: String get() = article.title

    companion object {
        fun from(article: Article): WorldMovement? {
            if (article.type != ArticleType.MOUVEMENT) return null
            val event = article.event
            val country = event.value(VoirTags.COUNTRY) ?: return null
            val period = event.fields(VoirTags.PERIOD).firstOrNull()
            return WorldMovement(
                article = article,
                country = country,
                start = CivilDate.parseIso(period?.at(0)),
                end = CivilDate.parseIso(period?.at(1)),
                claims = event.fields(VoirTags.CLAIM).mapNotNull { it.at(0) },
                results = event.fields(VoirTags.RESULT).mapNotNull { it.at(0) },
                sources = event.sources(),
            )
        }
    }
}

/**
 * A right of reply (VO-21; LCEN, article 6, IV): published under the content it answers, with the
 * mention "Droit de réponse de [nom]".
 */
data class RightOfReply(val article: Article, val respondent: String, val target: ContentAddress) {
    val text: String get() = article.body
    val publishedOn: CivilDate get() = CivilDate.fromEpochSeconds(article.publishedAt ?: article.updatedAt)

    companion object {
        fun from(article: Article): RightOfReply? {
            if (article.type != ArticleType.REPONSE) return null
            val respondent = article.event.value(VoirTags.RESPONDENT) ?: return null
            val target = article.event.links(VoirTags.ROLE_TARGET).firstOrNull() ?: return null
            return RightOfReply(article, respondent, target)
        }
    }
}
