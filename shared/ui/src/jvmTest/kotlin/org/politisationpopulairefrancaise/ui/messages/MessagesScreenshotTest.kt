package org.politisationpopulairefrancaise.ui.messages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.identity.StoredIdentity
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.messaging.ChatMessage
import org.politisationpopulairefrancaise.core.messaging.Conversation
import org.politisationpopulairefrancaise.core.messaging.MessageStore
import org.politisationpopulairefrancaise.core.social.FollowStore
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.shell.AdaptiveShell
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Not a check: a camera for the messages screens, like ScreenshotTest. With PPF_SCREENSHOTS_DIR
 * set, it writes the private list, an open conversation, the new conversation box, the
 * explanation of the encryption, the flows and an open flow, at phone and desktop sizes. Every
 * message is an [EXEMPLE] stored directly on the device.
 */
@OptIn(ExperimentalTestApi::class)
class MessagesScreenshotTest {

    private val directory: File? = System.getenv("PPF_SCREENSHOTS_DIR")?.takeIf { it.isNotBlank() }?.let(::File)

    private fun ComposeUiTest.save(name: String) {
        val dir = directory ?: return
        dir.mkdirs()
        ImageIO.write(onAllNodes(isRoot()).onLast().captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }

    private fun ComposeUiTest.has(tag: String) = onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun capture(width: Int, height: Int, dark: Boolean, prefix: String) = runComposeUiTest {
        if (directory == null) return@runComposeUiTest
        val me = IdentityKeys.generate()
        val camille = IdentityKeys.generate()
        val sacha = IdentityKeys.generate()
        val database = JvmDatabase.inMemory()
        FollowStore(database).follow(EditorialTrust.EXAMPLE_EDITOR)
        val store = MessageStore(database)
        val now = currentEpochSeconds()
        fun from(author: IdentityKeys, others: List<IdentityKeys>, text: String, at: Long, subject: String? = null) =
            ChatMessage.compose(author.publicKey, others.map { it.publicKey }, text, createdAt = at, subject = subject)
        store.store(from(camille, listOf(me), "[EXEMPLE] Tu viens au rassemblement de samedi ?", now - 2 * 86_400), read = true)
        store.store(from(me, listOf(camille), "[EXEMPLE] Oui, je serai là vers 14 h.", now - 2 * 86_400 + 600), read = true)
        store.store(from(camille, listOf(me), "[EXEMPLE] Parfait, on se retrouve devant la mairie.", now - 86_400), read = true)
        store.store(from(camille, listOf(me), "[EXEMPLE] Pense à prendre la fiche « Garde à vue ».", now - 300))
        store.store(from(sacha, listOf(me, camille), "[EXEMPLE] Réunion du collectif jeudi soir.", now - 5 * 86_400, subject = "[EXEMPLE] Collectif du quartier"), read = true)
        val pending = from(me, listOf(camille), "[EXEMPLE] Message écrit hors ligne.", now - 30)
        store.store(pending, read = true)
        store.queueOutgoing(pending.id, camille.publicKey.hex)

        val dependencies = AppDependencies.withDatabase(InMemoryIdentityStore(StoredIdentity.of(me, null)), database)
        setContent {
            Box(Modifier.requiredSize(width.dp, height.dp)) {
                PpfTheme(darkTheme = dark) { AdaptiveShell(dependencies) }
            }
        }
        waitForIdle()
        onNodeWithTag(ShellTags.navItem(Space.MESSAGES)).performClick()
        waitForIdle()
        val withCamille = Conversation.idOf(listOf(me.publicKey.hex, camille.publicKey.hex))
        waitUntil(timeoutMillis = 10_000) { has(PrivateTags.row(withCamille, me.publicKey.hex)) }
        save("$prefix-messages-prive")

        onNodeWithTag(PrivateTags.row(withCamille, me.publicKey.hex)).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { has(ConversationTags.LIST) }
        onNodeWithTag(ConversationTags.COMPOSER).performTextInput("[EXEMPLE] À samedi")
        waitForIdle()
        save("$prefix-messages-conversation")

        onNodeWithTag(ConversationTags.INFO).performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        save("$prefix-messages-chiffrement")
        onNodeWithTag(ConversationTags.ENCRYPTION_CLOSE).performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()

        if (width < 600) {
            onNode(hasContentDescription("Retour aux conversations")).performClick()
            waitForIdle()
        }
        onNodeWithTag(MessagesTags.NEW).performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        onNodeWithTag(NewConversationTags.KEY).performTextInput(sacha.publicKey.npub)
        onNodeWithTag(NewConversationTags.ADD).performClick()
        waitForIdle()
        save("$prefix-messages-nouvelle")
        onNodeWithTag(NewConversationTags.CANCEL).performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()

        onNodeWithTag(MessagesTags.TAB + MessagesView.FLOWS.id).performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) { has(FlowsTags.row(EditorialTrust.EXAMPLE_EDITOR)) }
        save("$prefix-messages-flux")
        onNodeWithTag(FlowsTags.row(EditorialTrust.EXAMPLE_EDITOR)).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { has(FlowsTags.SCREEN) }
        save("$prefix-messages-flux-ouvert")
    }

    @Test
    fun phoneLight() = capture(360, 760, dark = false, prefix = "telephone-clair")

    @Test
    fun phoneDark() = capture(360, 760, dark = true, prefix = "telephone-sombre")

    @Test
    fun desktopLight() = capture(1000, 740, dark = false, prefix = "ordinateur-clair")

    @Test
    fun desktopDark() = capture(1000, 740, dark = true, prefix = "ordinateur-sombre")
}
