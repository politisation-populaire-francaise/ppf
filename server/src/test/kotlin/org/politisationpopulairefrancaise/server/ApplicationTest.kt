package org.politisationpopulairefrancaise.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.AppInfo
import org.politisationpopulairefrancaise.core.HealthStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ApplicationTest {

    @Test
    fun healthCheckAnswersOk() = testApplication {
        application { module() }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        val health = Json.decodeFromString<HealthStatus>(response.bodyAsText())
        assertEquals(HealthStatus.OK, health.status)
        assertEquals(AppInfo.VERSION, health.version)
    }

    @Test
    fun homePageIsFrenchHtmlWithoutThirdParties() = testApplication {
        application { module() }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("<html lang=\"fr\""), "page must declare French")
        assertTrue(body.contains("Politisation Populaire Française"))
        assertFalse(body.contains("googleapis"), "no Google resource")
        assertFalse(body.contains("<script"), "no script in the skeleton page")
        // Nothing is loaded from another server; the only absolute address is the preview image of a
        // shared link, on our own domain (D-185).
        val external = Regex("""https?://[^"]+""").findAll(body).map { it.value }.filterNot { it.startsWith("https://politisation-populaire-francaise.org/") }.toList()
        assertEquals(emptyList(), external, "no external URL")
    }

    @Test
    fun securityHeadersArePresent() = testApplication {
        application { module() }
        val response = client.get("/")
        val csp = response.headers["Content-Security-Policy"]
        assertNotNull(csp)
        assertTrue(csp.contains("default-src 'self'"))
        assertFalse(csp.contains("unsafe-inline"))
        assertEquals("nosniff", response.headers["X-Content-Type-Options"])
        assertEquals("DENY", response.headers["X-Frame-Options"])
        assertEquals("no-referrer", response.headers["Referrer-Policy"])
    }

    @Test
    fun staticStylesheetIsServed() = testApplication {
        application { module() }
        val response = client.get("/static/site.css")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("prefers-color-scheme"))
    }
}
