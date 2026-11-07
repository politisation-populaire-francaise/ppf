package org.politisationpopulairefrancaise.server.elus

import java.io.InputStream

/** A department or an overseas collectivity of the Code officiel géographique. */
data class GeoArea(
    val code: String,
    /** The name as displayed, with its article when it has one (`La Réunion`). */
    val name: String,
    /** The name without article (`Réunion`), to write « de la Réunion ». */
    val bareName: String,
    /** Type of name (TNCC): which article and which « de » go with it. */
    val nameType: Int,
    /** Code of the region, null for an overseas collectivity. */
    val region: String?,
) {
    /** « du Rhône », « de Paris », « des Bouches-du-Rhône », « d'Ille-et-Vilaine », « de la Réunion ». */
    val ofName: String
        get() = when (nameType) {
            1 -> "d'$bareName"
            2 -> "du $bareName"
            3 -> "de la $bareName"
            4, 6 -> "des $bareName"
            5 -> "de l'$bareName"
            7 -> "de las $bareName"
            8 -> "de los $bareName"
            else -> "de $bareName"
        }
}

/** A commune as the packages describe it, before its officials are added. */
data class GeoCommune(
    val code: String,
    val name: String,
    /** Department or overseas collectivity: the package of the commune. */
    val area: String,
    /** Collectivity with departmental powers (CTCD: `69D`, `69M`, `75C`, `6AE`, `20R`...), null overseas. */
    val council: String?,
    /** Code of its canton in the Code officiel géographique, possibly a pseudo-canton. */
    val canton: String?,
)

/**
 * The geography of an import, read from the Code officiel géographique, La Poste and the election
 * results (docs/integration/elus-serveur.md, section 1). Small enough to stay in memory (about
 * 37 000 communes); the files are read once, in a stream.
 */
class Geography(
    val communes: Map<String, GeoCommune>,
    val departments: Map<String, GeoArea>,
    val collectivities: Map<String, GeoArea>,
    val regions: Map<String, String>,
    /** Canton code to name. */
    val cantonNames: Map<String, String>,
    /** Commune to its sorted postal codes (those of its municipal districts included). */
    val postalCodes: Map<String, List<String>>,
    /** Commune to its sorted cantons; absent when unknown (the device then shows the whole department). */
    val cantons: Map<String, List<String>>,
    /** Commune to its sorted constituencies (RNE codes, e.g. `6901`); absent when unknown. */
    val circonscriptions: Map<String, List<String>>,
) {
    /** The department or overseas collectivity [code]. */
    fun area(code: String): GeoArea? = departments[code] ?: collectivities[code]

    /** « 1re circonscription du Rhône »: the name of constituency [code] (department + two-digit number). */
    fun circonscriptionName(code: String): String {
        if (code == SAINT_BARTHELEMY_SAINT_MARTIN) return "1re circonscription de Saint-Barthélemy et Saint-Martin"
        val number = code.takeLast(2).toIntOrNull() ?: return "Circonscription $code"
        val ordinal = if (number == 1) "1re" else "${number}e"
        val area = area(code.dropLast(2)) ?: return "$ordinal circonscription"
        return "$ordinal circonscription ${area.ofName}"
    }

    companion object {
        /** The constituency shared by Saint-Barthélemy and Saint-Martin (ZX in the files of the ministry). */
        const val SAINT_BARTHELEMY_SAINT_MARTIN: String = "97701"

        /** First rounds of the elections whose results give constituencies (2022) and cantons (2021). */
        const val LEGISLATIVES_DATE: String = "2022-06-12"
        const val DEPARTEMENTALES_DATE: String = "2021-06-20"

        /**
         * Collectivities with departmental powers that have no departmental council, hence no
         * canton that matters for this directory: Corsica, Martinique, Guyane, Paris, Métropole de Lyon.
         */
        private val NO_DEPARTMENTAL_COUNCIL = setOf("20R", "972R", "973R", "75C", "69M")

        /** Departments and collectivities of the files of the Ministry of the Interior. */
        private val MINISTRY_AREAS = mapOf(
            "ZA" to "971", "ZB" to "972", "ZC" to "973", "ZD" to "974", "ZM" to "976",
            "ZS" to "975", "ZX" to "977", "ZW" to "986", "ZP" to "987", "ZN" to "988",
        )

        /** Reads the geography files of [open], each once. */
        fun read(open: (ElusFile) -> InputStream): Geography {
            val communes = HashMap<String, GeoCommune>()
            val districts = HashMap<String, String>()
            table(open, ElusFile.COG_COMMUNES, ',', listOf("TYPECOM", "COM", "DEP", "CTCD", "CAN", "LIBELLE", "COMPARENT")) { row ->
                when (row["TYPECOM"]) {
                    "COM" -> communes[row["COM"]] = GeoCommune(row["COM"], row["LIBELLE"], row["DEP"], row.optional("CTCD"), row.optional("CAN"))
                    // Paris, Lyon and Marseille: the postal codes are given per district.
                    "ARM" -> row.optional("COMPARENT")?.let { districts[row["COM"]] = it }
                }
            }
            val cantonNames = HashMap<String, String>()
            val realCantons = HashSet<String>()
            table(open, ElusFile.COG_CANTONS, ',', listOf("CAN", "LIBELLE", "TYPECT")) { row ->
                cantonNames[row["CAN"]] = row["LIBELLE"]
                if (row["TYPECT"] == "C") realCantons += row["CAN"]
            }
            val departments = HashMap<String, GeoArea>()
            table(open, ElusFile.COG_DEPARTEMENTS, ',', listOf("DEP", "REG", "TNCC", "NCCENR", "LIBELLE")) { row ->
                departments[row["DEP"]] = GeoArea(row["DEP"], row["LIBELLE"], row["NCCENR"], row["TNCC"].toIntOrNull() ?: 0, row.optional("REG"))
            }
            val regions = HashMap<String, String>()
            table(open, ElusFile.COG_REGIONS, ',', listOf("REG", "LIBELLE")) { row -> regions[row["REG"]] = row["LIBELLE"] }
            val collectivities = HashMap<String, GeoArea>()
            table(open, ElusFile.COG_COMER, ',', listOf("COMER", "TNCC", "NCCENR", "LIBELLE")) { row ->
                collectivities[row["COMER"]] = GeoArea(row["COMER"], row["LIBELLE"], row["NCCENR"], row["TNCC"].toIntOrNull() ?: 0, null)
            }
            table(open, ElusFile.COG_COMMUNES_COMER, ',', listOf("COM_COMER", "LIBELLE", "COMER")) { row ->
                communes.putIfAbsent(row["COM_COMER"], GeoCommune(row["COM_COMER"], row["LIBELLE"], row["COMER"], null, null))
            }
            val events = ArrayList<GeoEvent>()
            table(open, ElusFile.COG_EVENTS, ',', listOf("DATE_EFF", "TYPECOM_AV", "COM_AV", "TYPECOM_AP", "COM_AP")) { row ->
                if (row["TYPECOM_AV"] == "COM" && row["TYPECOM_AP"] == "COM" && row["COM_AV"] != row["COM_AP"]) {
                    events += GeoEvent(row["DATE_EFF"], row["COM_AV"], row["COM_AP"])
                }
            }
            events.sortWith(compareBy<GeoEvent>({ it.date }, { it.before }, { it.after }))

            val postal = HashMap<String, MutableSet<String>>()
            table(open, ElusFile.POSTAL_CODES, ';', listOf("#Code_commune_INSEE", "Code_postal")) { row ->
                val code = row["#Code_commune_INSEE"]
                val target = districts[code] ?: code
                val postalCode = row["Code_postal"]
                if (target.isNotEmpty() && postalCode.isNotEmpty()) postal.getOrPut(target) { sortedSetOf() } += postalCode
            }

            val legislatives = HashMap<String, MutableSet<String>>()
            table(open, ElusFile.LEGISLATIVES, ';', listOf("Code du département", "Code de la circonscription", "Code de la commune")) { row ->
                val area = ministryArea(row["Code du département"]) ?: return@table
                val commune = ministryCommune(row["Code du département"], row["Code de la commune"]) ?: return@table
                val number = row["Code de la circonscription"].takeIf { it.length in 1..2 && it.all(Char::isDigit) } ?: return@table
                legislatives.getOrPut(commune) { HashSet() } += area + number.padStart(2, '0')
            }
            val departementales = HashMap<String, MutableSet<String>>()
            table(open, ElusFile.DEPARTEMENTALES, ';', listOf("Code du département", "Code du canton", "Code de la commune")) { row ->
                val area = ministryArea(row["Code du département"]) ?: return@table
                val commune = ministryCommune(row["Code du département"], row["Code de la commune"]) ?: return@table
                val number = row["Code du canton"].takeIf { it.length in 1..2 && it.all(Char::isDigit) } ?: return@table
                departementales.getOrPut(commune) { HashSet() } += area + number.padStart(2, '0')
            }

            val current = communes.keys
            val circonscriptions = follow(legislatives, events, LEGISLATIVES_DATE, current)
            val split = follow(departementales, events, DEPARTEMENTALES_DATE, current)
            val cantons = HashMap<String, List<String>>()
            for (commune in communes.values) {
                if (commune.council == null || commune.council in NO_DEPARTMENTAL_COUNCIL) continue
                val canton = commune.canton
                val list = if (canton != null && canton in realCantons) listOf(canton) else split[commune.code]
                if (!list.isNullOrEmpty()) cantons[commune.code] = list
            }
            if (communes.isEmpty() || departments.isEmpty()) throw ElusFormatException("The Code officiel géographique has no commune or no department.")
            return Geography(
                communes = communes,
                departments = departments,
                collectivities = collectivities,
                regions = regions,
                cantonNames = cantonNames,
                postalCodes = postal.mapValues { it.value.toList() },
                cantons = cantons,
                circonscriptions = circonscriptions,
            )
        }

        /** `ZA` to `971`, `69` to `69`; null for the French abroad (`ZZ`) or an unknown code. */
        internal fun ministryArea(code: String): String? = when {
            code.length == 2 && (code.all(Char::isDigit) || code == "2A" || code == "2B") -> code
            else -> MINISTRY_AREAS[code]
        }

        /** The INSEE code of commune [commune] (three digits) of department [department] in the files of the ministry. */
        internal fun ministryCommune(department: String, commune: String): String? {
            if (commune.length != 3 || !commune.all(Char::isDigit)) return null
            val area = ministryArea(department) ?: return null
            // Overseas, the ministry numbers communes with three digits after `97` or `98` (ZA 101 = 97101).
            return if (area.length == 3) area.take(2) + commune else area + commune
        }

        /**
         * Carries the values of a table made on [date] to the communes of today: every event after
         * that date (merger, code change, restoration), in order, gives the values of the old code to
         * the new one. Only the communes of [current] are kept.
         */
        internal fun follow(values: Map<String, Set<String>>, events: List<GeoEvent>, date: String, current: Set<String>): Map<String, List<String>> {
            val carried = HashMap<String, MutableSet<String>>()
            values.forEach { (code, set) -> carried[code] = HashSet(set) }
            for (event in events) {
                if (event.date <= date) continue
                val old = carried[event.before] ?: continue
                carried.getOrPut(event.after) { HashSet() } += old
            }
            return carried.filterKeys { it in current }.mapValues { it.value.sorted() }
        }

        private fun table(open: (ElusFile) -> InputStream, file: ElusFile, separator: Char, columns: List<String>, action: (CsvRow) -> Unit) {
            CsvTable.open(file.fileName, CsvInput.reader(open(file)), separator, columns).use { it.forEach(action) }
        }
    }
}

/** An event of the Code officiel géographique: on [date], commune [before] became (part of) [after]. */
data class GeoEvent(val date: String, val before: String, val after: String)
