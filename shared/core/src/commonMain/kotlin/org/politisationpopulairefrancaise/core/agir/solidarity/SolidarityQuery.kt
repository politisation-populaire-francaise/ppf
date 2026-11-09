package org.politisationpopulairefrancaise.core.agir.solidarity

import org.politisationpopulairefrancaise.core.agir.organisations.Territories
import org.politisationpopulairefrancaise.core.text.FrenchText

/**
 * A search in the directory of solidarity funds (AG-10): words, types, a territory and "près de
 * chez moi". Every criterion is a constraint only when set. The query runs on the device only:
 * [near], the territory the user typed, is never sent anywhere nor stored (contract of the Agir
 * space, section 6).
 *
 * Open funds come first, then, with [includeClosed], the closed and withdrawn ones; each group is
 * sorted by name (accents, case and the [EXEMPLE] marker ignored). With [near], the funds of the
 * user's territory come before those that serve everywhere in France.
 */
data class FundQuery(
    /** Words that must all start a word of the name, the summary, the territory, the beneficiaries, the sector, the strike, the manager or the presentation. */
    val text: String = "",
    /** Types among [SolidarityFormats.FUND_TYPES]; a fund of any of them matches. */
    val types: Set<String> = emptySet(),
    /** One territory, compared folded. */
    val territory: String? = null,
    /** The user's own territory (commune, department, postal code), typed on the device. */
    val near: String? = null,
    /** Also keeps the funds that no longer take donations and those the team withdrew. */
    val includeClosed: Boolean = false,
) {
    val isUnconstrained: Boolean
        get() = text.isBlank() && types.isEmpty() && territory.isNullOrBlank() && near.isNullOrBlank() && !includeClosed

    /** How many criteria are set. */
    val constraintCount: Int
        get() = (if (text.isNotBlank()) 1 else 0) + types.size + (if (!territory.isNullOrBlank()) 1 else 0) + (if (!near.isNullOrBlank()) 1 else 0) + (if (includeClosed) 1 else 0)

    fun matches(fund: SolidarityFund): Boolean {
        if (!fund.isOpen && !includeClosed) return false
        if (types.isNotEmpty() && fund.type !in types) return false
        val place = territory?.takeIf { it.isNotBlank() }?.let(FrenchText::fold)
        if (place != null && FrenchText.fold(fund.territory) != place) return false
        val home = near?.takeIf { it.isNotBlank() }
        if (home != null && !fund.isNational && !Territories.isNear(fund.territory, home)) return false
        if (text.isNotBlank() && !FrenchText.matchesAllWords(searchableText(fund), text)) return false
        return true
    }

    /** The funds this query keeps, in its order. */
    fun apply(funds: List<SolidarityFund>): List<SolidarityFund> {
        val kept = funds.filter(::matches)
        val byName = compareBy<SolidarityFund> { if (it.isOpen) 0 else 1 }
            .thenBy { FrenchText.fold(it.displayName) }
            .thenBy { it.slug }
            .thenBy { it.editor }
        return if (near.isNullOrBlank()) kept.sortedWith(byName) else kept.sortedWith(compareBy<SolidarityFund> { if (it.isNational) 1 else 0 }.then(byName))
    }

    companion object {
        /** What the words of a search are looked for in. */
        fun searchableText(fund: SolidarityFund): String = listOfNotNull(
            fund.name,
            fund.summary,
            fund.territory,
            fund.beneficiary,
            fund.sector,
            fund.strike,
            fund.managerName,
            fund.presentation,
        ).joinToString(" ")
    }
}

/**
 * A search among the volunteer missions (AG-12): words, the skills the user picked ("Mes
 * compétences"), remote missions only. The skills stay on the device: they are never sent nor
 * stored, and serve only this filter.
 *
 * Open missions come first, then, with [includeClosed], the filled and closed ones; in each group
 * the nearest deadline first (missions without a deadline after), then the newest.
 */
data class MissionQuery(
    val text: String = "",
    /** Skills among [SolidarityFormats.SKILLS]; a mission that asks for any of them matches. */
    val skills: Set<String> = emptySet(),
    val remoteOnly: Boolean = false,
    /** Also keeps the missions that are filled or closed. */
    val includeClosed: Boolean = false,
) {
    val isUnconstrained: Boolean get() = text.isBlank() && skills.isEmpty() && !remoteOnly && !includeClosed

    val constraintCount: Int
        get() = (if (text.isNotBlank()) 1 else 0) + skills.size + (if (remoteOnly) 1 else 0) + (if (includeClosed) 1 else 0)

    fun matches(mission: VolunteerMission): Boolean {
        if (!mission.isOpen && !includeClosed) return false
        if (skills.isNotEmpty() && mission.skills.none { it in skills }) return false
        if (remoteOnly && !mission.remote) return false
        if (text.isNotBlank() && !FrenchText.matchesAllWords(searchableText(mission), text)) return false
        return true
    }

    fun apply(missions: List<VolunteerMission>): List<VolunteerMission> = missions.filter(::matches).sortedWith(
        compareBy<VolunteerMission> { if (it.isOpen) 0 else 1 }
            .thenBy { it.deadline ?: Long.MAX_VALUE }
            .thenByDescending { it.publishedAt }
            .thenBy { it.slug }
            .thenBy { it.editor },
    )

    companion object {
        fun searchableText(mission: VolunteerMission): String = listOfNotNull(
            mission.title,
            mission.summary,
            mission.territory,
            mission.location,
            mission.commitment,
            mission.skills.joinToString(" "),
            mission.description,
        ).joinToString(" ")
    }
}
