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

    const val HOME_INTRO: String = "Écrivez ici les contenus de l’espace. Un brouillon s’enregistre même incomplet : rien ne se perd. Il ne part en relecture qu’une fois sans problème (étape suivante du circuit)."
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
    const val CHANGE_NOTE: String = "Note de correction (publique, au journal des corrections)"
    const val BODY_LABEL: String = "Texte (Markdown)"
    const val BODY_HINT: String = "Titres « ## », listes « - », gras « **…** », liens « [texte](https://…) ». Aucun HTML : il serait affiché tel quel."
    const val BODY_BLOCKS: String = "Blocs attendus :"
    const val BODY_SECTIONS: String = "Sections obligatoires :"
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

    /** The French label of the status of a draft. */
    fun status(status: DraftStatus): String = when (status) {
        DraftStatus.DRAFT -> "Brouillon"
        DraftStatus.IN_REVIEW -> "En relecture"
        DraftStatus.CHANGES_REQUESTED -> "Modifications demandées"
        DraftStatus.APPROVED -> "Approuvé"
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
    fun fieldLabel(schema: ContentSchema, key: String?): String? = when (key) {
        null -> null
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

    fun format(template: String, vararg args: Any): String {
        var text = template
        args.forEachIndexed { i, arg -> text = text.replace("{$i}", arg.toString()) }
        return text
    }

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
