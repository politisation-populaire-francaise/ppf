package org.politisationpopulairefrancaise.server.backoffice

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.parseQueryString
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.html.respondHtml
import io.ktor.server.http.content.staticResources
import io.ktor.server.request.contentLength
import io.ktor.server.request.path
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.RoutingResolveContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.html.HTML
import kotlinx.html.html
import kotlinx.html.stream.createHTML
import kotlinx.io.readByteArray
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlin.time.Duration.Companion.hours

/** The session cookie: `__Host-` prefix (Secure, Path=/, no Domain), unreadable by scripts, never sent cross-site. */
const val SESSION_COOKIE: String = "__Host-ppf-equipe"

/** Every request body of the back-office is read up to this size, chunked bodies included; above, 413. */
const val MAX_BODY_BYTES: Int = 64 * 1024

/**
 * Routes of a port: matches only the calls received on [port] ([inverse]: every other port). The public
 * site wraps its routes in the inverse selector, the back-office in the direct one, so that no path of
 * one is ever answered on the port of the other (contract, section 3).
 */
class PortSelector(private val port: Int, private val inverse: Boolean = false) : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        val matches = (context.call.request.local.localPort == port) != inverse
        return if (matches) RouteSelectorEvaluation.Constant else RouteSelectorEvaluation.Failed
    }

    override fun toString(): String = if (inverse) "(port != $port)" else "(port $port)"
}

/** The routes of the public site and API: never on the back-office port (when there is one). */
fun Route.publicPort(backOfficePort: Int?, build: Route.() -> Unit) {
    if (backOfficePort == null) build() else createChild(PortSelector(backOfficePort, inverse = true)).build()
}

/**
 * Headers of every answer of the back-office port, even a 404: never kept by a cache, never indexed.
 * The other security headers are those of the site (DefaultHeaders of Application.module).
 */
fun backOfficeHeaders(port: Int) = createApplicationPlugin("BackOfficeHeaders") {
    onCall { call ->
        if (call.request.local.localPort == port) {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.response.header("X-Robots-Tag", "noindex, nofollow")
        }
    }
}

@Serializable
private data class EnrolStart(val invitation: String)

private val exportJson = Json { prettyPrint = true }

/** The JSON of the ceremonies: strict, as the script writes it. */
private val ceremonyJson = Json

/** Every route of the back-office, to mount under `PortSelector(backOffice.config.port)`. */
fun Route.backOfficeRoutes(backOffice: BackOffice) {
    val config = backOffice.config
    staticResources("/static/fonts", "static/fonts")
    staticResources("/static", "backoffice")

    // Sign-in -----------------------------------------------------------------------------------------
    get("/connexion") {
        if (call.authenticated(backOffice) != null) return@get call.respondRedirect("/")
        call.respondHtml { signInPage(noticeOf(call.request.queryParameters)) }
    }
    jsonPost("/webauthn/connexion/options", config) { _ ->
        when (val outcome = io { backOffice.startSignIn() }) {
            is Outcome.Ok -> call.respond(outcome.value)
            is Outcome.Denied -> call.refuse(outcome.code)
        }
    }
    jsonPost("/webauthn/connexion", config) { body ->
        val response = decode(AssertionResponse.serializer(), body) ?: return@jsonPost call.refuse("bad_form")
        when (val outcome = io { backOffice.finishSignIn(response) }) {
            is Outcome.Ok -> {
                call.setSessionCookie(outcome.value.token)
                call.respond(CeremonyDone("/"))
            }
            is Outcome.Denied -> call.refuse(outcome.code)
        }
    }
    formPost("/deconnexion", backOffice) { auth, _ ->
        io { backOffice.signOut(auth) }
        call.clearSessionCookie()
        call.respondRedirect("/connexion?ok=signed_out")
    }

    // Invitation ----------------------------------------------------------------------------------------
    get("/invitation/{token}") {
        val token = call.parameters["token"].orEmpty()
        val invitation = io { backOffice.findInvitation(token) }
        if (invitation == null) {
            call.respondHtml(HttpStatusCode.NotFound) { messagePage(BackOfficeTexts.INVITATION_GONE_TITLE, BackOfficeTexts.INVITATION_GONE_TEXT) }
        } else {
            call.respondHtml { enrolPage(invitation, token) }
        }
    }
    jsonPost("/webauthn/inscription/options", config) { body ->
        val start = decode(EnrolStart.serializer(), body) ?: return@jsonPost call.refuse("bad_form")
        when (val outcome = io { backOffice.startEnrolment(start.invitation) }) {
            is Outcome.Ok -> call.respond(outcome.value)
            is Outcome.Denied -> call.refuse(outcome.code)
        }
    }
    jsonPost("/webauthn/inscription", config) { body ->
        val response = decode(RegistrationResponse.serializer(), body) ?: return@jsonPost call.refuse("bad_form")
        when (val outcome = io { backOffice.finishEnrolment(response) }) {
            is Outcome.Ok -> {
                call.setSessionCookie(outcome.value.token)
                call.respond(CeremonyDone("/cles?ok=welcome"))
            }
            is Outcome.Denied -> call.refuse(outcome.code)
        }
    }

    // Confirmation and keys of the signed-in member -------------------------------------------------------
    jsonPost("/webauthn/confirmation/options", config) { _ ->
        val auth = call.jsonAuth(backOffice, null) ?: return@jsonPost
        when (val outcome = io { backOffice.startConfirmation(auth) }) {
            is Outcome.Ok -> call.respond(outcome.value)
            is Outcome.Denied -> call.refuse(outcome.code)
        }
    }
    jsonPost("/webauthn/confirmation", config) { body ->
        val response = decode(AssertionResponse.serializer(), body) ?: return@jsonPost call.refuse("bad_form")
        val auth = call.jsonAuth(backOffice, response.csrf) ?: return@jsonPost
        when (val outcome = io { backOffice.finishConfirmation(auth, response) }) {
            is Outcome.Ok -> call.respond(CeremonyDone(""))
            is Outcome.Denied -> call.refuse(outcome.code)
        }
    }
    // Adding a key: a verification of an existing key first (at most 60 seconds), then the new key.
    jsonPost("/webauthn/cle/preuve/options", config) { _ ->
        val auth = call.jsonAuth(backOffice, null) ?: return@jsonPost
        when (val outcome = io { backOffice.startAddKeyProof(auth) }) {
            is Outcome.Ok -> call.respond(outcome.value)
            is Outcome.Denied -> call.refuse(outcome.code)
        }
    }
    jsonPost("/webauthn/cle/options", config) { body ->
        val proof = decode(AssertionResponse.serializer(), body) ?: return@jsonPost call.refuse("bad_form")
        val auth = call.jsonAuth(backOffice, proof.csrf) ?: return@jsonPost
        when (val outcome = io { backOffice.startAddKey(auth, proof) }) {
            is Outcome.Ok -> call.respond(outcome.value)
            is Outcome.Denied -> call.refuse(outcome.code)
        }
    }
    jsonPost("/webauthn/cle", config) { body ->
        val response = decode(RegistrationResponse.serializer(), body) ?: return@jsonPost call.refuse("bad_form")
        val auth = call.jsonAuth(backOffice, response.csrf) ?: return@jsonPost
        when (val outcome = io { backOffice.finishAddKey(auth, response) }) {
            is Outcome.Ok -> call.respond(CeremonyDone("/cles?ok=key_added"))
            is Outcome.Denied -> call.refuse(outcome.code)
        }
    }
    get("/cles") {
        val auth = call.requireAuth(backOffice) ?: return@get
        val keys = io { backOffice.keysOf(auth.account.id) }
        call.respondHtml { keysPage(auth, keys, noticeOf(call.request.queryParameters)) }
    }
    formPost("/cles/{id}/suppression", backOffice) { auth, form ->
        val id = call.uuidParameter("id") ?: return@formPost call.notFound(auth)
        call.afterAction(auth, form, io { backOffice.deleteKey(auth, id) }, "/cles", "key_deleted")
    }
    formPost("/coupure", backOffice) { auth, _ ->
        when (val outcome = io { backOffice.emergencyCut(auth, auth.account.id, null) }) {
            is Outcome.Ok -> {
                call.clearSessionCookie()
                call.respondRedirect("/connexion?ok=self_cut")
            }
            is Outcome.Denied -> call.redirectWith("/cles", error = outcome.code)
        }
    }

    // Dashboard and alerts ---------------------------------------------------------------------------------
    get("/") {
        val auth = call.requireAuth(backOffice) ?: return@get
        val dashboard = io { backOffice.dashboard(auth.actor) }
        call.respondHtml { dashboardPage(auth, dashboard, noticeOf(call.request.queryParameters)) }
    }
    formPost("/alertes/{id}/fermeture", backOffice) { auth, form ->
        val id = call.uuidParameter("id") ?: return@formPost call.notFound(auth)
        call.afterAction(auth, form, io { backOffice.dismissAlert(auth, id) }, "/", "dismissed")
    }

    // Queues ------------------------------------------------------------------------------------------------
    get("/files/{queue}") {
        val auth = call.requireAuth(backOffice) ?: return@get
        val queue = backOffice.queues.find(call.parameters["queue"].orEmpty())?.takeIf { auth.actor.has(it.viewPermission) }
            ?: return@get call.notFound(auth)
        val cursor = call.request.queryParameters["curseur"]?.take(200)
        val page = io { runCatching { queue.page(cursor, QUEUE_PAGE) }.getOrNull() }
            ?: return@get call.respondHtml(HttpStatusCode.ServiceUnavailable) { messagePage(queue.title, BackOfficeTexts.error("queue_unavailable"), auth) }
        call.respondHtml { queuePage(auth, queue, page, noticeOf(call.request.queryParameters)) }
    }
    get("/files/{queue}/{item}") {
        val auth = call.requireAuth(backOffice) ?: return@get
        val queue = backOffice.queues.find(call.parameters["queue"].orEmpty())?.takeIf { auth.actor.has(it.viewPermission) }
            ?: return@get call.notFound(auth)
        val item = call.parameters["item"].orEmpty().take(200)
        val actions = io { runCatching { queue.actions(item) }.getOrDefault(emptyList()) }
        // The detail of an item may hold a citizen's content: it is rendered by the queue, never logged.
        val html = io { renderToString { queueItemPage(auth, queue, item, actions, noticeOf(call.request.queryParameters)) } }
        call.respondText(html, ContentType.Text.Html.withParameter("charset", "utf-8"))
    }
    formPost("/files/{queue}/{item}", backOffice) { auth, form ->
        val queueId = call.parameters["queue"].orEmpty()
        val item = call.parameters["item"].orEmpty().take(200)
        val action = form["action"].orEmpty()
        val back = itemPath(queueId, item)
        when (val outcome = io { backOffice.performQueueAction(auth, queueId, item, action, form["motif"]) }) {
            is Outcome.Denied -> if (outcome.code == "confirmation_required") call.confirm(auth, form) else call.redirectWith(back, error = outcome.code)
            is Outcome.Ok -> when (val result = outcome.value) {
                is QueueResult.Waiting -> call.redirectWith(queuePath(queueId), ok = "requested")
                is QueueResult.Performed -> when (val done = result.outcome) {
                    ActionOutcome.Done -> call.redirectWith(queuePath(queueId), ok = "done")
                    ActionOutcome.NotFound -> call.redirectWith(back, error = "not_found")
                    ActionOutcome.AlreadyDecided -> call.redirectWith(back, error = "action_unavailable")
                    // The refusal of a queue is its own French text: shown on the page, not in the address.
                    is ActionOutcome.Refused -> call.respondHtml(HttpStatusCode.Conflict) { messagePage(BackOfficeTexts.ITEM_ACTIONS, done.reason, auth) }
                }
            }
        }
    }

    // Team --------------------------------------------------------------------------------------------------
    get("/equipe") {
        val auth = call.requireAuth(backOffice) ?: return@get
        if (TEAM_PERMISSIONS.none(auth.actor::has)) return@get call.forbidden(auth)
        val (members, invitations) = io {
            backOffice.members().map { MemberLine(it, backOffice.liveGrants(it.id)) } to backOffice.pendingInvitations()
        }
        call.respondHtml { teamPage(auth, members, invitations, noticeOf(call.request.queryParameters)) }
    }
    formPost("/equipe/invitations", backOffice) { auth, form ->
        val roles = form.getAll("roles").orEmpty().mapNotNull(TeamRole::fromId)
        val end = when (val parsed = parseEndDate(form["fin"])) {
            is Outcome.Denied -> return@formPost call.redirectWith("/equipe", error = parsed.code)
            is Outcome.Ok -> parsed.value
        }
        when (val outcome = io { backOffice.createInvitation(auth, form["pseudonyme"].orEmpty(), roles, form["organisation"], end) }) {
            is Outcome.Ok -> call.respondHtml { invitationIssuedPage(auth, outcome.value.invitation, "${config.origin}/invitation/${outcome.value.token}") }
            is Outcome.Denied -> if (outcome.code == "confirmation_required") call.confirm(auth, form) else call.redirectWith("/equipe", error = outcome.code)
        }
    }
    formPost("/equipe/invitations/{id}/retrait", backOffice) { auth, form ->
        val id = call.uuidParameter("id") ?: return@formPost call.notFound(auth)
        call.afterAction(auth, form, io { backOffice.withdrawInvitation(auth, id) }, "/equipe", "withdrawn")
    }
    get("/equipe/membres/{id}") {
        val auth = call.requireAuth(backOffice) ?: return@get
        if (TEAM_PERMISSIONS.none(auth.actor::has)) return@get call.forbidden(auth)
        val id = call.uuidParameter("id") ?: return@get call.notFound(auth)
        val account = io { backOffice.member(id) } ?: return@get call.notFound(auth)
        val (grants, keys) = io { backOffice.grantHistory(id) to backOffice.keysOf(id) }
        call.respondHtml { memberPage(auth, account, grants, keys, noticeOf(call.request.queryParameters)) }
    }
    formPost("/equipe/membres/{id}/roles", backOffice) { auth, form ->
        val id = call.uuidParameter("id") ?: return@formPost call.notFound(auth)
        val back = "/equipe/membres/$id"
        val role = TeamRole.fromId(form["role"].orEmpty()) ?: return@formPost call.redirectWith(back, error = "bad_form")
        val end = when (val parsed = parseEndDate(form["fin"])) {
            is Outcome.Denied -> return@formPost call.redirectWith(back, error = parsed.code)
            is Outcome.Ok -> parsed.value
        }
        call.afterAction(auth, form, io { backOffice.requestGrant(auth, id, role, form["organisation"], end, form["motif"]) }, back, "requested")
    }
    formPost("/equipe/membres/{id}/roles/{grant}/retrait", backOffice) { auth, form ->
        val id = call.uuidParameter("id") ?: return@formPost call.notFound(auth)
        val grant = call.uuidParameter("grant") ?: return@formPost call.notFound(auth)
        call.afterChange(auth, form, io { backOffice.revokeGrant(auth, grant, form["motif"]) }, "/equipe/membres/$id", "done")
    }
    formPost("/equipe/membres/{id}/coupure", backOffice) { auth, form ->
        val id = call.uuidParameter("id") ?: return@formPost call.notFound(auth)
        call.afterAction(auth, form, io { backOffice.emergencyCut(auth, id, form["motif"]) }, "/equipe/membres/$id", "cut")
    }
    formPost("/equipe/membres/{id}/retrait", backOffice) { auth, form ->
        val id = call.uuidParameter("id") ?: return@formPost call.notFound(auth)
        call.afterChange(auth, form, io { backOffice.removeMember(auth, id, form["motif"]) }, "/equipe/membres/$id", "removed")
    }
    formPost("/equipe/membres/{id}/reintegration", backOffice) { auth, form ->
        val id = call.uuidParameter("id") ?: return@formPost call.notFound(auth)
        call.afterAction(auth, form, io { backOffice.requestReinstatement(auth, id, form["motif"]) }, "/equipe/membres/$id", "requested")
    }
    formPost("/equipe/membres/{id}/cles/{key}/suppression", backOffice) { auth, form ->
        val id = call.uuidParameter("id") ?: return@formPost call.notFound(auth)
        val key = call.uuidParameter("key") ?: return@formPost call.notFound(auth)
        call.afterAction(auth, form, io { backOffice.deleteMemberKey(auth, id, key) }, "/equipe/membres/$id", "key_deleted")
    }

    // Two-person rule ------------------------------------------------------------------------------------------
    get("/validations") {
        val auth = call.requireAuth(backOffice) ?: return@get
        val (open, pseudonyms) = io { backOffice.openApprovals(auth.actor) to backOffice.members().associate { it.id to it.pseudonym } }
        call.respondHtml { approvalsPage(auth, open, pseudonyms, noticeOf(call.request.queryParameters)) }
    }
    formPost("/validations/{id}/accord", backOffice) { auth, form ->
        val id = call.uuidParameter("id") ?: return@formPost call.notFound(auth)
        when (val outcome = io { backOffice.approve(auth, id) }) {
            is Outcome.Ok -> call.redirectWith("/validations", ok = if (outcome.value == ApprovalStatus.FAILED) "failed" else "approved")
            is Outcome.Denied -> if (outcome.code == "confirmation_required") call.confirm(auth, form) else call.redirectWith("/validations", error = outcome.code)
        }
    }
    formPost("/validations/{id}/refus", backOffice) { auth, form ->
        val id = call.uuidParameter("id") ?: return@formPost call.notFound(auth)
        call.afterAction(auth, form, io { backOffice.refuseApproval(auth, id, form["motif"]) }, "/validations", "refused")
    }
    formPost("/validations/{id}/annulation", backOffice) { auth, form ->
        val id = call.uuidParameter("id") ?: return@formPost call.notFound(auth)
        call.afterAction(auth, form, io { backOffice.cancelApproval(auth, id) }, "/validations", "cancelled")
    }

    // Journal -----------------------------------------------------------------------------------------------------
    get("/journal") {
        val auth = call.requireAuth(backOffice) ?: return@get
        if (!auth.actor.has(Permissions.AUDIT_READ)) return@get call.forbidden(auth)
        val (form, filter, error) = journalFilter(call.request.queryParameters)
        val (records, chain) = io {
            (backOffice.journal(auth.actor, filter, JOURNAL_PAGE) as Outcome.Ok).value to (backOffice.verifyJournal(auth.actor) as Outcome.Ok).value
        }
        val head = io { backOffice.journalHead() }
        val older = records.takeIf { it.size == JOURNAL_PAGE }?.lastOrNull()?.seq
        val notice = error?.let { Notice(BackOfficeTexts.error(it), error = true) }
        call.respondHtml { journalPage(auth, records, form, chain, head, notice, older) }
    }
    get("/journal.json") {
        val auth = call.requireAuth(backOffice) ?: return@get
        val (_, filter, error) = journalFilter(call.request.queryParameters)
        if (error != null) return@get call.respondText(BackOfficeTexts.error(error), status = HttpStatusCode.BadRequest)
        when (val outcome = io { backOffice.exportJournal(auth, filter) }) {
            is Outcome.Denied -> call.forbidden(auth)
            is Outcome.Ok -> {
                call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"journal-equipe.json\"")
                call.respondText(exportJson.encodeToString(ListSerializer(AuditRecord.serializer()), outcome.value), ContentType.Application.Json)
            }
        }
    }

    // Judicial requisitions (legal register, D-077) ------------------------------------------------------------------
    get("/requisitions") {
        val auth = call.requireAuth(backOffice) ?: return@get
        val requisitions = when (val outcome = io { backOffice.requisitions(auth.actor) }) {
            is Outcome.Denied -> return@get call.forbidden(auth)
            is Outcome.Ok -> outcome.value
        }
        val pseudonyms = io { backOffice.members().associate { it.id to it.pseudonym } }
        call.respondHtml { requisitionsPage(auth, requisitions, pseudonyms, backOffice.clock.instant(), noticeOf(call.request.queryParameters)) }
    }
    formPost("/requisitions", backOffice) { auth, form ->
        if (!auth.actor.has(Permissions.LEGAL_REQUISITION)) return@formPost call.forbidden(auth)
        val search = when (val parsed = RequisitionSearch.parse(form["cle"], form["evenement"], form["du"], form["au"])) {
            is Outcome.Denied -> return@formPost call.redirectWith("/requisitions", error = parsed.code)
            is Outcome.Ok -> parsed.value
        }
        call.afterAction(auth, form, io { backOffice.requestRequisition(auth, form["reference"], search) }, "/requisitions", "requested")
    }
    // The answer is a file given once: a POST (it changes the state), after a new verification of the key.
    formPost("/requisitions/{id}/reponse", backOffice) { auth, form ->
        val id = call.uuidParameter("id") ?: return@formPost call.notFound(auth)
        when (val outcome = io { backOffice.answerRequisition(auth, id) }) {
            is Outcome.Ok -> {
                call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"${LegalRequisitions.fileName(outcome.value)}\"")
                call.respondText(LegalRequisitions.json(outcome.value), ContentType.Application.Json)
            }
            is Outcome.Denied -> if (outcome.code == "confirmation_required") call.confirm(auth, form) else call.redirectWith("/requisitions", error = outcome.code)
        }
    }

    // Validation sessions -------------------------------------------------------------------------------------------
    get("/seances") {
        val auth = call.requireAuth(backOffice) ?: return@get
        val actor = auth.actor
        if (TeamRole.VALIDATOR !in actor.roles && !actor.has(Permissions.TEAM_REVOKE)) return@get call.forbidden(auth)
        val (own, open, pseudonyms) = io {
            Triple(
                backOffice.usableValidationSessionOf(actor.accountId),
                if (actor.has(Permissions.TEAM_REVOKE)) backOffice.openValidationSessions() else emptyList(),
                backOffice.members().associate { it.id to it.pseudonym },
            )
        }
        call.respondHtml { sessionsPage(auth, own, open, pseudonyms, noticeOf(call.request.queryParameters)) }
    }
    formPost("/seances", backOffice) { auth, form ->
        val ceiling = form["plafond"]?.trim()?.toIntOrNull() ?: ValidationSessions.MAX_VALIDATIONS
        val hours = form["heures"]?.trim()?.toIntOrNull() ?: ValidationSessions.DEFAULT_DURATION.inWholeHours.toInt()
        call.afterAction(auth, form, io { backOffice.openValidationSession(auth, form["cle"].orEmpty(), ceiling, hours.hours) }, "/seances", "session_opened")
    }
    formPost("/seances/{id}/fermeture", backOffice) { auth, form ->
        val id = call.uuidParameter("id") ?: return@formPost call.notFound(auth)
        call.afterAction(auth, form, io { backOffice.closeValidationSession(auth, id) }, "/seances", "session_closed")
    }
    formPost("/seances/{id}/revocation", backOffice) { auth, form ->
        val id = call.uuidParameter("id") ?: return@formPost call.notFound(auth)
        call.afterAction(auth, form, io { backOffice.revokeValidationSession(auth, id, form["motif"]) }, "/seances", "done")
    }
}

private const val QUEUE_PAGE = 50
private const val JOURNAL_PAGE = 100

private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

/** A whole page rendered on the calling thread (the detail of an item calls the queue, which may block). */
private fun renderToString(page: HTML.() -> Unit): String = "<!DOCTYPE html>\n" + createHTML().html(block = page)

/**
 * The body of the request, read through a bounded channel: at most [MAX_BODY_BYTES], whatever the
 * `Content-Length` says or whether the body is chunked; null when it is larger (answered 413).
 */
private suspend fun ApplicationCall.boundedBody(): ByteArray? {
    val declared = request.contentLength()
    if (declared != null && declared > MAX_BODY_BYTES) return null
    val bytes = receiveChannel().readBuffer(MAX_BODY_BYTES + 1L).readByteArray()
    return bytes.takeIf { it.size <= MAX_BODY_BYTES }
}

private fun <T> decode(strategy: DeserializationStrategy<T>, body: ByteArray): T? = try {
    ceremonyJson.decodeFromString(strategy, body.decodeToString())
} catch (e: Exception) {
    null
}

/**
 * Whether a POST comes from a page of the back-office itself: `Sec-Fetch-Site` (sent by every current
 * browser) must say same-origin, and an `Origin` header, when the browser gives a real one, must be ours.
 * The anti-forgery token and the SameSite=Strict cookie remain the main protections.
 */
private fun ApplicationCall.isSameOrigin(config: BackOfficeConfig): Boolean {
    val site = request.headers["Sec-Fetch-Site"]
    if (site != null && site != "same-origin" && site != "none") return false
    val origin = request.headers[HttpHeaders.Origin]
    return origin == null || origin == "null" || origin == config.origin
}

/** A POST of a JSON ceremony (no session needed for sign-in and invitation); [body] receives the bounded body. */
private fun Route.jsonPost(path: String, config: BackOfficeConfig, body: suspend RoutingContext.(ByteArray) -> Unit) {
    post(path) {
        if (!call.isSameOrigin(config)) return@post call.refuse("forbidden", HttpStatusCode.Forbidden)
        val bytes = call.boundedBody() ?: return@post call.refuse("too_large", HttpStatusCode.PayloadTooLarge)
        body(bytes)
    }
}

/**
 * A POST of a form of a signed-in member: same origin, live session, bounded body, valid anti-forgery
 * token; [body] receives the member and the form fields.
 */
private fun Route.formPost(path: String, backOffice: BackOffice, body: suspend RoutingContext.(Authenticated, Parameters) -> Unit) {
    post(path) {
        if (!call.isSameOrigin(backOffice.config)) return@post call.respondHtml(HttpStatusCode.Forbidden) { messagePage(BackOfficeTexts.FORBIDDEN_TITLE, BackOfficeTexts.error("forbidden")) }
        val bytes = call.boundedBody()
            ?: return@post call.respondHtml(HttpStatusCode.PayloadTooLarge) { messagePage(BackOfficeTexts.FORM_EXPIRED_TITLE, BackOfficeTexts.error("too_large")) }
        val auth = call.authenticated(backOffice) ?: return@post call.respondRedirect("/connexion")
        val form = parseQueryString(bytes.decodeToString())
        if (!Csrf.isValid(auth.token, form[Csrf.FIELD])) {
            return@post call.respondHtml(HttpStatusCode.Forbidden) { messagePage(BackOfficeTexts.FORM_EXPIRED_TITLE, BackOfficeTexts.FORM_EXPIRED_TEXT, auth) }
        }
        body(auth, form)
    }
}

private suspend fun ApplicationCall.authenticated(backOffice: BackOffice): Authenticated? {
    val token = request.cookies[SESSION_COOKIE] ?: return null
    return io { backOffice.authenticate(token) }
}

private suspend fun ApplicationCall.requireAuth(backOffice: BackOffice): Authenticated? {
    val auth = authenticated(backOffice)
    if (auth == null) respondRedirect("/connexion")
    return auth
}

/** The member of a JSON ceremony that needs a session: cookie, and the anti-forgery token in the header or the body. */
private suspend fun ApplicationCall.jsonAuth(backOffice: BackOffice, bodyCsrf: String?): Authenticated? {
    val auth = authenticated(backOffice)
    if (auth == null) {
        refuse("forbidden", HttpStatusCode.Unauthorized)
        return null
    }
    val submitted = request.headers[Csrf.HEADER] ?: bodyCsrf
    if (!Csrf.isValid(auth.token, submitted)) {
        refuse("forbidden", HttpStatusCode.Forbidden)
        return null
    }
    return auth
}

private suspend fun ApplicationCall.refuse(code: String, status: HttpStatusCode = HttpStatusCode.BadRequest) {
    respond(status, CeremonyRefused(code, BackOfficeTexts.error(code)))
}

private fun ApplicationCall.setSessionCookie(token: String) {
    response.header(
        HttpHeaders.SetCookie,
        "$SESSION_COOKIE=$token; Path=/; Max-Age=${BackOfficeConfig.SESSION_MAX.inWholeSeconds}; Secure; HttpOnly; SameSite=Strict",
    )
}

private fun ApplicationCall.clearSessionCookie() {
    response.header(HttpHeaders.SetCookie, "$SESSION_COOKIE=; Path=/; Max-Age=0; Secure; HttpOnly; SameSite=Strict")
}

private fun ApplicationCall.uuidParameter(name: String): UUID? = parameters[name]?.let { runCatching { UUID.fromString(it) }.getOrNull() }

private suspend fun ApplicationCall.notFound(auth: Authenticated?) =
    respondHtml(HttpStatusCode.NotFound) { messagePage(BackOfficeTexts.NOT_FOUND_TITLE, BackOfficeTexts.NOT_FOUND_TEXT, auth) }

private suspend fun ApplicationCall.forbidden(auth: Authenticated?) =
    respondHtml(HttpStatusCode.Forbidden) { messagePage(BackOfficeTexts.FORBIDDEN_TITLE, BackOfficeTexts.error("forbidden"), auth) }

/** The confirmation page of a sensitive action: the same form, sent again once the key is verified. */
private suspend fun ApplicationCall.confirm(auth: Authenticated, form: Parameters) {
    val fields = form.entries().flatMap { (name, values) -> values.map { name to it } }
    respondHtml { confirmPage(auth, request.path(), fields) }
}

private suspend fun ApplicationCall.redirectWith(path: String, ok: String? = null, error: String? = null) {
    val query = listOfNotNull(ok?.let { "ok=$it" }, error?.let { "erreur=$it" }).joinToString("&")
    respondRedirect(if (query.isEmpty()) path else "$path?$query")
}

/** Redirects after an action (Post/Redirect/Get), or asks for the key again when the base requires it. */
private suspend fun <T> ApplicationCall.afterAction(auth: Authenticated, form: Parameters, outcome: Outcome<T>, back: String, ok: String) {
    when (outcome) {
        is Outcome.Ok -> redirectWith(back, ok = ok)
        is Outcome.Denied -> if (outcome.code == "confirmation_required") confirm(auth, form) else redirectWith(back, error = outcome.code)
    }
}

/** Like [afterAction], for a change that may wait for a second person. */
private suspend fun ApplicationCall.afterChange(auth: Authenticated, form: Parameters, outcome: Outcome<TeamChange>, back: String, ok: String) {
    when (outcome) {
        is Outcome.Ok -> redirectWith(back, ok = if (outcome.value is TeamChange.Waiting) "requested" else ok)
        is Outcome.Denied -> if (outcome.code == "confirmation_required") confirm(auth, form) else redirectWith(back, error = outcome.code)
    }
}

/** The notice of a page from its address: only known codes are shown, never text from the address. */
private fun noticeOf(parameters: Parameters): Notice? {
    parameters["erreur"]?.takeIf(BackOfficeTexts::hasError)?.let { return Notice(BackOfficeTexts.error(it), error = true) }
    return BackOfficeTexts.notice(parameters["ok"])?.let { Notice(it) }
}

private val PARIS: ZoneId = ZoneId.of("Europe/Paris")

/** An optional end date typed as AAAA-MM-JJ: the end of that day, Paris time. */
private fun parseEndDate(value: String?): Outcome<Instant?> {
    val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return Outcome.Ok(null)
    return try {
        Outcome.Ok(LocalDate.parse(text).plusDays(1).atStartOfDay(PARIS).toInstant())
    } catch (e: DateTimeParseException) {
        Outcome.Denied("bad_date")
    }
}

private fun journalFilter(parameters: Parameters): Triple<JournalForm, AuditFilter, String?> {
    val form = JournalForm(
        from = parameters["du"]?.trim()?.take(10).orEmpty(),
        until = parameters["au"]?.trim()?.take(10).orEmpty(),
        account = parameters["compte"]?.trim()?.take(36).orEmpty(),
        action = parameters["action"]?.trim()?.take(120).orEmpty(),
        target = parameters["objet"]?.trim()?.take(200).orEmpty(),
    )
    var error: String? = null
    fun day(text: String, next: Boolean): Instant? = text.takeIf { it.isNotEmpty() }?.let {
        try {
            LocalDate.parse(it).let { date -> if (next) date.plusDays(1) else date }.atStartOfDay(PARIS).toInstant()
        } catch (e: DateTimeParseException) {
            error = "bad_date"
            null
        }
    }
    val filter = AuditFilter(
        from = day(form.from, next = false),
        until = day(form.until, next = true),
        actorId = form.account.takeIf { it.isNotEmpty() }?.let { runCatching { UUID.fromString(it) }.getOrNull() },
        action = form.action.takeIf { it.isNotEmpty() },
        targetId = form.target.takeIf { it.isNotEmpty() },
        beforeSeq = parameters["avant"]?.toLongOrNull(),
    )
    return Triple(form, filter, error)
}
