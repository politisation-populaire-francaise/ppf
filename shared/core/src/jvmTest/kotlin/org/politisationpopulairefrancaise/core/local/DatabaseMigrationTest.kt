package org.politisationpopulairefrancaise.core.local

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.politisationpopulairefrancaise.core.comprendre.LearningProgressStore
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaResponse
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaStore
import org.politisationpopulairefrancaise.core.agir.agenda.ProposalRecord
import org.politisationpopulairefrancaise.core.agir.agenda.ProposalReview
import org.politisationpopulairefrancaise.core.agir.calls.CallResponseStore
import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.elus.ChosenCommune
import org.politisationpopulairefrancaise.core.elus.ElusStore
import org.politisationpopulairefrancaise.core.elus.StoredPackage
import org.politisationpopulairefrancaise.core.groups.GroupStore
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.revendications.RevendicationViewStore
import org.politisationpopulairefrancaise.core.revendications.RevendicationViews
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.social.FollowStore
import org.politisationpopulairefrancaise.core.urne.UrneTokenStore
import org.politisationpopulairefrancaise.core.voir.ContentFollowStore
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The local database of an installed application is migrated, never recreated: a database of
 * version 1 (events only, first builds of November 2026) must gain the tables added since, and
 * keep its rows. The schema of version 1 is frozen here on purpose: it is what those builds wrote.
 */
class DatabaseMigrationTest {

    private val version1Schema = listOf(
        """CREATE TABLE event (
            id TEXT NOT NULL PRIMARY KEY, pubkey TEXT NOT NULL, created_at INTEGER NOT NULL, kind INTEGER NOT NULL,
            tags TEXT NOT NULL, content TEXT NOT NULL, sig TEXT NOT NULL, address TEXT, received_at INTEGER NOT NULL,
            pending INTEGER NOT NULL DEFAULT 0)""",
        "CREATE INDEX event_kind_created_at ON event(kind, created_at DESC, id)",
        "CREATE INDEX event_pubkey_created_at ON event(pubkey, created_at DESC, id)",
        "CREATE UNIQUE INDEX event_address ON event(address)",
        "CREATE INDEX event_pending ON event(pending)",
        "CREATE TABLE event_tag (event_id TEXT NOT NULL, name TEXT NOT NULL, value TEXT NOT NULL)",
        "CREATE INDEX event_tag_lookup ON event_tag(name, value, event_id)",
        "CREATE INDEX event_tag_event ON event_tag(event_id)",
    )

    private val eventId = "a".repeat(64)

    /** Writes a version 1 database holding one event, exactly as the first builds left it. */
    private fun writeVersion1Database(file: Path) {
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}")
        try {
            version1Schema.forEach { driver.execute(null, it, 0) }
            driver.execute(
                null,
                "INSERT INTO event(id, pubkey, created_at, kind, tags, content, sig, address, received_at, pending) " +
                    "VALUES ('$eventId', '${"b".repeat(64)}', 10, 1, '[]', '[EXEMPLE] ancienne note', '${"c".repeat(128)}', NULL, 11, 0)",
                0,
            )
            driver.execute(null, "PRAGMA user_version = 1", 0)
        } finally {
            driver.close()
        }
    }

    /**
     * Writes a version 5 database (the builds with the views of the demands wall, before the joined
     * rooms): the version 1 schema migrated up to 5, with one saved view.
     */
    private fun writeVersion5Database(file: Path) {
        writeVersion1Database(file)
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}")
        try {
            PpfDatabase.Schema.migrate(driver, 1, 5)
            driver.execute(
                null,
                "INSERT INTO revendication_view(id, position, name, query) VALUES ('national', 0, NULL, NULL)",
                0,
            )
            driver.execute(null, "PRAGMA user_version = 5", 0)
        } finally {
            driver.close()
        }
    }

    private fun userVersion(file: Path): Long {
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}")
        try {
            return driver.executeQuery(null, "PRAGMA user_version", { cursor ->
                cursor.next()
                QueryResult.Value(assertNotNull(cursor.getLong(0)))
            }, 0).value
        } finally {
            driver.close()
        }
    }

    private fun tempDatabaseFile(): Path {
        val file = Files.createTempFile("ppf-migration-", ".db")
        Files.delete(file)
        file.toFile().deleteOnExit()
        return file
    }

    @Test
    fun theSchemaIsAtVersion19() {
        // 1: events; 2: follow (1.sqm); 3: messaging (2.sqm); 4: sync cursors (3.sqm);
        // 5: views of the demands wall (4.sqm); 6: joined rooms (5.sqm); 7: progress in the courses (6.sqm);
        // 8: followed contents of Voir (7.sqm); 9: agenda answers and proposals (8.sqm);
        // 10: answered calls (9.sqm); 11: directory of elected officials (10.sqm);
        // 12: parliamentary open data (11.sqm), 13: referential of territories (12.sqm);
        // 14: testimonies of Agir (13.sqm), 15: anonymous voting (14.sqm);
        // 16: notifications and verified contacts (15.sqm, provisional number of Communauté);
        // 17: « Mes élus ont voté » and the followed words of Voir (16.sqm).
        // 18: personal reminders, private notes of the letters and requests for documents of Décider (17.sqm).
        // 19: signups to the slots of calls (18.sqm).
        assertEquals(19L, PpfDatabase.Schema.version)
    }

    @Test
    fun aVersion16DatabaseGainsTheAlertsOfVoirAndKeepsItsFollows() {
        val file = tempDatabaseFile()
        writeVersion1Database(file)
        JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}").let { driver ->
            try {
                PpfDatabase.Schema.migrate(driver, 1, 16)
                driver.execute(null, "INSERT INTO content_follow(address, followed_at) VALUES ('30023:${"b".repeat(64)}:fiche', 3)", 0)
                driver.execute(null, "PRAGMA user_version = 16", 0)
            } finally {
                driver.close()
            }
        }
        val database = JvmDatabase.open(file)
        assertEquals(PpfDatabase.Schema.version, userVersion(file))
        assertEquals(1, org.politisationpopulairefrancaise.core.voir.ContentFollowStore(database).all().size)
        val alerts = org.politisationpopulairefrancaise.core.voir.parlement.VoteAlertStore(database)
        assertTrue(alerts.addWord("retraites", 1))
        assertEquals(listOf("retraites"), alerts.words())

        val fresh = tempDatabaseFile()
        JvmDatabase.open(fresh)
        listOf("parliament_ballot_seen", "followed_word", "parliament_alert_").forEach { prefix ->
            assertEquals(tableDefinitions(fresh, prefix), tableDefinitions(file, prefix), prefix)
        }
        assertEquals(2, tableDefinitions(file, "parliament_alert_").size)
    }

    @Test
    fun aVersion17DatabaseGainsTheRemindersOfDeciderAndKeepsItsCommune() {
        val file = tempDatabaseFile()
        writeVersion1Database(file)
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}")
        try {
            PpfDatabase.Schema.migrate(driver, 1, 17)
            driver.execute(null, "INSERT INTO elus_commune(id, commune_code, department_code) VALUES (0, '69123', '69')", 0)
            driver.execute(null, "PRAGMA user_version = 17", 0)
        } finally {
            driver.close()
        }
        val database = JvmDatabase.open(file)
        assertEquals(PpfDatabase.Schema.version, userVersion(file))
        assertEquals(ChosenCommune("69123", "69"), ElusStore(database).commune())
        val reminders = org.politisationpopulairefrancaise.core.reminders.ReminderStore(database) { 1_800_000_000 }
        assertEquals(null, reminders.nextDueAfter())
        reminders.setPlan("decider:election:exemple", setOf("veille"))
        assertEquals(setOf("veille"), reminders.plan("decider:election:exemple")?.kinds)
        // The migration creates exactly the tables of Reminders.sq, the reference for fresh installations.
        val fresh = tempDatabaseFile()
        JvmDatabase.open(fresh)
        assertEquals(tableDefinitions(fresh, "reminder"), tableDefinitions(file, "reminder"))
        assertEquals(2, tableDefinitions(file, "reminder").size)
        assertEquals(tableDefinitions(fresh, "letter_"), tableDefinitions(file, "letter_"))
        assertEquals(1, tableDefinitions(file, "letter_").size)
        assertEquals(tableDefinitions(fresh, "document_"), tableDefinitions(file, "document_"))
        assertEquals(1, tableDefinitions(file, "document_").size)
    }

    @Test
    fun aVersion12DatabaseGainsTheTerritoriesAndKeepsTheDirectory() {
        val file = tempDatabaseFile()
        writeVersion1Database(file)
        JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}").let { driver ->
            try {
                PpfDatabase.Schema.migrate(driver, 1, 12)
                driver.execute(null, "INSERT INTO elus_commune(id, commune_code, department_code) VALUES (0, '69123', '69')", 0)
                driver.execute(null, "PRAGMA user_version = 12", 0)
            } finally {
                driver.close()
            }
        }
        val database = JvmDatabase.open(file)
        assertEquals(PpfDatabase.Schema.version, userVersion(file))
        assertEquals(ChosenCommune("69123", "69"), ElusStore(database).commune())

        val territoires = org.politisationpopulairefrancaise.core.territoires.TerritoiresStore(database)
        assertEquals(emptyList(), territoires.codes())
        territoires.save(org.politisationpopulairefrancaise.core.territoires.StoredTerritoires("69", "a".repeat(64), "{}", "2026-11-07", 1))
        assertEquals(listOf("69"), territoires.codes())

        val fresh = tempDatabaseFile()
        JvmDatabase.open(fresh)
        assertEquals(tableDefinitions(fresh, "territoire_"), tableDefinitions(file, "territoire_"))
        assertEquals(1, tableDefinitions(file, "territoire_").size)
    }

    /** Writes a version 10 database (the builds with the answered calls, before the directory of elected officials), with one answered call. */
    private fun writeVersion10Database(file: Path) {
        writeVersion1Database(file)
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}")
        try {
            PpfDatabase.Schema.migrate(driver, 1, 10)
            driver.execute(null, "INSERT INTO call_response(address) VALUES ('30023:${"b".repeat(64)}:appel')", 0)
            driver.execute(null, "PRAGMA user_version = 10", 0)
        } finally {
            driver.close()
        }
    }

    /** The CREATE statements of the tables whose name starts with [prefix], as SQLite keeps them. */
    private fun tableDefinitions(file: Path, prefix: String): List<String> {
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}")
        try {
            return driver.executeQuery(null, "SELECT sql FROM sqlite_master WHERE type = 'table' AND name LIKE '$prefix%' ORDER BY name", { cursor ->
                val rows = mutableListOf<String>()
                while (cursor.next().value) rows += assertNotNull(cursor.getString(0)).replace(Regex("\\s+"), " ")
                QueryResult.Value(rows)
            }, 0).value
        } finally {
            driver.close()
        }
    }

    @Test
    fun aVersion10DatabaseGainsTheDirectoryOfElectedOfficialsAndKeepsItsCalls() {
        val file = tempDatabaseFile()
        writeVersion10Database(file)
        assertEquals(10L, userVersion(file))

        val database = JvmDatabase.open(file)
        assertEquals(PpfDatabase.Schema.version, userVersion(file))
        assertEquals(setOf("30023:${"b".repeat(64)}:appel"), CallResponseStore(database).all())
        assertEquals("[EXEMPLE] ancienne note", assertNotNull(LocalEventStore(database).find(eventId)).content)

        val elus = ElusStore(database)
        assertEquals(null, elus.commune())
        elus.saveCommune(ChosenCommune("69123", "69"))
        elus.savePackage(StoredPackage("69", "a".repeat(64), "{}", "2026-10-01", 1))
        assertEquals(ChosenCommune("69123", "69"), elus.commune())
        assertEquals(1L, elus.countPackages())

        // The migration creates exactly the tables of Elus.sq, the reference for fresh installations.
        val fresh = tempDatabaseFile()
        JvmDatabase.open(fresh)
        assertEquals(tableDefinitions(fresh, "elus_"), tableDefinitions(file, "elus_"))
        assertEquals(2, tableDefinitions(file, "elus_").size)
    }

    @Test
    fun aVersion14DatabaseGainsTheVotingTablesAndKeepsItsCommune() {
        val file = tempDatabaseFile()
        writeVersion1Database(file)
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}")
        try {
            PpfDatabase.Schema.migrate(driver, 1, 14)
            driver.execute(null, "INSERT INTO elus_commune(id, commune_code, department_code) VALUES (0, '69123', '69')", 0)
            driver.execute(null, "PRAGMA user_version = 14", 0)
        } finally {
            driver.close()
        }
        val database = JvmDatabase.open(file)
        assertEquals(PpfDatabase.Schema.version, userVersion(file))
        assertEquals(ChosenCommune("69123", "69"), ElusStore(database).commune())
        val votes = UrneTokenStore(database)
        assertEquals(emptyList(), votes.tokens())
        votes.setState("a".repeat(64), registered = true)
        assertEquals("a".repeat(64) to true, votes.state())
        // The migration creates exactly the tables of Vote.sq, the reference for fresh installations.
        val fresh = tempDatabaseFile()
        JvmDatabase.open(fresh)
        assertEquals(tableDefinitions(fresh, "vote_"), tableDefinitions(file, "vote_"))
        assertEquals(3, tableDefinitions(file, "vote_").size)
    }

    /** Writes a version 6 database (the builds with the joined rooms, before the answered calls), with one joined room. */
    private fun writeVersion6Database(file: Path) {
        writeVersion1Database(file)
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}")
        try {
            PpfDatabase.Schema.migrate(driver, 1, 6)
            driver.execute(null, "INSERT INTO group_membership(group_id, joined_at) VALUES ('collectif-demonstration', 12)", 0)
            driver.execute(null, "PRAGMA user_version = 6", 0)
        } finally {
            driver.close()
        }
    }

    @Test
    fun aVersion6DatabaseGainsTheAnsweredCallsAndKeepsItsRoomsAndEvents() {
        val file = tempDatabaseFile()
        writeVersion6Database(file)
        assertEquals(6L, userVersion(file))

        val database = JvmDatabase.open(file)
        assertEquals(PpfDatabase.Schema.version, userVersion(file))

        // The rows written at version 6 survive.
        assertEquals("[EXEMPLE] ancienne note", assertNotNull(LocalEventStore(database).find(eventId)).content)
        assertTrue(GroupStore(database).isMember("collectif-demonstration"))

        // The new table starts empty and works.
        val answers = CallResponseStore(database)
        assertEquals(0L, answers.count())
        val address = "30023:${"b".repeat(64)}:assemblee-quartier-3f9a"
        answers.respond(address)
        answers.respond(address)
        assertEquals(setOf(address), answers.all())
        answers.withdraw(address)
        assertEquals(0L, answers.count())
    }

    @Test
    fun aVersion1DatabaseGainsTheFollowTableAndKeepsItsEvents() {
        val file = tempDatabaseFile()
        writeVersion1Database(file)
        assertEquals(1L, userVersion(file))

        val database = JvmDatabase.open(file)
        assertEquals(PpfDatabase.Schema.version, userVersion(file))

        // The new table works, and the old rows are still there.
        val follows = FollowStore(database)
        assertEquals(0L, follows.count())
        val key = IdentityKeys.generate().publicKey.hex
        follows.follow(key)
        assertTrue(follows.isFollowed(key))
        val events = LocalEventStore(database)
        assertEquals(1L, events.count())
        assertEquals("[EXEMPLE] ancienne note", assertNotNull(events.find(eventId)).content)

        // So does the table of the views of the demands wall (version 5).
        val views = RevendicationViewStore(database)
        assertEquals(null, views.layout())
        views.save(listOf(RevendicationViews.ALL, RevendicationViews.NATIONAL))
        assertEquals(listOf(RevendicationViews.ALL, RevendicationViews.NATIONAL), views.layout())

        // And the table of the joined rooms (version 6).
        val rooms = GroupStore(database)
        assertEquals(0L, rooms.count())
        rooms.join("collectif-demonstration")
        assertTrue(rooms.isMember("collectif-demonstration"))

        // And the progress in the courses of "Comprendre" (version 7).
        val learning = LearningProgressStore(database)
        assertEquals(0L, learning.count())
        learning.reachStep("30023:abc:la-dissolution", 1, now = 10)
        assertEquals(1, assertNotNull(learning.progress("30023:abc:la-dissolution")).furthestStep)

        // And the table of the followed contents of Voir (version 8).
        val followedContents = ContentFollowStore(database)
        val address = ContentAddress.parse("30023:$key:manifestants-journees")
        followedContents.follow(address)
        assertEquals(listOf(address), followedContents.all())
        // And the tables of the agenda (version 9).
        val agenda = AgendaStore(database)
        assertEquals(0L, agenda.countResponses())
        agenda.respond("31923:${"b".repeat(64)}:rassemblement", AgendaResponse.GOING, endsAt = 100)
        assertEquals(1L, agenda.countResponses())
    }

    @Test
    fun aVersion6DatabaseGainsTheAgendaTablesAndKeepsItsRooms() {
        val file = tempDatabaseFile()
        writeVersion1Database(file)
        JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}").let { driver ->
            try {
                PpfDatabase.Schema.migrate(driver, 1, 6)
                driver.execute(null, "INSERT INTO group_membership(group_id, joined_at) VALUES ('collectif-demonstration', 1)", 0)
                driver.execute(null, "PRAGMA user_version = 6", 0)
            } finally {
                driver.close()
            }
        }
        assertEquals(6L, userVersion(file))

        val database = JvmDatabase.open(file)
        assertEquals(PpfDatabase.Schema.version, userVersion(file))
        assertTrue(GroupStore(database).isMember("collectif-demonstration"))
        assertEquals("[EXEMPLE] ancienne note", assertNotNull(LocalEventStore(database).find(eventId)).content)

        val agenda = AgendaStore(database)
        assertEquals(0L, agenda.countResponses())
        assertEquals(0L, agenda.countProposals())
        agenda.respond("31923:${"b".repeat(64)}:rassemblement", AgendaResponse.INTERESTED, endsAt = 100)
        agenda.recordProposal("e".repeat(64), ProposalRecord(ProposalReview.REVIEW))
        assertEquals(AgendaResponse.INTERESTED, agenda.responses().values.single())
        assertEquals(ProposalReview.REVIEW, agenda.proposals().values.single().review)
        // And the table of the answered calls (version 10).
        val answers = CallResponseStore(database)
        assertEquals(0L, answers.count())
        answers.respond("30023:${"b".repeat(64)}:appel")
        assertEquals(1L, answers.count())
    }

    @Test
    fun aVersion5DatabaseGainsTheJoinedRoomsAndKeepsItsViewsAndEvents() {
        val file = tempDatabaseFile()
        writeVersion5Database(file)
        assertEquals(5L, userVersion(file))

        val database = JvmDatabase.open(file)
        assertEquals(PpfDatabase.Schema.version, userVersion(file))

        // The rows written at version 5 survive.
        assertEquals("[EXEMPLE] ancienne note", assertNotNull(LocalEventStore(database).find(eventId)).content)
        assertEquals(1L, RevendicationViewStore(database).count())

        // The new table starts empty and works.
        val rooms = GroupStore(database)
        assertEquals(0L, rooms.count())
        rooms.join("collectif-demonstration")
        rooms.join("syndicat-demonstration")
        assertEquals(listOf("syndicat-demonstration", "collectif-demonstration").toSet(), rooms.all().toSet())
        rooms.leave("collectif-demonstration")
        assertFalse(rooms.isMember("collectif-demonstration"))
    }

    @Test
    fun aFreshDatabaseHasEveryTableAtTheLatestVersion() {
        val database = JvmDatabase.inMemory()
        assertEquals(0L, FollowStore(database).count())
        assertEquals(0L, LocalEventStore(database).count())
        assertEquals(0L, RevendicationViewStore(database).count())
        assertEquals(0L, GroupStore(database).count())
        val followed = ContentFollowStore(database)
        assertEquals(emptyList(), followed.all())
        val address = ContentAddress.parse("30023:${IdentityKeys.generate().publicKey.hex}:recours-49-3")
        followed.follow(address)
        followed.follow(address)
        assertEquals(listOf(address), followed.all())
        followed.unfollow(address)
        assertEquals(emptyList(), followed.all())
        assertEquals(0L, AgendaStore(database).countResponses())
        assertEquals(0L, AgendaStore(database).countProposals())
        assertEquals(0L, CallResponseStore(database).count())
        assertEquals(0L, ElusStore(database).countPackages())
        assertEquals(null, ElusStore(database).commune())
        assertEquals(emptyList(), UrneTokenStore(database).tokens())
    }
}
