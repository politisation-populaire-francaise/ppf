package org.politisationpopulairefrancaise.tools.opendata

import org.politisationpopulairefrancaise.core.voir.parlement.Chamber
import org.politisationpopulairefrancaise.core.voir.parlement.CorrectionKind
import org.politisationpopulairefrancaise.core.voir.parlement.NotVotingCause
import org.politisationpopulairefrancaise.core.voir.parlement.Position
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The parsers of the official formats, on the fictional example files (never the real files, never the network). */
class ParsersTest {
    private val sources = ExampleSources()

    @Test
    fun constituencyCodesFollowTheDirectoryOfElectedOfficials() {
        assertEquals("0101", Assemblee.constituencyCode("01", "1"))
        assertEquals("6901", Assemblee.constituencyCode("69", "1"))
        assertEquals("2A02", Assemblee.constituencyCode("2A", "2"))
        assertEquals("2B01", Assemblee.constituencyCode("2b", "1"))
        assertEquals("97101", Assemblee.constituencyCode("971", "1"))
        assertEquals("09911", Assemblee.constituencyCode("099", "11"))
        assertEquals("0103", Assemblee.constituencyCode("1", "3"))
        // Saint-Barthélemy and Saint-Martin share one constituency.
        assertEquals("97701", Assemblee.constituencyCode("977", "1"))
        assertEquals("97701", Assemblee.constituencyCode("978", "1"))
        assertNull(Assemblee.constituencyCode("ZZ", "1"))
        assertNull(Assemblee.constituencyCode("01", "0"))
        assertNull(Assemblee.constituencyCode("01", null))
        assertNull(Assemblee.constituencyCode(null, "1"))
    }

    @Test
    fun deputiesAndTheirGroupsAreReadFromAmo10() {
        val roster = Assemblee.members(sources.anMembers())
        assertEquals(12, roster.members.size)
        assertTrue(roster.members.all { it.chamber == Chamber.AN && it.name.startsWith("[EXEMPLE] ") && it.id.startsWith("an:PA9") })
        assertEquals(
            setOf("0101", "2A02", "97101", "09911", "6901", "3303", "7505", "5912", "1304", "97701", "2B01", "4407"),
            roster.members.map { it.territory }.toSet(),
        )
        val first = roster.members.first()
        assertEquals("an:PA900001", first.id)
        assertEquals("[EXEMPLE] Alix Martinaud", first.name)
        assertEquals("an:PO900001", first.group)
        assertEquals("Ain", first.territoryName)
        assertEquals("2024-07-08", first.since)
        assertEquals("https://www.assemblee-nationale.fr/dyn/deputes/PA900001", first.url)
        // A past group is never shown; a deputy without a group mandate has none.
        assertEquals("an:PO900003", roster.members.single { it.id == "an:PA900011" }.group)
        assertNull(roster.members.single { it.id == "an:PA900012" }.group)
        assertEquals(listOf("an:PO900001", "an:PO900002", "an:PO900003", "an:PO900005"), roster.groups.map { it.id })
        assertEquals("GDC", roster.groups.first().abbreviation)
    }

    @Test
    fun ballotsKeepCountsPositionsDelegationsCorrectionsAndCauses() {
        val dossiers = Assemblee.dossierPaths(sources.anDossiers())
        assertEquals(mapOf("DLR5L17N90001" to "exemple_logement_abordable", "DLR5L17N90002" to "exemple_transparence_rendez_vous"), dossiers)
        val ballots = Assemblee.ballots(sources.anBallots(), dossiers).associateBy { it.number }
        assertEquals((9001..9006).toSet(), ballots.keys)

        val solemn = ballots.getValue(9002)
        assertEquals("an:17:9002", solemn.id)
        assertEquals("2026-09-29", solemn.date)
        assertEquals("SPS", solemn.kind)
        assertEquals(true, solemn.adopted)
        assertEquals(5, solemn.counts.inFavour)
        assertEquals(3, solemn.counts.against)
        assertEquals(2, solemn.counts.abstention)
        assertEquals(2, solemn.counts.notVoting)
        assertEquals(10, solemn.counts.voters)
        assertEquals(8, solemn.counts.expressed)
        assertEquals(solemn.counts.inFavour, solemn.groups.sumOf { it.inFavour })
        assertEquals(solemn.counts.against, solemn.groups.sumOf { it.against })
        assertEquals(Position.FOR, solemn.positions.of("an:PA900005"))
        assertEquals(listOf("an:PA900002", "an:PA900009"), solemn.positions.delegated)
        assertEquals(listOf(Triple("an:PA900005", Position.AGAINST, CorrectionKind.STATEMENT)), solemn.positions.corrections.map { Triple(it.member, it.intended, it.kind) })
        assertNull(solemn.positions.corrections.single().date)
        // The President of the Assemblée and a member of the Government do not vote, and the reason is kept.
        assertEquals(listOf(NotVotingCause("an:PA900001", "PAN"), NotVotingCause("an:PA900008", "MG")), solemn.positions.notVotingCauses)
        assertEquals("an-dossier:DLR5L17N90001", solemn.dossier)
        assertEquals("https://www.assemblee-nationale.fr/dyn/17/dossiers/exemple_logement_abordable", solemn.dossierUrl)
        assertEquals("https://www.assemblee-nationale.fr/dyn/17/scrutins/9002", solemn.url)

        val amendment = ballots.getValue(9003)
        assertNull(amendment.dossier)
        assertEquals(
            setOf(Triple("an:PA900004", Position.NOT_VOTING, CorrectionKind.STATEMENT), Triple("an:PA900011", Position.ABSTENTION, CorrectionKind.STATEMENT)),
            amendment.positions.corrections.map { Triple(it.member, it.intended, it.kind) }.toSet(),
        )
        val censure = ballots.getValue(9004)
        assertEquals("MOC", censure.kind)
        assertEquals(false, censure.adopted)
        assertEquals(listOf(Triple("an:PA900007", Position.FOR, CorrectionKind.MALFUNCTION)), censure.positions.corrections.map { Triple(it.member, it.intended, it.kind) })
        // A dossier unknown to Dossiers_Legislatifs keeps its identifier, without an address.
        assertEquals("an-dossier:DLR5L17N90003", ballots.getValue(9006).dossier)
        assertNull(ballots.getValue(9006).dossierUrl)
        ballots.values.forEach { ballot ->
            val listed = with(ballot.positions) { inFavour + against + abstention + notVoting }
            assertEquals(listed.size, listed.toSet().size, "a member is listed once per ballot")
            assertEquals(listed.sorted(), with(ballot.positions) { inFavour.sorted() + against.sorted() + abstention.sorted() + notVoting.sorted() }.sorted())
        }
    }

    @Test
    fun theZipOfTheAssembleeIsReadEntryByEntry() {
        val zip = Files.createTempFile("scrutins", ".zip")
        ZipOutputStream(Files.newOutputStream(zip)).use { out ->
            sources.anBallots().forEach { entry ->
                out.putNextEntry(ZipEntry(entry.name))
                entry.open().use { it.copyTo(out) }
                out.closeEntry()
            }
        }
        val fromZip = Assemblee.ballots(ZipEntries(zip), emptyMap()).sortedBy { it.number }
        val fromResources = Assemblee.ballots(sources.anBallots(), emptyMap()).sortedBy { it.number }
        assertEquals(fromResources, fromZip)
        Files.delete(zip)
        assertFailsWith<UnreadableSourceException> { ZipEntries(Files.createTempFile("broken", ".zip")).forEach { } }
    }

    @Test
    fun senatorsAndTheirGroups() {
        val roster = Senat.members(sources.senators())
        assertEquals(listOf("senat:99001X", "senat:99002X", "senat:99003X", "senat:99004X"), roster.members.map { it.id })
        assertEquals(listOf("33", "2A", "975", "01"), roster.members.map { it.territory })
        assertEquals(listOf("senat:groupe:EXA", "senat:groupe:EXB"), roster.groups.map { it.id })
        assertEquals("[EXEMPLE] Mathilde Garrigou", roster.members.first().name)
        assertEquals("https://www.senat.fr/senateur/exemple_99001x.html", roster.members.first().url)
        assertNull(roster.members.first().since)
        assertEquals(Senat.LIST_PAGE, Senat.pageOf("https://example.org/page.html"))
        assertEquals("https://www.senat.fr/senateur/x.html", Senat.pageOf("http://www.senat.fr/senateur/x.html"))
        assertEquals(Senat.LIST_PAGE, Senat.pageOf(null))
    }

    @Test
    fun membersOfTheEuropeanParliamentElectedInFranceAndTheirRollCalls() {
        val api = sources.europarl()
        val roster = Europarl.members(api, sources.today)
        assertEquals(listOf("pe:990001", "pe:990002", "pe:990003"), roster.members.map { it.id })
        assertTrue(roster.members.all { it.territory == "FR" && it.name.startsWith("[EXEMPLE] ") })
        assertEquals("[EXEMPLE] Parti des Rivières", roster.members.first().nationalParty)
        // The ended national party is never shown.
        assertEquals("[EXEMPLE] Union des Coteaux", roster.members.last().nationalParty)
        assertEquals("pe:groupe:990201", roster.members.first().group)
        assertEquals(listOf("pe:groupe:990201", "pe:groupe:990202"), roster.groups.map { it.id })
        assertEquals("PONTS", roster.groups.first().abbreviation)
        assertEquals("https://www.europarl.europa.eu/meps/fr/990001", roster.members.first().url)

        val ballots = Europarl.ballots(api, setOf("990001", "990002", "990003"), sources.today)
        // The vote by show of hands and the sitting after the day of the import are not kept.
        assertEquals(listOf("pe:2026-09-16:1", "pe:2026-09-16:2", "pe:2026-10-21:1", "pe:2026-10-21:2"), ballots.map { it.id })
        val first = ballots.first()
        assertEquals(listOf("pe:990001", "pe:990002"), first.positions.inFavour)
        assertEquals(listOf("pe:990003"), first.positions.against)
        assertEquals(emptyList(), first.positions.abstention)
        assertEquals(412, first.counts.inFavour)
        assertEquals(true, first.adopted)
        assertEquals(false, ballots[1].adopted)
        // A single voter is written as a string rather than a list (JSON-LD).
        assertEquals(listOf("pe:990003"), ballots.last().positions.inFavour)
        assertTrue(ballots.all { b -> with(b.positions) { inFavour + against + abstention }.all { it.startsWith("pe:99000") } })
    }
}
