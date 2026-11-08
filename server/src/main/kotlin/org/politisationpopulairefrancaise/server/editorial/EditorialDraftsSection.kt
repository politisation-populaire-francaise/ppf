package org.politisationpopulairefrancaise.server.editorial

import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.caption
import kotlinx.html.code
import kotlinx.html.dd
import kotlinx.html.div
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.input
import kotlinx.html.textArea
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
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialApprovals
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
import org.politisationpopulairefrancaise.web.voir.voirBlockPreview
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
 * base.
 *
 * Step 4: a writer submits a version without problem (`brouillons/{id}/soumettre`); another editor decides
 * on the review page (`relecture/{id}`): request changes (message, back to the writers), reject (reason,
 * closed), approve for publication (state `ready_to_sign`, handed to [EditorialPublisher]). Every decision
 * writes two journal lines through the base, `<action>_requested` before and `<action>` with its outcome
 * after, with identifiers and codes only: the free text stays in `editorial_review`. No signing and no
 * publication here: step 5, on the pages of the signing service.
 */
class EditorialDraftsSection(
    private val space: EditorialSpace,
    private val drafts: EditorialDrafts,
    /** The review of step 4: submission, and the decisions taken on the review page (the queue `editorial.<space>` lists them). */
    private val reviews: EditorialReviews,
    private val published: PublishedContents,
    private val clock: Clock = Clock.systemUTC(),
    /** Where an approved version goes (step 5); nothing is sent until the signing service is plugged. */
    private val publisher: EditorialPublisher = UnpluggedEditorialPublisher,
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
                "submitted" -> Notice(EditorialTexts.SUBMITTED)
                else -> null
            }
            if (!draft.status.editable) return@get readOnlyPage(draft, schema, latest, notice)
            formPage(schema, draft, FormState(latest.content), draft.latestVersion, check(schema, latest.content, draft.baseEventId != null), notice = notice, offerSubmit = true)
        }
        routes.post("brouillons/{id}") { form ->
            val (draft, schema) = draftOf(pathParameter("id")) ?: return@post notFound()
            postEdit(draft, schema, form)
        }
        routes.post("brouillons/{id}/soumettre") { form ->
            val (draft, schema) = draftOf(pathParameter("id")) ?: return@post notFound()
            postSubmit(draft, schema, form)
        }
        routes.get("relecture/{id}") {
            val (draft, schema) = draftOf(pathParameter("id")) ?: return@get notFound()
            val notice = when (query["ok"]) {
                "changes_requested" -> Notice(EditorialTexts.DONE_CHANGES_REQUESTED)
                "rejected" -> Notice(EditorialTexts.DONE_REJECTED)
                "withdrawn" -> Notice(EditorialTexts.DONE_WITHDRAWN)
                else -> null
            }
            reviewPage(draft, schema, notice)
        }
        routes.post("relecture/{id}") { form ->
            val (draft, schema) = draftOf(pathParameter("id")) ?: return@post notFound()
            postDecision(draft, schema, form)
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
                                th { +EditorialTexts.COLUMN_STATUS }
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
                                    td { +draft.latestVersion.toString() }
                                    td {
                                        +EditorialTexts.status(draft.status)
                                        if (draft.status == DraftStatus.IN_REVIEW || draft.status in EditorialReviews.SIGNABLE) {
                                            +" ("
                                            a(href = path("relecture/${draft.id}")) { +EditorialTexts.REVIEW_LINK.lowercase() }
                                            +")"
                                        }
                                    }
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
        /** Whether the page shows the saved latest version, which may then be submitted. */
        offerSubmit: Boolean = false,
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
            draft?.let { lastMessage(it) }
            EditorialForm.render(
                this,
                EditorialForm.View(schema, state, problems, action, version, isUpdate = draft?.baseEventId != null, known = known, csrf = { csrfInput(it) }),
            )
            // The submission, a separate form: only the latest saved version, without any problem, leaves for review.
            if (offerSubmit && draft != null && draft.status.editable && problems.isEmpty()) {
                section {
                    attributes["aria-labelledby"] = "soumettre"
                    h2 { id = "soumettre"; +EditorialTexts.SUBMIT_TITLE }
                    p { +EditorialTexts.format(EditorialTexts.SUBMIT_INTRO, draft.latestVersion) }
                    form(action = path("brouillons/${draft.id}/soumettre"), method = FormMethod.post) {
                        csrfInput(this)
                        input(type = InputType.hidden, name = "version") { value = draft.latestVersion.toString() }
                        button(type = ButtonType.submit, classes = "bo-button") { +EditorialTexts.SUBMIT }
                    }
                }
            }
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
                if (draft.submittedVersion != null) {
                    li {
                        a(href = context.path("relecture/${draft.id}")) {
                            if (page == EditorialTexts.REVIEW_TITLE) attributes["aria-current"] = "page"
                            +EditorialTexts.LINK_REVIEW
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

    /**
     * The content built from [content] as the site would show it, and the tags of the event. The unsigned
     * event built here lives only during this request: it is rendered as HTML (never its id, never as JSON),
     * never stored, never exported, never given to the publication port nor to the signing service. Only
     * the queue action of steps 4 and 5 builds the event to sign, again, from the version under review.
     */
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
        // Voir (step 6): the block as its page on the site, with the published blocks it links to.
        if (space == EditorialSpace.VOIR) {
            div(classes = "bo-preview-content") {
                if (!voirBlockPreview(event, published.snapshot().ofSpace(space))) p(classes = "bo-notice bo-notice-warning") { +EditorialTexts.PREVIEW_IMPOSSIBLE }
            }
        } else div(classes = "bo-preview-content") {
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
        posted.tooLong?.let { return formPage(schema, null, posted.state, null, problems, Notice(it, error = true), HttpStatusCode.UnprocessableEntity) }
        when (val result = drafts.create(space, schema.type, content, problems.size, actor.accountId)) {
            is SaveResult.Saved -> {
                if (!journal("draft_created", result.draft, 1, details(result.draft, 1, problems))) return
                redirect("brouillons/${result.draft.id}", "ok=created")
            }
            SaveResult.SlugTaken -> formPage(schema, null, posted.state, null, problems, Notice(EditorialTexts.SLUG_TAKEN, error = true), HttpStatusCode.Conflict)
            else -> notFound()
        }
    }

    private suspend fun SectionContext.postEdit(draft: EditorialDraft, schema: ContentSchema, form: Parameters) {
        val posted = EditorialForm.read(schema, form, isUpdate = draft.baseEventId != null)
        val content = posted.state.content
        val problems = check(schema, content, draft.baseEventId != null)
        val version = posted.version ?: 0
        if (!draft.status.editable) {
            return formPage(schema, draft, posted.state, draft.latestVersion, problems, Notice(EditorialTexts.NOT_EDITABLE, error = true), HttpStatusCode.Conflict)
        }
        if (posted.action != FormAction.Save) {
            return formPage(schema, draft, posted.state, version, problems, showPreview = posted.action == FormAction.Preview)
        }
        posted.tooLong?.let { return formPage(schema, draft, posted.state, version, problems, Notice(it, error = true), HttpStatusCode.UnprocessableEntity) }
        when (val result = drafts.save(draft.id, version, content, problems.size, actor.accountId)) {
            is SaveResult.Saved -> {
                if (!journal("draft_saved", result.draft, result.draft.latestVersion, details(result.draft, result.draft.latestVersion, problems))) return
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
        val problems = check(schema, content, isUpdate = true)
        when (val result = drafts.create(space, schema.type, content, problems.size, actor.accountId, baseEventId = event.id)) {
            is SaveResult.Saved -> {
                if (!journal("draft_created", result.draft, 1, details(result.draft, 1, problems) + ("base_event" to event.id))) return
                redirect("brouillons/${result.draft.id}", "ok=created")
            }
            SaveResult.SlugTaken -> redirect(query = "erreur=slug_taken")
            else -> notFound()
        }
    }

    // Review (step 4) -------------------------------------------------------------------------------

    /** A draft that is not editable (in review, ready to sign, finished): its state, the last message and its content. */
    private suspend fun SectionContext.readOnlyPage(draft: EditorialDraft, schema: ContentSchema, latest: DraftVersion, notice: Notice?, status: HttpStatusCode = HttpStatusCode.OK) {
        respondPage("${EditorialTexts.FORM_EDIT_TITLE} : ${draft.title.ifBlank { EditorialTexts.UNTITLED }}", notice, status) {
            draftHeader(this@readOnlyPage, draft, schema, null)
            p(classes = "bo-notice") {
                attributes["role"] = "status"
                +stateText(draft.status)
            }
            lastMessage(draft)
            section(classes = "bo-preview") {
                attributes["aria-labelledby"] = "apercu"
                h2 { id = "apercu"; +EditorialTexts.PREVIEW_TITLE }
                preview(schema, latest.content)
            }
        }
    }

    private fun stateText(status: DraftStatus): String = when (status) {
        DraftStatus.IN_REVIEW -> EditorialTexts.STATE_IN_REVIEW
        DraftStatus.READY_TO_SIGN -> EditorialTexts.STATE_READY_TO_SIGN
        DraftStatus.REJECTED -> EditorialTexts.STATE_REJECTED
        DraftStatus.SIGNING_REFUSED -> EditorialTexts.STATE_SIGNING_REFUSED
        DraftStatus.PUBLISHED -> EditorialTexts.STATE_PUBLISHED
        else -> EditorialTexts.STATE_FINISHED
    }

    /** The message of the last request for changes, or the reason of the refusal, for the writers. */
    private fun FlowContent.lastMessage(draft: EditorialDraft) {
        val withdrawal = draft.withdrawReason
        if (withdrawal != null && draft.status.editable) {
            section(classes = "bo-notice bo-notice-warning") {
                attributes["aria-labelledby"] = "dernier-retrait"
                h2 { id = "dernier-retrait"; +"${EditorialTexts.LAST_WITHDRAWAL} (${EditorialTexts.VERSION.lowercase()} ${draft.submittedVersion ?: draft.latestVersion})" }
                withdrawal.lines().forEach { line -> p { +line } }
            }
            return
        }
        if (draft.status != DraftStatus.CHANGES_REQUESTED && draft.status != DraftStatus.REJECTED) return
        val last = reviews.decisions(draft.id).firstOrNull { it.reason != null } ?: return
        section(classes = "bo-notice bo-notice-warning") {
            attributes["aria-labelledby"] = "dernier-message"
            h2 { id = "dernier-message"; +"${EditorialTexts.LAST_MESSAGE} (${EditorialTexts.status(last.decision.status).lowercase()}, ${EditorialTexts.VERSION.lowercase()} ${last.version})" }
            last.reason.orEmpty().lines().forEach { line -> p { +line } }
        }
    }

    /** The review page: the submitted version as the site would show it, its facts, the decisions, the forms. */
    private suspend fun SectionContext.reviewPage(given: EditorialDraft, schema: ContentSchema, notice: Notice?, status: HttpStatusCode = HttpStatusCode.OK, signer: PublisherAnswer? = null) {
        val submitted = given.submittedVersion?.let { drafts.version(given.id, it) }
        // A version approved for publication: where it stands on the signing service, read now (no background loop).
        val signing = if (given.status in SIGNING_STATES && submitted != null) signer ?: signerAnswer(given, submitted.version) { publisher.state(it) } else null
        // The signing seam may have published or refused it just now: the page shows the new state.
        val draft = if (signing != null) drafts.find(given.id) ?: given else given
        val history = reviews.decisions(draft.id)
        val names = (history.map { it.reviewer } + draft.author).distinct().associateWith { pseudonymOf(it) }
        val queued = reviews.queued(space).firstOrNull { it.draft.id == draft.id }
        val problems = if (submitted != null && draft.status == DraftStatus.IN_REVIEW) check(schema, submitted.content, draft.baseEventId != null) else emptyList()
        val writer = reviews.isWriterOf(draft.id, actor.accountId)
        val mayApprove = !writer && actor.has(EditorialQueue.publishPermission(space))
        respondPage("${EditorialTexts.REVIEW_TITLE} : ${draft.title.ifBlank { EditorialTexts.UNTITLED }}", notice, status) {
            draftHeader(this@reviewPage, draft, schema, EditorialTexts.REVIEW_TITLE)
            dl(classes = "bo-facts") {
                dt { +EditorialTexts.COLUMN_STATUS }
                dd { +EditorialTexts.status(draft.status) }
                draft.submittedVersion?.let { version -> dt { +EditorialTexts.REVIEW_SUBMITTED_VERSION }; dd { +version.toString() } }
                dt { +EditorialTexts.REVIEW_AUTHOR }
                dd { +(names[draft.author] ?: "") }
                draft.submittedAt?.let { at -> dt { +EditorialTexts.REVIEW_SUBMITTED }; dd { dateTime(at) } }
                queued?.let { item ->
                    dt { +EditorialTexts.REVIEW_DEADLINE }
                    dd { dateTime(item.deadline) }
                    dt { +EditorialTexts.REVIEW_LEVEL }
                    dd { +("${item.level} / 5" + if (item.correction) " (${EditorialTexts.QUEUE_CORRECTION})" else "") }
                }
            }
            submitted?.content?.changeKind?.takeIf { draft.baseEventId != null }?.let { kind ->
                p { +"${EditorialTexts.CHANGE_LEGEND} : ${if (kind == ChangeKind.CORRECTION) EditorialTexts.CHANGE_CORRECTION else EditorialTexts.CHANGE_UPDATE}" }
            }
            submitted?.content?.changeNote?.let { note -> p { +"${EditorialTexts.CHANGE_NOTE} : $note" } }
            if (problems.isNotEmpty()) {
                div(classes = "bo-notice bo-notice-error") {
                    attributes["role"] = "alert"
                    p { +EditorialTexts.format(EditorialTexts.REVIEW_PROBLEMS, problems.size) }
                    ul { problems.forEach { li { +EditorialTexts.problem(schema, it) } } }
                }
            }
            section {
                attributes["aria-labelledby"] = "historique"
                h2 { id = "historique"; +EditorialTexts.REVIEW_HISTORY }
                if (history.isEmpty()) {
                    p { +EditorialTexts.REVIEW_HISTORY_EMPTY }
                } else {
                    ul(classes = "bo-list") {
                        history.forEach { record ->
                            li {
                                p {
                                    +"${EditorialTexts.VERSION} ${record.version} : ${EditorialTexts.status(record.decision.status)}, ${EditorialTexts.REVIEW_BY} ${names[record.reviewer] ?: ""}, "
                                    dateTime(record.decidedAt)
                                }
                                record.reason?.lines()?.forEach { line -> p { +line } }
                            }
                        }
                    }
                }
            }
            signing?.let { signerBlock(it, mayApprove) }
            if (draft.status in EditorialReviews.SIGNABLE && submitted != null && signing !is PublisherAnswer.Published) signingActions(this@reviewPage, draft, submitted.version, signing, writer)
            section {
                attributes["aria-labelledby"] = "decision"
                h2 { id = "decision"; +EditorialTexts.REVIEW_ACTIONS }
                when {
                    draft.status != DraftStatus.IN_REVIEW -> p { +EditorialTexts.REVIEW_NOT_IN_REVIEW }
                    writer -> p(classes = "bo-notice") { +EditorialTexts.REVIEW_YOU_WROTE }
                    !actor.has(EditorialQueue.reviewPermission(space)) -> p { +EditorialTexts.REVIEW_NO_PERMISSION }
                    else -> {
                        val version = requireNotNull(draft.submittedVersion)
                        val context = this@reviewPage
                        decisionForm(context, draft, version, ACTION_REQUEST_CHANGES, EditorialTexts.REQUEST_CHANGES, EditorialTexts.REVIEW_MESSAGE_LABEL, sensitive = false)
                        decisionForm(context, draft, version, ACTION_REJECT, EditorialTexts.REJECT, EditorialTexts.REVIEW_REASON_LABEL, sensitive = true)
                        if (problems.isEmpty() && actor.has(EditorialQueue.publishPermission(space))) {
                            decisionForm(context, draft, version, ACTION_APPROVE, EditorialTexts.APPROVE, null, sensitive = true)
                        }
                    }
                }
            }
            if (submitted != null) {
                section(classes = "bo-preview") {
                    attributes["aria-labelledby"] = "contenu-soumis"
                    h2 { id = "contenu-soumis"; +"${EditorialTexts.REVIEW_CONTENT} (${EditorialTexts.VERSION.lowercase()} ${submitted.version})" }
                    p { +"${EditorialTexts.common(ContentSchema.ORIGIN)} : ${originText(submitted.content.origin)}" }
                    preview(schema, submitted.content)
                }
            }
        }
    }

    /** One decision form; [reasonLabel] null for an approval (no text). */
    private fun FlowContent.decisionForm(context: SectionContext, draft: EditorialDraft, version: Int, action: String, button: String, reasonLabel: String?, sensitive: Boolean) {
        form(action = context.path("relecture/${draft.id}"), method = FormMethod.post, classes = "bo-action") {
            context.csrfInput(this)
            input(type = InputType.hidden, name = "decision") { value = action }
            input(type = InputType.hidden, name = "version") { value = version.toString() }
            if (reasonLabel != null) {
                val fieldId = "motif-$action"
                label {
                    htmlFor = fieldId
                    +reasonLabel
                }
                textArea(rows = "4") {
                    id = fieldId
                    name = "motif"
                    required = true
                    maxLength = EditorialReviews.MAX_REASON.toString()
                    attributes["aria-describedby"] = "$fieldId-aide"
                }
                p(classes = "bo-hint") {
                    id = "$fieldId-aide"
                    +EditorialTexts.format(EditorialTexts.REVIEW_TEXT_HINT, EditorialTexts.number(EditorialReviews.MAX_REASON))
                }
            } else {
                p(classes = "bo-hint") { +EditorialTexts.APPROVE_HINT }
            }
            if (sensitive) p(classes = "bo-hint") { +EditorialTexts.SENSITIVE_HINT }
            button(type = ButtonType.submit, classes = "bo-button") { +button }
        }
    }

    private suspend fun SectionContext.postSubmit(draft: EditorialDraft, schema: ContentSchema, form: Parameters) {
        val version = form["version"]?.toIntOrNull() ?: 0
        val latest = requireNotNull(drafts.version(draft.id, draft.latestVersion))
        if (!draft.status.editable) {
            return readOnlyPage(draft, schema, latest, Notice(EditorialTexts.format(EditorialTexts.WRONG_STATE, EditorialTexts.status(draft.status).lowercase()), error = true), HttpStatusCode.Conflict)
        }
        // The rules of the engine, again, on the version saved: a version with problems never leaves for review.
        val problems = check(schema, latest.content, draft.baseEventId != null)
        if (problems.isNotEmpty()) {
            return formPage(
                schema, draft, FormState(latest.content), draft.latestVersion, problems,
                Notice(EditorialTexts.format(EditorialTexts.SUBMIT_REFUSED_PROBLEMS, problems.size), error = true), HttpStatusCode.UnprocessableEntity,
            )
        }
        val result = tracked(ACTION_SUBMIT, draft, version) { reviews.submit(draft.id, version, actor.accountId) } ?: return
        when (result) {
            is ReviewResult.Done -> redirect("brouillons/${draft.id}", "ok=submitted")
            ReviewResult.NotFound -> notFound()
            else -> formPage(schema, draft, FormState(latest.content), draft.latestVersion, problems, Notice(refusalText(result), error = true), statusOf(result))
        }
    }

    private suspend fun SectionContext.postDecision(draft: EditorialDraft, schema: ContentSchema, form: Parameters) {
        val action = form["decision"] ?: return notFound()
        if (action == ACTION_RETRY || action == ACTION_WITHDRAW) return postSigningAction(draft, schema, action, form)
        val decision = when (action) {
            ACTION_REQUEST_CHANGES -> ReviewDecision.CHANGES_REQUESTED
            ACTION_REJECT -> ReviewDecision.REJECTED
            ACTION_APPROVE -> ReviewDecision.READY_TO_SIGN
            else -> return notFound()
        }
        val permission = if (decision == ReviewDecision.READY_TO_SIGN) EditorialQueue.publishPermission(space) else EditorialQueue.reviewPermission(space)
        if (!actor.has(permission)) return forbidden()
        // A refusal and an approval are sensitive: the passkey again after 5 minutes (the base's confirmation page).
        if (decision != ReviewDecision.CHANGES_REQUESTED && !requireFresh()) return
        val version = form["version"]?.toIntOrNull() ?: 0
        val reason = form["motif"]?.trim()?.takeIf { it.isNotEmpty() }
        if (decision.needsReason && (reason == null || reason.length > EditorialReviews.MAX_REASON)) {
            return reviewPage(draft, schema, Notice(EditorialTexts.format(EditorialTexts.REASON_REQUIRED, EditorialTexts.number(EditorialReviews.MAX_REASON)), error = true), HttpStatusCode.UnprocessableEntity)
        }
        if (decision == ReviewDecision.READY_TO_SIGN) {
            // The rules may have changed since the submission: a version with problems is never approved.
            val submitted = draft.submittedVersion?.takeIf { it == version }?.let { drafts.version(draft.id, it) }
            val problems = submitted?.let { check(schema, it.content, draft.baseEventId != null) }.orEmpty()
            if (problems.isNotEmpty()) {
                return reviewPage(draft, schema, Notice(EditorialTexts.format(EditorialTexts.REVIEW_PROBLEMS, problems.size), error = true), HttpStatusCode.UnprocessableEntity)
            }
        }
        val result = tracked(action, draft, version) { reviews.decide(draft.id, version, decision, actor.accountId, reason) } ?: return
        val decided = (result as? ReviewResult.Done)?.draft
        when {
            decided == null && result == ReviewResult.NotFound -> notFound()
            decided == null -> reviewPage(drafts.find(draft.id) ?: draft, schema, Notice(refusalText(result), error = true), statusOf(result))
            decision == ReviewDecision.READY_TO_SIGN -> {
                val answer = signerAnswer(decided, version) { publisher.handOver(it) }
                reviewPage(decided, schema, Notice(EditorialTexts.DONE_READY_TO_SIGN), signer = answer)
            }
            else -> redirect("relecture/${draft.id}", "ok=${decision.db}")
        }
    }

    /** `retry_signing` and `withdraw` (step 5): sensitive, publish permission, never a writer of the draft. */
    private suspend fun SectionContext.postSigningAction(draft: EditorialDraft, schema: ContentSchema, action: String, form: Parameters) {
        if (!actor.has(EditorialQueue.publishPermission(space))) return forbidden()
        if (!requireFresh()) return
        val version = form["version"]?.toIntOrNull() ?: 0
        if (action == ACTION_WITHDRAW) {
            val reason = form["motif"]?.trim()?.takeIf { it.isNotEmpty() }
            if (reason == null || reason.length > EditorialReviews.MAX_REASON) {
                return reviewPage(draft, schema, Notice(EditorialTexts.format(EditorialTexts.REASON_REQUIRED, EditorialTexts.number(EditorialReviews.MAX_REASON)), error = true), HttpStatusCode.UnprocessableEntity)
            }
            val result = tracked(action, draft, version) { reviews.withdraw(draft.id, version, actor.accountId, reason) } ?: return
            return when (result) {
                is ReviewResult.Done -> redirect("relecture/${draft.id}", "ok=withdrawn")
                ReviewResult.NotFound -> notFound()
                else -> reviewPage(drafts.find(draft.id) ?: draft, schema, Notice(refusalText(result), error = true), statusOf(result))
            }
        }
        val result = tracked(action, draft, version) { reviews.checkRetry(draft.id, version, actor.accountId) } ?: return
        val allowed = (result as? ReviewResult.Done)?.draft
        when {
            allowed == null && result == ReviewResult.NotFound -> notFound()
            allowed == null -> reviewPage(drafts.find(draft.id) ?: draft, schema, Notice(refusalText(result), error = true), statusOf(result))
            else -> {
                val answer = signerAnswer(allowed, version) { publisher.retry(it) }
                reviewPage(drafts.find(draft.id) ?: allowed, schema, Notice(EditorialTexts.DONE_RETRIED), signer = answer)
            }
        }
    }

    /**
     * Asks the signing seam about version [version] of an approved draft ([call]: hand over after the approval,
     * or the state when the review page is opened). A failure never undoes the approval: the version stays
     * ready to sign and the page says the service is unavailable.
     */
    private suspend fun signerAnswer(draft: EditorialDraft, version: Int, call: suspend (ReadyToSign) -> PublisherAnswer): PublisherAnswer {
        val approval = reviews.decisions(draft.id).firstOrNull { it.version == version && it.decision == ReviewDecision.READY_TO_SIGN }
            ?: return PublisherAnswer.Refused("not_approved")
        val content = drafts.version(draft.id, version)?.content
        val request = ReadyToSign(
            space = space, draftId = draft.id, version = version, type = draft.type, slug = draft.slug, baseEventId = draft.baseEventId,
            author = draft.author, authorPubkey = reviews.nostrKeyOf(draft.author), approvedBy = approval.reviewer,
            correction = draft.baseEventId != null && content?.changeKind == ChangeKind.CORRECTION,
        )
        return try {
            call(request)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("Editorial review: the signing seam failed (${e::class.qualifiedName}).")
            PublisherAnswer.Refused("signer_unavailable")
        }
    }

    /** What the review page says of the signature; the approval link only for who may approve (publish permission, not a writer). */
    private fun FlowContent.signerBlock(answer: PublisherAnswer, mayApprove: Boolean) {
        section {
            attributes["aria-labelledby"] = "signature"
            h2 { id = "signature"; +EditorialTexts.SIGNER_TITLE }
            when (answer) {
                PublisherAnswer.NotConnected -> p { +EditorialTexts.SIGNER_NOT_PLUGGED }
                PublisherAnswer.KeyUnavailable -> p { +EditorialTexts.SIGNER_UNAVAILABLE }
                is PublisherAnswer.Refused -> div(classes = "bo-notice bo-notice-error") {
                    p { +EditorialTexts.format(EditorialTexts.SIGNER_REFUSED, answer.code) }
                    p { +EditorialTexts.SIGNER_REFUSED_HINT }
                }
                is PublisherAnswer.Published -> p(classes = "bo-notice") {
                    attributes["role"] = "status"
                    +EditorialTexts.SIGNER_PUBLISHED
                }
                PublisherAnswer.SignaturePending -> p { +EditorialTexts.SIGNER_PENDING }
                PublisherAnswer.Unavailable -> p(classes = "bo-notice bo-notice-warning") { +EditorialTexts.SIGNER_DOWN }
                PublisherAnswer.Expired -> p(classes = "bo-notice bo-notice-warning") { +EditorialTexts.SIGNER_EXPIRED }
                is PublisherAnswer.AwaitingApproval -> {
                    p { +EditorialTexts.format(EditorialTexts.SIGNER_AWAITING, answer.approvals, answer.required) }
                    p { +if (answer.required >= EditorialApprovals.M17) EditorialTexts.SIGNER_AWAITING_TWO else EditorialTexts.SIGNER_AWAITING_ONE }
                    // A plain link to the signing service's own page: no ceremony on this server.
                    if (mayApprove && isSignerLink(answer.approvalUrl)) p { a(href = answer.approvalUrl) { +EditorialTexts.SIGNER_LINK } }
                }
            }
        }
    }

    /**
     * The actions of an approved version on its way to the signature (step 5), for the members who may approve
     * it (publish permission, never a writer): retry after an expiry or a refusal (a new event), and withdraw
     * (back to draft, with a reason kept for the writers).
     */
    private fun FlowContent.signingActions(context: SectionContext, draft: EditorialDraft, version: Int, answer: PublisherAnswer?, writer: Boolean) {
        if (!context.actor.has(EditorialQueue.publishPermission(space))) return
        section {
            attributes["aria-labelledby"] = "signature-actions"
            h2 { id = "signature-actions"; +EditorialTexts.SIGNING_ACTIONS }
            if (writer) {
                p(classes = "bo-notice") { +EditorialTexts.SIGNING_YOU_WROTE }
                return@section
            }
            val retryable = draft.status == DraftStatus.SIGNING_REFUSED || answer == PublisherAnswer.Expired || answer is PublisherAnswer.Refused
            if (retryable) {
                form(action = context.path("relecture/${draft.id}"), method = FormMethod.post, classes = "bo-action") {
                    context.csrfInput(this)
                    input(type = InputType.hidden, name = "decision") { value = ACTION_RETRY }
                    input(type = InputType.hidden, name = "version") { value = version.toString() }
                    p(classes = "bo-hint") { +EditorialTexts.RETRY_HINT }
                    p(classes = "bo-hint") { +EditorialTexts.SENSITIVE_HINT }
                    button(type = ButtonType.submit, classes = "bo-button") { +EditorialTexts.RETRY }
                }
            }
            form(action = context.path("relecture/${draft.id}"), method = FormMethod.post, classes = "bo-action") {
                context.csrfInput(this)
                input(type = InputType.hidden, name = "decision") { value = ACTION_WITHDRAW }
                input(type = InputType.hidden, name = "version") { value = version.toString() }
                label {
                    htmlFor = "motif-withdraw"
                    +EditorialTexts.WITHDRAW_REASON_LABEL
                }
                textArea(rows = "3") {
                    id = "motif-withdraw"
                    name = "motif"
                    required = true
                    maxLength = EditorialReviews.MAX_REASON.toString()
                    attributes["aria-describedby"] = "motif-withdraw-aide"
                }
                p(classes = "bo-hint") {
                    id = "motif-withdraw-aide"
                    +EditorialTexts.WITHDRAW_HINT
                }
                p(classes = "bo-hint") { +EditorialTexts.SENSITIVE_HINT }
                button(type = ButtonType.submit, classes = "bo-button") { +EditorialTexts.WITHDRAW }
            }
        }
    }

    /**
     * Runs [block] between two journal lines written by the base: `<action>_requested` before (when it cannot
     * be written, nothing is done) and `<action>` with its outcome after. Identifiers and codes only: never the
     * message nor the reason, which stay in `editorial_review`. Returns null when an error page was sent.
     */
    private suspend fun SectionContext.tracked(action: String, draft: EditorialDraft, version: Int, block: () -> ReviewResult): ReviewResult? {
        val before = mapOf("version" to version.toString(), "status" to draft.status.db)
        if (!journalLine("${action}_requested", draft, before)) {
            respondPage(EditorialTexts.JOURNAL_FAILED_TITLE, Notice(EditorialTexts.JOURNAL_FAILED_BEFORE, error = true), HttpStatusCode.InternalServerError) {
                h1 { +EditorialTexts.JOURNAL_FAILED_TITLE }
                p { a(href = path("brouillons/${draft.id}")) { +EditorialTexts.LINK_FORM } }
            }
            return null
        }
        val result = block()
        val after = buildMap {
            put("version", version.toString())
            put("outcome", result.code)
            if (result is ReviewResult.Done) put("status", result.draft.status.db)
            if (result is ReviewResult.WrongState) put("status", result.status.db)
        }
        if (!journalLine(action, draft, after)) {
            respondPage(EditorialTexts.JOURNAL_FAILED_TITLE, Notice(EditorialTexts.JOURNAL_FAILED_DECISION, error = true), HttpStatusCode.InternalServerError) {
                h1 { +EditorialTexts.JOURNAL_FAILED_TITLE }
                p { a(href = path("brouillons/${draft.id}")) { +EditorialTexts.LINK_FORM } }
            }
            return null
        }
        return result
    }

    private fun SectionContext.journalLine(action: String, draft: EditorialDraft, details: Map<String, String>): Boolean = try {
        audit(action, draft.id.toString(), reason = null, details = details)
        true
    } catch (e: Exception) {
        log.error("Editorial review: a journal line could not be written (${e::class.qualifiedName}).")
        false
    }

    private fun refusalText(result: ReviewResult): String = when (result) {
        is ReviewResult.WrongState -> EditorialTexts.format(EditorialTexts.WRONG_STATE, EditorialTexts.status(result.status).lowercase())
        ReviewResult.StaleVersion -> EditorialTexts.STALE_VERSION
        ReviewResult.AlreadyReviewed -> EditorialTexts.SUBMIT_REFUSED_ALREADY_REVIEWED
        ReviewResult.Writer -> EditorialTexts.REVIEW_REFUSED_WRITER
        ReviewResult.NotWriter -> EditorialTexts.SUBMIT_REFUSED_NOT_WRITER
        ReviewResult.ReasonRequired -> EditorialTexts.format(EditorialTexts.REASON_REQUIRED, EditorialTexts.number(EditorialReviews.MAX_REASON))
        ReviewResult.NotFound, is ReviewResult.Done -> EditorialTexts.STATE_FINISHED
    }

    private fun statusOf(result: ReviewResult): HttpStatusCode = when (result) {
        ReviewResult.Writer, ReviewResult.NotWriter -> HttpStatusCode.Forbidden
        ReviewResult.ReasonRequired -> HttpStatusCode.UnprocessableEntity
        else -> HttpStatusCode.Conflict
    }

    // Helpers ---------------------------------------------------------------------------------------

    private fun draftOf(id: String?): Pair<EditorialDraft, ContentSchema>? {
        val uuid = id?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
        val draft = drafts.find(uuid)?.takeIf { it.space == space } ?: return null
        return draft to (schemaOf(draft.type) ?: return null)
    }

    /**
     * Every problem of [content], against the published contents and the other open drafts; for an update of a
     * published content ([isUpdate]), the nature of the change and its public summary too ([UpdateChecks]).
     */
    fun check(schema: ContentSchema, content: DraftContent, isUpdate: Boolean = false): List<Problem> {
        val context = published.snapshot().context(space, drafts.openSlugs())
        return EditorialEngine.check(schema, content.draft(schema.type), context) + UpdateChecks.problems(content, isUpdate)
    }

    /**
     * The journal line of a save, written right after the version, in the same handler. When the base
     * cannot write it, the member gets an error page, never a success page nor a redirect, so that the
     * gap is seen (the version stays saved: never lose the work). Returns whether the line was written.
     */
    private suspend fun SectionContext.journal(action: String, draft: EditorialDraft, version: Int, details: Map<String, String>): Boolean {
        val written = try {
            audit(action, draft.id.toString(), details = details)
            true
        } catch (e: Exception) {
            // Only the class: a database message could quote the content.
            log.error("Editorial drafts: the journal line of a save could not be written (${e::class.qualifiedName}).")
            false
        }
        if (!written) {
            respondPage(EditorialTexts.JOURNAL_FAILED_TITLE, Notice(EditorialTexts.format(EditorialTexts.JOURNAL_FAILED, version), error = true), HttpStatusCode.InternalServerError) {
                h1 { +EditorialTexts.JOURNAL_FAILED_TITLE }
                p { a(href = path("brouillons/${draft.id}")) { +EditorialTexts.LINK_FORM } }
            }
        }
        return written
    }

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

        /** An approval page of the signing service: https, or a local address in development; never a script nor a relative path. */
        fun isSignerLink(url: String): Boolean =
            url.length <= 500 && url.none { it.isWhitespace() || it.isISOControl() || it == '"' || it == '<' || it == '>' } &&
                (url.startsWith("https://") || Regex("^http://(localhost|127\\.0\\.0\\.1)(:[0-9]{1,5})?/").containsMatchIn(url))

        /** The actions of the review, as the forms post them and the journal names them (`section.editorial.<space>.<action>`). */
        const val ACTION_SUBMIT: String = "submit"
        const val ACTION_REQUEST_CHANGES: String = "request_changes"
        const val ACTION_REJECT: String = "reject"
        const val ACTION_APPROVE: String = "approve"
        const val ACTION_RETRY: String = "retry_signing"
        const val ACTION_WITHDRAW: String = "withdraw"

        /** The states whose review page asks the signing seam where the version stands. */
        private val SIGNING_STATES: Set<DraftStatus> = setOf(DraftStatus.READY_TO_SIGN, DraftStatus.SIGNING_REFUSED, DraftStatus.PUBLISHED)
        private val log = org.slf4j.LoggerFactory.getLogger(EditorialDraftsSection::class.java)
    }
}
