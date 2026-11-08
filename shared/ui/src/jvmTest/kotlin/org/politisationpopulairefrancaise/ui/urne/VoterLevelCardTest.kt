package org.politisationpopulairefrancaise.ui.urne

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.urne.Ballot
import org.politisationpopulairefrancaise.core.urne.BallotReceipt
import org.politisationpopulairefrancaise.core.urne.Difficulties
import org.politisationpopulairefrancaise.core.urne.TokenRequest
import org.politisationpopulairefrancaise.core.urne.TokenResponse
import org.politisationpopulairefrancaise.core.urne.UrneAnswer
import org.politisationpopulairefrancaise.core.urne.UrneClosing
import org.politisationpopulairefrancaise.core.urne.UrneJournal
import org.politisationpopulairefrancaise.core.urne.UrneList
import org.politisationpopulairefrancaise.core.urne.UrneRemote
import org.politisationpopulairefrancaise.core.urne.UrneTokenStore
import org.politisationpopulairefrancaise.core.urne.VoterStatus
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import kotlin.test.Test

/** « Mon niveau de votant » in "Mon espace" (TE-08). */
@OptIn(ExperimentalTestApi::class)
class VoterLevelCardTest {
    private object NoServer : UrneRemote {
        override suspend fun list(after: Long, limit: Int): UrneAnswer<UrneList> = UrneAnswer.Ok(UrneList(emptyList(), Difficulties(0, 0, 0)))
        override suspend fun register(signer: EventSigner, difficulty: Int): UrneAnswer<VoterStatus> = UrneAnswer.Failure(null, null)
        override suspend fun status(signer: EventSigner): UrneAnswer<VoterStatus> = UrneAnswer.Failure(null, null)
        override suspend fun tokens(signer: EventSigner, request: TokenRequest, difficulty: Int): UrneAnswer<TokenResponse> = UrneAnswer.Failure(null, null)
        override suspend fun ballot(ballot: Ballot): UrneAnswer<BallotReceipt> = UrneAnswer.Failure(null, null)
        override suspend fun journal(number: Long): UrneAnswer<UrneJournal> = UrneAnswer.Failure(null, null)
        override suspend fun closing(number: Long): UrneAnswer<UrneClosing> = UrneAnswer.Failure(null, null)
    }

    private fun voting(remote: UrneRemote? = NoServer) = VotingState(remote, UrneTokenStore(JvmDatabase.inMemory()), CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun beforeTheFirstSynchronisationTheCardWaits() = runComposeUiTest {
        val voting = voting()
        setContent { PpfTheme { CompositionLocalProvider(LocalVoting provides voting) { VoterLevelCard() } } }
        onNodeWithText("première synchronisation", substring = true).assertExists()
    }

    @Test
    fun aComingLevelSaysFromWhichMondayAndTheOneBefore() = runComposeUiTest {
        val voting = voting()
        voting.status = VoterStatus(level = "verifie", levelWeek = "2099-01-05", pending = emptyList(), previousLevel = "non-verifie")
        setContent { PpfTheme { CompositionLocalProvider(LocalVoting provides voting) { VoterLevelCard() } } }
        onNodeWithTag(VoterLevelTags.LEVEL).assertTextContains("Compte vérifié en personne")
        onNodeWithText("à partir du lundi 5 janvier 2099", substring = true).assertExists()
        onNodeWithText("Compte non vérifié", substring = true).assertExists()
    }

    @Test
    fun aCertificationUnderWayIsSaid() = runComposeUiTest {
        val voting = voting()
        voting.status = VoterStatus(level = "non-verifie", levelWeek = "2026-11-02", pending = emptyList(), certification = "pending", mandate = "rne1:" + "0".repeat(32))
        setContent { PpfTheme { CompositionLocalProvider(LocalVoting provides voting) { VoterLevelCard() } } }
        onNodeWithText("depuis le lundi 2 novembre 2026", substring = true).assertExists()
        onNodeWithTag(VoterLevelTags.NOTE).assertTextContains("Certification d’élu demandée", substring = true)
        onNodeWithText("présentez-vous à un validateur", substring = true).assertExists()
    }

    @Test
    fun withoutVotingTheCardIsAbsent() = runComposeUiTest {
        val voting = voting(remote = null)
        setContent { PpfTheme { CompositionLocalProvider(LocalVoting provides voting) { VoterLevelCard() } } }
        onNodeWithTag(VoterLevelTags.CARD).assertDoesNotExist()
    }
}
