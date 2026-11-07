package org.politisationpopulairefrancaise.server.backoffice

import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The result of an action of the base: done, or refused with a stable code ([BackOfficeTexts.error] gives the French). */
sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>

    data class Denied(val code: String) : Outcome<Nothing>
}

/** A signed-in member during one request: their session, account, keys and permissions. */
class Authenticated(
    val session: TeamSession,
    /** The token of the cookie (only in memory, to derive the anti-forgery token). */
    val token: String,
    val account: TeamAccount,
    val actor: TeamActor,
    val credentialCount: Int,
) {
    val csrf: String get() = Csrf.tokenFor(token)

    /** Whether a passkey was verified in this session less than 5 minutes before [now]. */
    fun isFresh(now: Instant): Boolean = !session.verifiedAt.plusSeconds(BackOfficeConfig.REAUTH_WINDOW.inWholeSeconds).isBefore(now)
}

/** What a queue action led to: performed now, or waiting for a second person. */
sealed interface QueueResult {
    data class Performed(val outcome: ActionOutcome) : QueueResult

    data class Waiting(val approval: PendingApproval) : QueueResult
}

/** A change of the team: done at once, or waiting for a second person (S-61). */
sealed interface TeamChange {
    /** Done; [report] gives the counts of a removal. */
    data class Done(val report: CutReport? = null) : TeamChange

    data class Waiting(val approval: PendingApproval) : TeamChange
}

/** One item of the dashboard's priority list (S-64, S-115). */
data class PriorityItem(val queue: BackOfficeQueue, val item: QueueItem, val overdue: Boolean, val nearDeadline: Boolean)

/** A queue as the dashboard shows it: its count, or null when it could not be read. */
data class QueueCount(val queue: BackOfficeQueue, val pending: Int?)

data class Dashboard(
    val queues: List<QueueCount>,
    val priorities: List<PriorityItem>,
    val alerts: List<DashboardAlert>,
    val approvalsToDecide: Int,
)

/** What the pages show of a key: never its public key nor its counter. */
data class KeyInfo(val id: UUID, val name: String, val createdAt: Instant, val lastUsedAt: Instant?)

/** Counts of an emergency cut or a removal. */
data class CutReport(
    val sessions: Int,
    /** Roles disabled by a cut (kept, unusable until reinstatement) or revoked by a removal. */
    val grants: Int,
    /** Keys disabled by a cut (kept) or deleted by a removal. */
    val keys: Int,
    val validationSessions: Int,
    val requests: Int,
    val invitations: Int,
)

/**
 * The team back-office base (contract docs/integration/back-office.md, step 1): accounts by invitation,
 * passkeys, sessions, roles and permissions, temporary access, two-person rule, emergency cut, journal,
 * queues, alerts and validation sessions. Every action of the team goes through this class, which checks
 * the permission, the freshness of the passkey for sensitive actions, and records the journal line.
 *
 * Its stores are private (D-127): the poles see read-only views and the audited operations only.
 * All calls block on JDBC: routes call them on Dispatchers.IO.
 */
class BackOffice(
    val config: BackOfficeConfig,
    private val dataSource: DataSource,
    val queues: BackOfficeQueues = BackOfficeQueues(),
    /** Whether a key is a verified organisation of the Agir directory (null when the Agir space is off). */
    private val isVerifiedOrganisation: ((String) -> Boolean)? = null,
    /** Every verified organisation key, for the periodic check of the validators (null when Agir is off). */
    private val verifiedOrganisations: (() -> Set<String>)? = null,
    private val passkeys: PasskeyVerifier = WebAuthn4jPasskeys(config.origin, config.rpId),
    val clock: Clock = Clock.systemUTC(),
    sessionRequestVerifier: SessionRequestVerifier = MinimalNip98Verifier(clock.instant()),
) {
    private val audit = AuditLog(dataSource, clock)
    private val accounts = TeamAccounts(dataSource)
    private val invitations = TeamInvitations(dataSource)
    private val sessions = TeamSessions(dataSource)
    private val grants = TeamGrants(dataSource)
    private val approvals = PendingApprovals(dataSource)
    private val validationSessions = ValidationSessions(dataSource)
    private val alerts = DashboardAlerts(dataSource)

    /** Sign-in: no state until a signature is verified (D-127). */
    private val signInChallenges = SignInChallenges(clock)

    /** Confirmations and new keys: one pending ceremony per session. */
    private val sessionCeremonies = CeremonySlots<String>(clock)

    /** Registrations from an invitation: one pending ceremony per invitation. */
    private val enrolments = CeremonySlots<UUID>(clock)

    private val failures = AnonymousFailures(clock)

    /** The size of the verified set at the last check of the validators that was trusted (item 14 of the review). */
    @Volatile
    private var verifiedReference: Int? = null

    @Volatile
    private var journalAlertPosted = false

    /** NIP-44 towards `PPF_AUDIT_PUBLIC_KEY` (contract, section 8); null when the key is not configured. */
    val auditSealer: AuditSealer? = config.auditPublicKey?.let(::AuditSealer)

    /** The door of Décider's validation endpoints (NIP-98 with a session key). */
    val validationGate = ValidationSessionGate(validationSessions, sessionRequestVerifier, ::organisationVerified, clock)

    private val log = LoggerFactory.getLogger(BackOffice::class.java)

    private fun now(): Instant = clock.nowSeconds()

    private fun organisationVerified(key: String): Boolean = isVerifiedOrganisation?.invoke(key) == true

    // Read-only views -------------------------------------------------------------------------------

    fun members(): List<TeamAccount> = accounts.all()

    fun member(id: UUID): TeamAccount? = accounts.find(id)

    fun keysOf(accountId: UUID): List<KeyInfo> = accounts.credentials(accountId).map { KeyInfo(it.id, it.name, it.createdAt, it.lastUsedAt) }

    fun liveGrants(accountId: UUID): List<RoleGrant> = grants.live(accountId, clock.instant())

    fun grantHistory(accountId: UUID): List<RoleGrant> = grants.history(accountId)

    fun pendingInvitations(): List<TeamInvitation> = invitations.pending(clock.instant())

    fun findInvitation(token: String): TeamInvitation? = invitations.findValid(token, now())

    fun approval(id: UUID): PendingApproval? = approvals.find(id)

    fun validationSession(id: UUID): ValidationSession? = validationSessions.find(id)

    /** The open session of [accountId] that may still be used. */
    fun usableValidationSessionOf(accountId: UUID): ValidationSession? = validationSessions.openOf(accountId)?.takeIf { it.isUsable(clock.instant()) }

    fun openValidationSessions(): List<ValidationSession> = validationSessions.allOpen()

    // Journal (read side) ---------------------------------------------------------------------------

    fun journal(actor: TeamActor, filter: AuditFilter, limit: Int = 100): Outcome<List<AuditRecord>> =
        if (!actor.has(Permissions.AUDIT_READ)) Outcome.Denied("forbidden") else Outcome.Ok(audit.query(filter, limit))

    /** Checks the chain from the last verified line; a broken or shortened journal posts an alert once. */
    fun verifyJournal(actor: TeamActor): Outcome<AuditChain.Check> =
        if (!actor.has(Permissions.AUDIT_READ)) Outcome.Denied("forbidden") else Outcome.Ok(checkJournal())

    private fun checkJournal(): AuditChain.Check {
        val check = audit.verifyChain()
        if (check !is AuditChain.Check.Intact && !journalAlertPosted) {
            journalAlertPosted = true
            runCatching { postAlert(ALERT_SOURCE, BackOfficeTexts.ALERT_JOURNAL_BROKEN, 5, Permissions.AUDIT_READ, "/journal") }
        }
        return check
    }

    /** The last line of the journal (number, hash, date), for an external anchor (D-111): nothing secret. */
    fun journalHead(): AuditHead? = audit.head()

    /** The JSON export of the journal: a sensitive reading, itself recorded. */
    fun exportJournal(auth: Authenticated, filter: AuditFilter): Outcome<List<AuditRecord>> {
        if (!auth.actor.has(Permissions.AUDIT_READ)) return Outcome.Denied("forbidden")
        val lines = audit.query(filter, AuditLog.MAX_PAGE)
        audit.record(AuditEntry(AuditActor.of(auth.actor), "audit.exported", details = mapOf("lines" to lines.size.toString())))
        return Outcome.Ok(lines)
    }

    // Server operator (command line) ----------------------------------------------------------------

    /**
     * The invitation of a first super administrator, created by the command `bootstrap` on the server
     * (nobody exists yet to invite). Allowed while fewer than two **active** super administrators exist,
     * counting the bootstrap invitations still valid: the contract wants two, and the second one cannot be
     * approved by a single person. Also the way back if both keys were lost.
     */
    fun bootstrapInvitation(pseudonym: String): Outcome<IssuedInvitation> {
        val name = Pseudonyms.normalize(pseudonym) ?: return Outcome.Denied("bad_pseudonym")
        val now = now()
        return dataSource.transaction { connection ->
            connection.advisoryLock("team_bootstrap")
            val existing = grants.countLiveHolders(connection, TeamRole.SUPER_ADMIN, now) + invitations.pendingBootstrap(connection, now)
            if (existing >= 2) return@transaction Outcome.Denied("bootstrap_closed")
            if (accounts.pseudonymTaken(connection, name)) return@transaction Outcome.Denied("pseudonym_taken")
            val issued = invitations.create(connection, name, listOf(TeamRole.SUPER_ADMIN), null, null, null, now)
            audit.record(
                connection,
                AuditEntry(AuditActor.System, "team.bootstrap_invitation", "team_invitation", issued.invitation.id.toString(), details = mapOf("pseudonym" to name)),
            )
            Outcome.Ok(issued)
        }
    }

    /** Withdraws the bootstrap invitations still valid (command line only: the pages cannot). */
    fun withdrawBootstrapInvitations(): Int = dataSource.transaction { connection ->
        val count = invitations.deleteBootstrap(connection)
        if (count > 0) audit.record(connection, AuditEntry(AuditActor.System, "team.invitation_withdrawn", details = mapOf("bootstrap" to count.toString())))
        count
    }

    /** The server operator cuts the active member [pseudonym] (journaled as « système »). */
    fun operatorCut(pseudonym: String, reason: String?): Outcome<CutReport> {
        val now = now()
        return dataSource.transaction { connection ->
            val target = accounts.byPseudonym(connection, pseudonym.trim(), AccountStatus.ACTIVE).singleOrNull() ?: return@transaction Outcome.Denied("not_found")
            Outcome.Ok(suspendIn(connection, target.id, AuditActor.System, cleanReason(reason), now))
        }
    }

    /** The server operator reinstates the suspended member [pseudonym] (journaled as « système »). */
    fun operatorReinstate(pseudonym: String, reason: String?): Outcome<Unit> {
        return dataSource.transaction { connection ->
            val candidates = accounts.byPseudonym(connection, pseudonym.trim(), AccountStatus.SUSPENDED)
            val target = when (candidates.size) {
                0 -> return@transaction Outcome.Denied("not_found")
                1 -> candidates.single()
                else -> return@transaction Outcome.Denied("ambiguous")
            }
            reinstateIn(connection, target, AuditActor.System, cleanReason(reason))
        }
    }

    // Enrolment -------------------------------------------------------------------------------------

    fun startEnrolment(token: String): Outcome<CreationOptionsResponse> {
        val invitation = invitations.findValid(token, now()) ?: return Outcome.Denied("invitation_gone")
        val handle = Tokens.randomBytes(32)
        val (id, ceremony) = enrolments.start(invitation.id) { challenge, at -> Ceremony.Enrol(challenge, at, invitation.id, handle) }
        return Outcome.Ok(creationOptions("${invitation.id}.$id", ceremony.challenge, handle, invitation.pseudonym, emptyList()))
    }

    /**
     * Registers the first key from an invitation: creates the account, its key and a session. Everything
     * is checked before the invitation is used, so that a refusal leaves it valid.
     */
    fun finishEnrolment(response: RegistrationResponse): Outcome<OpenedSession> {
        val invitationId = runCatching { UUID.fromString(response.ceremony.substringBefore('.')) }.getOrNull() ?: return Outcome.Denied("ceremony_gone")
        val ceremony = enrolments.take(invitationId, response.ceremony.substringAfter('.', "")) as? Ceremony.Enrol ?: return Outcome.Denied("ceremony_gone")
        val name = KeyNames.normalize(response.name) ?: BackOfficeTexts.DEFAULT_KEY_NAME
        val credential = try {
            passkeys.verifyRegistration(response, ceremony.challenge, name)
        } catch (e: PasskeyException) {
            // No account is known yet: counted, written as one line per period (D-127).
            failures.count("enrolment_${e.code}")
            return Outcome.Denied(e.code)
        }
        val now = now()
        return dataSource.transaction { connection ->
            val invitation = invitations.find(connection, ceremony.invitationId)?.takeIf { it.expiresAt.isAfter(now) } ?: return@transaction Outcome.Denied("invitation_gone")
            if (accounts.pseudonymTaken(connection, invitation.pseudonym)) return@transaction Outcome.Denied("pseudonym_taken")
            if (credentialTaken(connection, credential.credentialId)) return@transaction Outcome.Denied("key_known")
            if (invitation.bootstrap) {
                connection.advisoryLock("team_bootstrap")
                if (grants.countLiveHolders(connection, TeamRole.SUPER_ADMIN, now) >= 2) return@transaction Outcome.Denied("bootstrap_closed")
            }
            invitations.consume(connection, invitation.id, now) ?: return@transaction Outcome.Denied("invitation_gone")
            val account = accounts.create(connection, invitation.pseudonym, ceremony.userHandle, invitation.createdBy, now)
            val stored = accounts.addCredential(connection, account.id, credential, now)
            val member = AuditActor.Member(account.id, account.pseudonym)
            audit.record(
                connection,
                AuditEntry(member, "team.invitation_accepted", TARGET_ACCOUNT, account.id.toString(), details = mapOf("invitation" to invitation.id.toString(), "credential" to stored.id.toString())),
            )
            if (invitation.bootstrap) {
                val grant = grants.insert(connection, account.id, TeamRole.SUPER_ADMIN, null, null, null, null, now)
                audit.record(connection, AuditEntry(AuditActor.System, "team.role_granted", TARGET_ACCOUNT, account.id.toString(), details = grantDetails(grant) + ("bootstrap" to "true")))
            } else {
                val inviter = invitation.createdBy?.let { accounts.find(connection, it) }?.takeIf { it.status == AccountStatus.ACTIVE }
                if (inviter != null) {
                    invitation.proposedRoles.forEach { role ->
                        val approval = approvals.create(
                            connection, ACTION_GRANT, role.grantPermission, TARGET_ACCOUNT, account.id.toString(),
                            grantPayload(role, invitation.organisationPubkey, invitation.rolesEndAt), null, inviter.id, now,
                        )
                        audit.record(connection, approvalEntry(AuditActor.Member(inviter.id, inviter.pseudonym), "approval.requested", approval))
                    }
                }
            }
            val opened = sessions.open(connection, account.id, now)
            audit.record(connection, AuditEntry(member, "auth.login", TARGET_ACCOUNT, account.id.toString(), details = mapOf("credential" to stored.id.toString())))
            Outcome.Ok(opened)
        }
    }

    // Sign-in ---------------------------------------------------------------------------------------

    /** A sign-in challenge: no state on the server (D-127). */
    fun startSignIn(): Outcome<RequestOptionsResponse> {
        val id = signInChallenges.issue()
        return Outcome.Ok(requestOptions(id, requireNotNull(Tokens.fromBase64Url(id)), emptyList(), signInChallenges.timeoutMillis))
    }

    fun finishSignIn(response: AssertionResponse): Outcome<OpenedSession> {
        val challenge = signInChallenges.open(response.ceremony) ?: return Outcome.Denied("ceremony_gone")
        val credentialId = Tokens.fromBase64Url(response.credentialId) ?: return Outcome.Denied("bad_encoding")
        val credential = accounts.credentialByCredentialId(credentialId)
        if (credential == null) {
            failures.count("unknown_key")
            return Outcome.Denied("unknown_key")
        }
        val account = accounts.find(credential.accountId) ?: return Outcome.Denied("unknown_key")
        val member = AuditActor.Member(account.id, account.pseudonym)
        if (account.status != AccountStatus.ACTIVE) {
            audit.record(AuditEntry(member, "auth.login_failed", TARGET_ACCOUNT, account.id.toString(), details = mapOf("code" to "account_closed")))
            return Outcome.Denied("account_closed")
        }
        val handle = response.userHandle?.let(Tokens::fromBase64Url)
        if (response.userHandle != null && (handle == null || !handle.contentEquals(account.userHandle))) {
            audit.record(AuditEntry(member, "auth.login_failed", TARGET_ACCOUNT, account.id.toString(), details = mapOf("code" to "user_mismatch")))
            return Outcome.Denied("assertion_refused")
        }
        val verified = try {
            passkeys.verifyAssertion(response, challenge, credential)
        } catch (e: PasskeyException) {
            audit.record(AuditEntry(member, "auth.login_failed", TARGET_ACCOUNT, account.id.toString(), details = mapOf("code" to e.code, "credential" to credential.id.toString())))
            return Outcome.Denied(e.code)
        }
        // Entered in the set of used challenges only now, after a verified signature.
        if (!signInChallenges.markUsed(response.ceremony)) return Outcome.Denied("ceremony_gone")
        val now = now()
        accounts.markUsed(credential.id, verified.signCount, verified.backupState, now)
        return dataSource.transaction { connection ->
            val opened = sessions.open(connection, account.id, now)
            audit.record(connection, AuditEntry(member, "auth.login", TARGET_ACCOUNT, account.id.toString(), details = mapOf("credential" to credential.id.toString())))
            Outcome.Ok(opened)
        }
    }

    /** The member behind the cookie [token], or null when the session is unknown, idle, too old or closed. */
    fun authenticate(token: String): Authenticated? {
        val now = clock.instant()
        val session = sessions.touch(token, now) ?: return null
        val account = accounts.find(session.accountId)?.takeIf { it.status == AccountStatus.ACTIVE } ?: return null
        val (roles, keys) = dataSource.connection.use { connection ->
            grants.live(connection, account.id, now).map { it.role }.toSet() to accounts.credentialCount(connection, account.id)
        }
        return Authenticated(session, token, account, TeamActor.of(account.id, account.pseudonym, roles, keys), keys)
    }

    fun signOut(auth: Authenticated) {
        sessions.close(auth.session.tokenHash)
        audit.record(AuditEntry(AuditActor.of(auth.actor), "auth.logout", TARGET_ACCOUNT, auth.account.id.toString()))
    }

    // New verification before a sensitive action ----------------------------------------------------

    fun startConfirmation(auth: Authenticated): Outcome<RequestOptionsResponse> {
        val keys = accounts.credentials(auth.account.id)
        val (id, ceremony) = sessionCeremonies.start(auth.session.tokenHash) { challenge, at -> Ceremony.Confirm(challenge, at, auth.account.id) }
        return Outcome.Ok(requestOptions(id, ceremony.challenge, keys.map { it.credentialId }, sessionCeremonies.timeoutMillis))
    }

    fun finishConfirmation(auth: Authenticated, response: AssertionResponse): Outcome<Unit> {
        val ceremony = sessionCeremonies.take(auth.session.tokenHash, response.ceremony) as? Ceremony.Confirm ?: return Outcome.Denied("ceremony_gone")
        if (ceremony.accountId != auth.account.id) return Outcome.Denied("ceremony_gone")
        val credential = when (val checked = verifyOwnAssertion(auth, response, ceremony.challenge)) {
            is Outcome.Denied -> return checked
            is Outcome.Ok -> checked.value
        }
        sessions.markVerified(auth.session.tokenHash, now())
        audit.record(AuditEntry(AuditActor.of(auth.actor), "auth.confirmed", TARGET_ACCOUNT, auth.account.id.toString(), details = mapOf("credential" to credential.id.toString())))
        return Outcome.Ok(Unit)
    }

    /** An assertion of one of the member's own keys, user verified; failures are journaled (the account is known). */
    private fun verifyOwnAssertion(auth: Authenticated, response: AssertionResponse, challenge: ByteArray): Outcome<StoredCredential> {
        val credentialId = Tokens.fromBase64Url(response.credentialId) ?: return Outcome.Denied("bad_encoding")
        val credential = accounts.credentialByCredentialId(credentialId)?.takeIf { it.accountId == auth.account.id } ?: return Outcome.Denied("unknown_key")
        val verified = try {
            passkeys.verifyAssertion(response, challenge, credential)
        } catch (e: PasskeyException) {
            audit.record(AuditEntry(AuditActor.of(auth.actor), "auth.confirmation_failed", TARGET_ACCOUNT, auth.account.id.toString(), details = mapOf("code" to e.code, "credential" to credential.id.toString())))
            return Outcome.Denied(e.code)
        }
        accounts.markUsed(credential.id, verified.signCount, verified.backupState, now())
        return Outcome.Ok(credential)
    }

    // Keys of the member ----------------------------------------------------------------------------

    /**
     * Adding a key gives lasting access: it opens only with a key of the account verified inside this very
     * flow, at most 60 seconds before (D-127), not with the freshness of the session alone.
     */
    fun startAddKeyProof(auth: Authenticated): Outcome<RequestOptionsResponse> {
        val keys = accounts.credentials(auth.account.id)
        if (keys.size >= MAX_KEYS) return Outcome.Denied("too_many_keys")
        val (id, ceremony) = sessionCeremonies.start(auth.session.tokenHash) { challenge, at -> Ceremony.AddKeyProof(challenge, at, auth.account.id) }
        return Outcome.Ok(requestOptions(id, ceremony.challenge, keys.map { it.credentialId }, ADD_KEY_PROOF_LIFETIME.inWholeMilliseconds))
    }

    /** Checks the proof of [startAddKeyProof] and opens the registration of the new key, bound to it. */
    fun startAddKey(auth: Authenticated, proof: AssertionResponse): Outcome<CreationOptionsResponse> {
        val ceremony = sessionCeremonies.take(auth.session.tokenHash, proof.ceremony) as? Ceremony.AddKeyProof ?: return Outcome.Denied("ceremony_gone")
        if (ceremony.accountId != auth.account.id) return Outcome.Denied("ceremony_gone")
        if (ceremony.startedAt.plusSeconds(ADD_KEY_PROOF_LIFETIME.inWholeSeconds).isBefore(clock.instant())) return Outcome.Denied("ceremony_gone")
        when (val checked = verifyOwnAssertion(auth, proof, ceremony.challenge)) {
            is Outcome.Denied -> return checked
            is Outcome.Ok -> Unit
        }
        val keys = accounts.credentials(auth.account.id)
        if (keys.size >= MAX_KEYS) return Outcome.Denied("too_many_keys")
        val (id, creation) = sessionCeremonies.start(auth.session.tokenHash) { challenge, at -> Ceremony.AddKey(challenge, at, auth.account.id) }
        return Outcome.Ok(creationOptions(id, creation.challenge, auth.account.userHandle, auth.account.pseudonym, keys.map { it.credentialId }))
    }

    fun finishAddKey(auth: Authenticated, response: RegistrationResponse): Outcome<KeyInfo> {
        val ceremony = sessionCeremonies.take(auth.session.tokenHash, response.ceremony) as? Ceremony.AddKey ?: return Outcome.Denied("ceremony_gone")
        if (ceremony.accountId != auth.account.id) return Outcome.Denied("ceremony_gone")
        val name = KeyNames.normalize(response.name) ?: BackOfficeTexts.DEFAULT_BACKUP_KEY_NAME
        val credential = try {
            passkeys.verifyRegistration(response, ceremony.challenge, name)
        } catch (e: PasskeyException) {
            return Outcome.Denied(e.code)
        }
        val now = now()
        return dataSource.transaction { connection ->
            if (credentialTaken(connection, credential.credentialId)) return@transaction Outcome.Denied("key_known")
            if (accounts.credentialCount(connection, auth.account.id) >= MAX_KEYS) return@transaction Outcome.Denied("too_many_keys")
            val stored = accounts.addCredential(connection, auth.account.id, credential, now)
            audit.record(connection, AuditEntry(AuditActor.of(auth.actor), "team.key_added", TARGET_ACCOUNT, auth.account.id.toString(), details = mapOf("credential" to stored.id.toString())))
            Outcome.Ok(KeyInfo(stored.id, stored.name, stored.createdAt, null))
        }
    }

    /** Deletes one of one's own keys (never the last one: the account would be locked out). */
    fun deleteKey(auth: Authenticated, credentialId: UUID): Outcome<Unit> {
        if (!auth.isFresh(clock.instant())) return Outcome.Denied("confirmation_required")
        return dataSource.transaction { connection ->
            if (accounts.credentialCount(connection, auth.account.id) <= 1) return@transaction Outcome.Denied("last_key")
            if (!accounts.deleteCredential(connection, auth.account.id, credentialId)) return@transaction Outcome.Denied("not_found")
            audit.record(connection, AuditEntry(AuditActor.of(auth.actor), "team.key_deleted", TARGET_ACCOUNT, auth.account.id.toString(), details = mapOf("credential" to credentialId.toString())))
            Outcome.Ok(Unit)
        }
    }

    /**
     * Deletes a key of a **suspended** member (a lost or stolen key), so that a later reinstatement does
     * not give it back: `team.revoke`, and `team.manage_admins` for a holder of an administration role.
     */
    fun deleteMemberKey(auth: Authenticated, accountId: UUID, credentialId: UUID): Outcome<Unit> {
        if (!auth.isFresh(clock.instant())) return Outcome.Denied("confirmation_required")
        val now = now()
        return dataSource.transaction { connection ->
            val target = accounts.find(connection, accountId)?.takeIf { it.status == AccountStatus.SUSPENDED } ?: return@transaction Outcome.Denied("not_suspended")
            if (!auth.actor.has(closePermission(connection, target.id, now))) return@transaction Outcome.Denied("forbidden")
            if (!accounts.deleteCredential(connection, target.id, credentialId)) return@transaction Outcome.Denied("not_found")
            audit.record(connection, AuditEntry(AuditActor.of(auth.actor), "team.key_deleted", TARGET_ACCOUNT, target.id.toString(), details = mapOf("credential" to credentialId.toString())))
            Outcome.Ok(Unit)
        }
    }

    // Team ------------------------------------------------------------------------------------------

    /**
     * An invitation (S-57): a one-time link valid 48 hours. The proposed roles become requests for a
     * second approval once the person has registered their key; the inviter must be allowed to ask for each.
     */
    fun createInvitation(auth: Authenticated, pseudonym: String, roles: List<TeamRole>, organisation: String?, rolesEndAt: Instant?): Outcome<IssuedInvitation> {
        val actor = auth.actor
        if (!actor.has(Permissions.TEAM_INVITE)) return Outcome.Denied("forbidden")
        if (!auth.isFresh(clock.instant())) return Outcome.Denied("confirmation_required")
        val name = Pseudonyms.normalize(pseudonym) ?: return Outcome.Denied("bad_pseudonym")
        if (roles.any { !actor.has(it.grantPermission) }) return Outcome.Denied("forbidden_role")
        val now = now()
        if (rolesEndAt != null && !rolesEndAt.isAfter(now)) return Outcome.Denied("end_in_past")
        val organisationKey = when (val checked = checkOrganisation(roles.contains(TeamRole.VALIDATOR), organisation)) {
            is Outcome.Denied -> return checked
            is Outcome.Ok -> checked.value
        }
        return dataSource.transaction { connection ->
            if (accounts.pseudonymTaken(connection, name)) return@transaction Outcome.Denied("pseudonym_taken")
            val issued = invitations.create(connection, name, roles, organisationKey, rolesEndAt, actor.accountId, now)
            audit.record(
                connection,
                AuditEntry(
                    AuditActor.of(actor),
                    "team.invitation_created",
                    "team_invitation",
                    issued.invitation.id.toString(),
                    details = buildMap {
                        put("pseudonym", name)
                        put("roles", roles.joinToString(",") { it.id })
                        organisationKey?.let { put("organisation", it) }
                        rolesEndAt?.let { put("ends_at", it.epochSecond.toString()) }
                    },
                ),
            )
            Outcome.Ok(issued)
        }
    }

    /** Withdraws an invitation before it is used; never a bootstrap invitation (command line only). */
    fun withdrawInvitation(auth: Authenticated, id: UUID): Outcome<Unit> {
        if (!auth.actor.has(Permissions.TEAM_INVITE)) return Outcome.Denied("forbidden")
        if (!auth.isFresh(clock.instant())) return Outcome.Denied("confirmation_required")
        return dataSource.transaction { connection ->
            val invitation = invitations.find(connection, id) ?: return@transaction Outcome.Denied("not_found")
            if (invitation.bootstrap) return@transaction Outcome.Denied("bootstrap_invitation")
            invitations.delete(connection, id)
            audit.record(connection, AuditEntry(AuditActor.of(auth.actor), "team.invitation_withdrawn", "team_invitation", id.toString()))
            Outcome.Ok(Unit)
        }
    }

    /** Asks for [role] for [accountId]: always a request for a second person (S-61, contract section 5). */
    fun requestGrant(auth: Authenticated, accountId: UUID, role: TeamRole, organisation: String?, endsAt: Instant?, reason: String?): Outcome<PendingApproval> {
        val actor = auth.actor
        if (!actor.has(role.grantPermission)) return Outcome.Denied("forbidden")
        if (!auth.isFresh(clock.instant())) return Outcome.Denied("confirmation_required")
        val now = now()
        if (endsAt != null && !endsAt.isAfter(now)) return Outcome.Denied("end_in_past")
        val organisationKey = when (val checked = checkOrganisation(role == TeamRole.VALIDATOR, organisation)) {
            is Outcome.Denied -> return checked
            is Outcome.Ok -> checked.value
        }
        val text = cleanReason(reason)
        return dataSource.transaction { connection ->
            val target = accounts.find(connection, accountId)?.takeIf { it.status == AccountStatus.ACTIVE } ?: return@transaction Outcome.Denied("not_found")
            if (grants.hasActive(connection, target.id, role)) return@transaction Outcome.Denied("already_granted")
            if (approvals.hasOpen(connection, ACTION_GRANT, TARGET_ACCOUNT, target.id.toString(), role.id, now)) return@transaction Outcome.Denied("already_requested")
            val approval = approvals.create(connection, ACTION_GRANT, role.grantPermission, TARGET_ACCOUNT, target.id.toString(), grantPayload(role, organisationKey, endsAt), text, actor.accountId, now)
            audit.record(connection, approvalEntry(AuditActor.of(actor), "approval.requested", approval))
            Outcome.Ok(approval)
        }
    }

    /**
     * Withdraws a role: at once for the ordinary roles (`team.revoke`); for `admin` and `super_admin`, a
     * request for a second super administrator (`team.manage_admins`), as the contract asks.
     */
    fun revokeGrant(auth: Authenticated, grantId: UUID, reason: String?): Outcome<TeamChange> {
        val actor = auth.actor
        val grant = grants.find(grantId)?.takeIf { it.status == GrantStatus.ACTIVE } ?: return Outcome.Denied("not_found")
        val permission = if (grant.role.isAdministration) Permissions.TEAM_MANAGE_ADMINS else Permissions.TEAM_REVOKE
        if (!actor.has(permission)) return Outcome.Denied("forbidden")
        if (!auth.isFresh(clock.instant())) return Outcome.Denied("confirmation_required")
        val text = cleanReason(reason)
        val now = now()
        return dataSource.transaction { connection ->
            if (grant.role.isAdministration) {
                if (approvals.hasOpen(connection, ACTION_REVOKE, TARGET_GRANT, grant.id.toString(), null, now)) return@transaction Outcome.Denied("already_requested")
                val approval = approvals.create(
                    connection, ACTION_REVOKE, permission, TARGET_GRANT, grant.id.toString(),
                    mapOf("account" to grant.accountId.toString(), "role" to grant.role.id), text, actor.accountId, now,
                )
                audit.record(connection, approvalEntry(AuditActor.of(actor), "approval.requested", approval))
                Outcome.Ok(TeamChange.Waiting(approval))
            } else {
                val closed = grants.close(connection, grant.id, GrantStatus.REVOKED, now) ?: return@transaction Outcome.Denied("not_found")
                val revokedSessions = validationSessions.revokeForGrant(connection, closed.id, now)
                audit.record(
                    connection,
                    AuditEntry(AuditActor.of(actor), "team.role_revoked", TARGET_ACCOUNT, closed.accountId.toString(), text, grantDetails(closed) + ("validation_sessions" to revokedSessions.size.toString())),
                )
                Outcome.Ok(TeamChange.Done())
            }
        }
    }

    /** The account a request concerns (who may never decide on it), if it concerns one. */
    private fun targetAccountOf(approval: PendingApproval): UUID? = when (approval.targetType) {
        TARGET_ACCOUNT -> runCatching { UUID.fromString(approval.targetId) }.getOrNull()
        TARGET_GRANT -> approval.payload["account"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        else -> null
    }

    /** The requests [actor] could decide (they hold the permission, did not ask and are not concerned), and their own. */
    fun openApprovals(actor: TeamActor): List<PendingApproval> =
        approvals.open(clock.instant()).filter {
            it.requestedBy == actor.accountId || (actor.has(it.permission) && targetAccountOf(it) != actor.accountId)
        }

    /** Approves a request of somebody else (S-61): the action is done now, with the two names in the journal. */
    fun approve(auth: Authenticated, approvalId: UUID): Outcome<ApprovalStatus> {
        val actor = auth.actor
        if (!auth.isFresh(clock.instant())) return Outcome.Denied("confirmation_required")
        // A queue action is checked again against the queue: still offered, still allowed to this person.
        approvals.find(approvalId)?.takeIf { it.action == ACTION_QUEUE && it.status == ApprovalStatus.PENDING }?.let { pending ->
            queueActionAllowed(actor, pending.payload["queue"].orEmpty(), pending.payload["item"].orEmpty(), pending.payload["action"].orEmpty())?.let { return Outcome.Denied(it) }
        }
        val now = now()
        val work = dataSource.transaction<Outcome<Pair<ApprovalStatus, PendingApproval?>>> { connection ->
            val approval = approvals.lockOpen(connection, approvalId, now) ?: return@transaction Outcome.Denied("approval_gone")
            if (!actor.has(approval.permission)) return@transaction Outcome.Denied("forbidden")
            if (approval.requestedBy == actor.accountId) return@transaction Outcome.Denied("own_request")
            if (targetAccountOf(approval) == actor.accountId) return@transaction Outcome.Denied("own_target")
            val me = AuditActor.of(actor)
            when (approval.action) {
                ACTION_GRANT -> Outcome.Ok(approveGrant(connection, approval, actor, me, now) to null)
                ACTION_REVOKE -> {
                    val closed = grants.find(UUID.fromString(approval.targetId))?.let { grants.close(connection, it.id, GrantStatus.REVOKED, now) }
                    val status = if (closed == null) ApprovalStatus.FAILED else ApprovalStatus.APPROVED
                    approvals.decide(connection, approval.id, status, actor.accountId, now)
                    audit.record(connection, approvalEntry(me, if (closed == null) "approval.failed" else "approval.approved", approval))
                    if (closed != null) {
                        validationSessions.revokeForGrant(connection, closed.id, now)
                        audit.record(connection, AuditEntry(me, "team.role_revoked", TARGET_ACCOUNT, closed.accountId.toString(), approval.reason, grantDetails(closed) + ("approval" to approval.id.toString())))
                    }
                    Outcome.Ok(status to null)
                }
                ACTION_REMOVE -> {
                    val target = accounts.find(connection, UUID.fromString(approval.targetId))?.takeIf { it.status != AccountStatus.REMOVED }
                    if (target == null) {
                        approvals.decide(connection, approval.id, ApprovalStatus.FAILED, actor.accountId, now)
                        audit.record(connection, approvalEntry(me, "approval.failed", approval))
                        return@transaction Outcome.Ok(ApprovalStatus.FAILED to null)
                    }
                    approvals.decide(connection, approval.id, ApprovalStatus.APPROVED, actor.accountId, now)
                    audit.record(connection, approvalEntry(me, "approval.approved", approval))
                    removeIn(connection, target.id, me, approval.reason, now, approval.id)
                    Outcome.Ok(ApprovalStatus.APPROVED to null)
                }
                ACTION_REINSTATE -> {
                    val target = accounts.find(connection, UUID.fromString(approval.targetId))
                    val done = target != null && reinstateIn(connection, target, me, approval.reason, approval.id) is Outcome.Ok
                    val status = if (done) ApprovalStatus.APPROVED else ApprovalStatus.FAILED
                    approvals.decide(connection, approval.id, status, actor.accountId, now)
                    audit.record(connection, approvalEntry(me, if (done) "approval.approved" else "approval.failed", approval))
                    Outcome.Ok(status to null)
                }
                ACTION_QUEUE -> {
                    approvals.decide(connection, approval.id, ApprovalStatus.APPROVED, actor.accountId, now)
                    audit.record(connection, approvalEntry(me, "approval.approved", approval))
                    Outcome.Ok(ApprovalStatus.APPROVED to approval)
                }
                else -> Outcome.Denied("unknown_action")
            }
        }
        val (status, queueApproval) = when (work) {
            is Outcome.Denied -> return work
            is Outcome.Ok -> work.value
        }
        // A queue action approved by a second person is performed after the approval is committed: the
        // queue belongs to a pole and works with its own transactions.
        if (queueApproval != null) {
            val queueId = queueApproval.payload["queue"].orEmpty()
            val item = queueApproval.payload["item"].orEmpty()
            val action = queueApproval.payload["action"].orEmpty()
            val queue = queues.find(queueId)
            val outcome = if (queue == null) {
                ActionOutcome.NotFound
            } else {
                audit.record(queueEntry(actor, queueId, item, action, queueApproval.reason, null, queueApproval.id))
                runQueueAction(queue, item, action, queueApproval.reason, actor)
            }
            audit.record(queueEntry(actor, queueId, item, action, queueApproval.reason, outcome, queueApproval.id))
            if (outcome != ActionOutcome.Done) {
                approvals.markFailed(queueApproval.id)
                return Outcome.Ok(ApprovalStatus.FAILED)
            }
        }
        return Outcome.Ok(status)
    }

    private fun approveGrant(connection: Connection, approval: PendingApproval, actor: TeamActor, me: AuditActor, now: Instant): ApprovalStatus {
        val role = TeamRole.fromId(approval.payload["role"].orEmpty())
        val target = runCatching { UUID.fromString(approval.targetId) }.getOrNull()?.let { accounts.find(connection, it) }
        val organisation = approval.payload["organisation"]
        val endsAt = approval.payload["ends_at"]?.toLongOrNull()?.let(Instant::ofEpochSecond)
        val failure = when {
            role == null || target == null || target.status != AccountStatus.ACTIVE -> "target_gone"
            grants.hasActive(connection, target.id, role) -> "already_granted"
            role == TeamRole.VALIDATOR && (organisation == null || !organisationVerified(organisation)) -> "organisation_not_verified"
            endsAt != null && !endsAt.isAfter(now) -> "end_in_past"
            else -> null
        }
        if (failure != null) {
            approvals.decide(connection, approval.id, ApprovalStatus.FAILED, actor.accountId, now)
            audit.record(connection, approvalEntry(me, "approval.failed", approval) + ("code" to failure))
            return ApprovalStatus.FAILED
        }
        val grant = grants.insert(connection, target!!.id, role!!, organisation.takeIf { role == TeamRole.VALIDATOR }, endsAt, approval.requestedBy, actor.accountId, now)
        approvals.decide(connection, approval.id, ApprovalStatus.APPROVED, actor.accountId, now)
        audit.record(connection, approvalEntry(me, "approval.approved", approval))
        audit.record(connection, AuditEntry(me, "team.role_granted", TARGET_ACCOUNT, target.id.toString(), approval.reason, grantDetails(grant) + ("approval" to approval.id.toString())))
        return ApprovalStatus.APPROVED
    }

    /** Refuses a request of somebody else that does not concern oneself; a refusal only stops an action. */
    fun refuseApproval(auth: Authenticated, approvalId: UUID, reason: String?): Outcome<Unit> {
        val actor = auth.actor
        if (!auth.isFresh(clock.instant())) return Outcome.Denied("confirmation_required")
        val now = now()
        return dataSource.transaction { connection ->
            val approval = approvals.lockOpen(connection, approvalId, now) ?: return@transaction Outcome.Denied("approval_gone")
            if (!actor.has(approval.permission)) return@transaction Outcome.Denied("forbidden")
            if (approval.requestedBy == actor.accountId) return@transaction Outcome.Denied("own_request")
            if (targetAccountOf(approval) == actor.accountId) return@transaction Outcome.Denied("own_target")
            approvals.decide(connection, approval.id, ApprovalStatus.REFUSED, actor.accountId, now)
            audit.record(connection, approvalEntry(AuditActor.of(actor), "approval.refused", approval).copy(reason = cleanReason(reason)))
            Outcome.Ok(Unit)
        }
    }

    /** The requester withdraws their own request. */
    fun cancelApproval(auth: Authenticated, approvalId: UUID): Outcome<Unit> {
        val actor = auth.actor
        val now = now()
        return dataSource.transaction { connection ->
            val approval = approvals.lockOpen(connection, approvalId, now) ?: return@transaction Outcome.Denied("approval_gone")
            if (approval.requestedBy != actor.accountId) return@transaction Outcome.Denied("forbidden")
            approvals.decide(connection, approval.id, ApprovalStatus.CANCELLED, actor.accountId, now)
            audit.record(connection, approvalEntry(AuditActor.of(actor), "approval.cancelled", approval))
            Outcome.Ok(Unit)
        }
    }

    /** `team.manage_admins` for a holder of an administration role, `team.revoke` for anyone else. */
    private fun closePermission(connection: Connection, accountId: UUID, now: Instant): String =
        if (grants.live(connection, accountId, now).any { it.role.isAdministration }) Permissions.TEAM_MANAGE_ADMINS else Permissions.TEAM_REVOKE

    /**
     * Emergency cut (S-62, D-127): immediate and **reversible**. The account is suspended: its sessions,
     * open requests, validation sessions and the invitations it created end; its roles and keys stay but
     * are unusable until a reinstatement. Anyone may cut their own account (a stolen phone) without a new
     * verification; cutting someone else needs `team.revoke`, and `team.manage_admins` for an administrator.
     */
    fun emergencyCut(auth: Authenticated, accountId: UUID, reason: String?): Outcome<CutReport> {
        val actor = auth.actor
        val self = accountId == actor.accountId
        if (!self && !auth.isFresh(clock.instant())) return Outcome.Denied("confirmation_required")
        val now = now()
        return dataSource.transaction { connection ->
            val target = accounts.find(connection, accountId) ?: return@transaction Outcome.Denied("not_found")
            if (target.status != AccountStatus.ACTIVE) return@transaction Outcome.Denied("already_closed")
            if (!self && !actor.has(closePermission(connection, accountId, now))) return@transaction Outcome.Denied("forbidden")
            Outcome.Ok(suspendIn(connection, accountId, AuditActor.of(actor), cleanReason(reason), now))
        }
    }

    /**
     * Permanent removal of a member (D-127): with a new verification; immediate for oneself and for an
     * ordinary member (`team.revoke`); a request for a second super administrator when the member holds
     * `admin` or `super_admin`, so that nobody removes the others alone. The journal keeps the trace.
     */
    fun removeMember(auth: Authenticated, accountId: UUID, reason: String?): Outcome<TeamChange> {
        val actor = auth.actor
        if (!auth.isFresh(clock.instant())) return Outcome.Denied("confirmation_required")
        val self = accountId == actor.accountId
        val text = cleanReason(reason)
        val now = now()
        return dataSource.transaction { connection ->
            val target = accounts.find(connection, accountId)?.takeIf { it.status != AccountStatus.REMOVED } ?: return@transaction Outcome.Denied("not_found")
            val permission = closePermission(connection, target.id, now)
            if (!self && !actor.has(permission)) return@transaction Outcome.Denied("forbidden")
            if (!self && permission == Permissions.TEAM_MANAGE_ADMINS) {
                if (approvals.hasOpen(connection, ACTION_REMOVE, TARGET_ACCOUNT, target.id.toString(), null, now)) return@transaction Outcome.Denied("already_requested")
                val approval = approvals.create(connection, ACTION_REMOVE, permission, TARGET_ACCOUNT, target.id.toString(), mapOf("pseudonym" to target.pseudonym), text, actor.accountId, now)
                audit.record(connection, approvalEntry(AuditActor.of(actor), "approval.requested", approval))
                return@transaction Outcome.Ok(TeamChange.Waiting(approval))
            }
            Outcome.Ok(TeamChange.Done(removeIn(connection, target.id, AuditActor.of(actor), text, now, null)))
        }
    }

    /** Asks for the reinstatement of a suspended member: a second super administrator approves (or the command line). */
    fun requestReinstatement(auth: Authenticated, accountId: UUID, reason: String?): Outcome<PendingApproval> {
        val actor = auth.actor
        if (!actor.has(Permissions.TEAM_MANAGE_ADMINS)) return Outcome.Denied("forbidden")
        if (!auth.isFresh(clock.instant())) return Outcome.Denied("confirmation_required")
        val now = now()
        return dataSource.transaction { connection ->
            val target = accounts.find(connection, accountId)?.takeIf { it.status == AccountStatus.SUSPENDED } ?: return@transaction Outcome.Denied("not_suspended")
            if (approvals.hasOpen(connection, ACTION_REINSTATE, TARGET_ACCOUNT, target.id.toString(), null, now)) return@transaction Outcome.Denied("already_requested")
            val approval = approvals.create(
                connection, ACTION_REINSTATE, Permissions.TEAM_MANAGE_ADMINS, TARGET_ACCOUNT, target.id.toString(),
                mapOf("pseudonym" to target.pseudonym), cleanReason(reason), actor.accountId, now,
            )
            audit.record(connection, approvalEntry(AuditActor.of(actor), "approval.requested", approval))
            Outcome.Ok(approval)
        }
    }

    private fun suspendIn(connection: Connection, accountId: UUID, actor: AuditActor, reason: String?, now: Instant): CutReport {
        val roles = grants.live(connection, accountId, now)
        accounts.close(connection, accountId, AccountStatus.SUSPENDED, now)
        val report = CutReport(
            sessions = sessions.closeAll(connection, accountId),
            grants = roles.size,
            keys = accounts.credentialCount(connection, accountId),
            validationSessions = validationSessions.revokeForAccount(connection, accountId, now),
            requests = approvals.cancelForTarget(connection, TARGET_ACCOUNT, accountId.toString(), now) + cancelRequestedBy(connection, accountId, now),
            invitations = invitations.deleteCreatedBy(connection, accountId),
        )
        audit.record(connection, AuditEntry(actor, "team.emergency_cut", TARGET_ACCOUNT, accountId.toString(), reason, cutDetails(report, roles)))
        return report
    }

    private fun removeIn(connection: Connection, accountId: UUID, actor: AuditActor, reason: String?, now: Instant, approval: UUID?): CutReport {
        val roles = grants.live(connection, accountId, now)
        accounts.close(connection, accountId, AccountStatus.REMOVED, now)
        val report = CutReport(
            sessions = sessions.closeAll(connection, accountId),
            grants = grants.revokeAll(connection, accountId, now).size,
            keys = accounts.deleteCredentials(connection, accountId),
            validationSessions = validationSessions.revokeForAccount(connection, accountId, now),
            requests = approvals.cancelForTarget(connection, TARGET_ACCOUNT, accountId.toString(), now) + cancelRequestedBy(connection, accountId, now),
            invitations = invitations.deleteCreatedBy(connection, accountId),
        )
        val details = cutDetails(report, roles) + (approval?.let { mapOf("approval" to it.toString()) } ?: emptyMap())
        audit.record(connection, AuditEntry(actor, "team.member_removed", TARGET_ACCOUNT, accountId.toString(), reason, details))
        return report
    }

    private fun reinstateIn(connection: Connection, target: TeamAccount, actor: AuditActor, reason: String?, approval: UUID? = null): Outcome<Unit> {
        if (target.status != AccountStatus.SUSPENDED) return Outcome.Denied("not_suspended")
        if (accounts.pseudonymTaken(connection, target.pseudonym)) return Outcome.Denied("pseudonym_taken")
        if (!accounts.reactivate(connection, target.id)) return Outcome.Denied("not_suspended")
        audit.record(
            connection,
            AuditEntry(actor, "team.member_reinstated", TARGET_ACCOUNT, target.id.toString(), reason, approval?.let { mapOf("approval" to it.toString()) } ?: emptyMap()),
        )
        return Outcome.Ok(Unit)
    }

    private fun cutDetails(report: CutReport, roles: List<RoleGrant>): Map<String, String> = mapOf(
        "sessions" to report.sessions.toString(),
        "grants" to report.grants.toString(),
        "keys" to report.keys.toString(),
        "validation_sessions" to report.validationSessions.toString(),
        "requests" to report.requests.toString(),
        "invitations" to report.invitations.toString(),
        "roles" to roles.joinToString(",") { it.role.id },
    )

    private fun cancelRequestedBy(connection: Connection, accountId: UUID, now: Instant): Int =
        connection.prepareStatement("UPDATE team_pending_approval SET status = 'failed', decided_at = ? WHERE status = 'pending' AND requested_by = ?").use { update ->
            update.setInstant(1, now)
            update.setObject(2, accountId)
            update.executeUpdate()
        }

    // Queues ----------------------------------------------------------------------------------------

    /** Why [actor] may not do [actionId] on [itemId] of [queueId] now, or null when they may. */
    private fun queueActionAllowed(actor: TeamActor, queueId: String, itemId: String, actionId: String): String? {
        val queue = queues.find(queueId) ?: return "not_found"
        if (!actor.has(queue.viewPermission)) return "forbidden"
        val available = runCatching { queue.actions(itemId) }.onFailure { logQueueFailure(queue, it) }.getOrNull() ?: return "queue_unavailable"
        val action = available.firstOrNull { it.id == actionId } ?: return "action_unavailable"
        if (!actor.has(action.permission)) return "forbidden"
        return null
    }

    /**
     * An action on a queue item (contract, section 7): permission of the queue and of the action, reason
     * when required, new verification when sensitive, second person when required, and two journal lines,
     * `….requested` before the action and its outcome after (D-127).
     */
    fun performQueueAction(auth: Authenticated, queueId: String, itemId: String, actionId: String, reason: String?): Outcome<QueueResult> {
        val actor = auth.actor
        val queue = queues.find(queueId) ?: return Outcome.Denied("not_found")
        if (!actor.has(queue.viewPermission)) return Outcome.Denied("forbidden")
        // The journal identifier is checked before anything is done.
        if (!Permissions.isValidId("queue.$queueId.$actionId.requested")) return Outcome.Denied("unknown_action")
        val available = runCatching { queue.actions(itemId) }.onFailure { logQueueFailure(queue, it) }.getOrNull() ?: return Outcome.Denied("queue_unavailable")
        val action = available.firstOrNull { it.id == actionId } ?: return Outcome.Denied("action_unavailable")
        if (!actor.has(action.permission)) return Outcome.Denied("forbidden")
        val text = if (action.reasonAllowed) cleanReason(reason) else null
        if (action.reasonRequired && text == null) return Outcome.Denied("reason_required")
        if ((action.sensitive || action.twoPerson) && !auth.isFresh(clock.instant())) return Outcome.Denied("confirmation_required")
        if (action.twoPerson) {
            val now = now()
            return dataSource.transaction { connection ->
                val target = "$queueId:$itemId"
                if (approvals.hasOpen(connection, ACTION_QUEUE, TARGET_QUEUE_ITEM, target, actionId, now)) return@transaction Outcome.Denied("already_requested")
                val approval = approvals.create(
                    connection, ACTION_QUEUE, action.permission, TARGET_QUEUE_ITEM, target,
                    mapOf(PendingApprovals.KEY to actionId, "queue" to queueId, "item" to itemId, "action" to actionId), text, actor.accountId, now,
                )
                audit.record(connection, approvalEntry(AuditActor.of(actor), "approval.requested", approval))
                Outcome.Ok(QueueResult.Waiting(approval))
            }
        }
        audit.record(queueEntry(actor, queueId, itemId, actionId, text, null, null))
        val outcome = runQueueAction(queue, itemId, actionId, text, actor)
        audit.record(queueEntry(actor, queueId, itemId, actionId, text, outcome, null))
        return Outcome.Ok(QueueResult.Performed(outcome))
    }

    private fun runQueueAction(queue: BackOfficeQueue, itemId: String, actionId: String, reason: String?, actor: TeamActor): ActionOutcome = try {
        queue.perform(itemId, actionId, reason, actor)
    } catch (e: Exception) {
        // Only the class: the message of a database error can quote the content in question.
        log.error("Queue ${queue.id}: action $actionId failed with ${e::class.qualifiedName}.")
        ActionOutcome.Refused(BackOfficeTexts.QUEUE_ACTION_FAILED)
    }

    /** The line before an action ([outcome] null: `….requested`) or after it, with its outcome. */
    private fun queueEntry(actor: TeamActor, queueId: String, itemId: String, actionId: String, reason: String?, outcome: ActionOutcome?, approval: UUID?): AuditEntry = AuditEntry(
        AuditActor.of(actor),
        "queue.$queueId.$actionId" + if (outcome == null) ".requested" else "",
        TARGET_QUEUE_ITEM,
        "$queueId:$itemId",
        reason,
        buildMap {
            outcome?.let { put("outcome", outcomeCode(it)) }
            if (outcome is ActionOutcome.Refused) put("refusal", outcome.reason.take(500))
            approval?.let { put("approval", it.toString()) }
        },
    )

    /** The dashboard of [actor] (S-64): counts of the queues they may see, priorities, alerts, requests. */
    fun dashboard(actor: TeamActor): Dashboard {
        val now = clock.instant().epochSecond
        val visible = queues.visibleTo(actor)
        val counts = visible.map { queue -> QueueCount(queue, runCatching { queue.pendingCount() }.onFailure { logQueueFailure(queue, it) }.getOrNull()) }
        val priorities = visible.flatMap { queue ->
            val items = runCatching { queue.page(null, PRIORITY_SCAN).items }.onFailure { logQueueFailure(queue, it) }.getOrDefault(emptyList())
            items.filter { it.deadline != null || (it.level ?: 0) >= 4 }.map { item ->
                val deadline = item.deadline
                PriorityItem(queue, item, overdue = deadline != null && deadline < now, nearDeadline = deadline != null && deadline >= now && deadline - now <= NEAR_DEADLINE_SECONDS)
            }
        }.sortedWith(compareBy<PriorityItem> { it.item.deadline ?: Long.MAX_VALUE }.thenByDescending { it.item.level ?: 0 }).take(MAX_PRIORITIES)
        val visibleAlerts = alerts.open().filter { actor.has(it.permission) }
        return Dashboard(counts, priorities, visibleAlerts, openApprovals(actor).count { it.requestedBy != actor.accountId })
    }

    private fun logQueueFailure(queue: BackOfficeQueue, e: Throwable) {
        log.warn("Queue ${queue.id} could not be read (${e::class.simpleName}).")
    }

    // Alerts ----------------------------------------------------------------------------------------

    /**
     * Posts an alert on the dashboard without a queue (contract 1.1, section 7), for example « import des
     * données ouvertes refusé ». [permission] says who sees it and may dismiss it; [link] is a back-office
     * path (a slash, then a letter or digit, no backslash).
     */
    fun postAlert(source: String, title: String, level: Int, permission: String, link: String? = null): DashboardAlert {
        require(Regex("^[a-z0-9_]+(\\.[a-z0-9_]+)*$").matches(source)) { "The source is the stable id of a space" }
        require(title.isNotBlank() && title.length <= 200) { "An alert title has 1 to 200 characters" }
        require(level in 1..5) { "The importance level is between 1 and 5" }
        require(Permissions.isValidId(permission)) { "The permission is a dotted identifier" }
        require(link == null || isLocalLink(link)) { "The link is a path of the back-office" }
        val now = now()
        return dataSource.transaction { connection ->
            val alert = alerts.insert(connection, source, title.trim(), level, link, permission, now)
            audit.record(connection, AuditEntry(AuditActor.System, "alert.posted", "dashboard_alert", alert.id.toString(), details = mapOf("source" to source, "level" to level.toString(), "title" to alert.title)))
            alert
        }
    }

    fun dismissAlert(auth: Authenticated, id: UUID): Outcome<Unit> {
        val now = now()
        val outcome = dataSource.transaction<Outcome<DashboardAlert>> { connection ->
            val alert = alerts.find(connection, id)?.takeIf { it.dismissedAt == null } ?: return@transaction Outcome.Denied("not_found")
            if (!auth.actor.has(alert.permission)) return@transaction Outcome.Denied("forbidden")
            alerts.dismiss(connection, id, auth.actor.accountId, now)
            audit.record(connection, AuditEntry(AuditActor.of(auth.actor), "alert.dismissed", "dashboard_alert", id.toString(), details = mapOf("source" to alert.source)))
            Outcome.Ok(alert)
        }
        return when (outcome) {
            is Outcome.Denied -> outcome
            is Outcome.Ok -> {
                // Dismissing the alert of a shrunk verified set accepts the new set as the reference.
                if (outcome.value.source == ORGANISATIONS_ALERT_SOURCE) verifiedReference = null
                if (outcome.value.source == ALERT_SOURCE && outcome.value.permission == Permissions.AUDIT_READ) journalAlertPosted = false
                Outcome.Ok(Unit)
            }
        }
    }

    // Validation sessions (contract, section 8) ------------------------------------------------------

    /**
     * Opens a validation session for the signed-in validator: the public key generated by their
     * application for this session, a ceiling (40 at most) and a duration (12 hours at most).
     */
    fun openValidationSession(auth: Authenticated, sessionKey: String, maxValidations: Int, duration: Duration): Outcome<ValidationSession> {
        val actor = auth.actor
        if (!actor.has(Permissions.VALIDATION_SESSION_OPEN)) return Outcome.Denied("forbidden")
        if (!auth.isFresh(clock.instant())) return Outcome.Denied("confirmation_required")
        val key = parseKey(sessionKey) ?: return Outcome.Denied("bad_session_key")
        if (maxValidations !in 1..ValidationSessions.MAX_VALIDATIONS) return Outcome.Denied("bad_ceiling")
        if (!duration.isPositive() || duration > ValidationSessions.MAX_DURATION) return Outcome.Denied("bad_duration")
        val now = now()
        return dataSource.transaction { connection ->
            val grant = grants.live(connection, actor.accountId, now).firstOrNull { it.role == TeamRole.VALIDATOR } ?: return@transaction Outcome.Denied("forbidden")
            val organisation = grant.organisationPubkey
            if (organisation == null || !organisationVerified(organisation)) return@transaction Outcome.Denied("organisation_not_verified")
            if (validationSessions.openOf(actor.accountId)?.takeIf { it.isUsable(now) } != null) return@transaction Outcome.Denied("session_already_open")
            // A session still marked open but past its expiry is closed first.
            validationSessions.openOf(actor.accountId)?.let { validationSessions.end(connection, it.id, ValidationSessionState.EXPIRED, now) }
            if (validationSessions.sessionKeyUsed(connection, key)) return@transaction Outcome.Denied("session_key_used")
            val session = validationSessions.open(connection, actor.accountId, grant.id, key, maxValidations, duration, now)
            audit.record(
                connection,
                AuditEntry(
                    AuditActor.of(actor),
                    "validation.session_opened",
                    "validation_session",
                    session.id.toString(),
                    details = mapOf("week" to session.week, "max" to maxValidations.toString(), "expires_at" to session.expiresAt.epochSecond.toString()),
                ),
            )
            Outcome.Ok(session)
        }
    }

    /** The validator closes their own session (no confirmation needed: it only removes access). */
    fun closeValidationSession(auth: Authenticated, id: UUID): Outcome<ValidationSession> {
        val now = now()
        return dataSource.transaction { connection ->
            val session = validationSessions.find(id)?.takeIf { it.accountId == auth.actor.accountId } ?: return@transaction Outcome.Denied("not_found")
            val ended = validationSessions.end(connection, session.id, ValidationSessionState.CLOSED, now) ?: return@transaction Outcome.Denied("already_closed")
            audit.record(connection, AuditEntry(AuditActor.of(auth.actor), "validation.session_closed", "validation_session", id.toString(), details = mapOf("validations" to ended.validations.toString())))
            Outcome.Ok(ended)
        }
    }

    /** An administrator revokes a session at once (`team.revoke`), with a new verification. */
    fun revokeValidationSession(auth: Authenticated, id: UUID, reason: String?): Outcome<ValidationSession> {
        if (!auth.actor.has(Permissions.TEAM_REVOKE)) return Outcome.Denied("forbidden")
        if (!auth.isFresh(clock.instant())) return Outcome.Denied("confirmation_required")
        val now = now()
        return dataSource.transaction { connection ->
            val ended = validationSessions.end(connection, id, ValidationSessionState.REVOKED, now) ?: return@transaction Outcome.Denied("not_found")
            audit.record(connection, AuditEntry(AuditActor.of(auth.actor), "validation.session_revoked", "validation_session", id.toString(), cleanReason(reason), mapOf("validations" to ended.validations.toString())))
            Outcome.Ok(ended)
        }
    }

    /**
     * Suspends at once every validator grant designated by [organisation] and revokes their open sessions
     * (D-112: when the organisation loses its verification), one journal line per grant.
     */
    fun suspendValidatorsOf(organisation: String, actor: AuditActor = AuditActor.System, reason: String? = BackOfficeTexts.REASON_ORGANISATION_UNVERIFIED): Int {
        val now = now()
        return dataSource.transaction { connection ->
            val affected = grants.activeValidatorsOf(connection, organisation)
            affected.forEach { grant ->
                grants.close(connection, grant.id, GrantStatus.SUSPENDED, now)
                val revoked = validationSessions.revokeForGrant(connection, grant.id, now)
                audit.record(
                    connection,
                    AuditEntry(actor, "team.validator_suspended", TARGET_ACCOUNT, grant.accountId.toString(), reason, grantDetails(grant) + ("validation_sessions" to revoked.size.toString())),
                )
            }
            affected.size
        }
    }

    // Periodic work ---------------------------------------------------------------------------------

    /** What one pass of the periodic task did (counts only). */
    data class MaintenanceReport(
        val expiredGrants: Int,
        val expiredApprovals: Int,
        val suspendedValidators: Int,
        val deletedSessions: Int,
        val deletedInvitations: Int,
    )

    /**
     * Closes what reached its end (grants with an end date, requests older than 7 days, sessions,
     * invitations, validation sessions), deletes old decided requests and dismissed alerts, writes the
     * counts of anonymous failures, checks the journal and suspends the validators whose organisation is
     * no longer verified. Each closing of a grant or a request is a journal line of the server ("système").
     */
    fun maintain(): MaintenanceReport {
        val now = now()
        var expiredGrants = 0
        grants.due(now).forEach { grant ->
            dataSource.transaction<Unit> { connection ->
                val closed = grants.close(connection, grant.id, GrantStatus.EXPIRED, now) ?: return@transaction
                val revoked = validationSessions.revokeForGrant(connection, closed.id, now)
                audit.record(connection, AuditEntry(AuditActor.System, "team.role_expired", TARGET_ACCOUNT, closed.accountId.toString(), details = grantDetails(closed) + ("validation_sessions" to revoked.size.toString())))
                expiredGrants++
            }
        }
        var expiredApprovals = 0
        approvals.due(now).forEach { approval ->
            dataSource.transaction { connection ->
                if (approvals.decide(connection, approval.id, ApprovalStatus.EXPIRED, null, now)) {
                    audit.record(connection, approvalEntry(AuditActor.System, "approval.expired", approval))
                    expiredApprovals++
                }
            }
        }
        flushFailures(all = false)
        val suspended = checkValidatorOrganisations()
        val deletedSessions = sessions.deleteExpired(now)
        val deletedInvitations = invitations.deleteExpired(now)
        validationSessions.expireDue(now)
        sessionCeremonies.purge()
        enrolments.purge()
        val cutoff = now.minusSeconds(BackOfficeConfig.DECIDED_RETENTION.inWholeSeconds)
        approvals.deleteDecidedBefore(cutoff)
        alerts.deleteDismissedBefore(cutoff)
        checkJournal()
        return MaintenanceReport(expiredGrants, expiredApprovals, suspended, deletedSessions, deletedInvitations)
    }

    /**
     * Writes one journal line per period of 10 minutes with the counts of failures no account explains
     * (unknown keys, refused registrations); [all] includes the current period (tests, shutdown).
     */
    fun flushFailures(all: Boolean = false): Int {
        val buckets = failures.drain(all)
        buckets.forEach { (start, codes) ->
            audit.record(
                AuditEntry(
                    AuditActor.Unknown,
                    "auth.anonymous_failures",
                    details = codes.mapValues { it.value.toString() } + mapOf("from" to start.toString(), "until" to (start + failures.bucketSeconds).toString()),
                ),
            )
        }
        return buckets.size
    }

    /**
     * Suspends the validators of every organisation that left the verified set (its sheet replaced without
     * the `p` tag, or its editor key removed). The Agir space has no notification for this: the check runs
     * with the periodic task, and [ValidationSessionGate] refuses such a session at once in the meantime.
     * An empty set, or one that lost more than half of its keys since the last trusted check, suspends
     * nobody: it posts an alert instead (a configuration mistake must not end every validation); dismissing
     * that alert accepts the new set.
     */
    fun checkValidatorOrganisations(): Int {
        val list = verifiedOrganisations ?: return 0
        val validators = grants.activeValidators()
        if (validators.isEmpty()) return 0
        val verified = list()
        val reference = verifiedReference
        if (verified.isEmpty() || (reference != null && verified.size * 2 < reference)) {
            if (alerts.open().none { it.source == ORGANISATIONS_ALERT_SOURCE }) {
                postAlert(ORGANISATIONS_ALERT_SOURCE, BackOfficeTexts.ALERT_ORGANISATIONS_SHRANK, 4, Permissions.TEAM_REVOKE, "/seances")
            }
            return 0
        }
        verifiedReference = verified.size
        return validators.mapNotNull { it.organisationPubkey }.distinct().filter { it !in verified }.sumOf { suspendValidatorsOf(it) }
    }

    // Helpers ---------------------------------------------------------------------------------------

    private fun checkOrganisation(validator: Boolean, organisation: String?): Outcome<String?> {
        val value = organisation?.trim()?.takeIf { it.isNotEmpty() }
        if (!validator) return if (value == null) Outcome.Ok(null) else Outcome.Denied("organisation_not_expected")
        val key = value?.let(::parseKey) ?: return Outcome.Denied("organisation_required")
        if (!organisationVerified(key)) return Outcome.Denied("organisation_not_verified")
        return Outcome.Ok(key)
    }

    private fun credentialTaken(connection: Connection, credentialId: ByteArray): Boolean =
        connection.prepareStatement("SELECT 1 FROM team_credential WHERE credential_id = ?").use { statement ->
            statement.setBytes(1, credentialId)
            statement.executeQuery().next()
        }

    private fun creationOptions(ceremony: String, challenge: ByteArray, handle: ByteArray, pseudonym: String, exclude: List<ByteArray>) = CreationOptionsResponse(
        ceremony,
        PublicKeyCreationOptions(
            challenge = Tokens.base64Url(challenge),
            rp = RelyingParty(config.rpId, BackOfficeTexts.RELYING_PARTY_NAME),
            user = UserEntity(Tokens.base64Url(handle), pseudonym, pseudonym),
            pubKeyCredParams = WebAuthn4jPasskeys.ALGORITHM_IDS.map { CredentialParameter(alg = it) },
            timeout = sessionCeremonies.timeoutMillis,
            excludeCredentials = exclude.map { CredentialDescriptor(id = Tokens.base64Url(it)) },
        ),
    )

    private fun requestOptions(ceremony: String, challenge: ByteArray, allow: List<ByteArray>, timeout: Long) = RequestOptionsResponse(
        ceremony,
        PublicKeyRequestOptions(
            challenge = Tokens.base64Url(challenge),
            rpId = config.rpId,
            timeout = timeout,
            allowCredentials = allow.map { CredentialDescriptor(id = Tokens.base64Url(it)) },
        ),
    )

    companion object {
        const val ACTION_GRANT: String = "team.grant_role"
        const val ACTION_REVOKE: String = "team.revoke_role"
        const val ACTION_REMOVE: String = "team.remove_member"
        const val ACTION_REINSTATE: String = "team.reinstate_member"
        const val ACTION_QUEUE: String = "queue.action"
        const val TARGET_ACCOUNT: String = "team_account"
        const val TARGET_GRANT: String = "team_role_grant"
        const val TARGET_QUEUE_ITEM: String = "queue_item"

        /** The source of the alerts posted by the base itself. */
        const val ALERT_SOURCE: String = "backoffice"
        const val ORGANISATIONS_ALERT_SOURCE: String = "backoffice.organisations"

        /** A member keeps at most this many keys. */
        const val MAX_KEYS: Int = 10

        /** The dashboard flags what is due within 24 hours. */
        const val NEAR_DEADLINE_SECONDS: Long = 24 * 3600

        /** The verification that opens the registration of a new key must answer within this. */
        val ADD_KEY_PROOF_LIFETIME: Duration = 60.seconds

        private const val PRIORITY_SCAN = 50
        private const val MAX_PRIORITIES = 30

        private val LOCAL_LINK = Regex("^/[A-Za-z0-9][^\\\\\\s]{0,198}$")

        /** A link of an alert: a path of the back-office, a slash then a letter or digit, no backslash nor space. */
        fun isLocalLink(link: String): Boolean = LOCAL_LINK.matches(link) && link.none { it.isISOControl() }

        /** A key typed by the team: 64 hexadecimal characters or an npub. */
        fun parseKey(value: String): String? {
            val text = value.trim()
            return try {
                if (text.startsWith("npub1")) PublicKey.fromNpub(text).hex else text.lowercase().takeIf(::isHexKey)
            } catch (e: Exception) {
                null
            }
        }

        fun cleanReason(reason: String?): String? = reason?.trim()?.takeIf { it.isNotEmpty() }?.take(AuditEntry.MAX_REASON_LENGTH)

        fun outcomeCode(outcome: ActionOutcome): String = when (outcome) {
            ActionOutcome.Done -> "done"
            ActionOutcome.NotFound -> "not_found"
            ActionOutcome.AlreadyDecided -> "already_decided"
            is ActionOutcome.Refused -> "refused"
        }

        private fun grantPayload(role: TeamRole, organisation: String?, endsAt: Instant?): Map<String, String> = buildMap {
            put(PendingApprovals.KEY, role.id)
            put("role", role.id)
            organisation?.let { put("organisation", it) }
            endsAt?.let { put("ends_at", it.epochSecond.toString()) }
        }

        private fun grantDetails(grant: RoleGrant): Map<String, String> = buildMap {
            put("grant", grant.id.toString())
            put("role", grant.role.id)
            put("scope", grant.scope)
            grant.organisationPubkey?.let { put("organisation", it) }
            grant.endsAt?.let { put("ends_at", it.epochSecond.toString()) }
        }

        private fun approvalEntry(actor: AuditActor, action: String, approval: PendingApproval): AuditEntry = AuditEntry(
            actor,
            action,
            "pending_approval",
            approval.id.toString(),
            approval.reason,
            approval.payload + mapOf("requested_action" to approval.action, "target" to "${approval.targetType}:${approval.targetId}"),
        )

        private operator fun AuditEntry.plus(pair: Pair<String, String>): AuditEntry = copy(details = details + pair)
    }
}
