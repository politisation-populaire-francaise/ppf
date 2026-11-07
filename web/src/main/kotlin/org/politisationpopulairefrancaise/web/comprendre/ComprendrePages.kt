package org.politisationpopulairefrancaise.web.comprendre

import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.article
import kotlinx.html.blockQuote
import kotlinx.html.button
import kotlinx.html.dd
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.nav
import kotlinx.html.ol
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.strong
import kotlinx.html.summary
import kotlinx.html.ul
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.comprendre.ComprendreLinks
import org.politisationpopulairefrancaise.core.comprendre.Decryptage
import org.politisationpopulairefrancaise.core.comprendre.GlossaryEntry
import org.politisationpopulairefrancaise.core.comprendre.GlossaryIndex
import org.politisationpopulairefrancaise.core.comprendre.HistoryStory
import org.politisationpopulairefrancaise.core.comprendre.Parcours
import org.politisationpopulairefrancaise.core.comprendre.ParcoursStep
import org.politisationpopulairefrancaise.core.comprendre.QuiDecide
import org.politisationpopulairefrancaise.core.comprendre.QuizQuestion
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.voir.VoirItem
import org.politisationpopulairefrancaise.core.voir.VoirLinks
import org.politisationpopulairefrancaise.web.siteLayout
import org.politisationpopulairefrancaise.web.voir.badge

/**
 * The pages of "Comprendre" on the web site (D-091), readable without script: the practical guides
 * first (kept for offline reading by the service worker, and all on one page to save or print), the
 * courses with their quizzes (the answer behind a `<details>`, nothing is ever sent to the server),
 * the fact-checks, the history of social movements, the « Qui décide de ça ? » sheets, the method and
 * the glossary. Each page sits at the stable address of [ComprendreLinks]; an unknown address answers a
 * 404 page.
 */
fun Route.comprendrePages(source: ComprendreSource) {
    get(OfflineWorker.PATH) { OfflineWorker.respond(call, source.catalog()) }
    route(ComprendreLinks.HOME) {
        get { page(source) { comprendreHomeDocument(it) } }
        get("/${ComprendreLinks.FICHES}") { page(source) { fichesDocument(it) } }
        get("/${ComprendreLinks.FICHES}/{slug}") { item(source, { catalog, slug -> catalog.fiche(slug) }) { value, catalog -> ficheDocument(value, catalog) } }
        get("/${ComprendreLinks.PARCOURS}") { page(source) { parcoursListDocument(it) } }
        get("/${ComprendreLinks.PARCOURS}/{slug}") { item(source, { catalog, slug -> catalog.course(slug) }) { value, catalog -> parcoursDocument(value, catalog) } }
        get("/${ComprendreLinks.DECRYPTAGES}") { page(source) { decryptagesDocument(it) } }
        get("/${ComprendreLinks.DECRYPTAGES}/{slug}") { item(source, { catalog, slug -> catalog.decryptage(slug) }) { value, catalog -> decryptageDocument(value, catalog) } }
        get("/${ComprendreLinks.HISTOIRE}") { page(source) { storiesDocument(it) } }
        get("/${ComprendreLinks.HISTOIRE}/{slug}") { item(source, { catalog, slug -> catalog.story(slug) }) { value, catalog -> storyDocument(value, catalog) } }
        get("/${ComprendreLinks.QUI_DECIDE}") { page(source) { sheetsDocument(it) } }
        get("/${ComprendreLinks.QUI_DECIDE}/{slug}") { item(source, { catalog, slug -> catalog.sheet(slug) }) { value, catalog -> sheetDocument(value, catalog) } }
        get("/${ComprendreLinks.GLOSSAIRE}") {
            val query = call.request.queryParameters["q"]?.take(MAX_QUERY)?.trim().orEmpty()
            page(source) { glossaryDocument(it, query) }
        }
        get("/${ComprendreLinks.GLOSSAIRE}/{slug}") { item(source, { catalog, slug -> catalog.glossaryEntry(slug) }) { value, catalog -> glossaryEntryDocument(value, catalog) } }
        get("/methode") { item(source, { catalog, _ -> catalog.method }) { value, catalog -> methodDocument(value, catalog) } }
        get("/hors-ligne") { page(source) { offlineDocument(it) } }
        get("/{rest...}") { call.respondHtml(HttpStatusCode.NotFound) { comprendreNotFoundDocument() } }
    }
}

private const val MAX_QUERY: Int = 100

private suspend fun RoutingContext.page(source: ComprendreSource, document: HTML.(ComprendreCatalog) -> Unit) {
    val catalog = source.catalog()
    call.respondHtml { document(catalog) }
}

/** The page of one content found by [find] from the `slug` of the address, or the 404 page. */
private suspend fun <T : Any> RoutingContext.item(
    source: ComprendreSource,
    find: (ComprendreCatalog, String) -> T?,
    document: HTML.(T, ComprendreCatalog) -> Unit,
) {
    val catalog = source.catalog()
    val value = find(catalog, call.parameters["slug"].orEmpty())
    if (value == null) call.respondHtml(HttpStatusCode.NotFound) { comprendreNotFoundDocument() } else call.respondHtml { document(value, catalog) }
}

// ---------------------------------------------------------------------------------------------
// Home and lists

fun HTML.comprendreHomeDocument(catalog: ComprendreCatalog) {
    siteLayout(pageTitle = "Comprendre", currentSpace = Space.COMPRENDRE) {
        comprendreHead(null, "Comprendre", "Qui décide de quoi, comment fonctionnent les institutions, vos droits, le vrai et le faux.")
        if (catalog.isEmpty) p(classes = "notice") { +"Aucun contenu publié pour l’instant." }
        homeSection("droits", "Connaître ses droits", "Des fiches pratiques à garder sur soi, lisibles sans connexion.", ComprendreLinks.FICHES, "Toutes les fiches pratiques", catalog.fiches.size) {
            catalog.fiches.take(HOME_ITEMS).forEach { articleCard(it, "Fiche pratique", 3) }
            p { a(href = ComprendreLinks.OFFLINE) { +"Lire les fiches sans connexion" } }
        }
        homeSection("parcours", "Comprendre en 5 minutes", "Les institutions expliquées simplement, étape par étape, avec une question à la fin de chaque étape.", ComprendreLinks.PARCOURS, "Tous les parcours", catalog.parcours.size) {
            catalog.parcours.take(HOME_ITEMS).forEach { courseCard(it, 3) }
        }
        homeSection("qui-decide", "Qui décide de ça ?", "Pour un sujet de la vie quotidienne : qui décide de quoi, et quels élus interpeller.", ComprendreLinks.QUI_DECIDE, "Toutes les fiches « Qui décide de ça ? »", catalog.sheets.size) {
            catalog.sheets.take(HOME_ITEMS).forEach { sheetCard(it, 3) }
        }
        homeSection("vrai-faux", "Le vrai du faux", "Des affirmations qui circulent, vérifiées sur les textes et les sources.", ComprendreLinks.DECRYPTAGES, "Tous les décryptages", catalog.decryptages.size) {
            catalog.decryptages.take(HOME_ITEMS).forEach { decryptageCard(it, 3) }
            if (catalog.method != null) p { a(href = ComprendreLinks.METHOD) { +"Comment nous choisissons un verdict" } }
        }
        homeSection("histoire", "Histoire des luttes", "Ce que les mouvements sociaux et les révolutions ont obtenu, et pourquoi.", ComprendreLinks.HISTOIRE, "Toute l’histoire des luttes", catalog.stories.size) {
            catalog.stories.take(HOME_ITEMS).forEach { storyCard(it, 3) }
        }
        comprendreSection("glossaire", "Glossaire") {
            p { +"${catalog.glossary.size} mots de la vie politique, expliqués simplement. Dans les textes, ces mots mènent à leur définition." }
            p { a(href = ComprendreLinks.listPath(ComprendreLinks.GLOSSAIRE)) { +"Ouvrir le glossaire" } }
        }
        if (catalog.method != null) {
            comprendreSection("methode", "Notre méthode") {
                p { +"Comment nous vérifions une affirmation, ce que veut dire chaque verdict, comment demander une correction." }
                p { a(href = ComprendreLinks.METHOD) { +"Lire notre méthode" } }
            }
        }
        offlineScript()
    }
}

private const val HOME_ITEMS: Int = 4

private fun FlowContent.homeSection(id: String, title: String, intro: String, segment: String, allLabel: String, count: Int, block: FlowContent.() -> Unit) {
    comprendreSection(id, title) {
        p(classes = "muted") { +intro }
        if (count == 0) {
            p(classes = "muted") { +"Rien de publié pour l’instant." }
            return@comprendreSection
        }
        div(classes = "card-list") { block() }
        p { a(href = ComprendreLinks.listPath(segment)) { +"$allLabel ($count)" } }
    }
}

private fun FlowContent.listDocumentBody(title: String, lead: String, empty: Boolean, block: FlowContent.() -> Unit) {
    comprendreBreadcrumb(null, title)
    comprendreHead(null, title, lead)
    section(classes = "card-list") {
        attributes["aria-label"] = title
        if (empty) p(classes = "muted") { +"Rien de publié pour l’instant." } else block()
    }
}

fun HTML.fichesDocument(catalog: ComprendreCatalog) {
    siteLayout(pageTitle = "Fiches pratiques", currentSpace = Space.COMPRENDRE) {
        listDocumentBody("Fiches pratiques", "Connaître ses droits : des fiches à garder sur soi, lisibles sans connexion.", catalog.fiches.isEmpty()) {
            catalog.fiches.forEach { articleCard(it, "Fiche pratique", 2) }
        }
        offlineNotice()
        offlineScript()
    }
}

fun HTML.parcoursListDocument(catalog: ComprendreCatalog) {
    siteLayout(pageTitle = "Comprendre en 5 minutes", currentSpace = Space.COMPRENDRE) {
        listDocumentBody("Comprendre en 5 minutes", "Les institutions expliquées simplement, étape par étape, avec une question à la fin de chaque étape.", catalog.parcours.isEmpty()) {
            catalog.parcours.forEach { courseCard(it, 2) }
        }
        offlineScript()
    }
}

fun HTML.decryptagesDocument(catalog: ComprendreCatalog) {
    siteLayout(pageTitle = "Le vrai du faux", currentSpace = Space.COMPRENDRE) {
        listDocumentBody("Le vrai du faux", "Des affirmations qui circulent, vérifiées sur les textes et les sources. Le verdict porte sur une affirmation, jamais sur une personne.", catalog.decryptages.isEmpty()) {
            catalog.decryptages.forEach { decryptageCard(it, 2) }
        }
        if (catalog.method != null) p { a(href = ComprendreLinks.METHOD) { +"Comment nous choisissons un verdict" } }
        offlineScript()
    }
}

fun HTML.storiesDocument(catalog: ComprendreCatalog) {
    siteLayout(pageTitle = "Histoire des luttes", currentSpace = Space.COMPRENDRE) {
        listDocumentBody("Histoire des luttes", "Ce que les mouvements sociaux et les révolutions ont obtenu, et pourquoi.", catalog.stories.isEmpty()) {
            catalog.stories.forEach { storyCard(it, 2) }
        }
        offlineScript()
    }
}

fun HTML.sheetsDocument(catalog: ComprendreCatalog) {
    siteLayout(pageTitle = "Qui décide de ça ?", currentSpace = Space.COMPRENDRE) {
        listDocumentBody("Qui décide de ça ?", "Pour un sujet de la vie quotidienne : qui décide de quoi, et quels élus interpeller.", catalog.sheets.isEmpty()) {
            catalog.sheets.forEach { sheetCard(it, 2) }
        }
        offlineScript()
    }
}

// ---------------------------------------------------------------------------------------------
// Cards

private fun FlowContent.cardTitle(level: Int, path: String, title: String) {
    headingOf(this, level) { a(href = path) { +title } }
}

private fun FlowContent.articleCard(article: Article, kicker: String, level: Int) {
    article(classes = "card") {
        p(classes = "kicker") { +kicker }
        cardTitle(level, ComprendreLinks.pathOf(article)!!, article.title)
        article.summary?.let { p { +it } }
    }
}

private fun FlowContent.courseCard(course: Parcours, level: Int) {
    article(classes = "card") {
        p(classes = "kicker") { +"Parcours · ${courseMeta(course)}" }
        cardTitle(level, ComprendreLinks.pathOf(course.article)!!, course.article.title)
        course.article.summary?.let { p { +it } }
    }
}

private fun FlowContent.decryptageCard(check: Decryptage, level: Int) {
    article(classes = "card") {
        p(classes = "kicker") { +"Décryptage" }
        cardTitle(level, ComprendreLinks.pathOf(check.article)!!, check.article.title)
        div(classes = "badges") { badge("Verdict : ${check.verdict.label}", check.verdict.tone) }
        check.article.summary?.let { p { +it } }
    }
}

private fun FlowContent.storyCard(story: HistoryStory, level: Int) {
    article(classes = "card") {
        p(classes = "kicker") { +"${story.country} · ${story.period}" }
        cardTitle(level, ComprendreLinks.pathOf(story.article)!!, story.article.title)
        story.article.summary?.let { p { +it } }
    }
}

private fun FlowContent.sheetCard(sheet: QuiDecide, level: Int) {
    article(classes = "card") {
        p(classes = "kicker") { +"Qui décide ? · ${sheet.steps.joinToString(", ") { it.level.label }}" }
        cardTitle(level, ComprendreLinks.pathOf(sheet.article)!!, sheet.article.title)
        sheet.article.summary?.let { p { +it } }
    }
}

/** « 5 min · 5 étapes · 5 questions », as the application says it. */
internal fun courseMeta(course: Parcours): String = buildList {
    course.article.durationMinutes?.let { add("$it min") }
    add(if (course.steps.size > 1) "${course.steps.size} étapes" else "1 étape")
    add(if (course.quizCount > 1) "${course.quizCount} questions" else "${course.quizCount} question")
}.joinToString(" · ")

// ---------------------------------------------------------------------------------------------
// Content pages

private val ficheSection = "Fiches pratiques" to ComprendreLinks.listPath(ComprendreLinks.FICHES)

fun HTML.ficheDocument(fiche: Article, catalog: ComprendreCatalog) {
    val renderer = ComprendreRenderer(catalog.glossary)
    val parts = splitBody(fiche.body)
    siteLayout(pageTitle = fiche.title, currentSpace = Space.COMPRENDRE) {
        comprendreBreadcrumb(ficheSection, fiche.title)
        comprendreHead("Fiche pratique", fiche.title, fiche.summary) { contentBadges(fiche) }
        readingBody { renderer.markdown(this, parts.text) }
        glossaryTerms(renderer)
        nextSection(renderer, parts.nextActions, listOf(
            SiteStep("Trouver la prochaine mobilisation", "/agenda"),
            SiteStep("Agir : se mobiliser, s’organiser, s’entraider", spacePath(Space.AGIR)),
        ))
        sourcesSection(renderer, parts.sources)
        comprendreOrigin(fiche)
        offlineNotice()
        offlineScript()
    }
}

fun HTML.parcoursDocument(course: Parcours, catalog: ComprendreCatalog) {
    val renderer = ComprendreRenderer(catalog.glossary)
    val article = course.article
    siteLayout(pageTitle = article.title, currentSpace = Space.COMPRENDRE) {
        comprendreBreadcrumb("Comprendre en 5 minutes" to ComprendreLinks.listPath(ComprendreLinks.PARCOURS), article.title)
        comprendreHead("Comprendre en 5 minutes · ${courseMeta(course)}", article.title, article.summary) { contentBadges(article) }
        readingBody {
            if (course.introduction.isNotBlank()) renderer.markdown(this, course.introduction)
            nav(classes = "steps-toc") {
                attributes["aria-labelledby"] = "etapes"
                h2 {
                    id = "etapes"
                    +"Les étapes"
                }
                ol { course.steps.forEach { step -> li { a(href = "#${stepId(step)}") { +step.title } } } }
            }
            p(classes = "muted") { +"Le site n’enregistre ni votre progression ni vos réponses : rien n’est envoyé. Dans l’application, votre progression reste sur votre appareil." }
        }
        course.steps.forEach { step -> stepSection(renderer, step, course.steps.size) }
        if (course.objections.isNotEmpty()) section(classes = "objections-card") { renderer.objections(this, course.objections) }
        glossaryTerms(renderer)
        nextSection(renderer, course.nextActions, listOf(SiteStep("Proposer ou soutenir une revendication", spacePath(Space.REVENDICATIONS))))
        sourcesSection(renderer, course.sources)
        comprendreOrigin(article)
        offlineScript()
    }
}

private fun stepId(step: ParcoursStep): String = "etape-${step.index + 1}"

private fun FlowContent.stepSection(renderer: ComprendreRenderer, step: ParcoursStep, total: Int) {
    val id = stepId(step)
    section(classes = "course-step") {
        this.id = id
        attributes["aria-labelledby"] = "$id-titre"
        p(classes = "kicker") { +"Étape ${step.index + 1} sur $total" }
        h2 {
            this.id = "$id-titre"
            +step.title
        }
        renderer.markdown(this, step.body, headingOffset = 1)
        step.quiz?.let { quiz(renderer, it, id) }
    }
}

/**
 * A quiz readable without script: the question, the options in a list (A, B, C...), and the right
 * answer with its explanation behind a `<details>`, opened by the reader. Nothing is ever sent.
 */
private fun FlowContent.quiz(renderer: ComprendreRenderer, quiz: QuizQuestion, stepId: String) {
    val questionId = "$stepId-question"
    div(classes = "quiz") {
        attributes["role"] = "group"
        attributes["aria-labelledby"] = questionId
        p(classes = "quiz-label") { +"Question" }
        p(classes = "quiz-question") {
            id = questionId
            renderer.inline(this, quiz.question, glossary = false)
        }
        ul(classes = "quiz-options") {
            quiz.options.forEachIndexed { index, option ->
                li {
                    span(classes = "quiz-letter") { +"${letter(index)}." }
                    +" "
                    renderer.inline(this, option, glossary = false)
                }
            }
        }
        details(classes = "quiz-answer") {
            summary { +"Voir la bonne réponse" }
            p {
                strong { +"La bonne réponse : ${letter(quiz.correctIndex)}. " }
                renderer.inline(this, quiz.options[quiz.correctIndex], glossary = false)
            }
            p { renderer.inline(this, quiz.explanation) }
        }
    }
}

private fun letter(index: Int): Char = 'A' + index

fun HTML.decryptageDocument(check: Decryptage, catalog: ComprendreCatalog) {
    val renderer = ComprendreRenderer(catalog.glossary)
    val article = check.article
    val parts = splitBody(article.body)
    siteLayout(pageTitle = article.title, currentSpace = Space.COMPRENDRE) {
        comprendreBreadcrumb("Le vrai du faux" to ComprendreLinks.listPath(ComprendreLinks.DECRYPTAGES), article.title)
        comprendreHead("Le vrai du faux · Décryptage", article.title, article.summary) {
            contentBadges(article) { badge("Verdict : ${check.verdict.label}", check.verdict.tone) }
        }
        comprendreSection("affirmation", "L’affirmation", "claim") {
            blockQuote(classes = "claim-quote") { p { +"« ${check.claim} »" } }
            check.claimContext?.let { p(classes = "muted") { +"Où elle circule : $it" } }
            p {
                strong { +"Verdict : ${check.verdict.label}. " }
                +"Le verdict porte sur l’affirmation, jamais sur une personne."
            }
            if (catalog.method != null) p { a(href = ComprendreLinks.METHOD) { +"Comment nous choisissons un verdict" } }
        }
        readingBody { renderer.markdown(this, parts.text) }
        glossaryTerms(renderer)
        nextSection(renderer, parts.nextActions, listOf(
            SiteStep("Tous les décryptages", ComprendreLinks.listPath(ComprendreLinks.DECRYPTAGES)),
            SiteStep("Proposer ou soutenir une revendication", spacePath(Space.REVENDICATIONS)),
        ))
        sourcesSection(renderer, parts.sources)
        comprendreOrigin(article)
        offlineScript()
    }
}

fun HTML.storyDocument(story: HistoryStory, catalog: ComprendreCatalog) {
    val renderer = ComprendreRenderer(catalog.glossary)
    val article = story.article
    val parts = splitBody(article.body)
    siteLayout(pageTitle = article.title, currentSpace = Space.COMPRENDRE) {
        comprendreBreadcrumb("Histoire des luttes" to ComprendreLinks.listPath(ComprendreLinks.HISTOIRE), article.title)
        comprendreHead("Histoire des luttes · ${story.country} · ${story.period}", article.title, article.summary) { contentBadges(article) }
        if (story.claims.isNotEmpty() || story.results.isNotEmpty()) {
            section(classes = "story-facts") {
                attributes["aria-label"] = "En résumé"
                if (story.claims.isNotEmpty()) {
                    h2 { +"Ce qui était demandé" }
                    ul { story.claims.forEach { li { renderer.inline(this, it) } } }
                }
                if (story.results.isNotEmpty()) {
                    h2 { +"Résultats obtenus" }
                    ul { story.results.forEach { li { renderer.inline(this, it) } } }
                }
            }
        }
        readingBody { renderer.markdown(this, parts.text) }
        glossaryTerms(renderer)
        nextSection(renderer, parts.nextActions, listOf(
            SiteStep("Trouver la prochaine mobilisation", "/agenda"),
            SiteStep("Agir : se mobiliser, s’organiser, s’entraider", spacePath(Space.AGIR)),
        ))
        sourcesSection(renderer, parts.sources)
        comprendreOrigin(article)
        offlineScript()
    }
}

/**
 * A « Qui décide de ça ? » sheet: the chain of decision first (the box of the application), the public
 * persons the team linked, then the official directories. No personal elected officials on the site: the
 * reader's town stays on the device in the application (option C of the entry « Annuaire officiel des élus »).
 */
fun HTML.sheetDocument(sheet: QuiDecide, catalog: ComprendreCatalog) {
    val renderer = ComprendreRenderer(catalog.glossary)
    val article = sheet.article
    val parts = splitBody(sheet.body)
    val persons = sheet.persons.mapNotNull { catalog.persons[it] }
    siteLayout(pageTitle = article.title, currentSpace = Space.COMPRENDRE) {
        comprendreBreadcrumb("Qui décide de ça ?" to ComprendreLinks.listPath(ComprendreLinks.QUI_DECIDE), article.title)
        comprendreHead("Qui décide de ça ?", article.title, article.summary) { contentBadges(article) }
        comprendreSection("qui-decide", "Qui décide ? Les élus concernés", "decision-box") {
            ol(classes = "decision-chain") {
                sheet.steps.forEach { step ->
                    li {
                        h3 { +step.level.label }
                        if (step.roles.isNotEmpty()) p(classes = "muted") { +"Élus concernés : ${step.roles.joinToString(", ") { it.comprendreLabel }}" }
                        p { renderer.inline(this, step.description) }
                        val named = persons.filter { person -> person.roles.any { it in step.roles } }
                        if (named.isNotEmpty()) {
                            p {
                                +"Responsables publics suivis par « Voir » : "
                                named.forEachIndexed { index, person ->
                                    if (index > 0) +", "
                                    a(href = VoirLinks.pathOf(VoirItem.OfPerson(person))) { +person.name }
                                }
                            }
                        }
                    }
                }
            }
            h3 { +"Trouver vos élus" }
            p(classes = "muted") { +"Le site ne connaît pas votre commune et ne vous la demande pas. Dans l’application, vous pouvez la choisir : elle reste sur votre appareil. Ici, les annuaires officiels donnent les noms de vos élus." }
            ul(classes = "link-list") {
                officialDirectories.forEach { directory ->
                    li {
                        externalLink(this, directory.url, directory.title)
                        p(classes = "muted") { +directory.text }
                    }
                }
            }
        }
        readingBody { renderer.markdown(this, parts.text) }
        glossaryTerms(renderer)
        nextSection(renderer, parts.nextActions, listOf(
            SiteStep("Proposer ou soutenir une revendication", spacePath(Space.REVENDICATIONS)),
            SiteStep("Décider : obliger les élus à s’engager", spacePath(Space.DECIDER)),
        ))
        sourcesSection(renderer, parts.sources)
        comprendreOrigin(article)
        offlineScript()
    }
}

fun HTML.methodDocument(method: Article, catalog: ComprendreCatalog) {
    val renderer = ComprendreRenderer(catalog.glossary)
    val parts = splitBody(method.body)
    siteLayout(pageTitle = method.title, currentSpace = Space.COMPRENDRE) {
        comprendreBreadcrumb(null, method.title)
        comprendreHead("Notre méthode", method.title, method.summary) { contentBadges(method) }
        readingBody { renderer.markdown(this, parts.text) }
        glossaryTerms(renderer)
        nextSection(renderer, parts.nextActions, listOf(
            SiteStep("Le vrai du faux : tous les décryptages", ComprendreLinks.listPath(ComprendreLinks.DECRYPTAGES)),
            SiteStep("Lire les étiquettes de « Voir »", VoirLinks.METHOD),
        ))
        sourcesSection(renderer, parts.sources)
        comprendreOrigin(method)
        offlineScript()
    }
}

// ---------------------------------------------------------------------------------------------
// Glossary

fun HTML.glossaryDocument(catalog: ComprendreCatalog, query: String = "") {
    val index = catalog.glossary
    siteLayout(pageTitle = "Glossaire", currentSpace = Space.COMPRENDRE) {
        comprendreBreadcrumb(null, "Glossaire")
        comprendreHead(null, "Glossaire", "${index.size} mots de la vie politique, expliqués simplement.")
        form(action = ComprendreLinks.listPath(ComprendreLinks.GLOSSAIRE), method = FormMethod.get, classes = "search-form") {
            attributes["role"] = "search"
            label {
                htmlFor = "q"
                +"Chercher un mot"
            }
            input(type = InputType.search, name = "q") {
                id = "q"
                value = query
                attributes["maxlength"] = MAX_QUERY.toString()
            }
            button(type = kotlinx.html.ButtonType.submit) { +"Chercher" }
        }
        if (query.isNotEmpty()) {
            val found = index.search(query)
            section {
                attributes["aria-labelledby"] = "resultats"
                h2 {
                    id = "resultats"
                    +(if (found.size == 1) "1 résultat pour « $query »" else "${found.size} résultats pour « $query »")
                }
                if (found.isEmpty()) p(classes = "muted") { +"Aucun mot ne correspond. Essayez un autre mot, ou parcourez le glossaire par lettre." }
                glossaryList(found)
                p { a(href = ComprendreLinks.listPath(ComprendreLinks.GLOSSAIRE)) { +"Tout le glossaire" } }
            }
            return@siteLayout
        }
        val letters = index.entries.groupBy { GlossaryIndex.letterOf(it) }
        nav(classes = "letters") {
            attributes["aria-label"] = "Lettres du glossaire"
            ul { letters.keys.forEach { letter -> li { a(href = "#${letterId(letter)}") { +letter } } } }
        }
        if (index.isEmpty) p(classes = "muted") { +"Rien de publié pour l’instant." }
        letters.forEach { (letter, entries) ->
            section {
                attributes["aria-labelledby"] = letterId(letter)
                h2 {
                    id = letterId(letter)
                    +letter
                }
                glossaryList(entries)
            }
        }
    }
}

private fun letterId(letter: String): String = "lettre-" + (if (letter == "0-9") "chiffres" else letter.lowercase())

private fun FlowContent.glossaryList(entries: List<GlossaryEntry>) {
    if (entries.isEmpty()) return
    dl(classes = "gloss-list") {
        entries.forEach { entry ->
            dt { a(href = ComprendreLinks.glossaryPath(entry.slug)) { +entry.term } }
            dd { +entry.definition }
        }
    }
}

fun HTML.glossaryEntryDocument(entry: GlossaryEntry, catalog: ComprendreCatalog) {
    val renderer = ComprendreRenderer(catalog.glossary, excludedSlugs = setOf(entry.slug))
    val article = entry.article
    siteLayout(pageTitle = "${entry.term} : définition", currentSpace = Space.COMPRENDRE) {
        comprendreBreadcrumb("Glossaire" to ComprendreLinks.listPath(ComprendreLinks.GLOSSAIRE), entry.term)
        comprendreHead("Glossaire", entry.term, entry.definition) { contentBadges(article) }
        if (entry.explanation.isNotBlank()) {
            readingBody {
                h2 { +"En savoir plus" }
                renderer.markdown(this, entry.explanation, headingOffset = 1)
            }
        }
        val seeAlso = entry.seeAlso.mapNotNull { catalog.glossary.entry(it) }
        if (seeAlso.isNotEmpty()) {
            comprendreSection("voir-aussi", "Voir aussi") {
                ul(classes = "link-list") { seeAlso.forEach { other -> li { a(href = ComprendreLinks.glossaryPath(other.slug)) { +other.term } } } }
            }
        }
        val related = entry.related.mapNotNull { catalog.linked(it) }.mapNotNull { linked -> ComprendreLinks.pathOf(linked)?.let { linked to it } }
        if (related.isNotEmpty()) {
            comprendreSection("pour-aller-plus-loin", "Pour aller plus loin") {
                ul(classes = "link-list") { related.forEach { (linked, path) -> li { a(href = path) { +linked.title } } } }
            }
        }
        glossaryTerms(renderer)
        sourcesSection(renderer, entry.sources.ifBlank { null })
        comprendreOrigin(article)
        offlineScript()
    }
}

// ---------------------------------------------------------------------------------------------
// Offline reading

private fun FlowContent.offlineNotice() {
    p(classes = "notice") {
        +"Sans connexion : les fiches pratiques restent lisibles sur cet appareil une fois Comprendre ouvert, si votre navigateur le permet. "
        a(href = ComprendreLinks.OFFLINE) { +"Lire hors ligne, ou enregistrer toutes les fiches" }
    }
}

/**
 * Every practical guide in full on one page, to read offline: the page the service worker keeps and
 * shows when the network fails, and the page to save or print when the browser keeps nothing.
 */
fun HTML.offlineDocument(catalog: ComprendreCatalog) {
    val renderer = ComprendreRenderer(catalog.glossary)
    siteLayout(pageTitle = "Lire hors ligne", currentSpace = Space.COMPRENDRE) {
        comprendreBreadcrumb(null, "Lire hors ligne")
        comprendreHead(null, "Lire hors ligne", "Toutes les fiches pratiques en entier, sur une seule page.")
        comprendreSection("comment", "Comment garder les fiches sur soi") {
            ul {
                li { +"Sur un navigateur récent, les fiches pratiques sont enregistrées sur votre appareil dès que vous ouvrez Comprendre. Elles restent lisibles sans connexion, même en manifestation." }
                li { +"Vous pouvez aussi enregistrer cette page : menu du navigateur, puis « Enregistrer » ou « Imprimer » en PDF. Elle contient toutes les fiches." }
                li { +"Pour installer le site comme une application : sur Android, menu du navigateur, puis « Installer l’application » ou « Ajouter à l’écran d’accueil » ; sur iPhone, bouton « Partager » de Safari, puis « Sur l’écran d’accueil »." }
                li { +"Rien n’est envoyé à Politisation Populaire Française : ni ce que vous lisez, ni vos réponses aux questions." }
            }
        }
        if (catalog.fiches.isEmpty()) {
            p(classes = "muted") { +"Aucune fiche publiée pour l’instant." }
        } else {
            nav(classes = "steps-toc") {
                attributes["aria-label"] = "Les fiches de cette page"
                ul { catalog.fiches.forEach { fiche -> li { a(href = "#${ficheAnchor(fiche)}") { +fiche.title } } } }
            }
        }
        catalog.fiches.forEach { fiche ->
            val parts = splitBody(fiche.body)
            article(classes = "offline-fiche") {
                attributes["aria-labelledby"] = ficheAnchor(fiche)
                h2 {
                    id = ficheAnchor(fiche)
                    +fiche.title
                }
                fiche.summary?.let { p(classes = "lead") { +it } }
                renderer.markdown(this, parts.text, headingOffset = 1)
                parts.sources?.let {
                    h3 { +"Sources" }
                    renderer.markdown(this, it, headingOffset = 2, withGlossary = false)
                }
                p(classes = "muted") { a(href = ComprendreLinks.pathOf(fiche)!!) { +"Page de cette fiche" } }
            }
        }
        glossaryTerms(renderer)
        offlineScript()
    }
}

private fun ficheAnchor(fiche: Article): String = "fiche-" + fiche.slug.map { if (it.isLetterOrDigit() && it.code < 0x80) it else '-' }.joinToString("")

fun HTML.comprendreNotFoundDocument() {
    siteLayout(pageTitle = "Contenu introuvable", currentSpace = Space.COMPRENDRE) {
        comprendreHead(null, "Contenu introuvable", null)
        p { +"Ce contenu n’existe pas, ou il n’a pas été publié par l’équipe de Politisation Populaire Française. Vérifiez l’adresse, ou retrouvez tous les contenus de « Comprendre »." }
        p { a(href = ComprendreLinks.HOME) { +"Revenir à « Comprendre »" } }
    }
}
