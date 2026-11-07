package org.politisationpopulairefrancaise.ui.agir

import org.politisationpopulairefrancaise.core.agir.organisations.Organisation
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.publication.TagFilter
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.ui.AppDependencies

/**
 * What the tests of "Agir" share: a second editorial key (the bundled [EXEMPLE] entries are signed
 * by the example editor), a stranger, and the eight example organisations bundled with the
 * application (content/organisations).
 */
object AgirFixtures {
    val editor = LocalEventSigner(IdentityKeys.generate())
    val stranger = LocalEventSigner(IdentityKeys.generate())
    val trust: EditorialTrust = EditorialTrust.bundledExamples().plus(editor.publicKey.hex)

    /** The slugs of the bundled examples, by name. */
    val EXAMPLES = listOf(
        "ateliers-lanterne", "bibliotheque-imaginaire", "collectif-tilleuls", "legal-team-nord",
        "reseau-avocats-benevoles", "street-medics-marseille", "syndicat-livreurs-velo", "union-locale-lille",
    )

    /** The bundled example with an account: "Association fictive Les Ateliers de la Lanterne". */
    const val LANTERNE = "ateliers-lanterne"
    const val LANTERNE_ACCOUNT = "acfe2b6e1d2fd778464c51b14b0ab41ccc3fe5336728b65582f05245bd5efe97"
    const val LANTERNE_SITE = "https://example.org/ateliers-lanterne"

    fun dependencies(remote: RemoteEvents? = null): AppDependencies =
        AppDependencies.withDatabase(InMemoryIdentityStore(), JvmDatabase.inMemory(), editorialTrust = trust, remote = remote)

    suspend fun entry(
        slug: String,
        name: String = "[EXEMPLE] Organisation $slug",
        type: String = "association",
        territory: String = "France",
        accountKey: String? = null,
        signer: LocalEventSigner = editor,
        updatedAt: Long = 1_793_500_000,
        status: String = "verifiee",
    ): SignedEvent = signer.sign(
        Organisation.build(
            editor = signer.publicKey,
            slug = slug,
            name = name,
            summary = "[EXEMPLE] Une organisation fictive des tests.",
            type = type,
            territory = territory,
            verifiedOn = "2026-11-01",
            publishedAt = 1_793_400_000,
            updatedAt = updatedAt,
            presentation = "Présentation fictive.",
            accountKey = accountKey,
            origin = ContentOrigin.HUMAN,
            status = status,
        ),
    )
}

/** A server that serves directory entries by tag and records every request. */
class DirectoryServer : RemoteEvents {
    val tagRequests = mutableListOf<Pair<List<TagFilter>, RemoteFilter>>()
    val fetches = mutableListOf<RemoteFilter>()
    val toServe = mutableListOf<SignedEvent>()
    var reachable = true

    override suspend fun publish(event: SignedEvent): PublicationResult = PublicationResult.Accepted(event.id)

    override suspend fun fetch(filter: RemoteFilter): FetchResult {
        fetches += filter
        return if (reachable) FetchResult.Events(emptyList()) else FetchResult.Unavailable()
    }

    override suspend fun fetchByTags(tags: List<TagFilter>, filter: RemoteFilter): FetchResult {
        tagRequests += tags to filter
        if (!reachable) return FetchResult.Unavailable()
        val wanted = tags.associate { it.name to it.value }
        return FetchResult.Events(
            toServe.filter { event ->
                event.kind in filter.kinds && wanted.all { (name, value) -> event.tagValues(name).contains(value) } &&
                    (filter.since == null || event.createdAt >= filter.since!!) && (filter.until == null || event.createdAt <= filter.until!!)
            },
        )
    }
}
