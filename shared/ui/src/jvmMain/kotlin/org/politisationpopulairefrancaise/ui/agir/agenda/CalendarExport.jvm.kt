package org.politisationpopulairefrancaise.ui.agir.agenda

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.agenda_export_save_title
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlin.coroutines.resume

/**
 * On the desktop, the calendar file is saved where the user chooses, through the system's save
 * dialog; any calendar application (Thunderbird, Outlook, GNOME Agenda...) can then import it.
 */
@Composable
actual fun rememberPlatformCalendarExporter(): CalendarExporter {
    val title = stringResource(Res.string.agenda_export_save_title)
    return remember(title) { CalendarExporter { fileName, content, _ -> save(title, fileName, content) } }
}

private suspend fun save(title: String, fileName: String, content: String): CalendarExportResult {
    return try {
        val file = choose(title, fileName) ?: return CalendarExportResult.CANCELLED
        withContext(Dispatchers.IO) { file.writeText(content, Charsets.UTF_8) }
        CalendarExportResult.DONE
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        CalendarExportResult.FAILED
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
    return File(directory, if (name.endsWith(".ics", ignoreCase = true)) name else "$name.ics")
}
