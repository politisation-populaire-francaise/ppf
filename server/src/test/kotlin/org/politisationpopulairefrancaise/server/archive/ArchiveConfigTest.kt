package org.politisationpopulairefrancaise.server.archive

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIOEngineConfig
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Settings read from the environment, the HTTPS proxy, and the outbound client's timeouts and attempts. */
class ArchiveConfigTest {
    private val noProperty: (String) -> String? = { null }

    private fun config(vararg env: Pair<String, String>, property: (String) -> String? = noProperty): ArchiveConfig =
        ArchiveConfig.fromEnvironment(mapOf(*env)::get, property)

    @Test
    fun offOutsideProductionByDefaultWithTheServicesOfTheOfficialClient() {
        val defaults = config()
        assertFalse(defaults.enabled)
        assertEquals(ArchiveConfig(), defaults)
        assertEquals(
            listOf("https://a.pool.opentimestamps.org", "https://b.pool.opentimestamps.org", "https://a.pool.eternitywall.com", "https://ots.btc.catallaxy.com"),
            defaults.aggregators,
        )
        assertEquals(listOf("*.calendar.opentimestamps.org", "*.calendar.eternitywall.com", "*.calendar.catallaxy.com"), defaults.calendars.patterns)
        assertEquals(listOf("https://mempool.space/api", "https://blockstream.info/api"), defaults.explorers)
        assertEquals("https://freetsa.org/tsr", defaults.tsaUrl)
        assertEquals(60.minutes, defaults.batchInterval)
        assertEquals(60.minutes, defaults.upgradeInterval)
        assertEquals(30.seconds, defaults.requestTimeout)

        assertTrue(config("PPF_ENVIRONMENT" to "production").enabled)
        assertFalse(config("PPF_ENVIRONMENT" to "production", "PPF_ARCHIVE_ENABLED" to "false").enabled)
        assertFalse(config("PPF_ENVIRONMENT" to "preproduction").enabled)
        assertTrue(config("PPF_ENVIRONMENT" to "preproduction", "PPF_ARCHIVE_ENABLED" to " TRUE ").enabled)
        assertTrue(config("PPF_ARCHIVE_ENABLED" to "oui").enabled)
    }

    @Test
    fun everyServiceIsSetByEnvironment() {
        val custom = config(
            "PPF_ARCHIVE_AGGREGATORS" to "https://faux-1.preprod.example/, https://faux-2.preprod.example",
            "PPF_ARCHIVE_CALENDARS" to "*.calendrier.preprod.example",
            "PPF_ARCHIVE_EXPLORERS" to "http://localhost:3002/api,http://127.0.0.1:3003/api",
            "PPF_ARCHIVE_TSA_URL" to "off",
            "PPF_ARCHIVE_BATCH_MINUTES" to "15",
            "PPF_ARCHIVE_UPGRADE_MINUTES" to "2",
            "PPF_ARCHIVE_TIMEOUT_SECONDS" to "10",
        )
        assertEquals(listOf("https://faux-1.preprod.example", "https://faux-2.preprod.example"), custom.aggregators)
        assertTrue(custom.calendars.allows("https://a.calendrier.preprod.example"))
        assertFalse(custom.calendars.allows("https://alice.btc.calendar.opentimestamps.org"))
        assertEquals(listOf("http://localhost:3002/api", "http://127.0.0.1:3003/api"), custom.explorers)
        assertNull(custom.tsaUrl)
        assertEquals(15.minutes, custom.batchInterval)
        assertEquals(60.minutes, custom.upgradeInterval, "below 5 minutes, the default")
        assertEquals(10.seconds, custom.requestTimeout)
        assertEquals(30.seconds, config("PPF_ARCHIVE_TIMEOUT_SECONDS" to "600").requestTimeout)
    }

    @Test
    fun anInvalidAddressStopsTheServerWithoutBeingRepeated() {
        val refused = listOf(
            "PPF_ARCHIVE_AGGREGATORS" to "http://aggregateur.evil.example,https://b.pool.opentimestamps.org",
            "PPF_ARCHIVE_AGGREGATORS" to "https://user:motdepasse-secret@a.example,https://b.example",
            "PPF_ARCHIVE_AGGREGATORS" to "https://seul.example",
            "PPF_ARCHIVE_AGGREGATORS" to "https://double.example,https://double.example",
            "PPF_ARCHIVE_EXPLORERS" to "https://meme.example/api,https://meme.example/autre",
            "PPF_ARCHIVE_EXPLORERS" to "https://a.example,https://b.example,https://c.example",
            "PPF_ARCHIVE_TSA_URL" to "https://tsa.example/tsr?jeton=secret",
            "PPF_ARCHIVE_CALENDARS" to "*",
        )
        for ((name, value) in refused) {
            val error = assertFailsWith<IllegalArgumentException>(value) { config(name to value) }
            assertFalse(error.message.orEmpty().contains("secret") || error.message.orEmpty().contains("evil"), error.message)
        }
    }

    @Test
    fun theHttpsProxyComesFromTheJvmPropertiesOrHttpsProxy() {
        val on = arrayOf("PPF_ARCHIVE_ENABLED" to "true")
        val properties = mapOf("https.proxyHost" to "mandataire.interne", "https.proxyPort" to "3128")
        assertEquals(HttpsProxy("mandataire.interne", 3128), config(*on, property = properties::get).proxy)
        assertEquals(HttpsProxy("mandataire.interne", 443), config(*on, property = mapOf("https.proxyHost" to "mandataire.interne")::get).proxy)
        assertEquals(HttpsProxy("mandataire.interne", 8080), config(*on, "HTTPS_PROXY" to "http://mandataire.interne:8080").proxy)
        assertEquals(HttpsProxy("mandataire.interne", 8080), config(*on, "https_proxy" to "mandataire.interne:8080").proxy)
        assertEquals(HttpsProxy("mandataire.interne", 80), config(*on, "HTTPS_PROXY" to "http://mandataire.interne/").proxy)
        // The JVM properties win, as for the JDK's own clients.
        assertEquals(HttpsProxy("mandataire.interne", 3128), config(*on, "HTTPS_PROXY" to "http://autre.interne:1", property = properties::get).proxy)
        assertNull(config(*on).proxy)

        for (value in listOf("http://nom:motdepasse-secret@mandataire.interne:3128", "https://mandataire.interne:3128", "http://mandataire.interne:3128/chemin")) {
            val error = assertFailsWith<IllegalArgumentException>(value) { config(*on, "HTTPS_PROXY" to value) }
            assertFalse(error.message.orEmpty().contains("secret"), error.message)
        }
        assertFailsWith<IllegalArgumentException> { config(*on, property = mapOf("https.proxyHost" to "x.interne", "https.proxyPort" to "port")::get) }
        // Off, the proxy is not even read: a stray variable never stops a server that contacts nobody.
        assertNull(config("HTTPS_PROXY" to "https://mandataire.interne").proxy)
    }

    @Test
    fun theClientOfTheArchiveGoesThroughTheProxy() {
        val config = ArchiveConfig(enabled = true, proxy = HttpsProxy("mandataire.interne", 3128))
        ArchiveHttp.defaultClient(config).use { client ->
            val engine = client.engine.config as CIOEngineConfig
            val proxy = engine.proxy!!
            assertEquals(Proxy.Type.HTTP, proxy.type())
            assertEquals(InetSocketAddress.createUnresolved("mandataire.interne", 3128), proxy.address())
            assertTrue(engine.requestTimeout > config.requestTimeout.inWholeMilliseconds)
        }
        ArchiveHttp.defaultClient(ArchiveConfig()).use { assertNull((it.engine.config as CIOEngineConfig).proxy) }
    }

    // ------------------------------------------------------------ attempts and timeouts

    private val pauses = ArrayList<Duration>()
    private var calls = 0

    private fun http(timeout: Duration = 1.seconds, answer: suspend io.ktor.client.engine.mock.MockRequestHandleScope.() -> io.ktor.client.request.HttpResponseData): ArchiveHttp =
        ArchiveHttp(
            HttpClient(
                MockEngine {
                    calls++
                    answer()
                },
            ) {
                expectSuccess = false
                followRedirects = false
            },
            timeout,
            pause = { pauses += it },
        )

    @Test
    fun aFailureThatMayPassIsTriedThreeTimesAtMostWithABackoff() = runBlocking {
        val answer = http { respond(ByteArray(0), HttpStatusCode.ServiceUnavailable) }.get("https://a.example/x", "*/*", 100)
        assertEquals(HttpAnswer.Status(503), answer)
        assertEquals(3, calls)
        assertEquals(listOf(2.seconds, 4.seconds), pauses)
    }

    @Test
    fun aFinalAnswerIsNeverTriedAgain() = runBlocking {
        assertEquals(HttpAnswer.Status(404), http { respond(ByteArray(0), HttpStatusCode.NotFound) }.get("https://a.example/x", "*/*", 100))
        assertEquals(1, calls)
        // A redirection is an answer like any other: never followed.
        calls = 0
        val redirected = http { respond(ByteArray(0), HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://ailleurs.example/")) }
        assertEquals(HttpAnswer.Status(302), redirected.get("https://a.example/x", "*/*", 100))
        assertEquals(1, calls)
        assertTrue(pauses.isEmpty())
    }

    @Test
    fun aBodyTooLargeIsNotRead() = runBlocking {
        assertEquals(HttpAnswer.TooLarge, http { respond(ByteArray(101)) }.post("https://a.example/x", ByteArray(32), ContentType.Application.OctetStream, "*/*", 100))
        assertEquals(1, calls)
        val ok = assertIs<HttpAnswer.Ok>(http { respond(ByteArray(100) { 1 }) }.get("https://a.example/x", "*/*", 100))
        assertEquals(100, ok.body.size)
    }

    @Test
    fun everyRequestHasATimeout() = runBlocking {
        val slow = http(timeout = 50.milliseconds) {
            delay(10.seconds)
            respond(ByteArray(0))
        }
        assertEquals(HttpAnswer.Failed("timeout"), slow.get("https://a.example/x", "*/*", 100))
        assertEquals(3, calls)
    }
}
