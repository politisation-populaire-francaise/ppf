package org.politisationpopulairefrancaise.ui.components.qr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.qr_unavailable

/**
 * The common QR code component of the applications (team lead's decision of 8 November 2026): drawn here, for
 * every space that shows a code (the in-person validation of Décider, Agir AG-61, the invitations and the check
 * of contacts of Communauté), and read by [QrScanner]. Encoding and reading come from ZXing on Android and on the
 * computer (Apache 2.0, no network); never ML Kit. The camera is used on Android only, asked when a scanner is
 * shown, never at install. Nothing read or shown here is recorded or sent by this component.
 */
object QrTags {
    const val CODE: String = "qr-code"
    const val SCANNER: String = "qr-scanner"
}

/** The dark and light modules of a QR code, row after row, without its quiet zone. */
class QrMatrix(val size: Int, private val dark: BooleanArray) {
    init {
        require(size > 0 && dark.size == size * size) { "a square grid" }
    }

    operator fun get(x: Int, y: Int): Boolean = dark[y * size + x]
}

/** [text] (UTF-8, error correction M) as a QR code; null where this platform has no encoder, or when the text is too long. */
expect fun encodeQr(text: String): QrMatrix?

/** Whether this device reads QR codes with its camera ([QrScanner]). */
expect val canScanQr: Boolean

/**
 * Reads QR codes with the camera of the device; [onRead] receives the text of each code read (the same code may
 * come several times: the caller keeps what it needs). On Android, the camera permission is asked when the
 * scanner is first shown, with the reason; elsewhere, a line says that reading needs a phone.
 */
@Composable
expect fun QrScanner(onRead: (String) -> Unit, modifier: Modifier = Modifier)

/** Modules of light margin around the code, as the standard asks. */
private const val QUIET_ZONE = 4

/**
 * The QR code of [text], dark modules on white with its quiet zone. Black on white whatever the theme, on purpose:
 * the readers need the contrast of the standard (an inverted code in dark mode is not read by every camera).
 */
@Composable
fun QrCode(text: String, contentDescription: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { encodeQr(text) }
    if (matrix == null) {
        Text(stringResource(Res.string.qr_unavailable), style = MaterialTheme.typography.bodyMedium, modifier = modifier)
        return
    }
    Canvas(
        modifier
            .aspectRatio(1f)
            .background(Color.White)
            .semantics { this.contentDescription = contentDescription }
            .testTag(QrTags.CODE),
    ) {
        val cells = matrix.size + 2 * QUIET_ZONE
        val cell = size.minDimension / cells
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (matrix[x, y]) {
                    drawRect(Color.Black, topLeft = Offset((x + QUIET_ZONE) * cell, (y + QUIET_ZONE) * cell), size = Size(cell, cell))
                }
            }
        }
    }
}
