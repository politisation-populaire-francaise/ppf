package org.politisationpopulairefrancaise.server.editorial

import io.ktor.http.Parameters
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.dataList
import kotlinx.html.div
import kotlinx.html.fieldSet
import kotlinx.html.FormMethod
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.legend
import kotlinx.html.li
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.textArea
import kotlinx.html.ul
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.editorial.schema.ContentSchema
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialEngine
import org.politisationpopulairefrancaise.core.editorial.schema.FieldSpec
import org.politisationpopulairefrancaise.core.editorial.schema.FieldType
import org.politisationpopulairefrancaise.core.editorial.schema.Occurs
import org.politisationpopulairefrancaise.core.editorial.schema.Position
import org.politisationpopulairefrancaise.core.editorial.schema.Presence
import org.politisationpopulairefrancaise.core.editorial.schema.Problem
import org.politisationpopulairefrancaise.core.editorial.schema.TargetType
import org.politisationpopulairefrancaise.core.editorial.schema.ValueSpec

/** The form of a draft as shown: its content, and how many blank rows each repeated field adds. */
data class FormState(val content: DraftContent, val blankRows: Map<String, Int> = emptyMap())

/** What a posted form asks. */
sealed interface FormAction {
    data object Save : FormAction

    data object Preview : FormAction

    /** Two more blank rows for the repeated field [key]. */
    data class More(val key: String) : FormAction

    /** Moves row [row] of [key] up or down. */
    data class Move(val key: String, val row: Int, val up: Boolean) : FormAction
}

/** A posted form, read: the state, the action, the version it started from (null: a new draft). */
/**
 * A posted form, read: the state, the action, the version it started from (null: a new draft), and
 * [tooLong], the French message when a text is over its limit in characters (nothing is then saved, the
 * text stays in the form).
 */
data class PostedForm(val state: FormState, val action: FormAction, val version: Int?, val tooLong: String? = null)

/**
 * The form of a draft generated from its [ContentSchema] (docs/integration/circuit-editorial.md, section
 * 2), in kotlinx.html, without JavaScript:
 * - names `c.<key>` (common fields), `origin`, `f.<key>.<row>` or `f.<key>.<row>.<position>`, `body`;
 * - repeated fields: the rows already written plus two blank rows; « Ajouter des lignes »
 *   (`action=more:<key>`) sends the form back with two more, losing nothing; a blank row is ignored;
 *   « Monter » and « Descendre » (`move:<key>:<row>:up|down`);
 * - the server rebuilds the entries in the order of the schema, each row written as a line of content/
 *   (`value | value | …`, the trailing empty positions dropped);
 * - every control has its label; each problem of [EditorialEngine.check] is shown next to its control
 *   (`aria-invalid`, `aria-describedby`) and listed at the top with a link to the control.
 */
object EditorialForm {
    /** Blank rows shown under a repeated field. */
    const val BLANK_ROWS: Int = 2

    /** No field shows more rows than this, whatever its schema says. */
    const val MAX_ROWS: Int = 200

    /** No single value of the front matter is saved longer than this (the controls say less; the engine checks the real limits). */
    const val MAX_VALUE: Int = 5_000

    /**
     * All the texts of one form together, in characters (body included). Checked on the server before any
     * save, so that editors meet a clear message in characters long before the limit of the section in
     * bytes (256 KiB of URL-encoded body, where an accented letter takes up to 6 bytes and a typographic
     * apostrophe 9).
     */
    const val MAX_TOTAL: Int = 75_000

    private const val CHOICE_RADIOS_BELOW = 8

    /** The common fields entered as a date (`AAAA-MM-JJ`). */
    private val DATE_KEYS = setOf(ContentSchema.PUBLISHED_AT, ContentSchema.UPDATED_AT, ContentSchema.REVIEW_BY)

    // Reading ---------------------------------------------------------------------------------------

    /** The common fields of [schema] shown in the form, in the order of the entries. */
    fun commonKeys(schema: ContentSchema): List<String> = buildList {
        add(ContentSchema.SLUG)
        add(ContentSchema.TITLE)
        if (schema.summary != Presence.ABSENT) add(ContentSchema.SUMMARY)
        if (schema.topics != Presence.ABSENT) add(ContentSchema.TOPICS)
        if (schema.duration != Presence.ABSENT) add(ContentSchema.DURATION)
        add(ContentSchema.PUBLISHED_AT)
        add(ContentSchema.UPDATED_AT)
        if (schema.reviewBy != Presence.ABSENT) add(ContentSchema.REVIEW_BY)
    }

    /** An empty draft of [schema]: today's dates, the body template, no origin (never a default). */
    fun empty(schema: ContentSchema, today: String): FormState {
        val entries = listOf(ContentSchema.TYPE to schema.type.tagValue, ContentSchema.PUBLISHED_AT to today, ContentSchema.UPDATED_AT to today)
        return FormState(DraftContent(ordered(schema, entries), template(schema)))
    }

    /** The text proposed in an empty draft: the template of the schema, or its required sections. */
    fun template(schema: ContentSchema): String {
        val text = schema.body.templateKey?.let { EditorialTexts.label(it, "") }.orEmpty()
        if (text.isNotBlank()) return text.trim() + "\n"
        return schema.body.requiredSections.joinToString("\n\n") { "## $it\n\n- " }.let { if (it.isEmpty()) "" else it.trim() + "\n" }
    }

    /**
     * The entries of a draft in the order of the form: the common keys in the order of
     * [ContentSchema.COMMON_KEYS], then each field of the schema with its occurrences in order. Entries of
     * unknown keys are kept at the end (the engine reports them).
     */
    fun ordered(schema: ContentSchema, entries: List<Pair<String, String>>): List<Pair<String, String>> {
        val keys = ContentSchema.COMMON_KEYS + schema.fields.map { it.key }
        val known = keys.flatMap { key -> entries.filter { it.first == key } }
        return known + entries.filter { it.first !in keys }
    }

    /** Reads a posted form of [schema]. */
    fun read(schema: ContentSchema, form: Parameters, isUpdate: Boolean): PostedForm {
        val action = actionOf(form["action"])
        val entries = mutableListOf<Pair<String, String>>()
        for (key in ContentSchema.COMMON_KEYS) {
            when (key) {
                ContentSchema.TYPE -> entries += key to schema.type.tagValue
                ContentSchema.ORIGIN -> form["origin"]?.trim()?.let { ContentOrigin.fromTag(it) }?.let { entries += key to it.tagValue }
                in commonKeys(schema) -> form["c.$key"]?.let(::clean)?.takeIf { it.isNotEmpty() }?.let { entries += key to it }
            }
        }
        val blanks = HashMap<String, Int>()
        for (field in schema.fields) {
            val shown = form["rows.${field.key}"]?.trim()?.toIntOrNull()?.coerceIn(0, MAX_ROWS) ?: 1
            val rows = (0 until shown).map { row -> rowValue(field, form, row) }.toMutableList()
            if (action is FormAction.Move && action.key == field.key) {
                val other = if (action.up) action.row - 1 else action.row + 1
                if (action.row in rows.indices && other in rows.indices) rows[action.row] = rows[other].also { rows[other] = rows[action.row] }
            }
            rows.filter { it.isNotBlank() }.forEach { entries += field.key to it }
            if (field.occurs is Occurs.MANY) {
                val previous = form["blank.${field.key}"]?.trim()?.toIntOrNull()?.coerceIn(0, MAX_ROWS) ?: BLANK_ROWS
                blanks[field.key] = if (action is FormAction.More && action.key == field.key) previous + BLANK_ROWS else BLANK_ROWS
            }
        }
        val body = form["body"].orEmpty().replace("\r\n", "\n").let { if (it.isBlank()) "" else it.trim() + "\n" }
        val changeKind = if (isUpdate) ChangeKind.of(form["change_kind"]?.trim()) else null
        val note = if (isUpdate) form["change_note"]?.let(::clean)?.takeIf { it.isNotEmpty() } else null
        val version = form["version"]?.trim()?.toIntOrNull()?.takeIf { it >= 1 }
        val content = DraftContent(entries, body, changeKind, note)
        return PostedForm(FormState(content, blanks), action, version, tooLong(schema, content))
    }

    /** The message of the first text over its limit in characters, or null (never a silent cut). */
    fun tooLong(schema: ContentSchema, content: DraftContent): String? {
        val note = content.changeNote.orEmpty()
        content.entries.firstOrNull { it.second.length > MAX_VALUE }?.let { (key, value) ->
            return EditorialTexts.format(EditorialTexts.FIELD_TOO_LONG, EditorialTexts.fieldLabel(schema, key) ?: key, value.length, MAX_VALUE)
        }
        if (note.length > EditorialDrafts.MAX_CHANGE_NOTE) {
            return EditorialTexts.format(EditorialTexts.FIELD_TOO_LONG, EditorialTexts.CHANGE_NOTE, note.length, EditorialDrafts.MAX_CHANGE_NOTE)
        }
        if (content.body.length > EditorialDrafts.MAX_BODY) {
            return EditorialTexts.format(EditorialTexts.BODY_TOO_LONG_CHARS, EditorialTexts.number(content.body.length), EditorialTexts.number(EditorialDrafts.MAX_BODY))
        }
        val total = content.body.length + content.entries.sumOf { it.second.length } + note.length
        if (total > MAX_TOTAL) return EditorialTexts.format(EditorialTexts.FORM_TOO_LONG, EditorialTexts.number(total), EditorialTexts.number(MAX_TOTAL))
        return null
    }

    private fun clean(value: String): String = value.replace("\r\n", "\n").trim()

    private fun actionOf(value: String?): FormAction {
        val text = value?.trim().orEmpty()
        return when {
            text == "preview" -> FormAction.Preview
            text.startsWith("more:") -> FormAction.More(text.removePrefix("more:"))
            text.startsWith("move:") -> {
                val parts = text.removePrefix("move:").split(':')
                val row = parts.getOrNull(1)?.toIntOrNull()
                if (parts.size == 3 && row != null && parts[2] in setOf("up", "down")) FormAction.Move(parts[0], row, parts[2] == "up") else FormAction.Preview
            }
            else -> FormAction.Save
        }
    }

    /** The line of row [row] of [field], as in a file of content/ (empty when the row is blank). */
    private fun rowValue(field: FieldSpec, form: Parameters, row: Int): String {
        fun part(name: String?) = form[name?.let { "f.${field.key}.$row.$it" } ?: "f.${field.key}.$row"]?.let(::clean).orEmpty()
        return when (val value = field.value) {
            is ValueSpec.Positional -> joinPositions(value.positions.map { part(it.name) })
            ValueSpec.NextStep -> joinPositions(listOf(part(NEXT_LABEL), part(NEXT_TARGET)))
            else -> part(null)
        }
    }

    private fun joinPositions(parts: List<String>): String {
        if (parts.all { it.isEmpty() }) return ""
        return parts.dropLastWhile { it.isEmpty() }.joinToString(" | ")
    }

    private const val NEXT_LABEL = "label"
    private const val NEXT_TARGET = "target"

    // Locating the problems -------------------------------------------------------------------------

    /** The id of the control a value of [field] is typed in (row [row], position [position]). */
    private fun controlId(field: FieldSpec, row: Int, position: String? = null): String {
        val base = "f-${field.key}-$row"
        val positionName = when (val value = field.value) {
            is ValueSpec.Positional -> position?.takeIf { value.position(it) >= 0 } ?: value.positions.first().name
            ValueSpec.NextStep -> position ?: NEXT_LABEL
            else -> null
        }
        val type = typeOf(field, positionName)
        val id = if (positionName == null) base else "$base-$positionName"
        return if (type != null && isRadios(type)) "$id-v0" else id
    }

    private fun typeOf(field: FieldSpec, position: String?): FieldType? = when (val value = field.value) {
        is ValueSpec.Single -> value.type
        is ValueSpec.Positional -> value.positions.firstOrNull { it.name == position }?.type
        else -> null
    }

    private fun isRadios(type: FieldType): Boolean = type is FieldType.Choice && type.values.size < CHOICE_RADIOS_BELOW

    /** Where a problem is shown: the id of its control, or null for the summary only. */
    fun anchorOf(schema: ContentSchema, problem: Problem): String? {
        val key = problem.field ?: return null
        return when {
            key == EditorialEngine.BODY -> "body"
            key == ContentSchema.ORIGIN -> "origin-${ContentOrigin.AI_ASSISTED.tagValue}"
            key == ContentSchema.TYPE -> null
            key == UpdateChecks.KIND_FIELD -> "change-${ChangeKind.CORRECTION.db}"
            key == UpdateChecks.NOTE_FIELD -> "change-note"
            key in ContentSchema.COMMON_KEYS -> if (key in commonKeys(schema)) "c-$key" else null
            else -> schema.field(key)?.let { controlId(it, problem.occurrence ?: 0, problem.position) }
        }
    }

    // Rendering -------------------------------------------------------------------------------------

    /** What the form needs besides the state. */
    class View(
        val schema: ContentSchema,
        val state: FormState,
        val problems: List<Problem>,
        /** The address the form posts to. */
        val action: String,
        /** The version the form starts from (null: a new draft). */
        val version: Int?,
        /** An update of a published content: the nature of the change is asked. */
        val isUpdate: Boolean,
        /** The known slugs of each type (published contents and open drafts), for the links. */
        val known: Map<String, ArticleType>,
        val csrf: (FlowContent) -> Unit,
    ) {
        /** The messages of each control, with their ids, and those of the summary only. */
        val located: Map<String, List<Pair<String, String>>>
        val unlocated: List<String>
        val summary: List<Triple<String, String?, String>>

        init {
            val byAnchor = LinkedHashMap<String, MutableList<Pair<String, String>>>()
            val loose = mutableListOf<String>()
            val all = mutableListOf<Triple<String, String?, String>>()
            problems.forEachIndexed { index, problem ->
                val text = EditorialTexts.problem(schema, problem)
                val anchor = anchorOf(schema, problem)
                val errorId = "err-$index"
                if (anchor == null) loose += text else byAnchor.getOrPut(anchor) { mutableListOf() } += errorId to text
                all += Triple(errorId, anchor, text)
            }
            located = byAnchor
            unlocated = loose
            summary = all
        }
    }

    /** The error summary, then the form. */
    fun render(flow: FlowContent, view: View) = with(flow) {
        if (view.problems.isNotEmpty()) {
            div(classes = "bo-notice bo-notice-error bo-error-summary") {
                id = "problemes"
                attributes["role"] = "alert"
                attributes["tabindex"] = "-1"
                h2 { +EditorialTexts.SUMMARY_TITLE }
                p { +EditorialTexts.format(EditorialTexts.SUMMARY_COUNT, view.problems.size) }
                ul {
                    view.summary.forEach { (_, anchor, text) ->
                        li { if (anchor != null) a(href = "#$anchor") { +text } else +text }
                    }
                }
            }
        }
        form(action = view.action, method = FormMethod.post, classes = "bo-editorial-form") {
            attributes["novalidate"] = "novalidate"
            view.csrf(this)
            view.version?.let { input(type = InputType.hidden, name = "version") { value = it.toString() } }
            // The first submit button answers the Enter key: saving, never a row button.
            div(classes = "bo-form-actions") { saveButtons() }
            commonFields(view)
            originField(view)
            view.schema.fields.forEach { field(view, it) }
            if (view.isUpdate) changeFields(view)
            bodyField(view)
            div(classes = "bo-form-actions") { saveButtons() }
            knownLists(view)
        }
    }

    private fun FlowContent.saveButtons() {
        button(type = ButtonType.submit, classes = "bo-button") {
            name = "action"
            value = "save"
            +EditorialTexts.SAVE
        }
        +" "
        button(type = ButtonType.submit, classes = "bo-button-secondary") {
            name = "action"
            value = "preview"
            +EditorialTexts.PREVIEW
        }
    }

    /** The messages of [anchor], each with its id, written next to its control. */
    private fun FlowContent.errors(view: View, anchor: String): List<String> {
        val messages = view.located[anchor].orEmpty()
        messages.forEach { (errorId, text) ->
            p(classes = "bo-error") {
                id = errorId
                +text
            }
        }
        return messages.map { it.first }
    }

    private fun kotlinx.html.INPUT.describe(help: String?, errors: List<String>) {
        val ids = listOfNotNull(help) + errors
        if (ids.isNotEmpty()) attributes["aria-describedby"] = ids.joinToString(" ")
        if (errors.isNotEmpty()) attributes["aria-invalid"] = "true"
    }

    private fun kotlinx.html.TEXTAREA.describe(help: String?, errors: List<String>) {
        val ids = listOfNotNull(help) + errors
        if (ids.isNotEmpty()) attributes["aria-describedby"] = ids.joinToString(" ")
        if (errors.isNotEmpty()) attributes["aria-invalid"] = "true"
    }

    private fun kotlinx.html.SELECT.describe(help: String?, errors: List<String>) {
        val ids = listOfNotNull(help) + errors
        if (ids.isNotEmpty()) attributes["aria-describedby"] = ids.joinToString(" ")
        if (errors.isNotEmpty()) attributes["aria-invalid"] = "true"
    }

    private fun FlowContent.hint(id: String, text: String) {
        p(classes = "bo-hint") {
            this.id = id
            +text
        }
    }

    private fun FlowContent.commonFields(view: View) {
        val content = view.state.content
        fieldSet(classes = "bo-fieldset") {
            legend { +EditorialTexts.COMMON_LEGEND }
            for (key in commonKeys(view.schema)) {
                val id = "c-$key"
                val value = content.entries.lastOrNull { it.first == key }?.second.orEmpty()
                val required = when (key) {
                    ContentSchema.SUMMARY -> view.schema.summary == Presence.REQUIRED
                    ContentSchema.TOPICS -> view.schema.topics == Presence.REQUIRED
                    ContentSchema.DURATION -> view.schema.duration == Presence.REQUIRED
                    ContentSchema.REVIEW_BY -> view.schema.reviewBy == Presence.REQUIRED
                    else -> true
                }
                div(classes = "bo-field") {
                    label {
                        htmlFor = id
                        +"${EditorialTexts.common(key)} (${if (required) EditorialTexts.REQUIRED else EditorialTexts.OPTIONAL})"
                    }
                    val help = EditorialTexts.commonHelp(key) ?: if (key == ContentSchema.PUBLISHED_AT || key == ContentSchema.UPDATED_AT) EditorialTexts.DATE_HINT else null
                    val helpId = help?.let { "$id-aide" }
                    if (help != null) hint(helpId!!, help)
                    val errors = errors(view, id)
                    when (key) {
                        ContentSchema.SUMMARY -> textArea(rows = "3") {
                            this.id = id
                            name = "c.$key"
                            maxLength = EditorialEngine.SUMMARY_MAX.toString()
                            describe(helpId, errors)
                            +value
                        }
                        else -> input(type = if (key in DATE_KEYS) InputType.date else InputType.text, name = "c.$key") {
                            this.id = id
                            this.value = value
                            when (key) {
                                ContentSchema.TITLE -> maxLength = EditorialEngine.TITLE_MAX.toString()
                                ContentSchema.TOPICS -> maxLength = EditorialEngine.TOPICS_MAX.toString()
                                ContentSchema.SLUG -> {
                                    maxLength = "100"
                                    attributes["autocomplete"] = "off"
                                    attributes["spellcheck"] = "false"
                                }
                                ContentSchema.DURATION -> attributes["inputmode"] = "numeric"
                            }
                            describe(helpId, errors)
                        }
                    }
                }
            }
        }
    }

    private fun FlowContent.originField(view: View) {
        val current = view.state.content.origin
        val first = "origin-${ContentOrigin.AI_ASSISTED.tagValue}"
        fieldSet(classes = "bo-fieldset") {
            legend { +EditorialTexts.ORIGIN_LEGEND }
            hint("origin-aide", EditorialTexts.ORIGIN_HINT)
            val errors = errors(view, first)
            listOf(ContentOrigin.AI_ASSISTED to EditorialTexts.ORIGIN_YES, ContentOrigin.HUMAN to EditorialTexts.ORIGIN_NO).forEach { (origin, text) ->
                div(classes = "bo-check") {
                    val id = "origin-${origin.tagValue}"
                    input(type = InputType.radio, name = "origin") {
                        this.id = id
                        value = origin.tagValue
                        if (current == origin) checked = true
                        describe("origin-aide", if (id == first) errors else emptyList())
                        if (errors.isNotEmpty()) attributes["aria-invalid"] = "true"
                    }
                    label {
                        htmlFor = id
                        +text
                    }
                }
            }
        }
    }

    private fun FlowContent.changeFields(view: View) {
        val content = view.state.content
        fieldSet(classes = "bo-fieldset") {
            legend { +"${EditorialTexts.CHANGE_LEGEND} (${EditorialTexts.REQUIRED})" }
            val kindErrors = errors(view, "change-${ChangeKind.CORRECTION.db}")
            listOf(ChangeKind.CORRECTION to EditorialTexts.CHANGE_CORRECTION, ChangeKind.UPDATE to EditorialTexts.CHANGE_UPDATE).forEach { (kind, text) ->
                div(classes = "bo-check") {
                    val id = "change-${kind.db}"
                    input(type = InputType.radio, name = "change_kind") {
                        this.id = id
                        value = kind.db
                        if (content.changeKind == kind) checked = true
                        if (kindErrors.isNotEmpty()) {
                            attributes["aria-describedby"] = kindErrors.joinToString(" ")
                            attributes["aria-invalid"] = "true"
                        }
                    }
                    label {
                        htmlFor = id
                        +text
                    }
                }
            }
            div(classes = "bo-field") {
                label {
                    htmlFor = "change-note"
                    +EditorialTexts.CHANGE_NOTE
                }
                p(classes = "bo-hint") {
                    id = "change-note-aide"
                    +EditorialTexts.CHANGE_NOTE_HINT
                }
                val noteErrors = errors(view, "change-note")
                textArea(rows = "3") {
                    id = "change-note"
                    name = "change_note"
                    maxLength = EditorialDrafts.MAX_CHANGE_NOTE.toString()
                    attributes["aria-describedby"] = (listOf("change-note-aide") + noteErrors).joinToString(" ")
                    if (noteErrors.isNotEmpty()) attributes["aria-invalid"] = "true"
                    +content.changeNote.orEmpty()
                }
            }
        }
    }

    private fun FlowContent.bodyField(view: View) {
        val body = view.schema.body
        div(classes = "bo-field") {
            label {
                htmlFor = "body"
                +"${EditorialTexts.BODY_LABEL} (${EditorialTexts.REQUIRED})"
            }
            div(classes = "bo-hint") {
                id = "body-aide"
                p { +EditorialTexts.BODY_HINT }
                p { +EditorialTexts.format(EditorialTexts.BODY_LIMIT, EditorialTexts.number(minOf(body.maxLength, EditorialDrafts.MAX_BODY)), EditorialTexts.number(MAX_TOTAL)) }
                if (body.blocks.isNotEmpty()) {
                    p { +("${EditorialTexts.BODY_BLOCKS} " + body.blocks.joinToString(", ") { block -> "::: ${block.name}" + bounds(block.min, block.max) } + ".") }
                }
                if (body.requiredSections.isNotEmpty()) {
                    p { +("${EditorialTexts.BODY_SECTIONS} " + body.requiredSections.joinToString(", ") { "## $it" } + ".") }
                }
            }
            val errors = errors(view, "body")
            textArea(rows = "30", classes = "bo-body") {
                id = "body"
                name = "body"
                maxLength = minOf(body.maxLength, EditorialDrafts.MAX_BODY).toString()
                attributes["spellcheck"] = "true"
                describe("body-aide", errors)
                +view.state.content.body
            }
        }
    }

    private fun bounds(min: Int, max: Int): String = when {
        min == max -> " ($min)"
        max == Int.MAX_VALUE && min > 0 -> " ($min au moins)"
        max == Int.MAX_VALUE -> " (facultatif)"
        min == 0 -> " ($max au plus)"
        else -> " ($min à $max)"
    }

    private fun FlowContent.field(view: View, field: FieldSpec) {
        val values = view.state.content.entries.filter { it.first == field.key }.map { it.second }
        val many = field.occurs as? Occurs.MANY
        val blanks = if (many == null) 0 else (view.state.blankRows[field.key] ?: BLANK_ROWS)
        val rowCount = if (many == null) maxOf(1, values.size) else minOf(maxOf(values.size + blanks, many.min, 1), maxOf(values.size, minOf(many.max, MAX_ROWS)))
        val label = EditorialTexts.label(field.labelKey, field.key)
        val required = when (val occurs = field.occurs) {
            Occurs.ONE -> EditorialTexts.REQUIRED
            Occurs.OPTIONAL -> EditorialTexts.OPTIONAL
            is Occurs.MANY -> if (occurs.min > 0) "${occurs.min} au moins" else EditorialTexts.OPTIONAL
        }
        val helpId = "f-${field.key}-aide"
        val help = field.helpKey?.let { EditorialTexts.label(it, "") }?.takeIf { it.isNotBlank() }
        fieldSet(classes = "bo-fieldset") {
            id = "champ-${field.key}"
            legend { +"$label ($required)" }
            if (help != null) hint(helpId, help)
            if (many != null) hint("$helpId-lignes", EditorialTexts.ROWS_HINT)
            input(type = InputType.hidden, name = "rows.${field.key}") { value = rowCount.toString() }
            if (many != null) input(type = InputType.hidden, name = "blank.${field.key}") { value = blanks.toString() }
            val describedBy = listOfNotNull(help?.let { helpId })
            for (row in 0 until rowCount) {
                val value = values.getOrNull(row).orEmpty()
                if (many != null) {
                    fieldSet(classes = "bo-row") {
                        legend { +"${EditorialTexts.ROW} ${row + 1} : $label" }
                        rowControls(view, field, row, value, describedBy)
                        if (row > 0) moveButton(field, row, up = true, label)
                        if (row < rowCount - 1) moveButton(field, row, up = false, label)
                    }
                } else {
                    rowControls(view, field, row, value, describedBy)
                }
            }
            if (many != null && rowCount < minOf(many.max, MAX_ROWS)) {
                button(type = ButtonType.submit, classes = "bo-button-secondary") {
                    name = "action"
                    value = "more:${field.key}"
                    +"${EditorialTexts.MORE_ROWS} : $label"
                }
            }
        }
    }

    private fun FlowContent.moveButton(field: FieldSpec, row: Int, up: Boolean, label: String) {
        button(type = ButtonType.submit, classes = "bo-button-secondary bo-move") {
            name = "action"
            value = "move:${field.key}:$row:${if (up) "up" else "down"}"
            +"${if (up) EditorialTexts.MOVE_UP else EditorialTexts.MOVE_DOWN} la ligne ${row + 1}"
            span(classes = "bo-sr-only") { +" : $label" }
        }
    }

    /** The controls of one row: one per position (with their own labels), or a single one. */
    private fun FlowContent.rowControls(view: View, field: FieldSpec, row: Int, value: String, describedBy: List<String>) {
        val name = "f.${field.key}.$row"
        val id = "f-${field.key}-$row"
        val label = EditorialTexts.label(field.labelKey, field.key)
        when (val spec = field.value) {
            is ValueSpec.Single -> control(view, spec.type, id, name, label, value, describedBy, optional = field.occurs != Occurs.ONE)
            is ValueSpec.Positional -> {
                val parts = if (value.isEmpty()) emptyList() else EditorialEngine.positions(value)
                spec.positions.forEachIndexed { index, position ->
                    control(view, position.type, "$id-${position.name}", "$name.${position.name}", positionLabel(position), parts.getOrNull(index).orEmpty(), describedBy, optional = !position.required)
                }
            }
            ValueSpec.NextStep -> {
                val label2 = value.substringBefore('|', value).trim()
                val target = if ('|' in value) value.substringAfter('|').trim() else ""
                control(view, FieldType.Text(200), "$id-$NEXT_LABEL", "$name.$NEXT_LABEL", "Libellé", label2, describedBy, optional = false)
                control(view, FieldType.Text(200), "$id-$NEXT_TARGET", "$name.$NEXT_TARGET", "Cible (fiche:…, voir:… ou espace:…)", target, describedBy, optional = false)
            }
            is ValueSpec.Link -> textControl(view, id, name, label, value, describedBy, list = listOf(spec.target).takeIf { spec.kinds.isEmpty() }.orEmpty(), hintText = EditorialTexts.LINK_HINT)
            is ValueSpec.InlineList -> {
                val targets = (spec.item as? ValueSpec.Link)?.let { listOf(it.target) + it.kinds.values }.orEmpty()
                textControl(view, id, name, label, value, describedBy, list = targets.takeIf { it.size == 1 }.orEmpty(), maxLength = MAX_VALUE)
            }
            is ValueSpec.OfficialId -> textControl(view, id, name, label, value, describedBy)
        }
    }

    private fun positionLabel(position: Position): String =
        EditorialTexts.label(position.labelKey, position.name) + if (position.required) "" else " (${EditorialTexts.OPTIONAL})"

    private fun FlowContent.textControl(
        view: View,
        id: String,
        name: String,
        label: String,
        value: String,
        describedBy: List<String>,
        list: List<TargetType> = emptyList(),
        hintText: String? = null,
        maxLength: Int = 300,
        type: InputType = InputType.text,
        mode: String? = null,
    ) {
        div(classes = "bo-field") {
            label {
                htmlFor = id
                +label
            }
            val own = hintText?.let { "$id-aide".also { hintId -> hint(hintId, it) } }
            val errors = errors(view, id)
            input(type = type, name = name) {
                this.id = id
                this.value = value
                this.maxLength = maxLength.toString()
                list.firstOrNull()?.let { attributes["list"] = "connus-${it.prefix}" }
                mode?.let { attributes["inputmode"] = it }
                describe((describedBy + listOfNotNull(own)).joinToString(" ").takeIf { it.isNotEmpty() }, errors)
            }
        }
    }

    /** The control of one value of [type]. */
    private fun FlowContent.control(view: View, type: FieldType, id: String, name: String, label: String, value: String, describedBy: List<String>, optional: Boolean) {
        when (type) {
            is FieldType.Text -> if (type.multiline) {
                div(classes = "bo-field") {
                    label {
                        htmlFor = id
                        +label
                    }
                    val errors = errors(view, id)
                    textArea(rows = "4") {
                        this.id = id
                        this.name = name
                        maxLength = type.maxLength.toString()
                        describe(describedBy.joinToString(" ").takeIf { it.isNotEmpty() }, errors)
                        +value
                    }
                }
            } else {
                textControl(view, id, name, label, value, describedBy, maxLength = type.maxLength)
            }
            is FieldType.Choice -> choice(view, type, id, name, label, value, describedBy, optional)
            is FieldType.Date -> textControl(
                view, id, name, label, value, describedBy,
                hintText = if (type.yearOnly) EditorialTexts.YEAR_HINT else EditorialTexts.DATE_HINT,
                maxLength = 10, type = if (type.yearOnly) InputType.text else InputType.date,
            )
            is FieldType.Number -> textControl(view, id, name, label, value, describedBy, hintText = EditorialTexts.NUMBER_HINT, maxLength = 40, mode = "decimal")
            FieldType.Url -> textControl(view, id, name, label, value, describedBy, hintText = EditorialTexts.URL_HINT, maxLength = EditorialEngine.URL_MAX, type = InputType.url)
            is FieldType.Slug -> textControl(view, id, name, label, value, describedBy, list = listOfNotNull(type.target), maxLength = 100)
            FieldType.Minutes -> textControl(view, id, name, label, value, describedBy, maxLength = 3, mode = "numeric")
            FieldType.Hash -> textControl(view, id, name, label, value, describedBy, maxLength = 64)
        }
    }

    private fun FlowContent.choice(view: View, type: FieldType.Choice, id: String, name: String, label: String, value: String, describedBy: List<String>, optional: Boolean) {
        val labels = type.values.map { it to EditorialTexts.label(type.labelKeyPrefix + it, it) }
        if (isRadios(type)) {
            val first = "$id-v0"
            fieldSet(classes = "bo-choice") {
                legend { +label }
                val errors = errors(view, first)
                val options = labels + if (optional) listOf("" to EditorialTexts.CHOICE_NONE) else emptyList()
                options.forEachIndexed { index, (option, text) ->
                    div(classes = "bo-check") {
                        val optionId = "$id-v$index"
                        input(type = InputType.radio, name = name) {
                            this.id = optionId
                            this.value = option
                            if (value == option && (value.isNotEmpty() || optional)) checked = true
                            describe(describedBy.joinToString(" ").takeIf { it.isNotEmpty() }, if (index == 0) errors else emptyList())
                            if (errors.isNotEmpty()) attributes["aria-invalid"] = "true"
                        }
                        label {
                            htmlFor = optionId
                            +text
                        }
                    }
                }
            }
        } else {
            div(classes = "bo-field") {
                label {
                    htmlFor = id
                    +label
                }
                val errors = errors(view, id)
                select {
                    this.id = id
                    this.name = name
                    describe(describedBy.joinToString(" ").takeIf { it.isNotEmpty() }, errors)
                    option {
                        this.value = ""
                        +(if (optional) EditorialTexts.CHOICE_NONE else EditorialTexts.SELECT_PROMPT)
                    }
                    labels.forEach { (option, text) ->
                        option {
                            this.value = option
                            if (option == value) selected = true
                            +text
                        }
                    }
                    // A value the list does not know is kept, so that saving never loses it (the engine reports it).
                    if (value.isNotEmpty() && labels.none { it.first == value }) {
                        option {
                            this.value = value
                            selected = true
                            +value
                        }
                    }
                }
            }
        }
    }

    /** The known slugs of each target type used by the form (a list the browser proposes, without script). */
    private fun FlowContent.knownLists(view: View) {
        val targets = view.schema.fields.flatMap { field ->
            when (val value = field.value) {
                is ValueSpec.Link -> listOf(value.target)
                is ValueSpec.InlineList -> (value.item as? ValueSpec.Link)?.let { listOf(it.target) }.orEmpty()
                is ValueSpec.Single -> listOfNotNull((value.type as? FieldType.Slug)?.target)
                is ValueSpec.Positional -> value.positions.mapNotNull { (it.type as? FieldType.Slug)?.target }
                else -> emptyList()
            }
        }.distinct()
        targets.forEach { target ->
            dataList {
                id = "connus-${target.prefix}"
                view.known.filter { (_, type) -> target.accepts(type) }.keys.sorted().take(500).forEach { slug -> option { value = slug } }
            }
        }
    }
}
