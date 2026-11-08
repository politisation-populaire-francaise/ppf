package org.politisationpopulairefrancaise.server.archive

import io.ktor.client.HttpClient
import kotlinx.coroutines.delay
import java.net.URI
import java.net.URISyntaxException
import java.security.SecureRandom
import java.time.Clock
import java.util.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Settings of the timestamped archive (D-111, docs/integration/archivage-horodate.md), read from the
 * environment only:
 *
 * - PPF_ARCHIVE_ENABLED: `true` or `false`. Default: on with PPF_ENVIRONMENT=production, off
 *   elsewhere (development, tests; preproduction unless set to `true`). Off, the server contacts
 *   nobody: digests are still queued by [ArchiveService.stamp] and stored proofs are still served.
 * - PPF_ARCHIVE_AGGREGATORS: base URLs of the OpenTimestamps aggregators, separated by commas
 *   (default: the four of the official client). At least [MIN_AGGREGATOR_ANSWERS], since a batch
 *   counts as stamped only with that many answers.
 * - PPF_ARCHIVE_CALENDARS: the calendars whose pending attestations may be upgraded, as host names
 *   or `*.` patterns separated by commas (default: the whitelist of the official client).
 * - PPF_ARCHIVE_EXPLORERS: base URLs of exactly two independent Esplora explorers, separated by a comma.
 * - PPF_ARCHIVE_TSA_URL: the RFC 3161 timestamping authority (default FreeTSA); `off` to do without.
 * - PPF_ARCHIVE_BATCH_MINUTES, PPF_ARCHIVE_UPGRADE_MINUTES: how often a batch leaves and how often
 *   the waiting proofs are upgraded and checked (default 60, from 5 to 1440).
 * - PPF_ARCHIVE_TIMEOUT_SECONDS: the longest wait for one answer (default 30, from 1 to 120).
 * - The HTTPS proxy, if any (read only when the archive is on): the JVM properties
 *   `https.proxyHost` and `https.proxyPort`, otherwise the variable HTTPS_PROXY (or `https_proxy`),
 *   `http://host:port`. NO_PROXY is not read: every request of the archive goes to the Internet.
 *
 * Every URL: https, or http towards localhost or 127.0.0.1 only (a fake service on the same
 * machine); no user name, password, query or fragment. An invalid URL or proxy stops the server at
 * startup with a message that never repeats the value; invalid numbers fall back to the defaults.
 */
data class ArchiveConfig(
    val enabled: Boolean = false,
    val aggregators: List<String> = DEFAULT_AGGREGATORS,
    val calendars: CalendarWhitelist = CalendarWhitelist(DEFAULT_CALENDARS),
    val explorers: List<String> = DEFAULT_EXPLORERS,
    /** Null: no RFC 3161 token, OpenTimestamps alone. */
    val tsaUrl: String? = DEFAULT_TSA_URL,
    val batchInterval: Duration = DEFAULT_INTERVAL,
    val upgradeInterval: Duration = DEFAULT_INTERVAL,
    val requestTimeout: Duration = DEFAULT_TIMEOUT,
    val proxy: HttpsProxy? = null,
) {
    init {
        aggregators.forEach { checkUrl(it, AGGREGATORS_VARIABLE) }
        require(aggregators.toSet().size == aggregators.size) { "$AGGREGATORS_VARIABLE lists the same aggregator twice" }
        require(aggregators.size >= MIN_AGGREGATOR_ANSWERS) { "$AGGREGATORS_VARIABLE needs at least $MIN_AGGREGATOR_ANSWERS aggregators" }
        explorers.forEach { checkUrl(it, EXPLORERS_VARIABLE) }
        require(explorers.size == 2 && host(explorers[0]) != host(explorers[1])) {
            "$EXPLORERS_VARIABLE needs exactly two explorers on two different hosts"
        }
        tsaUrl?.let { checkUrl(it, TSA_VARIABLE) }
        require(batchInterval.isPositive() && upgradeInterval.isPositive()) { "The intervals of the archive must be positive" }
        require(requestTimeout.isPositive() && requestTimeout <= MAX_TIMEOUT) { "The timeout of the archive must be between 1 second and $MAX_TIMEOUT" }
    }

    companion object {
        const val ENABLED_VARIABLE: String = "PPF_ARCHIVE_ENABLED"
        const val AGGREGATORS_VARIABLE: String = "PPF_ARCHIVE_AGGREGATORS"
        const val CALENDARS_VARIABLE: String = "PPF_ARCHIVE_CALENDARS"
        const val EXPLORERS_VARIABLE: String = "PPF_ARCHIVE_EXPLORERS"
        const val TSA_VARIABLE: String = "PPF_ARCHIVE_TSA_URL"
        const val BATCH_VARIABLE: String = "PPF_ARCHIVE_BATCH_MINUTES"
        const val UPGRADE_VARIABLE: String = "PPF_ARCHIVE_UPGRADE_MINUTES"
        const val TIMEOUT_VARIABLE: String = "PPF_ARCHIVE_TIMEOUT_SECONDS"

        /** The default aggregators of the official client (opentimestamps-client, `otsclient/args.py`). */
        val DEFAULT_AGGREGATORS: List<String> = listOf(
            "https://a.pool.opentimestamps.org",
            "https://b.pool.opentimestamps.org",
            "https://a.pool.eternitywall.com",
            "https://ots.btc.catallaxy.com",
        )

        /** The default whitelist of the official library (python-opentimestamps, `calendar.py`). */
        val DEFAULT_CALENDARS: List<String> = listOf(
            "*.calendar.opentimestamps.org",
            "*.calendar.eternitywall.com",
            "*.calendar.catallaxy.com",
        )

        /** Two independent public Esplora APIs (Blockstream's API, also served by mempool.space). */
        val DEFAULT_EXPLORERS: List<String> = listOf("https://mempool.space/api", "https://blockstream.info/api")

        /** FreeTSA: free, commercial use allowed, no attribution required, one request per second at most. */
        const val DEFAULT_TSA_URL: String = "https://freetsa.org/tsr"

        /** D-111: a batch counts as stamped with at least two independent calendars. */
        const val MIN_AGGREGATOR_ANSWERS: Int = 2

        val DEFAULT_INTERVAL: Duration = 60.minutes
        val DEFAULT_TIMEOUT: Duration = 30.seconds
        val MAX_TIMEOUT: Duration = 120.seconds
        private val INTERVAL_MINUTES: IntRange = 5..1440
        private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1")
        private val OFF_VALUES = setOf("off", "none", "false", "0")

        /**
         * The settings of [env]; [property] gives the JVM system properties (the standard proxy
         * settings). Throws [IllegalArgumentException] on an invalid URL or proxy.
         */
        fun fromEnvironment(
            env: (String) -> String? = System::getenv,
            property: (String) -> String? = System::getProperty,
        ): ArchiveConfig {
            val production = env("PPF_ENVIRONMENT")?.trim() == "production"
            val enabled = when (env(ENABLED_VARIABLE)?.trim()?.lowercase()) {
                "true", "1", "yes", "oui" -> true
                "false", "0", "no", "non" -> false
                else -> production
            }
            fun list(name: String, default: List<String>): List<String> =
                env(name)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.takeIf { it.isNotEmpty() } ?: default
            fun minutes(name: String): Duration = env(name)?.trim()?.toIntOrNull()?.takeIf { it in INTERVAL_MINUTES }?.minutes ?: DEFAULT_INTERVAL
            val tsa = env(TSA_VARIABLE)?.trim()?.takeIf { it.isNotEmpty() }
            return ArchiveConfig(
                enabled = enabled,
                aggregators = list(AGGREGATORS_VARIABLE, DEFAULT_AGGREGATORS).map { checkUrl(it, AGGREGATORS_VARIABLE) },
                calendars = CalendarWhitelist(list(CALENDARS_VARIABLE, DEFAULT_CALENDARS)),
                explorers = list(EXPLORERS_VARIABLE, DEFAULT_EXPLORERS).map { checkUrl(it, EXPLORERS_VARIABLE) },
                tsaUrl = when {
                    tsa == null -> DEFAULT_TSA_URL
                    tsa.lowercase() in OFF_VALUES -> null
                    else -> checkUrl(tsa, TSA_VARIABLE)
                },
                batchInterval = minutes(BATCH_VARIABLE),
                upgradeInterval = minutes(UPGRADE_VARIABLE),
                requestTimeout = env(TIMEOUT_VARIABLE)?.trim()?.toIntOrNull()
                    ?.takeIf { it in 1..MAX_TIMEOUT.inWholeSeconds.toInt() }?.seconds ?: DEFAULT_TIMEOUT,
                // The proxy is read only when it would be used: a stray variable never stops a server that contacts nobody.
                proxy = if (enabled) HttpsProxy.fromSystem(property, env) else null,
            )
        }

        /**
         * A base URL, checked and without trailing slash. The messages name the variable, never the
         * value: a mistaken URL may hold a password.
         */
        fun checkUrl(value: String, variable: String): String {
            val uri = try {
                URI(value.trim())
            } catch (e: URISyntaxException) {
                throw IllegalArgumentException("$variable holds an invalid URL")
            }
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase()
            require(!host.isNullOrEmpty()) { "$variable holds a URL without host" }
            require(uri.rawUserInfo == null) { "$variable must not contain a user name or a password" }
            require(uri.rawQuery == null && uri.rawFragment == null) { "$variable must not contain a query or a fragment" }
            require(scheme == "https" || (scheme == "http" && host in LOCAL_HOSTS)) {
                "$variable must use https (http is accepted only for localhost or 127.0.0.1)"
            }
            return uri.toString().trimEnd('/')
        }

        private fun host(url: String): String? = runCatching { URI(url).host?.lowercase() }.getOrNull()
    }
}

/**
 * The calendars the server may ask for the rest of a proof, as the official library decides it
 * (python-opentimestamps, `UrlWhitelist`): an address read in a pending attestation is followed only
 * if it is exactly `https://<host>`, without port, path, user name, query or fragment, and its host
 * is one of [patterns] (`*.calendar.opentimestamps.org` matches any host ending with
 * `.calendar.opentimestamps.org`, never that name alone). Plain http is always refused.
 */
data class CalendarWhitelist(val patterns: List<String>) {
    private val exact: Set<String>
    private val suffixes: Set<String>

    init {
        val normalised = patterns.map { it.trim().lowercase() }
        require(normalised.isNotEmpty()) { "${ArchiveConfig.CALENDARS_VARIABLE} needs at least one calendar" }
        normalised.forEach { pattern ->
            val name = pattern.removePrefix("*.")
            require(name.contains('.') && name.all { it in HOST_CHARACTERS } && !name.startsWith('.') && !name.endsWith('.') && !name.contains("..")) {
                "${ArchiveConfig.CALENDARS_VARIABLE} holds an invalid host name or pattern"
            }
        }
        exact = normalised.filterNot { it.startsWith("*.") }.toSet()
        suffixes = normalised.filter { it.startsWith("*.") }.map { it.removePrefix("*") }.toSet()
    }

    fun allows(calendarUri: String): Boolean {
        val uri = try {
            URI(calendarUri)
        } catch (e: URISyntaxException) {
            return false
        }
        val rawHost = uri.host ?: return false
        if (uri.scheme != "https" || calendarUri != "https://$rawHost") return false
        val host = rawHost.lowercase()
        if (host.any { it !in HOST_CHARACTERS }) return false
        return host in exact || suffixes.any { suffix ->
            host.endsWith(suffix) && host.length > suffix.length && !host.removeSuffix(suffix).endsWith('.')
        }
    }

    private companion object {
        val HOST_CHARACTERS: Set<Char> = (('a'..'z') + ('0'..'9') + listOf('-', '.')).toSet()
    }
}

/**
 * An HTTP proxy through which the HTTPS requests of the archive are tunnelled (CONNECT): TLS stays
 * end to end, the proxy only sees host names. Proxy authentication is not supported.
 */
data class HttpsProxy(val host: String, val port: Int) {
    init {
        require(host.isNotEmpty() && host.length <= 253 && host.all { it.isLetterOrDigit() || it in ".-_:[]" }) { "The HTTPS proxy names an invalid host" }
        require(port in 1..65535) { "The port of the HTTPS proxy must be between 1 and 65535" }
    }

    companion object {
        const val HOST_PROPERTY: String = "https.proxyHost"
        const val PORT_PROPERTY: String = "https.proxyPort"
        const val VARIABLE: String = "HTTPS_PROXY"

        /** The port the JVM itself uses when `https.proxyPort` is not set. */
        private const val DEFAULT_JVM_PORT = 443
        private const val DEFAULT_HTTP_PORT = 80

        /** The JVM properties first, as the JDK's own clients read them; then HTTPS_PROXY, then https_proxy. */
        fun fromSystem(property: (String) -> String?, env: (String) -> String?): HttpsProxy? {
            property(HOST_PROPERTY)?.trim()?.takeIf { it.isNotEmpty() }?.let { host ->
                val port = property(PORT_PROPERTY)?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    it.toIntOrNull() ?: throw IllegalArgumentException("$PORT_PROPERTY is not a port number")
                } ?: DEFAULT_JVM_PORT
                return HttpsProxy(host, port)
            }
            val value = (env(VARIABLE) ?: env(VARIABLE.lowercase()))?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return parse(value)
        }

        /** `http://host:port` (or `host:port`). The messages never repeat the value. */
        fun parse(value: String): HttpsProxy {
            val uri = try {
                URI(if ("://" in value) value else "http://$value")
            } catch (e: URISyntaxException) {
                throw IllegalArgumentException("$VARIABLE is not a valid proxy address")
            }
            require(uri.scheme?.lowercase() == "http") { "$VARIABLE must be an http:// proxy (the tunnel to each host stays TLS end to end)" }
            require(uri.rawUserInfo == null) { "$VARIABLE must not contain a user name or a password (proxy authentication is not supported)" }
            require(uri.rawQuery == null && uri.rawFragment == null && (uri.rawPath.isNullOrEmpty() || uri.rawPath == "/")) {
                "$VARIABLE must be only http://host:port"
            }
            val host = uri.host ?: throw IllegalArgumentException("$VARIABLE must name a host")
            return HttpsProxy(host, if (uri.port == -1) DEFAULT_HTTP_PORT else uri.port)
        }
    }
}

/**
 * What the archive needs from outside: its settings, how to build its HTTP client (the tests give a
 * fake engine), the clock (for the day of a request), the source of the nonces and the pause between
 * two attempts (the tests do not wait).
 */
class ArchiveSetup(
    val config: ArchiveConfig = ArchiveConfig(),
    val client: (ArchiveConfig) -> HttpClient = ArchiveHttp::defaultClient,
    val clock: Clock = Clock.systemUTC(),
    val random: Random = SecureRandom(),
    val pause: suspend (Duration) -> Unit = { delay(it) },
) {
    companion object {
        fun fromEnvironment(
            env: (String) -> String? = System::getenv,
            property: (String) -> String? = System::getProperty,
        ): ArchiveSetup = ArchiveSetup(ArchiveConfig.fromEnvironment(env, property))
    }
}
