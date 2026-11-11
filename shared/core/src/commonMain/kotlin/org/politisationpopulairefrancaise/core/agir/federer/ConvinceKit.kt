package org.politisationpopulairefrancaise.core.agir.federer

import org.politisationpopulairefrancaise.core.comprendre.ComprendreLinks
import org.politisationpopulairefrancaise.core.comprendre.ContentBlocks
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.voir.VoirLinks

/**
 * One card of the kit « Convaincre autour de soi » that a person can show or share (rubric « Fédérer », D-195):
 * a short argument, the answer to an objection, or « Le monde bouge » in one card.
 *
 * [id] is stable for a given kit (kind and position), for the test tags and the screen reader.
 */
sealed interface KitCard {
    val id: String
    val title: String
    val text: String

    /** A short argument: its bold lead (« Vous n'êtes pas seul. ») and the sourced sentence that follows. */
    data class Argument(override val id: String, override val title: String, override val text: String) : KitCard

    /** An objection heard (« Manifester, ça ne sert à rien. ») and its sourced answer. */
    data class Answer(override val id: String, override val title: String, override val text: String) : KitCard

    /** « Le monde bouge », in one card: one line per country or movement. */
    data class World(override val id: String, override val title: String, val lines: List<String>) : KitCard {
        override val text: String get() = lines.joinToString("\n") { "- $it" }
    }
}

/** What the kit holds once read: nothing is invented, an absent part stays empty. */
data class ConvinceKit(
    val arguments: List<KitCard.Argument>,
    val answers: List<KitCard.Answer>,
    val world: KitCard.World?,
) {
    val cards: List<KitCard> get() = arguments + answers + listOfNotNull(world)
    val isEmpty: Boolean get() = cards.isEmpty()
}

/**
 * Reads the practical guide `convaincre-autour-de-soi`, written by « Comprendre » (content/fiches), into cards.
 *
 * The guide stays the one reference (its sources, its « Rédigé par l'IA »): the cards only cut it into pieces
 * easy to show on a phone or to share. Recognised, and nothing else:
 * - the numbered lines of the section whose title speaks of « arguments » (`1. **Lead.** text`);
 * - the `::: objections` block, read by [ContentBlocks.parseObjections];
 * - the bullet lines of the section « Le monde bouge » (`- **Kenya, 2024** : text`).
 */
object ConvinceKitReader {
    /** The stable identifier of the guide (content/fiches/convaincre-autour-de-soi.md). */
    const val SLUG: String = "convaincre-autour-de-soi"

    fun read(markdown: String): ConvinceKit {
        val lines = markdown.lines()
        return ConvinceKit(
            arguments = section(lines) { it.contains("argument", ignoreCase = true) }
                .mapNotNull { NUMBERED.matchEntire(it.trim())?.groupValues?.get(1) }
                .mapIndexed { index, item -> lead(item).let { (title, text) -> KitCard.Argument("argument-${index + 1}", title, text) } },
            answers = ContentBlocks.parseObjections(block(lines, ContentBlocks.OBJECTIONS)).first
                .mapIndexed { index, it -> KitCard.Answer("objection-${index + 1}", unquote(it.objection), it.answer) },
            world = section(lines) { it.contains("monde bouge", ignoreCase = true) }
                .mapNotNull { BULLET.matchEntire(it.trim())?.groupValues?.get(1) }
                .map { item -> lead(item).let { (title, text) -> if (text.isEmpty()) title else "$title : $text" } }
                .takeIf { it.isNotEmpty() }
                ?.let { KitCard.World("monde", WORLD_TITLE, it) },
        )
    }

    /**
     * The text given to the share sheet or the clipboard for [card]: the card, then where it comes from and the
     * page of the whole guide, whose sources travel with it ([base] is the site's address).
     */
    fun shareText(card: KitCard, base: String = VoirLinks.SITE): String {
        val page = ComprendreLinks.urlOf(ComprendreLinks.pathOf(ArticleType.FICHE_PRATIQUE, SLUG)!!, base)
        val head = when (card) {
            is KitCard.Answer -> "« ${card.title} »\n${card.text}"
            else -> "${card.title}\n${card.text}"
        }
        return "$head\n\n$SHARE_FOOTER\n$page"
    }

    /** What every shared card says of its origin (D-212): written by an AI, sources in the guide. */
    const val SHARE_FOOTER: String = "Extrait du kit « Convaincre autour de soi » de Politisation Populaire Française, rédigé par l'IA, sources dans la fiche :"

    private const val WORLD_TITLE = "Le monde bouge"
    private val NUMBERED = Regex("""^\d+[.)]\s+(.+)$""")
    private val BULLET = Regex("""^[-*]\s+(.+)$""")
    private val BOLD_LEAD = Regex("""^\*\*(.+?)\*\*\s*[:.]?\s*(.*)$""")

    /** Splits `**Lead.** rest` into its lead and the rest; a line without a bold lead is all text. */
    private fun lead(item: String): Pair<String, String> {
        val match = BOLD_LEAD.matchEntire(item.trim()) ?: return item.trim() to ""
        return match.groupValues[1].trim() to match.groupValues[2].trim()
    }

    private fun unquote(text: String): String = text.trim().removePrefix("«").removeSuffix("»").trim()

    /** The lines of the first `## ` section whose title matches, up to the next title or block. */
    private fun section(lines: List<String>, title: (String) -> Boolean): List<String> {
        val start = lines.indexOfFirst { it.startsWith("## ") && title(it.removePrefix("## ")) }
        if (start < 0) return emptyList()
        return lines.drop(start + 1).takeWhile { !it.startsWith("#") && !it.trimStart().startsWith(":::") }
    }

    /** The lines inside the first `::: name` block. */
    private fun block(lines: List<String>, name: String): List<String> {
        val start = lines.indexOfFirst { it.trim() == "::: $name" }
        if (start < 0) return emptyList()
        return lines.drop(start + 1).takeWhile { it.trim() != ":::" }
    }
}
