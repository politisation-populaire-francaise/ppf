package org.politisationpopulairefrancaise.server.agir.collectives

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.agir.collectives.CollectiveFormat
import org.politisationpopulairefrancaise.core.agir.collectives.CollectiveScope
import org.politisationpopulairefrancaise.core.agir.collectives.CollectiveType
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign

/**
 * Two example collectifs of the development server (M24, step 1), all marked [EXEMPLE], founded by
 * throwaway keys drawn at each start (never written anywhere), and one example mobilization in the
 * name of the first one, so that the directory, a page and its calendar can be tried. Stored
 * directly, like the example rooms: founding a real one needs an account validated in person.
 */
fun exampleCollectives(): List<SignedEvent> = runBlocking {
    val now = System.currentTimeMillis() / 1000
    val first = LocalEventSigner(IdentityKeys.generate())
    val second = LocalEventSigner(IdentityKeys.generate())
    val cyclists = first.sign(
        CollectiveFormat.build(
            author = first.publicKey,
            id = "exemple-collectif-cyclistes-lyon",
            name = "[EXEMPLE] Collectif des cyclistes",
            about = "[EXEMPLE] Collectif de démonstration du serveur de développement : des pistes cyclables sûres et des ateliers de réparation.",
            territory = "Lyon (69)",
            createdAt = now - 7_200,
            scope = CollectiveScope.LOCAL,
            type = CollectiveType.COLLECTIF,
            themes = listOf("vélo", "transports"),
            territoryCode = "com:69123",
            internalCharter = "[EXEMPLE] Nous décidons en assemblée, une fois par mois. Chacun parle à son tour.",
        ),
    )
    val school = second.sign(
        CollectiveFormat.build(
            author = second.publicKey,
            id = "exemple-comite-parents-paris",
            name = "[EXEMPLE] Comité des parents d'élèves",
            about = "[EXEMPLE] Comité de démonstration rattaché à un établissement : cantine, sécurité aux abords de l'école.",
            territory = "Paris",
            createdAt = now - 6_000,
            scope = CollectiveScope.ETABLISSEMENT,
            type = CollectiveType.COMITE,
            themes = listOf("éducation"),
            territoryCode = "com:75056",
        ),
    )
    val ride = first.sign(
        kind = AgirFormats.KIND_MOBILIZATION,
        content = "[EXEMPLE] Balade à vélo du collectif de démonstration : départ groupé, retour vers 16 h.",
        tags = listOf(
            listOf("d", "exemple-balade-cyclistes"),
            listOf(AgirFormats.TAG_TITLE, "[EXEMPLE] Balade à vélo du collectif"),
            listOf(AgirFormats.TAG_START, (now + 4 * 86_400).toString()),
            listOf(AgirFormats.TAG_START_TZID, AgirFormats.TIME_ZONE),
            listOf(AgirFormats.TAG_LOCATION, "[EXEMPLE] Place de la République"),
            listOf(AgirFormats.TAG_EVENT_TYPE, "rassemblement"),
            listOf(AgirFormats.TAG_TERRITORY, "Lyon"),
            listOf(AgirFormats.TAG_CHARTER, AgirFormats.CHARTER_VERSION),
            listOf(CollectiveFormat.TAG_COLLECTIVE, "exemple-collectif-cyclistes-lyon"),
        ),
        createdAt = now - 3_000,
    )
    listOf(cyclists, school, ride)
}
