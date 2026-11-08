package org.politisationpopulairefrancaise.server.contact

import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.caption
import kotlinx.html.dd
import kotlinx.html.div
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.textArea
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import org.politisationpopulairefrancaise.core.contact.CaseDeadlines
import org.politisationpopulairefrancaise.core.contact.CaseNature
import org.politisationpopulairefrancaise.core.contact.CaseNumber
import org.politisationpopulairefrancaise.core.contact.CaseStatus
import org.politisationpopulairefrancaise.core.contact.CaseType
import org.politisationpopulairefrancaise.core.contact.ClaimMotif
import org.politisationpopulairefrancaise.core.contact.ContactCategory
import org.politisationpopulairefrancaise.core.contact.DeadlineKind
import org.politisationpopulairefrancaise.core.contact.TicketSubtype
import org.politisationpopulairefrancaise.server.backoffice.AccountStatus
import org.politisationpopulairefrancaise.server.backoffice.BackOffice
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeSection
import org.politisationpopulairefrancaise.server.backoffice.Notice
import org.politisationpopulairefrancaise.server.backoffice.Permissions
import org.politisationpopulairefrancaise.server.backoffice.SectionContext
import org.politisationpopulairefrancaise.server.backoffice.SectionRoutes
import org.politisationpopulairefrancaise.server.backoffice.TeamActor
import org.politisationpopulairefrancaise.server.backoffice.dateTime
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.time.Duration.Companion.days

/**
 * The inbox of the team for the cases (M30, S-107; docs/integration/contact-reclamations.md, section 6): a
 * section of the back-office (`contact`, « Échanges », contract of the back-office, section 14). Metadata
 * only: the server holds no text of a case, which the team reads in its own client (step 3).
 *
 * The inbox is sorted by deadline, then by reception, with filters and marks (« échéance dans 2 jours »,
 * « dépassée »); a case page gives the actions of the contract: assign, priority, status, extension of a
 * request on one's data, author of a contested decision, member concerned, legal hold with two people. Every
 * change writes one line of the journal through the base (number of the case, identifiers and codes, never a
 * text of the case); the sensitive ones ask the passkey again ([SectionContext.requireFresh]). A case the
 * member may not see ([ContactRules.canSee]) answers as an unknown one.
 */
class ContactSection(
    private val space: ContactSpace,
    private val backOffice: BackOffice,
    private val clock: Clock = Clock.systemUTC(),
) : BackOfficeSection {
    override val id: String = ID
    override val title: String = ContactTexts.SECTION_TITLE
    override val permission: String = Permissions.CONTACT_TRIAGE

    private val store: ContactStore get() = space.store()
    private val team = ContactTeam(space.store(), clock)

    override fun routes(routes: SectionRoutes) {
        routes.get("") { inbox() }
        routes.get("dossiers/{number}") {
            val case = visibleCase(pathParameter("number")) ?: return@get notFound()
            casePage(case)
        }
        routes.post("dossiers/{number}/assignation") { form ->
            val case = visibleCase(pathParameter("number")) ?: return@post notFound()
            val (target, targetActor) = when (val choice = form["membre"].orEmpty()) {
                "none" -> null to null
                "me" -> actor.accountId to actor
                else -> {
                    val id = runCatching { UUID.fromString(choice) }.getOrNull() ?: return@post back(case, error = "invalid")
                    id to actorOf(id)
                }
            }
            conclude(case, "assigned", team.assign(actor, case.number, target, targetActor), "assigned")
        }
        routes.post("dossiers/{number}/priorite") { form ->
            val case = visibleCase(pathParameter("number")) ?: return@post notFound()
            val priority = CasePriority.of(form["priorite"]) ?: return@post back(case, error = "invalid")
            conclude(case, "priority_changed", team.setPriority(actor, case.number, priority), "priority")
        }
        routes.post("dossiers/{number}/statut") { form ->
            val case = visibleCase(pathParameter("number")) ?: return@post notFound()
            val status = CaseStatus.fromWire(form["statut"]) ?: return@post back(case, error = "invalid")
            conclude(case, "status_changed", team.setStatus(actor, case.number, status), "status")
        }
        routes.post("dossiers/{number}/prolongation") { form ->
            val case = visibleCase(pathParameter("number")) ?: return@post notFound()
            if (!requireFresh()) return@post
            val reason = form["motif"]
            conclude(case, "extended", team.extend(actor, case.number, reason), "extended", reason)
        }
        routes.post("dossiers/{number}/auteur-decision") { form ->
            val case = visibleCase(pathParameter("number")) ?: return@post notFound()
            if (!requireFresh()) return@post
            val member = memberChoice(form) ?: return@post back(case, error = "invalid")
            conclude(case, "decision_author_set", team.setDecisionAuthor(actor, case.number, member.value), "decision_author")
        }
        routes.post("dossiers/{number}/mis-en-cause") { form ->
            val case = visibleCase(pathParameter("number")) ?: return@post notFound()
            if (!requireFresh()) return@post
            val member = memberChoice(form) ?: return@post back(case, error = "invalid")
            conclude(case, "concerns_set", team.setConcerns(actor, case.number, member.value), "concerns")
        }
        routes.post("dossiers/{number}/archive") { form ->
            val case = visibleCase(pathParameter("number")) ?: return@post notFound()
            if (!requireFresh()) return@post
            val review = parseReviewDate(form["reexamen"])
            val reason = form["motif"]
            conclude(case, "legal_hold_requested", team.requestHold(actor, case.number, reason, review), "hold_requested", reason)
        }
        routes.post("dossiers/{number}/archive/levee") { form ->
            val case = visibleCase(pathParameter("number")) ?: return@post notFound()
            if (!requireFresh()) return@post
            val reason = form["motif"]
            conclude(case, "legal_hold_release_requested", team.requestRelease(actor, case.number, reason), "hold_requested", reason)
        }
        routes.post("dossiers/{number}/archive/accord") { _ ->
            val case = visibleCase(pathParameter("number")) ?: return@post notFound()
            if (!requireFresh()) return@post
            conclude(case, "legal_hold_confirmed", team.confirmHold(actor, case.number), "hold_confirmed")
        }
        routes.post("dossiers/{number}/archive/refus") { _ ->
            val case = visibleCase(pathParameter("number")) ?: return@post notFound()
            if (!requireFresh()) return@post
            conclude(case, "legal_hold_refused", team.dropHoldRequest(actor, case.number, own = false), "hold_refused")
        }
        routes.post("dossiers/{number}/archive/retrait") { _ ->
            val case = visibleCase(pathParameter("number")) ?: return@post notFound()
            conclude(case, "legal_hold_withdrawn", team.dropHoldRequest(actor, case.number, own = true), "hold_cancelled")
        }
    }

    // Pages -----------------------------------------------------------------------------------------

    private suspend fun SectionContext.inbox() {
        val now = clock.instant()
        val filters = InboxFilters.read(query)
        val restricted = actor.has(Permissions.CONTACT_RESTRICTED)
        val cases = store.inbox(filters.query(actor.accountId, restricted))
        val pending = store.pendingHoldNumbers()
        val names = pseudonyms()
        respondPage(ContactTexts.SECTION_TITLE) {
            h1 { +ContactTexts.SECTION_TITLE }
            p { +ContactTexts.HOME_INTRO }
            filterForm(this@inbox, filters, restricted)
            section {
                attributes["aria-labelledby"] = "boite"
                h2 { id = "boite"; +ContactTexts.INBOX_TITLE }
                if (cases.isEmpty()) {
                    p { +ContactTexts.INBOX_EMPTY }
                } else {
                    if (cases.size >= INBOX_LIMIT) p(classes = "bo-hint") { +ContactTexts.INBOX_LIMIT }
                    table(classes = "bo-table") {
                        caption { +ContactTexts.INBOX_TITLE }
                        thead {
                            tr {
                                listOf(
                                    ContactTexts.COLUMN_NUMBER, ContactTexts.COLUMN_NATURE, ContactTexts.COLUMN_STATUS, ContactTexts.COLUMN_RECEIVED,
                                    ContactTexts.COLUMN_DEADLINE, ContactTexts.COLUMN_PRIORITY, ContactTexts.COLUMN_ASSIGNED, ContactTexts.COLUMN_MARKS,
                                ).forEach { th { attributes["scope"] = "col"; +it } }
                            }
                        }
                        tbody {
                            cases.forEach { case ->
                                tr {
                                    td { a(href = this@inbox.path("dossiers/${case.number}")) { +case.number } }
                                    td { +natureLabel(case.nature) }
                                    td { +ContactTexts.status(case.status) }
                                    td { dateTime(case.receivedAt) }
                                    td { deadline(case) }
                                    td { +ContactTexts.priority(case.priority) }
                                    td { +(case.assignedTo?.let { names[it] } ?: ContactTexts.NOBODY) }
                                    td { marks(case, now, case.number in pending) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun FlowContent.filterForm(context: SectionContext, filters: InboxFilters, restricted: Boolean) {
        form(action = context.path(), method = FormMethod.get, classes = "bo-filters") {
            attributes["aria-label"] = ContactTexts.FILTERS
            div(classes = "bo-row") {
                choice("filtre-vue", "dossiers", ContactTexts.FILTER_VIEW, filters.view, listOf(
                    "" to ContactTexts.VIEW_OPEN, "tous" to ContactTexts.VIEW_ALL, "archives" to ContactTexts.VIEW_HOLDS,
                ) + CaseStatus.entries.map { it.wire to ContactTexts.status(it) })
                choice("filtre-type", "type", ContactTexts.FILTER_TYPE, filters.type?.wire.orEmpty(), listOf("" to ContactTexts.ALL) + CaseType.entries.map { it.wire to ContactTexts.type(it) })
                choice("filtre-nature", "nature", ContactTexts.FILTER_NATURE, filters.qualifier.orEmpty(), listOf("" to ContactTexts.ALL) + qualifierChoices(restricted))
                choice("filtre-assignation", "assignation", ContactTexts.FILTER_ASSIGNED, filters.assigned, listOf(
                    "" to ContactTexts.ALL, "moi" to ContactTexts.ASSIGNED_ME, "personne" to ContactTexts.ASSIGNED_NOBODY,
                ))
                choice("filtre-priorite", "priorite", ContactTexts.FILTER_PRIORITY, filters.priority?.db.orEmpty(), listOf("" to ContactTexts.ALL_FEMININE) + CasePriority.entries.map { it.db to ContactTexts.priority(it) })
            }
            button(type = ButtonType.submit, classes = "bo-button bo-button-secondary") { +ContactTexts.FILTER_BUTTON }
        }
    }

    private suspend fun SectionContext.casePage(case: StoredCase) {
        val now = clock.instant()
        val notice = query["ok"]?.let { ContactTexts.NOTICES[it] }?.let { Notice(it) }
            ?: query["erreur"]?.let { ContactTexts.ERRORS[it] }?.let { Notice(it, error = true) }
        val (incoming, outgoing) = store.envelopeCounts(case.number)
        val request = store.holdRequest(case.number)
        val members = activeMembers()
        respondPage("${ContactTexts.CASE_TITLE} ${case.number}", notice) {
            p { a(href = this@casePage.path()) { +ContactTexts.BACK } }
            h1 { +"${ContactTexts.CASE_TITLE} ${case.number}" }
            p(classes = "bo-summary") { +natureLabel(case.nature) }
            p { marks(case, now, request != null) }
            section {
                attributes["aria-labelledby"] = "suivi"
                h2 { id = "suivi"; +ContactTexts.FACTS }
                dl(classes = "bo-facts") {
                    fact(ContactTexts.FACT_STATUS) { +ContactTexts.status(case.status) }
                    fact(ContactTexts.FACT_RECEIVED) { dateTime(case.receivedAt) }
                    fact(ContactTexts.FACT_DEADLINE) { deadline(case) }
                    case.extendedAt?.let { extended -> fact(ContactTexts.FACT_EXTENDED) { dateTime(extended) } }
                    case.extensionReason?.let { reason -> fact(ContactTexts.FACT_EXTENSION_REASON) { +reason } }
                    fact(ContactTexts.FACT_PRIORITY) { +ContactTexts.priority(case.priority) }
                    fact(ContactTexts.FACT_ASSIGNED) { +(case.assignedTo?.let { this@casePage.pseudonymOf(it) } ?: ContactTexts.NOBODY) }
                    fact(ContactTexts.FACT_ACTIVITY) { dateTime(case.lastActivityAt) }
                    case.closedAt?.let { closed -> fact(ContactTexts.FACT_CLOSED) { dateTime(closed) } }
                    case.decisionAt?.let { decision -> fact(ContactTexts.FACT_DECISION) { dateTime(decision) } }
                    if (case.motif == ClaimMotif.MODERATION_DECISION) {
                        fact(ContactTexts.FACT_DECISION_AUTHOR) { +(case.decisionAuthor?.let { this@casePage.pseudonymOf(it) } ?: ContactTexts.NOT_SET) }
                    }
                    if (case.motif == ClaimMotif.TEAM_MEMBER) {
                        fact(ContactTexts.FACT_CONCERNS) { +(case.concerns?.let { this@casePage.pseudonymOf(it) } ?: ContactTexts.NOT_SET) }
                    }
                    case.legalHoldReason?.let { reason -> fact(ContactTexts.FACT_HOLD) { +reason } }
                    case.legalHoldReviewAt?.let { review -> fact(ContactTexts.FACT_HOLD_REVIEW) { dateTime(review) } }
                    case.anonymizedAt?.let { anonymized -> fact(ContactTexts.FACT_ANONYMIZED) { dateTime(anonymized) } }
                    fact(ContactTexts.FACT_ENVELOPES) { +"$incoming reçue(s), $outgoing envoyée(s)" }
                }
                p(classes = "bo-hint") { +ContactTexts.NO_TEXT_HERE }
            }
            section {
                attributes["aria-labelledby"] = "actions"
                h2 { id = "actions"; +ContactTexts.ACTIONS }
                if (case.isAnonymized) {
                    p { +ContactTexts.NO_ACTION }
                } else {
                    actions(this@casePage, case, now, request, members)
                }
            }
        }
    }

    private fun FlowContent.actions(context: SectionContext, case: StoredCase, now: Instant, request: HoldRequest?, members: List<TeamActor>) {
        val actor = context.actor
        val path = "dossiers/${case.number}"
        // Assignment: the members who could handle the case.
        val eligible = members.filter { ContactRules.canHandle(case, it) }
        actionForm(context, "$path/assignation", ContactTexts.ASSIGN_TITLE, ContactTexts.ASSIGN_BUTTON, hint = ContactTexts.ASSIGN_HINT) {
            memberSelect("assign", ContactTexts.ASSIGN_LABEL, buildList {
                if (ContactRules.canHandle(case, actor)) add("me" to ContactTexts.ASSIGN_ME)
                add("none" to ContactTexts.NOBODY)
                eligible.filter { it.accountId != actor.accountId }.forEach { add(it.accountId.toString() to it.pseudonym) }
            }, current = when (case.assignedTo) {
                null -> "none"
                actor.accountId -> "me"
                else -> case.assignedTo.toString()
            })
        }
        actionForm(context, "$path/priorite", ContactTexts.PRIORITY_TITLE, ContactTexts.PRIORITY_BUTTON) {
            memberSelect("priorite", ContactTexts.PRIORITY_TITLE, CasePriority.entries.map { it.db to ContactTexts.priority(it) }, case.priority.db, name = "priorite")
        }
        if (ContactRules.canHandle(case, actor)) {
            actionForm(context, "$path/statut", ContactTexts.STATUS_TITLE, ContactTexts.STATUS_BUTTON, hint = ContactTexts.STATUS_HINT) {
                memberSelect("statut", ContactTexts.STATUS_TITLE, CaseStatus.entries.map { it.wire to ContactTexts.status(it) }, case.status.wire, name = "statut")
            }
        }
        if (case.motif == ClaimMotif.DATA_RIGHTS && actor.has(Permissions.CONTACT_DATA_RIGHTS)) {
            if (CaseDeadlines.canExtend(case.nature, case.receivedAt.epochSecond, now.epochSecond, case.extendedAt != null) && !case.isClosed) {
                actionForm(context, "$path/prolongation", ContactTexts.EXTEND_TITLE, ContactTexts.EXTEND_BUTTON, hint = ContactTexts.EXTEND_HINT, sensitive = true) {
                    reasonField("prolongation", ContactTexts.EXTEND_REASON)
                }
            } else {
                section {
                    h2 { +ContactTexts.EXTEND_TITLE }
                    p { +ContactTexts.EXTEND_CLOSED }
                }
            }
        }
        if (case.motif == ClaimMotif.MODERATION_DECISION && case.decisionAuthor != actor.accountId) {
            actionForm(context, "$path/auteur-decision", ContactTexts.DECISION_AUTHOR_TITLE, ContactTexts.DECISION_AUTHOR_BUTTON, hint = ContactTexts.DECISION_AUTHOR_HINT, sensitive = true) {
                memberSelect("auteur", ContactTexts.DECISION_AUTHOR_TITLE, listOf("none" to ContactTexts.NOT_SET) + members.map { it.accountId.toString() to it.pseudonym },
                    case.decisionAuthor?.toString() ?: "none")
            }
        }
        if (case.motif == ClaimMotif.TEAM_MEMBER && actor.has(Permissions.CONTACT_RESTRICTED)) {
            actionForm(context, "$path/mis-en-cause", ContactTexts.CONCERNS_TITLE, ContactTexts.CONCERNS_BUTTON, hint = ContactTexts.CONCERNS_HINT, sensitive = true) {
                memberSelect("concerne", ContactTexts.CONCERNS_TITLE, listOf("none" to ContactTexts.NOT_SET) + members.filter { it.accountId != actor.accountId }.map { it.accountId.toString() to it.pseudonym },
                    case.concerns?.toString() ?: "none")
            }
        }
        if (actor.has(Permissions.CONTACT_LEGAL_HOLD)) holdActions(context, case, now, request)
    }

    private fun FlowContent.holdActions(context: SectionContext, case: StoredCase, now: Instant, request: HoldRequest?) {
        val path = "dossiers/${case.number}/archive"
        section {
            attributes["aria-labelledby"] = "archive"
            h2 { id = "archive"; +ContactTexts.HOLD_TITLE }
            p(classes = "bo-hint") { +ContactTexts.HOLD_INTRO }
            if (request != null) {
                h2 { +ContactTexts.HOLD_PENDING }
                dl(classes = "bo-facts") {
                    fact(ContactTexts.FACT_HOLD) { +(if (request.release) ContactTexts.HOLD_PENDING_RELEASE else ContactTexts.HOLD_PENDING_HOLD) }
                    fact(ContactTexts.HOLD_REASON) { +request.reason }
                    request.reviewAt?.let { review -> fact(ContactTexts.FACT_HOLD_REVIEW) { dateTime(review) } }
                    fact(ContactTexts.HOLD_REQUESTED_BY) { +(context.pseudonymOf(request.requestedBy).orEmpty()) }
                    fact(ContactTexts.HOLD_REQUESTED_AT) { dateTime(request.requestedAt) }
                }
                if (request.requestedBy == context.actor.accountId) {
                    p { +ContactTexts.HOLD_OWN_REQUEST }
                    postButton(context, "$path/retrait", ContactTexts.HOLD_CANCEL_BUTTON, secondary = true)
                } else {
                    p(classes = "bo-hint") { +ContactTexts.SENSITIVE_HINT }
                    postButton(context, "$path/accord", ContactTexts.HOLD_CONFIRM_BUTTON)
                    postButton(context, "$path/refus", ContactTexts.HOLD_REFUSE_BUTTON, secondary = true)
                }
                return@section
            }
            val today = LocalDate.ofInstant(now, PARIS)
            form(action = context.path(path), method = FormMethod.post) {
                context.csrfInput(this)
                reasonField("archive", ContactTexts.HOLD_REASON)
                div(classes = "bo-field") {
                    label { htmlFor = "archive-reexamen"; +ContactTexts.HOLD_REVIEW }
                    input(type = InputType.date, name = "reexamen") {
                        id = "archive-reexamen"
                        required = true
                        min = today.plusDays(1).toString()
                        max = today.plusYears(1).minusDays(1).toString()
                    }
                }
                p(classes = "bo-hint") { +ContactTexts.SENSITIVE_HINT }
                button(type = ButtonType.submit, classes = "bo-button") { +(if (case.hasLegalHold) ContactTexts.HOLD_RENEW_BUTTON else ContactTexts.HOLD_REQUEST_BUTTON) }
            }
            if (case.hasLegalHold) {
                form(action = context.path("$path/levee"), method = FormMethod.post) {
                    context.csrfInput(this)
                    reasonField("levee", ContactTexts.HOLD_RELEASE_REASON)
                    button(type = ButtonType.submit, classes = "bo-button bo-button-secondary") { +ContactTexts.HOLD_RELEASE_BUTTON }
                }
            }
        }
    }

    // Components ------------------------------------------------------------------------------------

    private fun FlowContent.actionForm(
        context: SectionContext,
        sub: String,
        heading: String,
        submit: String,
        hint: String? = null,
        sensitive: Boolean = false,
        fields: FlowContent.() -> Unit,
    ) {
        section {
            h2 { +heading }
            form(action = context.path(sub), method = FormMethod.post) {
                context.csrfInput(this)
                fields()
                hint?.let { p(classes = "bo-hint") { +it } }
                if (sensitive) p(classes = "bo-hint") { +ContactTexts.SENSITIVE_HINT }
                button(type = ButtonType.submit, classes = "bo-button") { +submit }
            }
        }
    }

    private fun FlowContent.postButton(context: SectionContext, sub: String, label: String, secondary: Boolean = false) {
        form(action = context.path(sub), method = FormMethod.post, classes = "bo-inline") {
            context.csrfInput(this)
            button(type = ButtonType.submit, classes = if (secondary) "bo-button bo-button-secondary" else "bo-button") { +label }
        }
    }

    private fun FlowContent.memberSelect(fieldId: String, label: String, choices: List<Pair<String, String>>, current: String, name: String = "membre") {
        div(classes = "bo-field") {
            label { htmlFor = "champ-$fieldId"; +label }
            select {
                id = "champ-$fieldId"
                this.name = name
                choices.forEach { (value, text) ->
                    option {
                        this.value = value
                        if (value == current) selected = true
                        +text
                    }
                }
            }
        }
    }

    private fun FlowContent.choice(fieldId: String, name: String, label: String, current: String, choices: List<Pair<String, String>>) {
        div(classes = "bo-field") {
            label { htmlFor = fieldId; +label }
            select {
                id = fieldId
                this.name = name
                choices.forEach { (value, text) ->
                    option {
                        this.value = value
                        if (value == current) selected = true
                        +text
                    }
                }
            }
        }
    }

    private fun FlowContent.reasonField(prefix: String, label: String) {
        div(classes = "bo-field") {
            label { htmlFor = "$prefix-motif"; +label }
            textArea(rows = "3") {
                id = "$prefix-motif"
                name = "motif"
                required = true
                maxLength = ContactTeam.MAX_REASON.toString()
                attributes["aria-describedby"] = "$prefix-motif-aide"
            }
            p(classes = "bo-hint") {
                id = "$prefix-motif-aide"
                +ContactTexts.REASON_HINT
            }
        }
    }

    private fun kotlinx.html.DL.fact(term: String, value: FlowContent.() -> Unit) {
        dt { +term }
        dd { value() }
    }

    private fun FlowContent.deadline(case: StoredCase) {
        val at = case.deadlineAt
        val kind = case.deadlineKind
        if (at == null || kind == null) {
            +ContactTexts.NO_DEADLINE
            return
        }
        dateTime(at)
        +" (${ContactTexts.deadlineKind(kind)})"
    }

    private fun FlowContent.marks(case: StoredCase, now: Instant, pendingHold: Boolean) {
        markList(case, now, pendingHold).forEach { (text, style) ->
            span(classes = "bo-badge" + style.orEmpty()) { +text }
            +" "
        }
    }

    // Helpers ---------------------------------------------------------------------------------------

    /** The case of [number] if it exists and [SectionContext.actor] may see it; null otherwise (the same « not found »). */
    private fun SectionContext.visibleCase(number: String?): StoredCase? {
        val parsed = number?.let(CaseNumber::fromDisplay) ?: return null
        return store.find(parsed.display)?.takeIf { ContactRules.canSee(it, actor) }
    }

    /**
     * Writes the journal line of a change done, then goes back to the case; a refusal goes back with its code.
     * When the base cannot write the line, the member gets an error page (the change stays: it is seen).
     */
    private suspend fun SectionContext.conclude(case: StoredCase, action: String, outcome: TeamOutcome, okCode: String, reason: String? = null) {
        when (outcome) {
            is TeamOutcome.Refused -> if (outcome.code == "not_found") notFound() else back(case, error = outcome.code)
            is TeamOutcome.Done -> {
                val details = mapOf("type" to case.type.wire, "nature" to case.nature.qualifier) + outcome.details
                val written = try {
                    audit(action, case.number, reason?.trim()?.takeIf { it.isNotEmpty() }, details)
                    true
                } catch (e: Exception) {
                    // Only the class: a database message could quote a row.
                    log.error("Contact: the journal line of an action could not be written (${e::class.qualifiedName}).")
                    false
                }
                if (!written) {
                    return respondPage(ContactTexts.JOURNAL_FAILED_TITLE, Notice(ContactTexts.JOURNAL_FAILED, error = true), HttpStatusCode.InternalServerError) {
                        h1 { +ContactTexts.JOURNAL_FAILED_TITLE }
                        p { a(href = path("dossiers/${case.number}")) { +"${ContactTexts.CASE_TITLE} ${case.number}" } }
                    }
                }
                back(case, ok = okCode)
            }
        }
    }

    private suspend fun SectionContext.back(case: StoredCase, ok: String? = null, error: String? = null) =
        redirect("dossiers/${case.number}", ok?.let { "ok=$it" } ?: error?.let { "erreur=$it" })

    /** A member chosen in a form: an account, or nobody (`none`); null when the value is not one of these. */
    private fun memberChoice(form: Parameters): Choice? = when (val value = form["membre"].orEmpty()) {
        "none" -> Choice(null)
        else -> runCatching { UUID.fromString(value) }.getOrNull()?.takeIf { id -> backOffice.member(id)?.status == AccountStatus.ACTIVE }?.let { Choice(it) }
    }

    private class Choice(val value: UUID?)

    /** The permissions of an active member, as the base computes them when they sign in. */
    private fun actorOf(accountId: UUID): TeamActor? {
        val account = backOffice.member(accountId)?.takeIf { it.status == AccountStatus.ACTIVE } ?: return null
        val roles = backOffice.liveGrants(accountId).map { it.role }.toSet()
        return TeamActor.of(account.id, account.pseudonym, roles, backOffice.keysOf(accountId).size)
    }

    /** The pseudonyms of the team, for a list (one query, not one per row). */
    private fun pseudonyms(): Map<UUID, String> = backOffice.members().associate { it.id to it.pseudonym }

    private fun activeMembers(): List<TeamActor> =
        backOffice.members().filter { it.status == AccountStatus.ACTIVE }.mapNotNull { actorOf(it.id) }.sortedBy { it.pseudonym.lowercase() }

    /** A review date typed as `yyyy-mm-dd`: midnight of that day in Paris; null when it is not a date. */
    private fun parseReviewDate(value: String?): Instant? =
        value?.trim()?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.atStartOfDay(PARIS)?.toInstant()

    companion object {
        const val ID: String = "contact"
        private const val INBOX_LIMIT: Int = 200
        private val PARIS: ZoneId = ZoneId.of("Europe/Paris")
        private val log = org.slf4j.LoggerFactory.getLogger(ContactSection::class.java)

        fun natureLabel(nature: CaseNature): String = ContactTexts.type(nature.type) + " : " + when (nature) {
            is CaseNature.Ticket -> ContactTexts.subtype(nature.subtype)
            is CaseNature.Contact -> ContactTexts.category(nature.category)
            is CaseNature.Claim -> ContactTexts.motif(nature.motif)
        }

        /** The sub-types, categories and motifs a member may filter on (the restricted motifs for administrators only). */
        fun qualifierChoices(restricted: Boolean): List<Pair<String, String>> =
            TicketSubtype.entries.map { "subtype:${it.wire}" to "${ContactTexts.type(CaseType.TICKET)} : ${ContactTexts.subtype(it)}" } +
                ContactCategory.entries.map { "category:${it.wire}" to "${ContactTexts.type(CaseType.CONTACT)} : ${ContactTexts.category(it)}" } +
                ClaimMotif.entries.filter { restricted || !it.restricted }.map { "motif:${it.wire}" to "${ContactTexts.type(CaseType.CLAIM)} : ${ContactTexts.motif(it)}" }

        /**
         * The marks of a case in the inbox: deadline passed or near (2 days; 7 days for a request on one's data;
         * from the reception for a right of reply), legal hold (active, to review, pending), high priority.
         */
        fun markList(case: StoredCase, now: Instant, pendingHold: Boolean): List<Pair<String, String?>> = buildList {
            val deadline = case.deadlineAt
            if (!case.isClosed && deadline != null) {
                val left = deadline.epochSecond - now.epochSecond
                val legal = case.deadlineKind == DeadlineKind.LEGAL
                when {
                    left < 0 -> add(ContactTexts.MARK_OVERDUE to " bo-badge-danger")
                    left <= 2.days.inWholeSeconds -> add(ContactTexts.MARK_TWO_DAYS to " bo-badge-warning")
                    legal && case.motif == ClaimMotif.DATA_RIGHTS && left <= 7.days.inWholeSeconds -> add(ContactTexts.MARK_SEVEN_DAYS to " bo-badge-warning")
                    legal && case.motif == ClaimMotif.RIGHT_OF_REPLY -> add(ContactTexts.MARK_RIGHT_OF_REPLY to " bo-badge-warning")
                }
            }
            val review = case.legalHoldReviewAt
            if (case.hasLegalHold && review != null) {
                if (!now.isBefore(review)) add(ContactTexts.MARK_HOLD_REVIEW to " bo-badge-warning") else add(ContactTexts.MARK_HOLD to null)
            }
            if (pendingHold) add(ContactTexts.MARK_HOLD_PENDING to null)
            if (case.priority == CasePriority.HIGH) add(ContactTexts.MARK_HIGH to null)
            if (case.isAnonymized) add(ContactTexts.MARK_ANONYMIZED to null)
        }
    }
}

/** The filters of the inbox, as the query string gives them (codes only, never a text). */
data class InboxFilters(
    val view: String = "",
    val type: CaseType? = null,
    /** `subtype:bug`, `category:journalist`, `motif:funds`… */
    val qualifier: String? = null,
    val assigned: String = "",
    val priority: CasePriority? = null,
) {
    fun query(viewer: UUID, restricted: Boolean): InboxQuery = InboxQuery(
        view = when (view) {
            "tous" -> InboxView.ALL
            "archives" -> InboxView.HOLDS
            "" -> InboxView.OPEN
            else -> CaseStatus.fromWire(view)?.let { InboxView.Status(it) } ?: InboxView.OPEN
        },
        type = type,
        subtype = qualifier?.takeIf { it.startsWith("subtype:") }?.removePrefix("subtype:")?.let(TicketSubtype::fromWire),
        category = qualifier?.takeIf { it.startsWith("category:") }?.removePrefix("category:")?.let(ContactCategory::fromWire),
        motif = qualifier?.takeIf { it.startsWith("motif:") }?.removePrefix("motif:")?.let(ClaimMotif::fromWire),
        assigned = when (assigned) {
            "moi" -> AssignedFilter.To(viewer)
            "personne" -> AssignedFilter.Nobody
            else -> AssignedFilter.Anyone
        },
        priority = priority,
        restricted = restricted,
        viewer = viewer,
    )

    companion object {
        private val QUALIFIERS: Set<String> = TicketSubtype.entries.map { "subtype:${it.wire}" }.toSet() +
            ContactCategory.entries.map { "category:${it.wire}" } + ClaimMotif.entries.map { "motif:${it.wire}" }

        fun read(query: Parameters): InboxFilters = InboxFilters(
            view = query["dossiers"]?.takeIf { it in setOf("tous", "archives") || CaseStatus.fromWire(it) != null }.orEmpty(),
            type = CaseType.fromWire(query["type"]),
            qualifier = query["nature"]?.takeIf { it in QUALIFIERS },
            assigned = query["assignation"]?.takeIf { it == "moi" || it == "personne" }.orEmpty(),
            priority = CasePriority.of(query["priorite"]),
        )
    }
}
