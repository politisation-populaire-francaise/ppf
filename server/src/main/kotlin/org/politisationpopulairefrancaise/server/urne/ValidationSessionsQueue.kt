package org.politisationpopulairefrancaise.server.urne

import kotlinx.html.FlowContent
import kotlinx.html.dd
import kotlinx.html.div
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.ul
import org.politisationpopulairefrancaise.server.backoffice.ActionOutcome
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeQueue
import org.politisationpopulairefrancaise.server.backoffice.Permissions
import org.politisationpopulairefrancaise.server.backoffice.QueueAction
import org.politisationpopulairefrancaise.server.backoffice.QueueItem
import org.politisationpopulairefrancaise.server.backoffice.QueuePage
import org.politisationpopulairefrancaise.server.backoffice.TeamActor
import org.politisationpopulairefrancaise.server.backoffice.ValidationSession
import org.politisationpopulairefrancaise.server.backoffice.ValidationSessionState
import org.politisationpopulairefrancaise.server.backoffice.ValidationSessions
import org.politisationpopulairefrancaise.server.backoffice.dateTime
import org.politisationpopulairefrancaise.server.backoffice.transaction
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

/**
 * The queue `decider.validation_sessions` of the team back-office (contract docs/integration/back-office.md,
 * section 8; une-personne-une-voix.md 4.3 and 8): the sessions of the validators of the last
 * [WINDOW_DAYS] days whose pace looks abnormal, until a person reviews them. The server knows only the
 * counters of a session, never who it validated: undoing its validations needs the sealed links, read
 * offline by the owner with the audit key. The queue says so, and lets the team revoke an open session.
 *
 * Signs looked at ([Sign]): a fast pace ([FAST_PER_HOUR] validations an hour or more, over at least
 * [FAST_MINIMUM]), a session filled to its ceiling, a validator with more than [MANY_PER_WEEK] sessions
 * the same week.
 */
class ValidationSessionsQueue(
    private val dataSource: DataSource,
    private val clock: Clock = Clock.systemUTC(),
) : BackOfficeQueue {
    private val sessions = ValidationSessions(dataSource)

    override val id: String = ID
    override val title: String = "Séances des validateurs à examiner"
    override val viewPermission: String = Permissions.queueDecide(ID)

    enum class Sign(val label: String) {
        FAST("Rythme rapide"),
        FULL("Plafond atteint"),
        MANY("Nombreuses séances du même validateur cette semaine"),
    }

    /** The sessions of the window with at least one sign and not reviewed, oldest first, with their signs. */
    private fun flagged(): List<Pair<ValidationSession, Set<Sign>>> {
        val now = clock.instant()
        val recent = sessions.openedSince(now.minus(Duration.ofDays(WINDOW_DAYS)))
        val reviewed = reviewed(recent.map { it.id })
        val perWeek = recent.groupingBy { it.accountId to it.week }.eachCount()
        return recent.filter { it.id !in reviewed }.mapNotNull { session ->
            val signs = signsOf(session, now, perWeek.getValue(session.accountId to session.week))
            if (signs.isEmpty()) null else session to signs
        }
    }

    private fun signsOf(session: ValidationSession, now: Instant, sameWeek: Int): Set<Sign> = buildSet {
        val end = session.closedAt ?: minOf(now, session.expiresAt)
        val hours = maxOf(Duration.between(session.openedAt, end).toMinutes(), 60L) / 60.0
        if (session.validations >= FAST_MINIMUM && session.validations / hours >= FAST_PER_HOUR) add(Sign.FAST)
        if (session.validations >= session.maxValidations) add(Sign.FULL)
        if (sameWeek > MANY_PER_WEEK) add(Sign.MANY)
    }

    private fun reviewed(ids: List<UUID>): Set<UUID> {
        if (ids.isEmpty()) return emptySet()
        return dataSource.connection.use { c ->
            c.prepareStatement("SELECT session_id FROM vote_session_review WHERE session_id = ANY(?)").use { s ->
                s.setArray(1, c.createArrayOf("uuid", ids.toTypedArray()))
                s.executeQuery().use { r -> buildSet { while (r.next()) add(r.getObject(1, UUID::class.java)) } }
            }
        }
    }

    override fun pendingCount(): Int = flagged().size

    override fun page(cursor: String?, limit: Int): QueuePage {
        val offset = cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val all = flagged()
        val items = all.drop(offset).take(limit).map { (session, signs) ->
            QueueItem(
                id = session.id.toString(),
                title = "Séance de la semaine ${session.week} : ${session.validations} validation(s) sur ${session.maxValidations}",
                receivedAt = session.openedAt.epochSecond,
                summary = signs.joinToString(", ") { it.label },
            )
        }
        return QueuePage(items, (offset + limit).takeIf { it < all.size }?.toString())
    }

    private fun sessionOf(itemId: String): ValidationSession? = runCatching { UUID.fromString(itemId) }.getOrNull()?.let(sessions::find)

    override fun render(itemId: String, html: FlowContent) {
        val session = sessionOf(itemId)
        if (session == null) {
            html.h1 { +"Cette séance n'existe pas." }
            return
        }
        val now = clock.instant()
        val sameWeek = sessions.openedSince(now.minus(Duration.ofDays(WINDOW_DAYS))).count { it.accountId == session.accountId && it.week == session.week }
        val signs = signsOf(session, now, sameWeek)
        html.h1 { +"Séance de validation de la semaine ${session.week}" }
        if (session.id in reviewed(listOf(session.id))) {
            html.div(classes = "bo-notice") {
                attributes["role"] = "status"
                +"Déjà examinée."
            }
        }
        html.dl(classes = "bo-facts") {
            dt { +"Ouverte" }
            dd { dateTime(session.openedAt.epochSecond) }
            dt { +(if (session.closedAt == null) "Échéance" else "Terminée") }
            dd { dateTime((session.closedAt ?: session.expiresAt).epochSecond) }
            dt { +"État" }
            dd { +STATE_TEXT.getValue(session.state) }
            dt { +"Validations" }
            dd { +"${session.validations} sur ${session.maxValidations} au plus" }
            dt { +"Séances du même validateur cette semaine" }
            dd { +sameWeek.toString() }
        }
        html.h2 { +"Ce qui attire l'attention" }
        if (signs.isEmpty()) html.p { +"Rien d'anormal." } else html.ul { signs.forEach { li { +it.label } } }
        html.h2 { +"Ce que le serveur ne sait pas" }
        html.p {
            +"Le serveur ne sait pas quels comptes cette séance a validés : chaque lien est scellé pour la clé « audit », que seule la propriétaire garde hors ligne. "
            +"Pour annuler les validations de la séance, classez-la « à annuler » : la propriétaire lit alors les liens hors ligne et fait redescendre ces comptes."
        }
    }

    override fun actions(itemId: String): List<QueueAction> {
        val session = sessionOf(itemId) ?: return emptyList()
        if (session.id in reviewed(listOf(session.id))) return emptyList()
        return buildList {
            if (session.state == ValidationSessionState.OPEN) add(QueueAction(REVOKE, "Révoquer la séance maintenant", Permissions.TEAM_REVOKE, reasonRequired = true, sensitive = true))
            add(QueueAction(NORMAL, "Classer : rien d'anormal", viewPermission, reasonRequired = true))
            add(QueueAction(TO_UNDO, "Classer : validations à annuler par la propriétaire", viewPermission, reasonRequired = true, sensitive = true))
        }
    }

    override fun perform(itemId: String, action: String, reason: String?, actor: TeamActor): ActionOutcome {
        val session = sessionOf(itemId) ?: return ActionOutcome.NotFound
        if (reason.isNullOrBlank() || reason.trim().length > MAX_REASON) return ActionOutcome.Refused("Un motif de 1 à $MAX_REASON caractères est obligatoire.")
        val now = clock.instant()
        return when (action) {
            REVOKE -> dataSource.transaction { c ->
                if (sessions.end(c, session.id, ValidationSessionState.REVOKED, now) == null) ActionOutcome.AlreadyDecided else ActionOutcome.Done
            }
            NORMAL, TO_UNDO -> dataSource.connection.use { c ->
                c.prepareStatement("INSERT INTO vote_session_review (session_id, outcome, reviewed_week) VALUES (?, ?, ?) ON CONFLICT DO NOTHING").use { s ->
                    s.setObject(1, session.id)
                    s.setString(2, if (action == NORMAL) "normal" else "to_undo")
                    s.setObject(3, UrneService.mondayOf(now))
                    if (s.executeUpdate() == 1) ActionOutcome.Done else ActionOutcome.AlreadyDecided
                }
            }
            else -> ActionOutcome.Refused("Action inconnue.")
        }
    }

    companion object {
        const val ID: String = "decider.validation_sessions"
        const val REVOKE: String = "revoke"
        const val NORMAL: String = "normal"
        const val TO_UNDO: String = "to_undo"
        const val WINDOW_DAYS: Long = 28
        const val FAST_PER_HOUR: Double = 20.0
        const val FAST_MINIMUM: Int = 10
        const val MANY_PER_WEEK: Int = 3
        private const val MAX_REASON = 1_000
        private val STATE_TEXT = mapOf(
            ValidationSessionState.OPEN to "Ouverte",
            ValidationSessionState.CLOSED to "Fermée par le validateur",
            ValidationSessionState.REVOKED to "Révoquée",
            ValidationSessionState.EXPIRED to "Échue",
        )
    }
}
