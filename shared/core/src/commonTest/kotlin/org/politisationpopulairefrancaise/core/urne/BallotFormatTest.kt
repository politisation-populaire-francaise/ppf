package org.politisationpopulairefrancaise.core.urne

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** What a ballot and a status say on the network (review R5): one size, the expected level. */
class BallotFormatTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun everyBallotHasTheSameSize() {
        val sizes = listOf(
            Ballot(1, "elu", "a".repeat(64), "b".repeat(128), "pour", 7),
            Ballot(987_654_321, "non-verifie", "c".repeat(64), "d".repeat(128), "contre", 4_294_967_296),
            Ballot(42, "verifie", "e".repeat(64), "f".repeat(128), UrneInfo.WITHDRAWN, 0),
        ).map { json.encodeToString(Ballot.serializer(), it.padded(json)).encodeToByteArray().size }
        assertEquals(listOf(Ballot.SIZE, Ballot.SIZE, Ballot.SIZE), sizes)
    }

    @Test
    fun thePaddingIsNotPartOfTheProofOfWork() {
        val ballot = Ballot.withProofOfWork(3, "verifie", "a".repeat(64), "b".repeat(128), "pour", difficulty = 8)
        val padded = ballot.padded(json)
        assertEquals(ballot.powDigest().toHexString(), padded.powDigest().toHexString())
        assertEquals(padded, json.decodeFromString(Ballot.serializer(), json.encodeToString(Ballot.serializer(), padded)))
    }

    @Test
    fun theExpectedLevelIsTheOneOfTheWeekOfOpening() {
        // Wednesday 4 November 2026, 12:00 UTC: the week of Monday 2 November.
        val wednesday = 1_793_793_600L
        assertEquals("2026-11-02", VoterStatus.mondayOf(wednesday))
        assertEquals("2026-11-09", VoterStatus.mondayOf(wednesday + 5 * 86_400))
        val status = VoterStatus("verifie", "2026-11-09", emptyList(), previousLevel = "non-verifie", registeredWeek = "2026-10-26")
        assertEquals(VoteLevel.NON_VERIFIE, status.levelFor(wednesday), "opened before the change")
        assertEquals(VoteLevel.VERIFIE, status.levelFor(wednesday + 7 * 86_400))
        // A box opened before the registration: « non vérifié », whatever the level.
        assertEquals(VoteLevel.NON_VERIFIE, status.copy(level = "elu", levelWeek = "2026-10-26").levelFor(wednesday - 14 * 86_400))
        // A server that does not say: no check.
        assertEquals(null, VoterStatus("elu", "2026-11-02", emptyList()).levelFor(wednesday))
    }
}
