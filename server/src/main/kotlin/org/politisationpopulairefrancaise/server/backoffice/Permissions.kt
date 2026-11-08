package org.politisationpopulairefrancaise.server.backoffice

import java.util.UUID

/**
 * The stable permission identifiers of the back-office (contract, section 5). Code and journal use these
 * strings; they never change once a grant or a journal line refers to them.
 */
object Permissions {
    const val TEAM_MANAGE_ADMINS: String = "team.manage_admins"
    const val TEAM_INVITE: String = "team.invite"
    const val TEAM_REVOKE: String = "team.revoke"
    const val TEAM_GRANT: String = "team.grant"
    const val AUDIT_READ: String = "audit.read"
    const val SETTINGS_MANAGE: String = "settings.manage"
    const val REPORTS_DECIDE: String = "reports.decide"
    const val APPEALS_DECIDE: String = "appeals.decide"
    const val PHAROS_RECORD: String = "pharos.record"
    const val RIGHT_OF_REPLY_DECIDE: String = "rightofreply.decide"
    const val VALIDATION_SESSION_OPEN: String = "validation.session.open"
    const val ELECTED_CERTIFY: String = "elected.certify"
    const val ELECTED_REVOKE: String = "elected.revoke"

    /**
     * Searching the legal register on a judicial requisition (D-077), with a second holder of the same
     * permission (two-person rule): `super_admin` and `admin` only.
     */
    const val LEGAL_REQUISITION: String = "legal.requisition"

    /** The permission to decide on the items of the queue [queueId] (for example `queue.agir.held.decide`). */
    fun queueDecide(queueId: String): String = "queue.$queueId.decide"

    /**
     * The permissions usable with a single passkey. Every other one waits until the account has a
     * second key (a backup), as the contract asks before any sensitive permission (section 4): a
     * validator opens their own session from their phone, nothing else.
     */
    val WITHOUT_BACKUP_KEY: Set<String> = setOf(VALIDATION_SESSION_OPEN)

    private val ID = Regex("^[a-z0-9_]+(\\.[a-z0-9_]+)+$")

    fun isValidId(value: String): Boolean = value.length <= 120 && ID.matches(value)
}

/**
 * The fixed roles of version 1 (contract, section 5) and what they allow. A pole that plugs a new queue
 * adds its permission to the role that decides on it, here, in the same pull request.
 */
enum class TeamRole(val id: String, val label: String, val permissions: Set<String>) {
    /**
     * Every permission, `team.manage_admins` included, and every queue; except opening a validation
     * session, which belongs to the volunteers designated by a verified organisation (D-112).
     */
    SUPER_ADMIN("super_admin", BackOfficeTexts.ROLE_SUPER_ADMIN, emptySet()),
    ADMIN(
        "admin",
        BackOfficeTexts.ROLE_ADMIN,
        setOf(
            Permissions.TEAM_INVITE,
            Permissions.TEAM_REVOKE,
            Permissions.TEAM_GRANT,
            Permissions.AUDIT_READ,
            Permissions.SETTINGS_MANAGE,
            Permissions.LEGAL_REQUISITION,
            // The sessions of the validators with an abnormal pace (Décider, une-personne-une-voix.md 4.3 and 8).
            Permissions.queueDecide("decider.validation_sessions"),
        ),
    ),
    MODERATOR(
        "moderator",
        BackOfficeTexts.ROLE_MODERATOR,
        setOf(
            Permissions.REPORTS_DECIDE,
            Permissions.APPEALS_DECIDE,
            Permissions.PHAROS_RECORD,
            Permissions.queueDecide("agir.held"),
            Permissions.queueDecide("agir.testimonies"),
            Permissions.queueDecide("revendications.proposals"),
            // The propositions and amendments of the citizen deliberations (Décider, DE-25).
            Permissions.queueDecide("decider.deliberation"),
        ),
    ),
    EDITOR(
        "editor",
        BackOfficeTexts.ROLE_EDITOR,
        setOf(
            "queue.editorial.comprendre.review",
            "queue.editorial.comprendre.publish",
            "queue.editorial.voir.review",
            "queue.editorial.voir.publish",
            Permissions.RIGHT_OF_REPLY_DECIDE,
        ),
    ),
    ORGANISATION_VERIFIER("organisation_verifier", BackOfficeTexts.ROLE_ORGANISATION_VERIFIER, setOf(Permissions.queueDecide("agir.organisations"))),

    /** A volunteer designated by a verified organisation of the Agir directory, approved by PPF (D-112). */
    VALIDATOR("validator", BackOfficeTexts.ROLE_VALIDATOR, setOf(Permissions.VALIDATION_SESSION_OPEN)),

    /** A team member without organisation who checks the mandates of elected officials' accounts. */
    ELECTED_CERTIFIER("elected_certifier", BackOfficeTexts.ROLE_ELECTED_CERTIFIER, setOf(Permissions.ELECTED_CERTIFY, Permissions.ELECTED_REVOKE)),
    ;

    /** Naming or removing these roles needs `team.manage_admins` and a second super administrator. */
    val isAdministration: Boolean get() = this == SUPER_ADMIN || this == ADMIN

    /** The permission a member needs to ask for this role, and the second person to approve it. */
    val grantPermission: String get() = if (isAdministration) Permissions.TEAM_MANAGE_ADMINS else Permissions.TEAM_GRANT

    companion object {
        fun fromId(id: String): TeamRole? = entries.firstOrNull { it.id == id }
    }
}

/**
 * A member of the team as the back-office sees them during one request (contract, section 7): what a
 * queue receives in `perform`. [permissions] already takes the backup-key rule into account.
 */
data class TeamActor(
    val accountId: UUID,
    val pseudonym: String,
    val permissions: Set<String>,
    val roles: Set<TeamRole> = emptySet(),
    /** Whether the account still has a single passkey (its permissions are then reduced). */
    val needsBackupKey: Boolean = false,
    /** Super administrators hold every permission but [Permissions.VALIDATION_SESSION_OPEN]. */
    private val superAdmin: Boolean = false,
) {
    fun has(permission: String): Boolean =
        permission in permissions || (superAdmin && permission != Permissions.VALIDATION_SESSION_OPEN)

    companion object {
        /**
         * The actor of an account holding [roles] and [credentialCount] passkeys: the union of the
         * permissions of the roles, reduced to [Permissions.WITHOUT_BACKUP_KEY] with a single key.
         */
        fun of(accountId: UUID, pseudonym: String, roles: Set<TeamRole>, credentialCount: Int): TeamActor {
            val backup = credentialCount >= 2
            val all = roles.flatMapTo(mutableSetOf()) { it.permissions }
            val superAdmin = TeamRole.SUPER_ADMIN in roles
            return TeamActor(
                accountId = accountId,
                pseudonym = pseudonym,
                permissions = if (backup) all else all.intersect(Permissions.WITHOUT_BACKUP_KEY),
                roles = roles,
                needsBackupKey = !backup,
                superAdmin = superAdmin && backup,
            )
        }
    }
}
