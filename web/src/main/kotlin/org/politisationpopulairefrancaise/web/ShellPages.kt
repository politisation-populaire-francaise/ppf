package org.politisationpopulairefrancaise.web

import io.ktor.server.html.respondHtml
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.nav
import org.politisationpopulairefrancaise.core.agir.calls.Call
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.RevendicationStatus
import org.politisationpopulairefrancaise.core.endirect.LiveBlock
import org.politisationpopulairefrancaise.core.endirect.LivePreset
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.VoirAlert
import org.politisationpopulairefrancaise.core.voir.VoirItem
import org.politisationpopulairefrancaise.web.decider.RevendicationLinks
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.ul
import org.politisationpopulairefrancaise.core.agir.poster.AgirLinks
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.agir.manifestation.EmergencyChannel
import org.politisationpopulairefrancaise.core.agir.manifestation.EmergencyNumber
import org.politisationpopulairefrancaise.core.agir.manifestation.EmergencyNumbers
import org.politisationpopulairefrancaise.core.comprendre.ComprendreLinks
import org.politisationpopulairefrancaise.core.decider.Petition
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.voir.Fact
import org.politisationpopulairefrancaise.core.voir.Indicator
import org.politisationpopulairefrancaise.core.voir.VoirAxis
import org.politisationpopulairefrancaise.core.voir.VoirCatalog
import org.politisationpopulairefrancaise.core.voir.VoirLinks
import org.politisationpopulairefrancaise.web.comprendre.ComprendreSource
import org.politisationpopulairefrancaise.web.comprendre.parisToday
import org.politisationpopulairefrancaise.web.decider.DeciderLinks
import org.politisationpopulairefrancaise.web.decider.DeciderSource
import org.politisationpopulairefrancaise.web.voir.VoirSource
import org.politisationpopulairefrancaise.web.voir.factCard
import org.politisationpopulairefrancaise.web.voir.keyFigureCard

/** The spaces whose site page presents what the application does (they have no page of their own on the site yet). */
internal val appSpaces: Set<Space> = setOf(Space.FIL, Space.CARTE, Space.MESSAGES, Space.AGIR, Space.APPELS, Space.MON_ESPACE)

private fun fiche(slug: String): String = "${ComprendreLinks.HOME}/${ComprendreLinks.FICHES}/$slug"

/** What "En direct" shows, read from the sources that exist (each one may be missing without a database). */
class LiveContents(
    val mobilizations: List<SignedEvent> = emptyList(),
    val figures: List<Indicator> = emptyList(),
    val facts: List<Fact> = emptyList(),
    val voir: VoirCatalog? = null,
    val petitions: List<Petition> = emptyList(),
    val decryptages: List<NavLink> = emptyList(),
    val alerts: List<VoirAlert> = emptyList(),
    val calls: List<Call> = emptyList(),
    val headline: Article? = null,
)

private const val LIVE_ITEMS: Int = 3
private const val LIVE_FIGURES: Int = 4

/**
 * The pages of the shell: « En direct » (the home of the application), AIDE (the red button) and,
 * for the spaces that only exist in the application, a page that says what they do and what the site
 * already offers instead of an empty placeholder.
 */
fun Route.shellPages(
    voir: VoirSource? = null,
    comprendre: ComprendreSource? = null,
    decider: DeciderSource? = null,
    agenda: AgendaSource? = null,
    calls: CallsSource? = null,
) {
    get(SiteNavigation.HELP_PATH) { call.respondHtml { helpDocument() } }
    // The site opens on « En direct », as the application does.
    for (path in listOf("/", "/${Space.EN_DIRECT.id}")) {
        get(path) {
            val contents = liveContents(voir, comprendre, decider, agenda, calls)
            val view = viewOf(call.request.queryParameters["vue"])
            call.respondHtml { liveDocument(contents, view) }
        }
    }
    appSpaces.forEach { space ->
        get("/${space.id}") { call.respondHtml { appSpaceDocument(space) } }
    }
}

private suspend fun liveContents(
    voir: VoirSource?,
    comprendre: ComprendreSource?,
    decider: DeciderSource?,
    agenda: AgendaSource?,
    calls: CallsSource?,
): LiveContents {
    val voirCatalog = voir?.catalog()?.takeUnless { it.isEmpty }
    val today = parisToday()
    val now = java.time.Instant.now().epochSecond
    return LiveContents(
        mobilizations = agenda?.mobilizations(AgendaQuery()).orEmpty().take(LIVE_ITEMS),
        figures = voirCatalog?.let { catalog ->
            catalog.keyFigures(VoirAxis.MOUVEMENT).ifEmpty { catalog.indicators }.take(LIVE_FIGURES)
        }.orEmpty(),
        facts = voirCatalog?.chronology()?.take(LIVE_ITEMS).orEmpty(),
        voir = voirCatalog,
        petitions = decider?.catalog()?.openPetitions(today)?.take(LIVE_ITEMS).orEmpty(),
        decryptages = comprendre?.catalog()?.decryptages?.take(LIVE_ITEMS)?.mapNotNull { d ->
            ComprendreLinks.pathOf(d.article)?.let { NavLink(d.article.title, it) }
        }.orEmpty(),
        alerts = voirCatalog?.let { VoirAlert.of(it, today) }.orEmpty(),
        calls = calls?.calls().orEmpty()
            .filter { it.isOpen && (it.deadline == null || it.deadline!! >= now) }
            .sortedBy { it.dueAt ?: Long.MAX_VALUE }
            .take(LIVE_ITEMS),
        headline = decider?.demands()?.let(::demandOfTheMoment),
    )
}

/** The demand of the moment, as in the application: a campaign first, then a demand kept, then a very supported one. */
internal fun demandOfTheMoment(demands: List<Article>): Article? =
    listOf(RevendicationStatus.CAMPAGNE, RevendicationStatus.RETENUE, RevendicationStatus.TRES_SOUTENUE)
        .firstNotNullOfOrNull { status -> demands.firstOrNull { it.status == status } }

/** The ready-made view asked by `?vue=` (« L'essentiel » otherwise), as in the application, without account nor script. */
internal fun viewOf(parameter: String?): LivePreset = parameter?.let(LivePreset::fromId) ?: LivePreset.ESSENTIEL

// ---------------------------------------------------------------------------------------------
// En direct

private val VIEW_LABELS: Map<LivePreset, String> = mapOf(
    LivePreset.ESSENTIEL to "L’essentiel",
    LivePreset.MOBILISATION to "Mobilisation",
    LivePreset.POLITIQUE to "Politique",
    LivePreset.PRES_DE_CHEZ_MOI to "Près de chez moi",
)

fun HTML.liveDocument(contents: LiveContents, view: LivePreset = LivePreset.ESSENTIEL) {
    siteLayout(pageTitle = "En direct", currentSpace = Space.EN_DIRECT) {
        h1 { +"En direct" }
        p(classes = "lead") { +"Ce qu’il faut savoir en moins de trente secondes, en quatre vues : choisissez la vôtre. Dans l’application, chacun compose aussi les siennes." }
        nav(classes = "view-tabs") {
            attributes["aria-label"] = "Vues d’En direct"
            ul {
                LivePreset.entries.forEach { preset ->
                    li {
                        a(href = if (preset == LivePreset.ESSENTIEL) "/${Space.EN_DIRECT.id}" else "/${Space.EN_DIRECT.id}?vue=${preset.id}") {
                            if (preset == view) attributes["aria-current"] = "page"
                            +VIEW_LABELS.getValue(preset)
                        }
                    }
                }
            }
        }
        view.blocks.forEach { block -> liveBlock(block, contents) }
        if (view == LivePreset.ESSENTIEL || view == LivePreset.POLITIQUE) {
            section {
                id = "vrai-faux"
                h2 { +"Le vrai du faux" }
                if (contents.decryptages.isEmpty()) p(classes = "muted") { +"Aucun décryptage publié pour l’instant." }
                ul(classes = "card-list") { contents.decryptages.forEach { link -> li { a(href = link.href) { +link.label } } } }
                p(classes = "section-more") { a(href = "${ComprendreLinks.HOME}/${ComprendreLinks.DECRYPTAGES}") { +"Tous les décryptages" } }
            }
        }
        section {
            id = "application"
            h2 { +"Dans l’application" }
            p { +"Le fil en direct, vos rappels, la semaine au Parlement et les mobilisations de votre territoire sont dans l’application PPF, qui fonctionne aussi hors ligne. Elle s’ouvre d’abord en bêta, sur invitation." }
        }
    }
}

/** One block of the page, the same as in the application; those only the application has are left out. */
private fun FlowContent.liveBlock(block: LiveBlock, contents: LiveContents) {
    when (block) {
        LiveBlock.ALERTS -> section {
            id = "alertes"
            h2 { +"Alertes" }
            if (contents.alerts.isEmpty()) p(classes = "muted") { +"Aucun vote proche ni interdiction récente." }
            ul(classes = "card-list") {
                contents.alerts.forEach { alert ->
                    li {
                        when (alert) {
                            is VoirAlert.UpcomingVote -> {
                                p(classes = "kicker alert-kicker") { +(when (alert.daysLeft) { 0 -> "Vote aujourd’hui"; 1 -> "Vote demain"; else -> "Vote dans ${alert.daysLeft} jours" }) }
                                a(href = VoirLinks.pathOf(VoirItem.OfText(alert.text))) { +alert.text.title }
                            }
                            is VoirAlert.ProtestBan -> {
                                p(classes = "kicker alert-kicker") { +"Interdiction de manifester" }
                                a(href = VoirLinks.pathOf(VoirItem.OfFact(alert.fact))) { +alert.fact.title }
                            }
                        }
                    }
                }
            }
        }
        LiveBlock.UPCOMING -> section {
            id = "mobilisations"
            h2 { +"Prochaines mobilisations" }
            if (contents.mobilizations.isEmpty()) p(classes = "muted") { +"Aucune mobilisation annoncée pour les semaines à venir." }
            contents.mobilizations.forEach { mobilization(it, compact = true) }
            p(classes = "section-more") { a(href = "/agenda") { +"Tout l’agenda, filtré par territoire" } }
        }
        LiveBlock.CALLS -> section {
            id = "appels"
            h2 { +"Appels qui pressent" }
            if (contents.calls.isEmpty()) p(classes = "muted") { +"Aucun appel ouvert pour l’instant." }
            ul(classes = "card-list") {
                contents.calls.forEach { c ->
                    li {
                        a(href = callPath(c)) { +c.title }
                        c.dueAt?.let { due -> p(classes = "muted") { +"Avant le ${CivilDate.fromEpochSeconds(due).toFrench()}" } }
                    }
                }
            }
            p(classes = "section-more") { a(href = "/appels") { +"Tous les appels ouverts" } }
        }
        LiveBlock.DECIDER -> section {
            id = "decider"
            h2 { +"Décider ensemble" }
            ul(classes = "card-list") {
                li { a(href = "${DeciderLinks.HOME}#pacte") { +"La Constitution Citoyenne et Démocratique" } }
                contents.petitions.forEach { petition -> li { a(href = DeciderLinks.petition(petition)) { +petition.title } } }
            }
            p(classes = "section-more") { a(href = DeciderLinks.HOME) { +"Tout « Décider »" } }
        }
        LiveBlock.HEADLINE -> section {
            id = "une"
            h2 { +"Revendication à la une" }
            val demand = contents.headline
            if (demand == null) p(classes = "muted") { +"Aucune revendication en campagne pour l’instant." }
            else {
                p { a(href = RevendicationLinks.of(demand)) { +demand.title } }
                demand.summary?.let { p { +it } }
            }
            p(classes = "section-more") { a(href = RevendicationLinks.HOME) { +"Le mur des revendications" } }
        }
        LiveBlock.FIGURES -> section {
            id = "chiffres"
            h2 { +"Chiffres clés" }
            if (contents.figures.isEmpty()) p(classes = "muted") { +"Les chiffres clés ne sont pas encore publiés." }
            div(classes = "card-grid") { contents.figures.forEach { keyFigureCard(it, level = 3) } }
            p(classes = "section-more") { a(href = VoirLinks.HOME) { +"Tous les chiffres de « Voir »" } }
        }
        LiveBlock.CHRONOLOGY -> section {
            id = "faits"
            h2 { +"Derniers faits sourcés" }
            val catalog = contents.voir
            if (contents.facts.isEmpty() || catalog == null) p(classes = "muted") { +"Rien de recensé pour l’instant." }
            else contents.facts.forEach { factCard(it, catalog, level = 3) }
            p(classes = "section-more") { a(href = VoirLinks.CHRONOLOGY) { +"Toute la chronologie du mouvement" } }
        }
        // The live posts, the reminders, the week in Parliament and the news of PPF are in the application.
        LiveBlock.LIVE, LiveBlock.PARLIAMENT, LiveBlock.REMINDERS, LiveBlock.NEWS, LiveBlock.CAP, LiveBlock.ELUS -> Unit
    }
}

// ---------------------------------------------------------------------------------------------
// AIDE

private val numberLabels: Map<String, String> = mapOf(
    "samu" to "SAMU, urgence médicale",
    "europe" to "Numéro d’urgence européen, depuis tout pays de l’Union",
    "police" to "Police secours",
    "pompiers" to "Pompiers",
    "sourds" to "Personnes sourdes, malentendantes ou aphasiques, par SMS",
    "enfance" to "Enfant en danger, ou inquiétude pour un enfant : gratuit, jour et nuit",
    "enfant-disparu" to "Enfant disparu : information et soutien des familles",
    "victimes" to "Aide aux victimes d’une infraction et à leurs proches : gratuit, tous les jours de 9 h à 20 h",
    "suicide" to "Prévention du suicide, pour soi ou pour un proche : gratuit et confidentiel, jour et nuit",
    "violences-femmes" to "Violences faites aux femmes, pour les victimes et les témoins : gratuit, anonyme, jour et nuit",
    "cyberharcelement" to "Harcèlement et cyberharcèlement, pour les jeunes et leurs parents : gratuit, anonyme",
    "croix-rouge-ecoute" to "Croix-Rouge Écoute, soutien psychologique : gratuit, anonyme",
    "sos-amitie" to "SOS Amitié, des bénévoles à l’écoute des personnes en détresse : anonyme, jour et nuit",
)

fun HTML.helpDocument() {
    siteLayout(pageTitle = "AIDE") {
        h1 { +"De quoi avez-vous besoin ?" }
        section(classes = "vital") {
            id = "urgence"
            h2 { +"Urgence vitale" }
            p { +"PPF ne remplace jamais les secours. Si une vie est en danger, appelez d’abord le 15 ou le 112." }
            numbers(EmergencyNumbers.emergency)
            p(classes = "muted") { +"Numéros vérifiés sur service-public.gouv.fr le 7 novembre 2026." }
        }
        section {
            id = "avocat"
            h2 { +"Besoin d’un avocat" }
            p { +"En garde à vue, vous avez droit à un avocat dès le début. Les legal teams et les avocats bénévoles de votre territoire sont dans l’annuaire des organisations de l’application." }
            ul(classes = "card-list") {
                li { a(href = fiche("garde-a-vue")) { +"Que faire en garde à vue" } }
                li { a(href = fiche("droits-en-manifestation")) { +"Vos droits en manifestation" } }
                li { a(href = fiche("apres-une-blessure-en-manifestation")) { +"Après une blessure en manifestation" } }
            }
        }
        section {
            id = "parler"
            h2 { +"Besoin de parler ?" }
            p { +"Après un moment difficile, vous pouvez appeler, pour vous ou parce que vous vous inquiétez pour quelqu’un. Ces lignes ne remplacent pas les secours : en cas de danger, le 17 ou le 112." }
            numbers(EmergencyNumbers.talk)
            h3 { +"Associations d’écoute" }
            numbers(EmergencyNumbers.talkAssociations)
            p(classes = "muted") { +"Lignes publiques vérifiées le 8 novembre 2026 sur service-public.gouv.fr, 3114.fr, allo119.gouv.fr et justice.gouv.fr ; associations citées par sante.fr." }
        }
        section {
            id = "application"
            h2 { +"Dans l’application" }
            p { +"Le mode manifestation (l’essentiel hors ligne en un geste), le point de ralliement gardé sur votre téléphone et le kit contre le harcèlement en ligne sont dans l’application PPF." }
        }
    }
}

private fun FlowContent.numbers(list: List<EmergencyNumber>) {
    ul(classes = "numbers") {
        list.forEach { number ->
            li {
                span(classes = "number") {
                    a(href = number.uri) {
                        attributes["aria-label"] = (if (number.channel == EmergencyChannel.SMS) "Envoyer un SMS au " else "Appeler le ") + number.number
                        +number.number
                    }
                }
                span { +numberLabels[number.id].orEmpty() }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The spaces of the application

/** A rubric of the application, with its anchor, and the page of the site that already covers it, if any. */
private class AppRubric(val id: String, val title: String, val text: String, val onSite: NavLink? = null)

private class AppSpacePage(val lead: String, val rubrics: List<AppRubric>, val note: String? = null)

private val appPages: Map<Space, AppSpacePage> = mapOf(
    Space.FIL to AppSpacePage(
        lead = "Les publications des comptes que vous suivez, les mobilisations et les contenus du moment.",
        rubrics = listOf(
            AppRubric("fil", "Le fil d’actualité", "Les publications signées des citoyens, des collectifs, des médias et des élus que vous suivez, avec leurs sources. Vos abonnements restent sur votre téléphone.", NavLink("Les derniers faits sourcés", VoirLinks.CHRONOLOGY)),
            AppRubric("decryptages", "Le vrai du faux", "Les décryptages de Comprendre, avec leurs sources.", NavLink("Lire les décryptages", "${ComprendreLinks.HOME}/${ComprendreLinks.DECRYPTAGES}")),
            AppRubric("presse", "Communiqués de PPF", "Les communiqués et les prises de position de PPF.", NavLink("Espace presse", "/presse")),
        ),
    ),
    Space.CARTE to AppSpacePage(
        lead = "Manifestations, rassemblements et lieux utiles près de chez vous.",
        rubrics = listOf(
            AppRubric("carte", "La carte des mobilisations", "Les mobilisations à venir et les lieux utiles autour de vous, sur une carte libre (OpenStreetMap).", NavLink("L’agenda des mobilisations, filtré par territoire", "/agenda")),
        ),
    ),
    Space.MESSAGES to AppSpacePage(
        lead = "Vos conversations privées et de groupe, les salons de vos collectifs, et les flux des médias et des élus que vous suivez.",
        rubrics = listOf(
            AppRubric("messages", "Conversations chiffrées", "Les messages sont chiffrés de bout en bout sur votre téléphone : les clés n’en sortent jamais, c’est pourquoi la messagerie n’existe que dans l’application."),
            AppRubric("salons", "Salons des collectifs", "Les salons ouverts à tous, avec leur charte et leur territoire."),
        ),
    ),
    Space.AGIR to AppSpacePage(
        lead = "Se mobiliser, s’organiser, s’entraider, témoigner, communiquer.",
        rubrics = listOf(
            AppRubric("agenda", "Agenda des mobilisations", "Manifestations, grèves, rassemblements, blocages et assemblées générales, près de chez vous et partout en France.", NavLink("Ouvrir l’agenda", "/agenda")),
            AppRubric("temoigner", "Témoigner", "Photos, vidéos et récits envoyés par un canal sécurisé, vérifiés et anonymisés avant publication."),
            AppRubric("collectifs", "Se réunir", "Trouver un collectif près de chez vous, voir ses événements et ses appels, ou en fonder un. Les salons de discussion restent ouverts à tous.", NavLink("Les collectifs", AgirSitePaths.COLLECTIVES)),
            AppRubric("s-engager", "S’engager en politique", "S’inscrire sur les listes électorales, voter, se présenter, monter une liste, créer ou rejoindre un parti : dans l’ordre du calendrier, les mêmes outils pour tous.", NavLink("Les étapes, dans l’ordre du calendrier", AgirSitePaths.ENGAGER)),
            AppRubric("solidarite", "Solidarité et caisses de grève", "Caisses de grève et de solidarité juridique vérifiées par l’équipe, avec des liens vers leurs propres pages de dons.", NavLink("Les caisses vérifiées", AgirSitePaths.SOLIDARITY)),
            AppRubric("organisations", "Organisations", "Associations, syndicats, collectifs, legal teams, street medics et avocats bénévoles, vérifiés par l’équipe de PPF.", NavLink("L’annuaire des organisations", "/organisations")),
            AppRubric("appels", "Appels", "Se réunir, soutenir une cause, interpeller ses élus, chercher des bénévoles : les appels des citoyens et des organisations.", NavLink("Les appels ouverts", AgirLinks.CALLS)),
            AppRubric("kits", "Kits d’action citoyenne", "Manifester, faire grève, s’organiser, convaincre, se présenter, se protéger : tous les outils et guides au même endroit.", NavLink("Les kits, par but", AgirSitePaths.KITS)),
            AppRubric("hors-reseau", "Messagerie hors réseau", "Des messages de proche en proche, sans réseau, entre téléphones : à venir dans l’application."),
            AppRubric("mode-manifestation", "Mode manifestation", "L’essentiel en un geste et hors ligne : vos droits, les numéros utiles, les premiers secours.", NavLink("AIDE : numéros utiles et avocat", SiteNavigation.HELP_PATH)),
        ),
    ),
    Space.APPELS to AppSpacePage(
        lead = "Les appels à la grève, à manifester, à soutenir, à bloquer : qui appelle, où, quand, et comment répondre.",
        rubrics = listOf(
            AppRubric("appels", "Les appels", "Chaque appel dit qui appelle, où, quand et comment répondre ; on y répond depuis l’application.", NavLink("Les appels ouverts", AgirLinks.CALLS)),
        ),
    ),
    Space.MON_ESPACE to AppSpacePage(
        lead = "Votre tableau de bord personnel : votre territoire, vos suivis, vos votes.",
        rubrics = listOf(
            AppRubric("appareil", "Tout reste sur votre appareil", "Votre compte est une paire de clés créée sur votre téléphone, sans e-mail ni numéro de téléphone. Le site, lui, se lit sans compte et ne garde rien de vous.", NavLink("Politique de confidentialité", "/confidentialite")),
        ),
    ),
)

fun HTML.appSpaceDocument(space: Space) {
    val page = appPages.getValue(space)
    siteLayout(pageTitle = spaceLabels.getValue(space), currentSpace = space) {
        h1 { +spaceLabels.getValue(space) }
        p(classes = "lead") { +page.lead }
        page.rubrics.forEach { rubric ->
            section {
                id = rubric.id
                h2 { +rubric.title }
                p { +rubric.text }
                val link = rubric.onSite
                if (link != null) p { +"Sur le site : "; a(href = link.href) { +link.label } }
                else p { span(classes = "app-only") { +"Dans l’application" } }
            }
        }
        p(classes = "muted") { +"L’application PPF s’ouvre d’abord en bêta, sur invitation ; elle fonctionne aussi hors ligne." }
    }
}
