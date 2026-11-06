package org.politisationpopulairefrancaise.core.revendications.checks

/**
 * Where the server looks for the legal texts in force that may already answer a demand
 * ("loi déjà existante et en vigueur", request of the owner, 2026-11-06). The source is still to
 * be chosen (brainstorming of the cahier des charges, entry of 2026-11-06 on the legal check):
 * the open data of Légifrance indexed by the server, or the Légifrance API. Until then the server
 * runs without one and the check says so.
 */
fun interface LegalCorpus {
    /** Texts in force whose words match [keywords], most relevant first, at most [limit]. */
    suspend fun search(keywords: List<String>, limit: Int): LegalSearch
}

sealed interface LegalSearch {
    data class Found(val references: List<LegalReference>) : LegalSearch

    data class Unavailable(val cause: Throwable? = null) : LegalSearch
}

/**
 * The AI model that reads a proposed demand on the server, looking for what words cannot see:
 * duplicates in other words, contradictions, a law that already exists, several ideas, the
 * charter (red and orange alerts, cahier des charges 5.6). Its hosting is still to be decided by
 * the owner (brainstorming of the cahier des charges, "Modération et assistant IA : hébergement du
 * modèle"); whatever the choice, the server talks to it through the same interface.
 *
 * The model only gives opinions: it never blocks, publishes or refuses anything. A person reviews
 * every demand before publication.
 */
fun interface ProposalReviewModel {
    suspend fun review(request: ReviewRequest): ReviewOutcome
}

/** What the model receives: the draft, and only the material it may refer to. */
data class ReviewRequest(
    val draft: RevendicationDraft,
    /** Existing demands the model may name as duplicates or contradictions (and only those). */
    val candidates: List<DemandText>,
    /** Texts found in the legal corpus, if any: the only references the model may mark as verified. */
    val legal: List<LegalReference> = emptyList(),
)

sealed interface ReviewOutcome {
    data class Reviewed(val issues: List<ProposalIssue>, val model: String) : ReviewOutcome

    /** The model could not be asked or its answer could not be read; the team checks by hand. */
    data class Failed(val reason: String) : ReviewOutcome
}
