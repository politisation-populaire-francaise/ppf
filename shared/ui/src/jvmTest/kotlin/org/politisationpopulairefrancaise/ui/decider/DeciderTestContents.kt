package org.politisationpopulairefrancaise.ui.decider

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.signing.SignedEvent

/**
 * The fictional contents of « Décider » the tests read (petitions, election, model letter, all marked [EXEMPLE]):
 * written in `content/tests/decider`, signed by `:tools:content-signer:run` into `contents/decider-tests.json` of the
 * resources of the tests, never bundled with the application, which carries real contents (owner's decision of
 * 9 November 2026). Signed with the key of the bundled examples, so `EditorialTrust.bundledExamples()` trusts them.
 */
internal object DeciderTestContents {
    const val RESOURCE: String = "/contents/decider-tests.json"

    val events: List<SignedEvent> by lazy {
        val text = checkNotNull(DeciderTestContents::class.java.getResource(RESOURCE)) { "$RESOURCE missing: run :tools:content-signer:run" }.readText()
        Json.decodeFromString(ListSerializer(SignedEvent.serializer()), text)
    }

    /** Stores them in [store], as the application imports its bundled contents, before the library starts. */
    fun seed(store: LocalEventStore) {
        events.forEach { store.store(it) }
    }
}
