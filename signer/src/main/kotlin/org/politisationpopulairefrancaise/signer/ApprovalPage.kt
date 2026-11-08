package org.politisationpopulairefrancaise.signer

import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.code
import kotlinx.html.dd
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.id
import kotlinx.html.lang
import kotlinx.html.li
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.noScript
import kotlinx.html.p
import kotlinx.html.pre
import kotlinx.html.script
import kotlinx.html.section
import kotlinx.html.stream.createHTML
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.title
import kotlinx.html.tr
import kotlinx.html.ul
import kotlinx.html.caption

/**
 * The approval page, rendered by the signing service from the draft it holds (contract, section 5): the
 * use, the kind, every tag, the content as escaped text, the id, the approvals so far. No inline script or
 * style: one script and one style sheet, both served by the service itself ([approvalModule]).
 */
internal object ApprovalPage {
    const val SCRIPT_PATH = "/approbation/static/approbation.js"
    const val STYLE_PATH = "/approbation/static/approbation.css"

    fun render(view: ApprovalView, config: SignerConfig): String = page(ApprovalTexts.TITLE, config) {
        h1 { +ApprovalTexts.HEADING }
        if (view.signed) {
            p(classes = "notice") { +ApprovalTexts.SIGNED }
            return@page
        }
        p(classes = "lead") { +ApprovalTexts.INTRO }
        p(classes = "hint") { +ApprovalTexts.originHint(config.approvalOrigin) }
        val event = view.event
        section {
            h2 { +ApprovalTexts.DETAILS }
            dl {
                dt { +ApprovalTexts.USE }
                dd { +ApprovalTexts.useLabel(view.use) }
                if (event != null) {
                    dt { +ApprovalTexts.KIND }
                    dd { +ApprovalTexts.kindLabel(event.kind) }
                    dt { +ApprovalTexts.EVENT_DATE }
                    dd { +ApprovalTexts.date(event.createdAt) }
                    dt { +ApprovalTexts.WORKING_KEY }
                    dd { code(classes = "long") { +event.pubkey } }
                }
                dt { +ApprovalTexts.EVENT_ID }
                dd { code(classes = "long") { +view.eventId } }
            }
        }
        if (event == null) {
            p(classes = "notice") { +ApprovalTexts.CONTENT_UNKNOWN }
        } else {
            section {
                h2 { +ApprovalTexts.TAGS }
                if (event.tags.isEmpty()) {
                    p { +ApprovalTexts.NO_TAG }
                } else {
                    table {
                        caption { +ApprovalTexts.TAGS_CAPTION }
                        thead {
                            tr {
                                th { attributes["scope"] = "col"; +ApprovalTexts.TAG_NAME }
                                th { attributes["scope"] = "col"; +ApprovalTexts.TAG_VALUES }
                            }
                        }
                        tbody {
                            for (tag in event.tags) {
                                tr {
                                    td { code { +tag.firstOrNull().orEmpty() } }
                                    td { tag.drop(1).forEach { value -> code(classes = "value") { +value } } }
                                }
                            }
                        }
                    }
                }
            }
            section {
                h2 { +ApprovalTexts.CONTENT }
                if (event.content.isEmpty()) p { +ApprovalTexts.EMPTY_CONTENT } else pre(classes = "content") { +event.content }
            }
        }
        section {
            h2 { +ApprovalTexts.APPROVALS }
            p(classes = "count") {
                +if (view.doubleValidation) ApprovalTexts.doubleCount(view.approvals.size) else ApprovalTexts.singleCount(view.approvals.size)
            }
            if (view.doubleValidation) p { +ApprovalTexts.DOUBLE_RULE }
            if (view.approvals.isEmpty()) {
                p { +ApprovalTexts.NO_APPROVAL }
            } else {
                ul { view.approvals.forEach { li { +ApprovalTexts.approvedBy(it.account, it.at) } } }
            }
            if (view.approvals.size >= view.required) p(classes = "notice") { +ApprovalTexts.READY }
        }
        if (event != null && view.approvals.size < view.required) {
            section {
                button(type = ButtonType.button, classes = "approve") {
                    id = "approuver"
                    attributes["data-use"] = view.use.id
                    attributes["data-event"] = view.eventId
                    attributes["data-status"] = "etat"
                    attributes["data-msg-working"] = ApprovalTexts.MSG_WORKING
                    attributes["data-msg-done"] = ApprovalTexts.MSG_DONE
                    attributes["data-msg-failed"] = ApprovalTexts.MSG_FAILED
                    attributes["data-msg-unsupported"] = ApprovalTexts.MSG_UNSUPPORTED
                    +ApprovalTexts.BUTTON
                }
                p(classes = "status") {
                    id = "etat"
                    attributes["role"] = "status"
                    attributes["aria-live"] = "polite"
                }
                noScript { p { +ApprovalTexts.NOSCRIPT } }
            }
        }
    }

    /** The page shown when no such draft is known. */
    fun unknown(config: SignerConfig): String = page(ApprovalTexts.UNKNOWN_TITLE, config) {
        h1 { +ApprovalTexts.UNKNOWN_TITLE }
        p { +ApprovalTexts.UNKNOWN }
    }

    private fun page(pageTitle: String, config: SignerConfig, content: FlowContent.() -> Unit): String {
        val html: HTML.() -> Unit = {
            lang = ApprovalTexts.LANGUAGE
            head {
                meta(charset = "utf-8")
                meta(name = "viewport", content = "width=device-width, initial-scale=1")
                meta(name = "color-scheme", content = "light dark")
                meta(name = "robots", content = "noindex, nofollow")
                meta(name = "referrer", content = "no-referrer")
                title { +pageTitle }
                link(rel = "stylesheet", href = STYLE_PATH)
                script(src = SCRIPT_PATH) { attributes["defer"] = "defer" }
            }
            body {
                main {
                    content()
                    p(classes = "back") { a(href = config.backOfficeOrigin + "/") { +ApprovalTexts.BACK } }
                }
            }
        }
        return "<!DOCTYPE html>\n" + createHTML().html(block = html)
    }
}
