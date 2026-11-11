package org.politisationpopulairefrancaise.web.decider

import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.ThScope
import kotlinx.html.a
import kotlinx.html.article
import kotlinx.html.caption
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h3
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.strong
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import kotlinx.html.ul
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.decider.ComparatorGrid
import org.politisationpopulairefrancaise.core.decider.ComparatorSheet
import org.politisationpopulairefrancaise.core.decider.ConsultationKind
import org.politisationpopulairefrancaise.core.decider.DeadlineKind
import org.politisationpopulairefrancaise.core.decider.ElectionCalendar
import org.politisationpopulairefrancaise.core.decider.ElectionStatus
import org.politisationpopulairefrancaise.core.decider.LetterRecipient
import org.politisationpopulairefrancaise.core.decider.LetterTemplate
import org.politisationpopulairefrancaise.core.decider.PublicConsultation
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.web.comprendre.ComprendreRenderer
import org.politisationpopulairefrancaise.web.siteLayout
import org.politisationpopulairefrancaise.web.voir.BadgeTone
import org.politisationpopulairefrancaise.web.voir.badge
import org.politisationpopulairefrancaise.web.voir.correctionsSection
import org.politisationpopulairefrancaise.web.voir.date
import org.politisationpopulairefrancaise.web.voir.externalLink
import org.politisationpopulairefrancaise.web.voir.originLine
import org.politisationpopulairefrancaise.web.voir.pageSection
import org.politisationpopulairefrancaise.web.voir.sourcesSection

/**
 * The other pages of "Décider" on the site, readable without script (MVP 1): the comparator of the candidates, the
 * electoral calendar, the official consultations and the model letters. Each at a stable address under
 * [DeciderLinks.HOME]; voting, reminders and filling in a letter happen in the application, as one line says.
 */
internal object DeciderMoreLinks {
    const val COMPARATOR: String = "comparateur"
    const val ELECTIONS: String = "elections"
    const val CONSULTATIONS: String = "consultations"
    const val LETTERS: String = "courriers"

    fun comparator(sheet: ComparatorSheet) = "${DeciderLinks.HOME}/$COMPARATOR/${sheet.article.slug}"
    fun election(election: ElectionCalendar) = "${DeciderLinks.HOME}/$ELECTIONS/${election.article.slug}"
    fun consultation(consultation: PublicConsultation) = "${DeciderLinks.HOME}/$CONSULTATIONS/${consultation.article.slug}"
    fun letter(letter: LetterTemplate) = "${DeciderLinks.HOME}/$LETTERS/${letter.article.slug}"
}

private val THEMES: Map<String, String> = mapOf(
    "pouvoir-d-achat" to "Pouvoir d’achat", "travail" to "Travail", "sante" to "Santé", "education" to "Éducation",
    "logement" to "Logement", "transports" to "Transports", "environnement" to "Environnement",
    "services-publics" to "Services publics", "democratie" to "Démocratie", "justice" to "Justice et libertés",
    "societe" to "Société", "fiscalite" to "Fiscalité", "agriculture" to "Agriculture", "numerique" to "Numérique",
    "culture" to "Culture",
)

private fun theme(id: String): String = THEMES[id] ?: id

private val CONSULTATION_KINDS: Map<ConsultationKind, String> = mapOf(
    ConsultationKind.OPEN_ONLINE to "Consultation ouverte en ligne",
    ConsultationKind.ENVIRONMENT_ONLINE to "Participation du public (environnement)",
    ConsultationKind.PUBLIC_INQUIRY to "Enquête publique",
    ConsultationKind.PUBLIC_DEBATE to "Débat public",
    ConsultationKind.LOCAL_VOTE to "Consultation locale des électeurs",
)

private val DEADLINES: Map<DeadlineKind, String> = mapOf(
    DeadlineKind.REGISTRATION_TOWN_HALL to "Inscription sur les listes électorales en mairie, jusqu’au",
    DeadlineKind.REGISTRATION_ONLINE to "Inscription sur les listes électorales en ligne, jusqu’au",
    DeadlineKind.PROXY to "Procuration conseillée avant le",
    DeadlineKind.CANDIDACIES to "Dépôt des candidatures, jusqu’au",
)

private val RECIPIENTS: Map<LetterRecipient, String> = mapOf(
    LetterRecipient.MAIRE to "Maire", LetterRecipient.DEPUTE to "Député", LetterRecipient.SENATEUR to "Sénateur",
    LetterRecipient.DEPARTEMENTAL to "Conseiller départemental", LetterRecipient.REGIONAL to "Conseiller régional",
    LetterRecipient.EUROPEEN to "Député européen",
)

private fun time(hour: Int, minute: Int) = if (minute == 0) "$hour h" else "$hour h ${minute.toString().padStart(2, '0')}"

// --------------------------------------------------------------------------------------------- Cards of the home

internal fun FlowContent.comparatorCard(sheet: ComparatorSheet) {
    article(classes = "card") {
        h3 { a(href = DeciderMoreLinks.comparator(sheet)) { +sheet.article.title } }
        p { +"${sheet.election.label} : ${sheet.candidates.size} candidat(s), ${sheet.themes.size} thème(s), par ordre alphabétique." }
    }
}

internal fun FlowContent.electionCard(election: ElectionCalendar) {
    article(classes = "card") {
        h3 { a(href = DeciderMoreLinks.election(election)) { +election.article.title } }
        election.firstDay?.let { p { +"Premier tour le " ; date(it) ; +"." } }
    }
}

internal fun FlowContent.consultationCard(consultation: PublicConsultation, today: CivilDate) {
    article(classes = "card") {
        h3 { a(href = DeciderMoreLinks.consultation(consultation)) { +consultation.title } }
        p {
            if (consultation.isOpen(today)) badge("Ouverte", BadgeTone.BRAND) else if (consultation.isUpcoming(today)) badge("À venir") else badge("Close")
            +" ${consultation.organizer}, jusqu’au "
            date(consultation.closesOn)
            +"."
        }
    }
}

internal fun FlowContent.letterCard(letter: LetterTemplate) {
    article(classes = "card") {
        h3 { a(href = DeciderMoreLinks.letter(letter)) { +letter.title } }
        p { +"Pour : ${letter.recipients.joinToString(", ") { RECIPIENTS.getValue(it) }}." }
    }
}

// --------------------------------------------------------------------------------------------- Pages

fun HTML.comparatorDocument(grid: ComparatorGrid) {
    val sheet = grid.sheet
    siteLayout(pageTitle = sheet.article.title, currentSpace = Space.DECIDER) {
        p(classes = "breadcrumb") { a(href = DeciderLinks.HOME) { +"Décider" } }
        h1 { +sheet.article.title }
        p(classes = "lead") { +"${sheet.election.label} : la position de chaque candidat, thème par thème, avec une courte citation et sa source. Les candidats sont rangés par ordre alphabétique ; une case vide dit qu’aucune position n’a été publiée." }
        div(classes = "table-scroll") {
            table {
                caption { +"Positions des candidats par thème" }
                thead {
                    tr {
                        th(scope = ThScope.col) { +"Candidat" }
                        grid.themes.forEach { th(scope = ThScope.col) { +theme(it) } }
                    }
                }
                tbody {
                    grid.rows.forEach { row ->
                        tr {
                            th(scope = ThScope.row) { +row.name }
                            grid.themes.forEach { id ->
                                td {
                                    val position = row.cell(id)?.position
                                    if (position == null) {
                                        +"Aucune position publiée"
                                    } else {
                                        p { +position.summary }
                                        p(classes = "muted") { +"« ${position.quote.text} » (" ; externalLink(position.quote.url, "source") ; +", " ; date(position.quote.date) ; +")" }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (grid.aside.isNotEmpty()) p(classes = "muted") { +"${grid.aside.size} position(s) mise(s) à part (hors des candidats ou des thèmes de cette fiche) : jamais montrée(s) dans le tableau." }
        sourcesSection(sheet.sources)
        correctionsSection(sheet.corrections)
        originLine(sheet.article)
    }
}

fun HTML.electionDocument(election: ElectionCalendar) {
    siteLayout(pageTitle = election.article.title, currentSpace = Space.DECIDER) {
        p(classes = "breadcrumb") { a(href = DeciderLinks.HOME) { +"Décider" } }
        h1 { +election.article.title }
        p { badge(if (election.status == ElectionStatus.CONVENED) "Convoquée par décret" else "Annoncée, en attente du décret de convocation") }
        election.decree?.let { decree -> p { +"Décret n° ${decree.number} du " ; date(decree.date) ; +" : " ; externalLink(decree.url, "texte officiel") } }
        pageSection("tours", "Les tours") {
            ul {
                election.rounds.forEach { round ->
                    li { strong { +"Tour ${round.number} : " } ; date(round.day) ; +", fermeture des bureaux à ${time(round.closingHour, round.closingMinute)}" }
                }
            }
        }
        if (election.deadlines.isNotEmpty()) {
            pageSection("dates-limites", "Les dates limites") {
                ul { election.deadlines.forEach { deadline -> li { +"${DEADLINES.getValue(deadline.kind)} " ; date(deadline.date) } } }
            }
        }
        p(classes = "notice") { +"Les rappels (inscription, procuration, veille du vote) se programment dans l’application PPF, sur cet appareil seulement." }
        ComprendreRenderer().markdown(this, election.article.body, headingOffset = 1, withGlossary = false)
        sourcesSection(election.sources)
        originLine(election.article)
    }
}

fun HTML.consultationDocument(consultation: PublicConsultation, today: CivilDate) {
    siteLayout(pageTitle = consultation.title, currentSpace = Space.DECIDER) {
        p(classes = "breadcrumb") { a(href = DeciderLinks.HOME) { +"Décider" } }
        h1 { +consultation.title }
        p {
            badge(CONSULTATION_KINDS.getValue(consultation.kind))
            +" Organisée par ${consultation.organizer}, du "
            date(consultation.opensOn)
            +" au "
            date(consultation.closesOn)
            +" inclus."
        }
        if (!consultation.isOver(today)) {
            p(classes = "notice") { +"On répond sur le site officiel, jamais sur PPF : " ; externalLink(consultation.officialUrl, consultation.officialHost) }
        } else {
            p(classes = "muted") { +"Cette consultation est close." }
        }
        ComprendreRenderer().markdown(this, consultation.article.body, headingOffset = 1, withGlossary = false)
        sourcesSection(consultation.sources)
        correctionsSection(consultation.corrections)
        originLine(consultation.article)
    }
}

fun HTML.letterDocument(letter: LetterTemplate) {
    siteLayout(pageTitle = letter.title, currentSpace = Space.DECIDER) {
        p(classes = "breadcrumb") { a(href = DeciderLinks.HOME) { +"Décider" } }
        h1 { +letter.title }
        p { +"Pour : ${letter.recipients.joinToString(", ") { RECIPIENTS.getValue(it) }}. Objet : ${letter.subject}" }
        p(classes = "notice") { +"PPF n’envoie rien : copiez ce modèle, complétez-le et envoyez-le vous-même. Dans l’application, il se remplit sur votre appareil avec vos élus." }
        letter.text.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }.forEach { paragraph -> p(classes = "letter") { +paragraph } }
        if (letter.contacts.isNotEmpty()) {
            pageSection("contacts", "Trouver le bon contact") {
                ul { letter.contacts.forEach { contact -> li { +"${RECIPIENTS.getValue(contact.recipient)} : " ; externalLink(contact.url, contact.label) } } }
            }
        }
        sourcesSection(letter.sources)
        originLine(letter.article)
    }
}
