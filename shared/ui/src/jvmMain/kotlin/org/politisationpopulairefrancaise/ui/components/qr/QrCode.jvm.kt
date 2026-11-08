package org.politisationpopulairefrancaise.ui.components.qr

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.WriterException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.qr_scan_phone_only

actual fun encodeQr(text: String): QrMatrix? = JvmQr.encode(text)

/** The computer has no scanner here: a code is read with a phone. */
actual val canScanQr: Boolean = false

@Composable
actual fun QrScanner(onRead: (String) -> Unit, modifier: Modifier) {
    Text(stringResource(Res.string.qr_scan_phone_only), style = MaterialTheme.typography.bodyMedium, modifier = modifier.testTag(QrTags.SCANNER))
}

/** ZXing on the computer (the same calls as on Android, where they also read the camera's images). */
internal object JvmQr {
    fun encode(text: String): QrMatrix? = try {
        val hints = mapOf(EncodeHintType.CHARACTER_SET to "UTF-8")
        val matrix = Encoder.encode(text, ErrorCorrectionLevel.M, hints).matrix
        QrMatrix(matrix.width, BooleanArray(matrix.width * matrix.height) { i -> matrix.get(i % matrix.width, i / matrix.width) == ONE })
    } catch (e: WriterException) {
        null
    }

    /** The text of the QR code in a grey image ([luminance], one byte per pixel, [width] by [height]), or null. */
    fun decode(luminance: ByteArray, width: Int, height: Int): String? = try {
        val source = PlanarYUVLuminanceSource(luminance, width, height, 0, 0, width, height, false)
        QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), mapOf(DecodeHintType.TRY_HARDER to true, DecodeHintType.CHARACTER_SET to "UTF-8")).text
    } catch (e: ReaderException) {
        null
    }

    private const val ONE: Byte = 1
}
