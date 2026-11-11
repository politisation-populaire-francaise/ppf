package org.politisationpopulairefrancaise.ui.agir.poster

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.poster.PosterColor
import org.politisationpopulairefrancaise.core.agir.poster.PosterElement
import org.politisationpopulairefrancaise.core.agir.poster.PosterLayout
import org.politisationpopulairefrancaise.core.agir.poster.PosterLayouts
import org.politisationpopulairefrancaise.core.agir.poster.PosterMeasure
import org.politisationpopulairefrancaise.ui.components.qr.encodeQr
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.affiche_save_title
import java.awt.Color
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Font
import java.awt.Frame
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadataNode
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/** The computer saves a PNG image: the JDK has no PDF writer, and no library is added for it (AG-61, step 1). */
actual val posterFileKind: PosterFileKind? = PosterFileKind.PNG

/** On the computer, the poster is drawn as an image and saved where the person chooses, through the system's save dialog. */
@Composable
actual fun rememberPlatformPosterExporter(): PosterExporter {
    val title = stringResource(Res.string.affiche_save_title)
    return remember(title) { PosterExporter { document -> savePng(title, document) } }
}

private suspend fun savePng(title: String, document: PosterDocument): PosterExportResult {
    return try {
        val fonts = JvmPosterFonts.load()
        val bytes = withContext(Dispatchers.Default) { JvmPosterRenderer.png(document, fonts) } ?: return PosterExportResult.FAILED
        val file = choose(title, document.fileName + "." + PosterFileKind.PNG.extension) ?: return PosterExportResult.CANCELLED
        withContext(Dispatchers.IO) { file.writeBytes(bytes) }
        PosterExportResult.DONE
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        PosterExportResult.FAILED
    }
}

/** The save dialog belongs to the event thread of the window system. */
private suspend fun choose(title: String, fileName: String): File? =
    if (EventQueue.isDispatchThread()) {
        pick(title, fileName)
    } else {
        suspendCancellableCoroutine { continuation ->
            EventQueue.invokeLater { continuation.resume(runCatching { pick(title, fileName) }.getOrNull()) }
        }
    }

private fun pick(title: String, fileName: String): File? {
    val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE)
    dialog.file = fileName
    dialog.isVisible = true
    val directory = dialog.directory ?: return null
    val name = dialog.file ?: return null
    return File(directory, if (name.endsWith(".png", ignoreCase = true)) name else "$name.png")
}

/**
 * The typeface of the identity for the image, made in memory from the resources of the application
 * (no file is written); the logical sans serif typeface of Java when it cannot be read.
 */
internal class JvmPosterFonts(private val regular: Font, private val bold: Font) {
    fun font(size: Float, bold: Boolean): Font = (if (bold) this.bold else regular).deriveFont(size)

    companion object {
        val SYSTEM: JvmPosterFonts = JvmPosterFonts(Font(Font.SANS_SERIF, Font.PLAIN, 12), Font(Font.SANS_SERIF, Font.BOLD, 12))

        suspend fun load(): JvmPosterFonts {
            val bytes = posterFontBytes() ?: return SYSTEM
            val regular = create(bytes.first) ?: return SYSTEM
            val bold = create(bytes.second) ?: return SYSTEM
            return JvmPosterFonts(regular, bold)
        }

        private fun create(bytes: ByteArray): Font? = runCatching { Font.createFont(Font.TRUETYPE_FONT, ByteArrayInputStream(bytes)) }.getOrNull()
    }
}

/**
 * Draws the common layout ([PosterLayouts]) on an image of the computer at [DPI] dots per inch,
 * white paper, black text and code. The code is drawn in whole pixels (each module the same number
 * of pixels, without smoothing), so that its edges stay sharp once printed.
 */
internal object JvmPosterRenderer {
    const val DPI: Int = 150

    /** 150 dots per inch, as PNG writes it (`pHYs`, dots per metre). */
    private const val DOTS_PER_METRE: String = "5906"

    private val context = FontRenderContext(null, true, true)

    fun measure(fonts: JvmPosterFonts): PosterMeasure = PosterMeasure { text, size, bold ->
        val line = printable(text)
        fontFor(fonts, line, size, bold).getStringBounds(line, context).width.toFloat()
    }

    fun layout(document: PosterDocument, fonts: JvmPosterFonts): PosterLayout = PosterLayouts.layout(document.text, document.format, measure(fonts))

    /** The poster as an image, or null when its code cannot be drawn. */
    fun image(document: PosterDocument, fonts: JvmPosterFonts): BufferedImage? {
        val matrix = encodeQr(document.code) ?: return null
        val layout = layout(document, fonts)
        val scale = DPI / 72.0
        val width = (document.format.widthPt * scale).roundToInt()
        val height = (document.format.heightPt * scale).roundToInt()
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        try {
            g.color = Color(PosterColor.PAPER.rgb)
            g.fillRect(0, 0, width, height)
            val base = g.transform
            g.scale(scale, scale)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            for (element in layout.elements) {
                when (element) {
                    is PosterElement.TextLine -> {
                        val line = printable(element.text)
                        g.font = fontFor(fonts, line, element.size, element.bold)
                        g.color = Color(element.color.rgb)
                        g.drawString(line, element.x, element.baseline)
                    }
                    is PosterElement.Rule -> {
                        g.color = Color(element.color.rgb)
                        g.fill(Rectangle2D.Float(element.x, element.y, element.width, element.height))
                    }
                    is PosterElement.Code -> Unit
                }
            }
            // The code, in whole pixels and without smoothing.
            g.transform = base
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
            val code = layout.code
            val modules = matrix.size + 2 * POSTER_QUIET_ZONE
            val box = (code.side * scale).toInt()
            val module = maxOf(1, box / modules)
            val left = (code.x * scale).roundToInt() + (box - module * modules) / 2
            val top = (code.y * scale).roundToInt() + (box - module * modules) / 2
            g.color = Color(PosterColor.INK.rgb)
            for (y in 0 until matrix.size) {
                for (x in 0 until matrix.size) {
                    if (matrix[x, y]) g.fillRect(left + (x + POSTER_QUIET_ZONE) * module, top + (y + POSTER_QUIET_ZONE) * module, module, module)
                }
            }
            g.transform = AffineTransform()
        } finally {
            g.dispose()
        }
        return image
    }

    /** The poster as a PNG file at [DPI] dots per inch, or null when it cannot be drawn. */
    fun png(document: PosterDocument, fonts: JvmPosterFonts): ByteArray? {
        val image = image(document, fonts) ?: return null
        val out = ByteArrayOutputStream()
        val writer = ImageIO.getImageWritersByFormatName("png").next()
        try {
            val metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), null)
            // The resolution, so that printing software gives the page its real size; the image stays valid without it.
            runCatching {
                val root = IIOMetadataNode(PNG_METADATA)
                val resolution = IIOMetadataNode("pHYs")
                resolution.setAttribute("pixelsPerUnitXAxis", DOTS_PER_METRE)
                resolution.setAttribute("pixelsPerUnitYAxis", DOTS_PER_METRE)
                resolution.setAttribute("unitSpecifier", "meter")
                root.appendChild(resolution)
                metadata.mergeTree(PNG_METADATA, root)
            }
            ImageIO.createImageOutputStream(out).use { stream ->
                writer.output = stream
                writer.write(null, IIOImage(image, null, metadata), null)
            }
        } finally {
            writer.dispose()
        }
        return out.toByteArray()
    }

    private const val PNG_METADATA: String = "javax_imageio_png_1.0"

    /** The narrow no-break space of French hours is printed as a no-break space, which every typeface has. */
    private fun printable(text: String): String = text.replace(' ', ' ')

    /** The typeface of the identity, or Java's sans serif for a line it cannot show entirely. */
    private fun fontFor(fonts: JvmPosterFonts, text: String, size: Float, bold: Boolean): Font {
        val font = fonts.font(size, bold)
        return if (font.canDisplayUpTo(text) == -1) font else JvmPosterFonts.SYSTEM.font(size, bold)
    }
}
