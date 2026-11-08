package org.politisationpopulairefrancaise.core.editorial

import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.SignedEvent

/**
 * « Vérifier un contenu PPF » (S-141) on the device, even offline: the editorial keys the application trusts
 * ([EditorialTrust]) and its local copy of the contents ([LocalEventStore]). The trusted contents are read once,
 * at the first question, at most [limit] of them; nothing is sent anywhere.
 */
class DeviceContentLookup(
    private val store: LocalEventStore,
    private val trust: EditorialTrust,
    private val limit: Int = MAX_EVENTS,
) : ContentLookup {
    private val trusted: List<SignedEvent> by lazy { store.recent(KINDS, limit).filter { trust.isTrusted(it.pubkey) } }

    override fun isTrusted(pubkeyHex: String): Boolean = trust.isTrusted(pubkeyHex)

    override fun byId(id: String): SignedEvent? = store.find(id)

    override fun byCode(prefix: String): List<SignedEvent> = trusted.filter { it.id.startsWith(prefix) }.take(2)

    override fun latestAt(address: ContentAddress): SignedEvent? = trusted
        .filter { ContentAddress.of(it) == address }
        // The same rule as the server: the most recent wins, the smaller id at the same second.
        .minWithOrNull(compareByDescending<SignedEvent> { it.createdAt }.thenBy { it.id })

    companion object {
        /** The kinds the team publishes (long-form contents and the agenda), as in the open export. */
        val KINDS: List<Int> = listOf(30023, 31923)
        const val MAX_EVENTS: Int = 5_000
    }
}
