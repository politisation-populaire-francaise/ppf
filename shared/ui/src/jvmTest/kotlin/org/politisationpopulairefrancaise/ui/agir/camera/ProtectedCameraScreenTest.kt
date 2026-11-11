package org.politisationpopulairefrancaise.ui.agir.camera

import org.politisationpopulairefrancaise.ui.agir.TEST_WINDOW_HEIGHT
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.agir.camera.CameraShot
import org.politisationpopulairefrancaise.core.agir.camera.CaptureResult
import org.politisationpopulairefrancaise.core.agir.camera.InMemoryProtectedVault
import org.politisationpopulairefrancaise.core.agir.camera.ProtectedCapture
import org.politisationpopulairefrancaise.core.agir.camera.ProtectedPhoto
import org.politisationpopulairefrancaise.core.agir.manifestation.InMemoryRallyPointStorage
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.media.MediaDigest
import org.politisationpopulairefrancaise.core.media.MetadataStripper
import org.politisationpopulairefrancaise.core.media.Provenance
import org.politisationpopulairefrancaise.core.media.StripResult
import org.politisationpopulairefrancaise.ui.account.AccountSession
import org.politisationpopulairefrancaise.ui.agir.AgirFixtures
import org.politisationpopulairefrancaise.ui.agir.awaitTag
import org.politisationpopulairefrancaise.ui.agir.count
import org.politisationpopulairefrancaise.ui.agir.manifestation.LocalRallyPointStorage
import org.politisationpopulairefrancaise.ui.agir.manifestation.ManifestationScreen
import org.politisationpopulairefrancaise.ui.agir.manifestation.ManifestationTags
import org.politisationpopulairefrancaise.ui.agir.manifestation.ProvideHelp
import org.politisationpopulairefrancaise.ui.agir.manifestation.rememberHelpState
import org.politisationpopulairefrancaise.ui.agir.organisations.rememberOrganisationsLibrary
import org.politisationpopulairefrancaise.ui.agir.testimonies.TestimonyLibrary
import org.politisationpopulairefrancaise.ui.agir.testimonies.TestimonyScreen
import org.politisationpopulairefrancaise.ui.agir.testimonies.TestimonyTags
import org.politisationpopulairefrancaise.ui.content.rememberContentLibrary
import org.politisationpopulairefrancaise.ui.media.ImagePicker
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val TAKEN_AT = 1_794_200_000L
private const val GPS = "GPS 45.7640N 4.8357E [EXEMPLE]"
private const val MODEL = "[EXEMPLE] Telephone Modele-Z"

private fun latin1(text: String) = ByteArray(text.length) { text[it].code.toByte() }

private fun contains(haystack: ByteArray, needle: String): Boolean {
    val bytes = latin1(needle)
    return (0..haystack.size - bytes.size).any { i -> bytes.indices.all { haystack[i + it] == bytes[it] } }
}

/** A photo as a camera hands it over: drawn by Java, with a fabricated device model and position in an EXIF block. */
private fun cameraPhoto(color: Color = Color(200, 33, 58)): ByteArray {
    val image = BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB)
    image.createGraphics().apply {
        this.color = color
        fillRect(0, 0, 120, 80)
        this.color = Color(43, 58, 143)
        fillOval(40, 20, 40, 40)
        dispose()
    }
    val jpeg = ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
    val payload = latin1("Exif\u0000\u0000MM\u0000*\u0000\u0000\u0000\u0008\u0000\u0000\u0000\u0000\u0000\u0000$MODEL $GPS")
    val length = payload.size + 2
    return byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(), (length ushr 8).toByte(), length.toByte()) + payload + jpeg.copyOfRange(2, jpeg.size)
}

/** Keeps a photo in [vault] as the camera would (through the chain), and gives its line. */
private fun keep(vault: InMemoryProtectedVault, takenAt: Long = TAKEN_AT, color: Color = Color(200, 33, 58)): ProtectedPhoto {
    val ready = assertIs<CaptureResult.Ready>(ProtectedCapture.prepare(CameraShot(cameraPhoto(color), 0, takenAt)))
    return runBlocking { vault.store(ready.bytes, ready.meta) }
}

/** The copy to the phone's gallery of the tests: records what it is given and says it is saved. */
private class FakeExporter : GalleryExporter {
    override val available: Boolean = true
    val saved = CopyOnWriteArrayList<Pair<ByteArray, String>>()
    var onResult: (GalleryExport) -> Unit = {}

    override fun save(bytes: ByteArray, name: String) {
        saved += bytes.copyOf() to name
        onResult(GalleryExport.SAVED)
    }
}

/** The protected camera alone, on a vault in memory, with what the tests look at. */
private class CameraHarness(val vault: InMemoryProtectedVault = InMemoryProtectedVault()) {
    lateinit var state: ProtectedCameraState
    val exporter = FakeExporter()
    val testified = CopyOnWriteArrayList<Pair<ByteArray, Long>>()

    @Composable
    fun Content(width: Int, height: Int = 900, dark: Boolean = false) {
        val scope = rememberCoroutineScope()
        val created = remember { ProtectedCameraState(vault, scope) }
        state = created
        exporter.onResult = { created.exportFinished(it) }
        Box(Modifier.requiredSize(width.dp, minOf(height, TEST_WINDOW_HEIGHT).dp)) {
            PpfTheme(darkTheme = dark) {
                ProtectedCameraScreen(
                    created,
                    WindowClass.of(width.toFloat(), height.toFloat()),
                    onBack = {},
                    onTestify = { bytes: ByteArray, takenAt: Long -> testified += bytes.copyOf() to takenAt },
                    exporter = exporter,
                )
            }
        }
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.show(harness: CameraHarness, width: Int = 420, dark: Boolean = false) {
    setContent { harness.Content(width, dark = dark) }
    awaitTag(ProtectedCameraTags.SCREEN)
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.openPhoto(id: String) {
    awaitTag(ProtectedCameraTags.THUMBNAIL + id)
    onNodeWithTag(ProtectedCameraTags.THUMBNAIL + id).performScrollTo().performClick()
    awaitTag(ProtectedCameraTags.VIEWER)
    awaitTag(ProtectedCameraTags.PHOTO)
}

/** « Caméra protégée » (AG-58, D-089): the entry, the gallery of PPF, the deletion, the copy to the phone's gallery, the testimony. */
@OptIn(ExperimentalTestApi::class)
class ProtectedCameraScreenTest {
    @Test
    fun theProtestModeSaysTheProtectedCameraIsOnAndroidOnAComputer() {
        for ((width, dark) in listOf(360 to false, 700 to true, 1000 to false)) {
            runComposeUiTest {
                val dependencies = AgirFixtures.dependencies()
                setContent {
                    Box(Modifier.requiredSize(width.dp, TEST_WINDOW_HEIGHT.dp)) {
                        PpfTheme(darkTheme = dark) {
                            CompositionLocalProvider(LocalRallyPointStorage provides InMemoryRallyPointStorage()) {
                                val library = rememberContentLibrary(dependencies)
                                val organisations = rememberOrganisationsLibrary(dependencies)
                                val help = rememberHelpState(dependencies, organisations, library)
                                ProvideHelp(help) {
                                    ManifestationScreen(help, WindowClass.of(width.toFloat(), 900f), onBack = {}, modifier = Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                }
                awaitTag(ManifestationTags.SCREEN)
                onNodeWithTag(ProtectedCameraTags.ENTRY).performScrollTo()
                onNodeWithTag(ProtectedCameraTags.UNAVAILABLE).performScrollTo().assertTextContains("application Android", substring = true)
                // Nothing to open on a computer: no camera, no vault.
                assertEquals(0, count(ProtectedCameraTags.ENTRY_OPEN))
            }
        }
    }

    @Test
    fun withoutAVaultTheScreenSaysSoAndShowsNoGallery() = runComposeUiTest {
        setContent {
            val scope = rememberCoroutineScope()
            val state = remember { ProtectedCameraState(null, scope) }
            Box(Modifier.requiredSize(420.dp, TEST_WINDOW_HEIGHT.dp)) {
                PpfTheme { ProtectedCameraScreen(state, WindowClass.of(420f, 800f), onBack = {}) }
            }
        }
        awaitTag(ProtectedCameraTags.UNAVAILABLE)
        onNodeWithTag(ProtectedCameraTags.UNAVAILABLE).assertExists()
        assertEquals(0, count(ProtectedCameraTags.GALLERY))
        assertEquals(0, count(ProtectedCameraTags.SHUTTER))
    }

    @Test
    fun aShotIsCleanedThenKeptEncryptedAndShownInTheGallery() = runComposeUiTest {
        val harness = CameraHarness()
        show(harness)
        awaitTag(ProtectedCameraTags.EMPTY)
        val shot = cameraPhoto()
        val cameraDigest = MediaDigest.sha256Hex(shot)
        // What the Android camera hands over, in memory: the JPEG and its rotation.
        runOnIdle { harness.state.protect(shot.copyOf(), 90) }
        waitUntil(timeoutMillis = 20_000) { runBlocking { harness.vault.list() }.size == 1 }
        val photo = runBlocking { harness.vault.list() }.single()
        awaitTag(ProtectedCameraTags.THUMBNAIL + photo.id)
        awaitTag(ProtectedCameraTags.NOTICE)
        onNodeWithTag(ProtectedCameraTags.NOTICE).assertTextContains("Photo protégée", substring = true)
        // The chain ran before the vault: no device model, no position; the camera's rotation alone stays.
        val kept = assertNotNull(runBlocking { harness.vault.open(photo.id) })
        assertFalse(contains(kept, GPS))
        assertFalse(contains(kept, MODEL))
        assertTrue(MetadataStripper.isClean(kept))
        assertEquals(6, assertIs<StripResult.Clean>(MetadataStripper.strip(kept)).image.orientation)
        assertEquals(cameraDigest, photo.cameraSha256)
        assertEquals(MediaDigest.sha256Hex(kept), photo.sha256)
    }

    @Test
    fun aPhotoOpensAndIsDeletedOnlyAfterConfirmation() = runComposeUiTest {
        val harness = CameraHarness()
        val older = keep(harness.vault, TAKEN_AT)
        val newer = keep(harness.vault, TAKEN_AT + 600, Color(20, 120, 60))
        show(harness)
        awaitTag(ProtectedCameraTags.THUMBNAIL + newer.id)
        openPhoto(older.id)
        onNodeWithText("novembre 2026", substring = true).assertExists()
        // « Annuler » keeps the photo.
        onNodeWithTag(ProtectedCameraTags.DELETE).performScrollTo().performClick()
        awaitTag(ProtectedCameraTags.DELETE_CANCEL)
        onNodeWithText("Supprimer cette photo ?").assertExists()
        onNodeWithTag(ProtectedCameraTags.DELETE_CANCEL).performClick()
        waitForIdle()
        assertEquals(2, runBlocking { harness.vault.list() }.size)
        // Confirmed, it is gone from the vault and from the gallery.
        onNodeWithTag(ProtectedCameraTags.DELETE).performScrollTo().performClick()
        awaitTag(ProtectedCameraTags.DELETE_CONFIRM)
        onNodeWithTag(ProtectedCameraTags.DELETE_CONFIRM).performClick()
        waitUntil(timeoutMillis = 10_000) { runBlocking { harness.vault.list() }.map { it.id } == listOf(newer.id) }
        awaitTag(ProtectedCameraTags.GALLERY)
        assertEquals(0, count(ProtectedCameraTags.THUMBNAIL + older.id))
        onNodeWithTag(ProtectedCameraTags.NOTICE).assertTextContains("Photo supprimée", substring = true)
    }

    @Test
    fun savingToThePhoneGalleryAsksFirstAndCancelKeepsTheProtection() = runComposeUiTest {
        val harness = CameraHarness()
        val photo = keep(harness.vault)
        show(harness)
        openPhoto(photo.id)
        onNodeWithTag(ProtectedCameraTags.SAVE).performScrollTo().performClick()
        awaitTag(ProtectedCameraTags.SAVE_CANCEL)
        onNodeWithText("Sortir une copie de la protection ?").assertExists()
        onNodeWithText("sauvegardée en ligne", substring = true).assertExists()
        onNodeWithTag(ProtectedCameraTags.SAVE_CANCEL).performClick()
        waitForIdle()
        assertTrue(harness.exporter.saved.isEmpty(), "nothing leaves the protection without a confirmation")
        onNodeWithTag(ProtectedCameraTags.SAVE).performScrollTo().performClick()
        awaitTag(ProtectedCameraTags.SAVE_CONFIRM)
        onNodeWithTag(ProtectedCameraTags.SAVE_CONFIRM).performClick()
        waitUntil(timeoutMillis = 10_000) { harness.exporter.saved.size == 1 }
        val (bytes, name) = harness.exporter.saved.single()
        // The copy is the protected photo itself, metadata still removed, under a name that does not say PPF.
        assertContentEquals(runBlocking { harness.vault.open(photo.id) }, bytes)
        assertFalse(contains(bytes, GPS))
        assertTrue(Regex("IMG_\\d{8}_\\d{4}\\.jpg").matches(name), name)
        awaitTag(ProtectedCameraTags.NOTICE)
        onNodeWithTag(ProtectedCameraTags.NOTICE).assertTextContains("galerie du téléphone", substring = true)
        // It also stays in PPF.
        assertEquals(1, runBlocking { harness.vault.list() }.size)
    }

    @Test
    fun witnessingWithAPhotoHandsItsDecryptedBytesAndItsDate() = runComposeUiTest {
        val harness = CameraHarness()
        val photo = keep(harness.vault)
        show(harness)
        openPhoto(photo.id)
        onNodeWithTag(ProtectedCameraTags.TESTIFY).performScrollTo().performClick()
        waitUntil(timeoutMillis = 10_000) { harness.testified.size == 1 }
        val (bytes, takenAt) = harness.testified.single()
        assertContentEquals(runBlocking { harness.vault.open(photo.id) }, bytes)
        assertEquals(TAKEN_AT, takenAt)
    }

    @Test
    fun theTestimonyFormTakesAPhotoOfTheProtectedCamera() = runComposeUiTest {
        val vault = InMemoryProtectedVault()
        val photo = keep(vault)
        var library: TestimonyLibrary? = null
        setContent {
            val scope = rememberCoroutineScope()
            val testimonies = remember { TestimonyLibrary(store = null, transport = null, scope = scope).also { library = it } }
            val session = remember { AccountSession(InMemoryIdentityStore(), scope).also { it.start() } }
            val camera = remember { ProtectedCameraState(vault, scope) }
            val picker = remember {
                object : ImagePicker {
                    override val available: Boolean = true
                    override fun pick() = Unit
                }
            }
            Box(Modifier.requiredSize(420.dp, TEST_WINDOW_HEIGHT.dp)) {
                PpfTheme {
                    TestimonyScreen(testimonies, session, WindowClass.of(420f, 900f), onBack = {}, onCreateAccount = {}, picker = picker, camera = camera)
                }
            }
        }
        awaitTag(TestimonyTags.SCREEN)
        onNodeWithTag(ProtectedCameraTags.ENTRY_OPEN).performScrollTo().performClick()
        awaitTag(ProtectedCameraTags.SCREEN)
        openPhoto(photo.id)
        onNodeWithTag(ProtectedCameraTags.TESTIFY).performScrollTo().performClick()
        // Back in the form, the photo attached: its provenance says "camera" and when it was taken.
        awaitTag(TestimonyTags.SCREEN)
        waitUntil(timeoutMillis = 20_000) { library?.photo != null }
        val attached = assertNotNull(library?.photo)
        assertEquals("camera", attached.provenance.firstTagValue(Provenance.TAG_CAPTURE))
        assertEquals(TAKEN_AT, attached.provenance.createdAt)
        assertEquals(MediaDigest.sha256Hex(attached.original), attached.provenance.firstTagValue(Provenance.TAG_ORIGINAL))
        assertEquals(photo.sha256, MediaDigest.sha256Hex(attached.original))
    }

    @Test
    fun theTestimonyFormSaysTheProtectedCameraIsOnAndroidOnAComputer() = runComposeUiTest {
        setContent {
            val scope = rememberCoroutineScope()
            val testimonies = remember { TestimonyLibrary(store = null, transport = null, scope = scope) }
            val session = remember { AccountSession(InMemoryIdentityStore(), scope).also { it.start() } }
            val camera = remember { ProtectedCameraState(null, scope) }
            Box(Modifier.requiredSize(420.dp, TEST_WINDOW_HEIGHT.dp)) {
                PpfTheme {
                    TestimonyScreen(testimonies, session, WindowClass.of(420f, 900f), onBack = {}, onCreateAccount = {}, camera = camera)
                }
            }
        }
        awaitTag(TestimonyTags.SCREEN)
        onNodeWithTag(ProtectedCameraTags.UNAVAILABLE).performScrollTo().assertTextContains("application Android", substring = true)
        assertEquals(0, count(ProtectedCameraTags.ENTRY_OPEN))
    }

    @Test
    fun theQuickEraseEmptiesTheVaultAndTheScreen() = runComposeUiTest {
        val harness = CameraHarness()
        val photo = keep(harness.vault)
        show(harness)
        awaitTag(ProtectedCameraTags.THUMBNAIL + photo.id)
        // As the quick erase runs it: awaited to the end.
        runOnIdle { runBlocking { harness.state.eraseFromDevice() } }
        waitForIdle()
        assertTrue(runBlocking { harness.vault.list() }.isEmpty())
        assertEquals(1, harness.vault.erasures)
        awaitTag(ProtectedCameraTags.EMPTY)
        assertEquals(0, count(ProtectedCameraTags.THUMBNAIL + photo.id))
        // It may run again.
        runOnIdle { runBlocking { harness.state.eraseFromDevice() } }
        assertEquals(2, harness.vault.erasures)
    }

    private fun fits(width: Int, dark: Boolean) = runComposeUiTest {
        val harness = CameraHarness()
        val photo = keep(harness.vault)
        show(harness, width, dark)
        onNodeWithTag(ProtectedCameraTags.GALLERY).performScrollTo().assertExists()
        awaitTag(ProtectedCameraTags.THUMBNAIL + photo.id)
        openPhoto(photo.id)
        onNodeWithTag(ProtectedCameraTags.DELETE).performScrollTo().assertExists()
        onNodeWithTag(ProtectedCameraTags.VIEW_CLOSE).performScrollTo().performClick()
        awaitTag(ProtectedCameraTags.SCREEN)
    }

    @Test
    fun theGalleryFitsAPhoneInLight() = fits(360, dark = false)

    @Test
    fun theGalleryFitsATabletInDark() = fits(760, dark = true)

    @Test
    fun theGalleryFitsAComputer() = fits(1000, dark = false)

    @Test
    fun theCopyInThePhoneGalleryHasANeutralNameAndTheLabelsSayTheDate() {
        assertTrue(Regex("IMG_20261109_\\d{4}\\.jpg").matches(ProtectedCameraState.galleryName(TAKEN_AT)))
        val (date, time) = ProtectedCameraState.dateAndTime(TAKEN_AT)
        assertEquals("9 novembre 2026", date)
        assertTrue(Regex("\\d{2}:\\d{2}").matches(time))
    }
}
