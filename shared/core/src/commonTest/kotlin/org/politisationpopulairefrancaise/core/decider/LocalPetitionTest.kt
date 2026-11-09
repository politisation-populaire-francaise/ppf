package org.politisationpopulairefrancaise.core.decider

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** DE-26: the local petitions and the threshold of CGCT L1112-16 (law n° 2022-217), computed on the device. */
class LocalPetitionTest {
    private val keys = IdentityKeys.generate()

    private fun article(slug: String, tags: List<List<String>>): Article {
        val unsigned = Article.build(keys.publicKey, slug, "[EXEMPLE] Pétition $slug", null, emptyList(), ArticleType.PETITION_LOCALE, ContentOrigin.HUMAN, 1_793_000_000, 1_794_000_000, "Corps.", extraTags = tags)
        return Article.from(SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128)))!!
    }

    private fun petition(
        slug: String = "p",
        territory: String = "69123",
        collectivity: List<String> = listOf("Commune d’exemple", "commune"),
        voters: String = "12 001",
        status: String = "en-collecte",
        more: List<List<String>> = listOf(listOf(DeciderTags.HOW_TO_SIGN, "Sur papier.")),
    ): Article = article(
        slug,
        listOf(
            listOf(DeciderTags.TERRITORY_CODE, territory),
            listOf(DeciderTags.COLLECTIVITY) + collectivity,
            listOf(DeciderTags.VOTERS, voters, "2026-11-01", "listes électorales"),
            listOf(DeciderTags.LOCAL_STATUS, status),
        ) + more,
    )

    @Test
    fun aTenthOfTheVotersOfACommuneATwentiethElsewhereRoundedUp() {
        val commune = assertNotNull(LocalPetition.from(petition()))
        assertEquals(12_001L, commune.voters.count)
        assertEquals(1_201L, commune.required, "a tenth of 12 001, rounded up")
        val department = assertNotNull(LocalPetition.from(petition(territory = "69", collectivity = listOf("Département d’exemple", "autre"), voters = "1 000 000")))
        assertEquals(50_000L, department.required)
        assertNull(commune.progress, "no count yet")
        val counted = assertNotNull(LocalPetition.from(petition(more = listOf(listOf(DeciderTags.HOW_TO_SIGN, "Sur papier."), listOf(DeciderTags.COUNT, "600", "2026-11-08", "organisateurs"), listOf(DeciderTags.COUNT, "300", "2026-11-01")))))
        assertEquals(600L, counted.latestCount?.count, "the latest reading")
        assertEquals(600.0 / 1_201, counted.progress)
        assertEquals(emptyList(), counted.warnings())
    }

    @Test
    fun aPetitionWithoutItsCollectivityItsVotersOrWithASigningPageOutsideHttpsIsNotShown() {
        assertNull(LocalPetition.from(petition(collectivity = listOf("Commune", "village"))), "commune or autre only")
        assertNull(LocalPetition.from(petition(voters = "beaucoup")))
        assertNull(LocalPetition.from(petition(status = "gagnee")))
        assertNull(LocalPetition.from(petition(more = listOf(listOf(DeciderTags.OFFICIAL_URL, "http://www.mairie-exemple.fr/petition")))))
        assertNotNull(LocalPetition.from(petition(more = listOf(listOf(DeciderTags.OFFICIAL_URL, "https://www.mairie-exemple.fr/petition")))))
        // Collecting without a page nor a way to sign on paper: the signing tool says it.
        assertEquals(1, assertNotNull(LocalPetition.from(petition(more = emptyList()))).warnings().size)
    }

    @Test
    fun theHomeShowsOnlyThePetitionsOfTheCommuneChosenOnTheDeviceAndOfItsDepartment() {
        val catalog = DeciderCatalog.from(
            listOf(
                petition(slug = "lyon"),
                petition(slug = "rhone", territory = "69", collectivity = listOf("Département d’exemple", "autre"), status = "deposee"),
                petition(slug = "ailleurs", territory = "75056"),
            ),
        )
        assertEquals(emptyList(), catalog.localPetitionsFor(emptySet()))
        assertEquals(listOf("lyon", "rhone"), catalog.localPetitionsFor(setOf("69123", "69")).map { it.article.slug }, "collecting first")
    }
}
