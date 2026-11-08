package org.politisationpopulairefrancaise.core.editorial

/** A licence under which PPF publishes something: its SPDX identifier, its names and the address of its official text. */
data class ContentLicense(val spdx: String, val name: String, val shortName: String, val url: String)

/**
 * The licences of what PPF publishes (decision of the owner of 8 November 2026, « Ok licence option b », D-103;
 * `LICENSE-CONTENUS.md`): Creative Commons Attribution 4.0 for the texts and images of PPF, Licence Ouverte 2.0
 * (Etalab) for the datasets. The SPDX identifiers were checked on the SPDX License List 3.29.0.
 *
 * The licence is chosen by the type of a content signed by the team ([of]): a type whose text belongs to someone
 * else (a right of reply, a candidate's answer, a citizen's demand, a testimony) has none, its rights staying with
 * its author. Quotations and titles of press articles stay with their authors whatever the type. Each pole checks
 * the types of its space here; a new type gets the licence of the texts of PPF unless it is listed in [AUTHORS_OWN].
 */
object ContentLicenses {
    /** The name to credit (CC BY: credit, link to the licence, say whether changes were made). */
    const val ATTRIBUTION: String = "Politisation Populaire Française"

    val TEXTS: ContentLicense = ContentLicense(
        spdx = "CC-BY-4.0",
        name = "Creative Commons Attribution 4.0 International",
        shortName = "CC BY 4.0",
        url = "https://creativecommons.org/licenses/by/4.0/deed.fr",
    )

    val DATA: ContentLicense = ContentLicense(
        spdx = "etalab-2.0",
        name = "Licence Ouverte / Open Licence 2.0 (Etalab)",
        shortName = "Licence Ouverte 2.0",
        url = "https://www.data.gouv.fr/pages/legal/licences/etalab-2.0",
    )

    /** The legal code of CC BY 4.0, in French. */
    const val TEXTS_LEGAL_CODE: String = "https://creativecommons.org/licenses/by/4.0/legalcode.fr"

    /** The types whose text is its author's, not PPF's, even when the team publishes it. */
    val AUTHORS_OWN: Set<ArticleType> = setOf(ArticleType.REPONSE, ArticleType.ENGAGEMENT, ArticleType.REVENDICATION, ArticleType.TEMOIGNAGE)

    /** The licence of a content of [type] signed by the team, null when its rights stay with its author. */
    fun of(type: ArticleType): ContentLicense? = if (type in AUTHORS_OWN) null else TEXTS
}
