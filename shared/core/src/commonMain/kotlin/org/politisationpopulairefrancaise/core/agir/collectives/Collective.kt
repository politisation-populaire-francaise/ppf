package org.politisationpopulairefrancaise.core.agir.collectives

import org.politisationpopulairefrancaise.core.groups.Group
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.territoires.TerritoryCode

/** M24, S-130: how far a collectif reaches (`ppf-scope`). Wire values never change. */
enum class CollectiveScope(val tagValue: String) {
    NATIONAL("national"),
    REGIONAL("regional"),
    DEPARTEMENTAL("departemental"),
    LOCAL("local"),
    QUARTIER("quartier"),
    ETABLISSEMENT("etablissement"),
    ;

    companion object {
        fun fromTag(value: String?): CollectiveScope? = entries.firstOrNull { it.tagValue == value }
    }
}

/** M24, S-130: what kind of group it is (`ppf-collective-type`). Wire values never change. */
enum class CollectiveType(val tagValue: String) {
    COLLECTIF("collectif"),
    CLUB("club"),
    COMITE("comite"),
    ASSEMBLEE("assemblee"),
    RESEAU("reseau"),
    AUTRE("autre"),
    ;

    companion object {
        fun fromTag(value: String?): CollectiveType? = entries.firstOrNull { it.tagValue == value }
    }
}

/**
 * Why a description carrying the collectif marker is not well formed (D-087). [code] is the error
 * the server answers with `400`, so that the device and the server name the same fault.
 */
enum class CollectiveProblem(val code: String) {
    /** Not exactly one `ppf-collective` tag, or a version other than [CollectiveFormat.VERSION]. */
    BAD_MARKER("collective_bad_marker"),
    BAD_SCOPE("collective_bad_scope"),
    BAD_TYPE("collective_bad_type"),
    TOO_MANY_THEMES("collective_too_many_themes"),

    /** An empty, too long, not normalized or repeated theme. */
    BAD_THEME("collective_bad_theme"),
    BAD_LANGUAGE("collective_bad_language"),
    BAD_TERRITORY_CODE("collective_bad_territory_code"),

    /** Several internal charters, or a blank one. */
    BAD_INTERNAL_CHARTER("collective_bad_internal_charter"),
    CHARTER_TOO_LONG("collective_charter_too_long"),

    /** Private and secret collectifs wait for end-to-end encrypted rooms. */
    VISIBILITY_UNSUPPORTED("collective_visibility_unsupported"),

    /** Joining on request or on invitation waits for the choice of the member list (point of attention 26). */
    JOIN_UNSUPPORTED("collective_join_unsupported"),
}

/**
 * The format of the collectifs (M24, decision D-087, docs/FORMATS.md section 4 quaterdecies): a
 * collectif **is** a room of the rooms' directory (NIP-29, kind 39000, [Group]) whose description
 * carries the marker `["ppf-collective", "1"]` and a few tags of its own. Everything else (id, name,
 * presentation, territory label, charter of values, refusal of duplicates, roles and moderation,
 * message thread) is the room's.
 *
 * The same rules run on the device and on the server ([problem], [immutableChange]): the device
 * checks before signing, the server refuses with the same codes.
 *
 * On a mobilization (31923) or a call (30023, `ppf-type` = `appel`), the same tag name
 * `ppf-collective` carries the id of the room of the collectif in whose name it is published
 * ([collectiveOf], [withCollective]).
 */
object CollectiveFormat {
    /** The version of this format, value of the marker. */
    const val VERSION: String = "1"

    /** On a room description: the marker `["ppf-collective", "1"]`; on an event or a call: the id of the collectif. */
    const val TAG_COLLECTIVE: String = "ppf-collective"
    const val TAG_SCOPE: String = "ppf-scope"
    const val TAG_TYPE: String = "ppf-collective-type"

    /** A theme (NIP-12 hashtag form, lowercase). */
    const val TAG_THEME: String = "t"

    /** The language of the collectif, ISO 639-1. */
    const val TAG_LANGUAGE: String = "l"

    /** The code of Comprendre's territories (FORMATS, section 4 nonies), chosen in the referential, never guessed. */
    const val TAG_TERRITORY_CODE: String = "ppf-territory-code"
    const val TAG_INTERNAL_CHARTER: String = "ppf-internal-charter"
    const val TAG_VISIBILITY: String = "ppf-visibility"
    const val TAG_JOIN: String = "ppf-join"

    /** The only visibility accepted in step 1; absent means the same. */
    const val VISIBILITY_PUBLIC: String = "public"

    /** Reserved for step 2 (end-to-end encrypted rooms): never accepted yet, never listed. */
    const val VISIBILITY_SECRET: String = "secret"

    /** The only way of joining accepted in step 1; absent means the same. */
    const val JOIN_OPEN: String = "open"

    const val DEFAULT_LANGUAGE: String = "fr"

    const val MAX_THEMES: Int = 5
    const val MAX_THEME_LENGTH: Int = 40
    const val MAX_INTERNAL_CHARTER_LENGTH: Int = 3000

    /**
     * What never changes once the collectif exists: whether it is a collectif at all, its scope, its
     * territory (label and code), the charter of values accepted, its visibility and how one joins.
     */
    val IMMUTABLE_TAGS: List<String> = listOf(
        TAG_COLLECTIVE, TAG_SCOPE, TAG_TERRITORY_CODE, Group.TAG_TERRITORY, Group.TAG_CHARTER, TAG_VISIBILITY, TAG_JOIN,
    )

    private val LANGUAGE = Regex("^[a-z]{2}$")
    private val SPACES = Regex("\\s+")

    /** Whether a room description says it is a collectif (any `ppf-collective` tag; [problem] then checks it). */
    fun isCollective(tags: List<List<String>>): Boolean = tags.any { it.isNotEmpty() && it[0] == TAG_COLLECTIVE }

    fun isCollective(event: SignedEvent): Boolean = event.kind == Group.KIND && isCollective(event.tags)

    /** A theme as it is published: trimmed, inner spaces collapsed, lowercase, without a leading `#`. */
    fun normalizeTheme(text: String): String =
        text.trim().removePrefix("#").trim().split(SPACES).filter { it.isNotEmpty() }.joinToString(" ").lowercase()

    /** Whether [value] is a theme as published: normalized, 1 to [MAX_THEME_LENGTH] characters, no control character. */
    fun isNormalizedTheme(value: String): Boolean =
        value.isNotEmpty() && value.length <= MAX_THEME_LENGTH && value.none { it.code < 0x20 } && normalizeTheme(value) == value

    fun isLanguage(value: String?): Boolean = value != null && LANGUAGE.matches(value)

    /**
     * The first fault of the collectif tags of a room description that carries the marker; null when
     * they are well formed (or when the description is not a collectif). The tags of the room itself
     * (name, territory, charter) are checked by the rules of the rooms.
     */
    fun problem(tags: List<List<String>>): CollectiveProblem? {
        if (!isCollective(tags)) return null
        fun all(name: String) = tags.filter { it.isNotEmpty() && it[0] == name }
        val markers = all(TAG_COLLECTIVE)
        if (markers.size != 1 || markers[0].getOrNull(1) != VERSION) return CollectiveProblem.BAD_MARKER
        val scopes = all(TAG_SCOPE)
        if (scopes.size != 1 || CollectiveScope.fromTag(scopes[0].getOrNull(1)) == null) return CollectiveProblem.BAD_SCOPE
        val types = all(TAG_TYPE)
        if (types.size != 1 || CollectiveType.fromTag(types[0].getOrNull(1)) == null) return CollectiveProblem.BAD_TYPE
        val themes = all(TAG_THEME).map { it.getOrNull(1) }
        if (themes.size > MAX_THEMES) return CollectiveProblem.TOO_MANY_THEMES
        if (themes.any { it == null || !isNormalizedTheme(it) } || themes.toSet().size != themes.size) return CollectiveProblem.BAD_THEME
        val languages = all(TAG_LANGUAGE)
        if (languages.size > 1 || languages.any { !isLanguage(it.getOrNull(1)) }) return CollectiveProblem.BAD_LANGUAGE
        val codes = all(TAG_TERRITORY_CODE)
        if (codes.size > 1 || codes.any { tag -> tag.getOrNull(1).let { it == null || TerritoryCode.parse(it)?.tag != it } }) {
            return CollectiveProblem.BAD_TERRITORY_CODE
        }
        val charters = all(TAG_INTERNAL_CHARTER)
        if (charters.size > 1 || charters.any { it.getOrNull(1).isNullOrBlank() }) return CollectiveProblem.BAD_INTERNAL_CHARTER
        if (charters.any { it[1].length > MAX_INTERNAL_CHARTER_LENGTH }) return CollectiveProblem.CHARTER_TOO_LONG
        val visibilities = all(TAG_VISIBILITY)
        if (visibilities.size > 1 || visibilities.any { it.getOrNull(1) != VISIBILITY_PUBLIC }) return CollectiveProblem.VISIBILITY_UNSUPPORTED
        val joins = all(TAG_JOIN)
        if (joins.size > 1 || joins.any { it.getOrNull(1) != JOIN_OPEN }) return CollectiveProblem.JOIN_UNSUPPORTED
        return null
    }

    /**
     * The collectif tags added to a room description, in this order: marker, scope, type, themes
     * (normalized, repetitions dropped), language, territory code, internal charter, visibility and
     * way of joining (public and open, the only ones of step 1). Throws [IllegalArgumentException] on
     * more than [MAX_THEMES] themes, a theme or an internal charter too long, a language that is not
     * two letters, or a territory code the referential cannot read.
     */
    fun tags(
        scope: CollectiveScope,
        type: CollectiveType,
        themes: List<String> = emptyList(),
        language: String = DEFAULT_LANGUAGE,
        territoryCode: String? = null,
        internalCharter: String? = null,
    ): List<List<String>> {
        val cleanThemes = themes.map(::normalizeTheme).filter { it.isNotEmpty() }.distinct()
        require(cleanThemes.size <= MAX_THEMES) { "A collectif has at most $MAX_THEMES themes" }
        require(cleanThemes.all(::isNormalizedTheme)) { "A theme has 1 to $MAX_THEME_LENGTH characters" }
        val cleanLanguage = language.trim().lowercase()
        require(isLanguage(cleanLanguage)) { "A language is a two-letter ISO 639-1 code" }
        val code = territoryCode?.trim()?.takeIf { it.isNotEmpty() }?.let { TerritoryCode.parse(it)?.tag ?: throw IllegalArgumentException("Unknown territory code") }
        val charter = internalCharter?.trim()?.takeIf { it.isNotEmpty() }
        require(charter == null || charter.length <= MAX_INTERNAL_CHARTER_LENGTH) { "An internal charter has at most $MAX_INTERNAL_CHARTER_LENGTH characters" }
        return buildList {
            add(listOf(TAG_COLLECTIVE, VERSION))
            add(listOf(TAG_SCOPE, scope.tagValue))
            add(listOf(TAG_TYPE, type.tagValue))
            cleanThemes.forEach { add(listOf(TAG_THEME, it)) }
            add(listOf(TAG_LANGUAGE, cleanLanguage))
            code?.let { add(listOf(TAG_TERRITORY_CODE, it)) }
            charter?.let { add(listOf(TAG_INTERNAL_CHARTER, it)) }
            add(listOf(TAG_VISIBILITY, VISIBILITY_PUBLIC))
            add(listOf(TAG_JOIN, JOIN_OPEN))
        }
    }

    /** The description of a new collectif, ready to be signed by [author]: the room of [Group.build] plus [tags]. */
    fun build(
        author: PublicKey,
        id: String,
        name: String,
        about: String?,
        territory: String,
        createdAt: Long,
        scope: CollectiveScope,
        type: CollectiveType,
        themes: List<String> = emptyList(),
        language: String = DEFAULT_LANGUAGE,
        territoryCode: String? = null,
        internalCharter: String? = null,
        difference: String? = null,
    ): UnsignedEvent {
        val room = Group.build(author, id, name, about, territory, createdAt = createdAt, difference = difference)
        return room.copy(tags = room.tags + tags(scope, type, themes, language, territoryCode, internalCharter))
    }

    /**
     * The first immutable tag ([IMMUTABLE_TAGS]) that differs between the stored description
     * [previous] and its new version [next], or null. Nothing is compared between two plain rooms; a
     * room that becomes a collectif, or a collectif that stops being one, changes `ppf-collective`.
     * An absent visibility or way of joining reads as public and open.
     */
    fun immutableChange(previous: List<List<String>>, next: List<List<String>>): String? {
        if (!isCollective(previous) && !isCollective(next)) return null
        return IMMUTABLE_TAGS.firstOrNull { name -> effectiveValues(previous, name) != effectiveValues(next, name) }
    }

    private fun effectiveValues(tags: List<List<String>>, name: String): List<String> {
        val values = tags.filter { it.isNotEmpty() && it[0] == name }.map { it.getOrNull(1).orEmpty() }
        return when {
            values.isNotEmpty() -> values
            name == TAG_VISIBILITY -> listOf(VISIBILITY_PUBLIC)
            name == TAG_JOIN -> listOf(JOIN_OPEN)
            else -> emptyList()
        }
    }

    /** The id of the collectif a mobilization or a call is published for: its single valid `ppf-collective`; null otherwise. */
    fun collectiveOf(event: SignedEvent): String? = event.tagValues(TAG_COLLECTIVE).singleOrNull()?.takeIf(Group::isValidId)

    /** True when a mobilization or a call carries `ppf-collective` tags that are not exactly one valid room id. */
    fun badReference(tags: List<List<String>>): Boolean {
        val references = tags.filter { it.isNotEmpty() && it[0] == TAG_COLLECTIVE }
        if (references.isEmpty()) return false
        return references.size != 1 || references[0].getOrNull(1)?.let(Group::isValidId) != true
    }

    /** [unsigned] (a mobilization or a call) published for the collectif [collectiveId]: its `ppf-collective` tag set. */
    fun withCollective(unsigned: UnsignedEvent, collectiveId: String): UnsignedEvent {
        require(Group.isValidId(collectiveId)) { "Invalid collectif id" }
        val kept = unsigned.tags.filterNot { it.isNotEmpty() && it[0] == TAG_COLLECTIVE }
        return unsigned.copy(tags = kept + listOf(listOf(TAG_COLLECTIVE, collectiveId)))
    }
}

/**
 * A collectif (M24, D-087) read from the description of its room. Reading is tolerant where a newer
 * version may add values (an unknown type reads as [CollectiveType.AUTRE], an unknown scope as null,
 * an invalid optional tag is ignored); [CollectiveFormat.problem] checks a description exactly.
 *
 * Membership is never published (D-027): joining a collectif is joining its room on the device. The
 * founder is the author of the description, publicly linked to the collectif.
 */
data class Collective(
    /** The room of the collectif: id, name, presentation, territory label, charter, signed description. */
    val group: Group,
    /** Null for a scope this version does not know. */
    val scope: CollectiveScope?,
    val type: CollectiveType,
    /** Normalized, at most [CollectiveFormat.MAX_THEMES]. */
    val themes: List<String>,
    /** ISO 639-1, [CollectiveFormat.DEFAULT_LANGUAGE] when absent. */
    val language: String,
    /** A tag of the referential of territories (`com:69123`), when the founder chose one. */
    val territoryCode: String?,
    val internalCharter: String?,
    val visibility: String,
    val join: String,
) {
    val id: String get() = group.id
    val name: String get() = group.name
    val displayName: String get() = group.displayName
    val about: String? get() = group.about
    val territory: String? get() = group.territory
    val example: Boolean get() = group.example

    /** The founder (lowercase hex): the author of the description, and its first administrator (D-131). */
    val founder: String get() = group.event.pubkey

    fun isFounder(pubkeyHex: String?): Boolean = pubkeyHex != null && pubkeyHex == founder

    /** Secret collectifs never appear in the directory (AG-47); none exists in step 1. */
    val listed: Boolean get() = visibility != CollectiveFormat.VISIBILITY_SECRET

    companion object {
        /** The collectif of a room, or null when its description does not carry the marker. */
        fun from(group: Group): Collective? {
            val event = group.event
            if (!CollectiveFormat.isCollective(event)) return null
            val themes = event.tagValues(CollectiveFormat.TAG_THEME)
                .map(CollectiveFormat::normalizeTheme)
                .filter(CollectiveFormat::isNormalizedTheme)
                .distinct()
                .take(CollectiveFormat.MAX_THEMES)
            return Collective(
                group = group,
                scope = CollectiveScope.fromTag(event.firstTagValue(CollectiveFormat.TAG_SCOPE)),
                type = CollectiveType.fromTag(event.firstTagValue(CollectiveFormat.TAG_TYPE)) ?: CollectiveType.AUTRE,
                themes = themes,
                language = event.firstTagValue(CollectiveFormat.TAG_LANGUAGE)?.takeIf(CollectiveFormat::isLanguage) ?: CollectiveFormat.DEFAULT_LANGUAGE,
                territoryCode = TerritoryCode.parse(event.firstTagValue(CollectiveFormat.TAG_TERRITORY_CODE))?.tag,
                internalCharter = event.firstTagValue(CollectiveFormat.TAG_INTERNAL_CHARTER)?.trim()?.takeIf { it.isNotEmpty() }
                    ?.take(CollectiveFormat.MAX_INTERNAL_CHARTER_LENGTH),
                visibility = event.firstTagValue(CollectiveFormat.TAG_VISIBILITY) ?: CollectiveFormat.VISIBILITY_PUBLIC,
                join = event.firstTagValue(CollectiveFormat.TAG_JOIN) ?: CollectiveFormat.JOIN_OPEN,
            )
        }

        /** The collectif described by [event], or null (not a room description, or a plain room). */
        fun from(event: SignedEvent): Collective? = Group.from(event)?.let(::from)
    }
}
