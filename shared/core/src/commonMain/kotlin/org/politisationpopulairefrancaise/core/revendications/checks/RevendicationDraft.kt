package org.politisationpopulairefrancaise.core.revendications.checks

import kotlinx.serialization.Serializable
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.editorial.RevendicationStatus
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.text.FrenchText
import kotlin.random.Random

/** The parts of a draft, so that a check can point at the field to correct. */
@Serializable
enum class DraftField { TITLE, SUMMARY, WHY, ASKS, EXISTING, THEMES, TERRITORY }

/**
 * A demand being written by a citizen (DE-18, guided form: "une revendication = une idée,
 * formulée simplement"). It becomes a demand event (NIP-23, `ppf-type` = revendication) only when
 * the author sends it; the server then holds it for review before publication (decision of the
 * owner, 2026-11-05).
 */
@Serializable
data class RevendicationDraft(
    /** The demand itself, in one or two sentences: it is the title of the card. */
    val title: String,
    /** Optional one-line summary under the title. */
    val summary: String = "",
    /** Why this should change. */
    val why: String = "",
    /** What exactly is asked. */
    val asks: String = "",
    /** What already exists (laws, schemes) as the author knows it; optional. */
    val existing: String = "",
    /** One to [MAX_THEMES] theme ids of [org.politisationpopulairefrancaise.core.revendications.RevendicationThemes]. */
    val themes: List<String> = emptyList(),
    /** The territory concerned: "France" for a national demand, or a town, a department, a region. */
    val territory: String = DEFAULT_TERRITORY,
) {
    /** The Markdown argument of the card, in the order of the bundled examples. */
    fun body(): String = buildString {
        section("Pourquoi", why)
        section("Ce que la revendication demande", asks)
        section("Ce qui existe déjà", existing)
    }.trim()

    /** Every text of the draft, for the checks that read them all. */
    fun texts(): Map<DraftField, String> = mapOf(
        DraftField.TITLE to title,
        DraftField.SUMMARY to summary,
        DraftField.WHY to why,
        DraftField.ASKS to asks,
        DraftField.EXISTING to existing,
        DraftField.TERRITORY to territory,
    )

    /**
     * The event to sign. The status is always "nouvelle": only the team moves a demand forward.
     * [slug] defaults to the folded first words of the title plus a random suffix, so that two
     * demands of the same author never share an address.
     */
    fun toUnsignedEvent(author: PublicKey, now: Long, slug: String = slugFor(title)): UnsignedEvent = Article.build(
        author = author,
        slug = slug,
        title = title.trim(),
        summary = summary.trim().ifEmpty { null },
        topics = themes,
        type = ArticleType.REVENDICATION,
        origin = ContentOrigin.HUMAN,
        publishedAt = now,
        updatedAt = now,
        body = body(),
        status = RevendicationStatus.NOUVELLE,
        territory = territory.trim().ifEmpty { DEFAULT_TERRITORY },
    )

    private fun StringBuilder.section(heading: String, text: String) {
        val content = text.trim()
        if (content.isEmpty()) return
        append("## ").append(heading).append("\n\n").append(content).append("\n\n")
    }

    companion object {
        const val DEFAULT_TERRITORY: String = "France"
        const val MAX_THEMES: Int = 3

        /** "Un médecin de garde le soir !" gives something like "medecin-garde-soir-3f9a". */
        fun slugFor(title: String, random: Random = Random.Default): String {
            val words = FrenchText.words(title).filter { it !in FrenchText.STOPWORDS }.take(6)
            val base = words.joinToString("-").take(60).trimEnd('-').ifEmpty { "revendication" }
            val suffix = random.nextInt(0x10000).toString(16).padStart(4, '0')
            return "$base-$suffix"
        }
    }
}
