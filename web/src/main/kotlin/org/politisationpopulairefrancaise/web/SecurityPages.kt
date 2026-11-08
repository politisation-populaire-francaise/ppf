package org.politisationpopulairefrancaise.web

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.html.respondHtml
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.code
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.ol
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.strong
import kotlinx.html.ul
import org.politisationpopulairefrancaise.core.identity.PublicKey
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * How security researchers reach the team (S-92, cahier des charges, section 5.7), in order of preference.
 *
 * - [nostrKey]: the public key of the security contact; a researcher writes to it with an encrypted private
 *   message (NIP-17), from the PPF application or any Nostr client. The encrypted channel the specifications
 *   ask for, free, with no third party.
 * - [githubAdvisoryUrl]: the private vulnerability reporting of the code repository, once it is on GitHub.
 * - The policy page itself is always listed last, so that the file never lacks a contact.
 *
 * [publicUrl] is the address of the site, without a trailing slash (`https://…` outside development).
 */
data class SecurityContact(
    val publicUrl: String,
    val nostrKey: PublicKey? = null,
    val githubAdvisoryUrl: String? = null,
) {
    init {
        require(SITE_ADDRESS.matches(publicUrl)) { "The public address is a scheme and a host, with no path nor trailing slash" }
        require(githubAdvisoryUrl == null || GITHUB_ADVISORY.matches(githubAdvisoryUrl)) {
            "The GitHub address must be https://github.com/<owner>/<repository>/security/advisories/new"
        }
    }

    /** True when a researcher has a private way to write to the team, not only the policy page. */
    val hasPrivateChannel: Boolean get() = nostrKey != null || githubAdvisoryUrl != null

    val policyUrl: String get() = "$publicUrl$POLICY_PATH"
    val acknowledgmentsUrl: String get() = "$publicUrl$POLICY_PATH#remerciements"
    val canonicalUrl: String get() = "$publicUrl$SECURITY_TXT_PATH"

    companion object {
        const val POLICY_PATH = "/securite"
        const val SECURITY_TXT_PATH = "/.well-known/security.txt"

        /** How long a served file stays valid: under the year recommended by RFC 9116 (section 2.5.5). */
        val VALIDITY: Duration = Duration.ofDays(180)

        private val SITE_ADDRESS = Regex("https?://[a-z0-9.-]+(:[0-9]{1,5})?")
        private val GITHUB_ADVISORY = Regex("https://github\\.com/[A-Za-z0-9._-]+/[A-Za-z0-9._-]+/security/advisories/new")
    }
}

/**
 * The `security.txt` file (RFC 9116) for [contact], valid [SecurityContact.VALIDITY] from the start of the
 * current UTC day. Its contents change only once a day, which keeps it cacheable and says nothing about
 * when it was asked for. Not signed with OpenPGP: the file is served over HTTPS only, and its Canonical
 * field names the one place it comes from.
 */
fun securityTxt(contact: SecurityContact, now: Instant): String {
    val expires = now.truncatedTo(ChronoUnit.DAYS).plus(SecurityContact.VALIDITY)
    return buildString {
        append("# Politisation Populaire Française: how to report a security vulnerability.\n")
        append("# Signaler une faille de sécurité : ").append(contact.policyUrl).append('\n')
        contact.nostrKey?.let { append("Contact: nostr:").append(it.npub).append('\n') }
        contact.githubAdvisoryUrl?.let { append("Contact: ").append(it).append('\n') }
        append("Contact: ").append(contact.policyUrl).append('\n')
        append("Expires: ").append(DateTimeFormatter.ISO_INSTANT.format(expires)).append('\n')
        append("Preferred-Languages: fr, en\n")
        append("Canonical: ").append(contact.canonicalUrl).append('\n')
        append("Policy: ").append(contact.policyUrl).append('\n')
        append("Acknowledgments: ").append(contact.acknowledgmentsUrl).append('\n')
    }
}

/** The page of the official way to report a vulnerability to the State (verified on 8 November 2026). */
private const val ANSSI_REPORTING_URL = "https://cyber.gouv.fr/signaler-une-vulnerabilite-un-incident"

/** Article L. 2321-4 of the code de la défense on Légifrance (verified on 8 November 2026). */
private const val L2321_4_URL = "https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000033206854"

/**
 * `GET /.well-known/security.txt` (RFC 9116) and `GET /securite`, the page of responsible disclosure (S-92).
 * The page is a draft marked [EXEMPLE] until a lawyer has read it (cahier des charges, sections 5.7 and 9.8).
 */
fun Route.securityPages(contact: SecurityContact, clock: Clock = Clock.systemUTC()) {
    // The root path is the legacy place some tools still look at (RFC 9116, section 3): same file.
    listOf(SecurityContact.SECURITY_TXT_PATH, "/security.txt").forEach { path ->
        get(path) {
            call.response.header(HttpHeaders.CacheControl, "public, max-age=3600")
            call.respondText(securityTxt(contact, clock.instant()), ContentType.Text.Plain.withParameter("charset", "utf-8"))
        }
    }
    get(SecurityContact.POLICY_PATH) {
        call.respondHtml {
            siteLayout(pageTitle = SecurityTexts.TITLE) {
                securityPolicy(contact)
            }
        }
    }
}

/** French texts of the page (the interface language by default), kept together for translation. */
internal object SecurityTexts {
    const val TITLE = "Signaler une faille de sécurité"
    const val DRAFT = "[EXEMPLE] Projet de politique de divulgation responsable, à faire relire par un avocat avant l’ouverture publique."
    const val INTRO = "Vous pensez avoir trouvé une faille dans le site, les applications ou le serveur de Politisation Populaire Française ? Merci de nous la signaler en privé, avant d’en parler publiquement. Nous répondons en français ou en anglais."
    const val NO_CHANNEL = "Le moyen de contact chiffré de l’équipe n’est pas encore publié : il le sera ici avant l’ouverture publique. En attendant, vous pouvez signaler la faille à l’ANSSI (voir plus bas)."
    const val ENGLISH = "English: please report vulnerabilities privately through the channels listed above. We answer in French or English, acknowledge within 72 hours and never take legal action against good-faith research that follows the rules of this page."
}

private fun FlowContent.securityPolicy(contact: SecurityContact) {
    h1 { +SecurityTexts.TITLE }
    p(classes = "notice") { +SecurityTexts.DRAFT }
    p { +SecurityTexts.INTRO }

    section {
        id = "signaler"
        h2 { +"Comment nous écrire" }
        if (contact.hasPrivateChannel) {
            ol {
                contact.nostrKey?.let { key ->
                    li {
                        strong { +"Message privé chiffré. " }
                        +"Écrivez à la clé de l’équipe sécurité depuis l’application PPF ou toute application Nostr (message privé NIP-17, chiffré de bout en bout) : "
                        code { +key.npub }
                        +"."
                    }
                }
                contact.githubAdvisoryUrl?.let { url ->
                    li {
                        strong { +"Signalement privé du dépôt de code. " }
                        a(href = url) {
                            attributes["rel"] = "noopener noreferrer external"
                            +"Ouvrir un signalement privé sur GitHub"
                        }
                        +" (compte GitHub nécessaire ; seule l’équipe le voit)."
                    }
                }
            }
        } else {
            p { +SecurityTexts.NO_CHANNEL }
        }
        p {
            +"Le fichier "
            a(href = SecurityContact.SECURITY_TXT_PATH) { +"security.txt" }
            +" reprend ces moyens de contact, pour les outils des chercheurs."
        }
    }

    section {
        id = "contenu-du-signalement"
        h2 { +"Ce qu’il faut nous dire" }
        ul {
            li { +"Ce que vous avez trouvé et où : adresse de la page, écran de l’application, version." }
            li { +"Comment le reproduire, étape par étape." }
            li { +"Ce qu’un attaquant pourrait en faire, selon vous." }
            li { +"Un pseudonyme, si vous souhaitez être remercié publiquement. Aucune donnée d’identité n’est demandée." }
        }
        p { +"N’y joignez jamais les données personnelles d’autres personnes, même si la faille y donne accès : décrivez seulement ce que vous avez pu voir." }
    }

    section {
        id = "engagements"
        h2 { +"Nos engagements" }
        ul {
            li { +"Accusé de réception sous 72 heures." }
            li { +"Premier avis sur la gravité sous dix jours, puis des nouvelles au moins tous les quinze jours." }
            li { +"Correction d’une faille grave au plus vite, et au plus tard sous 90 jours ; nous vous disons quand elle est déployée." }
            li { +"Publication conjointe : la faille est décrite publiquement après sa correction, à une date convenue avec vous." }
            li { +"Remerciements publics sur cette page, sous le nom ou le pseudonyme de votre choix, ou aucun si vous préférez." }
            li { +"Aucune poursuite contre une recherche menée de bonne foi dans les règles ci-dessous." }
        }
    }

    section {
        id = "regles"
        h2 { +"Les règles d’une recherche de bonne foi" }
        ul {
            li { +"N’accédez qu’à vos propres comptes ou à des comptes créés pour vos essais." }
            li { +"Arrêtez-vous dès que la faille est démontrée : ne lisez, ne copiez, ne modifiez et ne supprimez aucune donnée d’autrui." }
            li { +"Pas de déni de service, pas d’envoi massif, pas de piège tendu aux membres de l’équipe ou aux utilisateurs." }
            li { +"Gardez la faille pour vous jusqu’à sa correction et à la date de publication convenue." }
        }
        p { +"Ne sont pas concernés ici : les contenus publiés par les utilisateurs (signalez-les depuis le contenu lui-même, ils rejoignent la modération) et les services d’autres organisations." }
    }

    section {
        id = "anssi"
        h2 { +"Signaler à l’ANSSI" }
        p {
            +"Vous pouvez aussi signaler une faille à l’Agence nationale de la sécurité des systèmes d’information, qui prévient l’organisation concernée sans vous nommer : la loi protège l’identité de la personne qui signale de bonne foi ("
            a(href = L2321_4_URL) {
                attributes["rel"] = "noopener noreferrer external"
                +"article L. 2321-4 du code de la défense"
            }
            +"). "
            a(href = ANSSI_REPORTING_URL) {
                attributes["rel"] = "noopener noreferrer external"
                +"Signaler une vulnérabilité à l’ANSSI"
            }
            +"."
        }
    }

    section {
        id = "remerciements"
        h2 { +"Remerciements" }
        p { +"Aucun signalement pour l’instant. Les personnes qui nous aident à protéger la plateforme seront remerciées ici." }
    }

    section {
        id = "english"
        attributes["lang"] = "en"
        h2 { +"In English" }
        p { +SecurityTexts.ENGLISH }
    }
}
