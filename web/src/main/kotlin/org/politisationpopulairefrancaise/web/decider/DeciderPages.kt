package org.politisationpopulairefrancaise.web.decider

import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.ThScope
import kotlinx.html.a
import kotlinx.html.article
import kotlinx.html.caption
import kotlinx.html.dd
import kotlinx.html.div
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.h1
import kotlinx.html.h3
import kotlinx.html.li
import kotlinx.html.ol
import kotlinx.html.p
import kotlinx.html.strong
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import kotlinx.html.ul
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.decider.CellStatus
import org.politisationpopulairefrancaise.core.decider.DeciderCatalog
import org.politisationpopulairefrancaise.core.decider.Pacte
import org.politisationpopulairefrancaise.core.decider.PacteBoard
import org.politisationpopulairefrancaise.core.decider.PacteStatus
import org.politisationpopulairefrancaise.core.decider.Petition
import org.politisationpopulairefrancaise.core.decider.PetitionPlatform
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.FrenchNumbers
import org.politisationpopulairefrancaise.core.voir.VoirCatalog
import org.politisationpopulairefrancaise.web.siteLayout
import org.politisationpopulairefrancaise.web.voir.BadgeTone
import org.politisationpopulairefrancaise.web.voir.VoirSource
import org.politisationpopulairefrancaise.web.voir.badge
import org.politisationpopulairefrancaise.web.voir.correctionsSection
import org.politisationpopulairefrancaise.web.voir.date
import org.politisationpopulairefrancaise.web.voir.externalLink
import org.politisationpopulairefrancaise.web.voir.isWebAddress
import org.politisationpopulairefrancaise.web.voir.originLine
import org.politisationpopulairefrancaise.web.voir.pageSection
import org.politisationpopulairefrancaise.web.voir.sourcesSection
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The pages of "Décider" on the web site, readable without script, also on an iPhone (CLAUDE.md, section 7):
 * the official petitions relayed by PPF (signed on the official site only: PPF collects no signature) and
 * the democratic pact with its public table of answers (D-059 to D-062). Every page sits at a stable address
 * ([DeciderLinks]); an unknown address answers a 404 page. The names of the candidates come from "Voir" when
 * its source is given.
 */
fun Route.deciderPages(source: DeciderSource, voir: VoirSource? = null, clock: Clock = Clock.systemUTC()) {
    fun today(): CivilDate = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).let { CivilDate(it.year, it.monthValue, it.dayOfMonth) }
    route(DeciderLinks.HOME) {
        get {
            val catalog = source.catalog()
            val deliberations = source.deliberations()
            call.respondHtml { deciderHomeDocument(catalog, today(), deliberations, clock.millis() / 1000) }
        }
        get("/${DeliberationLinks.PATH}/{slug}") {
            val board = source.deliberations().firstOrNull { it.address.identifier == call.parameters["slug"] }
            if (board == null) call.respondHtml(HttpStatusCode.NotFound) { deciderNotFoundDocument() } else call.respondHtml { deliberationDocument(board, clock.millis() / 1000) }
        }
        get("/${DeciderLinks.PETITIONS}/{slug}") {
            val catalog = source.catalog()
            val petition = catalog.petitions.firstOrNull { it.address.identifier == call.parameters["slug"] }
            if (petition == null) call.respondHtml(HttpStatusCode.NotFound) { deciderNotFoundDocument() } else call.respondHtml { petitionDocument(petition, today()) }
        }
        get("/${DeciderLinks.PACTE}/{slug}") {
            val catalog = source.catalog()
            val pacte = catalog.pactes.firstOrNull { it.address.identifier == call.parameters["slug"] }
            if (pacte == null) {
                call.respondHtml(HttpStatusCode.NotFound) { deciderNotFoundDocument() }
            } else {
                val board = catalog.board(pacte, voir?.catalog() ?: VoirCatalog.EMPTY, today())
                call.respondHtml { pacteDocument(pacte, board) }
            }
        }
        get("/${DeciderMoreLinks.COMPARATOR}/{slug}") {
            val catalog = source.catalog()
            val sheet = catalog.comparators.firstOrNull { it.article.slug == call.parameters["slug"] }
            if (sheet == null) call.respondHtml(HttpStatusCode.NotFound) { deciderNotFoundDocument() } else call.respondHtml { comparatorDocument(catalog.grid(sheet, voir?.catalog() ?: VoirCatalog.EMPTY)) }
        }
        get("/${DeciderMoreLinks.ELECTIONS}/{slug}") {
            val election = source.catalog().elections.firstOrNull { it.article.slug == call.parameters["slug"] }
            if (election == null) call.respondHtml(HttpStatusCode.NotFound) { deciderNotFoundDocument() } else call.respondHtml { electionDocument(election) }
        }
        get("/${DeciderMoreLinks.CONSULTATIONS}/{slug}") {
            val consultation = source.catalog().consultations.firstOrNull { it.article.slug == call.parameters["slug"] }
            if (consultation == null) call.respondHtml(HttpStatusCode.NotFound) { deciderNotFoundDocument() } else call.respondHtml { consultationDocument(consultation, today()) }
        }
        get("/${DeciderMoreLinks.LETTERS}/{slug}") {
            val letter = source.catalog().letters.firstOrNull { it.article.slug == call.parameters["slug"] }
            if (letter == null) call.respondHtml(HttpStatusCode.NotFound) { deciderNotFoundDocument() } else call.respondHtml { letterDocument(letter) }
        }
        get("/{rest...}") { call.respondHtml(HttpStatusCode.NotFound) { deciderNotFoundDocument() } }
    }
    // The wall of the demands, readable without script (D-152): the demands of the movement first.
    route(RevendicationLinks.HOME) {
        get { source.demands().let { demands -> call.respondHtml { revendicationsDocument(demands) } } }
        get("/{slug}") {
            val demand = source.demands().firstOrNull { it.slug == call.parameters["slug"] }
            if (demand == null) call.respondHtml(HttpStatusCode.NotFound) { deciderNotFoundDocument() } else call.respondHtml { revendicationDocument(demand) }
        }
    }
}

/** The stable addresses of the pages of "Décider". */
object DeciderLinks {
    const val HOME: String = "/decider"
    const val PETITIONS: String = "petitions"
    const val PACTE: String = "pacte"

    fun petition(petition: Petition): String = "$HOME/$PETITIONS/${petition.address.identifier}"
    fun pacte(pacte: Pacte): String = "$HOME/$PACTE/${pacte.address.identifier}"
}

// ---------------------------------------------------------------------------------------------
// Home

fun HTML.deciderHomeDocument(
    catalog: DeciderCatalog,
    today: CivilDate,
    deliberations: List<org.politisationpopulairefrancaise.core.decider.DeliberationBoard> = emptyList(),
    nowSeconds: Long = 0,
) {
    siteLayout(pageTitle = "Décider", currentSpace = Space.DECIDER) {
        h1 { +"Décider" }
        p(classes = "lead") { +"Les pétitions officielles relayées par PPF et la Constitution Citoyenne et Démocratique proposée aux candidats. PPF n’adopte rien : ce sont les votes des citoyens qui choisissent." }
        if (catalog.isEmpty) p(classes = "notice") { +"Aucun contenu publié pour l’instant." }
        val pactes = catalog.pactes
        pageSection("pacte", "Constitution Citoyenne et Démocratique") {
            if (pactes.isEmpty()) p(classes = "muted") { +"Aucune Constitution Citoyenne et Démocratique publiée pour l’instant." }
            pactes.forEach { pacte -> pacteCard(pacte) }
        }
        pageSection("deliberation", "Délibération citoyenne sur les mesures") {
            if (deliberations.isEmpty()) {
                p(classes = "muted") { +"La délibération sur les mesures de la Constitution Citoyenne et Démocratique n’est pas encore ouverte : ses dates seront publiées ici. Proposer, amender et voter se feront dans l’application PPF." }
            }
            deliberations.forEach { deliberationCard(it, nowSeconds) }
        }
        pageSection("comparateur", "Comparer les candidats") {
            if (catalog.comparators.isEmpty()) p(classes = "muted") { +"Aucun comparateur publié pour l’instant." }
            catalog.comparators.forEach { comparatorCard(it) }
        }
        pageSection("calendrier", "Calendrier électoral") {
            if (catalog.elections.isEmpty()) p(classes = "muted") { +"Aucune élection publiée pour l’instant." }
            catalog.elections.forEach { electionCard(it) }
        }
        val open = catalog.openPetitions(today)
        pageSection("petitions-ouvertes", "Pétitions officielles ouvertes") {
            p { +"Elles se signent sur le site officiel de l’institution, jamais sur PPF : PPF ne recueille aucune signature." }
            if (open.isEmpty()) p(classes = "muted") { +"Aucune pétition ouverte pour l’instant." } else open.forEach { petitionCard(it, today) }
        }
        val closed = catalog.closedPetitions(today)
        if (closed.isNotEmpty()) {
            pageSection("petitions-closes", "Pétitions closes") {
                ul { closed.forEach { petition -> li { a(href = DeciderLinks.petition(petition)) { +petition.title } } } }
            }
        }
        pageSection("consultations", "Consultations publiques") {
            p { +"On y répond sur le site officiel de l’organisateur, jamais sur PPF." }
            if (catalog.consultations.isEmpty()) p(classes = "muted") { +"Aucune consultation publiée pour l’instant." }
            catalog.consultations.forEach { consultationCard(it, today) }
        }
        pageSection("courriers", "Écrire à ses élus") {
            if (catalog.letters.isEmpty()) p(classes = "muted") { +"Aucun modèle de courrier publié pour l’instant." }
            catalog.letters.forEach { letterCard(it) }
        }
    }
}

private fun FlowContent.pacteCard(pacte: Pacte) {
    article(classes = "card") {
        h3 { a(href = DeciderLinks.pacte(pacte)) { +pacte.title } }
        p {
            badge(STATUS_LABELS.getValue(pacte.status), if (pacte.status == PacteStatus.PROPOSED) BadgeTone.WARNING else BadgeTone.BRAND)
            +" ${pacte.election.label} : ${pacte.measures.size} mesure(s)."
        }
        if (pacte.status == PacteStatus.PROPOSED) p(classes = "notice") { +STARTING_PROPOSAL }
    }
}

private fun FlowContent.petitionCard(petition: Petition, today: CivilDate) {
    article(classes = "card") {
        h3 { a(href = DeciderLinks.petition(petition)) { +petition.title } }
        p {
            badge(PLATFORM_LABELS.getValue(petition.platform))
            petition.latestCount?.let { +" ${FrenchNumbers.format(it.count.toDouble())} signature(s) au " ; date(it.readOn, short = true) ; +"." }
            petition.daysLeft(today)?.let { +" Encore $it jour(s)." }
        }
        petition.article.summary?.let { p { +it } }
    }
}

// ---------------------------------------------------------------------------------------------
// One petition

fun HTML.petitionDocument(petition: Petition, today: CivilDate) {
    siteLayout(pageTitle = petition.title, currentSpace = Space.DECIDER) {
        p(classes = "breadcrumb") { a(href = DeciderLinks.HOME) { +"Décider" } }
        h1 { +petition.title }
        p {
            badge(PLATFORM_LABELS.getValue(petition.platform))
            +" "
            badge(if (petition.isOpen(today)) "Ouverte" else "Close", if (petition.isOpen(today)) BadgeTone.SUCCESS else BadgeTone.NEUTRAL)
        }
        petition.article.summary?.let { p(classes = "lead") { +it } }
        petition.article.body.lines().filter { it.isNotBlank() }.forEach { p { +it } }
        if (isWebAddress(petition.officialUrl)) {
            p(classes = "action") {
                externalLink(petition.officialUrl, if (petition.isOpen(today)) "Signer sur le site officiel" else "Voir la pétition sur le site officiel")
            }
        }
        p(classes = "muted") { +"PPF ne recueille aucune signature : la pétition se signe sur le site officiel, avec les règles de l’institution." }
        pageSection("compteur", "Où en est-elle") {
            dl {
                petition.reference?.let { dt { +"Numéro" }; dd { +it } }
                petition.openedOn?.let { dt { +"Ouverte le" }; dd { date(it) } }
                petition.closesOn?.let { dt { +"Close le" }; dd { date(it) } }
                petition.required?.let { dt { +"Signatures requises" }; dd { +FrenchNumbers.format(it.toDouble()) } }
                petition.departments?.let { dt { +"Départements des signataires" }; dd { +"${it.count} au " ; date(it.readOn) } }
            }
            if (petition.counts.isNotEmpty()) {
                table {
                    caption { +"Relevés du compteur officiel, du plus récent au plus ancien" }
                    thead { tr { th(scope = ThScope.col) { +"Date" }; th(scope = ThScope.col) { +"Signatures" }; th(scope = ThScope.col) { +"Source" } } }
                    tbody {
                        petition.counts.forEach { count ->
                            tr {
                                td { date(count.readOn) }
                                td { +FrenchNumbers.format(count.count.toDouble()) }
                                td { +(count.source ?: "Site officiel") }
                            }
                        }
                    }
                }
            }
        }
        if (petition.milestones.isNotEmpty()) {
            pageSection("etapes", "Ce qui s’est passé") {
                ol { petition.milestones.forEach { milestone -> li { date(milestone.date); +" : ${milestone.label}" } } }
            }
        }
        sourcesSection(petition.sources)
        correctionsSection(petition.corrections)
        originLine(petition.article)
    }
}

// ---------------------------------------------------------------------------------------------
// The democratic pact

fun HTML.pacteDocument(pacte: Pacte, board: PacteBoard) {
    siteLayout(pageTitle = pacte.title, currentSpace = Space.DECIDER) {
        p(classes = "breadcrumb") { a(href = DeciderLinks.HOME) { +"Décider" } }
        h1 { +pacte.title }
        p {
            badge(STATUS_LABELS.getValue(pacte.status), if (pacte.status == PacteStatus.PROPOSED) BadgeTone.WARNING else BadgeTone.BRAND)
            +" ${pacte.election.label}"
            pacte.election.firstRound?.let { +", premier tour le " ; date(it) }
        }
        if (pacte.status == PacteStatus.PROPOSED) p(classes = "notice") { +STARTING_PROPOSAL }
        pacte.article.summary?.let { p(classes = "lead") { +it } }
        pageSection("mesures", "Les mesures") {
            ol {
                pacte.measures.forEach { measure ->
                    li {
                        strong { +measure.title }
                        p { +measure.commitment }
                        measure.textToChange?.let { p(classes = "muted") { +"Texte à modifier : $it" } }
                    }
                }
            }
        }
        pageSection("methode", "La méthode") {
            ul {
                li { +"Le même texte, envoyé le même jour, avec le même délai de réponse, à tous les candidats, sans exception." }
                li { +"Les candidats sont rangés par ordre alphabétique ; une réponse reçue après la date limite n’est pas comptée, elle reste visible avec sa date de réception." }
                pacte.sentOn?.let { li { +"Envoyée le " ; date(it) ; +"." } }
                pacte.deadline?.let { li { +"Date limite de réponse : " ; date(it) ; +"." } }
            }
        }
        if (board.isOpenForAnswers) boardSection(pacte, board)
        sourcesSection(pacte.sources)
        correctionsSection(pacte.corrections)
        originLine(pacte.article)
    }
}

private fun FlowContent.boardSection(pacte: Pacte, board: PacteBoard) {
    pageSection("reponses", "Les réponses des candidats") {
        p { +"${board.answeredCandidates} candidat(s) sur ${board.rows.size} ont répondu." }
        if (board.rows.isEmpty()) return@pageSection
        div(classes = "table-scroll") {
            table {
                caption { +"Réponse de chaque candidat, mesure par mesure" }
                thead {
                    tr {
                        th(scope = ThScope.col) { +"Candidat" }
                        pacte.measures.forEachIndexed { index, measure -> th(scope = ThScope.col) { attributes["title"] = measure.title; +"Mesure ${index + 1}" } }
                    }
                }
                tbody {
                    board.rows.forEach { row ->
                        tr {
                            th(scope = ThScope.row) { +row.name }
                            row.cells.forEach { cell -> td { +CELL_LABELS.getValue(cell.status) } }
                        }
                    }
                }
            }
        }
        if (board.setAside.isNotEmpty()) p(classes = "muted") { +"${board.setAside.size} réponse(s) mise(s) à part (reçue après la date limite, sur un autre texte ou d’une autre personne) : visibles avec leur date, jamais comptées." }
    }
}

fun HTML.deciderNotFoundDocument() {
    siteLayout(pageTitle = "Page introuvable", currentSpace = Space.DECIDER) {
        h1 { +"Page introuvable" }
        p { +"Cette page de « Décider » n’existe pas ou plus." }
        p { a(href = DeciderLinks.HOME) { +"Revenir à « Décider »" } }
    }
}

internal const val STARTING_PROPOSAL: String = "Proposition de départ, pas encore votée : les citoyens la modifieront et la valideront par leur vote."

internal val STATUS_LABELS: Map<PacteStatus, String> = mapOf(
    PacteStatus.PROPOSED to "Proposition de départ",
    PacteStatus.SENT to "Envoyée aux candidats",
    PacteStatus.CLOSED to "Élection passée",
)

internal val PLATFORM_LABELS: Map<PetitionPlatform, String> = mapOf(
    PetitionPlatform.ASSEMBLEE to "Assemblée nationale",
    PetitionPlatform.SENAT to "Sénat",
    PetitionPlatform.CESE to "Conseil économique, social et environnemental",
    PetitionPlatform.RIP to "Référendum d’initiative partagée",
    PetitionPlatform.ICE to "Initiative citoyenne européenne",
)

internal val CELL_LABELS: Map<CellStatus, String> = mapOf(
    CellStatus.SIGNED to "Signé",
    CellStatus.REFUSED to "Refusé",
    CellStatus.AWAITING to "En attente",
    CellStatus.NO_ANSWER to "Sans réponse",
)
