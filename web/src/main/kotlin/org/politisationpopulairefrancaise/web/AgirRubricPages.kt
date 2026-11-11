package org.politisationpopulairefrancaise.web

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
import kotlinx.html.li
import kotlinx.html.nav
import kotlinx.html.ol
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.strong
import kotlinx.html.ul
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.agir.collectives.Collective
import org.politisationpopulairefrancaise.core.agir.collectives.CollectiveScope
import org.politisationpopulairefrancaise.core.agir.collectives.CollectiveType
import org.politisationpopulairefrancaise.core.agir.engagement.ElectoralMilestone
import org.politisationpopulairefrancaise.core.agir.engagement.Engagement
import org.politisationpopulairefrancaise.core.agir.kits.CitizenKit
import org.politisationpopulairefrancaise.core.agir.kits.CitizenKits
import org.politisationpopulairefrancaise.core.agir.kits.KitGoal
import org.politisationpopulairefrancaise.core.agir.kits.KitTarget
import org.politisationpopulairefrancaise.core.agir.solidarity.SolidarityFormats
import org.politisationpopulairefrancaise.core.agir.solidarity.SolidarityFund
import org.politisationpopulairefrancaise.core.comprendre.ComprendreLinks
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.web.comprendre.ComprendreCatalog
import org.politisationpopulairefrancaise.web.comprendre.ComprendreSource

/** Where the page of the collectifs reads the listed collectifs (public or private, never secret), filtered by territory. */
fun interface CollectivesSource {
    suspend fun collectives(territory: String?): List<Collective>
}

/** Where the page of solidarity reads the funds (entries signed by an editorial key). */
fun interface FundsSource {
    suspend fun funds(): List<SolidarityFund>
}

/** The addresses of the rubric pages of « Agir » on the site. */
object AgirSitePaths {
    const val ENGAGER: String = "/agir/s-engager"
    const val SOLIDARITY: String = "/agir/solidarite"
    const val COLLECTIVES: String = "/agir/collectifs"
    const val KITS: String = "/agir/kits"
}

private const val MAX_TERRITORY: Int = 200
private const val EXTERNAL: String = "noopener noreferrer nofollow external"

private fun fichePath(slug: String) = "${ComprendreLinks.HOME}/${ComprendreLinks.FICHES}/$slug"
private fun coursePath(slug: String) = "${ComprendreLinks.HOME}/${ComprendreLinks.PARCOURS}/$slug"

/**
 * The rubric pages of « Agir » on the site, readable without JavaScript: « S'engager en politique » (D-196, the
 * electoral rolls first), solidarity and strike funds (AG-10), the directory of the collectifs (M24) and the citizen
 * action kits (D-232, filtered by links). What only exists in the application (testifying, the demonstration mode,
 * the messaging without network) is named with one line that sends to the application. [comprendre] gives the titles
 * of the practical guides; without it, the links keep their slug.
 */
fun Route.agirRubricPages(comprendre: ComprendreSource?, collectives: CollectivesSource?, funds: FundsSource?) {
    get(AgirSitePaths.ENGAGER) { call.respondHtml { engagerDocument(parisToday(), comprendre?.catalog()) } }
    get(AgirSitePaths.KITS) {
        val goal = call.request.queryParameters["but"]?.let { id -> KitGoal.entries.firstOrNull { it.id == id } }
        val offline = call.request.queryParameters["sans-reseau"] == "1"
        call.respondHtml { kitsDocument(goal, offline, comprendre?.catalog()) }
    }
    get(AgirSitePaths.SOLIDARITY) { call.respondHtml { solidarityDocument(funds?.funds(), comprendre?.catalog()) } }
    get(AgirSitePaths.COLLECTIVES) {
        val territory = call.request.queryParameters["territoire"]?.trim()?.take(MAX_TERRITORY)?.takeIf { it.isNotEmpty() }
        call.respondHtml { collectivesDocument(territory, collectives?.collectives(territory)) }
    }
}

// ---------------------------------------------------------------------------------------------
// S'engager en politique

private val milestoneLabels: Map<String, String> = mapOf(
    Engagement.REGISTRATION_DEADLINE.id to "dernier jour pour s’inscrire sur les listes électorales",
    Engagement.PRESIDENTIAL_FIRST_ROUND.id to "premier tour de la présidentielle",
    Engagement.PRESIDENTIAL_SECOND_ROUND.id to "second tour de la présidentielle",
)

private fun milestoneDate(milestone: ElectoralMilestone): String =
    milestone.date.toFrench() + if (milestone.confirmed) "" else " (date annoncée, à confirmer par le décret de convocation)"

/** « S'engager en politique » (D-196): the four steps in the order of the electoral calendar, registering first. */
fun HTML.engagerDocument(today: CivilDate, catalog: ComprendreCatalog?) {
    siteLayout(pageTitle = "S’engager en politique", currentSpace = Space.AGIR) {
        h1 { +"S’engager en politique" }
        p(classes = "lead") { +"Voter, se présenter, monter une liste, rejoindre ou créer un parti : les étapes dans l’ordre du calendrier électoral, avec les démarches officielles." }
        Engagement.nextMilestone(today)?.let { next ->
            val days = Engagement.daysUntil(next, today)
            p {
                strong { +"Prochaine échéance : " }
                +"${milestoneLabels[next.id].orEmpty()}, ${next.date.toFrench()}. "
                +if (days == 0L) "C’est aujourd’hui." else "Dans $days jours."
            }
        }
        ol {
            li {
                section {
                    id = "s-inscrire"
                    h2 { +"Étape 1 : s’inscrire sur les listes électorales" }
                    p { +"Pour voter à la présidentielle, inscrivez-vous au plus tard le ${milestoneDate(Engagement.REGISTRATION_DEADLINE)}, en ligne ou en mairie. Après un déménagement, il faut se réinscrire dans sa nouvelle commune." }
                    ul {
                        li { external(Engagement.CHECK_REGISTRATION, "Vérifier mon inscription (service-public.gouv.fr)") }
                        li { external(Engagement.REGISTER_PAGE, "M’inscrire : la démarche officielle (service-public.gouv.fr)") }
                        li { guide(catalog, Engagement.GUIDE_PROXY, "Voter par procuration") }
                        li { course(catalog, Engagement.COURSE_REGISTER, "S’inscrire et voter") }
                    }
                }
            }
            li {
                section {
                    id = "presidentielle"
                    h2 { +"Étape 2 : voter à la présidentielle" }
                    p { +"Premier tour le ${milestoneDate(Engagement.PRESIDENTIAL_FIRST_ROUND)}, second tour le ${milestoneDate(Engagement.PRESIDENTIAL_SECOND_ROUND)}. Comparez les candidats et leurs engagements, sur les mêmes questions pour tous." }
                    ul {
                        li { a(href = "/decider") { +"Comparer dans Décider" } }
                        li { external(Engagement.CALENDAR_SOURCE, "Le calendrier officiel des élections (service-public.gouv.fr)") }
                    }
                }
            }
            li {
                section {
                    id = "se-presenter"
                    h2 { +"Étape 3 : se présenter, monter une liste" }
                    p { +"Prochaines élections où l’on peut se présenter : départementales et régionales en mars 2028, européennes en 2029, municipales en mars 2032. Des élections partielles ont lieu toute l’année. Des législatives n’auront lieu avant 2029 qu’en cas de dissolution de l’Assemblée nationale." }
                    ul {
                        li { course(catalog, Engagement.COURSE_STAND, "Se présenter à une élection") }
                        li { guide(catalog, Engagement.GUIDE_CITIZEN_LIST, "Constituer une liste citoyenne") }
                        li { guide(catalog, Engagement.GUIDE_2028, "Se présenter aux départementales et régionales de 2028") }
                        li { guide(catalog, Engagement.GUIDE_LEGISLATIVES, "Se présenter aux législatives") }
                        li { a(href = AgirSitePaths.COLLECTIVES) { +"Trouver des colistiers dans les collectifs" } }
                    }
                }
            }
            li {
                section {
                    id = "parti"
                    h2 { +"Étape 4 : créer ou rejoindre un parti" }
                    p { +"Un parti est d’abord une association, avec un mandataire financier ou une association de financement. Les fiches des partis sont présentées de la même façon pour tous." }
                    ul {
                        li { guide(catalog, Engagement.GUIDE_PARTY, "Créer ou rejoindre un parti") }
                        li { a(href = "/voir") { +"Voir les fiches des partis" } }
                    }
                }
            }
        }
        section {
            id = "neutralite"
            h2 { +"Les mêmes outils pour tous" }
            p { +"PPF ne soutient aucune liste, aucun candidat et aucun parti : une association ne peut aider la campagne d’un candidat (code électoral, article L52-8). Ces outils sont ouverts à toutes et à tous, aux mêmes conditions." }
            p { external(Engagement.L52_8, "Lire le code électoral sur Légifrance (article L52-8)") }
        }
        p(classes = "muted") { +"Dates relevées le ${Engagement.CHECKED_ON.toFrench()} sur les sites officiels. Rédigé par l’IA." }
    }
}

// ---------------------------------------------------------------------------------------------
// Kits d'action citoyenne

private val goalLabels: Map<KitGoal, String> = mapOf(
    KitGoal.MANIFESTER to "Manifester",
    KitGoal.GREVE to "Faire grève",
    KitGoal.ORGANISER to "M’organiser",
    KitGoal.CONVAINCRE to "Convaincre",
    KitGoal.SE_PRESENTER to "Me présenter",
    KitGoal.SE_PROTEGER to "Me protéger",
)

/** The tools of « Agir »: their name, what they do, and where to find them on the site (null: in the application only). */
private class KitTool(val title: String, val text: String, val onSite: String?)

private val kitTools: Map<String, KitTool> = mapOf(
    "mode-manifestation" to KitTool("Mode manifestation", "L’essentiel en un geste et hors ligne : vos droits, les numéros utiles, les premiers secours.", null),
    CitizenKits.POSTER to KitTool("Affiche et tract à code QR", "Depuis une mobilisation de l’agenda, une affiche ou un tract à imprimer, avec un code QR qui contient l’événement.", null),
    "simulateur-greve" to KitTool("Simulateur du gréviste", "Ce que coûte une journée de grève sur votre salaire, et ce que la caisse de grève peut compenser.", null),
    "solidarite" to KitTool("Solidarité et caisses de grève", "Les caisses de grève et de solidarité juridique vérifiées par l’équipe.", AgirSitePaths.SOLIDARITY),
    "se-reunir" to KitTool("Se réunir", "Les collectifs, l’annuaire des organisations, les salons ouverts à tous.", AgirSitePaths.COLLECTIVES),
    "collectifs" to KitTool("Collectifs", "Trouver un collectif près de chez vous, ou en fonder un.", AgirSitePaths.COLLECTIVES),
    "federer" to KitTool("Fédérer autour de soi", "Convaincre ses proches, pas à pas, sans rien publier.", null),
    "s-engager" to KitTool("S’engager en politique", "S’inscrire sur les listes, voter, se présenter, créer ou rejoindre un parti.", AgirSitePaths.ENGAGER),
    "harcelement" to KitTool("Face au harcèlement", "Garder les preuves, se protéger, signaler, porter plainte.", null),
)

/** The citizen action kits (D-232): tools and guides by goal, filtered by plain links (`?but=`, `?sans-reseau=1`). */
fun HTML.kitsDocument(goal: KitGoal?, offlineOnly: Boolean, catalog: ComprendreCatalog?) {
    siteLayout(pageTitle = "Kits d’action citoyenne", currentSpace = Space.AGIR) {
        h1 { +"Kits d’action citoyenne" }
        p(classes = "lead") { +"Les outils et les guides pratiques, réunis et classés par but. Chacun reste aussi dans sa rubrique." }
        nav {
            attributes["aria-label"] = "Je veux…"
            p { strong { +"Je veux…" } }
            ul {
                li { filterLink(null, offlineOnly, goal == null, "Tout afficher") }
                KitGoal.entries.forEach { candidate -> li { filterLink(candidate, offlineOnly, goal == candidate, goalLabels.getValue(candidate)) } }
                li {
                    val href = kitsHref(goal, !offlineOnly)
                    a(href = href) {
                        if (offlineOnly) attributes["aria-current"] = "true"
                        +if (offlineOnly) "Utilisable sans réseau (retirer ce filtre)" else "Utilisable sans réseau seulement"
                    }
                }
            }
        }
        val kits = CitizenKits.filter(goal, offlineOnly)
        section {
            attributes["aria-labelledby"] = "kits-results"
            h2 {
                id = "kits-results"
                +when (kits.size) {
                    0 -> "Aucun kit ne correspond à ces filtres"
                    1 -> "1 kit"
                    else -> "${kits.size} kits"
                }
            }
            kits.forEach { kit -> kitArticle(kit, catalog) }
        }
    }
}

private fun kitsHref(goal: KitGoal?, offline: Boolean): String {
    val parameters = listOfNotNull(goal?.let { "but=${it.id}" }, if (offline) "sans-reseau=1" else null)
    return AgirSitePaths.KITS + if (parameters.isEmpty()) "" else "?" + parameters.joinToString("&")
}

private fun FlowContent.filterLink(goal: KitGoal?, offline: Boolean, current: Boolean, label: String) {
    a(href = kitsHref(goal, offline)) {
        if (current) attributes["aria-current"] = "true"
        +label
    }
}

private fun FlowContent.kitArticle(kit: CitizenKit, catalog: ComprendreCatalog?) {
    article(classes = "agenda-event") {
        when (val target = kit.target) {
            is KitTarget.Guide -> {
                h3 { a(href = fichePath(target.slug)) { +(catalog?.fiche(target.slug)?.title ?: target.slug.replace('-', ' ').replaceFirstChar(Char::uppercase)) } }
                p { span(classes = "badge") { +"Guide" }; if (kit.offline) +" · lisible sans réseau dans l’application" }
                catalog?.fiche(target.slug)?.summary?.let { p { +it } }
            }
            is KitTarget.Rubric -> {
                val tool = kitTools[kit.id]
                h3 { +(tool?.title ?: kit.id) }
                p { span(classes = "badge") { +"Outil" }; if (kit.offline) +" · fonctionne sans réseau" }
                tool?.text?.let { p { +it } }
                val onSite = tool?.onSite
                if (onSite != null) p { a(href = onSite) { +"Ouvrir sur le site" } } else p { span(classes = "app-only") { +"Dans l’application" } }
            }
        }
        p(classes = "muted") { +kit.goals.sortedBy { it.ordinal }.joinToString(" · ") { goalLabels.getValue(it) } }
    }
}

// ---------------------------------------------------------------------------------------------
// Solidarité et caisses de grève

private val fundTypeLabels: Map<String, String> = mapOf(
    SolidarityFormats.FUND_STRIKE to "Caisse de grève",
    SolidarityFormats.FUND_LEGAL to "Solidarité juridique",
    SolidarityFormats.FUND_OTHER to "Autre caisse de solidarité",
)

/** Solidarity and strike funds (AG-10): verified by the team, with a link to their own donation page only while open. */
fun HTML.solidarityDocument(funds: List<SolidarityFund>?, catalog: ComprendreCatalog?) {
    siteLayout(pageTitle = "Solidarité et caisses de grève", currentSpace = Space.AGIR) {
        h1 { +"Solidarité et caisses de grève" }
        p(classes = "lead") { +"Les caisses de grève et de solidarité juridique vérifiées par l’équipe de PPF, avec un lien vers leur propre page de dons. PPF ne manipule jamais l’argent." }
        p { +"Méfiez-vous des fausses cagnottes : ne donnez que sur la page indiquée ici, et vérifiez qui gère la caisse." }
        val shown = funds.orEmpty().filterNot { it.withdrawn }.sortedWith(compareBy<SolidarityFund> { !it.isOpen }.thenBy { it.displayName.lowercase() })
        section {
            attributes["aria-labelledby"] = "caisses"
            h2 {
                id = "caisses"
                +when (shown.size) {
                    0 -> "Aucune caisse publiée pour l’instant"
                    1 -> "1 caisse"
                    else -> "${shown.size} caisses"
                }
            }
            shown.forEach { fund ->
                article(classes = "agenda-event") {
                    h3 {
                        if (fund.isClosed) strong { +"Close : " }
                        +fund.name.trim()
                    }
                    dl {
                        dt { +"Type" }
                        dd { +(fundTypeLabels[fund.type] ?: fund.type) }
                        dt { +"Territoire" }
                        dd { +fund.territory }
                        dt { +"Pour qui" }
                        dd { +fund.beneficiary }
                        fund.managerName?.let { dt { +"Gérée par" }; dd { +it } }
                        fund.strike?.let { dt { +"Grève" }; dd { +it } }
                        dt { +"Vérifiée le" }
                        dd { +fund.verifiedOn.toFrench() }
                    }
                    p { +fund.summary }
                    fund.openableDonationLink?.let { link ->
                        p { external(link, "Page de dons de la caisse (${fund.donationHost ?: "site extérieur"})") }
                    }
                    if (fund.origin == ContentOrigin.AI_ASSISTED) p(classes = "muted") { +"Rédigé par l’IA" }
                }
            }
        }
        section {
            id = "guides"
            h2 { +"Pour aller plus loin" }
            ul {
                li { guide(catalog, "faire-greve-vos-droits", "Faire grève : vos droits") }
                li { guide(catalog, "collecter-de-l-argent-pour-une-cause", "Collecter de l’argent pour une cause") }
            }
            p { +"Le simulateur du gréviste (ce que coûte une journée de grève) et les missions de bénévolat sont dans l’application." }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Collectifs

private val collectiveTypeLabels: Map<CollectiveType, String> = mapOf(
    CollectiveType.COLLECTIF to "Collectif",
    CollectiveType.CLUB to "Club",
    CollectiveType.COMITE to "Comité",
    CollectiveType.ASSEMBLEE to "Assemblée",
    CollectiveType.RESEAU to "Réseau",
    CollectiveType.AUTRE to "Autre",
)

private val collectiveScopeLabels: Map<CollectiveScope, String> = mapOf(
    CollectiveScope.NATIONAL to "National",
    CollectiveScope.REGIONAL to "Régional",
    CollectiveScope.DEPARTEMENTAL to "Départemental",
    CollectiveScope.LOCAL to "Local",
    CollectiveScope.QUARTIER to "Quartier",
    CollectiveScope.ETABLISSEMENT to "Établissement",
)

/** The public directory of the collectifs (M24): name, type, scope, territory, presentation. Membership is never shown. */
fun HTML.collectivesDocument(territory: String?, collectives: List<Collective>?) {
    siteLayout(pageTitle = "Collectifs", currentSpace = Space.AGIR) {
        h1 { +"Collectifs" }
        p(classes = "lead") { +"Les collectifs fondés dans PPF, près de chez vous et partout en France. Les membres d’un collectif ne sont jamais publiés." }
        form(action = AgirSitePaths.COLLECTIVES, method = FormMethod.get) {
            attributes["role"] = "search"
            attributes["aria-label"] = "Filtrer les collectifs"
            label { htmlFor = "collectifs-territoire"; +"Territoire" }
            input(type = InputType.text, name = "territoire") {
                id = "collectifs-territoire"
                maxLength = MAX_TERRITORY.toString()
                attributes["autocomplete"] = "off"
                territory?.let { value = it }
            }
            button(type = kotlinx.html.ButtonType.submit) { +"Filtrer" }
        }
        val shown = collectives.orEmpty().filter { it.listed }
        section {
            attributes["aria-labelledby"] = "collectifs-resultats"
            h2 {
                id = "collectifs-resultats"
                +when (shown.size) {
                    0 -> "Aucun collectif"
                    1 -> "1 collectif"
                    else -> "${shown.size} collectifs"
                }
            }
            shown.forEach { collective ->
                article(classes = "agenda-event") {
                    h3 { +collective.name.trim() }
                    dl {
                        dt { +"Type" }
                        dd { +collectiveTypeLabels.getValue(collective.type) }
                        collective.scope?.let { dt { +"Portée" }; dd { +collectiveScopeLabels.getValue(it) } }
                        collective.territory?.let { dt { +"Territoire" }; dd { +it } }
                        if (collective.themes.isNotEmpty()) { dt { +"Thèmes" }; dd { +collective.themes.joinToString(", ") } }
                    }
                    collective.about?.lines()?.filter { it.isNotBlank() }?.forEach { p { +it } }
                }
            }
        }
        p { +"Pour rejoindre un collectif, discuter dans son salon ou en fonder un, utilisez l’application PPF." }
        p { a(href = "/organisations") { +"L’annuaire des organisations vérifiées" } }
    }
}

// ---------------------------------------------------------------------------------------------

private fun FlowContent.external(href: String, label: String) {
    a(href = href) { attributes["rel"] = EXTERNAL; +label }
}

private fun FlowContent.guide(catalog: ComprendreCatalog?, slug: String, fallback: String) {
    a(href = fichePath(slug)) { +(catalog?.fiche(slug)?.title ?: fallback) }
}

private fun FlowContent.course(catalog: ComprendreCatalog?, slug: String, fallback: String) {
    a(href = coursePath(slug)) { +("Parcours : " + (catalog?.course(slug)?.article?.title ?: fallback)) }
}
