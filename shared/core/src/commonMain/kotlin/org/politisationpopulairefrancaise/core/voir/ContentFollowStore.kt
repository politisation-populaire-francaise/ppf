package org.politisationpopulairefrancaise.core.voir

import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/**
 * The contents of "Voir" the user follows (indicators, public persons, bills, facts), kept on the
 * device only: following a person or a bill can reveal a political opinion (GDPR, article 9), so
 * the list is never sent anywhere. It needs no account, like reading.
 */
class ContentFollowStore(private val database: PpfDatabase, private val clock: () -> Long = ::currentEpochSeconds) {

    private val queries get() = database.contentFollowQueries

    /** Follows a content; following it again keeps the first date. */
    fun follow(address: ContentAddress) {
        queries.insert(address.toString(), clock())
    }

    fun unfollow(address: ContentAddress) {
        queries.deleteByAddress(address.toString())
    }

    /** Every followed content, most recently followed first; unreadable rows are skipped. */
    fun all(): List<ContentAddress> = queries.selectAll().executeAsList().mapNotNull { runCatching { ContentAddress.parse(it) }.getOrNull() }

    fun clear() {
        queries.deleteAll()
    }
}
