package org.politisationpopulairefrancaise.core.contact

import fr.acinq.bitcoin.Crypto
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.PublicKey

/**
 * The inbox of the team for the cases (docs/integration/contact-reclamations.md, section 9): one dedicated
 * key, distinct from the keys that sign contents, whose secret half stays outside the server, shared by the
 * members who handle the cases, with a paper backup at the owner's. Its public key is built into the app for
 * each environment (like the import keys of the open data), never received from the server: a compromised
 * server cannot replace it. The server gets it in `PPF_CONTACT_INBOX_KEY`.
 *
 * [PREPRODUCTION] and [PRODUCTION] are empty until the key is created (before the public preproduction); an
 * empty value means "not created yet" and is never used. [example] is for development and tests only: its
 * secret key is derived from a sentence written in this file, so **anybody can read what is sent to it**.
 */
object ContactInbox {
    const val PREPRODUCTION: String = ""
    const val PRODUCTION: String = ""

    /** A public sentence: the example inbox protects nothing, on purpose. */
    const val EXAMPLE_SEED: String = "PPF [EXEMPLE] boite de l'equipe pour les dossiers de contact : publique, ne protege rien"

    /** The example key pair; only the development server, the development tools and the tests use its secret half. */
    fun exampleKeys(): IdentityKeys = IdentityKeys.fromSecretKey(Crypto.sha256(EXAMPLE_SEED.encodeToByteArray()))

    /** The public key of the example inbox. */
    val EXAMPLE: PublicKey by lazy {
        val keys = exampleKeys()
        try {
            keys.publicKey
        } finally {
            keys.destroy()
        }
    }

    /** The inbox of the preproduction or of the production, when it exists; null until it is created. */
    fun preproduction(): PublicKey? = PREPRODUCTION.takeIf { it.isNotEmpty() }?.let(PublicKey::fromHex)

    fun production(): PublicKey? = PRODUCTION.takeIf { it.isNotEmpty() }?.let(PublicKey::fromHex)
}
