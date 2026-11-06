package org.politisationpopulairefrancaise.core.revendications.checks

import kotlinx.coroutines.CancellationException
import org.politisationpopulairefrancaise.core.editorial.RevendicationStatus
import org.politisationpopulairefrancaise.core.text.FrenchText

/**
 * Runs the automatic checks of a proposed demand and gathers them in one [ProposalReport]
 * (request of the owner, 2026-11-06: the AI must also handle duplicates, laws already in force and
 * the other possible problems).
 *
 * - On the device, while the author writes: the rules of the form and the comparison with the
 *   demands the device holds; the legal check and the AI check are announced for after sending.
 * - On the server, when the demand arrives for review: the same two, against every published and
 *   pending demand, then the legal corpus and the AI model when they are configured.
 *
 * Nothing here publishes or refuses: blocking issues come from the rules only, and a person
 * reviews every demand before publication.
 */
class ProposalChecker(
    private val legal: LegalCorpus? = null,
    private val model: ProposalReviewModel? = null,
    private val clock: () -> Long,
) {
    /** The checks the device can run alone, instantly; fit for every keystroke. */
    fun checkOnDevice(draft: RevendicationDraft, existing: List<DemandText>, ownAddress: String? = null): ProposalReport {
        val detector = SimilarityDetector(existing)
        val issues = DraftRules.check(draft) + similarityIssues(detector.compare(draft, ownAddress, limit = MAX_RELATED))
        return ProposalReport(
            issues = issues.sortedWith(ISSUE_ORDER),
            checks = listOf(
                CheckStatus(CheckKind.RULES, CheckState.DONE),
                CheckStatus(CheckKind.SIMILARITY, CheckState.DONE, compared = detector.size),
                CheckStatus(CheckKind.LEGAL, CheckState.AFTER_SENDING),
                CheckStatus(CheckKind.AI, CheckState.AFTER_SENDING),
            ),
            checkedAt = clock(),
        )
    }

    /** Every check the server can run; a failing service is reported as such, never thrown. */
    suspend fun checkOnServer(draft: RevendicationDraft, existing: List<DemandText>, ownAddress: String? = null): ProposalReport {
        val detector = SimilarityDetector(existing)
        val ranked = detector.rank(draft.title, draft.summary, draft.territory, ownAddress)
        val wordIssues = DraftRules.check(draft) + similarityIssues(ranked.filter { it.level != null }.take(MAX_RELATED))
        val checks = mutableListOf(
            CheckStatus(CheckKind.RULES, CheckState.DONE),
            CheckStatus(CheckKind.SIMILARITY, CheckState.DONE, compared = detector.size),
        )

        val references = when (legal) {
            null -> {
                checks += CheckStatus(CheckKind.LEGAL, CheckState.UNAVAILABLE)
                emptyList()
            }
            else -> when (val found = guarded { legal.search(legalKeywords(draft), MAX_LEGAL) }) {
                is LegalSearch.Found -> {
                    checks += CheckStatus(CheckKind.LEGAL, CheckState.DONE)
                    found.references.map { it.copy(verified = true) }
                }
                else -> {
                    checks += CheckStatus(CheckKind.LEGAL, CheckState.FAILED)
                    emptyList()
                }
            }
        }

        val modelIssues = when (model) {
            null -> {
                checks += CheckStatus(CheckKind.AI, CheckState.UNAVAILABLE)
                emptyList()
            }
            else -> {
                val closest = ranked.map { it.demand }
                val others = existing.filter { candidate -> closest.none { it.address == candidate.address } && candidate.address != ownAddress }
                val request = ReviewRequest(draft, (closest + others).take(AiReview.MAX_CANDIDATES), references)
                when (val outcome = guarded { model.review(request) }) {
                    is ReviewOutcome.Reviewed -> {
                        checks += CheckStatus(CheckKind.AI, CheckState.DONE, model = outcome.model)
                        outcome.issues
                    }
                    else -> {
                        checks += CheckStatus(CheckKind.AI, CheckState.FAILED)
                        emptyList()
                    }
                }
            }
        }

        return ProposalReport(
            issues = merge(wordIssues, modelIssues).sortedWith(ISSUE_ORDER),
            checks = checks,
            checkedAt = clock(),
        )
    }

    private fun similarityIssues(matches: List<SimilarityDetector.Match>): List<ProposalIssue> = buildList {
        val duplicates = matches.filter { it.level == SimilarityDetector.Level.DUPLICATE }
        val similar = matches.filter { it.level == SimilarityDetector.Level.SIMILAR }
        if (duplicates.isNotEmpty()) {
            add(ProposalIssue(IssueKind.DUPLICATE, IssueSeverity.WARNING, IssueSource.SIMILARITY, DraftField.TITLE, duplicates.map { it.toRelated() }))
        }
        if (similar.isNotEmpty()) {
            add(ProposalIssue(IssueKind.SIMILAR, IssueSeverity.INFO, IssueSource.SIMILARITY, related = similar.map { it.toRelated() }))
        }
        val advanced = matches.filter { it.score >= ADVANCED_THRESHOLD && it.demand.status in ADVANCED_STATUSES }
        if (advanced.isNotEmpty()) {
            add(ProposalIssue(IssueKind.ALREADY_ADVANCED, IssueSeverity.INFO, IssueSource.SIMILARITY, related = advanced.map { it.toRelated() }))
        }
    }

    /**
     * The model's opinions next to the words' findings, without saying the same thing twice: a
     * duplicate the words already found, or several ideas the rules already saw, is not repeated.
     */
    private fun merge(words: List<ProposalIssue>, model: List<ProposalIssue>): List<ProposalIssue> {
        val knownDuplicates = words.filter { it.kind == IssueKind.DUPLICATE }.flatMap { it.related }.map { it.address }.toSet()
        val ruleKinds = words.filter { it.source == IssueSource.RULES }.map { it.kind }.toSet()
        val kept = model.mapNotNull { issue ->
            when {
                issue.kind == IssueKind.DUPLICATE -> {
                    val fresh = issue.related.filter { it.address !in knownDuplicates }
                    if (fresh.isEmpty()) null else issue.copy(related = fresh)
                }
                issue.kind == IssueKind.SEVERAL_IDEAS && IssueKind.SEVERAL_IDEAS in ruleKinds -> null
                else -> issue
            }
        }
        return words + kept
    }

    /** The words of the demand and of what it asks: what a search of the legal texts needs. */
    private fun legalKeywords(draft: RevendicationDraft): List<String> =
        (FrenchText.words(draft.title) + FrenchText.words(draft.asks)).filter { it !in FrenchText.STOPWORDS && it.length > 2 }.distinct().take(20)

    private suspend fun <T> guarded(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    companion object {
        const val MAX_RELATED: Int = 5
        const val MAX_LEGAL: Int = 10

        /** A close demand already retained or turned into a campaign is worth pointing at from this score. */
        const val ADVANCED_THRESHOLD: Double = 0.45

        private val ADVANCED_STATUSES = setOf(RevendicationStatus.RETENUE, RevendicationStatus.CAMPAGNE)

        /** Blocking first, then warnings, then information; within a level, the order of [IssueKind]. */
        val ISSUE_ORDER: Comparator<ProposalIssue> = compareBy<ProposalIssue> { it.severity.ordinal }.thenBy { it.kind.ordinal }
    }
}
