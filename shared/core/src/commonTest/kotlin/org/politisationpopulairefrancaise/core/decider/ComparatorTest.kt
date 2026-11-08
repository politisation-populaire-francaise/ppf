package org.politisationpopulairefrancaise.core.decider

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.voir.VoirCatalog
import org.politisationpopulairefrancaise.core.voir.VoirTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The comparator (DE-16): the same themes in the same order for every candidate, alphabetical lines. */
class ComparatorTest {
    private val keys = IdentityKeys.generate()
    private val author = keys.publicKey.hex

    private fun address(slug: String) = ContentAddress.parse("30023:$author:$slug")

    private fun block(slug: String, type: ArticleType, title: String, vararg tags: List<String>, updatedAt: Long = 1_794_000_000): Article {
        val unsigned = Article.build(keys.publicKey, slug, title, null, emptyList(), type, ContentOrigin.HUMAN, 1_793_000_000, updatedAt, "Corps.", extraTags = tags.toList())
        val event = SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128))
        return Article.from(event)!!
    }

    private fun link(slug: String) = listOf(VoirTags.LINK, address(slug).toString(), "", DeciderTags.ROLE_CANDIDATE)

    private val election = listOf(DeciderTags.ELECTION, "presidentielle-2027", "[EXEMPLE] Élection présidentielle", "")

    private fun sheet(candidates: List<String> = listOf("zoe", "alex", "camille"), themes: List<String> = listOf("sante", "logement", "democratie")) = block(
        "comparateur", ArticleType.COMPARATEUR, "[EXEMPLE] Comparateur",
        election,
        *(themes.map { listOf(DeciderTags.THEME, it) } + candidates.map(::link)).toTypedArray(),
    )

    private fun position(
        slug: String,
        candidate: String,
        theme: String,
        summary: String = "[EXEMPLE] Une position résumée en une phrase.",
        quote: List<String> = listOf(DeciderTags.QUOTE, "[EXEMPLE] Une phrase du programme.", "https://exemple.invalid/programme", "2027-01-15"),
        electionTag: List<String> = election,
        updatedAt: Long = 1_794_000_000,
    ) = block(slug, ArticleType.POSITION, summary, electionTag, link(candidate), listOf(DeciderTags.THEME, theme), quote, updatedAt = updatedAt)

    private fun person(slug: String, name: String) = block(slug, ArticleType.PERSONNE, name)

    private val voir = VoirCatalog.from(listOf(person("zoe", "Zoé Durand"), person("alex", "[EXEMPLE] Alex Modèle"), person("camille", "Camille Été")))

    private fun grid(vararg positions: Article): ComparatorGrid {
        val catalog = DeciderCatalog.from(listOf(sheet()) + positions)
        return catalog.grid(assertNotNull(catalog.currentComparator()), voir)
    }

    @Test
    fun everyCandidateHasEveryThemeInTheSameOrderAndTheLinesAreAlphabetical() {
        val grid = grid(position("p1", "zoe", "sante"), position("p2", "alex", "logement"))
        assertEquals(listOf("[EXEMPLE] Alex Modèle", "Camille Été", "Zoé Durand"), grid.rows.map { it.name })
        grid.rows.forEach { row -> assertEquals(listOf("sante", "logement", "democratie"), row.cells.map { it.theme }) }
        // Nothing published is shown as nothing, never filled in.
        assertNull(grid.rows.first { it.name == "Camille Été" }.cell("sante")!!.position)
        assertNotNull(grid.rows.first { it.name == "Zoé Durand" }.cell("sante")!!.position)
        assertEquals(3, grid.column("logement").size)
    }

    @Test
    fun onlyTheLatestPositionCountsAndTheOthersAreListedApart() {
        val grid = grid(
            position("p1", "zoe", "sante", summary = "Ancienne", updatedAt = 1_794_000_000),
            position("p2", "zoe", "sante", summary = "Nouvelle", updatedAt = 1_794_100_000),
            position("p3", "dominique", "sante"),
            position("p4", "zoe", "chasse"),
            position("p5", "zoe", "sante", electionTag = listOf(DeciderTags.ELECTION, "legislatives", "Législatives", "")),
        )
        assertEquals("Nouvelle", grid.rows.first { it.name == "Zoé Durand" }.cell("sante")!!.position!!.summary)
        assertEquals(
            setOf(PositionAside.NOT_A_CANDIDATE, PositionAside.OTHER_THEME, PositionAside.OTHER_ELECTION),
            grid.aside.map { it.second }.toSet(),
        )
    }

    @Test
    fun theSameLimitsForEveryoneAndASourceForEveryPosition() {
        assertNull(Position.from(position("long", "zoe", "sante", summary = "a".repeat(Position.MAX_SUMMARY + 1))))
        assertNull(Position.from(position("citation", "zoe", "sante", quote = listOf(DeciderTags.QUOTE, "b".repeat(Position.MAX_QUOTE + 1), "https://exemple.invalid/p", "2027-01-15"))))
        assertNull(Position.from(position("sans-lien", "zoe", "sante", quote = listOf(DeciderTags.QUOTE, "Texte", "javascript:alert(1)", "2027-01-15"))))
        assertNull(Position.from(position("sans-date", "zoe", "sante", quote = listOf(DeciderTags.QUOTE, "Texte", "https://exemple.invalid/p", ""))))
        assertNotNull(Position.from(position("bonne", "zoe", "sante")))
    }

    @Test
    fun aSheetNeedsItsThemesOnceEach() {
        assertNull(ComparatorSheet.from(sheet(themes = emptyList())))
        assertNull(ComparatorSheet.from(sheet(themes = listOf("sante", "sante"))))
        assertNull(ComparatorSheet.from(sheet(themes = (1..ComparatorSheet.MAX_THEMES + 1).map { "t$it" })))
        assertTrue(ArticleType.COMPARATEUR in DeciderCatalog.TYPES && ArticleType.POSITION in DeciderCatalog.TYPES)
    }
}
