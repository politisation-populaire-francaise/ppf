package org.politisationpopulairefrancaise.core.urne

import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** « Mon bulletin a été compté » (DE-29): the device finds its ballot in the published list and recounts the box. */
class MyBallotTest {

    private class Fixture(val group: Ristretto255, val box: UrneInfo, val closing: UrneClosing, val mine: StoredToken, val publicKeys: Map<String, String>)

    /** A closed box with three ballots, one of them this device's ([mineChoice]), and the token the device kept. */
    private suspend fun fixture(mineChoice: String = "pour", mineSent: String? = mineChoice, mineLatest: String? = mineSent): Fixture {
        val group = LibsodiumRistretto255.load()
        val voprf = Voprf(group, VoprfMode.VOPRF)
        val random = Random(29)
        val levels = listOf(VoteLevel.NON_VERIFIE, VoteLevel.VERIFIE, VoteLevel.ELU)
        val secrets = levels.associate { it.code to group.randomScalar() }
        val publicKeys = secrets.mapValues { group.multiplyBase(it.value).toHexString() }
        fun ballot(level: VoteLevel, y: ByteArray, choice: String) =
            PublishedBallot(y.toHexString(), voprf.evaluate(secrets.getValue(level.code), y).toHexString(), level.code, choice, day = 0)
        val myY = random.nextBytes(32)
        val ballots = listOf(
            ballot(VoteLevel.VERIFIE, myY, mineChoice),
            ballot(VoteLevel.VERIFIE, random.nextBytes(32), "contre"),
            ballot(VoteLevel.NON_VERIFIE, random.nextBytes(32), "pour"),
        ).sortedBy { it.y }
        val closing = UrneClosing(
            number = 7,
            id = "urne-7",
            secretKeys = secrets.mapValues { it.value.toHexString() },
            publicKeys = publicKeys,
            issued = mapOf("non-verifie" to 2L, "verifie" to 3L, "elu" to 0L),
            ballots = ballots,
            results = UrneTally.results(ballots.map { VoteLevel.of(it.level)!! to it.choice }),
        )
        val box = UrneInfo(7, "urne-7", "30023:${"a".repeat(64)}:exemple", listOf("pour", "contre"), 0, 100, 1, publicKeys, liveResults = true, closed = true)
        val mine = StoredToken(
            urneNumber = 7,
            urneId = "urne-7",
            level = VoteLevel.VERIFIE,
            publicKey = publicKeys.getValue("verifie"),
            y = myY,
            token = voprf.evaluate(secrets.getValue("verifie"), myY),
            choice = mineLatest,
            sendAfter = null,
            sentChoice = mineSent,
        )
        return Fixture(group, box, closing, mine, publicKeys)
    }

    @Test
    fun theBallotIsFoundWithItsChoiceAndTheBoxRecountsRight() = runTest {
        val f = fixture()
        val check = MyBallot.check(f.box, f.mine, f.closing, f.group)
        assertEquals(MyBallotFinding.COUNTED, check.finding)
        assertEquals(RecountResult.CORRECT, check.recount)
        assertFalse(check.alarming)
        // Without a token on this device, only the recount runs: the same request, nothing found.
        assertEquals(MyBallotCheck(MyBallotFinding.NOT_VOTED, RecountResult.CORRECT), MyBallot.check(f.box, null, f.closing, f.group))
    }

    @Test
    fun aLastChoiceThatNeverLeftAndAMissedAnswerAreToldApart() = runTest {
        // Chose « contre » after « pour », but « contre » was still waiting at the closing.
        val waiting = fixture(mineChoice = "pour", mineSent = "pour", mineLatest = "contre")
        assertEquals(MyBallotFinding.COUNTED_EARLIER_CHOICE, MyBallot.findingOf(waiting.mine, waiting.closing))
        // « contre » arrived, but the device never got the server's answer: it still counts as the last choice.
        val arrived = fixture(mineChoice = "contre", mineSent = "pour", mineLatest = "contre")
        assertEquals(MyBallotFinding.COUNTED, MyBallot.findingOf(arrived.mine, arrived.closing))
        // A withdrawal is found as such.
        val withdrawn = fixture(mineChoice = UrneInfo.WITHDRAWN)
        assertEquals(MyBallotFinding.WITHDRAWN, MyBallot.findingOf(withdrawn.mine, withdrawn.closing))
        // Never sent at all.
        val neverSent = fixture()
        val unsent = neverSent.mine.copy(y = Random(1).nextBytes(32), sentChoice = null, choice = "pour")
        assertEquals(MyBallotFinding.NOT_SENT, MyBallot.findingOf(unsent, neverSent.closing))
    }

    @Test
    fun aMissingOrChangedBallotAndAWrongRecountRaiseTheAlarm() = runTest {
        val f = fixture()
        // Accepted by the server, then left out of the list.
        val without = f.closing.copy(ballots = f.closing.ballots.filter { it.y != f.mine.y.toHexString() })
        assertEquals(MyBallotFinding.MISSING, MyBallot.findingOf(f.mine, without))
        // Published with another choice.
        val changed = f.closing.copy(ballots = f.closing.ballots.map { if (it.y == f.mine.y.toHexString()) it.copy(choice = "contre") else it })
        assertEquals(MyBallotFinding.ALTERED, MyBallot.findingOf(f.mine, changed))
        assertTrue(MyBallot.check(f.box, f.mine, changed, f.group).alarming, "the totals no longer match either")
        // Totals that do not match the ballots.
        val wrongTotals = f.closing.copy(results = UrneTally.results(listOf(VoteLevel.VERIFIE to "contre")))
        val recount = MyBallot.check(f.box, f.mine, wrongTotals, f.group)
        assertEquals(RecountResult.WRONG_TOTALS, recount.recount)
        assertTrue(recount.alarming)
        // The key of the device's level is the one pinned at delivery: a closing announcing another one fails.
        val otherKey = f.mine.copy(publicKey = f.publicKeys.getValue("non-verifie"))
        assertEquals(RecountResult.KEY_MISMATCH, MyBallot.check(f.box, otherKey, f.closing, f.group).recount)
    }
}
