package org.politisationpopulairefrancaise.ui.agir.testimonies

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.agir.calls.ParisDateTime
import org.politisationpopulairefrancaise.core.agir.calls.ParisTime
import org.politisationpopulairefrancaise.core.agir.testimonies.OutgoingTestimony
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyReader
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyRecipients
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonySendResult
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyState
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyStatuses
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyStore
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyTransport
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.identity.StoredIdentity
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.media.MediaDigest
import org.politisationpopulairefrancaise.core.messaging.LocalMessagingSigner
import org.politisationpopulairefrancaise.core.publication.HeldStatus
import org.politisationpopulairefrancaise.ui.account.AccountSession
import org.politisationpopulairefrancaise.ui.account.AccountUiState
import org.politisationpopulairefrancaise.ui.agir.awaitTag
import org.politisationpopulairefrancaise.ui.media.ImagePicker
import org.politisationpopulairefrancaise.ui.media.MediaTags
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val NOW: Long = ParisTime.toEpochSeconds(ParisDateTime(2026, 11, 7, 18, 0))
private const val GPS = "GPS 45.7640N 4.8357E [EXEMPLE]"

private fun latin1(text: String) = ByteArray(text.length) { text[it].code.toByte() }

private fun contains(haystack: ByteArray, needle: String): Boolean {
    val bytes = latin1(needle)
    return (0..haystack.size - bytes.size).any { i -> bytes.indices.all { haystack[i + it] == bytes[it] } }
}

/** A photo drawn by Java, with a planted GPS position in an EXIF block (fabricated, no real person). */
private fun dirtyPhoto(): ByteArray {
    val image = BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB)
    image.createGraphics().apply {
        color = Color(200, 33, 58)
        fillRect(0, 0, 120, 80)
        color = Color(43, 58, 143)
        fillOval(40, 20, 40, 40)
        dispose()
    }
    val jpeg = ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
    val payload = latin1("Exif\u0000\u0000MM\u0000*\u0000\u0000\u0000\u0008\u0000\u0000\u0000\u0000\u0000\u0000$GPS")
    val length = payload.size + 2
    return byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(), (length ushr 8).toByte(), length.toByte()) + payload + jpeg.copyOfRange(2, jpeg.size)
}

/** A server in memory: records what is sent; can be unreachable; answers statuses it is told. */
private class FakeTransport : TestimonyTransport {
    val sent = mutableListOf<OutgoingTestimony>()
    var reachable = true
    val states = mutableMapOf<String, TestimonyState>()

    override suspend fun send(testimony: OutgoingTestimony): TestimonySendResult {
        if (!reachable) return TestimonySendResult.Unavailable()
        sent += testimony
        states.getOrPut(testimony.id) { TestimonyState(testimony.id, HeldStatus.PENDING, NOW) }
        return TestimonySendResult.Sent(testimony.id)
    }

    override suspend fun statuses(ids: List<String>): TestimonyStatuses =
        if (reachable) TestimonyStatuses.Known(states.values.filter { it.id in ids }) else TestimonyStatuses.Unavailable()
}

private class Harness(val transport: FakeTransport) {
    val directory = Files.createTempDirectory("ppf-testimony-ui").also { it.toFile().deleteOnExit() }
    val store = TestimonyStore(JvmDatabase.open(directory.resolve("ppf.db")))
    var library: TestimonyLibrary? = null
    var session: AccountSession? = null
    var accountCreation = 0
    val testimonies: TestimonyLibrary get() = requireNotNull(library)
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.showTestimony(keys: IdentityKeys? = IdentityKeys.generate(), width: Int = 420, height: Int = 760, dark: Boolean = false, transport: FakeTransport = FakeTransport()): Harness {
    val harness = Harness(transport)
    val picker = object : ImagePicker {
        override val available: Boolean = true
        override fun pick() {
            harness.testimonies.attach(dirtyPhoto())
        }
    }
    setContent {
        val scope = rememberCoroutineScope()
        val library = remember { TestimonyLibrary(harness.store, transport, scope, TestimonyRecipients.EXAMPLE, clock = { NOW }).also { harness.library = it } }
        val session = remember {
            AccountSession(InMemoryIdentityStore(keys?.let { StoredIdentity.of(it, null) }), scope).also {
                harness.session = it
                it.start()
            }
        }
        PpfTheme(darkTheme = dark) {
            Box(Modifier.requiredSize(width.dp, height.dp)) {
                TestimonyScreen(library, session, WindowClass.of(width.toFloat(), height.toFloat()), onBack = {}, onCreateAccount = { harness.accountCreation++ }, picker = picker)
            }
        }
    }
    awaitTag(TestimonyTags.SCREEN)
    if (keys != null) waitUntil(timeoutMillis = 5_000) { harness.session?.state is AccountUiState.Ready }
    return harness
}

/** A card or a column holds its texts in its children: look for one under the tagged node. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertHasText(tag: String, text: String) {
    onNode(hasTestTag(tag) and hasAnyDescendant(hasText(text, substring = true))).assertExists()
}

@OptIn(ExperimentalTestApi::class)
class TestimonyScreenTest {
    @Test
    fun aTestimonyWithABlurredPhotoLeavesEncryptedForTheModerationKeyOnly() = runComposeUiTest {
        val keys = IdentityKeys.generate()
        val harness = showTestimony(keys)
        onNodeWithTag(TestimonyTags.EXAMPLE_KEY).assertExists()
        // Nothing goes before the rules are met: the errors appear under their fields.
        onNodeWithTag(TestimonyTags.SEND).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(TestimonyTags.ERROR + "text_too_short").performScrollTo().assertExists()
        onNodeWithTag(TestimonyTags.ERROR + "charter_not_accepted").performScrollTo().assertExists()
        assertTrue(harness.transport.sent.isEmpty())

        onNodeWithTag(TestimonyTags.TEXT).performScrollTo().performTextInput("[EXEMPLE] Vers 18 h, la sortie de la rue fictive était fermée par un cordon.")
        onNodeWithTag(TestimonyTags.DATE).performScrollTo().performTextInput("05/11/2026")
        onNodeWithTag(TestimonyTags.TIME).performScrollTo().performTextInput("18h05")
        onNodeWithTag(TestimonyTags.PLACE).performScrollTo().performTextInput("[EXEMPLE] Place fictive")
        onNodeWithTag(TestimonyTags.ADD_PHOTO).performScrollTo().performClick()
        awaitTag(MediaTags.PREVIEW)
        // What the cleaning removes is said plainly.
        onNodeWithTag(MediaTags.REMOVED).performScrollTo()
        assertHasText(MediaTags.REMOVED, "position GPS")
        // Two zones: one at the centre with the button, one placed then moved and enlarged.
        onNodeWithTag(MediaTags.ADD_ZONE).performScrollTo().performClick()
        onNodeWithTag(MediaTags.ADD_ZONE).performClick()
        onNodeWithTag(MediaTags.RIGHT).performScrollTo().performClick()
        onNodeWithTag(MediaTags.BIGGER).performScrollTo().performClick()
        waitForIdle()
        assertEquals(2, harness.testimonies.zones.size)
        onNodeWithTag(MediaTags.ZONE_COUNT).assertTextContains("2", substring = true)
        // Giving the original asks first; cancelling keeps the protection.
        onNodeWithTag(TestimonyTags.ORIGINAL).performScrollTo().performClick()
        onNodeWithTag(TestimonyTags.ORIGINAL_CANCEL).performClick()
        waitForIdle()
        assertFalse(harness.testimonies.giveOriginal)
        onNodeWithTag(TestimonyTags.ORIGINAL).performScrollTo().performClick()
        onNodeWithTag(TestimonyTags.ORIGINAL_CONFIRM).performClick()
        waitForIdle()
        assertTrue(harness.testimonies.giveOriginal)
        onNodeWithTag(TestimonyTags.CHARTER).performScrollTo().performClick()
        onNodeWithTag(TestimonyTags.SEND).performScrollTo().performClick()
        waitUntil(timeoutMillis = 30_000) { harness.transport.sent.isNotEmpty() }
        awaitTag(TestimonyTags.NEW)
        assertHasText(TestimonyTags.STATE, "attend la relecture")

        val outgoing = harness.transport.sent.single()
        val moderation = LocalMessagingSigner(TestimonyRecipients.exampleModerationKeys())
        val opened = TestimonyReader.open(outgoing.submission.envelope, moderation)
        assertEquals(keys.publicKey.hex, opened.author)
        assertEquals("2026-11-05", opened.factDate)
        assertEquals("18:05", opened.factTime)
        assertTrue(opened.originalGiven)
        val media = opened.media.single()
        assertTrue(media.blurred && media.reencoded)
        val published = TestimonyReader.decrypt(assertNotNull(outgoing.files[media.fileId]), media)
        assertFalse(contains(published, GPS))
        assertNotNull(ImageIO.read(ByteArrayInputStream(published)))
        // The original went for the evidence key only, untouched.
        val evidence = TestimonyReader.openEvidence(assertNotNull(outgoing.submission.evidenceEnvelope), LocalMessagingSigner(TestimonyRecipients.exampleEvidenceKeys()))
        assertTrue(contains(TestimonyReader.decrypt(assertNotNull(outgoing.files[evidence.media.single().fileId]), evidence.media.single()), GPS))
        // The stamps are the provenance statement and the digest of the published copy.
        assertEquals(listOf(opened.provenance!!.id, MediaDigest.sha256Hex(published)), outgoing.submission.stamps)

        // "Mes témoignages": the line, without text, then its decision.
        awaitTag(TestimonyTags.MINE_ITEM + outgoing.id)
        harness.transport.states[outgoing.id] = TestimonyState(outgoing.id, HeldStatus.REJECTED, NOW, NOW + 60, "[EXEMPLE] Faits déjà publiés.")
        harness.testimonies.refresh()
        waitUntil(timeoutMillis = 10_000) { harness.testimonies.mine.singleOrNull()?.reason != null }
        onNodeWithTag(TestimonyTags.MINE_ITEM + outgoing.id).performScrollTo()
        assertHasText(TestimonyTags.MINE_ITEM + outgoing.id, "Refusé")
        // The draft is gone from memory.
        assertEquals("", harness.testimonies.text)
        assertNull(harness.testimonies.photo)
    }

    @Test
    fun withoutNetworkTheTestimonyWaitsEncryptedThenLeaves() = runComposeUiTest {
        val transport = FakeTransport().apply { reachable = false }
        val harness = showTestimony(transport = transport)
        onNodeWithTag(TestimonyTags.TEXT).performScrollTo().performTextInput("[EXEMPLE] Un témoignage écrit sans réseau, au milieu de la foule.")
        onNodeWithTag(TestimonyTags.CHARTER).performScrollTo().performClick()
        onNodeWithTag(TestimonyTags.SEND).performScrollTo().performClick()
        awaitTag(TestimonyTags.NEW)
        assertHasText(TestimonyTags.STATE, "dès que le réseau reviendra")
        assertEquals(1, harness.store.unsent().size)
        transport.reachable = true
        harness.testimonies.refresh()
        waitUntil(timeoutMillis = 10_000) { transport.sent.isNotEmpty() && harness.store.unsent().isEmpty() }
        waitUntil(timeoutMillis = 10_000) { harness.testimonies.mine.single().progress.name == "PENDING" }
    }

    @Test
    fun anAccountIsRequiredAndVideosAreRefusedHonestly() = runComposeUiTest {
        val harness = showTestimony(keys = null)
        onNodeWithTag(TestimonyTags.ACCOUNT_NEEDED).performScrollTo().assertExists()
        assertEquals(0, onAllNodesWithTag(TestimonyTags.SEND).fetchSemanticsNodes().size)
        val video = byteArrayOf(0, 0, 0, 0x18) + latin1("ftypmp42") + ByteArray(40)
        harness.testimonies.attach(video)
        awaitTag(TestimonyTags.PHOTO_REFUSAL)
        onNodeWithTag(TestimonyTags.PHOTO_REFUSAL).performScrollTo().assertTextContains("vidéos ne peuvent pas encore", substring = true)
        assertNull(harness.testimonies.photo)
    }

    @Test
    fun answeringACallLinksTheTestimonyToIt() = runComposeUiTest {
        val harness = showTestimony(width = 1000, height = 740)
        val address = "30023:${"a".repeat(64)}:temoins-exemple"
        harness.testimonies.answer(address, "[EXEMPLE] Appel à témoins fictif")
        awaitTag(TestimonyTags.CALL)
        assertHasText(TestimonyTags.CALL, "Appel à témoins fictif")
        onNodeWithTag(TestimonyTags.TEXT).performScrollTo().performTextInput("[EXEMPLE] J'étais devant la mairie et j'ai vu la scène décrite par l'appel.")
        onNodeWithTag(TestimonyTags.CHARTER).performScrollTo().performClick()
        onNodeWithTag(TestimonyTags.SEND).performScrollTo().assertIsEnabled().performClick()
        waitUntil(timeoutMillis = 30_000) { harness.transport.sent.isNotEmpty() }
        val opened = TestimonyReader.open(harness.transport.sent.single().submission.envelope, LocalMessagingSigner(TestimonyRecipients.exampleModerationKeys()))
        assertEquals(address, opened.callAddress)
        awaitTag(TestimonyTags.MINE_ITEM + harness.transport.sent.single().id)
        assertHasText(TestimonyTags.MINE_ITEM + harness.transport.sent.single().id, "en réponse à un appel")
    }

    private fun fits(width: Int, height: Int, dark: Boolean) = runComposeUiTest {
        showTestimony(width = width, height = height, dark = dark)
        onNodeWithTag(TestimonyTags.SEND).performScrollTo().assertIsEnabled()
        onNodeWithTag(TestimonyTags.MINE).performScrollTo().assertExists()
    }

    @Test
    fun theFormFitsAPhoneInLight() = fits(360, 740, dark = false)

    @Test
    fun theFormFitsATabletInDark() = fits(760, 740, dark = true)

    @Test
    fun theFormFitsAComputer() = fits(1000, 740, dark = false)

    @Test
    fun theLetteredDatesBecomeIsoDates() {
        assertEquals("2026-11-05", TestimonyLibrary.isoDate("5/11/2026"))
        assertEquals("2026-11-05", TestimonyLibrary.isoDate(" 05/11/2026 "))
        assertEquals("2026-11-05", TestimonyLibrary.isoDate("2026-11-05"))
        assertNull(TestimonyLibrary.isoDate(""))
        assertEquals("18:05", TestimonyLibrary.isoTime("18h05"))
        assertEquals("08:00", TestimonyLibrary.isoTime("8h"))
        assertEquals("18:05", TestimonyLibrary.isoTime("18:05"))
        assertEquals("demain", TestimonyLibrary.isoTime("demain"))
    }
}

