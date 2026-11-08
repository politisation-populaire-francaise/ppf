package org.politisationpopulairefrancaise.web

import io.ktor.server.html.respondHtml
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.code
import kotlinx.html.dd
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.ul
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A file of a dataset, as the page lists it. */
data class OpenDataFileView(val format: String, val url: String, val bytes: Int)

/** A dataset, as the page lists it. */
data class OpenDataSetView(val id: String, val title: String, val description: String, val rows: Int, val schemaUrl: String?, val files: List<OpenDataFileView>)

/** What the page « Données ouvertes » shows, read from the catalog of the open export (M34, S-48). */
data class OpenDataView(
    val updatedAt: Long?,
    /** False while the owner has not chosen the licence. */
    val licenseChosen: Boolean,
    val licenseText: String,
    val licenseUrl: String?,
    val catalogUrl: String,
    val datasets: List<OpenDataSetView>,
)

/** Where the page reads the catalog: the server's open export; null without a database. */
fun interface OpenDataSource {
    suspend fun view(): OpenDataView
}

/** `/donnees`: the open data of PPF, readable without script (M34, S-48; docs/integration/export-ouvert.md). */
fun Route.openDataPage(source: OpenDataSource) {
    get("/donnees") {
        val view = source.view()
        call.respondHtml { openDataDocument(view) }
    }
}

/** The page itself; separate from the route so that tests render it without a server. */
fun HTML.openDataDocument(view: OpenDataView) {
    siteLayout(pageTitle = "Données ouvertes") {
        h1 { +"Données ouvertes" }
        p {
            +"Tout ce que publie l’équipe de Politisation Populaire Française se télécharge ici, sans compte : les contenus signés eux-mêmes, et des tableaux pour les tableurs où chaque ligne garde sa source et la version du contenu d’où elle vient. Personne ne sait qui télécharge : rien n’est enregistré."
        }
        section {
            attributes["aria-labelledby"] = "licence"
            h2 { id = "licence"; +"Licence" }
            p(classes = if (view.licenseChosen) null else "notice") {
                +view.licenseText
                view.licenseUrl?.let {
                    +" "
                    a(href = it) { +"Texte de la licence" }
                }
            }
            p { +"Les citations et les titres d’articles de presse restent à leurs auteurs." }
        }
        section {
            attributes["aria-labelledby"] = "jeux"
            h2 { id = "jeux"; +"Les jeux de données" }
            view.updatedAt?.let { p(classes = "muted") { +"À jour au ${frenchDate(it)}." } }
            if (view.datasets.isEmpty()) p(classes = "muted") { +"Aucun contenu publié pour l’instant." }
            view.datasets.forEach { dataset ->
                section(classes = "card") {
                    attributes["aria-labelledby"] = "jeu-${dataset.id}"
                    h3 { id = "jeu-${dataset.id}"; +dataset.title }
                    p { +dataset.description }
                    dl {
                        dt { +"Lignes" }
                        dd { +dataset.rows.toString() }
                        dt { +"Fichiers" }
                        dd {
                            ul(classes = "link-list") {
                                dataset.files.forEach { file ->
                                    li { a(href = file.url) { +"${formatLabel(file.format)} (${size(file.bytes)})" } }
                                }
                                dataset.schemaUrl?.let { li { a(href = it) { +"Description des colonnes (schéma JSON)" } } }
                            }
                        }
                    }
                }
            }
        }
        section {
            attributes["aria-labelledby"] = "verifier"
            h2 { id = "verifier"; +"Vérifier et réutiliser" }
            p {
                +"Chaque contenu est un événement signé : son identifiant est l’empreinte SHA-256 de son texte, et sa signature prouve qu’il vient d’une clé de l’équipe, listée dans le catalogue. Une ligne d’un tableau donne l’adresse stable du contenu et sa version : retrouvez-les dans le fichier des contenus pour vérifier la signature."
            }
            p {
                +"Pour un programme : le catalogue "
                a(href = view.catalogUrl) { code { +view.catalogUrl } }
                +" donne chaque fichier avec son empreinte et sa taille ; les formats sont décrits dans la documentation du dépôt (docs/FORMATS.md, section 5 bis)."
            }
        }
    }
}

private fun formatLabel(format: String): String = when (format) {
    "csv" -> "Tableur (CSV)"
    "json" -> "JSON"
    else -> format.uppercase()
}

private fun size(bytes: Int): String = when {
    bytes < 1024 -> "$bytes octets"
    bytes < 1024 * 1024 -> "${(bytes + 1023) / 1024} Ko"
    else -> String.format(Locale.FRANCE, "%.1f Mo", bytes / (1024.0 * 1024.0))
}

private fun frenchDate(seconds: Long): String =
    DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE).format(Instant.ofEpochSecond(seconds).atZone(ZoneId.of("Europe/Paris")))
