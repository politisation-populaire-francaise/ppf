package org.politisationpopulairefrancaise.core.agir.agenda

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The public responses of organisations to a mobilization (kind 31925, contract version 2, D-044). */
class ParticipationTest {
    private val author = AgendaFixtures()
    private val union = AgendaFixtures()
    private val collective = AgendaFixtures()
    private val citizen = AgendaFixtures()

    private suspend fun AgendaFixtures.answer(
        mobilization: Mobilization,
        status: ParticipationStatus = ParticipationStatus.ACCEPTED,
        role: ParticipationRole = ParticipationRole.PARTICIPANT,
        createdAt: Long = AgendaTimes.WRITTEN + AgendaTimes.HOUR,
    ): Participation = checkNotNull(Participation.from(sign(Participation.build(keys.publicKey, mobilization, status, createdAt, role))))

    @Test
    fun theWireValuesAreThoseOfTheContractAndOfNip52() {
        assertEquals(31925, Participation.KIND)
        assertEquals(listOf("accepted", "declined", "tentative"), ParticipationStatus.entries.map { it.tagValue })
        assertEquals(AgirFormats.PARTICIPATION_STATUSES, ParticipationStatus.entries.map { it.tagValue })
        assertEquals(AgirFormats.PARTICIPATION_ROLES, ParticipationRole.entries.map { it.tagValue })
        assertEquals("2", AgirFormats.CONTRACT_VERSION)
    }

    @Test
    fun aBuiltResponseNamesTheMobilizationAndReadsBack() = runTest {
        val m = author.mobilization()
        val event = union.sign(Participation.build(union.keys.publicKey, m, ParticipationStatus.ACCEPTED, AgendaTimes.WRITTEN, ParticipationRole.CO_ORGANISER, note = "  [EXEMPLE] Nous y serons.  "))
        assertTrue(event.verify())
        assertEquals(
            listOf(
                listOf("d", m.address.toString()),
                listOf("a", m.address.toString()),
                listOf("status", "accepted"),
                listOf("ppf-role", "organisateur"),
                listOf("p", author.pubkey),
            ),
            event.tags,
        )
        assertEquals("[EXEMPLE] Nous y serons.", event.content)
        val read = assertNotNull(Participation.from(event))
        assertEquals("31925:${union.pubkey}:${m.address}", read.address.toString())
        assertEquals(m.address, read.mobilization)
        assertEquals(ParticipationStatus.ACCEPTED, read.status)
        assertEquals(ParticipationRole.CO_ORGANISER, read.role)
        assertEquals(author.pubkey, read.mobilizationAuthor)
        // The note is cut at 280 characters.
        val long = Participation.build(union.keys.publicKey, m, ParticipationStatus.ACCEPTED, AgendaTimes.WRITTEN, note = "x".repeat(400))
        assertEquals(280, long.content.length)
    }

    @Test
    fun readingIsTolerantOnTheRoleAndStrictOnTheRest() = runTest {
        val m = author.mobilization()
        suspend fun read(tags: List<List<String>>) = Participation.from(union.sign(UnsignedEvent(union.pubkey, AgendaTimes.WRITTEN, Participation.KIND, tags)))
        val base = listOf(listOf("d", "uuid-1234"), listOf("a", m.address.toString()), listOf("status", "accepted"))
        // Another client: a random d, no role, no p.
        assertEquals(ParticipationRole.PARTICIPANT, read(base)?.role)
        assertEquals(ParticipationRole.PARTICIPANT, read(base + listOf(listOf("ppf-role", "president")))?.role)
        assertNull(read(base.without("a")))
        assertNull(read(base.with("a", "30023:${author.pubkey}:un-appel")), "only a mobilization can be answered")
        assertNull(read(base.with("a", "pas une adresse")))
        assertNull(read(base.without("status")))
        assertNull(read(base.with("status", "maybe")))
        assertNull(read(base.without("d")))
        assertNull(Participation.from(m.event), "a mobilization is not a response")
    }

    @Test
    fun onlyConfirmedNamedOrganisationsCoOrganiseAndOnlyCountedKeysTakePart() = runTest {
        val m = author.mobilization(organizers = listOf(union.pubkey, collective.pubkey, "e".repeat(64)))
        val counted = setOf(union.pubkey, collective.pubkey, author.pubkey)
        val responses = Participations.of(
            listOf(
                union.answer(m, role = ParticipationRole.CO_ORGANISER, createdAt = AgendaTimes.WRITTEN + 10),
                // Named, but only takes part: announced as organiser, listed as participant.
                collective.answer(m, role = ParticipationRole.PARTICIPANT, createdAt = AgendaTimes.WRITTEN + 5),
                // A key that does not count (a citizen): never shown, even as co-organiser.
                citizen.answer(m, role = ParticipationRole.CO_ORGANISER),
            ),
        )
        val hosting = responses.hosting(m) { it in counted }
        assertEquals(listOf(union.pubkey), hosting.confirmed)
        assertEquals(listOf(collective.pubkey, "e".repeat(64)), hosting.announced)
        assertEquals(listOf(collective.pubkey, union.pubkey), hosting.participants, "first to answer first")
        // Nothing counts: everything named is only announced, nobody takes part.
        assertEquals(Hosting(emptyList(), listOf(union.pubkey, collective.pubkey, "e".repeat(64)), emptyList()), responses.hosting(m) { false })
    }

    @Test
    fun aKeyNotNamedCannotMakeItselfCoOrganiser() = runTest {
        val m = author.mobilization()
        val responses = Participations.of(listOf(union.answer(m, role = ParticipationRole.CO_ORGANISER)))
        val hosting = responses.hosting(m) { true }
        assertEquals(emptyList(), hosting.confirmed)
        assertEquals(listOf(union.pubkey), hosting.participants)
    }

    @Test
    fun theNewestResponseOfAKeyWinsWhateverItsIdentifier() = runTest {
        val m = author.mobilization(organizers = listOf(union.pubkey))
        val accepted = union.answer(m, role = ParticipationRole.CO_ORGANISER, createdAt = AgendaTimes.WRITTEN)
        val declined = union.answer(m, status = ParticipationStatus.DECLINED, createdAt = AgendaTimes.WRITTEN + 60)
        // Another client answered earlier under another d.
        val older = checkNotNull(
            Participation.from(
                union.sign(
                    UnsignedEvent(
                        union.pubkey, AgendaTimes.WRITTEN - 60, Participation.KIND,
                        listOf(listOf("d", "autre-client"), listOf("a", m.address.toString()), listOf("status", "accepted")),
                    ),
                ),
            ),
        )
        val withdrawn = Participations.of(listOf(older, declined, accepted))
        assertEquals(ParticipationStatus.DECLINED, withdrawn.of(union.pubkey, m.address.toString())?.status)
        assertEquals(Hosting(emptyList(), listOf(union.pubkey), emptyList()), withdrawn.hosting(m) { true })
        assertEquals(emptySet(), withdrawn.acceptedBy(union.pubkey))
        val shown = Participations.of(listOf(older, accepted))
        assertEquals(setOf(m.address.toString()), shown.acceptedBy(union.pubkey))
        assertEquals(listOf(union.pubkey), shown.hosting(m) { true }.confirmed)
        // Tentative and declined answers are never shown.
        val tentative = Participations.of(listOf(collective.answer(m, status = ParticipationStatus.TENTATIVE)))
        assertEquals(emptyList(), tentative.hosting(m) { true }.participants)
        assertEquals(Participations.EMPTY, Participations.fromEvents(listOf(m.event)))
    }
}
