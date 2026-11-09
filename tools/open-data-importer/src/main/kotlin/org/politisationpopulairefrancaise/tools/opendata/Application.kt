package org.politisationpopulairefrancaise.tools.opendata

import org.politisationpopulairefrancaise.core.voir.application.ApplicationFormat
import org.politisationpopulairefrancaise.core.voir.application.ApplicationMeasure
import org.politisationpopulairefrancaise.core.voir.application.ApplicationPackage
import org.politisationpopulairefrancaise.core.voir.application.LawApplication
import org.politisationpopulairefrancaise.core.voir.application.MeasureState
import org.politisationpopulairefrancaise.core.voir.parlement.DataSource
import org.politisationpopulairefrancaise.core.voir.parlement.ImportGuard
import java.io.InputStream

/*
 * « Applicomètre » (VO-36; docs/integration/voir-applicometre.md): the two files of the baromètre de l'application
 * des lois of the Assemblée nationale (dataset « Données du baromètre de l'application des lois », LexImpact,
 * data.gouv.fr, Licence Ouverte 2.0): the laws promulgated since 2002 and the measures of their schedules,
 * joined by the Légifrance identifier of the law. Semicolons, no quotes; a text field may hold a semicolon,
 * so each row is read from its identifiers, never from its position alone. Only what the page needs is kept:
 * neither the president nor the government of the day, nor the keywords.
 */
object BarometerCsv {
    /** Columns after the identifier of the law in the file of the laws (21 columns in all). */
    private const val LAW_COLUMNS_AFTER_ID = 18
    /** Columns between the identifier of the law and the subject in the file of the measures (17 columns in all). */
    private const val MEASURE_COLUMNS_AFTER_ID = 9
    /** Columns at the end of a measure: decree, planned date, state, delay. */
    private const val MEASURE_TAIL = 4

    private fun lines(input: InputStream, name: String): List<List<String>> {
        val text = input.use { readBounded(it, MAX_BYTES, name) }.decodeToString().removePrefix("﻿")
        val rows = text.lineSequence().filter { it.isNotBlank() }.map { it.split(';') }.toList()
        if (rows.isEmpty()) throw UnreadableSourceException("$name: empty file")
        return rows.drop(1) // the header
    }

    private fun idIndex(row: List<String>): Int = row.indexOfFirst { ApplicationFormat.isLegifranceId(it.trim()) }

    private fun clean(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

    private fun yes(value: String): Boolean = value.trim().equals("oui", ignoreCase = true)

    /** The laws, by Légifrance identifier, without their measures. */
    fun laws(input: InputStream): List<LawApplication> = lines(input, "laws").mapNotNull { row ->
        val id = idIndex(row)
        if (id < 1 || row.size < id + 1 + LAW_COLUMNS_AFTER_ID) return@mapNotNull null
        val after = row.subList(id + 1, row.size)
        val legislature = after[6].trim().toIntOrNull() ?: return@mapNotNull null
        val publishedOn = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$").find(row[0].trim())?.value ?: return@mapNotNull null
        val dossier = clean(after[1])?.takeIf { Regex("^[A-Za-z0-9]{1,64}$").matches(it) }
        LawApplication(
            id = row[id].trim(),
            title = row.subList(1, id).joinToString(";").trim(),
            publishedOn = publishedOn,
            legislature = legislature,
            dossier = dossier?.let { "an-dossier:$it" },
            dossierPath = clean(after[2]),
            committee = clean(after[5]),
            withoutSchedule = yes(after[9]),
            directApplication = yes(after[10]),
            ratification = yes(after[11]),
            enabling = yes(after[12]),
            ordinanceRatification = yes(after[13]),
            toApply = after[14].trim().toIntOrNull() ?: 0,
            applied = after[15].trim().toIntOrNull() ?: 0,
        )
    }

    /** The measures, by Légifrance identifier of their law; a row with an unknown state is refused. */
    fun measures(input: InputStream): Map<String, List<ApplicationMeasure>> {
        val out = LinkedHashMap<String, MutableList<ApplicationMeasure>>()
        lines(input, "measures").forEach { row ->
            val id = idIndex(row)
            if (id < 1 || row.size < id + 1 + MEASURE_COLUMNS_AFTER_ID + 2 + MEASURE_TAIL) return@forEach
            val after = row.subList(id + 1, row.size)
            val tail = after.subList(after.size - MEASURE_TAIL, after.size)
            // Subject then observations: when a semicolon was in the text, the last field is the observation.
            val middle = after.subList(MEASURE_COLUMNS_AFTER_ID, after.size - MEASURE_TAIL)
            val subject = clean(middle.dropLast(1).joinToString(";"))
            val observation = clean(middle.last())
            val state = MeasureState.of(tail[2].trim()) ?: throw UnreadableSourceException("measures: unknown state of a measure")
            val order = after[0].trim().toIntOrNull() ?: return@forEach
            out.getOrPut(row[id].trim()) { ArrayList() } += ApplicationMeasure(
                order = order,
                articles = clean(after[1]),
                legalBasis = clean(after[8]),
                subject = subject,
                observation = observation,
                decree = clean(tail[0])?.takeIf(ApplicationFormat::isLegifranceId),
                state = state,
                delayDays = tail[3].trim().toIntOrNull()?.takeIf { it >= 0 },
                plannedMonth = if (state == MeasureState.PENDING) ApplicationFormat.plannedMonth(tail[1], observation) else null,
            )
        }
        return out
    }

    /** One package per legislature of [ApplicationFormat.LEGISLATURES], newest law first. */
    fun packages(lawsFile: InputStream, measuresFile: InputStream, example: Boolean, today: String): Map<Int, ApplicationPackage> {
        val byLaw = measures(measuresFile)
        val read = laws(lawsFile).filter { it.legislature in ApplicationFormat.LEGISLATURES }.distinctBy { it.id }
        if (read.isEmpty()) throw UnreadableSourceException("laws: no law of the legislatures ${ApplicationFormat.LEGISLATURES}")
        return read.map { law -> law.copy(measures = byLaw[law.id].orEmpty().sortedBy { it.order }) }
            .groupBy { it.legislature }
            .mapValues { (legislature, list) ->
                ApplicationPackage(
                    example = example,
                    legislature = legislature,
                    updatedOn = today,
                    laws = list.sortedWith(compareByDescending<LawApplication> { it.publishedOn }.thenBy { it.id }),
                )
            }
    }

    /** The two files together stay well under this size (about 9 MB in November 2026). */
    private const val MAX_BYTES: Long = 64L * 1024 * 1024
}

/**
 * The packages of one import, or null when the application of the laws is not asked or not available this time
 * (the previous packages are then kept by the importer). A package holding 20 % fewer laws than the previous
 * one is refused (a truncated file).
 */
class ApplicationImport(private val sources: ImportSources, private val log: (String) -> Unit) {
    fun files(previousLaws: (Int) -> Int?): Map<String, ByteArray>? {
        val laws = sources.applicationLaws()
        val measures = sources.applicationMeasures()
        if (laws == null || measures == null) {
            laws?.close()
            measures?.close()
            log("application of the laws: no file this time; the previous packages are kept")
            return null
        }
        val packages = BarometerCsv.packages(laws, measures, sources.example, sources.today)
        packages.forEach { (legislature, pkg) ->
            val before = previousLaws(legislature)
            if (!ImportGuard.accepts(before, pkg.laws.size)) throw GuardException("application of the laws, legislature $legislature: ${pkg.laws.size} law(s) instead of $before")
        }
        packages.toSortedMap().forEach { (legislature, pkg) ->
            log("application of the laws, legislature $legislature: ${pkg.laws.size} law(s), ${pkg.laws.sumOf { it.measures.size }} measure(s)")
        }
        return packages.map { (legislature, pkg) -> ApplicationFormat.packageName(legislature) to ApplicationFormat.write(pkg).encodeToByteArray() }.toMap()
    }

    companion object {
        /** The source of the index. */
        fun dataSource(today: String) = DataSource(
            ApplicationFormat.SOURCE_ID,
            "Assemblée nationale (LexImpact), baromètre de l'application des lois, d'après les échéanciers de Légifrance",
            "https://www.data.gouv.fr/datasets/donnees-du-barometre-de-lapplication-des-lois",
            "Licence Ouverte 2.0",
            today,
        )
    }
}
