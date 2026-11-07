package org.politisationpopulairefrancaise.core.editorial.schema

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.time.CivilDate

/**
 * The one engine of the editorial circuit (D-092): checks a draft against its [ContentSchema], builds the
 * unsigned event exactly as tools/content-signer always did, and reads a published event back as a draft.
 * The tags follow the order of the entries of the draft (the order of the file, or of the form); the
 * common tags come first, in the order of [Article.build].
 */
object EditorialEngine {
    /** Every problem of the draft: the common fields, each field alone, the cross rules, the body, then the checks. */
    fun check(schema: ContentSchema, draft: ContentDraft, context: CheckContext): List<Problem> {
        val problems = mutableListOf<Problem>()
        if (draft.type != schema.type) problems += Problem(Codes.TYPE_MISMATCH, ContentSchema.TYPE, args = listOf(draft.type.tagValue, schema.type.tagValue))
        problems += commonProblems(schema, draft)
        val known = schema.fields.map { it.key }.toSet() + ContentSchema.COMMON_KEYS
        draft.entries.map { it.first }.distinct().filter { it !in known }.forEach { problems += Problem(Codes.FIELD_UNKNOWN, it) }
        for (field in schema.fields) problems += fieldProblems(field, draft, context)
        for (rule in schema.rules) problems += ruleProblems(schema, rule, draft)
        problems += bodyProblems(schema.body, draft.body)
        if (problems.isEmpty() && schema.checks.isNotEmpty()) {
            val event = runCatching { build(schema, draft, PublicKey.fromHex(context.authorHex)) }.getOrNull()
            if (event == null) problems += Problem(Codes.UNBUILDABLE) else schema.checks.forEach { problems += it.problems(draft, event, context) }
        }
        return problems
    }

    /**
     * The unsigned event of a draft that passed [check] (throws IllegalArgumentException on a missing common
     * field). [createdAt] defaults to `updated_at` at noon UTC, like the dates of the tool.
     */
    fun build(schema: ContentSchema, draft: ContentDraft, author: PublicKey, createdAt: Long? = null): UnsignedEvent {
        val common = draft.entries.toMap() // the last value of a common key, as the tool always read it
        fun require(key: String): String = requireNotNull(common[key]) { "missing front matter field: $key" }
        val extra = draft.entries.mapNotNull { (key, value) -> schema.field(key)?.let { it to value } }
            .flatMap { (field, value) -> tagsOf(field, value, author.hex) }
        return Article.build(
            author = author,
            slug = require(ContentSchema.SLUG),
            title = require(ContentSchema.TITLE),
            summary = if (schema.summary == Presence.ABSENT) null else common[ContentSchema.SUMMARY],
            topics = if (schema.topics == Presence.ABSENT) emptyList() else topicsOf(common[ContentSchema.TOPICS]),
            type = schema.type,
            origin = requireNotNull(ContentOrigin.fromTag(require(ContentSchema.ORIGIN))) { "unknown origin" },
            publishedAt = requireNotNull(EditorialDates.noonUtc(require(ContentSchema.PUBLISHED_AT))) { "invalid published_at" },
            updatedAt = createdAt ?: requireNotNull(EditorialDates.noonUtc(require(ContentSchema.UPDATED_AT))) { "invalid updated_at" },
            body = draft.body,
            durationMinutes = if (schema.duration == Presence.ABSENT) {
                null
            } else {
                common[ContentSchema.DURATION]?.takeIf { it.isNotBlank() }?.let { requireNotNull(it.trim().toIntOrNull()) { "duration must be a number of minutes" } }
            },
            extraTags = extra,
        )
    }

    /**
     * A published event read back as a draft (for an update): the common entries in the order of
     * [ContentSchema.COMMON_KEYS], then one entry per specific tag in the order of the tags (the items of an
     * inline list joined on one line). [context] tells « fiche: » from « voir: » and the kind of a prefixed
     * link; without it, « voir: » and the bare slug are written. `build(draftOf(build(d))) == build(d)`.
     */
    fun draftOf(schema: ContentSchema, event: SignedEvent, context: CheckContext? = null): ContentDraft {
        val entries = mutableListOf<Pair<String, String>>()
        event.firstTagValue("d")?.let { entries += ContentSchema.SLUG to it }
        event.firstTagValue(Article.TAG_TITLE)?.let { entries += ContentSchema.TITLE to it }
        event.firstTagValue(Article.TAG_SUMMARY)?.let { entries += ContentSchema.SUMMARY to it }
        event.tagValues(Article.TAG_TOPIC).takeIf { it.isNotEmpty() }?.let { entries += ContentSchema.TOPICS to it.joinToString(", ") }
        entries += ContentSchema.TYPE to schema.type.tagValue
        event.firstTagValue(Article.TAG_ORIGIN)?.let { entries += ContentSchema.ORIGIN to it }
        event.firstTagValue(Article.TAG_DURATION)?.let { entries += ContentSchema.DURATION to it }
        event.firstTagValue(Article.TAG_PUBLISHED_AT)?.toLongOrNull()?.let { entries += ContentSchema.PUBLISHED_AT to CivilDate.fromEpochSeconds(it).toIso() }
        entries += ContentSchema.UPDATED_AT to CivilDate.fromEpochSeconds(event.createdAt).toIso()

        val commonTags = setOf("d", Article.TAG_TITLE, Article.TAG_SUMMARY, Article.TAG_PUBLISHED_AT, Article.TAG_TYPE, Article.TAG_ORIGIN, Article.TAG_DURATION, Article.TAG_TOPIC)
        val lists = LinkedHashMap<String, MutableList<String>>() // inline lists: one entry, items gathered
        val listSlots = HashMap<String, Int>()
        for (tag in event.tags) {
            if (tag.isEmpty() || tag[0] in commonTags) continue
            val field = fieldOfTag(schema, tag) ?: continue
            val value = field.value
            if (value is ValueSpec.InlineList) {
                val item = when (value.item) {
                    is ValueSpec.Link -> linkText(value.item, tag[1], context)
                    else -> tag.getOrNull(1).orEmpty()
                }
                val items = if (value.each == Tagging.Each.ONE_TAG && value.item !is ValueSpec.Link) tag.drop(1) else listOf(item)
                val escaped = items.map { if (value.escapable) it.replace(value.separator.toString(), "\\${value.separator}") else it }
                if (field.key !in listSlots) {
                    listSlots[field.key] = entries.size
                    entries += field.key to ""
                }
                lists.getOrPut(field.key) { mutableListOf() } += escaped
                continue
            }
            entries += field.key to when (value) {
                is ValueSpec.Link -> linkText(value, tag[1], context)
                is ValueSpec.NextStep -> "${tag.getOrNull(1).orEmpty()} | ${nextText(tag.getOrNull(2).orEmpty(), context)}"
                is ValueSpec.Positional -> tag.drop(1).joinToString(" | ").trim()
                else -> tag.getOrNull(1).orEmpty()
            }
        }
        lists.forEach { (key, items) ->
            val spec = schema.field(key)!!.value as ValueSpec.InlineList
            entries[listSlots.getValue(key)] = key to items.joinToString(spec.joiner)
        }
        return ContentDraft(schema.type, entries, event.content)
    }

    // ---------------------------------------------------------------------------------------------------
    // Values

    /** Splits an inline list: trimmed, empty items dropped, « \<separator> » kept inside an item when escapable. */
    fun items(list: ValueSpec.InlineList, value: String): List<String> {
        if (!list.escapable) return value.split(list.separator).map { it.trim() }.filter { it.isNotEmpty() }
        val items = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < value.length) {
            val char = value[i]
            when {
                char == '\\' && i + 1 < value.length && value[i + 1] == list.separator -> {
                    current.append(list.separator)
                    i++
                }
                char == list.separator -> {
                    items += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            i++
        }
        items += current.toString()
        return items.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** The positions of a positional value, trimmed; empty strings for « unknown ». */
    fun positions(value: String): List<String> = value.split('|').map { it.trim() }

    private fun topicsOf(value: String?): List<String> = value.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }

    private fun address(authorHex: String, slug: String) = "${Article.KIND}:$authorHex:${slug.trim()}"

    private fun tagsOf(field: FieldSpec, raw: String, authorHex: String): List<List<String>> {
        val value = raw.trim()
        if (value.isEmpty() || value in field.silentValues) return emptyList()
        return when (val spec = field.value) {
            is ValueSpec.Single, is ValueSpec.OfficialId -> listOf(listOf(field.tagName, value))
            is ValueSpec.Positional -> {
                val parts = positions(value)
                listOf(listOf(field.tagName) + if (spec.keepEmpty) parts else parts.filter { it.isNotEmpty() })
            }
            is ValueSpec.Link -> listOf(linkTag(spec, value, authorHex))
            is ValueSpec.NextStep -> {
                val parts = positions(value)
                require(parts.size == 2) { "next is written \"label | target\"" }
                listOf(listOf(field.tagName, parts[0], requireNotNull(nextTarget(parts[1], authorHex)) { "unknown next target ${parts[1]}" }))
            }
            is ValueSpec.InlineList -> {
                val items = items(spec, value)
                when (val item = spec.item) {
                    is ValueSpec.Link -> items.map { linkTag(item, it, authorHex) }
                    else -> if (spec.each == Tagging.Each.ONE_TAG) listOf(listOf(field.tagName) + items) else items.map { listOf(field.tagName, it) }
                }
            }
        }
    }

    private fun linkSlug(spec: ValueSpec.Link, value: String): String = if (spec.kinds.isEmpty() || ':' !in value) value.trim() else value.substringAfter(':').trim()

    private fun linkTag(spec: ValueSpec.Link, value: String, authorHex: String): List<String> = listOf("a", address(authorHex, linkSlug(spec, value)), "", spec.role)

    private fun nextTarget(target: String, authorHex: String): String? = when {
        target.startsWith(NEXT_GUIDE) -> address(authorHex, target.removePrefix(NEXT_GUIDE))
        target.startsWith(NEXT_VOIR) -> address(authorHex, target.removePrefix(NEXT_VOIR))
        target.startsWith(NEXT_SPACE) -> "space:" + target.removePrefix(NEXT_SPACE)
        else -> null
    }

    private const val NEXT_GUIDE = "fiche:"
    private const val NEXT_VOIR = "voir:"
    private const val NEXT_SPACE = "espace:"

    private fun fieldOfTag(schema: ContentSchema, tag: List<String>): FieldSpec? {
        if (tag[0] == "a") {
            val role = tag.getOrNull(3) ?: return null
            return schema.fields.firstOrNull { field ->
                val value = field.value
                (value is ValueSpec.Link && value.role == role) || (value is ValueSpec.InlineList && (value.item as? ValueSpec.Link)?.role == role)
            }
        }
        return schema.fields.firstOrNull { field ->
            val value = field.value
            field.tagName == tag[0] && value !is ValueSpec.Link && !(value is ValueSpec.InlineList && value.item is ValueSpec.Link)
        }
    }

    private fun linkText(spec: ValueSpec.Link, address: String, context: CheckContext?): String {
        val slug = address.substringAfterLast(':')
        if (spec.kinds.isEmpty()) return slug
        val type = context?.let { typeOfAddress(it, address) }
        val kind = type?.let { t -> spec.kinds.entries.firstOrNull { it.value.accepts(t) }?.key }
        return if (kind == null) slug else "$kind:$slug"
    }

    private fun nextText(resolved: String, context: CheckContext?): String {
        if (resolved.startsWith("space:")) return NEXT_SPACE + resolved.removePrefix("space:")
        val slug = resolved.substringAfterLast(':')
        val type = context?.let { typeOfAddress(it, resolved) }
        return if (type != null && TargetType.GUIDE.accepts(type)) NEXT_GUIDE + slug else NEXT_VOIR + slug
    }

    private fun typeOfAddress(context: CheckContext, address: String): ArticleType? =
        runCatching { ContentAddress.parse(address) }.getOrNull()?.let { context.typeOf(it) }

    // ---------------------------------------------------------------------------------------------------
    // Checks

    private fun commonProblems(schema: ContentSchema, draft: ContentDraft): List<Problem> {
        val problems = mutableListOf<Problem>()
        for (key in ContentSchema.COMMON_KEYS) {
            val values = draft.values(key)
            if (values.size > 1) problems += Problem(Codes.FIELD_REPEATED, key)
        }
        fun value(key: String) = draft.entries.lastOrNull { it.first == key }?.second?.trim()?.takeIf { it.isNotEmpty() }
        fun required(key: String, type: FieldType) {
            val v = value(key)
            if (v == null) problems += Problem(Codes.FIELD_MISSING, key) else problems += typeProblems(type, v, key, null, null, null)
        }
        fun presence(key: String, presence: Presence, type: FieldType) {
            val v = value(key)
            when {
                presence == Presence.ABSENT && draft.values(key).isNotEmpty() -> problems += Problem(Codes.FIELD_UNKNOWN, key)
                presence == Presence.REQUIRED && v == null -> problems += Problem(Codes.FIELD_MISSING, key)
                v != null && presence != Presence.ABSENT -> problems += typeProblems(type, v, key, null, null, null)
            }
        }
        required(ContentSchema.SLUG, FieldType.Slug())
        required(ContentSchema.TITLE, FieldType.Text(TITLE_MAX))
        presence(ContentSchema.SUMMARY, schema.summary, FieldType.Text(SUMMARY_MAX))
        presence(ContentSchema.TOPICS, schema.topics, FieldType.Text(TOPICS_MAX))
        value(ContentSchema.TYPE)?.let { if (it != schema.type.tagValue) problems += Problem(Codes.TYPE_MISMATCH, ContentSchema.TYPE, args = listOf(it, schema.type.tagValue)) }
        required(ContentSchema.ORIGIN, FieldType.Choice(ContentOrigin.entries.map { it.tagValue }, "editorial.origin."))
        presence(ContentSchema.DURATION, schema.duration, FieldType.Minutes)
        required(ContentSchema.PUBLISHED_AT, FieldType.Date())
        required(ContentSchema.UPDATED_AT, FieldType.Date())
        return problems
    }

    private fun fieldProblems(field: FieldSpec, draft: ContentDraft, context: CheckContext): List<Problem> {
        val problems = mutableListOf<Problem>()
        val occurrences = draft.values(field.key).withIndex().filter { it.value.isNotBlank() }
        val count = occurrences.size
        when (val occurs = field.occurs) {
            Occurs.ONE -> {
                if (count == 0) problems += Problem(Codes.FIELD_MISSING, field.key)
                if (count > 1) problems += Problem(Codes.FIELD_REPEATED, field.key)
            }
            Occurs.OPTIONAL -> if (count > 1) problems += Problem(Codes.FIELD_REPEATED, field.key)
            is Occurs.MANY -> {
                if (count < occurs.min) problems += Problem(Codes.FIELD_TOO_FEW, field.key, args = listOf(occurs.min.toString()))
                if (count > occurs.max) problems += Problem(Codes.FIELD_TOO_MANY, field.key, args = listOf(occurs.max.toString()))
            }
        }
        for ((index, raw) in occurrences) problems += valueProblems(field.value, raw.trim(), field.key, index, context)
        return problems
    }

    private fun valueProblems(spec: ValueSpec, value: String, key: String, occurrence: Int, context: CheckContext): List<Problem> = when (spec) {
        is ValueSpec.Single -> typeProblems(spec.type, value, key, occurrence, null, context)
        is ValueSpec.OfficialId ->
            if (spec.regex.matches(value)) emptyList() else listOf(Problem(Codes.VALUE_OFFICIAL_ID, key, occurrence, args = listOf(value)))
        is ValueSpec.Positional -> {
            val parts = positions(value)
            val problems = mutableListOf<Problem>()
            if (parts.size > spec.positions.size) problems += Problem(Codes.VALUE_POSITIONS, key, occurrence, args = listOf(spec.positions.size.toString(), parts.size.toString()))
            spec.positions.forEachIndexed { i, position ->
                val part = parts.getOrNull(i).orEmpty()
                if (part.isEmpty()) {
                    if (position.required) problems += Problem(Codes.VALUE_POSITION_REQUIRED, key, occurrence, position.name)
                } else {
                    problems += typeProblems(position.type, part, key, occurrence, position.name, context)
                }
            }
            problems
        }
        is ValueSpec.Link -> linkProblems(spec, value, key, occurrence, context)
        is ValueSpec.NextStep -> {
            val parts = positions(value)
            when {
                parts.size != 2 || parts[0].isEmpty() -> listOf(Problem(Codes.VALUE_NEXT, key, occurrence, args = listOf(value)))
                parts[1].startsWith(NEXT_SPACE) ->
                    if (parts[1].removePrefix(NEXT_SPACE).isBlank()) listOf(Problem(Codes.VALUE_NEXT_TARGET, key, occurrence, args = listOf(parts[1]))) else emptyList()
                parts[1].startsWith(NEXT_GUIDE) -> targetProblems(TargetType.GUIDE, parts[1].removePrefix(NEXT_GUIDE), key, occurrence, null, context)
                parts[1].startsWith(NEXT_VOIR) -> targetProblems(TargetType.VOIR_BLOCK, parts[1].removePrefix(NEXT_VOIR), key, occurrence, null, context)
                else -> listOf(Problem(Codes.VALUE_NEXT_TARGET, key, occurrence, args = listOf(parts[1])))
            }
        }
        is ValueSpec.InlineList -> items(spec, value).flatMap { item -> valueProblems(spec.item, item, key, occurrence, context) }
    }

    private fun linkProblems(spec: ValueSpec.Link, value: String, key: String, occurrence: Int, context: CheckContext): List<Problem> {
        if (spec.kinds.isEmpty()) return targetProblems(spec.target, value, key, occurrence, null, context)
        val kind = value.substringBefore(':', "")
        val target = spec.kinds[kind] ?: return listOf(Problem(Codes.LINK_KIND, key, occurrence, args = listOf(value, spec.kinds.keys.joinToString(", "))))
        return targetProblems(target, value.substringAfter(':'), key, occurrence, null, context)
    }

    private fun targetProblems(target: TargetType, slug: String, key: String, occurrence: Int?, position: String?, context: CheckContext?): List<Problem> {
        val trimmed = slug.trim()
        if (!SLUG.matches(trimmed)) return listOf(Problem(Codes.VALUE_SLUG, key, occurrence, position, listOf(trimmed)))
        if (context == null) return emptyList()
        val type = context.typeOf(ContentAddress(Article.KIND, context.authorHex, trimmed))
            ?: return listOf(Problem(Codes.LINK_UNKNOWN, key, occurrence, position, listOf(trimmed)))
        return if (target.accepts(type)) emptyList() else listOf(Problem(Codes.LINK_WRONG_TYPE, key, occurrence, position, listOf(trimmed, target.prefix, type.tagValue)))
    }

    /** The problems of one value of [type]; [context] null: links are not looked up. */
    fun typeProblems(type: FieldType, value: String, key: String?, occurrence: Int?, position: String?, context: CheckContext?): List<Problem> {
        fun problem(code: String, vararg args: String) = listOf(Problem(code, key, occurrence, position, args.toList()))
        return when (type) {
            is FieldType.Text -> when {
                value.isBlank() -> problem(Codes.VALUE_EMPTY)
                value.length > type.maxLength -> problem(Codes.VALUE_TOO_LONG, type.maxLength.toString(), value.length.toString())
                !type.multiline && ('\n' in value || '\r' in value) -> problem(Codes.VALUE_MULTILINE)
                else -> emptyList()
            }
            is FieldType.Choice -> if (value in type.values) emptyList() else problem(Codes.VALUE_CHOICE, value, type.values.joinToString(", "))
            is FieldType.Date -> when {
                CivilDate.parseIso(value) != null && value.length == 10 -> emptyList()
                type.yearOnly && EditorialDates.isYearOrMonth(value) -> emptyList()
                type.yearOnly -> problem(Codes.VALUE_DATE_OR_YEAR, value)
                else -> problem(Codes.VALUE_DATE, value)
            }
            is FieldType.Number -> {
                val number = FrenchNumber.parse(value, decimals = true, allowNegative = true)
                when {
                    number == null -> problem(Codes.VALUE_NUMBER, value)
                    !type.decimals && ',' in value -> problem(Codes.VALUE_NUMBER_DECIMALS, value)
                    !type.allowNegative && number < 0 -> problem(Codes.VALUE_NUMBER_NEGATIVE, value)
                    type.min != null && number < type.min -> problem(Codes.VALUE_NUMBER_MIN, value, FrenchNumber.format(type.min))
                    type.max != null && number > type.max -> problem(Codes.VALUE_NUMBER_MAX, value, FrenchNumber.format(type.max))
                    else -> emptyList()
                }
            }
            FieldType.Url -> if (isHttpsUrl(value)) emptyList() else problem(Codes.VALUE_URL, value)
            is FieldType.Slug -> if (type.target == null) {
                if (SLUG.matches(value)) emptyList() else problem(Codes.VALUE_SLUG, value)
            } else {
                targetProblems(type.target, value, key ?: "", occurrence, position, context)
            }
            FieldType.Minutes -> if (value.all { it.isDigit() } && value.toIntOrNull()?.let { it in 1..240 } == true) emptyList() else problem(Codes.VALUE_MINUTES, value)
            FieldType.Hash -> if (value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }) emptyList() else problem(Codes.VALUE_HASH, value)
        }
    }

    private fun isHttpsUrl(value: String): Boolean {
        if (!value.startsWith("https://") || value.length > URL_MAX || value.any { it.isWhitespace() || it.code < 0x20 }) return false
        val host = value.removePrefix("https://").substringBefore('/').substringBefore('?').substringBefore('#')
        return host.isNotEmpty() && '.' in host && host.none { it == '@' }
    }

    /** The values of one occurrence that a rule compares: its positions, its items, or the whole value. */
    private fun valuesOf(field: FieldSpec?, raw: String, position: String?): List<String> {
        val value = raw.trim()
        val spec = field?.value
        return when {
            position != null && spec is ValueSpec.Positional -> listOf(positions(value).getOrNull(spec.position(position)).orEmpty())
            spec is ValueSpec.InlineList -> items(spec, value)
            else -> listOf(value)
        }
    }

    private fun occurrencesOf(draft: ContentDraft, key: String) = draft.values(key).withIndex().filter { it.value.isNotBlank() }

    private fun ruleProblems(schema: ContentSchema, rule: CrossRule, draft: ContentDraft): List<Problem> {
        fun holds(whenField: String, whenPosition: String?, values: Set<String>): Boolean =
            occurrencesOf(draft, whenField).any { (_, raw) -> valuesOf(schema.field(whenField), raw, whenPosition).any { it in values } }

        fun filled(key: String, raw: String, position: String) = valuesOf(schema.field(key), raw, position).single().isNotEmpty()

        return when (rule) {
            is CrossRule.RequiredWhen -> {
                val sameField = rule.whenField == rule.field && rule.whenPosition != null && rule.position != null
                when {
                    sameField -> occurrencesOf(draft, rule.field)
                        .filter { (_, raw) -> valuesOf(schema.field(rule.field), raw, rule.whenPosition).any { it in rule.values } }
                        .filter { (_, raw) -> !filled(rule.field, raw, rule.position!!) }
                        .map { (i, _) -> Problem(rule.messageKey, rule.field, i, rule.position) }
                    !holds(rule.whenField, rule.whenPosition, rule.values) -> emptyList()
                    rule.position == null ->
                        if (occurrencesOf(draft, rule.field).isEmpty()) listOf(Problem(rule.messageKey, rule.field)) else emptyList()
                    else -> {
                        val occurrences = occurrencesOf(draft, rule.field)
                        if (occurrences.isEmpty()) {
                            listOf(Problem(rule.messageKey, rule.field, position = rule.position))
                        } else {
                            occurrences.filter { (_, raw) -> !filled(rule.field, raw, rule.position) }.map { (i, _) -> Problem(rule.messageKey, rule.field, i, rule.position) }
                        }
                    }
                }
            }
            is CrossRule.ForbiddenWhen -> forbidden(schema, draft, rule.field, rule.position, rule.whenField, rule.whenPosition, rule.values, rule.messageKey)
            is CrossRule.PositionEmptyWhen -> forbidden(schema, draft, rule.field, rule.position, rule.field, rule.whenPosition, rule.values, rule.messageKey)
            is CrossRule.UniquePosition -> unique(schema, draft, rule.field, listOf(rule.position), rule.messageKey)
            is CrossRule.UniqueBy -> unique(schema, draft, rule.field, rule.positions, rule.messageKey)
            is CrossRule.AnyOf ->
                if (rule.fields.any { occurrencesOf(draft, it).isNotEmpty() }) emptyList() else listOf(Problem(rule.messageKey, rule.fields.firstOrNull()))
            is CrossRule.BlockRequiredWhen ->
                if (holds(rule.whenField, null, rule.values) && EditorialBody.blocks(draft.body, rule.block).isEmpty()) {
                    listOf(Problem(rule.messageKey, BODY, args = listOf(rule.block)))
                } else {
                    emptyList()
                }
        }
    }

    private fun forbidden(
        schema: ContentSchema,
        draft: ContentDraft,
        field: String,
        position: String?,
        whenField: String,
        whenPosition: String?,
        values: Set<String>,
        messageKey: String,
    ): List<Problem> {
        val spec = schema.field(field)
        if (whenField == field && whenPosition != null && position != null) {
            return occurrencesOf(draft, field)
                .filter { (_, raw) -> valuesOf(spec, raw, whenPosition).any { it in values } && valuesOf(spec, raw, position).single().isNotEmpty() }
                .map { (i, _) -> Problem(messageKey, field, i, position) }
        }
        val holds = occurrencesOf(draft, whenField).any { (_, raw) -> valuesOf(schema.field(whenField), raw, whenPosition).any { it in values } }
        if (!holds) return emptyList()
        return occurrencesOf(draft, field)
            .filter { (_, raw) -> position == null || valuesOf(spec, raw, position).single().isNotEmpty() }
            .map { (i, _) -> Problem(messageKey, field, i, position) }
    }

    private fun unique(schema: ContentSchema, draft: ContentDraft, field: String, positions: List<String>, messageKey: String): List<Problem> {
        val spec = schema.field(field)
        val seen = HashSet<List<String>>()
        return occurrencesOf(draft, field).mapNotNull { (i, raw) ->
            val key = if (positions.isEmpty()) listOf(raw.trim()) else positions.map { valuesOf(spec, raw, it).single() }
            if (seen.add(key)) null else Problem(messageKey, field, i, positions.singleOrNull(), key)
        }
    }

    private fun bodyProblems(spec: BodySpec, body: String): List<Problem> {
        val problems = mutableListOf<Problem>()
        if (body.length > spec.maxLength) problems += Problem(Codes.BODY_TOO_LONG, BODY, args = listOf(spec.maxLength.toString(), body.length.toString()))
        val lines = body.lines()
        for (section in spec.requiredSections) {
            if (lines.none { it.trim() == "## $section" }) problems += Problem(Codes.BODY_SECTION_MISSING, BODY, args = listOf(section))
        }
        for (block in spec.blocks) {
            val found = EditorialBody.blocks(body, block.name)
            if (found.size < block.min) problems += Problem(Codes.BODY_BLOCK_TOO_FEW, BODY, args = listOf(block.name, block.min.toString()))
            if (found.size > block.max) problems += Problem(Codes.BODY_BLOCK_TOO_MANY, BODY, args = listOf(block.name, block.max.toString()))
            found.forEach { inner -> block.parser.problems(inner).forEach { problems += Problem(Codes.BODY_INVALID, BODY, args = listOf(block.name, it)) } }
        }
        return problems
    }

    /** The pseudo-field of the body in [Problem.field]. */
    const val BODY: String = "body"

    const val TITLE_MAX: Int = 200
    const val SUMMARY_MAX: Int = 500
    const val TOPICS_MAX: Int = 300
    const val URL_MAX: Int = 2000

    /** A slug: lowercase letters, digits and hyphens, as the `editorial_draft.slug` column. */
    val SLUG: Regex = Regex("^[a-z0-9][a-z0-9-]{0,99}$")
}

/** The fenced blocks of a body, read as ContentBlocks.extract does (`::: name` … `:::`, no nesting). */
object EditorialBody {
    private val BLOCK_START = Regex("^:::\\s*([a-z-]+)\\s*$")
    private val BLOCK_END = Regex("^:::\\s*$")

    /** The inner lines of every `::: [name]` block of [markdown], in order. */
    fun blocks(markdown: String, name: String): List<List<String>> {
        val lines = markdown.lines()
        val found = mutableListOf<List<String>>()
        var index = 0
        while (index < lines.size) {
            val start = BLOCK_START.matchEntire(lines[index].trimEnd())
            if (start != null && start.groupValues[1] == name) {
                val inner = mutableListOf<String>()
                index++
                while (index < lines.size && !BLOCK_END.matches(lines[index].trimEnd())) {
                    inner += lines[index]
                    index++
                }
                found += inner
            }
            index++
        }
        return found
    }
}

/** The dates of the front matter: `published_at` and `updated_at` at noon UTC, so that the id never depends on the machine. */
object EditorialDates {
    /** « 2026-11-07 » at noon UTC, in seconds since 1970; null for anything else. */
    fun noonUtc(date: String): Long? {
        val trimmed = date.trim()
        if (trimmed.length != 10) return null
        return CivilDate.parseIso(trimmed)?.let { it.toEpochDays() * 86_400L + 43_200L }
    }

    /** « 1936 » or « 1936-06 » (the period of a story), as ContentBlocks.frenchDate reads them. */
    fun isYearOrMonth(value: String): Boolean {
        val parts = value.split('-')
        if (parts[0].length != 4 || parts[0].toIntOrNull() == null || !parts[0].all { it.isDigit() }) return false
        return when (parts.size) {
            1 -> true
            2 -> parts[1].length == 2 && parts[1].all { it.isDigit() } && parts[1].toInt() in 1..12
            else -> false
        }
    }
}

/** Numbers written the French way: « 4 210 345,60 », « 120 », « -3,5 ». */
object FrenchNumber {
    private val PATTERN = Regex("^(-)?(\\d{1,3}(?:[   ]\\d{3})+|\\d+)(?:,(\\d+))?$")

    /** The value, or null when the text is not a number written this way (or has decimals or a sign not allowed). */
    fun parse(text: String, decimals: Boolean = true, allowNegative: Boolean = false): Double? {
        val match = PATTERN.matchEntire(text.trim()) ?: return null
        val negative = match.groupValues[1].isNotEmpty()
        val fraction = match.groupValues[3]
        if (negative && !allowNegative) return null
        if (fraction.isNotEmpty() && !decimals) return null
        val digits = match.groupValues[2].filter { it.isDigit() }
        val number = (digits + if (fraction.isEmpty()) "" else ".$fraction").toDoubleOrNull() ?: return null
        return if (negative) -number else number
    }

    /** « 1988 », « 0,5 »: for the messages. */
    fun format(value: Double): String {
        val long = value.toLong()
        return if (long.toDouble() == value) long.toString() else value.toString().replace('.', ',')
    }
}
