package org.politisationpopulairefrancaise.server.revendications

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.RevendicationStatus
import org.politisationpopulairefrancaise.core.revendications.RevendicationThemes
import org.politisationpopulairefrancaise.core.revendications.checks.RevendicationDraft
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.text.FrenchText

/**
 * Reads a proposed demand back into the draft its author wrote, so that the server runs the same
 * rules as the guided form ([org.politisationpopulairefrancaise.core.revendications.checks.DraftRules]),
 * and checks that the event has the shape the form produces ([RevendicationDraft.toUnsignedEvent]).
 * A modified application can sign anything: what the team approves must still be a demand of the
 * wall and nothing more.
 */
object DemandDrafts {
    /** The tags the form writes; any other tag is refused. */
    val ALLOWED_TAGS: Set<String> = setOf(
        "d", Article.TAG_TITLE, Article.TAG_SUMMARY, Article.TAG_PUBLISHED_AT, Article.TAG_TYPE,
        Article.TAG_ORIGIN, Article.TAG_STATUS, Article.TAG_TERRITORY, Article.TAG_TOPIC,
    )

    /** Tags that must appear at most once, so that every reader sees the same value. */
    private val SINGLE_TAGS: Set<String> = ALLOWED_TAGS - Article.TAG_TOPIC

    /** The identifier written by the form is at most 65 characters ([RevendicationDraft.slugFor]). */
    const val MAX_IDENTIFIER_LENGTH: Int = 128

    /** A device whose clock is a little ahead is fine; a demand dated from the future is not. */
    const val MAX_CLOCK_AHEAD_SECONDS: Long = 3_600

    sealed interface Shape {
        data class Valid(val article: Article) : Shape

        data class Invalid(val error: String, val message: String) : Shape
    }

    /** Whether [event] (a verified kind 30023 event with `ppf-type` = revendication) is a demand as the form writes it. */
    fun shapeOf(event: SignedEvent, nowSeconds: Long): Shape {
        val names = event.tags.map { it.firstOrNull().orEmpty() }
        if (event.tags.any { it.size < 2 }) return malformed("Every tag of a demand has a name and a value.")
        if (names.any { it !in ALLOWED_TAGS }) return malformed("A demand carries only the tags of the form: ${ALLOWED_TAGS.joinToString()}.")
        SINGLE_TAGS.firstOrNull { name -> names.count { it == name } > 1 }?.let { return malformed("A demand carries at most one \"$it\" tag.") }
        if (names.count { it == "d" } != 1) return malformed("A demand carries exactly one d tag.")
        val identifier = event.firstTagValue("d").orEmpty()
        if (identifier.isEmpty() || identifier.length > MAX_IDENTIFIER_LENGTH || identifier.any { it.isWhitespace() || it.isISOControl() }) {
            return malformed("The d tag of a demand is 1 to $MAX_IDENTIFIER_LENGTH characters, without spaces.")
        }
        val themes = event.tagValues(Article.TAG_TOPIC)
        if (themes.any { RevendicationThemes.byId(it) == null }) return malformed("The themes of a demand are theme ids of the wall.")
        if (themes.size > RevendicationDraft.MAX_THEMES || themes.distinct().size != themes.size) {
            return malformed("A demand carries 1 to ${RevendicationDraft.MAX_THEMES} different themes.")
        }
        if (event.createdAt > nowSeconds + MAX_CLOCK_AHEAD_SECONDS) return malformed("The demand is dated in the future.")
        val article = Article.from(event)?.takeIf { it.type == ArticleType.REVENDICATION }
            ?: return malformed("The event is not a well-formed demand (title and origin are required).")
        if (event.firstTagValue(Article.TAG_STATUS) != RevendicationStatus.NOUVELLE.tagValue) {
            return Shape.Invalid(
                "status_not_allowed",
                "A proposed demand has the status \"${RevendicationStatus.NOUVELLE.tagValue}\": only the team moves a demand forward.",
            )
        }
        return Shape.Valid(article)
    }

    private fun malformed(message: String) = Shape.Invalid("malformed_demand", message)

    /**
     * The draft behind [article]: the title, summary, themes and territory from the tags, and the
     * three parts of the argument from the Markdown body, read back from the headings that
     * [RevendicationDraft.body] writes. Text under no known heading counts as "why", so that the
     * length and personal data rules see every word of the body.
     */
    fun draftOf(article: Article): RevendicationDraft {
        val sections = sectionsOf(article.body)
        return RevendicationDraft(
            title = article.title,
            summary = article.summary.orEmpty(),
            why = sections[Section.WHY].orEmpty(),
            asks = sections[Section.ASKS].orEmpty(),
            existing = sections[Section.EXISTING].orEmpty(),
            themes = article.topics,
            territory = article.territory.orEmpty(),
        )
    }

    private enum class Section(val heading: String) {
        WHY("Pourquoi"),
        ASKS("Ce que la revendication demande"),
        EXISTING("Ce qui existe déjà"),
    }

    private val HEADING = Regex("""^##\s+(.+?)\s*#*\s*$""")

    private fun sectionsOf(body: String): Map<Section, String> {
        val parts = mutableMapOf<Section, StringBuilder>()
        var current = Section.WHY
        for (line in body.lines()) {
            val heading = HEADING.matchEntire(line)?.groupValues?.get(1)
            val known = heading?.let { title -> Section.entries.firstOrNull { FrenchText.fold(it.heading) == FrenchText.fold(title) } }
            if (known != null) {
                current = known
                continue
            }
            // An unknown heading and its text belong to "why": nothing escapes the rules.
            if (heading != null) current = Section.WHY
            parts.getOrPut(current) { StringBuilder() }.append(line).append('\n')
        }
        return parts.mapValues { (_, text) -> text.toString().trim() }.filterValues { it.isNotEmpty() }
    }
}
