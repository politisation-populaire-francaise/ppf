package org.politisationpopulairefrancaise.server.backoffice

import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.caption
import kotlinx.html.code
import kotlinx.html.dd
import kotlinx.html.div
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.option
import kotlinx.html.p
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
import kotlinx.html.tr
import kotlinx.html.ul
import kotlinx.html.unsafe
import org.politisationpopulairefrancaise.core.platform.PasskeyFingerprint
import org.politisationpopulairefrancaise.core.platform.RootRequestItem
import org.politisationpopulairefrancaise.core.platform.RootRequests
import org.politisationpopulairefrancaise.core.platform.decodeCanonicalBase64Url
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val PARIS: ZoneId = ZoneId.of("Europe/Paris")
private val LOCAL_INPUT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

/** A key abbreviated for the tables: the first and last 8 characters. */
private fun short(key: String): String = if (key.length > 20) "${key.take(8)}…${key.takeLast(8)}" else key

/** A hexadecimal hash by groups of 4 characters, to compare or type it. */
fun groupedBy4(hex: String): String = hex.chunked(4).joinToString(" ")

/** The value of a `datetime-local` field for [epochSeconds], Paris time. */
fun localInput(epochSeconds: Long): String = LOCAL_INPUT.format(Instant.ofEpochSecond(epochSeconds).atZone(PARIS))

/** The page « Clés de la plateforme » (D-141). */
fun HTML.platformKeysPage(auth: Authenticated, overview: PlatformOverview, notice: Notice?) {
    backOfficeLayout(BackOfficeTexts.PLATFORM_TITLE, auth, current = BackOffice.PLATFORM_PATH, notice = notice) {
        h1 { +BackOfficeTexts.PLATFORM_TITLE }
        p { +BackOfficeTexts.PLATFORM_INTRO }
        if (!overview.signerConfigured) {
            div(classes = "bo-notice bo-notice-warning") { +BackOfficeTexts.PLATFORM_SIGNER_OFF }
            return@backOfficeLayout
        }
        if (overview.root == null) div(classes = "bo-notice bo-notice-error") { attributes["role"] = "alert"; +BackOfficeTexts.PLATFORM_NO_ROOT }
        val keys = overview.keys
        if (keys == null) {
            div(classes = "bo-notice bo-notice-error") { attributes["role"] = "alert"; +BackOfficeTexts.PLATFORM_SIGNER_UNAVAILABLE }
        } else if (!keys.rootConfigured && overview.root != null) {
            div(classes = "bo-notice bo-notice-error") { attributes["role"] = "alert"; +BackOfficeTexts.PLATFORM_NO_ROOT }
        }
        if (overview.warnings.isNotEmpty()) {
            section(classes = "bo-danger") {
                attributes["aria-labelledby"] = "bo-platform-warnings"
                h2 { id = "bo-platform-warnings"; +BackOfficeTexts.PLATFORM_WARNINGS }
                ul(classes = "bo-list") {
                    attributes["role"] = "alert"
                    overview.warnings.forEach { warning -> li { warningText(warning) } }
                }
            }
        }
        if (keys != null) {
            section {
                attributes["aria-labelledby"] = "bo-platform-keys"
                h2 { id = "bo-platform-keys"; +BackOfficeTexts.PLATFORM_KEYS_TITLE }
                if (keys.keys.isEmpty()) {
                    p { +BackOfficeTexts.PLATFORM_NO_KEYS }
                } else {
                    div(classes = "bo-table") {
                        table {
                            caption { +BackOfficeTexts.PLATFORM_KEYS_TITLE }
                            thead {
                                tr {
                                    listOf(
                                        BackOfficeTexts.PLATFORM_USE, BackOfficeTexts.PLATFORM_KEY, BackOfficeTexts.PLATFORM_STATE,
                                        BackOfficeTexts.PLATFORM_VALIDITY, BackOfficeTexts.PLATFORM_LAST_SIGNED,
                                    ).forEach { heading -> th { attributes["scope"] = "col"; +heading } }
                                }
                            }
                            tbody {
                                keys.keys.forEach { key ->
                                    tr {
                                        th { attributes["scope"] = "row"; +BackOfficeTexts.signingUse(key.use) }
                                        td { code { attributes["title"] = key.pubkey; +short(key.pubkey) } }
                                        td {
                                            +BackOfficeTexts.keyState(key.state)
                                            if (key.current) {
                                                +" "
                                                span(classes = "bo-badge") { +BackOfficeTexts.PLATFORM_IN_USE }
                                            }
                                        }
                                        td {
                                            val from = key.notBefore
                                            val until = key.notAfter
                                            if (from != null && until != null) {
                                                +"${BackOfficeTexts.PLATFORM_FROM} "
                                                dateTime(from)
                                                +" ${BackOfficeTexts.PLATFORM_UNTIL} "
                                                dateTime(until)
                                            }
                                            key.revokedFrom?.let { revoked ->
                                                +" (${BackOfficeTexts.PLATFORM_REVOKED_FROM} "
                                                dateTime(revoked)
                                                +")"
                                            }
                                        }
                                        td { key.lastSignedAt?.let { dateTime(it) } ?: +BackOfficeTexts.PLATFORM_NEVER }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            section {
                attributes["aria-labelledby"] = "bo-platform-team"
                h2 { id = "bo-platform-team"; +BackOfficeTexts.PLATFORM_TEAM_LIST }
                val list = overview.teamList
                if (keys.teamList == null || list == null) {
                    p { +BackOfficeTexts.PLATFORM_TEAM_LIST_NONE }
                } else {
                    p {
                        +"${BackOfficeTexts.PLATFORM_TEAM_LIST_HELD} "
                        dateTime(list.event.createdAt)
                        +", "
                        val end = list.notAfter
                        if (end != null) {
                            +"${BackOfficeTexts.PLATFORM_TEAM_LIST_END} "
                            dateTime(end)
                        } else {
                            +BackOfficeTexts.PLATFORM_TEAM_LIST_NO_END
                        }
                        +", ${list.members.size} ${BackOfficeTexts.PLATFORM_TEAM_LIST_MEMBERS}."
                    }
                }
            }
        }
        overview.preview?.let { plan ->
            section {
                attributes["aria-labelledby"] = "bo-platform-ceremony"
                h2 { id = "bo-platform-ceremony"; +BackOfficeTexts.PLATFORM_CEREMONY_TITLE }
                p { +BackOfficeTexts.PLATFORM_CEREMONY_TEXT }
                h3 { +BackOfficeTexts.PLATFORM_CEREMONY_PREVIEW }
                ul(classes = "bo-list") {
                    li { +"${plan.certificates.size} ${BackOfficeTexts.PLATFORM_CEREMONY_CERTIFICATES}" }
                    li { +"${plan.revocations.size} ${BackOfficeTexts.PLATFORM_CEREMONY_REVOCATIONS}" }
                    li { +"${plan.listed.size} ${BackOfficeTexts.PLATFORM_CEREMONY_MEMBERS}" }
                }
                h3 { +BackOfficeTexts.PLATFORM_EXCLUDED }
                if (plan.excluded.isEmpty()) {
                    p { +BackOfficeTexts.PLATFORM_EXCLUDED_NONE }
                } else {
                    ul(classes = "bo-list") {
                        plan.excluded.forEach { (candidate, why) -> li { strong { +candidate.account.pseudonym }; +" : ${BackOfficeTexts.exclusion(why)}" } }
                    }
                }
                form(action = "${BackOffice.PLATFORM_PATH}/ceremonie", method = FormMethod.post) {
                    csrfField(auth)
                    p(classes = "bo-hint") { +BackOfficeTexts.SENSITIVE_HINT }
                    button(type = ButtonType.submit, classes = "bo-button") { +BackOfficeTexts.PLATFORM_CEREMONY_BUTTON }
                }
            }
        }
        if (keys != null && overview.root != null) revocationForm(auth, overview)
        section {
            attributes["aria-labelledby"] = "bo-platform-prepared"
            h2 { id = "bo-platform-prepared"; +BackOfficeTexts.PLATFORM_PREPARED }
            if (overview.prepared.isEmpty()) {
                p { +BackOfficeTexts.PLATFORM_PREPARED_NONE }
            } else {
                ul(classes = "bo-list") {
                    overview.prepared.forEach { request ->
                        li {
                            a(href = requestPath(request.id)) { +kindLabel(request.kind) }
                            +" ${BackOfficeTexts.PLATFORM_PREPARED_BY} ${request.preparedBy}, "
                            dateTime(request.createdAt)
                            +", ${BackOfficeTexts.PLATFORM_EXPIRES} "
                            dateTime(request.expiresAt)
                        }
                    }
                }
            }
        }
        section {
            attributes["aria-labelledby"] = "bo-platform-copy"
            h2 { id = "bo-platform-copy"; +BackOfficeTexts.PLATFORM_COPY_TITLE }
            val copy = overview.copy
            when {
                copy == null -> p { +BackOfficeTexts.PLATFORM_COPY_NONE }
                copy.action == SignerJournalCopy.ACTION_BREAK -> p(classes = "bo-notice bo-notice-error") { attributes["role"] = "alert"; +BackOfficeTexts.PLATFORM_COPY_BROKEN }
                copy.action == SignerJournalCopy.ACTION_RESUMED -> p { +"${BackOfficeTexts.PLATFORM_COPY_RESUMED} ${copy.details["after_seq"].orEmpty()}." }
                else -> p {
                    +"${BackOfficeTexts.PLATFORM_COPY_AT} ${copy.details["seq"].orEmpty()}, ${BackOfficeTexts.JOURNAL_HEAD_HASH} "
                    code { +copy.details["hash"].orEmpty() }
                    +" ("
                    dateTime(copy.recordedAt)
                    +")."
                }
            }
        }
    }
}

private fun FlowContent.warningText(warning: PlatformWarning) {
    when (warning) {
        PlatformWarning.NoTeamList -> +BackOfficeTexts.WARNING_NO_TEAM_LIST
        is PlatformWarning.TeamListExpired -> +BackOfficeTexts.WARNING_TEAM_LIST_EXPIRED
        is PlatformWarning.TeamListEnds -> {
            +"${BackOfficeTexts.WARNING_TEAM_LIST_ENDS} "
            dateTime(warning.at)
            +"."
        }
        is PlatformWarning.KeyEnds -> {
            +"${BackOfficeTexts.WARNING_KEY_ENDS_PREFIX} ${BackOfficeTexts.signingUse(warning.use)} "
            code { +short(warning.key) }
            +" ${BackOfficeTexts.WARNING_KEY_ENDS} "
            dateTime(warning.at)
            +"."
        }
        is PlatformWarning.AwaitingCertification -> +"${warning.count} ${BackOfficeTexts.WARNING_AWAITING}"
        is PlatformWarning.NoCurrentKey -> +"${BackOfficeTexts.WARNING_NO_CURRENT_KEY} ${BackOfficeTexts.signingUse(warning.use)}."
    }
}

private fun FlowContent.revocationForm(auth: Authenticated, overview: PlatformOverview) {
    val revocable = overview.keys?.keys.orEmpty().filter { it.notBefore != null && it.notAfter != null }
    section(classes = "bo-danger") {
        attributes["aria-labelledby"] = "bo-platform-revoke"
        h2 { id = "bo-platform-revoke"; +BackOfficeTexts.PLATFORM_REVOKE_TITLE }
        p { strong { +BackOfficeTexts.PLATFORM_REVOKE_TEXT } }
        if (revocable.isEmpty()) {
            p { +BackOfficeTexts.PLATFORM_REVOKE_NONE }
            return@section
        }
        form(action = "${BackOffice.PLATFORM_PATH}/revocation", method = FormMethod.post) {
            csrfField(auth)
            label { htmlFor = "bo-revoke-key"; +BackOfficeTexts.PLATFORM_REVOKE_KEY }
            select {
                id = "bo-revoke-key"
                name = "cle"
                required = true
                revocable.forEach { key ->
                    option {
                        value = key.pubkey
                        +"${BackOfficeTexts.signingUse(key.use)} : ${short(key.pubkey)} (${BackOfficeTexts.keyState(key.state)})"
                    }
                }
            }
            label { htmlFor = "bo-revoke-date"; +BackOfficeTexts.PLATFORM_REVOKE_DATE }
            input(type = InputType.dateTimeLocal, name = "date") {
                id = "bo-revoke-date"
                value = localInput(overview.now)
                max = localInput(overview.now + 60)
            }
            label { htmlFor = "bo-revoke-reason"; +BackOfficeTexts.PLATFORM_REVOKE_REASON }
            textArea {
                id = "bo-revoke-reason"
                name = "motif"
                attributes["required"] = "required"
                attributes["maxlength"] = AuditEntry.MAX_REASON_LENGTH.toString()
                attributes["aria-describedby"] = "bo-revoke-reason-hint"
            }
            p(classes = "bo-hint") { id = "bo-revoke-reason-hint"; +BackOfficeTexts.REASON_PERMANENT_HINT }
            p(classes = "bo-hint") { +BackOfficeTexts.SENSITIVE_HINT }
            button(type = ButtonType.submit, classes = "bo-button-danger") { +BackOfficeTexts.PLATFORM_REVOKE_BUTTON }
        }
    }
}

fun requestPath(id: String): String = "${BackOffice.PLATFORM_PATH}/demandes/$id"

private fun kindLabel(kind: RootRequestKind): String = when (kind) {
    RootRequestKind.CEREMONY -> BackOfficeTexts.PLATFORM_KIND_CEREMONY
    RootRequestKind.REVOCATION -> BackOfficeTexts.PLATFORM_KIND_REVOCATION
}

/**
 * The page of a request for the root: the QR code (inline SVG, [QrSvg]), its text and the hash by groups of
 * 4, then everything that is asked, in clear, read from the events themselves, each with its state as the
 * service now holds it (reload the page: no script).
 */
fun HTML.rootRequestPage(auth: Authenticated, request: PreparedRequest, qrText: String, root: String, states: Map<String, EventState>?, notice: Notice? = null) {
    backOfficeLayout(BackOfficeTexts.REQUEST_TITLE, auth, current = BackOffice.PLATFORM_PATH, notice = notice) {
        h1 { +"${BackOfficeTexts.REQUEST_TITLE} : ${kindLabel(request.kind)}" }
        p { +BackOfficeTexts.REQUEST_INTRO }
        section(classes = "bo-qr") {
            div(classes = "bo-qr-code") { unsafe { +QrSvg.render(qrText, BackOfficeTexts.REQUEST_QR_LABEL) } }
            dl(classes = "bo-facts") {
                dt { +BackOfficeTexts.REQUEST_TEXT }
                dd { code(classes = "bo-wrap") { +qrText } }
                dt { +BackOfficeTexts.REQUEST_HASH }
                dd { code(classes = "bo-wrap") { +groupedBy4(request.sha256) } }
            }
            p {
                +"${BackOfficeTexts.REQUEST_EXPIRES} "
                dateTime(request.expiresAt)
                +BackOfficeTexts.REQUEST_EXPIRES_AFTER
            }
            p { +BackOfficeTexts.REQUEST_RELOAD }
        }
        section {
            attributes["aria-labelledby"] = "bo-request-content"
            h2 { id = "bo-request-content"; +BackOfficeTexts.REQUEST_CONTENT }
            ul(classes = "bo-list") {
                request.events.forEach { event ->
                    li {
                        val state = states?.get(event.idHex())
                        when (val item = RootRequests.read(event, root)) {
                            is RootRequestItem.Certificate -> {
                                h3 { +BackOfficeTexts.REQUEST_CERTIFICATE }
                                keyFacts(item.use.id, item.key, item.notBefore, item.notAfter)
                            }
                            is RootRequestItem.Revocation -> {
                                h3 { +BackOfficeTexts.REQUEST_REVOCATION }
                                keyFacts(item.use.id, item.key, item.notBefore, item.notAfter)
                                p {
                                    strong {
                                        +"${BackOfficeTexts.REQUEST_REVOCATION} ${BackOfficeTexts.REQUEST_REVOKED_FROM} "
                                        dateTime(item.revokedFrom)
                                    }
                                }
                            }
                            is RootRequestItem.Team -> {
                                h3 {
                                    +"${BackOfficeTexts.REQUEST_TEAM}, ${BackOfficeTexts.REQUEST_TEAM_UNTIL} "
                                    dateTime(item.notAfter)
                                }
                                teamFacts(item, request)
                            }
                            null -> h3 { +BackOfficeTexts.REQUEST_UNKNOWN_EVENT }
                        }
                        p {
                            span(classes = if (state == EventState.RECEIVED) "bo-badge" else "bo-badge bo-badge-warning") {
                                +when (state) {
                                    EventState.RECEIVED -> BackOfficeTexts.REQUEST_STATE_RECEIVED
                                    EventState.WAITING -> BackOfficeTexts.REQUEST_STATE_WAITING
                                    null -> BackOfficeTexts.REQUEST_STATE_UNKNOWN
                                }
                            }
                        }
                    }
                }
            }
        }
        p { a(href = BackOffice.PLATFORM_PATH) { +BackOfficeTexts.REQUEST_BACK } }
    }
}

private fun FlowContent.keyFacts(use: String, key: String, notBefore: Long, notAfter: Long) {
    dl(classes = "bo-facts") {
        dt { +BackOfficeTexts.PLATFORM_USE }
        dd { +BackOfficeTexts.signingUse(use) }
        dt { +BackOfficeTexts.PLATFORM_KEY }
        dd { code(classes = "bo-wrap") { +key } }
        dt { +BackOfficeTexts.PLATFORM_VALIDITY }
        dd {
            +"${BackOfficeTexts.PLATFORM_FROM} "
            dateTime(notBefore)
            +" ${BackOfficeTexts.PLATFORM_UNTIL} "
            dateTime(notAfter)
        }
    }
}

private fun FlowContent.teamFacts(team: RootRequestItem.Team, request: PreparedRequest) {
    ul(classes = "bo-list") {
        team.members.forEach { member ->
            li {
                strong { +(request.pseudonyms[member.account] ?: BackOfficeTexts.ACTOR_UNKNOWN) }
                +" ("
                code { +member.account }
                +")"
                dl(classes = "bo-facts") {
                    dt { +BackOfficeTexts.REQUEST_MEMBER_SINCE }
                    dd { dateTime(member.validFrom) }
                    dt { +BackOfficeTexts.REQUEST_MEMBER_NOSTR }
                    dd { member.nostrKey?.let { code(classes = "bo-wrap") { +it } } ?: +BackOfficeTexts.REQUEST_MEMBER_NO_NOSTR }
                    dt { +BackOfficeTexts.REQUEST_MEMBER_USES }
                    dd { +member.uses.map { BackOfficeTexts.signingUse(it.id) }.sorted().joinToString(", ") }
                    dt { +BackOfficeTexts.REQUEST_MEMBER_KEYS }
                    dd {
                        ul {
                            member.credentials.forEach { credential ->
                                li {
                                    val fingerprint = decodeCanonicalBase64Url(credential.publicKey)?.let(PasskeyFingerprint::of)
                                    code { +(fingerprint ?: BackOfficeTexts.KEYS_FINGERPRINT_UNKNOWN) }
                                    request.keyNames[credential.id]?.let { +" ($it)" }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (request.excluded.isNotEmpty()) {
        p { +BackOfficeTexts.PLATFORM_EXCLUDED }
        ul(classes = "bo-list") {
            request.excluded.forEach { (pseudonym, why) -> li { strong { +pseudonym }; +" : ${BackOfficeTexts.exclusion(why)}" } }
        }
    }
}
