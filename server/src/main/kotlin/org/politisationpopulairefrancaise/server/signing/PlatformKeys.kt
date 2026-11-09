package org.politisationpopulairefrancaise.server.signing

import org.politisationpopulairefrancaise.core.platform.PlatformTrust

/**
 * The working keys of the platform this server knows (D-129, step 6): certified by the root of this
 * environment for their uses, revocations included. The publication policy accepts a content signed by
 * such a key for a use whose form the content has, at the content's own date, and the legal register
 * leaves these contents out, like those of the editorial keys: they are the association's own contents,
 * not contents of third parties (D-077).
 *
 * [HttpPlatformSigner] is the source in production: it builds this trust from the certificates of the
 * signing service, before it hands over any signed event, so that the publication of what it returns
 * always finds the key.
 */
fun interface PlatformKeys {
    /** The trust known now. It only grows: a certificate or a revocation seen once is never forgotten. */
    fun trust(): PlatformTrust

    companion object {
        private val EMPTY: PlatformTrust = PlatformTrust.of(null)

        /** No signing service configured: no key of the platform is known, no content is taken for the platform's. */
        val NONE: PlatformKeys = PlatformKeys { EMPTY }
    }
}
