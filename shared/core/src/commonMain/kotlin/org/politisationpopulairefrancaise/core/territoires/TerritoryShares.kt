package org.politisationpopulairefrancaise.core.territoires

/** What a total is shared by. */
enum class ShareBasis { POPULATION, ELECTORATE }

data class TerritoryShare(val territory: Territory, val weight: Long, val target: Long)

data class Shares(
    val total: Long,
    val basis: ShareBasis,
    /** In the order of the territories given. */
    val parts: List<TerritoryShare>,
    /** Left out: no figure for the basis (Nouvelle-Calédonie for registered voters, a split commune...). */
    val missing: List<Territory>,
)

/** Indicative objectives, in proportion to population or registered voters. */
object TerritoryShares {
    /**
     * Splits [total] among [territories] in proportion to their population municipale (or
     * registered voters on the liste principale), by the largest remainder: the parts add up
     * exactly to [total]; ties go to the larger weight, then to the smaller code. Deterministic.
     * Territories without a figure, or a weight of zero for everybody, get nothing.
     */
    fun proportional(total: Long, territories: List<Territory>, basis: ShareBasis = ShareBasis.POPULATION): Shares {
        require(total >= 0) { "The total cannot be negative" }
        val distinct = territories.distinctBy { it.code }
        val weighted = distinct.mapNotNull { territory ->
            val weight = when (basis) {
                ShareBasis.POPULATION -> territory.population?.municipale
                ShareBasis.ELECTORATE -> territory.electorate?.principale
            }
            weight?.let { territory to it }
        }
        val missing = distinct.filter { t -> weighted.none { it.first.code == t.code } }
        val sum = weighted.sumOf { it.second }
        if (sum == 0L || total == 0L) return Shares(total, basis, weighted.map { TerritoryShare(it.first, it.second, 0) }, missing)
        // Exact integer arithmetic: total * weight fits in a Long for any French figure (2e8 * 1e9).
        val floors = weighted.map { (_, weight) -> (total * weight) / sum }
        val remainders = weighted.mapIndexed { index, (_, weight) -> index to (total * weight) % sum }
        var left = total - floors.sum()
        val extra = IntArray(weighted.size)
        val order = remainders.sortedWith(
            compareByDescending<Pair<Int, Long>> { it.second }
                .thenByDescending { weighted[it.first].second }
                .thenBy { weighted[it.first].first.code.tag },
        )
        for ((index, _) in order) {
            if (left <= 0) break
            extra[index]++
            left--
        }
        val parts = weighted.mapIndexed { index, (territory, weight) -> TerritoryShare(territory, weight, floors[index] + extra[index]) }
        return Shares(total, basis, parts, missing)
    }
}
