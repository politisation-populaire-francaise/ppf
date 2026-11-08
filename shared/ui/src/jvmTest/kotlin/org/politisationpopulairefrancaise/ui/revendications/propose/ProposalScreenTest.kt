package org.politisationpopulairefrancaise.ui.revendications.propose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.RevendicationStatus
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.publication.PublicationPort
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RejectionReason
import org.politisationpopulairefrancaise.core.revendications.checks.DemandText
import org.politisationpopulairefrancaise.core.revendications.checks.IssueKind
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.ui.account.LocalCanPublish
import org.politisationpopulairefrancaise.ui.account.PUBLISH_BLOCKED_TAG
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The guided form of a demand: live duplicates, rules that block, the charter, and the three ways a sending ends. */
@OptIn(ExperimentalTestApi::class)
class ProposalScreenTest {
    private val cantine = DemandText(
        address = "30023:${"a".repeat(64)}:cantines-tarif-social",
        title = "Un tarif de cantine à un euro pour les familles aux revenus modestes dans toutes les communes",
        summary = "Généraliser le tarif de cantine scolaire à un euro.",
        territory = "France",
        status = RevendicationStatus.NOUVELLE,
    )
    private val existing = listOf(cantine)
    private val signer = LocalEventSigner(IdentityKeys.generate())

    private class RecordingPort(private val answer: (SignedEvent) -> PublicationResult) : PublicationPort {
        val received = mutableListOf<SignedEvent>()
        override suspend fun publish(event: SignedEvent): PublicationResult {
            received += event
            return answer(event)
        }
    }

    private fun ComposeUiTest.show(
        composer: ProposalComposer,
        width: Int = 380,
        height: Int = 740,
        withSigner: Boolean = true,
        onOpenDemand: (String) -> Unit = {},
        onCreateAccount: () -> Unit = {},
        onClose: () -> Unit = {},
        canPublish: Boolean = true,
    ) {
        setContent {
            PpfTheme(darkTheme = false) {
                CompositionLocalProvider(LocalCanPublish provides canPublish) {
                    Box(Modifier.requiredSize(width.dp, height.dp)) {
                        ProposalScreen(
                            composer = composer,
                            existing = existing,
                            signer = if (withSigner) signer else null,
                            windowClass = WindowClass.of(widthDp = width.toFloat(), heightDp = height.toFloat()),
                            onClose = onClose,
                            onOpenDemand = onOpenDemand,
                            onCreateAccount = onCreateAccount,
                            onSent = {},
                        )
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.fillValidDraft(title: String = "Un médecin de garde joignable le soir dans chaque canton") {
        onNodeWithTag(ProposalTags.TITLE).performTextInput(title)
        onNodeWithTag(ProposalTags.WHY).performScrollTo().performTextInput("Le soir, il ne reste que les urgences.")
        onNodeWithTag(ProposalTags.ASKS).performScrollTo().performTextInput("Une permanence de garde par canton.")
        onNodeWithTag(ProposalTags.THEME + "sante").performScrollTo().performClick()
        onNodeWithTag(ProposalTags.CHARTER).performScrollTo().performClick()
        waitForIdle()
    }

    @Test
    fun aDemandThatSaysTheSameThingAppearsUnderTheDemandAsItIsTyped() = runComposeUiTest {
        var opened: String? = null
        show(ProposalComposer(LocalEventStore(JvmDatabase.inMemory()), remote = null), onOpenDemand = { opened = it })
        onNodeWithTag(ProposalTags.SIMILAR).assertDoesNotExist()
        onNodeWithTag(ProposalTags.TITLE).performTextInput("Cantine à un euro pour les familles modestes")
        waitForIdle()
        onNodeWithTag(ProposalTags.SIMILAR).assertExists()
        onNodeWithTag(IssueTags.ISSUE + "duplicate").assertExists()
        onNodeWithTag(IssueTags.RELATED + cantine.address).performScrollTo().performClick()
        assertEquals(cantine.address, opened)
    }

    @Test
    fun theButtonWaitsForTheRulesAndTheCharter() = runComposeUiTest {
        val composer = ProposalComposer(LocalEventStore(JvmDatabase.inMemory()), remote = null)
        show(composer)
        onNodeWithTag(ProposalTags.TITLE).performTextInput("Un médecin de garde joignable le soir dans chaque canton")
        waitForIdle()
        onNodeWithTag(ProposalTags.SEND).performScrollTo().assertIsNotEnabled()
        onNodeWithText("Corrigez d’abord les points marqués « À corriger ».").assertExists()
        onNodeWithTag(IssueTags.ISSUE + "missing_theme").performScrollTo().assertExists()

        onNodeWithTag(ProposalTags.THEME + "sante").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ProposalTags.SEND).performScrollTo().assertIsNotEnabled()
        onNodeWithText("Cochez l’acceptation de la charte pour envoyer.").assertExists()

        onNodeWithTag(ProposalTags.CHARTER).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ProposalTags.SEND).performScrollTo().assertIsEnabled()

        // Personal data blocks again, whatever the rest.
        onNodeWithTag(ProposalTags.WHY).performScrollTo().performTextReplacement("Appelez-moi au 06 12 34 56 78.")
        waitForIdle()
        onNodeWithTag(IssueTags.ISSUE + "personal_data").performScrollTo().assertExists()
        onNodeWithTag(ProposalTags.SEND).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun anAccountDeclaredCompromisedSendsNothingAndIsToldWhy() = runComposeUiTest {
        // S-146: the server refuses it anyway (403 account_compromised); the form says so before.
        show(ProposalComposer(LocalEventStore(JvmDatabase.inMemory()), remote = null), canPublish = false)
        fillValidDraft()
        onNodeWithTag(PUBLISH_BLOCKED_TAG).performScrollTo().assertExists()
        onNodeWithTag(ProposalTags.SEND).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun aDemandHeldForReviewIsSignedStoredAndConfirmed() = runComposeUiTest {
        val store = LocalEventStore(JvmDatabase.inMemory())
        val port = RecordingPort { PublicationResult.Accepted(it.id, heldForReview = true) }
        val composer = ProposalComposer(store, port)
        show(composer)
        fillValidDraft()
        onNodeWithTag(ProposalTags.SEND).performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { composer.outcome != null }
        assertEquals(SendOutcome.SENT_FOR_REVIEW, composer.outcome)
        onNodeWithTag(ProposalTags.RESULT).assertExists()
        onNodeWithText("Revendication envoyée en relecture").assertExists()

        val event = port.received.single()
        assertTrue(event.verify())
        assertEquals(signer.publicKey.hex, event.pubkey)
        val article = assertNotNull(Article.from(event))
        assertEquals("Un médecin de garde joignable le soir dans chaque canton", article.title)
        assertEquals(listOf("sante"), article.topics)
        assertEquals(RevendicationStatus.NOUVELLE, article.status)
        assertNotNull(store.byTag(listOf(Article.KIND), Article.TAG_TYPE, "revendication").singleOrNull { it.id == event.id })
        assertTrue(store.pending().isEmpty(), "accepted by the server: no longer waiting to leave")
        assertEquals("", composer.draft.title, "the form is emptied once the demand is safe")
    }

    @Test
    fun withoutServerTheDemandWaitsOnTheDevice() = runComposeUiTest {
        val store = LocalEventStore(JvmDatabase.inMemory())
        val composer = ProposalComposer(store, remote = null)
        show(composer)
        fillValidDraft()
        onNodeWithTag(ProposalTags.SEND).performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { composer.outcome != null }
        assertEquals(SendOutcome.WAITING_FOR_SERVER, composer.outcome)
        assertEquals(1, store.pending().size, "it leaves with the next synchronization")
        onNodeWithText("Revendication gardée sur cet appareil").assertExists()
    }

    @Test
    fun aRefusalKeepsTheDraftToCorrectIt() = runComposeUiTest {
        val store = LocalEventStore(JvmDatabase.inMemory())
        val composer = ProposalComposer(store, RecordingPort { PublicationResult.Rejected(it.id, RejectionReason.REFUSED) })
        show(composer)
        fillValidDraft()
        onNodeWithTag(ProposalTags.SEND).performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { composer.outcome != null }
        assertEquals(SendOutcome.REFUSED, composer.outcome)
        assertTrue(store.pending().isEmpty() && store.recent().isEmpty(), "nothing the server refuses is kept")
        onNodeWithTag(ProposalTags.RESULT_AGAIN).performClick()
        waitForIdle()
        onNodeWithTag(ProposalTags.TITLE).assertExists()
        assertEquals("Un médecin de garde joignable le soir dans chaque canton", composer.draft.title)
    }

    @Test
    fun withoutAccountTheFormInvitesToCreateOne() = runComposeUiTest {
        var asked = false
        show(ProposalComposer(LocalEventStore(JvmDatabase.inMemory()), remote = null), withSigner = false, onCreateAccount = { asked = true })
        onNodeWithTag(ProposalTags.SEND).assertDoesNotExist()
        onNodeWithTag(ProposalTags.CREATE_ACCOUNT).performScrollTo().performClick()
        assertTrue(asked)
    }

    @Test
    fun onWideWindowsTheChecksSitBesideTheForm() = runComposeUiTest {
        show(ProposalComposer(LocalEventStore(JvmDatabase.inMemory()), remote = null), width = 1000, height = 740)
        onNodeWithTag(ProposalTags.TITLE).performTextInput("Cantine à un euro pour les familles modestes")
        waitForIdle()
        onNodeWithTag(ProposalTags.CHECKS).assertExists()
        onNodeWithTag(ProposalTags.SIMILAR).assertDoesNotExist()
        onNodeWithTag(IssueTags.ISSUE + IssueKind.DUPLICATE.name.lowercase()).assertExists()
    }
}
