package org.politisationpopulairefrancaise.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.html.HTML
import kotlinx.html.h1
import kotlinx.html.p
import org.politisationpopulairefrancaise.web.comprendre.ComprendreRenderer

/**
 * One legal text of the site (mentions légales, confidentialité, conditions d'utilisation, charte,
 * signalement). Its Markdown lives in `content/legal/` (outside the signed contents) and is copied
 * into the resources of the site under `legal/` at build time.
 */
data class LegalPage(val path: String, val file: String, val footerLabel: String)

/** A legal text read from its file: the title and date of its front matter, and its body. */
data class LegalText(val title: String, val updatedAt: String?, val body: String)

object LegalPages {
    val ALL: List<LegalPage> = listOf(
        LegalPage("/mentions-legales", "mentions-legales.md", "Mentions légales"),
        LegalPage("/confidentialite", "politique-de-confidentialite.md", "Confidentialité"),
        LegalPage("/conditions-utilisation", "conditions-d-utilisation.md", "Conditions d’utilisation"),
        LegalPage("/charte", "charte-de-contenu.md", "Charte de contenu"),
        LegalPage("/signaler", "procedure-de-signalement.md", "Signaler un contenu"),
    )

    /** The text of [page] from the resources of the site, or null when the file is missing. */
    fun load(page: LegalPage): LegalText? =
        LegalPages::class.java.classLoader.getResourceAsStream("legal/${page.file}")
            ?.use { parse(it.readBytes().decodeToString()) }

    /**
     * Splits an optional front matter (`---` lines, `key: value`) from the body. The title is the
     * `title` field; without it, the first `# ` heading of the body, which is then removed (the page
     * writes the title itself as its only h1).
     */
    fun parse(source: String): LegalText {
        val text = source.replace("\r\n", "\n").removePrefix("﻿")
        var fields = emptyMap<String, String>()
        var body = text
        if (text.startsWith("---\n")) {
            val end = text.indexOf("\n---", 4)
            if (end > 0) {
                fields = text.substring(4, end).lines()
                    .filter { ':' in it }
                    .associate { it.substringBefore(':').trim() to it.substringAfter(':').trim() }
                body = text.substring(end + 4).removePrefix("\n")
            }
        }
        val lines = body.lines()
        val headingIndex = lines.indexOfFirst { it.startsWith("# ") }
        val title = fields["title"]?.takeIf { it.isNotBlank() }
            ?: lines.getOrNull(headingIndex)?.removePrefix("# ")?.trim()
            ?: "Informations légales"
        val withoutHeading = if (headingIndex >= 0) lines.filterIndexed { i, _ -> i != headingIndex } else lines
        return LegalText(title, fields["updated_at"], withoutHeading.joinToString("\n").trim())
    }
}

/** Renders a legal text in the layout of the site. */
fun HTML.legalPage(text: LegalText) {
    siteLayout(pageTitle = text.title) {
        h1 { +text.title }
        text.updatedAt?.let { p(classes = "legal-updated") { +"Version du ${frenchDate(it)}" } }
        ComprendreRenderer().markdown(this, text.body)
    }
}

private val MONTHS = listOf(
    "janvier", "février", "mars", "avril", "mai", "juin",
    "juillet", "août", "septembre", "octobre", "novembre", "décembre",
)

/** « 11 novembre 2026 » for « 2026-11-11 »; any other text is kept as it is. */
internal fun frenchDate(iso: String): String {
    val match = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").matchEntire(iso.trim()) ?: return iso
    val (year, month, day) = match.destructured
    val name = MONTHS.getOrNull(month.toInt() - 1) ?: return iso
    val dayText = if (day.toInt() == 1) "1er" else day.toInt().toString()
    return "$dayText $name $year"
}

/** The routes of the legal texts. A missing file answers 404, never an empty page. */
fun Route.legalPages() {
    for (page in LegalPages.ALL) {
        get(page.path) {
            val text = LegalPages.load(page)
            if (text == null) call.respond(HttpStatusCode.NotFound) else call.respondHtml { legalPage(text) }
        }
    }
}
