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

    /**
     * The action with the value chosen in its [QueueAction.choice], already checked by the base (present,
     * within the rules, among the options computed again at the moment of the action). The base calls
     * this one; a queue without choices keeps only [perform] above.
     */
    fun perform(itemId: String, action: String, reason: String?, choice: String?, actor: TeamActor): ActionOutcome =
        perform(itemId, action, reason, actor)
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
    /**
     * A value the member must choose or type with the action (an account among the moderators of a
     * room, a public key), checked by the base and written in the journal: never free text.
     */
    val choice: QueueChoice? = null,
) {
    init {
        require(Regex("^[a-z0-9_]{1,40}$").matches(id)) { "An action id is a short lowercase word" }
        require(!reasonRequired || reasonAllowed)
        // A request waiting for a second person cannot keep a choice yet (it would need a migration).
        require(choice == null || !twoPerson) { "An action with a choice cannot wait for a second person" }
    }
}

/** What the member chooses with an action (contract of the back-office, section 7, addition of 8 November 2026). */
sealed interface QueueChoice {
    val label: String

    /** One of [options], shown as radio buttons; the base accepts only one of the options of the moment. */
    data class OneOf(override val label: String, val options: List<QueueOption>) : QueueChoice {
        init {
            require(options.isNotEmpty()) { "A choice has at least one option" }
            require(options.map { it.value }.distinct().size == options.size) { "The options of a choice are distinct" }
        }
    }

    /** A short value typed by the member (a public key, an identifier), at most [maxLength] characters, matching [pattern]. */
    data class Text(override val label: String, val maxLength: Int, val pattern: Regex? = null) : QueueChoice {
        init {
            require(maxLength in 1..MAX_LENGTH) { "A typed choice has 1 to $MAX_LENGTH characters" }
        }
    }

    companion object {
        /** The longest value a choice may carry into the journal. */
        const val MAX_LENGTH: Int = 200

        /**
         * The value of [choice] if it is acceptable: present, short enough, matching, among the options;
         * null otherwise.
         */
        fun accepted(choice: QueueChoice, value: String?): String? {
            val given = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return when (choice) {
                is OneOf -> given.takeIf { candidate -> choice.options.any { it.value == candidate } }
                is Text -> given.takeIf { it.length <= choice.maxLength && (choice.pattern == null || choice.pattern.matches(it)) }
            }
        }
    }
}

/** One option of a [QueueChoice.OneOf]: the value written in the journal and its French label. */
data class QueueOption(val value: String, val label: String) {
    init {
        require(value.isNotBlank() && value.length <= QueueChoice.MAX_LENGTH) { "An option value has 1 to ${QueueChoice.MAX_LENGTH} characters" }
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
