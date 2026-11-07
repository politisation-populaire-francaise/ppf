package org.politisationpopulairefrancaise.core.urne

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.signing.EventSigner

/** What the ballot-box API answered; [Failure.code] is the server's fixed error code, or null without network. */
sealed interface UrneAnswer<out T> {
    data class Ok<T>(val value: T) : UrneAnswer<T>

    data class Failure(val status: Int?, val code: String?) : UrneAnswer<Nothing>
}

/** The ballot-box API of the server, as the device sees it (`docs/FORMATS.md`, section 4 nonies). */
interface UrneRemote {
    suspend fun list(after: Long, limit: Int = 100): UrneAnswer<UrneList>

    suspend fun register(signer: EventSigner, difficulty: Int): UrneAnswer<VoterStatus>

    suspend fun status(signer: EventSigner): UrneAnswer<VoterStatus>

    suspend fun tokens(signer: EventSigner, request: TokenRequest, difficulty: Int): UrneAnswer<TokenResponse>

    /** Sends an anonymous ballot: never with the account, never on the authenticated connection. */
    suspend fun ballot(ballot: Ballot): UrneAnswer<BallotReceipt>

    suspend fun journal(number: Long): UrneAnswer<UrneJournal>

    suspend fun closing(number: Long): UrneAnswer<UrneClosing>
}

/**
 * [UrneRemote] over HTTP. Two clients (D-110, R4): [authenticated] for what is done with the
 * account (registration, status, tokens: NIP-98 headers), and [anonymous] for the ballots only, a
 * separate instance without cookies, closing its connection after each request and naming itself
 * with a generic agent: a ballot never travels on a connection that carried the account.
 */
class HttpUrneRemote(
    private val authenticated: HttpClient,
    private val anonymous: HttpClient,
    /** Origin of the server, without trailing slash: also the start of the NIP-98 `u` tag. */
    private val baseUrl: String,
    private val now: () -> Long,
) : UrneRemote {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun list(after: Long, limit: Int): UrneAnswer<UrneList> = call(UrneList.serializer()) {
        authenticated.get("$baseUrl/api/v1/urnes") {
            parameter("after", after)
            parameter("limit", limit)
        }
    }

    override suspend fun register(signer: EventSigner, difficulty: Int): UrneAnswer<VoterStatus> = call(VoterStatus.serializer()) {
        val path = "/api/v1/urnes/register"
        authenticated.post("$baseUrl$path") {
            header(HttpHeaders.Authorization, Nip98.authorization(signer, "$baseUrl$path", "POST", null, now(), difficulty))
        }
    }

    override suspend fun status(signer: EventSigner): UrneAnswer<VoterStatus> = call(VoterStatus.serializer()) {
        val path = "/api/v1/urnes/me"
        authenticated.get("$baseUrl$path") {
            header(HttpHeaders.Authorization, Nip98.authorization(signer, "$baseUrl$path", "GET", null, now()))
        }
    }

    override suspend fun tokens(signer: EventSigner, request: TokenRequest, difficulty: Int): UrneAnswer<TokenResponse> = call(TokenResponse.serializer()) {
        val path = "/api/v1/urnes/tokens"
        val body = json.encodeToString(TokenRequest.serializer(), request)
        authenticated.post("$baseUrl$path") {
            header(HttpHeaders.Authorization, Nip98.authorization(signer, "$baseUrl$path", "POST", body.encodeToByteArray(), now(), difficulty))
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    override suspend fun ballot(ballot: Ballot): UrneAnswer<BallotReceipt> = call(BallotReceipt.serializer()) {
        anonymous.post("$baseUrl/api/v1/ballots") {
            header(HttpHeaders.Connection, "close")
            header(HttpHeaders.UserAgent, GENERIC_AGENT)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(Ballot.serializer(), ballot))
        }
    }

    override suspend fun journal(number: Long): UrneAnswer<UrneJournal> = call(UrneJournal.serializer()) {
        authenticated.get("$baseUrl/api/v1/urnes/$number/journal")
    }

    override suspend fun closing(number: Long): UrneAnswer<UrneClosing> = call(UrneClosing.serializer()) {
        authenticated.get("$baseUrl/api/v1/urnes/$number/closing")
    }

    private suspend fun <T> call(serializer: kotlinx.serialization.KSerializer<T>, request: suspend () -> HttpResponse): UrneAnswer<T> {
        val response = try {
            request()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return UrneAnswer.Failure(null, null)
        }
        val text = runCatching { response.bodyAsText() }.getOrNull()
        if (response.status.value !in 200..299) {
            val code = text?.let { runCatching { json.decodeFromString(ErrorBody.serializer(), it).error }.getOrNull() }
            return UrneAnswer.Failure(response.status.value, code)
        }
        val value = text?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() } ?: return UrneAnswer.Failure(response.status.value, "malformed_answer")
        return UrneAnswer.Ok(value)
    }

    @kotlinx.serialization.Serializable
    private data class ErrorBody(val error: String)

    companion object {
        /** The same agent for every device and version: a ballot says nothing about who sent it. */
        const val GENERIC_AGENT: String = "PPF"
    }
}
