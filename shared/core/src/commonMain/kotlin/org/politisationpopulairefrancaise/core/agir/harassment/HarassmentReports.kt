package org.politisationpopulairefrancaise.core.agir.harassment

/** What the facts may be, as the person ticks it (the prosecutor alone gives the legal qualification). */
enum class Offence {
    /** Moral harassment, committed online among others (code pénal, article 222-33-2-2, version in force since 23 March 2024). */
    HARASSMENT,

    /** Revealing information that identifies or locates a person to expose her to a direct risk (code pénal, article 223-1-1, same date). */
    DOXXING,
}

/**
 * The four elements a notice must be able to contain under the Digital Services Act, article 16,
 * paragraph 2 (Regulation (EU) 2022/2065, French text of the Official Journal L 277 of 27 October
 * 2022, read on the Publications Office on 9 November 2026).
 */
enum class DsaElement {
    /** a) a sufficiently substantiated explanation of why the content is illegal. */
    EXPLANATION,

    /** b) the exact electronic location, such as the exact URL, and what else identifies it. */
    LOCATION,

    /** c) the name and email address of the person notifying (the exception of point c concerns child sexual abuse only). */
    NAME_AND_EMAIL,

    /** d) a statement that the person believes in good faith that the notice is accurate and complete. */
    GOOD_FAITH,
}

/**
 * What the person types around her evidence to fill a template, in memory only: her name and
 * email address are never kept by PPF.
 */
data class ReportInput(
    val evidence: List<Evidence> = emptyList(),
    /** Why the content is illegal, in her words. */
    val explanation: String = "",
    val offences: Set<Offence> = emptySet(),
    val name: String = "",
    val email: String = "",
    /** Ticked by the person herself: the template never states her good faith for her. */
    val goodFaith: Boolean = false,
    /** The suspected author for the complaint, if she knows; otherwise the complaint is against X. */
    val author: String = "",
    /** The town of the court and of the letter. */
    val city: String = "",
)

/** Official addresses the kit opens (checked on 9 November 2026). */
object HarassmentLinks {
    /** PHAROS, the official portal of the Ministry of the Interior for illegal public content (masecurite.interieur.gouv.fr, page updated on 8 September 2026). */
    const val PHAROS: String = "https://www.internet-signalement.gouv.fr"

    /** Service-public.gouv.fr, « Porter plainte auprès du procureur de la République » (model letter R11469, verified on 12 December 2025). */
    const val COMPLAINT_MODEL: String = "https://www.service-public.gouv.fr/particuliers/vosdroits/R11469"

    /** Service-public.gouv.fr, « Signaler un cyberharcèlement aux forces de l'ordre » (R57286, written exchange with a police officer or a gendarme, verified on 7 August 2026). */
    const val POLICE_CHAT: String = "https://www.service-public.gouv.fr/particuliers/vosdroits/R57286"

    /** Service-public.gouv.fr, « Cyberharcèlement (harcèlement sur internet) » (F32239, verified on 7 August 2026). */
    const val CYBERHARASSMENT: String = "https://www.service-public.gouv.fr/particuliers/vosdroits/F32239"

    /** The fiche of the kit, bundled with the application (content/fiches). */
    const val FICHE_SLUG: String = "harcelement-en-ligne-et-divulgation"
}

/**
 * The templates of the kit (AG-59), filled from the evidence and copied or shared by the person
 * herself: PPF never sends them. They are documents for French authorities and platforms serving
 * France, written in French whatever the language of the interface (a complaint to a French
 * prosecutor is written in French); the screen around them is in the resources.
 *
 * Sources (checked on 9 November 2026): Regulation (EU) 2022/2065, articles 16 and 20; code pénal,
 * articles 222-33-2-2 and 223-1-1 (Légifrance, versions in force since 23 March 2024);
 * service-public.gouv.fr F1435 « Porter plainte » (verified on 8 July 2026: what the letter to the
 * prosecutor contains) and R11469 (model letter, verified on 12 December 2025); PHAROS on
 * masecurite.interieur.gouv.fr (updated on 8 September 2026). To be reviewed by a lawyer before
 * the public opening.
 */
object HarassmentReports {
    /** What is still missing for a notice with every element of the DSA, article 16, paragraph 2. */
    fun dsaMissing(input: ReportInput): Set<DsaElement> {
        val missing = linkedSetOf<DsaElement>()
        if (input.explanation.isBlank()) missing += DsaElement.EXPLANATION
        if (input.evidence.none { it.content.url.isNotBlank() }) missing += DsaElement.LOCATION
        if (input.name.isBlank() || !isEmail(input.email)) missing += DsaElement.NAME_AND_EMAIL
        if (!input.goodFaith) missing += DsaElement.GOOD_FAITH
        return missing
    }

    /** A notice to the hosting service under the DSA, article 16, to paste in its own form or send to it. */
    fun dsaNotice(input: ReportInput, today: Long): String = buildString {
        line("Objet : notification d’un contenu illicite (règlement (UE) 2022/2065 sur les services numériques, article 16)")
        line()
        line("Madame, Monsieur,")
        line()
        line("Je vous signale, au titre de l’article 16 du règlement (UE) 2022/2065, la présence sur votre service des informations suivantes, que je considère comme un contenu illicite.")
        line()
        line("1. Emplacement exact du contenu")
        val located = input.evidence.filter { it.content.url.isNotBlank() }
        if (located.isEmpty()) {
            line("[Indiquez l’adresse exacte (URL) du contenu. Pour un message privé : le nom du compte, la conversation, la date et l’heure du message.]")
        }
        for (evidence in input.evidence) {
            val content = evidence.content
            val (date, time) = EvidenceFormat.dateAndTime(content.seenAt)
            val where = content.url.ifBlank { "adresse non relevée" }
            line("- $where" + platformPart(content.platform) + ", vu le $date à $time (heure de Paris)")
        }
        line()
        line("2. Pourquoi ce contenu est illicite")
        line(input.explanation.trim().ifEmpty { "[Expliquez avec vos mots pourquoi ce contenu est illicite : par exemple des messages répétés qui vous visent, ou la publication de votre adresse pour vous exposer à un danger.]" })
        offenceLines(input.offences).forEach { line(it) }
        val texts = input.evidence.filter { it.content.text.isNotBlank() }
        if (texts.isNotEmpty()) {
            line()
            line("Texte du contenu, tel que je l’ai relevé :")
            for (evidence in texts) line("« " + evidence.content.text + " »")
        }
        line()
        line("3. Mes coordonnées")
        line("Nom : " + input.name.trim().ifEmpty { "[votre nom]" })
        line("Adresse électronique : " + input.email.trim().ifEmpty { "[votre adresse électronique]" })
        line()
        line("4. Déclaration de bonne foi")
        if (input.goodFaith) {
            line(GOOD_FAITH)
        } else {
            line("[À compléter par vous seulement si c’est vrai : « $GOOD_FAITH »]")
        }
        line()
        line("Je vous remercie de m’adresser un accusé de réception et de m’informer de votre décision, avec les voies de recours, comme le prévoit l’article 16, paragraphes 4 et 5, du même règlement.")
        line()
        val (date, _) = EvidenceFormat.dateAndTime(today)
        append("Le ").append(date)
    }

    /** What to carry over into the guided form of PHAROS (it is not a complaint, and PHAROS does not remove the content). */
    fun pharos(input: ReportInput): String = buildString {
        line("Signalement PHAROS : éléments à reporter dans le formulaire de internet-signalement.gouv.fr")
        line()
        line("Nature des faits : " + when {
            input.offences.isEmpty() -> "[harcèlement, menaces, divulgation d’informations personnelles…]"
            else -> input.offences.sortedBy { it.ordinal }.joinToString(" ; ") { offenceName(it) }
        })
        line()
        if (input.evidence.isEmpty()) line("[Ajoutez ici l’adresse (URL), la date et l’heure, et la plateforme de chaque contenu.]")
        input.evidence.forEachIndexed { index, evidence -> evidenceBlock(index + 1, evidence) }
        line("Description :")
        append(input.explanation.trim().ifEmpty { "[Décrivez les faits en quelques phrases : depuis quand, combien de fois, qui est visé.]" })
    }

    /**
     * A complaint to the public prosecutor (« plainte auprès du procureur de la République »), with
     * what service-public.gouv.fr asks for: identity and full contact details, a detailed account of
     * the facts with their date and place, the author if known (otherwise against X), the witnesses,
     * the harm and its estimate, the evidence. To print, sign and send (registered letter with
     * acknowledgement of receipt) to the court of the place of the offence or of the author's home.
     */
    fun complaint(input: ReportInput, today: Long): String = buildString {
        line(input.name.trim().ifEmpty { "[Votre prénom et votre nom]" })
        line("[Votre adresse postale]")
        line("[Votre numéro de téléphone]")
        line(input.email.trim().ifEmpty { "[Votre adresse électronique]" })
        line()
        line("Madame, Monsieur le procureur de la République")
        line("Tribunal judiciaire de " + input.city.trim().ifEmpty { "[ville du tribunal]" })
        line("[Adresse du tribunal]")
        line()
        val (date, _) = EvidenceFormat.dateAndTime(today)
        line("À " + input.city.trim().ifEmpty { "[votre ville]" } + ", le " + date)
        line()
        line("Lettre recommandée avec avis de réception")
        line()
        val against = input.author.trim().ifEmpty { "X" }
        line("Objet : plainte contre $against")
        line()
        line("Madame, Monsieur le procureur de la République,")
        line()
        val offences = input.offences.toList().sortedBy { it.ordinal }
        if (offences.isEmpty()) {
            line("J’ai l’honneur de porter plainte contre $against pour les faits exposés ci-dessous.")
        } else {
            line("J’ai l’honneur de porter plainte contre $against pour des faits susceptibles de constituer " + offences.joinToString(", ainsi que ") { offenceDescription(it) } + ".")
        }
        line()
        line("Les faits")
        line(input.explanation.trim().ifEmpty { "[Racontez les faits en détail : depuis quand, combien de fois, sur quels services, par qui si vous le savez, et ce que cela a changé pour vous.]" })
        line()
        line("Les contenus que j’ai conservés")
        if (input.evidence.isEmpty()) line("[Listez chaque contenu : son adresse, la date et l’heure où vous l’avez vu, la plateforme.]")
        input.evidence.forEachIndexed { index, evidence -> evidenceBlock(index + 1, evidence) }
        line("Témoins")
        line("[Noms et adresses des témoins éventuels, ou supprimez cette partie.]")
        line()
        line("Préjudice")
        line("[Décrivez le préjudice subi (santé, travail, frais engagés) et, si possible, son estimation.]")
        line()
        line("Pièces jointes")
        line("[Captures d’écran, certificats médicaux, autres documents : listez-les ici et joignez-en une copie.]")
        line()
        line("Je vous prie d’agréer, Madame, Monsieur le procureur de la République, l’expression de ma considération distinguée.")
        line()
        append("[Signature]")
    }

    /** The good faith statement of the DSA, article 16, paragraph 2, d), in the first person. */
    const val GOOD_FAITH: String = "Je déclare de bonne foi que les informations et les allégations contenues dans la présente notification sont exactes et complètes."

    private fun StringBuilder.evidenceBlock(number: Int, evidence: Evidence) {
        val content = evidence.content
        val (date, time) = EvidenceFormat.dateAndTime(content.seenAt)
        line("Contenu $number")
        line("Adresse (URL) : " + content.url.ifBlank { "non relevée" })
        line("Vu le $date à $time (heure de Paris)" + platformPart(content.platform))
        if (content.text.isNotBlank()) line("Texte : « " + content.text + " »")
        val image = content.image
        if (image != null) {
            line(if (image.source == EvidenceFormat.SOURCE_CAMERA) "Photo conservée." else "Capture d’écran conservée.")
            line("Empreinte SHA-256 du fichier : " + image.originalSha256)
        }
        val (keptDate, keptTime) = EvidenceFormat.dateAndTime(content.keptAt)
        line("Relevé dans PPF le $keptDate à $keptTime (horloge du téléphone), empreinte SHA-256 du relevé : " + evidence.digest)
        line()
    }

    private fun platformPart(platform: String): String = if (platform.isBlank()) "" else " (" + platform.trim() + ")"

    private fun offenceLines(offences: Set<Offence>): List<String> = offences.sortedBy { it.ordinal }.map { offence ->
        when (offence) {
            Offence.HARASSMENT -> "Ces faits peuvent constituer un harcèlement moral commis au moyen d’un service de communication au public en ligne (code pénal, article 222-33-2-2)."
            Offence.DOXXING -> "Ces faits peuvent constituer la divulgation d’informations permettant d’identifier ou de localiser une personne afin de l’exposer à un risque direct d’atteinte à sa personne ou à ses biens (code pénal, article 223-1-1)."
        }
    }

    private fun offenceName(offence: Offence): String = when (offence) {
        Offence.HARASSMENT -> "harcèlement en ligne"
        Offence.DOXXING -> "divulgation d’informations personnelles"
    }

    private fun offenceDescription(offence: Offence): String = when (offence) {
        Offence.HARASSMENT -> "un harcèlement moral commis au moyen d’un service de communication au public en ligne (code pénal, article 222-33-2-2)"
        Offence.DOXXING -> "la divulgation d’informations permettant de m’identifier ou de me localiser afin de m’exposer à un risque direct d’atteinte à ma personne ou à mes biens (code pénal, article 223-1-1)"
    }

    private fun isEmail(text: String): Boolean {
        val value = text.trim()
        val at = value.indexOf('@')
        return at > 0 && at < value.length - 1 && value.indexOf('@', at + 1) < 0 && value.none { it.isWhitespace() }
    }

    private fun StringBuilder.line(text: String = "") {
        append(text).append('\n')
    }
}
