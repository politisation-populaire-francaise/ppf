package org.politisationpopulairefrancaise.server.groups

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.sql.DataSource

/**
 * The guardrails of the creation of a room against duplicates and the squatting of a name (D-132,
 * contract salons-roles.md, section 3 bis), beyond the refusal of exact duplicates:
 *
 * - at most [maxPerDay] rooms created by a key in 24 hours (held creations count);
 * - a name close to the rooms of the same territory or a parent one ([parents]) is refused with the
 *   list (`409 group_similar`) unless the creator confirms and says how the room differs;
 * - a name close to a verified organisation's ([organisations]) is held for the team, and refused
 *   to a key first seen less than [youngAccountDays] days ago; a name close to a room of another
 *   territory with at least [activeRoomMessages] messages is held too.
 *
 * [isTrusted] (editorial keys) bypasses the organisations; an organisation's own keys bypass its name.
 */
class GroupGuardrails(
    private val dataSource: DataSource,
    private val organisations: OrganisationNames? = null,
    private val isTrusted: (String) -> Boolean = { false },
    val parents: TerritoryParents = TerritoryParents.NATIONAL_ONLY,
    val creations: GroupCreations = GroupCreations(dataSource),
    val maxPerDay: Int = DEFAULT_MAX_PER_DAY,
    val youngAccountDays: Long = DEFAULT_YOUNG_ACCOUNT_DAYS,
    val activeRoomMessages: Int = DEFAULT_ACTIVE_ROOM_MESSAGES,
    private val clock: () -> Instant = Instant::now,
) {
    /** Rooms described by [pubkey] in the last 24 hours, stored or held. */
    fun createdToday(pubkey: String): Int = dataSource.connection.use { connection ->
        val stored = connection.prepareStatement(
            "SELECT count(DISTINCT d.group_id) FROM group_directory d JOIN events e ON e.id = d.event_id WHERE e.pubkey = ? AND e.received_at > ?",
        ).use { statement ->
            statement.setString(1, pubkey)
            statement.setObject(2, now().minusDays(1))
            val rows = statement.executeQuery()
            rows.next()
            rows.getInt(1)
        }
        stored + creations.pendingToday(connection, pubkey)
    }

    /** A key the server first saw less than [youngAccountDays] days ago, or never. */
    fun isYoung(pubkey: String): Boolean = youngAccountDays > 0 && dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT min(received_at) FROM events WHERE pubkey = ?").use { statement ->
            statement.setString(1, pubkey)
            val rows = statement.executeQuery()
            val first = if (rows.next()) rows.getObject(1, OffsetDateTime::class.java) else null
            first == null || first.isAfter(now().minusDays(youngAccountDays))
        }
    }

    /** The verified organisations whose name the room name [nameKey] on [territoryKey] resembles, unless [pubkey] is trusted or theirs. */
    fun organisationsResembling(nameKey: String, territoryKey: String, pubkey: String): List<String> {
        val known = organisations ?: return emptyList()
        if (isTrusted(pubkey)) return emptyList()
        val words = GroupSimilarity.significantWords(nameKey, territoryKey)
        return known.all()
            .filter { pubkey !in it.keys && GroupSimilarity.resemblesOrganisation(words, it.words) }
            .map { it.name }
            .distinct()
    }

    /** Forgets the cached names of the organisations: an entry of the directory was just stored. */
    fun organisationsChanged() {
        organisations?.invalidate()
    }

    private fun now(): OffsetDateTime = OffsetDateTime.ofInstant(clock(), ZoneOffset.UTC)

    companion object {
        const val DEFAULT_MAX_PER_DAY: Int = 3
        const val DEFAULT_YOUNG_ACCOUNT_DAYS: Long = 7
        const val DEFAULT_ACTIVE_ROOM_MESSAGES: Int = 200

        /** At most this many similar rooms are listed in a `409 group_similar`. */
        const val MAX_SIMILAR: Int = 10

        /**
         * PPF_GROUP_YOUNG_ACCOUNT_DAYS: how many days a key must have been known before it may create a
         * room named like an organisation (0 to 365, 7 by default). 0 on the development server only, to
         * try the hold of a creation with a new account.
         */
        const val YOUNG_ACCOUNT_DAYS_VARIABLE: String = "PPF_GROUP_YOUNG_ACCOUNT_DAYS"

        fun youngAccountDaysFromEnvironment(env: (String) -> String? = System::getenv): Long =
            env(YOUNG_ACCOUNT_DAYS_VARIABLE)?.trim()?.toLongOrNull()?.takeIf { it in 0..365 } ?: DEFAULT_YOUNG_ACCOUNT_DAYS
    }
}
