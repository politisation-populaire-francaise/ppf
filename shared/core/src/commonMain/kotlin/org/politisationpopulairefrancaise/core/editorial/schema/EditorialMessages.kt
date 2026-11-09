package org.politisationpopulairefrancaise.core.editorial.schema

/** The problem codes of the engine (translation keys; the French texts are in [EditorialMessages]). */
object Codes {
    const val TYPE_MISMATCH: String = "editorial.problem.type_mismatch"
    const val FIELD_MISSING: String = "editorial.problem.field_missing"
    const val FIELD_REPEATED: String = "editorial.problem.field_repeated"
    const val FIELD_TOO_FEW: String = "editorial.problem.field_too_few"
    const val FIELD_TOO_MANY: String = "editorial.problem.field_too_many"
    const val FIELD_UNKNOWN: String = "editorial.problem.field_unknown"
    const val VALUE_EMPTY: String = "editorial.problem.value_empty"
    const val VALUE_TOO_LONG: String = "editorial.problem.value_too_long"
    const val VALUE_MULTILINE: String = "editorial.problem.value_multiline"
    const val VALUE_CHOICE: String = "editorial.problem.value_choice"
    const val VALUE_DATE: String = "editorial.problem.value_date"
    const val VALUE_DATE_OR_YEAR: String = "editorial.problem.value_date_or_year"
    const val VALUE_NUMBER: String = "editorial.problem.value_number"
    const val VALUE_NUMBER_DECIMALS: String = "editorial.problem.value_number_decimals"
    const val VALUE_NUMBER_NEGATIVE: String = "editorial.problem.value_number_negative"
    const val VALUE_NUMBER_MIN: String = "editorial.problem.value_number_min"
    const val VALUE_NUMBER_MAX: String = "editorial.problem.value_number_max"
    const val VALUE_URL: String = "editorial.problem.value_url"
    const val VALUE_SLUG: String = "editorial.problem.value_slug"
    const val VALUE_MINUTES: String = "editorial.problem.value_minutes"
    const val VALUE_HASH: String = "editorial.problem.value_hash"
    const val VALUE_OFFICIAL_ID: String = "editorial.problem.value_official_id"
    const val VALUE_POSITIONS: String = "editorial.problem.value_positions"
    const val VALUE_POSITION_REQUIRED: String = "editorial.problem.value_position_required"
    const val VALUE_NEXT: String = "editorial.problem.value_next"
    const val VALUE_NEXT_TARGET: String = "editorial.problem.value_next_target"
    const val LINK_UNKNOWN: String = "editorial.problem.link_unknown"
    const val LINK_WRONG_TYPE: String = "editorial.problem.link_wrong_type"
    const val LINK_KIND: String = "editorial.problem.link_kind"
    const val BODY_TOO_LONG: String = "editorial.problem.body_too_long"
    const val BODY_SECTION_MISSING: String = "editorial.problem.body_section_missing"
    const val BODY_BLOCK_TOO_FEW: String = "editorial.problem.body_block_too_few"
    const val BODY_BLOCK_TOO_MANY: String = "editorial.problem.body_block_too_many"
    const val BODY_INVALID: String = "editorial.problem.body_invalid"
    const val UNBUILDABLE: String = "editorial.problem.unbuildable"
    /** A problem of a [SchemaCheck] that wraps an existing reader: args = [message of the reader]. */
    const val CHECK: String = "editorial.problem.check"
}

/**
 * The French texts of the problems, by key. `{0}`, `{1}`… are the [Problem.args]. The server and the
 * application may override them with their own externalized texts; these are the default (French is the
 * default language). The messages of the existing readers of blocks (ParcoursFormat, QuiDecide,
 * ContentBlocks) are passed as an argument as they are written.
 */
object EditorialMessages {
    private val FRENCH: Map<String, String> = mapOf(
        Codes.TYPE_MISMATCH to "le type « {0} » ne correspond pas au formulaire « {1} »",
        Codes.FIELD_MISSING to "ce champ est obligatoire",
        Codes.FIELD_REPEATED to "ce champ ne peut être donné qu'une fois",
        Codes.FIELD_TOO_FEW to "il faut au moins {0} ligne(s)",
        Codes.FIELD_TOO_MANY to "{0} ligne(s) au plus",
        Codes.FIELD_UNKNOWN to "ce champ n'existe pas pour ce type de contenu",
        Codes.VALUE_EMPTY to "la valeur est vide",
        Codes.VALUE_TOO_LONG to "{1} caractères, {0} au plus",
        Codes.VALUE_MULTILINE to "le texte doit tenir sur une ligne",
        Codes.VALUE_CHOICE to "« {0} » n'est pas une valeur possible ({1})",
        Codes.VALUE_DATE to "« {0} » n'est pas une date AAAA-MM-JJ valide",
        Codes.VALUE_DATE_OR_YEAR to "« {0} » n'est ni une date AAAA-MM-JJ, ni un mois AAAA-MM, ni une année AAAA",
        Codes.VALUE_NUMBER to "« {0} » n'est pas un nombre écrit à la française (par exemple 4 210 345,60)",
        Codes.VALUE_NUMBER_DECIMALS to "« {0} » : un nombre entier est attendu, sans virgule",
        Codes.VALUE_NUMBER_NEGATIVE to "« {0} » : le nombre ne peut pas être négatif",
        Codes.VALUE_NUMBER_MIN to "« {0} » est plus petit que {1}",
        Codes.VALUE_NUMBER_MAX to "« {0} » est plus grand que {1}",
        Codes.VALUE_URL to "« {0} » n'est pas une adresse https:// valide",
        Codes.VALUE_SLUG to "« {0} » n'est pas un identifiant valide (lettres minuscules, chiffres et tirets)",
        Codes.VALUE_MINUTES to "« {0} » n'est pas une durée en minutes (de 1 à 240)",
        Codes.VALUE_HASH to "« {0} » n'est pas une empreinte SHA-256 (64 caractères hexadécimaux)",
        Codes.VALUE_OFFICIAL_ID to "« {0} » n'est pas un identifiant officiel reconnu",
        Codes.VALUE_POSITIONS to "{0} valeur(s) séparées par « | » au plus, {1} données",
        Codes.VALUE_POSITION_REQUIRED to "cette valeur est obligatoire",
        Codes.VALUE_NEXT to "« {0} » : une étape suivante s'écrit « libellé | cible »",
        Codes.VALUE_NEXT_TARGET to "« {0} » : la cible commence par fiche:, voir: ou espace:",
        Codes.LINK_UNKNOWN to "aucun contenu ne porte l'identifiant « {0} »",
        Codes.LINK_WRONG_TYPE to "« {0} » doit être de type {1}, c'est un contenu de type {2}",
        Codes.LINK_KIND to "« {0} » s'écrit « genre:identifiant », le genre étant parmi {1}",
        Codes.BODY_TOO_LONG to "le texte a {1} caractères, {0} au plus",
        Codes.BODY_SECTION_MISSING to "il manque la section « ## {0} »",
        Codes.BODY_BLOCK_TOO_FEW to "il faut au moins {1} bloc(s) « ::: {0} »",
        Codes.BODY_BLOCK_TOO_MANY to "{1} bloc(s) « ::: {0} » au plus",
        Codes.BODY_INVALID to "bloc « ::: {0} » : {1}",
        Codes.UNBUILDABLE to "le contenu ne peut pas être construit (vérifiez les champs communs)",
        Codes.CHECK to "{0}",
        // Rules of the schemas of Comprendre.
        "comprendre.rule.course_readable" to "parcours illisible : {0}",
        "comprendre.rule.qui_decide_readable" to "chaîne de décision illisible : {0}",
        "comprendre.rule.glossary_readable" to "l'application ne lirait pas cette entrée du glossaire",
        "comprendre.rule.glossary_definition" to "la définition (résumé) a {1} caractères, {0} au plus",
        "comprendre.rule.glossary_title" to "le terme (titre) est vide",
        "comprendre.rule.teaching_section_unknown" to "la section « ## {0} » n'existe pas dans une fiche pédagogique (sections possibles : {1})",
        "comprendre.rule.teaching_section_repeated" to "la section « ## {0} » est écrite {1} fois, une seule est possible",
        "comprendre.rule.teaching_section_order" to "les sections viennent dans cet ordre : {0} (« Pour aller plus loin » est facultative)",
        "comprendre.rule.teaching_section_empty" to "la section « ## {0} » est vide",
        "comprendre.rule.teaching_objections" to "le débat guidé présente au moins {0} objections, une de chaque côté du débat, dans un bloc « ::: objections » de la section « ## Débat guidé » ({1} trouvée(s))",
        "comprendre.rule.teaching_step" to "étape du déroulé mal écrite : « {0} » (attendu : « - 10 min : ce que fait la classe »)",
        "comprendre.rule.teaching_no_step" to "le déroulé n'a aucune étape minutée (« - 10 min : ce que fait la classe »)",
        "comprendre.rule.teaching_duration" to "les étapes du déroulé font {1} minutes en tout, la durée annoncée est de {0} minutes",
        "comprendre.rule.teaching_readable" to "l'application ne lirait pas cette fiche pédagogique (vérifiez le parcours lié, le niveau et le programme)",
        // Rules of the schemas of Voir.
        "voir.rule.unreadable" to "l'application ne lirait pas cette brique de « Voir » (vérifiez ses champs)",
        "voir.rule.line_ignored" to "l'application ignorerait {0} ligne(s) de ce champ",
        "voir.rule.case_needs_procedure" to "une affaire indique toujours l'étape de la procédure (présomption d'innocence)",
        "voir.rule.sentence_only_for_cases" to "une peine ne s'écrit que pour une affaire",
        "voir.rule.no_agent_name" to "n'écrivez jamais le nom ni la référence d'un mandataire financier (une personne privée)",
        "voir.rule.public_funding_year_once" to "l'aide publique de {0} est déjà donnée",
        "voir.rule.measure_id_once" to "l'identifiant de mesure « {0} » est déjà utilisé",
        "voir.rule.measure_part" to "la part ({1}) dépasse la valeur ({0})",
        "voir.rule.duplicate" to "« {0} » est déjà donné",
        "voir.rule.next_space" to "« {0} » n'est pas un espace de l'application",
        // Rules of the schemas of Agir (AG-62, the follow-ups of a mobilization).
        "agir.rule.suite_unreadable" to "l'application ne lirait pas cette suite (vérifiez le type de suite, la date, le statut, la mobilisation et les sources)",
        "agir.rule.suite_line_ignored" to "l'application ignorerait {0} ligne(s) de ce champ",
        "agir.rule.suite_reply_needs_person" to "la réponse d'un élu nomme sa fiche dans « Voir » (champ person), pour que le droit de réponse s'applique",
        "agir.rule.suite_mobilization_address" to "« {0} » n'est pas l'adresse d'une mobilisation de l'agenda (31923:<clé>:<identifiant>)",
        "agir.rule.suite_content_address" to "« {0} » n'est pas l'adresse d'un contenu (30023:<clé>:<identifiant>)",
        "agir.rule.suite_link_wrong_type" to "« {0} » doit être un contenu de type {1}, c'est un contenu de type {2}",
        "agir.rule.suite_duplicate" to "« {0} » est déjà donné",
    )

    /** The French template of [code], or null when it is unknown. */
    fun template(code: String): String? = FRENCH[code]

    /** The French text of [problem], located: « Champ « period », ligne 1, valeur « start » : … ». */
    fun french(problem: Problem): String {
        var text = FRENCH[problem.code] ?: problem.code
        problem.args.forEachIndexed { i, arg -> text = text.replace("{$i}", arg) }
        val where = buildList {
            when (problem.field) {
                null -> Unit
                EditorialEngine.BODY -> add("Texte")
                else -> add("Champ « ${problem.field} »")
            }
            problem.occurrence?.let { add("ligne ${it + 1}") }
            problem.position?.let { add("valeur « $it »") }
        }
        return if (where.isEmpty()) text.replaceFirstChar { it.uppercase() } else where.joinToString(", ") + " : " + text
    }
}
