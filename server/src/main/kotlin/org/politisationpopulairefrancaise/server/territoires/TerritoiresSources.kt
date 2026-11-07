package org.politisationpopulairefrancaise.server.territoires

import org.politisationpopulairefrancaise.server.elus.ElusFormatException
import java.io.Closeable
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.Properties

/**
 * The files an import reads, with the name they have in the example set and in the directory
 * where the official source writes them (docs/integration/territoires.md, section 1).
 */
enum class TerritoiresFile(val fileName: String) {
    /** Code officiel géographique (Insee): communes and municipal districts, with their CTCD. */
    COG_COMMUNES("v_commune.csv"),
    COG_DEPARTEMENTS("v_departement.csv"),
    COG_REGIONS("v_region.csv"),

    /** Collectivities with the powers of a department. */
    COG_CTCD("v_ctcd.csv"),
    COG_COMER("v_comer.csv"),
    COG_COMMUNES_COMER("v_commune_comer.csv"),

    /** Events of the communes (mergers, code changes), to carry the figures of an older geography. */
    COG_EVENTS("v_mvt_commune.csv"),

    /** Base officielle des codes postaux (La Poste). */
    POSTAL_CODES("base-officielle-codes-postaux.csv"),

    /** The figures of Melodi (populations, registered voters), one per line: `niveau;code;grandeur;valeur`. */
    STATISTICS("statistiques.csv"),

    /** The composition of the intercommunalities: `siren;categorie;nom;commune`. */
    EPCI("epci.csv"),
}

/** What the figures of an import describe. */
data class TerritoiresDates(
    /** Day of the geography (COG). */
    val geography: LocalDate,
    /** Day of the geography of the figures of Melodi (one year older than the COG). */
    val statisticsGeography: LocalDate,
    val populationVintage: Int,
    val populationInForce: LocalDate,
    val electorateDate: LocalDate,
) {
    /** The newest of the dates of the sources: the `builtOn` of the documents, stable from one import of the same sources to the next. */
    val builtOn: LocalDate get() = listOf(geography, populationInForce, electorateDate).max()

    companion object {
        fun read(properties: Properties): TerritoiresDates {
            fun date(key: String) = properties.getProperty(key)?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }
                ?: throw ElusFormatException("source.properties lacks a valid $key.")
            return TerritoiresDates(
                geography = date("geography"),
                statisticsGeography = date("statisticsGeography"),
                populationVintage = properties.getProperty("populationVintage")?.trim()?.toIntOrNull()
                    ?: throw ElusFormatException("source.properties lacks populationVintage."),
                populationInForce = date("populationInForce"),
                electorateDate = date("electorateDate"),
            )
        }
    }
}

/**
 * Where an import reads its files. [prepare] makes every file available (downloads for the
 * official source) and gives the dates of the figures; [open] is then called once per file.
 */
interface TerritoiresSource : Closeable {
    /** True for the example set: real public figures for a few departments only ([EXEMPLE]). */
    val example: Boolean

    /** Value of `territoires_import.source`: `example` or `official`. */
    val kind: String

    fun prepare(): TerritoiresDates

    fun open(file: TerritoiresFile): InputStream

    override fun close() {}
}

/**
 * The example set embedded in the server (resources `territoires/example/`): real public
 * statistics of the Insee for the Rhône, Paris, Corse-du-Sud, Martinique and three overseas
 * collectivities, and the whole national level. Contacts nobody.
 */
class ExampleTerritoiresSource : TerritoiresSource {
    override val example: Boolean = true
    override val kind: String = TerritoiresSources.EXAMPLE

    override fun prepare(): TerritoiresDates {
        val properties = Properties()
        resource("source.properties").use { properties.load(it.reader(Charsets.UTF_8)) }
        return TerritoiresDates.read(properties)
    }

    override fun open(file: TerritoiresFile): InputStream = resource(file.fileName)

    private fun resource(name: String): InputStream =
        ExampleTerritoiresSource::class.java.getResourceAsStream("$RESOURCE_DIRECTORY/$name")
            ?: throw ElusFormatException("The example file $name is missing from the server.")

    companion object {
        const val RESOURCE_DIRECTORY: String = "/territoires/example"
    }
}

/** Files already in a local directory (tests, manual checks), named as [TerritoiresFile.fileName]. */
class DirectoryTerritoiresSource(
    private val directory: Path,
    private val dates: TerritoiresDates,
    override val example: Boolean,
) : TerritoiresSource {
    override val kind: String = if (example) TerritoiresSources.EXAMPLE else TerritoiresSources.OFFICIAL

    override fun prepare(): TerritoiresDates = dates

    override fun open(file: TerritoiresFile): InputStream {
        val path = directory.resolve(file.fileName)
        if (!Files.exists(path)) throw ElusFormatException("${file.fileName} is missing.")
        return Files.newInputStream(path)
    }
}

object TerritoiresSources {
    const val EXAMPLE: String = "example"
    const val OFFICIAL: String = "official"

    /** The figures Melodi lacks (Mayotte, overseas collectivities), kept by hand with their source: `niveau;code;grandeur;valeur;source`. */
    const val SUPPLEMENTS_RESOURCE: String = "/territoires/outre-mer.csv"

    fun supplements(): InputStream = TerritoiresSources::class.java.getResourceAsStream(SUPPLEMENTS_RESOURCE)
        ?: throw ElusFormatException("The supplements of the overseas collectivities are missing from the server.")
}
