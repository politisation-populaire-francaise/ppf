package org.politisationpopulairefrancaise.server.urne

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.RevendicationStatus
import java.time.Clock
import javax.sql.DataSource

/**
 * The ballot boxes of the demands wall (DE-01, D-110, step 4 of the design): a demand that the team
 * put in debate gets its box, and the box closes when the demand leaves the debate.
 *
 * - **Opened** for every published demand (kind 30023, `ppf-type` = `revendication`) whose status is
 *   « en débat » or « très soutenue », signed by the editorial team (only the team moves a demand
 *   forward; a citizen's demand is published « nouvelle »). The box: `decider:revendication:<address>`,
 *   choices `pour` and `contre`, results shown live, no natural end.
 * - **Closed early** when the demand is retained in the programme, becomes a campaign, goes back to
 *   « nouvelle » or disappears (deleted): the closing publishes the keys and the anonymous ballots,
 *   so that anyone can recount (6.7). A box is never reopened: a demand that comes back to the
 *   debate keeps its closed box (one box per demand, one vote per account).
 *
 * Voting is limited to the demands in debate because the server computes one token per account and
 * per open box (measured at step 1, D-110): opening a box for every demand would not hold.
 *
 * Run by the closing task of [UrneService] at every pass; it only reads the events and opens or
 * closes boxes, idempotently.
 */
class RevendicationUrnes(
    private val dataSource: DataSource,
    private val urnes: UrneService,
    private val isEditor: (String) -> Boolean,
    private val clock: Clock = Clock.systemUTC(),
) {
    /** Opens the boxes of the demands in debate, closes the others; returns (opened, closed). */
    fun reconcile(): Pair<Int, Int> {
        val inDebate = demandsInDebate()
        val open = urnes.openWithPrefix(PREFIX).associateBy { it.id }
        var opened = 0
        for (address in inDebate) {
            val id = PREFIX + address
            if (id in open) continue
            // An existing box, closed, is returned as it is: never reopened.
            val box = urnes.open(id, address, CHOICES, clock.instant(), closesAt = null, liveResults = true)
            if (!box.closed) opened++
        }
        var closed = 0
        val wanted = inDebate.mapTo(HashSet()) { PREFIX + it }
        for ((id, _) in open) {
            if (id !in wanted && urnes.closeEarly(id, clock.instant())?.closed == true) closed++
        }
        return opened to closed
    }

    /** Addresses (`30023:<key>:<d>`) of the published demands in debate, signed by the team. */
    private fun demandsInDebate(): List<String> = dataSource.connection.use { c ->
        c.prepareStatement(
            """
            SELECT e.pubkey, e.address, s.value
            FROM events e
            JOIN event_tags s ON s.event_id = e.id AND s.name = ?
            WHERE e.kind = ? AND e.address IS NOT NULL
              AND EXISTS (SELECT 1 FROM event_tags t WHERE t.event_id = e.id AND t.name = ? AND t.value = ?)
            ORDER BY e.address
            """.trimIndent(),
        ).use { s ->
            s.setString(1, Article.TAG_STATUS)
            s.setInt(2, Article.KIND)
            s.setString(3, Article.TAG_TYPE)
            s.setString(4, ArticleType.REVENDICATION.tagValue)
            s.executeQuery().use { r ->
                val found = LinkedHashSet<String>()
                while (r.next()) {
                    val status = RevendicationStatus.fromTag(r.getString(3))
                    if (status in VOTABLE && isEditor(r.getString(1))) found += r.getString(2)
                }
                found.toList()
            }
        }
    }

    companion object {
        const val PREFIX: String = "decider:revendication:"
        val CHOICES: List<String> = listOf("pour", "contre")
        private val VOTABLE = setOf(RevendicationStatus.EN_DEBAT, RevendicationStatus.TRES_SOUTENUE)
    }
}
