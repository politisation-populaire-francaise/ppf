package org.politisationpopulairefrancaise.core.agir.organisations

import org.politisationpopulairefrancaise.core.text.FrenchText

/**
 * A search in the directory of organisations (M3, AG-03): words, types, a territory, and "près de
 * chez moi". Every criterion is a constraint only when set. The query runs on the device only:
 * [near], the territory the user typed, is never sent anywhere (contract of the Agir space,
 * section 6), and nothing here is stored.
 *
 * Results are sorted by name (accents, case and the [EXEMPLE] marker ignored). With [near], the
 * organisations of the user's territory come first, then those that act everywhere in France.
 */
data class OrganisationQuery(
    /** Words that must all start a word of the name, the summary, the territory, the type or the presentation. */
    val text: String = "",
    /** Types among `AgirFormats.ORG_TYPES`; an organisation of any of them matches. */
    val types: Set<String> = emptySet(),
    /** One territory, compared folded ("Métropole de Lyon" equals "metropole de lyon"). */
    val territory: String? = null,
    /** The user's own territory (commune, department, postal code), typed on the device. */
    val near: String? = null,
) {
    /** True when no criterion is set. */
    val isUnconstrained: Boolean
        get() = text.isBlank() && types.isEmpty() && territory.isNullOrBlank() && near.isNullOrBlank()

    /** How many criteria are set (the "Filtres" count). */
    val constraintCount: Int
        get() = (if (text.isNotBlank()) 1 else 0) + types.size + (if (!territory.isNullOrBlank()) 1 else 0) + (if (!near.isNullOrBlank()) 1 else 0)

    fun matches(organisation: Organisation): Boolean {
        if (types.isNotEmpty() && organisation.type !in types) return false
        val place = territory?.takeIf { it.isNotBlank() }?.let(FrenchText::fold)
        if (place != null && FrenchText.fold(organisation.territory) != place) return false
        val home = near?.takeIf { it.isNotBlank() }
        if (home != null && !organisation.isNational && !Territories.isNear(organisation.territory, home)) return false
        if (text.isNotBlank() && !FrenchText.matchesAllWords(searchableText(organisation), text)) return false
        return true
    }

    /** The organisations this query keeps, in its order. */
    fun apply(organisations: List<Organisation>): List<Organisation> {
        val kept = organisations.filter(::matches)
        val byName = compareBy<Organisation> { FrenchText.fold(it.displayName) }.thenBy { it.slug }.thenBy { it.editor }
        return if (near.isNullOrBlank()) kept.sortedWith(byName) else kept.sortedWith(compareBy<Organisation> { if (it.isNational) 1 else 0 }.then(byName))
    }

    companion object {
        /** What the words of a search are looked for in. */
        fun searchableText(organisation: Organisation): String = listOf(
            organisation.name,
            organisation.summary,
            organisation.territory,
            organisation.type.replace('-', ' '),
            organisation.presentation,
        ).joinToString(" ")
    }
}

/**
 * "Près de chez moi" without any map or service: the device compares the territory of an
 * organisation with the one the user typed. Two territories are near when, once folded:
 *
 * - they are equal, or the meaningful words of one are all words of the other ("Lyon" and
 *   "Lyon 3e", "Lyon" and "Métropole de Lyon");
 * - or they share a department number, written as such ("Nord (59)", "59") or read from a postal
 *   code ("59000" is in department 59, "97411" in 974, "20000" in Corsica, 2A or 2B).
 *
 * It knows no list of communes: "Lille" and "Nord" are not near unless one of them carries the
 * department number. The examples of the directory write it after the commune ("Lille (59)").
 */
object Territories {

    /** Small words that say nothing about a place. */
    private val SMALL_WORDS = setOf("de", "du", "des", "la", "le", "les", "l", "d", "et", "en", "sur", "sous", "aux", "au", "a")

    fun isNear(territory: String, home: String): Boolean {
        val a = FrenchText.fold(territory)
        val b = FrenchText.fold(home)
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        val wordsA = placeWords(a)
        val wordsB = placeWords(b)
        if (wordsA.isNotEmpty() && wordsB.isNotEmpty()) {
            val (shorter, longer) = if (wordsA.size <= wordsB.size) wordsA to wordsB else wordsB to wordsA
            if (longer.containsAll(shorter)) return true
        }
        val departmentsA = departments(a)
        return departmentsA.isNotEmpty() && departments(b).any { it in departmentsA }
    }

    /**
     * The department numbers a folded territory names: two-digit numbers (01 to 95, 2a, 2b),
     * overseas numbers (971 to 976) and those read from five-digit postal codes.
     */
    fun departments(folded: String): Set<String> = buildSet {
        for (word in folded.split(' ')) {
            when {
                word == "2a" || word == "2b" -> add(word)
                word.length == 2 && word.all(Char::isDigit) && word != "00" && word != "20" && word.toInt() <= 95 -> add(word)
                word.length == 3 && word.all(Char::isDigit) && word.toInt() in 971..976 -> add(word)
                word.length == 5 && word.all(Char::isDigit) -> when {
                    word.startsWith("97") || word.startsWith("98") -> add(word.take(3))
                    word.startsWith("20") -> {
                        add("2a")
                        add("2b")
                    }
                    word.startsWith("00") -> Unit
                    else -> add(word.take(2))
                }
            }
        }
    }

    /** The words that name a place: no small word, no number (numbers are compared as departments). */
    private fun placeWords(folded: String): Set<String> =
        folded.split(' ').filter { it.isNotEmpty() && it !in SMALL_WORDS && !it.all(Char::isDigit) }.toSet()
}
