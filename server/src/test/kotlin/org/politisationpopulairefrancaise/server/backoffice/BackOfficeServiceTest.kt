package org.politisationpopulairefrancaise.server.backoffice

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeTestData.addKey
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeTestData.auth
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeTestData.journalAfter
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeTestData.lastSeq
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeTestData.member
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.sql.SQLException
import java.util.Base64
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** The base on the real PostgreSQL 17 of the tests: journal, accounts, grants, two-person rule, cut, sessions. */
class BackOfficeServiceTest {
    private val clock = MutableClock()

    @BeforeTest
    fun emptyTables() = BackOfficeTestData.reset()

    private fun <T> Outcome<T>.ok(): T = when (this) {
        is Outcome.Ok -> value
        is Outcome.Denied -> error("Refused: $code")
    }

    private fun Outcome<*>.code(): String = (this as Outcome.Denied).code

    // Journal ------------------------------------------------------------------------------------------

    @Test
    fun theJournalIsChainedAndAppendOnly() {
        val audit = AuditLog(TestDatabase.dataSource, clock)
        val first = audit.record(AuditEntry(AuditActor.System, "team.test_line", details = mapOf("n" to "1")))
        val second = audit.record(AuditEntry(AuditActor.Unknown, "team.test_line", details = mapOf("n" to "2")))
        assertEquals(first.seq + 1, second.seq)
        assertEquals(first.hash, second.previousHash)
        assertIs<AuditChain.Check.Intact>(audit.verifyChain())
        TestDatabase.dataSource.connection.use { connection ->
            listOf(
                "UPDATE team_audit_log SET reason = 'changé' WHERE seq = ${first.seq}",
                "DELETE FROM team_audit_log WHERE seq = ${first.seq}",
                "TRUNCATE team_audit_log",
            ).forEach { sql ->
                val error = assertFailsWith<SQLException> { connection.createStatement().use { it.execute(sql) } }
                assertTrue(error.message.orEmpty().contains("append-only"), sql)
            }
        }
        assertEquals(second, audit.query(AuditFilter(action = "team.test_line"), 10).first())
        assertEquals(listOf(first), audit.query(AuditFilter(action = "team", beforeSeq = second.seq), 1))
        assertEquals(AuditHead(second.seq, second.hash, second.recordedAt), audit.head())
        assertIs<AuditChain.Check.Intact>(audit.verifyChain())
    }

    @Test
    fun concurrentLinesKeepOneChain() {
        val audit = AuditLog(TestDatabase.dataSource)
        val threads = (1..8).map { n -> Thread { repeat(5) { audit.record(AuditEntry(AuditActor.System, "team.concurrent_line", details = mapOf("n" to "$n"))) } } }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)
        assertIs<AuditChain.Check.Intact>(AuditLog(TestDatabase.dataSource).verifyChain())
    }

    /** Runs [sql] as the database owner with the triggers of the journal off: what a compromised superuser could do. */
    private fun bypassTrigger(sql: String) {
        TestDatabase.dataSource.transaction { connection ->
            connection.createStatement().use { statement ->
                statement.execute("ALTER TABLE team_audit_log DISABLE TRIGGER USER")
                statement.execute(sql)
                statement.execute("ALTER TABLE team_audit_log ENABLE TRIGGER USER")
            }
        }
    }

    @Test
    fun aChangedLineIsFoundFromTheCheckpointAndARemovedEndToo() {
        val audit = AuditLog(TestDatabase.dataSource)
        audit.record(AuditEntry(AuditActor.System, "team.checkpoint_line"))
        assertIs<AuditChain.Check.Intact>(audit.verifyChain())
        val head = assertNotNull(audit.head())
        // The verified line is changed behind the back of the application, then put back.
        bypassTrigger("UPDATE team_audit_log SET reason = 'retouché' WHERE seq = ${head.seq}")
        try {
            assertEquals(AuditChain.Check.Broken(head.seq, "hash"), audit.verifyChain())
        } finally {
            bypassTrigger("UPDATE team_audit_log SET reason = NULL WHERE seq = ${head.seq}")
        }
        assertIs<AuditChain.Check.Intact>(audit.verifyChain())
        // The last line is removed: the chain left is valid, but this process saw a later line.
        val last = audit.record(AuditEntry(AuditActor.System, "team.removed_line"))
        bypassTrigger("DELETE FROM team_audit_log WHERE seq = ${last.seq}")
        assertEquals(AuditChain.Check.Truncated(last.seq, last.seq - 1), audit.verifyChain())
        // A new process (no memory of that line) sees nothing: hence the anchor of the head (D-111).
        assertIs<AuditChain.Check.Intact>(AuditLog(TestDatabase.dataSource).verifyChain())
    }

    @Test
    fun aShortenedJournalPostsAnAlertOnce() {
        val backOffice = BackOfficeTestData.backOffice(clock)
        val admin = member(backOffice, "Admin", listOf(TeamRole.ADMIN))
        backOffice.postAlert("voir", "[EXEMPLE] Ligne de test", 1, Permissions.AUDIT_READ)
        assertIs<AuditChain.Check.Intact>(backOffice.verifyJournal(auth(backOffice, admin).actor).ok())
        val head = assertNotNull(backOffice.journalHead())
        bypassTrigger("DELETE FROM team_audit_log WHERE seq = ${head.seq}")
        assertIs<AuditChain.Check.Truncated>(backOffice.verifyJournal(auth(backOffice, admin).actor).ok())
        backOffice.verifyJournal(auth(backOffice, admin).actor)
        assertEquals(1, backOffice.dashboard(auth(backOffice, admin).actor).alerts.count { it.title == BackOfficeTexts.ALERT_JOURNAL_BROKEN })
    }

    @Test
    fun theTablesHoldNoAddressNoBrowserNoEmail() {
        TestDatabase.dataSource.connection.use { connection ->
            val columns = connection.prepareStatement("SELECT table_name, column_name FROM information_schema.columns WHERE table_name LIKE 'team_%'").use { statement ->
                val rows = statement.executeQuery()
                buildList { while (rows.next()) add(rows.getString(1) + "." + rows.getString(2)) }
            }
            assertTrue(columns.size > 50)
            val forbidden = listOf("ip", "address", "agent", "browser", "email", "mail", "device", "phone")
            columns.forEach { column -> forbidden.forEach { word -> assertFalse(column.substringAfter('.').split('_').contains(word), column) } }
        }
    }

    // Bootstrap, command line and invitations ------------------------------------------------------------

    @Test
    fun theBootstrapCreatesAtMostTwoActiveSuperAdministrators() {
        val backOffice = BackOfficeTestData.backOffice(clock)
        val seq = lastSeq()
        val first = backOffice.bootstrapInvitation("Claire").ok()
        assertEquals(listOf(TeamRole.SUPER_ADMIN), first.invitation.proposedRoles)
        assertTrue(first.invitation.bootstrap)
        backOffice.bootstrapInvitation("Seconde").ok()
        assertEquals("bootstrap_closed", backOffice.bootstrapInvitation("Troisième").code())
        TestDatabase.dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                val rows = statement.executeQuery("SELECT token_hash FROM team_invitation")
                val hashes = buildList { while (rows.next()) add(rows.getString(1)) }
                assertTrue(Tokens.hash(first.token) in hashes)
                assertFalse(first.token in hashes)
            }
        }
        assertEquals(listOf("team.bootstrap_invitation", "team.bootstrap_invitation"), journalAfter(seq).map { it.action })
        // A bootstrap invitation is never withdrawn from the pages.
        val admin = member(backOffice, "Admin", listOf(TeamRole.ADMIN))
        assertEquals("bootstrap_invitation", backOffice.withdrawInvitation(auth(backOffice, admin), first.invitation.id).code())
        assertEquals(2, backOffice.withdrawBootstrapInvitations())
        backOffice.bootstrapInvitation("Troisième").ok()
        // 48 hours later the invitations are gone.
        clock.advance(48.hours + 1.minutes)
        assertNull(backOffice.findInvitation(first.token))
    }

    @Test
    fun aSuspendedSuperAdministratorDoesNotCountForTheBootstrap() {
        val backOffice = BackOfficeTestData.backOffice(clock)
        val first = member(backOffice, "Première", listOf(TeamRole.SUPER_ADMIN))
        member(backOffice, "Seconde", listOf(TeamRole.SUPER_ADMIN))
        assertEquals("bootstrap_closed", backOffice.bootstrapInvitation("Troisième").code())
        backOffice.emergencyCut(auth(backOffice, first), first.id, null).ok()
        backOffice.bootstrapInvitation("Troisième").ok()
    }

    @Test
    fun theCommandLineSaysWhereItActsAndNeverMigrates() {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val env = mapOf(BackOfficeConfig.ORIGIN_VARIABLE to "http://localhost:8081", "PPF_ENVIRONMENT" to "development")
        val code = BackOfficeCli.run(listOf("bootstrap", "Claire"), env::get, PrintStream(out), PrintStream(err), TestDatabase.dataSource)
        assertEquals(0, code, err.toString())
        val lines = out.toString().lines()
        assertTrue(lines.first().startsWith("Environment: development, database host:"))
        val link = lines.first { it.startsWith("http") }
        assertTrue(link.startsWith("http://localhost:8081/invitation/"))
        assertNotNull(BackOfficeTestData.backOffice().findInvitation(link.substringAfterLast('/')))
        assertEquals(2, BackOfficeCli.run(listOf("bootstrap"), env::get, PrintStream(out), PrintStream(err), TestDatabase.dataSource))
        assertEquals(2, BackOfficeCli.run(listOf("bootstrap", "X"), { null }, PrintStream(out), PrintStream(err), TestDatabase.dataSource))
        assertEquals(0, BackOfficeCli.run(listOf("withdraw-bootstrap"), env::get, PrintStream(out), PrintStream(err), TestDatabase.dataSource))
        assertNull(BackOfficeTestData.backOffice().findInvitation(link.substringAfterLast('/')))
    }

    @Test
    fun theServerOperatorCutsAndReinstatesFromTheCommandLine() {
        val backOffice = BackOfficeTestData.backOffice(clock)
        val claire = member(backOffice, "Claire", listOf(TeamRole.SUPER_ADMIN))
        val env = mapOf(BackOfficeConfig.ORIGIN_VARIABLE to "http://localhost:8081")
        val seq = lastSeq()
        val out = ByteArrayOutputStream()
        assertEquals(0, BackOfficeCli.run(listOf("cut", "claire"), env::get, PrintStream(out), PrintStream(ByteArrayOutputStream()), TestDatabase.dataSource))
        assertEquals(AccountStatus.SUSPENDED, backOffice.member(claire.id)!!.status)
        assertNull(backOffice.authenticate(claire.token))
        assertEquals(1, BackOfficeCli.run(listOf("cut", "Claire"), env::get, PrintStream(out), PrintStream(ByteArrayOutputStream()), TestDatabase.dataSource))
        assertEquals(0, BackOfficeCli.run(listOf("reinstate", "Claire"), env::get, PrintStream(out), PrintStream(ByteArrayOutputStream()), TestDatabase.dataSource))
        assertEquals(AccountStatus.ACTIVE, backOffice.member(claire.id)!!.status)
        // Keys and roles come back: Claire signs in again with her key.
        val opened = backOffice.finishSignIn(claire.keys[0].assertion(backOffice.startSignIn().ok())).ok()
        assertTrue(backOffice.authenticate(opened.token)!!.actor.has(Permissions.TEAM_MANAGE_ADMINS))
        val lines = journalAfter(seq).filter { it.action.startsWith("team.") }
        assertEquals(listOf("team.emergency_cut", "team.member_reinstated"), lines.map { it.action })
        assertTrue(lines.all { it.actorLabel == BackOfficeTexts.ACTOR_SYSTEM })
    }

    @Test
    fun anInvitationIsAcceptedOnceWithAKeyAndProposedRolesWaitForASecondPerson() {
        val backOffice = BackOfficeTestData.backOffice(clock)
        val admin = member(backOffice, "Admin", listOf(TeamRole.ADMIN))
        val issued = backOffice.createInvitation(auth(backOffice, admin), "Nouvelle", listOf(TeamRole.MODERATOR), null, null).ok()
        val options = backOffice.startEnrolment(issued.token).ok()
        val key = TestAuthenticator()
        val seq = lastSeq()
        val opened = backOffice.finishEnrolment(key.register(options, name = "Téléphone de Nouvelle")).ok()
        val newcomer = backOffice.authenticate(opened.token)!!
        assertEquals("Nouvelle", newcomer.account.pseudonym)
        assertEquals(admin.id, newcomer.account.invitedBy)
        assertTrue(newcomer.actor.permissions.isEmpty())
        // The journal names keys by their identifier, never by the name given by the person.
        assertFalse(journalAfter(seq).any { line -> line.details.values.any { it.contains("Téléphone") } })
        assertTrue(journalAfter(seq).first { it.action == "team.invitation_accepted" }.details.containsKey("credential"))
        assertNull(backOffice.findInvitation(issued.token))
        assertEquals("invitation_gone", backOffice.startEnrolment(issued.token).code())
        val pending = BackOfficeTestData.approvals.open(clock.instant()).single()
        assertEquals(BackOffice.ACTION_GRANT, pending.action)
        assertEquals(admin.id, pending.requestedBy)
        assertEquals("own_request", backOffice.approve(auth(backOffice, admin), pending.id).code())
        val other = member(backOffice, "Autre admin", listOf(TeamRole.ADMIN))
        assertEquals(ApprovalStatus.APPROVED, backOffice.approve(auth(backOffice, other), pending.id).ok())
        val grant = backOffice.liveGrants(newcomer.account.id).single()
        assertEquals(TeamRole.MODERATOR, grant.role)
        assertEquals(admin.id, grant.grantedBy)
        assertEquals(other.id, grant.approvedBy)
        val reloaded = backOffice.authenticate(opened.token)!!
        assertTrue(reloaded.actor.needsBackupKey)
        assertFalse(reloaded.actor.has(Permissions.queueDecide("agir.held")))
        addKey(backOffice, reloaded, key, TestAuthenticator()).ok()
        assertTrue(backOffice.authenticate(opened.token)!!.actor.has(Permissions.queueDecide("agir.held")))
    }

    @Test
    fun aRefusedRegistrationLeavesTheInvitationValidAndIsOnlyCounted() {
        val backOffice = BackOfficeTestData.backOffice(clock)
        val issued = backOffice.bootstrapInvitation("Claire").ok()
        val seq = lastSeq()
        val withoutPin = TestAuthenticator(userVerified = false).register(backOffice.startEnrolment(issued.token).ok())
        assertEquals("registration_refused", backOffice.finishEnrolment(withoutPin).code())
        val phishing = TestAuthenticator().register(backOffice.startEnrolment(issued.token).ok(), clientOrigin = "https://equipe.example.org")
        assertEquals("registration_refused", backOffice.finishEnrolment(phishing).code())
        // An already registered key: refused, and the invitation is still valid.
        val existing = member(backOffice, "Ancienne", keys = 1)
        val known = existing.keys[0].register(backOffice.startEnrolment(issued.token).ok())
        assertEquals("key_known", backOffice.finishEnrolment(known).code())
        assertNotNull(backOffice.findInvitation(issued.token))
        // One pending ceremony per invitation: a new start replaces the previous one.
        val older = backOffice.startEnrolment(issued.token).ok()
        val good = TestAuthenticator()
        val newer = backOffice.startEnrolment(issued.token).ok()
        assertEquals("ceremony_gone", backOffice.finishEnrolment(good.register(older)).code())
        val response = good.register(newer)
        backOffice.finishEnrolment(response).ok()
        assertEquals("ceremony_gone", backOffice.finishEnrolment(response).code())
        // No line per anonymous failure; one line of counts per period of 10 minutes.
        assertFalse(journalAfter(seq).any { it.action.startsWith("auth.enrolment") })
        clock.advance(11.minutes)
        assertEquals(1, backOffice.flushFailures())
        val counts = journalAfter(seq).single { it.action == "auth.anonymous_failures" }
        assertEquals("2", counts.details["enrolment_registration_refused"])
        assertEquals(BackOfficeTexts.ACTOR_UNKNOWN, counts.actorLabel)
    }

    // Sign-in ---------------------------------------------------------------------------------------------

    @Test
    fun signInIsStatelessUntilASignatureIsVerified() {
        val backOffice = BackOfficeTestData.backOffice(clock)
        val claire = member(backOffice, "Claire", listOf(TeamRole.SUPER_ADMIN))
        val seq = lastSeq()
        val options = backOffice.startSignIn().ok()
        assertTrue(options.publicKey.allowCredentials.isEmpty())
        assertEquals("required", options.publicKey.userVerification)
        val opened = backOffice.finishSignIn(claire.keys[0].assertion(options)).ok()
        assertEquals(claire.id, backOffice.authenticate(opened.token)!!.account.id)
        // The same challenge never serves twice; a forged or old one is refused.
        assertEquals("ceremony_gone", backOffice.finishSignIn(claire.keys[0].assertion(options)).code())
        val forged = Tokens.base64Url(Tokens.randomBytes(56))
        assertEquals("ceremony_gone", backOffice.finishSignIn(claire.keys[0].assertion(forged, forged)).code())
        val old = backOffice.startSignIn().ok()
        clock.advance(6.minutes)
        assertEquals("ceremony_gone", backOffice.finishSignIn(claire.keys[0].assertion(old)).code())
        // Thousands of starts keep no state: nothing to exhaust.
        repeat(5_000) { backOffice.startSignIn() }
        // An unknown key is counted, not journaled one by one; a known account's failure is journaled.
        val stranger = TestAuthenticator()
        stranger.userHandle = Tokens.randomBytes(32)
        assertEquals("unknown_key", backOffice.finishSignIn(stranger.assertion(backOffice.startSignIn().ok())).code())
        claire.keys[0].counter = 0
        assertEquals("assertion_refused", backOffice.finishSignIn(claire.keys[0].assertion(backOffice.startSignIn().ok())).code())
        assertEquals(listOf("auth.login", "auth.login_failed"), journalAfter(seq).map { it.action })
        assertEquals(1, backOffice.flushFailures(all = true))
        assertEquals("1", journalAfter(seq).last().details["unknown_key"])
    }

    @Test
    fun sessionsEndAfterThirtyIdleMinutesOrEightHours() {
        val backOffice = BackOfficeTestData.backOffice(clock)
        val claire = member(backOffice, "Claire", listOf(TeamRole.SUPER_ADMIN))
        clock.advance(29.minutes)
        assertNotNull(backOffice.authenticate(claire.token))
        clock.advance(31.minutes)
        assertNull(backOffice.authenticate(claire.token))
        val again = member(backOffice, "Seconde", listOf(TeamRole.SUPER_ADMIN))
        repeat(16) {
            clock.advance(29.minutes)
            assertNotNull(backOffice.authenticate(again.token))
        }
        clock.advance(29.minutes)
        assertNull(backOffice.authenticate(again.token), "never more than 8 hours")
        TestDatabase.dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                val rows = statement.executeQuery("SELECT token_hash FROM team_session")
                while (rows.next()) assertFalse(rows.getString(1) == again.token)
            }
        }
    }

    @Test
    fun sensitiveActionsAskForTheKeyAfterFiveMinutes() {
        val backOffice = BackOfficeTestData.backOffice(clock)
        val claire = member(backOffice, "Claire", listOf(TeamRole.SUPER_ADMIN))
        val target = member(backOffice, "Cible")
        clock.advance(6.minutes)
        val stale = auth(backOffice, claire)
        assertFalse(stale.isFresh(clock.instant()))
        assertEquals("confirmation_required", backOffice.requestGrant(stale, target.id, TeamRole.MODERATOR, null, null, null).code())
        assertEquals("confirmation_required", backOffice.removeMember(stale, claire.id, null).code())
        val options = backOffice.startConfirmation(stale).ok()
        assertEquals(2, options.publicKey.allowCredentials.size)
        backOffice.finishConfirmation(stale, claire.keys[1].assertion(options)).ok()
        val fresh = auth(backOffice, claire)
        assertTrue(fresh.isFresh(clock.instant()))
        backOffice.requestGrant(fresh, target.id, TeamRole.MODERATOR, null, null, null).ok()
        // A confirmation belongs to its session, and one ceremony waits per session.
        val other = member(backOffice, "Autre", listOf(TeamRole.SUPER_ADMIN))
        val stolen = backOffice.startConfirmation(auth(backOffice, claire)).ok()
        assertEquals("ceremony_gone", backOffice.finishConfirmation(auth(backOffice, other), other.keys[0].assertion(stolen)).code())
        val replaced = backOffice.startConfirmation(auth(backOffice, claire)).ok()
        backOffice.startConfirmation(auth(backOffice, claire)).ok()
        assertEquals("ceremony_gone", backOffice.finishConfirmation(auth(backOffice, claire), claire.keys[0].assertion(replaced)).code())
    }

    @Test
    fun aNewKeyNeedsAKeyVerifiedWithinSixtySecondsInTheSameFlow() {
        val backOffice = BackOfficeTestData.backOffice(clock)
        val claire = member(backOffice, "Claire", keys = 1)
        clock.advance(6.minutes)
        // A stale session may add a key, but only with a fresh proof from an existing key.
        val proof = backOffice.startAddKeyProof(auth(backOffice, claire)).ok()
        clock.advance(61.seconds)
        assertEquals("ceremony_gone", backOffice.startAddKey(auth(backOffice, claire), claire.keys[0].assertion(proof)).code())
        val stranger = TestAuthenticator()
        val second = backOffice.startAddKeyProof(auth(backOffice, claire)).ok()
        assertEquals("unknown_key", backOffice.startAddKey(auth(backOffice, claire), stranger.assertion(second)).code())
        val added = addKey(backOffice, auth(backOffice, claire), claire.keys[0], stranger, name = "Clé USB").ok()
        assertEquals("Clé USB", added.name)
        // The registration is bound to the proof: a registration without its ceremony is refused.
        backOffice.startAddKeyProof(auth(backOffice, claire)).ok()
        val unbound = CreationOptionsResponse(
            "sans-preuve",
            PublicKeyCreationOptions(Tokens.base64Url(Tokens.randomBytes(32)), RelyingParty("localhost", "x"), UserEntity(Tokens.base64Url(Tokens.randomBytes(32)), "x", "x"), emptyList(), 1),
        )
        assertEquals("ceremony_gone", backOffice.finishAddKey(auth(backOffice, claire), TestAuthenticator().register(unbound)).code())
    }

    // Grants and two-person rule ------------------------------------------------------------------------------

    @Test
    fun aRoleNeedsASecondPersonWithTheSamePermission() {
        val backOffice = BackOfficeTestData.backOffice(clock)
        val admin = member(backOffice, "Admin", listOf(TeamRole.ADMIN))
        val moderator = member(backOffice, "Modo", listOf(TeamRole.MODERATOR))
        val target = member(backOffice, "Cible")
        val request = backOffice.requestGrant(auth(backOffice, admin), target.id, TeamRole.EDITOR, null, null, "Rédaction de Comprendre").ok()
        assertEquals("already_requested", backOffice.requestGrant(auth(backOffice, admin), target.id, TeamRole.EDITOR, null, null, null).code())
        assertTrue(backOffice.liveGrants(target.id).isEmpty())
        assertEquals("forbidden", backOffice.approve(auth(backOffice, moderator), request.id).code())
        assertEquals("own_request", backOffice.approve(auth(backOffice, admin), request.id).code())
        assertEquals("forbidden", backOffice.approve(auth(backOffice, member(backOffice, "Sans rôle")), request.id).code())
        val second = member(backOffice, "Admin2", listOf(TeamRole.ADMIN))
        backOffice.approve(auth(backOffice, second), request.id).ok()
        assertEquals("approval_gone", backOffice.approve(auth(backOffice, second), request.id).code())
        assertEquals(TeamRole.EDITOR, backOffice.liveGrants(target.id).single().role)
    }

    @Test
    fun theTargetNeverApprovesNorRefusesARequestAboutThem() {
        val backOffice = BackOfficeTestData.backOffice(clock)
        val admin = member(backOffice, "Admin", listOf(TeamRole.ADMIN))
        val target = member(backOffice, "Admin cible", listOf(TeamRole.ADMIN))
        val request = backOffice.requestGrant(auth(backOffice, admin), target.id, TeamRole.MODERATOR, null, null, null).ok()
        assertEquals("own_target", backOffice.approve(auth(backOffice, target), request.id).code())
        assertEquals("own_target", backOffice.refuseApproval(auth(backOffice, target), request.id, "Non").code())
        assertFalse(backOffice.openApprovals(auth(backOffice, target).actor).any { it.id == request.id })
    }

    @Test
    fun administratorsAreNamedAndRemovedBySuperAdministratorsOnly() {
        val backOffice = BackOfficeTestData.backOffice(clock)
        val admin = member(backOffice, "Admin", listOf(TeamRole.ADMIN))
        val superAdmin = member(backOffice, "Super", listOf(TeamRole.SUPER_ADMIN))
        val target = member(backOffice, "Cible")
        assertEquals("forbidden", backOffice.requestGrant(auth(backOffice, admin), target.id, TeamRole.ADMIN, null, null, null).code())
        val request = backOffice.requestGrant(auth(backOffice, superAdmin), target.id, TeamRole.ADMIN, null, null, null).ok()
        assertEquals(Permissions.TEAM_MANAGE_ADMINS, request.permission)
        assertEquals("forbidden", backOffice.approve(auth(backOffice, admin), request.id).code())
        val second = member(backOffice, "Super2", listOf(TeamRole.SUPER_ADMIN))
        backOffice.approve(auth(backOffice, second), request.id).ok()
        val grant = backOffice.liveGrants(target.id).single()
        val revocation = backOffice.revokeGrant(auth(backOffice, superAdmin), grant.id, "Fin de mission").ok()
        assertIs<TeamChange.Waiting>(revocation)
        assertTrue(backOffice.liveGrants(target.id).isNotEmpty())
        backOffice.approve(auth(backOffice, second), revocation.approval.id).ok()
        assertTrue(backOffice.liveGrants(target.id).isEmpty())
        val moderatorRequest = backOffice.requestGrant(auth(backOffice, admin), target.id, TeamRole.MODERATOR, null, null, null).ok()
        backOffice.approve(auth(backOffice, superAdmin), moderatorRequest.id).ok()
        val moderatorGrant = backOffice.liveGrants(target.id).single()
        assertEquals(TeamChange.Done(), backOffice.revokeGrant(auth(backOffice, admin), moderatorGrant.id, null).ok())
        assertTrue(backOffice.liveGrants(target.id).isEmpty())
    }

    @Test
    fun temporaryGrantsExpireByThemselves() {
        val backOffice = BackOfficeTestData.backOffice(clock)
        val admin = member(backOffice, "Admin", listOf(TeamRole.ADMIN))
        val second = member(backOffice, "Admin2", listOf(TeamRole.ADMIN))
        val target = member(backOffice, "Cible")
        val end = clock.instant().plusSeconds(3.days.inWholeSeconds)
        val request = backOffice.requestGrant(auth(backOffice, admin), target.id, TeamRole.MODERATOR, null, end, null).ok()
        backOffice.approve(auth(backOffice, second), request.id).ok()
        assertTrue(auth(backOffice, target).actor.has(Permissions.queueDecide("agir.held")))
        clock.advance(3.days + 1.minutes)
        assertTrue(backOffice.liveGrants(target.id).isEmpty())
        val seq = lastSeq()
        assertEquals(1, backOffice.maintain().expiredGrants)
        assertEquals(GrantStatus.EXPIRED, backOffice.grantHistory(target.id).single().status)
        val expired = journalAfter(seq).filter { it.action == "team.role_expired" }
        assertEquals(1, expired.size)
        assertEquals(BackOfficeTexts.ACTOR_SYSTEM, expired.single().actorLabel)
        assertEquals(0, backOffice.maintain().expiredGrants)
    }

    @Test
    fun requestsExpireAfterSevenDaysAndCanBeCancelledOrRefused() {
        val backOffice = BackOfficeTestData.backOffice(clock)
        val admin = member(backOffice, "Admin", listOf(TeamRole.ADMIN))
        val second = member(backOffice, "Admin2", listOf(TeamRole.ADMIN))
        val a = member(backOffice, "Alpha")
        val b = member(backOffice, "Bravo")
        val c = member(backOffice, "Charlie")
        val expiring = backOffice.requestGrant(auth(backOffice, admin), a.id, TeamRole.MODERATOR, null, null, null).ok()
        val cancelled = backOffice.requestGrant(auth(backOffice, admin), b.id, TeamRole.MODERATOR, null, null, null).ok()
        val refused = backOffice.requestGrant(auth(backOffice, admin), c.id, TeamRole.MODERATOR, null, null, null).ok()
        assertEquals("forbidden", backOffice.cancelApproval(auth(backOffice, second), cancelled.id).code())
        backOffice.cancelApproval(auth(backOffice, admin), cancelled.id).ok()
        assertEquals("own_request", backOffice.refuseApproval(auth(backOffice, admin), refused.id, null).code())
        backOffice.refuseApproval(auth(backOffice, second), refused.id, "Pas maintenant").ok()
        clock.advance(7.days + 1.minutes)
        val fresh = member(backOffice, "Admin3", listOf(TeamRole.ADMIN))
        assertEquals("approval_gone", backOffice.approve(auth(backOffice, fresh), expiring.id).code())
        assertEquals(1, backOffice.maintain().expiredApprovals)
        assertEquals(ApprovalStatus.EXPIRED, backOffice.approval(expiring.id)!!.status)
        assertEquals(ApprovalStatus.CANCELLED, backOffice.approval(cancelled.id)!!.status)
        assertEquals(ApprovalStatus.REFUSED, backOffice.approval(refused.id)!!.status)
        clock.advance(31.days)
        backOffice.maintain()
        assertNull(backOffice.approval(expiring.id))
    }

    // Emergency cut, removal, reinstatement ---------------------------------------------------------------

    @Test
    fun theEmergencyCutSuspendsEverythingAtOnceAndReversibly() {
        val backOffice = BackOfficeTestData.backOffice(clock)
        val admin = member(backOffice, "Admin", listOf(TeamRole.ADMIN))
        val moderator = member(backOffice, "Modo", listOf(TeamRole.MODERATOR, TeamRole.ADMIN))
        val pending = backOffice.requestGrant(auth(backOffice, admin), moderator.id, TeamRole.EDITOR, null, null, null).ok()
        val invitation = backOffice.createInvitation(auth(backOffice, moderator), "Invitée", emptyList(), null, null).ok()
        val superAdmin = member(backOffice, "Super", listOf(TeamRole.SUPER_ADMIN))
        val seq = lastSeq()
        val report = backOffice.emergencyCut(auth(backOffice, superAdmin), moderator.id, "Téléphone volé").ok()
        assertEquals(CutReport(sessions = 1, grants = 2, keys = 2, validationSessions = 0, requests = 1, invitations = 1), report)
        assertNull(backOffice.authenticate(moderator.token))
        assertEquals(AccountStatus.SUSPENDED, backOffice.member(moderator.id)!!.status)
        assertEquals(2, backOffice.keysOf(moderator.id).size, "keys are kept, unusable")
        assertEquals(ApprovalStatus.FAILED, backOffice.approval(pending.id)!!.status)
        assertNull(backOffice.findInvitation(invitation.token), "the invitations it created end too")
        assertEquals("account_closed", backOffice.finishSignIn(moderator.keys[0].assertion(backOffice.startSignIn().ok())).code())
        val line = journalAfter(seq).single { it.action == "team.emergency_cut" }
        assertEquals("Téléphone volé", line.reason)
        assertEquals(setOf("moderator", "admin"), line.details["roles"]!!.split(",").toSet())
        // The lost key is deleted before the reinstatement, which needs a second super administrator.
        val lost = backOffice.keysOf(moderator.id).single { it.name == "Clé 0" }
        backOffice.deleteMemberKey(auth(backOffice, superAdmin), moderator.id, lost.id).ok()
        val request = backOffice.requestReinstatement(auth(backOffice, superAdmin), moderator.id, "Clé perdue retirée").ok()
        assertEquals("own_request", backOffice.approve(auth(backOffice, superAdmin), request.id).code())
        val second = member(backOffice, "Super2", listOf(TeamRole.SUPER_ADMIN))
        backOffice.approve(auth(backOffice, second), request.id).ok()
        assertEquals(AccountStatus.ACTIVE, backOffice.member(moderator.id)!!.status)
        assertEquals("unknown_key", backOffice.finishSignIn(moderator.keys[0].assertion(backOffice.startSignIn().ok())).code())
        val back = backOffice.finishSignIn(moderator.keys[1].assertion(backOffice.startSignIn().ok())).ok()
        assertEquals(setOf(TeamRole.MODERATOR, TeamRole.ADMIN), backOffice.authenticate(back.token)!!.actor.roles)
    }

    @Test
    fun aSingleSuperAdministratorCannotRemoveTheOthers() {
        val backOffice = BackOfficeTestData.backOffice(clock)
        val rogue = member(backOffice, "Rogue", listOf(TeamRole.SUPER_ADMIN))
        val other = member(backOffice, "Autre", listOf(TeamRole.SUPER_ADMIN))
        val admin = member(backOffice, "Admin", listOf(TeamRole.ADMIN))
        val removal = backOffice.removeMember(auth(backOffice, rogue), other.id, "Départ").ok()
        assertIs<TeamChange.Waiting>(removal)
        assertEquals(AccountStatus.ACTIVE, backOffice.member(other.id)!!.status)
        assertEquals("own_target", backOffice.approve(auth(backOffice, other), removal.approval.id).code())
        assertEquals("forbidden", backOffice.approve(auth(backOffice, admin), removal.approval.id).code())
        // The cut is immediate but only suspends; nobody reinstates alone.
        backOffice.emergencyCut(auth(backOffice, rogue), other.id, null).ok()
        assertEquals(AccountStatus.SUSPENDED, backOffice.member(other.id)!!.status)
        val reinstatement = backOffice.requestReinstatement(auth(backOffice, rogue), other.id, null).ok()
        assertEquals("own_request", backOffice.approve(auth(backOffice, rogue), reinstatement.id).code())
        // An ordinary member is removed at once.
        val ordinary = member(backOffice, "Ordinaire", listOf(TeamRole.MODERATOR))
        val done = backOffice.removeMember(auth(backOffice, admin), ordinary.id, null).ok()
        assertIs<TeamChange.Done>(done)
        val removed = backOffice.member(ordinary.id)!!
        assertEquals(AccountStatus.REMOVED, removed.status)
        assertTrue(backOffice.keysOf(ordinary.id).isEmpty())
        member(backOffice, "Ordinaire")
    }

    @Test
    fun anAdministratorIsRemovedWhenASecondSuperAdministratorAgrees() {
        val backOffice = BackOfficeTestData.backOffice(clock)
        val first = member(backOffice, "Première", listOf(TeamRole.SUPER_ADMIN))
        val second = member(backOffice, "Seconde", listOf(TeamRole.SUPER_ADMIN))
        val admin = member(backOffice, "Admin", listOf(TeamRole.ADMIN))
        val removal = backOffice.removeMember(auth(backOffice, first), admin.id, "Départ").ok() as TeamChange.Waiting
        assertEquals("already_requested", backOffice.removeMember(auth(backOffice, first), admin.id, null).code())
        val seq = lastSeq()
        backOffice.approve(auth(backOffice, second), removal.approval.id).ok()
        assertEquals(AccountStatus.REMOVED, backOffice.member(admin.id)!!.status)
        assertEquals(listOf("approval.approved", "team.member_removed"), journalAfter(seq).map { it.action })
    }

    @Test
    fun anyoneCutsThemselvesWithoutConfirmationButRemovesThemselvesWithIt() {
        val backOffice = BackOfficeTestData.backOffice(clock)
        val admin = member(backOffice, "Admin", listOf(TeamRole.ADMIN))
        val otherAdmin = member(backOffice, "Admin2", listOf(TeamRole.ADMIN))
        val moderator = member(backOffice, "Modo", listOf(TeamRole.MODERATOR))
        assertEquals("forbidden", backOffice.emergencyCut(auth(backOffice, moderator), admin.id, null).code())
        assertEquals("forbidden", backOffice.emergencyCut(auth(backOffice, admin), otherAdmin.id, null).code())
        clock.advance(10.minutes)
        backOffice.emergencyCut(auth(backOffice, moderator), moderator.id, null).ok()
        assertEquals("confirmation_required", backOffice.removeMember(auth(backOffice, admin), admin.id, null).code())
    }

    @Test
    fun keysAreAddedAndDeletedButNeverTheLastOne() {
        val backOffice = BackOfficeTestData.backOffice(clock)
        val claire = member(backOffice, "Claire", keys = 1)
        val key = backOffice.keysOf(claire.id).single()
        assertEquals("last_key", backOffice.deleteKey(auth(backOffice, claire), key.id).code())
        val second = TestAuthenticator()
        addKey(backOffice, auth(backOffice, claire), claire.keys[0], second, name = "Clé USB").ok()
        assertEquals("key_known", addKey(backOffice, auth(backOffice, claire), claire.keys[0], second).code())
        val seq = lastSeq()
        backOffice.deleteKey(auth(backOffice, claire), key.id).ok()
        assertEquals(key.id.toString(), journalAfter(seq).single().details["credential"])
        val stored = BackOfficeTestData.accounts.credentials(claire.id).single()
        assertTrue(stored.attestedData.copyOfRange(0, 16).all { it == 0.toByte() }, "the model of the key is not kept")
    }

    // Validators (contract, section 8 and D-112) --------------------------------------------------------------

    private val organisation = IdentityKeys.generate().publicKey.hex

    @Test
    fun aValidatorIsDesignatedByAVerifiedOrganisation() {
        val verified = mutableSetOf(organisation)
        val backOffice = BackOffice(BackOfficeTestData.CONFIG, TestDatabase.dataSource, isVerifiedOrganisation = { it in verified }, verifiedOrganisations = { verified }, clock = clock)
        val admin = member(backOffice, "Admin", listOf(TeamRole.ADMIN))
        val volunteer = member(backOffice, "Bénévole", keys = 1)
        val stranger = IdentityKeys.generate().publicKey.hex
        assertEquals("organisation_required", backOffice.requestGrant(auth(backOffice, admin), volunteer.id, TeamRole.VALIDATOR, null, null, null).code())
        assertEquals("organisation_not_verified", backOffice.requestGrant(auth(backOffice, admin), volunteer.id, TeamRole.VALIDATOR, stranger, null, null).code())
        assertEquals("organisation_not_expected", backOffice.requestGrant(auth(backOffice, admin), volunteer.id, TeamRole.ELECTED_CERTIFIER, organisation, null, null).code())
        val npub = org.politisationpopulairefrancaise.core.identity.PublicKey.fromHex(organisation).npub
        val request = backOffice.requestGrant(auth(backOffice, admin), volunteer.id, TeamRole.VALIDATOR, npub, null, null).ok()
        backOffice.approve(auth(backOffice, member(backOffice, "Admin2", listOf(TeamRole.ADMIN))), request.id).ok()
        val grant = backOffice.liveGrants(volunteer.id).single()
        assertEquals(organisation, grant.organisationPubkey)
        assertTrue(auth(backOffice, volunteer).actor.has(Permissions.VALIDATION_SESSION_OPEN))
    }

    private suspend fun signed(keys: IdentityKeys, url: String, method: String): SignedSessionRequest {
        clock.advance(1.seconds)
        val event = LocalEventSigner(keys).sign(kind = MinimalNip98Verifier.KIND, createdAt = clock.instant().epochSecond, tags = listOf(listOf("u", url), listOf("method", method)))
        return SignedSessionRequest("Nostr " + Base64.getEncoder().encodeToString(event.toJson().toByteArray()), url, method)
    }

    @Test
    fun aValidationSessionAuthenticatesTheApplicationUntilItEnds() = runBlocking {
        val verified = mutableSetOf(organisation, IdentityKeys.generate().publicKey.hex)
        val backOffice = BackOffice(BackOfficeTestData.CONFIG, TestDatabase.dataSource, isVerifiedOrganisation = { it in verified }, verifiedOrganisations = { verified.toSet() }, clock = clock)
        val volunteer = member(backOffice, "Bénévole", listOf(TeamRole.VALIDATOR), keys = 1, organisation = organisation)
        val sessionKeys = IdentityKeys.generate()
        val session = backOffice.openValidationSession(auth(backOffice, volunteer), sessionKeys.publicKey.npub, 2, 4.hours).ok()
        assertEquals("2026-W45", session.week)
        assertEquals("session_already_open", backOffice.openValidationSession(auth(backOffice, volunteer), IdentityKeys.generate().publicKey.hex, 2, 4.hours).code())
        assertEquals("bad_ceiling", backOffice.openValidationSession(auth(backOffice, volunteer), IdentityKeys.generate().publicKey.hex, 41, 4.hours).code())
        val url = "https://equipe.example.org/api/v1/urne/validation"
        backOffice.checkValidatorOrganisations()
        val granted = backOffice.validationGate.authorize(signed(sessionKeys, url, "POST"))
        assertIs<SessionAccess.Granted>(granted)
        assertEquals(session.id, granted.session.id)
        assertEquals(SessionAccess.Refused("no_open_session"), backOffice.validationGate.authorize(signed(IdentityKeys.generate(), url, "POST")))
        assertTrue(backOffice.validationGate.recordValidation(granted.session))
        assertTrue(backOffice.validationGate.recordValidation(granted.session))
        assertFalse(backOffice.validationGate.recordValidation(granted.session))
        assertEquals(2, backOffice.validationSession(session.id)!!.validations)
        // The organisation loses its verification (one of two keys left): refused at once, suspended by the check.
        verified.remove(organisation)
        assertEquals(SessionAccess.Refused("organisation_not_verified"), backOffice.validationGate.authorize(signed(sessionKeys, url, "POST")))
        val seq = lastSeq()
        assertEquals(1, backOffice.maintain().suspendedValidators)
        assertEquals(ValidationSessionState.REVOKED, backOffice.validationSession(session.id)!!.state)
        assertEquals(GrantStatus.SUSPENDED, backOffice.grantHistory(volunteer.id).single().status)
        assertEquals(listOf("team.validator_suspended"), journalAfter(seq).map { it.action }.filter { it.startsWith("team.") })
        verified += organisation
        assertEquals(SessionAccess.Refused("no_open_session"), backOffice.validationGate.authorize(signed(sessionKeys, url, "POST")))
    }

    @Test
    fun anEmptyOrHalvedVerifiedSetSuspendsNobodyAndAlerts() {
        val others = List(4) { IdentityKeys.generate().publicKey.hex }
        val verified = (others + organisation).toMutableSet()
        val backOffice = BackOffice(BackOfficeTestData.CONFIG, TestDatabase.dataSource, isVerifiedOrganisation = { it in verified }, verifiedOrganisations = { verified.toSet() }, clock = clock)
        val volunteer = member(backOffice, "Bénévole", listOf(TeamRole.VALIDATOR), keys = 1, organisation = organisation)
        val admin = member(backOffice, "Admin", listOf(TeamRole.ADMIN))
        assertEquals(0, backOffice.checkValidatorOrganisations())
        verified.clear()
        assertEquals(0, backOffice.checkValidatorOrganisations())
        verified += others.take(2)
        assertEquals(0, backOffice.checkValidatorOrganisations(), "5 keys down to 2: more than half lost")
        assertEquals(GrantStatus.ACTIVE, backOffice.grantHistory(volunteer.id).single().status)
        val alerts = backOffice.dashboard(auth(backOffice, admin).actor).alerts.filter { it.source == BackOffice.ORGANISATIONS_ALERT_SOURCE }
        assertEquals(1, alerts.size)
        assertEquals(4, alerts.single().level)
        // Dismissing the alert accepts the new set: the next check suspends.
        backOffice.dismissAlert(auth(backOffice, admin), alerts.single().id).ok()
        assertEquals(1, backOffice.checkValidatorOrganisations())
        assertEquals(GrantStatus.SUSPENDED, backOffice.grantHistory(volunteer.id).single().status)
    }

    @Test
    fun anExpiredOrRevokedSessionIsRefusedAtOnce() = runBlocking {
        val backOffice = BackOfficeTestData.backOffice(clock, verified = setOf(organisation))
        val volunteer = member(backOffice, "Bénévole", listOf(TeamRole.VALIDATOR), keys = 1, organisation = organisation)
        val url = "https://equipe.example.org/x"
        val first = IdentityKeys.generate()
        val session = backOffice.openValidationSession(auth(backOffice, volunteer), first.publicKey.hex, 40, 1.hours).ok()
        assertIs<SessionAccess.Granted>(backOffice.validationGate.authorize(signed(first, url, "GET")))
        clock.advance(61.minutes)
        assertEquals(SessionAccess.Refused("no_open_session"), backOffice.validationGate.authorize(signed(first, url, "GET")))
        val again = backOffice.finishSignIn(volunteer.keys[0].assertion(backOffice.startSignIn().ok())).ok()
        val volunteerAuth = backOffice.authenticate(again.token)!!
        assertEquals("session_key_used", backOffice.openValidationSession(volunteerAuth, first.publicKey.hex, 40, 1.hours).code())
        assertEquals(ValidationSessionState.EXPIRED, backOffice.validationSession(session.id)!!.state)
        val second = IdentityKeys.generate()
        val reopened = backOffice.openValidationSession(volunteerAuth, second.publicKey.hex, 40, 1.hours).ok()
        assertIs<SessionAccess.Granted>(backOffice.validationGate.authorize(signed(second, url, "GET")))
        val admin = member(backOffice, "Admin", listOf(TeamRole.ADMIN))
        backOffice.revokeValidationSession(auth(backOffice, admin), reopened.id, "Fin de l'événement").ok()
        assertEquals(SessionAccess.Refused("no_open_session"), backOffice.validationGate.authorize(signed(second, url, "GET")))
        assertEquals("already_closed", backOffice.closeValidationSession(volunteerAuth, reopened.id).code())
    }

    // Alerts ------------------------------------------------------------------------------------------------

    @Test
    fun alertsAreShownToWhoMayDismissThemAndRecorded() {
        val backOffice = BackOfficeTestData.backOffice(clock)
        val admin = member(backOffice, "Admin", listOf(TeamRole.ADMIN))
        val moderator = member(backOffice, "Modo", listOf(TeamRole.MODERATOR))
        val seq = lastSeq()
        val alert = backOffice.postAlert("voir", "[EXEMPLE] Import des données ouvertes refusé", 4, Permissions.SETTINGS_MANAGE, "/journal")
        assertFailsWith<IllegalArgumentException> { backOffice.postAlert("voir", "x", 6, Permissions.SETTINGS_MANAGE) }
        listOf("https://ailleurs.example", "//ailleurs.example", "/\\ailleurs.example", "/.hidden", "/a b", "journal").forEach { link ->
            assertFailsWith<IllegalArgumentException>(link) { backOffice.postAlert("voir", "x", 3, Permissions.SETTINGS_MANAGE, link) }
        }
        // The database refuses such a link too.
        TestDatabase.dataSource.connection.use { connection ->
            assertFailsWith<SQLException> {
                connection.createStatement().use {
                    it.execute("INSERT INTO team_dashboard_alert (id, source, title, level, link, permission, created_at) VALUES (gen_random_uuid(), 'voir', 'x', 1, '/a' || chr(92) || 'b', 'settings.manage', now())")
                }
            }
        }
        assertEquals(listOf(alert.id), backOffice.dashboard(auth(backOffice, admin).actor).alerts.map { it.id })
        assertTrue(backOffice.dashboard(auth(backOffice, moderator).actor).alerts.isEmpty())
        assertEquals("forbidden", backOffice.dismissAlert(auth(backOffice, moderator), alert.id).code())
        backOffice.dismissAlert(auth(backOffice, admin), alert.id).ok()
        assertTrue(backOffice.dashboard(auth(backOffice, admin).actor).alerts.isEmpty())
        assertEquals(listOf("alert.posted", "alert.dismissed"), journalAfter(seq).map { it.action })
    }

    @Test
    fun theDashboardSortsByDeadlineThenLevel() {
        val now = clock.instant().epochSecond
        val queue = object : BackOfficeQueue {
            override val id = "test.priorities"
            override val title = "[EXEMPLE] File de test"
            override val viewPermission = "queue.test.priorities.decide"
            override fun pendingCount() = 4
            override fun page(cursor: String?, limit: Int) = QueuePage(
                listOf(
                    QueueItem("late", "En retard", deadline = now - 60),
                    QueueItem("soon", "Bientôt", deadline = now + 3_600, level = 5),
                    QueueItem("soon-low", "Bientôt, moins important", deadline = now + 3_600, level = 2),
                    QueueItem("far", "Plus tard", deadline = now + 10 * 86_400),
                    QueueItem("urgent", "Sans échéance mais important", level = 5),
                    QueueItem("plain", "Ordinaire"),
                ),
            )
            override fun render(itemId: String, html: kotlinx.html.FlowContent) = Unit
            override fun actions(itemId: String) = emptyList<QueueAction>()
            override fun perform(itemId: String, action: String, reason: String?, actor: TeamActor) = ActionOutcome.NotFound
        }
        val backOffice = BackOfficeTestData.backOffice(clock, queues = BackOfficeQueues().apply { register(queue) })
        val claire = member(backOffice, "Claire", listOf(TeamRole.SUPER_ADMIN))
        val dashboard = backOffice.dashboard(auth(backOffice, claire).actor)
        assertEquals(listOf("late", "soon", "soon-low", "far", "urgent"), dashboard.priorities.map { it.item.id })
        assertTrue(dashboard.priorities[0].overdue)
        assertTrue(dashboard.priorities[1].nearDeadline)
        assertFalse(dashboard.priorities[3].nearDeadline)
        assertEquals(4, dashboard.queues.single().pending)
        assertTrue(backOffice.dashboard(auth(backOffice, member(backOffice, "Modo", listOf(TeamRole.MODERATOR))).actor).queues.isEmpty())
    }

    // Queues ------------------------------------------------------------------------------------------------

    private class DoubleQueue(var offered: Boolean = true, var fails: Boolean = false) : BackOfficeQueue {
        val performed = mutableListOf<String>()
        override val id = "test.double"
        override val title = "[EXEMPLE] Double validation"
        override val viewPermission = "queue.test.double.decide"
        override fun pendingCount() = 1
        override fun page(cursor: String?, limit: Int) = QueuePage(listOf(QueueItem("item-1", "[EXEMPLE] Élément")))
        override fun render(itemId: String, html: kotlinx.html.FlowContent) = Unit
        override fun actions(itemId: String) = if (offered) listOf(QueueAction("publish", "Publier", viewPermission, twoPerson = true)) else emptyList()
        override fun perform(itemId: String, action: String, reason: String?, actor: TeamActor): ActionOutcome {
            if (fails) throw IllegalStateException("broken")
            performed += "$itemId:$action:${actor.pseudonym}"
            return ActionOutcome.Done
        }
    }

    @Test
    fun aTwoPersonQueueActionWaitsForASecondPersonAndIsCheckedAgain() {
        val queue = DoubleQueue()
        val backOffice = BackOfficeTestData.backOffice(clock, queues = BackOfficeQueues().apply { register(queue) })
        val first = member(backOffice, "Première", listOf(TeamRole.SUPER_ADMIN))
        val second = member(backOffice, "Seconde", listOf(TeamRole.SUPER_ADMIN))
        val result = backOffice.performQueueAction(auth(backOffice, first), "test.double", "item-1", "publish", null).ok()
        assertIs<QueueResult.Waiting>(result)
        assertTrue(queue.performed.isEmpty())
        assertEquals("own_request", backOffice.approve(auth(backOffice, first), result.approval.id).code())
        // The queue no longer offers the action: the approval is refused before anything is done.
        queue.offered = false
        assertEquals("action_unavailable", backOffice.approve(auth(backOffice, second), result.approval.id).code())
        queue.offered = true
        val seq = lastSeq()
        backOffice.approve(auth(backOffice, second), result.approval.id).ok()
        assertEquals(listOf("item-1:publish:Seconde"), queue.performed)
        assertEquals(listOf("approval.approved", "queue.test.double.publish.requested", "queue.test.double.publish"), journalAfter(seq).map { it.action })
        assertEquals("done", journalAfter(seq).last().details["outcome"])
    }

    @Test
    fun aQueueActionThatFailsAfterItsApprovalIsRecordedAsFailed() {
        val queue = DoubleQueue(fails = true)
        val backOffice = BackOfficeTestData.backOffice(clock, queues = BackOfficeQueues().apply { register(queue) })
        val first = member(backOffice, "Première", listOf(TeamRole.SUPER_ADMIN))
        val second = member(backOffice, "Seconde", listOf(TeamRole.SUPER_ADMIN))
        val waiting = backOffice.performQueueAction(auth(backOffice, first), "test.double", "item-1", "publish", null).ok() as QueueResult.Waiting
        val seq = lastSeq()
        assertEquals(ApprovalStatus.FAILED, backOffice.approve(auth(backOffice, second), waiting.approval.id).ok())
        assertEquals(ApprovalStatus.FAILED, backOffice.approval(waiting.approval.id)!!.status)
        assertEquals("refused", journalAfter(seq).last().details["outcome"])
    }

    @Test
    fun aQueueActionChecksPermissionAndReasonAndIsRecordedBeforeAndAfter() {
        val queue = object : BackOfficeQueue {
            override val id = "test.reason"
            override val title = "[EXEMPLE] Motif obligatoire"
            override val viewPermission = "queue.test.reason.decide"
            override fun pendingCount() = 1
            override fun page(cursor: String?, limit: Int) = QueuePage(listOf(QueueItem("item-1", "[EXEMPLE] Élément")))
            override fun render(itemId: String, html: kotlinx.html.FlowContent) = Unit
            override fun actions(itemId: String) = if (itemId == "item-1") listOf(QueueAction("reject", "Refuser", viewPermission, reasonRequired = true)) else emptyList()
            override fun perform(itemId: String, action: String, reason: String?, actor: TeamActor) = ActionOutcome.Done
        }
        val long = object : BackOfficeQueue by queue {
            override val id = "test." + "a".repeat(110)
        }
        val backOffice = BackOfficeTestData.backOffice(clock, queues = BackOfficeQueues().apply { register(queue); register(long) })
        val claire = member(backOffice, "Claire", listOf(TeamRole.SUPER_ADMIN))
        val moderator = member(backOffice, "Modo", listOf(TeamRole.MODERATOR))
        assertEquals("not_found", backOffice.performQueueAction(auth(backOffice, claire), "nope", "item-1", "reject", "x").code())
        assertEquals("forbidden", backOffice.performQueueAction(auth(backOffice, moderator), "test.reason", "item-1", "reject", "x").code())
        assertEquals("action_unavailable", backOffice.performQueueAction(auth(backOffice, claire), "test.reason", "item-2", "reject", "x").code())
        assertEquals("action_unavailable", backOffice.performQueueAction(auth(backOffice, claire), "test.reason", "item-1", "approve", null).code())
        assertEquals("reason_required", backOffice.performQueueAction(auth(backOffice, claire), "test.reason", "item-1", "reject", "   ").code())
        // A journal identifier that would not be valid is refused before the action.
        assertEquals("unknown_action", backOffice.performQueueAction(auth(backOffice, claire), long.id, "item-1", "reject", "x").code())
        val seq = lastSeq()
        val result = backOffice.performQueueAction(auth(backOffice, claire), "test.reason", "item-1", "reject", " Hors charte ").ok()
        assertEquals(QueueResult.Performed(ActionOutcome.Done), result)
        val lines = journalAfter(seq)
        assertEquals(listOf("queue.test.reason.reject.requested", "queue.test.reason.reject"), lines.map { it.action })
        val line = lines.last()
        assertEquals("queue_item", line.targetType)
        assertEquals("test.reason:item-1", line.targetId)
        assertEquals("Hors charte", line.reason)
        assertEquals("Claire", line.actorLabel)
        assertEquals(claire.id.toString(), line.actorId)
    }
}
