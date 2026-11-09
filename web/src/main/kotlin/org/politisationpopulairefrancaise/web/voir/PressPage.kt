package org.politisationpopulairefrancaise.web.voir

import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.article
import kotlinx.html.code
import kotlinx.html.dd
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.h1
import kotlinx.html.h3
import kotlinx.html.h4
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.ul
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.voir.MediaKit
import org.politisationpopulairefrancaise.core.voir.PressRelease
import org.politisationpopulairefrancaise.core.voir.VoirCatalog
import org.politisationpopulairefrancaise.core.voir.VoirTags
import org.politisationpopulairefrancaise.web.siteLayout

/** The address of « Espace presse » on the site (PP-04). */
const val PRESS_PATH: String = "/presse"

/**
 * « Espace presse » (M34, PP-04), readable without script: the press releases newest first, the media kit, the
 * reusable figures and the press contact through the contact of PPF (M30, category « Journaliste »).
 */
fun HTML.pressDocument(catalog: VoirCatalog) {
    siteLayout(pageTitle = "Espace presse", currentSpace = Space.VOIR) {
        h1 { +"Espace presse" }
        p { +"Pour les journalistes, les chercheurs et les observateurs : les communiqués de PPF, de quoi nous nommer et nous montrer, des chiffres réutilisables avec leurs sources, et le contact presse." }
        pageSection("communiques", "Communiqués") {
            if (catalog.pressReleases.isEmpty()) p(classes = "muted") { +"Aucun communiqué n’est encore publié." }
            catalog.pressReleases.forEach { release -> article(classes = "press-release") { pressReleaseBody(release) } }
        }
        pageSection("kit", "Kit média") {
            dl {
                dt { +"Nom" }
                dd { +"${MediaKit.NAME} (${MediaKit.ACRONYM})" }
                dt { +"Site" }
                dd { +MediaKit.SITE }
                dt { +"Couleurs" }
                dd {
                    code { +MediaKit.BRAND_COLOR }
                    +" (bleu de la marque), "
                    code { +MediaKit.ACCENT_COLOR }
                    +" (carmin), "
                    code { +MediaKit.INK_COLOR }
                    +" (texte)"
                }
                dt { +"Police" }
                dd { +MediaKit.TYPEFACE }
                dt { +"Icônes" }
                dd { +MediaKit.ICONS }
                dt { +"Icône de l’application" }
                dd { a(href = "/static/icon.svg") { +"icon.svg" } }
            }
            p { +"Textes et images de PPF : licence CC BY 4.0 ; données : Licence Ouverte 2.0. Citez la source et la date." }
            p(classes = "muted") { +"PPF n’utilise jamais l’identité visuelle de l’État (logo officiel, bloc-marque) : merci de ne pas l’associer à nos contenus." }
        }
        pageSection("chiffres", "Chiffres réutilisables") {
            p { +"Les jeux de données de PPF sont téléchargeables, chacun avec sa source et sa licence. Chaque contenu se cite avec « Citer ce contenu »." }
            ul {
                li { a(href = "/donnees") { +"Données ouvertes" } }
                li { a(href = TRANSPARENCY_PATH) { +"Transparence de PPF" } }
                li { a(href = VICTORIES_PATH) { +"Nos victoires" } }
                li { a(href = DASHBOARD_PATH) { +"Tableau de bord du mouvement" } }
            }
        }
        pageSection("contact-presse", "Contact presse") {
            p { +"Écrivez à l’équipe par le formulaire de contact de PPF, en choisissant « Journaliste ». Réponse visée sous ${MediaKit.PRESS_REPLY_GOAL_DAYS} jours. Aucun e-mail n’est demandé : vous suivez l’échange avec votre numéro et votre code." }
        }
    }
}

/** One press release, as the site shows it: shared by the page and the preview of the back-office. */
internal fun FlowContent.pressReleaseBody(release: PressRelease) {
    p(classes = "muted") { date(release.date) }
    h3 { +release.title }
    release.article.summary?.let { p { +it } }
    if (release.article.body.isNotBlank()) markdownBelow(release.article.body, levels = 2)
    if (release.sources.isNotEmpty()) {
        h4 { +"Sources" }
        ul(classes = "source-list") {
            release.sources.forEach { source ->
                li {
                    +"${source.kind.label} : "
                    val url = source.url?.takeIf(VoirTags::isWebAddress)
                    if (url != null) externalLink(url, source.name) else +source.name
                }
            }
        }
    }
    if (release.archives.isNotEmpty()) {
        h4 { +"Documents archivés" }
        ul {
            release.archives.forEach { copy ->
                li {
                    +"Empreinte "
                    code(classes = "hash") { +copy.sha256 }
                    copy.archiveUrl?.takeIf(VoirTags::isWebAddress)?.let {
                        +". "
                        externalLink(it, "Voir le document archivé")
                    }
                }
            }
        }
    }
    if (release.corrections.isNotEmpty()) {
        h4 { +"Corrections" }
        ul {
            release.corrections.forEach { correction ->
                li {
                    +"Corrigé le "
                    date(correction.date)
                    +" : ${correction.reason}"
                }
            }
        }
    }
}
