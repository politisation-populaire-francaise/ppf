package org.politisationpopulairefrancaise.core.agir.agenda

import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent

/** Fixed instants of the tests (all [EXEMPLE] data), in seconds since 1970. */
internal object AgendaTimes {
    /** Friday 13 November 2026, 10 h in Paris (09:00 UTC): "now" of most tests. */
    const val NOW: Long = 1_794_560_400L

    /** Saturday 14 November 2026, 14 h in Paris (13:00 UTC). */
    const val SATURDAY_14H: Long = 1_794_661_200L

    /** Saturday 14 November 2026, 17 h in Paris (16:00 UTC). */
    const val SATURDAY_17H: Long = 1_794_672_000L

    /** Thursday 5 November 2026, 13 h in Paris (12:00 UTC): when the example versions were written. */
    const val WRITTEN: Long = 1_793_880_000L

    const val HOUR: Long = 3_600
    const val DAY: Long = 86_400
}

/** Signs mobilizations for the tests, with one key per instance. */
internal class AgendaFixtures(val keys: IdentityKeys = IdentityKeys.generate()) {
    val signer = LocalEventSigner(keys)
    val pubkey: String get() = keys.publicKey.hex

    suspend fun sign(event: UnsignedEvent): SignedEvent = signer.sign(event)

    suspend fun mobilization(
        identifier: String = "rassemblement-test",
        title: String = "[EXEMPLE] Rassemblement pour les services publics",
        start: Long = AgendaTimes.SATURDAY_14H,
        end: Long? = AgendaTimes.SATURDAY_17H,
        location: String = "Place de la Mairie, Villefictive",
        type: MobilizationType = MobilizationType.RASSEMBLEMENT,
        territory: String = "Villefictive",
        status: MobilizationStatus = MobilizationStatus.PREVU,
        organizers: List<String> = emptyList(),
        links: List<String> = emptyList(),
        summary: String? = null,
        description: String = "[EXEMPLE] Description de démonstration.",
        createdAt: Long = AgendaTimes.WRITTEN,
        declaration: DeclarationState? = null,
    ): Mobilization {
        val event = sign(
            Mobilization.build(
                author = keys.publicKey,
                identifier = identifier,
                createdAt = createdAt,
                title = title,
                start = start,
                end = end,
                location = location,
                type = type,
                territory = territory,
                status = status,
                organizers = organizers,
                links = links,
                summary = summary,
                description = description,
                declaration = declaration,
            ),
        )
        return checkNotNull(Mobilization.from(event))
    }

    /** An event of kind 31923 with exactly [tags] and [content], for the rules. */
    suspend fun raw(tags: List<List<String>>, content: String = ""): SignedEvent =
        sign(UnsignedEvent(pubkey, AgendaTimes.WRITTEN, Mobilization.KIND, tags, content))
}

/** The tags of a valid mobilization, to break one at a time. */
internal fun validTags(): List<List<String>> = listOf(
    listOf("d", "assemblee-test"),
    listOf("title", "[EXEMPLE] Assemblée générale"),
    listOf("start", AgendaTimes.SATURDAY_14H.toString()),
    listOf("end", AgendaTimes.SATURDAY_17H.toString()),
    listOf("start_tzid", "Europe/Paris"),
    listOf("location", "Salle des fêtes, Villefictive"),
    listOf("ppf-event-type", "assemblee-generale"),
    listOf("ppf-territory", "Villefictive"),
    listOf("ppf-charter", "1.0"),
)

internal fun List<List<String>>.without(name: String) = filterNot { it[0] == name }

internal fun List<List<String>>.with(name: String, value: String) = without(name) + listOf(listOf(name, value))
