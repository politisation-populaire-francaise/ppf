package org.politisationpopulairefrancaise.server.revendications

import java.io.File
import java.net.URI
import java.net.URISyntaxException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * How to reach the AI model that reads proposed demands. Its hosting is still to be decided by the
 * owner (a model on a rented server or a European pay-per-use service); both expose the
 * OpenAI-compatible chat completions API, which [OpenAiCompatibleReviewModel] speaks. Read from the
 * environment only; without PPF_AI_BASE_URL there is no model and the AI check says so.
 *
 * - PPF_AI_BASE_URL: the base of the API, the part before `/chat/completions`, for example
 *   `https://ia.example/v1`. Must be https, except `http://localhost` or `http://127.0.0.1` for a
 *   model running on the same machine. No user name, password, query or fragment in it.
 * - PPF_AI_MODEL: the name of the model, as the service knows it (required with the base URL).
 * - PPF_AI_API_KEY_FILE: path of a file holding only the API key, if the service needs one (the
 *   form used by Docker secrets in infra/). The key itself is never the value of an environment
 *   variable nor in the repository: PPF_AI_API_KEY is refused.
 * - PPF_AI_JSON_MODE: "true" to ask for `response_format: {"type": "json_object"}`, which not
 *   every server supports (default false: the answer is read either way).
 * - PPF_AI_TIMEOUT_SECONDS: how long to wait for an answer (default 30, at most 600).
 */
class AiReviewConfig(
    baseUrl: String,
    val model: String,
    /** Sent as `Authorization: Bearer`; never logged nor printed (see [toString]). */
    val apiKey: String? = null,
    val jsonMode: Boolean = false,
    val timeout: Duration = DEFAULT_TIMEOUT,
) {
    /** The base URL, checked, without trailing slash. */
    val baseUrl: String = checkBaseUrl(baseUrl)

    val chatCompletionsUrl: String get() = "$baseUrl/chat/completions"

    init {
        require(model.isNotBlank() && model.length <= MAX_MODEL_LENGTH && model.none { it.isISOControl() }) {
            "$MODEL_VARIABLE must be a model name of 1 to $MAX_MODEL_LENGTH characters"
        }
        require(apiKey == null || (apiKey.isNotEmpty() && apiKey.all { it in '!'..'~' })) {
            "The API key must be printable ASCII without spaces"
        }
        require(timeout.isPositive() && timeout <= MAX_TIMEOUT) { "The timeout must be between 1 second and $MAX_TIMEOUT" }
    }

    override fun toString(): String =
        "AiReviewConfig(baseUrl=$baseUrl, model=$model, apiKey=${if (apiKey == null) "none" else "set"}, jsonMode=$jsonMode, timeout=$timeout)"

    companion object {
        const val BASE_URL_VARIABLE: String = "PPF_AI_BASE_URL"
        const val MODEL_VARIABLE: String = "PPF_AI_MODEL"
        const val API_KEY_FILE_VARIABLE: String = "PPF_AI_API_KEY_FILE"
        const val JSON_MODE_VARIABLE: String = "PPF_AI_JSON_MODE"
        const val TIMEOUT_VARIABLE: String = "PPF_AI_TIMEOUT_SECONDS"

        /** Refused on purpose: a key in an environment variable leaks through process listings and crash reports. */
        const val FORBIDDEN_KEY_VARIABLE: String = "PPF_AI_API_KEY"

        val DEFAULT_TIMEOUT: Duration = 30.seconds
        val MAX_TIMEOUT: Duration = 600.seconds
        private const val MAX_MODEL_LENGTH = 200
        private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1")

        /** Null when PPF_AI_BASE_URL is not set; throws [IllegalArgumentException] on an invalid configuration. */
        fun fromEnvironment(env: (String) -> String? = System::getenv): AiReviewConfig? {
            require(env(FORBIDDEN_KEY_VARIABLE) == null) {
                "$FORBIDDEN_KEY_VARIABLE is not read: put the key in a file and give its path in $API_KEY_FILE_VARIABLE"
            }
            val baseUrl = env(BASE_URL_VARIABLE)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val model = env(MODEL_VARIABLE)?.trim().orEmpty()
            require(model.isNotEmpty()) { "$MODEL_VARIABLE is required when $BASE_URL_VARIABLE is set" }
            val apiKey = env(API_KEY_FILE_VARIABLE)?.trim()?.takeIf { it.isNotEmpty() }?.let { path ->
                val file = File(path)
                require(file.isFile && file.canRead()) { "$API_KEY_FILE_VARIABLE does not name a readable file" }
                file.readText().trim().also { require(it.isNotEmpty()) { "The file named by $API_KEY_FILE_VARIABLE is empty" } }
            }
            val jsonMode = env(JSON_MODE_VARIABLE)?.trim()?.lowercase() in setOf("true", "1", "yes", "oui")
            val timeout = env(TIMEOUT_VARIABLE)?.trim()?.toIntOrNull()?.takeIf { it in 1..MAX_TIMEOUT.inWholeSeconds.toInt() }?.seconds ?: DEFAULT_TIMEOUT
            return AiReviewConfig(baseUrl, model, apiKey, jsonMode, timeout)
        }

        /**
         * https everywhere; plain http only towards a model on the same machine. The messages never
         * repeat the URL: a mistaken one may hold a password.
         */
        fun checkBaseUrl(value: String): String {
            val uri = try {
                URI(value.trim())
            } catch (e: URISyntaxException) {
                throw IllegalArgumentException("$BASE_URL_VARIABLE is not a valid URL")
            }
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase()
            require(host != null && host.isNotEmpty()) { "$BASE_URL_VARIABLE must name a host" }
            require(uri.rawUserInfo == null) { "$BASE_URL_VARIABLE must not contain a user name or a password" }
            require(uri.rawQuery == null && uri.rawFragment == null) { "$BASE_URL_VARIABLE must not contain a query or a fragment" }
            require(scheme == "https" || (scheme == "http" && host in LOCAL_HOSTS)) {
                "$BASE_URL_VARIABLE must use https (http is accepted only for localhost or 127.0.0.1)"
            }
            return uri.toString().trimEnd('/')
        }
    }
}
