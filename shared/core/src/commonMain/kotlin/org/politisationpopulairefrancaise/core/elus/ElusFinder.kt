package org.politisationpopulairefrancaise.core.elus

import org.politisationpopulairefrancaise.core.comprendre.DecisionLevel
import org.politisationpopulairefrancaise.core.text.FrenchText
import org.politisationpopulairefrancaise.core.time.CivilDate

/** What a group of officials is, so that the interface can title it. */
enum class OfficialGroupKind {
    /** The municipal council of the commune: the mayor, the deputies in order, then the members. */
    MUNICIPAL_COUNCIL,

    /** The intercommunality: its president, then the members representing the commune. */
    INTERCOMMUNALITE,

    /** The members of the departmental council elected in one canton of the commune. */
    CANTON,

    /** The president of the departmental council. */
    DEPARTMENT_PRESIDENCY,

    /** The assembly of a collectivity with a special status. */
    COLLECTIVITE,

    /** The president of the regional council. */
    REGION_PRESIDENCY,

    /** The other members of the regional council found in the package of the department. */
    REGIONAL_COUNCIL,

    /** The member of the Assemblée nationale of one constituency of the commune. */
    CIRCONSCRIPTION,

    /** The members of the Sénat elected in the department. */
    SENATE,

    /** The members of the European Parliament elected in France. */
    EUROPEAN_PARLIAMENT,
}

/** One council, canton or constituency under a level, with its officials in the order to show them. */
data class OfficialGroup(val kind: OfficialGroupKind, val territory: Territory?, val officials: List<ElectedOfficial>)

/** Something the interface explains under a level, when the list is not the plain answer. */
enum class LevelNote {
    /** The commune belongs to no intercommunality. */
    NO_INTERCOMMUNALITE,

    /** The source does not say which commune each member of the intercommunality represents: all are listed. */
    ALL_MEMBERS_OF_INTERCOMMUNALITE,

    /** The package does not say which canton each commune belongs to: every canton of the department is listed. */
    ALL_CANTONS_OF_DEPARTMENT,

    /** The package does not say which constituency each commune belongs to: every constituency of the department is listed. */
    ALL_CIRCONSCRIPTIONS_OF_DEPARTMENT,

    /** The national package (European Parliament) is not on the device. */
    NATIONAL_PACKAGE_MISSING,
}

/** The officials of one level of decision, for one commune. */
data class LevelOfficials(val level: DecisionLevel, val groups: List<OfficialGroup>, val notes: List<LevelNote> = emptyList()) {
    val count: Int get() = groups.sumOf { it.officials.size }
}

/** Every elected official of a commune, level by level, from the commune to the European Union. */
data class CommuneOfficials(
    val commune: ElusCommune,
    val department: Territory,
    val region: Territory?,
    /** True when any package used holds fictitious data ([EXEMPLE]). */
    val example: Boolean,
    val sourceUpdatedOn: CivilDate?,
    /** The six levels of [DecisionLevel], in order. */
    val levels: List<LevelOfficials>,
) {
    fun level(level: DecisionLevel): LevelOfficials = levels.firstOrNull { it.level == level } ?: LevelOfficials(level, emptyList())

    val count: Int get() = levels.sumOf { it.count }
}

/**
 * Finds the elected officials of a commune in the package of its department (and in the national
 * package for the European Parliament), on the device only (contract, 1.6):
 * - commune: the mayor, the deputies in the order of their rank, then the members of the council;
 * - intercommunality: its president, then the members representing the commune;
 * - department: the members elected in the canton(s) of the commune, then the president of the council;
 * - region: the president of the regional council, then the members found in the package;
 * - State: the members of the Assemblée nationale of the constituency (or constituencies) of the
 *   commune, then the members of the Sénat elected in the department;
 * - European Union: the members of the European Parliament elected in France.
 *
 * When the package says nothing of the cantons (or constituencies) of any commune, the table is
 * missing for the whole department: every canton (constituency) is listed with its name, as the
 * contract asks. When other communes have one and this one none (a commune of the Métropole de
 * Lyon has no canton, for example), it truly has none.
 */
object ElusFinder {

    fun find(pkg: ElusPackage, communeCode: String, national: ElusPackage? = null): CommuneOfficials? {
        val commune = pkg.commune(communeCode) ?: return null
        val levels = listOf(
            LevelOfficials(DecisionLevel.COMMUNE, communeLevel(pkg, commune)),
            intercommunality(pkg, commune),
            department(pkg, commune),
            LevelOfficials(DecisionLevel.REGION, region(pkg)),
            state(pkg, commune),
            europe(national),
        )
        return CommuneOfficials(
            commune = commune,
            department = Territory(pkg.code, pkg.name),
            region = pkg.region,
            example = pkg.example || (national?.example ?: false),
            sourceUpdatedOn = pkg.sourceUpdatedOn,
            levels = levels,
        )
    }

    private fun communeLevel(pkg: ElusPackage, commune: ElusCommune): List<OfficialGroup> {
        val council = ordered(pkg.officials.filter { it.mandate == Mandate.MUNICIPAL && it.territory == commune.code })
        return if (council.isEmpty()) emptyList() else listOf(OfficialGroup(OfficialGroupKind.MUNICIPAL_COUNCIL, Territory(commune.code, commune.name), council))
    }

    private fun intercommunality(pkg: ElusPackage, commune: ElusCommune): LevelOfficials {
        val epci = commune.epci ?: return LevelOfficials(DecisionLevel.INTERCOMMUNALITE, emptyList(), listOf(LevelNote.NO_INTERCOMMUNALITE))
        val members = pkg.officials.filter { it.mandate == Mandate.COMMUNAUTAIRE && it.territory == epci }
        val presidents = members.filter { isPresident(it.function) }
        val everyoneListed = members.none { it.commune != null }
        val representatives = if (everyoneListed) members else members.filter { it.commune == commune.code }
        val shown = presidents + ordered(representatives.filter { it !in presidents })
        val group = OfficialGroup(OfficialGroupKind.INTERCOMMUNALITE, Territory(epci, pkg.epciName(epci) ?: epci), shown)
        return LevelOfficials(
            level = DecisionLevel.INTERCOMMUNALITE,
            groups = if (shown.isEmpty() && pkg.epciName(epci) == null) emptyList() else listOf(group),
            notes = if (everyoneListed && members.isNotEmpty()) listOf(LevelNote.ALL_MEMBERS_OF_INTERCOMMUNALITE) else emptyList(),
        )
    }

    private fun department(pkg: ElusPackage, commune: ElusCommune): LevelOfficials {
        // A commune of the Métropole de Lyon, of Corsica, Martinique, Guyane or of an overseas
        // collectivity: the assembly of its collectivity, never the departmental council.
        commune.collectivite?.let { code ->
            val members = ordered(pkg.officials.filter { it.mandate == Mandate.COLLECTIVITE && it.territory == code })
            val group = OfficialGroup(OfficialGroupKind.COLLECTIVITE, Territory(code, pkg.collectiviteName(code) ?: code), members)
            return LevelOfficials(DecisionLevel.DEPARTEMENT, if (members.isEmpty() && pkg.collectiviteName(code) == null) emptyList() else listOf(group))
        }
        val councillors = pkg.officials.filter { it.mandate == Mandate.DEPARTEMENTAL }
        val tableMissing = commune.cantons.isEmpty() && pkg.communes.none { it.cantons.isNotEmpty() }
        val cantons = if (tableMissing) councillors.map { it.territory }.distinct().sortedBy { pkg.cantonName(it)?.lowercase() ?: it } else commune.cantons
        val groups = mutableListOf<OfficialGroup>()
        cantons.forEach { canton ->
            val elected = ordered(councillors.filter { it.territory == canton })
            if (elected.isNotEmpty() || pkg.cantonName(canton) != null) {
                groups += OfficialGroup(OfficialGroupKind.CANTON, Territory(canton, pkg.cantonName(canton) ?: canton), elected)
            }
        }
        val presidents = councillors.filter { isPresident(it.function) }
        if (presidents.isNotEmpty()) groups += OfficialGroup(OfficialGroupKind.DEPARTMENT_PRESIDENCY, Territory(pkg.code, pkg.name), presidents)
        // Packages written before the field `collectivite` do not say who belongs to which assembly: show them all.
        if (!pkg.knowsCollectivities) {
            pkg.officials.filter { it.mandate == Mandate.COLLECTIVITE }.groupBy { it.territory }.forEach { (territory, members) ->
                groups += OfficialGroup(OfficialGroupKind.COLLECTIVITE, Territory(territory, pkg.collectiviteName(territory) ?: territory), ordered(members))
            }
        }
        val notes = if (tableMissing && councillors.isNotEmpty()) listOf(LevelNote.ALL_CANTONS_OF_DEPARTMENT) else emptyList()
        return LevelOfficials(DecisionLevel.DEPARTEMENT, groups, notes)
    }

    private fun region(pkg: ElusPackage): List<OfficialGroup> {
        val councillors = pkg.officials.filter { it.mandate == Mandate.REGIONAL }
        if (councillors.isEmpty()) return emptyList()
        val region = pkg.region ?: Territory(councillors.first().territory, councillors.first().territory)
        val presidents = councillors.filter { isPresident(it.function) }
        val others = ordered(councillors.filter { it !in presidents })
        return buildList {
            if (presidents.isNotEmpty()) add(OfficialGroup(OfficialGroupKind.REGION_PRESIDENCY, region, presidents))
            if (others.isNotEmpty()) add(OfficialGroup(OfficialGroupKind.REGIONAL_COUNCIL, region, others))
        }
    }

    private fun state(pkg: ElusPackage, commune: ElusCommune): LevelOfficials {
        val deputies = pkg.officials.filter { it.mandate == Mandate.DEPUTE }
        val tableMissing = commune.circonscriptions.isEmpty() && pkg.communes.none { it.circonscriptions.isNotEmpty() }
        val circonscriptions = if (tableMissing) deputies.map { it.territory }.distinct().sortedWith(compareBy({ it.length }, { it })) else commune.circonscriptions
        val groups = mutableListOf<OfficialGroup>()
        circonscriptions.forEach { code ->
            val elected = deputies.filter { it.territory == code }
            if (elected.isNotEmpty() || pkg.circonscriptionName(code) != null) {
                groups += OfficialGroup(OfficialGroupKind.CIRCONSCRIPTION, Territory(code, pkg.circonscriptionName(code) ?: code), elected)
            }
        }
        val senators = pkg.officials.filter { it.mandate == Mandate.SENATEUR }
        val ownSenators = senators.filter { it.territory == pkg.code }.ifEmpty { senators }
        if (ownSenators.isNotEmpty()) groups += OfficialGroup(OfficialGroupKind.SENATE, Territory(pkg.code, pkg.name), ordered(ownSenators))
        val notes = if (tableMissing && deputies.isNotEmpty()) listOf(LevelNote.ALL_CIRCONSCRIPTIONS_OF_DEPARTMENT) else emptyList()
        return LevelOfficials(DecisionLevel.ETAT, groups, notes)
    }

    private fun europe(national: ElusPackage?): LevelOfficials {
        if (national == null) return LevelOfficials(DecisionLevel.UNION_EUROPEENNE, emptyList(), listOf(LevelNote.NATIONAL_PACKAGE_MISSING))
        val members = national.officials.filter { it.mandate == Mandate.EUROPEEN }
        val groups = if (members.isEmpty()) emptyList() else listOf(OfficialGroup(OfficialGroupKind.EUROPEAN_PARLIAMENT, Territory(national.code, national.name), ordered(members)))
        return LevelOfficials(DecisionLevel.UNION_EUROPEENNE, groups)
    }

    /**
     * The order of a council: its head (mayor, president), then the deputies and vice-presidents by
     * rank, then the other functions, then the members; within each, the order of the package (the
     * order of the table of the council when the source gives it, otherwise by name).
     */
    internal fun ordered(officials: List<ElectedOfficial>): List<ElectedOfficial> =
        officials.withIndex().sortedWith(compareBy({ rank(it.value.function) }, { it.index })).map { it.value }

    private fun rank(function: String?): Int {
        val folded = function?.let(FrenchText::fold)?.takeIf { it.isNotEmpty() } ?: return 300_000
        return when {
            folded == "maire" || isPresident(function) -> 0
            "adjoint" in folded || "vice president" in folded -> 1_000 + (ordinal(folded) ?: 99_000)
            else -> 200_000
        }
    }

    /** "Président du conseil départemental", "Présidente" — not "Vice-président". */
    internal fun isPresident(function: String?): Boolean {
        val folded = function?.let(FrenchText::fold) ?: return false
        return folded.startsWith("president")
    }

    /** The rank written at the start of a function: "1er adjoint" is 1, "12e adjoint" is 12, "Premier adjoint" is 1. */
    private fun ordinal(folded: String): Int? {
        val digits = folded.takeWhile { it.isDigit() }
        if (digits.isNotEmpty()) return digits.toIntOrNull()
        return when (folded.substringBefore(' ')) {
            "premier", "premiere" -> 1
            "deuxieme", "second", "seconde" -> 2
            "troisieme" -> 3
            else -> null
        }
    }
}
