package org.politisationpopulairefrancaise.core.voir.parlement

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.elus.ElectedOfficial
import org.politisationpopulairefrancaise.core.elus.Mandate
import org.politisationpopulairefrancaise.core.notifications.NotificationCategory
import org.politisationpopulairefrancaise.core.time.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * One round of « Mes élus ont voté » (VO-34, D-102): nothing asked in data saver mode or on a metered network
 * by default, only the packages of the current and the previous month of my assemblies, a first round that
 * tells nothing, then each new ballot told once in one summary, and the followed words found on the device.
 */
class VoteAlertRoundTest {
    private val keys = ParliamentFixtures.Keys()
    private val deputy = ElectedOfficial(Mandate.DEPUTE, "6901", null, "[EXEMPLE] VASSEUR Zoé", null, null, null)

    /** My deputy is the member an:PA900002 of the fixtures, matched in the catalog given. */
    private val myDeputy: (ParliamentCatalog, Set<String>) -> MyParliamentarians? = { catalog, europeans ->
        MyParliamentarians(listOf(MyParliamentarian(deputy, catalog.member("an:PA900002"))), emptyList(), europeans.mapNotNull(catalog::member))
    }

    private var network = NetworkConditions(dataSaver = false, unmetered = true)
    private var now = ParliamentFixtures.NOW

    private suspend fun server() = ParliamentFixtures.packages().let { ParliamentFixtures.Server(keys.sign(ParliamentFixtures.index(it)), it) }

    private fun round(server: ParliamentFixtures.Server, storage: VoteAlertStorage, mine: (ParliamentCatalog, Set<String>) -> MyParliamentarians? = myDeputy) =
        VoteAlertRound(
            ParliamentRepository(server, InMemoryParliamentStorage(), trust = keys.trust, clock = { now }),
            storage,
            mine,
            network = { network },
            clock = { now },
        )

    /** A new ballot in November where my deputy votes for, published with a newer index. */
    private suspend fun publishNewBallot(server: ParliamentFixtures.Server, title: String = "[EXEMPLE] le projet de loi sur les retraites") {
        val ballot = Ballot(
            id = "an:17:104", number = 104, date = "2026-11-06", title = title, kind = "SPS", adopted = true,
            counts = Counts(voters = 1, expressed = 1, inFavour = 1, against = 0, abstention = 0),
            positions = Positions(inFavour = listOf("an:PA900002")),
        )
        val november = ParliamentFixtures.novembre.copy(ballots = ParliamentFixtures.novembre.ballots + ballot)
        val packages = ParliamentFixtures.packages() + ("scrutins-an-2026-11" to ParliamentFixtures.json(november))
        server.packages = packages
        server.indexEvent = keys.sign(ParliamentFixtures.index(packages, importedOn = "2026-11-08"), ParliamentFixtures.NOW + 86_400)
        now = ParliamentFixtures.NOW + 86_400 + 60
    }

    @Test
    fun nothingIsAskedInDataSaverModeNorOnAMeteredNetworkWhileWifiOnlyIsOn() = runTest {
        val server = server()
        val storage = InMemoryVoteAlertStorage()
        val round = round(server, storage)
        assertTrue(storage.wifiOnly(), "« seulement en Wi-Fi » is on by default (D-102)")
        network = NetworkConditions(dataSaver = true, unmetered = true)
        assertNull(round.run())
        network = NetworkConditions(dataSaver = false, unmetered = false)
        assertNull(round.run())
        assertTrue(server.requests.isEmpty(), "not even the index: ${server.requests}")

        // The person allows the mobile network: the round runs.
        storage.setWifiOnly(false)
        assertNotNull(round.run())
        assertTrue(server.requests.isNotEmpty())
        // Data saver mode always wins.
        server.requests.clear()
        network = NetworkConditions(dataSaver = true, unmetered = false)
        assertNull(round.run())
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun theFirstRoundDownloadsTheRecentMonthsOfMyAssembliesAndTellsNothing() = runTest {
        val server = server()
        val storage = InMemoryVoteAlertStorage()
        val found = assertNotNull(round(server, storage).run())
        assertTrue(found.first && found.isEmpty)
        assertEquals(listOf(ParliamentFormat.MEMBERS_PACKAGE, "scrutins-an-2026-10", "scrutins-an-2026-11"), found.downloaded.sorted())
        // Whole packages by name only: the server learns nothing of the person.
        assertEquals(listOf(null, ParliamentFormat.MEMBERS_PACKAGE, "scrutins-an-2026-10", "scrutins-an-2026-11"), server.requests.sortedBy { it ?: "" })
        assertEquals(setOf("an:17:101", "an:17:102", "an:17:103"), storage.seen())
        assertEquals(ParliamentFixtures.NOW, storage.startedAt())
        assertTrue(VoteAlertSource.candidatesOf(found).isEmpty())
    }

    @Test
    fun aNewBallotOfMyDeputyIsToldOnceInOneSummary() = runTest {
        val server = server()
        val storage = InMemoryVoteAlertStorage()
        val round = round(server, storage)
        round.run()
        publishNewBallot(server)

        val found = assertNotNull(round.run())
        assertFalse(found.first)
        assertEquals(listOf("scrutins-an-2026-11"), found.downloaded, "only the package that changed")
        val alert = found.votes.single()
        assertEquals("an:17:104", alert.entry.ballot.id)
        assertEquals("an:PA900002", alert.votes.single().member.id)
        val candidate = VoteAlertSource.candidatesOf(found).single()
        assertEquals(NotificationCategory.VOTES, candidate.category)
        assertEquals(VoteAlertCodes.MY_OFFICIALS, candidate.title)
        assertEquals("1", candidate.detail)
        assertEquals(VoteAlertCodes.TARGET, candidate.target)
        assertEquals(now, candidate.createdAt, "dated by its reception on the device")

        // Told once: the next round finds nothing and downloads nothing.
        now += 3600
        val again = assertNotNull(round.run())
        assertTrue(again.isEmpty && again.downloaded.isEmpty())
        assertTrue(VoteAlertSource.candidatesOf(again).isEmpty())
    }

    @Test
    fun aFollowedWordFindsANewBallotWithoutAnyOfficialChosen() = runTest {
        val server = server()
        val storage = InMemoryVoteAlertStorage()
        assertTrue(storage.addWord("Retraite", ParliamentFixtures.NOW))
        val round = round(server, storage, mine = { _, _ -> null })
        assertTrue(assertNotNull(round.run()).first)
        publishNewBallot(server)

        val found = assertNotNull(round.run())
        assertTrue(found.votes.isEmpty())
        assertEquals("Retraite", found.words.single().word)
        val candidate = VoteAlertSource.candidatesOf(found).single()
        assertEquals(VoteAlertCodes.WORD, candidate.title)
        assertEquals("Retraite", candidate.detail)
        // The word never leaves the device.
        assertTrue(server.requests.none { it.orEmpty().contains("retraite", ignoreCase = true) }, "${server.requests}")
    }

    @Test
    fun withoutCommuneNorWordTheRoundDownloadsNoPackage() = runTest {
        val server = server()
        val storage = InMemoryVoteAlertStorage()
        assertNull(round(server, storage, mine = { _, _ -> null }).run())
        assertTrue(server.requests.all { it == null || it == ParliamentFormat.MEMBERS_PACKAGE }, "${server.requests}")
    }

    @Test
    fun theRecentMonthsCrossTheYear() {
        val january = CivilDate(2027, 1, 15).toEpochDays() * 86_400
        assertEquals(setOf("2027-01", "2026-12"), VoteAlertRound.recentMonths(january))
        assertEquals(setOf("2026-11", "2026-10"), VoteAlertRound.recentMonths(ParliamentFixtures.NOW))
    }

    @Test
    fun theStorageKeepsFiftyDistinctWordsAndForgetsEverything() {
        val storage = InMemoryVoteAlertStorage()
        assertTrue(storage.addWord(" Retraites ", 1))
        assertFalse(storage.addWord("RETRAITES", 2), "accents and case ignored")
        assertFalse(storage.addWord("  ", 3))
        (2..VoteAlerts.MAX_FOLLOWED_WORDS).forEach { assertTrue(storage.addWord("mot$it", it.toLong())) }
        assertFalse(storage.addWord("un de trop", 99))
        assertEquals("Retraites", storage.words().first())
        storage.removeWord("retraites")
        assertEquals(VoteAlerts.MAX_FOLLOWED_WORDS - 1, storage.words().size)
        storage.setWifiOnly(false)
        storage.setEuropeans(setOf("pe:990001"))
        storage.markSeen(listOf("an:17:1"), 5)
        storage.start(5)
        storage.forget()
        assertTrue(storage.words().isEmpty() && storage.seen().isEmpty() && storage.europeans().isEmpty())
        assertNull(storage.startedAt())
        assertTrue(storage.wifiOnly())
    }
}
