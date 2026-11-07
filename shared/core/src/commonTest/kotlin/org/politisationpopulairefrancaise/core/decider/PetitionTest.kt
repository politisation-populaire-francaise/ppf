package org.politisationpopulairefrancaise.core.decider

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PetitionTest {

    private val keys = IdentityKeys.generate()

    private fun petition(platform: String = "assemblee", url: String = "https://petitions.assemblee-nationale.fr/initiatives/i-1234", status: String = "ouverte", vararg tags: List<String>): Article {
        val unsigned = Article.build(
            keys.publicKey, "p", "[EXEMPLE] Pétition", null, emptyList(), ArticleType.PETITION, ContentOrigin.HUMAN, 1_793_000_000, 1_794_000_000, "Corps.",
            extraTags = listOf(listOf(DeciderTags.PLATFORM, platform), listOf(DeciderTags.OFFICIAL_URL, url), listOf(DeciderTags.PETITION_STATUS, status)) + tags.toList(),
        )
        return Article.from(SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128)))!!
    }

    private fun count(value: String, date: String) = listOf(DeciderTags.COUNT, value, date, "site officiel")

    @Test
    fun onlyTheOfficialSiteOfThePlatformIsAccepted() {
        assertNotNull(Petition.from(petition()))
        assertNull(Petition.from(petition(url = "http://petitions.assemblee-nationale.fr/")), "https only")
        assertNull(Petition.from(petition(url = "https://petitions-assemblee-nationale.fr.example.org/")), "look-alike")
        assertNull(Petition.from(petition(url = "https://petitions.assemblee-nationale.fr@evil.example/")), "user name trick")
        assertNull(Petition.from(petition(url = "https://petitions.senat.fr/")), "another platform's site")
        assertNotNull(Petition.from(petition(platform = "cese", url = "https://petitions.lecese.fr/")))
        assertNull(Petition.from(petition(platform = "inconnue")))
        assertNull(Petition.from(petition(status = "peut-etre")))
    }

    @Test
    fun theLatestReadingCountsAndTheHistoryIsKept() {
        val p = Petition.from(petition(tags = arrayOf(count("45210", "2026-10-05"), count("412 950", "2026-11-06"), count("-3", "2026-11-07"), count("12", "pas une date"))))!!
        assertEquals(listOf(412_950L, 45_210L), p.counts.map { it.count })
        assertEquals(CivilDate(2026, 11, 6), p.latestCount!!.readOn)
    }

    @Test
    fun theProgressAimsAtTheNextThresholdOfThePlatform() {
        val p = Petition.from(petition(tags = arrayOf(count("412950", "2026-11-06"))))!!
        val progress = PetitionRules.progress(p)
        assertEquals(listOf(100_000L), progress.reached.map { it.signatures })
        assertEquals(500_000L, progress.next!!.signatures)
        assertEquals(87_050L, progress.remaining)
        assertEquals(30, progress.next!!.minDepartments)
        assertFalse(progress.next!!.guaranteed, "a debate at the Assemblée is never automatic")
        assertTrue(progress.fraction in 0.82f..0.83f)
    }

    @Test
    fun everyThresholdPassedFillsTheBar() {
        val p = Petition.from(petition(platform = "senat", url = "https://petitions.senat.fr/initiatives/1", tags = arrayOf(count("118400", "2026-08-10"))))!!
        val progress = PetitionRules.progress(p)
        assertNull(progress.next)
        assertEquals(1f, progress.fraction)
    }

    @Test
    fun aSharedInitiativeReferendumTakesTheFigureOfItsProcedure() {
        val p = Petition.from(petition(platform = "rip", url = "https://www.referendum.interieur.gouv.fr/soutien/1", tags = arrayOf(count("1000000", "2027-01-01"))))!!
        assertTrue(PetitionRules.progress(p).thresholds.isEmpty(), "no figure invented when the Conseil constitutionnel has not set it")
        val set = Petition.from(petition(platform = "rip", url = "https://www.referendum.interieur.gouv.fr/soutien/1", tags = arrayOf(count("1000000", "2027-01-01"), listOf(DeciderTags.REQUIRED, "4 900 000"))))!!
        assertEquals(3_900_000L, PetitionRules.progress(set).remaining)
    }

    @Test
    fun aPetitionClosesOnItsLastDay() {
        val p = Petition.from(petition(tags = arrayOf(listOf(DeciderTags.CLOSES, "2026-11-30"))))!!
        assertTrue(p.isOpen(CivilDate(2026, 11, 30)))
        assertEquals(0L, p.daysLeft(CivilDate(2026, 11, 30)))
        assertFalse(p.isOpen(CivilDate(2026, 12, 1)))
        assertEquals(23L, p.daysLeft(CivilDate(2026, 11, 7)))
    }

    @Test
    fun theRulesNameTheirLegalTextAndTheDayTheyWereChecked() {
        PetitionPlatform.entries.forEach { platform ->
            val rules = PetitionRules.of(platform)
            assertTrue(rules.sourceUrl.startsWith("https://"))
            assertTrue(rules.legalReference.isNotBlank())
            assertEquals(PetitionRules.VERIFIED_ON, rules.verifiedOn)
            assertEquals(rules.thresholds.sortedBy { it.signatures }, rules.thresholds)
        }
        assertEquals(16, PetitionRules.of(PetitionPlatform.CESE).minimumAge)
        assertEquals(6, PetitionRules.of(PetitionPlatform.SENAT).collectionMonths)
    }
}
