package org.politisationpopulairefrancaise.server.push

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/** What became of a wake-up. */
sealed interface WakeResult {
    /** Accepted by the push service. */
    data object Delivered : WakeResult

    /** 404 or 410: the subscription no longer exists and must be deleted. */
    data object Gone : WakeResult

    /** The push service asked to wait (429) until [at]; nothing was sent. */
    data class RetryAt(val at: Instant) : WakeResult

    /** Any other failure (network, 4xx, 5xx); not retried, the next activity will wake again. */
    data object Failed : WakeResult
}

/** Sends one wake-up to one subscription. */
fun interface WakeSender {
    suspend fun wake(subscription: PushSubscription): WakeResult
}

/** The HTTP exchange of a push, behind an interface so that tests need no network. */
fun interface PushTransport {
    data class Response(val status: Int, val retryAfter: String?)

    suspend fun post(url: URI, headers: Map<String, String>, body: ByteArray): Response
}

/** The JDK HTTP client: no redirect followed, short timeouts, nothing logged. */
class JdkPushTransport(
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(5))
        .build(),
) : PushTransport {
    override suspend fun post(url: URI, headers: Map<String, String>, body: ByteArray): PushTransport.Response =
        withContext(Dispatchers.IO) {
            val request = HttpRequest.newBuilder(url)
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .apply { headers.forEach { (name, value) -> header(name, value) } }
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.discarding())
            PushTransport.Response(response.statusCode(), response.headers().firstValue("Retry-After").orElse(null))
        }
}

/**
 * Web Push (RFC 8030) of the constant wake-up `{"v":1}`, encrypted with `aes128gcm` (RFC 8291) and
 * authorized by a VAPID token (RFC 8292). A push to an allowed host goes to [PushConfig.internalUrl]
 * when it is set, keeping the path and query of the endpoint, never through the public name.
 * A 429 answer blocks the host until its `Retry-After`.
 */
class WebPushSender(
    private val config: PushConfig,
    private val transport: PushTransport = JdkPushTransport(),
    private val clock: () -> Instant = Instant::now,
) : WakeSender {
    private val vapid: VapidKeys = requireNotNull(config.vapid) { "Push is disabled" }
    private val blockedUntil = ConcurrentHashMap<String, Instant>()

    override suspend fun wake(subscription: PushSubscription): WakeResult {
        val endpoint = runCatching { URI(subscription.endpoint) }.getOrNull() ?: return WakeResult.Failed
        val host = endpoint.host?.lowercase() ?: return WakeResult.Failed
        // Checked again at sending time: the list may have changed since the subscription.
        if (host !in config.allowedHosts || !isUnifiedPushTopic(endpoint)) return WakeResult.Failed
        val now = clock()
        blockedUntil[host]?.let { until -> if (until.isAfter(now)) return WakeResult.RetryAt(until) else blockedUntil.remove(host, until) }
        val userAgentKey = Base64Url.decodeOrNull(subscription.p256dh) ?: return WakeResult.Failed
        val authSecret = Base64Url.decodeOrNull(subscription.auth) ?: return WakeResult.Failed
        val body = runCatching { WebPushEncryption.encrypt(PAYLOAD, userAgentKey, authSecret) }.getOrNull() ?: return WakeResult.Failed
        val headers = mapOf(
            "Authorization" to vapid.authorization(endpoint, config.publicUrl, now),
            "TTL" to TTL_SECONDS.toString(),
            "Content-Encoding" to "aes128gcm",
            "Content-Type" to "application/octet-stream",
            "Urgency" to if (subscription.scope == PushScope.MAILBOX) "high" else "normal",
        )
        val response = try {
            transport.post(targetOf(endpoint), headers, body)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            return WakeResult.Failed
        }
        return when (response.status) {
            in 200..299 -> WakeResult.Delivered
            404, 410 -> WakeResult.Gone
            429 -> {
                val until = now.plusSeconds(retryAfterSeconds(response.retryAfter, now))
                blockedUntil[host] = until
                WakeResult.RetryAt(until)
            }
            else -> WakeResult.Failed
        }
    }

    /** Where the request really goes: the internal base of our ntfy, or the endpoint itself. */
    internal fun targetOf(endpoint: URI): URI {
        val internal = config.internalUrl ?: return endpoint
        val path = endpoint.rawPath?.takeIf { it.isNotEmpty() } ?: "/"
        val query = endpoint.rawQuery?.let { "?$it" } ?: ""
        return URI(internal.toString().trimEnd('/') + path + query)
    }

    companion object {
        /** The whole content of every push: a constant, it says nothing but "synchronize". */
        val PAYLOAD: ByteArray = """{"v":1}""".encodeToByteArray()
        const val TTL_SECONDS: Int = 3600
        const val DEFAULT_RETRY_SECONDS: Long = 60
        const val MAX_RETRY_SECONDS: Long = 24 * 60 * 60

        /** `Retry-After` in seconds or as an HTTP date, bounded to a day; one minute when absent or unreadable. */
        fun retryAfterSeconds(value: String?, now: Instant): Long {
            val text = value?.trim().orEmpty()
            val seconds = text.toLongOrNull()
                ?: runCatching { Duration.between(now, ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).seconds }.getOrNull()
                ?: DEFAULT_RETRY_SECONDS
            return seconds.coerceIn(1, MAX_RETRY_SECONDS)
        }
    }
}
