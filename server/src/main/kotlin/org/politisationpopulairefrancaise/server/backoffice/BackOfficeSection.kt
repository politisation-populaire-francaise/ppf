package org.politisationpopulairefrancaise.server.backoffice

import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.parseQueryString
import io.ktor.server.application.ApplicationCall
import io.ktor.server.html.respondHtml
import io.ktor.server.request.path
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.html.FlowContent
import kotlinx.html.MAIN
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Pages of the team for one pole (contract docs/integration/back-office.md, section 14): a pole that needs
 * more than a queue (the writing forms of the editorial circuit) gives a section; the base mounts its
 * routes on the back-office port only, under `/equipe/sections/<id>/…`, and keeps everything else: sign-in,
 * the permission of the section, the anti-forgery token of every form, the bounded body, the new passkey
 * verification of sensitive posts ([SectionContext.requireFresh]) and the journal ([SectionContext.audit]:
 * a section never writes the journal itself). Pages are rendered in the layout of the base
 * ([SectionContext.respondPage]).
 *
 * Handlers run on Dispatchers.IO: they may block on JDBC. A section shows no more personal data than its
 * work needs, and never logs the content it handles.
 */
interface BackOfficeSection {
    /** Stable, dotted lowercase, for example `editorial.comprendre`: the path `/equipe/sections/<id>` and the journal prefix. */
    val id: String

    /** French title of the menu entry and of the pages. */
    val title: String

    /** Who may open any page of the section; finer permissions are checked by the section with [TeamActor.has]. */
    val permission: String

    /** The largest form body the section accepts, at most [MAX_SECTION_BODY_BYTES] (a long text); 64 KiB by default. */
    val maxBodyBytes: Int get() = MAX_BODY_BYTES

    /** Declares the pages, once, when the base mounts the section. */
    fun routes(routes: SectionRoutes)

    companion object {
        /** No section reads a form body larger than this (256 KiB, the design of the editorial circuit, section 2). */
        const val MAX_SECTION_BODY_BYTES: Int = 256 * 1024
    }
}

/** A menu entry of a section, as the navigation of the base shows it. */
data class SectionLink(val href: String, val title: String)

/** The sections plugged into the back-office. Registered at startup, before the routes are mounted; ids are unique. */
class BackOfficeSections {
    private val sections = ConcurrentHashMap<String, BackOfficeSection>()
    private val order = mutableListOf<String>()

    @Volatile
    private var mounted = false

    @Synchronized
    fun register(section: BackOfficeSection) {
        check(!mounted) { "The sections are registered before the routes of the back-office are mounted" }
        require(SECTION_ID.matches(section.id) && section.id.length <= 60) { "A section id is a dotted lowercase identifier: ${section.id}" }
        require(Permissions.isValidId(section.permission)) { "A section permission is a dotted identifier" }
        require(section.title.isNotBlank() && section.title.length <= 60) { "A section title has 1 to 60 characters" }
        require(section.maxBodyBytes in 1..BackOfficeSection.MAX_SECTION_BODY_BYTES) { "A section reads at most 256 KiB" }
        require(sections.putIfAbsent(section.id, section) == null) { "The section ${section.id} is already registered" }
        order += section.id
    }

    fun find(id: String): BackOfficeSection? = sections[id]

    @Synchronized
    fun all(): List<BackOfficeSection> = order.mapNotNull(sections::get)

    fun visibleTo(actor: TeamActor): List<BackOfficeSection> = all().filter { actor.has(it.permission) }

    /** The menu entries of [actor]. */
    fun linksFor(actor: TeamActor): List<SectionLink> = visibleTo(actor).map { SectionLink(sectionPath(it.id), it.title) }

    internal fun markMounted(): List<BackOfficeSection> {
        mounted = true
        return all()
    }

    companion object {
        private val SECTION_ID = Regex("^[a-z0-9_]+(\\.[a-z0-9_]+)*$")
    }
}

/** The path of a section, or of [sub] inside it (relative, no leading slash). */
fun sectionPath(sectionId: String, sub: String = ""): String = "/equipe/sections/$sectionId" + if (sub.isEmpty()) "" else "/$sub"

/**
 * What a section may declare: GET pages and POST forms under its own path, with Ktor's path syntax
 * (`brouillons/{id}`). Each handler receives a [SectionContext]; the base has checked, before calling it,
 * the session (otherwise the sign-in page), the permission of the section (otherwise 403) and, for a post,
 * the origin, the size of the body and the anti-forgery token (otherwise refused).
 */
class SectionRoutes internal constructor(
    private val route: Route,
    private val section: BackOfficeSection,
    private val backOffice: BackOffice,
) {
    /** A page; [path] is relative to the section ("" for its home). */
    fun get(path: String, handler: suspend SectionContext.() -> Unit) {
        val target = if (path.isEmpty()) route else route.route(path) {}
        target.get {
            val auth = call.authenticated(backOffice) ?: return@get call.respondRedirect("/connexion")
            if (!auth.actor.has(section.permission)) return@get call.forbidden(auth)
            val context = SectionContext(call, auth, backOffice, section, Parameters.Empty)
            withContext(Dispatchers.IO) { context.handler() }
        }
    }

    /** A form; the handler receives the posted fields. */
    fun post(path: String, handler: suspend SectionContext.(form: Parameters) -> Unit) {
        val target = if (path.isEmpty()) route else route.route(path) {}
        target.post {
            if (!call.isSameOrigin(backOffice.config)) {
                return@post call.respondHtml(HttpStatusCode.Forbidden) { messagePage(BackOfficeTexts.FORBIDDEN_TITLE, BackOfficeTexts.error("forbidden")) }
            }
            val bytes = call.boundedBody(section.maxBodyBytes)
                ?: return@post call.respondHtml(HttpStatusCode.PayloadTooLarge) { messagePage(BackOfficeTexts.FORM_EXPIRED_TITLE, BackOfficeTexts.error("too_large_form")) }
            val auth = call.authenticated(backOffice) ?: return@post call.respondRedirect("/connexion")
            val form = parseQueryString(bytes.decodeToString())
            if (!Csrf.isValid(auth.token, form[Csrf.FIELD])) {
                return@post call.respondHtml(HttpStatusCode.Forbidden) { messagePage(BackOfficeTexts.FORM_EXPIRED_TITLE, BackOfficeTexts.FORM_EXPIRED_TEXT, auth) }
            }
            if (!auth.actor.has(section.permission)) return@post call.forbidden(auth)
            val context = SectionContext(call, auth, backOffice, section, form)
            withContext(Dispatchers.IO) { context.handler(form) }
        }
    }
}

/**
 * One request of a section: who acts, how to answer, and the services of the base. The section never sees
 * the session token nor the call itself.
 */
class SectionContext internal constructor(
    private val call: ApplicationCall,
    private val auth: Authenticated,
    private val backOffice: BackOffice,
    private val section: BackOfficeSection,
    private val form: Parameters,
) {
    /** The member who acts (their permissions already reduced without a backup key). */
    val actor: TeamActor get() = auth.actor

    /** The parameters of the path (`{id}`), raw: the section validates them. */
    fun pathParameter(name: String): String? = call.parameters[name]

    /** The query string of a page (never logged). */
    val query: Parameters get() = call.request.queryParameters

    /** The hidden anti-forgery field, to put in every form of the section. */
    fun csrfInput(flow: FlowContent) = flow.csrfField(auth)

    /** The path of [sub] inside the section. */
    fun path(sub: String = ""): String = sectionPath(section.id, sub)

    /** Whether the passkey was verified less than 5 minutes ago. */
    fun isFresh(): Boolean = auth.isFresh(backOffice.clock.instant())

    /**
     * For a sensitive post: true when the passkey was verified less than 5 minutes ago; otherwise the base
     * answers with its confirmation page, which posts the same fields again once the key is verified, and
     * this returns false (the handler then returns without doing anything).
     */
    suspend fun requireFresh(): Boolean {
        if (isFresh()) return true
        val fields = form.entries().flatMap { (name, values) -> values.map { name to it } }
        call.respondHtml { confirmPage(auth, call.request.path(), fields) }
        return false
    }

    /**
     * Writes one line of the journal through the base (the section never writes it itself): action
     * `section.<section id>.<action>`, target `<section id>:<target>`. [details] holds identifiers and
     * counts only, never the text of a content. Blocking (JDBC).
     */
    fun audit(action: String, target: String?, reason: String? = null, details: Map<String, String> = emptyMap()): AuditRecord =
        backOffice.recordSectionAction(actor, section.id, action, target, reason, details)

    /** The pseudonym of a team account, for the pages (null when unknown or removed). */
    fun pseudonymOf(accountId: UUID): String? = backOffice.member(accountId)?.pseudonym

    /** A page of the section in the layout of the base, its menu entry marked as current. */
    suspend fun respondPage(title: String, notice: Notice? = null, status: HttpStatusCode = HttpStatusCode.OK, content: MAIN.() -> Unit) {
        call.respondHtml(status) { backOfficeLayout(title, auth, current = path(), notice = notice, content = content) }
    }

    /** Post/Redirect/Get to [sub] inside the section, with an optional query (codes only, never a text). */
    suspend fun redirect(sub: String = "", query: String? = null) {
        val target = path(sub) + if (query.isNullOrEmpty()) "" else "?$query"
        call.respondRedirect(target)
    }

    suspend fun notFound() = call.notFound(auth)

    suspend fun forbidden() = call.forbidden(auth)
}

/** Mounts every registered section under `/equipe/sections/<id>` (called once by [backOfficeRoutes]). */
internal fun Route.mountSections(backOffice: BackOffice) {
    backOffice.sections.markMounted().forEach { section ->
        route(sectionPath(section.id)) { section.routes(SectionRoutes(this, section, backOffice)) }
    }
}
