package org.politisationpopulairefrancaise.web.voir

import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.article
import kotlinx.html.code
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.h4
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.pre
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.summary
import kotlinx.html.time
import kotlinx.html.ul
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.Citation
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.ArchivedCopy
import org.politisationpopulairefrancaise.core.voir.Correction
import org.politisationpopulairefrancaise.core.voir.Fact
import org.politisationpopulairefrancaise.core.voir.FactKind
import org.politisationpopulairefrancaise.core.voir.Indicator
import org.politisationpopulairefrancaise.core.voir.LegislativeText
import org.politisationpopulairefrancaise.core.voir.NextStep
import org.politisationpopulairefrancaise.core.voir.NextTarget
import org.politisationpopulairefrancaise.core.voir.PublicPerson
import org.politisationpopulairefrancaise.core.voir.RightOfReply
import org.politisationpopulairefrancaise.core.voir.Source
import org.politisationpopulairefrancaise.core.voir.TextStage
import org.politisationpopulairefrancaise.core.voir.VoirCatalog
import org.politisationpopulairefrancaise.core.voir.VoirDossier
import org.politisationpopulairefrancaise.core.voir.VoirItem
import org.politisationpopulairefrancaise.core.voir.VoirLinks
import org.politisationpopulairefrancaise.core.voir.WorldMovement
import org.politisationpopulairefrancaise.web.spaceLabels

/** The `rel` of every link that leaves the site: no referrer, no ranking given, opened without access to this page. */
internal const val EXTERNAL_REL: String = "noopener noreferrer nofollow external"

/** Only web addresses are followed (no javascript:, data:, file:), without spaces or control characters. */
internal fun isWebAddress(value: String): Boolean =
    (value.startsWith("https://") || value.startsWith("http://")) && value.length > 8 && value.none { it.isWhitespace() || it.isISOControl() }

/** The visible mark of a link that leaves the site (the words, not only an icon). */
internal fun FlowContent.externalMark() {
    span(classes = "external-mark") { +" (site extérieur)" }
}

internal fun FlowContent.externalLink(url: String, text: String) {
    a(href = url) {
        attributes["rel"] = EXTERNAL_REL
        +text
        externalMark()
    }
}

internal fun FlowContent.badge(text: String, tone: BadgeTone = BadgeTone.NEUTRAL) {
    span(classes = tone.css) { +text }
}

internal fun FlowContent.date(date: CivilDate, short: Boolean = false) {
    time {
        attributes["datetime"] = date.toIso()
        +(if (short) date.toFrenchShort() else date.toFrench())
    }
}

/** A heading of the given level (cards are h3 on detail pages, h4 inside the sections of the home of "Voir"). */
internal fun FlowContent.heading(level: Int, classes: String? = null, block: FlowContent.() -> Unit) {
    when (level) {
        2 -> h2(classes) { block() }
        3 -> h3(classes) { block() }
        else -> h4(classes) { block() }
    }
}

/** A section of a page, a card of site.css, named by its heading for screen readers. */
internal fun FlowContent.pageSection(id: String, title: String, classes: String? = null, block: FlowContent.() -> Unit) {
    section(classes) {
        attributes["aria-labelledby"] = id
        h2 {
            this.id = id
            +title
        }
        block()
    }
}

// ---------------------------------------------------------------------------------------------
// What ends every page: sources, archives, corrections, replies, origin, "Et maintenant ?"

internal fun FlowContent.sourcesSection(sources: List<Source>, empty: String = "Aucune source publiée pour l’instant.") {
    pageSection("sources", "Sources", "voir-sources") {
        if (sources.isEmpty()) {
            p(classes = "muted") { +empty }
            return@pageSection
        }
        ul(classes = "source-list") {
            sources.forEach { source ->
                li {
                    span(classes = "source-kind") { +source.kind.label }
                    +" : "
                    val url = source.url?.takeIf(::isWebAddress)
                    if (url != null) externalLink(url, source.name) else +source.name
                    source.date?.let {
                        +", "
                        date(it)
                    }
                }
            }
        }
    }
}

internal fun FlowContent.archivesSection(archives: List<ArchivedCopy>) {
    if (archives.isEmpty()) return
    pageSection("archives", "Copies archivées") {
        p(classes = "muted") { +"L’empreinte SHA-256 permet de vérifier que la copie est identique au document d’origine." }
        ul {
            archives.forEach { copy ->
                li {
                    if (copy.archivedAt != null) {
                        +"Archivée le "
                        date(copy.archivedAt!!)
                        +", empreinte "
                    } else {
                        +"Empreinte "
                    }
                    code(classes = "hash") { +copy.sha256 }
                    copy.proof?.let { +" ; preuve d’horodatage : $it" }
                    copy.archiveUrl?.takeIf(::isWebAddress)?.let {
                        +". "
                        externalLink(it, "Voir la copie archivée")
                    }
                }
            }
        }
    }
}

internal fun FlowContent.correctionsSection(corrections: List<Correction>) {
    if (corrections.isEmpty()) return
    pageSection("corrections", "Corrections") {
        ul {
            corrections.forEach { correction ->
                li {
                    +"Corrigé le "
                    date(correction.date)
                    +" : ${correction.reason}"
                }
            }
        }
        p { a(href = VoirLinks.CORRECTIONS) { +"Journal de toutes les corrections" } }
    }
}

/** The rights of reply published under a content (VO-21), and how to exercise one. */
internal fun FlowContent.repliesSection(replies: List<RightOfReply>, offerReply: Boolean) {
    if (replies.isEmpty() && !offerReply) return
    pageSection("droit-de-reponse", if (replies.size > 1) "Droits de réponse" else "Droit de réponse") {
        replies.forEach { reply ->
            article(classes = "reply") {
                h3 { +"Droit de réponse de ${reply.respondent}" }
                p(classes = "muted") {
                    +"Publié le "
                    date(reply.publishedOn)
                }
                markdown(reply.text.trim())
            }
        }
        if (offerReply) {
            details {
                summary { +"Exercer un droit de réponse" }
                p { +"Toute personne nommée ou désignée dans un contenu peut y répondre (loi pour la confiance dans l’économie numérique, article 6, IV). Sa réponse est publiée sous le contenu dans les trois jours suivant sa réception, avec la mention « Droit de réponse de [nom] »." }
                p { +"Le formulaire de demande arrivera avec le serveur de PPF. Une personne qui a un compte vérifié pourra écrire sa réponse directement ; toute autre personne passera par ce formulaire en justifiant de son identité." }
            }
        }
    }
}

internal fun FlowContent.originLine(article: Article) {
    p(classes = "origin") {
        +article.origin.label
        +". "
        article.publishedAt?.let {
            +"Publié le "
            date(CivilDate.fromEpochSeconds(it))
            +", "
        }
        +"mis à jour le "
        date(CivilDate.fromEpochSeconds(article.updatedAt))
        +"."
    }
}

/** A step of "Et maintenant ?" as a link of the site. */
internal data class SiteStep(val label: String, val path: String)

/** Where a step leads on the site: a space page, or the page of a content of "Voir"; null when the site has no page for it. */
internal fun siteStep(step: NextStep, catalog: VoirCatalog): SiteStep? = when (val target = step.target) {
    is NextTarget.ToSpace -> SiteStep(step.label, spacePath(target.space))
    is NextTarget.ToContent -> catalog.item(target.address)?.let { SiteStep(step.label, VoirLinks.pathOf(it)) }
}

/** The page of a space on the site; the agenda is the page of the mobilizations. */
internal fun spacePath(space: Space): String = "/${space.id}"

/** The steps offered when the content gives none (each page ends with "Et maintenant ?"). */
internal fun defaultSteps(item: VoirItem?): List<SiteStep> = buildList {
    when (item) {
        is VoirItem.OfIndicator, is VoirItem.OfMovement -> {
            add(SiteStep("Trouver la prochaine mobilisation", "/agenda"))
            add(SiteStep("Agir : se mobiliser, s’organiser, s’entraider", spacePath(Space.AGIR)))
        }
        is VoirItem.OfFact, is VoirItem.OfPerson, is VoirItem.OfParty, is VoirItem.OfText -> {
            add(SiteStep("Décider : obliger les élus à s’engager", spacePath(Space.DECIDER)))
            add(SiteStep("Proposer ou soutenir une revendication", spacePath(Space.REVENDICATIONS)))
        }
        is VoirItem.OfDossier, null -> {
            add(SiteStep("Comprendre qui décide de quoi", spacePath(Space.COMPRENDRE)))
            add(SiteStep("Agir : se mobiliser, s’organiser, s’entraider", spacePath(Space.AGIR)))
        }
    }
}

internal fun FlowContent.nextSection(steps: List<NextStep>, catalog: VoirCatalog, item: VoirItem?) {
    val own = steps.mapNotNull { siteStep(it, catalog) }
    val all = (own.ifEmpty { defaultSteps(item) } + SiteStep("Tous les contenus de « Voir »", VoirLinks.HOME)).distinctBy { it.path }
    pageSection("et-maintenant", "Et maintenant ?", "voir-next") {
        ul(classes = "next-list") {
            all.forEach { step -> li { a(href = step.path) { +step.label } } }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Cards

internal fun FlowContent.factBadges(fact: Fact) {
    div(classes = "badges") {
        badge(fact.status.label, fact.status.tone)
        fact.procedure?.let { badge(it.label, it.tone) }
        fact.promise?.let { badge("Promesse : ${it.label}", it.tone) }
        if (fact.kind == FactKind.VOTE || fact.kind == FactKind.STATEMENT_VS_VOTE) fact.vote?.let { badge("Vote : ${it.label}") }
        fact.liberty?.let { badge(it.label) }
        fact.recourse?.let { badge("Recours : ${it.label}", BadgeTone.BRAND) }
    }
}

internal fun FlowContent.factCard(fact: Fact, catalog: VoirCatalog, level: Int) {
    article(classes = "card") {
        p(classes = "kicker") {
            +"${fact.kind.label} · "
            date(fact.date, short = true)
            fact.place?.let { +" · $it" }
        }
        heading(level, "card-title") { a(href = VoirLinks.pathOf(VoirItem.OfFact(fact))) { +fact.title } }
        factBadges(fact)
        fact.article.summary?.let { p { +it } }
        val persons = catalog.personsOf(fact)
        if (persons.isNotEmpty()) {
            p(classes = "card-persons") {
                +"Concerne : "
                persons.forEachIndexed { index, person ->
                    if (index > 0) +", "
                    a(href = VoirLinks.pathOf(VoirItem.OfPerson(person))) { +person.name }
                }
            }
        }
    }
}

internal fun FlowContent.keyFigureCard(indicator: Indicator, level: Int) {
    article(classes = "card") {
        heading(level, "card-title") { a(href = VoirLinks.pathOf(VoirItem.OfIndicator(indicator))) { +indicator.title } }
        val latest = indicator.latest
        if (latest == null) {
            p(classes = "muted") { +"Aucune mesure publiée pour l’instant." }
            return@article
        }
        p(classes = "kicker") {
            +"${latest.occasion} · "
            date(latest.date, short = true)
        }
        ul(classes = "figures") {
            latest.measures.forEach { measure ->
                li {
                    span(classes = "figure-value") { +valueText(measure.value, indicator.unit) }
                    if (indicator.unit != "%") +" ${indicator.unit}"
                    +" "
                    span(classes = "figure-source") { +"selon ${measure.source.kind.label}" }
                }
            }
        }
        div(classes = "badges") {
            if (latest.diverges) badge("Sources divergentes", BadgeTone.WARNING)
            if (indicator.declarative) badge("Déclaratif", BadgeTone.WARNING)
        }
    }
}

internal fun FlowContent.personCard(person: PublicPerson, catalog: VoirCatalog, level: Int) {
    article(classes = "card") {
        heading(level, "card-title") { a(href = VoirLinks.pathOf(VoirItem.OfPerson(person))) { +person.name } }
        person.function?.let { p { +it } }
        person.group?.let { p(classes = "muted") { +it } }
        val facts = catalog.factsAbout(person.address)
        val counts = PERSON_SECTIONS.map { kind -> kind to facts.count { it.kind == kind } }.filter { it.second > 0 }
        if (counts.isNotEmpty()) {
            div(classes = "badges") {
                counts.forEach { (kind, count) -> badge("${kind.section} : $count", if (kind == FactKind.CASE) BadgeTone.WARNING else BadgeTone.NEUTRAL) }
            }
        }
    }
}

internal fun FlowContent.billCard(text: LegislativeText, level: Int) {
    article(classes = "card") {
        heading(level, "card-title") { a(href = VoirLinks.pathOf(VoirItem.OfText(text))) { +text.title } }
        p(classes = "kicker") { +stageWords(text) }
        text.nextVote?.let { vote ->
            p {
                +"Prochain vote le "
                date(vote.date, short = true)
                if (vote.label.isNotBlank()) +" : ${vote.label}"
            }
        }
        if (text.uses493.isNotEmpty()) div(classes = "badges") { badge("Article 49.3 : ${text.uses493.size} recours", BadgeTone.ACCENT) }
    }
}

internal fun FlowContent.movementCard(movement: WorldMovement, level: Int) {
    article(classes = "card") {
        p(classes = "kicker") { +"${movement.country} · ${periodText(movement)}" }
        heading(level, "card-title") { a(href = VoirLinks.pathOf(VoirItem.OfMovement(movement))) { +movement.title } }
        movement.results.firstOrNull()?.let { p { +"Obtenu : $it" } }
    }
}

internal fun FlowContent.dossierCard(dossier: VoirDossier, level: Int) {
    article(classes = "card") {
        p(classes = "kicker") { +"Dossier" }
        heading(level, "card-title") { a(href = VoirLinks.pathOf(VoirItem.OfDossier(dossier))) { +dossier.title } }
        dossier.article.summary?.let { p { +it } }
    }
}

/** The order of the sections of every sheet, whoever the person is (règle « Traitement identique »), as in the app. */
internal val PERSON_SECTIONS: List<FactKind> = listOf(
    FactKind.VOTE,
    FactKind.PROMISE,
    FactKind.STATEMENT_VS_VOTE,
    FactKind.CASE,
    FactKind.CONTRADICTED_STATEMENT,
    FactKind.INTERESTS,
    FactKind.LOBBYING,
    FactKind.PUBLIC_MONEY,
)

/** "Étape 4 sur 7 : Sénat", as the app says it. */
internal fun stageWords(text: LegislativeText): String {
    val path = TextStage.path
    val reached = path.indexOfLast { it.order <= text.stage.order }.coerceAtLeast(0)
    return "Étape ${reached + 1} sur ${path.size} : ${text.stage.label}"
}

internal fun periodText(movement: WorldMovement): String {
    val start = movement.start
    val end = movement.end
    return when {
        start != null && end != null -> "Du ${start.toFrenchShort()} au ${end.toFrenchShort()}"
        start != null -> "Depuis le ${start.toFrenchShort()}"
        else -> "En cours"
    }
}

/** The editorial content of [item], whatever its kind. */
internal fun articleOf(item: VoirItem): Article = when (item) {
    is VoirItem.OfIndicator -> item.indicator.article
    is VoirItem.OfFact -> item.fact.article
    is VoirItem.OfPerson -> item.person.article
    is VoirItem.OfParty -> item.party.article
    is VoirItem.OfText -> item.text.article
    is VoirItem.OfMovement -> item.movement.article
    is VoirItem.OfDossier -> item.dossier.article
}

/**
 * « Citer ce contenu » (S-144): the reference of the published page, ready to select and copy without script,
 * and the same in CSL-JSON for bibliography software. Never on a preview: a draft has no published version.
 */
internal fun FlowContent.citeSection(item: VoirItem, consultedOn: CivilDate) {
    val citation = Citation.of(articleOf(item), VoirLinks.urlOf(item), consultedOn)
    details(classes = "cite") {
        summary { +"Citer ce contenu" }
        p { +citation.text }
        p(classes = "muted") { +"La même référence au format CSL-JSON, pour un logiciel de bibliographie (Zotero, par exemple) :" }
        pre { code { +citation.cslJson } }
    }
}
