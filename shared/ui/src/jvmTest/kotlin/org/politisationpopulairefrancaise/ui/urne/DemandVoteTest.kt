package org.politisationpopulairefrancaise.ui.urne

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.urne.Ballot
import org.politisationpopulairefrancaise.core.urne.BallotReceipt
import org.politisationpopulairefrancaise.core.urne.ChoiceCounts
import org.politisationpopulairefrancaise.core.urne.Difficulties
import org.politisationpopulairefrancaise.core.urne.TokenRequest
import org.politisationpopulairefrancaise.core.urne.TokenResponse
import org.politisationpopulairefrancaise.core.urne.UrneAnswer
import org.politisationpopulairefrancaise.core.urne.UrneClosing
import org.politisationpopulairefrancaise.core.urne.UrneInfo
import org.politisationpopulairefrancaise.core.urne.UrneJournal
import org.politisationpopulairefrancaise.core.urne.UrneList
import org.politisationpopulairefrancaise.core.urne.UrneRemote
import org.politisationpopulairefrancaise.core.urne.UrneResults
import org.politisationpopulairefrancaise.core.urne.UrneTokenStore
import org.politisationpopulairefrancaise.core.urne.VoterStatus
import org.politisationpopulairefrancaise.core.voir.FrenchNumbers
import org.politisationpopulairefrancaise.core.territoires.InMemoryTerritoiresStorage
import org.politisationpopulairefrancaise.core.territoires.Territoires
import org.politisationpopulairefrancaise.ui.account.LocalCanPublish
import org.politisationpopulairefrancaise.ui.territoires.bundledNationalTerritoires
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The vote on a demand (DE-01, D-110, step 4): what the card says before and around the vote. */
@OptIn(ExperimentalTestApi::class)
class DemandVoteTest {
    private val subject = "30023:${"a".repeat(64)}:hopital"
    private val counts = { pour: Long, contre: Long -> ChoiceCounts(mapOf("contre" to contre, "pour" to pour), pour + contre) }
    private val box = UrneInfo(
        number = 1, id = "decider:revendication:$subject", subject = subject, choices = listOf("pour", "contre", "blanc"),
        opensAt = 1_793_793_600, closesAt = null, seedVersion = 1, keys = emptyMap(), liveResults = true, closed = false,
        results = UrneResults(verified = counts(2, 1), notVerified = counts(10, 2), citizens = counts(1_234, 3), electedOfficials = counts(1, 0)),
    )

    /** The public list only: the device asks it without the account. */
    private class ListOnly(private val boxes: List<UrneInfo>) : UrneRemote {
        var listed = 0
        override suspend fun list(after: Long, limit: Int): UrneAnswer<UrneList> {
            listed++
            return UrneAnswer.Ok(UrneList(boxes.filter { it.number > after }, Difficulties(0, 0, 0)))
        }
        override suspend fun register(signer: EventSigner, difficulty: Int): UrneAnswer<VoterStatus> = UrneAnswer.Failure(null, null)
        override suspend fun status(signer: EventSigner): UrneAnswer<VoterStatus> = UrneAnswer.Failure(null, null)
        override suspend fun tokens(signer: EventSigner, request: TokenRequest, difficulty: Int): UrneAnswer<TokenResponse> = UrneAnswer.Failure(null, null)
        override suspend fun ballot(ballot: Ballot): UrneAnswer<BallotReceipt> = UrneAnswer.Failure(null, null)
        override suspend fun journal(number: Long): UrneAnswer<UrneJournal> = UrneAnswer.Failure(null, null)
        override suspend fun closing(number: Long): UrneAnswer<UrneClosing> = UrneAnswer.Failure(null, null)
    }

    private fun voting(remote: UrneRemote) =
        VotingState(remote, UrneTokenStore(JvmDatabase.inMemory()), CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun withoutServerTheVoteSaysWhyItIsClosed() = runComposeUiTest {
        setContent { PpfTheme { DemandVoteRow(subject, inDebate = true) } }
        onNodeWithTag(DemandVoteTags.FOR).assertIsNotEnabled()
        onNodeWithTag(DemandVoteTags.STATUS).assertTextContains("connexion au serveur", substring = true)
    }

    @Test
    fun theResultsComeWithTheListAndAnAccountIsAskedFor() = runComposeUiTest {
        val remote = ListOnly(listOf(box))
        val voting = voting(remote)
        runBlocking { voting.refreshBoxes() }
        setContent { PpfTheme { CompositionLocalProvider(LocalVoting provides voting) { DemandVoteRow(subject, inDebate = true) } } }
        onNodeWithTag(DemandVoteTags.RESULTS).assertTextContains("1${FrenchNumbers.GROUP_SEPARATOR}234 pour, 3 contre, 0 blanc", substring = true)
        onNodeWithTag(DemandVoteTags.FOR).assertIsNotEnabled()
        onNodeWithTag(DemandVoteTags.STATUS).assertTextContains("Créez un compte", substring = true)
        // The whole list in one request, whatever the demand shown.
        assertEquals(1, remote.listed)
    }

    @Test
    fun aCompromisedAccountIsToldThatItsTokensAlreadyReceivedStayUsable() = runComposeUiTest {
        val voting = voting(ListOnly(listOf(box)))
        runBlocking { voting.refreshBoxes() }
        var compromised by mutableStateOf(false)
        setContent {
            PpfTheme {
                CompositionLocalProvider(LocalVoting provides voting, LocalCanPublish provides !compromised) { DemandVoteRow(subject, inDebate = true) }
            }
        }
        onNodeWithTag(DemandVoteTags.COMPROMISED).assertDoesNotExist()
        compromised = true
        onNodeWithTag(DemandVoteTags.COMPROMISED).assertTextContains("restent utilisables", substring = true)
    }

    @Test
    fun theSupportsAreShownBesideTheThresholdsOfTheTerritory() = runComposeUiTest {
        val voting = voting(ListOnly(listOf(box)))
        voting.territoires = Territoires(storage = InMemoryTerritoiresStorage(), server = null, bundled = ::bundledNationalTerritoires)
        runBlocking { voting.refreshBoxes() }
        setContent { PpfTheme { CompositionLocalProvider(LocalVoting provides voting) { DemandVoteRow(subject, inDebate = true) } } }
        // A national demand (no territory code): more than 100 000 to carry, 500 000 for the petition (D-115).
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(DemandVoteTags.SUPPORTS).fetchSemanticsNodes().isNotEmpty() }
        val group = FrenchNumbers.GROUP_SEPARATOR
        onNodeWithTag(DemandVoteTags.SUPPORTS).assertTextContains("1${group}234", substring = true)
        onNodeWithTag(DemandVoteTags.SUPPORTS).assertTextContains("100${group}000", substring = true)
        onNodeWithTag(DemandVoteTags.STAGE).assertDoesNotExist()
    }

    @Test
    fun aDemandWithoutBoxSaysWhenItsVoteOpens() = runComposeUiTest {
        val voting = voting(ListOnly(emptyList()))
        runBlocking { voting.refreshBoxes() }
        setContent {
            PpfTheme {
                CompositionLocalProvider(LocalVoting provides voting) {
                    DemandVoteRow(subject, inDebate = false)
                }
            }
        }
        onNodeWithTag(DemandVoteTags.STATUS).assertTextContains("met la revendication en débat", substring = true)
    }
}
