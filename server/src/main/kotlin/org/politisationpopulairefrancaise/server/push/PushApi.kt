package org.politisationpopulairefrancaise.server.push

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.request.contentLength
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.api.ApiError
import org.politisationpopulairefrancaise.server.relay.RateLimiter
import org.politisationpopulairefrancaise.server.revendications.PeriodBudget
import java.net.URI
import java.time.Instant
import javax.sql.DataSource
import kotlin.time.Duration.Companion.hours

@Serializable
data class VapidKeyResponse(val publicKey: String)

@Serializable
data class SubscriptionRequest(val scope: String, val endpoint: String, val p256dh: String, val auth: String)

@Serializable
data class SubscriptionResponse(val id: String)

/**
 * Everything the push wake-ups need at run time: settings, table, limits and dispatcher. Built by
 * [start] with the application; [stored] is the hook of the publication path.
 */
class PushService(
    val config: PushConfig,
    /** Null without a database: subscriptions then answer 503. */
    val subscriptions: PushSubscriptions?,
    /** Null when push is off or without a database. */
    val dispatcher: WakeDispatcher?,
    val perKey: RateLimiter = RateLimiter(SUBSCRIPTIONS_PER_KEY_PER_MINUTE),
    val publicPerDay: PeriodBudget = PeriodBudget(PUBLIC_SUBSCRIPTIONS_PER_DAY, 24.hours, maxTrackedKeys = 1),
    val clock: () -> Instant = Instant::now,
) {
    /** Called right after an event is stored; never blocks the publication. */
    fun stored(event: SignedEvent) {
        dispatcher?.stored(event)
    }

    companion object {
        const val SUBSCRIPTIONS_PER_KEY_PER_MINUTE: Int = 5
        const val PUBLIC_SUBSCRIPTIONS_PER_DAY: Int = 10_000
        const val MAX_BODY_BYTES: Long = 4 * 1024
        const val MAX_ENDPOINT_CHARS: Int = 1024

        /** Builds the service and, when push is on and a database exists, starts its worker with the application. */
        fun start(application: Application, dataSource: DataSource?, config: PushConfig, sender: WakeSender? = null): PushService {
            val subscriptions = dataSource?.let(::PushSubscriptions)
            val dispatcher = if (config.enabled && subscriptions != null) {
                WakeDispatcher(subscriptions, sender ?: WebPushSender(config)).also { it.start(application, application.log) }
            } else {
                null
            }
            if (!config.enabled) application.log.info("Push wake-ups are off (no ${PushConfig.VAPID_KEY_FILE_VARIABLE}).")
            return PushService(config, subscriptions, dispatcher)
        }
    }
}

/**
 * The push API (contract, section 3):
 *
 * - GET /api/v1/push/vapid: the VAPID public key, `503 push_disabled` when push is off.
 * - POST /api/v1/push/subscriptions: `{scope, endpoint, p256dh, auth}`; a mailbox subscription also
 *   carries a NIP-98 authorization signed by the account key. `201 {id}`; the same endpoint keeps its id.
 * - DELETE /api/v1/push/subscriptions/{id}: `204`, or `404` for an unknown id.
 *
 * Neither the client address nor the endpoint is ever logged.
 */
fun Route.pushApi(service: PushService) {
    val config = service.config
    route("/api/v1/push") {
        get("vapid") {
            val vapid = config.vapid ?: return@get call.pushDisabled()
            call.respond(VapidKeyResponse(vapid.publicKeyBase64Url))
        }
        post("subscriptions") {
            if (!config.enabled) return@post call.pushDisabled()
            val subscriptions = service.subscriptions ?: return@post call.databaseUnavailable()
            if ((call.request.contentLength() ?: 0) > PushService.MAX_BODY_BYTES) {
                return@post call.respond(HttpStatusCode.PayloadTooLarge, ApiError("too_large", "A subscription is at most ${PushService.MAX_BODY_BYTES} bytes."))
            }
            val body = call.receive<ByteArray>()
            if (body.size > PushService.MAX_BODY_BYTES) {
                return@post call.respond(HttpStatusCode.PayloadTooLarge, ApiError("too_large", "A subscription is at most ${PushService.MAX_BODY_BYTES} bytes."))
            }
            val request = runCatching { RequestJson.decodeFromString(SubscriptionRequest.serializer(), body.decodeToString()) }.getOrNull()
                ?: return@post call.badRequest("malformed_subscription", "The body is not a subscription.")
            val scope = PushScope.fromWire(request.scope)
                ?: return@post call.badRequest("bad_scope", "The scope is \"public\" or \"mailbox\".")
            val endpoint = parseEndpoint(request.endpoint, config.allowHttpEndpoints)
                ?: return@post call.badRequest("bad_endpoint", "The endpoint must be an https URL.")
            if (endpoint.host.lowercase() !in config.allowedHosts) {
                return@post call.respond(HttpStatusCode.UnprocessableEntity, ApiError("push_host_not_allowed", "This push service is not accepted by the server."))
            }
            val p256dh = Base64Url.decodeOrNull(request.p256dh)
            val auth = Base64Url.decodeOrNull(request.auth)
            if (p256dh == null || auth == null || auth.size != WebPushEncryption.AUTH_SECRET_BYTES ||
                runCatching { P256.decodePoint(p256dh) }.isFailure
            ) {
                return@post call.badRequest("bad_keys", "p256dh must be a P-256 point (65 bytes) and auth 16 bytes, in base64url.")
            }
            val now = service.clock()
            val pubkey = if (scope == PushScope.MAILBOX) {
                val key = Nip98.verify(call.request.headers[HttpHeaders.Authorization], config.subscriptionsUrl, "POST", body, now)
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("unauthorized", "A mailbox subscription needs a valid NIP-98 authorization."))
                when (val budget = service.perKey.tryAcquire(key)) {
                    is RateLimiter.Outcome.Limited -> return@post call.rateLimited(budget.retryAfterSeconds, "Too many subscriptions for this key; try again later.")
                    RateLimiter.Outcome.Allowed -> Unit
                }
                key
            } else {
                null
            }
            val normalized = endpoint.toString()
            if (scope == PushScope.PUBLIC && !withContext(Dispatchers.IO) { subscriptions.exists(normalized) }) {
                when (val budget = service.publicPerDay.tryAcquire(PeriodBudget.GLOBAL_KEY)) {
                    is RateLimiter.Outcome.Limited -> return@post call.rateLimited(budget.retryAfterSeconds, "Too many new subscriptions today; try again later.")
                    RateLimiter.Outcome.Allowed -> Unit
                }
            }
            val saved = withContext(Dispatchers.IO) {
                subscriptions.save(scope, normalized, Base64Url.encode(p256dh), Base64Url.encode(auth), pubkey, now)
            }
            call.respond(HttpStatusCode.Created, SubscriptionResponse(saved.id))
        }
        delete("subscriptions/{id}") {
            val subscriptions = service.subscriptions ?: return@delete call.databaseUnavailable()
            val id = call.parameters["id"].orEmpty()
            val deleted = ID_PATTERN.matches(id) && withContext(Dispatchers.IO) { subscriptions.delete(id) }
            if (deleted) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound, ApiError("not_found", "No subscription with this id."))
            }
        }
    }
}

/** An absolute https URL (http in development only), without credentials or fragment; null otherwise. */
internal fun parseEndpoint(raw: String, allowHttp: Boolean): URI? {
    if (raw.length > PushService.MAX_ENDPOINT_CHARS) return null
    val uri = runCatching { URI(raw) }.getOrNull() ?: return null
    val schemeAllowed = uri.scheme == "https" || (allowHttp && uri.scheme == "http")
    if (!schemeAllowed || uri.host.isNullOrEmpty() || uri.rawUserInfo != null || uri.rawFragment != null) return null
    return uri
}

private val ID_PATTERN = Regex("^[A-Za-z0-9_-]{22}$")

private val RequestJson = Json { ignoreUnknownKeys = true }

private suspend fun RoutingCall.pushDisabled() =
    respond(HttpStatusCode.ServiceUnavailable, ApiError("push_disabled", "Push notifications are not configured on this server."))

private suspend fun RoutingCall.databaseUnavailable() =
    respond(HttpStatusCode.ServiceUnavailable, ApiError("database_unavailable", "The database is not configured."))

private suspend fun RoutingCall.badRequest(error: String, message: String) =
    respond(HttpStatusCode.BadRequest, ApiError(error, message))

private suspend fun RoutingCall.rateLimited(retryAfterSeconds: Long, message: String) {
    response.headers.append(HttpHeaders.RetryAfter, retryAfterSeconds.toString())
    respond(HttpStatusCode.TooManyRequests, ApiError("rate_limited", message))
}
