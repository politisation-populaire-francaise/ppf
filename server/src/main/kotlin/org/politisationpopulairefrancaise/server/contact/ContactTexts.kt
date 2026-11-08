package org.politisationpopulairefrancaise.server.contact

import org.politisationpopulairefrancaise.core.contact.CaseStatus
import org.politisationpopulairefrancaise.core.contact.CaseType
import org.politisationpopulairefrancaise.core.contact.ClaimMotif
import org.politisationpopulairefrancaise.core.contact.ContactCategory
import org.politisationpopulairefrancaise.core.contact.DeadlineKind
import org.politisationpopulairefrancaise.core.contact.TicketSubtype

/**
 * Every French text of the section « Échanges » of the back-office (M30), kept together like
 * [org.politisationpopulairefrancaise.server.backoffice.BackOfficeTexts]. No em dash, typographic apostrophes.
 */
object ContactTexts {
    const val SECTION_TITLE: String = "Échanges"

    const val HOME_INTRO: String =
        "Les tickets, les messages et les réclamations adressés à l’équipe. Le serveur ne garde que des enveloppes chiffrées pour la boîte de l’équipe : " +
            "les textes se lisent et se répondent dans le client de l’équipe (étape suivante). Ici, seulement le suivi : nature, statut, dates, échéance, priorité, assignation."
    const val FILTERS: String = "Filtrer la boîte"
    const val FILTER_VIEW: String = "Dossiers"
    const val FILTER_TYPE: String = "Type"
    const val FILTER_NATURE: String = "Sous-type, catégorie ou motif"
    const val FILTER_ASSIGNED: String = "Assignation"
    const val FILTER_PRIORITY: String = "Priorité"
    const val FILTER_BUTTON: String = "Filtrer"
    const val ALL: String = "Tous"
    const val ALL_FEMININE: String = "Toutes"
    const val VIEW_OPEN: String = "À traiter (non clos, ou archive juridique à réexaminer)"
    const val VIEW_ALL: String = "Tous, clos compris"
    const val VIEW_HOLDS: String = "Sous archive juridique"
    const val ASSIGNED_ME: String = "À moi"
    const val ASSIGNED_NOBODY: String = "À personne"
    const val INBOX_TITLE: String = "Boîte de réception"
    const val INBOX_EMPTY: String = "Aucun dossier."
    const val INBOX_LIMIT: String = "Seuls les 200 premiers dossiers sont affichés : affinez les filtres."
    const val COLUMN_NUMBER: String = "Numéro"
    const val COLUMN_NATURE: String = "Nature"
    const val COLUMN_STATUS: String = "Statut"
    const val COLUMN_RECEIVED: String = "Reçu le"
    const val COLUMN_DEADLINE: String = "Échéance"
    const val COLUMN_PRIORITY: String = "Priorité"
    const val COLUMN_ASSIGNED: String = "Assigné à"
    const val COLUMN_MARKS: String = "À noter"
    const val NO_DEADLINE: String = "Aucune"
    const val NOBODY: String = "Personne"

    const val MARK_OVERDUE: String = "Dépassée"
    const val MARK_TWO_DAYS: String = "Échéance dans 2 jours"
    const val MARK_SEVEN_DAYS: String = "Échéance dans 7 jours"
    const val MARK_RIGHT_OF_REPLY: String = "Droit de réponse : 3 jours"
    const val MARK_HOLD: String = "Archive juridique"
    const val MARK_HOLD_REVIEW: String = "Archive juridique à réexaminer"
    const val MARK_HOLD_PENDING: String = "Archive juridique : validation en attente"
    const val MARK_HIGH: String = "Priorité haute"
    const val MARK_ANONYMIZED: String = "Anonymisé"

    const val BACK: String = "Retour à la boîte"
    const val CASE_TITLE: String = "Dossier"
    const val FACTS: String = "Suivi du dossier"
    const val FACT_NATURE: String = "Nature"
    const val FACT_STATUS: String = "Statut"
    const val FACT_RECEIVED: String = "Reçu le"
    const val FACT_DEADLINE: String = "Échéance"
    const val FACT_EXTENDED: String = "Prolongé le"
    const val FACT_EXTENSION_REASON: String = "Motif de la prolongation"
    const val FACT_PRIORITY: String = "Priorité"
    const val FACT_ASSIGNED: String = "Assigné à"
    const val FACT_ACTIVITY: String = "Dernière activité"
    const val FACT_CLOSED: String = "Clos le"
    const val FACT_DECISION: String = "Décision contestée du"
    const val FACT_DECISION_AUTHOR: String = "Auteur de la décision"
    const val FACT_CONCERNS: String = "Membre mis en cause"
    const val FACT_HOLD: String = "Archive juridique"
    const val FACT_HOLD_REVIEW: String = "Réexamen le"
    const val FACT_ANONYMIZED: String = "Anonymisé le"
    const val FACT_ENVELOPES: String = "Enveloppes chiffrées"
    const val NOT_SET: String = "Non renseigné"
    const val NO_TEXT_HERE: String = "Le texte du dossier n’est pas sur le serveur en clair : il se lit dans le client de l’équipe, avec la clé de la boîte."
    const val LEGAL: String = "délai légal"
    const val GOAL: String = "objectif interne"

    const val ACTIONS: String = "Actions"
    const val ASSIGN_TITLE: String = "Assignation"
    const val ASSIGN_LABEL: String = "Assigner à"
    const val ASSIGN_ME: String = "Moi"
    const val ASSIGN_BUTTON: String = "Enregistrer l’assignation"
    const val ASSIGN_HINT: String = "Seuls les membres qui peuvent traiter ce dossier sont proposés."
    const val PRIORITY_TITLE: String = "Priorité"
    const val PRIORITY_BUTTON: String = "Enregistrer la priorité"
    const val STATUS_TITLE: String = "Statut"
    const val STATUS_BUTTON: String = "Changer le statut"
    const val STATUS_HINT: String =
        "La personne voit le nouveau statut dans « Mes échanges ». Pour lui expliquer un changement (une clôture, par exemple), envoyez aussi un message depuis le client de l’équipe."
    const val EXTEND_TITLE: String = "Prolongation (droits sur les données)"
    const val EXTEND_HINT: String =
        "Le RGPD (article 12, paragraphe 3) permet de prolonger de deux mois, selon la complexité et le nombre de demandes, à condition de l’annoncer à la personne, avec ses motifs, dans le premier mois. " +
            "Envoyez-lui ce motif depuis le client de l’équipe."
    const val EXTEND_REASON: String = "Motif de la prolongation (obligatoire)"
    const val EXTEND_BUTTON: String = "Prolonger de deux mois"
    const val EXTEND_CLOSED: String = "La prolongation n’est plus possible : le premier mois est passé, ou le délai a déjà été prolongé."
    const val DECISION_AUTHOR_TITLE: String = "Auteur de la décision contestée"
    const val DECISION_AUTHOR_HINT: String = "Une contestation est examinée par une autre personne que l’auteur de la décision : ce membre ne pourra ni la traiter ni se la voir assigner."
    const val DECISION_AUTHOR_BUTTON: String = "Enregistrer l’auteur"
    const val CONCERNS_TITLE: String = "Membre mis en cause"
    const val CONCERNS_HINT: String = "Le membre mis en cause par la réclamation ne verra plus ce dossier, nulle part dans le back-office."
    const val CONCERNS_BUTTON: String = "Enregistrer le membre mis en cause"
    const val HOLD_TITLE: String = "Archive juridique"
    const val HOLD_INTRO: String =
        "Tant que l’archive juridique est active, rien n’est effacé ni anonymisé. Elle se pose pour un an au plus, avec un motif, et demande l’accord d’une deuxième personne ; " +
            "à la date de réexamen, le dossier revient dans la boîte avec une alerte, et sans renouvellement dans les 30 jours, les durées ordinaires reprennent."
    const val HOLD_REASON: String = "Motif (obligatoire)"
    const val HOLD_REVIEW: String = "Date de réexamen (dans un an au plus)"
    const val HOLD_REQUEST_BUTTON: String = "Demander l’archive juridique"
    const val HOLD_RENEW_BUTTON: String = "Demander le renouvellement"
    const val HOLD_RELEASE_REASON: String = "Motif de la levée (obligatoire)"
    const val HOLD_RELEASE_BUTTON: String = "Demander la levée de l’archive"
    const val HOLD_PENDING: String = "Demande en attente d’une deuxième personne"
    const val HOLD_PENDING_HOLD: String = "Poser l’archive juridique"
    const val HOLD_PENDING_RELEASE: String = "Lever l’archive juridique"
    const val HOLD_REQUESTED_BY: String = "Demandée par"
    const val HOLD_REQUESTED_AT: String = "Le"
    const val HOLD_CONFIRM_BUTTON: String = "Accepter la demande"
    const val HOLD_REFUSE_BUTTON: String = "Refuser la demande"
    const val HOLD_CANCEL_BUTTON: String = "Retirer ma demande"
    const val HOLD_OWN_REQUEST: String = "Vous avez fait cette demande : une autre personne doit l’accepter."
    const val NO_ACTION: String = "Aucune action possible sur ce dossier."
    const val SENSITIVE_HINT: String = "Votre clé vous sera redemandée si la dernière vérification date de plus de 5 minutes."
    const val REASON_HINT: String = "Le motif est conservé sans limite dans le journal de l’équipe : n’y écrivez rien de personnel, et jamais le texte du dossier."

    const val JOURNAL_FAILED_TITLE: String = "Journal indisponible"
    const val JOURNAL_FAILED: String = "Le changement est enregistré, mais sa ligne du journal n’a pas pu être écrite. Prévenez un administrateur."

    /** The alert of the dashboard when legal holds reach their review date (counts only, never a number). */
    fun holdsDueAlert(count: Int): String =
        if (count == 1) "Une archive juridique d’un dossier d’échanges est à réexaminer" else "$count archives juridiques de dossiers d’échanges sont à réexaminer"

    val NOTICES: Map<String, String> = mapOf(
        "assigned" to "Assignation enregistrée.",
        "priority" to "Priorité enregistrée.",
        "status" to "Statut changé.",
        "extended" to "Délai prolongé de deux mois. Prévenez la personne, avec le motif, depuis le client de l’équipe.",
        "decision_author" to "Auteur de la décision enregistré.",
        "concerns" to "Membre mis en cause enregistré.",
        "hold_requested" to "Demande enregistrée : elle attend l’accord d’une deuxième personne (7 jours au plus).",
        "hold_confirmed" to "Demande acceptée.",
        "hold_refused" to "Demande refusée.",
        "hold_cancelled" to "Demande retirée.",
    )

    val ERRORS: Map<String, String> = mapOf(
        "unchanged" to "Rien n’a changé.",
        "forbidden" to "Vous ne pouvez pas faire cette action sur ce dossier.",
        "not_eligible" to "Ce membre ne peut pas traiter ce dossier.",
        "decision_author" to "L’auteur de la décision contestée ne peut pas traiter la contestation.",
        "assigned_to_member" to "Le dossier est assigné à ce membre : assignez-le d’abord à quelqu’un d’autre.",
        "own_target" to "Vous ne pouvez pas vous désigner vous-même.",
        "anonymized" to "Ce dossier est anonymisé : il ne change plus.",
        "not_extendable" to EXTEND_CLOSED,
        "reason_required" to "Un motif est obligatoire.",
        "review_date" to "La date de réexamen est à venir, dans un an au plus.",
        "already_requested" to "Une demande attend déjà une deuxième personne.",
        "no_request" to "Aucune demande en attente.",
        "own_request" to "Une autre personne que l’auteur de la demande doit l’accepter ou la refuser.",
        "no_hold" to "Ce dossier n’a pas d’archive juridique.",
        "invalid" to "Valeur non reconnue.",
    )

    fun type(type: CaseType): String = when (type) {
        CaseType.TICKET -> "Ticket"
        CaseType.CONTACT -> "Contact"
        CaseType.CLAIM -> "Réclamation"
    }

    fun subtype(subtype: TicketSubtype): String = when (subtype) {
        TicketSubtype.BUG -> "Bug"
        TicketSubtype.SUGGESTION -> "Suggestion"
        TicketSubtype.REPORT -> "Signalement d’un contenu ou d’un compte"
    }

    fun category(category: ContactCategory): String = when (category) {
        ContactCategory.CITIZEN -> "Citoyen ou citoyenne"
        ContactCategory.ASSOCIATION -> "Association ou collectif"
        ContactCategory.UNION -> "Syndicat"
        ContactCategory.JOURNALIST -> "Journaliste"
        ContactCategory.ELECTED -> "Élu ou élue"
        ContactCategory.INSTITUTION -> "Institution"
        ContactCategory.COMPANY -> "Entreprise"
        ContactCategory.OTHER -> "Autre"
    }

    fun motif(motif: ClaimMotif): String = when (motif) {
        ClaimMotif.MODERATION_DECISION -> "Contestation d’une décision de modération"
        ClaimMotif.DATA_RIGHTS -> "Droits sur les données"
        ClaimMotif.RIGHT_OF_REPLY -> "Droit de réponse"
        ClaimMotif.FUNDS -> "Usage des fonds"
        ClaimMotif.TEAM_MEMBER -> "Comportement d’un membre de l’équipe"
        ClaimMotif.OTHER -> "Autre"
    }

    fun status(status: CaseStatus): String = when (status) {
        CaseStatus.RECEIVED -> "Reçu"
        CaseStatus.IN_PROGRESS -> "En cours"
        CaseStatus.ANSWERED -> "Réponse envoyée"
        CaseStatus.AWAITING_YOU -> "En attente de la personne"
        CaseStatus.CLOSED -> "Clos"
    }

    fun priority(priority: CasePriority): String = when (priority) {
        CasePriority.NORMAL -> "Normale"
        CasePriority.HIGH -> "Haute"
    }

    fun deadlineKind(kind: DeadlineKind): String = when (kind) {
        DeadlineKind.LEGAL -> LEGAL
        DeadlineKind.GOAL -> GOAL
    }
}
