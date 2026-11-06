package org.politisationpopulairefrancaise.ui

import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.IdentityStore
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.publication.RemoteEvents

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
) {
    companion object {
        /** For tests and previews: nothing survives the process, no contents, no network. */
        fun inMemory(): AppDependencies = AppDependencies(identityStore = InMemoryIdentityStore())
    }
}
