package org.politisationpopulairefrancaise.server.revendications

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.politisationpopulairefrancaise.core.revendications.checks.AiMessage
import org.politisationpopulairefrancaise.core.revendications.checks.AiReview
import org.politisationpopulairefrancaise.core.revendications.checks.ProposalReviewModel
import org.politisationpopulairefrancaise.core.revendications.checks.ReviewOutcome
import org.politisationpopulairefrancaise.core.revendications.checks.ReviewRequest

/**
 * The AI model reached through the OpenAI-compatible chat completions API (`POST
 * <base>/chat/completions`), which both hosting options under study speak: a model on a rented
 * server (vLLM, llama.cpp, Ollama...) or a European pay-per-use service. The prompt and the strict
 * reading of the answer live in the shared core ([AiReview]); this class only carries them.
 *
 * What leaves the server: the texts of the proposal and of the closest existing demands, nothing
 * else. The addresses of the existing demands (which hold their authors' public keys) are replaced
 * by short aliases ("r1", "r2"...) and put back when the answer is read; no public key, no address,
 * no header of the citizen's request is ever sent. The deterministic rules have already refused
 * e-mail addresses, phone, bank and social security numbers before a proposal is queued.
 *
 * Every problem (timeout, unreachable server, HTTP error, unreadable answer) gives
 * [ReviewOutcome.Failed] with a short code, never an exception: the team then checks by hand.
 */
class OpenAiCompatibleReviewModel(
    private val config: AiReviewConfig,
    private val client: HttpClient,
    private val ownsClient: Boolean = false,
) : ProposalReviewModel, AutoCloseable {

    override suspend fun review(request: ReviewRequest): ReviewOutcome {
        val aliases = request.candidates.take(AiReview.MAX_CANDIDATES).mapIndexed { index, candidate -> "r${index + 1}" to candidate }
        val sent = request.copy(candidates = aliases.map { (alias, candidate) -> candidate.copy(address = alias) })
        val realAddress = aliases.associate { (alias, candidate) -> alias to candidate.address }
        val body = requestBody(AiReview.messages(sent))

        val answer = try {
            withTimeoutOrNull(config.timeout) { exchange(body) } ?: return ReviewOutcome.Failed("timeout")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ReviewOutcome.Failed("unreachable")
        }
        val text = when (answer) {
            is Exchange.Answer -> answer.text
            is Exchange.HttpError -> return ReviewOutcome.Failed("http_${answer.status}")
            Exchange.TooLarge -> return ReviewOutcome.Failed("answer_too_large")
        }
        val content = messageContent(text) ?: return ReviewOutcome.Failed("unexpected_answer")
        val issues = AiReview.parse(content, sent) ?: return ReviewOutcome.Failed("unreadable_answer")
        val restored = issues.map { issue ->
            issue.copy(related = issue.related.mapNotNull { related -> realAddress[related.address]?.let { related.copy(address = it) } })
        }
        return ReviewOutcome.Reviewed(restored, config.model)
    }

    private sealed interface Exchange {
        data class Answer(val text: String) : Exchange

        data class HttpError(val status: Int) : Exchange

        data object TooLarge : Exchange
    }

    private suspend fun exchange(body: String): Exchange {
        val response = client.post(config.chatCompletionsUrl) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            config.apiKey?.let { bearerAuth(it) }
            setBody(body)
        }
        if (!response.status.isSuccess()) return Exchange.HttpError(response.status.value)
        val bytes = response.bodyAsChannel().readBuffer(MAX_ANSWER_BYTES + 1).readByteArray()
        if (bytes.size > MAX_ANSWER_BYTES) return Exchange.TooLarge
        return Exchange.Answer(bytes.decodeToString())
    }

    private fun requestBody(messages: List<AiMessage>): String = buildJsonObject {
        put("model", config.model)
        put("temperature", 0)
        put("max_tokens", MAX_ANSWER_TOKENS)
        put("stream", false)
        putJsonArray("messages") {
            messages.forEach { message ->
                addJsonObject {
                    put("role", message.role)
                    put("content", message.content)
                }
            }
        }
        if (config.jsonMode) {
            putJsonObject("response_format") { put("type", "json_object") }
        }
    }.toString()

    /** `choices[0].message.content` of a chat completion, or null. */
    private fun messageContent(text: String): String? {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
        val choice = (root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        val message = choice["message"] as? JsonObject ?: return null
        return (message["content"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    }

    override fun close() {
        if (ownsClient) client.close()
    }

    companion object {
        /** An answer is a few kilobytes; a bigger one is not read. */
        const val MAX_ANSWER_BYTES: Long = 256 * 1024

        /** Bounds what an answer may cost: ten short issues fit comfortably. */
        const val MAX_ANSWER_TOKENS: Int = 2_048

        private val json = Json { ignoreUnknownKeys = true }

        /** The connector with its own HTTP client: no redirect followed (the key must not travel elsewhere), no retry. */
        fun create(config: AiReviewConfig): OpenAiCompatibleReviewModel {
            val client = HttpClient(CIO) {
                expectSuccess = false
                followRedirects = false
                engine {
                    // The overall timeout is applied by review(); the engine must not cut earlier.
                    requestTimeout = config.timeout.inWholeMilliseconds + ENGINE_MARGIN_MILLIS
                }
            }
            return OpenAiCompatibleReviewModel(config, client, ownsClient = true)
        }

        private const val ENGINE_MARGIN_MILLIS = 5_000L
    }
}
