package org.politisationpopulairefrancaise.core.groups

import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.text.normalizeNfkd

/**
 * A room of a collective, union or association: a NIP-29 group hosted by our server (decisions
 * D-027 and D-028). Its description is an addressable event of kind 39000 whose `d` tag is the
 * room id; `name`, `about` and `picture` follow NIP-29, `ppf-territory` and `ppf-charter` are the
 * platform's own tags: a room always belongs to a territory and its creator always accepted the
 * charter of values. Any account may describe a room; the server refuses a second room with the
 * same name on the same territory (`group_exists`) and an id already described by another key
 * (`group_taken`).
 *
 * Membership is never published: joining a room stays on the device ([GroupStore]). The server
 * holds no list of members.
 */
data class Group(
    /** The room id: 1 to 64 characters among `a-z`, `0-9`, `.`, `_`, `~`, `-`, derived from the name. */
    val id: String,
    val name: String,
    val about: String?,
    val picture: String?,
    val territory: String?,
    /** The version of the charter of values accepted by the creator (`1.0`). */
    val charter: String?,
    /** Readable by everyone (no `private` flag). Always true in this version. */
    val public: Boolean,
    /** Any account may write (neither `closed` nor `restricted`). */
    val open: Boolean,
    /** The signed description itself, verified by the caller before it is trusted or stored. */
    val event: SignedEvent,
) {
    /** Example rooms carry the marker required by the cahier des charges; it is shown as a badge. */
    val example: Boolean get() = name.trimStart().startsWith(EXAMPLE_MARKER)

    /** The name without the example marker. */
    val displayName: String get() = name.trimStart().removePrefix(EXAMPLE_MARKER).trim()

    companion object {
        const val KIND: Int = EventKinds.GROUP_METADATA

        const val TAG_ID: String = "d"
        const val TAG_NAME: String = "name"
        const val TAG_ABOUT: String = "about"
        const val TAG_PICTURE: String = "picture"
        const val TAG_TERRITORY: String = "ppf-territory"
        const val TAG_CHARTER: String = "ppf-charter"

        /** The current version of the charter of values, written in the `ppf-charter` tag. */
        const val CHARTER_VERSION: String = "1.0"

        const val EXAMPLE_MARKER: String = "[EXEMPLE]"

        const val MAX_ID_LENGTH: Int = 64
        const val MAX_NAME_LENGTH: Int = 80
        const val MAX_TERRITORY_LENGTH: Int = 80
        const val MAX_ABOUT_LENGTH: Int = 1000

        /** NIP-29 flags carried as one-element tags. */
        private const val FLAG_PRIVATE = "private"
        private const val FLAG_HIDDEN = "hidden"
        private const val FLAG_CLOSED = "closed"
        private const val FLAG_RESTRICTED = "restricted"

        private val ID_PATTERN = Regex("[a-z0-9._~-]{1,$MAX_ID_LENGTH}")

        fun isValidId(id: String): Boolean = ID_PATTERN.matches(id)

        /**
         * Reads a room from its description, or null when the event is not a well-formed room
         * description: wrong kind, not exactly one `d` tag, or an invalid id. The name falls back to
         * the id. The caller checks the signature.
         */
        fun from(event: SignedEvent): Group? {
            if (event.kind != KIND) return null
            val ids = event.tagValues(TAG_ID)
            val id = ids.singleOrNull() ?: return null
            if (!isValidId(id)) return null
            val flags = event.tags.filter { it.size == 1 }.map { it[0] }.toSet()
            return Group(
                id = id,
                name = event.firstTagValue(TAG_NAME)?.takeIf { it.isNotBlank() } ?: id,
                about = event.firstTagValue(TAG_ABOUT)?.takeIf { it.isNotBlank() },
                picture = event.firstTagValue(TAG_PICTURE)?.takeIf { it.isNotBlank() },
                territory = event.firstTagValue(TAG_TERRITORY)?.takeIf { it.isNotBlank() },
                charter = event.firstTagValue(TAG_CHARTER)?.takeIf { it.isNotBlank() },
                public = FLAG_PRIVATE !in flags && FLAG_HIDDEN !in flags,
                open = FLAG_CLOSED !in flags && FLAG_RESTRICTED !in flags,
                event = event,
            )
        }

        /**
         * The description of a new or edited room, ready to be signed by [author]. The room is
         * public and open (no flag). Throws [IllegalArgumentException] on an invalid id, a blank
         * name or territory, or a text beyond the bounds.
         */
        fun build(
            author: PublicKey,
            id: String,
            name: String,
            about: String?,
            territory: String,
            charter: String = CHARTER_VERSION,
            createdAt: Long,
        ): UnsignedEvent {
            require(isValidId(id)) { "A room id has 1 to $MAX_ID_LENGTH characters among a-z, 0-9, '.', '_', '~' and '-'" }
            val cleanName = name.trim()
            val cleanTerritory = territory.trim()
            val cleanAbout = about?.trim()?.takeIf { it.isNotEmpty() }
            require(cleanName.isNotEmpty() && cleanName.length <= MAX_NAME_LENGTH) { "A room name has 1 to $MAX_NAME_LENGTH characters" }
            require(cleanTerritory.isNotEmpty() && cleanTerritory.length <= MAX_TERRITORY_LENGTH) { "A territory has 1 to $MAX_TERRITORY_LENGTH characters" }
            require(cleanAbout == null || cleanAbout.length <= MAX_ABOUT_LENGTH) { "A description has at most $MAX_ABOUT_LENGTH characters" }
            require(charter.isNotBlank()) { "The charter version is required" }
            val tags = buildList {
                add(listOf(TAG_ID, id))
                add(listOf(TAG_NAME, cleanName))
                if (cleanAbout != null) add(listOf(TAG_ABOUT, cleanAbout))
                add(listOf(TAG_TERRITORY, cleanTerritory))
                add(listOf(TAG_CHARTER, charter))
            }
            return UnsignedEvent.create(author, KIND, createdAt, tags)
        }

        /**
         * The id derived from a name: accents removed, lowercase, any run of other characters
         * replaced by one hyphen, at most [MAX_ID_LENGTH] characters. The example marker is dropped.
         * Empty when the name holds no letter or digit (the caller then asks for another name).
         */
        fun slugify(name: String): String {
            val decomposed = normalizeNfkd(name.trimStart().removePrefix(EXAMPLE_MARKER)).lowercase()
            val builder = StringBuilder()
            var separate = false
            for (char in decomposed) {
                when {
                    char in 'a'..'z' || char in '0'..'9' -> {
                        if (separate && builder.isNotEmpty()) builder.append('-')
                        separate = false
                        builder.append(char)
                    }
                    char.code in 0x0300..0x036F -> Unit // combining accent: dropped
                    else -> separate = true
                }
            }
            return builder.toString().take(MAX_ID_LENGTH).trimEnd('-')
        }

        /**
         * The id proposed for a new room: its name then its territory, slugified
         * (`collectif-sante-lyon`), so that rooms of the same name on different territories do not
         * compete for one id; a name that already ends with the territory does not repeat it.
         * Empty when the name holds no letter or digit.
         */
        fun idFor(name: String, territory: String): String {
            val base = slugify(name)
            if (base.isEmpty()) return ""
            val place = slugify(territory)
            val full = if (place.isEmpty() || base == place || base.endsWith("-$place")) base else "$base-$place"
            return full.take(MAX_ID_LENGTH).trimEnd('-')
        }

        /** The fallback id number [attempt] (2, 3…) when [id] is taken: `-2`, `-3`… within the length limit. */
        fun numbered(id: String, attempt: Int): String {
            val suffix = "-$attempt"
            return id.take(MAX_ID_LENGTH - suffix.length).trimEnd('-') + suffix
        }

        /**
         * Whether two rooms are duplicates in the sense of the server: same normalized name on the
         * same normalized territory (accents and case ignored, punctuation and spacing collapsed).
         */
        fun sameNameAndTerritory(a: Group, b: Group): Boolean =
            slugify(a.name) == slugify(b.name) && slugify(a.territory ?: "") == slugify(b.territory ?: "")
    }
}

/** A message posted in a room: a kind 9 event whose single `h` tag names the room. Plain text. */
data class GroupMessage(
    val id: String,
    val groupId: String,
    /** Author's public key, lowercase hex. */
    val author: String,
    val text: String,
    val createdAt: Long,
    val event: SignedEvent,
) {
    companion object {
        const val KIND: Int = EventKinds.GROUP_CHAT
        const val TAG_GROUP: String = "h"

        /** Enough for a long paragraph; the server caps the whole event at 64 KiB anyway. */
        const val MAX_TEXT_LENGTH: Int = 4000

        /** Reads a room message, or null when the event is not one (wrong kind, not exactly one `h`, empty text). */
        fun from(event: SignedEvent): GroupMessage? {
            if (event.kind != KIND) return null
            val groupId = event.tagValues(TAG_GROUP).singleOrNull() ?: return null
            if (!Group.isValidId(groupId)) return null
            if (event.content.isBlank()) return null
            return GroupMessage(event.id, groupId, event.pubkey, event.content, event.createdAt, event)
        }

        /** A message for [groupId], ready to be signed. Throws on an invalid id or an empty or too long text. */
        fun build(author: PublicKey, groupId: String, text: String, createdAt: Long): UnsignedEvent {
            require(Group.isValidId(groupId)) { "Invalid room id" }
            val content = text.trim()
            require(content.isNotEmpty()) { "A message has some text" }
            require(content.length <= MAX_TEXT_LENGTH) { "A message has at most $MAX_TEXT_LENGTH characters" }
            return UnsignedEvent.create(author, KIND, createdAt, listOf(listOf(TAG_GROUP, groupId)), content)
        }
    }
}
