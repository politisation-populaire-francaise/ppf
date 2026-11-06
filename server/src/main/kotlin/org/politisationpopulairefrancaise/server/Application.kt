package org.politisationpopulairefrancaise.server

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.CallFailed
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import org.politisationpopulairefrancaise.core.AppInfo
import org.politisationpopulairefrancaise.core.HealthStatus
import org.politisationpopulairefrancaise.server.api.ApiError
import org.politisationpopulairefrancaise.server.api.PostLimits
import org.politisationpopulairefrancaise.server.api.eventsApi
import org.politisationpopulairefrancaise.server.api.groupsApi
import org.politisationpopulairefrancaise.server.db.Database
import org.politisationpopulairefrancaise.server.db.DatabaseConfig
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.groups.GroupDirectory
import org.politisationpopulairefrancaise.server.relay.RelayConfig
import org.politisationpopulairefrancaise.server.relay.WrapRetention
import org.politisationpopulairefrancaise.web.webRoutes
import javax.sql.DataSource

/**
 * Runtime configuration, read from environment variables only (never from the repository).
 *
 * - PORT: TCP port to listen on (default 8080).
 * - PPF_ENVIRONMENT: "production", "preproduction" or "development" (default).
 * - DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD or DATABASE_PASSWORD_FILE: see [DatabaseConfig].
 * - PPF_EDITOR_KEYS: keys of the editorial team, see [PublicationPolicy].
 * - PPF_WRAP_RETENTION_DAYS, PPF_WRAP_CLEANUP_INTERVAL_MINUTES, PPF_WRAP_MAX_PER_RECIPIENT,
 *   PPF_POST_RATE_PER_MINUTE, PPF_POST_GLOBAL_RATE_PER_MINUTE: relay settings, see [RelayConfig].
 */
object ServerConfig {
    val port: Int = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val environment: String = System.getenv("PPF_ENVIRONMENT") ?: "development"
}

fun main() {
    val dataSource = DatabaseConfig.fromEnvironment()?.let { config ->
        Database.connect(config).also(Database::migrate)
    }
    // Bind on every interface inside the container; the reverse proxy is the only
    // process allowed to reach it (see infra/).
    embeddedServer(Netty, port = ServerConfig.port, host = "0.0.0.0") {
        module(dataSource)
    }.start(wait = true)
}

/**
 * Turns any unhandled exception into a generic 500 answer. Without it, the engine would log the
 * exception's message, which for a database error can quote the failing row, that is an event's
 * content and author. Only the exception class is logged.
 */
private val ErrorShield = createApplicationPlugin("ErrorShield") {
    on(CallFailed) { call, cause ->
        if (cause is CancellationException) throw cause
        call.application.log.error("Unhandled ${cause::class.qualifiedName} while answering a request.")
        if (!call.response.isCommitted) {
            try {
                call.respond(HttpStatusCode.InternalServerError, ApiError("internal_error", "The server could not handle the request."))
            } catch (e: Exception) {
                // The answer had already started; nothing more can be said to the client.
            }
        }
    }
}

/**
 * Wires every plugin and route. Kept separate from main() so that tests can
 * start the application in memory, with or without a database.
 *
 * Privacy rule: no request logging plugin is installed. Client addresses are never
 * written anywhere (cahier des charges, section 5.1).
 */
fun Application.module(
    dataSource: DataSource? = null,
    policy: PublicationPolicy = PublicationPolicy.fromEnvironment(),
    relay: RelayConfig = RelayConfig.fromEnvironment(),
) {
    val repository = dataSource?.let(::EventRepository)
    if (repository == null) {
        log.info("No database configured: the events API answers 503 (health check and web pages work).")
    } else if (relay.cleanupInterval.isPositive()) {
        // Encrypted envelopes are kept for a limited time; the task lives and dies with the application.
        WrapRetention(repository, relay.wrapRetention).start(this, relay.cleanupInterval, log)
    }
    val limits = PostLimits(relay)
    val groups = repository?.let(::GroupDirectory)
    install(ErrorShield)
    install(ContentNegotiation) {
        json()
    }
    install(DefaultHeaders) {
        // Strict security headers on every answer. HTTPS and HSTS are handled by the
        // reverse proxy (Caddy) which terminates TLS.
        header(HttpHeaders.Server, "PPF")
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("Referrer-Policy", "no-referrer")
        header("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(), usb=(), interest-cohort=()")
        header("Cross-Origin-Opener-Policy", "same-origin")
        header("Cross-Origin-Resource-Policy", "same-origin")
        header(
            "Content-Security-Policy",
            "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; " +
                "font-src 'self'; connect-src 'self'; manifest-src 'self'; " +
                "frame-ancestors 'none'; base-uri 'self'; form-action 'self'; object-src 'none'",
        )
    }
    routing {
        get("/health") {
            call.respond(
                HealthStatus(
                    status = HealthStatus.OK,
                    service = "ppf-server",
                    version = AppInfo.VERSION,
                    environment = ServerConfig.environment,
                ),
            )
        }
        eventsApi(repository, policy, limits, groups)
        groupsApi(groups)
        webRoutes()
    }
}
