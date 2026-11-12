package org.politisationpopulairefrancaise.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentWeek
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentWeekFormat
import org.politisationpopulairefrancaise.server.events.EventFilter
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.web.LivePostsSource
import org.politisationpopulairefrancaise.web.ParliamentWeekSource
import java.util.concurrent.atomic.AtomicReference

/*
 * The sources of « En direct » on the site that the application reads from elsewhere: the short posts of PPF and
 * « Cette semaine au Parlement ».
 */

/**
 * The short posts of PPF: the latest notes, those signed by an editorial key only (the site never shows a post of
 * a citizen; those are in the application, with their own rules). The server checked the signatures when it stored
 * them.
 */
fun livePostsSource(events: EventRepository, isEditor: (String) -> Boolean): LivePostsSource = LivePostsSource {
    withContext(Dispatchers.IO) {
        events.query(EventFilter(kinds = listOf(EventKinds.TEXT_NOTE), limit = EventFilter.MAX_LIMIT))
    }.filter { isEditor(it.pubkey) }
}

/**
 * The package `semaine` of the folder of open data (`PPF_OPEN_DATA_DIR`, written by the import tool), read again
 * only when the file changes: « En direct » is the home of the site. Null without the folder.
 */
fun parliamentWeekSource(config: OpenDataConfig = OpenDataConfig.fromEnvironment()): ParliamentWeekSource? {
    val directory = config.directory ?: return null
    val cache = AtomicReference<Pair<String, ParliamentWeek?>?>(null)
    return ParliamentWeekSource {
        withContext(Dispatchers.IO) {
            val bytes = readOpenDataPackage(directory, ParliamentWeekFormat.PACKAGE) ?: return@withContext null
            val key = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            cache.get()?.takeIf { it.first == key }?.second
                ?: ParliamentWeekFormat.read(bytes.decodeToString())?.let(::ParliamentWeek).also { cache.set(key to it) }
        }
    }
}
