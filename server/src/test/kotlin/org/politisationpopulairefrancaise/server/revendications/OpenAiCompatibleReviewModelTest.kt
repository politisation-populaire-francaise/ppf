package org.politisationpopulairefrancaise.server.revendications

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.politisationpopulairefrancaise.core.editorial.RevendicationStatus
import org.politisationpopulairefrancaise.core.revendications.checks.AiReview
import org.politisationpopulairefrancaise.core.revendications.checks.DemandText
import org.politisationpopulairefrancaise.core.revendications.checks.IssueKind
import org.politisationpopulairefrancaise.core.revendications.checks.ReviewOutcome
import org.politisationpopulairefrancaise.core.revendications.checks.ReviewRequest
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class OpenAiCompatibleReviewModelTest {
    private val author = "c".repeat(64)
    private val cantine = DemandText(
        address = "30023:$author:cantines-tarif-social",
        title = "Un tarif de cantine à un euro pour les familles aux revenus modestes dans toutes les communes",
        summary = "Généraliser le tarif de cantine scolaire à un euro.",
        territory = "France",
        status = RevendicationStatus.NOUVELLE,
    )
    private val request = ReviewRequest(DemandFixtures.draft(title = "Cantine à un euro pour les familles modestes", themes = listOf("education")), listOf(cantine))
    private val config = AiReviewConfig("https://ia.example/v1/", model = "modele-de-test", apiKey = "cle-de-test")

    private val requests = mutableListOf<HttpRequestData>()

    private fun model(config: AiReviewConfig = this.config, answer: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): OpenAiCompatibleReviewModel =
        OpenAiCompatibleReviewModel(
            config,
            HttpClient(
                MockEngine { request ->
                    requests += request
                    answer(request)
                },
            ) {
                // As in OpenAiCompatibleReviewModel.create: no redirect followed.
                expectSuccess = false
                followRedirects = false
            },
        )

    /** A chat completion whose message is [content]. */
    private fun completion(content: String): String = buildJsonObject {
        put("id", "chatcmpl-test")
        put("object", "chat.completion")
        put("model", "modele-de-test")
        put(
            "choices",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("index", 0)
                        put("message", buildJsonObject { put("role", "assistant"); put("content", content) })
                        put("finish_reason", "stop")
                    },
                )
            },
        )
    }.toString()

    private fun MockRequestHandleScope.json(text: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(text, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

    private fun sentBody(): JsonObject = Json.parseToJsonElement((requests.single().body as TextContent).text).jsonObject

    @Test
    fun sendsTheTextsOnlyAndReadsTheAnswerBack() = runBlocking {
        val answer = """{"issues":[{"kind":"duplicate","severity":"warning","related":["r1"],"detail":"[EXEMPLE] La même idée existe déjà."}]}"""
        val outcome = model { json(completion(answer)) }.review(request)

        val reviewed = assertIs<ReviewOutcome.Reviewed>(outcome)
        assertEquals("modele-de-test", reviewed.model)
        val duplicate = reviewed.issues.single()
        assertEquals(IssueKind.DUPLICATE, duplicate.kind)
        assertEquals(cantine.address, duplicate.related.single().address, "the alias is turned back into the address")

        val sent = requests.single()
        assertEquals(HttpMethod.Post, sent.method)
        assertEquals("https://ia.example/v1/chat/completions", sent.url.toString())
        assertEquals("Bearer cle-de-test", sent.headers[HttpHeaders.Authorization])
        assertEquals(ContentType.Application.Json, sent.body.contentType?.withoutParameters())
        val body = sentBody()
        assertEquals("modele-de-test", body["model"]!!.jsonPrimitive.content)
        assertEquals(0, body["temperature"]!!.jsonPrimitive.int)
        assertNull(body["response_format"], "json mode is off unless configured")
        val messages = body["messages"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("system", "user"), messages.map { it["role"]!!.jsonPrimitive.content })
        assertEquals(AiReview.SYSTEM_PROMPT, messages[0]["content"]!!.jsonPrimitive.content)
        val user = messages[1]["content"]!!.jsonPrimitive.content
        assertTrue("Cantine à un euro pour les familles modestes" in user)
        assertTrue("\"r1\"" in user, "existing demands are named by an alias")
        val raw = (sent.body as TextContent).text
        assertFalse(author in raw, "no public key leaves the server")
        assertFalse("cantines-tarif-social" in raw, "no address leaves the server")
    }

    @Test
    fun asksForAJsonObjectWhenConfigured() = runBlocking {
        model(AiReviewConfig("https://ia.example/v1", "modele-de-test", jsonMode = true)) { json(completion("""{"issues":[]}""")) }.review(request)
        assertEquals("json_object", sentBody()["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertNull(requests.single().headers[HttpHeaders.Authorization], "no key configured: no Authorization header")
    }

    @Test
    fun everyProblemGivesAFailureNeverAnException() = runBlocking {
        val fast = AiReviewConfig("https://ia.example/v1", "modele-de-test", timeout = 200.milliseconds)
        assertEquals(ReviewOutcome.Failed("timeout"), model(fast) { delay(5_000); json(completion("""{"issues":[]}""")) }.review(request))
        assertEquals(ReviewOutcome.Failed("http_500"), model { json("""{"error":"panne"}""", HttpStatusCode.InternalServerError) }.review(request))
        assertEquals(ReviewOutcome.Failed("http_302"), model { respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://ailleurs.example/")) }.review(request))
        assertEquals(ReviewOutcome.Failed("unreachable"), model { throw IOException("connexion refusée") }.review(request))
        assertEquals(ReviewOutcome.Failed("unexpected_answer"), model { json("pas du JSON") }.review(request))
        assertEquals(ReviewOutcome.Failed("unexpected_answer"), model { json("""{"choices":[]}""") }.review(request))
        assertEquals(ReviewOutcome.Failed("unexpected_answer"), model { json("""{"choices":[{"message":{"content":null}}]}""") }.review(request))
        assertEquals(ReviewOutcome.Failed("unreadable_answer"), model { json(completion("Je ne peux pas répondre.")) }.review(request))
        assertEquals(ReviewOutcome.Failed("unreadable_answer"), model { json(completion("""{"problemes":[]}""")) }.review(request))
        assertEquals(ReviewOutcome.Failed("answer_too_large"), model { json(completion("x".repeat(300_000))) }.review(request))
    }

    @Test
    fun anInventedDemandOrAForbiddenKindIsDropped() = runBlocking {
        val answer = """{"issues":[{"kind":"duplicate","related":["r7"]},{"kind":"too_short"},{"kind":"unclear","severity":"blocking","detail":"[EXEMPLE] Peu clair."}]}"""
        val reviewed = assertIs<ReviewOutcome.Reviewed>(model { json(completion(answer)) }.review(request))
        assertEquals(listOf(IssueKind.UNCLEAR), reviewed.issues.map { it.kind }, "only what the format allows, never blocking")
        assertFalse(reviewed.issues.single().severity.name == "BLOCKING")
    }

    @Test
    fun readsItsConfigurationFromTheEnvironment() {
        val keyFile = Files.createTempFile("ppf-ai-key", ".txt").also { Files.writeString(it, "  cle-de-test\n") }
        try {
            val env = mapOf(
                AiReviewConfig.BASE_URL_VARIABLE to "https://ia.example/v1",
                AiReviewConfig.MODEL_VARIABLE to "modele-de-test",
                AiReviewConfig.API_KEY_FILE_VARIABLE to keyFile.toString(),
                AiReviewConfig.JSON_MODE_VARIABLE to "true",
                AiReviewConfig.TIMEOUT_VARIABLE to "45",
            )
            val config = AiReviewConfig.fromEnvironment(env::get)!!
            assertEquals("cle-de-test", config.apiKey, "read from the file, trimmed")
            assertTrue(config.jsonMode)
            assertEquals(45_000, config.timeout.inWholeMilliseconds)
            assertEquals("https://ia.example/v1/chat/completions", config.chatCompletionsUrl)
            assertFalse("cle-de-test" in config.toString(), "the key is never printed")

            assertNull(AiReviewConfig.fromEnvironment { null }, "no base URL: no model")
            assertNull(RevendicationsConfig.fromEnvironment { null }.ai)
            fun refused(vararg pairs: Pair<String, String>) = assertFailsWith<IllegalArgumentException> { AiReviewConfig.fromEnvironment(mapOf(*pairs)::get) }
            val model = AiReviewConfig.MODEL_VARIABLE to "modele-de-test"
            refused(AiReviewConfig.BASE_URL_VARIABLE to "http://ia.example/v1", model)
            refused(AiReviewConfig.BASE_URL_VARIABLE to "http://localhost.ia.example/v1", model)
            refused(AiReviewConfig.BASE_URL_VARIABLE to "http://127.0.0.1@ia.example/v1", model)
            refused(AiReviewConfig.BASE_URL_VARIABLE to "https://nom:secret@ia.example/v1", model)
            refused(AiReviewConfig.BASE_URL_VARIABLE to "https://ia.example/v1?cle=1", model)
            refused(AiReviewConfig.BASE_URL_VARIABLE to "ftp://ia.example/v1", model)
            refused(AiReviewConfig.BASE_URL_VARIABLE to "https://ia.example/v1")
            refused(AiReviewConfig.BASE_URL_VARIABLE to "https://ia.example/v1", model, AiReviewConfig.API_KEY_FILE_VARIABLE to keyFile.resolveSibling("absent.txt").toString())
            refused(AiReviewConfig.BASE_URL_VARIABLE to "https://ia.example/v1", model, AiReviewConfig.FORBIDDEN_KEY_VARIABLE to "cle-de-test")
            assertEquals("http://localhost:11434/v1", AiReviewConfig.fromEnvironment(mapOf(AiReviewConfig.BASE_URL_VARIABLE to "http://localhost:11434/v1", model)::get)!!.baseUrl)
            assertEquals("http://127.0.0.1:8000/v1", AiReviewConfig.fromEnvironment(mapOf(AiReviewConfig.BASE_URL_VARIABLE to "http://127.0.0.1:8000/v1/", model)::get)!!.baseUrl)
        } finally {
            Files.deleteIfExists(keyFile)
        }
    }
}
