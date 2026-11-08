package org.politisationpopulairefrancaise.core.voir.parlement

import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.elus.ElusDirectory
import org.politisationpopulairefrancaise.core.elus.ElusStore
import org.politisationpopulairefrancaise.core.notifications.NotificationCandidate
import org.politisationpopulairefrancaise.core.notifications.NotificationCategory
import org.politisationpopulairefrancaise.core.notifications.NotificationSource
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.text.FrenchText

/**
 * The codes of the alerts of « Voir » about the Parliament: the titles of their candidates, which the
 * interface and the platform translate (like `AccountAlerts`), never a name nor a word written in a title.
 */
object VoteAlertCodes {
    /** My parliamentarians took part in new ballots; the detail is their number. */
    const val MY_OFFICIALS: String = "votes:my-officials"

    /** A new ballot about a followed word; the detail is the word, shown only when the person allowed details. */
    const val WORD: String = "votes:word"

    /** Where the alerts lead: « Mes élus ont voté », in the Parliament of « Voir ». */
    const val TARGET: String = "ppf:voir/parlement/mes-elus"
}

/**
 * « Mes élus ont voté » and the followed words in the notifications (VO-34, D-102), in the category
 * [NotificationCategory.VOTES] of the engine of Communauté: it is read only while « Votes au Parlement »
 * is on, so nothing is downloaded while it is off. One candidate per round that found ballots of my
 * parliamentarians (a summary, never one per ballot), and one per followed word found; the engine groups
 * them in one system notification and keeps the lock screen neutral.
 */
class VoteAlertSource(private val round: VoteAlertRound) : NotificationSource {

    override val name: String = "voir-vote-alerts"

    override suspend fun collect(after: Long?): List<NotificationCandidate> {
        val found = round.run() ?: return emptyList()
        return candidatesOf(found)
    }

    companion object {
        /**
         * The source as a platform host wires it on its local database: the officials of the commune chosen in
         * « Mes élus » (device only), the parliamentary open data of the same tables as the screens, the storage
         * of the page « Mes élus ont voté », and the network as the platform reads it.
         */
        fun on(database: PpfDatabase, remote: RemoteEvents?, trust: OpenDataTrust, network: () -> NetworkConditions): VoteAlertSource {
            val elus = ElusDirectory(remote, ElusStore(database))
            val round = VoteAlertRound(
                repository = ParliamentRepository(remote, ParliamentStore(database), trust = trust),
                storage = VoteAlertStore(database),
                parliamentarians = { catalog, europeans ->
                    val mine = runCatching { elus.mine() }.getOrNull()
                    when {
                        mine != null -> MyParliamentarians.of(mine.officials, catalog.members(), europeans)
                        europeans.isNotEmpty() -> MyParliamentarians(emptyList(), emptyList(), europeans.mapNotNull(catalog::member))
                        else -> null
                    }
                },
                network = network,
            )
            return VoteAlertSource(round)
        }

        fun candidatesOf(found: VoteAlertFindings): List<NotificationCandidate> {
            if (found.first || found.isEmpty) return emptyList()
            val votes = if (found.votes.isEmpty()) emptyList() else listOf(
                NotificationCandidate(
                    id = "vote-alert:${found.receivedAt}",
                    category = NotificationCategory.VOTES,
                    title = VoteAlertCodes.MY_OFFICIALS,
                    detail = found.votes.size.toString(),
                    target = VoteAlertCodes.TARGET,
                    createdAt = found.receivedAt,
                ),
            )
            val words = found.words.distinctBy { FrenchText.fold(it.word) }.map { alert ->
                NotificationCandidate(
                    id = "vote-word:${found.receivedAt}:${FrenchText.fold(alert.word)}",
                    category = NotificationCategory.VOTES,
                    title = VoteAlertCodes.WORD,
                    detail = alert.word,
                    target = VoteAlertCodes.TARGET,
                    createdAt = found.receivedAt,
                )
            }
            return votes + words
        }
    }
}
