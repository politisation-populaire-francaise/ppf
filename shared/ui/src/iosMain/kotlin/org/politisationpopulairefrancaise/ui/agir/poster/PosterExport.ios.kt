package org.politisationpopulairefrancaise.ui.agir.poster

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Not available yet on iOS: the application is not built for it until the Apple Developer account
 * exists, and the common code draws no QR code there yet (`encodeQr` returns null). The poster
 * screen says so; the PDF will come with the iOS build (UIGraphicsPDFRenderer and the share sheet).
 */
actual val posterFileKind: PosterFileKind? = null

@Composable
actual fun rememberPlatformPosterExporter(): PosterExporter = remember { PosterExporter { PosterExportResult.UNSUPPORTED } }
