package org.politisationpopulairefrancaise.web.comprendre

import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.article
import kotlinx.html.code
import kotlinx.html.dd
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.header
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.nav
import kotlinx.html.ol
import kotlinx.html.p
import kotlinx.html.pre
import kotlinx.html.script
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.summary
import kotlinx.html.ul
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.comprendre.ComprendreLinks
import org.politisationpopulairefrancaise.core.comprendre.ContentBlocks
import org.politisationpopulairefrancaise.core.comprendre.DecisionLevel
import org.politisationpopulairefrancaise.core.comprendre.GlossaryEntry
import org.politisationpopulairefrancaise.core.comprendre.TeachingLevel
import org.politisationpopulairefrancaise.core.comprendre.Verdict
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.Citation
import org.politisationpopulairefrancaise.core.editorial.ContentCheck
import org.politisationpopulairefrancaise.core.editorial.ContentLicenses
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.PublicRole
import org.politisationpopulairefrancaise.web.voir.BadgeTone
import org.politisationpopulairefrancaise.web.voir.externalLink
import org.politisationpopulairefrancaise.web.voir.originLine

// ---------------------------------------------------------------------------------------------
// Labels: the same words as strings_comprendre.xml of the application.

internal val Verdict.label: String get() = when (this) {
    Verdict.EXACT -> "Exact"
    Verdict.MOSTLY_EXACT -> "Plutôt exact"
    Verdict.MISLEADING -> "Trompeur"
    Verdict.CONTRADICTED -> "Contredit par les faits"
    Verdict.UNVERIFIABLE -> "Invérifiable"
}

/** The tone of a verdict: never red for a person, only for a claim contradicted by the facts. */
internal val Verdict.tone: BadgeTone get() = when (this) {
    Verdict.EXACT -> BadgeTone.SUCCESS
    Verdict.MOSTLY_EXACT -> BadgeTone.BRAND
    Verdict.MISLEADING -> BadgeTone.WARNING
    Verdict.CONTRADICTED -> BadgeTone.ACCENT
    Verdict.UNVERIFIABLE -> BadgeTone.NEUTRAL
}

internal val DecisionLevel.label: String get() = when (this) {
    DecisionLevel.COMMUNE -> "Commune"
    DecisionLevel.INTERCOMMUNALITE -> "Intercommunalité"
    DecisionLevel.DEPARTEMENT -> "Département"
    DecisionLevel.REGION -> "Région"
    DecisionLevel.ETAT -> "État"
    DecisionLevel.UNION_EUROPEENNE -> "Union européenne"
}

internal val PublicRole.comprendreLabel: String get() = when (this) {
    PublicRole.MAYOR -> "Maire"
    PublicRole.LOCAL_ELECTED -> "Élus locaux"
    PublicRole.DEPUTY -> "Députés"
    PublicRole.SENATOR -> "Sénateurs"
    PublicRole.MEP -> "Députés européens"
    PublicRole.MINISTER -> "Ministres"
    PublicRole.PRESIDENT -> "Président de la République"
    PublicRole.OTHER -> "Autres"
}

/** The level of a teaching sheet, as a heading: « Lycée ». */
internal val TeachingLevel.label: String get() = when (this) {
    TeachingLevel.LYCEE -> "Lycée"
    TeachingLevel.CYCLE_4 -> "Cycle 4 (collège)"
    TeachingLevel.ATELIER -> "Atelier d’éducation populaire"
}

/** The level of a teaching sheet, inside a sentence: « fiche pédagogique (lycée) ». */
internal val TeachingLevel.inSentence: String get() = when (this) {
    TeachingLevel.LYCEE -> "lycée"
    TeachingLevel.CYCLE_4 -> "cycle 4"
    TeachingLevel.ATELIER -> "atelier"
}

/**
 * The notice of every teaching sheet (D-096; docs/integration/comprendre-enseignants.md, section 1): a fixed
 * text of the site and of the application, never left to the writer of the sheet.
 */
internal const val TEACHING_NEUTRALITY: String =
    "L’enseignant reste libre de ses choix pédagogiques. Cette fiche respecte la neutralité de l’enseignement public : elle présente des faits et des débats, jamais une consigne de vote ni une position partisane."

/**
 * The notice of every animation sheet of a workshop of popular education (CO-24; docs/integration/comprendre-enseignants.md,
 * section 6): a fixed text, like [TEACHING_NEUTRALITY], for the associations, unions, social centres and libraries.
 */
internal const val WORKSHOP_NEUTRALITY: String =
    "L’animatrice ou l’animateur reste libre de ses choix. Cette fiche présente des faits et des débats, jamais une consigne de vote ni une position partisane."

/** One official directory to find one's elected officials (option C, as `OfficialDirectories` of the application). */
internal class OfficialDirectory(val url: String, val title: String, val text: String)

/**
 * The official directories, same list and addresses as `ui/comprendre/elus/OfficialDirectories.kt`
 * (checked on their official sites on 7 November 2026). The site never knows the reader's town: it
 * stays on the device in the application, so the site sends to these directories.
 */
internal val officialDirectories: List<OfficialDirectory> = listOf(
    OfficialDirectory("https://lannuaire.service-public.gouv.fr/navigation/mairie", "Annuaire de l’administration", "Votre mairie et ses coordonnées, par commune ou par code postal (Service Public)."),
    OfficialDirectory("https://www.assemblee-nationale.fr/dyn/vos-deputes", "Assemblée nationale", "Les 577 membres de l’Assemblée nationale, par département, par région ou par nom."),
    OfficialDirectory("https://www.senat.fr/senateurs/sencir.html", "Sénat", "Les membres du Sénat, par département."),
    OfficialDirectory("https://www.europarl.europa.eu/meps/fr/search/advanced?countryCode=FR", "Parlement européen", "Les membres du Parlement européen élus en France."),
    OfficialDirectory("https://www.data.gouv.fr/fr/datasets/5c34c4d1634f4173183a64f1/", "Répertoire national des élus", "Tous les élus de France, en fichiers ouverts (ministère de l’Intérieur, data.gouv.fr)."),
)

// ---------------------------------------------------------------------------------------------
// The parts of a body: the text, « Et maintenant ? » and the sources, shown in this order.

internal class BodyParts(val text: String, val nextActions: String?, val sources: String?)

/** Takes the `::: et-maintenant` blocks and the `## Sources` section out of [body]: the page shows them last. */
internal fun splitBody(body: String): BodyParts {
    val actions = mutableListOf<List<String>>()
    val lines = ContentBlocks.extract(body.replace("\r\n", "\n").lines(), NEXT_ACTIONS_BLOCK, actions)
    val at = lines.indexOfFirst { it.trim().equals("## ${ComprendreRenderer.SOURCES}", ignoreCase = true) }
    val text = (if (at < 0) lines else lines.subList(0, at)).joinToString("\n").trim()
    val sources = if (at < 0) null else lines.subList(at + 1, lines.size).joinToString("\n").trim().ifEmpty { null }
    val next = actions.flatten().joinToString("\n").trim().ifEmpty { null }
    return BodyParts(text, next, sources)
}

internal const val NEXT_ACTIONS_BLOCK: String = "et-maintenant"

// ---------------------------------------------------------------------------------------------
// Page parts

/** The trail from the home of "Comprendre" to the page; [section] is the list the page belongs to. */
internal fun FlowContent.comprendreBreadcrumb(section: Pair<String, String>?, current: String?) {
    nav(classes = "breadcrumb") {
        attributes["aria-label"] = "Fil d’Ariane"
        ol {
            li { a(href = ComprendreLinks.HOME) { +"Comprendre" } }
            section?.let { (label, path) -> li { a(href = path) { +label } } }
            current?.let { li { span { attributes["aria-current"] = "page"; +it } } }
        }
    }
}

internal fun FlowContent.comprendreHead(kicker: String?, title: String, lead: String?, badges: (FlowContent.() -> Unit)? = null) {
    header(classes = "page-head") {
        kicker?.let { p(classes = "kicker") { +it } }
        h1 { +title }
        badges?.invoke(this)
        lead?.let { p(classes = "lead") { +it } }
    }
}

/** True for a content of example (its title carries the [EXEMPLE] marker). */
internal fun isExample(article: Article): Boolean = article.title.trimStart().startsWith(GlossaryEntry.EXAMPLE_MARKER)

/** The badges every content shows: example, origin of the text. */
internal fun FlowContent.contentBadges(article: Article, extra: (FlowContent.() -> Unit)? = null) {
    div(classes = "badges") {
        extra?.invoke(this)
        if (isExample(article)) span(classes = BadgeTone.WARNING.css) { +"Contenu d’exemple" }
        if (isUnreviewedAiPosition(article)) {
            span(classes = BadgeTone.WARNING.css) { +CAP_POSITION_LABEL }
        } else if (article.origin == org.politisationpopulairefrancaise.core.editorial.ContentOrigin.AI_ASSISTED) {
            span(classes = BadgeTone.NEUTRAL.css) { +"Rédigé avec l’aide d’une IA" }
        }
    }
}

/** A section of a page (a card of site.css), named by its heading for screen readers. */
internal fun FlowContent.comprendreSection(id: String, title: String, classes: String? = null, block: FlowContent.() -> Unit) {
    section(classes) {
        attributes["aria-labelledby"] = id
        h2 {
            this.id = id
            +title
        }
        block()
    }
}

/** A step of « Et maintenant ? » proposed by the site when the content gives none. */
internal class SiteStep(val label: String, val path: String)

internal fun spacePath(space: Space): String = "/${space.id}"

/**
 * « Et maintenant ? » (cahier fonctionnel, section 3): the actions of the content, its links to the
 * spaces of the site, or the steps proposed for its kind, then the home of "Comprendre".
 */
internal fun FlowContent.nextSection(renderer: ComprendreRenderer, actions: String?, defaults: List<SiteStep>) {
    comprendreSection("et-maintenant", "Et maintenant ?", "comprendre-next") {
        if (actions != null) {
            renderer.markdown(this, actions, withGlossary = false)
        } else {
            ul(classes = "next-list") { defaults.forEach { step -> li { a(href = step.path) { +step.label } } } }
        }
        p { a(href = ComprendreLinks.HOME) { +"Tout « Comprendre »" } }
    }
}

internal fun FlowContent.sourcesSection(renderer: ComprendreRenderer, sources: String?) {
    comprendreSection("sources", "Sources", "comprendre-sources") {
        if (sources == null) p(classes = "muted") { +"Aucune source publiée pour l’instant." } else renderer.markdown(this, sources, headingOffset = 1, withGlossary = false)
    }
}

/**
 * The terms of the glossary linked in the page, with their short definition: the target of the
 * `aria-describedby` of each link, readable without script and printed with the page.
 */
internal fun FlowContent.glossaryTerms(renderer: ComprendreRenderer) {
    val used = renderer.used
    if (used.isEmpty()) return
    comprendreSection("mots-du-glossaire", "Les mots du glossaire de cette page", "gloss-terms") {
        dl(classes = "gloss-list") {
            used.forEach { entry ->
                dt { a(href = ComprendreLinks.glossaryPath(entry.slug)) { +entry.term } }
                dd {
                    id = ComprendreRenderer.definitionId(entry.slug)
                    +entry.definition
                }
            }
        }
        p { a(href = ComprendreLinks.listPath(ComprendreLinks.GLOSSAIRE)) { +"Tout le glossaire" } }
    }
}

/**
 * The end of every published page of "Comprendre": the origin of the text and its dates, as on the pages of
 * "Voir"; its licence (D-103, `ContentLicenses.of(type)`, none for a text whose rights stay with its author);
 * « Citer ce contenu » (S-144) and the verification code of this version (S-141), printed with the page so
 * that a printed or copied sheet can be checked on `/v/<code>`. [path] is the page of the content (null:
 * no reference); [consultedOn] the day of the reader. Never on a preview: a draft has no published version.
 */
internal fun FlowContent.comprendreOrigin(article: Article, path: String? = ComprendreLinks.pathOf(article), consultedOn: CivilDate = parisToday()) {
    if (isUnreviewedAiPosition(article)) p(classes = "origin") { +"$CAP_POSITION_LABEL." }
    originLine(article)
    ContentLicenses.of(article.type)?.let { license ->
        p(classes = "licence") {
            +"Texte sous licence "
            externalLink(license.url, license.shortName)
            +", ${ContentLicenses.ATTRIBUTION}."
        }
    }
    if (path != null) comprendreCite(article, ComprendreLinks.urlOf(path), consultedOn)
}

/** « Citer ce contenu » (S-144), selectable without script, then the code to check this version (S-141). */
internal fun FlowContent.comprendreCite(article: Article, url: String, consultedOn: CivilDate) {
    val citation = Citation.of(article, url, consultedOn)
    details(classes = "cite") {
        summary { +"Citer ce contenu" }
        p { +citation.text }
        p(classes = "muted") { +"La même référence au format CSL-JSON, pour un logiciel de bibliographie (Zotero, par exemple) :" }
        pre { code { +citation.cslJson } }
    }
    val verification = ContentCheck.codeOf(article.event)
    p(classes = "verify-line") {
        +"Code de vérification : "
        code { +verification }
        +". "
        a(href = "/v/$verification") { +"Vérifier que ce contenu vient de PPF et n’a pas été modifié" }
    }
}

/** The day of the reader, in France, for « consulté le » of a reference. */
internal fun parisToday(): CivilDate =
    java.time.LocalDate.now(java.time.ZoneId.of("Europe/Paris")).let { CivilDate(it.year, it.monthValue, it.dayOfMonth) }

/** The reading body of a content, a card of the page. */
internal fun FlowContent.readingBody(block: FlowContent.() -> Unit) {
    article(classes = "reading-body") { block() }
}

/**
 * Registers the service worker that keeps the practical guides for offline reading. Only a plus:
 * every page is complete without it (no script needed to read), and the script is a file of the
 * site, allowed by the CSP (`script-src 'self'`).
 */
internal fun FlowContent.offlineScript() {
    script(src = PWA_SCRIPT) { attributes["defer"] = "defer" }
}

internal const val PWA_SCRIPT: String = "/static/pwa.js"

/** A text of the series « Le cap » that is a position of PPF written with the help of an AI, not yet reviewed (D-212). */
internal fun isUnreviewedAiPosition(article: Article): Boolean =
    org.politisationpopulairefrancaise.core.comprendre.CapText.from(article)?.isUnreviewedAiPosition == true
