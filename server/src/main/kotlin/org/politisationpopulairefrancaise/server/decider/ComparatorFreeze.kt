package org.politisationpopulairefrancaise.server.decider

import org.politisationpopulairefrancaise.core.decider.ComparatorSheet
import org.politisationpopulairefrancaise.core.decider.DeciderCatalog
import org.politisationpopulairefrancaise.core.decider.DeciderTags
import org.politisationpopulairefrancaise.core.decider.ElectionRound
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.voir.VoirTags.at
import org.politisationpopulairefrancaise.core.voir.VoirTags.fields
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import java.time.Clock
import java.time.Instant

/**
 * The freeze of the comparators (DE-16, decision of the owner Q4 option A, `docs/integration/comparateur-2027.md`):
 * from 0 h, Paris time, the day before the first voting day of a round until its last polling stations close,
 * the server refuses every publication, update or deletion of a comparator or of a position of that election.
 * The application stays readable: what it already has is kept.
 *
 * The rounds that count are those of the comparators **already stored** ([catalog]): a new version that would
 * move the dates cannot lift a freeze that holds. A new comparator is also refused when its own rounds say
 * that its election is frozen now.
 */
class ComparatorFreeze(
    private val catalog: suspend () -> DeciderCatalog,
    private val clock: Clock = Clock.systemUTC(),
) {
    /** The refusal of [event] while a comparator it touches is frozen, null otherwise. */
    suspend fun refusal(event: SignedEvent): PublicationPolicy.Decision.Refused? {
        val now = clock.instant().epochSecond
        return when (event.kind) {
            Article.KIND -> {
                val type = event.firstTagValue(Article.TAG_TYPE)
                if (type != ArticleType.COMPARATEUR.tagValue && type != ArticleType.POSITION.tagValue) return null
                val elections = event.fields(DeciderTags.ELECTION).mapNotNull { it.at(0) }.toSet()
                if (elections.isEmpty()) return null
                // A new comparator whose own rounds say that its election is frozen now.
                val own = if (type == ArticleType.COMPARATEUR.tagValue) {
                    Article.from(event)?.let(ComparatorSheet::from)?.frozenRound(now)
                } else {
                    null
                }
                (own ?: frozenRound(catalog(), elections, now))?.let(::refused)
            }
            EventKinds.DELETION_REQUEST -> {
                val current = catalog()
                val ids = event.tagValues("e").toSet()
                val addresses = event.tagValues("a").map { it.trimEnd(':') }.toSet()
                fun targeted(article: Article) = article.event.id in ids || article.address.toString() in addresses
                val elections = current.comparators.filter { targeted(it.article) }.map { it.election.id } +
                    current.positions.filter { targeted(it.article) }.map { it.electionId }
                if (elections.isEmpty()) return null
                frozenRound(current, elections.toSet(), now)?.let(::refused)
            }
            else -> null
        }
    }

    private fun frozenRound(current: DeciderCatalog, elections: Set<String>, now: Long): ElectionRound? =
        current.comparators.filter { it.election.id in elections }.firstNotNullOfOrNull { it.frozenRound(now) }

    private fun refused(round: ElectionRound) = PublicationPolicy.Decision.Refused(
        status = 409,
        error = "comparator_frozen",
        message = "The comparator of this election is frozen until the polling stations of round ${round.number} close " +
            "(${Instant.ofEpochSecond(round.freezeEnd)}); nothing of it is published meanwhile.",
    )
}
