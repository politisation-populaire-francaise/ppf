package org.politisationpopulairefrancaise.server.backoffice

import kotlinx.html.FlowContent
import java.util.concurrent.ConcurrentHashMap

/**
 * A review or decision queue plugged into the back-office by a pole (contract, section 7). The pole
 * implements it and registers it in `Application.module` ([BackOfficeQueues.register]); the base does
 * everything else: sign-in, permissions, anti-forgery token, new passkey verification, two-person rule,
 * journal line, dashboard counts. [perform] does only the action asked and never writes the journal.
 *
 * A queue shows no more personal data than the decision needs (the content in question, the
 * pseudonymous key of its author). Calls happen on a thread that may block (plain JDBC is fine).
 */
interface BackOfficeQueue {
    /** Stable, for example `agir.held` or `revendications.proposals`. */
    val id: String

    /** French label shown to the team. */
    val title: String

    /** Who sees the queue, for example `queue.agir.held.decide`. */
    val viewPermission: String

    fun pendingCount(): Int

    /** Items, by deadline or newest first, [limit] at most, from [cursor] (null for the first page). */
    fun page(cursor: String?, limit: Int): QueuePage

    /** The kotlinx.html detail of one item (the queue renders its own message for an unknown item). */
    fun render(itemId: String, html: FlowContent)

    /** What the team may do on the item; empty when it is decided or unknown. */
    fun actions(itemId: String): List<QueueAction>

    fun perform(itemId: String, action: String, reason: String?, actor: TeamActor): ActionOutcome
}

/** One page of a queue. */
data class QueuePage(val items: List<QueueItem>, val nextCursor: String? = null)

/**
 * One item of a queue as the lists and the dashboard show it. [deadline] (seconds since 1970) and
 * [level] (importance 1 to 5, S-115) are optional: the dashboard sorts by deadline then level and
 * flags what is past or near its deadline.
 */
data class QueueItem(
    val id: String,
    val title: String,
    /** Seconds since 1970. */
    val receivedAt: Long? = null,
    val deadline: Long? = null,
    val level: Int? = null,
    /** A short French line under the title (type, territory); never the full text of a content. */
    val summary: String? = null,
) {
    init {
        require(id.isNotBlank() && id.length <= 200) { "An item id has 1 to 200 characters" }
        require(level == null || level in 1..5) { "The importance level is between 1 and 5" }
    }
}

/**
 * An action on an item (contract, section 7): whether a reason is required (always for a refusal),
 * whether it is sensitive (new passkey verification when the last one is older than 5 minutes), whether
 * it needs a second person, and the permission it requires.
 */
data class QueueAction(
    /** Stable, for example `approve` or `reject` (it becomes part of the journal's action identifier). */
    val id: String,
    /** French label of the button. */
    val label: String,
    val permission: String,
    val reasonRequired: Boolean = false,
    /** Whether a reason may be typed when it is not required. */
    val reasonAllowed: Boolean = reasonRequired,
    val sensitive: Boolean = false,
    val twoPerson: Boolean = false,
) {
    init {
        require(Regex("^[a-z0-9_]{1,40}$").matches(id)) { "An action id is a short lowercase word" }
        require(!reasonRequired || reasonAllowed)
    }
}

/** What [BackOfficeQueue.perform] answers. */
sealed interface ActionOutcome {
    data object Done : ActionOutcome

    data object NotFound : ActionOutcome

    data object AlreadyDecided : ActionOutcome

    /** The queue refused, with a French [reason] shown to the team. */
    data class Refused(val reason: String) : ActionOutcome
}

/** The queues plugged into the back-office (S-64). Registered once at startup; ids are unique. */
class BackOfficeQueues {
    private val queues = ConcurrentHashMap<String, BackOfficeQueue>()
    private val order = mutableListOf<String>()

    @Synchronized
    fun register(queue: BackOfficeQueue) {
        require(QUEUE_ID.matches(queue.id)) { "A queue id is a dotted lowercase identifier: ${queue.id}" }
        require(Permissions.isValidId(queue.viewPermission)) { "A view permission is a dotted identifier" }
        require(queues.putIfAbsent(queue.id, queue) == null) { "The queue ${queue.id} is already registered" }
        order += queue.id
    }

    fun find(id: String): BackOfficeQueue? = queues[id]

    /** Every queue, in the order of registration. */
    @Synchronized
    fun all(): List<BackOfficeQueue> = order.mapNotNull(queues::get)

    /** The queues [actor] may see. */
    fun visibleTo(actor: TeamActor): List<BackOfficeQueue> = all().filter { actor.has(it.viewPermission) }

    companion object {
        private val QUEUE_ID = Regex("^[a-z0-9_]+(\\.[a-z0-9_]+)*$")
    }
}
