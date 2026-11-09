package org.politisationpopulairefrancaise.server.backoffice

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.politisationpopulairefrancaise.core.platform.RootRequestQr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The QR code of a request for the root, drawn as an inline SVG and read back with ZXing. */
class QrSvgTest {
    companion object {
        /**
         * Reads back the text of an SVG drawn by [QrSvg]: its path of dark rectangles drawn into an image (4 pixels
         * per module), then decoded by ZXing as a camera image would be.
         */
        fun decode(svg: String): String {
            val size = Regex("viewBox=\"0 0 (\\d+) (\\d+)\"").find(svg)!!.groupValues[1].toInt()
            val path = Regex("<path fill=\"#000000\" d=\"([^\"]*)\"").find(svg)!!.groupValues[1]
            val scale = 4
            val pixels = IntArray(size * scale * size * scale) { 0xFFFFFF }
            Regex("M(\\d+) (\\d+)h(\\d+)v1h-(\\d+)z").findAll(path).forEach { run ->
                val (x, y, width) = run.destructured
                for (module in x.toInt() until x.toInt() + width.toInt()) {
                    for (dy in 0 until scale) for (dx in 0 until scale) {
                        pixels[(y.toInt() * scale + dy) * size * scale + module * scale + dx] = 0x000000
                    }
                }
            }
            val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(size * scale, size * scale, pixels)))
            return QRCodeReader().decode(bitmap, mapOf(DecodeHintType.PURE_BARCODE to true)).text
        }
    }

    @Test
    fun theQrCodeOfARequestReadsBackWithoutAnyInlineStyle() {
        val text = RootRequestQr("preproduction", "0123456789abcdef0123456789abcdef", "ab".repeat(32)).text()
        val svg = QrSvg.render(text, "Code QR <de> la \"demande\"")
        assertEquals(text, decode(svg))
        assertTrue(svg.startsWith("<svg "))
        assertTrue("fill=\"#ffffff\"" in svg && "fill=\"#000000\"" in svg)
        assertFalse("style" in svg, "the policy of the pages forbids inline styles")
        assertFalse("<script" in svg)
        assertTrue("aria-label=\"Code QR &lt;de&gt; la &quot;demande&quot;\"" in svg)
        // The quiet zone: no dark module in the first and last 4 modules.
        val size = Regex("viewBox=\"0 0 (\\d+) ").find(svg)!!.groupValues[1].toInt()
        Regex("M(\\d+) (\\d+)h(\\d+)").findAll(svg).forEach { run ->
            val (x, y, width) = run.destructured.toList().map { it.toInt() }
            assertTrue(x >= QrSvg.QUIET_ZONE && y >= QrSvg.QUIET_ZONE && x + width <= size - QrSvg.QUIET_ZONE && y < size - QrSvg.QUIET_ZONE)
        }
    }
}
