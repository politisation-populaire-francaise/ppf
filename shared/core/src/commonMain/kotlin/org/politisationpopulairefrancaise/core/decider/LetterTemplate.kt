package org.politisationpopulairefrancaise.core.decider

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.elus.ElectedOfficial
import org.politisationpopulairefrancaise.core.elus.Mandate
import org.politisationpopulairefrancaise.core.elus.MyElus
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.NextStep
import org.politisationpopulairefrancaise.core.voir.Source
import org.politisationpopulairefrancaise.core.voir.VoirTags
import org.politisationpopulairefrancaise.core.voir.VoirTags.at
import org.politisationpopulairefrancaise.core.voir.VoirTags.fields
import org.politisationpopulairefrancaise.core.voir.VoirTags.links
import org.politisationpopulairefrancaise.core.voir.VoirTags.nextSteps
import org.politisationpopulairefrancaise.core.voir.VoirTags.sources
import org.politisationpopulairefrancaise.core.voir.VoirTags.value

/**
 * Who a letter is addressed to (DE-09): a kind of mandate of the directory of elected officials (D-047), or the mayor
 * of the commune. [key] is stored in the signed contents and must not change.
 */
enum class LetterRecipient(val key: String) {
    MAIRE("maire"),
    DEPUTE("depute"),
    SENATEUR("senateur"),
    DEPARTEMENTAL("departemental"),
    REGIONAL("regional"),
    EUROPEEN("europeen"),
    ;

    /** Whether [official] holds this mandate (the mayor: the member of the municipal council whose function says so). */
    fun matches(official: ElectedOfficial): Boolean = when (this) {
        MAIRE -> official.mandate == Mandate.MUNICIPAL && official.function?.trim()?.lowercase() == "maire"
        DEPUTE -> official.mandate == Mandate.DEPUTE
        SENATEUR -> official.mandate == Mandate.SENATEUR
        DEPARTEMENTAL -> official.mandate == Mandate.DEPARTEMENTAL
        REGIONAL -> official.mandate == Mandate.REGIONAL || official.mandate == Mandate.COLLECTIVITE
        EUROPEEN -> official.mandate == Mandate.EUROPEEN
    }

    companion object {
        fun fromKey(key: String?): LetterRecipient? = entries.firstOrNull { it.key == key?.trim() }
    }
}

/** Where to find the official contact of the elected officials of one kind: `recipient | label | page (https)`, written by the team. */
data class OfficialContact(val recipient: LetterRecipient, val label: String, val url: String)

/**
 * A model letter to one's elected officials (DE-09, `ppf-type` = `modele-courrier`, `docs/integration/interpeller-ses-elus.md`),
 * written by the team: who it is for, its subject, its text with the words to fill in, and where the official contacts are.
 * The application fills it on the device with the name of one elected official of the commune chosen in « Mes élus »; the
 * person edits it and sends it herself or himself. PPF sends nothing and keeps nothing of it.
 */
data class LetterTemplate(
    val article: Article,
    val recipients: List<LetterRecipient>,
    val subject: String,
    /** The letter, with [ELU], [COMMUNE] and [DATE] to fill in; the rest is the person's to change. */
    val text: String,
    val contacts: List<OfficialContact>,
    /** The bills of « Voir » the letter is about (the législomètre opens it before a vote). */
    val texts: List<ContentAddress>,
    val sources: List<Source>,
    val nextSteps: List<NextStep>,
) {
    val address: ContentAddress get() = article.address
    val title: String get() = article.title

    /** The elected officials of the commune chosen on the device to whom this letter is addressed, in the order of [recipients]. */
    fun recipientsAmong(mine: MyElus?): List<ElectedOfficial> {
        val officials = mine?.officials?.levels.orEmpty().flatMap { level -> level.groups.flatMap { it.officials } }
        return recipients.flatMap { recipient -> officials.filter(recipient::matches) }.distinct()
    }

    /**
     * The letter filled in for [official] (its name as the directory gives it, never a guessed civility), [commune] and
     * [today]; what is unknown stays as a visible mark to replace.
     */
    fun fill(official: ElectedOfficial?, commune: String?, today: CivilDate): String =
        text.replace(ELU, official?.displayName ?: UNKNOWN_ELU)
            .replace(COMMUNE, commune ?: UNKNOWN_COMMUNE)
            .replace(DATE, today.toFrench())

    /** What makes the signing tool hesitate: a mark the application does not fill, a recipient without its official contact. */
    fun warnings(): List<String> = buildList {
        Regex("\\{[^}]*}").findAll(text).map { it.value }.distinct().filter { it !in KNOWN }.forEach { add("unknown mark $it in the letter") }
        recipients.filter { r -> contacts.none { it.recipient == r } }.forEach { add("no official contact for ${it.key}") }
    }

    companion object {
        const val ELU: String = "{elu}"
        const val COMMUNE: String = "{commune}"
        const val DATE: String = "{date}"
        private val KNOWN = setOf(ELU, COMMUNE, DATE)

        /** Left in the letter when no commune is chosen on the device: the person writes the name. */
        const val UNKNOWN_ELU: String = "[Nom de l’élu]"
        const val UNKNOWN_COMMUNE: String = "[Votre commune]"

        fun from(article: Article): LetterTemplate? {
            if (article.type != ArticleType.MODELE_COURRIER) return null
            val event = article.event
            val recipients = event.fields(DeciderTags.RECIPIENT).mapNotNull { LetterRecipient.fromKey(it.at(0)) }.distinct()
            if (recipients.isEmpty()) return null
            val subject = event.value(DeciderTags.SUBJECT) ?: return null
            val text = article.body.trim().takeIf { it.isNotEmpty() } ?: return null
            val contacts = event.fields(DeciderTags.CONTACT).mapNotNull { f ->
                val recipient = LetterRecipient.fromKey(f.at(0)) ?: return@mapNotNull null
                val label = f.at(1)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val url = f.at(2)?.trim()?.takeIf { PublicConsultation.isOfficialPage(it) } ?: return@mapNotNull null
                OfficialContact(recipient, label, url)
            }
            return LetterTemplate(
                article = article,
                recipients = recipients,
                subject = subject,
                text = text,
                contacts = contacts,
                texts = event.links(VoirTags.ROLE_TEXT),
                sources = event.sources(),
                nextSteps = event.nextSteps(),
            )
        }
    }
}
