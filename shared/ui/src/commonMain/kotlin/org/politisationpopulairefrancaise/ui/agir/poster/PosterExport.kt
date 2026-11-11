package org.politisationpopulairefrancaise.ui.agir.poster

import androidx.compose.runtime.Composable
import org.politisationpopulairefrancaise.core.agir.poster.PosterFormat
import org.politisationpopulairefrancaise.core.agir.poster.PosterText
import org.politisationpopulairefrancaise.ui.resources.Res

/** What a platform saves a poster as. */
enum class PosterFileKind(val extension: String, val mediaType: String) {
    /** Android: a PDF page drawn by the system (`android.graphics.pdf.PdfDocument`), vector text and code. */
    PDF("pdf", "application/pdf"),

    /** The computer: an image at 150 dots per inch (the JDK writes no PDF without a library). */
    PNG("png", "image/png"),
}

/**
 * Everything a platform needs to draw and save one poster (AG-61): the page, its words, the text of
 * its code and the name proposed for the file (without extension). Built when the person asks for
 * the file, dropped once it is written: nothing of it is kept anywhere else.
 */
data class PosterDocument(val format: PosterFormat, val text: PosterText, val code: String, val fileName: String)

/** How saving a poster ended. */
enum class PosterExportResult {
    /** Written where the person chose. */
    DONE,

    /** The person closed the save dialog. */
    CANCELLED,

    /** This platform cannot save a poster yet (iOS until its application is built). */
    UNSUPPORTED,

    FAILED,
}

/**
 * Draws a poster on the device and saves it where the person chooses: the system's document dialog
 * on Android (nothing is written in shared storage without that choice), a save dialog on the
 * computer. Never any network.
 */
fun interface PosterExporter {
    suspend fun export(document: PosterDocument): PosterExportResult
}

/** The kind of file this platform saves, or null where a poster cannot be saved yet (iOS). */
expect val posterFileKind: PosterFileKind?

/** The exporter of the platform the application runs on. */
@Composable
expect fun rememberPlatformPosterExporter(): PosterExporter

/** Modules of light margin around the code, as the standard asks (the same as the common component). */
internal const val POSTER_QUIET_ZONE: Int = 4

/**
 * The two weights of the typeface of the identity (Atkinson Hyperlegible), read from the resources
 * of the application; null when they cannot be read, and the platform then prints in its own sans
 * serif typeface.
 */
internal suspend fun posterFontBytes(): Pair<ByteArray, ByteArray>? = runCatching {
    Res.readBytes(POSTER_FONT_REGULAR) to Res.readBytes(POSTER_FONT_BOLD)
}.getOrNull()

internal const val POSTER_FONT_REGULAR: String = "font/atkinson_hyperlegible_regular.ttf"
internal const val POSTER_FONT_BOLD: String = "font/atkinson_hyperlegible_bold.ttf"
