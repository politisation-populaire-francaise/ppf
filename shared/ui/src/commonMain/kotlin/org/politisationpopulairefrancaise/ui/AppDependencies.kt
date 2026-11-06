package org.politisationpopulairefrancaise.ui

import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.IdentityStore
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.social.FollowStore

/**
 * What each platform host (Android activity, desktop window, iOS view controller) provides to
 * the shared interface. Keeping it explicit avoids global state and makes tests trivial.
 */
class AppDependencies(
    val identityStore: IdentityStore,
    /** The offline copy of the contents; null means no local database (contents are then unavailable). */
    val contentStore: LocalEventStore? = null,
    /** Which authors' editorial contents are shown as PPF contents. */
    val editorialTrust: EditorialTrust = EditorialTrust.bundledExamples(),
    /** The server, when one is configured; null keeps the application fully offline. */
    val remote: RemoteEvents? = null,
    /**
     * The accounts the user follows, kept on the device (never sent to the server as a list).
     * Null keeps the list in memory for the life of the process only (tests, previews).
     */
    val followStore: FollowStore? = null,
    /**
     * The local database itself, for the stores that other modules build on it (messages, rooms);
     * null means no local database.
     */
    val database: PpfDatabase? = null,
) {
    companion object {
        /** For tests and previews: nothing survives the process, no contents, no network. */
        fun inMemory(): AppDependencies = AppDependencies(identityStore = InMemoryIdentityStore())

        /**
         * The usual wiring of a platform host: every store of the device (contents, followed accounts)
         * is built on the same local [database], opened once by the platform.
         */
        fun withDatabase(
            identityStore: IdentityStore,
            database: PpfDatabase,
            editorialTrust: EditorialTrust = EditorialTrust.bundledExamples(),
            remote: RemoteEvents? = null,
        ): AppDependencies = AppDependencies(
            identityStore = identityStore,
            contentStore = LocalEventStore(database),
            editorialTrust = editorialTrust,
            remote = remote,
            followStore = FollowStore(database),
            database = database,
        )
    }
}
