package org.politisationpopulairefrancaise.server.elus

import java.io.InputStream
import java.text.Normalizer
import java.time.DateTimeException
import java.time.LocalDate

/**
 * One official as staged during an import: only what the contract keeps (section 1.2), plus
 * [area] (where the RNE files them, to build the packages) and [rank] (to sort the councils).
 */
data class StagedOfficial(
    val area: String,
    val mandate: Mandate,
    val territory: String,
    val commune: String?,
    val name: String,
    val function: String?,
    val rank: Int,
    val since: String,
    val functionSince: String?,
)

/** The intercommunalities seen in the file of their councillors: names, and the commune of each. */
class EpciCollector {
    val names = HashMap<String, String>()
    private val communes = HashMap<String, HashMap<String, Int>>()

    fun add(siren: String, name: String?, commune: String?) {
        if (name != null) names.putIfAbsent(siren, name)
        if (commune != null) communes.getOrPut(commune) { HashMap() }.merge(siren, 1, Int::plus)
    }

    /** The intercommunality of each commune: the one most of its councillors sit in (ties: the smallest SIREN). */
    fun communeEpcis(): Map<String, String> = communes.mapValues { (_, counts) ->
        counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).first().key
    }
}

/** What reading one file of the RNE gave. */
data class RneFileResult(val staged: Int, val skipped: Int, val abroad: Int)

/**
 * Reads the files of the RNE (docs/integration/elus-serveur.md, section 1.1). Columns are found by
 * their names; the personal columns the contract excludes (sex, date of birth, occupation,
 * nationality) are never declared, hence never read.
 */
object Rne {
    const val DEPARTMENT: String = "Code du département"
    const val COLLECTIVITY: String = "Code de la collectivité à statut particulier"
    const val COMMUNE: String = "Code de la commune"
    const val SIREN: String = "N° SIREN"
    const val EPCI_NAME: String = "Libellé de l'EPCI"
    const val ATTACHED_COMMUNE: String = "Code de la commune de rattachement"
    const val CANTON: String = "Code du canton"
    const val REGION: String = "Code de la région"
    const val SECTION: String = "Code de la section départementale"
    const val CONSTITUENCY: String = "Code de la circonscription législative"
    const val LAST_NAME: String = "Nom de l'élu"
    const val FIRST_NAME: String = "Prénom de l'élu"
    const val SINCE: String = "Date de début du mandat"
    const val FUNCTION: String = "Libellé de la fonction"
    const val FUNCTION_SINCE: String = "Date de début de la fonction"

    /** Area of the deputies and senators of the French established abroad: no package holds them. */
    const val ABROAD: String = "ZZ"

    /** Area of the Collectivité de Corse, whose RNE code (94) is also the code of the Val-de-Marne. */
    const val CORSICA: String = "20R"

    private val COLLECTIVITY_AREAS = mapOf("94" to CORSICA)

    private val PERSON = listOf(LAST_NAME, FIRST_NAME, SINCE)
    private val WITH_FUNCTION = PERSON + listOf(FUNCTION, FUNCTION_SINCE)

    /** The columns read in each file of the RNE. */
    fun columns(file: ElusFile): List<String> = when (file) {
        ElusFile.MUNICIPAL -> listOf(DEPARTMENT, COLLECTIVITY, COMMUNE) + WITH_FUNCTION
        ElusFile.COMMUNAUTAIRE -> listOf(DEPARTMENT, COLLECTIVITY, SIREN, EPCI_NAME, ATTACHED_COMMUNE) + WITH_FUNCTION
        ElusFile.DEPARTEMENTAL -> listOf(DEPARTMENT, CANTON) + WITH_FUNCTION
        ElusFile.REGIONAL -> listOf(REGION, SECTION) + WITH_FUNCTION
        ElusFile.COLLECTIVITE -> listOf(DEPARTMENT, COLLECTIVITY) + WITH_FUNCTION
        ElusFile.DEPUTE -> listOf(DEPARTMENT, COLLECTIVITY, CONSTITUENCY) + PERSON
        ElusFile.SENATEUR -> listOf(DEPARTMENT, COLLECTIVITY) + PERSON
        ElusFile.EUROPEEN -> PERSON
        else -> throw IllegalArgumentException("${file.fileName} is not a file of the RNE.")
    }

    fun mandateOf(file: ElusFile): Mandate = when (file) {
        ElusFile.MUNICIPAL -> Mandate.MUNICIPAL
        ElusFile.COMMUNAUTAIRE -> Mandate.COMMUNAUTAIRE
        ElusFile.DEPARTEMENTAL -> Mandate.DEPARTEMENTAL
        ElusFile.REGIONAL -> Mandate.REGIONAL
        ElusFile.COLLECTIVITE -> Mandate.COLLECTIVITE
        ElusFile.DEPUTE -> Mandate.DEPUTE
        ElusFile.SENATEUR -> Mandate.SENATEUR
        ElusFile.EUROPEEN -> Mandate.EUROPEEN
        else -> throw IllegalArgumentException("${file.fileName} is not a file of the RNE.")
    }

    /**
     * Reads [file] from [input] and gives each official to [sink]; the intercommunalities go to
     * [epcis] as well. A record without area, territory, name or valid start date is skipped and counted.
     */
    fun read(file: ElusFile, input: InputStream, epcis: EpciCollector, sink: (StagedOfficial) -> Unit): RneFileResult {
        val mandate = mandateOf(file)
        var staged = 0
        var skipped = 0
        var abroad = 0
        CsvTable.open(file.fileName, CsvInput.reader(input), ';', columns(file)).use { table ->
            table.forEach { row ->
                val place = place(file, row)
                if (place == null) {
                    skipped++
                    return@forEach
                }
                val (area, territory, commune) = place
                if (area == ABROAD) {
                    abroad++
                    return@forEach
                }
                val name = listOf(row[FIRST_NAME], row[LAST_NAME]).filter { it.isNotEmpty() }.joinToString(" ").replace(SPACES, " ")
                val since = isoDate(row[SINCE])
                if (name.isEmpty() || since == null) {
                    skipped++
                    return@forEach
                }
                val function = if (file in FUNCTION_FILES) row.optional(FUNCTION)?.replace(SPACES, " ") else null
                val functionSince = if (file in FUNCTION_FILES) row.optional(FUNCTION_SINCE)?.let(::isoDate) else null
                if (file == ElusFile.COMMUNAUTAIRE) epcis.add(territory, row.optional(EPCI_NAME), commune)
                sink(StagedOfficial(area, mandate, territory, commune, name, function, rank(function), since, functionSince))
                staged++
            }
            skipped += table.skipped
        }
        return RneFileResult(staged, skipped, abroad)
    }

    private val FUNCTION_FILES = setOf(ElusFile.MUNICIPAL, ElusFile.COMMUNAUTAIRE, ElusFile.DEPARTEMENTAL, ElusFile.REGIONAL, ElusFile.COLLECTIVITE)

    /** Area, territory and represented commune of a record, or null when the record says none. */
    private fun place(file: ElusFile, row: CsvRow): Triple<String, String, String?>? {
        // Without a department, the collectivity; Corsica (94 in the RNE) becomes 20R, as in the
        // Code officiel géographique, not to be taken for the Val-de-Marne.
        fun areaOf(): String? = row.optional(DEPARTMENT) ?: row.optional(COLLECTIVITY)?.let { COLLECTIVITY_AREAS[it] ?: it }
        return when (file) {
            ElusFile.MUNICIPAL -> Triple(areaOf() ?: return null, row.optional(COMMUNE) ?: return null, null)
            ElusFile.COMMUNAUTAIRE -> Triple(areaOf() ?: return null, row.optional(SIREN) ?: return null, row.optional(ATTACHED_COMMUNE))
            ElusFile.DEPARTEMENTAL -> Triple(row.optional(DEPARTMENT) ?: return null, row.optional(CANTON) ?: return null, null)
            ElusFile.REGIONAL -> Triple(section(row.optional(SECTION) ?: return null), row.optional(REGION) ?: return null, null)
            ElusFile.COLLECTIVITE -> Triple(areaOf() ?: return null, row.optional(COLLECTIVITY) ?: return null, null)
            ElusFile.DEPUTE -> Triple(areaOf() ?: return null, row.optional(CONSTITUENCY) ?: return null, null)
            ElusFile.SENATEUR -> areaOf()?.let { Triple(it, it, null) }
            ElusFile.EUROPEEN -> Triple(NATIONAL_PACKAGE, NATIONAL_PACKAGE, null)
            else -> null
        }
    }

    /** A regional section as a department code: the file writes `2` for the Aisne, `8` for the Ardennes. */
    internal fun section(code: String): String = if (code.length == 1 && code[0].isDigit()) "0$code" else code

    /** `AAAA-MM-JJ` (current files) or `JJ/MM/AAAA` (older ones) as `AAAA-MM-JJ`; null when invalid. */
    internal fun isoDate(value: String): String? {
        val text = value.trim()
        val iso = ISO_DATE.matchEntire(text)?.let { "${it.groupValues[1]}-${it.groupValues[2]}-${it.groupValues[3]}" }
            ?: FRENCH_DATE.matchEntire(text)?.let { "${it.groupValues[3]}-${it.groupValues[2]}-${it.groupValues[1]}" }
            ?: return null
        return try {
            LocalDate.parse(iso).toString()
        } catch (e: DateTimeException) {
            null
        }
    }

    /**
     * Order of an official in their council, for sorting only: 0 for the mayor or the president,
     * n for the n-th deputy or vice-president, then the other functions, then the members.
     */
    internal fun rank(function: String?): Int {
        if (function == null) return MEMBER
        val f = Normalizer.normalize(function, Normalizer.Form.NFD).replace(MARKS, "").lowercase().replace('’', '\'').trim()
        if (f == "maire" || f.startsWith("president du conseil") || f == "president de l'assemblee" || f == "president de l'organe executif") return 0
        ORDINAL.find(f)?.let { match -> return match.groupValues[1].toInt().coerceIn(1, 499) }
        if (f.startsWith("vice-president")) return 500
        if (f == "autre membre") return MEMBER
        return 1_000
    }

    private const val MEMBER = 10_000
    private val SPACES = Regex("\\s+")
    private val MARKS = Regex("\\p{M}+")
    private val ORDINAL = Regex("^(\\d{1,3})\\s*(?:er|ere|eme|e)?\\s+(?:adjoint|vice-president)")
    private val ISO_DATE = Regex("(\\d{4})-(\\d{2})-(\\d{2})")
    private val FRENCH_DATE = Regex("(\\d{2})/(\\d{2})/(\\d{4})")
}
