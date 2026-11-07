package org.politisationpopulairefrancaise.core.voir

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PartiesTest {

    private val keys = IdentityKeys.generate()
    private val author = keys.publicKey.hex

    private fun address(slug: String) = "30023:$author:$slug"

    private fun block(slug: String, type: ArticleType, title: String, vararg tags: List<String>, summary: String? = null): Article {
        val unsigned = Article.build(keys.publicKey, slug, title, summary, emptyList(), type, ContentOrigin.HUMAN, 1_793_000_000, 1_794_000_000, "Corps.", extraTags = tags.toList())
        val event = SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128))
        return Article.from(event)!!
    }

    private fun link(slug: String, role: String) = listOf(VoirTags.LINK, address(slug), "", role)

    private fun seats(vararg fields: String) = listOf(VoirTags.SEATS) + fields

    private val association = listOf(VoirTags.FINANCING, "association-de-financement", "Agrément n° 0000 (exemple)", "2009-07-02", "texte-officiel", "JO (exemple)", "https://example.org/jo")

    /** A party with its mandatory financing structure (an approved association) unless [financing] says otherwise. */
    private fun party(slug: String, title: String, vararg tags: List<String>, financing: List<String>? = association) =
        block(slug, ArticleType.PARTI, title, *(listOfNotNull(financing) + tags.toList()).toTypedArray())

    private fun person(slug: String, title: String, party: String?) = block(
        slug, ArticleType.PERSONNE, title, listOf(VoirTags.ROLE, "depute"),
        *(if (party != null) arrayOf(link(party, VoirTags.ROLE_PARTY)) else emptyArray()),
    )

    @Test
    fun readsAPartyWithItsSeatsSideBySide() {
        val article = party(
            "pea", "[EXEMPLE] Parti A",
            listOf(VoirTags.ACRONYM, "PEA"), listOf(VoirTags.FOUNDED, "2009-05-16"),
            seats("assemblee", "120", "2026-10-01", "parlement", "Assemblée (exemple)", "https://example.org/an"),
            seats("assemblee", "124", "2026-10-01", "declaration", "Communiqué (exemple)", "javascript:alert(1)"),
            seats("assemblee", "98", "2022-07-01", "parlement", "Assemblée 2022 (exemple)", ""),
            seats("senat", "40", "2026-10-01", "", "", ""),
            seats("nowhere", "3", "2026-10-01"),
            seats("senat", "-2", "2026-10-01"),
            listOf(VoirTags.WEBSITE, "javascript:alert(1)"),
            link("camille", VoirTags.ROLE_LEADER),
            listOf(VoirTags.CORRECTION, "2026-11-07", "Sièges corrigés"),
        )
        val p = assertNotNull(PoliticalParty.from(article))
        assertEquals("PEA", p.acronym)
        assertEquals(CivilDate(2009, 5, 16), p.founded)
        assertEquals(4, p.seats.size, "unknown assemblies and negative counts are left out")
        assertEquals(listOf(Chamber.ASSEMBLY, Chamber.SENATE), p.chambers)
        assertEquals(listOf(120, 124, 98), p.seatsIn(Chamber.ASSEMBLY).map { it.count })
        assertEquals(120, p.referenceSeats(Chamber.ASSEMBLY)?.count, "the primary source of the latest date is the reference")
        assertNull(p.seatsIn(Chamber.ASSEMBLY)[1].source?.url, "only web addresses are kept")
        assertNull(p.referenceSeats(Chamber.SENATE)?.source, "a count may have no source")
        assertEquals(160, p.totalSeats)
        assertNull(p.website)
        assertEquals(listOf(address("camille")), p.leaders.map { it.toString() })
        assertEquals("Sièges corrigés", p.corrections.single().reason)
        assertNull(PoliticalParty.from(block("x", ArticleType.ORGANISATION, "Association")), "the directory of Agir is not a party")
        assertEquals(FinancingKind.ASSOCIATION, p.financing.kind)
        assertEquals("Agrément n° 0000 (exemple)", p.financing.approval)
        assertEquals(SourceKind.OFFICIAL_TEXT, p.financing.source?.kind)
    }

    @Test
    fun aPartyNeedsItsFinancingAndTheNameOfAFinancialAgentIsNeverKept() {
        assertNull(PoliticalParty.from(party("none", "Sans financement", financing = null)))
        assertNull(PoliticalParty.from(party("odd", "Financement inconnu", financing = listOf(VoirTags.FINANCING, "tirelire"))))
        val agent = PoliticalParty.from(party("agent", "Mandataire", financing = listOf(VoirTags.FINANCING, "mandataire-financier", "Jean Particulier", "1998-12-15")))!!
        assertEquals(FinancingKind.AGENT, agent.financing.kind)
        assertNull(agent.financing.approval, "a financial agent is a private person: the name is ignored")
        assertEquals(CivilDate(1998, 12, 15), agent.financing.date)
    }

    @Test
    fun readsThePublicAidPerYearWithItsDecreeAndTheEuropeanAffiliation() {
        val p = PoliticalParty.from(
            party(
                "pea", "Parti A",
                listOf(VoirTags.PUBLIC_FUNDING, "2025", "4 105 120", "Décret 2025 (exemple)", "https://example.org/d2025"),
                listOf(VoirTags.PUBLIC_FUNDING, "2026", "4 210 345,60", "Décret 2026 (exemple)", "https://example.org/d2026"),
                listOf(VoirTags.PUBLIC_FUNDING, "1950", "12", "Trop ancien"),
                listOf(VoirTags.PUBLIC_FUNDING, "2024", "beaucoup", "Illisible"),
                listOf(VoirTags.EUROPEAN_PARTY, "Parti européen d'exemple"),
                listOf(VoirTags.EUROPEAN_GROUP, "Groupe européen d'exemple"),
            ),
        )!!
        assertEquals(listOf(2026, 2025), p.publicFunding.map { it.year }, "latest year first, unreadable lines left out")
        assertEquals(421_034_560L, p.publicFunding.first().amountCents)
        assertEquals("Décret 2026 (exemple)", p.publicFunding.first().decree?.name)
        assertEquals(SourceKind.OFFICIAL_TEXT, p.publicFunding.first().decree?.kind)
        assertEquals("Parti européen d'exemple", p.europeanParty)
        assertEquals("Groupe européen d'exemple", p.europeanGroup)
        val catalog = VoirCatalog.from(listOf(party("pea", "Parti A", listOf(VoirTags.EUROPEAN_GROUP, "Groupe des Zèbres"))))
        assertEquals(1, catalog.search("zebres").size, "the European group is searchable")
    }

    @Test
    fun partiesAreSortedByNameNeverBySizeAndLinkTheirMembers() {
        val small = party("small", "[EXEMPLE] Parti Zèbre", seats("assemblee", "12", "2026-10-01", "parlement", "AN", ""))
        val bigB = party("big-b", "[EXEMPLE] Parti B", seats("senat", "60", "2026-10-01", "parlement", "Sénat", ""))
        val bigA = party("big-a", "[EXEMPLE] Parti A", seats("assemblee", "60", "2026-10-01", "parlement", "AN", ""))
        val none = party("none", "[EXEMPLE] Parti sans siège")
        val camille = person("camille", "[EXEMPLE] Camille Exemple", "big-a")
        val alex = person("alex", "[EXEMPLE] Alex Modèle", "big-a")
        val sacha = person("sacha", "[EXEMPLE] Sacha Témoin", "small")
        val loner = person("loner", "[EXEMPLE] Sans parti", null)
        val catalog = VoirCatalog.from(listOf(small, bigB, bigA, none, camille, alex, sacha, loner))
        assertEquals(listOf("big-a", "big-b", "none", "small"), catalog.parties.map { it.article.slug }, "by name, the [EXEMPLE] marker and accents ignored")
        assertEquals("big-a", catalog.partyOf(catalog.person(camille.address)!!)?.article?.slug)
        assertNull(catalog.partyOf(catalog.person(loner.address)!!))
        assertEquals(listOf("alex", "camille"), catalog.membersOf(bigA.address).map { it.article.slug })
        assertEquals(listOf("big-a", "small"), catalog.partiesInUse().map { it.article.slug })
        assertEquals(listOf("sacha"), catalog.persons(party = small.address).map { it.article.slug })
        assertEquals(listOf("alex", "camille"), catalog.persons(PublicRole.DEPUTY, bigA.address).map { it.article.slug })
        assertTrue(catalog.persons(PublicRole.SENATOR, bigA.address).isEmpty())
        assertTrue(ArticleType.PARTI in VoirCatalog.TYPES)
        assertEquals(VoirAxis.POUVOIR, catalog.item(bigA.address)?.axis)
    }

    @Test
    fun aPartySheetShowsOnlyTheFactsOfThePartyItself() {
        val pea = party("pea", "[EXEMPLE] Parti A")
        val camille = person("camille", "[EXEMPLE] Camille Exemple", "pea")
        val memberCase = block(
            "affaire-camille", ArticleType.FAIT, "Affaire d'un membre",
            listOf(VoirTags.FACT, "affaire"), listOf(VoirTags.DATE, "2026-06-03"), listOf(VoirTags.FACT_STATUS, "enquete"),
            listOf(VoirTags.PROCEDURE, "enquete"), link("camille", VoirTags.ROLE_PERSON),
        )
        val promise = block(
            "promesse-pea", ArticleType.FAIT, "Promesse du programme",
            listOf(VoirTags.FACT, "promesse"), listOf(VoirTags.DATE, "2024-06-20"), listOf(VoirTags.FACT_STATUS, "etabli"),
            listOf(VoirTags.PROMISE, "en-cours"), link("pea", VoirTags.ROLE_PARTY),
        )
        val catalog = VoirCatalog.from(listOf(pea, camille, memberCase, promise))
        assertEquals(listOf("promesse-pea"), catalog.factsAbout(pea.address).map { it.article.slug }, "no guilt by association")
        assertEquals(listOf("affaire-camille"), catalog.factsAbout(camille.address).map { it.article.slug })
        assertEquals(listOf(pea.address), catalog.fact(promise.address)!!.parties)
    }

    @Test
    fun searchFindsAPartyByItsAcronymAndCorrectionsAreLogged() {
        val pea = party(
            "pea", "[EXEMPLE] Parti d'exemple A", listOf(VoirTags.ACRONYM, "PEA"),
            listOf(VoirTags.CORRECTION, "2026-11-07", "Sièges corrigés"),
        )
        val camille = person("camille", "[EXEMPLE] Camille Exemple", "pea")
        val catalog = VoirCatalog.from(listOf(pea, camille))
        assertEquals(listOf(pea.address), catalog.search("pea").map { it.address })
        assertEquals(listOf(pea.address), catalog.search("parti exemple").map { it.address })
        val entry = catalog.corrections().single()
        assertEquals(pea.address, entry.item.address)
        assertTrue(entry.item is VoirItem.OfParty)
    }
}
