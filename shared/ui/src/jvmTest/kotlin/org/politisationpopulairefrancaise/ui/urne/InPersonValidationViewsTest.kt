package org.politisationpopulairefrancaise.ui.urne

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
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
import org.politisationpopulairefrancaise.core.urne.ValidationAnswer
import org.politisationpopulairefrancaise.core.urne.ValidationProof
import org.politisationpopulairefrancaise.core.urne.VoterStatus
import org.politisationpopulairefrancaise.ui.components.qr.QrTags
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The in-person validation (D-112, 4.3) in the application: the citizen's code, the validator's reading. */
@OptIn(ExperimentalTestApi::class)
class InPersonValidationViewsTest {
    /** A server that answers the validations with [answer] and counts them. */
    private class ValidatingServer(private val answer: UrneAnswer<ValidationAnswer>) : UrneRemote {
        val sent = mutableListOf<Pair<String, SignedEvent>>()
        override suspend fun list(after: Long, limit: Int): UrneAnswer<UrneList> = UrneAnswer.Ok(UrneList(emptyList(), Difficulties(0, 0, 0)))
        override suspend fun register(signer: EventSigner, difficulty: Int): UrneAnswer<VoterStatus> = UrneAnswer.Failure(null, null)
        override suspend fun status(signer: EventSigner): UrneAnswer<VoterStatus> = UrneAnswer.Failure(null, null)
        override suspend fun tokens(signer: EventSigner, request: TokenRequest, difficulty: Int): UrneAnswer<TokenResponse> = UrneAnswer.Failure(null, null)
        override suspend fun ballot(ballot: Ballot): UrneAnswer<BallotReceipt> = UrneAnswer.Failure(null, null)
        override suspend fun journal(number: Long): UrneAnswer<UrneJournal> = UrneAnswer.Failure(null, null)
        override suspend fun closing(number: Long): UrneAnswer<UrneClosing> = UrneAnswer.Failure(null, null)
        override suspend fun validate(session: EventSigner, proof: SignedEvent): UrneAnswer<ValidationAnswer> {
            sent += session.publicKey.hex to proof
            return answer
        }
    }

    private fun voting(remote: UrneRemote) = VotingState(remote, UrneTokenStore(JvmDatabase.inMemory()), CoroutineScope(Dispatchers.Unconfined))

    private val citizen = LocalEventSigner(IdentityKeys.generate())
    private val proof = runBlocking { ValidationProof.sign(citizen, 1_794_000_000).toJson() }

    @Test
    fun aCitizenNotVerifiedShowsTheCodeOfTheAccount() = runComposeUiTest {
        val voting = voting(ValidatingServer(UrneAnswer.Failure(null, null)))
        voting.signerProvider = { citizen }
        voting.status = VoterStatus(level = "non-verifie", levelWeek = "2026-11-02", pending = emptyList())
        setContent { PpfTheme { CompositionLocalProvider(LocalVoting provides voting) { VoterLevelCard() } } }
        onNodeWithTag(VoterLevelTags.SHOW_CODE).performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(QrTags.CODE).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Il change chaque minute", substring = true).assertExists()
    }

    @Test
    fun aVerifiedAccountHasNoCodeToShow() = runComposeUiTest {
        val voting = voting(ValidatingServer(UrneAnswer.Failure(null, null)))
        voting.status = VoterStatus(level = "verifie", levelWeek = "2026-11-02", pending = emptyList())
        setContent { PpfTheme { CompositionLocalProvider(LocalVoting provides voting) { VoterLevelCard() } } }
        onNodeWithTag(VoterLevelTags.SHOW_CODE).assertDoesNotExist()
        onNodeWithTag(VoterLevelTags.VALIDATE).assertExists()
    }

    @Test
    fun aValidatorSendsEachCodeOnceWithTheSessionKey() = runComposeUiTest {
        val server = ValidatingServer(UrneAnswer.Ok(ValidationAnswer(ValidationAnswer.PENDING, "2026-11-09")))
        val voting = voting(server)
        setContent {
            PpfTheme {
                // The camera reads the same code again and again: here, twice.
                ValidatorDialog(voting, onClose = {}, scanner = { onRead -> LaunchedEffect(Unit) { onRead(proof); onRead(proof) } })
            }
        }
        onNodeWithTag(ValidationTags.SESSION_KEY).assertTextContains("npub1", substring = true)
        onNodeWithTag(ValidationTags.SCAN).performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(ValidationTags.RESULT).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(ValidationTags.RESULT).assertTextContains("lundi 9 novembre 2026", substring = true)
        assertEquals(1, server.sent.size)
        assertEquals(voting.validatorKeys!!.publicKey.hex, server.sent.single().first, "signed with the session key, never the account's")
        assertEquals(proof, server.sent.single().second.toJson())
    }

    @Test
    fun aRefusalSaysWhatToDo() = runComposeUiTest {
        val voting = voting(ValidatingServer(UrneAnswer.Failure(401, "no_open_session")))
        setContent { PpfTheme { ValidatorDialog(voting, onClose = {}, scanner = { onRead -> LaunchedEffect(Unit) { onRead(proof) } }) } }
        onNodeWithTag(ValidationTags.SCAN).performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(ValidationTags.RESULT).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(ValidationTags.RESULT).assertTextContains("Aucune séance ouverte", substring = true)
        assertTrue(voting.validatorKeys != null)
    }
}
