package org.politisationpopulairefrancaise.server.backoffice

import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.MAIN
import kotlinx.html.a
import kotlinx.html.article
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.caption
import kotlinx.html.code
import kotlinx.html.dd
import kotlinx.html.div
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.fieldSet
import kotlinx.html.footer
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.head
import kotlinx.html.header
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.lang
import kotlinx.html.legend
import kotlinx.html.li
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.nav
import kotlinx.html.noScript
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.section
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.strong
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.textArea
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.time
import kotlinx.html.title
import kotlinx.html.tr
import kotlinx.html.ul
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/** A message shown at the top of a page after an action; [error] makes it an alert. */
data class Notice(val text: String, val error: Boolean = false)

private val PARIS: ZoneId = ZoneId.of("Europe/Paris")
private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH 'h' mm", Locale.FRENCH)
private val MACHINE: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

/**
 * Common layout of the back-office pages: French, landmarks, skip link, self-hosted stylesheet; the one
 * script only on the pages of a key ceremony ([script]). Never indexed.
 */
fun HTML.backOfficeLayout(
    pageTitle: String,
    auth: Authenticated?,
    current: String? = null,
    notice: Notice? = null,
    script: Boolean = false,
    content: MAIN.() -> Unit,
) {
    lang = "fr"
    head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        meta(name = "color-scheme", content = "light dark")
        meta(name = "robots", content = "noindex, nofollow")
        title { +"$pageTitle | ${BackOfficeTexts.SITE_NAME}" }
        link(rel = "stylesheet", href = "/static/equipe.css")
        if (script) script(src = "/static/equipe.js") { attributes["defer"] = "defer" }
    }
    body {
        a(href = "#contenu", classes = "skip-link") { +BackOfficeTexts.SKIP_LINK }
        header(classes = "bo-header") {
            a(href = "/", classes = "bo-title") { +BackOfficeTexts.SITE_NAME }
            if (auth != null) navigation(auth, current)
        }
        main {
            id = "contenu"
            attributes["tabindex"] = "-1"
            notice?.let { shown ->
                div(classes = if (shown.error) "bo-notice bo-notice-error" else "bo-notice") {
                    attributes["role"] = if (shown.error) "alert" else "status"
                    +shown.text
                }
            }
            content()
        }
        footer(classes = "bo-footer") {
            p { +BackOfficeTexts.FOOTER_PRIVACY }
            p { +BackOfficeTexts.FOOTER_JOURNAL }
        }
    }
}

private fun FlowContent.navigation(auth: Authenticated, current: String?) {
    val actor = auth.actor
    nav {
        attributes["aria-label"] = BackOfficeTexts.NAV_LABEL
        ul {
            navLink("/", BackOfficeTexts.NAV_DASHBOARD, current)
            navLink("/validations", BackOfficeTexts.NAV_APPROVALS, current)
            if (TEAM_PERMISSIONS.any(actor::has)) navLink("/equipe", BackOfficeTexts.NAV_TEAM, current)
            if (actor.has(Permissions.AUDIT_READ)) navLink("/journal", BackOfficeTexts.NAV_JOURNAL, current)
            if (actor.has(Permissions.LEGAL_REQUISITION)) navLink("/requisitions", BackOfficeTexts.NAV_REQUISITIONS, current)
            if (actor.has(Permissions.PLATFORM_KEYS)) navLink(BackOffice.PLATFORM_PATH, BackOfficeTexts.NAV_PLATFORM_KEYS, current)
            if (actor.has(Permissions.VALIDATION_SESSION_OPEN) || actor.has(Permissions.TEAM_REVOKE) || TeamRole.VALIDATOR in actor.roles) {
                navLink("/seances", BackOfficeTexts.NAV_SESSIONS, current)
            }
            auth.sections.forEach { navLink(it.href, it.title, current) }
            navLink("/cles", BackOfficeTexts.NAV_KEYS, current)
        }
        form(action = "/deconnexion", method = FormMethod.post, classes = "bo-signout") {
            csrfField(auth)
            button(type = ButtonType.submit, classes = "bo-button-secondary") { +BackOfficeTexts.SIGN_OUT }
        }
    }
}

private fun kotlinx.html.UL.navLink(href: String, label: String, current: String?) {
    li {
        a(href = href) {
            if (href == current) attributes["aria-current"] = "page"
            +label
        }
    }
}

/** The permissions that open the team page. */
val TEAM_PERMISSIONS: List<String> = listOf(Permissions.TEAM_INVITE, Permissions.TEAM_GRANT, Permissions.TEAM_REVOKE, Permissions.TEAM_MANAGE_ADMINS)

fun FlowContent.csrfField(auth: Authenticated) {
    input(type = InputType.hidden, name = Csrf.FIELD) { value = auth.csrf }
}

fun FlowContent.dateTime(instant: Instant) {
    val moment = instant.atZone(PARIS)
    time {
        attributes["datetime"] = MACHINE.format(moment)
        +DATE_TIME.format(moment)
    }
}

fun FlowContent.dateTime(epochSeconds: Long) = dateTime(Instant.ofEpochSecond(epochSeconds))

/** The status line a ceremony script writes into (polite live region). */
private fun FlowContent.ceremonyStatus(id: String) {
    p(classes = "bo-ceremony-status") {
        this.id = id
        attributes["role"] = "status"
        attributes["aria-live"] = "polite"
    }
}

/** A button that starts a key ceremony; the script reads what to do from its data attributes. */
private fun FlowContent.ceremonyButton(kind: String, label: String, statusId: String, extra: Map<String, String> = emptyMap()) {
    button(type = ButtonType.button, classes = "bo-button") {
        attributes["data-ceremony"] = kind
        attributes["data-status"] = statusId
        attributes["data-msg-working"] = BackOfficeTexts.CEREMONY_WORKING
        attributes["data-msg-failed"] = BackOfficeTexts.CEREMONY_FAILED
        attributes["data-msg-unsupported"] = BackOfficeTexts.CEREMONY_UNSUPPORTED
        extra.forEach { (key, value) -> attributes["data-$key"] = value }
        +label
    }
    ceremonyStatus(statusId)
    noScript { p { +BackOfficeTexts.NOSCRIPT } }
}

// Sign-in and invitation --------------------------------------------------------------------------

fun HTML.signInPage(notice: Notice?) {
    backOfficeLayout(BackOfficeTexts.SIGN_IN_TITLE, null, notice = notice, script = true) {
        h1 { +BackOfficeTexts.SIGN_IN_TITLE }
        section {
            p { +BackOfficeTexts.SIGN_IN_INTRO }
            p { +BackOfficeTexts.SIGN_IN_HELP }
            ceremonyButton("signin", BackOfficeTexts.SIGN_IN_BUTTON, "ppf-signin-status")
        }
    }
}

fun HTML.enrolPage(invitation: TeamInvitation, token: String) {
    backOfficeLayout(BackOfficeTexts.ENROL_TITLE, null, script = true) {
        h1 { +BackOfficeTexts.ENROL_TITLE }
        section {
            p { +BackOfficeTexts.ENROL_INTRO }
            p(classes = "bo-pseudonym") { strong { +invitation.pseudonym } }
            p { +BackOfficeTexts.ENROL_EXPLAIN }
            label {
                htmlFor = "ppf-key-name"
                +BackOfficeTexts.ENROL_KEY_NAME
            }
            input(type = InputType.text, name = "name") {
                id = "ppf-key-name"
                maxLength = KeyNames.MAX_LENGTH.toString()
                value = BackOfficeTexts.DEFAULT_KEY_NAME
                attributes["autocomplete"] = "off"
            }
            ceremonyButton("enrol", BackOfficeTexts.ENROL_BUTTON, "ppf-enrol-status", mapOf("invitation" to token, "name-field" to "ppf-key-name"))
            p { +BackOfficeTexts.ENROL_BACKUP }
        }
    }
}

fun HTML.messagePage(title: String, text: String, auth: Authenticated? = null) {
    backOfficeLayout(title, auth) {
        h1 { +title }
        section {
            p { +text }
            p { a(href = "/") { +BackOfficeTexts.BACK_HOME } }
        }
    }
}

/**
 * The confirmation of a sensitive action: the posted fields are kept in a hidden form, sent again once
 * the script has verified the key (POST to the same address, with the same anti-forgery token).
 */
fun HTML.confirmPage(auth: Authenticated, action: String, fields: List<Pair<String, String>>) {
    backOfficeLayout(BackOfficeTexts.CONFIRM_TITLE, auth, script = true) {
        h1 { +BackOfficeTexts.CONFIRM_TITLE }
        section {
            p { +BackOfficeTexts.CONFIRM_TEXT }
            form(action = action, method = FormMethod.post) {
                id = "ppf-confirm-form"
                fields.forEach { (name, value) -> input(type = InputType.hidden, name = name) { this.value = value } }
            }
            ceremonyButton("confirm", BackOfficeTexts.CONFIRM_BUTTON, "ppf-confirm-status", mapOf("csrf" to auth.csrf, "form" to "ppf-confirm-form"))
            p { a(href = "/") { +BackOfficeTexts.CONFIRM_CANCEL } }
        }
    }
}

// Dashboard -------------------------------------------------------------------------------------------

fun HTML.dashboardPage(auth: Authenticated, dashboard: Dashboard, notice: Notice?) {
    backOfficeLayout(BackOfficeTexts.DASHBOARD_TITLE, auth, current = "/", notice = notice) {
        h1 { +BackOfficeTexts.DASHBOARD_TITLE }
        p { +"${BackOfficeTexts.DASHBOARD_HELLO} ${auth.actor.pseudonym}." }
        if (auth.actor.needsBackupKey) {
            div(classes = "bo-notice bo-notice-warning") {
                p { +BackOfficeTexts.BACKUP_KEY_MISSING }
                p { a(href = "/cles") { +BackOfficeTexts.BACKUP_KEY_LINK } }
            }
        }
        if (dashboard.alerts.isNotEmpty()) {
            section {
                attributes["aria-labelledby"] = "bo-alerts"
                h2 { id = "bo-alerts"; +BackOfficeTexts.ALERTS_TITLE }
                ul(classes = "bo-list") {
                    dashboard.alerts.forEach { alert ->
                        li(classes = "bo-level-${alert.level}") {
                            p {
                                span(classes = "bo-badge") { +"${BackOfficeTexts.LEVEL} ${alert.level}" }
                                +" ${alert.title} "
                                dateTime(alert.createdAt)
                            }
                            alert.link?.let { link -> p { a(href = link) { +BackOfficeTexts.ALERT_OPEN } } }
                            form(action = "/alertes/${alert.id}/fermeture", method = FormMethod.post) {
                                csrfField(auth)
                                button(type = ButtonType.submit, classes = "bo-button-secondary") { +BackOfficeTexts.ALERT_DISMISS }
                            }
                        }
                    }
                }
            }
        }
        section {
            attributes["aria-labelledby"] = "bo-priorities"
            h2 { id = "bo-priorities"; +BackOfficeTexts.PRIORITIES_TITLE }
            if (dashboard.priorities.isEmpty()) {
                p { +BackOfficeTexts.PRIORITIES_EMPTY }
            } else {
                ul(classes = "bo-list") {
                    dashboard.priorities.forEach { priority ->
                        li {
                            if (priority.overdue) span(classes = "bo-badge bo-badge-danger") { +BackOfficeTexts.OVERDUE }
                            if (priority.nearDeadline) span(classes = "bo-badge bo-badge-warning") { +BackOfficeTexts.NEAR_DEADLINE }
                            +" "
                            a(href = itemPath(priority.queue.id, priority.item.id)) { +priority.item.title }
                            +" (${priority.queue.title})"
                            itemFacts(priority.item)
                        }
                    }
                }
            }
        }
        section {
            attributes["aria-labelledby"] = "bo-queues"
            h2 { id = "bo-queues"; +BackOfficeTexts.QUEUES_TITLE }
            if (dashboard.queues.isEmpty()) {
                p { +BackOfficeTexts.QUEUES_EMPTY }
            } else {
                ul(classes = "bo-list") {
                    dashboard.queues.forEach { count ->
                        li {
                            a(href = queuePath(count.queue.id)) { +count.queue.title }
                            +" : "
                            strong {
                                +when (val pending = count.pending) {
                                    null -> BackOfficeTexts.QUEUE_UNAVAILABLE
                                    0 -> BackOfficeTexts.PENDING_COUNT_ZERO
                                    else -> pending.toString()
                                }
                            }
                        }
                    }
                }
            }
            if (dashboard.approvalsToDecide > 0) {
                p {
                    a(href = "/validations") { +BackOfficeTexts.APPROVALS_WAITING }
                    +" : "
                    strong { +dashboard.approvalsToDecide.toString() }
                }
            }
        }
    }
}

private fun FlowContent.itemFacts(item: QueueItem) {
    item.summary?.let { summary -> p(classes = "bo-summary") { +summary } }
    if (item.receivedAt == null && item.deadline == null && item.level == null) return
    dl(classes = "bo-facts") {
        item.receivedAt?.let { received -> dt { +BackOfficeTexts.RECEIVED }; dd { dateTime(received) } }
        item.deadline?.let { deadline -> dt { +BackOfficeTexts.DEADLINE }; dd { dateTime(deadline) } }
        item.level?.let { level -> dt { +BackOfficeTexts.LEVEL }; dd { +"$level / 5" } }
    }
}

fun queuePath(queueId: String): String = "/files/$queueId"

fun itemPath(queueId: String, itemId: String): String = "/files/$queueId/" + java.net.URLEncoder.encode(itemId, Charsets.UTF_8)

// Queues ------------------------------------------------------------------------------------------------

fun HTML.queuePage(auth: Authenticated, queue: BackOfficeQueue, page: QueuePage, notice: Notice?) {
    backOfficeLayout(queue.title, auth, notice = notice) {
        h1 { +queue.title }
        section {
            if (page.items.isEmpty()) {
                p { +BackOfficeTexts.QUEUE_EMPTY }
            } else {
                ul(classes = "bo-list") {
                    page.items.forEach { item ->
                        li {
                            h2(classes = "bo-item-title") { a(href = itemPath(queue.id, item.id)) { +item.title } }
                            itemFacts(item)
                        }
                    }
                }
            }
            page.nextCursor?.let { cursor ->
                p { a(href = queuePath(queue.id) + "?curseur=" + java.net.URLEncoder.encode(cursor, Charsets.UTF_8)) { +BackOfficeTexts.QUEUE_NEXT } }
            }
        }
    }
}

fun HTML.queueItemPage(auth: Authenticated, queue: BackOfficeQueue, itemId: String, actions: List<QueueAction>, notice: Notice?) {
    backOfficeLayout(queue.title, auth, notice = notice) {
        p { a(href = queuePath(queue.id)) { +BackOfficeTexts.QUEUE_BACK } }
        article(classes = "bo-item") { queue.render(itemId, this) }
        section {
            attributes["aria-labelledby"] = "bo-actions"
            h2 { id = "bo-actions"; +BackOfficeTexts.ITEM_ACTIONS }
            val allowed = actions.filter { auth.actor.has(it.permission) }
            if (allowed.isEmpty()) p { +BackOfficeTexts.ITEM_NO_ACTION }
            allowed.forEachIndexed { index, action ->
                form(action = itemPath(queue.id, itemId), method = FormMethod.post, classes = "bo-action") {
                    csrfField(auth)
                    input(type = InputType.hidden, name = "action") { value = action.id }
                    if (action.reasonAllowed) {
                        val fieldId = "bo-reason-$index"
                        label {
                            htmlFor = fieldId
                            +"${BackOfficeTexts.REASON_LABEL} (${if (action.reasonRequired) BackOfficeTexts.REASON_REQUIRED_HINT else BackOfficeTexts.REASON_OPTIONAL_HINT})"
                        }
                        textArea(rows = "3") {
                            id = fieldId
                            name = "motif"
                            maxLength = AuditEntry.MAX_REASON_LENGTH.toString()
                            attributes["aria-describedby"] = "$fieldId-hint"
                            if (action.reasonRequired) required = true
                        }
                        p(classes = "bo-hint") {
                            id = "$fieldId-hint"
                            +BackOfficeTexts.REASON_PERMANENT_HINT
                        }
                    }
                    if (action.twoPerson) p(classes = "bo-hint") { +BackOfficeTexts.TWO_PERSON_HINT }
                    if (action.sensitive) p(classes = "bo-hint") { +BackOfficeTexts.SENSITIVE_HINT }
                    button(type = ButtonType.submit, classes = "bo-button") { +action.label }
                }
            }
        }
    }
}

// Team --------------------------------------------------------------------------------------------------

private fun statusLabel(status: AccountStatus): String = when (status) {
    AccountStatus.ACTIVE -> BackOfficeTexts.STATUS_ACTIVE
    AccountStatus.SUSPENDED -> BackOfficeTexts.STATUS_SUSPENDED
    AccountStatus.REMOVED -> BackOfficeTexts.STATUS_REMOVED
}

/** One member line of the team page. */
data class MemberLine(val account: TeamAccount, val roles: List<RoleGrant>)

fun HTML.teamPage(auth: Authenticated, members: List<MemberLine>, invitations: List<TeamInvitation>, notice: Notice?) {
    backOfficeLayout(BackOfficeTexts.TEAM_TITLE, auth, current = "/equipe", notice = notice) {
        h1 { +BackOfficeTexts.TEAM_TITLE }
        section {
            attributes["aria-labelledby"] = "bo-members"
            h2 { id = "bo-members"; +BackOfficeTexts.TEAM_MEMBERS }
            div(classes = "bo-table") {
                table {
                    caption { +BackOfficeTexts.TEAM_MEMBERS }
                    thead {
                        tr {
                            th { attributes["scope"] = "col"; +BackOfficeTexts.TEAM_PSEUDONYM }
                            th { attributes["scope"] = "col"; +BackOfficeTexts.TEAM_STATUS }
                            th { attributes["scope"] = "col"; +BackOfficeTexts.TEAM_ROLES }
                            th { attributes["scope"] = "col"; +BackOfficeTexts.TEAM_SINCE }
                        }
                    }
                    tbody {
                        members.forEach { member ->
                            tr {
                                th {
                                    attributes["scope"] = "row"
                                    a(href = "/equipe/membres/${member.account.id}") { +member.account.pseudonym }
                                }
                                td { +statusLabel(member.account.status) }
                                td { +member.roles.joinToString(", ") { it.role.label }.ifEmpty { BackOfficeTexts.TEAM_NO_ROLE } }
                                td { dateTime(member.account.createdAt) }
                            }
                        }
                    }
                }
            }
        }
        if (auth.actor.has(Permissions.TEAM_INVITE)) {
            section {
                attributes["aria-labelledby"] = "bo-invite"
                h2 { id = "bo-invite"; +BackOfficeTexts.INVITE_TITLE }
                form(action = "/equipe/invitations", method = FormMethod.post) {
                    csrfField(auth)
                    label { htmlFor = "bo-invite-pseudonym"; +BackOfficeTexts.INVITE_PSEUDONYM }
                    input(type = InputType.text, name = "pseudonyme") {
                        id = "bo-invite-pseudonym"
                        required = true
                        maxLength = Pseudonyms.MAX_LENGTH.toString()
                        attributes["autocomplete"] = "off"
                    }
                    fieldSet {
                        legend { +BackOfficeTexts.INVITE_ROLES }
                        TeamRole.entries.filter { auth.actor.has(it.grantPermission) }.forEach { role ->
                            div(classes = "bo-check") {
                                input(type = InputType.checkBox, name = "roles") {
                                    id = "bo-invite-role-${role.id}"
                                    value = role.id
                                }
                                label { htmlFor = "bo-invite-role-${role.id}"; +role.label }
                            }
                        }
                    }
                    organisationField("bo-invite-organisation")
                    endDateField("bo-invite-end", BackOfficeTexts.INVITE_END)
                    button(type = ButtonType.submit, classes = "bo-button") { +BackOfficeTexts.INVITE_BUTTON }
                }
            }
            if (invitations.isNotEmpty()) {
                section {
                    attributes["aria-labelledby"] = "bo-invitations"
                    h2 { id = "bo-invitations"; +BackOfficeTexts.INVITATIONS_PENDING }
                    ul(classes = "bo-list") {
                        invitations.forEach { invitation ->
                            li {
                                strong { +invitation.pseudonym }
                                +" (${invitation.proposedRoles.joinToString(", ") { it.label }.ifEmpty { BackOfficeTexts.TEAM_NO_ROLE }}), ${BackOfficeTexts.INVITATION_EXPIRES} "
                                dateTime(invitation.expiresAt)
                                if (!invitation.bootstrap) {
                                    form(action = "/equipe/invitations/${invitation.id}/retrait", method = FormMethod.post) {
                                        csrfField(auth)
                                        button(type = ButtonType.submit, classes = "bo-button-secondary") { +BackOfficeTexts.INVITATION_WITHDRAW }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun FlowContent.organisationField(fieldId: String) {
    label { htmlFor = fieldId; +BackOfficeTexts.INVITE_ORGANISATION }
    input(type = InputType.text, name = "organisation") {
        id = fieldId
        maxLength = "100"
        attributes["autocomplete"] = "off"
        attributes["spellcheck"] = "false"
    }
}

private fun FlowContent.endDateField(fieldId: String, text: String) {
    label { htmlFor = fieldId; +text }
    input(type = InputType.date, name = "fin") { id = fieldId }
}

fun HTML.invitationIssuedPage(auth: Authenticated, invitation: TeamInvitation, link: String) {
    backOfficeLayout(BackOfficeTexts.INVITE_ISSUED_TITLE, auth, current = "/equipe") {
        h1 { +BackOfficeTexts.INVITE_ISSUED_TITLE }
        section {
            p { +"${invitation.pseudonym} : ${BackOfficeTexts.INVITE_ISSUED_TEXT}" }
            label { htmlFor = "bo-invitation-link"; +BackOfficeTexts.INVITE_LINK_LABEL }
            input(type = InputType.text, name = "lien") {
                id = "bo-invitation-link"
                value = link
                readonly = true
                attributes["spellcheck"] = "false"
            }
            p { a(href = "/equipe") { +BackOfficeTexts.TEAM_TITLE } }
        }
    }
}

private fun grantStatusLabel(grant: RoleGrant): String = when (grant.status) {
    GrantStatus.ACTIVE -> BackOfficeTexts.MEMBER_GRANT_ACTIVE
    GrantStatus.EXPIRED -> BackOfficeTexts.MEMBER_GRANT_EXPIRED
    GrantStatus.REVOKED -> BackOfficeTexts.MEMBER_GRANT_REVOKED
    GrantStatus.SUSPENDED -> BackOfficeTexts.MEMBER_GRANT_SUSPENDED
}

fun HTML.memberPage(auth: Authenticated, account: TeamAccount, grants: List<RoleGrant>, keys: List<KeyInfo>, notice: Notice?) {
    val actor = auth.actor
    backOfficeLayout(account.pseudonym, auth, current = "/equipe", notice = notice) {
        h1 { +account.pseudonym }
        section {
            dl(classes = "bo-facts") {
                dt { +BackOfficeTexts.TEAM_STATUS }
                dd { +statusLabel(account.status) }
                dt { +BackOfficeTexts.TEAM_SINCE }
                dd { dateTime(account.createdAt) }
                dt { +BackOfficeTexts.MEMBER_KEYS }
                dd { +keys.size.toString() }
            }
        }
        if (account.status == AccountStatus.SUSPENDED) {
            div(classes = "bo-notice bo-notice-warning") { +BackOfficeTexts.MEMBER_SUSPENDED }
            if (actor.has(Permissions.TEAM_REVOKE) || actor.has(Permissions.TEAM_MANAGE_ADMINS)) {
                section {
                    attributes["aria-labelledby"] = "bo-member-keys"
                    h2 { id = "bo-member-keys"; +BackOfficeTexts.MEMBER_KEYS }
                    ul(classes = "bo-list") {
                        keys.forEach { key ->
                            li {
                                strong { +key.name }
                                +" : ${BackOfficeTexts.KEYS_CREATED} "
                                dateTime(key.createdAt)
                                fingerprint(key)
                                form(action = "/equipe/membres/${account.id}/cles/${key.id}/suppression", method = FormMethod.post) {
                                    csrfField(auth)
                                    button(type = ButtonType.submit, classes = "bo-button-secondary") { +BackOfficeTexts.MEMBER_KEY_DELETE }
                                }
                            }
                        }
                    }
                }
            }
            if (actor.has(Permissions.TEAM_MANAGE_ADMINS)) {
                section {
                    attributes["aria-labelledby"] = "bo-reinstate"
                    h2 { id = "bo-reinstate"; +BackOfficeTexts.MEMBER_REINSTATE_TITLE }
                    p { +BackOfficeTexts.MEMBER_REINSTATE_TEXT }
                    form(action = "/equipe/membres/${account.id}/reintegration", method = FormMethod.post) {
                        csrfField(auth)
                        reasonField("bo-reinstate-reason", required = false)
                        button(type = ButtonType.submit, classes = "bo-button") { +BackOfficeTexts.MEMBER_REINSTATE_BUTTON }
                    }
                }
            }
        }
        section {
            attributes["aria-labelledby"] = "bo-grants"
            h2 { id = "bo-grants"; +BackOfficeTexts.MEMBER_GRANTS }
            ul(classes = "bo-list") {
                grants.forEach { grant ->
                    li {
                        strong { +grant.role.label }
                        +" : ${grantStatusLabel(grant)}, ${BackOfficeTexts.TEAM_SINCE.lowercase()} "
                        dateTime(grant.grantedAt)
                        grant.endsAt?.let { end ->
                            +" ${BackOfficeTexts.MEMBER_GRANT_UNTIL} "
                            dateTime(end)
                        }
                        grant.organisationPubkey?.let { organisation ->
                            +" (${BackOfficeTexts.MEMBER_GRANT_ORGANISATION} "
                            code { +organisation.take(16) }
                            +"…)"
                        }
                        val revokePermission = if (grant.role.isAdministration) Permissions.TEAM_MANAGE_ADMINS else Permissions.TEAM_REVOKE
                        if (grant.status == GrantStatus.ACTIVE && actor.has(revokePermission) && account.status == AccountStatus.ACTIVE) {
                            form(action = "/equipe/membres/${account.id}/roles/${grant.id}/retrait", method = FormMethod.post) {
                                csrfField(auth)
                                reasonField("bo-revoke-${grant.id}", required = false)
                                button(type = ButtonType.submit, classes = "bo-button-secondary") { +BackOfficeTexts.MEMBER_REVOKE_ROLE }
                            }
                        }
                    }
                }
            }
        }
        val requestable = TeamRole.entries.filter { actor.has(it.grantPermission) }
        if (account.status == AccountStatus.ACTIVE && requestable.isNotEmpty() && account.id != actor.accountId) {
            section {
                attributes["aria-labelledby"] = "bo-request"
                h2 { id = "bo-request"; +BackOfficeTexts.MEMBER_REQUEST_ROLE }
                form(action = "/equipe/membres/${account.id}/roles", method = FormMethod.post) {
                    csrfField(auth)
                    label { htmlFor = "bo-request-role"; +BackOfficeTexts.MEMBER_ROLE }
                    select {
                        id = "bo-request-role"
                        name = "role"
                        requestable.forEach { role -> option { value = role.id; +role.label } }
                    }
                    organisationField("bo-request-organisation")
                    endDateField("bo-request-end", BackOfficeTexts.INVITE_END)
                    reasonField("bo-request-reason", required = false)
                    p(classes = "bo-hint") { +BackOfficeTexts.TWO_PERSON_HINT }
                    button(type = ButtonType.submit, classes = "bo-button") { +BackOfficeTexts.MEMBER_REQUEST_BUTTON }
                }
            }
        }
        if (account.status != AccountStatus.REMOVED && (actor.has(Permissions.TEAM_REVOKE) || actor.has(Permissions.TEAM_MANAGE_ADMINS))) {
            if (account.status == AccountStatus.ACTIVE) {
                section(classes = "bo-danger") {
                    attributes["aria-labelledby"] = "bo-cut"
                    h2 { id = "bo-cut"; +BackOfficeTexts.MEMBER_CUT_TITLE }
                    p { +BackOfficeTexts.MEMBER_CUT_TEXT }
                    form(action = "/equipe/membres/${account.id}/coupure", method = FormMethod.post) {
                        csrfField(auth)
                        reasonField("bo-cut-reason", required = false)
                        button(type = ButtonType.submit, classes = "bo-button-danger") { +BackOfficeTexts.MEMBER_CUT_BUTTON }
                    }
                }
            }
            section(classes = "bo-danger") {
                attributes["aria-labelledby"] = "bo-remove"
                h2 { id = "bo-remove"; +BackOfficeTexts.MEMBER_REMOVE_TITLE }
                p { +BackOfficeTexts.MEMBER_REMOVE_TEXT }
                form(action = "/equipe/membres/${account.id}/retrait", method = FormMethod.post) {
                    csrfField(auth)
                    reasonField("bo-remove-reason", required = false)
                    button(type = ButtonType.submit, classes = "bo-button-danger") { +BackOfficeTexts.MEMBER_REMOVE_BUTTON }
                }
            }
        }
    }
}

private fun FlowContent.reasonField(fieldId: String, required: Boolean) {
    label {
        htmlFor = fieldId
        +"${BackOfficeTexts.REASON_LABEL} (${if (required) BackOfficeTexts.REASON_REQUIRED_HINT else BackOfficeTexts.REASON_OPTIONAL_HINT})"
    }
    input(type = InputType.text, name = "motif") {
        id = fieldId
        maxLength = AuditEntry.MAX_REASON_LENGTH.toString()
        attributes["aria-describedby"] = "$fieldId-hint"
        if (required) this.required = true
    }
    p(classes = "bo-hint") {
        id = "$fieldId-hint"
        +BackOfficeTexts.REASON_PERMANENT_HINT
    }
}

// Approvals ---------------------------------------------------------------------------------------------

fun HTML.approvalsPage(auth: Authenticated, approvals: List<PendingApproval>, pseudonyms: Map<UUID, String>, notice: Notice?) {
    backOfficeLayout(BackOfficeTexts.APPROVALS_TITLE, auth, current = "/validations", notice = notice) {
        h1 { +BackOfficeTexts.APPROVALS_TITLE }
        p { +BackOfficeTexts.APPROVALS_INTRO }
        section {
            if (approvals.isEmpty()) p { +BackOfficeTexts.APPROVALS_EMPTY }
            ul(classes = "bo-list") {
                approvals.forEach { approval ->
                    li {
                        h2(classes = "bo-item-title") { +describe(approval, pseudonyms) }
                        p {
                            +"${BackOfficeTexts.APPROVAL_REQUESTED_BY} ${pseudonyms[approval.requestedBy] ?: BackOfficeTexts.ACTOR_UNKNOWN} ${BackOfficeTexts.APPROVAL_ON} "
                            dateTime(approval.requestedAt)
                            +", ${BackOfficeTexts.APPROVAL_EXPIRES} "
                            dateTime(approval.expiresAt)
                        }
                        val reasonLabel = if (approval.action == BackOffice.ACTION_REQUISITION) BackOfficeTexts.REQUISITION_REFERENCE_SHORT else BackOfficeTexts.REASON_LABEL
                        approval.reason?.let { reason -> p { +"$reasonLabel : $reason" } }
                        if (approval.requestedBy == auth.actor.accountId) {
                            p(classes = "bo-hint") { +BackOfficeTexts.APPROVAL_YOURS }
                            form(action = "/validations/${approval.id}/annulation", method = FormMethod.post) {
                                csrfField(auth)
                                button(type = ButtonType.submit, classes = "bo-button-secondary") { +BackOfficeTexts.APPROVAL_CANCEL }
                            }
                        } else {
                            form(action = "/validations/${approval.id}/accord", method = FormMethod.post, classes = "bo-inline") {
                                csrfField(auth)
                                button(type = ButtonType.submit, classes = "bo-button") { +BackOfficeTexts.APPROVAL_APPROVE }
                            }
                            form(action = "/validations/${approval.id}/refus", method = FormMethod.post, classes = "bo-inline") {
                                csrfField(auth)
                                reasonField("bo-refuse-${approval.id}", required = false)
                                button(type = ButtonType.submit, classes = "bo-button-secondary") { +BackOfficeTexts.APPROVAL_REFUSE }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun describe(approval: PendingApproval, pseudonyms: Map<UUID, String>): String {
    fun account(id: String?): String = id?.let { runCatching { UUID.fromString(it) }.getOrNull() }?.let(pseudonyms::get) ?: BackOfficeTexts.ACTOR_UNKNOWN
    val role = approval.payload["role"]?.let(TeamRole::fromId)?.label ?: approval.payload["role"].orEmpty()
    return when (approval.action) {
        BackOffice.ACTION_GRANT -> "${BackOfficeTexts.APPROVAL_GRANT} « $role » ${BackOfficeTexts.APPROVAL_TO} ${account(approval.targetId)}"
        BackOffice.ACTION_REVOKE -> "${BackOfficeTexts.APPROVAL_REVOKE} « $role » ${BackOfficeTexts.APPROVAL_TO} ${account(approval.payload["account"])}"
        BackOffice.ACTION_REMOVE -> "${BackOfficeTexts.APPROVAL_REMOVE} : ${account(approval.targetId)}"
        BackOffice.ACTION_REINSTATE -> "${BackOfficeTexts.APPROVAL_REINSTATE} : ${account(approval.targetId)}"
        BackOffice.ACTION_REQUISITION -> "${BackOfficeTexts.APPROVAL_REQUISITION} (${describeSearch(RequisitionSearch.of(approval.payload))})"
        else -> "${BackOfficeTexts.APPROVAL_QUEUE} ${approval.payload["queue"].orEmpty()} : ${approval.payload["action"].orEmpty()} (${approval.payload["item"].orEmpty()})"
    }
}

/** The search of a requisition in words: key, content, period (Paris days, end included). */
private fun describeSearch(search: RequisitionSearch?): String {
    if (search == null) return BackOfficeTexts.REQUISITION_SEARCH_GONE
    val day = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)
    return listOfNotNull(
        search.pubkey?.let { "${BackOfficeTexts.REQUISITION_KEY_SHORT} $it" },
        search.eventId?.let { "${BackOfficeTexts.REQUISITION_EVENT_SHORT} $it" },
        "${BackOfficeTexts.REQUISITION_PERIOD_FROM} ${day.format(search.from.atZone(PARIS))} ${BackOfficeTexts.REQUISITION_PERIOD_UNTIL} " +
            day.format(search.until.minusSeconds(1).atZone(PARIS)),
    ).joinToString(", ")
}

// Judicial requisitions (legal register, D-077) ---------------------------------------------------------

/**
 * The requisitions of the last 30 days and the form of a new one (`legal.requisition`). The answer of an
 * approved requisition is offered to the person who asked and to the one who approved, once.
 */
fun HTML.requisitionsPage(auth: Authenticated, requisitions: List<PendingApproval>, pseudonyms: Map<UUID, String>, now: Instant, notice: Notice?) {
    fun who(id: UUID?): String = id?.let(pseudonyms::get) ?: BackOfficeTexts.ACTOR_UNKNOWN
    backOfficeLayout(BackOfficeTexts.REQUISITIONS_TITLE, auth, current = "/requisitions", notice = notice) {
        h1 { +BackOfficeTexts.REQUISITIONS_TITLE }
        p { +BackOfficeTexts.REQUISITIONS_INTRO }
        p { +BackOfficeTexts.REQUISITIONS_CONTENT }
        section {
            attributes["aria-labelledby"] = "bo-requisitions-ready"
            h2 { id = "bo-requisitions-ready"; +BackOfficeTexts.REQUISITIONS_READY }
            p { +BackOfficeTexts.REQUISITIONS_READY_TEXT }
            val ready = requisitions.filter { LegalRequisitions.mayDownload(it, auth.actor.accountId, now) }
            if (ready.isEmpty()) p { +BackOfficeTexts.REQUISITIONS_READY_NONE }
            ul(classes = "bo-list") {
                ready.forEach { requisition ->
                    li {
                        h3(classes = "bo-item-title") { +"${BackOfficeTexts.REQUISITION_REFERENCE_SHORT} : ${requisition.reason.orEmpty()}" }
                        p { +"${BackOfficeTexts.REQUISITION_SEARCH} : ${describeSearch(RequisitionSearch.of(requisition.payload))}" }
                        p {
                            +"${BackOfficeTexts.APPROVAL_REQUESTED_BY} ${who(requisition.requestedBy)}, ${BackOfficeTexts.REQUISITION_APPROVED_BY} ${who(requisition.decidedBy)} ${BackOfficeTexts.APPROVAL_ON} "
                            requisition.decidedAt?.let { dateTime(it) }
                        }
                        form(action = "/requisitions/${requisition.id}/reponse", method = FormMethod.post) {
                            csrfField(auth)
                            p(classes = "bo-hint") { +BackOfficeTexts.SENSITIVE_HINT }
                            button(type = ButtonType.submit, classes = "bo-button") { +BackOfficeTexts.REQUISITION_DOWNLOAD }
                        }
                    }
                }
            }
        }
        section {
            attributes["aria-labelledby"] = "bo-requisition-new"
            h2 { id = "bo-requisition-new"; +BackOfficeTexts.REQUISITIONS_FORM_TITLE }
            form(action = "/requisitions", method = FormMethod.post) {
                csrfField(auth)
                div(classes = "bo-field") {
                    label { htmlFor = "bo-requisition-reference"; +BackOfficeTexts.REQUISITION_REFERENCE }
                    input(type = InputType.text, name = "reference") {
                        id = "bo-requisition-reference"
                        required = true
                        maxLength = AuditEntry.MAX_REASON_LENGTH.toString()
                        attributes["aria-describedby"] = "bo-requisition-reference-hint"
                        attributes["autocomplete"] = "off"
                    }
                    p(classes = "bo-hint") { id = "bo-requisition-reference-hint"; +BackOfficeTexts.REQUISITION_REFERENCE_HINT }
                }
                fieldSet {
                    legend { +BackOfficeTexts.REQUISITION_SEARCH }
                    p(classes = "bo-hint") { id = "bo-requisition-search-hint"; +BackOfficeTexts.REQUISITION_SEARCH_HINT }
                    requisitionField("bo-requisition-key", "cle", BackOfficeTexts.REQUISITION_KEY, InputType.text, required = false)
                    requisitionField("bo-requisition-event", "evenement", BackOfficeTexts.REQUISITION_EVENT, InputType.text, required = false)
                    requisitionField("bo-requisition-from", "du", BackOfficeTexts.REQUISITION_FROM, InputType.date, required = true)
                    requisitionField("bo-requisition-until", "au", BackOfficeTexts.REQUISITION_UNTIL, InputType.date, required = true)
                }
                p(classes = "bo-hint") { +BackOfficeTexts.REQUISITION_TWO_PERSON }
                button(type = ButtonType.submit, classes = "bo-button") { +BackOfficeTexts.REQUISITION_SUBMIT }
            }
        }
        section {
            attributes["aria-labelledby"] = "bo-requisitions-history"
            h2 { id = "bo-requisitions-history"; +BackOfficeTexts.REQUISITIONS_HISTORY }
            if (requisitions.isEmpty()) p { +BackOfficeTexts.REQUISITIONS_NONE }
            ul(classes = "bo-list") {
                requisitions.forEach { requisition ->
                    li {
                        h3(classes = "bo-item-title") { +"${BackOfficeTexts.REQUISITION_REFERENCE_SHORT} : ${requisition.reason.orEmpty()}" }
                        dl(classes = "bo-facts") {
                            dt { +BackOfficeTexts.REQUISITION_STATE }
                            dd {
                                +BackOfficeTexts.requisitionState(LegalRequisitions.state(requisition, now))
                                val operations = requisition.payload["operations"]
                                val found = requisition.payload["pseudonyms"]
                                if (operations != null && found != null) +" ($operations, $found ${BackOfficeTexts.REQUISITION_ANSWERED_ROWS})"
                            }
                            dt { +BackOfficeTexts.REQUISITION_SEARCH }
                            dd { +describeSearch(RequisitionSearch.of(requisition.payload)) }
                            dt { +BackOfficeTexts.APPROVAL_REQUESTED_BY }
                            dd {
                                +"${who(requisition.requestedBy)} ${BackOfficeTexts.APPROVAL_ON} "
                                dateTime(requisition.requestedAt)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun FlowContent.requisitionField(fieldId: String, name: String, text: String, type: InputType, required: Boolean) {
    div(classes = "bo-field") {
        label { htmlFor = fieldId; +text }
        input(type = type, name = name) {
            id = fieldId
            maxLength = "100"
            if (required) this.required = true
            attributes["aria-describedby"] = "bo-requisition-search-hint"
            attributes["autocomplete"] = "off"
            attributes["spellcheck"] = "false"
        }
    }
}

// Journal -----------------------------------------------------------------------------------------------

/** The filters of the journal as typed (kept to fill the form again and build the links). */
data class JournalForm(
    val from: String = "",
    val until: String = "",
    val account: String = "",
    val action: String = "",
    val target: String = "",
) {
    fun query(beforeSeq: Long? = null): String {
        val parts = listOf("du" to from, "au" to until, "compte" to account, "action" to action, "objet" to target)
            .filter { it.second.isNotEmpty() }
            .map { (key, value) -> key + "=" + java.net.URLEncoder.encode(value, Charsets.UTF_8) } +
            listOfNotNull(beforeSeq?.let { "avant=$it" })
        return if (parts.isEmpty()) "" else "?" + parts.joinToString("&")
    }
}

fun HTML.journalPage(auth: Authenticated, records: List<AuditRecord>, form: JournalForm, chain: AuditChain.Check, head: AuditHead?, notice: Notice?, olderThan: Long?) {
    backOfficeLayout(BackOfficeTexts.JOURNAL_TITLE, auth, current = "/journal", notice = notice) {
        h1 { +BackOfficeTexts.JOURNAL_TITLE }
        p { +BackOfficeTexts.JOURNAL_INTRO }
        when (chain) {
            is AuditChain.Check.Intact -> p(classes = "bo-notice") {
                attributes["role"] = "status"
                +"${BackOfficeTexts.JOURNAL_CHAIN_OK} (${chain.lines} ${BackOfficeTexts.JOURNAL_LINES})."
            }
            is AuditChain.Check.Broken -> p(classes = "bo-notice bo-notice-error") {
                attributes["role"] = "alert"
                +"${BackOfficeTexts.JOURNAL_CHAIN_BROKEN} ${chain.seq} (${chain.problem})."
            }
            is AuditChain.Check.Truncated -> p(classes = "bo-notice bo-notice-error") {
                attributes["role"] = "alert"
                +"${BackOfficeTexts.JOURNAL_TRUNCATED} ${chain.found}, ${BackOfficeTexts.JOURNAL_TRUNCATED_EXPECTED} ${chain.expected}."
            }
        }
        head?.let { last ->
            p {
                +"${BackOfficeTexts.JOURNAL_HEAD} ${last.seq}, ${BackOfficeTexts.JOURNAL_HEAD_HASH} "
                code { +last.hash }
                +" ("
                dateTime(last.recordedAt)
                +")."
            }
        }
        section {
            attributes["aria-labelledby"] = "bo-journal-filters"
            h2 { id = "bo-journal-filters"; +BackOfficeTexts.JOURNAL_FILTERS }
            form(action = "/journal", method = FormMethod.get) {
                attributes["role"] = "search"
                filterField("bo-from", "du", BackOfficeTexts.JOURNAL_FROM, form.from, InputType.date)
                filterField("bo-until", "au", BackOfficeTexts.JOURNAL_UNTIL, form.until, InputType.date)
                filterField("bo-account", "compte", BackOfficeTexts.JOURNAL_ACCOUNT, form.account, InputType.text)
                filterField("bo-action", "action", BackOfficeTexts.JOURNAL_ACTION, form.action, InputType.text)
                filterField("bo-target", "objet", BackOfficeTexts.JOURNAL_OBJECT, form.target, InputType.text)
                button(type = ButtonType.submit, classes = "bo-button") { +BackOfficeTexts.JOURNAL_FILTER_BUTTON }
            }
            p { a(href = "/journal.json" + form.query()) { +BackOfficeTexts.JOURNAL_EXPORT } }
        }
        section {
            if (records.isEmpty()) {
                p { +BackOfficeTexts.JOURNAL_EMPTY }
            } else {
                div(classes = "bo-table") {
                    table {
                        caption { +BackOfficeTexts.JOURNAL_TITLE }
                        thead {
                            tr {
                                listOf(
                                    BackOfficeTexts.JOURNAL_NUMBER, BackOfficeTexts.JOURNAL_DATE, BackOfficeTexts.JOURNAL_WHO, BackOfficeTexts.JOURNAL_WHAT,
                                    BackOfficeTexts.JOURNAL_TARGET, BackOfficeTexts.JOURNAL_REASON, BackOfficeTexts.JOURNAL_DETAILS,
                                ).forEach { heading -> th { attributes["scope"] = "col"; +heading } }
                            }
                        }
                        tbody {
                            records.forEach { record ->
                                tr {
                                    th { attributes["scope"] = "row"; +record.seq.toString() }
                                    td { dateTime(record.recordedAt) }
                                    td { +record.actorLabel }
                                    td {
                                        BackOfficeTexts.action(record.action)?.let { +"$it " }
                                        code { +record.action }
                                    }
                                    td { record.targetType?.let { +"$it " }; record.targetId?.let { code { +it } } }
                                    td { record.reason?.let { +it } }
                                    td { +record.details.entries.joinToString(", ") { "${it.key} = ${it.value}" } }
                                }
                            }
                        }
                    }
                }
            }
            olderThan?.let { before -> p { a(href = "/journal" + form.query(before)) { +BackOfficeTexts.JOURNAL_OLDER } } }
        }
    }
}

private fun FlowContent.filterField(fieldId: String, name: String, text: String, current: String, type: InputType) {
    div(classes = "bo-field") {
        label { htmlFor = fieldId; +text }
        input(type = type, name = name) {
            id = fieldId
            value = current
            maxLength = "100"
            attributes["autocomplete"] = "off"
        }
    }
}

// Keys --------------------------------------------------------------------------------------------------

fun HTML.keysPage(auth: Authenticated, keys: List<KeyInfo>, notice: Notice?) {
    backOfficeLayout(BackOfficeTexts.KEYS_TITLE, auth, current = "/cles", notice = notice, script = true) {
        h1 { +BackOfficeTexts.KEYS_TITLE }
        p { +BackOfficeTexts.KEYS_INTRO }
        section {
            p(classes = "bo-hint") { +BackOfficeTexts.KEYS_FINGERPRINT_HINT }
            ul(classes = "bo-list") {
                keys.forEach { key ->
                    li {
                        strong { +key.name }
                        +" : ${BackOfficeTexts.KEYS_CREATED} "
                        dateTime(key.createdAt)
                        +", "
                        key.lastUsedAt?.let { used -> +"${BackOfficeTexts.KEYS_USED} "; dateTime(used) } ?: +BackOfficeTexts.KEYS_NEVER_USED
                        fingerprint(key)
                        if (keys.size > 1) {
                            form(action = "/cles/${key.id}/suppression", method = FormMethod.post) {
                                csrfField(auth)
                                button(type = ButtonType.submit, classes = "bo-button-secondary") { +BackOfficeTexts.KEYS_DELETE }
                            }
                        }
                    }
                }
            }
        }
        section {
            attributes["aria-labelledby"] = "bo-add-key"
            h2 { id = "bo-add-key"; +BackOfficeTexts.KEYS_ADD_TITLE }
            label { htmlFor = "ppf-new-key-name"; +BackOfficeTexts.KEYS_ADD_NAME }
            input(type = InputType.text, name = "name") {
                id = "ppf-new-key-name"
                maxLength = KeyNames.MAX_LENGTH.toString()
                value = BackOfficeTexts.DEFAULT_BACKUP_KEY_NAME
                attributes["autocomplete"] = "off"
            }
            p(classes = "bo-hint") { +BackOfficeTexts.SENSITIVE_HINT }
            ceremonyButton("add-key", BackOfficeTexts.KEYS_ADD_BUTTON, "ppf-add-key-status", mapOf("csrf" to auth.csrf, "name-field" to "ppf-new-key-name"))
        }
        section(classes = "bo-danger") {
            attributes["aria-labelledby"] = "bo-self-cut"
            h2 { id = "bo-self-cut"; +BackOfficeTexts.KEYS_SELF_CUT_TITLE }
            p { +BackOfficeTexts.KEYS_SELF_CUT_TEXT }
            form(action = "/coupure", method = FormMethod.post) {
                csrfField(auth)
                button(type = ButtonType.submit, classes = "bo-button-danger") { +BackOfficeTexts.KEYS_SELF_CUT_BUTTON }
            }
        }
    }
}

/**
 * The short fingerprint of a passkey ([org.politisationpopulairefrancaise.core.platform.PasskeyFingerprint]),
 * the one the owner's phone shows before signing the team list (D-141).
 */
private fun FlowContent.fingerprint(key: KeyInfo) {
    p(classes = "bo-fingerprint") {
        +"${BackOfficeTexts.KEYS_FINGERPRINT} : "
        code { +(key.fingerprint ?: BackOfficeTexts.KEYS_FINGERPRINT_UNKNOWN) }
    }
}

// Validation sessions -----------------------------------------------------------------------------------

fun HTML.sessionsPage(auth: Authenticated, own: ValidationSession?, open: List<ValidationSession>, pseudonyms: Map<UUID, String>, notice: Notice?) {
    backOfficeLayout(BackOfficeTexts.SESSIONS_TITLE, auth, current = "/seances", notice = notice) {
        h1 { +BackOfficeTexts.SESSIONS_TITLE }
        p { +BackOfficeTexts.SESSIONS_INTRO }
        if (TeamRole.VALIDATOR in auth.actor.roles) {
            section {
                if (own != null) {
                    h2 { +BackOfficeTexts.SESSIONS_CURRENT }
                    sessionFacts(own)
                    form(action = "/seances/${own.id}/fermeture", method = FormMethod.post) {
                        csrfField(auth)
                        button(type = ButtonType.submit, classes = "bo-button-secondary") { +BackOfficeTexts.SESSIONS_CLOSE }
                    }
                } else if (auth.actor.has(Permissions.VALIDATION_SESSION_OPEN)) {
                    h2 { +BackOfficeTexts.SESSIONS_OPEN_TITLE }
                    form(action = "/seances", method = FormMethod.post) {
                        csrfField(auth)
                        label { htmlFor = "bo-session-key"; +BackOfficeTexts.SESSIONS_KEY }
                        input(type = InputType.text, name = "cle") {
                            id = "bo-session-key"
                            required = true
                            maxLength = "100"
                            attributes["autocomplete"] = "off"
                            attributes["spellcheck"] = "false"
                        }
                        label { htmlFor = "bo-session-max"; +BackOfficeTexts.SESSIONS_MAX }
                        input(type = InputType.number, name = "plafond") {
                            id = "bo-session-max"
                            min = "1"
                            max = ValidationSessions.MAX_VALIDATIONS.toString()
                            value = ValidationSessions.MAX_VALIDATIONS.toString()
                        }
                        label { htmlFor = "bo-session-hours"; +BackOfficeTexts.SESSIONS_HOURS }
                        input(type = InputType.number, name = "heures") {
                            id = "bo-session-hours"
                            min = "1"
                            max = ValidationSessions.MAX_DURATION.inWholeHours.toString()
                            value = ValidationSessions.DEFAULT_DURATION.inWholeHours.toString()
                        }
                        p(classes = "bo-hint") { +BackOfficeTexts.SENSITIVE_HINT }
                        button(type = ButtonType.submit, classes = "bo-button") { +BackOfficeTexts.SESSIONS_OPEN_BUTTON }
                    }
                }
            }
        }
        if (auth.actor.has(Permissions.TEAM_REVOKE)) {
            section {
                attributes["aria-labelledby"] = "bo-all-sessions"
                h2 { id = "bo-all-sessions"; +BackOfficeTexts.SESSIONS_ALL }
                if (open.isEmpty()) p { +BackOfficeTexts.SESSIONS_NONE }
                ul(classes = "bo-list") {
                    open.forEach { session ->
                        li {
                            h3 { +(pseudonyms[session.accountId] ?: BackOfficeTexts.ACTOR_UNKNOWN) }
                            sessionFacts(session)
                            form(action = "/seances/${session.id}/revocation", method = FormMethod.post) {
                                csrfField(auth)
                                reasonField("bo-session-revoke-${session.id}", required = false)
                                button(type = ButtonType.submit, classes = "bo-button-danger") { +BackOfficeTexts.SESSIONS_REVOKE }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun FlowContent.sessionFacts(session: ValidationSession) {
    dl(classes = "bo-facts") {
        dt { +BackOfficeTexts.SESSIONS_WEEK }
        dd { +session.week }
        dt { +BackOfficeTexts.SESSIONS_COUNT }
        dd { +"${session.validations} / ${session.maxValidations}" }
        dt { +BackOfficeTexts.SESSIONS_UNTIL }
        dd { dateTime(session.expiresAt) }
    }
}
