package org.politisationpopulairefrancaise.server.archive

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.endpoint
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpStatement
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.readByteArray
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** What one exchange gave. Never holds a URL; a body only when it was accepted whole. */
sealed interface HttpAnswer {
    /** Status 200 and a body of at most the size asked. */
    class Ok(val body: ByteArray) : HttpAnswer

    /** Any other status (no redirection is ever followed). */
    data class Status(val code: Int) : HttpAnswer

    /** No answer: timeout, unreachable host, broken connection (the class name of the error only). */
    data class Failed(val reason: String) : HttpAnswer

    /** The body was larger than allowed; it was not read further. */
    data object TooLarge : HttpAnswer
}

/**
 * The outbound requests of the archive (D-111), with what the infrastructure lead asked for: a
 * timeout on every request, a few attempts at most with an exponential backoff (2 s, then 4 s) and
 * only after a failure that may pass (no answer, 429, 5xx), never a redirection followed, bodies
 * read up to a fixed size. The base URLs come from [ArchiveConfig]; the proxy is set on the engine
 * by [defaultClient]. Nothing is logged here: callers log only counts and class names.
 */
class ArchiveHttp(
    private val client: HttpClient,
    private val timeout: Duration,
    private val pause: suspend (Duration) -> Unit = { delay(it) },
    private val maxAttempts: Int = MAX_ATTEMPTS,
    private val firstBackoff: Duration = FIRST_BACKOFF,
) {
    init {
        require(maxAttempts in 1..MAX_ATTEMPTS) { "At most $MAX_ATTEMPTS attempts" }
    }

    /** [beforeAttempt] runs before each attempt (the spacing asked by a service, for example). */
    suspend fun get(url: String, accept: String, maxBytes: Int, beforeAttempt: suspend () -> Unit = {}): HttpAnswer =
        withRetries(beforeAttempt) {
            exchange(maxBytes) {
                client.prepareGet(url) {
                    header(HttpHeaders.Accept, accept)
                    header(HttpHeaders.UserAgent, USER_AGENT)
                }
            }
        }

    suspend fun post(
        url: String,
        body: ByteArray,
        contentType: ContentType,
        accept: String,
        maxBytes: Int,
        beforeAttempt: suspend () -> Unit = {},
    ): HttpAnswer = withRetries(beforeAttempt) {
        exchange(maxBytes) {
            client.preparePost(url) {
                header(HttpHeaders.Accept, accept)
                header(HttpHeaders.UserAgent, USER_AGENT)
                contentType(contentType)
                setBody(body)
            }
        }
    }

    private suspend fun withRetries(beforeAttempt: suspend () -> Unit, attempt: suspend () -> HttpAnswer): HttpAnswer {
        var wait = firstBackoff
        var answer: HttpAnswer = HttpAnswer.Failed("not_tried")
        for (index in 1..maxAttempts) {
            beforeAttempt()
            answer = attempt()
            if (!mayPass(answer) || index == maxAttempts) return answer
            pause(wait)
            wait *= 2
        }
        return answer
    }

    private suspend fun exchange(maxBytes: Int, prepare: suspend () -> HttpStatement): HttpAnswer = try {
        withTimeoutOrNull(timeout) {
            prepare().execute { response ->
                val status = response.status.value
                if (status != 200) return@execute HttpAnswer.Status(status)
                if ((response.contentLength() ?: 0L) > maxBytes) return@execute HttpAnswer.TooLarge
                val bytes = response.bodyAsChannel().readBuffer(maxBytes.toLong() + 1).readByteArray()
                if (bytes.size > maxBytes) HttpAnswer.TooLarge else HttpAnswer.Ok(bytes)
            }
        } ?: HttpAnswer.Failed("timeout")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        HttpAnswer.Failed(e::class.simpleName ?: "Exception")
    }

    companion object {
        /** Three attempts at most: never a storm of requests towards a service in trouble. */
        const val MAX_ATTEMPTS: Int = 3
        val FIRST_BACKOFF: Duration = 2.seconds

        /** A plain identifier, the same for every request; nothing about the readers of PPF. */
        const val USER_AGENT: String = "PPF-server (archive; https://politisation-populaire-francaise.org)"

        private const val ENGINE_MARGIN_MILLIS = 5_000L

        /** Failures worth another attempt; any other answer is final. */
        fun mayPass(answer: HttpAnswer): Boolean = when (answer) {
            is HttpAnswer.Failed -> true
            is HttpAnswer.Status -> answer.code == 429 || answer.code in 500..599
            else -> false
        }

        /**
         * The client of the archive: no redirection followed, no retry by the engine (done above),
         * timeouts on connection, socket and request, the HTTPS proxy of [ArchiveConfig.proxy] if any.
         */
        fun defaultClient(config: ArchiveConfig): HttpClient = HttpClient(CIO) {
            expectSuccess = false
            followRedirects = false
            engine {
                // The timeout of each exchange is applied by ArchiveHttp; the engine must not cut earlier.
                requestTimeout = config.requestTimeout.inWholeMilliseconds + ENGINE_MARGIN_MILLIS
                endpoint {
                    connectTimeout = config.requestTimeout.inWholeMilliseconds
                    socketTimeout = config.requestTimeout.inWholeMilliseconds
                    connectAttempts = 1
                }
                config.proxy?.let { proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(it.host.removeSurrounding("[", "]"), it.port)) }
            }
        }
    }
}

/**
 * At least [spacing] between two calls of [await] (FreeTSA asks for one request per second at
 * most). The tests give a [pause] that records instead of waiting.
 */
class MinimumSpacing(
    private val spacing: Duration,
    private val pause: suspend (Duration) -> Unit = { delay(it) },
) {
    private val mutex = Mutex()
    private var last: TimeSource.Monotonic.ValueTimeMark? = null

    suspend fun await() = mutex.withLock {
        last?.elapsedNow()?.let { elapsed -> if (elapsed < spacing) pause(spacing - elapsed) }
        last = TimeSource.Monotonic.markNow()
    }
}
