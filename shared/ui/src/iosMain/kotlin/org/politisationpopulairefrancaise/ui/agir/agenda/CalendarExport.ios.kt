package org.politisationpopulairefrancaise.ui.agir.agenda

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Not available yet on iOS: the application is not built for it until the Apple Developer account
 * exists. On an iPhone, the web site (with its iCalendar feed `/api/v1/agenda.ics`) is the way in
 * the meantime. The share sheet (`UIActivityViewController` with a temporary file) comes with the
 * iOS build.
 */
@Composable
actual fun rememberPlatformCalendarExporter(): CalendarExporter =
    remember { CalendarExporter { _, _, _ -> CalendarExportResult.UNSUPPORTED } }
