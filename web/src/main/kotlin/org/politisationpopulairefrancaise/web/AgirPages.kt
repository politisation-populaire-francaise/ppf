package org.politisationpopulairefrancaise.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.article
import kotlinx.html.button
import kotlinx.html.dd
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.select
import kotlinx.html.strong
import kotlinx.html.time
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.agir.calls.Call
import org.politisationpopulairefrancaise.core.agir.calls.CallFields
import org.politisationpopulairefrancaise.core.agir.calls.CallStatus
import org.politisationpopulairefrancaise.core.agir.calls.CallType
import org.politisationpopulairefrancaise.core.agir.calls.slots
import org.politisationpopulairefrancaise.core.agir.organisations.Organisation
import org.politisationpopulairefrancaise.core.agir.poster.AgirLinks
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Where the pages of the calls read the published calls (the server: published ones only, never those held for review). */
fun interface CallsSource {
    suspend fun calls(): List<Call>
}

/** Where the directory page reads the entries of the organisations (signed by an editorial key). */
fun interface OrganisationsSource {
    suspend fun organisations(): List<Organisation>
}

/** French names of the types of calls (AP-01, D-199). */
val callTypeLabels: Map<CallType, String> = mapOf(
    CallType.SE_REUNIR to "Se réunir",
    CallType.DEBATTRE to "Débattre",
    CallType.S_ORGANISER to "S’organiser",
    CallType.MANIFESTER to "Manifester",
    CallType.GREVE to "Faire grève",
    CallType.BLOQUER to "Bloquer",
    CallType.SOUTENIR to "Soutenir une cause",
    CallType.PETITION to "Signer une pétition",
    CallType.INTERPELLER to "Interpeller",
    CallType.BOYCOTTER to "Boycotter",
    CallType.COLLECTE to "Collecte de fonds",
    CallType.BENEVOLES to "Bénévoles recherchés",
    CallType.INFORMATION to "Information",
    CallType.TEMOINS to "Appel à témoins",
    CallType.AUTRE to "Autre",
)

/** French names of the types of organisations (M3). */
val organisationTypeLabels: Map<String, String> = mapOf(
    "association" to "Association",
    "syndicat" to "Syndicat",
    "collectif" to "Collectif",
    "legal-team" to "Legal team",
    "street-medics" to "Street medics",
    "avocats-benevoles" to "Avocats bénévoles",
    "autre" to "Autre",
)

private val CALL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'à' HH 'h' mm", Locale.FRENCH)
private val PARIS: ZoneId = ZoneId.of(AgirFormats.TIME_ZONE)
private const val MAX_FILTER_LENGTH: Int = 200

/** The page of a call on the site: the address printed on the posters (AG-61, AgirLinks.CALLS). */
fun callPath(call: Call): String = AgirLinks.pathOf(call)

/**
 * The public pages of « Agir » readable without JavaScript: the open calls (`/agir/appels`, filtered by type and
 * territory by a form sent by GET), the page of one call (`/agir/appels/<key>/<identifier>`, the address of the
 * posters) and the directory of the organisations (`/organisations`). Only what the server published: the calls held
 * for review never show. Links lead out of the site only to web addresses, marked as such.
 */
fun Route.agirPages(calls: CallsSource?, organisations: OrganisationsSource?) {
    if (calls != null) {
        get(AgirLinks.CALLS) {
            val parameters = call.request.queryParameters
            val type = parameters["type"]?.let(CallType::fromTag)
            val territory = parameters["territory"]?.trim()?.take(MAX_FILTER_LENGTH)?.takeIf { it.isNotEmpty() }
            val now = Instant.now().epochSecond
            val shown = calls.calls()
                .filter { it.isOpen && (it.deadline == null || it.deadline!! >= now) }
                .filter { type == null || it.type == type }
                .filter { territory == null || it.territory.contains(territory, ignoreCase = true) }
                .sortedWith(compareBy<Call> { it.dueAt ?: Long.MAX_VALUE }.thenBy { it.displayTitle })
            call.respondHtml { callsDocument(type, territory, shown) }
        }
        get("/agir/appels/{key}/{identifier}") {
            val key = call.parameters["key"].orEmpty()
            val identifier = call.parameters["identifier"].orEmpty()
            val found = calls.calls().firstOrNull { it.author == key && it.slug == identifier }
            if (found == null) {
                call.respondHtml(HttpStatusCode.NotFound) { notFoundDocument() }
            } else {
                call.respondHtml { callDocument(found) }
            }
        }
    }
    if (organisations != null) {
        get("/organisations") {
            val parameters = call.request.queryParameters
            val type = parameters["type"]?.takeIf { it in AgirFormats.ORG_TYPES }
            val territory = parameters["territory"]?.trim()?.take(MAX_FILTER_LENGTH)?.takeIf { it.isNotEmpty() }
            val shown = organisations.organisations()
                .filter { !it.revoked }
                .filter { type == null || it.type == type }
                .filter { territory == null || it.territory.contains(territory, ignoreCase = true) }
                .sortedBy { it.displayName.lowercase() }
            call.respondHtml { organisationsDocument(type, territory, shown) }
        }
    }
}

/** The list of the open calls; separate from the route so that tests render it without a server. */
fun HTML.callsDocument(type: CallType?, territory: String?, calls: List<Call>) {
    siteLayout(pageTitle = "Appels à agir", currentSpace = Space.AGIR) {
        h1 { +"Appels à agir" }
        p { +"Grèves, manifestations, soutiens, blocages, pétitions, collectes : les appels ouverts, publiés par l’équipe, par les organisations vérifiées ou relus avant publication. Pour répondre ou lancer un appel, utilisez l’application." }
        form(action = AgirLinks.CALLS, method = FormMethod.get) {
            attributes["role"] = "search"
            attributes["aria-label"] = "Filtrer les appels"
            label { htmlFor = "appels-type"; +"Type" }
            select {
                id = "appels-type"
                name = "type"
                option { value = ""; if (type == null) selected = true; +"Tous les types" }
                CallType.entries.forEach { candidate ->
                    option {
                        value = candidate.tagValue
                        if (type == candidate) selected = true
                        +callTypeLabels.getValue(candidate)
                    }
                }
            }
            label { htmlFor = "appels-territory"; +"Territoire" }
            input(type = InputType.text, name = "territory") {
                id = "appels-territory"
                maxLength = MAX_FILTER_LENGTH.toString()
                attributes["autocomplete"] = "off"
                territory?.let { value = it }
            }
            button(type = kotlinx.html.ButtonType.submit) { +"Filtrer" }
        }
        section {
            attributes["aria-labelledby"] = "appels-results"
            h2 {
                id = "appels-results"
                +when (calls.size) {
                    0 -> "Aucun appel ouvert"
                    1 -> "1 appel ouvert"
                    else -> "${calls.size} appels ouverts"
                }
            }
            calls.forEach { item ->
                article(classes = "agenda-event") {
                    h3 { a(href = callPath(item)) { +item.title.trim() } }
                    callFacts(item)
                    item.summary?.let { p { +it } }
                }
            }
        }
    }
}

/** The page of one call: what, when, where, who, the text, and the legal notice of a call to block (D-199). */
fun HTML.callDocument(item: Call) {
    siteLayout(pageTitle = item.title.trim(), currentSpace = Space.AGIR) {
        p { a(href = AgirLinks.CALLS) { +"Tous les appels" } }
        h1 {
            if (item.status == CallStatus.CLOSED) strong { +"Clos : " }
            +item.title.trim()
        }
        callFacts(item)
        if (item.type == CallType.BLOQUER) {
            section {
                attributes["aria-labelledby"] = "loi"
                h2 { id = "loi"; +"Bloquer : ce que dit la loi" }
                p { +"Sont légaux : la grève, un piquet de grève qui laisse passer celles et ceux qui veulent travailler, la fermeture volontaire de son commerce. Exposent à des poursuites : entraver la circulation sur une voie publique (code de la route, article L412-1) et entraver la liberté du travail de manière concertée, par des menaces ou des violences (code pénal, article 431-1). Laissez toujours passer les secours." }
            }
        }
        item.text.lines().filter { it.isNotBlank() }.forEach { line -> p { +line } }
        item.slots.takeIf { it.isNotEmpty() }?.let { slots ->
            section {
                attributes["aria-labelledby"] = "creneaux"
                h2 { id = "creneaux"; +"Créneaux" }
                slots.forEach { slot ->
                    p {
                        strong { +slot.label }
                        +" : du "
                        dateTime(slot.start)
                        +" au "
                        dateTime(slot.end)
                        +" (${slot.places} places). Inscription dans l’application."
                    }
                }
            }
        }
        item.report?.let { report ->
            h2 { +"Bilan" }
            report.lines().filter { it.isNotBlank() }.forEach { line -> p { +line } }
        }
        item.links.firstOrNull(::isWebLink)?.let { link ->
            p { a(href = link) { attributes["rel"] = "noopener noreferrer nofollow external"; +"Lien de l’appel (site extérieur)" } }
        }
        p { +"Pour répondre à cet appel, le partager ou le signaler, ouvrez-le dans l’application Politisation Populaire Française." }
    }
}

private fun FlowContent.callFacts(item: Call) {
    dl {
        dt { +"Type" }
        dd { +callTypeLabels.getValue(item.type) }
        dt { +"Territoire" }
        dd { +item.territory }
        item.start?.let { start -> dt { +"Quand" }; dd { dateTime(start) } }
        item.location?.let { place -> dt { +"Où" }; dd { +place } }
        item.deadline?.let { deadline -> dt { +"Jusqu’au" }; dd { dateTime(deadline) } }
        item.fields[CallFields.ORGANIZER]?.let { organizer -> dt { +"Qui appelle" }; dd { +organizer } }
    }
}

/** The directory of the organisations: verified by the team, their territory, their contacts. */
fun HTML.organisationsDocument(type: String?, territory: String?, entries: List<Organisation>) {
    siteLayout(pageTitle = "Annuaire des organisations", currentSpace = Space.AGIR) {
        h1 { +"Annuaire des organisations" }
        p { +"Associations, syndicats, collectifs, legal teams, street medics : les organisations vérifiées par l’équipe de PPF, avec leur territoire et leurs contacts publics." }
        form(action = "/organisations", method = FormMethod.get) {
            attributes["role"] = "search"
            attributes["aria-label"] = "Filtrer l’annuaire"
            label { htmlFor = "orgs-type"; +"Type" }
            select {
                id = "orgs-type"
                name = "type"
                option { value = ""; if (type == null) selected = true; +"Tous les types" }
                AgirFormats.ORG_TYPES.forEach { candidate ->
                    option {
                        value = candidate
                        if (type == candidate) selected = true
                        +organisationTypeLabels[candidate].orEmpty().ifEmpty { candidate }
                    }
                }
            }
            label { htmlFor = "orgs-territory"; +"Territoire" }
            input(type = InputType.text, name = "territory") {
                id = "orgs-territory"
                maxLength = MAX_FILTER_LENGTH.toString()
                attributes["autocomplete"] = "off"
                territory?.let { value = it }
            }
            button(type = kotlinx.html.ButtonType.submit) { +"Filtrer" }
        }
        section {
            attributes["aria-labelledby"] = "orgs-results"
            h2 {
                id = "orgs-results"
                +when (entries.size) {
                    0 -> "Aucune organisation"
                    1 -> "1 organisation"
                    else -> "${entries.size} organisations"
                }
            }
            entries.forEach { entry ->
                article(classes = "agenda-event") {
                    id = "org-${entry.slug}"
                    h3 { +entry.name.trim() }
                    dl {
                        dt { +"Type" }
                        dd { +(organisationTypeLabels[entry.type] ?: entry.type) }
                        dt { +"Territoire" }
                        dd { +entry.territory }
                        dt { +"Vérifiée le" }
                        dd { +entry.verifiedOn.toFrench() }
                        entry.phone?.let { phone -> dt { +"Téléphone" }; dd { a(href = entry.phoneUri ?: "") { +phone } } }
                        entry.email?.let { mail -> dt { +"Courriel" }; dd { a(href = entry.emailUri ?: "") { +mail } } }
                        entry.postalAddress?.let { address -> dt { +"Adresse" }; dd { +address } }
                    }
                    p { +entry.summary }
                    if (entry.origin == ContentOrigin.AI_ASSISTED) p { +"Rédigé par l’IA" }
                    entry.websites.firstOrNull(::isWebLink)?.let { site ->
                        p { a(href = site) { attributes["rel"] = "noopener noreferrer nofollow external"; +"Site de l’organisation (site extérieur)" } }
                    }
                }
            }
        }
    }
}

private fun HTML.notFoundDocument() {
    siteLayout(pageTitle = "Appel introuvable", currentSpace = Space.AGIR) {
        h1 { +"Appel introuvable" }
        p { +"Cet appel n’existe pas ou n’est plus publié." }
        p { a(href = AgirLinks.CALLS) { +"Voir les appels ouverts" } }
    }
}

private fun FlowContent.dateTime(seconds: Long) {
    val moment = Instant.ofEpochSecond(seconds).atZone(PARIS)
    time {
        attributes["datetime"] = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(moment)
        +CALL_DATE.format(moment)
    }
}

private fun isWebLink(value: String): Boolean =
    (value.startsWith("https://") || value.startsWith("http://")) && value.none { it.isWhitespace() || it.isISOControl() }
