package org.politisationpopulairefrancaise.core.agir.organisations

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrganisationQueryTest {

    private val fixtures = OrganisationFixtures()

    private suspend fun directory(): List<Organisation> = listOf(
        fixtures.entry("lanterne", name = "[EXEMPLE] Les Ateliers de la Lanterne", type = "association", territory = "Lyon (69)"),
        fixtures.entry("livreurs", name = "[EXEMPLE] Syndicat fictif des livreurs à vélo", type = "syndicat", territory = "France"),
        fixtures.entry("union-lille", name = "[EXEMPLE] Union locale fictive de Lille", type = "syndicat", territory = "Lille (59)"),
        fixtures.entry("legal-nord", name = "[EXEMPLE] Legal team de démonstration du Nord", type = "legal-team", territory = "Nord (59)"),
        fixtures.entry("medics", name = "[EXEMPLE] Street medics de démonstration", type = "street-medics", territory = "Marseille (13)"),
        fixtures.entry("avocats", name = "[EXEMPLE] Réseau fictif d'avocats bénévoles", type = "avocats-benevoles", territory = "France"),
        fixtures.entry("tilleuls", name = "[EXEMPLE] Collectif du quartier des Tilleuls", type = "collectif", territory = "Rennes (35)", presentation = "Jardins partagés et entraide."),
        fixtures.entry("ajaccio", name = "[EXEMPLE] Atelier fictif d'Ajaccio", type = "autre", territory = "Corse-du-Sud (2A)"),
    ).map(fixtures::read)

    private fun List<Organisation>.slugs(): List<String> = map { it.slug }

    @Test
    fun withoutCriteriaEverythingIsSortedByName() = runTest {
        val query = OrganisationQuery()
        assertTrue(query.isUnconstrained)
        assertEquals(0, query.constraintCount)
        // Accents, case and the [EXEMPLE] marker are ignored: "Atelier", "Collectif", "Legal", "Les", "Réseau", "Street", "Syndicat", "Union".
        assertEquals(
            listOf("ajaccio", "tilleuls", "legal-nord", "lanterne", "avocats", "medics", "livreurs", "union-lille"),
            query.apply(directory()).slugs(),
        )
    }

    @Test
    fun revokedEntriesAreLeftOutUnlessAsked() = runTest {
        val revoked = fixtures.read(fixtures.entry("retiree", name = "[EXEMPLE] Association retirée", status = "revoquee"))
        val all = directory() + revoked
        assertFalse("retiree" in OrganisationQuery().apply(all).slugs())
        val including = OrganisationQuery(includeRevoked = true)
        assertTrue("retiree" in including.apply(all).slugs())
        assertFalse(including.isUnconstrained)
        assertEquals(1, including.constraintCount)
    }

    @Test
    fun wordsAreLookedForInTheNameTheTypeAndThePresentation() = runTest {
        val all = directory()
        assertEquals(listOf("livreurs"), OrganisationQuery(text = "livreur velo").apply(all).slugs())
        assertEquals(listOf("avocats"), OrganisationQuery(text = "AVOCATS benevoles").apply(all).slugs())
        assertEquals(listOf("legal-nord"), OrganisationQuery(text = "legal team").apply(all).slugs())
        assertEquals(listOf("tilleuls"), OrganisationQuery(text = "jardins").apply(all).slugs())
        assertEquals(listOf("union-lille", "livreurs").sorted(), OrganisationQuery(text = "syndicat").apply(all).slugs().sorted())
        assertTrue(OrganisationQuery(text = "boulangerie").apply(all).isEmpty())
    }

    @Test
    fun typesAndTerritoryNarrowTheList() = runTest {
        val all = directory()
        assertEquals(listOf("livreurs", "union-lille"), OrganisationQuery(types = setOf("syndicat")).apply(all).slugs())
        assertEquals(listOf("legal-nord", "medics"), OrganisationQuery(types = setOf("legal-team", "street-medics")).apply(all).slugs())
        assertEquals(listOf("legal-nord"), OrganisationQuery(territory = "nord (59)").apply(all).slugs())
        assertEquals(listOf("lanterne"), OrganisationQuery(territory = "LYON (69)").apply(all).slugs())
        val both = OrganisationQuery(types = setOf("syndicat"), territory = "Lille (59)")
        assertEquals(listOf("union-lille"), both.apply(all).slugs())
        assertEquals(2, both.constraintCount)
    }

    @Test
    fun nearMeKeepsTheLocalOnesFirstThenTheNationalOnes() = runTest {
        val all = directory()
        // A postal code of Lille: department 59, then the national organisations.
        assertEquals(listOf("legal-nord", "union-lille", "avocats", "livreurs"), OrganisationQuery(near = "59000").apply(all).slugs())
        // A commune, with or without its district.
        assertEquals(listOf("lanterne", "avocats", "livreurs"), OrganisationQuery(near = "Lyon 3e").apply(all).slugs())
        assertEquals(listOf("lanterne", "avocats", "livreurs"), OrganisationQuery(near = "lyon").apply(all).slugs())
        // Corsica: the postal codes 20xxx belong to 2A or 2B.
        assertEquals(listOf("ajaccio", "avocats", "livreurs"), OrganisationQuery(near = "20000").apply(all).slugs())
        // Nothing near: only the organisations that act everywhere.
        assertEquals(listOf("avocats", "livreurs"), OrganisationQuery(near = "Brest").apply(all).slugs())
        // Combined with a type.
        assertEquals(listOf("union-lille", "livreurs"), OrganisationQuery(types = setOf("syndicat"), near = "Nord (59)").apply(all).slugs())
    }

    @Test
    fun territoriesAreNearByWordsOrByDepartment() {
        assertTrue(Territories.isNear("Lyon (69)", "lyon"))
        assertTrue(Territories.isNear("Métropole de Lyon", "Lyon"))
        assertTrue(Territories.isNear("Nord (59)", "59"))
        assertTrue(Territories.isNear("Nord (59)", "59800"))
        assertTrue(Territories.isNear("La Réunion (974)", "97411"))
        assertTrue(Territories.isNear("Haute-Corse (2B)", "20200"))
        assertFalse(Territories.isNear("Lille", "Nord"), "no list of communes on the device")
        assertFalse(Territories.isNear("Lyon (69)", "Marseille (13)"))
        assertFalse(Territories.isNear("Lyon (69)", ""))
        assertEquals(setOf("75"), Territories.departments("paris 75"))
        assertEquals(setOf("971"), Territories.departments("97110"))
        assertEquals(emptySet(), Territories.departments("paris 20e"))
    }
}
