package org.politisationpopulairefrancaise.server.backoffice

/**
 * Every French text of the back-office, kept together (the web module has no resource mechanism; one
 * object of constants, as the contract allows). No em dash, typographic apostrophes.
 */
object BackOfficeTexts {
    const val SITE_NAME: String = "Espace de l’équipe PPF"
    const val RELYING_PARTY_NAME: String = "Équipe PPF"
    const val SKIP_LINK: String = "Aller au contenu"
    const val NAV_LABEL: String = "Navigation de l’équipe"
    const val NAV_DASHBOARD: String = "Tableau de bord"
    const val NAV_APPROVALS: String = "Validations à deux"
    const val NAV_TEAM: String = "Équipe"
    const val NAV_JOURNAL: String = "Journal"
    const val NAV_SESSIONS: String = "Séances"
    const val NAV_KEYS: String = "Mes clés"
    const val NAV_REQUISITIONS: String = "Réquisitions"
    const val SIGN_OUT: String = "Se déconnecter"
    const val FOOTER_PRIVACY: String = "Aucun traceur, aucune ressource extérieure, aucune adresse IP enregistrée."
    const val FOOTER_JOURNAL: String = "Chaque action est inscrite dans le journal de l’équipe, que personne ne peut modifier."

    const val ACTOR_SYSTEM: String = "système"
    const val ACTOR_UNKNOWN: String = "inconnu"
    const val DEFAULT_KEY_NAME: String = "Clé principale"
    const val DEFAULT_BACKUP_KEY_NAME: String = "Clé de secours"
    const val QUEUE_ACTION_FAILED: String = "L’action n’a pas pu être faite. Rien n’a changé ; réessayez plus tard."
    const val REASON_ORGANISATION_UNVERIFIED: String = "L’organisation qui a désigné ce validateur n’est plus vérifiée."
    const val REASON_PERMANENT_HINT: String = "Le motif est conservé sans limite dans le journal de l’équipe : n’y écrivez rien de personnel."
    const val WIREGUARD_REMINDER: String = "Pensez aussi à faire retirer du serveur le profil WireGuard des appareils de ce membre."
    const val ALERT_JOURNAL_BROKEN: String = "Le journal de l’équipe a été modifié ou raccourci en dehors de l’application : à examiner tout de suite."
    const val ALERT_ORGANISATIONS_SHRANK: String = "Organisations vérifiées : liste vide ou réduite de plus de moitié. Aucun validateur suspendu, leurs séances sont refusées. Vérifiez l’annuaire, puis marquez l’alerte traitée."

    // Roles
    const val ROLE_SUPER_ADMIN: String = "Super-administrateur"
    const val ROLE_ADMIN: String = "Administrateur"
    const val ROLE_MODERATOR: String = "Modérateur"
    const val ROLE_EDITOR: String = "Éditeur"
    const val ROLE_ORGANISATION_VERIFIER: String = "Vérification des organisations"
    const val ROLE_VALIDATOR: String = "Validateur"
    const val ROLE_ELECTED_CERTIFIER: String = "Certification des élus"

    // Ceremonies (messages read by the script from data attributes)
    const val CEREMONY_WORKING: String = "Suivez les indications de votre appareil ou de votre clé de sécurité…"
    const val CEREMONY_FAILED: String = "La clé n’a pas répondu ou l’opération a été annulée. Vous pouvez réessayer."
    const val CEREMONY_UNSUPPORTED: String = "Ce navigateur ne gère pas les clés d’accès. Utilisez un navigateur récent (Firefox, Chrome, Edge ou Safari)."
    const val NOSCRIPT: String = "La connexion par clé d’accès demande JavaScript (un seul petit fichier, hébergé par PPF). Le reste de l’espace de l’équipe fonctionne sans."

    // Sign-in
    const val SIGN_IN_TITLE: String = "Connexion"
    const val SIGN_IN_INTRO: String = "L’espace de l’équipe s’ouvre avec votre clé d’accès (Windows Hello, téléphone, gestionnaire de mots de passe) ou votre clé de sécurité. Il n’y a aucun mot de passe."
    const val SIGN_IN_BUTTON: String = "Se connecter avec ma clé d’accès"
    const val SIGN_IN_HELP: String = "Votre appareil vous demandera votre code, votre empreinte ou votre visage : c’est la double authentification."

    // Invitation
    const val ENROL_TITLE: String = "Rejoindre l’équipe"
    const val ENROL_INTRO: String = "Vous avez été invité à rejoindre l’équipe de PPF sous le pseudonyme suivant :"
    const val ENROL_EXPLAIN: String = "Enregistrez une clé d’accès sur cet appareil ou une clé de sécurité. PPF ne garde que sa partie publique ; aucune adresse e-mail, aucun mot de passe."
    const val ENROL_KEY_NAME: String = "Nom de cette clé (pour la reconnaître)"
    const val ENROL_BUTTON: String = "Enregistrer ma clé d’accès"
    const val ENROL_BACKUP: String = "Ajoutez ensuite une deuxième clé, de secours : elle est demandée avant toute permission de l’équipe."
    const val INVITATION_GONE_TITLE: String = "Invitation expirée ou déjà utilisée"
    const val INVITATION_GONE_TEXT: String = "Ce lien n’est plus valable. Une invitation sert une seule fois et dure 48 heures. Demandez-en une nouvelle à la personne qui vous a invité."

    // Dashboard
    const val DASHBOARD_TITLE: String = "Tableau de bord"
    const val DASHBOARD_HELLO: String = "Bonjour"
    const val BACKUP_KEY_MISSING: String = "Votre compte n’a qu’une seule clé. Ajoutez une clé de secours pour obtenir les permissions de vos rôles."
    const val BACKUP_KEY_LINK: String = "Ajouter une clé de secours"
    const val ALERTS_TITLE: String = "Alertes"
    const val ALERT_DISMISS: String = "Marquer comme traitée"
    const val ALERT_OPEN: String = "Voir"
    const val PRIORITIES_TITLE: String = "À traiter en priorité"
    const val PRIORITIES_EMPTY: String = "Aucun élément n’approche de son échéance."
    const val QUEUES_TITLE: String = "Files"
    const val QUEUES_EMPTY: String = "Aucune file ne vous concerne pour l’instant."
    const val QUEUE_UNAVAILABLE: String = "indisponible"
    const val APPROVALS_WAITING: String = "Demandes de validation à deux en attente de votre accord"
    const val OVERDUE: String = "Échéance dépassée"
    const val NEAR_DEADLINE: String = "Échéance proche"
    const val DEADLINE: String = "Échéance"
    const val LEVEL: String = "Importance"
    const val RECEIVED: String = "Reçu le"
    const val PENDING_COUNT_ZERO: String = "rien en attente"

    // Queues
    const val QUEUE_EMPTY: String = "Rien n’attend dans cette file."
    const val QUEUE_NEXT: String = "Éléments suivants"
    const val QUEUE_BACK: String = "Retour à la file"
    const val ITEM_ACTIONS: String = "Décision"
    const val ITEM_NO_ACTION: String = "Aucune action possible : cet élément est déjà décidé ou n’existe plus."
    const val REASON_LABEL: String = "Motif"
    const val REASON_REQUIRED_HINT: String = "obligatoire, montré à l’auteur"
    const val REASON_OPTIONAL_HINT: String = "facultatif"
    const val TWO_PERSON_HINT: String = "Cette action attendra l’accord d’une deuxième personne."
    const val SENSITIVE_HINT: String = "Votre clé vous sera redemandée si la dernière vérification date de plus de 5 minutes."

    // Team
    const val TEAM_TITLE: String = "Équipe"
    const val TEAM_MEMBERS: String = "Membres"
    const val TEAM_PSEUDONYM: String = "Pseudonyme"
    const val TEAM_STATUS: String = "État"
    const val TEAM_ROLES: String = "Rôles"
    const val TEAM_SINCE: String = "Depuis le"
    const val TEAM_NO_ROLE: String = "aucun"
    const val STATUS_ACTIVE: String = "actif"
    const val STATUS_SUSPENDED: String = "coupé"
    const val STATUS_REMOVED: String = "retiré"
    const val INVITE_TITLE: String = "Inviter une personne"
    const val INVITE_PSEUDONYM: String = "Pseudonyme (jamais une adresse e-mail)"
    const val INVITE_ROLES: String = "Rôles proposés (chacun attendra l’accord d’une deuxième personne)"
    const val INVITE_ORGANISATION: String = "Clé publique de l’organisation vérifiée qui désigne le validateur (obligatoire pour le rôle Validateur)"
    const val INVITE_END: String = "Fin des rôles proposés (facultatif, accès temporaire)"
    const val INVITE_BUTTON: String = "Créer le lien d’invitation"
    const val INVITE_ISSUED_TITLE: String = "Lien d’invitation"
    const val INVITE_ISSUED_TEXT: String = "Transmettez ce lien à la personne par un canal sûr, avec son profil WireGuard. Il ne sera plus jamais affiché, sert une seule fois et expire dans 48 heures :"
    const val INVITE_LINK_LABEL: String = "Lien à copier"
    const val INVITATIONS_PENDING: String = "Invitations en cours"
    const val INVITATION_WITHDRAW: String = "Retirer l’invitation"
    const val INVITATION_EXPIRES: String = "expire le"
    const val MEMBER_KEYS: String = "Clés enregistrées"
    const val MEMBER_GRANTS: String = "Rôles et historique"
    const val MEMBER_GRANT_ACTIVE: String = "en cours"
    const val MEMBER_GRANT_EXPIRED: String = "échu"
    const val MEMBER_GRANT_REVOKED: String = "retiré"
    const val MEMBER_GRANT_SUSPENDED: String = "suspendu (organisation plus vérifiée)"
    const val MEMBER_GRANT_UNTIL: String = "jusqu’au"
    const val MEMBER_GRANT_ORGANISATION: String = "désigné par l’organisation"
    const val MEMBER_REVOKE_ROLE: String = "Retirer ce rôle"
    const val MEMBER_REQUEST_ROLE: String = "Demander un rôle"
    const val MEMBER_ROLE: String = "Rôle"
    const val MEMBER_REQUEST_BUTTON: String = "Envoyer la demande"
    const val MEMBER_CUT_TITLE: String = "Coupure d’urgence"
    const val MEMBER_CUT_TEXT: String = "Suspend en un geste toutes les sessions, tous les rôles et toutes les clés de ce compte. À utiliser si un appareil ou une clé est perdu ou volé. C’est réversible : un deuxième super-administrateur peut réintégrer le compte."
    const val MEMBER_CUT_BUTTON: String = "Couper l’accès maintenant"
    const val MEMBER_REMOVE_TITLE: String = "Retirer de l’équipe"
    const val MEMBER_REMOVE_TEXT: String = "Retire définitivement la personne de l’équipe ; ses clés sont effacées et sa trace reste dans le journal. Pour un administrateur ou un super-administrateur, la demande attend l’accord d’un deuxième super-administrateur."
    const val MEMBER_SUSPENDED: String = "Ce compte est coupé : ses rôles et ses clés sont conservés mais inutilisables. Supprimez ci-dessous une clé perdue ou volée avant toute réintégration."
    const val MEMBER_KEY_DELETE: String = "Supprimer cette clé"
    const val MEMBER_REINSTATE_TITLE: String = "Réintégrer"
    const val MEMBER_REINSTATE_TEXT: String = "Rend au compte ses rôles et ses clés restantes. La demande attend l’accord d’un deuxième super-administrateur."
    const val MEMBER_REINSTATE_BUTTON: String = "Demander la réintégration"
    const val MEMBER_REMOVE_BUTTON: String = "Retirer ce membre"

    // Approvals
    const val APPROVALS_TITLE: String = "Validations à deux"
    const val APPROVALS_INTRO: String = "Certaines actions attendent l’accord d’une deuxième personne qui a la même permission. La personne qui demande ne peut pas valider sa propre demande. Une demande expire après 7 jours."
    const val APPROVALS_EMPTY: String = "Aucune demande en attente."
    const val APPROVAL_REQUESTED_BY: String = "Demandé par"
    const val APPROVAL_ON: String = "le"
    const val APPROVAL_EXPIRES: String = "expire le"
    const val APPROVAL_APPROVE: String = "Valider"
    const val APPROVAL_REFUSE: String = "Refuser"
    const val APPROVAL_CANCEL: String = "Annuler ma demande"
    const val APPROVAL_YOURS: String = "Votre demande : une autre personne doit la valider."
    const val APPROVAL_GRANT: String = "Attribuer le rôle"
    const val APPROVAL_REVOKE: String = "Retirer le rôle"
    const val APPROVAL_QUEUE: String = "Action sur une file"
    const val APPROVAL_REMOVE: String = "Retirer définitivement de l’équipe"
    const val APPROVAL_REINSTATE: String = "Réintégrer dans l’équipe"
    const val APPROVAL_TO: String = "à"
    const val APPROVAL_REQUISITION: String = "Réquisition judiciaire : recherche dans le registre légal"

    // Judicial requisitions (legal register, D-077)
    const val REQUISITIONS_TITLE: String = "Réquisitions judiciaires"
    const val REQUISITIONS_INTRO: String = "Le registre légal ne s’ouvre que sur une réquisition judiciaire écrite (officier de police judiciaire, procureur de la République, juge d’instruction, ou juge saisi par une victime). Vérifiez d’abord qui l’envoie et sur quelle base légale."
    const val REQUISITIONS_CONTENT: String = "Le registre ne contient que des données pseudonymes, gardées un an : la clé publique et les pseudonymes successifs de chaque compte, et pour chaque création, modification ou suppression d’un contenu public, son identifiant, sa nature, ses dates et la clé de son auteur. PPF ne collecte aucune adresse IP, aucun e-mail, aucun numéro de téléphone, et ne garde jamais le texte d’un contenu supprimé."
    const val REQUISITIONS_READY: String = "Réponses à télécharger"
    const val REQUISITIONS_READY_TEXT: String = "Une réponse se télécharge une seule fois, en JSON, par la personne qui a fait la demande ou par celle qui l’a validée, dans les 7 jours qui suivent l’accord. Le téléchargement est inscrit dans le journal de l’équipe avec la référence et le nombre de lignes, jamais les lignes elles-mêmes."
    const val REQUISITIONS_READY_NONE: String = "Aucune réponse à télécharger."
    const val REQUISITION_DOWNLOAD: String = "Télécharger la réponse (une seule fois)"
    const val REQUISITIONS_FORM_TITLE: String = "Nouvelle réquisition"
    const val REQUISITION_REFERENCE: String = "Référence de la réquisition (autorité, numéro, date)"
    const val REQUISITION_REFERENCE_HINT: String = "Obligatoire. La référence est conservée sans limite dans le journal de l’équipe : n’y écrivez rien d’autre."
    const val REQUISITION_KEY: String = "Clé publique visée (npub ou 64 caractères hexadécimaux)"
    const val REQUISITION_EVENT: String = "Identifiant d’un contenu visé (64 caractères hexadécimaux)"
    const val REQUISITION_SEARCH_HINT: String = "Indiquez une clé, un contenu, ou les deux (les deux ensemble restreignent la recherche). La recherche n’entre jamais dans le journal."
    const val REQUISITION_FROM: String = "Reçu à partir du (AAAA-MM-JJ)"
    const val REQUISITION_UNTIL: String = "Jusqu’au (AAAA-MM-JJ, inclus)"
    const val REQUISITION_TWO_PERSON: String = "La recherche attendra l’accord d’une deuxième personne qui a la même permission. Votre clé vous sera redemandée si la dernière vérification date de plus de 5 minutes."
    const val REQUISITION_SUBMIT: String = "Demander l’accord d’une deuxième personne"
    const val REQUISITIONS_HISTORY: String = "Réquisitions des 30 derniers jours"
    const val REQUISITIONS_NONE: String = "Aucune réquisition ces 30 derniers jours."
    const val REQUISITION_REFERENCE_SHORT: String = "Référence"
    const val REQUISITION_SEARCH: String = "Recherche"
    const val REQUISITION_STATE: String = "État"
    const val REQUISITION_KEY_SHORT: String = "clé"
    const val REQUISITION_EVENT_SHORT: String = "contenu"
    const val REQUISITION_PERIOD_FROM: String = "reçus du"
    const val REQUISITION_PERIOD_UNTIL: String = "au"
    const val REQUISITION_SEARCH_GONE: String = "effacée après la réponse ou le délai"
    const val REQUISITION_APPROVED_BY: String = "validée par"
    const val REQUISITION_ANSWERED_ROWS: String = "lignes d’opérations et lignes de pseudonymes"
    const val REQUISITION_FILE_NOTE: String = "Registre pseudonyme de PPF (niveau 1), tenu en application de l’article 6, V, A de la LCEN, du décret n° 2021-1362 (articles 3, 5, 6 et 7) et de l’injonction du décret n° 2026-920. Il contient, pour les seuls contenus publics, l’identifiant de l’événement, la nature de l’opération (create, replace, delete), la date de réception par le serveur (UTC, à la seconde), la date indiquée dans l’événement, son type, la clé publique de l’auteur et le type de protocole ; pour une modification ou une suppression, l’identifiant, la date et la clé de la version retirée, jamais son contenu ; pour chaque compte, la clé et les pseudonymes successifs (name, display_name) avec la date de chaque changement. Durée : un an. PPF ne collecte aucune adresse IP ni aucun port, aucun e-mail, aucun numéro de téléphone (article 8 du décret)."

    private val requisitionStates: Map<RequisitionState, String> = mapOf(
        RequisitionState.WAITING to "en attente de l’accord d’une deuxième personne",
        RequisitionState.READY to "validée, réponse à télécharger",
        RequisitionState.ANSWERED to "réponse téléchargée",
        RequisitionState.LAPSED to "validée, mais pas téléchargée dans les 7 jours : à demander de nouveau",
        RequisitionState.REFUSED to "refusée",
        RequisitionState.EXPIRED to "expirée sans accord",
        RequisitionState.CANCELLED to "annulée",
        RequisitionState.FAILED to "impossible",
    )

    fun requisitionState(state: RequisitionState): String = requisitionStates.getValue(state)

    // Journal
    const val JOURNAL_TITLE: String = "Journal de l’équipe"
    const val JOURNAL_INTRO: String = "Une ligne par action. Chaque ligne porte l’empreinte de la précédente : une ligne retirée ou modifiée se voit."
    const val JOURNAL_CHAIN_OK: String = "Chaîne d’empreintes intacte"
    const val JOURNAL_CHAIN_BROKEN: String = "Chaîne d’empreintes rompue à la ligne"
    const val JOURNAL_LINES: String = "lignes"
    const val JOURNAL_TRUNCATED: String = "Fin du journal disparue : la dernière ligne est la"
    const val JOURNAL_TRUNCATED_EXPECTED: String = "alors que ce serveur a vu la ligne"
    const val JOURNAL_HEAD: String = "Dernière ligne, à noter ou à faire horodater pour prouver plus tard que rien n’a été retiré à la fin du journal : n°"
    const val JOURNAL_HEAD_HASH: String = "empreinte"
    const val JOURNAL_FILTERS: String = "Filtrer le journal"
    const val JOURNAL_FROM: String = "Du (AAAA-MM-JJ)"
    const val JOURNAL_UNTIL: String = "Au (AAAA-MM-JJ, inclus)"
    const val JOURNAL_ACCOUNT: String = "Identifiant du compte"
    const val JOURNAL_ACTION: String = "Action (par exemple team ou queue.agir.held)"
    const val JOURNAL_OBJECT: String = "Objet visé (identifiant)"
    const val JOURNAL_FILTER_BUTTON: String = "Filtrer"
    const val JOURNAL_EXPORT: String = "Exporter cette sélection en JSON"
    const val JOURNAL_EMPTY: String = "Aucune ligne pour ces filtres."
    const val JOURNAL_NUMBER: String = "N°"
    const val JOURNAL_DATE: String = "Date"
    const val JOURNAL_WHO: String = "Qui"
    const val JOURNAL_WHAT: String = "Action"
    const val JOURNAL_TARGET: String = "Objet"
    const val JOURNAL_REASON: String = "Motif"
    const val JOURNAL_DETAILS: String = "Détails"
    const val JOURNAL_OLDER: String = "Lignes plus anciennes"

    // Keys
    const val KEYS_TITLE: String = "Mes clés d’accès"
    const val KEYS_INTRO: String = "Gardez au moins deux clés (une principale, une de secours), sur deux appareils différents."
    const val KEYS_CREATED: String = "ajoutée le"
    const val KEYS_USED: String = "dernier usage le"
    const val KEYS_NEVER_USED: String = "jamais utilisée"
    const val KEYS_DELETE: String = "Supprimer cette clé"
    const val KEYS_ADD_TITLE: String = "Ajouter une clé"
    const val KEYS_ADD_NAME: String = "Nom de la nouvelle clé"
    const val KEYS_ADD_BUTTON: String = "Enregistrer une nouvelle clé"
    const val KEYS_SELF_CUT_TITLE: String = "Appareil ou clé perdu ?"
    const val KEYS_SELF_CUT_TEXT: String = "Coupez tout de suite l’accès de votre compte : sessions, rôles et clés. Un administrateur devra ensuite vous inviter de nouveau."
    const val KEYS_SELF_CUT_BUTTON: String = "Couper l’accès de mon compte"

    // Validation sessions
    const val SESSIONS_TITLE: String = "Séances de validation"
    const val SESSIONS_INTRO: String = "Une séance permet à votre application de valider des comptes en personne. Elle ne garde qu’un compteur, jamais le lieu, l’événement ni les personnes validées."
    const val SESSIONS_OPEN_TITLE: String = "Ouvrir une séance"
    const val SESSIONS_KEY: String = "Clé publique de séance créée par votre application (npub ou 64 caractères hexadécimaux)"
    const val SESSIONS_MAX: String = "Nombre maximal de validations (40 au plus)"
    const val SESSIONS_HOURS: String = "Durée en heures (12 au plus)"
    const val SESSIONS_OPEN_BUTTON: String = "Ouvrir la séance"
    const val SESSIONS_CURRENT: String = "Votre séance en cours"
    const val SESSIONS_WEEK: String = "Semaine"
    const val SESSIONS_COUNT: String = "Validations"
    const val SESSIONS_UNTIL: String = "jusqu’au"
    const val SESSIONS_CLOSE: String = "Fermer ma séance"
    const val SESSIONS_ALL: String = "Séances ouvertes"
    const val SESSIONS_REVOKE: String = "Révoquer cette séance"
    const val SESSIONS_NONE: String = "Aucune séance ouverte."

    // Confirmation
    const val CONFIRM_TITLE: String = "Confirmez avec votre clé"
    const val CONFIRM_TEXT: String = "Cette action est sensible : votre clé d’accès vous est redemandée, car la dernière vérification date de plus de 5 minutes."
    const val CONFIRM_BUTTON: String = "Confirmer avec ma clé et continuer"
    const val CONFIRM_CANCEL: String = "Annuler et revenir au tableau de bord"

    // Generic pages
    const val FORBIDDEN_TITLE: String = "Accès refusé"
    const val NOT_FOUND_TITLE: String = "Page introuvable"
    const val NOT_FOUND_TEXT: String = "Cette page n’existe pas ou plus."
    const val FORM_EXPIRED_TITLE: String = "Formulaire expiré"
    const val FORM_EXPIRED_TEXT: String = "Ce formulaire n’est plus valable (jeton anti-falsification absent ou ancien). Rechargez la page et recommencez."
    const val BACK_HOME: String = "Revenir au tableau de bord"

    // Queue agir.held
    const val AGIR_HELD_TITLE: String = "Agir : mobilisations et appels en relecture"
    const val AGIR_HELD_GONE: String = "Ce contenu n’est plus dans la file (décidé depuis plus de 30 jours, ou inconnu)."
    const val AGIR_UNTITLED: String = "Sans titre"
    const val AGIR_WITNESSES_ALWAYS_REVIEWED: String = "Appel à témoins : il est toujours relu par l’équipe, quel que soit son auteur, même une organisation vérifiée."
    const val AGIR_ALREADY_APPROVED: String = "Déjà approuvé et publié."
    const val AGIR_ALREADY_REJECTED: String = "Déjà refusé."
    const val AGIR_KIND: String = "Nature"
    const val AGIR_START: String = "Début"
    const val AGIR_END: String = "Fin"
    const val AGIR_LOCATION: String = "Lieu"
    const val AGIR_TERRITORY: String = "Territoire"
    const val AGIR_LINKS: String = "Liens (à vérifier avant de les ouvrir)"
    const val AGIR_AUTHOR: String = "Clé publique de l’auteur (pseudonyme)"
    const val AGIR_APPROVE: String = "Approuver et publier"
    const val AGIR_REJECT: String = "Refuser"
    const val AGIR_MOBILIZATION: String = "Mobilisation"
    const val AGIR_CALL: String = "Appel"
    const val AGIR_OTHER: String = "Autre contenu"

    private val callTypes: Map<String, String> = mapOf(
        "se-reunir" to "se réunir",
        "debattre" to "débattre",
        "s-organiser" to "s’organiser",
        "manifester" to "manifester",
        "soutenir" to "soutenir",
        "petition" to "pétition",
        "interpeller" to "interpeller",
        "boycotter" to "boycotter",
        "collecte" to "collecte",
        "benevoles" to "bénévoles",
        "information" to "information",
        "temoins" to "appel à témoins",
        "autre" to "autre",
    )

    fun callType(value: String): String = callTypes[value] ?: value

    /** Confirmations after an action (the codes are given in the address after a redirect). */
    private val notices: Map<String, String> = mapOf(
        "done" to "C’est fait.",
        "requested" to "Demande enregistrée : elle attend l’accord d’une deuxième personne.",
        "approved" to "Demande validée.",
        "refused" to "Demande refusée.",
        "cancelled" to "Demande annulée.",
        "failed" to "Demande validée, mais l’action n’était plus possible (compte retiré, élément déjà décidé…).",
        "key_added" to "Clé ajoutée.",
        "key_deleted" to "Clé supprimée.",
        "welcome" to "Bienvenue dans l’équipe. Ajoutez maintenant une clé de secours.",
        "cut" to "L’accès du compte est coupé : sessions, rôles et clés sont suspendus. Pensez aussi à faire retirer du serveur le profil WireGuard des appareils de ce membre.",
        "self_cut" to "L’accès de votre compte est coupé. Demandez qu’on retire aussi du serveur le profil WireGuard de l’appareil perdu.",
        "removed" to "Le membre est retiré de l’équipe. Pensez aussi à faire retirer du serveur le profil WireGuard de ses appareils.",
        "withdrawn" to "Invitation retirée.",
        "session_opened" to "Séance ouverte.",
        "session_closed" to "Séance fermée.",
        "dismissed" to "Alerte marquée comme traitée.",
        "signed_out" to "Vous êtes déconnecté.",
    )

    fun notice(code: String?): String? = code?.let(notices::get)

    /** The French message of a refusal code of the base. */
    fun error(code: String): String = errors[code] ?: "L’action a été refusée."

    fun hasError(code: String?): Boolean = code != null && code in errors

    private val errors: Map<String, String> = mapOf(
        "forbidden" to "Vous n’avez pas la permission de faire cela.",
        "forbidden_role" to "Vous n’avez pas la permission de proposer l’un de ces rôles.",
        "not_found" to "Introuvable : l’élément n’existe pas ou plus.",
        "confirmation_required" to "Confirmez d’abord avec votre clé (dernière vérification de plus de 5 minutes).",
        "bad_pseudonym" to "Le pseudonyme doit faire de 2 à 40 caractères : lettres, chiffres, espaces, tirets, points ou apostrophes ; jamais une adresse e-mail.",
        "pseudonym_taken" to "Ce pseudonyme est déjà utilisé par un membre de l’équipe.",
        "bootstrap_closed" to "Il existe déjà deux super-administrateurs (ou des invitations en cours pour eux).",
        "invitation_gone" to "Cette invitation n’est plus valable.",
        "ceremony_gone" to "L’opération a pris trop de temps ou a déjà servi. Recommencez.",
        "busy" to "Trop de connexions sont en cours. Réessayez dans quelques minutes.",
        "bad_encoding" to "La réponse de la clé est illisible. Recommencez.",
        "too_large" to "La réponse de la clé est trop grande.",
        "too_large_form" to "Le formulaire envoyé est trop long. Raccourcissez le texte, puis enregistrez de nouveau.",
        "registration_refused" to "La clé n’a pas pu être enregistrée (vérification de la personne exigée : code, empreinte ou visage).",
        "assertion_refused" to "La clé n’a pas été reconnue. Recommencez.",
        "credential_mismatch" to "La réponse ne correspond pas à la clé. Recommencez.",
        "unknown_key" to "Cette clé n’est enregistrée pour aucun membre de l’équipe.",
        "account_closed" to "Ce compte n’a plus accès à l’espace de l’équipe.",
        "key_known" to "Cette clé est déjà enregistrée.",
        "too_many_keys" to "Vous avez déjà 10 clés : supprimez-en une d’abord.",
        "last_key" to "C’est votre dernière clé : ajoutez-en une autre avant de la supprimer.",
        "end_in_past" to "La date de fin doit être dans le futur.",
        "organisation_required" to "Le rôle Validateur demande la clé publique de l’organisation vérifiée qui désigne la personne.",
        "organisation_not_expected" to "Seul le rôle Validateur est désigné par une organisation.",
        "organisation_not_verified" to "Cette organisation n’est pas (ou plus) vérifiée dans l’annuaire d’« Agir ».",
        "already_granted" to "Ce membre a déjà ce rôle.",
        "already_requested" to "Une demande identique attend déjà une validation.",
        "approval_gone" to "Cette demande n’est plus en attente (décidée, annulée ou expirée).",
        "own_request" to "Vous ne pouvez pas valider votre propre demande.",
        "own_grant" to "Vous ne pouvez pas valider une demande qui vous concerne.",
        "unknown_action" to "Action inconnue.",
        "already_closed" to "Ce compte est déjà coupé ou retiré.",
        "own_target" to "Vous ne pouvez pas décider d’une demande qui vous concerne.",
        "not_suspended" to "Ce compte n’est pas coupé.",
        "ambiguous" to "Plusieurs comptes portent ce pseudonyme.",
        "bootstrap_invitation" to "Une invitation de premier super-administrateur ne se retire que sur le serveur.",
        "action_unavailable" to "Cette action n’est plus possible sur cet élément.",
        "queue_unavailable" to "Cette file ne répond pas pour l’instant. Réessayez plus tard.",
        "reason_required" to "Un motif est obligatoire.",
        "bad_session_key" to "La clé de séance doit être une clé publique Nostr (npub ou 64 caractères hexadécimaux).",
        "bad_ceiling" to "Le nombre maximal de validations doit être entre 1 et 40.",
        "bad_duration" to "La durée doit être entre 1 et 12 heures.",
        "session_already_open" to "Vous avez déjà une séance ouverte : fermez-la d’abord.",
        "session_key_used" to "Cette clé de séance a déjà servi : votre application doit en créer une nouvelle.",
        "bad_date" to "Une date n’est pas au format AAAA-MM-JJ.",
        "bad_form" to "Le formulaire est incomplet.",
        "reference_required" to "La référence de la réquisition (autorité, numéro, date) est obligatoire.",
        "search_required" to "Indiquez la clé publique ou l’identifiant du contenu visés par la réquisition.",
        "bad_key" to "La clé publique doit être un npub ou 64 caractères hexadécimaux.",
        "bad_event_id" to "L’identifiant d’un contenu fait 64 caractères hexadécimaux.",
        "period_required" to "La période (début et fin) est obligatoire.",
        "bad_period" to "La fin de la période doit venir après son début.",
        "requisition_not_approved" to "Cette réquisition n’a pas (ou pas encore) l’accord d’une deuxième personne.",
        "requisition_answered" to "La réponse à cette réquisition a déjà été téléchargée : elle ne se télécharge qu’une fois.",
        "requisition_lapsed" to "Le délai de 7 jours pour télécharger la réponse est dépassé : faites une nouvelle demande.",
        "requisition_unavailable" to "Cette réquisition n’a plus de recherche à faire : faites une nouvelle demande.",
    )

    /** French label of a role. */
    fun role(role: TeamRole): String = role.label

    /** French label of an action of the journal, when known (the identifier is shown otherwise). */
    fun action(id: String): String? = actions[id]

    private val actions: Map<String, String> = mapOf(
        "auth.login" to "Connexion",
        "auth.login_failed" to "Échec de connexion",
        "auth.logout" to "Déconnexion",
        "auth.confirmed" to "Clé vérifiée de nouveau",
        "auth.confirmation_failed" to "Échec de la nouvelle vérification",
        "auth.enrolment_failed" to "Échec d’enregistrement d’une clé",
        "team.bootstrap_invitation" to "Invitation d’un premier super-administrateur",
        "team.invitation_created" to "Invitation créée",
        "team.invitation_withdrawn" to "Invitation retirée",
        "team.invitation_accepted" to "Invitation acceptée",
        "team.key_added" to "Clé ajoutée",
        "team.key_deleted" to "Clé supprimée",
        "team.role_granted" to "Rôle attribué",
        "team.role_revoked" to "Rôle retiré",
        "team.role_expired" to "Rôle échu",
        "team.validator_suspended" to "Validateur suspendu",
        "team.emergency_cut" to "Coupure d’urgence",
        "team.member_removed" to "Membre retiré",
        "team.member_reinstated" to "Membre réintégré",
        "auth.anonymous_failures" to "Échecs sans compte connu (nombres par période de 10 minutes)",
        "approval.requested" to "Demande de validation à deux",
        "approval.approved" to "Demande validée",
        "approval.refused" to "Demande refusée",
        "approval.cancelled" to "Demande annulée",
        "approval.expired" to "Demande expirée",
        "approval.failed" to "Demande impossible à exécuter",
        "audit.exported" to "Export du journal",
        "alert.posted" to "Alerte déposée",
        "alert.dismissed" to "Alerte traitée",
        "validation.session_opened" to "Séance de validation ouverte",
        "validation.session_closed" to "Séance de validation fermée",
        "validation.session_revoked" to "Séance de validation révoquée",
        "legal.requisition_answered" to "Réponse à une réquisition judiciaire (référence et nombres de lignes)",
    )
}
