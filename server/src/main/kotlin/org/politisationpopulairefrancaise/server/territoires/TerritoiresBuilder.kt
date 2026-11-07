package org.politisationpopulairefrancaise.server.territoires

import org.politisationpopulairefrancaise.server.elus.CsvInput
import org.politisationpopulairefrancaise.server.elus.CsvRow
import org.politisationpopulairefrancaise.server.elus.CsvTable
import org.politisationpopulairefrancaise.server.elus.ElusFormatException
import org.politisationpopulairefrancaise.server.elus.sha256Hex
import java.io.InputStream
import java.text.Normalizer
import java.time.LocalDate

/** One document ready to be stored: its exact JSON body and SHA-256. */
class BuiltDocument(val code: String, val name: String, val body: ByteArray, val postalCodes: List<String>) {
    val sha256: String = sha256Hex(body)

    fun entry(): PackageEntryJson = PackageEntryJson(code, name, sha256, body.size.toLong(), postalCodes)
}

/** The result of an import: the national level, the packages (index of the names included), the numbers for the guard. */
class BuiltTerritoires(
    val national: ByteArray,
    val packages: List<BuiltDocument>,
    /** `communes` (communes of the geography) and `population` (population municipale of France) for the guard. */
    val counts: Map<String, Long>,
) {
    val nationalSha256: String = sha256Hex(national)
}

/** The figures of a territory; null when unknown. [computed] names the figures added up by the server. */
data class Figures(
    val pmun: Long? = null,
    val ptot: Long? = null,
    val lp: Long? = null,
    val lc: Long? = null,
    val computed: Set<String> = emptySet(),
    /** Source of the population when it is not the first population source. */
    val populationSource: String? = null,
) {
    operator fun plus(other: Figures): Figures = Figures(
        pmun = sum(pmun, other.pmun),
        ptot = null,
        lp = sum(lp, other.lp),
        lc = sum(lc, other.lc),
        computed = setOf(POPULATION, INSCRITS, INSCRITS_UE),
    )

    val computedList: List<String> get() = listOf(POPULATION, INSCRITS, INSCRITS_UE).filter { it in computed }

    companion object {
        const val POPULATION: String = "population"
        const val INSCRITS: String = "inscrits"
        const val INSCRITS_UE: String = "inscritsComplementaires"

        private fun sum(a: Long?, b: Long?): Long? = if (a == null || b == null) null else a + b

        /** The sum of [all]; a figure stays unknown when one of them lacks it; empty gives nothing known. */
        fun total(all: List<Figures>): Figures? = all.reduceOrNull { a, b -> a + b }?.let { if (all.size == 1) all[0].copy(computed = setOf(POPULATION, INSCRITS, INSCRITS_UE), ptot = null) else it }
    }
}

/**
 * Builds the documents of the referential from the files of a source (docs/integration/territoires.md,
 * sections 2 and 5). Deterministic: fixed sorts, no locale, dates from the sources only.
 */
class TerritoiresBuilder(private val example: Boolean, private val dates: TerritoiresDates, private val open: (TerritoiresFile) -> InputStream) {

    private class Area(val code: String, val name: String, val of: String, val region: String?)
    private class Commune(val code: String, val name: String, val of: String, val area: String, val ctcd: String?)
    private class District(val code: String, val name: String, val of: String, val parent: String)
    private class Epci(val siren: String, val nature: String, val name: String, val members: MutableList<String> = mutableListOf())

    fun build(): BuiltTerritoires {
        val communes = LinkedHashMap<String, Commune>()
        val districts = LinkedHashMap<String, District>()
        table(TerritoiresFile.COG_COMMUNES, ',', listOf("TYPECOM", "COM", "DEP", "CTCD", "TNCC", "NCCENR", "LIBELLE", "COMPARENT")) { row ->
            when (row["TYPECOM"]) {
                "COM" -> communes[row["COM"]] = Commune(row["COM"], row["LIBELLE"], ofName(row["TNCC"], row["NCCENR"]), row["DEP"], row.optional("CTCD"))
                "ARM" -> row.optional("COMPARENT")?.let { districts[row["COM"]] = District(row["COM"], row["LIBELLE"], ofName(row["TNCC"], row["NCCENR"]), it) }
            }
        }
        table(TerritoiresFile.COG_COMMUNES_COMER, ',', listOf("COM_COMER", "TNCC", "NCCENR", "LIBELLE", "COMER")) { row ->
            communes.putIfAbsent(row["COM_COMER"], Commune(row["COM_COMER"], row["LIBELLE"], ofName(row["TNCC"], row["NCCENR"]), row["COMER"], null))
        }
        val departments = areas(TerritoiresFile.COG_DEPARTEMENTS, "DEP", withRegion = true).toSortedMap()
        val regions = areas(TerritoiresFile.COG_REGIONS, "REG", withRegion = false).toSortedMap()
        val ctcds = areas(TerritoiresFile.COG_CTCD, "CTCD", withRegion = true).toSortedMap()
        val comers = areas(TerritoiresFile.COG_COMER, "COMER", withRegion = false).toSortedMap()
        if (communes.isEmpty() || departments.isEmpty() || regions.isEmpty()) throw ElusFormatException("The Code officiel géographique has no commune, department or region.")

        val postal = HashMap<String, MutableSet<String>>()
        table(TerritoiresFile.POSTAL_CODES, ';', listOf("#Code_commune_INSEE", "Code_postal")) { row ->
            val code = row["#Code_commune_INSEE"]
            val postalCode = row["Code_postal"]
            if (code.isEmpty() || !POSTAL.matches(postalCode)) return@table
            postal.getOrPut(code) { sortedSetOf() } += postalCode
            districts[code]?.let { postal.getOrPut(it.parent) { sortedSetOf() } += postalCode }
        }

        val stats = HashMap<String, HashMap<String, Figures>>()
        table(TerritoiresFile.STATISTICS, ';', listOf("niveau", "code", "grandeur", "valeur")) { row -> addFigure(stats, row["niveau"], row["code"], row["grandeur"], row["valeur"], null) }
        CsvTable.open(SUPPLEMENTS, CsvInput.reader(TerritoiresSources.supplements()), ';', listOf("niveau", "code", "grandeur", "valeur", "source")).use { table ->
            table.forEach { row ->
                val source = row["source"]
                if (source !in SUPPLEMENT_SOURCES) throw ElusFormatException("$SUPPLEMENTS names an unknown source.")
                addFigure(stats, row["niveau"], row["code"], row["grandeur"], row["valeur"], source)
            }
        }

        val (carried, formers) = carry(stats["COM"].orEmpty())

        val epcis = java.util.TreeMap<String, Epci>()
        table(TerritoiresFile.EPCI, ';', listOf("siren", "categorie", "nom", "commune")) { row ->
            val siren = row["siren"]
            if (!SIREN.matches(siren) || siren == METROPOLE_DE_LYON_SIREN) return@table
            val nature = NATURES[row["categorie"]] ?: return@table
            epcis.getOrPut(siren) { Epci(siren, nature, row["nom"]) }.members += row["commune"]
        }
        val communeEpci = HashMap<String, String>()
        epcis.values.forEach { epci -> epci.members.sort(); epci.members.forEach { communeEpci[it] = epci.siren } }

        val sources = sourcesJson()
        val geography = dates.geography.toString()

        // Collectivities with the powers of a department.
        fun ctcdDepartments(code: String): List<String> = CTCD_DEPARTMENTS[code] ?: listOf(code.removeSuffix("D"))
        val ctcdFigures = ctcds.keys.associateWith { code ->
            if (code in SHARED_CTCD) {
                Figures.total(communes.values.filter { it.ctcd == code }.mapNotNull { carried[it.code] })
            } else {
                val deps = ctcdDepartments(code).mapNotNull { stats["DEP"]?.get(it) }
                when {
                    deps.isEmpty() -> null
                    deps.size == 1 -> deps[0]
                    else -> Figures.total(deps)
                }
            }
        }

        // Packages: a department or overseas collectivity with communes.
        val byArea = communes.values.groupBy { it.area }
        val documents = ArrayList<BuiltDocument>()
        for (area in (departments.keys + comers.keys).filter { byArea.containsKey(it) }.sorted()) {
            val outreMer = area !in departments
            val areaInfo = departments[area] ?: comers.getValue(area)
            val members = byArea.getValue(area).sortedWith(compareBy<Commune>({ sortKey(it.name) }, { it.code }))
            val communeJson = members.map { commune ->
                val fig = carried[commune.code]
                CommuneJson(
                    code = commune.code,
                    name = commune.name,
                    of = commune.of,
                    postalCodes = postal[commune.code].orEmpty().toList(),
                    epci = communeEpci[commune.code],
                    collectivite = if (outreMer) commune.area else commune.ctcd,
                    population = fig?.pmun,
                    populationTotale = fig?.ptot,
                    inscrits = fig?.lp,
                    inscritsComplementaires = fig?.lc,
                    computed = fig?.computedList.orEmpty(),
                    sources = fig?.populationSource?.let { SourceOverride(population = it) },
                    formerCodes = formers[commune.code].orEmpty().sorted(),
                )
            }
            val codes = members.map { it.code }.toSet()
            val districtJson = districts.values.filter { it.parent in codes }.sortedBy { it.code }.map { district ->
                val fig = stats["ARM"]?.get(district.code)
                ArrondissementJson(district.code, district.name, district.of, district.parent, postal[district.code].orEmpty().toList(), fig?.pmun, fig?.ptot, fig?.lp, fig?.lc)
            }
            val epciJson = epcis.values.filter { epci -> epci.members.any { it in codes } }.map { epci ->
                val fig = Figures.total(epci.members.map { carried[it] ?: Figures() })
                EpciJson(
                    code = epci.siren,
                    name = epci.name,
                    of = epciOf(epci.name),
                    nature = epci.nature,
                    departements = epci.members.map(::packageOf).distinct().sorted(),
                    communes = epci.members.toList(),
                    population = fig?.pmun,
                    inscrits = fig?.lp,
                    inscritsComplementaires = fig?.lc,
                    computed = fig?.computedList.orEmpty(),
                )
            }
            val body = PackageJson(
                format = TERRITOIRES_FORMAT,
                example = example,
                code = area,
                name = areaInfo.name,
                outreMer = outreMer,
                region = if (outreMer) null else areaInfo.region,
                geography = geography,
                sources = sources,
                communes = communeJson,
                arrondissements = districtJson,
                epcis = epciJson,
            )
            val postalCodes = communeJson.flatMap { it.postalCodes }.toSortedSet().toList()
            documents += BuiltDocument(area, areaInfo.name, TerritoiresJson.encodeToString(PackageJson.serializer(), body).toByteArray(Charsets.UTF_8), postalCodes)
        }
        val index = CommunesIndexJson(
            format = TERRITOIRES_FORMAT,
            example = example,
            code = COMMUNES_PACKAGE,
            geography = geography,
            communes = communes.values.sortedBy { it.code }.map { listOf(it.code, it.name, it.area) },
        )
        documents += BuiltDocument(COMMUNES_PACKAGE, "Toutes les communes", TerritoiresJson.encodeToString(CommunesIndexJson.serializer(), index).toByteArray(Charsets.UTF_8), emptyList())

        val france = stats["FRANCE"]
        val populationFrance = france?.get("F")
        val electorateFrance = france?.get("F_COMER")
        val departmentPackages = documents.count { it.code in departments }
        val national = NationalJson(
            format = TERRITOIRES_FORMAT,
            example = example,
            partial = departmentPackages < departments.size,
            geography = geography,
            builtOn = dates.builtOn.toString(),
            sources = sources,
            france = FranceJson(
                name = "France",
                population = populationFrance?.pmun,
                populationTotale = populationFrance?.ptot,
                populationPerimeter = "France hors Mayotte et hors collectivités d'outre-mer",
                inscrits = electorateFrance?.lp,
                inscritsComplementaires = electorateFrance?.lc,
                inscritsPerimeter = "France et collectivités d'outre-mer, hors Nouvelle-Calédonie",
            ),
            regions = regions.values.map { region ->
                val fig = stats["REG"]?.get(region.code)
                RegionJson(region.code, region.name, region.of, fig?.pmun, fig?.ptot, fig?.lp, fig?.lc, REGIONS_HELD[region.code], fig?.populationSource?.let { SourceOverride(population = it) })
            },
            departements = departments.values.map { dep ->
                val fig = stats["DEP"]?.get(dep.code)
                DepartementJson(
                    code = dep.code,
                    name = dep.name,
                    of = dep.of,
                    region = dep.region,
                    collectivites = ctcds.keys.filter { dep.code in ctcdDepartments(it) },
                    population = fig?.pmun,
                    populationTotale = fig?.ptot,
                    inscrits = fig?.lp,
                    inscritsComplementaires = fig?.lc,
                    spread = SPREAD_COUNTS,
                    sources = fig?.populationSource?.let { SourceOverride(population = it) },
                )
            },
            collectivites = ctcds.values.map { ctcd ->
                val fig = ctcdFigures[ctcd.code]
                CollectiviteJson(
                    code = ctcd.code,
                    name = ctcd.name,
                    of = ctcd.of,
                    special = !ctcd.code.endsWith("D"),
                    region = ctcd.region,
                    departements = ctcdDepartments(ctcd.code),
                    population = fig?.pmun,
                    populationTotale = fig?.ptot,
                    inscrits = fig?.lp,
                    inscritsComplementaires = fig?.lc,
                    computed = fig?.computedList.orEmpty(),
                )
            },
            outreMer = comers.values.map { comer ->
                val fig = stats["COMER"]?.get(comer.code)
                OutreMerJson(
                    code = comer.code,
                    name = comer.name,
                    of = comer.of,
                    population = fig?.pmun,
                    inscrits = fig?.lp,
                    inscritsComplementaires = fig?.lc,
                    spread = SPREAD[comer.code] ?: SPREAD_EXCLUDED,
                    sources = fig?.populationSource?.let { SourceOverride(population = it) },
                )
            },
            packages = documents.map { it.entry() },
        )
        val nationalBytes = TerritoiresJson.encodeToString(NationalJson.serializer(), national).toByteArray(Charsets.UTF_8)
        if (nationalBytes.size > MAX_NATIONAL_BYTES) throw ElusFormatException("The national level is larger than $MAX_NATIONAL_BYTES bytes.")
        return BuiltTerritoires(
            national = nationalBytes,
            packages = documents,
            counts = mapOf("communes" to communes.size.toLong(), "population" to (populationFrance?.pmun ?: 0L)),
        )
    }

    /**
     * Carries the figures of the communes from the geography of Melodi to that of the COG: a
     * commune merged since gets the sum of its former communes (figures marked computed, no
     * population totale); a commune split since gets no figure rather than a wrong one. Also
     * gives, for each commune, the codes it replaced (mergers since 2024), to read older tags.
     */
    private fun carry(values: Map<String, Figures>): Pair<Map<String, Figures>, Map<String, Set<String>>> {
        val events = ArrayList<Triple<String, String, String>>()
        table(TerritoiresFile.COG_EVENTS, ',', listOf("DATE_EFF", "TYPECOM_AV", "COM_AV", "TYPECOM_AP", "COM_AP")) { row ->
            if (row["TYPECOM_AV"] == "COM" && row["TYPECOM_AP"] == "COM") events += Triple(row["DATE_EFF"], row["COM_AV"], row["COM_AP"])
        }
        val carried = HashMap(values)
        val formers = HashMap<String, MutableSet<String>>()
        val since = dates.statisticsGeography.toString()
        for ((date, group) in events.groupBy { it.first }.toSortedMap()) {
            for ((before, moves) in group.groupBy { it.second }.toSortedMap()) {
                val afters = moves.map { it.third }.toSortedSet()
                when {
                    afters == sortedSetOf(before) -> Unit
                    afters.size == 1 -> {
                        val after = afters.first()
                        if (date >= FORMER_CODES_SINCE) {
                            formers.getOrPut(after) { sortedSetOf() }.apply { add(before); addAll(formers.remove(before).orEmpty()) }
                        }
                        if (date > since) {
                            val moved = carried.remove(before)
                            val target = carried[after]
                            when {
                                moved == null && target == null -> Unit
                                // One part without figures: the whole is unknown.
                                moved == null -> carried.remove(after)
                                target == null -> carried[after] = moved.copy(ptot = null, computed = ALL_COMPUTED)
                                else -> carried[after] = target + moved
                            }
                        }
                    }
                    date > since -> {
                        // A split: nobody knows the share of each part.
                        carried.remove(before)
                        afters.forEach { carried.remove(it) }
                    }
                }
            }
        }
        return carried to formers
    }

    private fun addFigure(stats: HashMap<String, HashMap<String, Figures>>, level: String, code: String, measure: String, value: String, source: String?) {
        if (level !in LEVELS || code.isEmpty() || code.length > 12) return
        val number = value.trim().toLongOrNull()?.takeIf { it in 0..MAX_FIGURE } ?: throw ElusFormatException("statistiques.csv holds a figure that is not a positive whole number.")
        val byCode = stats.getOrPut(level) { HashMap() }
        val current = byCode[code] ?: Figures()
        byCode[code] = when (measure) {
            "PMUN" -> current.copy(pmun = number, populationSource = source ?: current.populationSource)
            "PTOT" -> current.copy(ptot = number)
            "LP" -> current.copy(lp = number)
            "LC" -> current.copy(lc = number)
            else -> current
        }
    }

    private fun areas(file: TerritoiresFile, codeColumn: String, withRegion: Boolean): Map<String, Area> {
        val result = HashMap<String, Area>()
        val columns = listOfNotNull(codeColumn, "TNCC", "NCCENR", "LIBELLE", if (withRegion) "REG" else null)
        table(file, ',', columns) { row ->
            result[row[codeColumn]] = Area(row[codeColumn], row["LIBELLE"], ofName(row["TNCC"], row["NCCENR"]), if (withRegion) row.optional("REG") else null)
        }
        return result
    }

    private fun table(file: TerritoiresFile, separator: Char, columns: List<String>, action: (CsvRow) -> Unit) {
        CsvTable.open(file.fileName, CsvInput.reader(open(file)), separator, columns).use { it.forEach(action) }
    }

    private fun sourcesJson(): List<SourceJson> {
        val vintage = dates.populationVintage
        return listOf(
            SourceJson("popref-$vintage", "population", "Populations de référence $vintage (Insee)", "https://www.insee.fr/fr/statistiques/8681011", LICENCE, vintage, dates.populationInForce.toString()),
            SourceJson("reu-${dates.electorateDate}", "electorate", "Répertoire électoral unique, électeurs inscrits (Insee)", "https://www.insee.fr/fr/statistiques/8888278", LICENCE, null, dates.electorateDate.toString()),
        ) + SUPPLEMENT_SOURCES.values + listOf(
            SourceJson("cog-${dates.geography.year}", "geography", "Code officiel géographique ${dates.geography.year} (Insee)", "https://www.data.gouv.fr/datasets/code-officiel-geographique-1", "Licence Ouverte", null, dates.geography.toString()),
            SourceJson("laposte", "geography", "Base officielle des codes postaux (La Poste)", "https://www.data.gouv.fr/datasets/base-officielle-des-codes-postaux", LICENCE, null, null),
            SourceJson("epci-${dates.geography.year}", "geography", "Intercommunalités au 1er janvier ${dates.geography.year} (Insee, API Métadonnées)", "https://www.insee.fr/fr/information/2510634", "Licence Ouverte", null, dates.geography.toString()),
        )
    }

    companion object {
        const val LICENCE: String = "Licence Ouverte 2.0"
        const val SUPPLEMENTS: String = "outre-mer.csv"
        const val MAX_NATIONAL_BYTES: Int = 2 * 1024 * 1024
        private const val MAX_FIGURE = 200_000_000L
        private val ALL_COMPUTED = setOf(Figures.POPULATION, Figures.INSCRITS, Figures.INSCRITS_UE)

        /** Formers codes are kept for mergers from this day on. */
        const val FORMER_CODES_SINCE: String = "2024-01-01"

        /** The Métropole de Lyon is a collectivity (`69M`), never an EPCI, although the Insee lists it with them. */
        const val METROPOLE_DE_LYON_SIREN: String = "200046977"

        private val LEVELS = setOf("COM", "ARM", "DEP", "REG", "COMER", "FRANCE")
        private val POSTAL = Regex("^[0-9]{5}$")
        private val SIREN = Regex("^[0-9]{9}$")

        val NATURES: Map<String, String> = mapOf(
            "Communauté de communes" to "CC",
            "Communauté d'agglomération" to "CA",
            "Communauté urbaine" to "CU",
            "Métropole" to "METRO",
        )

        /** Collectivities whose departments are not those of their code: the others cover the department of their code (`01D`: 01). */
        val CTCD_DEPARTMENTS: Map<String, List<String>> = mapOf(
            "20R" to listOf("2A", "2B"),
            "6AE" to listOf("67", "68"),
            "69M" to listOf("69"),
            "69D" to listOf("69"),
            "75C" to listOf("75"),
            "972R" to listOf("972"),
            "973R" to listOf("973"),
            "976R" to listOf("976"),
        )

        /** Collectivities that share their department (the Rhône): their figures are sums of their communes. */
        val SHARED_CTCD: Set<String> = setOf("69D", "69M")

        /** Regions whose powers a collectivity with a special status holds. */
        val REGIONS_HELD: Map<String, String> = mapOf("94" to "20R", "02" to "972R", "03" to "973R", "06" to "976R")

        const val SPREAD_COUNTS: String = "counts"
        const val SPREAD_EXCLUDED: String = "excluded"

        /** Règlement de l'Assemblée nationale, article 148, alinéa 6 (verified on 7 November 2026): the five article 74 collectivities count; Nouvelle-Calédonie is to confirm. */
        val SPREAD: Map<String, String> = mapOf(
            "975" to SPREAD_COUNTS, "977" to SPREAD_COUNTS, "978" to SPREAD_COUNTS, "986" to SPREAD_COUNTS, "987" to SPREAD_COUNTS,
            "988" to "to-confirm",
        )

        /** The sources the supplements may name (docs/integration/territoires.md, section 9.2). */
        val SUPPLEMENT_SOURCES: Map<String, SourceJson> = linkedMapOf(
            "insee-com-2023" to SourceJson(
                "insee-com-2023", "population", "Populations de référence 2023 des collectivités d'outre-mer (Insee)",
                "https://www.insee.fr/fr/statistiques/8680667", LICENCE, 2023, "2026-01-01",
            ),
        )

        /** The package of a commune by its code: `2A004` in 2A, `97411` in 974, `69123` in 69. */
        fun packageOf(code: String): String = if (code.startsWith("97") || code.startsWith("98")) code.take(3) else code.take(2)

        /** « du Rhône », « de la Réunion », « d'Ille-et-Vilaine » from the type of name (TNCC) and the name without article. */
        fun ofName(tncc: String, bareName: String): String = when (tncc.trim()) {
            "1" -> "d'$bareName"
            "2" -> "du $bareName"
            "3" -> "de la $bareName"
            "4", "6" -> "des $bareName"
            "5" -> "de l'$bareName"
            "7" -> "de las $bareName"
            "8" -> "de los $bareName"
            else -> "de $bareName"
        }

        fun epciOf(name: String): String = when {
            name.startsWith("Communauté") || name.startsWith("Métropole") -> "de la $name"
            else -> "de $name"
        }

        /** A name as sorted: without accents, lowercase. */
        fun sortKey(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFD).replace(MARKS, "").lowercase()

        private val MARKS = Regex("\\p{M}+")
    }
}

/** The day [text] (AAAA-MM-JJ) or null. */
internal fun parseDay(text: String?): LocalDate? = text?.trim()?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
