package org.politisationpopulairefrancaise.server.backoffice

import org.politisationpopulairefrancaise.core.identity.PublicKey
import java.net.URI
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Settings of the team back-office (contract docs/integration/back-office.md), read from the
 * environment only:
 *
 * - PPF_BACKOFFICE_ORIGIN: the address of the back-office as the browser sees it, for example
 *   `https://equipe.preprod.politisation-populaire-francaise.org`. Its host name is the WebAuthn
 *   relying party id. `http://localhost:<port>` (or 127.0.0.1) is accepted only when PPF_ENVIRONMENT is
 *   `development`. Without this variable the back-office is off (no second port).
 * - PPF_BACKOFFICE_PORT: the internal port of the back-office routes (default 8081), never the public port.
 * - PPF_AUDIT_PUBLIC_KEY: the public key (hex or npub) of the "audit" key whose secret stays offline with
 *   the owner; [AuditSealer] encrypts towards it (NIP-44). Optional.
 * - PPF_BACKOFFICE_MAINTENANCE_INTERVAL_MINUTES: how often grants, sessions, invitations and requests past
 *   their end are closed, and the validators of organisations no longer verified suspended (default 5;
 *   0 disables the periodic task, for tests).
 *
 * The server refuses to start with an invalid value: a back-office reachable under the wrong name would
 * refuse every passkey anyway, and a plain `http` address outside development would expose the cookie.
 */
data class BackOfficeConfig(
    val origin: String,
    val port: Int = DEFAULT_PORT,
    val auditPublicKey: PublicKey? = null,
    val maintenanceInterval: Duration = DEFAULT_MAINTENANCE_INTERVAL,
) {
    /** The WebAuthn relying party id: the host name of [origin]. */
    val rpId: String = URI(origin).host

    init {
        require(port in 1..65535) { "The back-office port must be between 1 and 65535" }
        require(!maintenanceInterval.isNegative()) { "The maintenance interval cannot be negative" }
    }

    companion object {
        const val ORIGIN_VARIABLE: String = "PPF_BACKOFFICE_ORIGIN"
        const val PORT_VARIABLE: String = "PPF_BACKOFFICE_PORT"
        const val AUDIT_KEY_VARIABLE: String = "PPF_AUDIT_PUBLIC_KEY"
        const val MAINTENANCE_VARIABLE: String = "PPF_BACKOFFICE_MAINTENANCE_INTERVAL_MINUTES"

        const val DEFAULT_PORT: Int = 8081
        val DEFAULT_MAINTENANCE_INTERVAL: Duration = 5.minutes

        /** Sessions: closed after 30 minutes without a request, 8 hours after the sign-in at the latest. */
        val SESSION_IDLE: Duration = 30.minutes
        val SESSION_MAX: Duration = 8.hours

        /** A sensitive action asks for the passkey again when the last verification is older than this. */
        val REAUTH_WINDOW: Duration = 5.minutes

        val INVITATION_LIFETIME: Duration = 48.hours
        val APPROVAL_LIFETIME: Duration = 7.days

        /** Decided requests and dismissed alerts are deleted after this (their trace stays in the journal). */
        val DECIDED_RETENTION: Duration = 30.days

        /**
         * The back-office settings, or null when [ORIGIN_VARIABLE] is not set (back-office off).
         * [environment] is PPF_ENVIRONMENT; [publicPort] the port of the public site, which the
         * back-office may never share.
         */
        fun fromEnvironment(environment: String, publicPort: Int, env: (String) -> String? = System::getenv): BackOfficeConfig? {
            val origin = env(ORIGIN_VARIABLE)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val port = env(PORT_VARIABLE)?.trim()?.takeIf { it.isNotEmpty() }?.let {
                requireNotNull(it.toIntOrNull()) { "$PORT_VARIABLE must be a number" }
            } ?: DEFAULT_PORT
            require(port != publicPort) { "$PORT_VARIABLE must differ from the public port $publicPort" }
            val auditKey = env(AUDIT_KEY_VARIABLE)?.trim()?.takeIf { it.isNotEmpty() }?.let(::parseAuditKey)
            val interval = env(MAINTENANCE_VARIABLE)?.trim()?.toIntOrNull()?.takeIf { it >= 0 }?.minutes ?: DEFAULT_MAINTENANCE_INTERVAL
            return BackOfficeConfig(checkedOrigin(origin, environment), port, auditKey, interval)
        }

        /**
         * [value] if it is an acceptable origin: `https://<host>[:port]` with nothing after, or a local
         * `http` address in development only.
         */
        fun checkedOrigin(value: String, environment: String): String {
            val uri = try {
                URI(value)
            } catch (e: Exception) {
                throw IllegalArgumentException("$ORIGIN_VARIABLE is not a valid address")
            }
            val host = uri.host
            require(host != null && uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
                "$ORIGIN_VARIABLE must be scheme://host[:port], nothing else"
            }
            require(uri.rawPath.isNullOrEmpty()) { "$ORIGIN_VARIABLE must not end with a path (not even '/')" }
            when (uri.scheme) {
                "https" -> Unit
                "http" -> require(environment == "development" && host in LOCAL_HOSTS) {
                    "$ORIGIN_VARIABLE must use https (http://localhost is accepted in development only)"
                }
                else -> throw IllegalArgumentException("$ORIGIN_VARIABLE must use https")
            }
            return "${uri.scheme}://${host.lowercase()}" + if (uri.port != -1) ":${uri.port}" else ""
        }

        fun parseAuditKey(value: String): PublicKey = try {
            if (value.startsWith("npub1")) PublicKey.fromNpub(value) else PublicKey.fromHex(value.lowercase())
        } catch (e: Exception) {
            throw IllegalArgumentException("$AUDIT_KEY_VARIABLE must be a public key (64 hexadecimal characters or npub)")
        }

        private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1")
    }
}

/** What the back-office needs from outside: its settings and the clock (injectable for the tests of dates). */
class BackOfficeSetup(
    val config: BackOfficeConfig,
    val clock: Clock = Clock.systemUTC(),
) {
    companion object {
        fun fromEnvironment(environment: String, publicPort: Int, env: (String) -> String? = System::getenv): BackOfficeSetup? =
            BackOfficeConfig.fromEnvironment(environment, publicPort, env)?.let(::BackOfficeSetup)
    }
}
