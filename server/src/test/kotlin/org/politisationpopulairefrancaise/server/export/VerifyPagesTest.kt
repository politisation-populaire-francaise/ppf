package org.politisationpopulairefrancaise.server.export

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.http.parameters
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentCheck
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.voir.VoirTags
import org.politisationpopulairefrancaise.server.BundledContents
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.testAgir
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.module
import org.politisationpopulairefrancaise.web.PASTED_FIELD
import org.politisationpopulairefrancaise.server.testRevendications
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** « Vérifier un contenu PPF » on the site (S-141), against the contents of the editorial keys in PostgreSQL. */
class VerifyPagesTest {
    private val events = EventRepository(TestDatabase.dataSource)
    private val team = LocalEventSigner(IdentityKeys.generate())
    private val stranger = LocalEventSigner(IdentityKeys.generate())
    private val policy = PublicationPolicy(setOf(EditorialTrust.EXAMPLE_EDITOR, team.publicKey.hex))

    private fun fact(signer: LocalEventSigner, title: String, updatedAt: Long): SignedEvent = runBlocking {
        signer.sign(
            Article.build(
                signer.publicKey, "exemple-verifie", title, "[EXEMPLE] Résumé.", emptyList(), ArticleType.FAIT, ContentOrigin.HUMAN, 1_793_000_000, updatedAt, "[EXEMPLE] Texte.",
                extraTags = listOf(listOf(VoirTags.FACT, "mobilisation"), listOf(VoirTags.DATE, "2026-10-02"), listOf(VoirTags.FACT_STATUS, "etabli")),
            ),
        )
    }

    private lateinit var old: SignedEvent
    private lateinit var current: SignedEvent
    private lateinit var strangers: SignedEvent

    @BeforeTest
    fun storeTheContents() {
        TestDatabase.reset()
        BundledContents.load(BundledContents.VOIR_FILE).forEach(events::store)
        old = fact(team, "[EXEMPLE] Première version", 1_794_000_000)
        current = fact(team, "[EXEMPLE] Version corrigée", 1_794_100_000)
        strangers = fact(stranger, "[EXEMPLE] Faux fait de PPF", 1_794_100_000)
        // The server keeps only the latest version of an address: the old one is not stored.
        listOf(current, strangers).forEach(events::store)
    }

    private suspend fun HttpClient.page(path: String): String {
        val response = get(path)
        assertEquals(HttpStatusCode.OK, response.status, path)
        val html = response.bodyAsText()
        assertFalse(html.contains("<script"), path)
        assertFalse(html.contains("style=\""), path)
        return html
    }

    private suspend fun HttpClient.paste(text: String): HttpResponse = submitForm("/verifier", parameters { append(PASTED_FIELD, text) })

    @Test
    fun aCodeAnIdentifierOrTheShortAddressFindTheCurrentVersion() = testApplication {
        application { module(TestDatabase.dataSource, policy, TEST_RELAY, testRevendications(), testAgir()) }
        val form = client.page("/verifier")
        assertTrue(form.contains("Code de vérification, identifiant ou lien"))
        assertFalse(form.contains("class=\"verdict"), "no verdict before a question")
        assertTrue(client.page("/").contains("href=\"/verifier\""), "linked from the footer of every page")

        val code = ContentCheck.codeOf(current)
        listOf("/verifier?q=$code", "/verifier?q=${code.chunked(4).joinToString("%20")}", "/verifier?q=${current.id}", "/v/$code").forEach { path ->
            val html = client.page(path)
            assertTrue(html.contains("class=\"verdict verdict-ok\""), path)
            assertTrue(html.contains("Authentique"), path)
            assertTrue(html.contains("[EXEMPLE] Version corrigée"), path)
            assertTrue(html.contains(current.id), "$path: the fingerprint")
            assertTrue(html.contains("href=\"/voir/faits/exemple-verifie\""), "$path: the page of the content")
        }
        assertTrue(client.page("/verifier?q=${"0".repeat(16)}").contains("Inconnu de PPF"))
        assertTrue(client.page("/verifier?q=bonjour").contains("Rien à vérifier"))
    }

    @Test
    fun aPastedContentIsAuthenticOutdatedAlteredOrNotFromPpfAndNeverWrittenBack() = testApplication {
        application { module(TestDatabase.dataSource, policy, TEST_RELAY, testRevendications(), testAgir()) }
        val authentic = client.paste(current.toJson()).bodyAsText()
        assertTrue(authentic.contains("verdict-ok") && authentic.contains("Authentique"), authentic)
        assertFalse(authentic.contains(current.sig), "the pasted content is not written back into the page")

        val outdated = client.paste(old.toJson()).bodyAsText()
        assertTrue(outdated.contains("Authentique, mais ancien"), outdated)
        assertTrue(outdated.contains("Version actuelle") && outdated.contains(current.id), "the current version is shown")

        val altered = client.paste(current.copy(content = "[EXEMPLE] Texte modifié.").toJson()).bodyAsText()
        assertTrue(altered.contains("Modifié ou abîmé"), altered)
        assertTrue(client.paste(strangers.toJson()).bodyAsText().contains("Pas un contenu de PPF"))
        assertTrue(client.paste("{ pas du JSON").bodyAsText().contains("Rien à vérifier"))
    }

    @Test
    fun aLinkElsewhereIsNeverPpfAndATooLongTextIsRefused() = testApplication {
        application { module(TestDatabase.dataSource, policy, TEST_RELAY, testRevendications(), testAgir()) }
        val code = ContentCheck.codeOf(current)
        val trap = client.page("/verifier?q=" + "https://politisation-populaire-francaise.org.piege.example/v/$code".encodeURLParameter())
        assertTrue(trap.contains("Lien qui n’est pas de PPF") && trap.contains("politisation-populaire-francaise.org.piege.example"), trap)
        assertFalse(trap.contains("[EXEMPLE] Version corrigée"), "a link elsewhere is never followed")
        assertTrue(client.page("/verifier?q=" + "https://politisation-populaire-francaise.org/voir".encodeURLParameter()).contains("Lien officiel"))

        val tooLong = client.post("/verifier") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody("contenu=" + "a".repeat(ContentCheck.MAX_INPUT_CHARS * 9 + 10))
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, tooLong.status)
        assertTrue(tooLong.bodyAsText().contains("trop long"))
    }
}
