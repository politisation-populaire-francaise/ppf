package org.politisationpopulairefrancaise.ui.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.identity.RecoveryPhrase
import org.politisationpopulairefrancaise.core.identity.StoredIdentity
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.agir.security.AppLockSetupTags
import org.politisationpopulairefrancaise.ui.agir.security.SecurityTags
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The account journeys of "Mon espace", on the desktop layout and on the phone layout. */
@OptIn(ExperimentalTestApi::class)
class MySpaceScreenTest {

    private val nip06Phrase = "leader monkey parrot ring guide accident before fence cannon height naive bean"
    private val nip06Npub = "npub1zutzeysacnf9rru6zqwmxd54mud0k44tst6l70ja5mhv8jjumytsd2x7nu"

    private fun ComposeUiTest.openMySpace(store: InMemoryIdentityStore, width: Int = 1000, height: Int = 740) {
        setContent { Box(Modifier.requiredSize(width.dp, height.dp)) { PpfApp(AppDependencies(store)) } }
        onNodeWithTag(ShellTags.navItem(Space.MON_ESPACE)).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.textOf(tag: String): String =
        onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.joinToString { it.text }

    private fun ComposeUiTest.has(tag: String): Boolean = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /**
     * Right after an account arrives on the device, « Protéger PPF sur cet appareil » comes first (M33, D-082),
     * « Choisir un code » preselected; the journeys of this class skip it, after its warning.
     */
    private fun ComposeUiTest.passLockStep() {
        waitUntil(timeoutMillis = 10_000) { has(AppLockSetupTags.STEP) }
        onNodeWithTag(AppLockSetupTags.CHOOSE).assertIsSelected()
        onNodeWithTag(AccountTags.PUBLIC_KEY).assertDoesNotExist()
        onNodeWithTag(AppLockSetupTags.SKIP).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(AppLockSetupTags.CONTINUE).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(SecurityTags.DIALOG_CONFIRM).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) { !has(AppLockSetupTags.STEP) }
    }

    @Test
    fun creatingAnAccountShowsTheWordsVerifiesThemAndStoresTheKeys() = runComposeUiTest {
        val store = InMemoryIdentityStore()
        openMySpace(store)
        onNodeWithText("Un compte sans e-mail ni téléphone").assertExists()
        onNodeWithTag(AccountTags.CREATE).performClick()
        waitForIdle()

        // Twelve numbered words are shown; read them back as the user would copy them.
        val words = (0 until 12).map { index -> textOf(AccountTags.PHRASE_WORD + index).substringAfter(". ") }
        // Twelve non-empty words; a random phrase may repeat a word, so they need not all differ.
        assertEquals(12, words.size)
        assertTrue(words.all { it.isNotBlank() && ' ' !in it }, words.toString())
        onNodeWithTag(AccountTags.PHRASE_NOTED).performClick()
        waitForIdle()

        // A wrong word is refused.
        val asked = Regex("\\d+").findAll(textOf(AccountTags.VERIFY_INSTRUCTION)).map { it.value.toInt() }.toList()
        assertEquals(3, asked.size)
        onNodeWithTag(AccountTags.VERIFY_FIELD + 0).performTextInput("xyzzy")
        onNodeWithTag(AccountTags.VERIFY_SUBMIT).performClick()
        waitForIdle()
        onNodeWithTag(AccountTags.VERIFY_ERROR).assertExists()
        assertNull(store.snapshot)

        // Go back to the phrase and type the right words (upper case and spaces are tolerated).
        onNodeWithText("Revoir la phrase").performClick()
        waitForIdle()
        onNodeWithTag(AccountTags.PHRASE_NOTED).performClick()
        waitForIdle()
        val askedAgain = Regex("\\d+").findAll(textOf(AccountTags.VERIFY_INSTRUCTION)).map { it.value.toInt() }.toList()
        askedAgain.forEachIndexed { field, position ->
            onNodeWithTag(AccountTags.VERIFY_FIELD + field).performTextInput(" " + words[position - 1].uppercase() + " ")
        }
        onNodeWithTag(AccountTags.VERIFY_SUBMIT).performClick()
        waitForIdle()
        passLockStep()

        val stored = assertNotNull(store.snapshot)
        // Checked, the phrase is erased from the device by default (S-146, D-134).
        assertNull(stored.recoveryWords)
        assertTrue(stored.phraseVerified)
        val expectedNpub = RecoveryPhrase.parse(words.joinToString(" ")).deriveKeys().publicKey.npub
        onNodeWithTag(AccountTags.PUBLIC_KEY).assertTextContains(expectedNpub)
        assertEquals(expectedNpub, stored.keys().publicKey.npub)
    }

    @Test
    fun restoringFromAPhraseGivesTheExpectedIdentity() = runComposeUiTest {
        val store = InMemoryIdentityStore()
        openMySpace(store, width = 360, height = 700)
        onNodeWithTag(AccountTags.RESTORE).performClick()
        waitForIdle()
        onNodeWithTag(AccountTags.RESTORE_INPUT).performTextInput(nip06Phrase)
        onNodeWithTag(AccountTags.RESTORE_SUBMIT).performClick()
        waitForIdle()
        passLockStep()
        onNodeWithTag(AccountTags.PUBLIC_KEY).assertTextContains(nip06Npub)
        // Typed in full, the phrase counts as checked and is not kept on the device.
        assertNull(store.snapshot?.recoveryWords)
        assertTrue(store.snapshot?.phraseVerified == true)
    }

    @Test
    fun restoringFromASecretKeyWorksWithoutPhrase() = runComposeUiTest {
        val store = InMemoryIdentityStore()
        openMySpace(store)
        onNodeWithTag(AccountTags.RESTORE).performClick()
        waitForIdle()
        onNodeWithTag(AccountTags.RESTORE_INPUT).performTextInput("nsec10allq0gjx7fddtzef0ax00mdps9t2kmtrldkyjfs8l5xruwvh2dq0lhhkp")
        onNodeWithTag(AccountTags.RESTORE_SUBMIT).performClick()
        waitForIdle()
        passLockStep()
        onNodeWithTag(AccountTags.PUBLIC_KEY).assertTextContains(nip06Npub)
        onNodeWithTag(AccountTags.SHOW_PHRASE).assertDoesNotExist()
        onNodeWithText("Ce compte a été importé par sa clé secrète : il n’a pas de phrase de récupération dans l’application.").assertExists()
    }

    @Test
    fun invalidPhrasesAreExplained() = runComposeUiTest {
        openMySpace(InMemoryIdentityStore())
        onNodeWithTag(AccountTags.RESTORE).performClick()
        waitForIdle()
        onNodeWithTag(AccountTags.RESTORE_INPUT).performTextInput("leader monkey parrot ring guide accident before fence cannon height naive xyzzy")
        onNodeWithTag(AccountTags.RESTORE_SUBMIT).performClick()
        waitForIdle()
        onNodeWithTag(AccountTags.RESTORE_ERROR).assertTextContains("xyzzy", substring = true)
        onNodeWithTag(AccountTags.PUBLIC_KEY).assertDoesNotExist()
    }

    @Test
    fun existingAccountIsLoadedRevealedAfterWarningAndErasedOnSignOut() = runComposeUiTest {
        val phrase = RecoveryPhrase.parse(nip06Phrase)
        val store = InMemoryIdentityStore(StoredIdentity.of(phrase.deriveKeys(), phrase))
        openMySpace(store)
        onNodeWithTag(AccountTags.PUBLIC_KEY).assertTextContains(nip06Npub)

        // The secret key only appears after the warning is confirmed.
        onNodeWithTag(AccountTags.SECRET_KEY).assertDoesNotExist()
        onNodeWithTag(AccountTags.SHOW_SECRET).performClick()
        waitForIdle()
        onNodeWithText("Avant d’afficher").assertIsDisplayed()
        onNodeWithTag(AccountTags.DIALOG_CONFIRM).performClick()
        waitForIdle()
        onNodeWithTag(AccountTags.SECRET_KEY).assertTextContains("nsec10allq0gjx7fddtzef0ax00mdps9t2kmtrldkyjfs8l5xruwvh2dq0lhhkp")
        onNodeWithText("Masquer").performClick()
        waitForIdle()
        onNodeWithTag(AccountTags.SECRET_KEY).assertDoesNotExist()

        // The phrase too (still on the device: this account has not checked it yet).
        onNodeWithTag(AccountTags.SHOW_PHRASE).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(AccountTags.DIALOG_CONFIRM).performClick()
        waitForIdle()
        assertTrue(textOf(AccountTags.PHRASE_WORD + 0).endsWith("leader"))
        onNodeWithText("Masquer").performScrollTo().performClick()
        waitForIdle()

        // Signing out (at the bottom of the page) asks for confirmation, then erases the keys from the device.
        onNodeWithTag(AccountTags.SIGN_OUT).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Annuler").performClick()
        waitForIdle()
        onNodeWithTag(AccountTags.PUBLIC_KEY).assertExists()
        onNodeWithTag(AccountTags.SIGN_OUT).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(AccountTags.DIALOG_CONFIRM).performClick()
        waitForIdle()
        onNodeWithTag(AccountTags.CREATE).assertExists()
        assertNull(store.snapshot)
    }

    @Test
    fun storedIdentityRoundTripsThroughBytes() {
        val phrase = RecoveryPhrase.generate()
        val keys = phrase.deriveKeys()
        val decoded = StoredIdentity.decode(StoredIdentity.of(keys, phrase).encode())
        assertEquals(keys.publicKey, decoded.keys().publicKey)
        assertEquals(phrase, decoded.recoveryPhrase())
        val keyOnly = StoredIdentity.decode(StoredIdentity.of(IdentityKeys.generate(), null).encode())
        assertNull(keyOnly.recoveryPhrase())
    }
}
