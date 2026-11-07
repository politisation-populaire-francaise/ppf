package org.politisationpopulairefrancaise.server.backoffice

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.server.TestDatabase
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
        val backOffice = BackOfficeTestData.backOffice(clock)
        val first = backOffice.audit.record(AuditEntry(AuditActor.System, "team.test_line", details = mapOf("n" to "1")))
        val second = backOffice.audit.record(AuditEntry(AuditActor.Unknown, "team.test_line", details = mapOf("n" to "2")))
        assertEquals(first.seq + 1, second.seq)
        assertEquals(first.hash, second.previousHash)
        assertIs<AuditChain.Check.Intact>(backOffice.audit.verifyChain())
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
        assertEquals(second, backOffice.audit.query(AuditFilter(action = "team.test_line"), 10).first())
        assertEquals(listOf(first), backOffice.audit.query(AuditFilter(action = "team", beforeSeq = second.seq), 1))
        assertIs<AuditChain.Check.Intact>(backOffice.audit.verifyChain())
    }

    @Test
    fun concurrentLinesKeepOneChain() {
        val backOffice = BackOfficeTestData.backOffice()
        val threads = (1..8).map { n -> Thread { repeat(5) { backOffice.audit.record(AuditEntry(AuditActor.System, "team.concurrent_line", details = mapOf("n" to "$n"))) } } }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)
        assertIs<AuditChain.Check.Intact>(backOffice.audit.verifyChain())
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

    // Bootstrap and invitations ---------------------------------------------------------------------------

    @Test
    fun theBootstrapCreatesAtMostTwoSuperAdministrators() {
        val backOffice = BackOfficeTestData.backOffice(clock)
        val seq = lastSeq(backOffice)
        val first = backOffice.bootstrapInvitation("Claire").ok()
        assertEquals(listOf(TeamRole.SUPER_ADMIN), first.invitation.proposedRoles)
        assertTrue(first.invitation.bootstrap)
        backOffice.bootstrapInvitation("Seconde").ok()
        assertEquals("bootstrap_closed", backOffice.bootstrapInvitation("Troisième").code())
        // Only the hash of the token is stored.
        TestDatabase.dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                val rows = statement.executeQuery("SELECT token_hash FROM team_invitation")
                val hashes = buildList { while (rows.next()) add(rows.getString(1)) }
                assertTrue(Tokens.hash(first.token) in hashes)
                assertFalse(first.token in hashes)
            }
        }
        assertEquals(listOf("team.bootstrap_invitation", "team.bootstrap_invitation"), journalAfter(backOffice, seq).map { it.action })
        // 48 hours later the invitations are gone, and the bootstrap opens again.
        clock.advance(48.hours + 1.minutes)
        assertNull(backOffice.findInvitation(first.token))
        backOffice.maintain()
        backOffice.bootstrapInvitation("Troisième").ok()
    }

    @Test
    fun theCommandLinePrintsTheLinkOnce() {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val env = mapOf(BackOfficeConfig.ORIGIN_VARIABLE to "http://localhost:8081", "PPF_ENVIRONMENT" to "development")
        val code = BackOfficeCli.run(listOf("bootstrap", "Claire"), env::get, PrintStream(out), PrintStream(err), TestDatabase.dataSource)
        assertEquals(0, code, err.toString())
        val link = out.toString().lines().first { it.startsWith("http") }
        assertTrue(link.startsWith("http://localhost:8081/invitation/"))
        assertNotNull(BackOfficeTestData.backOffice().findInvitation(link.substringAfterLast('/')))
        assertEquals(2, BackOfficeCli.run(listOf("bootstrap"), env::get, PrintStream(out), PrintStream(err), TestDatabase.dataSource))
        assertEquals(2, BackOfficeCli.run(listOf("bootstrap", "X"), { null }, PrintStream(out), PrintStream(err), TestDatabase.dataSource))
    }

    @Test
    fun anInvitationIsAcceptedOnceWithAKeyAndProposedRolesWaitForASecondPerson() {
        val backOffice = BackOfficeTestData.backOffice(clock)
        val admin = member(backOffice, "Admin", listOf(TeamRole.ADMIN))
        val issued = backOffice.createInvitation(auth(backOffice, admin), "Nouvelle", listOf(TeamRole.MODERATOR), null, null).ok()
        val options = backOffice.startEnrolment(issued.token).ok()
        val key = TestAuthenticator()
        val opened = backOffice.finishEnrolment(key.register(options, name = "Téléphone")).ok()
        val newcomer = backOffice.authenticate(opened.token)!!
        assertEquals("Nouvelle", newcomer.account.pseudonym)
        assertEquals(admin.id, newcomer.account.invitedBy)
        assertTrue(newcomer.actor.permissions.isEmpty())
        // The link is used up.
        assertNull(backOffice.findInvitation(issued.token))
        assertEquals("invitation_gone", backOffice.startEnrolment(issued.token).code())
        // The proposed role waits for someone else than the inviter.
        val pending = backOffice.approvals.open(clock.instant()).single()
        assertEquals(BackOffice.ACTION_GRANT, pending.action)
        assertEquals(admin.id, pending.requestedBy)
        assertEquals("own_request", backOffice.approve(auth(backOffice, admin), pending.id).code())
        val other = member(backOffice, "Autre admin", listOf(TeamRole.ADMIN))
        assertEquals(ApprovalStatus.APPROVED, backOffice.approve(auth(backOffice, other), pending.id).ok())
        val grant = backOffice.grants.live(newcomer.account.id, clock.instant()).single()
        assertEquals(TeamRole.MODERATOR, grant.role)
        assertEquals(admin.id, grant.grantedBy)
        assertEquals(other.id, grant.approvedBy)
        // A single key: the role is there, its permissions wait for a backup key.
        val reloaded = backOffice.authenticate(opened.token)!!
        assertTrue(reloaded.actor.needsBackupKey)
        assertFalse(reloaded.actor.has(Permissions.queueDecide("agir.held")))
        val second = TestAuthenticator()
        backOffice.finishAddKey(reloaded, second.register(backOffice.startAddKey(reloaded).ok())).ok()
        assertTrue(backOffice.authenticate(opened.token)!!.actor.has(Permissions.queueDecide("agir.held")))
    }

    @Test
    fun aKeyWithoutUserVerificationOrFromAnotherOriginIsRefused() {
        val backOffice = BackOfficeTestData.backOffice(clock)
        val issued = backOffice.bootstrapInvitation("Claire").ok()
        val withoutPin = TestAuthenticator(userVerified = false).register(backOffice.startEnrolment(issued.token).ok())
        assertEquals("registration_refused", backOffice.finishEnrolment(withoutPin).code())
        val phishing = TestAuthenticator().register(backOffice.startEnrolment(issued.token).ok(), clientOrigin = "https://equipe.example.org")
        assertEquals("registration_refused", backOffice.finishEnrolment(phishing).code())
        val good = TestAuthenticator()
        val options = backOffice.startEnrolment(issued.token).ok()
        val response = good.register(options)
        backOffice.finishEnrolment(response).ok()
        // A ceremony serves once.
        assertEquals("ceremony_gone", backOffice.finishEnrolment(response).code())
    }

    // Sign-in ---------------------------------------------------------------------------------------------

    @Test
    fun signInWorksWithARegisteredKeyAndIsRecorded() {
        val backOffice = BackOfficeTestData.backOffice(clock)
        val claire = member(backOffice, "Claire", listOf(TeamRole.SUPER_ADMIN))
        val seq = lastSeq(backOffice)
        val options = backOffice.startSignIn().ok()
        assertTrue(options.publicKey.allowCredentials.isEmpty())
        assertEquals("required", options.publicKey.userVerification)
        val opened = backOffice.finishSignIn(claire.keys[0].assertion(options)).ok()
        assertEquals(claire.id, backOffice.authenticate(opened.token)!!.account.id)
        // An unknown key and a replayed signature counter are refused and recorded, without address.
        val stranger = TestAuthenticator()
        stranger.userHandle = Tokens.randomBytes(32)
        assertEquals("unknown_key", backOffice.finishSignIn(stranger.assertion(backOffice.startSignIn().ok())).code())
        claire.keys[0].counter = 0
        assertEquals("assertion_refused", backOffice.finishSignIn(claire.keys[0].assertion(backOffice.startSignIn().ok())).code())
        assertEquals(listOf("auth.login", "auth.login_failed", "auth.login_failed"), journalAfter(backOffice, seq).map { it.action })
        assertEquals(BackOfficeTexts.ACTOR_UNKNOWN, journalAfter(backOffice, seq)[1].actorLabel)
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
        // Only the hash is stored.
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
        assertEquals("confirmation_required", backOffice.startAddKey(stale).code())
        val options = backOffice.startConfirmation(stale).ok()
        assertEquals(2, options.publicKey.allowCredentials.size)
        backOffice.finishConfirmation(stale, claire.keys[1].assertion(options)).ok()
        val fresh = auth(backOffice, claire)
        assertTrue(fresh.isFresh(clock.instant()))
        backOffice.requestGrant(fresh, target.id, TeamRole.MODERATOR, null, null, null).ok()
        // A confirmation belongs to its session: another session cannot use it.
        val other = member(backOffice, "Autre", listOf(TeamRole.SUPER_ADMIN))
        val stolen = backOffice.startConfirmation(auth(backOffice, claire)).ok()
        assertEquals("ceremony_gone", backOffice.finishConfirmation(auth(backOffice, other), other.keys[0].assertion(stolen)).code())
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
        assertTrue(backOffice.grants.live(target.id, clock.instant()).isEmpty())
        assertEquals("forbidden", backOffice.approve(auth(backOffice, moderator), request.id).code())
        assertEquals("own_request", backOffice.approve(auth(backOffice, admin), request.id).code())
        assertEquals("forbidden", backOffice.approve(auth(backOffice, member(backOffice, "Sans rôle")), request.id).code())
        val second = member(backOffice, "Admin2", listOf(TeamRole.ADMIN))
        backOffice.approve(auth(backOffice, second), request.id).ok()
        assertEquals("approval_gone", backOffice.approve(auth(backOffice, second), request.id).code())
        assertEquals(TeamRole.EDITOR, backOffice.grants.live(target.id, clock.instant()).single().role)
    }

    @Test
    fun theTargetNeverApprovesTheirOwnRole() {
        val backOffice = BackOfficeTestData.backOffice(clock)
        val admin = member(backOffice, "Admin", listOf(TeamRole.ADMIN))
        val target = member(backOffice, "Admin cible", listOf(TeamRole.ADMIN))
        val request = backOffice.requestGrant(auth(backOffice, admin), target.id, TeamRole.MODERATOR, null, null, null).ok()
        assertEquals("own_grant", backOffice.approve(auth(backOffice, target), request.id).code())
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
        val grant = backOffice.grants.live(target.id, clock.instant()).single()
        // Removing the admin role waits for a second super administrator too.
        val revocation = backOffice.revokeGrant(auth(backOffice, superAdmin), grant.id, "Fin de mission").ok()
        assertIs<Revocation.Waiting>(revocation)
        assertTrue(backOffice.grants.live(target.id, clock.instant()).isNotEmpty())
        backOffice.approve(auth(backOffice, second), revocation.approval.id).ok()
        assertTrue(backOffice.grants.live(target.id, clock.instant()).isEmpty())
        // An ordinary role goes at once.
        val moderatorRequest = backOffice.requestGrant(auth(backOffice, admin), target.id, TeamRole.MODERATOR, null, null, null).ok()
        backOffice.approve(auth(backOffice, superAdmin), moderatorRequest.id).ok()
        val moderatorGrant = backOffice.grants.live(target.id, clock.instant()).single()
        assertEquals(Revocation.Done, backOffice.revokeGrant(auth(backOffice, admin), moderatorGrant.id, null).ok())
        assertTrue(backOffice.grants.live(target.id, clock.instant()).isEmpty())
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
        // Even before the periodic task runs, a grant past its end gives nothing.
        assertTrue(backOffice.grants.live(target.id, clock.instant()).isEmpty())
        val seq = lastSeq(backOffice)
        assertEquals(1, backOffice.maintain().expiredGrants)
        assertEquals(GrantStatus.EXPIRED, backOffice.grants.history(target.id).single().status)
        assertEquals(listOf("team.role_expired"), journalAfter(backOffice, seq).map { it.action })
        assertEquals(BackOfficeTexts.ACTOR_SYSTEM, journalAfter(backOffice, seq).single().actorLabel)
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
        assertEquals(ApprovalStatus.EXPIRED, backOffice.approvals.find(expiring.id)!!.status)
        assertEquals(ApprovalStatus.CANCELLED, backOffice.approvals.find(cancelled.id)!!.status)
        assertEquals(ApprovalStatus.REFUSED, backOffice.approvals.find(refused.id)!!.status)
        // Thirty days after the decision, the requests are deleted; the journal keeps their trace.
        clock.advance(31.days)
        backOffice.maintain()
        assertNull(backOffice.approvals.find(expiring.id))
    }

    // Emergency cut and removal -----------------------------------------------------------------------------

    @Test
    fun theEmergencyCutTakesEverythingAtOnce() {
        val backOffice = BackOfficeTestData.backOffice(clock)
        val admin = member(backOffice, "Admin", listOf(TeamRole.ADMIN))
        val moderator = member(backOffice, "Modo", listOf(TeamRole.MODERATOR))
        val pending = backOffice.requestGrant(auth(backOffice, admin), moderator.id, TeamRole.EDITOR, null, null, null).ok()
        val seq = lastSeq(backOffice)
        val report = backOffice.emergencyCut(auth(backOffice, admin), moderator.id, "Téléphone volé").ok()
        assertEquals(CutReport(sessions = 1, grants = 1, keys = 2, validationSessions = 0, requests = 1), report)
        assertNull(backOffice.authenticate(moderator.token))
        assertEquals(AccountStatus.SUSPENDED, backOffice.accounts.find(moderator.id)!!.status)
        assertTrue(backOffice.accounts.credentials(moderator.id).isEmpty())
        assertEquals(ApprovalStatus.FAILED, backOffice.approvals.find(pending.id)!!.status)
        assertEquals("unknown_key", backOffice.finishSignIn(moderator.keys[0].assertion(backOffice.startSignIn().ok())).code())
        val line = journalAfter(backOffice, seq).single { it.action == "team.emergency_cut" }
        assertEquals("Téléphone volé", line.reason)
        assertEquals("moderator", line.details["roles"])
    }

    @Test
    fun anyoneCutsThemselvesButOnlySuperAdministratorsCutAnAdministrator() {
        val backOffice = BackOfficeTestData.backOffice(clock)
        val admin = member(backOffice, "Admin", listOf(TeamRole.ADMIN))
        val otherAdmin = member(backOffice, "Admin2", listOf(TeamRole.ADMIN))
        val moderator = member(backOffice, "Modo", listOf(TeamRole.MODERATOR))
        assertEquals("forbidden", backOffice.emergencyCut(auth(backOffice, moderator), admin.id, null).code())
        assertEquals("forbidden", backOffice.emergencyCut(auth(backOffice, admin), otherAdmin.id, null).code())
        clock.advance(10.minutes)
        // Cutting oneself needs no new verification: it only removes access.
        backOffice.emergencyCut(auth(backOffice, moderator), moderator.id, null).ok()
        val superAdmin = member(backOffice, "Super", listOf(TeamRole.SUPER_ADMIN))
        backOffice.removeMember(auth(backOffice, superAdmin), otherAdmin.id, "Départ").ok()
        val removed = backOffice.accounts.find(otherAdmin.id)!!
        assertEquals(AccountStatus.REMOVED, removed.status)
        assertNull(removed.nostrPubkey)
        // The pseudonym of a removed member may be used again.
        member(backOffice, "Admin2")
    }

    @Test
    fun keysAreAddedAndDeletedButNeverTheLastOne() {
        val backOffice = BackOfficeTestData.backOffice(clock)
        val claire = member(backOffice, "Claire", keys = 1)
        val key = backOffice.accounts.credentials(claire.id).single()
        assertEquals("last_key", backOffice.deleteKey(auth(backOffice, claire), key.id).code())
        val second = TestAuthenticator()
        val added = backOffice.finishAddKey(auth(backOffice, claire), second.register(backOffice.startAddKey(auth(backOffice, claire)).ok(), name = "Clé USB")).ok()
        assertEquals("Clé USB", added.name)
        // The same authenticator cannot be registered twice.
        assertEquals("key_known", backOffice.finishAddKey(auth(backOffice, claire), second.register(backOffice.startAddKey(auth(backOffice, claire)).ok())).code())
        assertEquals("Clé 0", backOffice.deleteKey(auth(backOffice, claire), key.id).ok())
        // The model of the key is not kept.
        val stored = backOffice.accounts.credentials(claire.id).single()
        assertTrue(stored.attestedData.copyOfRange(0, 16).all { it == 0.toByte() })
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
        val grant = backOffice.grants.live(volunteer.id, clock.instant()).single()
        assertEquals(organisation, grant.organisationPubkey)
        // One key is enough for a validator's own session.
        assertTrue(auth(backOffice, volunteer).actor.has(Permissions.VALIDATION_SESSION_OPEN))
    }

    @Test
    fun aValidationSessionAuthenticatesTheApplicationUntilItEnds() = runBlocking {
        val verified = mutableSetOf(organisation)
        val backOffice = BackOffice(BackOfficeTestData.CONFIG, TestDatabase.dataSource, isVerifiedOrganisation = { it in verified }, verifiedOrganisations = { verified }, clock = clock)
        val volunteer = member(backOffice, "Bénévole", listOf(TeamRole.VALIDATOR), keys = 1, organisation = organisation)
        val sessionKeys = IdentityKeys.generate()
        val session = backOffice.openValidationSession(auth(backOffice, volunteer), sessionKeys.publicKey.npub, 2, 4.hours).ok()
        assertEquals("2026-W45", session.week)
        assertEquals("session_already_open", backOffice.openValidationSession(auth(backOffice, volunteer), IdentityKeys.generate().publicKey.hex, 2, 4.hours).code())
        assertEquals("bad_ceiling", backOffice.openValidationSession(auth(backOffice, volunteer), IdentityKeys.generate().publicKey.hex, 41, 4.hours).code())
        val url = "https://equipe.example.org/api/v1/urne/validation"
        suspend fun request(keys: IdentityKeys = sessionKeys): SignedSessionRequest {
            clock.advance(1.seconds)
            val event = LocalEventSigner(keys).sign(kind = MinimalNip98Verifier.KIND, createdAt = clock.instant().epochSecond, tags = listOf(listOf("u", url), listOf("method", "POST")))
            return SignedSessionRequest("Nostr " + Base64.getEncoder().encodeToString(event.toJson().toByteArray()), url, "POST")
        }
        val granted = backOffice.validationGate.authorize(request())
        assertIs<SessionAccess.Granted>(granted)
        assertEquals(session.id, granted.session.id)
        assertEquals(SessionAccess.Refused("no_open_session"), backOffice.validationGate.authorize(request(IdentityKeys.generate())))
        // Counters only, up to the ceiling.
        assertTrue(backOffice.validationGate.recordValidation(granted.session))
        assertTrue(backOffice.validationGate.recordValidation(granted.session))
        assertFalse(backOffice.validationGate.recordValidation(granted.session))
        assertEquals(2, backOffice.validationSessions.find(session.id)!!.validations)
        // The organisation loses its verification: refused at once, then suspended by the periodic check.
        verified.clear()
        assertEquals(SessionAccess.Refused("organisation_not_verified"), backOffice.validationGate.authorize(request()))
        val seq = lastSeq(backOffice)
        assertEquals(1, backOffice.maintain().suspendedValidators)
        assertEquals(ValidationSessionState.REVOKED, backOffice.validationSessions.find(session.id)!!.state)
        assertEquals(GrantStatus.SUSPENDED, backOffice.grants.history(volunteer.id).single().status)
        assertEquals(listOf("team.validator_suspended"), journalAfter(backOffice, seq).map { it.action })
        verified += organisation
        assertEquals(SessionAccess.Refused("no_open_session"), backOffice.validationGate.authorize(request()))
    }

    @Test
    fun anExpiredOrRevokedSessionIsRefusedAtOnce() = runBlocking {
        val backOffice = BackOfficeTestData.backOffice(clock, verified = setOf(organisation))
        val volunteer = member(backOffice, "Bénévole", listOf(TeamRole.VALIDATOR), keys = 1, organisation = organisation)
        val url = "https://equipe.example.org/x"
        suspend fun request(keys: IdentityKeys): SignedSessionRequest {
            clock.advance(1.seconds)
            val event = LocalEventSigner(keys).sign(kind = MinimalNip98Verifier.KIND, createdAt = clock.instant().epochSecond, tags = listOf(listOf("u", url), listOf("method", "GET")))
            return SignedSessionRequest("Nostr " + Base64.getEncoder().encodeToString(event.toJson().toByteArray()), url, "GET")
        }
        val first = IdentityKeys.generate()
        val session = backOffice.openValidationSession(auth(backOffice, volunteer), first.publicKey.hex, 40, 1.hours).ok()
        assertIs<SessionAccess.Granted>(backOffice.validationGate.authorize(request(first)))
        clock.advance(61.minutes)
        assertEquals(SessionAccess.Refused("no_open_session"), backOffice.validationGate.authorize(request(first)))
        // The volunteer signs in again; the key of a session never serves twice, a new one opens.
        val again = backOffice.finishSignIn(volunteer.keys[0].assertion(backOffice.startSignIn().ok())).ok()
        val volunteerAuth = backOffice.authenticate(again.token)!!
        assertEquals("session_key_used", backOffice.openValidationSession(volunteerAuth, first.publicKey.hex, 40, 1.hours).code())
        assertEquals(ValidationSessionState.EXPIRED, backOffice.validationSessions.find(session.id)!!.state)
        val second = IdentityKeys.generate()
        val reopened = backOffice.openValidationSession(volunteerAuth, second.publicKey.hex, 40, 1.hours).ok()
        assertIs<SessionAccess.Granted>(backOffice.validationGate.authorize(request(second)))
        val admin = member(backOffice, "Admin", listOf(TeamRole.ADMIN))
        backOffice.revokeValidationSession(auth(backOffice, admin), reopened.id, "Fin de l'événement").ok()
        assertEquals(SessionAccess.Refused("no_open_session"), backOffice.validationGate.authorize(request(second)))
        // Closing it again is refused: it is no longer open.
        assertEquals("already_closed", backOffice.closeValidationSession(volunteerAuth, reopened.id).code())
    }

    // Alerts ------------------------------------------------------------------------------------------------

    @Test
    fun alertsAreShownToWhoMayDismissThemAndRecorded() {
        val backOffice = BackOfficeTestData.backOffice(clock)
        val admin = member(backOffice, "Admin", listOf(TeamRole.ADMIN))
        val moderator = member(backOffice, "Modo", listOf(TeamRole.MODERATOR))
        val seq = lastSeq(backOffice)
        val alert = backOffice.postAlert("voir", "[EXEMPLE] Import des données ouvertes refusé", 4, Permissions.SETTINGS_MANAGE, "/journal")
        assertFailsWith<IllegalArgumentException> { backOffice.postAlert("voir", "x", 6, Permissions.SETTINGS_MANAGE) }
        assertFailsWith<IllegalArgumentException> { backOffice.postAlert("voir", "x", 3, Permissions.SETTINGS_MANAGE, "https://ailleurs.example") }
        assertEquals(listOf(alert.id), backOffice.dashboard(auth(backOffice, admin).actor).alerts.map { it.id })
        assertTrue(backOffice.dashboard(auth(backOffice, moderator).actor).alerts.isEmpty())
        assertEquals("forbidden", backOffice.dismissAlert(auth(backOffice, moderator), alert.id).code())
        backOffice.dismissAlert(auth(backOffice, admin), alert.id).ok()
        assertTrue(backOffice.dashboard(auth(backOffice, admin).actor).alerts.isEmpty())
        assertEquals(listOf("alert.posted", "alert.dismissed"), journalAfter(backOffice, seq).map { it.action })
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
        val queues = BackOfficeQueues().apply { register(queue) }
        val backOffice = BackOfficeTestData.backOffice(clock, queues = queues)
        val claire = member(backOffice, "Claire", listOf(TeamRole.SUPER_ADMIN))
        val dashboard = backOffice.dashboard(auth(backOffice, claire).actor)
        assertEquals(listOf("late", "soon", "soon-low", "far", "urgent"), dashboard.priorities.map { it.item.id })
        assertTrue(dashboard.priorities[0].overdue)
        assertTrue(dashboard.priorities[1].nearDeadline)
        assertFalse(dashboard.priorities[3].nearDeadline)
        assertEquals(4, dashboard.queues.single().pending)
        assertTrue(backOffice.dashboard(auth(backOffice, member(backOffice, "Modo", listOf(TeamRole.MODERATOR))).actor).queues.isEmpty())
    }

    @Test
    fun aTwoPersonQueueActionWaitsForASecondPerson() {
        val performed = mutableListOf<String>()
        val queue = object : BackOfficeQueue {
            override val id = "test.double"
            override val title = "[EXEMPLE] Double validation"
            override val viewPermission = "queue.test.double.decide"
            override fun pendingCount() = 1
            override fun page(cursor: String?, limit: Int) = QueuePage(listOf(QueueItem("item-1", "[EXEMPLE] Élément")))
            override fun render(itemId: String, html: kotlinx.html.FlowContent) = Unit
            override fun actions(itemId: String) = listOf(QueueAction("publish", "Publier", viewPermission, twoPerson = true))
            override fun perform(itemId: String, action: String, reason: String?, actor: TeamActor): ActionOutcome {
                performed += "$itemId:$action:${actor.pseudonym}"
                return ActionOutcome.Done
            }
        }
        val backOffice = BackOfficeTestData.backOffice(clock, queues = BackOfficeQueues().apply { register(queue) })
        val first = member(backOffice, "Première", listOf(TeamRole.SUPER_ADMIN))
        val second = member(backOffice, "Seconde", listOf(TeamRole.SUPER_ADMIN))
        val result = backOffice.performQueueAction(auth(backOffice, first), "test.double", "item-1", "publish", null).ok()
        assertIs<QueueResult.Waiting>(result)
        assertTrue(performed.isEmpty())
        assertEquals("own_request", backOffice.approve(auth(backOffice, first), result.approval.id).code())
        val seq = lastSeq(backOffice)
        backOffice.approve(auth(backOffice, second), result.approval.id).ok()
        assertEquals(listOf("item-1:publish:Seconde"), performed)
        assertEquals(listOf("approval.approved", "queue.test.double.publish"), journalAfter(backOffice, seq).map { it.action })
        assertEquals("done", journalAfter(backOffice, seq).last().details["outcome"])
    }

    @Test
    fun aQueueActionChecksPermissionAndReasonAndIsRecorded() {
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
        val backOffice = BackOfficeTestData.backOffice(clock, queues = BackOfficeQueues().apply { register(queue) })
        val claire = member(backOffice, "Claire", listOf(TeamRole.SUPER_ADMIN))
        val moderator = member(backOffice, "Modo", listOf(TeamRole.MODERATOR))
        assertEquals("not_found", backOffice.performQueueAction(auth(backOffice, claire), "nope", "item-1", "reject", "x").code())
        assertEquals("forbidden", backOffice.performQueueAction(auth(backOffice, moderator), "test.reason", "item-1", "reject", "x").code())
        assertEquals("action_unavailable", backOffice.performQueueAction(auth(backOffice, claire), "test.reason", "item-2", "reject", "x").code())
        assertEquals("action_unavailable", backOffice.performQueueAction(auth(backOffice, claire), "test.reason", "item-1", "approve", null).code())
        assertEquals("reason_required", backOffice.performQueueAction(auth(backOffice, claire), "test.reason", "item-1", "reject", "   ").code())
        val seq = lastSeq(backOffice)
        val result = backOffice.performQueueAction(auth(backOffice, claire), "test.reason", "item-1", "reject", " Hors charte ").ok()
        assertEquals(QueueResult.Performed(ActionOutcome.Done), result)
        val line = journalAfter(backOffice, seq).single()
        assertEquals("queue.test.reason.reject", line.action)
        assertEquals("queue_item", line.targetType)
        assertEquals("test.reason:item-1", line.targetId)
        assertEquals("Hors charte", line.reason)
        assertEquals("Claire", line.actorLabel)
        assertEquals(claire.id.toString(), line.actorId)
    }
}
