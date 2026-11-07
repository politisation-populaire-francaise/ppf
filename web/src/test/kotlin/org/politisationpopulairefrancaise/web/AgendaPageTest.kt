package org.politisationpopulairefrancaise.web

import kotlinx.html.html
import kotlinx.html.stream.createHTML
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The agenda page of the site: readable without script, links only to web addresses. */
class AgendaPageTest {

    private fun event(tags: List<List<String>>, content: String = "") =
        SignedEvent(id = "b".repeat(64), pubkey = "a".repeat(64), createdAt = 1, kind = AgirFormats.KIND_MOBILIZATION, tags = tags, content = content, sig = "c".repeat(128))

    private fun render(query: AgendaQuery = AgendaQuery(), events: List<SignedEvent> = emptyList()): String =
        createHTML().html { agendaDocument(query, events) }

    @Test
    fun showsEachMobilizationInItsTimeZone() {
        // 21 November 2026, 13:00 UTC: 14 h 00 in Paris, 17 h 00 in La Réunion.
        val paris = event(
            listOf(
                listOf("title", "[EXEMPLE] Manifestation <b>pour</b> l'école"),
                listOf("start", "1795266000"),
                listOf("end", "1795273200"),
                listOf("location", "[EXEMPLE] Place Bellecour"),
                listOf("ppf-territory", "Lyon"),
                listOf("ppf-event-type", "greve"),
                listOf("ppf-event-status", "annule"),
                listOf("r", "javascript:alert(1)"),
                listOf("r", "https://exemple.invalid/manif"),
            ),
            content = "[EXEMPLE] Première ligne\nDeuxième ligne",
        )
        val reunion = event(listOf(listOf("title", "[EXEMPLE] Rassemblement"), listOf("start", "1795266000"), listOf("start_tzid", "Indian/Reunion")))
        val html = render(events = listOf(paris, reunion))
        assertTrue(html.contains("2 mobilisations"))
        assertTrue(html.contains("samedi 21 novembre 2026 à 14 h 00"), html)
        assertTrue(html.contains("jusqu’au <time"))
        assertTrue(html.contains("samedi 21 novembre 2026 à 16 h 00"))
        assertTrue(html.contains("datetime=\"2026-11-21T14:00:00+01:00\""))
        assertTrue(html.contains("samedi 21 novembre 2026 à 17 h 00"))
        assertTrue(html.contains("<strong>Annulé : </strong>"))
        assertTrue(html.contains("Grève"))
        assertTrue(html.contains("<p>[EXEMPLE] Première ligne</p>"))
        // Text is escaped, script links are never written.
        assertTrue(html.contains("&lt;b&gt;pour&lt;/b&gt;"))
        assertFalse(html.contains("javascript:"))
        assertTrue(html.contains("href=\"https://exemple.invalid/manif\" rel=\"noopener noreferrer nofollow external\""))
        assertFalse(html.contains("<script"))
    }

    @Test
    fun theFormKeepsTheFiltersAndTheFeedFollowsThem() {
        val html = render(AgendaQuery(territory = "Saint-Étienne & co", type = "reunion"))
        assertTrue(html.contains("<form action=\"/agenda\" method=\"get\""))
        assertTrue(html.contains("value=\"Saint-Étienne &amp; co\""))
        assertTrue(html.contains("<option value=\"reunion\" selected=\"selected\">Réunion</option>"))
        assertTrue(html.contains("href=\"/api/v1/agenda.ics?territory=Saint-%C3%89tienne+%26+co&amp;type=reunion\""))
        assertTrue(html.contains("Aucune mobilisation prévue"))
        assertTrue(render().contains("href=\"/api/v1/agenda.ics\""))
        // Every type of the contract has a label.
        assertEquals(AgirFormats.EVENT_TYPES.toSet(), mobilizationTypeLabels.keys)
    }
}
