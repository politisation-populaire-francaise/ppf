package org.politisationpopulairefrancaise.core.editorial

/**
 * Which authors' editorial contents the application shows as PPF contents. Phase 1: a list fixed
 * in the application; later a list signed by the platform key and distributed as an event.
 * Keys are lowercase hex public keys.
 */
class EditorialTrust(private val trustedAuthors: Set<String>) {

    fun isTrusted(pubkeyHex: String): Boolean = pubkeyHex in trustedAuthors

    fun plus(pubkeyHex: String): EditorialTrust = EditorialTrust(trustedAuthors + pubkeyHex)

    companion object {
        /**
         * Author of the [EXEMPLE] contents bundled with the application (content/). Its secret key is
         * kept outside the repository (content/README.md). To remove before the public launch.
         */
        const val EXAMPLE_EDITOR: String = "23484985405d16929a93f9aea29135678ad0ced909bf5a264f7b923d619b012f"

        fun bundledExamples(): EditorialTrust = EditorialTrust(setOf(EXAMPLE_EDITOR))
    }
}
