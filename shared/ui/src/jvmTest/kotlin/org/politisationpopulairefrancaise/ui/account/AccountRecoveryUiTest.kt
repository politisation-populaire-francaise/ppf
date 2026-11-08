package org.politisationpopulairefrancaise.ui.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.identity.AccountRecovery
import org.politisationpopulairefrancaise.core.identity.AccountRecoveryClient
import org.politisationpopulairefrancaise.core.identity.AccountStatusResult
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.identity.RecoveryPhrase
import org.politisationpopulairefrancaise.core.identity.StoredIdentity
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.notifications.NotificationCategory
import org.politisationpopulairefrancaise.core.notifications.NotificationSettings
import org.politisationpopulairefrancaise.core.publication.InMemoryPublicationPort
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.agir.security.AppLockSetupTags
import org.politisationpopulairefrancaise.ui.agir.security.SecurityTags
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * "Déclarer mon compte compromis" in Mon espace (S-146, D-134): the phrase erased by default after
 * its check, kept only after a warning, the reminder of a postponed check, the commitment to the
 * recovery key published once, the declaration from the phrase, and the banner of a compromised
 * account.
 */
@OptIn(ExperimentalTestApi::class)
class AccountRecoveryUiTest {

    private val nip06 = "leader monkey parrot ring guide accident before fence cannon height naive bean"
    private val phrase = RecoveryPhrase.parse(nip06)
    private val account = phrase.deriveKeys()

    private fun ComposeUiTest.openMySpace(store: InMemoryIdentityStore, server: InMemoryPublicationPort?, width: Int = 1000, height: Int = 740) {
        setContent { Box(Modifier.requiredSize(width.dp, height.dp)) { PpfApp(AppDependencies(store, remote = server)) } }
        onNodeWithTag(ShellTags.navItem(Space.MON_ESPACE)).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.has(tag: String): Boolean = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.sees(text: String): Boolean = onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.textOf(tag: String): String =
        onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.joinToString { it.text }

    private fun ComposeUiTest.click(tag: String) {
        onNodeWithTag(tag).performScrollTo().performClick()
        waitForIdle()
    }

    /** A button of a dialog, outside the scrolled page. */
    private fun ComposeUiTest.tap(tag: String) {
        onNodeWithTag(tag).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.awaitTag(tag: String) = waitUntil(timeoutMillis = 10_000) { has(tag) }

    private fun commitments(server: InMemoryPublicationPort) = server.events.filter { it.kind == AccountRecovery.COMMITMENT_KIND }

    /** Creates an account through the screens and returns its twelve words. */
    private fun ComposeUiTest.createAccount(keep: Boolean = false): List<String> {
        click(AccountTags.CREATE)
        // The signup insists on the sheet of paper (D-134).
        assertTrue(sees("ne peut pas être récupéré"))
        val words = (0 until 12).map { textOf(AccountTags.PHRASE_WORD + it).substringAfter(". ") }
        click(AccountTags.PHRASE_NOTED)
        if (keep) {
            click(RecoveryTags.KEEP_PHRASE)
            tap(RecoveryTags.KEEP_CONFIRM)
            assertTrue(sees("La phrase restera sur ce téléphone"))
        }
        val asked = Regex("\\d+").findAll(textOf(AccountTags.VERIFY_INSTRUCTION)).map { it.value.toInt() }.toList()
        asked.forEachIndexed { field, position -> onNodeWithTag(AccountTags.VERIFY_FIELD + field).performTextInput(words[position - 1]) }
        click(AccountTags.VERIFY_SUBMIT)
        passLockStep()
        return words
    }

    /**
     * « Protéger PPF sur cet appareil » (Agir, M33, D-082), right after the account arrives on the
     * device: « Choisir un code » is preselected; these tests continue without a code, after its warning.
     */
    private fun ComposeUiTest.passLockStep() {
        awaitTag(AppLockSetupTags.STEP)
        onNodeWithTag(AppLockSetupTags.CHOOSE).assertIsSelected()
        click(AppLockSetupTags.SKIP)
        click(AppLockSetupTags.CONTINUE)
        tap(SecurityTags.DIALOG_CONFIRM)
        waitUntil(timeoutMillis = 10_000) { !has(AppLockSetupTags.STEP) }
    }

    @Test
    fun aNewAccountErasesItsPhraseAndPublishesItsRecoveryKeyOnce() = runComposeUiTest {
        val store = InMemoryIdentityStore()
        val server = InMemoryPublicationPort()
        openMySpace(store, server)
        val words = createAccount()

        val stored = assertNotNull(store.snapshot)
        assertNull(stored.recoveryWords, "the phrase is erased from the device by default")
        assertTrue(stored.phraseVerified)
        val created = RecoveryPhrase.parse(words.joinToString(" "))
        waitUntil(timeoutMillis = 10_000) { commitments(server).isNotEmpty() }
        val commitment = AccountRecovery.parseCommitment(commitments(server).single())!!
        assertEquals(created.deriveKeys().publicKey.hex, commitment.account)
        assertEquals(created.deriveRecoveryKeys().publicKey.hex, commitment.recovery)
        waitUntil(timeoutMillis = 10_000) { store.snapshot?.pendingRecoveryKey == null && store.snapshot?.recoveryCommitment == commitment.id }
        onNodeWithTag(RecoveryTags.RECOVERY_KEY).assertTextContains("Enregistrée")
        onNodeWithTag(RecoveryTags.PHRASE_STATE).assertTextContains("n’est plus sur ce téléphone", substring = true)
        assertFalse(has(AccountTags.SHOW_PHRASE))
    }

    @Test
    fun keepingThePhraseAsksAWarningAndCanBeUndone() = runComposeUiTest {
        val store = InMemoryIdentityStore()
        openMySpace(store, server = null)
        click(AccountTags.CREATE)
        click(AccountTags.PHRASE_NOTED)
        // Cancelling the warning changes nothing.
        click(RecoveryTags.KEEP_PHRASE)
        tap(RecoveryTags.KEEP_CANCEL)
        assertTrue(sees("sera effacée de ce téléphone"))
        assertNull(store.snapshot)
        click(AccountTags.CANCEL)

        val words = createAccount(keep = true)
        val stored = assertNotNull(store.snapshot)
        assertEquals(words, stored.recoveryWords)
        assertTrue(stored.phraseVerified)
        // Without a server, the commitment waits on the device.
        onNodeWithTag(RecoveryTags.RECOVERY_KEY).assertTextContains("En attente du serveur")
        // Reversible: erase it from Mon espace.
        click(RecoveryTags.ERASE_PHRASE)
        waitUntil(timeoutMillis = 5_000) { store.snapshot?.recoveryWords == null }
        assertNotNull(store.snapshot?.pendingRecoveryKey)
    }

    @Test
    fun aPostponedCheckIsRemindedAndThenErasesThePhrase() = runComposeUiTest {
        val store = InMemoryIdentityStore()
        openMySpace(store, server = null)
        click(AccountTags.CREATE)
        click(AccountTags.PHRASE_NOTED)
        click(RecoveryTags.VERIFY_LATER)
        passLockStep()
        val postponed = assertNotNull(store.snapshot)
        assertNotNull(postponed.recoveryWords)
        assertFalse(postponed.phraseVerified)
        val due = assertNotNull(postponed.phraseCheckDueAt)
        assertTrue(due - currentEpochSeconds() in (AccountSession.REMINDER_DELAY_SECONDS - 60)..AccountSession.REMINDER_DELAY_SECONDS)
        // Not before a week.
        assertFalse(has(RecoveryTags.REMINDER))
    }

    @Test
    fun theDueReminderLeadsToTheCheck() = runComposeUiTest {
        val store = InMemoryIdentityStore(StoredIdentity.of(account, phrase, verified = false, checkDueAt = currentEpochSeconds() - 60))
        openMySpace(store, server = null)
        awaitTag(RecoveryTags.REMINDER)
        click(RecoveryTags.CHECK_PHRASE)
        // A wrong word is refused, the phrase stays.
        onNodeWithTag(RecoveryTags.CHECK_FIELD + 0).performTextInput("xyzzy")
        click(RecoveryTags.CHECK_SUBMIT)
        awaitTag(RecoveryTags.CHECK_ERROR)
        assertNotNull(store.snapshot?.recoveryWords)
    }

    @Test
    fun typingThePhraseBackOffersTheAlertsOfTheAccount() = runComposeUiTest {
        val store = InMemoryIdentityStore(StoredIdentity.of(account, null, verified = true))
        // With a database, so that the device has its notifications.
        val dependencies = AppDependencies.withDatabase(store, JvmDatabase.inMemory())
        val notifications = assertNotNull(dependencies.notifications)
        setContent { Box(Modifier.requiredSize(1000.dp, 740.dp)) { PpfApp(dependencies) } }
        onNodeWithTag(ShellTags.navItem(Space.MON_ESPACE)).performClick()
        waitForIdle()
        click(RecoveryTags.CHECK_PHRASE)
        onNodeWithTag(RecoveryTags.CHECK_WHOLE).performTextInput(nip06)
        click(RecoveryTags.CHECK_SUBMIT)
        awaitTag(RecoveryTags.CHECK_DONE)

        // A proposal only (decision of the team lead of 8 November 2026): nothing changes before the answer.
        awaitTag(AccountAlertsOfferTags.OFFER)
        assertEquals(NotificationSettings.MOST_PROTECTIVE, notifications.settings.load())
        click(AccountAlertsOfferTags.ACCEPT)
        awaitTag(AccountAlertsOfferTags.DONE)
        assertEquals(setOf(NotificationCategory.ACCOUNT), notifications.settings.load().enabled)
    }

    @Test
    fun theWholePhraseIsCheckedAndKeptOnlyAfterTheWarning() = runComposeUiTest {
        val store = InMemoryIdentityStore(StoredIdentity.of(account, null, verified = true))
        openMySpace(store, server = null)
        onNodeWithTag(RecoveryTags.PHRASE_STATE).assertTextContains("n’est plus sur ce téléphone", substring = true)
        // Another account's phrase is refused.
        click(RecoveryTags.CHECK_PHRASE)
        onNodeWithTag(RecoveryTags.CHECK_WHOLE).performTextInput(RecoveryPhrase.generate().text)
        click(RecoveryTags.CHECK_SUBMIT)
        onNodeWithTag(RecoveryTags.CHECK_ERROR).assertTextContains("autre compte", substring = true)
        click(RecoveryTags.KEEP_PHRASE)
        tap(RecoveryTags.KEEP_CONFIRM)
        onNodeWithTag(RecoveryTags.CHECK_WHOLE).performTextClearance()
        onNodeWithTag(RecoveryTags.CHECK_WHOLE).performTextInput(nip06.uppercase())
        click(RecoveryTags.CHECK_SUBMIT)
        awaitTag(RecoveryTags.CHECK_DONE)
        waitUntil(timeoutMillis = 5_000) { store.snapshot?.recoveryWords != null }
        assertEquals(phrase.words, store.snapshot?.recoveryWords)
    }

    @Test
    fun anOldAccountWithItsPhrasePublishesItsCommitmentAndAnImportedOneIsToldItHasNone() = runComposeUiTest {
        val server = InMemoryPublicationPort()
        // An account created before S-146: its phrase on the device, no commitment.
        val legacy = InMemoryIdentityStore(StoredIdentity(secretKeyHex = account.secretKey().toHexString(), recoveryWords = phrase.words, phraseLanguage = "en"))
        openMySpace(legacy, server)
        waitUntil(timeoutMillis = 10_000) { commitments(server).isNotEmpty() }
        assertEquals(phrase.deriveRecoveryKeys().publicKey.hex, AccountRecovery.parseCommitment(commitments(server).single())!!.recovery)
        // Never verified: the reminder shows.
        awaitTag(RecoveryTags.REMINDER)
    }

    @Test
    fun anAccountImportedByItsKeyHasNoRecoveryKey() = runComposeUiTest {
        val server = InMemoryPublicationPort()
        openMySpace(InMemoryIdentityStore(StoredIdentity.of(IdentityKeys.generate(), null)), server)
        waitUntil(timeoutMillis = 10_000) { sees("Absente") }
        assertTrue(sees("n’a pas encore de clé de secours"))
    }

    @Test
    fun aLostAccountIsDeclaredFromANewDeviceWithANewAccount() = runComposeUiTest {
        val server = InMemoryPublicationPort()
        runBlocking { AccountRecoveryClient(server).ensureCommitment(LocalEventSigner(account), phrase.deriveRecoveryKeys().publicKey) }
        val store = InMemoryIdentityStore()
        openMySpace(store, server, width = 360, height = 740)
        click(LostControlTags.OPEN)
        onNodeWithTag(LostControlTags.PHRASE).performTextInput(nip06)
        onNodeWithTag(LostControlTags.SINCE).assertTextContains("Maintenant")
        click(LostControlTags.SINCE_EARLIER_DAY)
        assertNotEquals("Maintenant", textOf(LostControlTags.SINCE))
        click(LostControlTags.SINCE_LATER_HOUR)
        // The default successor on a device without account: a new account.
        click(LostControlTags.CONTINUE)
        val newWords = (0 until 12).map { textOf(AccountTags.PHRASE_WORD + it).substringAfter(". ") }
        click(LostControlTags.NEW_NOTED)
        onNodeWithTag(LostControlTags.NEW_FIELD + 0).performTextInput("xyzzy")
        click(LostControlTags.NEW_SUBMIT)
        awaitTag(LostControlTags.NEW_ERROR)
        assertTrue(server.events.none { it.kind == AccountRecovery.NOTICE_KIND })
        // Type the right words.
        val instruction = onAllNodesWithText("Pour vérifier votre copie", substring = true).fetchSemanticsNodes().single()
            .config.getOrNull(SemanticsProperties.Text)!!.joinToString { it.text }
        val asked = Regex("\\d+").findAll(instruction).map { it.value.toInt() }.toList()
        asked.forEachIndexed { field, position ->
            onNodeWithTag(LostControlTags.NEW_FIELD + field).performTextClearance()
            onNodeWithTag(LostControlTags.NEW_FIELD + field).performTextInput(newWords[position - 1])
        }
        click(LostControlTags.NEW_SUBMIT)
        awaitTag(LostControlTags.CONFIRM_DIALOG)
        assertTrue(sees("irréversible"))
        onNodeWithTag(LostControlTags.CONFIRM).performClick()
        waitForIdle()
        awaitTag(LostControlTags.DONE)

        val successor = RecoveryPhrase.parse(newWords.joinToString(" ")).deriveKeys().publicKey.hex
        val status = runBlocking { (server.fetchAccountStatus(account.publicKey.hex) as AccountStatusResult.Known).status }
        assertTrue(status.compromised)
        assertEquals(successor, status.successor)
        val since = assertNotNull(status.since)
        assertTrue(currentEpochSeconds() - since in (23L * 3600 - 120)..(23L * 3600 + 120), "one day earlier, then one hour later")
        // The new account is this device's, its phrase checked and erased; its own commitment follows.
        waitUntil(timeoutMillis = 10_000) { store.snapshot?.keys()?.publicKey?.hex == successor }
        assertNull(store.snapshot?.recoveryWords)
        waitUntil(timeoutMillis = 10_000) { commitments(server).any { it.pubkey == successor } }
    }

    @Test
    fun theRefusalsAreExplained() = runComposeUiTest {
        val server = InMemoryPublicationPort()
        val store = InMemoryIdentityStore(StoredIdentity.of(IdentityKeys.generate(), null))
        openMySpace(store, server)
        click(LostControlTags.OPEN)
        // An account that never published its recovery key.
        onNodeWithTag(LostControlTags.PHRASE).performTextInput(nip06)
        click(LostControlTags.SUCCESSOR + SuccessorChoice.NONE.name)
        click(LostControlTags.CONTINUE)
        awaitTag(LostControlTags.CONFIRM_DIALOG)
        onNodeWithTag(LostControlTags.CONFIRM).performClick()
        waitForIdle()
        awaitTag(LostControlTags.ERROR)
        onNodeWithTag(LostControlTags.ERROR).assertTextContains("jamais enregistré de clé de secours", substring = true)
        // A successor that is not a key.
        click(LostControlTags.SUCCESSOR + SuccessorChoice.OTHER.name)
        onNodeWithTag(LostControlTags.OTHER_KEY).performTextInput("npub1nimporte")
        click(LostControlTags.CONTINUE)
        onNodeWithTag(LostControlTags.ERROR).assertTextContains("n’est pas valide", substring = true)
        assertTrue(server.events.none { it.kind == AccountRecovery.NOTICE_KIND })
    }

    @Test
    fun aCompromisedAccountShowsTheBannerAndNoLongerOffersItsSettings() = runComposeUiTest {
        val server = InMemoryPublicationPort()
        runBlocking {
            val client = AccountRecoveryClient(server)
            client.ensureCommitment(LocalEventSigner(account), phrase.deriveRecoveryKeys().publicKey)
            client.declareCompromised(phrase, currentEpochSeconds() - 3600, null)
        }
        openMySpace(InMemoryIdentityStore(StoredIdentity.of(account, null, verified = true)), server)
        awaitTag(RecoveryTags.COMPROMISED_BANNER)
        assertTrue(sees("Le serveur refuse tout nouveau contenu"))
        assertFalse(has(MailboxTags.CARD))
    }
}
