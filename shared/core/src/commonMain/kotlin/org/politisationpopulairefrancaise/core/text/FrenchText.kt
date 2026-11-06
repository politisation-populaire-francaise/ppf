package org.politisationpopulairefrancaise.core.text

/**
 * Plain-text tools for French, shared by the device and the server: searching the demands wall,
 * comparing a new demand with the existing ones (DE-18, DE-19), matching territories.
 *
 * Everything here is deterministic and runs offline. It compares words, not meanings: "cantine à
 * un euro" and "repas scolaire à prix réduit" look different to it. The meaning-based comparison
 * belongs to the self-hosted AI model (DE-19), plugged in behind the same results later.
 */
object FrenchText {

    /**
     * The folded form of a text: lowercase, accents and other diacritics removed, ligatures
     * spelled out (œ, æ, ß), every character that is neither a letter nor a digit replaced by a
     * space, runs of spaces collapsed, ends trimmed. "Œuvres  d'Été - Lyon 3e" folds to
     * "oeuvres d ete lyon 3e".
     */
    fun fold(text: String): String {
        val decomposed = normalizeNfkd(text.lowercase())
        val out = StringBuilder(decomposed.length)
        var pendingSpace = false
        for (char in decomposed) {
            if (char.category == CharCategory.NON_SPACING_MARK || char.category == CharCategory.COMBINING_SPACING_MARK || char.category == CharCategory.ENCLOSING_MARK) continue
            val spelled = when (char) {
                'œ' -> "oe"
                'æ' -> "ae"
                'ß' -> "ss"
                'ø' -> "o"
                'đ' -> "d"
                'ł' -> "l"
                else -> null
            }
            if (spelled != null || char.isLetterOrDigit()) {
                if (pendingSpace && out.isNotEmpty()) out.append(' ')
                pendingSpace = false
                if (spelled != null) out.append(spelled) else out.append(char)
            } else {
                pendingSpace = true
            }
        }
        return out.toString()
    }

    /** The words of a text, folded, in order (duplicates kept). */
    fun words(text: String): List<String> = fold(text).split(' ').filter { it.isNotEmpty() }

    /**
     * The meaningful words of a text, reduced to a common root ([stem]), without the small words
     * of the language ([STOPWORDS]) and without the words every demand uses ("demander",
     * "revendication"...). Order kept, duplicates removed.
     */
    fun keywords(text: String): List<String> =
        words(text).asSequence()
            .filter { it !in STOPWORDS }
            .map(::stem)
            .filter { it.length >= 2 || it.all(Char::isDigit) }
            .distinct()
            .toList()

    /**
     * A light root of a folded word, enough to match "cantines" with "cantine", "gratuité" with
     * "gratuit", "hôpitaux" with "hôpital": the plural mark goes (-s, -x, -aux becomes -al), then
     * a final -e, then the word is cut after [STEM_LENGTH] letters. Numbers are kept as they are.
     * Two different words may share a root ("éducation", "éducatif"): close enough for a hint.
     */
    fun stem(word: String): String {
        if (word.any(Char::isDigit)) return word
        var w = word
        when {
            w.length > 4 && w.endsWith("eaux") -> w = w.dropLast(1)
            w.length > 4 && w.endsWith("aux") -> w = w.dropLast(3) + "al"
            w.length > 3 && (w.endsWith('s') || w.endsWith('x')) && !w.endsWith("ss") -> w = w.dropLast(1)
        }
        if (w.length > 4 && w.endsWith('e')) w = w.dropLast(1)
        return if (w.length > STEM_LENGTH) w.take(STEM_LENGTH) else w
    }

    /** True when every word of [query] starts a word of [text] (both folded): the search of the wall. */
    fun matchesAllWords(text: String, query: String): Boolean {
        val wanted = words(query)
        if (wanted.isEmpty()) return true
        val available = words(text)
        return wanted.all { word -> available.any { it.startsWith(word) } }
    }

    /** Length after which [stem] cuts a word: "transparence" and "transports" keep apart. */
    const val STEM_LENGTH: Int = 7

    /**
     * Small words of French that carry no subject (articles, pronouns, prepositions, auxiliaries),
     * plus the words any demand uses, folded. Kept short on purpose: a word removed here can never
     * help to find a duplicate.
     */
    val STOPWORDS: Set<String> = setOf(
        // articles, prepositions, conjunctions
        "a", "au", "aux", "avec", "ce", "ces", "cet", "cette", "c", "d", "dans", "de", "des", "du", "en", "et", "l", "la", "le", "les",
        "ou", "par", "pour", "sans", "sous", "sur", "un", "une", "vers", "chez", "entre", "afin", "ainsi", "aussi", "donc", "or", "ni",
        "car", "dont", "comme", "si", "que", "qu", "qui", "quoi", "y", "ne", "pas", "plus", "moins", "tres", "trop", "tout", "tous",
        "toute", "toutes", "chaque", "meme", "memes", "autre", "autres", "ici", "cela", "ceci", "ca", "celle", "celui", "ceux",
        "celles", "leur", "leurs", "notre", "nos", "votre", "vos", "son", "sa", "ses", "mon", "ma", "mes", "ton", "ta", "tes",
        // pronouns
        "je", "j", "tu", "il", "elle", "on", "nous", "vous", "ils", "elles", "me", "m", "te", "t", "se", "s", "lui", "eux", "moi", "toi", "n",
        // auxiliaries and empty verbs
        "est", "sont", "etre", "ete", "sera", "seront", "soit", "soient", "ont", "avoir", "fait", "faire", "doit", "doivent", "peut", "peuvent",
        // the vocabulary of every demand
        "revendication", "revendications", "demande", "demandes", "demander", "demandons", "exiger", "exigeons", "exige", "voulons",
        "souhaitons", "proposons", "proposition", "propositions", "exemple",
    )
}
