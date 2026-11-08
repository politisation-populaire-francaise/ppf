package org.politisationpopulairefrancaise.core.decider

import org.politisationpopulairefrancaise.core.urne.UrneResults
import kotlin.math.roundToLong

/**
 * When PPF carries a demand (D-115, `docs/integration/seuils-soutiens.md`; owner's decisions of
 * 7 November 2026): PPF adopts nothing; above a number of supports proportional to the population
 * of the demand's territory, calibrated on the petitions of the Assemblée nationale (Règlement,
 * article 148: « plus de 100 000 », « plus de 500 000 »), it carries the demand to the competent
 * officials; above the second, a member of the team files an official petition.
 *
 * - The supports are the citizens' `pour` (verified and not verified accounts, never the elected
 *   officials, who vote publicly apart). `contre` and `blanc` are shown beside.
 * - A threshold is reached when the supports are **more than** it, **and** more than the `contre`
 *   (option B of the owner: « on verra à l'usage »).
 * - Floors for small places: more than 25 to carry, more than 100 for the petition (option A).
 * - No condition of spread: PPF never knows where a voter lives.
 */
object SupportThresholds {
    const val NATIONAL_CARRY: Long = 100_000
    const val NATIONAL_PETITION: Long = 500_000
    const val FLOOR_CARRY: Long = 25
    const val FLOOR_PETITION: Long = 100

    data class Thresholds(val carry: Long, val petition: Long)

    /** Where a demand stands against its thresholds. */
    enum class Stage {
        /** Fewer supports than the first threshold (or not more than the `contre`). */
        BELOW,

        /** PPF carries the demand to the competent officials and publishes it. */
        CARRY,

        /** The team is asked to file an official petition, whose link the card shows. */
        PETITION,
    }

    /** The two thresholds of a territory of [population] inhabitants, France having [nationalPopulation]. */
    fun of(population: Long, nationalPopulation: Long): Thresholds {
        require(population >= 0 && nationalPopulation > 0) { "populations" }
        val share = population.toDouble() / nationalPopulation.toDouble()
        return Thresholds(
            carry = maxOf(FLOOR_CARRY, (NATIONAL_CARRY * share).roundToLong()),
            petition = maxOf(FLOOR_PETITION, (NATIONAL_PETITION * share).roundToLong()),
        )
    }

    /** The citizens' supports in [results]; null while they are hidden (too few ballots of one level). */
    fun supports(results: UrneResults): Long? = results.citizens.takeIf { !it.hidden }?.let { it.counts[FOR] ?: 0L }

    fun stage(results: UrneResults, thresholds: Thresholds): Stage {
        val supports = supports(results) ?: return Stage.BELOW
        val against = results.citizens.counts[AGAINST] ?: 0L
        if (supports <= against) return Stage.BELOW
        return when {
            supports > thresholds.petition -> Stage.PETITION
            supports > thresholds.carry -> Stage.CARRY
            else -> Stage.BELOW
        }
    }

    private const val FOR = "pour"
    private const val AGAINST = "contre"
}
