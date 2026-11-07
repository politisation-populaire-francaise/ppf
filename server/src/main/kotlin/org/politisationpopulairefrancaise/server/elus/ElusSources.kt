package org.politisationpopulairefrancaise.server.elus

import java.io.Closeable
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.Properties

/**
 * The files an import reads, with the name they have in the example set and in a local directory.
 * The officials come from the RNE; the rest is geography (docs/integration/elus-serveur.md, section 1).
 */
enum class ElusFile(val fileName: String) {
    MUNICIPAL("elus-conseillers-municipaux-cm.csv"),
    COMMUNAUTAIRE("elus-conseillers-communautaires-epci.csv"),
    DEPARTEMENTAL("elus-conseillers-departementaux-cd.csv"),
    REGIONAL("elus-conseillers-regionaux-cr.csv"),
    COLLECTIVITE("elus-membres-assemblee-ma.csv"),
    DEPUTE("elus-deputes-dep.csv"),
    SENATEUR("elus-senateurs-sen.csv"),
    EUROPEEN("elus-representants-parlement-europeen-rpe.csv"),

    /** Code officiel géographique (Insee): communes, municipal districts, delegated and associated communes. */
    COG_COMMUNES("v_commune.csv"),
    COG_CANTONS("v_canton.csv"),
    COG_DEPARTEMENTS("v_departement.csv"),
    COG_REGIONS("v_region.csv"),

    /** Overseas collectivities (975, 977, 978, 986, 987, 988...) and their communes. */
    COG_COMER("v_comer.csv"),
    COG_COMMUNES_COMER("v_commune_comer.csv"),

    /** Events of the communes since 1943 (mergers, code changes), to follow a code of 2021 or 2022. */
    COG_EVENTS("v_mvt_commune.csv"),

    /** Base officielle des codes postaux (La Poste). */
    POSTAL_CODES("base-officielle-codes-postaux.csv"),

    /** Commune-level results of the 2022 legislative elections, first round: commune to constituencies. */
    LEGISLATIVES("legislatives-2022-communes.txt"),

    /** Commune-level results of the 2021 departmental elections, first round: commune to cantons. */
    DEPARTEMENTALES("departementales-2021-communes.txt"),
    ;

    companion object {
        val RNE: List<ElusFile> = listOf(MUNICIPAL, COMMUNAUTAIRE, DEPARTEMENTAL, REGIONAL, COLLECTIVITE, DEPUTE, SENATEUR, EUROPEEN)
    }
}

/**
 * Where an import reads its files. [prepare] makes every file available (a download for the
 * official source) and gives the date of the last update of the RNE; [open] is then called once per
 * file. [close] removes whatever [prepare] wrote.
 */
interface ElusSource : Closeable {
    /** Whether the officials are fictitious ([ExampleSource]): written in every package and in the index. */
    val example: Boolean

    /** Value of `elus_import.source`: `example` or `official`. */
    val kind: String

    fun prepare(): LocalDate

    fun open(file: ElusFile): InputStream

    override fun close() {}
}

/**
 * The fictitious set embedded in the server (resources `elus/example/`), in the format of the real
 * files: every person is called `[EXEMPLE] Prénom Nom`; the geography (communes, codes, cantons,
 * constituencies, postal codes) is real. Contacts nobody.
 */
class ExampleSource : ElusSource {
    override val example: Boolean = true
    override val kind: String = ElusSources.EXAMPLE

    override fun prepare(): LocalDate {
        val properties = Properties()
        resource("source.properties").use { properties.load(it.reader(Charsets.UTF_8)) }
        return LocalDate.parse(properties.getProperty("updatedOn") ?: throw ElusFormatException("source.properties lacks updatedOn."))
    }

    override fun open(file: ElusFile): InputStream = resource(file.fileName)

    private fun resource(name: String): InputStream =
        ExampleSource::class.java.getResourceAsStream("$RESOURCE_DIRECTORY/$name")
            ?: throw ElusFormatException("The example file $name is missing from the server.")

    companion object {
        const val RESOURCE_DIRECTORY: String = "/elus/example"
    }
}

/**
 * Files already in a local directory, named as [ElusFile.fileName] (tests, and the manual check of
 * the real files described in docs/integration/elus-serveur.md). Files missing from [directory] are
 * taken from [fallback] when one is given.
 */
class DirectorySource(
    private val directory: Path,
    private val updatedOn: LocalDate,
    override val example: Boolean,
    private val fallback: ElusSource? = null,
) : ElusSource {
    override val kind: String = if (example) ElusSources.EXAMPLE else ElusSources.OFFICIAL

    override fun prepare(): LocalDate {
        fallback?.prepare()
        return updatedOn
    }

    override fun open(file: ElusFile): InputStream {
        val path = directory.resolve(file.fileName)
        if (Files.exists(path)) return Files.newInputStream(path)
        return fallback?.open(file) ?: throw ElusFormatException("${file.fileName} is missing.")
    }

    override fun close() {
        fallback?.close()
    }
}

object ElusSources {
    const val EXAMPLE: String = "example"
    const val OFFICIAL: String = "official"
}
