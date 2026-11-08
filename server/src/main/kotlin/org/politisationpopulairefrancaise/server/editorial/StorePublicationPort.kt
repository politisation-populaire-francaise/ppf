package org.politisationpopulairefrancaise.server.editorial

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.publication.PublicationPort
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RejectionReason
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.events.StoreOutcome

/**
 * The single publication port (CLAUDE.md, section 7) as the server itself uses it for a content it
 * publishes (phase 1 adapter: the events of this server). The same rules as the public API, in the same
 * order: the signature, then the publication policy (an editorial content only from an editorial key),
 * then the store, which keeps the newest version of an address. A version older than the stored one is
 * refused ([RejectionReason.REFUSED]): it would never be shown.
 */
class StorePublicationPort(private val repository: EventRepository, private val policy: PublicationPolicy) : PublicationPort {
    override suspend fun publish(event: SignedEvent): PublicationResult {
        if (!event.verify()) return PublicationResult.Rejected(event.id, RejectionReason.INVALID_SIGNATURE)
        if (policy.check(event) is PublicationPolicy.Decision.Refused) return PublicationResult.Rejected(event.id, RejectionReason.REFUSED)
        return try {
            when (withContext(Dispatchers.IO) { repository.store(event) }) {
                StoreOutcome.STORED, StoreOutcome.DUPLICATE -> PublicationResult.Accepted(event.id)
                StoreOutcome.SUPERSEDED -> PublicationResult.Rejected(event.id, RejectionReason.REFUSED)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            PublicationResult.Unavailable(e)
        }
    }
}
