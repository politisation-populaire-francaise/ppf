package org.politisationpopulairefrancaise.ui.agir.agenda

import androidx.compose.runtime.Composable
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization

/** How the export of a calendar file ended. */
enum class CalendarExportResult {
    /** The file was handed to the system (share sheet) or saved where the user chose. */
    DONE,

    /** The user closed the share sheet or the save dialog. */
    CANCELLED,

    /** This platform cannot export yet (iOS until the application is built for it). */
    UNSUPPORTED,

    FAILED,
}

/**
 * Hands an iCalendar file ([org.politisationpopulairefrancaise.core.agir.agenda.ICalendar]) to the
 * platform: the share sheet on Android (with the calendar application offered first), a save
 * dialog on the desktop. The file is built on the device and nothing is sent anywhere by PPF.
 */
fun interface CalendarExporter {
    /** [fileName] ends with ".ics"; [content] is the whole file; [mobilization] lets a platform prefill its calendar. */
    suspend fun export(fileName: String, content: String, mobilization: Mobilization): CalendarExportResult
}

/** The exporter of the platform the application runs on. */
@Composable
expect fun rememberPlatformCalendarExporter(): CalendarExporter
