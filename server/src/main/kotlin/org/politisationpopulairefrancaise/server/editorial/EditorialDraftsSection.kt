package org.politisationpopulairefrancaise.server.editorial

import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.caption
import kotlinx.html.code
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.nav
import kotlinx.html.ol
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import kotlinx.html.ul
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.editorial.schema.ContentSchema
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialEngine
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSchemas
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSpace
import org.politisationpopulairefrancaise.core.editorial.schema.Problem
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeSection
import org.politisationpopulairefrancaise.server.backoffice.Notice
import org.politisationpopulairefrancaise.server.backoffice.Permissions
import org.politisationpopulairefrancaise.server.backoffice.SectionContext
import org.politisationpopulairefrancaise.server.backoffice.SectionRoutes
import org.politisationpopulairefrancaise.server.backoffice.dateTime
import org.politisationpopulairefrancaise.web.comprendre.comprendreBodyPreview
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * The writing pages of the editorial circuit of one space (docs/integration/circuit-editorial.md, step 3),
 * a section of the back-office (`editorial.<space>`, contract of the back-office, section 14): the drafts,
 * a new draft of each type of the space ([EditorialSchemas.of]), the form generated from the schema
 * without JavaScript, the checks of [EditorialEngine.check] at every save (a draft with problems is saved
 * all the same: no work is lost), the preview rendered as on the site and the diff with the published
 * version. Every save adds a version (optimistic concurrency) and a line of the journal, written by the
 * base. No queue action, no signing, no publication here: steps 4 and 5.
 */
class EditorialDraftsSection(
    private val space: EditorialSpace,
    private val drafts: EditorialDrafts,
    private val published: PublishedContents,
    private val clock: Clock = Clock.systemUTC(),
) : BackOfficeSection {
    override val id: String = "editorial.${space.id}"
    override val title: String = if (space == EditorialSpace.COMPRENDRE) EditorialTexts.SECTION_COMPRENDRE else EditorialTexts.SECTION_VOIR
    override val permission: String = Permissions.editorialWrite(space.id)

    // A long text in French, URL-encoded, takes up to about four times its length.
    override val maxBodyBytes: Int = BackOfficeSection.MAX_SECTION_BODY_BYTES

    private val schemas: List<ContentSchema> get() = EditorialSchemas.of(space)

    private fun schemaOf(type: String?): ContentSchema? = schemas.firstOrNull { it.type.tagValue == type }

    private fun schemaOf(type: ArticleType): ContentSchema? = schemas.firstOrNull { it.type == type }

    override fun routes(routes: SectionRoutes) {
        routes.get("") { home() }
        routes.get("nouveau/{type}") {
            val schema = schemaOf(pathParameter("type")) ?: return@get notFound()
            formPage(schema, null, EditorialForm.empty(schema, today()), version = null, problems = emptyList())
        }
        routes.post("nouveau/{type}") { form ->
            val schema = schemaOf(pathParameter("type")) ?: return@post notFound()
            postNew(schema, form)
        }
        routes.post("mise-a-jour") { form -> createUpdate(form["slug"]?.trim().orEmpty()) }
        routes.get("brouillons/{id}") {
            val (draft, schema) = draftOf(pathParameter("id")) ?: return@get notFound()
            val latest = requireNotNull(drafts.version(draft.id, draft.latestVersion))
            val notice = when (query["ok"]) {
                "created" -> Notice(EditorialTexts.CREATED)
                "saved" -> Notice(EditorialTexts.SAVED)
                "unchanged" -> Notice(EditorialTexts.UNCHANGED)
                else -> null
            }
            formPage(schema, draft, FormState(latest.content), draft.latestVersion, check(schema, latest.content), notice = notice)
        }
        routes.post("brouillons/{id}") { form ->
            val (draft, schema) = draftOf(pathParameter("id")) ?: return@post notFound()
            postEdit(draft, schema, form)
        }
        routes.get("brouillons/{id}/apercu") {
            val (draft, schema) = draftOf(pathParameter("id")) ?: return@get notFound()
            val latest = requireNotNull(drafts.version(draft.id, draft.latestVersion))
            respondPage("${EditorialTexts.PREVIEW_TITLE} : ${draft.title.ifBlank { EditorialTexts.UNTITLED }}") {
                draftHeader(this@get, draft, schema, EditorialTexts.PREVIEW_TITLE)
                p { +EditorialTexts.PREVIEW_INTRO }
                preview(schema, latest.content)
            }
        }
        routes.get("brouillons/{id}/differences") {
            val (draft, schema) = draftOf(pathParameter("id")) ?: return@get notFound()
            val latest = requireNotNull(drafts.version(draft.id, draft.latestVersion))
            val snapshot = published.snapshot()
            val base = draft.slug?.let { snapshot.published(snapshot.authorOf(space), it) }
            respondPage("${EditorialTexts.DIFF_TITLE} : ${draft.title.ifBlank { EditorialTexts.UNTITLED }}") {
                draftHeader(this@get, draft, schema, EditorialTexts.DIFF_TITLE)
                if (base == null) {
                    p { +EditorialTexts.DIFF_NONE_PUBLISHED }
                } else {
                    val baseSchema = ArticleType.entries.firstOrNull { it.tagValue == base.firstTagValue(Article.TAG_TYPE) }?.let { EditorialSchemas.of(it) } ?: schema
                    val before = EditorialEngine.draftOf(baseSchema, base, snapshot.context(space, emptyMap()))
                    diff(LineDiff.linesOf(before), LineDiff.linesOf(latest.content.draft(schema.type)))
                }
            }
        }
        routes.get("brouillons/{id}/versions") {
            val (draft, schema) = draftOf(pathParameter("id")) ?: return@get notFound()
            val versions = drafts.versions(draft.id)
            val names = versions.map { it.writtenBy }.distinct().associateWith { pseudonymOf(it) }
            respondPage("${EditorialTexts.VERSIONS_TITLE} : ${draft.title.ifBlank { EditorialTexts.UNTITLED }}") {
                draftHeader(this@get, draft, schema, EditorialTexts.VERSIONS_TITLE)
                table(classes = "bo-table") {
                    caption { +EditorialTexts.VERSIONS_TITLE }
                    thead {
                        tr {
                            th { +EditorialTexts.VERSION }
                            th { +EditorialTexts.COLUMN_UPDATED }
                            th { +EditorialTexts.COLUMN_BY }
                            th { +EditorialTexts.COLUMN_PROBLEMS }
                            th { +EditorialTexts.common(ContentSchema.ORIGIN) }
                        }
                    }
                    tbody {
                        versions.forEach { version ->
                            tr {
                                td { +version.version.toString() }
                                td { dateTime(version.writtenAt) }
                                td { +(names[version.writtenBy] ?: "") }
                                td { +version.problems.toString() }
                                td { +originText(version.content.origin) }
                            }
                        }
                    }
                }
            }
        }
    }

    // Pages -----------------------------------------------------------------------------------------

    private suspend fun SectionContext.home() {
        val list = drafts.list(space)
        val authors = list.map { it.author }.distinct().associateWith { pseudonymOf(it) }
        val publishedContents = published.snapshot().ofSpace(space)
        val notice = when {
            query["ok"] == "created" -> Notice(EditorialTexts.CREATED)
            query["erreur"] == "not_published" -> Notice(EditorialTexts.NOT_PUBLISHED, error = true)
            query["erreur"] == "slug_taken" -> Notice(EditorialTexts.SLUG_TAKEN, error = true)
            else -> null
        }
        respondPage(title, notice) {
            h1 { +title }
            p { +EditorialTexts.HOME_INTRO }
            section {
                attributes["aria-labelledby"] = "brouillons"
                h2 { id = "brouillons"; +EditorialTexts.DRAFTS_TITLE }
                if (list.isEmpty()) {
                    p { +EditorialTexts.DRAFTS_EMPTY }
                } else {
                    table(classes = "bo-table") {
                        caption { +EditorialTexts.DRAFTS_TITLE }
                        thead {
                            tr {
                                th { +EditorialTexts.COLUMN_TITLE }
                                th { +EditorialTexts.COLUMN_TYPE }
                                th { +EditorialTexts.common(ContentSchema.SLUG) }
                                th { +EditorialTexts.VERSION }
                                th { +EditorialTexts.COLUMN_UPDATED }
                                th { +EditorialTexts.COLUMN_BY }
                            }
                        }
                        tbody {
                            list.forEach { draft ->
                                tr {
                                    td { a(href = path("brouillons/${draft.id}")) { +draft.title.ifBlank { EditorialTexts.UNTITLED } } }
                                    td { +typeLabel(draft.type) }
                                    td { draft.slug?.let { code { +it } } }
                                    td { +"${draft.latestVersion} (${EditorialTexts.status(draft.status)})" }
                                    td { dateTime(draft.updatedAt) }
                                    td { +(authors[draft.author] ?: "") }
                                }
                            }
                        }
                    }
                }
            }
            section {
                attributes["aria-labelledby"] = "nouveau"
                h2 { id = "nouveau"; +EditorialTexts.NEW_TITLE }
                p { +EditorialTexts.NEW_INTRO }
                ul {
                    schemas.forEach { schema -> li { a(href = path("nouveau/${schema.type.tagValue}")) { +EditorialTexts.label(schema.labelKey, schema.type.tagValue) } } }
                }
            }
            section {
                attributes["aria-labelledby"] = "mise-a-jour"
                h2 { id = "mise-a-jour"; +EditorialTexts.UPDATE_TITLE }
                p { +EditorialTexts.UPDATE_INTRO }
                if (publishedContents.isEmpty()) {
                    p { +EditorialTexts.PUBLISHED_EMPTY }
                } else {
                    form(action = path("mise-a-jour"), method = FormMethod.post) {
                        csrfInput(this)
                        div(classes = "bo-field") {
                            label {
                                htmlFor = "maj-slug"
                                +EditorialTexts.UPDATE_SLUG
                            }
                            select {
                                id = "maj-slug"
                                name = "slug"
                                publishedContents.forEach { event ->
                                    val slug = event.firstTagValue("d").orEmpty()
                                    option {
                                        value = slug
                                        +"${event.firstTagValue(Article.TAG_TITLE).orEmpty()} ($slug)"
                                    }
                                }
                            }
                        }
                        button(type = ButtonType.submit, classes = "bo-button") { +EditorialTexts.UPDATE_BUTTON }
                    }
                }
            }
        }
    }

    /** The form of a new draft or of [draft], with its problems, the preview or the conflict when asked. */
    private suspend fun SectionContext.formPage(
        schema: ContentSchema,
        draft: EditorialDraft?,
        state: FormState,
        version: Int?,
        problems: List<Problem>,
        notice: Notice? = null,
        status: HttpStatusCode = HttpStatusCode.OK,
        showPreview: Boolean = false,
        conflict: DraftVersion? = null,
    ) {
        val snapshot = published.snapshot()
        val known = drafts.openSlugs() + snapshot.types(snapshot.authorOf(space))
        val typeName = typeLabel(schema.type)
        val pageTitle = if (draft == null) "${EditorialTexts.FORM_NEW_TITLE} : $typeName" else "${EditorialTexts.FORM_EDIT_TITLE} : ${draft.title.ifBlank { EditorialTexts.UNTITLED }}"
        val action = if (draft == null) path("nouveau/${schema.type.tagValue}") else path("brouillons/${draft.id}")
        respondPage(pageTitle, notice, status) {
            if (draft == null) {
                p { a(href = path()) { +EditorialTexts.BACK_TO_SECTION } }
                h1 { +pageTitle }
            } else {
                draftHeader(this@formPage, draft, schema, null)
            }
            conflict?.let { latest ->
                section {
                    attributes["aria-labelledby"] = "conflit"
                    h2 { id = "conflit"; +EditorialTexts.CONFLICT_DIFF }
                    diff(LineDiff.linesOf(latest.content.draft(schema.type)), LineDiff.linesOf(state.content.draft(schema.type)))
                }
            }
            if (problems.isEmpty() && draft != null) p(classes = "bo-notice") { attributes["role"] = "status"; +EditorialTexts.NO_PROBLEM }
            EditorialForm.render(
                this,
                EditorialForm.View(schema, state, problems, action, version, isUpdate = draft?.baseEventId != null, known = known, csrf = { csrfInput(it) }),
            )
            if (showPreview) {
                section(classes = "bo-preview") {
                    attributes["aria-labelledby"] = "apercu"
                    h2 { id = "apercu"; +EditorialTexts.PREVIEW_TITLE }
                    p { +EditorialTexts.PREVIEW_INTRO }
                    preview(schema, state.content)
                }
            }
        }
    }

    /** The title of a draft's page and the links to its other pages. */
    private fun FlowContent.draftHeader(context: SectionContext, draft: EditorialDraft, schema: ContentSchema, page: String?) {
        p { a(href = context.path()) { +EditorialTexts.BACK_TO_SECTION } }
        h1 { +(page?.let { "$it : " }.orEmpty() + draft.title.ifBlank { EditorialTexts.UNTITLED }) }
        p(classes = "bo-summary") {
            +"${typeLabel(schema.type)}, ${EditorialTexts.VERSION.lowercase()} ${draft.latestVersion}, ${EditorialTexts.status(draft.status).lowercase()}"
            if (draft.baseEventId != null) +" (${EditorialTexts.FORM_UPDATE_OF.lowercase()})"
        }
        nav {
            attributes["aria-label"] = EditorialTexts.LINKS_LABEL
            ul(classes = "bo-inline") {
                listOf(
                    "" to EditorialTexts.LINK_FORM,
                    "/apercu" to EditorialTexts.LINK_PREVIEW,
                    "/differences" to EditorialTexts.LINK_DIFF,
                    "/versions" to EditorialTexts.LINK_VERSIONS,
                ).forEach { (suffix, text) ->
                    li {
                        a(href = context.path("brouillons/${draft.id}$suffix")) {
                            if (page == null && suffix.isEmpty() || page == pageName(suffix)) attributes["aria-current"] = "page"
                            +text
                        }
                    }
                }
            }
        }
    }

    private fun pageName(suffix: String): String? = when (suffix) {
        "/apercu" -> EditorialTexts.PREVIEW_TITLE
        "/differences" -> EditorialTexts.DIFF_TITLE
        "/versions" -> EditorialTexts.VERSIONS_TITLE
        else -> null
    }

    /** The content built from [content] as the site would show it, and the tags of the event. */
    private fun FlowContent.preview(schema: ContentSchema, content: DraftContent) {
        val author = published.snapshot().authorOf(space)
        val event = try {
            EditorialEngine.build(schema, content.draft(schema.type), PublicKey.fromHex(author))
        } catch (e: Exception) {
            null
        }
        if (event == null) {
            p(classes = "bo-notice bo-notice-warning") { +EditorialTexts.PREVIEW_IMPOSSIBLE }
            return
        }
        div(classes = "bo-preview-content") {
            h2 { +event.tags.firstOrNull { it.firstOrNull() == Article.TAG_TITLE }?.getOrNull(1).orEmpty() }
            event.tags.firstOrNull { it.firstOrNull() == Article.TAG_SUMMARY }?.getOrNull(1)?.let { p(classes = "bo-summary") { +it } }
            p(classes = "bo-badge") { +originText(content.origin) }
            comprendreBodyPreview(event.content)
        }
        table(classes = "bo-table") {
            caption { +EditorialTexts.PREVIEW_TAGS }
            thead {
                tr {
                    th { +EditorialTexts.PREVIEW_TAG_NAME }
                    th { +EditorialTexts.PREVIEW_TAG_VALUES }
                }
            }
            tbody {
                event.tags.forEach { tag ->
                    tr {
                        td { code { +tag.firstOrNull().orEmpty() } }
                        td { +tag.drop(1).joinToString(" | ") }
                    }
                }
            }
        }
    }

    /** A line diff: each line marked in text (« + », « - ») as well as by its style. */
    private fun FlowContent.diff(before: List<String>, after: List<String>) {
        val lines = LineDiff.of(before, after)
        when {
            lines == null -> p { +EditorialTexts.DIFF_TOO_LONG }
            !LineDiff.changed(lines) -> p { +EditorialTexts.DIFF_SAME }
            else -> {
                p(classes = "bo-hint") { +EditorialTexts.DIFF_LEGEND }
                ol(classes = "bo-diff") {
                    lines.forEach { line ->
                        when (line.kind) {
                            LineDiff.Kind.SAME -> li(classes = "bo-diff-same") { +"  ${line.text}" }
                            LineDiff.Kind.ADDED -> li(classes = "bo-diff-added") {
                                span(classes = "bo-sr-only") { +"${EditorialTexts.DIFF_ADDED} : " }
                                +"+ ${line.text}"
                            }
                            LineDiff.Kind.REMOVED -> li(classes = "bo-diff-removed") {
                                span(classes = "bo-sr-only") { +"${EditorialTexts.DIFF_REMOVED} : " }
                                +"- ${line.text}"
                            }
                        }
                    }
                }
            }
        }
    }

    // Forms -----------------------------------------------------------------------------------------

    private suspend fun SectionContext.postNew(schema: ContentSchema, form: Parameters) {
        val posted = EditorialForm.read(schema, form, isUpdate = false)
        val content = posted.state.content
        val problems = check(schema, content)
        if (posted.action != FormAction.Save) {
            return formPage(schema, null, posted.state, null, problems, showPreview = posted.action == FormAction.Preview)
        }
        if (content.body.length > EditorialDrafts.MAX_BODY) return formPage(schema, null, posted.state, null, problems, Notice(bodyTooLong(schema, content), error = true), HttpStatusCode.UnprocessableEntity)
        when (val result = drafts.create(space, schema.type, content, problems.size, actor.accountId)) {
            is SaveResult.Saved -> {
                audit("draft_created", result.draft.id.toString(), details = details(result.draft, 1, problems))
                redirect("brouillons/${result.draft.id}", "ok=created")
            }
            SaveResult.SlugTaken -> formPage(schema, null, posted.state, null, problems, Notice(EditorialTexts.SLUG_TAKEN, error = true), HttpStatusCode.Conflict)
            else -> notFound()
        }
    }

    private suspend fun SectionContext.postEdit(draft: EditorialDraft, schema: ContentSchema, form: Parameters) {
        val posted = EditorialForm.read(schema, form, isUpdate = draft.baseEventId != null)
        val content = posted.state.content
        val problems = check(schema, content)
        val version = posted.version ?: 0
        if (!draft.status.editable) {
            return formPage(schema, draft, posted.state, draft.latestVersion, problems, Notice(EditorialTexts.NOT_EDITABLE, error = true), HttpStatusCode.Conflict)
        }
        if (posted.action != FormAction.Save) {
            return formPage(schema, draft, posted.state, version, problems, showPreview = posted.action == FormAction.Preview)
        }
        if (content.body.length > EditorialDrafts.MAX_BODY) return formPage(schema, draft, posted.state, version, problems, Notice(bodyTooLong(schema, content), error = true), HttpStatusCode.UnprocessableEntity)
        when (val result = drafts.save(draft.id, version, content, problems.size, actor.accountId)) {
            is SaveResult.Saved -> {
                audit("draft_saved", draft.id.toString(), details = details(result.draft, result.draft.latestVersion, problems))
                redirect("brouillons/${draft.id}", "ok=saved")
            }
            is SaveResult.Unchanged -> redirect("brouillons/${draft.id}", "ok=unchanged")
            // The form comes back with the editor's text, now based on the latest version: saving again keeps it, knowingly.
            is SaveResult.Conflict -> formPage(
                schema, draft, posted.state, result.latest.version, problems,
                Notice(EditorialTexts.format(EditorialTexts.CONFLICT, result.latest.version), error = true), HttpStatusCode.Conflict, conflict = result.latest,
            )
            SaveResult.SlugTaken -> formPage(schema, draft, posted.state, version, problems, Notice(EditorialTexts.SLUG_TAKEN, error = true), HttpStatusCode.Conflict)
            SaveResult.NotEditable -> formPage(schema, draft, posted.state, version, problems, Notice(EditorialTexts.NOT_EDITABLE, error = true), HttpStatusCode.Conflict)
            SaveResult.NotFound -> notFound()
        }
    }

    /** A draft of a published content of the space, its first version read from the published event. */
    private suspend fun SectionContext.createUpdate(slug: String) {
        val snapshot = published.snapshot()
        val event = snapshot.ofSpace(space).firstOrNull { it.firstTagValue("d") == slug } ?: return redirect(query = "erreur=not_published")
        val schema = ArticleType.entries.firstOrNull { it.tagValue == event.firstTagValue(Article.TAG_TYPE) }?.let { schemaOf(it) } ?: return redirect(query = "erreur=not_published")
        val read = EditorialEngine.draftOf(schema, event, snapshot.context(space, emptyMap()))
        val content = DraftContent(EditorialForm.ordered(schema, read.entries), read.body)
        val problems = check(schema, content)
        when (val result = drafts.create(space, schema.type, content, problems.size, actor.accountId, baseEventId = event.id)) {
            is SaveResult.Saved -> {
                audit("draft_created", result.draft.id.toString(), details = details(result.draft, 1, problems) + ("base_event" to event.id))
                redirect("brouillons/${result.draft.id}", "ok=created")
            }
            SaveResult.SlugTaken -> redirect(query = "erreur=slug_taken")
            else -> notFound()
        }
    }

    // Helpers ---------------------------------------------------------------------------------------

    private fun draftOf(id: String?): Pair<EditorialDraft, ContentSchema>? {
        val uuid = id?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
        val draft = drafts.find(uuid)?.takeIf { it.space == space } ?: return null
        return draft to (schemaOf(draft.type) ?: return null)
    }

    /** Every problem of [content], against the published contents and the other open drafts. */
    fun check(schema: ContentSchema, content: DraftContent): List<Problem> {
        val context = published.snapshot().context(space, drafts.openSlugs())
        return EditorialEngine.check(schema, content.draft(schema.type), context)
    }

    private fun bodyTooLong(schema: ContentSchema, content: DraftContent): String =
        EditorialTexts.problem(schema, Problem(org.politisationpopulairefrancaise.core.editorial.schema.Codes.BODY_TOO_LONG, EditorialEngine.BODY, args = listOf(EditorialDrafts.MAX_BODY.toString(), content.body.length.toString())))

    /** What the journal keeps of a save: identifiers and counts, never the text. */
    private fun details(draft: EditorialDraft, version: Int, problems: List<Problem>): Map<String, String> = buildMap {
        put("version", version.toString())
        put("type", draft.type.tagValue)
        draft.slug?.let { put("slug", it) }
        put("problems", problems.size.toString())
    }

    private fun typeLabel(type: ArticleType): String = schemaOf(type)?.let { EditorialTexts.label(it.labelKey, type.tagValue) } ?: type.tagValue

    private fun originText(origin: ContentOrigin?): String = when (origin) {
        ContentOrigin.AI_ASSISTED -> EditorialTexts.PREVIEW_ORIGIN_AI
        ContentOrigin.HUMAN -> EditorialTexts.PREVIEW_ORIGIN_HUMAN
        null -> EditorialTexts.CHOICE_NONE
    }

    private fun today(): String = LocalDate.now(clock.withZone(PARIS)).toString()

    companion object {
        private val PARIS: ZoneId = ZoneId.of("Europe/Paris")
    }
}
