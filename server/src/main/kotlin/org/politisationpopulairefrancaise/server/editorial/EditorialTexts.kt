package org.politisationpopulairefrancaise.server.editorial

import org.politisationpopulairefrancaise.core.editorial.schema.ContentSchema
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialEngine
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialMessages
import org.politisationpopulairefrancaise.core.editorial.schema.FieldSpec
import org.politisationpopulairefrancaise.core.editorial.schema.Problem
import org.politisationpopulairefrancaise.core.editorial.schema.ValueSpec

/**
 * Every French text of the editorial pages of the back-office (circuit éditorial, step 3), kept together
 * like [org.politisationpopulairefrancaise.server.backoffice.BackOfficeTexts], and the French labels of the
 * translation keys of the schemas (`labelKey`, `helpKey`, `templateKey`, choice values). No em dash,
 * typographic apostrophes.
 */
object EditorialTexts {
    const val SECTION_COMPRENDRE: String = "Rédaction Comprendre"
    const val SECTION_VOIR: String = "Rédaction Voir"

    const val HOME_INTRO: String = "Écrivez ici les contenus de l’espace. Un brouillon s’enregistre même incomplet : rien ne se perd. Il ne part en relecture qu’une fois sans problème, et c’est un autre éditeur ou une autre éditrice qui le relit."
    const val DRAFTS_TITLE: String = "Brouillons en cours"
    const val DRAFTS_EMPTY: String = "Aucun brouillon pour l’instant."
    const val NEW_TITLE: String = "Nouveau brouillon"
    const val NEW_INTRO: String = "Choisissez le type de contenu : le formulaire et le modèle de texte en dépendent."
    const val UPDATE_TITLE: String = "Mettre à jour un contenu publié"
    const val UPDATE_INTRO: String = "Une mise à jour garde l’adresse du contenu : elle repart de la version publiée."
    const val UPDATE_SLUG: String = "Identifiant du contenu publié"
    const val UPDATE_BUTTON: String = "Créer le brouillon de mise à jour"
    const val PUBLISHED_EMPTY: String = "Aucun contenu publié de cet espace pour l’instant."
    const val COLUMN_TITLE: String = "Titre"
    const val COLUMN_TYPE: String = "Type"
    const val COLUMN_VERSION: String = "Version"
    const val COLUMN_PROBLEMS: String = "Problèmes"
    const val COLUMN_UPDATED: String = "Modifié le"
    const val COLUMN_BY: String = "Par"
    const val UNTITLED: String = "(sans titre)"
    const val BACK_TO_SECTION: String = "Retour aux brouillons"

    const val FORM_NEW_TITLE: String = "Nouveau brouillon"
    const val FORM_EDIT_TITLE: String = "Brouillon"
    const val FORM_UPDATE_OF: String = "Mise à jour du contenu publié"
    const val COMMON_LEGEND: String = "Informations communes"
    const val REQUIRED: String = "obligatoire"
    const val OPTIONAL: String = "facultatif"
    const val ROW: String = "Ligne"
    const val MORE_ROWS: String = "Ajouter des lignes"
    const val MOVE_UP: String = "Monter"
    const val MOVE_DOWN: String = "Descendre"
    const val ROWS_HINT: String = "Une ligne laissée vide est ignorée ; pour retirer une ligne, videz-la. Le bouton « Ajouter des lignes » garde tout ce qui est déjà écrit."
    const val CHOICE_NONE: String = "Non renseigné"
    const val SELECT_PROMPT: String = "Choisir"
    const val ORIGIN_LEGEND: String = "Ce contenu a-t-il été produit avec l’aide d’une IA ? (obligatoire avant la relecture)"
    const val ORIGIN_YES: String = "Oui, avec l’aide d’une IA"
    const val ORIGIN_NO: String = "Non"
    const val ORIGIN_HINT: String = "Aucune réponse n’est cochée d’avance : le site affiche l’origine de chaque contenu (cahier des charges, section 5.6)."
    const val CHANGE_LEGEND: String = "Nature du changement"
    const val CHANGE_CORRECTION: String = "Correction (une erreur de fond : un fait, un chiffre, un statut)"
    const val CHANGE_UPDATE: String = "Mise à jour (complément, nouvelle source, forme)"
    const val CHANGE_NOTE: String = "Résumé du changement (public, au journal des corrections, obligatoire)"
    const val CHANGE_NOTE_HINT: String = "Une ou deux phrases sur ce qui change et pourquoi : elles sont publiées avec le contenu et dans le journal public des corrections, à côté de la date."
    const val CHANGE_KIND_REQUIRED: String = "Nature du changement : choisissez « Correction » ou « Mise à jour » (obligatoire pour la mise à jour d’un contenu publié)."
    const val CHANGE_NOTE_REQUIRED: String = "Résumé du changement : ce champ est obligatoire pour la mise à jour d’un contenu publié (il entre au journal public des corrections)."
    const val BODY_LABEL: String = "Texte (Markdown)"
    const val BODY_HINT: String = "Titres « ## », listes « - », gras « **…** », liens « [texte](https://…) ». Aucun HTML : il serait affiché tel quel."
    const val BODY_BLOCKS: String = "Blocs attendus :"
    const val BODY_SECTIONS: String = "Sections obligatoires :"
    const val BODY_LIMIT: String = "{0} caractères au plus (espaces compris) ; {1} pour tout le formulaire."
    const val BODY_TOO_LONG_CHARS: String = "Le texte a {0} caractères, {1} au plus. Rien n’a été enregistré : raccourcissez-le (il reste dans le formulaire), puis enregistrez de nouveau."
    const val FIELD_TOO_LONG: String = "Le champ « {0} » a {1} caractères, {2} au plus. Rien n’a été enregistré : raccourcissez-le, puis enregistrez de nouveau."
    const val FORM_TOO_LONG: String = "Le formulaire a {0} caractères en tout, {1} au plus. Rien n’a été enregistré : raccourcissez le texte, puis enregistrez de nouveau."
    const val JOURNAL_FAILED_TITLE: String = "Journal non écrit"
    const val JOURNAL_FAILED: String = "La version {0} du brouillon est enregistrée, mais sa ligne du journal de l’équipe n’a pas pu être écrite. Signalez-le au Directeur technique avant de continuer : chaque enregistrement doit être tracé."
    const val SAVE: String = "Enregistrer"
    const val PREVIEW: String = "Prévisualiser sans enregistrer"
    const val DATE_HINT: String = "AAAA-MM-JJ"
    const val YEAR_HINT: String = "AAAA, AAAA-MM ou AAAA-MM-JJ"
    const val NUMBER_HINT: String = "Écriture française : 4 210 345,60"
    const val URL_HINT: String = "Adresse https:// (jamais ouverte par le serveur)"
    const val LINK_HINT: String = "Identifiant d’un contenu publié ou d’un autre brouillon"
    const val KNOWN_SLUGS: String = "Identifiants connus de ce type :"

    const val SUMMARY_TITLE: String = "Problèmes à corriger"
    const val SUMMARY_COUNT: String = "Ce brouillon a {0} problème(s). Il est enregistré, mais ne pourra pas partir en relecture tant qu’ils restent."
    const val NO_PROBLEM: String = "Aucun problème : ce brouillon pourra partir en relecture."
    const val SAVED: String = "Brouillon enregistré."
    const val UNCHANGED: String = "Rien n’a changé depuis la dernière version : aucune version ajoutée."
    const val CREATED: String = "Brouillon créé."
    const val CONFLICT: String = "Une autre version a été enregistrée entre-temps (version {0}). Rien n’a été écrasé : comparez ci-dessous, puis enregistrez de nouveau pour garder votre texte."
    const val CONFLICT_DIFF: String = "Différences entre la dernière version enregistrée et votre texte"
    const val SLUG_TAKEN: String = "Un autre brouillon en cours porte déjà cet identifiant : choisissez-en un autre, ou reprenez ce brouillon."
    const val NOT_EDITABLE: String = "Ce brouillon n’est plus modifiable (il est en relecture ou terminé)."
    const val UNKNOWN_TYPE: String = "Ce type de contenu n’existe pas dans cet espace."
    const val NOT_PUBLISHED: String = "Aucun contenu publié de cet espace ne porte cet identifiant."
    const val TOO_MANY_ROWS: String = "Trop de lignes pour ce champ."

    const val PREVIEW_TITLE: String = "Aperçu"
    const val PREVIEW_INTRO: String = "Le contenu tel que le site le montrerait (sans le glossaire). Rien n’est publié."
    const val PREVIEW_IMPOSSIBLE: String = "L’aperçu n’est pas possible tant que les champs communs (identifiant, titre, origine, dates) ne sont pas valides."
    const val PREVIEW_TAGS: String = "Étiquettes de l’événement construit"
    const val PREVIEW_TAG_NAME: String = "Nom"
    const val PREVIEW_TAG_VALUES: String = "Valeurs"
    const val PREVIEW_ORIGIN_AI: String = "Produit avec l’aide d’une IA"
    const val PREVIEW_ORIGIN_HUMAN: String = "Rédigé sans IA"
    const val DIFF_TITLE: String = "Différences avec la version publiée"
    const val DIFF_NONE_PUBLISHED: String = "Aucune version publiée : c’est un contenu nouveau."
    const val DIFF_SAME: String = "Aucune différence avec la version publiée."
    const val DIFF_TOO_LONG: String = "Textes trop longs pour être comparés ligne à ligne."
    const val DIFF_ADDED: String = "ajouté"
    const val DIFF_REMOVED: String = "retiré"
    const val DIFF_LEGEND: String = "Les lignes précédées de « + » sont ajoutées, celles précédées de « - » sont retirées."
    const val VERSIONS_TITLE: String = "Versions"
    const val VERSION: String = "Version"
    const val LINKS_LABEL: String = "Pages du brouillon"
    const val LINK_FORM: String = "Formulaire"
    const val LINK_PREVIEW: String = "Aperçu"
    const val LINK_DIFF: String = "Différences avec la version publiée"
    const val LINK_VERSIONS: String = "Versions"

    // Review (step 4) ----------------------------------------------------------------------------------
    const val QUEUE_COMPRENDRE: String = "Relecture Comprendre"
    const val QUEUE_VOIR: String = "Relecture Voir"
    const val QUEUE_GONE: String = "Cette version n’est plus dans la file (elle a été relue, ou une nouvelle version l’a remplacée)."
    const val QUEUE_DECIDE_ON_PAGE: String = "Les décisions se prennent sur la page de relecture du brouillon, dans la rédaction de l’espace : le message aux auteurs y reste, hors du journal de l’équipe."
    const val QUEUE_OPEN_REVIEW: String = "Ouvrir la page de relecture"
    const val QUEUE_CORRECTION: String = "correction"
    const val COLUMN_STATUS: String = "État"
    const val SLUG_LABEL: String = "Identifiant"
    const val REVIEW_LINK: String = "Relire"
    const val LINK_REVIEW: String = "Relecture"
    const val REVIEW_TITLE: String = "Relecture"
    const val REVIEW_AUTHOR: String = "Auteur ou autrice"
    const val REVIEW_SUBMITTED: String = "Soumis le"
    const val REVIEW_DEADLINE: String = "À relire avant le"
    const val REVIEW_LEVEL: String = "Importance"
    const val REVIEW_SUBMITTED_VERSION: String = "Version soumise"
    const val REVIEW_CONTENT: String = "Contenu soumis"
    const val REVIEW_HISTORY: String = "Décisions précédentes"
    const val REVIEW_HISTORY_EMPTY: String = "Aucune décision pour l’instant."
    const val REVIEW_BY: String = "par"
    const val REVIEW_NOT_IN_REVIEW: String = "Ce brouillon n’est pas en relecture : aucune décision à prendre."
    const val REVIEW_YOU_WROTE: String = "Vous avez écrit ce brouillon : un autre éditeur ou une autre éditrice doit le relire."
    const val REVIEW_NO_PERMISSION: String = "Votre rôle ne permet pas de relire les brouillons de cet espace."
    const val REVIEW_PROBLEMS: String = "Les règles ont trouvé {0} problème(s) dans la version soumise : demandez des modifications, elle ne peut pas être approuvée."
    const val REVIEW_ACTIONS: String = "Décision"
    const val REVIEW_MESSAGE_LABEL: String = "Message aux auteurs (obligatoire)"
    const val REVIEW_REASON_LABEL: String = "Motif du refus (obligatoire)"
    const val REVIEW_TEXT_HINT: String = "{0} caractères au plus. Visible par l’équipe de rédaction, gardé avec le brouillon ; le journal de l’équipe n’en garde que la décision."
    const val REQUEST_CHANGES: String = "Demander des modifications"
    const val REJECT: String = "Refuser"
    const val APPROVE: String = "Approuver pour publication"
    const val APPROVE_HINT: String = "La version devient « prête à signer ». Vous confirmez ensuite votre approbation avec votre clé d’accès sur la page du service de signature ; un contenu qui met en cause une personne (méthode M17) y demande aussi l’approbation d’une deuxième personne."
    const val SENSITIVE_HINT: String = "Action sensible : votre clé d’accès vous sera redemandée si la dernière vérification date de plus de 5 minutes."
    const val DONE_CHANGES_REQUESTED: String = "Modifications demandées : le brouillon est de nouveau modifiable par ses auteurs."
    const val DONE_REJECTED: String = "Brouillon refusé."
    const val DONE_READY_TO_SIGN: String = "Version approuvée : elle est prête à signer."
    const val SIGNER_NOT_PLUGGED: String = "Le service de signature n’est pas encore branché : la version reste prête à signer, rien n’est publié."
    const val SIGNER_TITLE: String = "Signature"
    const val SIGNER_UNAVAILABLE: String = "La signature n’est pas encore disponible pour cet espace : rien n’est envoyé, et un contenu n’est jamais publié sans signature."
    const val SIGNER_AWAITING: String = "En attente des approbations sur la page du service de signature : {0} sur {1}."
    const val SIGNER_AWAITING_ONE: String = "Une personne du groupe de rédaction qui n’a pas écrit le brouillon, en général celle qui l’a approuvé ici, y confirme l’approbation avec sa clé d’accès."
    const val SIGNER_AWAITING_TWO: String = "Ce contenu met en cause une personne (méthode M17) : deux personnes qui n’ont pas écrit le brouillon y relisent et approuvent le contenu, chacune avec sa clé d’accès."
    const val SIGNER_LINK: String = "Approuver sur la page de signature"
    const val SIGNER_REFUSED: String = "Le service de signature a refusé cette version (code « {0} »)."
    const val SIGNER_PUBLISHED: String = "Contenu signé et publié."
    const val ALERT_SIGNING_REFUSED: String = "Rédaction : la signature d’un contenu approuvé a été refusée"
    const val SIGNER_PENDING: String = "Les approbations sont réunies ; la signature est demandée et pas encore donnée. Rechargez la page dans un moment."
    const val SIGNER_DOWN: String = "Le service de signature ne répond pas pour l’instant : rien n’est perdu, la demande sera reprise au prochain chargement de cette page."
    const val SIGNER_EXPIRED: String = "La demande de signature a expiré (7 jours sans les approbations demandées) : relancez-la, un nouvel événement sera préparé avec une nouvelle date."
    const val SIGNER_REFUSED_HINT: String = "Rien n’est publié. Après avoir réglé la cause, relancez la signature ou retirez la version."
    const val RETRY: String = "Relancer la signature"
    const val RETRY_HINT: String = "Un nouvel événement est préparé à partir de la version approuvée, avec une nouvelle date, et soumis de nouveau au service de signature."
    const val WITHDRAW: String = "Retirer la version approuvée"
    const val WITHDRAW_REASON_LABEL: String = "Motif du retrait (obligatoire)"
    const val WITHDRAW_HINT: String = "La version ne sera ni signée ni publiée ; le brouillon redevient modifiable par ses auteurs, qui enregistrent une nouvelle version avant de le soumettre de nouveau."
    const val SIGNING_ACTIONS: String = "Signature : actions"
    const val SIGNING_YOU_WROTE: String = "Vous avez écrit ce brouillon : la relance et le retrait reviennent à un autre éditeur ou une autre éditrice."
    const val DONE_WITHDRAWN: String = "Version retirée : le brouillon est de nouveau modifiable par ses auteurs."
    const val DONE_RETRIED: String = "Signature relancée avec un nouvel événement."
    const val LAST_WITHDRAWAL: String = "Motif du retrait de la version approuvée"
    const val STATE_SIGNING_REFUSED: String = "La signature de cette version a été refusée : rien n’est publié. Voir la page de relecture."
    const val STATE_PUBLISHED: String = "Ce contenu est signé et publié : une nouvelle modification passe par « Mettre à jour un contenu publié »."

    const val SUBMIT_TITLE: String = "Envoyer en relecture"
    const val SUBMIT_INTRO: String = "La version {0} partira en relecture ; elle ne sera plus modifiable, sauf si des modifications sont demandées."
    const val SUBMIT: String = "Soumettre pour relecture"
    const val SUBMITTED: String = "Brouillon envoyé en relecture."
    const val SUBMIT_REFUSED_PROBLEMS: String = "Ce brouillon a encore {0} problème(s) : corrigez-les avant de le soumettre (liste ci-dessous)."
    const val SUBMIT_REFUSED_NOT_WRITER: String = "Seules les personnes qui ont écrit ce brouillon peuvent le soumettre."
    const val SUBMIT_REFUSED_ALREADY_REVIEWED: String = "Cette version a déjà été relue : enregistrez une nouvelle version avant de la soumettre de nouveau."
    const val STALE_VERSION: String = "Une autre version a été enregistrée ou soumise entre-temps : rechargez la page."
    const val WRONG_STATE: String = "Cette action n’est plus possible : le brouillon est maintenant dans l’état « {0} »."
    const val REVIEW_REFUSED_WRITER: String = "Vous êtes l’auteur ou l’autrice de cette version : un autre éditeur ou une autre éditrice doit la relire."
    const val REASON_REQUIRED: String = "Écrivez le message ou le motif (de 1 à {0} caractères)."
    const val STATE_IN_REVIEW: String = "Ce brouillon est en relecture : il n’est pas modifiable tant qu’aucune modification n’est demandée."
    const val STATE_READY_TO_SIGN: String = "Cette version est approuvée et prête à signer : elle n’est plus modifiable."
    const val STATE_REJECTED: String = "Ce brouillon a été refusé : il n’est plus modifiable."
    const val STATE_FINISHED: String = "Ce brouillon n’est plus modifiable."
    const val LAST_MESSAGE: String = "Dernier message de la relecture"
    const val JOURNAL_FAILED_DECISION: String = "La décision est enregistrée, mais sa ligne du journal de l’équipe n’a pas pu être écrite. Signalez-le au Directeur technique avant de continuer."
    const val JOURNAL_FAILED_BEFORE: String = "Le journal de l’équipe n’a pas pu être écrit : rien n’a été fait. Réessayez, puis signalez-le au Directeur technique."

    /** The French label of the status of a draft. */
    fun status(status: DraftStatus): String = when (status) {
        DraftStatus.DRAFT -> "Brouillon"
        DraftStatus.IN_REVIEW -> "En relecture"
        DraftStatus.CHANGES_REQUESTED -> "Modifications demandées"
        DraftStatus.READY_TO_SIGN -> "Prêt à signer"
        DraftStatus.SIGNING -> "En signature"
        DraftStatus.PUBLISHED -> "Publié"
        DraftStatus.REJECTED -> "Refusé"
        DraftStatus.WITHDRAWN -> "Retiré"
        DraftStatus.SIGNING_REFUSED -> "Signature refusée"
    }

    /** The French text of a translation key of a schema, or [fallback] when it has none. */
    fun label(key: String?, fallback: String = key.orEmpty()): String = key?.let(labels::get) ?: fallback

    /** The label of a common field of every article. */
    fun common(key: String): String = commonLabels[key] ?: key

    /** The help of a common field, if any. */
    fun commonHelp(key: String): String? = commonHelps[key]

    /** The label of a field of [schema] (common or specific). */
    fun fieldLabel(schema: ContentSchema, key: String?): String? = if (key == null) null else when (key) {
        EditorialEngine.BODY -> BODY_LABEL
        in ContentSchema.COMMON_KEYS -> common(key)
        else -> schema.field(key)?.let { label(it.labelKey, it.key) } ?: key
    }

    private fun positionLabel(field: FieldSpec?, name: String): String = when (val value = field?.value) {
        is ValueSpec.Positional -> value.positions.firstOrNull { it.name == name }?.let { label(it.labelKey, it.name) } ?: name
        else -> name
    }

    /**
     * The French message of a problem of [EditorialEngine.check], located with the labels of the form:
     * « Période, ligne 1, Début : … ». The templates are those of [EditorialMessages].
     */
    fun problem(schema: ContentSchema, problem: Problem): String {
        serverProblems[problem.code]?.let { return it }
        var text = EditorialMessages.template(problem.code) ?: problem.code
        problem.args.forEachIndexed { i, arg -> text = text.replace("{$i}", arg) }
        val field = problem.field?.let(schema::field)
        val where = buildList {
            fieldLabel(schema, problem.field)?.let(::add)
            problem.occurrence?.let { add("${ROW.lowercase()} ${it + 1}") }
            problem.position?.let { add(positionLabel(field, it)) }
        }
        return if (where.isEmpty()) text.replaceFirstChar { it.uppercase() } else where.joinToString(", ") + " : " + text
    }

    /** A number written the French way, with a narrow no-break space between thousands: « 60 000 ». */
    fun number(value: Int): String = value.toString().reversed().chunked(3).joinToString("\u202F").reversed()

    fun format(template: String, vararg args: Any): String {
        var text = template
        args.forEachIndexed { i, arg -> text = text.replace("{$i}", arg.toString()) }
        return text
    }

    /** The problems found by the server around the engine (an update of a published content), already in French. */
    private val serverProblems: Map<String, String> = mapOf(
        UpdateChecks.CHANGE_KIND_REQUIRED to CHANGE_KIND_REQUIRED,
        UpdateChecks.CHANGE_NOTE_REQUIRED to CHANGE_NOTE_REQUIRED,
    )

    private val commonLabels: Map<String, String> = mapOf(
        ContentSchema.SLUG to "Identifiant (adresse du contenu)",
        ContentSchema.TITLE to "Titre",
        ContentSchema.SUMMARY to "Résumé",
        ContentSchema.TOPICS to "Thèmes",
        ContentSchema.TYPE to "Type",
        ContentSchema.ORIGIN to "Origine (IA ou non)",
        ContentSchema.DURATION to "Durée de lecture (minutes)",
        ContentSchema.PUBLISHED_AT to "Date de première publication",
        ContentSchema.UPDATED_AT to "Date de mise à jour",
    )

    private val commonHelps: Map<String, String> = mapOf(
        ContentSchema.SLUG to "Lettres minuscules, chiffres et tirets : par exemple eau-du-robinet. Il ne change plus après la publication.",
        ContentSchema.TITLE to "200 caractères au plus. Un contenu d’exemple commence par [EXEMPLE].",
        ContentSchema.SUMMARY to "Une ou deux phrases, 500 caractères au plus.",
        ContentSchema.TOPICS to "Séparés par des virgules : institutions, parlement.",
        ContentSchema.DURATION to "De 1 à 240 minutes.",
    )

    private val labels: Map<String, String> = mapOf(
        // Types of Comprendre.
        "editorial.type.fiche_pratique" to "Fiche pratique",
        "editorial.type.parcours" to "Parcours « Comprendre en 5 minutes »",
        "editorial.type.decryptage" to "Décryptage",
        "editorial.type.histoire" to "Histoire des luttes",
        "editorial.type.qui_decide" to "« Qui décide de ça ? »",
        "editorial.type.methode" to "Page de méthode",
        "editorial.type.glossaire" to "Entrée du glossaire",
        // Fields.
        "editorial.field.verdict" to "Verdict",
        "editorial.help.verdict" to "Ce que disent les faits de l’affirmation vérifiée.",
        "editorial.field.claim" to "Affirmation vérifiée",
        "editorial.help.claim" to "L’affirmation telle qu’elle circule, en une phrase.",
        "editorial.field.claim_context" to "Contexte de l’affirmation",
        "editorial.help.claim_context" to "Où et quand elle circule (facultatif).",
        "editorial.field.period" to "Période",
        "editorial.help.period" to "Début obligatoire, fin facultative (inconnue ou en cours).",
        "editorial.period.start" to "Début",
        "editorial.period.end" to "Fin",
        "editorial.field.country" to "Pays",
        "editorial.field.claims" to "Revendications",
        "editorial.help.claims" to "Séparées par « ; ».",
        "editorial.field.results" to "Résultats obtenus",
        "editorial.help.results" to "Séparés par « ; ».",
        "editorial.field.persons" to "Personnes citées",
        "editorial.help.persons" to "Identifiants des fiches de personnes de « Voir », séparés par des virgules.",
        "editorial.field.variants" to "Formes repérées dans les textes",
        "editorial.help.variants" to "Séparées par des virgules ; une virgule dans une forme s’écrit « \\, ».",
        "editorial.field.auto" to "Lien automatique dans les textes",
        "editorial.help.auto" to "« Oui » par défaut : la première occurrence du terme devient un lien vers sa définition.",
        "editorial.auto.oui" to "Oui",
        "editorial.auto.non" to "Non",
        "editorial.field.see_also" to "Voir aussi (autres entrées du glossaire)",
        "editorial.field.related" to "Contenus liés",
        "editorial.help.related" to "« genre:identifiant », séparés par des virgules ; genres : parcours, fiche, decryptage, qui-decide, histoire.",
        // Values.
        "comprendre.verdict.exact" to "Exact",
        "comprendre.verdict.plutot-exact" to "Plutôt exact",
        "comprendre.verdict.trompeur" to "Trompeur",
        "comprendre.verdict.contredit-par-les-faits" to "Contredit par les faits",
        "comprendre.verdict.inverifiable" to "Invérifiable",
        // Templates of an empty draft (the blocks already written).
        "editorial.template.fiche_pratique" to """
            ::: en-bref
            -
            :::

            ## Ce qu’il faut savoir

            ::: objections
            ? « Une idée reçue »
            > La réponse, avec sa source.
            :::

            ## Sources

            -
        """.trimIndent(),
        "editorial.template.parcours" to """
            Introduction du parcours.

            ## Première étape

            ::: quiz
            ? La question
            - [ ] Une réponse
            - [x] La bonne réponse
            > L’explication, avec sa source.
            :::

            ::: et-maintenant
            -
            :::

            ## Sources

            -
        """.trimIndent(),
        "editorial.template.decryptage" to """
            ::: en-bref
            -
            :::

            ## Ce que dit l’affirmation

            ## Ce que disent les textes

            ::: objections
            ? « Une objection »
            > La réponse, avec sa source.
            :::

            ## Sources

            -
        """.trimIndent(),
        "editorial.template.histoire" to """
            ::: en-bref
            -
            :::

            ## Le contexte

            ## Ce qui a été obtenu

            ::: objections
            ? « Une idée reçue »
            > La réponse, avec sa source.
            :::

            ## Sources

            -
        """.trimIndent(),
        "editorial.template.qui_decide" to """
            ::: decideurs
            - commune | maire | Ce que décide la commune, avec sa source.
            :::

            ::: en-bref
            -
            :::

            ## Le rôle de la commune

            ::: et-maintenant
            -
            :::

            ## Sources

            -
        """.trimIndent(),
    )
}
