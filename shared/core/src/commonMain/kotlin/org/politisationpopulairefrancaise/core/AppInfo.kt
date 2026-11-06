package org.politisationpopulairefrancaise.core

/** Identity and version of the platform, shared by every application and the server. */
object AppInfo {
    const val NAME: String = "Politisation Populaire Française"
    const val SHORT_NAME: String = "PPF"

    /** Semantic version of the whole platform. Bumped in docs/CHANGELOG.md. */
    const val VERSION: String = "0.1.0"

    /** Main public domain. Other extensions redirect to it. */
    const val MAIN_DOMAIN: String = "politisation-populaire-francaise.org"
}
