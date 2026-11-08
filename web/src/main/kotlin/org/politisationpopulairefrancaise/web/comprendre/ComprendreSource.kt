package org.politisationpopulairefrancaise.web.comprendre

import org.politisationpopulairefrancaise.core.comprendre.ComprendreLinks
import org.politisationpopulairefrancaise.core.comprendre.Decryptage
import org.politisationpopulairefrancaise.core.comprendre.GlossaryEntry
import org.politisationpopulairefrancaise.core.comprendre.GlossaryIndex
import org.politisationpopulairefrancaise.core.comprendre.HistoryStory
import org.politisationpopulairefrancaise.core.comprendre.Parcours
import org.politisationpopulairefrancaise.core.comprendre.QuiDecide
import org.politisationpopulairefrancaise.core.comprendre.TeachingSheet
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.text.FrenchText
import org.politisationpopulairefrancaise.core.voir.PublicPerson

/**
 * Where the pages of "Comprendre" read their contents: provided by the server, which assembles the
 * catalog from the editorial contents signed by the trusted editorial keys only (D-034), as for "Voir".
 */
fun interface ComprendreSource {
    suspend fun catalog(): ComprendreCatalog
}

/**
 * The contents of "Comprendre" shown by the site (D-091). Every list holds one version per identifier
 * (the newest), in the order of the pages: guides, courses and sheets by title, fact-checks newest first,
 * stories by period, teaching sheets by level then title.
 */
class ComprendreCatalog(
    val fiches: List<Article>,
    val parcours: List<Parcours>,
    val decryptages: List<Decryptage>,
    val stories: List<HistoryStory>,
    val sheets: List<QuiDecide>,
    /** The page « Notre méthode » (`notre-methode`), when published. */
    val method: Article?,
    val glossary: GlossaryIndex,
    /** The public persons of "Voir", by address (`30023:<key>:<identifier>`), to name them in the « Qui décide de ça ? » sheets. */
    val persons: Map<String, PublicPerson>,
    /** The public journal of corrections: the `ppf-correction` tags of the published versions, the most recent first. */
    val corrections: List<PublicCorrection> = emptyList(),
    /** The teaching sheets for the classroom (CO-13, D-096), each linked to one course. */
    val teachingSheets: List<TeachingSheet> = emptyList(),
) {
    private val byPath: Map<String, Any> = buildMap {
        fiches.forEach { a -> ComprendreLinks.pathOf(a)?.let { put(it, a) } }
        parcours.forEach { put(ComprendreLinks.pathOf(it.article)!!, it) }
        decryptages.forEach { put(ComprendreLinks.pathOf(it.article)!!, it) }
        stories.forEach { put(ComprendreLinks.pathOf(it.article)!!, it) }
        sheets.forEach { put(ComprendreLinks.pathOf(it.article)!!, it) }
        teachingSheets.forEach { put(ComprendreLinks.pathOf(it.article)!!, it) }
    }

    /** Every content with a page, by identifier, to follow a link `30023:<key>:<identifier>`. */
    private val bySlug: Map<String, Article> = buildMap {
        (fiches + parcours.map { it.article } + decryptages.map { it.article } + stories.map { it.article } + sheets.map { it.article } + glossary.entries.map { it.article } + teachingSheets.map { it.article })
            .forEach { putIfAbsent(it.slug, it) }
    }

    fun fiche(slug: String): Article? = fiches.firstOrNull { it.slug == slug }
    fun course(slug: String): Parcours? = parcours.firstOrNull { it.article.slug == slug }
    fun decryptage(slug: String): Decryptage? = decryptages.firstOrNull { it.article.slug == slug }
    fun story(slug: String): HistoryStory? = stories.firstOrNull { it.article.slug == slug }
    fun sheet(slug: String): QuiDecide? = sheets.firstOrNull { it.article.slug == slug }
    fun teachingSheet(slug: String): TeachingSheet? = teachingSheets.firstOrNull { it.article.slug == slug }

    /** The course a teaching sheet brings to the classroom, by its full address; null when it is not published. */
    fun courseOf(sheet: TeachingSheet): Parcours? = parcours.firstOrNull { it.address == sheet.courseAddress }

    /** The teaching sheets of [course], by level. */
    fun teachingSheetsFor(course: Parcours): List<TeachingSheet> = teachingSheets.filter { it.courseAddress == course.address }

    /** The glossary entry of an address segment: `49-3` or, for an old link, `glossaire-49-3`. */
    fun glossaryEntry(identifier: String): GlossaryEntry? =
        glossary.entry(ComprendreLinks.GLOSSARY_PREFIX + identifier) ?: glossary.entry(identifier)

    /** The content an address (`30023:<key>:<identifier>`) or a bare identifier names, when it has a page. */
    fun linked(address: String): Article? = bySlug[address.substringAfterLast(':')]

    val isEmpty: Boolean get() = byPath.isEmpty() && glossary.isEmpty && method == null

    companion object {
        /** The types read by the site for "Comprendre"; the persons of "Voir" are named in the sheets. */
        val TYPES: List<ArticleType> = listOf(
            ArticleType.FICHE_PRATIQUE,
            ArticleType.PARCOURS,
            ArticleType.DECRYPTAGE,
            ArticleType.HISTOIRE,
            ArticleType.QUI_DECIDE,
            ArticleType.GLOSSAIRE,
            ArticleType.ARTICLE,
            ArticleType.PERSONNE,
            ArticleType.FICHE_PEDAGOGIQUE,
        )

        /** The identifier of the page « Notre méthode » (same as the application). */
        const val METHOD_SLUG: String = "notre-methode"

        val EMPTY: ComprendreCatalog = from(emptyList())

        /**
         * The catalog of [events], keeping only the authentic events of a trusted key ([isEditor]):
         * a content of anyone else is never shown, whatever its tags say.
         */
        fun fromEvents(events: List<SignedEvent>, isEditor: (String) -> Boolean): ComprendreCatalog =
            from(events.filter { isEditor(it.pubkey) && it.verify() }.mapNotNull(Article::from))

        /** The catalog of [articles], already read from trusted events. */
        fun from(articles: List<Article>): ComprendreCatalog {
            fun newest(type: ArticleType): List<Article> = articles.filter { it.type == type }
                .groupBy { it.slug }
                .map { (_, versions) -> versions.maxBy { it.updatedAt } }
            fun byTitle(a: Article) = FrenchText.fold(a.title.removePrefix("[EXEMPLE]").trim())
            return ComprendreCatalog(
                fiches = newest(ArticleType.FICHE_PRATIQUE).sortedBy(::byTitle),
                parcours = newest(ArticleType.PARCOURS).mapNotNull(Parcours::from).sortedBy { byTitle(it.article) },
                decryptages = newest(ArticleType.DECRYPTAGE).mapNotNull(Decryptage::from)
                    .sortedWith(compareByDescending<Decryptage> { it.article.publishedAt ?: it.article.updatedAt }.thenBy { byTitle(it.article) }),
                stories = newest(ArticleType.HISTOIRE).mapNotNull(HistoryStory::from).sortedWith(compareBy<HistoryStory>({ it.start }, { byTitle(it.article) })),
                sheets = newest(ArticleType.QUI_DECIDE).mapNotNull(QuiDecide::from).sortedBy { byTitle(it.article) },
                method = newest(ArticleType.ARTICLE).firstOrNull { it.slug == METHOD_SLUG },
                glossary = GlossaryIndex(newest(ArticleType.GLOSSAIRE).mapNotNull(GlossaryEntry::from)),
                persons = newest(ArticleType.PERSONNE).mapNotNull(PublicPerson::from).associateBy { it.address.toString() },
                corrections = PublicCorrection.of(articles),
                teachingSheets = newest(ArticleType.FICHE_PEDAGOGIQUE).mapNotNull(TeachingSheet::from)
                    .sortedWith(compareBy<TeachingSheet>({ it.level.ordinal }, { byTitle(it.article) })),
            )
        }
    }
}
