package org.politisationpopulairefrancaise.core.agir.collectives

import org.politisationpopulairefrancaise.core.groups.Group
import org.politisationpopulairefrancaise.core.territoires.TerritoryCode

/**
 * The search of the directory of collectifs (AG-47), run on the device only: nothing of it is sent.
 * Text (name, territory, presentation, themes), theme, type, scope and « près de chez moi ».
 *
 * « Près de chez moi » compares the territory code chosen by the founder with the user's home: the
 * territory chosen on the device (the commune of « Mes élus », for instance) followed by the
 * territories that contain it, nearest first ([home]). France is never "near": a national collectif
 * is everyone's. A collectif without a territory code is never "near" either (its label alone is
 * not compared, so that a homonym elsewhere is not taken for the user's town).
 */
data class CollectiveQuery(
    val text: String = "",
    /** A normalized theme ([CollectiveFormat.normalizeTheme]), or null for all. */
    val theme: String? = null,
    val type: CollectiveType? = null,
    val scope: CollectiveScope? = null,
    /** Only the collectifs near the home; ignored while the home is unknown. */
    val nearMe: Boolean = false,
) {
    val isFiltered: Boolean get() = text.isNotBlank() || theme != null || type != null || scope != null || nearMe

    /**
     * The listed collectifs ([Collective.listed]) that match, the nearest first when [home] is known,
     * then by name (accents and case ignored) and id.
     */
    fun apply(collectives: List<Collective>, home: List<String> = emptyList()): List<Collective> {
        val words = Group.slugify(text.trim())
        val wanted = theme?.let(CollectiveFormat::normalizeTheme)?.takeIf { it.isNotEmpty() }
        val near = nearMe && home.isNotEmpty()
        return collectives
            .filter { it.listed }
            .filter { words.isEmpty() || matches(it, words) }
            .filter { wanted == null || wanted in it.themes }
            .filter { type == null || it.type == type }
            .filter { scope == null || it.scope == scope }
            .filter { !near || nearRank(it, home) != null }
            .sortedWith(
                compareBy<Collective> { nearRank(it, home) ?: Int.MAX_VALUE }
                    .thenBy { it.displayName.lowercase() }
                    .thenBy { it.id },
            )
    }

    private fun matches(collective: Collective, words: String): Boolean =
        Group.slugify(collective.name).contains(words) ||
            Group.slugify(collective.territory ?: "").contains(words) ||
            Group.slugify(collective.about ?: "").contains(words) ||
            collective.themes.any { Group.slugify(it).contains(words) }

    companion object {
        /** Themes offered as filters, at most this many. */
        const val MAX_THEME_FILTERS: Int = 12

        /**
         * How near [collective] is to [home] (0: the home itself, 1: the territory that contains it...),
         * or null when it is not near: no territory code, France, or a territory outside the home.
         */
        fun nearRank(collective: Collective, home: List<String>): Int? {
            val code = collective.territoryCode ?: return null
            if (code == TerritoryCode.FRANCE.tag) return null
            return home.indexOf(code).takeIf { it >= 0 }
        }

        /**
         * The home of the user for « près de chez moi »: [place] then the territories that contain it
         * ([parents], nearest first), as tags, without France and without repetition.
         */
        fun home(place: TerritoryCode?, parents: List<TerritoryCode> = emptyList()): List<String> =
            (listOfNotNull(place) + parents).filter { it != TerritoryCode.FRANCE }.map { it.tag }.distinct()

        /** The themes of [collectives], the most frequent first, then alphabetically; at most [max]. */
        fun themesOf(collectives: List<Collective>, max: Int = MAX_THEME_FILTERS): List<String> =
            collectives.filter { it.listed }
                .flatMap { it.themes }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .take(max)
                .map { it.key }
    }
}
