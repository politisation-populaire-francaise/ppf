package org.politisationpopulairefrancaise.core.agir.organisations

import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent

/** Signed directory entries for the tests: an editorial key the trust holds, and a stranger. */
class OrganisationFixtures {
    val editorKeys: IdentityKeys = IdentityKeys.generate()
    val editor = LocalEventSigner(editorKeys)
    val strangerKeys: IdentityKeys = IdentityKeys.generate()
    val stranger = LocalEventSigner(strangerKeys)
    val trust: EditorialTrust = EditorialTrust(setOf(editorKeys.publicKey.hex))

    suspend fun entry(
        slug: String,
        name: String = "[EXEMPLE] $slug",
        type: String = "association",
        territory: String = "France",
        accountKey: String? = null,
        summary: String = "[EXEMPLE] Une organisation fictive.",
        presentation: String = "Présentation fictive.",
        updatedAt: Long = 1_794_000_000,
        signer: LocalEventSigner = editor,
        websites: List<String> = emptyList(),
        phone: String? = null,
        email: String? = null,
    ): SignedEvent = signer.sign(
        Organisation.build(
            editor = signer.publicKey,
            slug = slug,
            name = name,
            summary = summary,
            type = type,
            territory = territory,
            verifiedOn = "2026-10-12",
            publishedAt = 1_793_000_000,
            updatedAt = updatedAt,
            presentation = presentation,
            accountKey = accountKey,
            websites = websites,
            phone = phone,
            email = email,
            origin = ContentOrigin.AI_ASSISTED,
        ),
    )

    fun read(event: SignedEvent): Organisation = Organisation.from(event, trust) ?: error("entry not accepted: ${event.tags}")
}
