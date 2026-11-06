package org.politisationpopulairefrancaise.server

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.server.db.Database
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.groups.GroupKinds
import java.nio.file.Files
import java.nio.file.Path

/** Id of the example group of the development server; its messages are read with kind=9&tag=h:<id>. */
const val DEMO_GROUP_ID: String = "collectif-demonstration"

/**
 * Development server: a real PostgreSQL 17 started in-process (nothing to install), the bundled
 * example contents loaded, their author allowed to publish, and an example group described by a
 * development key generated at each start (and allowed as an editor for the session). Run from the
 * repository root with `./gradlew :server:runDev`, then point the desktop app at it with
 * PPF_SERVER_URL=http://localhost:8080.
 * Never used in production: it listens on the local machine only and forgets everything on exit.
 */
fun main() {
    val postgres = EmbeddedPostgres.builder().start()
    Runtime.getRuntime().addShutdownHook(Thread { postgres.close() })
    val dataSource = postgres.postgresDatabase.also(Database::migrate)
    val repository = EventRepository(dataSource)

    val bundled = Path.of("shared/ui/src/commonMain/composeResources/files/contents/fiches.json")
    val seeded = if (Files.exists(bundled)) {
        Json.decodeFromString(ListSerializer(SignedEvent.serializer()), Files.readString(bundled)).filter { it.verify() }
    } else {
        emptyList()
    }
    seeded.forEach(repository::store)

    // The development key exists only for this run; it is never written anywhere nor printed.
    val developmentKey = LocalEventSigner(IdentityKeys.generate())
    exampleGroup(developmentKey).forEach(repository::store)

    val editors = PublicationPolicy.parseKeys(System.getenv(PublicationPolicy.EDITOR_KEYS_VARIABLE)) +
        seeded.map { it.pubkey } + developmentKey.publicKey.hex
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    println(
        "Development server: ${seeded.size} bundled content(s) loaded, example group \"$DEMO_GROUP_ID\" loaded, " +
            "${editors.size} editor key(s), http://localhost:$port",
    )
    embeddedServer(Netty, port = port, host = "127.0.0.1") {
        module(dataSource, PublicationPolicy(editors))
    }.start(wait = true)
}

/** The example group (public, open) and two example messages, all marked [EXEMPLE]. */
private fun exampleGroup(developmentKey: LocalEventSigner): List<SignedEvent> = runBlocking {
    val now = System.currentTimeMillis() / 1000
    val description = developmentKey.sign(
        kind = GroupKinds.METADATA,
        tags = listOf(
            listOf("d", DEMO_GROUP_ID),
            listOf("name", "[EXEMPLE] Collectif de démonstration"),
            listOf("about", "[EXEMPLE] Salon public et ouvert du serveur de développement : tout compte peut y écrire. Les messages disparaissent à l'arrêt du serveur."),
        ),
        createdAt = now - 3_600,
    )
    val member = LocalEventSigner(IdentityKeys.generate())
    val welcome = developmentKey.sign(
        kind = GroupKinds.CHAT_MESSAGE,
        content = "[EXEMPLE] Bienvenue dans le salon de démonstration. Ici, on prépare la réunion publique de samedi.",
        tags = listOf(listOf("h", DEMO_GROUP_ID)),
        createdAt = now - 1_800,
    )
    val reply = member.sign(
        kind = GroupKinds.CHAT_MESSAGE,
        content = "[EXEMPLE] Merci ! Je m'occupe d'imprimer les fiches pratiques pour les nouveaux venus.",
        tags = listOf(listOf("h", DEMO_GROUP_ID)),
        createdAt = now - 900,
    )
    listOf(description, welcome, reply)
}
