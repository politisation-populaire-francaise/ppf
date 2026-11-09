package org.politisationpopulairefrancaise.server.backoffice

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeTestData.member
import org.politisationpopulairefrancaise.server.signing.FakeSignerAdmin
import org.politisationpopulairefrancaise.server.signing.SignerJournalHead
import org.politisationpopulairefrancaise.server.signing.SignerJournalLine
import java.time.Instant
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The copy of the journal of the signing service into the journal of the team (D-141). */
class SignerJournalCopyTest {
    private val clock = MutableClock(Instant.now())

    /** The journal of the team is shared by every test of the run: each test copies under its own target type. */
    private val target = "signer_journal_test_" + UUID.randomUUID().toString().replace("-", "_")

    @BeforeTest
    fun emptyTables() = BackOfficeTestData.reset()

    private val signer = FakeSignerAdmin(now = { clock.instant().epochSecond })
    private val backOffice = BackOffice(config = BackOfficeTestData.CONFIG, dataSource = TestDatabase.dataSource, clock = clock, signer = signer)

    private fun copy() = SignerJournalCopy(backOffice, signer, target)

    private fun copied(): List<AuditRecord> = BackOfficeTestData.audit.query(AuditFilter(targetType = target), AuditLog.MAX_PAGE).sortedBy { it.seq }

    @Test
    fun everyLineIsCopiedOnceInOrderAndARestartedCopyContinues() = runBlocking<Unit> {
        signer.append("service.started")
        signer.append("draft.submitted", use = "editorial.voir", event = "a".repeat(64))
        signer.append("approval.recorded", use = "editorial.voir", event = "a".repeat(64), approvers = listOf("alice"))
        assertEquals(SignerJournalCopy.Pass.Copied(3, 3), copy().runOnce())
        val lines = copied()
        assertEquals(listOf("signer.service.started", "signer.draft.submitted", "signer.approval.recorded"), lines.map { it.action })
        lines.zip(signer.lines).forEach { (record, line) ->
            assertEquals(line.seq.toString(), record.targetId)
            assertEquals(line.seq.toString(), record.details["seq"])
            assertEquals(line.hash, record.details["hash"])
            assertEquals(line.previousHash, record.details["previous_hash"])
            assertEquals(null, record.actorId)
            assertEquals(BackOfficeTexts.ACTOR_SYSTEM, record.actorLabel)
        }
        assertEquals("alice", lines.last().details["accounts"])
        assertEquals("editorial.voir", lines.last().details["use"])
        assertEquals("a".repeat(64), lines.last().details["event"])
        // Nothing new: nothing copied; a new copy (after a restart) continues from the last line copied.
        assertEquals(SignerJournalCopy.Pass.Copied(0, 3), copy().runOnce())
        signer.append("sign.signed", use = "editorial.voir", event = "a".repeat(64), approvers = listOf("alice"))
        signer.append("key.created", use = "opendata.index")
        assertEquals(SignerJournalCopy.Pass.Copied(2, 5), copy().runOnce())
        assertEquals((1L..5L).map { it.toString() }, copied().map { it.details["seq"] })
        // More than a page: everything, in order.
        repeat(SignerJournalCopy.PAGE + 5) { signer.append("sign.refused", use = "editorial.voir") }
        assertEquals(SignerJournalCopy.Pass.Copied(SignerJournalCopy.PAGE + 5, SignerJournalCopy.PAGE + 10L), copy().runOnce())
    }

    @Test
    fun aBreakRaisesTheHighestAlertAndStopsTheCopyUntilAnAdministratorTreatsIt() = runBlocking<Unit> {
        val admin = member(backOffice, "Admin", listOf(TeamRole.ADMIN))
        val editor = member(backOffice, "Éditrice", listOf(TeamRole.EDITOR))
        repeat(3) { signer.append("sign.signed", use = "editorial.voir") }
        copy().runOnce()
        // A line that does not follow the last one copied (the journal of the service was rewritten).
        signer.lines += SignerJournalLine(4, clock.instant().epochSecond, "sign.signed", previousHash = "f".repeat(64), hash = "e".repeat(64))
        assertEquals(SignerJournalCopy.Pass.Broken("previous_hash"), copy().runOnce())
        val mark = copied().last()
        assertEquals(SignerJournalCopy.ACTION_BREAK, mark.action)
        assertEquals("3", mark.details["after_seq"])
        assertEquals(signer.lines[2].hash, mark.details["after_hash"])
        assertEquals("4", mark.details["found_seq"])
        val alert = backOffice.dashboard(BackOfficeTestData.auth(backOffice, admin).actor).alerts.single { it.source == SignerJournalCopy.ALERT_SOURCE }
        assertEquals(5, alert.level)
        assertEquals(Permissions.PLATFORM_KEYS, alert.permission)
        assertEquals("/plateforme", alert.link)
        assertEquals(alert.id.toString(), mark.details["alert"])
        assertTrue(backOffice.dashboard(BackOfficeTestData.auth(backOffice, editor).actor).alerts.none { it.source == SignerJournalCopy.ALERT_SOURCE })
        // Stopped: nothing more is copied, the alert stays alone.
        signer.append("sign.signed")
        assertEquals(SignerJournalCopy.Pass.Halted(alert.id), copy().runOnce())
        assertEquals(SignerJournalCopy.ACTION_BREAK, copied().last().action)
        // The Directeur technique starts a new journal (procedure of the contract); an administrator treats the alert.
        assertIs<Outcome.Ok<Unit>>(backOffice.dismissAlert(BackOfficeTestData.auth(backOffice, admin), alert.id))
        signer.lines.clear()
        signer.append("service.started")
        signer.append("key.created", use = "editorial.voir")
        assertEquals(SignerJournalCopy.Pass.Copied(2, 2), copy().runOnce())
        val resumed = copied().single { it.action == SignerJournalCopy.ACTION_RESUMED }
        assertEquals("0", resumed.details["after_seq"])
        assertEquals("0".repeat(64), resumed.details["previous_hash"])
        assertEquals(listOf("signer.service.started", "signer.key.created"), copied().takeLast(2).map { it.action })
        assertEquals(SignerJournalCopy.Pass.Copied(0, 2), copy().runOnce())
    }

    @Test
    fun aJournalRewrittenFromALineGoesOnFromThatLineOnceTreated() = runBlocking<Unit> {
        val admin = member(backOffice, "Admin", listOf(TeamRole.ADMIN))
        repeat(3) { signer.append("sign.signed") }
        copy().runOnce()
        // The head of the service names the same last line with another hash: the end was rewritten.
        signer.head = SignerJournalHead(3, "e".repeat(64))
        assertEquals(SignerJournalCopy.Pass.Broken("head_hash"), copy().runOnce())
        val alert = assertNotNull(copied().last().details["alert"]).let(UUID::fromString)
        backOffice.dismissAlert(BackOfficeTestData.auth(backOffice, admin), alert)
        // Line 3 as the service now shows it, then a new line: the copy takes line 3 as it is now and goes on.
        signer.head = null
        val rewritten = signer.lines[2].copy(hash = "d".repeat(64))
        signer.lines[2] = rewritten
        signer.append("sign.signed")
        assertEquals(SignerJournalCopy.Pass.Copied(1, 4), copy().runOnce())
        assertEquals("3", copied().single { it.action == SignerJournalCopy.ACTION_RESUMED }.details["after_seq"])
        assertEquals("d".repeat(64), copied().single { it.action == SignerJournalCopy.ACTION_RESUMED }.details["previous_hash"])
    }

    @Test
    fun aHeadBelowTheCopyIsABreakAndAnUnreachableServiceChangesNothing() = runBlocking<Unit> {
        repeat(3) { signer.append("sign.signed") }
        copy().runOnce()
        signer.head = SignerJournalHead(1, signer.lines[0].hash)
        assertEquals(SignerJournalCopy.Pass.Broken("head_below_copy"), copy().runOnce())
        val other = "signer_journal_test_" + UUID.randomUUID().toString().replace("-", "_")
        signer.available = false
        assertEquals(SignerJournalCopy.Pass.Unavailable, SignerJournalCopy(backOffice, signer, other).runOnce())
        assertTrue(BackOfficeTestData.audit.query(AuditFilter(targetType = other), 10).isEmpty())
    }
}
