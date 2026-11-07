package org.politisationpopulairefrancaise.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.revendications.checks.ProposalReport
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.server.agir.HeldQueue
import org.politisationpopulairefrancaise.server.agir.devHeldRoutes
import org.politisationpopulairefrancaise.server.agir.exampleMobilizations
import org.politisationpopulairefrancaise.server.agir.testimonies.TestimonyConfig
import org.politisationpopulairefrancaise.server.agir.testimonies.TestimonySetup
import org.politisationpopulairefrancaise.server.agir.testimonies.TestimonySpace
import org.politisationpopulairefrancaise.server.agir.testimonies.TestimonyStore
import org.politisationpopulairefrancaise.server.agir.testimonies.devTestimonyRoutes
import org.politisationpopulairefrancaise.server.api.ApiError
import org.politisationpopulairefrancaise.server.backoffice.BackOffice
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeSetup
import org.politisationpopulairefrancaise.server.backoffice.Outcome
import org.politisationpopulairefrancaise.server.backoffice.publicPort
import org.politisationpopulairefrancaise.server.db.Database
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.groups.GroupKinds
import org.politisationpopulairefrancaise.server.revendications.DecisionOutcome
import org.politisationpopulairefrancaise.server.revendications.ProposalQueue
import org.politisationpopulairefrancaise.server.revendications.RevendicationsSetup
import org.politisationpopulairefrancaise.server.revendications.column

/** Id of the example group of the development server; its messages are read with kind=9&tag=h:<id>. */
const val DEMO_GROUP_ID: String = "collectif-demonstration"

/**
 * Development server: a real PostgreSQL 17 started in-process (nothing to install), the bundled
 * example contents loaded (practical guides, demands of the wall, notes of the feed), their author
 * allowed to publish, and two example rooms described by keys generated at each start (the first one
 * also allowed as an editor for the session); any account may create more rooms, as in production.
 * Run from the repository root with
 * `./gradlew :server:runDev`, then point the desktop app at it with PPF_SERVER_URL=http://localhost:8080.
 *
 * Demands proposed by citizens wait for review, as in production. For local tests only, the review
 * of the team is replaced by three routes without authentication (the real back-office, with
 * two-factor authentication and an audit log, is to come):
 * - GET /dev/revendications/proposals: the pending proposals with their full reports;
 * - POST /dev/revendications/proposals/{id}/approve: publishes one;
 * - POST /dev/revendications/proposals/{id}/reject, with the reason as the request body.
 *
 * The Agir space likewise: agenda.json, appels.json and organisations.json are loaded when they exist,
 * two example mobilizations are added (one published, one waiting for review), and the review of the
 * held contents goes through GET /dev/held, POST /dev/held/{id}/approve and POST /dev/held/{id}/reject
 * (see devHeldRoutes).
 *
 * Testimonies are encrypted for the EXAMPLE moderation key and kept in a temporary folder (or
 * PPF_TESTIMONY_DIR); their review goes through /dev/testimonies (see devTestimonyRoutes), and their
 * refusal also through the queue agir.testimonies of the back-office.
 *
 * The team back-office opens on http://localhost:8081 when PPF_BACKOFFICE_ORIGIN=http://localhost:8081 is
 * set: the link of a first super administrator is printed at start (a passkey of the computer, Windows
 * Hello for example, works on localhost).
 *
 * The pages of "Voir" (http://localhost:8080/voir) show voir.json, signed by the example editor key.
 *
 * Never used in production: it listens on the local machine only and forgets everything on exit.
 */
fun main() {
    val postgres = EmbeddedPostgres.builder().start()
    Runtime.getRuntime().addShutdownHook(Thread { postgres.close() })
    val dataSource = postgres.postgresDatabase.also(Database::migrate)
    val repository = EventRepository(dataSource)

    val bundled = BundledContents.FILES.associateWith(BundledContents::load)
    val seeded = bundled.values.flatten()
    seeded.forEach(repository::store)

    // The development key exists only for this run; it is never written anywhere nor printed.
    val developmentKey = LocalEventSigner(IdentityKeys.generate())
    exampleGroup(developmentKey).forEach(repository::store)

    val editors = PublicationPolicy.parseKeys(System.getenv(PublicationPolicy.EDITOR_KEYS_VARIABLE)) +
        seeded.map { it.pubkey } + developmentKey.publicKey.hex
    // The same settings as production: an AI model only if PPF_AI_BASE_URL is set (for example a local model).
    val revendications = RevendicationsSetup.fromEnvironment()
    val queue = ProposalQueue(dataSource, repository, revendications.clock)
    val held = HeldQueue(dataSource, repository)
    repository.store(exampleMobilizations(developmentKey, held))
    // Testimonies: example keys (their secret halves are public), files in a temporary folder unless PPF_TESTIMONY_DIR is set.
    val testimonyConfig = TestimonyConfig.fromEnvironment().let { config ->
        config.copy(directory = config.directory ?: java.nio.file.Files.createTempDirectory("ppf-dev-testimonies"))
    }
    val testimonySetup = TestimonySetup(testimonyConfig)
    val testimonies = TestimonySpace(
        TestimonyStore(dataSource, testimonyConfig.directory!!), testimonyConfig, repository, PublicationPolicy(editors), testimonySetup.clock, testimonySetup.timestamping,
    )
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    println(
        "Development server: ${seeded.size} bundled content(s) loaded, example rooms \"$DEMO_GROUP_ID\" and \"atelier-logement-lyon\" loaded, " +
            "${editors.size} editor key(s), AI model ${if (revendications.model == null) "off" else "on"}, http://localhost:$port",
    )
    println("Agir space: ${BundledContents.AGIR_FILES.joinToString { "$it ${bundled[it].orEmpty().size}" }}, review of held contents at /dev/held.")
    println("Testimonies: encrypted for the example keys, review at /dev/testimonies.")
    println("Voir: ${bundled[BundledContents.VOIR_FILE].orEmpty().size} block(s) of ${BundledContents.VOIR_FILE}, pages at http://localhost:$port/voir")
    println("Comprendre: ${(listOf("fiches.json") + BundledContents.COMPRENDRE_FILES).joinToString { "$it ${bundled[it].orEmpty().size}" }}, pages at http://localhost:$port/comprendre")
    // The fictitious example set (PPF_RNE_SOURCE is never `official` outside production), imported at start.
    println("Directory of elected officials: example set, imported in the background, at /api/v1/elus and /api/v1/elus/69.")
    println("Territories: example set (real public figures, a few departments), at /api/v1/territoires and /api/v1/territoires/69.")
    val backOffice = BackOfficeSetup.fromEnvironment("development", port)
    backOffice?.let { setup ->
        (BackOffice(setup.config, dataSource).bootstrapInvitation("Propriétaire") as? Outcome.Ok)?.let { issued ->
            println("Back-office: ${setup.config.origin}, first super administrator: ${setup.config.origin}/invitation/${issued.value.token}")
        }
    }
    embeddedServer(
        Netty,
        configure = {
            connector {
                this.port = port
                host = "127.0.0.1"
            }
            backOffice?.let { setup ->
                connector {
                    this.port = setup.config.port
                    host = "127.0.0.1"
                }
            }
        },
    ) {
        module(dataSource, PublicationPolicy(editors), revendications = revendications, testimonies = testimonySetup, backOffice = backOffice)
        routing {
            // The stand-ins of the review answer on the public port only, never on the back-office port.
            publicPort(backOffice?.config?.port) {
                devReviewRoutes(queue)
                devHeldRoutes(held)
                parlementRoutes(devOpenData()) // Example packages of parliamentary open data (DevOpenData.kt).
                devTestimonyRoutes(testimonies, developmentKey)
            }
        }
    }.start(wait = true)
}

/** A pending proposal as the development routes list it: everything the team will see. */
@Serializable
private data class DevProposal(
    val id: String,
    val address: String?,
    val title: String?,
    val summary: String?,
    val territory: String?,
    val themes: List<String>,
    val body: String,
    @SerialName("received_at") val receivedAt: Long,
    val report: ProposalReport?,
    val event: SignedEvent,
)

@Serializable
private data class DevDecision(val id: String, val outcome: String)

/** The development stand-in for the team's review (see [main]); 127.0.0.1 only. */
private fun io.ktor.server.routing.Route.devReviewRoutes(queue: ProposalQueue) {
    route("/dev/revendications/proposals") {
        get {
            val pending = withContext(Dispatchers.IO) { queue.pending() }.map { proposal ->
                val article = Article.from(proposal.event)
                DevProposal(
                    id = proposal.id,
                    address = article?.address?.toString(),
                    title = article?.title,
                    summary = article?.summary,
                    territory = article?.territory,
                    themes = article?.topics.orEmpty(),
                    body = proposal.event.content,
                    receivedAt = proposal.receivedAt,
                    report = proposal.report,
                    event = proposal.event,
                )
            }
            call.respond(pending)
        }
        post("{id}/approve") {
            val id = call.parameters["id"].orEmpty()
            call.answer(id, withContext(Dispatchers.IO) { queue.approve(id) })
        }
        post("{id}/reject") {
            val id = call.parameters["id"].orEmpty()
            val reason = call.receiveText().trim()
            if (reason.isEmpty() || reason.length > ProposalQueue.MAX_REASON_LENGTH) {
                return@post call.respond(HttpStatusCode.BadRequest, ApiError("bad_reason", "Give the reason (1 to ${ProposalQueue.MAX_REASON_LENGTH} characters) as the request body."))
            }
            call.answer(id, withContext(Dispatchers.IO) { queue.reject(id, reason) })
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.answer(id: String, outcome: DecisionOutcome) = when (outcome) {
    is DecisionOutcome.Approved -> respond(DevDecision(id, "approved (${outcome.stored.name.lowercase()})"))
    DecisionOutcome.Rejected -> respond(DevDecision(id, "rejected"))
    DecisionOutcome.NotFound -> respond(HttpStatusCode.NotFound, ApiError("not_found", "No proposal with this id."))
    is DecisionOutcome.AlreadyDecided -> respond(HttpStatusCode.Conflict, ApiError("already_decided", "This proposal is already ${outcome.status.column}."))
}

/**
 * Two example rooms (public, open, on a territory, charter accepted, as any account may create them)
 * and two example messages in the first one, all marked [EXEMPLE]. The second room is created by
 * another throwaway key, so that searching by territory and the refusal of a duplicate can be tried.
 */
private fun exampleGroup(developmentKey: LocalEventSigner): List<SignedEvent> = runBlocking {
    val now = System.currentTimeMillis() / 1000
    val description = developmentKey.sign(
        kind = GroupKinds.METADATA,
        tags = listOf(
            listOf("d", DEMO_GROUP_ID),
            listOf(GroupKinds.NAME_TAG, "[EXEMPLE] Collectif de démonstration"),
            listOf(GroupKinds.ABOUT_TAG, "[EXEMPLE] Salon public et ouvert du serveur de développement : tout compte peut y écrire. Les messages disparaissent à l'arrêt du serveur."),
            listOf(GroupKinds.TERRITORY_TAG, "France"),
            listOf(GroupKinds.CHARTER_TAG, GroupKinds.CHARTER_VERSION),
        ),
        createdAt = now - 3_600,
    )
    val member = LocalEventSigner(IdentityKeys.generate())
    val secondRoom = member.sign(
        kind = GroupKinds.METADATA,
        tags = listOf(
            listOf("d", "atelier-logement-lyon"),
            listOf(GroupKinds.NAME_TAG, "[EXEMPLE] Atelier logement"),
            listOf(GroupKinds.ABOUT_TAG, "[EXEMPLE] Salon de démonstration rattaché à un territoire : on y prépare une permanence sur les loyers."),
            listOf(GroupKinds.TERRITORY_TAG, "Lyon"),
            listOf(GroupKinds.CHARTER_TAG, GroupKinds.CHARTER_VERSION),
        ),
        createdAt = now - 3_000,
    )
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
    listOf(description, secondRoom, welcome, reply)
}
