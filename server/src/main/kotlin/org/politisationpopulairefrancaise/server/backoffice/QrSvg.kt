package org.politisationpopulairefrancaise.server.backoffice

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * A QR code drawn as an inline SVG for the pages of the back-office (D-141), with ZXing core (no network):
 * error correction M, black modules on a white square with a quiet zone of 4 modules, whatever the theme of
 * the page. Only SVG attributes (`fill`, `width`, `height`, `viewBox`), never a `style` attribute nor a
 * `<style>` element: the policy of the pages forbids inline styles. The text is ours (never a value typed
 * by someone), and the label is escaped.
 */
object QrSvg {
    /** The white margin around the code, in modules (the minimum of the standard). */
    const val QUIET_ZONE: Int = 4

    /** The modules of [text], without margin: `true` is a dark module. */
    fun matrix(text: String): BitMatrix =
        QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0))

    /**
     * The `<svg>` element of [text]: one white `<rect>` and one black `<path>` (a rectangle per run of dark
     * modules on a row), [pixelsPerModule] pixels per module, [label] read by screen readers.
     */
    fun render(text: String, label: String, pixelsPerModule: Int = 5): String {
        val modules = matrix(text)
        val size = modules.width + 2 * QUIET_ZONE
        val path = StringBuilder()
        for (y in 0 until modules.height) {
            var x = 0
            while (x < modules.width) {
                if (!modules.get(x, y)) {
                    x++
                    continue
                }
                val start = x
                while (x < modules.width && modules.get(x, y)) x++
                path.append("M").append(start + QUIET_ZONE).append(' ').append(y + QUIET_ZONE)
                    .append("h").append(x - start).append("v1h-").append(x - start).append('z')
            }
        }
        val pixels = size * pixelsPerModule
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $size $size\" width=\"$pixels\" height=\"$pixels\" " +
            "role=\"img\" aria-label=\"${escape(label)}\" shape-rendering=\"crispEdges\">" +
            "<rect width=\"$size\" height=\"$size\" fill=\"#ffffff\"/>" +
            "<path fill=\"#000000\" d=\"$path\"/></svg>"
    }

    private fun escape(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
}
