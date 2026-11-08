package org.politisationpopulairefrancaise.core.decider

import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.urne.UrneResults

/**
 * The citizen deliberation (DE-25, docs/integration/deliberation.md; owner's decision D-062: « c'est le
 * peuple qui doit la modifier et la valider via notre appli »): propose, amend, vote, with one ballot box
 * per proposition, per amendment and per final measure. PPF adopts nothing: the votes of the citizens choose
 * the wording and the measures, by rules published in advance and the same on every device and on the
 * server, so that anyone can redo the computation.
 *
 * Every object is a signed long content (kind 30023) with a `ppf-type`:
 * - `deliberation` (the team): `title`, `ppf-subject`, three `ppf-phase` tags
 *   `["ppf-phase","propositions|amendements|vote","<start, seconds>","<end, seconds>"]` in this order, and
 *   `["ppf-measures","<min>","<max>"]`;
 * - `proposition` (a citizen after review, or the team for the starting proposal, `["ppf-origin","depart"]`):
 *   `a` to the deliberation, `title`, the text as content, `ppf-justification`;
 * - `amendement` (a citizen after review): `a` to the proposition, the **complete new wording** as content,
 *   `ppf-justification`.
 */
object DeliberationFormats {
    const val TAG_TYPE: String = "ppf-type"
    const val TYPE_DELIBERATION: String = "deliberation"
    const val TYPE_PROPOSITION: String = "proposition"
    const val TYPE_AMENDMENT: String = "amendement"
    const val TAG_SUBJECT: String = "ppf-subject"
    const val TAG_PHASE: String = "ppf-phase"
    const val TAG_MEASURES: String = "ppf-measures"
    const val TAG_ORIGIN: String = "ppf-origin"
    const val ORIGIN_START: String = "depart"
    const val TAG_JUSTIFICATION: String = "ppf-justification"

    /** The one choice of a box of support (phase of propositions). */
    const val SUPPORT: String = "soutien"

    /** Limits that keep the vote bearable (one token per account and per box, D-110). */
    const val PROPOSITIONS_PER_ACCOUNT: Int = 1
    const val AMENDMENTS_PER_ACCOUNT: Int = 3
    const val VOTED_PROPOSITIONS: Int = 60
    const val VOTED_AMENDMENTS: Int = 10

    const val MAX_TITLE: Int = 200
    const val MAX_TEXT: Int = 20_000
    const val MAX_JUSTIFICATION: Int = 5_000
}

enum class DeliberationPhase(val code: String) {
    PROPOSITIONS("propositions"),
    AMENDMENTS("amendements"),
    VOTE("vote"),
    ;

    companion object {
        fun of(code: String): DeliberationPhase? = entries.firstOrNull { it.code == code }
    }
}

/** One phase and its dates (seconds, UTC): open from [start] included to [end] excluded. */
data class PhaseDates(val phase: DeliberationPhase, val start: Long, val end: Long)

data class Deliberation(
    val address: ContentAddress,
    val event: SignedEvent,
    val title: String,
    val subject: String?,
    /** The three phases, in order, each after the previous one. */
    val phases: List<PhaseDates>,
    val minMeasures: Int,
    val maxMeasures: Int,
) {
    fun dates(phase: DeliberationPhase): PhaseDates = phases.first { it.phase == phase }

    /** The phase open at [now] (seconds), or null before the first and after the last. */
    fun phaseAt(now: Long): DeliberationPhase? = phases.firstOrNull { now >= it.start && now < it.end }?.phase

    /** Whether the final vote is over at [now]: its results are then final. */
    fun isOver(now: Long): Boolean = now >= dates(DeliberationPhase.VOTE).end

    companion object {
        fun parse(event: SignedEvent): Deliberation? {
            if (event.kind != EventKinds.LONG_FORM_ARTICLE || event.firstTagValue(DeliberationFormats.TAG_TYPE) != DeliberationFormats.TYPE_DELIBERATION) return null
            val address = ContentAddress.of(event) ?: return null
            val title = event.firstTagValue("title")?.trim()?.takeIf { it.isNotEmpty() && it.length <= DeliberationFormats.MAX_TITLE } ?: return null
            val phases = event.tags.filter { it.firstOrNull() == DeliberationFormats.TAG_PHASE }.map { tag ->
                if (tag.size < 4) return null
                val phase = DeliberationPhase.of(tag[1]) ?: return null
                val start = tag[2].toLongOrNull() ?: return null
                val end = tag[3].toLongOrNull() ?: return null
                PhaseDates(phase, start, end)
            }
            // Exactly the three phases, in order, each one ending before the next one starts: the course is
            // dated and the same for everyone, nothing changes during a phase.
            if (phases.map { it.phase } != DeliberationPhase.entries.toList()) return null
            if (phases.any { it.start < 0 || it.end <= it.start }) return null
            if (phases.zipWithNext().any { (a, b) -> b.start < a.end }) return null
            val measures = event.tags.firstOrNull { it.firstOrNull() == DeliberationFormats.TAG_MEASURES } ?: return null
            val min = measures.getOrNull(1)?.toIntOrNull() ?: return null
            val max = measures.getOrNull(2)?.toIntOrNull() ?: return null
            if (min < 1 || max < min || max > MeasureSelection.MAX) return null
            val subject = event.firstTagValue(DeliberationFormats.TAG_SUBJECT)?.trim()?.takeIf { it.isNotEmpty() }
            return Deliberation(address, event, title, subject, phases, min, max)
        }
    }
}

/** A proposition of measure (or of modality) in a deliberation. */
data class Proposition(
    val address: ContentAddress,
    val event: SignedEvent,
    val deliberation: ContentAddress,
    val title: String,
    val text: String,
    val justification: String?,
    /** The starting proposal of PPF: shown « proposition de départ, pas encore votée » until the final vote. */
    val isStart: Boolean,
) {
    companion object {
        fun parse(event: SignedEvent): Proposition? {
            if (event.kind != EventKinds.LONG_FORM_ARTICLE || event.firstTagValue(DeliberationFormats.TAG_TYPE) != DeliberationFormats.TYPE_PROPOSITION) return null
            val address = ContentAddress.of(event) ?: return null
            val deliberation = linkTo(event) ?: return null
            val title = event.firstTagValue("title")?.trim()?.takeIf { it.isNotEmpty() && it.length <= DeliberationFormats.MAX_TITLE } ?: return null
            val text = event.content.trim().takeIf { it.isNotEmpty() && it.length <= DeliberationFormats.MAX_TEXT } ?: return null
            return Proposition(address, event, deliberation, title, text, justificationOf(event), event.firstTagValue(DeliberationFormats.TAG_ORIGIN) == DeliberationFormats.ORIGIN_START)
        }
    }
}

/** An amendment: a complete new wording of a proposition, never a piece of text to paste back. */
data class Amendment(
    val address: ContentAddress,
    val event: SignedEvent,
    val proposition: ContentAddress,
    val text: String,
    val justification: String?,
) {
    companion object {
        fun parse(event: SignedEvent): Amendment? {
            if (event.kind != EventKinds.LONG_FORM_ARTICLE || event.firstTagValue(DeliberationFormats.TAG_TYPE) != DeliberationFormats.TYPE_AMENDMENT) return null
            val address = ContentAddress.of(event) ?: return null
            val proposition = linkTo(event) ?: return null
            val text = event.content.trim().takeIf { it.isNotEmpty() && it.length <= DeliberationFormats.MAX_TEXT } ?: return null
            return Amendment(address, event, proposition, text, justificationOf(event))
        }
    }
}

/** The one `a` tag of [event], a long content (an amendment names its proposition, a proposition its deliberation). */
private fun linkTo(event: SignedEvent): ContentAddress? {
    val links = event.tagValues("a")
    if (links.size != 1) return null
    return runCatching { ContentAddress.parse(links.single()) }.getOrNull()?.takeIf { it.kind == EventKinds.LONG_FORM_ARTICLE }
}

private fun justificationOf(event: SignedEvent): String? =
    event.firstTagValue(DeliberationFormats.TAG_JUSTIFICATION)?.trim()?.takeIf { it.isNotEmpty() && it.length <= DeliberationFormats.MAX_JUSTIFICATION }

/**
 * The mechanical rules of the deliberation (docs/integration/deliberation.md, sections 2, 4 and 5): the same
 * functions on the server, which opens the boxes, and on the devices, which show and recount.
 */
object DeliberationRules {
    /** The identifiers of the ballot boxes, stable and public. */
    fun supportUrneId(proposition: ContentAddress): String = "decider:deliberation:soutien:$proposition"
    fun amendmentUrneId(amendment: ContentAddress): String = "decider:deliberation:amendement:$amendment"
    fun finalUrneId(proposition: ContentAddress): String = "decider:deliberation:final:$proposition"

    /** Order of creation: date written in the event, then identifier (never the order of arrival on a server). */
    private val byCreation: Comparator<SignedEvent> = compareBy<SignedEvent> { it.createdAt }.thenBy { it.id }

    /**
     * The propositions of a deliberation that a reviewer may accept: at most one per account (the first
     * created), in order of creation. The starting proposal of PPF, by the deliberation's author, is kept
     * besides.
     */
    fun admissiblePropositions(deliberation: Deliberation, propositions: List<Proposition>): List<Proposition> =
        propositions.filter { it.deliberation == deliberation.address }
            .sortedWith(compareBy(byCreation) { it.event })
            .distinctBy { if (it.isStart && it.event.pubkey == deliberation.event.pubkey) "start:${it.address}" else it.event.pubkey }

    /**
     * The propositions that go on to the amendments and the final vote: all of them up to
     * [DeliberationFormats.VOTED_PROPOSITIONS]; beyond, the most supported in the phase of propositions
     * ([supports]: the citizens' supports, null when not known), equal supports in order of creation. The
     * starting proposals always go on: they are where the deliberation starts from.
     */
    fun votedPropositions(propositions: List<Proposition>, supports: (Proposition) -> Long?): List<Proposition> {
        val ordered = propositions.sortedWith(compareBy(byCreation) { it.event })
        if (ordered.size <= DeliberationFormats.VOTED_PROPOSITIONS) return ordered
        val (start, others) = ordered.partition { it.isStart }
        val kept = others.withIndex()
            .sortedWith(compareByDescending<IndexedValue<Proposition>> { supports(it.value) ?: 0L }.thenBy { it.index })
            .take((DeliberationFormats.VOTED_PROPOSITIONS - start.size).coerceAtLeast(0))
            .map { it.value }
            .toSet()
        return ordered.filter { it.isStart || it in kept }
    }

    /**
     * The amendments of [proposition] put to the vote: at most [DeliberationFormats.AMENDMENTS_PER_ACCOUNT]
     * per account and [DeliberationFormats.VOTED_AMENDMENTS] in all, the first created.
     */
    fun votedAmendments(proposition: Proposition, amendments: List<Amendment>): List<Amendment> {
        val perAccount = HashMap<String, Int>()
        return amendments.filter { it.proposition == proposition.address }
            .sortedWith(compareBy(byCreation) { it.event })
            .filter { amendment ->
                val count = (perAccount[amendment.event.pubkey] ?: 0) + 1
                perAccount[amendment.event.pubkey] = count
                count <= DeliberationFormats.AMENDMENTS_PER_ACCOUNT
            }
            .take(DeliberationFormats.VOTED_AMENDMENTS)
    }

    private fun count(results: UrneResults, choice: String): Long = results.citizens.counts[choice] ?: 0L

    /**
     * The wording put to the final vote (section 4): an amendment wins over the original wording when the
     * citizens give it more `pour` than `contre` (the elected officials vote apart and do not count, D-115);
     * of several winners, the most `pour`, then the fewest `contre`, then the oldest. Null: the original
     * wording. A box whose citizens' column is hidden (still open) wins nothing.
     */
    fun retainedAmendment(amendments: List<Pair<Amendment, UrneResults?>>): Amendment? =
        amendments
            .filter { (_, results) -> results != null && !results.citizens.hidden && count(results, "pour") > count(results, "contre") }
            .sortedWith(
                compareByDescending<Pair<Amendment, UrneResults?>> { count(it.second!!, "pour") }
                    .thenBy { count(it.second!!, "contre") }
                    .thenBy(byCreation) { it.first.event },
            )
            .firstOrNull()?.first

    /** The text put to the final vote for [proposition]: the retained amendment's, or its own. */
    fun finalText(proposition: Proposition, retained: Amendment?): String = retained?.text ?: proposition.text

    /**
     * The measures kept at the end (section 5): those with more `pour` than `contre`, the most supported
     * first, at most the deliberation's maximum; fewer than its minimum is said, never completed.
     */
    fun <T> kept(deliberation: Deliberation, measures: List<MeasureSelection.Candidate<T>>): MeasureSelection.Selection<T> {
        val selection = MeasureSelection.select(measures)
        val kept = selection.measures.take(deliberation.maxMeasures)
        return MeasureSelection.Selection(kept, tooFew = kept.size < deliberation.minMeasures)
    }
}
