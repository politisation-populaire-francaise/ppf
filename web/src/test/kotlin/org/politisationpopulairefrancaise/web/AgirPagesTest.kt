package org.politisationpopulairefrancaise.web

import kotlinx.html.html
import kotlinx.html.stream.createHTML
import org.politisationpopulairefrancaise.core.agir.calls.Call
import org.politisationpopulairefrancaise.core.agir.calls.CallType
import org.politisationpopulairefrancaise.core.agir.organisations.Organisation
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The public pages of the calls and of the directory: readable without script, escaped, web links only. */
class AgirPagesTest {
    private val key = "a".repeat(64)

    private fun event(tags: List<List<String>>, content: String = "") =
        SignedEvent(id = "b".repeat(64), pubkey = key, createdAt = 1, kind = Call.KIND, tags = tags, content = content, sig = "c".repeat(128))

    private fun blockade(): Call = requireNotNull(
        Call.from(
            event(
                listOf(
                    listOf("d", "blocage-exemple"),
                    listOf("ppf-type", "appel"),
                    listOf("title", "[EXEMPLE] Blocage <b>filtrant</b> du rond-point"),
                    listOf("ppf-call-type", "bloquer"),
                    listOf("ppf-territory", "Lyon"),
                    listOf("start", "1795266000"),
                    listOf("location", "[EXEMPLE] Rond-point des Lilas"),
                    listOf("ppf-organizer", "[EXEMPLE] Collectif des Lilas"),
                    listOf("r", "javascript:alert(1)"),
                ),
                content = "[EXEMPLE] Première ligne\nDeuxième ligne",
            ),
        ),
    )

    @Test
    fun theListLinksEachCallToItsPosterAddressAndKeepsTheFilters() {
        val call = blockade()
        val html = createHTML().html { callsDocument(CallType.BLOQUER, "Lyon & co", listOf(call)) }
        assertTrue(html.contains("1 appel ouvert"))
        assertTrue(html.contains("href=\"/agir/appels/$key/blocage-exemple\""), html)
        assertTrue(html.contains("Blocage &lt;b&gt;filtrant&lt;/b&gt;"))
        assertTrue(html.contains("[EXEMPLE] Blocage"), "an example stays marked on the site")
        assertTrue(html.contains("<option value=\"bloquer\" selected"))
        assertTrue(html.contains("value=\"Lyon &amp; co\""))
        assertTrue(html.contains("samedi 21 novembre 2026 à 14 h 00"), html)
        assertFalse(html.contains("<script"))
    }

    @Test
    fun aCallToBlockSaysWhatTheLawSaysAndNeverWritesAScriptLink() {
        val html = createHTML().html { callDocument(blockade()) }
        assertTrue(html.contains("Bloquer : ce que dit la loi"))
        assertTrue(html.contains("<p>[EXEMPLE] Première ligne</p>"))
        assertTrue(html.contains("[EXEMPLE] Collectif des Lilas"))
        assertFalse(html.contains("javascript:"))
    }

    @Test
    fun theDirectoryShowsTheContactsOfEachOrganisation() {
        val entry = requireNotNull(
            Organisation.parse(
                event(
                    listOf(
                        listOf("d", "legal-team-exemple"),
                        listOf("ppf-type", "organisation"),
                        listOf("title", "[EXEMPLE] Legal team de Lyon"),
                        listOf("summary", "[EXEMPLE] Assistance juridique en manifestation."),
                        listOf("ppf-org-type", "legal-team"),
                        listOf("ppf-territory", "Lyon"),
                        listOf("ppf-verified", "2026-11-10"),
                        listOf("published_at", "1794394800"),
                        listOf("ppf-contact-phone", "+33 6 00 00 00 00"),
                        listOf("r", "https://exemple.invalid/legal"),
                        listOf("ppf-origin", "ai-assisted"),
                    ),
                ),
            ),
        )
        val html = createHTML().html { organisationsDocument("legal-team", null, listOf(entry)) }
        assertTrue(html.contains("1 organisation"))
        assertTrue(html.contains("Legal team de Lyon"))
        assertTrue(html.contains("10 novembre 2026"), html)
        assertTrue(html.contains("href=\"tel:"))
        assertTrue(html.contains("Rédigé par l’IA"))
        assertTrue(html.contains("href=\"https://exemple.invalid/legal\" rel=\"noopener noreferrer nofollow external\""))
        assertEquals(1, Regex("<option value=\"legal-team\" selected").findAll(html).count())
    }
}
