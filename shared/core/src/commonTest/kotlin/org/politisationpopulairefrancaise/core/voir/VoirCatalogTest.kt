package org.politisationpopulairefrancaise.core.voir

import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoirCatalogTest {

    private val keys = IdentityKeys.generate()
    private val author = keys.publicKey.hex

    private fun address(slug: String) = "30023:$author:$slug"

    /** An unsigned article read as the catalog reads it (the signature is checked before, by the library). */
    private fun block(slug: String, type: ArticleType, title: String, vararg tags: List<String>, summary: String? = null, updatedAt: Long = 1_794_000_000, body: String = "Corps."): Article {
        val unsigned = Article.build(keys.publicKey, slug, title, summary, emptyList(), type, ContentOrigin.HUMAN, 1_793_000_000, updatedAt, body, extraTags = tags.toList())
        val event = SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128))
        return Article.from(event)!!
    }

    private fun indicator(vararg measures: String, slug: String = "manifestants", axis: String = "mouvement", rank: String? = "1") = block(
        slug, ArticleType.INDICATEUR, "[EXEMPLE] Manifestants",
        listOf(VoirTags.AXIS, axis), listOf(VoirTags.UNIT, "personnes"),
        *(if (rank != null) arrayOf(listOf(VoirTags.RANK, rank)) else emptyArray()),
        *measures.map { listOf(VoirTags.MEASURE) + it.split('|').map(String::trim) }.toTypedArray(),
    )

    @Test
    fun readsTheMeasuresOfAnIndicatorSideBySide() {
        val article = indicator(
            "a | 2026-09-18 | 312000 | autorites | Ministère (exemple) | https://example.org/a | moyenne | Journée du 18 septembre | France",
            "b | 2026-09-18 | 1050000 | organisateurs | Intersyndicale (exemple) | | moyenne | Journée du 18 septembre | France",
            "c | 2026-10-02 | 268000 | autorites | Ministère (exemple) | javascript:alert(1) | elevee | Journée du 2 octobre",
            "broken | 2026-13-02 | 5 | autorites | X | | elevee",
            "negative | 2026-10-02 | -5 | autorites | X | | elevee",
            "unknown-kind | 2026-10-02 | 5 | rumeur | X | | elevee",
        )
        val indicator = assertNotNull(Indicator.from(article))
        assertEquals(3, indicator.measures.size, "malformed measures are left out")
        assertEquals(listOf("Journée du 18 septembre", "Journée du 2 octobre"), indicator.readings.map { it.occasion })
        val first = indicator.readings.first()
        assertEquals(listOf(SourceKind.AUTHORITIES, SourceKind.ORGANISERS), first.measures.map { it.source.kind })
        assertTrue(first.diverges)
        assertEquals("Journée du 2 octobre", indicator.latest?.occasion)
        assertEquals("Journée du 18 septembre", indicator.previous?.occasion)
        assertNull(indicator.measures.first { it.id == "c" }.source.url, "only web addresses are kept")
        assertEquals(listOf(SourceKind.AUTHORITIES, SourceKind.ORGANISERS), indicator.series.map { it.kind })
        assertEquals(2, indicator.series.first().points.size)
        assertEquals(CivilDate(2026, 10, 2), indicator.lastUpdate)
    }

    @Test
    fun anIndicatorNeedsAUnit() {
        val article = block("sans-unite", ArticleType.INDICATEUR, "Sans unité", listOf(VoirTags.AXIS, "mouvement"))
        assertNull(Indicator.from(article))
    }

    @Test
    fun sourcesThatAgreeDoNotDiverge() {
        val indicator = Indicator.from(
            indicator(
                "a | 2026-09-18 | 100 | autorites | A | | elevee | J1",
                "b | 2026-09-18 | 110 | organisateurs | B | | elevee | J1",
            ),
        )!!
        assertFalse(indicator.latest!!.diverges)
    }

    @Test
    fun aFactAlwaysCarriesItsStatusAndACaseItsProcedure() {
        val noStatus = block("f1", ArticleType.FAIT, "Fait", listOf(VoirTags.FACT, "mobilisation"), listOf(VoirTags.DATE, "2026-09-18"))
        assertNull(Fact.from(noStatus))
        val caseWithoutProcedure = block(
            "f2", ArticleType.FAIT, "Affaire",
            listOf(VoirTags.FACT, "affaire"), listOf(VoirTags.DATE, "2026-06-03"), listOf(VoirTags.FACT_STATUS, "enquete"),
        )
        assertNull(Fact.from(caseWithoutProcedure), "presumption of innocence: no case without the stage of its procedure")
        val case = block(
            "f3", ArticleType.FAIT, "Affaire",
            listOf(VoirTags.FACT, "affaire"), listOf(VoirTags.DATE, "2026-06-03"), listOf(VoirTags.FACT_STATUS, "enquete"),
            listOf(VoirTags.PROCEDURE, "enquete"), listOf(VoirTags.LINK, address("dominique"), "", VoirTags.ROLE_PERSON),
            listOf(VoirTags.CORRECTION, "2026-06-05", "Statut corrigé"),
        )
        val fact = assertNotNull(Fact.from(case))
        assertEquals(ProcedureStage.INVESTIGATION, fact.procedure)
        assertTrue(fact.procedure!!.presumedInnocent)
        assertEquals(listOf(ContentAddress.parse(address("dominique"))), fact.persons)
        assertEquals("Statut corrigé", fact.corrections.single().reason)
    }

    @Test
    fun oneFactAppearsInEveryPlaceItBelongs() {
        val person = block("camille", ArticleType.PERSONNE, "[EXEMPLE] Camille Exemple", listOf(VoirTags.ROLE, "depute"), listOf(VoirTags.MANDATE, "Députée", "Exempleville", "2024-07-18", ""), summary = "Députée")
        val bill = block("pjl", ArticleType.TEXTE, "[EXEMPLE] Projet de loi", listOf(VoirTags.STAGE, "senat"), listOf(VoirTags.NEXT_VOTE, "2026-11-18", "Vote au Sénat"), listOf(VoirTags.ARTICLE_49_3, "2026-10-21"))
        val ban = block(
            "arrete", ArticleType.FAIT, "[EXEMPLE] Arrêté d'interdiction",
            listOf(VoirTags.FACT, "decision-officielle"), listOf(VoirTags.DATE, "2026-10-02"), listOf(VoirTags.FACT_STATUS, "etabli"),
            listOf(VoirTags.LIBERTY, "interdiction-manifester"), listOf(VoirTags.RECOURSE, "suspendu"),
        )
        val vote = block(
            "vote", ArticleType.FAIT, "[EXEMPLE] Amendement 112",
            listOf(VoirTags.FACT, "vote"), listOf(VoirTags.DATE, "2026-10-16"), listOf(VoirTags.FACT_STATUS, "etabli"), listOf(VoirTags.VOTE, "pour"),
            listOf(VoirTags.LINK, address("camille"), "", VoirTags.ROLE_PERSON), listOf(VoirTags.LINK, address("pjl"), "", VoirTags.ROLE_TEXT),
        )
        val catalog = VoirCatalog.from(listOf(person, bill, ban, vote))
        assertEquals(listOf("arrete"), catalog.chronology().map { it.article.slug })
        assertEquals(listOf("arrete"), catalog.liberties().map { it.article.slug }, "a prefectoral ban is also in the observatory of freedoms")
        assertEquals(listOf(LibertyCategory.PROTEST_BAN), catalog.libertyCategoriesInUse())
        assertEquals(listOf("vote"), catalog.factsAbout(person.address).map { it.article.slug })
        assertEquals(listOf("vote"), catalog.factsOn(bill.address).map { it.article.slug })
        val fact = catalog.fact(vote.address)!!
        assertEquals(listOf("[EXEMPLE] Camille Exemple"), catalog.personsOf(fact).map { it.name })
        assertEquals(VotePosition.FOR, fact.vote)
        val text = catalog.text(bill.address)!!
        assertEquals(TextStage.SENATE, text.stage)
        assertEquals(PlannedVote(CivilDate(2026, 11, 18), "Vote au Sénat"), text.nextVote)
        assertEquals(listOf(CivilDate(2026, 10, 21)), text.uses493)
        val sheet = catalog.person(person.address)!!
        assertTrue(sheet.mandates.single().current)
        assertEquals(listOf(PublicRole.DEPUTY), catalog.rolesInUse())
    }

    @Test
    fun repliesAreShownUnderTheContentTheyAnswer() {
        val fact = block("affaire", ArticleType.FAIT, "Affaire", listOf(VoirTags.FACT, "affaire"), listOf(VoirTags.DATE, "2026-06-03"), listOf(VoirTags.FACT_STATUS, "enquete"), listOf(VoirTags.PROCEDURE, "enquete"))
        val reply = block("reponse", ArticleType.REPONSE, "Droit de réponse", listOf(VoirTags.RESPONDENT, "Dominique Fictif"), listOf(VoirTags.LINK, address("affaire"), "", VoirTags.ROLE_TARGET), body = "Je conteste.")
        val anonymous = block("anonyme", ArticleType.REPONSE, "Sans nom", listOf(VoirTags.LINK, address("affaire"), "", VoirTags.ROLE_TARGET))
        val catalog = VoirCatalog.from(listOf(fact, reply, anonymous))
        assertEquals(listOf("Dominique Fictif"), catalog.repliesTo(fact.address).map { it.respondent })
        assertEquals("Je conteste.", catalog.repliesTo(fact.address).single().text)
    }

    @Test
    fun theMostRecentVersionOfABlockWins() {
        val old = indicator("a | 2026-09-18 | 100 | autorites | A | | elevee")
        val newer = block(
            "manifestants", ArticleType.INDICATEUR, "[EXEMPLE] Manifestants (corrigé)",
            listOf(VoirTags.UNIT, "personnes"), listOf(VoirTags.MEASURE, "a", "2026-09-18", "120", "autorites", "A", "", "elevee"),
            updatedAt = 1_795_000_000,
        )
        val catalog = VoirCatalog.from(listOf(newer, old))
        assertEquals(1, catalog.indicators.size)
        assertEquals(120.0, catalog.indicators.single().measures.single().value)
    }

    @Test
    fun keyFiguresFollowTheirRankAndCorrectionsAreLogged() {
        val second = indicator("a | 2026-09-18 | 1 | autorites | A | | elevee", slug = "deux", rank = "2")
        val unranked = indicator("a | 2026-09-18 | 1 | autorites | A | | elevee", slug = "aaa", rank = null)
        val first = block(
            "un", ArticleType.INDICATEUR, "Zèbres", listOf(VoirTags.UNIT, "zèbres"), listOf(VoirTags.RANK, "1"),
            listOf(VoirTags.CORRECTION, "2026-10-18", "Chiffres inversés"),
        )
        val freedoms = indicator("a | 2026-09-18 | 1 | parlement | A | | elevee", slug = "493", axis = "libertes")
        val catalog = VoirCatalog.from(listOf(second, unranked, first, freedoms))
        assertEquals(listOf("un", "deux", "aaa"), catalog.keyFigures(VoirAxis.MOUVEMENT).map { it.article.slug })
        assertEquals(listOf("493"), catalog.keyFigures(VoirAxis.LIBERTES).map { it.article.slug })
        assertEquals("Chiffres inversés", catalog.corrections().single().correction.reason)
    }

    @Test
    fun searchIgnoresAccentsAndCase() {
        val person = block("camille", ArticleType.PERSONNE, "[EXEMPLE] Camille Exemple", listOf(VoirTags.ROLE, "depute"), listOf(Article.TAG_TERRITORY, "Exempleville"), summary = "Députée")
        val figure = indicator("a | 2026-09-18 | 1 | autorites | A | | elevee")
        val catalog = VoirCatalog.from(listOf(person, figure))
        assertEquals(listOf(person.address), catalog.search("DEPUTEE").map { it.address })
        assertEquals(listOf(person.address), catalog.search("exemplev").map { it.address })
        assertEquals(listOf(figure.address), catalog.search("manif").map { it.address })
        assertTrue(catalog.search("  ").isEmpty())
    }

    @Test
    fun nextStepsLeadToASpaceOrAContent() {
        assertEquals(NextTarget.ToSpace(Space.CARTE), NextTarget.parse("space:carte"))
        assertNull(NextTarget.parse("space:nowhere"))
        assertEquals(NextTarget.ToContent(ContentAddress.parse(address("garde-a-vue"))), NextTarget.parse(address("garde-a-vue")))
        assertNull(NextTarget.parse("https://example.org"))
    }

    @Test
    fun billsUnderDiscussionComeFirstNearestVoteFirst() {
        val later = block("later", ArticleType.TEXTE, "Plus tard", listOf(VoirTags.STAGE, "commission"), listOf(VoirTags.NEXT_VOTE, "2026-11-24", "Séance"))
        val sooner = block("sooner", ArticleType.TEXTE, "Bientôt", listOf(VoirTags.STAGE, "assemblee"), listOf(VoirTags.NEXT_VOTE, "2026-11-12", "Censure"))
        val closed = block("closed", ArticleType.TEXTE, "Promulgué", listOf(VoirTags.STAGE, "promulgue"), listOf(VoirTags.STAGE_HISTORY, "promulgue", "2026-01-10"))
        val invalid = block("invalid", ArticleType.TEXTE, "Sans étape")
        val catalog = VoirCatalog.from(listOf(closed, later, sooner, invalid))
        assertEquals(listOf("sooner", "later", "closed"), catalog.bills().map { it.article.slug })
    }

    @Test
    fun frenchNumbersAndDates() {
        assertEquals("45 000", FrenchNumbers.format(45_000.0))
        assertEquals("1 050 000", FrenchNumbers.format(1_050_000.0))
        assertEquals("2,5", FrenchNumbers.format(2.5))
        assertEquals("1,1 M", FrenchNumbers.short(1_050_000.0))
        assertEquals("12 à 45", FrenchNumbers.range(12.0, 45.0))
        assertEquals(45_000.0, Indicator.parseNumber("45 000"))
        assertEquals(2.5, Indicator.parseNumber("2,5"))
        assertNull(Indicator.parseNumber("12abc"))
        assertEquals(CivilDate(2026, 11, 5), CivilDate.parseIso("2026-11-05"))
        assertNull(CivilDate.parseIso("2026-02-30"))
        assertNull(CivilDate.parseIso("05/11/2026"))
        val date = CivilDate(2024, 2, 29)
        assertEquals(date, CivilDate.fromEpochDays(date.toEpochDays()))
        assertEquals(0L, CivilDate(1970, 1, 1).toEpochDays())
        assertEquals("2026-11-05", CivilDate(2026, 11, 5).toIso())
        assertEquals("5 nov. 2026", CivilDate(2026, 11, 5).toFrenchShort())
        assertTrue(CivilDate(2026, 11, 5) > CivilDate(2026, 10, 31))
    }
}
