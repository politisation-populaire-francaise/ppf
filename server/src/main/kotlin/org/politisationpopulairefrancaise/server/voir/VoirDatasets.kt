package org.politisationpopulairefrancaise.server.voir

import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.voir.VoirCatalog
import org.politisationpopulairefrancaise.server.export.Column
import org.politisationpopulairefrancaise.server.export.ColumnType
import org.politisationpopulairefrancaise.server.export.ExportContents
import org.politisationpopulairefrancaise.server.export.OpenDataset
import org.politisationpopulairefrancaise.server.export.OpenDatasets
import java.math.BigDecimal

/**
 * The tables of « Voir » in the open export (M34, S-48; docs/integration/export-ouvert.md, section 3):
 * the values of the indicators, the public aid and the seats of the parties. Every line names the content
 * it comes from (stable address and version) and its source. No table of persons, cases or sentences
 * before the review of the lawyer (RGPD article 10): those contents stay in the file of signed contents only.
 * The catalog of « Voir » is read as the applications and the site read it ([VoirCatalog.from]).
 */
object VoirDatasets {
    private fun ExportContents.voir(): VoirCatalog = derived("voir") { VoirCatalog.from(articles) }

    private fun address(address: ContentAddress): String = "${address.kind}:${address.pubkey}:${address.identifier}"

    private val CONTENT = Column("content", ColumnType.STRING, "Adresse stable du contenu de PPF d'où vient la ligne (kind:pubkey:d).")
    private val VERSION = Column("version", ColumnType.STRING, "Identifiant de la version de ce contenu (SHA-256 de l'événement signé), à retrouver dans contenus.json.")
    private val SOURCE_KIND = Column("source_kind", ColumnType.STRING, "Type de source : autorites, organisateurs, comptage-independant, medias, signalements, texte-officiel, justice, parlement, rapport, declaration ou enquete-presse.")
    private val SOURCE_NAME = Column("source_name", ColumnType.STRING, "Nom de la source.")
    private val SOURCE_URL = Column("source_url", ColumnType.URL, "Adresse de la source, quand elle est publiée.", nullable = true)
    private val SOURCE_DATE = Column("source_date", ColumnType.DATE, "Date de la source, quand elle est connue.", nullable = true)

    val MEASURES: OpenDataset = object : OpenDataset {
        override val id = "voir-mesures"
        override val title = "Chiffres de l'observatoire"
        override val description = "Chaque valeur des indicateurs de « Voir » (M2), source par source : jamais fusionnées, les sources différentes d'une même occasion sont des lignes séparées."
        override val space = "voir"
        override val version = 1
        override val columns = listOf(
            CONTENT,
            Column("indicator_title", ColumnType.STRING, "Titre de l'indicateur."),
            Column("unit", ColumnType.STRING, "Unité des valeurs."),
            Column("measure_id", ColumnType.STRING, "Identifiant de la valeur, stable dans son indicateur."),
            Column("date", ColumnType.DATE, "Date de la valeur."),
            Column("value", ColumnType.NUMBER, "La valeur."),
            Column("part", ColumnType.NUMBER, "La part nommée par l'indicateur (par exemple « dont mineurs »), quand elle est connue.", nullable = true),
            Column("part_label", ColumnType.STRING, "Ce que compte la part.", nullable = true),
            Column("occasion", ColumnType.STRING, "Ce que compte la valeur (par exemple « Journée du 18 septembre »).", nullable = true),
            Column("place", ColumnType.STRING, "Lieu de la valeur.", nullable = true),
            Column("reliability", ColumnType.STRING, "Fiabilité : elevee, moyenne ou faible."),
            Column("declarative", ColumnType.STRING, "oui si l'indicateur repose sur des déclarations, non sinon."),
            SOURCE_KIND, SOURCE_NAME, SOURCE_URL, SOURCE_DATE,
            VERSION,
        )

        override fun rows(contents: ExportContents): List<List<Any?>> = contents.voir().indicators
            .sortedBy { address(it.address) }
            .flatMap { indicator ->
                indicator.measures.sortedWith(compareBy({ it.date }, { it.id })).map { measure ->
                    listOf(
                        address(indicator.address), indicator.article.title, indicator.unit, measure.id, measure.date.toIso(), measure.value,
                        measure.part, indicator.breakdown?.takeIf { measure.part != null }, measure.occasion, measure.place, measure.reliability.tagValue,
                        if (indicator.declarative) "oui" else "non",
                        measure.source.kind.tagValue, measure.source.name, measure.source.url, measure.source.date?.toIso(),
                        indicator.article.event.id,
                    )
                }
            }
    }

    val PARTY_FUNDING: OpenDataset = object : OpenDataset {
        override val id = "voir-partis-aides"
        override val title = "Aide publique aux partis"
        override val description = "L'aide publique de l'État versée à chaque parti présent dans « Voir », année par année, avec son décret de répartition (loi n° 88-227, articles 8 et 9)."
        override val space = "voir"
        override val version = 1
        override val columns = listOf(
            CONTENT,
            Column("party_name", ColumnType.STRING, "Nom du parti."),
            Column("year", ColumnType.INTEGER, "Année de l'aide."),
            Column("amount_eur", ColumnType.NUMBER, "Montant en euros, au centime."),
            Column("decree", ColumnType.STRING, "Décret de répartition.", nullable = true),
            SOURCE_URL,
            VERSION,
        )

        override fun rows(contents: ExportContents): List<List<Any?>> = contents.voir().parties
            .sortedBy { address(it.address) }
            .flatMap { party ->
                party.publicFunding.sortedBy { it.year }.map { funding ->
                    listOf(
                        address(party.address), party.name, funding.year, BigDecimal.valueOf(funding.amountCents, 2),
                        funding.decree?.name, funding.decree?.url, party.article.event.id,
                    )
                }
            }
    }

    val PARTY_SEATS: OpenDataset = object : OpenDataset {
        override val id = "voir-partis-sieges"
        override val title = "Sièges des partis"
        override val description = "Le nombre de sièges de chaque parti présent dans « Voir », par assemblée et par date, tel que chaque source le donne : plusieurs sources de la même date restent côte à côte."
        override val space = "voir"
        override val version = 1
        override val columns = listOf(
            CONTENT,
            Column("party_name", ColumnType.STRING, "Nom du parti."),
            Column("chamber", ColumnType.STRING, "Assemblée : assemblee, senat, parlement-europeen ou autre."),
            Column("seats", ColumnType.INTEGER, "Nombre de sièges."),
            Column("date", ColumnType.DATE, "Date du décompte.", nullable = true),
            Column("source_kind", ColumnType.STRING, SOURCE_KIND.description, nullable = true),
            Column("source_name", ColumnType.STRING, SOURCE_NAME.description, nullable = true),
            SOURCE_URL,
            VERSION,
        )

        override fun rows(contents: ExportContents): List<List<Any?>> = contents.voir().parties
            .sortedBy { address(it.address) }
            .flatMap { party ->
                party.seats.sortedWith(compareBy({ it.chamber.ordinal }, { it.date })).map { seats ->
                    listOf(
                        address(party.address), party.name, seats.chamber.tagValue, seats.count, seats.date?.toIso(),
                        seats.source?.kind?.tagValue, seats.source?.name, seats.source?.url, party.article.event.id,
                    )
                }
            }
    }

    val all: List<OpenDataset> = listOf(MEASURES, PARTY_FUNDING, PARTY_SEATS)

    fun registerOn(datasets: OpenDatasets) = all.forEach(datasets::register)
}
