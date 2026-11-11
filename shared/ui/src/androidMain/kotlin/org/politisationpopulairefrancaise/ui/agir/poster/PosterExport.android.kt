package org.politisationpopulairefrancaise.ui.agir.poster

import android.content.Context
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.agir.poster.PosterColor
import org.politisationpopulairefrancaise.core.agir.poster.PosterElement
import org.politisationpopulairefrancaise.core.agir.poster.PosterLayouts
import org.politisationpopulairefrancaise.core.agir.poster.PosterMeasure
import org.politisationpopulairefrancaise.ui.components.qr.encodeQr
import java.io.ByteArrayOutputStream
import java.io.File

/** Android saves a PDF page drawn by the system: text and code stay sharp at any printing size. */
actual val posterFileKind: PosterFileKind? = PosterFileKind.PDF

/**
 * On Android, the poster is drawn on a PDF page by the system (`PdfDocument`, page in points: A4
 * 595 x 842, A3 842 x 1191), then written where the person chooses in the system's document dialog
 * (`ACTION_CREATE_DOCUMENT`): nothing reaches shared storage without that choice, and no permission
 * is needed. The page is drawn before the dialog opens, in memory, and dropped once written.
 */
@Composable
actual fun rememberPlatformPosterExporter(): PosterExporter {
    val context = LocalContext.current.applicationContext
    val pending = remember { PendingPoster() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PosterFileKind.PDF.mediaType)) { uri ->
        val answer = pending.answer
        pending.answer = null
        answer?.complete(uri)
    }
    return remember(launcher, context) {
        PosterExporter { document -> savePdf(context, document, pending) { name -> launcher.launch(name) } }
    }
}

/** The answer of the document dialog the exporter waits for. */
private class PendingPoster {
    var answer: CompletableDeferred<Uri?>? = null
}

private suspend fun savePdf(context: Context, document: PosterDocument, pending: PendingPoster, ask: (String) -> Unit): PosterExportResult {
    return try {
        val fonts = AndroidPosterFonts.load(context)
        val bytes = withContext(Dispatchers.Default) { AndroidPosterRenderer.pdf(document, fonts) } ?: return PosterExportResult.FAILED
        val answer = CompletableDeferred<Uri?>()
        pending.answer?.complete(null)
        pending.answer = answer
        ask(document.fileName + "." + PosterFileKind.PDF.extension)
        val uri = answer.await() ?: return PosterExportResult.CANCELLED
        val written = withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null }
        if (written) PosterExportResult.DONE else PosterExportResult.FAILED
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        PosterExportResult.FAILED
    }
}

/**
 * The typeface of the identity for the PDF. Android makes a typeface from a file only (API 26), so
 * the two font files of the application are copied into its cache (`cache/affiche/`, the same public
 * files at each export, nothing personal); the system sans serif when that fails. A character the
 * typeface lacks is drawn by the system's fallback fonts.
 */
internal class AndroidPosterFonts(val regular: Typeface, val bold: Typeface) {
    companion object {
        private const val DIRECTORY: String = "affiche"

        suspend fun load(context: Context): AndroidPosterFonts {
            val bytes = posterFontBytes()
            return withContext(Dispatchers.IO) {
                val directory = File(context.cacheDir, DIRECTORY)
                AndroidPosterFonts(
                    regular = bytes?.first?.let { typeface(directory, "regular.ttf", it) } ?: Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL),
                    bold = bytes?.second?.let { typeface(directory, "bold.ttf", it) } ?: Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD),
                )
            }
        }

        private fun typeface(directory: File, name: String, bytes: ByteArray): Typeface? = runCatching {
            directory.mkdirs()
            val file = File(directory, name)
            file.writeBytes(bytes)
            Typeface.createFromFile(file)
        }.getOrNull()
    }
}

/** Draws the common layout ([PosterLayouts]) on one PDF page: points are the units of the page. */
internal object AndroidPosterRenderer {
    /** The PDF file of the poster, or null when its code cannot be drawn. */
    fun pdf(document: PosterDocument, fonts: AndroidPosterFonts): ByteArray? {
        val matrix = encodeQr(document.code) ?: return null
        val regular = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = fonts.regular }
        val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = fonts.bold }
        val measure = PosterMeasure { text, size, isBold ->
            val paint = if (isBold) bold else regular
            paint.textSize = size
            paint.measureText(text)
        }
        val layout = PosterLayouts.layout(document.text, document.format, measure)
        val fill = Paint().apply { style = Paint.Style.FILL }
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(document.format.widthPt, document.format.heightPt, 1).create())
            val canvas = page.canvas
            for (element in layout.elements) {
                when (element) {
                    is PosterElement.TextLine -> {
                        val paint = if (element.bold) bold else regular
                        paint.textSize = element.size
                        paint.color = argb(element.color)
                        canvas.drawText(element.text, element.x, element.baseline, paint)
                    }
                    is PosterElement.Rule -> {
                        fill.color = argb(element.color)
                        canvas.drawRect(element.x, element.y, element.x + element.width, element.y + element.height, fill)
                    }
                    is PosterElement.Code -> {
                        // One path for every dark module: adjacent modules merge, without a hairline between them.
                        val modules = matrix.size + 2 * POSTER_QUIET_ZONE
                        val module = element.side / modules
                        val path = Path()
                        for (y in 0 until matrix.size) {
                            for (x in 0 until matrix.size) {
                                if (!matrix[x, y]) continue
                                val left = element.x + (x + POSTER_QUIET_ZONE) * module
                                val top = element.y + (y + POSTER_QUIET_ZONE) * module
                                path.addRect(left, top, left + module, top + module, Path.Direction.CW)
                            }
                        }
                        fill.color = argb(PosterColor.INK)
                        canvas.drawPath(path, fill)
                    }
                }
            }
            pdf.finishPage(page)
            val out = ByteArrayOutputStream()
            pdf.writeTo(out)
            return out.toByteArray()
        } finally {
            pdf.close()
        }
    }

    private fun argb(color: PosterColor): Int = OPAQUE or color.rgb

    private const val OPAQUE: Int = -0x1000000
}
