package org.politisationpopulairefrancaise.web.voir

import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.ThScope
import kotlinx.html.a
import kotlinx.html.aside
import kotlinx.html.blockQuote
import kotlinx.html.caption
import kotlinx.html.div
import kotlinx.html.figcaption
import kotlinx.html.figure
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.header
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.nav
import kotlinx.html.ol
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.strong
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import kotlinx.html.ul
import kotlinx.html.unsafe
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.voir.Fact
import org.politisationpopulairefrancaise.core.voir.FactKind
import org.politisationpopulairefrancaise.core.voir.FactStatus
import org.politisationpopulairefrancaise.core.voir.FrenchNumbers
import org.politisationpopulairefrancaise.core.voir.Indicator
import org.politisationpopulairefrancaise.core.voir.LegislativeText
import org.politisationpopulairefrancaise.core.voir.LibertyCategory
import org.politisationpopulairefrancaise.core.voir.ProcedureStage
import org.politisationpopulairefrancaise.core.voir.PublicPerson
import org.politisationpopulairefrancaise.core.voir.Reliability
import org.politisationpopulairefrancaise.core.voir.TextStage
import org.politisationpopulairefrancaise.core.voir.VoirAxis
import org.politisationpopulairefrancaise.core.voir.VoirCatalog
import org.politisationpopulairefrancaise.core.voir.VoirDossier
import org.politisationpopulairefrancaise.core.voir.VoirItem
import org.politisationpopulairefrancaise.core.voir.VoirLinks
import org.politisationpopulairefrancaise.core.voir.WorldMovement
import org.politisationpopulairefrancaise.web.siteLayout

/**
 * The pages of "Voir" (cahier fonctionnel M2, M4, M9, M13.2, M17), readable without script: one
 * page per content at the stable address of [VoirLinks], the home of the space with its three axes,
 * the chronology, the observatory of freedoms, the log of corrections and the method. Every content
 * page ends with its sources and "Et maintenant ?". An unknown address answers a 404 page.
 */
fun Route.voirPages(source: VoirSource) {
    route(VoirLinks.HOME) {
        get {
            val catalog = source.catalog()
            call.respondHtml { voirHomeDocument(catalog) }
        }
        get("/chronologie") {
            val kind = FactKind.fromTag(call.request.queryParameters["type"])?.takeIf { it.inChronology }
            val catalog = source.catalog()
            call.respondHtml { chronologyDocument(catalog, kind) }
        }
        get("/libertes") {
            val category = LibertyCategory.fromTag(call.request.queryParameters["categorie"])
            val catalog = source.catalog()
            call.respondHtml { libertiesDocument(catalog, category) }
        }
        get("/corrections") {
            val catalog = source.catalog()
            call.respondHtml { correctionsDocument(catalog) }
        }
        get("/methode") {
            call.respondHtml { methodDocument() }
        }
        get("/{segment}/{slug}") {
            val segment = call.parameters["segment"].orEmpty()
            val slug = call.parameters["slug"].orEmpty()
            val catalog = source.catalog()
            val item = VoirIndex(catalog).find(segment, slug)
            if (item == null) {
                call.respondHtml(HttpStatusCode.NotFound) { voirNotFoundDocument() }
            } else {
                call.respondHtml { voirItemDocument(item, catalog) }
            }
        }
    }
}

/** The page of one content; separate from the route so that tests render it without a server. */
fun HTML.voirItemDocument(item: VoirItem, catalog: VoirCatalog) {
    siteLayout(pageTitle = item.title, currentSpace = Space.VOIR) {
        breadcrumb(item.title)
        when (item) {
            is VoirItem.OfIndicator -> indicatorPage(item.indicator, catalog)
            is VoirItem.OfFact -> factPage(item.fact, catalog)
            is VoirItem.OfPerson -> personPage(item.person, catalog)
            is VoirItem.OfParty -> partyPage(item.party, catalog)
            is VoirItem.OfText -> textPage(item.text, catalog)
            is VoirItem.OfMovement -> movementPage(item.movement, catalog)
            is VoirItem.OfDossier -> dossierPage(item.dossier, catalog)
        }
    }
}

fun HTML.voirNotFoundDocument() {
    siteLayout(pageTitle = "Contenu introuvable", currentSpace = Space.VOIR) {
        h1 { +"Contenu introuvable" }
        p { +"Ce contenu n’existe pas, ou il n’a pas été publié par l’équipe de Politisation Populaire Française. Vérifiez l’adresse, ou retrouvez tous les contenus de « Voir »." }
        p { a(href = VoirLinks.HOME) { +"Revenir à « Voir »" } }
    }
}

private fun FlowContent.breadcrumb(current: String?) {
    nav(classes = "breadcrumb") {
        attributes["aria-label"] = "Fil d’Ariane"
        ol {
            li { a(href = VoirLinks.HOME) { +"Voir" } }
            current?.let { li { span { attributes["aria-current"] = "page"; +it } } }
        }
    }
}

private fun FlowContent.pageHead(kicker: String?, title: String, lead: String?, badges: (FlowContent.() -> Unit)? = null) {
    header(classes = "page-head") {
        kicker?.let { p(classes = "kicker") { +it } }
        h1 { +title }
        badges?.invoke(this)
        lead?.let { p(classes = "lead") { +it } }
    }
}

// ---------------------------------------------------------------------------------------------
// Home of "Voir"

private const val PREVIEW: Int = 4

fun HTML.voirHomeDocument(catalog: VoirCatalog) {
    siteLayout(pageTitle = "Voir", currentSpace = Space.VOIR) {
        h1 { +"Voir" }
        p(classes = "lead") { +"Rendre visible, de façon chiffrée et sourcée, ce qui se passe et ce que fait le pouvoir." }
        aside(classes = "notice") {
            attributes["aria-label"] = "Comment lire ces contenus"
            p { +"Chaque chiffre et chaque fait a sa source, sa date et son niveau de fiabilité. Quand les sources divergent, elles sont affichées côte à côte, jamais fusionnées." }
            p { a(href = VoirLinks.METHOD) { +"Établi, allégué, fiabilité : ce que veulent dire les étiquettes" } }
        }
        if (catalog.isEmpty) {
            p { +"Les contenus de « Voir » ne sont pas encore disponibles." }
            return@siteLayout
        }
        nav(classes = "toc") {
            attributes["aria-label"] = "Sur cette page"
            ul {
                VoirAxis.entries.forEach { axis -> li { a(href = "#${axis.tagValue}") { +axis.label } } }
                li { a(href = VoirLinks.CORRECTIONS) { +"Journal des corrections" } }
            }
        }
        pageSection(VoirAxis.MOUVEMENT.tagValue, VoirAxis.MOUVEMENT.label) {
            p { +VoirAxis.MOUVEMENT.intro }
            keyFiguresBlock(catalog, VoirAxis.MOUVEMENT, "Chiffres clés")
            val chronology = catalog.chronology()
            subsection("Chronologie du mouvement") {
                if (chronology.isEmpty()) p(classes = "muted") { +"Rien de recensé pour l’instant." }
                chronology.take(PREVIEW).forEach { factCard(it, catalog, level = 4) }
                p { a(href = VoirLinks.CHRONOLOGY) { +"Toute la chronologie (${chronology.size})" } }
            }
            if (catalog.movements.isNotEmpty()) {
                subsection("Dans le monde") {
                    p { +"Les mouvements sociaux contre la vie chère et pour la démocratie ailleurs dans le monde : ce qu’ils demandaient, ce qu’ils ont obtenu." }
                    catalog.movements.forEach { movementCard(it, level = 4) }
                }
            }
        }
        pageSection(VoirAxis.POUVOIR.tagValue, VoirAxis.POUVOIR.label) {
            p { +VoirAxis.POUVOIR.intro }
            if (catalog.keyFigures(VoirAxis.POUVOIR).isNotEmpty()) keyFiguresBlock(catalog, VoirAxis.POUVOIR, "Chiffres clés")
            subsection("Responsables publics") {
                p { +"Chaque fiche présente les mêmes rubriques, dans le même ordre, quelle que soit la personne." }
                catalog.persons(null).forEach { personCard(it, catalog, level = 4) }
            }
            subsection("Textes en discussion") {
                p { +"Les projets et propositions de loi : où ils en sont, le prochain vote, qui y gagne et qui y perd." }
                catalog.bills().forEach { billCard(it, level = 4) }
            }
            subsection("Derniers faits sourcés") {
                catalog.latestAboutPower().forEach { factCard(it, catalog, level = 4) }
            }
        }
        pageSection(VoirAxis.LIBERTES.tagValue, VoirAxis.LIBERTES.label) {
            p { +VoirAxis.LIBERTES.intro }
            keyFiguresBlock(catalog, VoirAxis.LIBERTES, "Tableau de bord des contre-pouvoirs")
            val liberties = catalog.liberties()
            subsection("Observatoire des libertés publiques") {
                p { +"Interdictions, dissolutions, lois restrictives, surveillance : chaque mesure avec ses sources et l’état des recours." }
                liberties.take(PREVIEW).forEach { factCard(it, catalog, level = 4) }
                p { a(href = VoirLinks.LIBERTIES) { +"Tout l’observatoire (${liberties.size})" } }
            }
            val dossiers = catalog.dossiers
            if (dossiers.isNotEmpty()) {
                subsection("Dossiers") { dossiers.forEach { dossierCard(it, level = 4) } }
            }
        }
        nextSection(emptyList(), catalog, null)
    }
}

private fun FlowContent.subsection(title: String, block: FlowContent.() -> Unit) {
    div(classes = "subsection") {
        h3 { +title }
        block()
    }
}

private fun FlowContent.keyFiguresBlock(catalog: VoirCatalog, axis: VoirAxis, title: String) {
    val figures = catalog.keyFigures(axis)
    subsection(title) {
        if (figures.isEmpty()) p(classes = "muted") { +"Rien de recensé pour l’instant." }
        div(classes = "card-grid") { figures.forEach { keyFigureCard(it, level = 4) } }
    }
}

// ---------------------------------------------------------------------------------------------
// An indicator

private fun FlowContent.indicatorPage(indicator: Indicator, catalog: VoirCatalog) {
    pageHead("${indicator.axis.label} · Mis à jour le ${indicator.lastUpdate.toFrench()}", indicator.title, indicator.definition) {
        val latest = indicator.latest
        if (indicator.declarative || latest?.diverges == true) {
            div(classes = "badges") {
                if (latest?.diverges == true) badge("Sources divergentes", BadgeTone.WARNING)
                if (indicator.declarative) badge("Déclaratif", BadgeTone.WARNING)
            }
        }
    }
    if (indicator.declarative) {
        p(classes = "notice") { +"Indicateur déclaratif : il additionne ce que déclarent les utilisateurs, sans vérification possible." }
    }
    val latest = indicator.latest
    pageSection("derniere-mesure", "Dernière mesure") {
        if (latest == null) {
            p(classes = "muted") { +"Aucune mesure publiée pour l’instant." }
            return@pageSection
        }
        p(classes = "kicker") {
            +latest.occasion
            +" · "
            date(latest.date)
            latest.place?.let { +" · $it" }
        }
        ul(classes = "reading") {
            latest.measures.forEach { measure ->
                li(classes = "reading-item") {
                    p(classes = "figure-line") {
                        span(classes = "figure-value") { +valueText(measure.value, indicator.unit) }
                        if (indicator.unit != "%") +" ${indicator.unit}"
                    }
                    p {
                        strong { +measure.source.kind.label }
                        +" : "
                        val url = measure.source.url?.takeIf(::isWebAddress)
                        if (url != null) externalLink(url, measure.source.name) else +measure.source.name
                    }
                    div(classes = "badges") { badge(measure.reliability.label, measure.reliability.tone) }
                    indicator.breakdown?.let { label -> measure.part?.let { p { +"$label : ${FrenchNumbers.format(it)}" } } }
                }
            }
        }
        p(classes = "muted") { +SIDE_BY_SIDE_NOTE }
    }
    pageSection("evolution", "Évolution") {
        val svg = VoirChart.svg(indicator)
        if (svg == null) {
            p(classes = "muted") { +"Aucune mesure publiée pour l’instant." }
            return@pageSection
        }
        val series = indicator.series
        val slots = VoirChart.slots(series.map { it.kind })
        figure(classes = "chart-figure") {
            div(classes = "chart-box") { unsafe { +svg } }
            figcaption {
                ul(classes = "legend") {
                    series.forEach { s ->
                        li {
                            span(classes = "swatch chart-s${slots.getValue(s.kind)}") { attributes["aria-hidden"] = "true" }
                            +s.kind.label
                        }
                    }
                }
            }
        }
        chartTable(indicator)
    }
    pageSection("toutes-les-mesures", "Toutes les mesures") {
        div(classes = "table-scroll") {
            attributes["tabindex"] = "0"
            attributes["role"] = "region"
            attributes["aria-label"] = "Toutes les mesures, tableau"
            table {
                caption { +"Chaque valeur publiée, avec sa source et son niveau de fiabilité" }
                thead {
                    tr {
                        listOf("Date", "Occasion", "Lieu", "Source", "Valeur", "Fiabilité").forEach { th(ThScope.col) { +it } }
                    }
                }
                tbody {
                    indicator.readings.asReversed().forEach { reading ->
                        reading.measures.forEach { measure ->
                            tr {
                                td { date(measure.date, short = true) }
                                td { +(measure.occasion ?: "") }
                                td { +(measure.place ?: "") }
                                td {
                                    +"${measure.source.kind.label} : "
                                    val url = measure.source.url?.takeIf(::isWebAddress)
                                    if (url != null) externalLink(url, measure.source.name) else +measure.source.name
                                }
                                td(classes = "num") { +(valueText(measure.value, indicator.unit) + if (indicator.unit != "%") " ${indicator.unit}" else "") }
                                td { +measure.reliability.label }
                            }
                        }
                    }
                }
            }
        }
    }
    pageSection("methode", "Méthode") {
        if (indicator.acceptedSources.isNotEmpty()) {
            p { +"Sources retenues : ${indicator.acceptedSources.joinToString(", ") { it.label }}" }
        }
        markdown(indicator.method)
        val levels = indicator.measures.map { it.reliability }.distinct().sortedBy { it.ordinal }
        if (levels.isNotEmpty()) ul { levels.forEach { li { +it.explanation } } }
    }
    val measureSources = indicator.measures.map { it.source.copy(date = null) }.distinctBy { it.name to it.url }
    sourcesSection((indicator.sources + measureSources).distinctBy { it.name to it.url })
    correctionsSection(indicator.corrections)
    repliesSection(catalog.repliesTo(indicator.address), offerReply = false)
    originLine(indicator.article)
    nextSection(indicator.nextSteps, catalog, VoirItem.OfIndicator(indicator))
}

/** The data of the chart, as a table: one row per occasion, one column per kind of source. */
private fun FlowContent.chartTable(indicator: Indicator) {
    val kinds = indicator.series.map { it.kind }
    div(classes = "table-scroll") {
        attributes["tabindex"] = "0"
        attributes["role"] = "region"
        attributes["aria-label"] = "Données du graphique, tableau"
        table(classes = "chart-table") {
            caption { +"Les données du graphique en tableau (${indicator.unit})" }
            thead {
                tr {
                    th(ThScope.col) { +"Date" }
                    th(ThScope.col) { +"Occasion" }
                    kinds.forEach { th(ThScope.col) { +it.label } }
                }
            }
            tbody {
                indicator.readings.forEach { reading ->
                    tr {
                        th(ThScope.row) { +reading.date.toFrenchShort() }
                        td { +reading.occasion }
                        kinds.forEach { kind ->
                            val values = reading.measures.filter { it.source.kind == kind }
                            td(classes = "num") { +(values.joinToString(" ; ") { valueText(it.value, indicator.unit) }.ifEmpty { "non publié" }) }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// A sourced fact

private fun FlowContent.factPage(fact: Fact, catalog: VoirCatalog) {
    val kicker = listOfNotNull(fact.kind.label, fact.date.toFrench(), fact.place).joinToString(" · ")
    pageHead(kicker, fact.title, fact.article.summary) { factBadges(fact) }
    fact.procedure?.let { procedure ->
        procedureBlock(procedure)
    }
    fact.quote?.let { quote ->
        figure(classes = "quote") {
            figcaption { +"Ce qui a été dit" }
            blockQuote { p { +"« $quote »" } }
        }
    }
    if (fact.sentence != null || fact.sentenceServed != null) {
        div(classes = "notice") {
            fact.sentence?.let { p { +"Peine prononcée : $it" } }
            fact.sentenceServed?.let { p { +"Peine réellement exécutée : $it" } }
        }
    }
    section(classes = "voir-body") {
        attributes["aria-label"] = "Le fait"
        p(classes = "muted") { +fact.status.explanation }
        markdown(fact.article.body)
    }
    val persons = catalog.personsOf(fact)
    val texts = catalog.textsOf(fact)
    if (persons.isNotEmpty() || texts.isNotEmpty()) {
        pageSection("concerne", "Concerne") {
            ul(classes = "link-list") {
                persons.forEach { person ->
                    li {
                        a(href = VoirLinks.pathOf(VoirItem.OfPerson(person))) { +person.name }
                        person.function?.let { +", $it" }
                    }
                }
                texts.forEach { text -> li { a(href = VoirLinks.pathOf(VoirItem.OfText(text))) { +text.title } } }
            }
        }
    }
    sourcesSection(fact.sources)
    archivesSection(fact.archives)
    correctionsSection(fact.corrections)
    repliesSection(catalog.repliesTo(fact.address), offerReply = persons.isNotEmpty())
    originLine(fact.article)
    nextSection(fact.nextSteps, catalog, VoirItem.OfFact(fact))
}

/** Where the procedure stands, always shown, with the presumption of innocence until a final conviction. */
private fun FlowContent.procedureBlock(procedure: ProcedureStage) {
    section(classes = "procedure") {
        attributes["aria-labelledby"] = "procedure"
        h2 {
            id = "procedure"
            +"Où en est la procédure"
        }
        p {
            strong { +procedure.label }
            +". ${procedure.explanation}"
        }
        if (procedure.presumedInnocent) presumptionNotice()
    }
}

private fun FlowContent.presumptionNotice() {
    p(classes = "notice presumption") {
        attributes["role"] = "note"
        +PRESUMPTION_NOTICE
    }
}

// ---------------------------------------------------------------------------------------------
// A public person: the same sections, in the same order, for everyone

private fun FlowContent.personPage(person: PublicPerson, catalog: VoirCatalog) {
    val facts = catalog.factsAbout(person.address)
    pageHead(person.roles.joinToString(", ") { it.label }, person.name, person.function) {
        person.group?.let { p(classes = "muted") { +"Groupe : $it" } }
        person.territory?.let { p(classes = "muted") { +it } }
    }
    p(classes = "notice") { +"Chaque fiche présente les mêmes rubriques, dans le même ordre, quelle que soit la personne." }
    pageSection("mandats", "Mandats et fonctions") {
        if (person.mandates.isEmpty()) p(classes = "muted") { +"Rien de recensé pour l’instant." }
        ul(classes = "link-list") {
            person.mandates.forEach { mandate ->
                li {
                    strong { +mandate.title }
                    mandate.territory?.let { +", $it" }
                    val start = mandate.start
                    val end = mandate.end
                    when {
                        start != null && end != null -> {
                            +", du "
                            date(start)
                            +" au "
                            date(end)
                        }
                        start != null -> {
                            +", depuis le "
                            date(start)
                        }
                    }
                    if (mandate.current) {
                        +" "
                        badge("En cours", BadgeTone.SUCCESS)
                    }
                }
            }
        }
    }
    PERSON_SECTIONS.forEach { kind ->
        val list = facts.filter { it.kind == kind }
        pageSection("rubrique-${kind.tagValue}", "${kind.section} (${list.size})") {
            if (kind == FactKind.CASE && list.any { it.procedure?.presumedInnocent == true }) presumptionNotice()
            if (list.isEmpty()) p(classes = "muted") { +"Rien de recensé pour l’instant." }
            list.forEach { factCard(it, catalog, level = 3) }
        }
    }
    if (person.article.body.isNotBlank()) {
        section(classes = "voir-body") {
            attributes["aria-label"] = "Présentation"
            markdown(person.article.body)
        }
    }
    sourcesSection(person.sources)
    correctionsSection(person.corrections)
    repliesSection(catalog.repliesTo(person.address), offerReply = true)
    originLine(person.article)
    nextSection(emptyList(), catalog, VoirItem.OfPerson(person))
}

// ---------------------------------------------------------------------------------------------
// A bill (législomètre)

private fun FlowContent.textPage(text: LegislativeText, catalog: VoirCatalog) {
    val kicker = when (text.textKind) {
        "proposition" -> "Proposition de loi, déposée par des parlementaires"
        "projet" -> "Projet de loi, déposé par le gouvernement"
        else -> null
    }
    pageHead(kicker, text.title, text.article.summary)
    text.nextVote?.let { vote ->
        p(classes = "notice") {
            strong {
                +"Prochain vote le "
                date(vote.date)
            }
            if (vote.label.isNotBlank()) +" : ${vote.label}"
        }
    }
    pageSection("etape", "Où en est le texte") {
        p { +stageWords(text) }
        val path = TextStage.path
        val reached = path.indexOfLast { it.order <= text.stage.order }.coerceAtLeast(0)
        ol(classes = "stages") {
            path.forEachIndexed { index, stage ->
                li(classes = if (index <= reached) "stage stage-done" else "stage") {
                    if (index == reached) attributes["aria-current"] = "step"
                    +stage.label
                    text.history.lastOrNull { it.stage == stage }?.let {
                        +", le "
                        date(it.date, short = true)
                    }
                }
            }
        }
        if (text.stage !in path) p { +"Étape actuelle : ${text.stage.label}" }
        if (text.uses493.isNotEmpty()) {
            h3 { +"Article 49.3 : ${text.uses493.size} recours" }
            div(classes = "badges") { text.uses493.forEach { badge("49.3 le ${it.toFrenchShort()}", BadgeTone.ACCENT) } }
        }
    }
    pageSection("qui-gagne-qui-perd", "Qui gagne, qui perd") {
        if (text.impacts.isEmpty()) p(classes = "muted") { +"Aucun effet chiffré publié pour l’instant." }
        text.impacts.forEach { impact ->
            div(classes = "impact") {
                h3 {
                    +impact.group
                    +" "
                    badge(impact.direction.label, impact.direction.tone)
                }
                p { +impact.description }
                impact.source?.let { source ->
                    p(classes = "muted") {
                        +"Source : "
                        val url = source.url?.takeIf(::isWebAddress)
                        if (url != null) externalLink(url, source.name) else +source.name
                    }
                }
            }
        }
    }
    if (text.article.body.isNotBlank()) {
        section(classes = "voir-body") {
            attributes["aria-label"] = "Le texte"
            markdown(text.article.body)
        }
    }
    val votes = catalog.factsOn(text.address, FactKind.VOTE)
    val lobbying = catalog.factsOn(text.address, FactKind.LOBBYING)
    val others = catalog.factsOn(text.address).filter { it.kind != FactKind.VOTE && it.kind != FactKind.LOBBYING }
    pageSection("votes", "Votes des élus (${votes.size})") {
        if (votes.isEmpty()) p(classes = "muted") { +"Rien de recensé pour l’instant." }
        votes.forEach { factCard(it, catalog, level = 3) }
    }
    pageSection("influence", "Influence des représentants d’intérêts (${lobbying.size})") {
        if (lobbying.isEmpty()) p(classes = "muted") { +"Rien de recensé pour l’instant." }
        lobbying.forEach { factCard(it, catalog, level = 3) }
    }
    if (others.isNotEmpty()) {
        pageSection("autres-faits", "Autres faits liés (${others.size})") { others.forEach { factCard(it, catalog, level = 3) } }
    }
    sourcesSection(text.sources)
    correctionsSection(text.corrections)
    repliesSection(catalog.repliesTo(text.address), offerReply = false)
    originLine(text.article)
    nextSection(text.nextSteps, catalog, VoirItem.OfText(text))
}

// ---------------------------------------------------------------------------------------------
// A movement of the world, a dossier

private fun FlowContent.movementPage(movement: WorldMovement, catalog: VoirCatalog) {
    pageHead("${movement.country} · ${periodText(movement)}", movement.title, movement.article.summary)
    if (movement.claims.isNotEmpty()) {
        pageSection("revendications", "Ce qu’ils demandaient") { ul { movement.claims.forEach { li { +it } } } }
    }
    if (movement.results.isNotEmpty()) {
        pageSection("resultats", "Ce qu’ils ont obtenu") { ul { movement.results.forEach { li { +it } } } }
    }
    if (movement.article.body.isNotBlank()) {
        section(classes = "voir-body") {
            attributes["aria-label"] = "Le mouvement"
            markdown(movement.article.body)
        }
    }
    sourcesSection(movement.sources)
    originLine(movement.article)
    nextSection(emptyList(), catalog, VoirItem.OfMovement(movement))
}

/**
 * A party on the site: its presentation, sources and origin. The full sheet of the application
 * (financing, seats, public aid, members) comes to the site in a later step.
 */
private fun FlowContent.partyPage(party: org.politisationpopulairefrancaise.core.voir.PoliticalParty, catalog: VoirCatalog) {
    pageHead(listOfNotNull("Parti politique", party.acronym).joinToString(" · "), party.name, party.article.summary)
    if (party.article.body.isNotBlank()) {
        section(classes = "voir-body") {
            attributes["aria-label"] = "Présentation du parti"
            markdown(party.article.body)
        }
    }
    sourcesSection(party.sources)
    originLine(party.article)
    nextSection(party.nextSteps, catalog, VoirItem.OfParty(party))
}

private fun FlowContent.dossierPage(dossier: VoirDossier, catalog: VoirCatalog) {
    pageHead("Dossier · ${dossier.axis.label}", dossier.title, dossier.article.summary)
    section(classes = "voir-body") {
        attributes["aria-label"] = "Le dossier"
        markdown(dossier.article.body)
    }
    sourcesSection(emptyList(), empty = "Les sources sont citées dans le texte du dossier.")
    originLine(dossier.article)
    nextSection(emptyList(), catalog, VoirItem.OfDossier(dossier))
}

// ---------------------------------------------------------------------------------------------
// Lists: chronology, freedoms, corrections, method

fun HTML.chronologyDocument(catalog: VoirCatalog, kind: FactKind?) {
    siteLayout(pageTitle = "Chronologie du mouvement", currentSpace = Space.VOIR) {
        breadcrumb("Chronologie du mouvement")
        h1 { +"Chronologie du mouvement" }
        p(classes = "lead") { +"Manifestations, déclarations et décisions officielles, réponses du gouvernement, du plus récent au plus ancien." }
        val kinds = FactKind.chronology.filter { k -> catalog.chronology().any { it.kind == k } }
        filterLinks(VoirLinks.CHRONOLOGY, "type", kinds.map { it.tagValue to it.label }, kind?.tagValue, "Filtrer la chronologie")
        val facts = catalog.chronology(kind?.let { setOf(it) } ?: FactKind.chronology.toSet())
        if (facts.isEmpty()) p { +"Rien de recensé pour l’instant." }
        facts.groupBy { it.date.monthYearFrench() }.forEach { (month, list) ->
            val anchor = "mois-" + list.first().date.toIso().take(7)
            pageSection(anchor, month.replaceFirstChar { it.uppercase() }) { list.forEach { factCard(it, catalog, level = 3) } }
        }
        nextSection(emptyList(), catalog, null)
    }
}

fun HTML.libertiesDocument(catalog: VoirCatalog, category: LibertyCategory?) {
    siteLayout(pageTitle = "Observatoire des libertés publiques", currentSpace = Space.VOIR) {
        breadcrumb("Observatoire des libertés publiques")
        h1 { +"Observatoire des libertés publiques" }
        p(classes = "lead") { +"Interdictions, dissolutions, lois restrictives, surveillance : chaque mesure avec ses sources et l’état des recours." }
        filterLinks(VoirLinks.LIBERTIES, "categorie", catalog.libertyCategoriesInUse().map { it.tagValue to it.label }, category?.tagValue, "Filtrer l’observatoire")
        val facts = catalog.liberties(category)
        section {
            attributes["aria-labelledby"] = "mesures"
            h2 {
                id = "mesures"
                +when (facts.size) {
                    0 -> "Aucune mesure recensée"
                    1 -> "1 mesure"
                    else -> "${facts.size} mesures"
                }
            }
            facts.forEach { factCard(it, catalog, level = 3) }
        }
        nextSection(emptyList(), catalog, null)
    }
}

fun HTML.correctionsDocument(catalog: VoirCatalog) {
    siteLayout(pageTitle = "Journal des corrections", currentSpace = Space.VOIR) {
        breadcrumb("Journal des corrections")
        h1 { +"Journal des corrections" }
        p(classes = "lead") { +"Toutes les corrections apportées aux contenus de « Voir », avec leur date et leur motif. Une plateforme qui demande des comptes doit rendre les siens." }
        val entries = catalog.corrections()
        section {
            attributes["aria-label"] = "Corrections"
            if (entries.isEmpty()) p { +"Aucune correction pour l’instant." }
            ul(classes = "corrections-log") {
                entries.forEach { entry ->
                    li {
                        date(entry.correction.date)
                        +" : "
                        a(href = VoirLinks.pathOf(entry.item)) { +entry.item.title }
                        p { +entry.correction.reason }
                    }
                }
            }
        }
        nextSection(emptyList(), catalog, null)
    }
}

fun HTML.methodDocument() {
    siteLayout(pageTitle = "Lire les étiquettes de « Voir »", currentSpace = Space.VOIR) {
        breadcrumb("Méthode")
        h1 { +"Lire les étiquettes de « Voir »" }
        p(classes = "lead") { +"Chaque chiffre et chaque fait a sa source, sa date et son niveau de fiabilité. Quand les sources divergent, elles sont affichées côte à côte, jamais fusionnées." }
        pageSection("statut", "Établi, allégué, en cours d’enquête") {
            ul(classes = "glossary") {
                FactStatus.entries.forEach { status ->
                    li {
                        badge(status.label, status.tone)
                        p { +status.explanation }
                    }
                }
            }
        }
        pageSection("fiabilite", "Niveaux de fiabilité d’un chiffre") {
            ul(classes = "glossary") {
                Reliability.entries.forEach { level ->
                    li {
                        badge(level.label, level.tone)
                        p { +level.explanation }
                    }
                }
            }
        }
        pageSection("sources-cote-a-cote", "Les sources côte à côte") {
            p { +"Autorités, organisateurs, comptages indépendants, médias : quand ils ne donnent pas le même chiffre, PPF les affiche tous, chacun avec sa méthode. L’écart entre les sources est lui-même une information." }
        }
        pageSection("procedure-judiciaire", "Les étapes d’une procédure judiciaire") {
            ul(classes = "glossary") {
                ProcedureStage.entries.forEach { stage ->
                    li {
                        badge(stage.label, stage.tone)
                        p { +stage.explanation }
                    }
                }
            }
            p(classes = "notice") { +PRESUMPTION_LAW }
        }
        pageSection("corrections-et-reponses", "Corrections et droit de réponse") {
            p { +"Toute erreur est corrigée et la correction est datée, avec son motif, sur le contenu et dans le journal des corrections. Toute personne nommée peut exercer un droit de réponse, publié sous le contenu." }
            p { a(href = VoirLinks.CORRECTIONS) { +"Journal des corrections" } }
        }
    }
}

/** Filters as plain links (no script, no form): the current one is marked for screen readers. */
private fun FlowContent.filterLinks(base: String, parameter: String, values: List<Pair<String, String>>, selected: String?, label: String) {
    if (values.isEmpty()) return
    nav(classes = "filters") {
        attributes["aria-label"] = label
        ul {
            li {
                a(href = base, classes = "chip") {
                    if (selected == null) attributes["aria-current"] = "true"
                    +"Tout"
                }
            }
            values.forEach { (value, text) ->
                li {
                    a(href = "$base?$parameter=$value", classes = "chip") {
                        if (selected == value) attributes["aria-current"] = "true"
                        +text
                    }
                }
            }
        }
    }
}
