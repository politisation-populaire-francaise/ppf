package org.politisationpopulairefrancaise.core.agir.calls

import org.politisationpopulairefrancaise.core.revendications.checks.DemandText
import org.politisationpopulairefrancaise.core.revendications.checks.SimilarityDetector
import org.politisationpopulairefrancaise.core.text.FrenchText

/** The deadline filter of the wall (AP-05), computed on [Call.dueAt] in Paris time. */
enum class CallDue {
    ANY,

    /** Due from now to the end of Sunday. */
    THIS_WEEK,

    /** Due from now to the end of the month. */
    THIS_MONTH,

    /** Neither deadline nor start. */
    NONE,
}

enum class CallStatusFilter { OPEN, CLOSED, ALL }

/**
 * The orders of the wall. There is no "popularity" yet: answers stay on the devices and no counter
 * is published until the anonymous counting method is validated (contract, section 6).
 */
enum class CallSort {
    /** First publication, newest first. */
    NEWEST,

    /** The closest date to come first, then the calls without a date, then those already passed. */
    DEADLINE,
}

/**
 * The filters of the wall of calls (AP-05): types, territories, deadline, open or closed, words,
 * and the sort. Empty sets and blank text mean "no constraint". Everything is computed on the
 * device, on the local copy of the calls: no filter is ever sent to the server (contract, section 6).
 */
data class CallQuery(
    val types: Set<CallType> = emptySet(),
    /** Folded territories ([FrenchText.fold]): a call matches when its own folded territory is one of them. */
    val territories: Set<String> = emptySet(),
    val due: CallDue = CallDue.ANY,
    val status: CallStatusFilter = CallStatusFilter.OPEN,
    val text: String = "",
    val sort: CallSort = CallSort.NEWEST,
) {
    fun matches(call: Call, now: Long): Boolean {
        if (types.isNotEmpty() && call.type !in types) return false
        if (territories.isNotEmpty() && FrenchText.fold(call.territory) !in territories) return false
        // A call whose deadline has passed is over in practice: it leaves the open calls for the closed ones.
        val over = !call.isOpen || call.deadline?.let { it < now } == true
        when (status) {
            CallStatusFilter.OPEN -> if (over) return false
            CallStatusFilter.CLOSED -> if (!over) return false
            CallStatusFilter.ALL -> Unit
        }
        val due = call.dueAt
        when (this.due) {
            CallDue.ANY -> Unit
            CallDue.THIS_WEEK -> if (due == null || due < now || due >= ParisTime.endOfWeek(now)) return false
            CallDue.THIS_MONTH -> if (due == null || due < now || due >= ParisTime.endOfMonth(now)) return false
            CallDue.NONE -> if (due != null) return false
        }
        if (text.isNotBlank() && !FrenchText.matchesAllWords(searchableText(call), text)) return false
        return true
    }

    /** The matching calls, in the order of [sort]. */
    fun apply(calls: List<Call>, now: Long): List<Call> = sorted(calls.filter { matches(it, now) }, now)

    fun sorted(calls: List<Call>, now: Long): List<Call> = when (sort) {
        CallSort.NEWEST -> calls.sortedWith(
            compareByDescending<Call> { it.publishedAt ?: it.updatedAt }.thenBy { it.displayTitle.lowercase() }.thenBy { it.address.toString() },
        )
        CallSort.DEADLINE -> calls.sortedWith(
            compareBy<Call> { deadlineRank(it, now) }
                .thenBy { call -> call.dueAt?.let { if (it >= now) it else -it } ?: 0L }
                .thenBy { it.displayTitle.lowercase() },
        )
    }

    /** The number shown on the "Filtres" button: one per type and territory, one for each other criterion set. */
    val constraintCount: Int
        get() = types.size + territories.size + (if (due != CallDue.ANY) 1 else 0) + (if (status != CallStatusFilter.OPEN) 1 else 0) + (if (text.isNotBlank()) 1 else 0)

    /** Back to no filter, keeping the sort. */
    fun cleared(): CallQuery = CallQuery(sort = sort)

    private fun deadlineRank(call: Call, now: Long): Int {
        val due = call.dueAt
        return when {
            due == null -> 1
            due >= now -> 0
            else -> 2
        }
    }

    companion object {
        /** The words the search reads: title, summary, text, territory, place, report and the specific fields. */
        fun searchableText(call: Call): String = buildString {
            append(call.title).append(' ')
            call.summary?.let { append(it).append(' ') }
            append(call.text).append(' ')
            append(call.territory).append(' ')
            call.location?.let { append(it).append(' ') }
            call.report?.let { append(it).append(' ') }
            call.fields.values.forEach { append(it).append(' ') }
        }

        /** The territories of [calls] for the filter chips: folded key, then the first spelling met, sorted by label. */
        fun territoriesOf(calls: List<Call>): Map<String, String> {
            val found = LinkedHashMap<String, String>()
            calls.forEach { call ->
                val key = FrenchText.fold(call.territory)
                if (key.isNotEmpty() && key !in found) found[key] = call.territory
            }
            return found.entries.sortedBy { FrenchText.fold(it.value) }.associate { it.key to it.value }
        }
    }
}

/** A published call close to a draft (AP-02, like the demands of DE-18). */
data class SimilarCall(val call: Call, val score: Double, val likelyDuplicate: Boolean)

/**
 * The calls that seem to say the same thing as a draft, shown before the sending so that the author
 * can join an existing call rather than scatter the people (AP-02). It reuses the comparison of the
 * demands ([SimilarityDetector]: keywords of the title and the summary, territory), on the open
 * calls only (a closed call is no longer a place to join). Two calls of different types are at most
 * "close": a petition and a demonstration on the same subject are not duplicates.
 */
object CallSimilarity {
    fun find(draft: CallDraft, calls: List<Call>, ownAddress: String? = null, limit: Int = 3): List<SimilarCall> {
        if (FrenchText.keywords(draft.title.removePrefix(Call.EXAMPLE_MARKER)).isEmpty()) return emptyList()
        val open = calls.filter { it.isOpen }.distinctBy { it.address }
        val byAddress = open.associateBy { it.address.toString() }
        val detector = SimilarityDetector(open.map { DemandText(it.address.toString(), it.displayTitle, it.summary, it.territory) })
        return detector.compare(draft.title.removePrefix(Call.EXAMPLE_MARKER), draft.summary, draft.territory, ownAddress, limit).mapNotNull { match ->
            val call = byAddress[match.demand.address] ?: return@mapNotNull null
            val sameType = draft.type == null || draft.type == call.type
            SimilarCall(call, match.score, likelyDuplicate = sameType && match.level == SimilarityDetector.Level.DUPLICATE)
        }
    }
}
