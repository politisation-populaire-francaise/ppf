package org.politisationpopulairefrancaise.core.editorial.schema

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent

/*
 * The declarative schema of the editorial circuit (docs/integration/circuit-editorial.md, section 1, D-092):
 * one schema per type of content describes its front matter fields, how each becomes a tag of the event,
 * its body and its rules. The same schema feeds the form of the back-office, the checks of the server and
 * of tools/content-signer, and the event builder ([EditorialEngine]). Common code: no java.*, no reflection.
 * The texts of the interface are not here, only translation keys (labelKey, helpKey, messageKey).
 */

/** The spaces that own an editorial queue (`editorial.<id>`). */
enum class EditorialSpace(val id: String) {
    COMPRENDRE("comprendre"),
    VOIR("voir"),
}

/** Whether a common field of every article (summary, topics, duration) is required, optional or absent. */
enum class Presence { REQUIRED, OPTIONAL, ABSENT }

/** One type of editorial content, as the form, the checks and the event builder see it. */
data class ContentSchema(
    /** The `ppf-type` of the event (existing enum, values never renamed). */
    val type: ArticleType,
    val space: EditorialSpace,
    /** "editorial.type.qui_decide". */
    val labelKey: String,
    /** The folder of content/ read by the tool: "qui-decide", "voir/faits". */
    val sourceDirectory: String,
    /** The bundled JSON written by the tool: "comprendre.json", "voir.json". */
    val bundle: String,
    val summary: Presence = Presence.REQUIRED,
    val topics: Presence = Presence.OPTIONAL,
    /** `duration` in minutes, carried by Article.build as `ppf-duration` (courses only). */
    val duration: Presence = Presence.ABSENT,
    /** The specific fields, in the order of the form. The tags follow the order of the entries of the draft. */
    val fields: List<FieldSpec>,
    val body: BodySpec,
    /** Declarative rules between fields, checked after each field is valid on its own. */
    val rules: List<CrossRule> = emptyList(),
    /** Kotlin extension point for the rules that do not describe themselves. */
    val checks: List<SchemaCheck> = emptyList(),
    val tagging: Tagging = Tagging.PPF_PREFIX,
    /**
     * The approvals of editors who wrote no version that a content of this type needs before the platform
     * signs it: [EditorialApprovals.ORDINARY] (one, the default) or [EditorialApprovals.M17] (two, for the
     * types of M17 that implicate a person). The event itself may ask for two ([EditorialApprovals.required]).
     */
    val approvals: Int = EditorialApprovals.ORDINARY,
) {
    init {
        require(approvals in EditorialApprovals.ORDINARY..EditorialApprovals.M17) { "${type.tagValue}: approvals must be 1 or 2" }
        val keys = fields.map { it.key }
        require(keys.size == keys.toSet().size) { "${type.tagValue}: a field key is declared twice" }
        require(keys.none { it in COMMON_KEYS }) { "${type.tagValue}: a specific field uses a common key" }
    }

    fun field(key: String): FieldSpec? = fields.firstOrNull { it.key == key }

    companion object {
        /** The fields every article has, handled by the engine in the order of Article.build. */
        const val SLUG: String = "slug"
        const val TITLE: String = "title"
        const val SUMMARY: String = "summary"
        const val TOPICS: String = "topics"
        const val TYPE: String = "type"
        const val ORIGIN: String = "origin"
        const val DURATION: String = "duration"
        const val PUBLISHED_AT: String = "published_at"
        const val UPDATED_AT: String = "updated_at"

        val COMMON_KEYS: List<String> = listOf(SLUG, TITLE, SUMMARY, TOPICS, TYPE, ORIGIN, DURATION, PUBLISHED_AT, UPDATED_AT)
    }
}

/** A field of the front matter (one key) and of the form (one fieldset). */
data class FieldSpec(
    /** Front matter key, also the form name: "fact-status". */
    val key: String,
    val labelKey: String,
    val helpKey: String? = null,
    /** How many times the key may appear. */
    val occurs: Occurs = Occurs.ONE,
    val value: ValueSpec,
    /** Tag name when it is not "ppf-<key>"; links always use "a". */
    val tag: String? = null,
    /** Values that write no tag (the default of the field, such as `auto: oui` in the glossary). */
    val silentValues: Set<String> = emptySet(),
) {
    val tagName: String get() = tag ?: "ppf-$key"
}

/** Repetition of a key: ONE (exactly one), OPTIONAL (zero or one), MANY(min, max) (repeated key, one tag each). */
sealed interface Occurs {
    data object ONE : Occurs
    data object OPTIONAL : Occurs
    data class MANY(val min: Int = 0, val max: Int = 200) : Occurs {
        init {
            require(min in 0..max) { "MANY needs 0 <= min <= max" }
        }
    }
}

/** What one occurrence of a field holds. An empty occurrence (`persons:`) counts as absent. */
sealed interface ValueSpec {
    /** A single value. */
    data class Single(val type: FieldType) : ValueSpec

    /**
     * Several values separated by « | » (the convention of content/ since D-034), one type per position.
     * An empty position (« a || c », or a trailing « | ») means « unknown » and is allowed only when the
     * position is optional; positions after the last given one may be left out when they are optional.
     * [keepEmpty]: the tag keeps the empty positions (Voir) or drops them (the `period` of a story).
     */
    data class Positional(val positions: List<Position>, val keepEmpty: Boolean = true) : ValueSpec {
        init {
            require(positions.isNotEmpty()) { "a positional value needs at least one position" }
            require(positions.map { it.name }.toSet().size == positions.size) { "two positions have the same name" }
        }

        fun position(name: String): Int = positions.indexOfFirst { it.name == name }
    }

    /**
     * A link to another content by its slug: becomes `["a", "30023:<key>:<slug>", "", role]`. The role is free
     * (`personne`, `responsable`…). With [kinds], the value is written « <kind>:<slug> » and the kind gives
     * the expected target (the `related` links of the glossary: « parcours:le-referendum »).
     */
    data class Link(val role: String, val target: TargetType, val kinds: Map<String, TargetType> = emptyMap()) : ValueSpec

    /** « label | fiche:<slug> », « label | voir:<slug> » or « label | espace:<id> » (ppf-next). */
    data object NextStep : ValueSpec

    /**
     * Several items on one line separated by [separator] (« persons: a, b » ; « claims: x ; y »), each item
     * being a [Single] or a [Link]. A separator inside an item is written with a backslash when [escapable]
     * (« Conseil économique\, social et environnemental »). [joiner] writes the line back (draftOf).
     */
    data class InlineList(
        val separator: Char,
        val item: ValueSpec,
        val each: Tagging.Each = Tagging.Each.TAG_PER_ITEM,
        val escapable: Boolean = false,
        val joiner: String = "$separator ",
    ) : ValueSpec {
        init {
            require(item is Single || item is Link) { "an inline list holds single values or links" }
            require(each == Tagging.Each.TAG_PER_ITEM || item is Single) { "links are always one tag per item" }
        }
    }

    /**
     * An official identifier matching [pattern] (anchored regular expression), one repeatable tag
     * `ppf-official-id` by default: `an:PA1234`, `senat:12345A`, `an-dossier:DLR5L17N12345`.
     */
    data class OfficialId(val pattern: String) : ValueSpec {
        val regex: Regex get() = Regex(pattern)
    }
}

data class Position(val name: String, val labelKey: String, val type: FieldType, val required: Boolean = true)

/** The type of one value; each type has its parser, its check and its form control. */
sealed interface FieldType {
    data class Text(val maxLength: Int = 300, val multiline: Boolean = false) : FieldType

    /** A closed list of values (the tag values of an existing enum: FactKind, Verdict…). */
    data class Choice(val values: List<String>, val labelKeyPrefix: String) : FieldType

    /** AAAA-MM-JJ; [yearOnly]: a year (AAAA) or a month (AAAA-MM) is accepted too, as the period of a story. */
    data class Date(val yearOnly: Boolean = false) : FieldType

    /**
     * A number written the French way: groups of three digits separated by spaces (« 4 210 345,60 ») or
     * not, decimal comma when [decimals]; negative only when [allowNegative]. Read by [FrenchNumber.parse].
     */
    data class Number(
        val decimals: Boolean = false,
        val allowNegative: Boolean = false,
        val min: Double? = null,
        val max: Double? = null,
    ) : FieldType

    /** An https address (never followed by the server; shown as text in the back-office). */
    data object Url : FieldType

    /** A slug (`a-z`, `0-9`, `-`); with [target], the slug of an existing content of that type. */
    data class Slug(val target: TargetType? = null) : FieldType

    /** A reading time in minutes, 1 to 240. */
    data object Minutes : FieldType

    /** A SHA-256 fingerprint: 64 lowercase hexadecimal characters. */
    data object Hash : FieldType
}

/**
 * The expected type of a linked content. [prefix] is the stable name written in the schemas and in the
 * messages; [types] the ArticleType the target must have, checked against the published contents and the
 * other drafts of the same batch. An empty set accepts any type of the space (the slug must still exist).
 */
enum class TargetType(val prefix: String, val types: Set<ArticleType>) {
    PERSON("person", setOf(ArticleType.PERSONNE)),
    PARTY("party", setOf(ArticleType.PARTI)),
    TEXT("text", setOf(ArticleType.TEXTE)),
    INDICATOR("indicator", setOf(ArticleType.INDICATEUR)),
    FACT("fact", setOf(ArticleType.FAIT)),
    GUIDE("fiche", setOf(ArticleType.FICHE_PRATIQUE, ArticleType.DOSSIER, ArticleType.DECRYPTAGE, ArticleType.ARTICLE)),
    COURSE("parcours", setOf(ArticleType.PARCOURS)),
    FACT_CHECK("decryptage", setOf(ArticleType.DECRYPTAGE)),
    QUI_DECIDE("qui-decide", setOf(ArticleType.QUI_DECIDE)),
    STORY("histoire", setOf(ArticleType.HISTOIRE)),
    GLOSSARY("glossaire", setOf(ArticleType.GLOSSAIRE)),

    /** Any building block of Voir. */
    VOIR_BLOCK(
        "voir",
        setOf(
            ArticleType.INDICATEUR, ArticleType.FAIT, ArticleType.PERSONNE, ArticleType.TEXTE, ArticleType.MOUVEMENT,
            ArticleType.REPONSE, ArticleType.DOSSIER, ArticleType.PARTI,
        ),
    ),
    ;

    fun accepts(type: ArticleType): Boolean = types.isEmpty() || type in types
}

/** How a field becomes tags. */
enum class Tagging {
    /** `["ppf-<key>", value…]` (Voir, D-034), or `[tag, value…]` when FieldSpec.tag is given. */
    PPF_PREFIX;

    /** For an InlineList: one tag per item, or one tag holding all the items. */
    enum class Each { TAG_PER_ITEM, ONE_TAG }
}

/** The body: Markdown with fenced blocks « ::: name … ::: » and required « ## » sections. */
data class BodySpec(
    val blocks: List<BlockSpec> = emptyList(),
    /** « ## Sources » for every type of Comprendre. */
    val requiredSections: List<String> = listOf("Sources"),
    /** Text proposed in an empty draft (blocks already written). */
    val templateKey: String? = null,
    val maxLength: Int = 60_000,
)

/** A fenced block of the body, counted and read by its existing parser. */
data class BlockSpec(
    /** "quiz", "decideurs", "objections", "chronologie", "en-bref", "et-maintenant". */
    val name: String,
    val min: Int = 0,
    val max: Int = Int.MAX_VALUE,
    val parser: BlockParser = BlockParser.NONE,
)

/** The problems of the inner lines of one block (the parsers already in core, wrapped). */
fun interface BlockParser {
    fun problems(lines: List<String>): List<String>

    companion object {
        val NONE: BlockParser = BlockParser { emptyList() }
    }
}

/**
 * Declarative rules between fields, checked after each field is valid on its own. A condition holds when
 * the field (or its position [CrossRule.RequiredWhen.whenPosition]) has one of the values; when the
 * condition is a position of the same positional field, it is read occurrence by occurrence.
 */
sealed interface CrossRule {
    val messageKey: String

    /**
     * [field] is required when [whenField] has one of [values]; with [position], that position of [field]
     * must be filled (in the same occurrence when the condition is on [field] itself). Ex.: a « affaire »
     * fact needs its procedure.
     */
    data class RequiredWhen(
        val field: String,
        val whenField: String,
        val values: Set<String>,
        override val messageKey: String,
        val position: String? = null,
        val whenPosition: String? = null,
    ) : CrossRule

    /** [field] (or its [position]) is forbidden when [whenField] (or its [whenPosition]) has one of [values]. */
    data class ForbiddenWhen(
        val field: String,
        val whenField: String,
        val values: Set<String>,
        override val messageKey: String,
        val position: String? = null,
        val whenPosition: String? = null,
    ) : CrossRule

    /** Position [position] of [field] must be empty when its position [whenPosition] has one of [values]. */
    data class PositionEmptyWhen(
        val field: String,
        val position: String,
        val whenPosition: String,
        val values: Set<String>,
        override val messageKey: String,
    ) : CrossRule

    /** No two occurrences of [field] with the same value at [position] (one year of public aid once). */
    data class UniquePosition(val field: String, val position: String, override val messageKey: String) : CrossRule

    /** No two occurrences of [field] with the same values at all of [positions] (empty: the whole value). */
    data class UniqueBy(val field: String, val positions: List<String>, override val messageKey: String) : CrossRule

    /** At least one of [fields] is present. */
    data class AnyOf(val fields: List<String>, override val messageKey: String) : CrossRule

    /** The body has the block [block] when [whenField] has one of [values]. */
    data class BlockRequiredWhen(val block: String, val whenField: String, val values: Set<String>, override val messageKey: String) : CrossRule
}

/**
 * Extension point for a rule that does not describe itself: receives the draft, the event it gives and the
 * context, returns problems. Pure function, common code.
 */
fun interface SchemaCheck {
    fun problems(draft: ContentDraft, event: UnsignedEvent, context: CheckContext): List<Problem>
}

/** What a check may know: the author of the space, and the type of every known address. */
interface CheckContext {
    val authorHex: String
    fun typeOf(address: ContentAddress): ArticleType?

    companion object {
        /** A context over the slugs of [types] (contents published and drafts of the same batch). */
        fun of(authorHex: String, types: Map<String, ArticleType>): CheckContext = object : CheckContext {
            override val authorHex: String = authorHex
            override fun typeOf(address: ContentAddress): ArticleType? =
                if (address.pubkey == authorHex && address.kind == Article.KIND) types[address.identifier] else null
        }
    }
}

/** A draft as the engine reads it: the ordered entries of the front matter (repeated keys kept) and the body. */
data class ContentDraft(val type: ArticleType, val entries: List<Pair<String, String>>, val body: String) {
    fun values(key: String): List<String> = entries.filter { it.first == key }.map { it.second }
    fun first(key: String): String? = entries.firstOrNull { it.first == key }?.second
}

/**
 * A problem, located for the form: field key, occurrence (0-based), position name. [code] is a translation
 * key ([EditorialMessages] gives the French text), [args] its parameters.
 */
data class Problem(
    val code: String,
    val field: String? = null,
    val occurrence: Int? = null,
    val position: String? = null,
    val args: List<String> = emptyList(),
)
