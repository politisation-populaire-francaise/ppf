package org.politisationpopulairefrancaise.core.agir.calls

import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.agir.calls.CallFixtures.AUTHOR
import org.politisationpopulairefrancaise.core.agir.calls.CallFixtures.NOW
import org.politisationpopulairefrancaise.core.agir.calls.CallFixtures.unsignedForTest
import org.politisationpopulairefrancaise.core.signing.EventReplacement
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CallTest {

    @Test
    fun theTypesAndTheirFieldsAreThoseOfTheContract() {
        assertEquals(AgirFormats.CALL_TYPES, CallType.entries.map { it.tagValue })
        assertEquals(AgirFormats.CALL_REQUIRED_TAGS.keys, CallType.entries.map { it.tagValue }.toSet())
        // Every specific field of the form is a tag the contract requires for some type: no name drifts.
        val contractTags = AgirFormats.CALL_REQUIRED_TAGS.values.flatten().flatMap { it.split('|') }.toSet()
        CallType.entries.forEach { type -> CallFields.specific(type).forEach { assertTrue(it.tag in contractTags, it.tag) } }
        assertTrue(CallFields.DURATION in contractTags)
        // The common fields stay in the common part of the form.
        assertTrue(CallFields.specific(CallType.SE_REUNIR).none { it.tag == AgirFormats.TAG_LOCATION || it.tag == AgirFormats.TAG_LINK })
        assertEquals(listOf("ppf-object", "start", "ppf-organizer", "ppf-accessibility"), CallFields.specific(CallType.SE_REUNIR).map { it.tag })
        assertEquals(listOf("location", "r"), CallType.SE_REUNIR.requirements[2].alternatives)
        assertEquals(CallFieldKind.CHOICE, CallFields.field(CallFields.PLATFORM).kind)
        assertEquals(AgirFormats.PETITION_PLATFORMS, CallFields.field(CallFields.PLATFORM).choices)
        assertEquals(CallFieldKind.FLAG, CallFields.field(CallFields.REMOTE).kind)
        assertTrue(CallType.entries.single { it.isFundraising } == CallType.COLLECTE)
        assertTrue(CallType.entries.single { it.isWitnesses } == CallType.TEMOINS)
    }

    @Test
    fun eachTypeAnswersWithItsOwnButton() {
        assertEquals(CallResponse.ATTEND, CallType.MANIFESTER.response)
        assertEquals(CallResponse.SIGNED, CallType.PETITION.response)
        assertEquals(CallResponse.HELP, CallType.BENEVOLES.response)
        assertEquals(CallResponse.SUPPORT, CallType.SOUTENIR.response)
        assertEquals(CallResponse.TESTIFY, CallType.TEMOINS.response)
    }

    @Test
    fun aDraftBecomesTheEventOfTheContractAndReadsBack() {
        val draft = CallFixtures.validDraft(CallType.SE_REUNIR).copy(summary = "Un résumé court", goal = "40")
        val event = draft.toUnsignedEvent(AUTHOR, NOW, slug = "reunion-loyers-3f9a")
        assertEquals(30023, event.kind)
        fun tag(name: String) = event.tags.firstOrNull { it[0] == name }?.getOrNull(1)
        assertEquals("reunion-loyers-3f9a", tag("d"))
        assertEquals("appel", tag("ppf-type"))
        assertEquals("se-reunir", tag("ppf-call-type"))
        assertEquals("Lyon", tag("ppf-territory"))
        assertEquals("1.0", tag("ppf-charter"))
        assertEquals("human", tag("ppf-origin"))
        assertEquals(NOW.toString(), tag("published_at"))
        assertEquals("ouvert", tag("ppf-call-status"))
        assertEquals("40", tag("ppf-goal"))
        assertEquals("1794677400", tag("start"))
        assertEquals("Europe/Paris", tag("start_tzid"))
        assertEquals("Salle des fêtes, place fictive", tag("location"))
        assertEquals("[EXEMPLE] Collectif fictif des locataires", tag("ppf-organizer"))
        assertNull(tag("ppf-deadline"))
        assertNull(tag("end"))

        val call = assertNotNull(Call.from(event.unsignedForTest()))
        assertEquals(CallType.SE_REUNIR, call.type)
        assertEquals(CallStatus.OPEN, call.status)
        assertEquals("[EXEMPLE] Réunion publique sur les loyers du quartier", call.title)
        assertEquals("Réunion publique sur les loyers du quartier", call.displayTitle)
        assertTrue(call.isExample)
        assertEquals("Un résumé court", call.summary)
        assertEquals(40L, call.goal)
        assertEquals(1_794_677_400L, call.start)
        assertEquals(call.start, call.dueAt)
        assertEquals("Salle de plain-pied, accessible en fauteuil roulant", call.fields[CallFields.ACCESSIBILITY])
        assertEquals("30023:${AUTHOR.hex}:reunion-loyers-3f9a", call.address.toString())
        assertTrue(call.missingRequirements().isEmpty())
    }

    @Test
    fun theValidDraftOfEveryTypeCarriesEveryRequiredTag() {
        CallType.entries.forEach { type ->
            val call = CallFixtures.call(CallFixtures.validDraft(type), slug = "appel-${type.tagValue}")
            assertEquals(type, call.type)
            assertEquals(emptyList(), call.missingRequirements(), type.tagValue)
        }
    }

    @Test
    fun aDateTypedUnderATypeThatDoesNotAskForItIsDropped() {
        val draft = CallFixtures.validDraft(CallType.SOUTENIR).withDate(AgirFormats.TAG_START, DateTimeText("14/11/2026", "18:00"))
        val event = draft.toUnsignedEvent(AUTHOR, NOW, slug = "soutien")
        assertTrue(event.tags.none { it[0] == AgirFormats.TAG_START || it[0] == AgirFormats.TAG_START_TZID })
        // A deadline without an hour lasts the whole day.
        val deadline = event.tags.first { it[0] == AgirFormats.TAG_DEADLINE }[1].toLong()
        assertEquals(ParisDateTime(2026, 12, 12, 23, 59), ParisTime.of(deadline))
        // And the fields of another type are not written either.
        val mixed = draft.withField(CallFields.COMPANY, "Entreprise").toUnsignedEvent(AUTHOR, NOW, slug = "soutien")
        assertTrue(mixed.tags.none { it[0] == CallFields.COMPANY })
    }

    @Test
    fun onlyWellFormedCallsAreRead() {
        val good = CallFixtures.validDraft(CallType.AUTRE).toUnsignedEvent(AUTHOR, NOW, slug = "autre")
        fun without(name: String) = good.copy(tags = good.tags.filter { it[0] != name }).unsignedForTest()
        fun replaced(name: String, value: String) = good.copy(tags = good.tags.map { if (it[0] == name) listOf(name, value) else it }).unsignedForTest()
        assertNotNull(Call.from(good.unsignedForTest()))
        assertNull(Call.from(without("title")))
        assertNull(Call.from(without("ppf-territory")))
        assertNull(Call.from(without("d")))
        assertNull(Call.from(replaced("ppf-call-type", "inconnu")))
        assertNull(Call.from(replaced("ppf-type", "revendication")))
        assertNull(Call.from(good.copy(kind = 1).unsignedForTest()))
        assertFalse(Call.isCall(replaced("ppf-type", "fiche-pratique")))
        // An unknown status reads as open; a negative goal is ignored.
        assertEquals(CallStatus.OPEN, assertNotNull(Call.from(replaced("ppf-call-status", "bizarre"))).status)
        assertNull(Call.from(replaced("ppf-call-status", "ouvert").let { e -> e.copy(tags = e.tags + listOf(listOf("ppf-goal", "-3"))) })?.goal)
    }

    @Test
    fun closingPublishesANewVersionOfTheSameAddressWithItsReport() {
        val open = CallFixtures.call(CallFixtures.validDraft(CallType.PETITION), slug = "petition-bus")
        val closing = open.closedVersion("  [EXEMPLE] 812 signatures, la ligne est maintenue.  ", now = NOW + 86_400)
        val closed = assertNotNull(Call.from(closing.unsignedForTest()))
        assertEquals(open.address, closed.address)
        assertEquals(CallStatus.CLOSED, closed.status)
        assertEquals("[EXEMPLE] 812 signatures, la ligne est maintenue.", closed.report)
        assertEquals(open.publishedAt, closed.publishedAt)
        assertEquals(open.links, closed.links)
        assertEquals(open.text, closed.text)
        assertEquals(1, closing.tags.count { it[0] == AgirFormats.TAG_CALL_STATUS })
        assertTrue(EventReplacement.supersedes(closed.event, open.event))

        // A clock behind the call still produces a newer version; an empty report is simply absent.
        val early = open.closedVersion("", now = NOW - 10)
        assertEquals(NOW + 1, early.createdAt)
        assertNull(assertNotNull(Call.from(early.unsignedForTest())).report)
    }

    @Test
    fun theDueDateIsTheDeadlineElseTheStart() {
        val petition = CallFixtures.call(CallFixtures.validDraft(CallType.PETITION), slug = "p")
        assertEquals(petition.deadline, petition.dueAt)
        val other = CallFixtures.call(CallFixtures.validDraft(CallType.AUTRE), slug = "a")
        assertNull(other.dueAt)
    }

    @Test
    fun slugsAreShortStableWordsWithARandomSuffix() {
        val slug = CallDraft.slugFor("[EXEMPLE] Assemblée de quartier sur les loyers !", Random(1))
        assertTrue(slug.startsWith("assemblee-quartier-loyers-"), slug)
        assertTrue(Regex("[a-z0-9-]{1,64}").matches(slug), slug)
        assertTrue(CallDraft.slugFor("x".repeat(300)).length <= 64)
        assertTrue(CallDraft.slugFor("!!!").startsWith("appel-"))
    }

    @Test
    fun anEmptyDraftCannotBecomeAnEvent() {
        val failure = runCatching { CallDraft(title = "Sans type").toUnsignedEvent(AUTHOR, NOW) }
        assertTrue(failure.isFailure)
        assertTrue(runCatching { UnsignedEvent(AUTHOR.hex, NOW, 30023) }.isSuccess)
    }
}
