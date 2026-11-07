package org.politisationpopulairefrancaise.core.agir.agenda

import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.EventReplacement
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent

/** The `status` of a NIP-52 response. Wire values never change. */
enum class ParticipationStatus(val tagValue: String) {
    ACCEPTED("accepted"),
    DECLINED("declined"),
    TENTATIVE("tentative"),
    ;

    companion object {
        fun fromTag(value: String?): ParticipationStatus? = entries.firstOrNull { it.tagValue == value }
    }
}

/** `ppf-role` of a response: taking part, or co-organising (only for a key the mobilization names). */
enum class ParticipationRole(val tagValue: String) {
    PARTICIPANT(AgirFormats.ROLE_PARTICIPANT),
    CO_ORGANISER(AgirFormats.ROLE_CO_ORGANISER),
    ;

    companion object {
        fun fromTag(value: String?): ParticipationRole? = entries.firstOrNull { it.tagValue == value }
    }
}

/**
 * A public response to a mobilization (contract of the Agir space, version 2; D-044): a NIP-52
 * calendar event RSVP, kind 31925, addressable by `31925:<key>:<d>`, signed by the key that
 * answers. "La transparence passe avant tout": the organisations that take part are shown, but
 * only an event signed by their own key can say so; nobody can make an organisation participate.
 *
 * Only the responses of verified organisations and of editorial keys are ever shown ([Hosting]):
 * the public answer of any other key (a citizen using another client) is read and ignored, since
 * the visibility of citizens waits for a decision of the owner. The [note] (content) is never
 * displayed by the application: there is no free text to review.
 */
data class Participation(
    val event: SignedEvent,
    val address: ContentAddress,
    /** The mobilization answered (`a`, `31923:<key>:<d>`). */
    val mobilization: ContentAddress,
    val status: ParticipationStatus,
    val role: ParticipationRole,
    /** The author of the mobilization (`p`), as the responding key wrote it. */
    val mobilizationAuthor: String?,
    val note: String,
) {
    val author: String get() = event.pubkey
    val updatedAt: Long get() = event.createdAt
    val accepted: Boolean get() = status == ParticipationStatus.ACCEPTED

    companion object {
        const val KIND: Int = AgirFormats.KIND_PARTICIPATION
        const val MAX_NOTE: Int = AgirFormats.MAX_PARTICIPATION_NOTE

        /**
         * Reads a response; null when [event] cannot be one: another kind, no `d`, no readable `a`
         * naming a mobilization, an unknown status. An unknown role claims nothing more than taking
         * part ([ParticipationRole.PARTICIPANT]), as when it is absent.
         */
        fun from(event: SignedEvent): Participation? {
            if (event.kind != KIND) return null
            val address = ContentAddress.of(event)?.takeIf { it.identifier.isNotEmpty() } ?: return null
            val target = event.firstTagValue(AgirFormats.TAG_ADDRESS)?.trim()?.let { runCatching { ContentAddress.parse(it) }.getOrNull() } ?: return null
            if (target.kind != Mobilization.KIND || !Mobilization.isValidIdentifier(target.identifier)) return null
            val status = ParticipationStatus.fromTag(event.firstTagValue(AgirFormats.TAG_STATUS)) ?: return null
            return Participation(
                event = event,
                address = address,
                mobilization = target,
                status = status,
                role = ParticipationRole.fromTag(event.firstTagValue(AgirFormats.TAG_ROLE)) ?: ParticipationRole.PARTICIPANT,
                mobilizationAuthor = event.firstTagValue(AgirFormats.TAG_PUBKEY)?.lowercase()?.takeIf(Mobilization::isValidKey),
                note = event.content,
            )
        }

        /**
         * The `d` of the response of a key to [mobilization]: the address of the mobilization itself,
         * so that each new answer of the same key replaces the previous one (NIP-52 lets every client
         * choose its `d`; [Participations] keeps the newest response of a key whatever its `d`).
         */
        fun identifierFor(mobilization: ContentAddress): String = mobilization.toString()

        /**
         * The response of [author] to [mobilization], tags in the order of the contract: `d`, `a`,
         * `status`, `ppf-role`, `p` (the author of the mobilization). [note] is cut at [MAX_NOTE].
         */
        fun build(
            author: PublicKey,
            mobilization: Mobilization,
            status: ParticipationStatus,
            createdAt: Long,
            role: ParticipationRole = ParticipationRole.PARTICIPANT,
            note: String = "",
        ): UnsignedEvent {
            val tags = listOf(
                listOf("d", identifierFor(mobilization.address)),
                listOf(AgirFormats.TAG_ADDRESS, mobilization.address.toString()),
                listOf(AgirFormats.TAG_STATUS, status.tagValue),
                listOf(AgirFormats.TAG_ROLE, role.tagValue),
                listOf(AgirFormats.TAG_PUBKEY, mobilization.author),
            )
            return UnsignedEvent.create(author, KIND, createdAt, tags, note.trim().take(MAX_NOTE))
        }
    }
}

/**
 * Who organises a mobilization and which organisations take part, as the device shows it (D-044).
 * Every key here counts (a verified organisation or an editorial key) except in [announced].
 */
data class Hosting(
    /** The organisations the mobilization names (`p`, role "organizer") that confirmed it with their own key, in the order of the event. */
    val confirmed: List<String>,
    /** The keys the mobilization names that did not confirm (or whose answer does not count): "annoncée, non confirmée". */
    val announced: List<String>,
    /** The keys that published an `accepted` response, organisers included, first to answer first. */
    val participants: List<String>,
) {
    companion object {
        val NONE: Hosting = Hosting(emptyList(), emptyList(), emptyList())
    }
}

/**
 * The public responses known to the device, grouped by mobilization: for each key, only its newest
 * response counts (another client may answer twice under two `d`; the newest wins, at equal dates
 * the lowest id, as for replaceable events). Which keys count is the caller's: [hosting] takes it.
 */
class Participations private constructor(private val byMobilization: Map<String, List<Participation>>) {

    /** The newest response of each key to the mobilization at [address] (`31923:<key>:<d>`). */
    fun responses(address: String): List<Participation> = byMobilization[address].orEmpty()

    /** The newest response of [key] to the mobilization at [address], or null. */
    fun of(key: String, address: String): Participation? = responses(address).firstOrNull { it.author == key }

    /** The addresses of the mobilizations [key] answered `accepted`. */
    fun acceptedBy(key: String): Set<String> =
        byMobilization.filterValues { list -> list.any { it.author == key && it.accepted } }.keys

    /**
     * Who organises [mobilization] and who takes part, counting only the keys [counts] accepts (a
     * verified organisation or an editorial key). A key the mobilization names as organiser is
     * confirmed when its own newest response is `accepted` with the role "organisateur"; otherwise it
     * is only announced. A key not named cannot make itself co-organiser: its response counts as
     * taking part. Declined and tentative answers are never shown.
     */
    fun hosting(mobilization: Mobilization, counts: (String) -> Boolean): Hosting {
        val responses = responses(mobilization.address.toString())
        val named = mobilization.organizers.filter { it != mobilization.author }
        val confirmed = named.filter { key ->
            counts(key) && responses.any { it.author == key && it.accepted && it.role == ParticipationRole.CO_ORGANISER }
        }
        val participants = responses.filter { it.accepted && counts(it.author) }
            .sortedWith(compareBy<Participation> { it.updatedAt }.thenBy { it.author })
            .map { it.author }
        return Hosting(confirmed = confirmed, announced = named - confirmed.toSet(), participants = participants)
    }

    val size: Int get() = byMobilization.values.sumOf { it.size }

    override fun equals(other: Any?): Boolean = other is Participations && other.byMobilization == byMobilization

    override fun hashCode(): Int = byMobilization.hashCode()

    override fun toString(): String = "Participations($size responses)"

    companion object {
        val EMPTY: Participations = Participations(emptyMap())

        fun of(responses: List<Participation>): Participations {
            val newest = HashMap<Pair<String, String>, Participation>()
            for (response in responses) {
                val key = response.mobilization.toString() to response.author
                val current = newest[key]
                if (current == null || EventReplacement.supersedes(response.event, current.event)) newest[key] = response
            }
            return Participations(newest.values.groupBy { it.mobilization.toString() })
        }

        /** From signed events of any kind: only the readable responses count. */
        fun fromEvents(events: List<SignedEvent>): Participations = of(events.mapNotNull(Participation::from))
    }
}
