package org.politisationpopulairefrancaise.core.voir.parlement

/**
 * The import keys the application trusts for the packages of parliamentary open data (D-064),
 * one per environment, all distinct from the platform and editorial keys:
 * - [EXAMPLE] signs the fictional packages of the repository and its tests (private key outside
 *   the repository, `~/.ppf/open-data-example.nsec`);
 * - [PREPRODUCTION] signs the real open data imported in preproduction (decision of the owner of
 *   7 November 2026: real information in preproduction); generated in the importer's container
 *   with `ppf-open-data-importer keygen`, its public key is written here;
 * - [PRODUCTION] signs the real data of the public service, set before the public launch.
 * An empty value means "not generated yet" and is never trusted.
 */
object OpenDataKeys {
    const val EXAMPLE: String = "ab66ff6d2e9ecc5bbc298a02e96f5c79d8e92306e128714546aef285511b44b0"
    const val PREPRODUCTION: String = ""
    const val PRODUCTION: String = ""

    fun bundledExamples(): OpenDataTrust = OpenDataTrust.of(EXAMPLE)

    /** Test builds (debug, preview, desktop): the preproduction and the examples. */
    fun testBuilds(): OpenDataTrust = OpenDataTrust.of(*listOf(PREPRODUCTION, EXAMPLE).filter { it.isNotEmpty() }.toTypedArray())

    /** The published build: the production key only. */
    fun release(): OpenDataTrust = OpenDataTrust.of(*listOf(PRODUCTION).filter { it.isNotEmpty() }.toTypedArray())
}
