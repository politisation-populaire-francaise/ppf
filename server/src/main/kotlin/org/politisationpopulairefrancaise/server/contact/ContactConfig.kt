package org.politisationpopulairefrancaise.server.contact

import org.politisationpopulairefrancaise.core.contact.CaseEnvelopes
import org.politisationpopulairefrancaise.core.contact.CaseGoals
import org.politisationpopulairefrancaise.core.contact.ContactInbox
import org.politisationpopulairefrancaise.core.identity.PublicKey
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Settings of the cases (M30; docs/integration/contact-reclamations.md, section 6), read from the environment
 * only:
 *
 * - PPF_CONTACT_INBOX_KEY: the public key (hexadecimal or npub) of the inbox of the team, the one built into
 *   the app for this environment. Empty: in development, the example inbox ([ContactInbox.EXAMPLE], whose
 *   secret half is public); elsewhere the routes answer `503 contact_not_open`. The example key is refused in
 *   production.
 * - PPF_CASE_POW_BITS (16): the proof of work (NIP-13) asked to create a case, 0 to 32; a private setting.
 * - PPF_CASE_GOAL_CONTACT_DAYS (15), PPF_CASE_GOAL_BUG_DAYS, PPF_CASE_GOAL_SUGGESTION_DAYS,
 *   PPF_CASE_GOAL_REPORT_DAYS (none), PPF_CASE_GOAL_MODERATION_DAYS (15), PPF_CASE_GOAL_FUNDS_DAYS (30),
 *   PPF_CASE_GOAL_TEAM_MEMBER_DAYS (30), PPF_CASE_GOAL_OTHER_DAYS (30): the internal goals, in days, 1 to 365,
 *   0 for none; the legal deadlines are not settings.
 * - PPF_CONTACT_RETENTION_INTERVAL_MINUTES (1440): how often the retention rules run; 0 turns the task off
 *   (tests).
 */
data class ContactConfig(
    /** The inbox the envelopes of the person must be addressed to; null when the cases are closed. */
    val inbox: PublicKey?,
    val workBits: Int = CaseEnvelopes.DEFAULT_WORK_BITS,
    val goals: CaseGoals = CaseGoals.DEFAULT,
    val retentionInterval: Duration = 24.hours,
    /** Why the routes stay closed, for the one line of the log; null when open. */
    val closedReason: String? = null,
) {
    val open: Boolean get() = inbox != null && closedReason == null

    /** Whether the inbox is the example one, readable by anybody. */
    val isExample: Boolean get() = inbox == ContactInbox.EXAMPLE

    init {
        require(workBits in 0..MAX_WORK_BITS) { "The proof of work is between 0 and $MAX_WORK_BITS bits" }
    }

    companion object {
        const val INBOX_VARIABLE: String = "PPF_CONTACT_INBOX_KEY"
        const val WORK_VARIABLE: String = "PPF_CASE_POW_BITS"
        const val MAX_WORK_BITS: Int = 32

        fun parseKey(value: String?): PublicKey? {
            val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return runCatching { if (text.startsWith("npub1")) PublicKey.fromNpub(text) else PublicKey.fromHex(text.lowercase()) }.getOrNull()
        }

        fun fromEnvironment(env: (String) -> String? = System::getenv): ContactConfig {
            val environment = env("PPF_ENVIRONMENT") ?: "development"
            val raw = env(INBOX_VARIABLE)?.trim().orEmpty()
            val parsed = parseKey(raw)
            val inbox = when {
                raw.isEmpty() && environment == "development" -> ContactInbox.EXAMPLE
                else -> parsed
            }
            val closed = when {
                raw.isNotEmpty() && parsed == null -> "$INBOX_VARIABLE is not a public key"
                inbox == null -> "$INBOX_VARIABLE is not set"
                environment == "production" && inbox == ContactInbox.EXAMPLE -> "the example inbox is refused in production"
                else -> null
            }
            fun goal(name: String, default: Int?): Int? {
                val value = env("PPF_CASE_GOAL_${name}_DAYS")?.trim()?.toIntOrNull() ?: return default
                return when (value) {
                    0 -> null
                    in 1..CaseGoals.MAX_GOAL_DAYS -> value
                    else -> default
                }
            }
            val defaults = CaseGoals.DEFAULT
            return ContactConfig(
                inbox = inbox,
                workBits = env(WORK_VARIABLE)?.trim()?.toIntOrNull()?.takeIf { it in 0..MAX_WORK_BITS } ?: CaseEnvelopes.DEFAULT_WORK_BITS,
                goals = CaseGoals(
                    contactDays = goal("CONTACT", defaults.contactDays),
                    bugDays = goal("BUG", defaults.bugDays),
                    suggestionDays = goal("SUGGESTION", defaults.suggestionDays),
                    reportDays = goal("REPORT", defaults.reportDays),
                    moderationDecisionDays = goal("MODERATION", defaults.moderationDecisionDays),
                    fundsDays = goal("FUNDS", defaults.fundsDays),
                    teamMemberDays = goal("TEAM_MEMBER", defaults.teamMemberDays),
                    otherClaimDays = goal("OTHER", defaults.otherClaimDays),
                ),
                retentionInterval = env("PPF_CONTACT_RETENTION_INTERVAL_MINUTES")?.trim()?.toIntOrNull()?.takeIf { it >= 0 }?.minutes ?: 24.hours,
                closedReason = closed,
            )
        }
    }
}

/** What the cases need from outside: settings and clock (the tests move it by hand). */
class ContactSetup(val config: ContactConfig, val clock: Clock = Clock.systemUTC()) {
    companion object {
        fun fromEnvironment(env: (String) -> String? = System::getenv): ContactSetup = ContactSetup(ContactConfig.fromEnvironment(env))
    }
}
