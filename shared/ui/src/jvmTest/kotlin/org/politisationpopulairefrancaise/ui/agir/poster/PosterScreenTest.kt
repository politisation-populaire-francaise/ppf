package org.politisationpopulairefrancaise.ui.agir.poster

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.map.Gzip
import org.politisationpopulairefrancaise.core.agir.poster.Base45
import org.politisationpopulairefrancaise.core.agir.poster.PosterQr
import org.politisationpopulairefrancaise.core.agir.poster.PosterScan
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.ui.account.AccountSession
import org.politisationpopulairefrancaise.ui.agir.agenda.AgendaClock
import org.politisationpopulairefrancaise.ui.agir.agenda.AgendaDevice
import org.politisationpopulairefrancaise.ui.agir.agenda.AgendaKit
import org.politisationpopulairefrancaise.ui.agir.agenda.AgendaLibrary
import org.politisationpopulairefrancaise.ui.agir.agenda.AgendaNames
import org.politisationpopulairefrancaise.ui.agir.agenda.AgendaScreen
import org.politisationpopulairefrancaise.ui.agir.agenda.AgendaTags
import org.politisationpopulairefrancaise.ui.agir.agenda.CalendarExportResult
import org.politisationpopulairefrancaise.ui.agir.agenda.CalendarExporter
import org.politisationpopulairefrancaise.ui.agir.agenda.cardCount
import org.politisationpopulairefrancaise.ui.agir.manifestation.ManifestationTags
import org.politisationpopulairefrancaise.ui.components.qr.JvmQr
import org.politisationpopulairefrancaise.ui.components.qr.canScanQr
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * « Faire une affiche » and « Scanner une affiche » (AG-61) on the computer, on the bundled examples
 * of the agenda, on Friday 13 November 2026 at 10 h: the poster opens from a mobilization, switches
 * between A4 and A3, saves a PNG whose code reads back as the signed event; the scan is not offered
 * without a camera; a code read is verified, added to the agenda, or refused when forged.
 */
@OptIn(ExperimentalTestApi::class)
class PosterScreenTest {
    private val paris = "rassemblement-services-publics-paris-2026-11-14"
    private val rennes = "projection-debat-rennes-2026-11-03"
    private val cancelled = "rassemblement-maternite-seine-saint-denis-2026-12-05"

    /** The agenda of the tests with the poster exporter given. */
    @Composable
    private fun PosterAgenda(device: AgendaDevice, exporter: PosterExporter, width: Int = 1000, height: Int = 900) {
        val scope = rememberCoroutineScope()
        val agenda = remember { AgendaLibrary(device.store, device.state, EditorialTrust.bundledExamples(), device.server, scope, clock = { AgendaClock.NOW }).also { it.start() } }
        val session = remember { AccountSession(device.identityStore, scope).also { it.start() } }
        PpfTheme {
            Box(Modifier.requiredSize(width.dp, height.dp)) {
                AgendaScreen(
                    dependencies = device.dependencies,
                    session = session,
                    windowClass = WindowClass.of(widthDp = width.toFloat(), heightDp = height.toFloat()),
                    agenda = agenda,
                    exporter = CalendarExporter { _, _, _ -> CalendarExportResult.DONE },
                    posterExporter = exporter,
                )
            }
        }
    }

    private fun ComposeUiTest.openPosterOf(identifier: String) {
        waitUntil(timeoutMillis = 20_000) { cardCount() == 7 }
        onNodeWithTag(AgendaTags.CARD + identifier).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(PosterTags.MAKE).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(PosterTags.SCREEN).assertExists()
    }

    @Test
    fun thePosterOpensFromAMobilizationAndSwitchesBetweenA4AndA3() = runComposeUiTest {
        setContent { PosterAgenda(AgendaDevice(withAccount = false), PosterExporter { PosterExportResult.DONE }) }
        openPosterOf(paris)
        onNodeWithTag(PosterTags.PREVIEW).assertExists()
        onNodeWithText("Scannez pour ajouter à votre agenda, même sans réseau").assertExists()
        onNodeWithText("RASSEMBLEMENT").assertExists()
        onNodeWithText("Le code QR contient l’événement signé lui-même", substring = true).assertExists()
        onNodeWithTag(PosterTags.FORMAT + "a4").assertIsSelected()
        onNodeWithTag(PosterTags.FORMAT_CAPTION).assertTextContains("Format A4 : 21 × 29,7 cm", substring = true)
        onNodeWithTag(PosterTags.FORMAT + "a3").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(PosterTags.FORMAT + "a3").assertIsSelected()
        onNodeWithTag(PosterTags.FORMAT + "a4").assertIsNotSelected()
        onNodeWithTag(PosterTags.FORMAT_CAPTION).assertTextContains("Format A3 : 29,7 × 42 cm", substring = true)
        // The computer saves an image; the guide of free posting is offered.
        onNodeWithTag(PosterTags.EXPORT).performScrollTo().assertTextContains("Enregistrer en image PNG")
        onNodeWithTag(PosterTags.GUIDE_OPEN).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(PosterTags.SCREEN).assertDoesNotExist()
        // Outside the shell the guides are not loaded: the reader says so, then leads back.
        onNodeWithTag(ManifestationTags.GUIDE).assertExists()
        onNodeWithText("Cette fiche n’est pas encore sur cet appareil", substring = true).assertExists()
        onNodeWithTag(ManifestationTags.GUIDE_BACK).performClick()
        waitForIdle()
        onNodeWithTag(PosterTags.BACK).performClick()
        waitForIdle()
        onNodeWithTag(AgendaTags.DETAIL).assertExists()
    }

    @Test
    fun onTheComputerTheExportWritesAPngWhoseCodeReadsBack() = runComposeUiTest {
        val folder = Files.createTempDirectory("ppf-affiche").toFile()
        var saved: Pair<File, PosterDocument>? = null
        val exporter = PosterExporter { document ->
            val png = JvmPosterRenderer.png(document, JvmPosterFonts.load()) ?: return@PosterExporter PosterExportResult.FAILED
            val file = File(folder, document.fileName + ".png")
            file.writeBytes(png)
            saved = file to document
            PosterExportResult.DONE
        }
        try {
            setContent { PosterAgenda(AgendaDevice(withAccount = false), exporter) }
            openPosterOf(paris)
            onNodeWithTag(PosterTags.PRINTER).performScrollTo().performTextInput("[EXEMPLE] Imprimé par nos soins")
            onNodeWithTag(PosterTags.EXPORT).performScrollTo().performClick()
            waitUntil(timeoutMillis = 30_000) { saved != null }
            waitForIdle()
            onNodeWithTag(PosterTags.EXPORT_MESSAGE).assertTextContains("Affiche enregistrée", substring = true)

            val (file, document) = assertNotNull(saved)
            assertEquals("affiche-$paris-a4.png", file.name)
            assertEquals(folder, file.parentFile, "only the file chosen is written")
            assertTrue(file.length() > 0)
            assertEquals("[EXEMPLE] Imprimé par nos soins", document.text.printer)
            val image = assertNotNull(ImageIO.read(file))
            assertEquals(1240, image.width, "A4 at 150 dots per inch")
            assertEquals(1754, image.height)

            // The code of the image reads back as the signed event itself, verified.
            val code = JvmPosterRenderer.layout(document, runBlocking { JvmPosterFonts.load() }).code
            val scale = JvmPosterRenderer.DPI / 72.0
            val pad = 24
            val left = ((code.x * scale).roundToInt() - pad).coerceAtLeast(0)
            val top = ((code.y * scale).roundToInt() - pad).coerceAtLeast(0)
            val side = ((code.side * scale).roundToInt() + 2 * pad).coerceAtMost(minOf(image.width - left, image.height - top))
            val luminance = ByteArray(side * side) { i -> (image.getRGB(left + i % side, top + i / side) and 0xFF).toByte() }
            val text = assertNotNull(JvmQr.decode(luminance, side, side))
            assertEquals(document.code, text)
            val read = assertIs<PosterScan.Event>(PosterQr.read(text))
            assertEquals(paris, read.event.firstTagValue("d"))
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun onTheComputerTheScanOfAPosterIsNotOffered() = runComposeUiTest {
        assertFalse(canScanQr, "the computer has no camera here")
        setContent { PosterAgenda(AgendaDevice(withAccount = false), PosterExporter { PosterExportResult.DONE }) }
        waitUntil(timeoutMillis = 20_000) { cardCount() == 7 }
        onNodeWithTag(AgendaTags.PROPOSE).assertExists()
        onNodeWithTag(PosterTags.SCAN).assertDoesNotExist()
    }

    @Test
    fun noPosterOfACancelledOrPastMobilization() = runComposeUiTest {
        setContent { PosterAgenda(AgendaDevice(withAccount = false), PosterExporter { PosterExportResult.DONE }) }
        waitUntil(timeoutMillis = 20_000) { cardCount() == 7 }
        onNodeWithTag(AgendaTags.CARD + cancelled).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(AgendaTags.DETAIL).assertExists()
        onNodeWithTag(PosterTags.MAKE).assertDoesNotExist()
        onNodeWithTag(AgendaTags.TAB + "past").performClick()
        waitForIdle()
        onNodeWithTag(AgendaTags.CARD + rennes).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(PosterTags.MAKE).assertDoesNotExist()
    }

    /** The scan screen with a code already read (the computer has no camera here). */
    @Composable
    private fun Scan(device: AgendaDevice, scan: PosterScan, onReady: (AgendaLibrary) -> Unit, onOpened: () -> Unit = {}) {
        val scope = rememberCoroutineScope()
        val agenda = remember { AgendaLibrary(device.store, device.state, EditorialTrust.bundledExamples(), null, scope, clock = { AgendaClock.NOW }).also { it.start() } }
        remember { onReady(agenda) }
        val names = remember { AgendaNames({ null }, { agenda.isEditorial(it) }) }
        PpfTheme {
            Box(Modifier.requiredSize(400.dp, 900.dp)) {
                PosterScanScreen(agenda, names, compact = true, onBack = {}, onOpened = onOpened, initial = scan)
            }
        }
    }

    @Test
    fun aMobilizationReadOnAPosterIsVerifiedThenAddedToTheAgendaWithoutNetwork() = runComposeUiTest {
        val device = AgendaDevice(withAccount = false)
        val event = AgendaKit().mobilization("reunion-affiche", "[EXEMPLE] Réunion lue sur une affiche", start = AgendaClock.at(2026, 11, 20, 18))
        val address = assertNotNull(Mobilization.from(event)).address.toString()
        val text = assertNotNull(PosterQr.encodeEvent(event))
        var library: AgendaLibrary? = null
        var opened = false
        setContent { Scan(device, PosterQr.read(text), onReady = { library = it }, onOpened = { opened = true }) }
        onNodeWithText("Signature vérifiée", substring = true).assertExists()
        onNodeWithTag(PosterTags.SCAN_SIGNER).assertTextContains("que l’application ne connaît pas", substring = true)
        onNodeWithText("Réunion lue sur une affiche").assertExists()
        onNodeWithTag(PosterTags.SCAN_ADD).performScrollTo().performClick()
        waitUntil(timeoutMillis = 10_000) { library?.knows(address) == true }
        waitForIdle()
        onNodeWithText("Ajouté à votre agenda", substring = true).assertExists()
        onNodeWithTag(PosterTags.SCAN_OPEN).performScrollTo().performClick()
        assertTrue(opened)
        assertEquals("reunion-affiche", library?.selected?.identifier)
        // Kept as an event received, never as something to send: there is no server, and nothing waits for one.
        assertNotNull(device.store.find(event.id))
        assertTrue(device.store.pending().isEmpty())
    }

    @Test
    fun aForgedCodeIsRefusedWithAClearMessage() = runComposeUiTest {
        val device = AgendaDevice(withAccount = false)
        val event = AgendaKit().mobilization("reunion-affiche", "[EXEMPLE] Réunion lue sur une affiche", start = AgendaClock.at(2026, 11, 20, 18))
        val forged = event.copy(content = "[EXEMPLE] Rendez-vous ailleurs")
        val text = PosterQr.PREFIX + Base45.encode(assertNotNull(Gzip.compress(forged.toJson().encodeToByteArray())))
        setContent { Scan(device, PosterQr.read(text), onReady = {}) }
        onNodeWithText("Signature invalide", substring = true).assertExists()
        onNodeWithTag(PosterTags.SCAN_ADD).assertDoesNotExist()
        assertNull(device.store.find(forged.id))
        // Another code can be read at once.
        onNodeWithTag(PosterTags.SCAN_AGAIN).assertExists()
    }
}
