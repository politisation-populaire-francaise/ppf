package org.politisationpopulairefrancaise.server.security

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.server.module
import org.politisationpopulairefrancaise.web.SecurityContact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecurityContactTest {

    // A public key built from fixed bytes (an x-only key is only checked for its length here).
    private val keyHex = PublicKey(ByteArray(32) { (it * 7 + 3).toByte() }).hex
    private val github = "https://github.com/ppf-collectif/ppf/security/advisories/new"

    private fun setup(environment: String, vararg values: Pair<String, String>): SecurityContact =
        SecurityContactSetup.fromEnvironment(environment, 8080) { name -> values.toMap()[name] }

    @Test
    fun developmentFallsBackToLocalhost() {
        val contact = setup("development")
        assertEquals("http://localhost:8080", contact.publicUrl)
        assertNull(contact.nostrKey)
    }

    @Test
    fun keyIsReadAsHexOrNpubAndTheAddressLosesItsSlash() {
        val npub = PublicKey.fromHex(keyHex).npub
        val fromHex = setup("preproduction", "PPF_PUBLIC_URL" to "https://preprod.politisation-populaire-francaise.org/", "PPF_SECURITY_NOSTR_KEY" to keyHex.uppercase())
        val fromNpub = setup("preproduction", "PPF_PUBLIC_URL" to "https://preprod.politisation-populaire-francaise.org", "PPF_SECURITY_NOSTR_KEY" to npub)
        assertEquals("https://preprod.politisation-populaire-francaise.org", fromHex.publicUrl)
        assertEquals(fromHex.nostrKey, fromNpub.nostrKey)
    }

    @Test
    fun wrongValuesStopTheServer() {
        assertFailsWith<IllegalArgumentException> { setup("preproduction") } // no https address
        assertFailsWith<IllegalArgumentException> { setup("preproduction", "PPF_PUBLIC_URL" to "http://preprod.politisation-populaire-francaise.org") }
        assertFailsWith<IllegalArgumentException> {
            setup("preproduction", "PPF_PUBLIC_URL" to "https://preprod.politisation-populaire-francaise.org", "PPF_SECURITY_NOSTR_KEY" to "npub1nonsense")
        }
        assertFailsWith<IllegalArgumentException> {
            setup("preproduction", "PPF_PUBLIC_URL" to "https://preprod.politisation-populaire-francaise.org", "PPF_SECURITY_GITHUB_URL" to "https://example.org/")
        }
    }

    @Test
    fun productionNeedsAPrivateChannel() {
        val url = "PPF_PUBLIC_URL" to "https://politisation-populaire-francaise.org"
        assertFailsWith<IllegalArgumentException> { setup("production", url) }
        assertTrue(setup("production", url, "PPF_SECURITY_NOSTR_KEY" to keyHex).hasPrivateChannel)
        assertTrue(setup("production", url, "PPF_SECURITY_GITHUB_URL" to github).hasPrivateChannel)
    }

    @Test
    fun fileAndPageAreServed() = testApplication {
        val contact = SecurityContact("https://politisation-populaire-francaise.org", PublicKey.fromHex(keyHex), github)
        application { module(securityContact = contact) }
        for (path in listOf("/.well-known/security.txt", "/security.txt")) {
            val response = client.get(path)
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("text/plain; charset=utf-8", response.headers["Content-Type"]?.lowercase())
            val body = response.bodyAsText()
            assertTrue(body.contains("Contact: nostr:${contact.nostrKey!!.npub}\n"))
            assertTrue(body.contains("Canonical: https://politisation-populaire-francaise.org/.well-known/security.txt\n"))
        }
        val page = client.get("/securite")
        assertEquals(HttpStatusCode.OK, page.status)
        val html = page.bodyAsText()
        assertTrue(html.contains("Signaler une faille de sécurité"))
        assertTrue(html.contains("[EXEMPLE]"), "draft until a lawyer has read it")
        assertTrue(html.contains(contact.nostrKey!!.npub))
        assertTrue(html.contains("id=\"remerciements\""))
        assertFalse(html.contains("<script"))
        assertFalse(html.contains("—"), "no em dash")
        // The only external links are the repository report and the official pages of the State.
        val external = Regex("href=\"(https?://[^\"]+)\"").findAll(html).map { it.groupValues[1] }.toSet()
        assertEquals(
            setOf(
                github,
                "https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000033206854",
                "https://cyber.gouv.fr/signaler-une-vulnerabilite-un-incident",
            ),
            external,
        )
    }

    @Test
    fun withoutChannelThePageSaysSo() = testApplication {
        application { module(securityContact = SecurityContact("http://localhost:8080")) }
        val html = client.get("/securite").bodyAsText()
        assertTrue(html.contains("pas encore publié"))
        assertTrue(client.get("/").bodyAsText().contains("href=\"/securite\""), "linked from every page")
    }
}
