package org.politisationpopulairefrancaise.ui.agir.agenda

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.agenda.ICalendar
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.agenda_export_share_title
import java.io.File

/**
 * On Android, the calendar file is shared through the system's share sheet (messaging, mail,
 * files, calendar applications that import .ics files), with the phone's calendar application
 * offered first, prefilled (`ACTION_INSERT`: the user still confirms in that application). No
 * permission is needed. The file lives in the application's cache, in `agenda/`, and is served
 * read-only by the `FileProvider` the host declares in its manifest (authority
 * `<applicationId>.agenda`, path name `agenda`): only the application chosen by the user gets a
 * temporary right to read it. Each export replaces the previous file.
 */
@Composable
actual fun rememberPlatformCalendarExporter(): CalendarExporter {
    val context = LocalContext.current
    val title = stringResource(Res.string.agenda_export_share_title)
    return remember(context, title) { CalendarExporter { fileName, content, mobilization -> share(context, title, fileName, content, mobilization) } }
}

private suspend fun share(context: Context, title: String, fileName: String, content: String, mobilization: Mobilization): CalendarExportResult = try {
    val name = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        File(directory, fileName).writeText(content, Charsets.UTF_8)
        fileName
    }
    val uri = Uri.parse("content://${context.packageName}.$AUTHORITY_SUFFIX/$DIRECTORY/${Uri.encode(name)}")
    val send = Intent(Intent.ACTION_SEND).apply {
        type = ICalendar.MEDIA_TYPE
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    // The calendar of the phone, prefilled; the share sheet skips it when no application handles it.
    val insert = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, mobilization.start * 1_000)
        mobilization.end?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it * 1_000) }
        putExtra(CalendarContract.Events.TITLE, mobilization.title)
        putExtra(CalendarContract.Events.EVENT_LOCATION, mobilization.location)
        putExtra(CalendarContract.Events.DESCRIPTION, listOfNotNull(mobilization.summary, mobilization.description.takeIf { it.isNotBlank() }).joinToString("\n\n"))
        putExtra(CalendarContract.Events.EVENT_TIMEZONE, "Europe/Paris")
    }
    val chooser = Intent.createChooser(send, title).apply {
        putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(insert))
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
    CalendarExportResult.DONE
} catch (e: CancellationException) {
    throw e
} catch (e: ActivityNotFoundException) {
    CalendarExportResult.FAILED
} catch (e: Exception) {
    CalendarExportResult.FAILED
}

private const val DIRECTORY = "agenda"
private const val AUTHORITY_SUFFIX = "agenda"
