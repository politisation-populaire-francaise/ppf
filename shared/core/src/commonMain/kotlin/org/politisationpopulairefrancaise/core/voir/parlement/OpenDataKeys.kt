package org.politisationpopulairefrancaise.core.voir.parlement

/**
 * The import keys the application trusts for the packages of parliamentary open data (D-064).
 * The example key signs the fictional packages of development, tests and preproduction; its
 * private key lives outside the repository (`~/.ppf/open-data-example.nsec`). The production key
 * is added here before the public launch; it is distinct from the platform and editorial keys.
 */
object OpenDataKeys {
    const val EXAMPLE: String = "ab66ff6d2e9ecc5bbc298a02e96f5c79d8e92306e128714546aef285511b44b0"

    fun bundledExamples(): OpenDataTrust = OpenDataTrust.of(EXAMPLE)
}
