package org.politisationpopulairefrancaise.core.agir.poster

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaFixtures
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaTimes
import org.politisationpopulairefrancaise.core.agir.agenda.MobilizationType
import org.politisationpopulairefrancaise.core.agir.calls.Call
import org.politisationpopulairefrancaise.core.agir.calls.CallFixtures
import org.politisationpopulairefrancaise.core.agir.calls.CallType
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The common model of a poster (AG-61), from a mobilization and from a call; fictitious data. */
class PosterContentTest {
    @Test
    fun aMobilizationGivesItsPoster() = runTest {
        val fixtures = AgendaFixtures()
        val organisation = IdentityKeys.generate().publicKey.hex
        val mobilization = fixtures.mobilization(summary = "[EXEMPLE] Résumé court.")
        val poster = PosterContent.of(mobilization, hosts = listOf(mobilization.author, organisation, organisation))
        assertEquals(PosterKind.MOBILIZATION, poster.kind)
        assertEquals(mobilization.address, poster.address)
        assertEquals("Rassemblement pour les services publics", poster.title, "the marker is shown apart")
        assertTrue(poster.example)
        assertEquals(MobilizationType.RASSEMBLEMENT, poster.mobilizationType)
        assertNull(poster.callType)
        assertEquals(AgendaTimes.SATURDAY_14H, poster.start)
        assertEquals(AgendaTimes.SATURDAY_17H, poster.end)
        assertNull(poster.deadline)
        assertEquals("Place de la Mairie, Villefictive", poster.place, "the place names its territory already")
        assertEquals("Villefictive", poster.territory)
        assertEquals(listOf(mobilization.author, organisation), poster.hosts)
        assertEquals("[EXEMPLE] Résumé court.", poster.text)
        assertIs<PosterPayload.Event>(poster.payload)
        assertEquals("affiche-rassemblement-test", poster.fileBase)
        assertEquals("affiche-rassemblement-test-a3", poster.fileName(PosterFormat.A3))
        // By default, the author hosts the event alone.
        assertEquals(listOf(mobilization.author), PosterContent.of(mobilization).hosts)
    }

    @Test
    fun theShortTextIsTheSummaryOrTheBeginningOfTheDescription() = runTest {
        val mobilization = AgendaFixtures().mobilization(summary = null, description = "[EXEMPLE] Une description courte.")
        assertEquals("[EXEMPLE] Une description courte.", PosterContent.of(mobilization).text)
        assertNull(PosterContent.shortText(null, "  "))
        assertEquals("Court.", PosterContent.shortText(" ", "Court."))
        val long = (1..200).joinToString(" ") { "mot" }
        val text = assertNotNull(PosterContent.shortText(null, long))
        assertTrue(text.length <= PosterContent.MAX_TEXT)
        assertTrue(text.endsWith("…"))
        assertTrue(text.removeSuffix("…").endsWith("mot"), "cut after a whole word")
    }

    @Test
    fun thePlaceAddsTheTerritoryOnlyWhenItIsMissing() {
        assertEquals("Salle des fêtes, Villefictive", PosterContent.placeOf("Salle des fêtes", "Villefictive"))
        assertEquals("Place de la République, Paris", PosterContent.placeOf("Place de la République, Paris", "paris"))
        assertEquals("Gare de Saint-Étienne", PosterContent.placeOf("Gare de Saint-Étienne", "Saint-Etienne"))
        assertEquals("Mairie", PosterContent.placeOf(" Mairie ", " "))
    }

    @Test
    fun aCallGivesItsPoster() = runTest {
        val keys = IdentityKeys.generate()
        val draft = CallFixtures.validDraft(CallType.SE_REUNIR)
        val call = assertNotNull(Call.from(LocalEventSigner(keys).sign(draft.toUnsignedEvent(keys.publicKey, CallFixtures.NOW, "reunion-loyers-1a2b"))))
        val poster = PosterContent.of(call)
        assertEquals(PosterKind.CALL, poster.kind)
        assertEquals(CallType.SE_REUNIR, poster.callType)
        assertNull(poster.mobilizationType)
        assertEquals("Réunion publique sur les loyers du quartier", poster.title)
        assertTrue(poster.example)
        assertEquals(call.start, poster.start)
        assertNotNull(poster.start)
        assertEquals("Salle des fêtes, place fictive, Lyon", poster.place)
        assertEquals("Lyon", poster.territory)
        assertEquals(listOf(keys.publicKey.hex), poster.hosts, "the author launched the call")
        assertEquals(draft.text, poster.text, "no summary: the text of the call")
        val payload = assertIs<PosterPayload.Event>(poster.payload)
        assertEquals(call.event, assertIs<PosterScan.Event>(PosterQr.read(payload.text)).event)
        assertEquals("affiche-reunion-loyers-1a2b-a4", poster.fileName(PosterFormat.A4))
        assertFalse(poster.fileBase.any { it.isUpperCase() || it == ' ' })
    }

    @Test
    fun theFormatsAreThoseOfIso216() {
        assertEquals(595, PosterFormat.A4.widthPt)
        assertEquals(842, PosterFormat.A4.heightPt)
        assertEquals(842, PosterFormat.A3.widthPt)
        assertEquals(1191, PosterFormat.A3.heightPt)
        assertEquals(1f, PosterFormat.A4.scale)
        assertTrue(PosterFormat.A3.scale in 1.41f..1.42f)
    }
}
