package org.politisationpopulairefrancaise.core.revendications.checks

import org.politisationpopulairefrancaise.core.revendications.RevendicationThemes
import org.politisationpopulairefrancaise.core.text.FrenchText

/**
 * The deterministic rules of the guided form (DE-18): lengths, one idea per demand, themes,
 * territory, and no personal data. They run on the device while the author types and again on the
 * server, so a modified application cannot skip them. Only these rules may block a draft: the
 * opinions of the AI model never do.
 */
object DraftRules {
    const val TITLE_MIN: Int = 15
    const val TITLE_MAX: Int = 280
    const val SUMMARY_MAX: Int = 300
    const val SECTION_MAX: Int = 4_000
    const val TERRITORY_MAX: Int = 80

    fun check(draft: RevendicationDraft): List<ProposalIssue> = buildList {
        val title = draft.title.trim()
        when {
            title.length < TITLE_MIN -> add(blocking(IssueKind.TOO_SHORT, DraftField.TITLE))
            title.length > TITLE_MAX -> add(blocking(IssueKind.TOO_LONG, DraftField.TITLE))
            sentences(title) > 2 -> add(warning(IssueKind.TOO_LONG, DraftField.TITLE))
        }
        if (draft.summary.trim().length > SUMMARY_MAX) add(blocking(IssueKind.TOO_LONG, DraftField.SUMMARY))
        listOf(DraftField.WHY to draft.why, DraftField.ASKS to draft.asks, DraftField.EXISTING to draft.existing).forEach { (field, text) ->
            if (text.trim().length > SECTION_MAX) add(blocking(IssueKind.TOO_LONG, field))
        }
        // The style hints only read a title of an acceptable length (no pattern runs on an oversized text).
        if (title.length <= TITLE_MAX && isShouting(title)) add(warning(IssueKind.ALL_CAPS, DraftField.TITLE))
        if (title.length in TITLE_MIN..TITLE_MAX && hasSeveralIdeas(title)) add(warning(IssueKind.SEVERAL_IDEAS, DraftField.TITLE))

        val known = draft.themes.filter { RevendicationThemes.byId(it) != null }.distinct()
        when {
            known.isEmpty() -> add(blocking(IssueKind.MISSING_THEME, DraftField.THEMES))
            known.size > RevendicationDraft.MAX_THEMES -> add(blocking(IssueKind.TOO_MANY_THEMES, DraftField.THEMES))
        }
        val territory = draft.territory.trim()
        if (FrenchText.fold(territory).isEmpty()) add(blocking(IssueKind.MISSING_TERRITORY, DraftField.TERRITORY))
        if (territory.length > TERRITORY_MAX) add(blocking(IssueKind.TOO_LONG, DraftField.TERRITORY))

        draft.texts().entries.firstOrNull { (_, text) -> containsPersonalData(text) }?.let { (field, _) ->
            add(blocking(IssueKind.PERSONAL_DATA, field))
        }
    }

    /**
     * True when [text] contains an e-mail address, a French phone number, an IBAN or a French
     * social security number. The match itself is never kept nor reported.
     */
    fun containsPersonalData(text: String): Boolean = PERSONAL_PATTERNS.any { it.containsMatchIn(text) }

    /**
     * One demand, one idea: true when the demand chains several actions ("Augmenter le SMIC et
     * baisser la TVA"), that is at least two clauses (split on "et", commas, semicolons, "ainsi
     * que", "puis") that start with a verb in the infinitive, or when it is written as a list.
     * A hint only: it can be wrong both ways.
     */
    fun hasSeveralIdeas(title: String): Boolean {
        if ('\n' in title.trim() || LIST_MARK.containsMatchIn(title)) return true
        val actions = CLAUSE_SEPARATOR.split(title.lowercase())
            .map { FrenchText.fold(it).substringBefore(' ') }
            .count(::looksLikeInfinitive)
        return actions >= 2
    }

    private fun looksLikeInfinitive(word: String): Boolean =
        word.length >= 5 && (word.endsWith("er") || word.endsWith("ir")) && word !in NOT_VERBS

    private fun sentences(text: String): Int = SENTENCE_END.findAll(text.trim()).count() + if (text.trim().lastOrNull() in setOf('.', '!', '?', '…')) 0 else 1

    private fun isShouting(text: String): Boolean {
        val letters = text.filter(Char::isLetter)
        return letters.length >= 12 && letters.count(Char::isUpperCase) > letters.length * 0.7
    }

    private fun blocking(kind: IssueKind, field: DraftField) = ProposalIssue(kind, IssueSeverity.BLOCKING, IssueSource.RULES, field)

    private fun warning(kind: IssueKind, field: DraftField) = ProposalIssue(kind, IssueSeverity.WARNING, IssueSource.RULES, field)

    private val SENTENCE_END = Regex("""[.!?…]+(\s+|$)""")
    private val CLAUSE_SEPARATOR = Regex("""\s*(?:[,;:]|\bet\b|\bainsi que\b|\bpuis\b)\s*""")
    private val LIST_MARK = Regex("""(^|\s)(?:\d+[.)]|[-•*])\s+\S.*(\s(?:\d+[.)]|[-•*])\s+\S)""")

    private val PERSONAL_PATTERNS = listOf(
        // e-mail address; every repetition is bounded so that a long text without "@" stays cheap to scan
        Regex("""[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9-]{1,63}(?:\.[A-Za-z0-9-]{1,63}){0,8}\.[A-Za-z]{2,24}"""),
        // French phone number: 0X XX XX XX XX, +33 X XX XX XX XX, 0033...
        Regex("""(?:\+33\s?|0033\s?|\b0)[1-9](?:[\s.-]?\d{2}){4}\b"""),
        // IBAN: two letters, two check digits, then 11 to 30 letters or digits in groups
        Regex("""\b[A-Z]{2}\d{2}(?:\s?[A-Z0-9]{4}){3,7}(?:\s?[A-Z0-9]{1,3})?\b"""),
        // French social security number (NIR), 13 digits plus an optional 2-digit key, spaces allowed
        Regex("""\b[12]\s?\d{2}\s?(?:0[1-9]|1[0-2]|[2-9]\d)\s?(?:\d{2}|2[ABab])\s?\d{3}\s?\d{3}(?:\s?\d{2})?\b"""),
    )

    /** Words ending like an infinitive that usually are not verbs at the start of a clause. */
    private val NOT_VERBS: Set<String> = setOf(
        "premier", "dernier", "entier", "hiver", "loisir", "avenir", "plaisir", "desir", "souvenir", "pouvoir", "devoir", "savoir",
        "espoir", "soir", "noir", "miroir", "trottoir", "couloir", "foyer", "loyer", "loyers", "metier", "quartier", "papier",
        "dossier", "chantier", "atelier", "janvier", "fevrier", "calendrier", "sentier", "panier", "cahier", "acier", "fier", "cher",
        "leur", "leurs", "plusieurs", "toujours", "jour", "pour", "sur", "contre", "entre", "notre", "votre", "chaque", "cuir",
    )
}
