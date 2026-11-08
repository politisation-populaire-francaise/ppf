package org.politisationpopulairefrancaise.web

import io.ktor.http.HttpStatusCode
import io.ktor.http.parseQueryString
import io.ktor.server.html.respondHtml
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveChannel
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.readRemaining
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.code
import kotlinx.html.dd
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.strong
import kotlinx.html.textArea
import kotlinx.html.ul
import kotlinx.io.readByteArray
import org.politisationpopulairefrancaise.core.comprendre.ComprendreLinks
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.CheckOutcome
import org.politisationpopulairefrancaise.core.editorial.CheckQuery
import org.politisationpopulairefrancaise.core.editorial.CheckResult
import org.politisationpopulairefrancaise.core.editorial.ContentCheck
import org.politisationpopulairefrancaise.core.editorial.OfficialSite
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.VoirLinks

/** Where the page checks a content: the server's copy of what PPF publishes (S-141). */
fun interface ContentVerifier {
    suspend fun check(query: CheckQuery): CheckResult
}

/** A code, an identifier or a link fits in this many characters; a longer text is a JSON to post. */
private const val MAX_QUERY_CHARS: Int = 300

/** A pasted JSON, form-encoded (each special character may take 9 bytes once encoded). */
private const val MAX_BODY_BYTES: Long = 9L * ContentCheck.MAX_INPUT_CHARS

/**
 * « Vérifier un contenu PPF » (S-141, M33; docs/integration/export-ouvert.md, section 6), readable without
 * script: `/verifier?q=<code, identifier or link>`, `/v/<code>` (the address printed on the images of PPF)
 * and `POST /verifier` with a pasted signed content (`contenu`), read through a bounded channel. Nothing of
 * what is given is kept, logged or sent anywhere; a pasted content is never written back into the page.
 */
fun Route.verifyPages(verifier: ContentVerifier) {
    get("/verifier") {
        val input = call.request.queryParameters["q"].orEmpty().take(MAX_QUERY_CHARS)
        call.answer(verifier, input, shown = input)
    }
    get("/v/{code}") {
        val code = call.parameters["code"].orEmpty().take(MAX_QUERY_CHARS)
        call.answer(verifier, code, shown = code)
    }
    post("/verifier") {
        val declared = call.request.contentLength()
        if (declared != null && declared > MAX_BODY_BYTES) return@post call.respondHtml(HttpStatusCode.PayloadTooLarge) { verifyDocument("", null, tooLarge = true) }
        val bytes = call.receiveChannel().readRemaining(MAX_BODY_BYTES + 1).readByteArray()
        if (bytes.size > MAX_BODY_BYTES) return@post call.respondHtml(HttpStatusCode.PayloadTooLarge) { verifyDocument("", null, tooLarge = true) }
        val pasted = runCatching { parseQueryString(bytes.decodeToString())["contenu"] }.getOrNull().orEmpty()
        call.answer(verifier, pasted, shown = "")
    }
}

private suspend fun RoutingCall.answer(verifier: ContentVerifier, input: String, shown: String) {
    val result = if (input.isBlank()) null else verifier.check(CheckQuery.parse(input))
    respondHtml { verifyDocument(shown, result) }
}

/** The page itself; separate from the routes so that tests render it without a server. */
fun HTML.verifyDocument(shown: String, result: CheckResult?, tooLarge: Boolean = false) {
    siteLayout(pageTitle = "Vérifier un contenu") {
        h1 { +"Vérifier un contenu" }
        p {
            +"Vous avez reçu une image, un lien ou un texte présenté comme venant de Politisation Populaire Française ? Vérifiez ici qu’il a bien été publié par son équipe et qu’il n’a pas été modifié. Rien n’est enregistré : ni ce que vous donnez, ni votre visite."
        }
        if (tooLarge) p(classes = "verdict verdict-bad") { +"Ce texte est trop long pour être un contenu de PPF (64 Ko au plus)." }
        result?.let { verdict(it) }
        form(action = "/verifier", method = FormMethod.get, classes = "search-form") {
            label {
                htmlFor = "q"
                +"Code de vérification, identifiant ou lien"
            }
            input(type = InputType.text, name = "q") {
                id = "q"
                value = shown
                attributes["maxlength"] = MAX_QUERY_CHARS.toString()
                attributes["autocomplete"] = "off"
                attributes["spellcheck"] = "false"
            }
            button(type = ButtonType.submit) { +"Vérifier" }
        }
        form(action = "/verifier", method = FormMethod.post, classes = "verify-form") {
            label {
                htmlFor = "contenu"
                +"Ou collez le contenu signé (le texte JSON d’un fichier enregistré depuis PPF)"
            }
            textArea(rows = "6") {
                id = "contenu"
                name = "contenu"
                attributes["spellcheck"] = "false"
            }
            button(type = ButtonType.submit) { +"Vérifier ce contenu" }
        }
        section {
            attributes["aria-labelledby"] = "comment"
            h2 { id = "comment"; +"Comment ça marche" }
            p {
                +"Chaque contenu de PPF est signé par une clé de son équipe : sa signature ne correspond plus dès qu’une seule lettre change. Le code de vérification, imprimé sur les images partagées par PPF, est le début de l’identifiant d’une version : il sert à retrouver l’original, que vous comparez avec ce que vous avez reçu."
            }
            p { +"Les seules adresses de PPF sont :" }
            ul(classes = "link-list") { OfficialSite.DOMAINS.sorted().forEach { li { code { +it } } } }
            p { +"Une autre adresse n’est pas PPF, même si la page l’affirme." }
        }
    }
}

private fun FlowContent.verdict(result: CheckResult) {
    val (tone, title, text) = when (result.outcome) {
        CheckOutcome.AUTHENTIC_CURRENT -> Triple("ok", "Authentique", "Ce contenu a bien été publié par Politisation Populaire Française, et c’est sa version actuelle.")
        CheckOutcome.AUTHENTIC_OUTDATED -> Triple("warn", "Authentique, mais ancien", "Ce contenu a bien été publié par PPF, mais il a été mis à jour depuis : lisez la version actuelle avant de le citer.")
        CheckOutcome.AUTHENTIC_UNLISTED -> Triple("warn", "Signé par PPF, mais pas servi ici", "La signature est celle d’une clé de PPF, mais ce site ne sert pas ce contenu : il a pu être retiré, ou ce site ne l’a pas encore reçu.")
        CheckOutcome.NOT_PPF_KEY -> Triple("bad", "Pas un contenu de PPF", "Ce contenu est signé par une autre clé que celles de PPF : ne le présentez pas comme venant de PPF.")
        CheckOutcome.ALTERED -> Triple("bad", "Modifié ou abîmé", "Le texte ne correspond plus à sa signature : il a été changé après sa publication.")
        CheckOutcome.UNKNOWN -> Triple("bad", "Inconnu de PPF", "Aucun contenu de PPF n’a ce code ou cet identifiant. Vérifiez que vous l’avez recopié sans erreur.")
        CheckOutcome.OFFICIAL_LINK -> Triple("ok", "Lien officiel", "Ce lien mène bien à un site de PPF (${result.host}). Ouvrez-le pour lire la version actuelle.")
        CheckOutcome.UNOFFICIAL_LINK -> Triple("bad", "Lien qui n’est pas de PPF", "Attention : ce lien mène à ${result.host}, qui n’est pas un site de PPF, même si la page l’affirme.")
        CheckOutcome.UNREADABLE -> Triple("bad", "Rien à vérifier", "Donnez un code de vérification (16 caractères), un identifiant (64 caractères), un lien, ou collez le contenu signé.")
    }
    section(classes = "verdict verdict-$tone") {
        attributes["aria-labelledby"] = "verdict"
        attributes["role"] = "status"
        h2 { id = "verdict"; +title }
        p { +text }
        result.event?.takeIf { result.outcome.isAuthentic() }?.let { facts(it, "Version vérifiée") }
        result.current?.takeIf { result.outcome == CheckOutcome.AUTHENTIC_OUTDATED }?.let { facts(it, "Version actuelle") }
    }
}

private fun CheckOutcome.isAuthentic(): Boolean =
    this == CheckOutcome.AUTHENTIC_CURRENT || this == CheckOutcome.AUTHENTIC_OUTDATED || this == CheckOutcome.AUTHENTIC_UNLISTED

/** What the person compares: title, dates, code, fingerprint, and the page of the content when it has one. */
private fun FlowContent.facts(event: SignedEvent, label: String) {
    val article = Article.from(event)
    dl(classes = "facts") {
        dt { +label }
        dd { strong { +(article?.title ?: "Contenu de type ${event.kind}") } }
        article?.publishedAt?.let {
            dt { +"Publié le" }
            dd { +CivilDate.fromEpochSeconds(it).toFrench() }
        }
        dt { +"Version du" }
        dd { +CivilDate.fromEpochSeconds(event.createdAt).toFrench() }
        dt { +"Code de vérification" }
        dd { code { +ContentCheck.codeOf(event) } }
        dt { +"Empreinte SHA-256" }
        dd { code { +event.id } }
        article?.let { VoirLinks.pathOf(it) ?: ComprendreLinks.pathOf(it) }?.let { path ->
            dt { +"Page" }
            dd { a(href = path) { +"Lire ce contenu sur le site" } }
        }
    }
}
