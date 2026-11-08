package org.politisationpopulairefrancaise.tools.opendata

import kotlinx.serialization.json.JsonElement
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.voir.parlement.Chamber
import org.politisationpopulairefrancaise.core.voir.parlement.HistoryBallot
import org.politisationpopulairefrancaise.core.voir.parlement.HistoryPackage
import org.politisationpopulairefrancaise.core.voir.parlement.IndexReading
import org.politisationpopulairefrancaise.core.voir.parlement.OpenDataTrust
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentFormat
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentHistory
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentIndexReader
import org.politisationpopulairefrancaise.core.voir.parlement.PartyBasis
import org.politisationpopulairefrancaise.core.voir.parlement.PartyDistribution
import org.politisationpopulairefrancaise.core.voir.parlement.historyPackage
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The history of the ballots (voir-historique-import.md), on the fictional example files; never the network. */
class HistoryTest {
    private val sources = ExampleSources()
    private val keys = IdentityKeys.generate()
    private val trust = OpenDataTrust.of(keys.publicKey.hex)

    private fun line(p: PartyDistribution) = listOf(p.inFavour, p.against, p.abstention, p.notVoting, p.members)
    private fun HistoryBallot.of(party: String) = parties.single { it.party == party }

    // ------------------------------------------------------------ JSON read as a stream

    private fun objects(json: String, objectKeys: Set<String> = setOf("scrutin"), arrayKeys: Set<String> = setOf("scrutin", "scrutins")): List<JsonElement> {
        val found = ArrayList<JsonElement>()
        JsonObjectStream(json.reader(), "test", objectKeys, arrayKeys).forEach { found += it }
        return found
    }

    @Test
    fun theObjectsOfAHugeDocumentAreHandedOverOneByOne() {
        // One ballot per file, the list of the 14th legislature, and a bare list.
        assertEquals(listOf("1"), objects("""{"scrutin": {"numero": "1", "x": [1, {"y": "}"}]}}""").map { it["numero"].text() })
        assertEquals(listOf("1", "2"), objects("""{"scrutins": {"scrutin": [{"numero": "1"}, {"numero": "2", "t": "a \"quoted\" {brace"}]}}""").map { it["numero"].text() })
        assertEquals(listOf("3", "4"), objects("""{"meta": {"n": 2, "ok": true, "z": null}, "scrutins": [{"numero": "3"}, {"numero": "4"}]}""").map { it["numero"].text() })
        assertTrue(objects("""{"autre": {"scrutinx": {}}, "scrutins": []}""").isEmpty())
        assertEquals("é", objects("""{"scrutin": {"t": "é"}}""").single()["t"].text())
        listOf("""{"scrutin": {"numero": "1"}""", """{"scrutins": [{"a": 1},]}""", """{"scrutin": {}} extra""", """{"scrutin" {}}""").forEach { broken ->
            assertFailsWith<UnreadableSourceException>(broken) { objects(broken) }
        }
        assertFailsWith<UnreadableSourceException> {
            JsonObjectStream("""{"scrutin": {"t": "${"x".repeat(200)}"}}""".reader(), "test", setOf("scrutin"), emptySet(), maxChars = 100).forEach { }
        }
    }

    // ------------------------------------------------------------ Assemblée nationale

    @Test
    fun thePartyOfADeputyAtTheDateOfABallot() {
        val reference = AssembleeHistory.reference(sources.anHistoryActors())
        // Six deputies; someone who never sat in the Assemblée is left out.
        assertEquals(6, reference.deputies.size)
        assertFalse("PA910099" in reference.deputies)
        assertEquals(setOf("PO910101", "PO910102", "PO910201", "PO910202"), reference.organs.keys)
        // Before December 2012 no party of attachment is published: the group stands for it.
        assertEquals(Line("an:PO910101", PartyBasis.GROUP), reference.lineOf("PA910001", "2012-07-10", "PO910101"))
        assertEquals(Line("an:PO910201", PartyBasis.PARTY), reference.lineOf("PA910001", "2014-03-12", "PO910101"))
        // A change of party is followed at its date.
        assertEquals(Line("an:PO910201", PartyBasis.PARTY), reference.lineOf("PA910002", "2019-12-31", "PO910101"))
        assertEquals(Line("an:PO910202", PartyBasis.PARTY), reference.lineOf("PA910002", "2020-01-01", "PO910101"))
        // A deputy who never declared a party: their group, as the ballot names it, else as AMO30 dates it.
        assertEquals(Line("an:PO910102", PartyBasis.GROUP), reference.lineOf("PA910004", "2025-01-15", "PO910102"))
        assertEquals(Line("an:PO910102", PartyBasis.GROUP), reference.lineOf("PA910004", "2025-01-15", null))
        assertNull(reference.lineOf("PA999999", "2025-01-15", null))
        // Members in office at a date, per line.
        assertEquals(mapOf(Line("an:PO910101", PartyBasis.GROUP) to 2, Line("an:PO910102", PartyBasis.GROUP) to 3), reference.members("2012-07-10"))
        assertEquals(
            mapOf(Line("an:PO910201", PartyBasis.PARTY) to 1, Line("an:PO910202", PartyBasis.PARTY) to 2, Line("an:PO910102", PartyBasis.GROUP) to 1),
            reference.members("2020-05-12"),
        )
        assertEquals(listOf(Span("A", "2012-01-01", null)), listOf(Span("B", "2011-01-01", null), Span("A", "2012-01-01", null)).let { listOfNotNull(it.at("2013-01-01")) })
    }

    @Test
    fun theBallotsOfEachLegislatureWithTheDistributionPerParty() {
        val reference = AssembleeHistory.reference(sources.anHistoryActors())
        val xiv = AssembleeHistory.ballots(14, sources.anHistoryBallots(14), reference, example = true)
        assertEquals(Chamber.AN, xiv.chamber)
        assertEquals(14, xiv.period)
        assertEquals(listOf("an:14:9001", "an:14:9002", "an:14:9003"), xiv.ballots.map { it.id })
        // The official file stops on 24 November 2016: the coverage says so.
        assertEquals("2012-07-10", xiv.coverage!!.first)
        assertEquals("2016-11-24", xiv.coverage!!.last)
        val first = xiv.ballots[0]
        assertEquals(listOf("an:PO910101", "an:PO910102"), first.parties.map { it.party })
        assertTrue(first.parties.all { it.basis == PartyBasis.GROUP })
        assertEquals(listOf(2, 0, 0, 0, 2), line(first.of("an:PO910101")))
        assertEquals(listOf(0, 1, 1, 1, 3), line(first.of("an:PO910102")))
        assertEquals("an-dossier:DLR5L14N91001", first.dossier)
        assertEquals(true, first.adopted)
        assertEquals("SPO", first.kind)
        assertEquals("https://www.assemblee-nationale.fr/dyn/14/scrutins/9001", first.url)
        assertEquals(listOf(2, 1, 1), listOf(first.counts.inFavour, first.counts.against, first.counts.abstention))
        val second = xiv.ballots[1]
        assertEquals(listOf(1, 1, 0, 0, 2), line(second.of("an:PO910201")))
        assertEquals(listOf(0, 2, 0, 0, 2), line(second.of("an:PO910202")))
        assertEquals(listOf(0, 0, 1, 0, 1), line(second.of("an:PO910102")))
        assertEquals(PartyBasis.GROUP, second.of("an:PO910102").basis)
        assertEquals(false, second.adopted)
        // A party with members of whom none voted still has its line.
        assertEquals(listOf(0, 0, 0, 0, 2), line(xiv.ballots[2].of("an:PO910202")))
        val parties = xiv.parties.associateBy { it.id }
        assertEquals(listOf("an:PO910101", "an:PO910102", "an:PO910201", "an:PO910202"), xiv.parties.map { it.id })
        assertEquals("[EXEMPLE] Parti des Collines", parties.getValue("an:PO910201").label)
        assertEquals("PCO", parties.getValue("an:PO910201").abbreviation)
        assertEquals(PartyBasis.PARTY, parties.getValue("an:PO910201").basis)
        assertNull(parties.getValue("an:PO910201").basisLabel)
        assertEquals(ParliamentHistory.GROUP_BASIS_LABEL, parties.getValue("an:PO910102").basisLabel)

        val xv = AssembleeHistory.ballots(15, sources.anHistoryBallots(15), reference, example = true)
        val censure = xv.ballots.single { it.id == "an:15:9002" }
        assertEquals("MOC", censure.kind)
        assertEquals(listOf(0, 1, 0, 0, 1), line(censure.of("an:PO910201")))
        assertEquals(listOf(2, 0, 0, 0, 2), line(censure.of("an:PO910202")))
        assertEquals(listOf(0, 0, 1, 0, 1), line(censure.of("an:PO910102")))
        assertEquals(listOf(1, 2), listOf(16, 17).map { AssembleeHistory.ballots(it, sources.anHistoryBallots(it), reference, true).ballots.size })
        // A file of another legislature is refused.
        assertFailsWith<UnreadableSourceException> { AssembleeHistory.ballots(16, sources.anHistoryBallots(15), reference, true) }
    }

    // ------------------------------------------------------------ Sénat

    @Test
    fun theDumpOfDoslegIsReadAsTextOnly() {
        assertEquals("scr" to listOf("sesann", "scrnum", "scrint"), SenatHistory.copyHeader("COPY scr (sesann, scrnum, scrint) FROM stdin;"))
        assertEquals("votsen" to listOf("sesann", "senmat"), SenatHistory.copyHeader("COPY public.\"votsen\" (\"sesann\", senmat) FROM stdin;"))
        assertNull(SenatHistory.copyHeader("COPY scr TO '/tmp/x';"))
        assertNull(SenatHistory.copyValue("\\N"))
        assertEquals("a\tb\\c\nd", SenatHistory.copyValue("a\\tb\\\\c\\nd"))
        val sql = "SET client_encoding = 'LATIN1';\nCOPY autre (a) FROM stdin;\nCOPY scr (x) FROM stdin;\n\\.\nCOPY scr (sesann, scrint) FROM stdin;\n2006\tcôte\n\\.\n"
        val rows = ArrayList<String?>()
        SenatHistory.readCopyBlocks(sql.toByteArray(Charsets.ISO_8859_1).inputStream(), setOf("scr")) { table, row -> rows += "$table:${row["sesann"]}:${row["scrint"]}" }
        // The rows of another table (even one looking like a COPY line) are skipped.
        assertEquals(listOf<String?>("scr:2006:côte"), rows)
        assertFailsWith<UnreadableSourceException> { SenatHistory.readCopyBlocks("COPY scr (a) FROM stdin;\n1\n".byteInputStream(), setOf("scr")) { _, _ -> } }
    }

    @Test
    fun theGroupsOfTheSenatorsOverTime() {
        assertEquals("2018-07-01", SenatHistory.csvDay("01/07/2018"))
        assertEquals("2018-07-01", SenatHistory.csvDay("01/07/2018 00:00:00"))
        assertEquals("2018-07-01", SenatHistory.csvDay("2018-07-01"))
        assertNull(SenatHistory.csvDay(""))
        assertEquals(listOf("a", "b;c", "d\"e", ""), SenatHistory.csvFields("a;\"b;c\";\"d\"\"e\";"))
        val groups = SenatHistory.groups(sources.senatHistoGroups())
        assertEquals(5, groups.bySenator.size)
        assertEquals("EXA", groups.groupAt("99102A", "2018-06-30"))
        assertEquals("EXB", groups.groupAt("99102A", "2018-07-01"))
        assertNull(groups.groupAt("99104A", "2020-10-01"))
        assertEquals(mapOf("EXA" to 1, "EXB" to 2, "RASNAG" to 1), groups.members("2026-10-20"))
        // Latin-1 read as such.
        assertEquals("[EXEMPLE] Réunion administrative des non-inscrits", groups.labels.getValue("RASNAG").first)
        assertFailsWith<UnreadableSourceException> { SenatHistory.groups("\"Nom\";\"Prénom\"\n\"x\";\"y\"\n".byteInputStream(Charsets.ISO_8859_1)) }
    }

    @Test
    fun theBallotsOfEachSessionPerGroup() {
        val packages = SenatHistory.packages(sources.senatDosleg(), SenatHistory.groups(sources.senatHistoGroups()), example = true)
        // Nothing before the session 2006-2007.
        assertEquals(listOf(2006, 2018, 2026), packages.map { it.period })
        assertTrue(packages.all { it.chamber == Chamber.SENAT })
        val first = packages[0].ballots.single()
        assertEquals("senat:2006:9001", first.id)
        assertEquals("2006-11-15", first.date)
        assertEquals("[EXEMPLE] l'ensemble du projet de loi relatif aux sentiers côtiers", first.title)
        assertEquals(true, first.adopted)
        assertEquals(listOf(330, 320, 200, 120, 10), listOf(first.counts.voters, first.counts.expressed, first.counts.inFavour, first.counts.against, first.counts.abstention))
        assertEquals(listOf(2, 0, 0, 0, 2), line(first.of("senat-grp:EXA")))
        assertEquals(listOf(0, 1, 0, 0, 1), line(first.of("senat-grp:EXB")))
        assertEquals("https://www.senat.fr/scrutin-public/2006/scr2006-9001.html", first.url)
        val session2018 = packages[1]
        assertEquals(listOf("senat:2018:9001", "senat:2018:9002"), session2018.ballots.map { it.id })
        assertEquals("2019-02-05", session2018.coverage!!.last)
        val amendment = session2018.ballots[0]
        assertEquals(false, amendment.adopted)
        assertTrue('\t' in amendment.title)
        assertEquals(listOf(1, 0, 0, 0, 1), line(amendment.of("senat-grp:EXA")))
        assertEquals(listOf(0, 2, 1, 0, 3), line(amendment.of("senat-grp:EXB")))
        assertEquals(listOf(2, 0, 0, 1, 3), line(session2018.ballots[1].of("senat-grp:EXB")))
        val last = packages[2].ballots.single()
        assertEquals(listOf(0, 0, 1, 0, 1), line(last.of("senat-grp:RASNAG")))
        // Always the group, never a party: the Sénat publishes none.
        assertTrue(packages.flatMap { it.parties }.all { it.basis == PartyBasis.GROUP && it.basisLabel == ParliamentHistory.GROUP_BASIS_LABEL })
        assertTrue(packages.flatMap { p -> p.ballots.flatMap { it.parties } }.all { it.basis == PartyBasis.GROUP })
        assertEquals("[EXEMPLE] Groupe des Estuaires", packages[1].parties.single { it.id == "senat-grp:EXB" }.label)
    }

    // ------------------------------------------------------------ Parlement européen

    @Test
    fun theRollCallVotesPerNationalPartyOfTheMembersElectedInFrance() {
        val packages = EuroparlHistory.packages(sources.europarl(), sources.today, example = true)
        assertEquals(listOf(9, 10), packages.map { it.period })
        val ninth = packages[0]
        // The sitting before 15 July 2019 and the vote by show of hands are not imported.
        assertEquals(listOf("pe:2021-03-10:1", "pe:2021-03-10:2"), ninth.ballots.map { it.id })
        val report = ninth.ballots[0]
        assertEquals("A9-0001/2021", report.text)
        assertEquals("appel-nominal", report.kind)
        assertEquals(listOf(455, 180, 50), listOf(report.counts.inFavour, report.counts.against, report.counts.abstention))
        // The party at the date of the vote (changed on 1 February 2021), or none.
        assertEquals(listOf(1, 0, 0, 0, 1), line(report.of("pe-org:990102")))
        assertEquals(listOf(1, 0, 0, 0, 1), line(report.of("pe-org:990104")))
        assertEquals(listOf(0, 1, 0, 0, 1), line(report.of("pe-org:aucun")))
        assertFalse(report.parties.any { it.party == "pe-org:990101" })
        assertEquals(listOf(0, 0, 1, 0, 1), line(ninth.ballots[1].of("pe-org:990104")))
        assertEquals("https://www.europarl.europa.eu/doceo/document/PV-9-2021-03-10-RCV_FR.html", report.url)
        assertTrue(ninth.parties.all { it.basis == PartyBasis.PARTY })
        assertEquals("[EXEMPLE] Mouvement des Villages", ninth.parties.single { it.id == "pe-org:990102" }.label)
        assertEquals("MDV", ninth.parties.single { it.id == "pe-org:990102" }.abbreviation)
        val tenth = packages[1]
        assertEquals(listOf("pe:2026-09-16:1", "pe:2026-09-16:2", "pe:2026-10-21:1", "pe:2026-10-21:2"), tenth.ballots.map { it.id })
        val amendment = tenth.ballots[1]
        // Members on no list of the roll call did not vote.
        assertEquals(listOf(0, 1, 0, 0, 1), line(amendment.of("pe-org:990101")))
        assertEquals(listOf(0, 0, 1, 0, 1), line(amendment.of("pe-org:990102")))
        assertEquals(listOf(0, 0, 0, 1, 1), line(tenth.ballots[3].of("pe-org:990102")))
    }

    // ------------------------------------------------------------ The whole import

    private fun folder(): Path = Files.createTempDirectory("ppf-history-test")

    private fun import(out: Path, source: ImportSources = ExampleSources(), only: Set<SourceId> = SourceId.entries.toSet(), history: Set<HistorySourceId> = HistorySourceId.entries.toSet()) =
        Importer(source, keys, out, only, history, log = {}).run()

    private fun snapshot(out: Path): Map<String, ByteArray> = out.listDirectoryEntries().sortedBy { it.name }.associate { it.name to Files.readAllBytes(it) }

    private fun assertSame(expected: Map<String, ByteArray>, actual: Map<String, ByteArray>) {
        assertEquals(expected.keys, actual.keys)
        expected.forEach { (name, bytes) -> assertContentEquals(bytes, actual.getValue(name), name) }
    }

    private fun index(out: Path) = assertIs<IndexReading.Accepted>(ParliamentIndexReader.read(SignedEvent.fromJson(out.resolve(Importer.INDEX_FILE).readText()), trust)).index

    private val historyNames = listOf(
        "historique-an-14", "historique-an-15", "historique-an-16", "historique-an-17",
        "historique-pe-10", "historique-pe-9",
        "historique-senat-2006", "historique-senat-2018", "historique-senat-2026",
    )

    @Test
    fun theHistoryPackagesAreListedInTheSignedIndex() {
        val out = folder()
        assertEquals(ExitCodes.OK, import(out))
        val index = index(out)
        assertEquals(listOf("an", "senat", "pe", "an-history", "senat-history", "pe-history"), index.sources.map { it.id })
        assertEquals(historyNames, index.packages.map { it.name }.filter { it.startsWith(ParliamentHistory.PREFIX) })
        index.packages.forEach { ref -> assertTrue(ParliamentIndexReader.matches(ref, Files.readAllBytes(out.resolve(ref.name + ".json"))), ref.name) }
        historyNames.forEach { name ->
            val pkg = ParliamentHistory.read(name, out.resolve("$name.json").readText())
            assertTrue(pkg != null && pkg.example, name)
        }
        val xiv = ParliamentHistory.read("historique-an-14", out.resolve("historique-an-14.json").readText())!!
        assertEquals("2016-11-24", xiv.coverage!!.last)
    }

    @Test
    fun noPersonIsNamedInTheHistory() {
        val out = folder()
        assertEquals(ExitCodes.OK, import(out))
        val text = historyNames.joinToString("\n") { out.resolve("$it.json").readText() }
        listOf(
            // Names, first names and identifiers of the fictional people of the history files.
            "Armelle", "Kerjean", "Benoît", "Larrieu", "Capucine", "Roussel", "Marsan", "Estelle", "Guérin", "Firmin", "Aubrac", "Gaston",
            "Hélène", "Castagnet", "Ignace", "Daubrée", "Escoffier", "Fabrègue", "Léonie", "Gaubert",
            "Maëlle", "Ourcival", "Prunières", "Solène", "Marivaux", "Quitterie", "Bellocq", "Romain", "Taillefer",
            "PA9", "99101A", "99102A", "99103A", "99104A", "99105A", "990011", "990012", "990001", "990002", "990003", "person/",
            // Fields that are never kept.
            "1970-01-01", "Profession fictive", "Ville fictive", "Collaborateur", "example.invalid", "#123456", "numPlace", "mandatRef",
            "acteurRef", "\"positions\"", "senmat",
        ).forEach { marker -> assertFalse(marker in text, "found $marker") }
        val keys = Regex("\"([a-zA-Z]+)\":").findAll(text).map { it.groupValues[1] }.toSet()
        val allowed = setOf(
            "example", "chamber", "period", "coverage", "first", "last", "parties", "ballots", "id", "label", "abbreviation", "basis", "basisLabel",
            "date", "title", "kind", "text", "dossier", "committee", "adopted", "counts", "voters", "expressed", "pour", "contre", "abstention",
            "nonVotant", "party", "members", "url",
        )
        assertEquals(emptySet(), keys - allowed)
    }

    @Test
    fun theHistoryIsDeterministicAndKeptByTheDailyImport() {
        val first = folder()
        val second = folder()
        assertEquals(ExitCodes.OK, import(first))
        assertEquals(ExitCodes.OK, import(second))
        val a = snapshot(first)
        assertSame(a - Importer.INDEX_FILE, snapshot(second) - Importer.INDEX_FILE)
        assertEquals(ExitCodes.OK, import(first))
        assertSame(a, snapshot(first))
        // The daily import (no history) keeps the history packages as they are, and the reverse.
        assertEquals(ExitCodes.OK, import(first, history = emptySet()))
        assertSame(a, snapshot(first))
        assertEquals(ExitCodes.OK, import(first, ExampleSources { !it.startsWith("an/") && !it.startsWith("senat/") && !it.startsWith("pe/meps/show") }, only = emptySet()))
        assertSame(a, snapshot(first))
        // A first import of the history alone: no members package, only the history.
        val alone = folder()
        assertEquals(ExitCodes.OK, import(alone, only = emptySet(), history = setOf(HistorySourceId.SENAT)))
        assertEquals(listOf("historique-senat-2006", "historique-senat-2018", "historique-senat-2026"), index(alone).packages.map { it.name })
        assertEquals(listOf("senat-history"), index(alone).sources.map { it.id })
    }

    @Test
    fun aTruncatedHistoryIsRefused() {
        val out = folder()
        assertEquals(ExitCodes.OK, import(out))
        val before = snapshot(out)
        // 3 ballots of 8 missing in the Assemblée.
        val fewer = ExampleSources { it != "an-historique/scrutins-14/Scrutins_XIV.json" }
        assertEquals(ExitCodes.UNREADABLE_SOURCE, import(out, fewer, only = emptySet(), history = setOf(HistorySourceId.AN)))
        assertSame(before, snapshot(out))
        val fewerSenat = ExampleSources { it != "senat-historique/ODSEN_HISTOGROUPES.csv" }
        assertEquals(ExitCodes.UNREADABLE_SOURCE, import(out, fewerSenat, only = emptySet(), history = setOf(HistorySourceId.SENAT)))
        assertSame(before, snapshot(out))
        val fewerVotes = ExampleSources { !it.startsWith("pe/meetings/MTG-PL-2026") }
        assertEquals(ExitCodes.REFUSED_BY_GUARD, import(out, fewerVotes, only = emptySet(), history = setOf(HistorySourceId.PE)))
        assertSame(before, snapshot(out))
    }

    @Test
    fun theCommandLineOfTheHistory() {
        val env = mapOf<String, String>()
        val history = parseOptions(listOf("--history", "--out", "x", "--key", "k", "--source", "official"), env::get).getOrThrow()
        assertEquals(emptySet(), history.only)
        assertEquals(HistorySourceId.entries.toSet(), history.history)
        val some = parseOptions(listOf("--only", "an,pe-history", "--out", "x", "--key", "k", "--source", "official"), env::get).getOrThrow()
        assertEquals(setOf(SourceId.AN), some.only)
        assertEquals(setOf(HistorySourceId.PE), some.history)
        // The daily official import never reads the history; the example set always holds it.
        val daily = parseOptions(listOf("--out", "x", "--key", "k", "--source", "official"), env::get).getOrThrow()
        assertEquals(SourceId.entries.toSet(), daily.only)
        assertEquals(emptySet(), daily.history)
        assertEquals(HistorySourceId.entries.toSet(), parseOptions(listOf("--out", "x", "--key", "k"), env::get).getOrThrow().history)
        assertTrue(parseOptions(listOf("--history", "--history", "--out", "x", "--key", "k"), env::get).isFailure)
        assertTrue(parseOptions(listOf("--only", "an-histoire", "--out", "x", "--key", "k"), env::get).isFailure)
        // The official addresses of the history are on the allowed hosts only.
        (OfficialSources.AN_HISTORY_BALLOTS.values + listOf(OfficialSources.AMO30, OfficialSources.DOSLEG, OfficialSources.HISTOGROUPES)).forEach {
            assertTrue(java.net.URI.create(it).host in ALLOWED_HOSTS && it.startsWith("https://"), it)
        }
        assertEquals(AssembleeHistory.LEGISLATURES.toSet(), OfficialSources.AN_HISTORY_BALLOTS.keys)
    }

    @Test
    fun theHistoryPackagesDecodeWithTheClassesOfTheApplication() {
        val out = folder()
        assertEquals(ExitCodes.OK, import(out))
        historyNames.forEach { name ->
            val pkg: HistoryPackage = ParliamentFormat.json.decodeFromString(HistoryPackage.serializer(), out.resolve("$name.json").readText())
            assertEquals(name, ParliamentFormat.historyPackage(pkg.chamber, pkg.period))
            assertEquals(pkg.ballots.sortedWith(compareBy({ it.date }, { it.id.substringAfterLast(':').toInt() })), pkg.ballots)
            pkg.ballots.forEach { b ->
                assertEquals(b.parties.sortedBy { it.party }, b.parties)
                assertTrue(b.parties.all { it.members >= it.inFavour + it.against + it.abstention + it.notVoting && it.party in pkg.parties.map { p -> p.id } }, b.id)
            }
        }
    }
}
