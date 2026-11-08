package org.politisationpopulairefrancaise.ui.components.qr

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.urne.ValidationProof
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The common QR code component: what is drawn is read back exactly; the computer shows that a phone reads. */
@OptIn(ExperimentalTestApi::class)
class QrCodeTest {
    /** The grey image of [matrix], [scale] pixels per module, with the quiet zone of the standard. */
    private fun raster(matrix: QrMatrix, scale: Int = 4, quiet: Int = 4): Triple<ByteArray, Int, Int> {
        val side = (matrix.size + 2 * quiet) * scale
        val pixels = ByteArray(side * side) { 0xFF.toByte() }
        for (y in 0 until matrix.size) for (x in 0 until matrix.size) {
            if (!matrix[x, y]) continue
            for (dy in 0 until scale) for (dx in 0 until scale) pixels[((y + quiet) * scale + dy) * side + (x + quiet) * scale + dx] = 0
        }
        return Triple(pixels, side, side)
    }

    @Test
    fun theProofOfAnAccountIsReadBackExactly() = runTest {
        // What the citizen shows a validator (D-112, 4.3): a signed event of a few hundred bytes, accents included.
        val proof = ValidationProof.sign(LocalEventSigner(IdentityKeys.generate()), 1_794_000_000).toJson()
        val matrix = assertNotNull(encodeQr(proof))
        val (pixels, width, height) = raster(matrix)
        assertEquals(proof, JvmQr.decode(pixels, width, height))
        val (accents, accentsWidth, accentsHeight) = raster(encodeQr("Réunion à l’école")!!)
        assertEquals("Réunion à l’école", JvmQr.decode(accents, accentsWidth, accentsHeight))
        // An image without code reads nothing; a text far too long for a QR code gives none.
        assertNull(JvmQr.decode(ByteArray(100 * 100) { 0xFF.toByte() }, 100, 100))
        assertNull(encodeQr("x".repeat(10_000)))
        assertFalse(canScanQr, "the computer shows codes, a phone reads them")
    }

    @Test
    fun theCodeIsDrawnWithItsDescriptionAndTheComputerSaysAPhoneReads() = runComposeUiTest {
        setContent {
            PpfTheme {
                QrCode("ppf", contentDescription = "Code de mon compte", modifier = Modifier.size(200.dp))
                QrScanner(onRead = {})
            }
        }
        onNodeWithTag(QrTags.CODE).assertContentDescriptionEquals("Code de mon compte")
        onNodeWithText("caméra d’un téléphone", substring = true).assertExists()
    }
}
