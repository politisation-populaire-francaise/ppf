package org.politisationpopulairefrancaise.ui.revendications.propose

import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.InMemoryPublicationPort
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.publication.TagFilter
import org.politisationpopulairefrancaise.core.revendications.ProposalState
import org.politisationpopulairefrancaise.core.revendications.ProposalStatus
import org.politisationpopulairefrancaise.core.revendications.ProposalStatusSource
import org.politisationpopulairefrancaise.core.revendications.ProposalStatuses
import org.politisationpopulairefrancaise.core.revendications.checks.IssueKind
import org.politisationpopulairefrancaise.core.revendications.checks.IssueSeverity
import org.politisationpopulairefrancaise.core.revendications.checks.IssueSource
import org.politisationpopulairefrancaise.core.revendications.checks.ProposalIssue
import org.politisationpopulairefrancaise.core.revendications.checks.ProposalReport
import org.politisationpopulairefrancaise.core.signing.SignedEvent

/** A server that holds citizen demands for review and answers where they stand, as the real one does. */
internal class ReviewingServer : RemoteEvents, ProposalStatusSource {
    private val published = InMemoryPublicationPort()
    val held = mutableListOf<SignedEvent>()
    var decision: ProposalStatus = ProposalStatus.PENDING

    override suspend fun publish(event: SignedEvent): PublicationResult {
        if (event.kind == 30023) {
            held += event
            return PublicationResult.Accepted(event.id, heldForReview = true)
        }
        return published.publish(event)
    }

    override suspend fun fetch(filter: RemoteFilter): FetchResult = published.fetch(filter)

    override suspend fun fetchByTags(tags: List<TagFilter>, filter: RemoteFilter): FetchResult = published.fetchByTags(tags, filter)

    override suspend fun proposalStatuses(ids: List<String>): ProposalStatuses = ProposalStatuses.Known(
        held.filter { it.id in ids }.map {
            ProposalState(
                id = it.id,
                status = decision,
                receivedAt = it.createdAt,
                reason = if (decision == ProposalStatus.REJECTED) "[EXEMPLE] Revendication déjà présente sur le mur." else null,
                report = ProposalReport(
                    issues = listOf(ProposalIssue(IssueKind.EXISTING_LAW, IssueSeverity.INFO, IssueSource.AI, detail = "[EXEMPLE] Avis de l'IA.")),
                    checks = emptyList(),
                    checkedAt = it.createdAt,
                ),
            )
        },
    )
}
