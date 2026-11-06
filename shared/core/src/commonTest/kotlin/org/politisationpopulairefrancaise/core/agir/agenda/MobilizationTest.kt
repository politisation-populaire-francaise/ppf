package org.politisationpopulairefrancaise.core.agir.agenda

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.signing.EventReplacement
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MobilizationTest {
    private val fixtures = AgendaFixtures()

    @Test
    fun theWireValuesAreThoseOfTheContract() {
        assertEquals(AgirFormats.EVENT_TYPES, MobilizationType.entries.map { it.tagValue })
        assertEquals(AgirFormats.EVENT_STATUSES, MobilizationStatus.entries.map { it.tagValue })
        assertEquals(AgirFormats.DECLARATION_STATES, DeclarationState.entries.map { it.tagValue })
        assertEquals(31923, Mobilization.KIND)
    }

    @Test
    fun aBuiltEventIsSignedReadBackAndFollowsTheRules() = runTest {
        val organizer = "c".repeat(64)
        val m = fixtures.mobilization(
            organizers = listOf(organizer, organizer),
            links = listOf("https://exemple.invalid/appel"),
            summary = "[EXEMPLE] Résumé",
            declaration = DeclarationState.FAITE,
        )
        assertTrue(m.event.verify())
        assertEquals(emptyList(), MobilizationRules.check(m.event))
        assertEquals("31923:${fixtures.pubkey}:rassemblement-test", m.address.toString())
        assertEquals("[EXEMPLE] Rassemblement pour les services publics", m.title)
        assertEquals("Rassemblement pour les services publics", m.displayTitle)
        assertTrue(m.example)
        assertEquals(AgendaTimes.SATURDAY_14H, m.start)
        assertEquals(AgendaTimes.SATURDAY_17H, m.end)
        assertEquals("Europe/Paris", m.timeZone)
        assertEquals(MobilizationType.RASSEMBLEMENT, m.type)
        assertEquals(MobilizationStatus.PREVU, m.status)
        assertEquals(DeclarationState.FAITE, m.declaration)
        assertEquals(listOf(organizer), m.organizers, "an organiser named twice appears once")
        assertEquals(listOf(organizer), m.hosts)
        assertEquals(listOf("https://exemple.invalid/appel"), m.links)
        assertEquals("[EXEMPLE] Résumé", m.summary)
        assertEquals("1.0", m.charter)
        // Tags in the order of the contract; no status tag for an event "prévu".
        assertEquals(
            listOf("d", "title", "start", "end", "start_tzid", "location", "ppf-event-type", "ppf-territory", "ppf-charter", "ppf-declaration", "p", "r", "summary"),
            m.event.tags.map { it[0] },
        )
        assertEquals(listOf("p", organizer, "", "organizer"), m.event.tags.single { it[0] == "p" })
    }

    @Test
    fun withoutOrganiserTheAuthorHostsTheEvent() = runTest {
        val m = fixtures.mobilization(end = null)
        assertEquals(listOf(fixtures.pubkey), m.hosts)
        assertTrue(m.isHostedBy(fixtures.pubkey))
        assertFalse(m.isHostedBy("d".repeat(64)))
        // Without end, the event is under way for two hours.
        assertEquals(AgendaTimes.SATURDAY_14H + 2 * AgendaTimes.HOUR, m.endsAt)
        assertTrue(m.isOngoing(AgendaTimes.SATURDAY_14H + AgendaTimes.HOUR))
        assertFalse(m.isPast(AgendaTimes.SATURDAY_14H + AgendaTimes.HOUR))
        assertTrue(m.isPast(AgendaTimes.SATURDAY_14H + 2 * AgendaTimes.HOUR))
    }

    @Test
    fun readingIsTolerantWithValuesANewerVersionMayAdd() = runTest {
        val event = fixtures.raw(
            validTags().with("ppf-event-type", "flashmob").with("ppf-event-status", "complet")
                .with("g", "not a geohash!").with("ppf-declaration", "peut-etre") +
                listOf(listOf("r", "javascript:alert(1)"), listOf("r", "https://exemple.invalid"), listOf("p", "zz", "", "organizer")),
        )
        val m = assertNotNull(Mobilization.from(event))
        assertEquals(MobilizationType.AUTRE, m.type)
        assertEquals(MobilizationStatus.PREVU, m.status)
        assertNull(m.geohash)
        assertNull(m.declaration)
        assertEquals(listOf("https://exemple.invalid"), m.links, "only http and https links are kept")
        assertEquals(emptyList(), m.organizers)
        // The rules, like the server, refuse them.
        assertEquals(MobilizationError.BAD_EVENT_TYPE, MobilizationRules.firstIssue(event)?.error)
    }

    @Test
    fun anEventWithoutWhatTheAgendaNeedsIsNotAMobilization() = runTest {
        for (name in listOf("d", "title", "start", "location", "ppf-territory")) {
            assertNull(Mobilization.from(fixtures.raw(validTags().without(name))), "without $name")
        }
        assertNull(Mobilization.from(fixtures.raw(validTags().with("d", "Pas Valide"))))
        assertNull(Mobilization.from(fixtures.raw(validTags().with("start", "demain"))))
        // An end before the start is ignored by the reading (and refused by the rules).
        val reversed = fixtures.raw(validTags().with("end", (AgendaTimes.SATURDAY_14H - 1).toString()))
        assertNull(assertNotNull(Mobilization.from(reversed)).end)
        assertEquals(MobilizationError.BAD_END, MobilizationRules.firstIssue(reversed)?.error)
        // Another kind is never a mobilization.
        val note = fixtures.sign(org.politisationpopulairefrancaise.core.signing.UnsignedEvent(fixtures.pubkey, 1, 1, validTags()))
        assertNull(Mobilization.from(note))
    }

    @Test
    fun aNewerVersionReplacesTheOlderUnderTheSameAddress() = runTest {
        val first = fixtures.mobilization(createdAt = AgendaTimes.WRITTEN)
        val cancelled = fixtures.mobilization(createdAt = AgendaTimes.WRITTEN + 60, status = MobilizationStatus.ANNULE)
        assertEquals(EventReplacement.replacementKey(first.event), EventReplacement.replacementKey(cancelled.event))
        assertTrue(EventReplacement.supersedes(cancelled.event, first.event))
        assertTrue(cancelled.cancelled)
        assertEquals(listOf("ppf-event-status", "annule"), cancelled.event.tags.single { it[0] == "ppf-event-status" })
    }

    @Test
    fun identifiersAreStableSlugsWithARandomSuffix() {
        val id = Mobilization.newIdentifier("[EXEMPLE] Grève du 5 décembre !", Random(1))
        assertTrue(id.startsWith("exemple-greve-du-5-decembre-"), id)
        assertEquals("exemple-greve-du-5-decembre-".length + 8, id.length)
        assertTrue(Mobilization.isValidIdentifier(id))
        assertTrue(Mobilization.newIdentifier("###", Random(2)).startsWith("evenement-"))
        assertTrue(Mobilization.newIdentifier("a".repeat(200)).length <= 64)
        assertFalse(Mobilization.isValidIdentifier(""))
        assertFalse(Mobilization.isValidIdentifier("a".repeat(65)))
        assertFalse(Mobilization.isValidIdentifier("A"))
    }

    @Test
    fun onlyWebLinksMayBeOpened() {
        assertTrue(Mobilization.isValidLink("https://exemple.invalid/page?x=1"))
        assertTrue(Mobilization.isValidLink("http://exemple.invalid"))
        assertFalse(Mobilization.isValidLink("https://"))
        assertFalse(Mobilization.isValidLink("ftp://exemple.invalid"))
        assertFalse(Mobilization.isValidLink("javascript:alert(1)"))
        assertFalse(Mobilization.isValidLink("https://exemple.invalid/a b"))
        assertFalse(Mobilization.isValidLink("intent://exemple.invalid"))
    }
}
