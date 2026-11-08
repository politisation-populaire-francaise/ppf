package org.politisationpopulairefrancaise.tools.opendata

import kotlinx.serialization.json.JsonElement
import org.politisationpopulairefrancaise.core.voir.parlement.ImportGuard
import org.politisationpopulairefrancaise.core.voir.parrainages.SponsorMandate
import org.politisationpopulairefrancaise.core.voir.parrainages.Sponsorship
import org.politisationpopulairefrancaise.core.voir.parrainages.SponsorshipsFormat
import org.politisationpopulairefrancaise.core.voir.parrainages.SponsorshipsPackage
import java.io.InputStream

/*
 * The presentations of the presidential election (VO-37; docs/integration/voir-parrainages.md): the file the
 * Conseil constitutionnel publishes twice a week on data.gouv.fr (`parrainagestotal.json`, every validated
 * presentation since the opening), turned into the package `parrainages-2027`. Only what the page needs is
 * kept: never the civility, and the mandate as a neutral code.
 */

/** Reads the JSON file of the Conseil constitutionnel: an array of objects `Civilite`, `Nom`, `Prenom`, `Mandat`, `Circonscription`, `Departement`, `Candidat`, `DatePublication`. */
object CcSponsorships {
    fun read(input: InputStream, example: Boolean): SponsorshipsPackage {
        val root = input.use { lenientJson.parseToJsonElement(it.readBytes().decodeToString()) }
        val rows = root.items()
        if (rows.isEmpty()) throw UnreadableSourceException("sponsorships: no presentation in the file")
        val sponsorships = rows.map(::row).sortedWith(SponsorshipsFormat.ORDER)
        return SponsorshipsPackage(
            example = example,
            election = SponsorshipsFormat.ELECTION_2027,
            publishedOn = sponsorships.maxOfOrNull { it.publishedOn },
            sponsorships = sponsorships,
        )
    }

    private fun row(item: JsonElement): Sponsorship {
        fun field(vararg keys: String): String? = keys.firstNotNullOfOrNull { item[it].text()?.trim()?.takeIf(String::isNotEmpty) }
        val lastName = field("Nom") ?: throw UnreadableSourceException("sponsorships: a presentation without name")
        val label = field("Mandat") ?: throw UnreadableSourceException("sponsorships: a presentation without mandate")
        val candidate = field("Candidat") ?: throw UnreadableSourceException("sponsorships: a presentation without candidate")
        val department = field("Departement", "Département") ?: throw UnreadableSourceException("sponsorships: a presentation without department")
        val mandate = SponsorMandate.of(label)
        return Sponsorship(
            lastName = lastName,
            firstName = field("Prenom", "Prénom").orEmpty(),
            mandate = mandate,
            otherMandate = label.takeIf { mandate == SponsorMandate.AUTRE },
            territory = field("Circonscription"),
            department = department,
            departmentCode = null,
            candidate = candidate,
            publishedOn = day(field("DatePublication", "Date de publication")) ?: throw UnreadableSourceException("sponsorships: a presentation without date"),
        )
    }

    /** `2022-02-01T00:00:00` (JSON) or `01/02/2022` (CSV) to `2022-02-01`. */
    internal fun day(text: String?): String? {
        text ?: return null
        Regex("^(\\d{4})-(\\d{2})-(\\d{2})").find(text)?.let { return it.value }
        Regex("^(\\d{2})/(\\d{2})/(\\d{4})$").find(text)?.destructured?.let { (d, m, y) -> return "$y-$m-$d" }
        return null
    }
}

/**
 * The package of one import, or null when the presentations are not asked this time (the previous package is
 * then kept by the importer). Refused when it holds 20 % fewer presentations than the previous one (a truncated file).
 */
class SponsorshipsImport(private val sources: ImportSources, private val log: (String) -> Unit) {
    fun bytes(previousCount: Int?): ByteArray? {
        val input = sources.sponsorships() ?: run {
            log("sponsorships: no file this time; the previous package is kept")
            return null
        }
        val pkg = CcSponsorships.read(input, sources.example)
        if (!ImportGuard.accepts(previousCount, pkg.sponsorships.size)) throw GuardException("sponsorships: ${pkg.sponsorships.size} presentation(s) instead of $previousCount")
        log("sponsorships: ${pkg.sponsorships.size} presentation(s), ${pkg.sponsorships.map { it.candidate }.distinct().size} candidate(s), published up to ${pkg.publishedOn}")
        return SponsorshipsFormat.write(pkg).encodeToByteArray()
    }

    companion object {
        /** The source of the index. */
        fun dataSource(today: String) = org.politisationpopulairefrancaise.core.voir.parlement.DataSource(
            SponsorshipsFormat.SOURCE_ID,
            "Conseil constitutionnel, parrainages de l'élection présidentielle",
            "https://www.data.gouv.fr/organizations/conseil-constitutionnel/",
            "Domaine public",
            today,
        )
    }
}
