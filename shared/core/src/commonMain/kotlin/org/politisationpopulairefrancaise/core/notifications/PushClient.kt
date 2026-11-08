package org.politisationpopulairefrancaise.core.notifications

import fr.acinq.bitcoin.Crypto
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.signing.sign
import kotlin.io.encoding.Base64

/** A push address given by the platform (UnifiedPush endpoint) with its RFC 8291 keys, in base64url. */
data class PushEndpoint(val url: String, val p256dh: String, val auth: String)

/** The answer of the server to a push call. */
sealed interface PushResult<out T> {
    data class Done<T>(val value: T) : PushResult<T>

    /** Notifications are off on this server (no VAPID key), or it refused the address. */
    data class Refused(val status: Int, val error: String?) : PushResult<Nothing>

    data class Unavailable(val cause: Throwable?) : PushResult<Nothing>
}

/** What the device asks the server about push (see [PushClient]); an interface so it can be faked in tests. */
interface PushApi {
    suspend fun vapidPublicKey(): PushResult<String>

    suspend fun subscribe(scope: PushScope, endpoint: PushEndpoint, signer: EventSigner? = null): PushResult<String>

    suspend fun unsubscribe(subscriptionId: String): PushResult<Unit>
}

/**
 * The device side of `/api/v1/push` (contract docs/integration/notifications.md, section 3). The
 * mailbox registration is signed by the account key (NIP-98), the public one is anonymous: the two
 * use different push addresses, so the server cannot link them.
 */
class PushClient(private val client: HttpClient, private val baseUrl: String) : PushApi {

    override suspend fun vapidPublicKey(): PushResult<String> = call {
        val response = client.get("$baseUrl/api/v1/push/vapid")
        if (response.status.value == 200) {
            PushResult.Done(Json.decodeFromString(VapidKey.serializer(), response.bodyAsText()).publicKey)
        } else {
            refused(response.status.value, response.bodyAsText())
        }
    }

    override suspend fun subscribe(scope: PushScope, endpoint: PushEndpoint, signer: EventSigner?): PushResult<String> = call {
        val url = "$baseUrl/api/v1/push/subscriptions"
        val body = JSON.encodeToString(SubscriptionRequest.serializer(), SubscriptionRequest(scope.key, endpoint.url, endpoint.p256dh, endpoint.auth))
        val authorization = if (scope == PushScope.MAILBOX) {
            requireNotNull(signer) { "A mailbox registration is signed by the account" }
            httpAuthorization(signer, url, "POST", body.encodeToByteArray())
        } else {
            null
        }
        val response = client.post(url) {
            contentType(ContentType.Application.Json)
            authorization?.let { header("Authorization", it) }
            setBody(body)
        }
        if (response.status.value in 200..299) {
            PushResult.Done(Json.decodeFromString(SubscriptionAnswer.serializer(), response.bodyAsText()).id)
        } else {
            refused(response.status.value, response.bodyAsText())
        }
    }

    override suspend fun unsubscribe(subscriptionId: String): PushResult<Unit> = call {
        val response = client.delete("$baseUrl/api/v1/push/subscriptions/$subscriptionId")
        // 404: already forgotten by the server, which is what was asked.
        if (response.status.value in 200..299 || response.status.value == 404) PushResult.Done(Unit) else refused(response.status.value, response.bodyAsText())
    }

    private suspend fun <T> call(block: suspend () -> PushResult<T>): PushResult<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        PushResult.Unavailable(e)
    }

    private fun refused(status: Int, body: String): PushResult<Nothing> {
        if (status >= 500 && status != 503) return PushResult.Unavailable(null)
        val error = runCatching { Json.decodeFromString(ErrorAnswer.serializer(), body).error }.getOrNull()
        return PushResult.Refused(status, error)
    }

    @Serializable
    private data class VapidKey(val publicKey: String)

    @Serializable
    private data class SubscriptionRequest(val scope: String, val endpoint: String, val p256dh: String, val auth: String)

    @Serializable
    private data class SubscriptionAnswer(val id: String)

    @Serializable
    private data class ErrorAnswer(val error: String? = null)

    companion object {
        /** NIP-98 HTTP authorization event. */
        const val HTTP_AUTH_KIND: Int = 27235

        private val JSON = Json { encodeDefaults = true }

        /** The `Authorization: Nostr <base64>` value proving that [signer] makes this exact request. */
        suspend fun httpAuthorization(signer: EventSigner, url: String, method: String, body: ByteArray): String {
            val event = signer.sign(
                kind = HTTP_AUTH_KIND,
                tags = listOf(
                    listOf("u", url),
                    listOf("method", method),
                    listOf("payload", Crypto.sha256(body).toHexString()),
                ),
            )
            return "Nostr " + Base64.encode(event.toJson().encodeToByteArray())
        }
    }
}
