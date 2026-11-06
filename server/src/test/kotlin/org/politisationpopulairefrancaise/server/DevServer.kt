package org.politisationpopulairefrancaise.server

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.db.Database
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import java.nio.file.Files
import java.nio.file.Path

/**
 * Development server: a real PostgreSQL 17 started in-process (nothing to install), the bundled
 * example contents loaded, and their author allowed to publish. Run from the repository root with
 * `./gradlew :server:runDev`, then point the desktop app at it with PPF_SERVER_URL=http://localhost:8080.
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

    val editors = PublicationPolicy.parseKeys(System.getenv(PublicationPolicy.EDITOR_KEYS_VARIABLE)) + seeded.map { it.pubkey }
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    println("Development server: ${seeded.size} bundled content(s) loaded, ${editors.size} editor key(s), http://localhost:$port")
    embeddedServer(Netty, port = port, host = "127.0.0.1") {
        module(dataSource, PublicationPolicy(editors))
    }.start(wait = true)
}
