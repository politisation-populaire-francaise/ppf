package org.politisationpopulairefrancaise.web.comprendre

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.withCharset
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.politisationpopulairefrancaise.core.comprendre.ComprendreLinks
import java.security.MessageDigest

/**
 * The service worker of the site (D-091): it keeps the practical guides, the pages of "Comprendre" and
 * the files of the site on the device, so that the guides stay readable without network. It is only a
 * plus: every page is complete without script, and a browser without service workers reads the same pages
 * online (and can save the page « Lire hors ligne », which holds every guide).
 *
 * Served at the root (`/sw.js`) so that its scope is the whole site. Its list of pages is written by the
 * server from the catalog, and its version follows the guides and the files of the site: when a guide is
 * corrected, the browser installs the new version and keeps the new copy.
 *
 * Rules: same-origin GET requests only; pages are asked to the network first and the kept copy is used
 * only when the network fails (a correction is always seen online); the files of `/static/` are kept
 * first. Nothing is ever sent: the worker only keeps what the site already shows to everyone. Pages are
 * kept only under [KEPT_PREFIXES], never the API.
 */
object OfflineWorker {
    const val PATH: String = "/sw.js"

    /** The pages the worker keeps a copy of when they are visited. */
    val KEPT_PREFIXES: List<String> = listOf(ComprendreLinks.HOME, "/voir")

    /** The files of the site kept at installation. */
    val STATIC_FILES: List<String> = listOf(
        "/static/site.css",
        "/static/fonts/atkinson_hyperlegible_regular.ttf",
        "/static/fonts/atkinson_hyperlegible_bold.ttf",
        "/static/icon-192.png",
        "/static/manifest.webmanifest",
        PWA_SCRIPT,
    )

    /** The pages kept at installation: the home of "Comprendre", the guides, the page that holds them all. */
    fun precache(catalog: ComprendreCatalog): List<String> = buildList {
        add(ComprendreLinks.HOME)
        add(ComprendreLinks.listPath(ComprendreLinks.FICHES))
        catalog.fiches.forEach { fiche -> ComprendreLinks.pathOf(fiche)?.let(::add) }
        add(ComprendreLinks.OFFLINE)
        addAll(STATIC_FILES)
    }

    /** Changes with the guides, the glossary they link and the files of the site. */
    fun version(catalog: ComprendreCatalog): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(staticFingerprint)
        catalog.fiches.forEach { digest.update("${it.slug}:${it.event.id}\n".encodeToByteArray()) }
        catalog.glossary.entries.forEach { digest.update("${it.slug}:${it.article.event.id}\n".encodeToByteArray()) }
        return "ppf-" + digest.digest().take(8).joinToString("") { "%02x".format(it) }
    }

    private val staticFingerprint: ByteArray by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
        (STATIC_FILES.map { it.removePrefix("/") } + "comprendre/sw-template.js").forEach { path ->
            OfflineWorker::class.java.classLoader.getResourceAsStream(path)?.use { digest.update(it.readBytes()) }
        }
        digest.digest()
    }

    private val template: String by lazy {
        OfflineWorker::class.java.classLoader.getResourceAsStream("comprendre/sw-template.js")!!.use { it.readBytes().decodeToString() }
    }

    /** The script of the worker for [catalog]: the template with its version and its lists, written as JSON. */
    fun script(catalog: ComprendreCatalog): String = template
        .replace("\"__VERSION__\"", JsonPrimitive(version(catalog)).toString())
        .replace("[\"__PRECACHE__\"]", JsonArray(precache(catalog).map(::JsonPrimitive)).toString())
        .replace("[\"__KEPT__\"]", JsonArray(KEPT_PREFIXES.map(::JsonPrimitive)).toString())
        .replace("\"__FALLBACK__\"", JsonPrimitive(ComprendreLinks.OFFLINE).toString())

    suspend fun respond(call: ApplicationCall, catalog: ComprendreCatalog) {
        // Always asked again to the server, so that a new version is found at once.
        call.response.header(HttpHeaders.CacheControl, "no-cache")
        call.respondText(script(catalog), ContentType.Application.JavaScript.withCharset(Charsets.UTF_8))
    }
}
