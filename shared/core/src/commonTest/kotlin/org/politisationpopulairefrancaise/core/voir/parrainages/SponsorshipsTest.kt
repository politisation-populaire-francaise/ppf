package org.politisationpopulairefrancaise.core.voir.parrainages

import org.politisationpopulairefrancaise.core.elus.ElusFinder
import org.politisationpopulairefrancaise.core.elus.ElusFixtures
import org.politisationpopulairefrancaise.core.elus.ElusReader
import org.politisationpopulairefrancaise.core.elus.Mandate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The presentations of the presidential election (VO-37): neutral mandates, the same figures for every candidate
 * in alphabetical order, and « mon maire, mon député a parrainé » matched on the device, never on a name alone.
 */
class SponsorshipsTest {

    private fun s(last: String, first: String, mandate: SponsorMandate, territory: String?, department: String, candidate: String, on: String = "2027-02-16", code: String? = null) =
        Sponsorship("[EXEMPLE] $last", first, mandate, null, territory, department, code, "[EXEMPLE] $candidate", on)

    private val pkg = SponsorshipsPackage(
        example = true,
        election = SponsorshipsFormat.ELECTION_2027,
        publishedOn = "2027-02-19",
        sponsorships = listOf(
            // The same name as the mayor of Lyon, but mayor of another commune: never mistaken for her.
            s("MAIRE", "Noa", SponsorMandate.MAIRE, "Villeurbanne", "Rhône", "ZOLA Bastien", code = "69"),
            s("MAIRE", "Noa", SponsorMandate.MAIRE, "Lyon", "Rhône", "ZOLA Bastien", code = "69"),
            s("PREMIERE", "Elie", SponsorMandate.DEPUTE, "1ère circonscription", "Rhône", "ARNAUD Claire", "2027-02-19", "69"),
            // Not the constituency of Remi Deuxieme (2nd): not him.
            s("DEUXIEME", "Remi", SponsorMandate.DEPUTE, "7ème circonscription", "Rhône", "ARNAUD Claire", code = "69"),
            s("SENAT", "Kim", SponsorMandate.SENATEUR, null, "Finistère", "ARNAUD Claire", code = "29"),
            s("EUROPE", "Ana", SponsorMandate.DEPUTE_EUROPEEN, null, "Parlement européen", "ZOLA Bastien"),
        ),
    )
    private val catalog = SponsorshipsCatalog(pkg)

    @Test
    fun theOfficialLabelsBecomeNeutralCodes() {
        assertEquals(SponsorMandate.MAIRE, SponsorMandate.of("Maire"))
        assertEquals(SponsorMandate.MAIRE_DELEGUE, SponsorMandate.of("Maire délégué d'une commune associée ou d'une commune déléguée"))
        assertEquals(SponsorMandate.DEPUTE, SponsorMandate.of("Députée"))
        assertEquals(SponsorMandate.SENATEUR, SponsorMandate.of("Sénatrice"))
        assertEquals(SponsorMandate.SENATEUR, SponsorMandate.of("Sénateur"))
        assertEquals(SponsorMandate.DEPUTE_EUROPEEN, SponsorMandate.of("Représentante française au Parlement européen"))
        assertEquals(SponsorMandate.CONSEILLER_DEPARTEMENTAL, SponsorMandate.of("Conseillère départementale"))
        assertEquals(SponsorMandate.CONSEILLER_REGIONAL, SponsorMandate.of("Conseiller régional"))
        assertEquals(SponsorMandate.CONSEILLER_PARIS, SponsorMandate.of("Conseillère de Paris"))
        assertEquals(SponsorMandate.CONSEILLER_METROPOLITAIN_LYON, SponsorMandate.of("Conseiller métropolitain de Lyon"))
        assertEquals(SponsorMandate.CONSEILLER_AFE, SponsorMandate.of("Conseiller à l'Assemblée des Français de l'étranger"))
        assertEquals(SponsorMandate.AUTRE, SponsorMandate.of("Président d'un conseil consulaire"))
    }

    @Test
    fun everyCandidateHasTheSameFiguresInAlphabeticalOrder() {
        val candidates = catalog.candidates()
        assertEquals(listOf("[EXEMPLE] ARNAUD Claire", "[EXEMPLE] ZOLA Bastien"), candidates.map { it.candidate })
        val arnaud = candidates[0]
        assertEquals(CandidateSponsors("[EXEMPLE] ARNAUD Claire", 3, 2, 2, "2027-02-16", "2027-02-19"), arnaud)
        assertEquals(listOf("Finistère", "Rhône", "Rhône"), catalog.of(arnaud.candidate).map { it.department })
        assertEquals(listOf("Finistère", "Parlement européen", "Rhône"), catalog.departments().map { it.first })
        assertEquals(4, catalog.inDepartment("rhone").size)
        assertEquals(6, catalog.total)
        assertTrue(catalog.example)
    }

    @Test
    fun myMayorAndMyDeputyAreFoundButNeverOnANameAlone() {
        val rhone = assertNotNull(ElusReader.readPackage(ElusFixtures.text(ElusFixtures.rhone)))
        val france = assertNotNull(ElusReader.readPackage(ElusFixtures.text(ElusFixtures.france)))
        val lyon = assertNotNull(ElusFinder.find(rhone, "69123", france))
        val mine = MySponsors.of(lyon, catalog)
        val byName = mine.associate { it.official.displayName to it.sponsorship }
        assertEquals("Lyon", byName["Noa Maire"]?.territory, "the mayor of Lyon, not her namesake of Villeurbanne")
        assertEquals("[EXEMPLE] ARNAUD Claire", byName["Elie Premiere"]?.candidate)
        assertNull(byName["Remi Deuxieme"], "another constituency: not him")
        assertNull(byName["Kim Senat"], "another department: not her")
        assertEquals("[EXEMPLE] ZOLA Bastien", byName["Ana Europe"]?.candidate)
        assertEquals(listOf(Mandate.MUNICIPAL, Mandate.DEPUTE, Mandate.EUROPEEN), mine.map { it.official.mandate })
        assertEquals(1, MySponsors.constituency("1ère circonscription"))
        assertEquals(5, MySponsors.constituency("5ème circonscription"))
        assertNull(MySponsors.constituency("Lyon"))
    }

    @Test
    fun thePackageReadsBackAndRefusesAnotherFormat() {
        val text = SponsorshipsFormat.write(pkg)
        assertEquals(pkg, SponsorshipsFormat.read(text))
        assertTrue("\"mandate\":\"depute-europeen\"" in text)
        assertTrue("civilit" !in text.lowercase(), "the civility is never kept")
        assertNull(SponsorshipsFormat.read(SponsorshipsFormat.write(pkg.copy(format = 2))))
        assertNull(SponsorshipsFormat.read("{}"))
    }
}
