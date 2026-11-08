package org.politisationpopulairefrancaise.signer

import org.politisationpopulairefrancaise.core.platform.SigningRefusals
import org.politisationpopulairefrancaise.core.platform.SigningUse
import org.politisationpopulairefrancaise.core.signing.EventKinds
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Every French text of the approval pages, kept together (the page, its script messages, the refusals). */
internal object ApprovalTexts {
    const val LANGUAGE = "fr"
    const val TITLE = "Approuver une signature de la plateforme PPF"
    const val HEADING = "Approuver la signature de la plateforme"
    const val INTRO =
        "Cette page est affichée par le service de signature lui-même, et non par l'espace de l'équipe : " +
            "ce que vous lisez ci-dessous est exactement ce qui sera signé au nom de PPF. Lisez tout avant d'approuver."

    fun originHint(origin: String): String = "Vérifiez l'adresse dans la barre du navigateur : elle doit commencer par $origin."

    const val DETAILS = "L'événement"
    const val USE = "Usage"
    const val KIND = "Type d'événement"
    const val EVENT_ID = "Identifiant de l'événement"
    const val EVENT_DATE = "Date de l'événement"
    const val WORKING_KEY = "Clé de travail qui signera"
    const val TAGS = "Étiquettes"
    const val TAGS_CAPTION = "Toutes les étiquettes de l'événement, dans l'ordre"
    const val TAG_NAME = "Nom"
    const val TAG_VALUES = "Valeurs"
    const val NO_TAG = "Aucune étiquette."
    const val CONTENT = "Contenu"
    const val EMPTY_CONTENT = "Contenu vide."
    const val APPROVALS = "Approbations"
    const val NO_APPROVAL = "Aucune approbation pour l'instant."
    const val DOUBLE_RULE = "La double validation demande deux membres différents ; l'auteur du brouillon ne peut pas approuver."

    fun doubleCount(count: Int): String = "Double validation : $count sur 2"
    fun singleCount(count: Int): String = "Approbation : $count sur 1"
    fun approvedBy(account: String, at: Long): String = "$account, le ${date(at)}"

    const val READY = "Les approbations nécessaires sont réunies : l'espace de l'équipe peut maintenant demander la signature."
    const val BUTTON = "Approuver avec ma clé d'accès"
    const val MSG_WORKING = "Votre navigateur va vous demander votre clé d'accès."
    const val MSG_DONE = "Approbation enregistrée. La page va se mettre à jour."
    const val MSG_FAILED = "L'approbation n'a pas abouti. Vous pouvez réessayer."
    const val MSG_UNSUPPORTED = "Ce navigateur ne gère pas les clés d'accès : ouvrez cette page sur votre téléphone ou votre ordinateur habituel."
    const val NOSCRIPT = "L'approbation par clé d'accès demande JavaScript (le seul script de cette page, servi par le service de signature lui-même)."
    const val BACK = "Revenir à l'espace de l'équipe"

    const val UNKNOWN_TITLE = "Brouillon inconnu"
    const val UNKNOWN =
        "Le service de signature ne connaît pas ce brouillon : il n'a jamais été soumis, il a expiré après 7 jours, " +
            "ou l'adresse est incomplète."
    const val SIGNED_TITLE = "Déjà signé"
    const val SIGNED = "Cet événement est déjà signé par la plateforme. Il n'y a plus rien à approuver."
    const val CONTENT_UNKNOWN =
        "Le service de signature a redémarré et n'a pas encore reçu de nouveau le contenu de ce brouillon. " +
            "Rouvrez-le depuis l'espace de l'équipe, puis revenez sur cette page."

    fun useLabel(use: SigningUse): String = when (use) {
        SigningUse.EDITORIAL_COMPRENDRE -> "Contenu de l'espace « Comprendre »"
        SigningUse.EDITORIAL_VOIR -> "Brique sourcée de l'espace « Voir »"
        SigningUse.REVENDICATIONS_STATUS -> "Statut d'une revendication"
        SigningUse.AGIR_ORGANISATIONS -> "Fiche d'une organisation vérifiée (« Agir »)"
        SigningUse.DECIDER_PACTE -> "Pacte démocratique ou réponse d'un candidat (« Décider »)"
        SigningUse.ARCHIVES_TIMESTAMPS -> "Preuve d'horodatage des archives (automatique)"
        SigningUse.OPENDATA_INDEX -> "Index des données ouvertes (automatique)"
        SigningUse.DECIDER_URN_CLOSURE -> "Clôture d'une urne (automatique)"
    }

    fun kindLabel(kind: Int): String = when (kind) {
        EventKinds.LONG_FORM_ARTICLE -> "Article long ($kind, NIP-23)"
        EventKinds.LABEL -> "Étiquette ($kind, NIP-32)"
        else -> "Type $kind"
    }

    /** The message shown for a refusal of the approval routes. */
    fun refusal(code: String): String = when (code) {
        SigningRefusals.UNKNOWN_DRAFT -> "Ce brouillon n'est plus connu du service de signature."
        SigningRefusals.ALREADY_SIGNED -> "Cet événement est déjà signé."
        SigningRefusals.NO_TEAM_LIST -> "Le service de signature n'a pas encore reçu la liste de l'équipe signée par la propriétaire."
        SigningRefusals.TEAM_LIST_EXPIRED -> "La liste de l'équipe signée par la propriétaire est échue : aucune approbation n'est possible avant sa mise à jour."
        SigningRefusals.UNKNOWN_CREDENTIAL -> "Cette clé d'accès ne figure pas dans la liste de l'équipe signée par la propriétaire."
        SigningRefusals.ASSERTION_REFUSED -> "La réponse de la clé d'accès n'a pas été acceptée (clé, adresse de la page ou vérification de la personne)."
        SigningRefusals.NOT_ALLOWED_FOR_USE -> "Aucun compte autorisé ne peut approuver ce contenu avec cette clé d'accès."
        SigningRefusals.AUTHOR_CANNOT_APPROVE -> "L'auteur du brouillon ne peut pas l'approuver lui-même quand une double validation est demandée."
        SigningRefusals.DOUBLE_VALIDATION_REQUIRED -> "Ce contenu met en cause une personne : il demande une double validation, que ce brouillon ne prévoit pas."
        SigningRefusals.ALREADY_APPROVED -> "Vous avez déjà approuvé ce brouillon : la seconde approbation doit venir d'un autre membre."
        SigningRefusals.UNKNOWN_USE, SigningRefusals.AUTOMATIC_USE, SigningRefusals.USE_DISABLED -> "Ce type de contenu ne s'approuve pas ici."
        SigningRefusals.MALFORMED_REQUEST, SigningRefusals.TOO_LARGE -> "La demande est mal formée."
        else -> "L'approbation a été refusée."
    }

    private val DATE = DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH 'h' mm", Locale.FRENCH).withZone(ZoneId.of("Europe/Paris"))

    /** A date of the page, in the time of Paris. */
    fun date(seconds: Long): String = DATE.format(Instant.ofEpochSecond(seconds)) + " (heure de Paris)"
}
