package org.politisationpopulairefrancaise.core.agir.agenda

import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.text.FrenchText
import kotlin.random.Random

/** M1: what kind of mobilization it is (`ppf-event-type`). Wire values never change. */
enum class MobilizationType(val tagValue: String) {
    MANIFESTATION("manifestation"),
    BLOCAGE("blocage"),
    RASSEMBLEMENT("rassemblement"),
    GREVE("greve"),
    ASSEMBLEE_GENERALE("assemblee-generale"),
    REUNION("reunion"),
    AUTRE("autre"),
    ;

    companion object {
        fun fromTag(value: String?): MobilizationType? = entries.firstOrNull { it.tagValue == value }
    }
}

/** Where a mobilization stands (`ppf-event-status`); absent means [PREVU]. */
enum class MobilizationStatus(val tagValue: String) {
    PREVU("prevu"),
    ANNULE("annule"),
    REPORTE("reporte"),
    ;

    companion object {
        fun fromTag(value: String?): MobilizationStatus? = entries.firstOrNull { it.tagValue == value }
    }
}

/** For a demonstration: where its declaration to the prefecture stands (`ppf-declaration`). */
enum class DeclarationState(val tagValue: String) {
    FAITE("faite"),
    EN_COURS("en-cours"),
    NON_FAITE("non-faite"),
    ;

    companion object {
        fun fromTag(value: String?): DeclarationState? = entries.firstOrNull { it.tagValue == value }
    }
}

/**
 * A mobilization of the agenda (M1, AG-01): a NIP-52 time-based calendar event, kind 31923,
 * addressable by `31923:<pubkey>:<d>` (contract of the Agir space, section 2). A corrected or
 * cancelled event is republished under the same address; the newest version wins
 * ([org.politisationpopulairefrancaise.core.signing.EventReplacement], like every addressable content).
 *
 * Reading is tolerant where a newer version of the contract may add values (an unknown type reads
 * as [MobilizationType.AUTRE], an unknown status as [MobilizationStatus.PREVU], an invalid optional
 * tag is ignored) and strict on what the agenda cannot show without (identifier, title, start,
 * place, territory): such an event is not a mobilization at all. [MobilizationRules] checks an
 * event exactly, like the server does before publishing it.
 */
data class Mobilization(
    val event: SignedEvent,
    val address: ContentAddress,
    /** As written, with the [EXEMPLE] marker of example contents when present. */
    val title: String,
    /** Seconds since 1970. */
    val start: Long,
    /** Seconds since 1970, strictly after [start]; null when the author gave no end. */
    val end: Long?,
    /** `start_tzid` as written ("Europe/Paris"), null when absent. */
    val timeZone: String?,
    val location: String,
    /** Placed by the author only, never computed by a third-party service. */
    val geohash: String?,
    val type: MobilizationType,
    val status: MobilizationStatus,
    /** A town, a department or "France". */
    val territory: String,
    val charter: String?,
    val declaration: DeclarationState?,
    /** Keys (lowercase hex) of the organisations named as organisers (AG-21), in their order. */
    val organizers: List<String>,
    /** Address of the call this event comes from (AP-07). */
    val call: String?,
    /** Web links (`r`), http or https only. */
    val links: List<String>,
    val summary: String?,
    /** The description (event content), plain text. */
    val description: String,
) {
    val identifier: String get() = address.identifier
    val author: String get() = event.pubkey
    val updatedAt: Long get() = event.createdAt

    /** Example contents carry the marker required by the cahier des charges; it is shown as a badge. */
    val example: Boolean get() = title.trimStart().startsWith(EXAMPLE_MARKER)

    /** The title without the example marker. */
    val displayTitle: String get() = title.trimStart().removePrefix(EXAMPLE_MARKER).trim()

    /** Who carries the event: the organisers named, or else its author. */
    val hosts: List<String> get() = organizers.ifEmpty { listOf(author) }

    val cancelled: Boolean get() = status == MobilizationStatus.ANNULE

    /**
     * When the event is over, for the agenda: its end, or [DEFAULT_DURATION] after its start when
     * it has none (a gathering at 14 h is still "en cours" at 15 h).
     */
    val endsAt: Long get() = end ?: (start + DEFAULT_DURATION)

    /** True while [now] is between the start and [endsAt]. */
    fun isOngoing(now: Long): Boolean = now in start until endsAt

    fun isPast(now: Long): Boolean = endsAt <= now

    /** True when [key] (lowercase hex) organises the event, named as organiser or as its author. */
    fun isHostedBy(key: String): Boolean = key == author || key in organizers

    companion object {
        const val KIND: Int = AgirFormats.KIND_MOBILIZATION
        const val EXAMPLE_MARKER: String = "[EXEMPLE]"

        /** How long an event without end is considered under way. */
        const val DEFAULT_DURATION: Long = 2 * 3_600

        const val TAG_IDENTIFIER: String = "d"

        /** Longest summary, in characters (device rule, proposed to the server). */
        const val MAX_SUMMARY: Int = 280

        /** Longest place, in characters (device rule, proposed to the server). */
        const val MAX_LOCATION: Int = 200

        /** Longest territory, in characters (the same as the rooms of collectives). */
        const val MAX_TERRITORY: Int = 100

        /** Longest web link, in characters. */
        const val MAX_LINK: Int = 2_000

        private val IDENTIFIER = Regex("^[a-z0-9._~-]{1,64}$")
        private val GEOHASH = Regex("^[0-9bcdefghjkmnpqrstuvwxyz]{1,9}$")
        private val HEX_KEY = Regex("^[0-9a-f]{64}$")

        fun isValidIdentifier(value: String): Boolean = IDENTIFIER.matches(value)

        fun isValidGeohash(value: String): Boolean = GEOHASH.matches(value)

        fun isValidKey(value: String): Boolean = HEX_KEY.matches(value)

        /** A web link the application may open: http or https, no space, a host. */
        fun isValidLink(value: String): Boolean {
            if (value.length > MAX_LINK || value.any { it.isWhitespace() || it.code < 0x20 }) return false
            val lower = value.lowercase()
            val rest = when {
                lower.startsWith("https://") -> value.substring(8)
                lower.startsWith("http://") -> value.substring(7)
                else -> return false
            }
            val host = rest.takeWhile { it != '/' && it != '?' && it != '#' }.substringAfterLast('@')
            return host.isNotEmpty() && host.first() != ':'
        }

        /** Reads a mobilization; null when [event] is not one the agenda can show (see the class). */
        fun from(event: SignedEvent): Mobilization? {
            if (event.kind != KIND) return null
            val address = ContentAddress.of(event) ?: return null
            if (!isValidIdentifier(address.identifier)) return null
            val title = event.firstTagValue(AgirFormats.TAG_TITLE)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val start = event.firstTagValue(AgirFormats.TAG_START)?.trim()?.toLongOrNull()?.takeIf { it >= 0 } ?: return null
            val location = event.firstTagValue(AgirFormats.TAG_LOCATION)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val territory = event.firstTagValue(AgirFormats.TAG_TERRITORY)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val end = event.firstTagValue(AgirFormats.TAG_END)?.trim()?.toLongOrNull()?.takeIf { it > start }
            return Mobilization(
                event = event,
                address = address,
                title = title,
                start = start,
                end = end,
                timeZone = event.firstTagValue(AgirFormats.TAG_START_TZID)?.trim()?.takeIf { it.isNotEmpty() },
                location = location,
                geohash = event.firstTagValue(AgirFormats.TAG_GEOHASH)?.trim()?.lowercase()?.takeIf(::isValidGeohash),
                type = MobilizationType.fromTag(event.firstTagValue(AgirFormats.TAG_EVENT_TYPE)) ?: MobilizationType.AUTRE,
                status = MobilizationStatus.fromTag(event.firstTagValue(AgirFormats.TAG_EVENT_STATUS)) ?: MobilizationStatus.PREVU,
                territory = territory,
                charter = event.firstTagValue(AgirFormats.TAG_CHARTER),
                declaration = DeclarationState.fromTag(event.firstTagValue(AgirFormats.TAG_DECLARATION)),
                organizers = organizersOf(event),
                call = event.firstTagValue(AgirFormats.TAG_ADDRESS)?.takeIf { runCatching { ContentAddress.parse(it) }.isSuccess },
                links = event.tagValues(AgirFormats.TAG_LINK).map { it.trim() }.filter(::isValidLink).distinct(),
                summary = event.firstTagValue(AgirFormats.TAG_SUMMARY)?.trim()?.takeIf { it.isNotEmpty() },
                description = event.content,
            )
        }

        /** The `p` tags whose fourth value is "organizer" (NIP-52), valid keys only, without repetition. */
        fun organizersOf(event: SignedEvent): List<String> =
            event.tags.asSequence()
                .filter { it.size >= 4 && it[0] == AgirFormats.TAG_PUBKEY && it[3] == AgirFormats.ROLE_ORGANIZER }
                .map { it[1].lowercase() }
                .filter(::isValidKey)
                .distinct()
                .toList()

        /**
         * A new stable identifier: the beginning of the title as a slug, then eight random
         * hexadecimal characters, so that two events with the same title never share an address.
         */
        fun newIdentifier(title: String, random: Random = Random.Default): String {
            val slug = slugify(title).take(40).trim('-')
            val suffix = (0 until 8).joinToString("") { random.nextInt(16).toString(16) }
            return if (slug.isEmpty()) "evenement-$suffix" else "$slug-$suffix"
        }

        /** Lowercase ASCII letters and digits separated by dashes ("Grève du 5 décembre" becomes "greve-du-5-decembre"). */
        fun slugify(text: String): String =
            FrenchText.fold(text)
                .map { if (it in 'a'..'z' || it in '0'..'9') it else '-' }
                .joinToString("")
                .split('-')
                .filter { it.isNotEmpty() }
                .joinToString("-")

        /**
         * The unsigned event of a mobilization, tags in the order of the contract. The status tag is
         * written only when it is not [MobilizationStatus.PREVU] (absent means "prévu").
         */
        fun build(
            author: PublicKey,
            identifier: String,
            createdAt: Long,
            title: String,
            start: Long,
            location: String,
            type: MobilizationType,
            territory: String,
            end: Long? = null,
            status: MobilizationStatus = MobilizationStatus.PREVU,
            declaration: DeclarationState? = null,
            organizers: List<String> = emptyList(),
            call: String? = null,
            links: List<String> = emptyList(),
            summary: String? = null,
            description: String = "",
            geohash: String? = null,
        ): UnsignedEvent {
            val tags = buildList {
                add(listOf(TAG_IDENTIFIER, identifier))
                add(listOf(AgirFormats.TAG_TITLE, title.trim()))
                add(listOf(AgirFormats.TAG_START, start.toString()))
                if (end != null) add(listOf(AgirFormats.TAG_END, end.toString()))
                add(listOf(AgirFormats.TAG_START_TZID, AgirFormats.TIME_ZONE))
                add(listOf(AgirFormats.TAG_LOCATION, location.trim()))
                geohash?.let { add(listOf(AgirFormats.TAG_GEOHASH, it)) }
                add(listOf(AgirFormats.TAG_EVENT_TYPE, type.tagValue))
                if (status != MobilizationStatus.PREVU) add(listOf(AgirFormats.TAG_EVENT_STATUS, status.tagValue))
                add(listOf(AgirFormats.TAG_TERRITORY, territory.trim()))
                add(listOf(AgirFormats.TAG_CHARTER, AgirFormats.CHARTER_VERSION))
                declaration?.let { add(listOf(AgirFormats.TAG_DECLARATION, it.tagValue)) }
                organizers.distinct().forEach { add(listOf(AgirFormats.TAG_PUBKEY, it, "", AgirFormats.ROLE_ORGANIZER)) }
                call?.let { add(listOf(AgirFormats.TAG_ADDRESS, it)) }
                links.forEach { add(listOf(AgirFormats.TAG_LINK, it.trim())) }
                summary?.trim()?.takeIf { it.isNotEmpty() }?.let { add(listOf(AgirFormats.TAG_SUMMARY, it)) }
            }
            return UnsignedEvent.create(author, KIND, createdAt, tags, description.trim())
        }
    }
}
