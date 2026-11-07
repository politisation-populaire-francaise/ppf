package org.politisationpopulairefrancaise.server.urne

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.RevendicationStatus
import org.politisationpopulairefrancaise.core.revendications.StatusLabels
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import java.time.Clock
import javax.sql.DataSource

/**
 * The ballot boxes of the demands wall (DE-01, D-110, step 4 of the design): a demand that the team
 * put in debate gets its box, and the box closes when the demand leaves the debate.
 *
 * - **Opened** for every published demand (kind 30023, `ppf-type` = `revendication`) whose status is
 *   « en débat » or « très soutenue »: the status of the latest label of the team ([StatusLabels],
 *   NIP-32), or the status written in a demand of the team itself (only the team moves a demand
 *   forward; a citizen's demand is published « nouvelle »). The box: `decider:revendication:<address>`,
 *   choices `pour`, `contre` and `blanc` (the blank vote counted and shown; not voting is abstaining,
 *   shown by the participation; owner's decision of 7 November 2026), results shown live, no natural end.
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

    /**
     * Addresses (`30023:<key>:<d>`) of the published demands in debate: by the most recent status
     * label of the team (NIP-32, [StatusLabels]), else by the status the team wrote in its own demand.
     */
    private fun demandsInDebate(): List<String> = dataSource.connection.use { c ->
        val labels = HashMap<String, MutableList<StatusLabels.Label>>()
        c.prepareStatement(
            """
            SELECT e.pubkey, e.created_at, l.value, a.value
            FROM events e
            JOIN event_tags l ON l.event_id = e.id AND l.name = 'l'
            JOIN event_tags a ON a.event_id = e.id AND a.name = 'a'
            WHERE e.kind = ?
              AND EXISTS (SELECT 1 FROM event_tags n WHERE n.event_id = e.id AND n.name = 'L' AND n.value = ?)
            """.trimIndent(),
        ).use { s ->
            s.setInt(1, StatusLabels.KIND)
            s.setString(2, StatusLabels.NAMESPACE)
            s.executeQuery().use { r ->
                while (r.next()) {
                    val status = RevendicationStatus.fromTag(r.getString(3)) ?: continue
                    val demand = runCatching { ContentAddress.parse(r.getString(4)) }.getOrNull() ?: continue
                    labels.getOrPut(demand.toString()) { ArrayList() } += StatusLabels.Label(demand, status, r.getLong(2), r.getString(1))
                }
            }
        }
        c.prepareStatement(
            """
            SELECT e.pubkey, e.address, (SELECT s.value FROM event_tags s WHERE s.event_id = e.id AND s.name = ? LIMIT 1)
            FROM events e
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
                val found = ArrayList<String>()
                while (r.next()) {
                    val address = r.getString(2)
                    val written = r.getString(3)?.let(RevendicationStatus::fromTag)
                    val status = StatusLabels.effective(r.getString(1), written, labels[address].orEmpty(), isEditor)
                    if (status in VOTABLE) found += address
                }
                found
            }
        }
    }

    companion object {
        const val PREFIX: String = "decider:revendication:"
        val CHOICES: List<String> = listOf("pour", "contre", "blanc")
        private val VOTABLE = setOf(RevendicationStatus.EN_DEBAT, RevendicationStatus.TRES_SOUTENUE)
    }
}
