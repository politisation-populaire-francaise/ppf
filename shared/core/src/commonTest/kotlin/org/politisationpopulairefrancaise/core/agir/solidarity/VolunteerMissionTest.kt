package org.politisationpopulairefrancaise.core.agir.solidarity

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.time.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The volunteer missions of "Je veux aider" (AG-12): read only from a trusted editorial key, their skills, place and state. */
class VolunteerMissionTest {

    private val fixtures = SolidarityFixtures()

    @Test
    fun buildsAndReadsBackAWholeMission() = runTest {
        // 15 December 2026, 23:59 in Paris (UTC+1).
        val deadline = 1_797_375_540L
        val event = fixtures.editor.sign(
            fixtures.missionEvent(
                slug = "mission-accueil",
                title = "[EXEMPLE] Accueillir au stand",
                skills = listOf("moderation", "accueil"),
                territory = "Lyon (69)",
                remote = false,
                location = "Stand fictif, Lyon",
                commitment = "Une demi-journée",
                people = 2,
                deadline = deadline,
            ),
        )
        assertTrue(event.verify())
        assertEquals(SolidarityFormats.TYPE_MISSION, event.firstTagValue(AgirFormats.TAG_TYPE))
        // One tag per skill, in the order of the list.
        assertEquals(listOf("moderation", "accueil"), event.tagValues(SolidarityFormats.TAG_SKILL))
        val mission = fixtures.readMission(event)
        assertEquals("mission-accueil", mission.slug)
        assertEquals("Accueillir au stand", mission.displayTitle)
        assertTrue(mission.example)
        assertEquals(listOf("moderation", "accueil"), mission.skills)
        assertEquals("Lyon (69)", mission.territory)
        assertEquals(false, mission.remote)
        assertEquals("Stand fictif, Lyon", mission.location)
        assertEquals("Une demi-journée", mission.commitment)
        assertEquals(2, mission.people)
        assertEquals(deadline, mission.deadline)
        assertEquals(CivilDate(2026, 12, 15), mission.deadlineDate)
        assertTrue(mission.isOpen)
        assertEquals(ContentOrigin.HUMAN, mission.origin)
        assertEquals("Description fictive.", mission.description)
    }

    @Test
    fun onlyMissionsOfATrustedKeyAreAccepted() = runTest {
        val byStranger = fixtures.mission("intrus", signer = fixtures.stranger)
        assertNotNull(VolunteerMission.parse(byStranger))
        assertNull(VolunteerMission.from(byStranger, fixtures.trust))
        // A fund is not a mission.
        assertNull(VolunteerMission.parse(fixtures.fund("caisse")))
    }

    @Test
    fun eachRequiredTagIsRequiredAndAMissionIsRemoteOrHasAPlace() = runTest {
        val remote = fixtures.missionEvent("a-distance")
        assertNotNull(VolunteerMission.from(fixtures.editor.sign(remote), fixtures.trust))
        val required = listOf("d", AgirFormats.TAG_TITLE, AgirFormats.TAG_SUMMARY, AgirFormats.TAG_TYPE, SolidarityFormats.TAG_SKILL, AgirFormats.TAG_TERRITORY, AgirFormats.TAG_PUBLISHED_AT)
        for (tag in required) {
            assertNull(VolunteerMission.from(fixtures.without(remote, tag), fixtures.trust), "without $tag")
        }
        // Neither remote nor a place: refused; a place alone is enough.
        assertNull(VolunteerMission.from(fixtures.without(remote, SolidarityFormats.TAG_REMOTE), fixtures.trust))
        val onSite = fixtures.missionEvent("sur-place", remote = false, location = "Local fictif, Lille")
        assertEquals("Local fictif, Lille", fixtures.readMission(fixtures.editor.sign(onSite)).location)
    }

    @Test
    fun unknownSkillsAndStatusesNeverHideNorOpenAMission() = runTest {
        val unsigned = fixtures.missionEvent("future", skills = listOf("traduction"))
        val tags = unsigned.tags + listOf(listOf(SolidarityFormats.TAG_SKILL, "photographie"), listOf(SolidarityFormats.TAG_SKILL, "traduction"))
        val read = fixtures.readMission(fixtures.editor.sign(unsigned.copy(tags = tags)))
        assertEquals(listOf("traduction", SolidarityFormats.SKILL_OTHER), read.skills, "unknown read as other, each once, in the order of the list")

        val newStatus = fixtures.readMission(fixtures.replacing(unsigned, SolidarityFormats.TAG_MISSION_STATUS, "en-pause"))
        assertEquals(SolidarityFormats.MISSION_CLOSED, newStatus.status)
        val filled = fixtures.readMission(fixtures.mission("pourvue", status = SolidarityFormats.MISSION_FILLED))
        assertTrue(filled.isFilled && !filled.isOpen)

        assertEquals(listOf("juriste", "graphisme"), VolunteerMission.readSkills(listOf(" graphisme ", "juriste", "")))
    }

    @Test
    fun buildRefusesWhatTheApplicationWouldNotShow() {
        assertFailsWith<IllegalArgumentException>("a skill") { fixtures.missionEvent("x", skills = emptyList()) }
        assertFailsWith<IllegalArgumentException>("a known skill") { fixtures.missionEvent("x", skills = listOf("photographie")) }
        assertFailsWith<IllegalArgumentException>("remote or a place") { fixtures.missionEvent("x", remote = false, location = null) }
        assertFailsWith<IllegalArgumentException>("a positive number") { fixtures.missionEvent("x", people = 0) }
        assertFailsWith<IllegalArgumentException>("a known status") { fixtures.missionEvent("x", status = "en-pause") }
    }
}
