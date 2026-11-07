package org.politisationpopulairefrancaise.server.backoffice

import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource
import kotlin.time.Duration

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

/**
 * The team back-office base (contract docs/integration/back-office.md, step 1): accounts by invitation,
 * passkeys, sessions, roles and permissions, temporary access, two-person rule, emergency cut, journal,
 * queues, alerts and validation sessions. Every action of the team goes through this class, which checks
 * the permission, the freshness of the passkey for sensitive actions, and records the journal line.
 *
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
    sessionRequestVerifier: SessionRequestVerifier = MinimalNip98Verifier(),
) {
    internal val audit = AuditLog(dataSource, clock)
    val accounts = TeamAccounts(dataSource)
    val invitations = TeamInvitations(dataSource)
    val sessions = TeamSessions(dataSource)
    val grants = TeamGrants(dataSource)
    val approvals = PendingApprovals(dataSource)
    val validationSessions = ValidationSessions(dataSource)
    val alerts = DashboardAlerts(dataSource)
    val ceremonies = Ceremonies(clock)

    /** NIP-44 towards `PPF_AUDIT_PUBLIC_KEY` (contract, section 8); null when the key is not configured. */
    val auditSealer: AuditSealer? = config.auditPublicKey?.let(::AuditSealer)

    /** The door of Décider's validation endpoints (NIP-98 with a session key). */
    val validationGate = ValidationSessionGate(validationSessions, sessionRequestVerifier, ::organisationVerified, clock)

    private val log = LoggerFactory.getLogger(BackOffice::class.java)

    private fun now(): Instant = clock.nowSeconds()

    private fun organisationVerified(key: String): Boolean = isVerifiedOrganisation?.invoke(key) == true

    // Journal (read side) ---------------------------------------------------------------------------

    fun journal(actor: TeamActor, filter: AuditFilter, limit: Int = 100): Outcome<List<AuditRecord>> =
        if (!actor.has(Permissions.AUDIT_READ)) Outcome.Denied("forbidden") else Outcome.Ok(audit.query(filter, limit))

    fun verifyJournal(actor: TeamActor): Outcome<AuditChain.Check> =
        if (!actor.has(Permissions.AUDIT_READ)) Outcome.Denied("forbidden") else Outcome.Ok(audit.verifyChain())

    /** The JSON export of the journal: a sensitive reading, itself recorded. */
    fun exportJournal(auth: Authenticated, filter: AuditFilter): Outcome<List<AuditRecord>> {
        if (!auth.actor.has(Permissions.AUDIT_READ)) return Outcome.Denied("forbidden")
        val lines = audit.query(filter, AuditLog.MAX_PAGE)
        audit.record(AuditEntry(AuditActor.of(auth.actor), "audit.exported", details = mapOf("lines" to lines.size.toString())))
        return Outcome.Ok(lines)
    }

    // Bootstrap -------------------------------------------------------------------------------------

    /**
     * The invitation of a first super administrator, created by the command `ppf-backoffice bootstrap` on
     * the server (nobody exists yet to invite). Allowed while fewer than two super administrators exist,
     * counting the bootstrap invitations still valid: the contract wants two of them, and the second one
     * cannot be approved by a single person. Also the way back if both keys were lost.
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

    // Enrolment -------------------------------------------------------------------------------------

    fun findInvitation(token: String): TeamInvitation? = invitations.findValid(token, now())

    fun startEnrolment(token: String): Outcome<CreationOptionsResponse> {
        val invitation = invitations.findValid(token, now()) ?: return Outcome.Denied("invitation_gone")
        val handle = Tokens.randomBytes(32)
        val (id, ceremony) = ceremonies.start { challenge, at -> Ceremony.Enrol(challenge, at, invitation.id, handle) } ?: return Outcome.Denied("busy")
        return Outcome.Ok(creationOptions(id, ceremony.challenge, handle, invitation.pseudonym, emptyList()))
    }

    /** Registers the first key from an invitation: creates the account, its key and a session. */
    fun finishEnrolment(response: RegistrationResponse): Outcome<OpenedSession> {
        val ceremony = ceremonies.take(response.ceremony) as? Ceremony.Enrol ?: return Outcome.Denied("ceremony_gone")
        val name = KeyNames.normalize(response.name) ?: BackOfficeTexts.DEFAULT_KEY_NAME
        val credential = try {
            passkeys.verifyRegistration(response, ceremony.challenge, name)
        } catch (e: PasskeyException) {
            audit.record(AuditEntry(AuditActor.Unknown, "auth.enrolment_failed", "team_invitation", ceremony.invitationId.toString(), details = mapOf("code" to e.code)))
            return Outcome.Denied(e.code)
        }
        val now = now()
        return dataSource.transaction { connection ->
            val invitation = invitations.consume(connection, ceremony.invitationId, now) ?: return@transaction Outcome.Denied("invitation_gone")
            if (accounts.pseudonymTaken(connection, invitation.pseudonym)) return@transaction Outcome.Denied("pseudonym_taken")
            if (credentialTaken(connection, credential.credentialId)) return@transaction Outcome.Denied("key_known")
            val account = accounts.create(connection, invitation.pseudonym, ceremony.userHandle, invitation.createdBy, now)
            val stored = accounts.addCredential(connection, account.id, credential, now)
            val member = AuditActor.Member(account.id, account.pseudonym)
            audit.record(
                connection,
                AuditEntry(member, "team.invitation_accepted", "team_account", account.id.toString(), details = mapOf("invitation" to invitation.id.toString(), "key" to stored.name)),
            )
            if (invitation.bootstrap) {
                val grant = grants.insert(connection, account.id, TeamRole.SUPER_ADMIN, null, null, null, null, now)
                audit.record(connection, AuditEntry(AuditActor.System, "team.role_granted", "team_account", account.id.toString(), details = grantDetails(grant) + ("bootstrap" to "true")))
            } else {
                val inviter = invitation.createdBy?.let { accounts.find(connection, it) }?.takeIf { it.status == AccountStatus.ACTIVE }
                if (inviter != null) {
                    invitation.proposedRoles.forEach { role ->
                        val approval = approvals.create(
                            connection,
                            ACTION_GRANT,
                            role.grantPermission,
                            TARGET_ACCOUNT,
                            account.id.toString(),
                            grantPayload(role, invitation.organisationPubkey, invitation.rolesEndAt),
                            null,
                            inviter.id,
                            now,
                        )
                        audit.record(connection, approvalEntry(AuditActor.Member(inviter.id, inviter.pseudonym), "approval.requested", approval))
                    }
                }
            }
            val opened = sessions.open(connection, account.id, now)
            audit.record(connection, AuditEntry(member, "auth.login", "team_account", account.id.toString(), details = mapOf("key" to stored.name)))
            Outcome.Ok(opened)
        }
    }

    // Sign-in ---------------------------------------------------------------------------------------

    fun startSignIn(): Outcome<RequestOptionsResponse> {
        val (id, ceremony) = ceremonies.start { challenge, at -> Ceremony.SignIn(challenge, at) } ?: return Outcome.Denied("busy")
        return Outcome.Ok(requestOptions(id, ceremony.challenge, emptyList()))
    }

    fun finishSignIn(response: AssertionResponse): Outcome<OpenedSession> {
        val ceremony = ceremonies.take(response.ceremony) as? Ceremony.SignIn ?: return Outcome.Denied("ceremony_gone")
        val credentialId = Tokens.fromBase64Url(response.credentialId) ?: return Outcome.Denied("bad_encoding")
        val credential = accounts.credentialByCredentialId(credentialId)
        if (credential == null) {
            audit.record(AuditEntry(AuditActor.Unknown, "auth.login_failed", details = mapOf("code" to "unknown_key")))
            return Outcome.Denied("unknown_key")
        }
        val account = accounts.find(credential.accountId) ?: return Outcome.Denied("unknown_key")
        val member = AuditActor.Member(account.id, account.pseudonym)
        if (account.status != AccountStatus.ACTIVE) {
            audit.record(AuditEntry(member, "auth.login_failed", "team_account", account.id.toString(), details = mapOf("code" to "account_closed")))
            return Outcome.Denied("account_closed")
        }
        val handle = response.userHandle?.let(Tokens::fromBase64Url)
        if (response.userHandle != null && (handle == null || !handle.contentEquals(account.userHandle))) {
            audit.record(AuditEntry(member, "auth.login_failed", "team_account", account.id.toString(), details = mapOf("code" to "user_mismatch")))
            return Outcome.Denied("assertion_refused")
        }
        val verified = try {
            passkeys.verifyAssertion(response, ceremony.challenge, credential)
        } catch (e: PasskeyException) {
            audit.record(AuditEntry(member, "auth.login_failed", "team_account", account.id.toString(), details = mapOf("code" to e.code)))
            return Outcome.Denied(e.code)
        }
        val now = now()
        accounts.markUsed(credential.id, verified.signCount, verified.backupState, now)
        return dataSource.transaction { connection ->
            val opened = sessions.open(connection, account.id, now)
            audit.record(connection, AuditEntry(member, "auth.login", "team_account", account.id.toString(), details = mapOf("key" to credential.name)))
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
        audit.record(AuditEntry(AuditActor.of(auth.actor), "auth.logout", "team_account", auth.account.id.toString()))
    }

    // New verification before a sensitive action ----------------------------------------------------

    fun startConfirmation(auth: Authenticated): Outcome<RequestOptionsResponse> {
        val keys = accounts.credentials(auth.account.id)
        val (id, ceremony) = ceremonies.start { challenge, at -> Ceremony.Confirm(challenge, at, auth.session.tokenHash, auth.account.id) } ?: return Outcome.Denied("busy")
        return Outcome.Ok(requestOptions(id, ceremony.challenge, keys.map { it.credentialId }))
    }

    fun finishConfirmation(auth: Authenticated, response: AssertionResponse): Outcome<Unit> {
        val ceremony = ceremonies.take(response.ceremony) as? Ceremony.Confirm ?: return Outcome.Denied("ceremony_gone")
        if (ceremony.sessionHash != auth.session.tokenHash || ceremony.accountId != auth.account.id) return Outcome.Denied("ceremony_gone")
        val credentialId = Tokens.fromBase64Url(response.credentialId) ?: return Outcome.Denied("bad_encoding")
        val credential = accounts.credentialByCredentialId(credentialId)?.takeIf { it.accountId == auth.account.id } ?: return Outcome.Denied("unknown_key")
        val verified = try {
            passkeys.verifyAssertion(response, ceremony.challenge, credential)
        } catch (e: PasskeyException) {
            audit.record(AuditEntry(AuditActor.of(auth.actor), "auth.confirmation_failed", "team_account", auth.account.id.toString(), details = mapOf("code" to e.code)))
            return Outcome.Denied(e.code)
        }
        val now = now()
        accounts.markUsed(credential.id, verified.signCount, verified.backupState, now)
        sessions.markVerified(auth.session.tokenHash, now)
        audit.record(AuditEntry(AuditActor.of(auth.actor), "auth.confirmed", "team_account", auth.account.id.toString(), details = mapOf("key" to credential.name)))
        return Outcome.Ok(Unit)
    }

    // Keys of the member ----------------------------------------------------------------------------

    /** Adding a key gives lasting access: it needs a passkey verified less than 5 minutes ago. */
    fun startAddKey(auth: Authenticated): Outcome<CreationOptionsResponse> {
        if (!auth.isFresh(clock.instant())) return Outcome.Denied("confirmation_required")
        val keys = accounts.credentials(auth.account.id)
        if (keys.size >= MAX_KEYS) return Outcome.Denied("too_many_keys")
        val (id, ceremony) = ceremonies.start { challenge, at -> Ceremony.AddKey(challenge, at, auth.session.tokenHash, auth.account.id) } ?: return Outcome.Denied("busy")
        return Outcome.Ok(creationOptions(id, ceremony.challenge, auth.account.userHandle, auth.account.pseudonym, keys.map { it.credentialId }))
    }

    fun finishAddKey(auth: Authenticated, response: RegistrationResponse): Outcome<StoredCredential> {
        val ceremony = ceremonies.take(response.ceremony) as? Ceremony.AddKey ?: return Outcome.Denied("ceremony_gone")
        if (ceremony.sessionHash != auth.session.tokenHash || ceremony.accountId != auth.account.id) return Outcome.Denied("ceremony_gone")
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
            audit.record(connection, AuditEntry(AuditActor.of(auth.actor), "team.key_added", "team_account", auth.account.id.toString(), details = mapOf("key" to stored.name)))
            Outcome.Ok(stored)
        }
    }

    /** Deletes one of one's own keys (never the last one: the account would be locked out). */
    fun deleteKey(auth: Authenticated, credentialId: UUID): Outcome<String> {
        if (!auth.isFresh(clock.instant())) return Outcome.Denied("confirmation_required")
        return dataSource.transaction { connection ->
            if (accounts.credentialCount(connection, auth.account.id) <= 1) return@transaction Outcome.Denied("last_key")
            val name = accounts.deleteCredential(connection, auth.account.id, credentialId) ?: return@transaction Outcome.Denied("not_found")
            audit.record(connection, AuditEntry(AuditActor.of(auth.actor), "team.key_deleted", "team_account", auth.account.id.toString(), details = mapOf("key" to name)))
            Outcome.Ok(name)
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

    fun withdrawInvitation(auth: Authenticated, id: UUID): Outcome<Unit> {
        if (!auth.actor.has(Permissions.TEAM_INVITE)) return Outcome.Denied("forbidden")
        return dataSource.transaction { connection ->
            if (!invitations.delete(connection, id)) return@transaction Outcome.Denied("not_found")
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
    fun revokeGrant(auth: Authenticated, grantId: UUID, reason: String?): Outcome<Revocation> {
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
                Outcome.Ok(Revocation.Waiting(approval))
            } else {
                val closed = grants.close(connection, grant.id, GrantStatus.REVOKED, now) ?: return@transaction Outcome.Denied("not_found")
                val revokedSessions = validationSessions.revokeForGrant(connection, closed.id, now)
                audit.record(
                    connection,
                    AuditEntry(AuditActor.of(actor), "team.role_revoked", TARGET_ACCOUNT, closed.accountId.toString(), text, grantDetails(closed) + ("validation_sessions" to revokedSessions.size.toString())),
                )
                Outcome.Ok(Revocation.Done)
            }
        }
    }

    /** The requests [actor] could decide (they hold the permission and did not ask themselves), and theirs. */
    fun openApprovals(actor: TeamActor): List<PendingApproval> =
        approvals.open(clock.instant()).filter { (actor.has(it.permission) && it.requestedBy != actor.accountId) || it.requestedBy == actor.accountId }

    /** Approves a request of somebody else (S-61): the action is done now, with the two names in the journal. */
    fun approve(auth: Authenticated, approvalId: UUID): Outcome<ApprovalStatus> {
        val actor = auth.actor
        if (!auth.isFresh(clock.instant())) return Outcome.Denied("confirmation_required")
        val now = now()
        val queueWork = dataSource.transaction<Outcome<Pair<ApprovalStatus, PendingApproval?>>> { connection ->
            val approval = approvals.lockOpen(connection, approvalId, now) ?: return@transaction Outcome.Denied("approval_gone")
            if (!actor.has(approval.permission)) return@transaction Outcome.Denied("forbidden")
            if (approval.requestedBy == actor.accountId) return@transaction Outcome.Denied("own_request")
            if (approval.targetType == TARGET_ACCOUNT && approval.targetId == actor.accountId.toString()) return@transaction Outcome.Denied("own_grant")
            val me = AuditActor.of(actor)
            when (approval.action) {
                ACTION_GRANT -> Outcome.Ok(approveGrant(connection, approval, actor, me, now) to null)
                ACTION_REVOKE -> {
                    val grant = grants.find(UUID.fromString(approval.targetId))
                    if (grant != null && grant.accountId == actor.accountId) return@transaction Outcome.Denied("own_grant")
                    val closed = grant?.let { grants.close(connection, it.id, GrantStatus.REVOKED, now) }
                    val status = if (closed == null) ApprovalStatus.FAILED else ApprovalStatus.APPROVED
                    approvals.decide(connection, approval.id, status, actor.accountId, now)
                    audit.record(connection, approvalEntry(me, if (closed == null) "approval.failed" else "approval.approved", approval))
                    if (closed != null) {
                        audit.record(connection, AuditEntry(me, "team.role_revoked", TARGET_ACCOUNT, closed.accountId.toString(), approval.reason, grantDetails(closed) + ("approval" to approval.id.toString())))
                    }
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
        val (status, queueApproval) = when (queueWork) {
            is Outcome.Denied -> return queueWork
            is Outcome.Ok -> queueWork.value
        }
        // A queue action approved by a second person is performed after the approval is committed: the
        // queue belongs to a pole and works with its own transactions.
        if (queueApproval != null) {
            val queue = queues.find(queueApproval.payload["queue"].orEmpty())
            val item = queueApproval.payload["item"].orEmpty()
            val action = queueApproval.payload["action"].orEmpty()
            val outcome = queue?.let { runQueueAction(it, item, action, queueApproval.reason, actor) } ?: ActionOutcome.NotFound
            audit.record(queueEntry(actor, queueApproval.payload["queue"].orEmpty(), item, action, queueApproval.reason, outcome) + ("approval" to queueApproval.id.toString()))
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

    fun refuseApproval(auth: Authenticated, approvalId: UUID, reason: String?): Outcome<Unit> {
        val actor = auth.actor
        val now = now()
        return dataSource.transaction { connection ->
            val approval = approvals.lockOpen(connection, approvalId, now) ?: return@transaction Outcome.Denied("approval_gone")
            if (!actor.has(approval.permission)) return@transaction Outcome.Denied("forbidden")
            if (approval.requestedBy == actor.accountId) return@transaction Outcome.Denied("own_request")
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

    /**
     * Emergency cut (S-62): every session, every grant and every key of the account at once; its open
     * requests and validation sessions end too. Anyone may cut their own account (a stolen phone); cutting
     * someone else needs `team.revoke`, and `team.manage_admins` for an administrator.
     */
    fun emergencyCut(auth: Authenticated, accountId: UUID, reason: String?): Outcome<CutReport> =
        closeAccount(auth, accountId, reason, AccountStatus.SUSPENDED, "team.emergency_cut")

    /** Removal of a member: immediate, without second approval (contract, section 4); the journal keeps their trace. */
    fun removeMember(auth: Authenticated, accountId: UUID, reason: String?): Outcome<CutReport> =
        closeAccount(auth, accountId, reason, AccountStatus.REMOVED, "team.member_removed")

    private fun closeAccount(auth: Authenticated, accountId: UUID, reason: String?, status: AccountStatus, action: String): Outcome<CutReport> {
        val actor = auth.actor
        val self = accountId == actor.accountId
        val now = now()
        if (!self && !auth.isFresh(clock.instant())) return Outcome.Denied("confirmation_required")
        return dataSource.transaction { connection ->
            val target = accounts.find(connection, accountId) ?: return@transaction Outcome.Denied("not_found")
            if (target.status == AccountStatus.REMOVED || (status == AccountStatus.SUSPENDED && target.status != AccountStatus.ACTIVE)) {
                return@transaction Outcome.Denied("already_closed")
            }
            if (!self) {
                val holdsAdministration = grants.live(connection, accountId, now).any { it.role.isAdministration }
                val needed = if (holdsAdministration) Permissions.TEAM_MANAGE_ADMINS else Permissions.TEAM_REVOKE
                if (!actor.has(needed)) return@transaction Outcome.Denied("forbidden")
            }
            accounts.close(connection, accountId, status, now)
            val revoked = grants.revokeAll(connection, accountId, now)
            val keys = accounts.deleteCredentials(connection, accountId)
            val closedSessions = sessions.closeAll(connection, accountId)
            val validation = validationSessions.revokeForAccount(connection, accountId, now)
            val requests = approvals.cancelForTarget(connection, TARGET_ACCOUNT, accountId.toString(), now) + cancelRequestedBy(connection, accountId, now)
            val report = CutReport(closedSessions, revoked.size, keys, validation, requests)
            audit.record(
                connection,
                AuditEntry(
                    AuditActor.of(actor),
                    action,
                    TARGET_ACCOUNT,
                    accountId.toString(),
                    cleanReason(reason),
                    mapOf(
                        "sessions" to report.sessions.toString(),
                        "grants" to report.grants.toString(),
                        "keys" to report.keys.toString(),
                        "validation_sessions" to report.validationSessions.toString(),
                        "requests" to report.requests.toString(),
                        "roles" to revoked.joinToString(",") { it.role.id },
                    ),
                ),
            )
            Outcome.Ok(report)
        }
    }

    private fun cancelRequestedBy(connection: Connection, accountId: UUID, now: Instant): Int =
        connection.prepareStatement("UPDATE team_pending_approval SET status = 'failed', decided_at = ? WHERE status = 'pending' AND requested_by = ?").use { update ->
            update.setInstant(1, now)
            update.setObject(2, accountId)
            update.executeUpdate()
        }

    // Queues ----------------------------------------------------------------------------------------

    /**
     * An action on a queue item (contract, section 7): permission of the queue and of the action, reason
     * when required, new verification when sensitive, second person when required, journal line always.
     */
    fun performQueueAction(auth: Authenticated, queueId: String, itemId: String, actionId: String, reason: String?): Outcome<QueueResult> {
        val actor = auth.actor
        val queue = queues.find(queueId) ?: return Outcome.Denied("not_found")
        if (!actor.has(queue.viewPermission)) return Outcome.Denied("forbidden")
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
        val outcome = runQueueAction(queue, itemId, actionId, text, actor)
        audit.record(queueEntry(actor, queueId, itemId, actionId, text, outcome))
        return Outcome.Ok(QueueResult.Performed(outcome))
    }

    private fun runQueueAction(queue: BackOfficeQueue, itemId: String, actionId: String, reason: String?, actor: TeamActor): ActionOutcome = try {
        queue.perform(itemId, actionId, reason, actor)
    } catch (e: Exception) {
        // Only the class: the message of a database error can quote the content in question.
        log.error("Queue ${queue.id}: action $actionId failed with ${e::class.qualifiedName}.")
        ActionOutcome.Refused(BackOfficeTexts.QUEUE_ACTION_FAILED)
    }

    private fun queueEntry(actor: TeamActor, queueId: String, itemId: String, actionId: String, reason: String?, outcome: ActionOutcome): AuditEntry = AuditEntry(
        AuditActor.of(actor),
        "queue.${queueId}.$actionId",
        TARGET_QUEUE_ITEM,
        "$queueId:$itemId",
        reason,
        buildMap {
            put("outcome", outcomeCode(outcome))
            if (outcome is ActionOutcome.Refused) put("refusal", outcome.reason.take(500))
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
     * données ouvertes refusé ». [permission] says who sees it and may dismiss it; [link] is a back-office path.
     */
    fun postAlert(source: String, title: String, level: Int, permission: String, link: String? = null): DashboardAlert {
        require(Regex("^[a-z0-9_]+(\\.[a-z0-9_]+)*$").matches(source)) { "The source is the stable id of a space" }
        require(title.isNotBlank() && title.length <= 200) { "An alert title has 1 to 200 characters" }
        require(level in 1..5) { "The importance level is between 1 and 5" }
        require(Permissions.isValidId(permission)) { "The permission is a dotted identifier" }
        require(link == null || (link.startsWith("/") && !link.startsWith("//"))) { "The link is a path of the back-office" }
        val now = now()
        return dataSource.transaction { connection ->
            val alert = alerts.insert(connection, source, title.trim(), level, link, permission, now)
            audit.record(connection, AuditEntry(AuditActor.System, "alert.posted", "dashboard_alert", alert.id.toString(), details = mapOf("source" to source, "level" to level.toString(), "title" to alert.title)))
            alert
        }
    }

    fun dismissAlert(auth: Authenticated, id: UUID): Outcome<Unit> {
        val now = now()
        return dataSource.transaction { connection ->
            val alert = alerts.find(connection, id)?.takeIf { it.dismissedAt == null } ?: return@transaction Outcome.Denied("not_found")
            if (!auth.actor.has(alert.permission)) return@transaction Outcome.Denied("forbidden")
            alerts.dismiss(connection, id, auth.actor.accountId, now)
            audit.record(connection, AuditEntry(AuditActor.of(auth.actor), "alert.dismissed", "dashboard_alert", id.toString(), details = mapOf("source" to alert.source)))
            Outcome.Ok(Unit)
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

    /** An administrator revokes a session at once (`team.revoke`). */
    fun revokeValidationSession(auth: Authenticated, id: UUID, reason: String?): Outcome<ValidationSession> {
        if (!auth.actor.has(Permissions.TEAM_REVOKE)) return Outcome.Denied("forbidden")
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
     * invitations, validation sessions), deletes old decided requests and dismissed alerts, and suspends
     * the validators whose organisation is no longer verified. Each closing of a grant or a request is a
     * journal line of the server ("système").
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
        val suspended = checkValidatorOrganisations()
        val deletedSessions = sessions.deleteExpired(now)
        val deletedInvitations = invitations.deleteExpired(now)
        validationSessions.expireDue(now)
        val cutoff = now.minusSeconds(BackOfficeConfig.DECIDED_RETENTION.inWholeSeconds)
        approvals.deleteDecidedBefore(cutoff)
        alerts.deleteDismissedBefore(cutoff)
        return MaintenanceReport(expiredGrants, expiredApprovals, suspended, deletedSessions, deletedInvitations)
    }

    /**
     * Suspends the validators of every organisation that left the verified set (its sheet replaced without
     * the `p` tag, or its editor key removed). The Agir space has no notification for this: the check runs
     * with the periodic task, and [ValidationSessionGate] refuses such a session at once in the meantime.
     */
    fun checkValidatorOrganisations(): Int {
        val list = verifiedOrganisations ?: return 0
        val validators = grants.activeValidators()
        if (validators.isEmpty()) return 0
        val verified = list()
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
            timeout = ceremonies.timeoutMillis,
            excludeCredentials = exclude.map { CredentialDescriptor(id = Tokens.base64Url(it)) },
        ),
    )

    private fun requestOptions(ceremony: String, challenge: ByteArray, allow: List<ByteArray>) = RequestOptionsResponse(
        ceremony,
        PublicKeyRequestOptions(
            challenge = Tokens.base64Url(challenge),
            rpId = config.rpId,
            timeout = ceremonies.timeoutMillis,
            allowCredentials = allow.map { CredentialDescriptor(id = Tokens.base64Url(it)) },
        ),
    )

    companion object {
        const val ACTION_GRANT: String = "team.grant_role"
        const val ACTION_REVOKE: String = "team.revoke_role"
        const val ACTION_QUEUE: String = "queue.action"
        const val TARGET_ACCOUNT: String = "team_account"
        const val TARGET_GRANT: String = "team_role_grant"
        const val TARGET_QUEUE_ITEM: String = "queue_item"

        /** A member keeps at most this many keys. */
        const val MAX_KEYS: Int = 10

        /** The dashboard flags what is due within 24 hours. */
        const val NEAR_DEADLINE_SECONDS: Long = 24 * 3600

        private const val PRIORITY_SCAN = 50
        private const val MAX_PRIORITIES = 30

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

/** Counts of an emergency cut or a removal. */
data class CutReport(val sessions: Int, val grants: Int, val keys: Int, val validationSessions: Int, val requests: Int)

/** A withdrawal of a role: done at once, or waiting for a second super administrator. */
sealed interface Revocation {
    data object Done : Revocation

    data class Waiting(val approval: PendingApproval) : Revocation
}
