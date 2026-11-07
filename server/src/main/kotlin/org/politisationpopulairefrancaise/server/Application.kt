package org.politisationpopulairefrancaise.server

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.CallFailed
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import org.politisationpopulairefrancaise.core.AppInfo
import org.politisationpopulairefrancaise.core.HealthStatus
import org.politisationpopulairefrancaise.server.agir.AgirConfig
import org.politisationpopulairefrancaise.server.agir.AgirSetup
import org.politisationpopulairefrancaise.server.agir.AgirSpace
import org.politisationpopulairefrancaise.server.agir.agendaSource
import org.politisationpopulairefrancaise.server.agir.agirApi
import org.politisationpopulairefrancaise.server.agir.testimonies.TestimonyQueue
import org.politisationpopulairefrancaise.server.agir.testimonies.TestimonySetup
import org.politisationpopulairefrancaise.server.agir.testimonies.TestimonySpace
import org.politisationpopulairefrancaise.server.agir.testimonies.testimonyApi
import org.politisationpopulairefrancaise.server.api.ApiError
import org.politisationpopulairefrancaise.server.api.PostLimits
import org.politisationpopulairefrancaise.server.api.eventsApi
import org.politisationpopulairefrancaise.server.api.groupsApi
import org.politisationpopulairefrancaise.server.archive.ArchiveService
import org.politisationpopulairefrancaise.server.archive.ArchiveSetup
import org.politisationpopulairefrancaise.server.archive.archivesApi
import org.politisationpopulairefrancaise.server.backoffice.BackOffice
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeConfig
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeSetup
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeSpace
import org.politisationpopulairefrancaise.server.backoffice.PortSelector
import org.politisationpopulairefrancaise.server.backoffice.backOfficeHeaders
import org.politisationpopulairefrancaise.server.backoffice.backOfficeRoutes
import org.politisationpopulairefrancaise.server.backoffice.publicPort
import org.politisationpopulairefrancaise.server.db.Database
import org.politisationpopulairefrancaise.server.db.DatabaseConfig
import org.politisationpopulairefrancaise.server.elus.ElusConfig
import org.politisationpopulairefrancaise.server.elus.ElusService
import org.politisationpopulairefrancaise.server.elus.ElusSetup
import org.politisationpopulairefrancaise.server.elus.elusApi
import org.politisationpopulairefrancaise.server.territoires.TerritoiresService
import org.politisationpopulairefrancaise.server.territoires.TerritoiresSetup
import org.politisationpopulairefrancaise.server.territoires.territoiresApi
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.groups.GroupDirectory
import org.politisationpopulairefrancaise.server.groups.GroupIndex
import org.politisationpopulairefrancaise.server.relay.RelayConfig
import org.politisationpopulairefrancaise.server.relay.WrapRetention
import org.politisationpopulairefrancaise.server.revendications.AiReviewConfig
import org.politisationpopulairefrancaise.server.revendications.RevendicationProposals
import org.politisationpopulairefrancaise.server.revendications.RevendicationsConfig
import org.politisationpopulairefrancaise.server.revendications.RevendicationsSetup
import org.politisationpopulairefrancaise.server.revendications.revendicationsApi
import org.politisationpopulairefrancaise.server.voir.EditorialVoirSource
import org.politisationpopulairefrancaise.server.comprendre.EditorialComprendreSource
import org.politisationpopulairefrancaise.server.tiles.TilesConfig
import org.politisationpopulairefrancaise.server.tiles.tilesRoutes
import org.politisationpopulairefrancaise.server.seed.ExampleSeed
import org.politisationpopulairefrancaise.server.agir.VerifiedOrganisations
import org.politisationpopulairefrancaise.server.revendications.ProposalsBackOfficeQueue
import org.politisationpopulairefrancaise.server.urne.PersonAccounts
import org.politisationpopulairefrancaise.server.urne.RevendicationUrnes
import org.politisationpopulairefrancaise.server.urne.UrneConfig
import org.politisationpopulairefrancaise.server.urne.UrneService
import org.politisationpopulairefrancaise.server.urne.UrneStore
import org.politisationpopulairefrancaise.server.urne.urneApi
import org.politisationpopulairefrancaise.web.agendaPage
import org.politisationpopulairefrancaise.web.comprendre.ComprendreSource
import org.politisationpopulairefrancaise.web.voir.VoirSource
import org.politisationpopulairefrancaise.web.webRoutes
import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * Runtime configuration, read from environment variables only (never from the repository).
 *
 * - PORT: TCP port to listen on (default 8080).
 * - PPF_ENVIRONMENT: "production", "preproduction" or "development" (default).
 * - DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD or DATABASE_PASSWORD_FILE: see [DatabaseConfig].
 * - PPF_DATABASE_MIGRATE (default true only in development; false makes the server refuse to start while a
 *   migration is pending, see DatabaseCli) and PPF_DATABASE_APP_ROLE: see [Database].
 * - PPF_EDITOR_KEYS: keys of the editorial team, see [PublicationPolicy].
 * - PPF_WRAP_RETENTION_DAYS, PPF_WRAP_RETENTION_MAX_DAYS, PPF_WRAP_CLEANUP_INTERVAL_MINUTES,
 *   PPF_WRAP_MAX_PER_RECIPIENT, PPF_POST_RATE_PER_MINUTE, PPF_POST_GLOBAL_RATE_PER_MINUTE: relay
 *   settings, see [RelayConfig].
 * - PPF_PROPOSALS_MAX_PENDING_PER_KEY, PPF_PROPOSALS_PER_KEY_PER_DAY, PPF_PROPOSALS_MAX_PENDING,
 *   PPF_PROPOSALS_RETENTION_DAYS, PPF_PROPOSALS_CLEANUP_INTERVAL_MINUTES, PPF_AI_MAX_REVIEWS_PER_DAY:
 *   the review queue of citizen demands, see [RevendicationsConfig].
 * - PPF_AI_BASE_URL, PPF_AI_MODEL, PPF_AI_API_KEY_FILE, PPF_AI_JSON_MODE, PPF_AI_TIMEOUT_SECONDS: the
 *   AI model that reads proposed demands, off unless PPF_AI_BASE_URL is set, see [AiReviewConfig].
 * - PPF_HELD_RETENTION_DAYS, PPF_HELD_CLEANUP_INTERVAL_MINUTES: the review queue of the Agir space,
 *   see [AgirConfig].
 * - PPF_TILES_DIR: the folder of the PMTiles files of the map backgrounds, served at `/tiles/` by
 *   range (development and preproduction; in production Caddy serves them), see [TilesConfig].
 * - PPF_RNE_SOURCE, PPF_RNE_REFRESH_DAYS: the directory of elected officials (fictitious example set
 *   by default), see [ElusConfig].
 * - PPF_ARCHIVE_*: the timestamped archive (on in production only by default), see [ArchiveConfig].
 * - PPF_TESTIMONY_DIR, PPF_MODERATION_KEY, PPF_EVIDENCE_KEY and the other PPF_TESTIMONY_* and
 *   PPF_EVIDENCE_RETENTION_DAYS settings: the testimonies of the Agir space, see [TestimonySetup].
 * - PPF_EXAMPLES_DIR: preproduction only, a directory of example contents (JSON arrays of signed
 *   events, all marked [EXEMPLE]) stored at startup when a database is configured; the server refuses
 *   to start when it is set in production, see [ExampleSeed].
 * - PPF_VOTE_SEEDS_DIR, PPF_VOTE_POW_REGISTER, PPF_VOTE_POW_TOKENS, PPF_VOTE_POW_BALLOT, PPF_PUBLIC_URL,
 *   PPF_VOTE_MAX_TOKENS_PER_REQUEST: the anonymous ballot boxes, see [UrneConfig] (D-110).
 * - PPF_BACKOFFICE_ORIGIN, PPF_BACKOFFICE_PORT, PPF_AUDIT_PUBLIC_KEY, PPF_BACKOFFICE_MAINTENANCE_INTERVAL_MINUTES:
 *   the team back-office, off unless PPF_BACKOFFICE_ORIGIN is set (for example
 *   `https://equipe.preprod.politisation-populaire-francaise.org`; `http://localhost:8081` in development
 *   only). Its routes answer only on PPF_BACKOFFICE_PORT (default 8081), never on PORT; see [BackOfficeConfig].
 */
object ServerConfig {
    val port: Int = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val environment: String = System.getenv("PPF_ENVIRONMENT") ?: "development"
}

fun main() {
    // Checked before anything else: in production, example contents stop the server from starting.
    val examples = ExampleSeed.fromEnvironment()
    // In production and preproduction the migrations ran before, in their own container, with the owner
    // role; the server, connected with a restricted role, only checks them (D-127).
    val migrate = Database.shouldMigrate(ServerConfig.environment)
    val appRole = Database.appRole()
    val dataSource = DatabaseConfig.fromEnvironment()?.let { config ->
        Database.connect(config).also { Database.prepare(it, migrate, appRole) }
    }
    if (examples != null) {
        if (dataSource == null) {
            LoggerFactory.getLogger(ExampleSeed::class.java)
                .warn("Example contents: ${ExampleSeed.DIRECTORY_VARIABLE} is set but no database is configured, no example content loaded.")
        } else {
            ExampleSeed.load(examples, EventRepository(dataSource))
        }
    }
    // Checked before the server starts: a back-office under a wrong or plain-http address is refused.
    val backOffice = BackOfficeSetup.fromEnvironment(ServerConfig.environment, ServerConfig.port)
    // Bind on every interface inside the container; the reverse proxy is the only
    // process allowed to reach it (see infra/). The back-office has its own internal port, which the
    // proxy forwards only for its own host name (contract docs/integration/back-office.md, section 3).
    embeddedServer(
        Netty,
        configure = {
            connector {
                port = ServerConfig.port
                host = "0.0.0.0"
            }
            backOffice?.let { setup ->
                connector {
                    port = setup.config.port
                    host = "0.0.0.0"
                }
            }
        },
    ) {
        module(dataSource, backOffice = backOffice)
    }.start(wait = true)
}

/**
 * Turns any unhandled exception into a generic 500 answer. Without it, the engine would log the
 * exception's message, which for a database error can quote the failing row, that is an event's
 * content and author. Only the exception class is logged.
 */
private val ErrorShield = createApplicationPlugin("ErrorShield") {
    on(CallFailed) { call, cause ->
        if (cause is CancellationException) throw cause
        call.application.log.error("Unhandled ${cause::class.qualifiedName} while answering a request.")
        if (!call.response.isCommitted) {
            try {
                call.respond(HttpStatusCode.InternalServerError, ApiError("internal_error", "The server could not handle the request."))
            } catch (e: Exception) {
                // The answer had already started; nothing more can be said to the client.
            }
        }
    }
}

/**
 * Wires every plugin and route. Kept separate from main() so that tests can
 * start the application in memory, with or without a database.
 *
 * Privacy rule: no request logging plugin is installed. Client addresses are never
 * written anywhere (cahier des charges, section 5.1).
 */
fun Application.module(
    dataSource: DataSource? = null,
    policy: PublicationPolicy = PublicationPolicy.fromEnvironment(),
    relay: RelayConfig = RelayConfig.fromEnvironment(),
    revendications: RevendicationsSetup = RevendicationsSetup.fromEnvironment(),
    agir: AgirSetup = AgirSetup.fromEnvironment(),
    /** Where the pages of "Voir" read; by default the editorial contents of the database (none without one). */
    voir: VoirSource? = null,
    /** Where the pages of "Comprendre" read (D-091); by default the editorial contents of the database (none without one). */
    comprendre: ComprendreSource? = null,
    tiles: TilesConfig = TilesConfig.fromEnvironment(),
    elus: ElusSetup = ElusSetup.fromEnvironment(),
    urne: UrneConfig = UrneConfig.fromEnvironment(),
    archive: ArchiveSetup = ArchiveSetup.fromEnvironment(),
    territoires: TerritoiresSetup = TerritoiresSetup.fromEnvironment(),
    testimonies: TestimonySetup = TestimonySetup.fromEnvironment(),
    backOffice: BackOfficeSetup? = BackOfficeSetup.fromEnvironment(ServerConfig.environment, ServerConfig.port),
) {
    val repository = dataSource?.let(::EventRepository)
    // Timestamped archive (D-111): its batches, upgrades and checks live and die with the application.
    val archives = dataSource?.let { ArchiveService.start(this, it, archive) }
    // The directory of elected officials: the last import served at once, the next ones on schedule (D-047).
    val elusService = dataSource?.let { ElusService.start(this, it, elus, log) }
    // The referential of territories (D-090): the last import served at once, the next ones on schedule.
    val territoiresService = dataSource?.let { TerritoiresService.start(this, it, territoires, log) }
    // Citizen demands wait in a review queue; its checks and cleanup live and die with the application.
    val proposals = if (dataSource != null && repository != null) {
        RevendicationProposals.start(this, dataSource, repository, revendications)
    } else {
        revendications.close()
        null
    }
    // Mobilizations, calls and directory of the Agir space: rules, review queue, agenda (contract, section 5).
    val agirSpace = if (dataSource != null && repository != null) AgirSpace.start(this, dataSource, repository, policy, agir) else null
    // Testimonies: encrypted envelopes and files only, open when a folder is configured (docs/integration/agir-temoignages.md).
    val testimonySpace = if (dataSource != null && repository != null) TestimonySpace.start(this, dataSource, repository, policy, testimonies) else null
    if (repository == null) {
        log.info("No database configured: the events API answers 503 (health check and web pages work).")
    } else if (relay.cleanupInterval.isPositive()) {
        // Encrypted envelopes are kept for a limited time, set by each recipient within the server's
        // bounds; the task lives and dies with the application.
        WrapRetention(repository, relay).start(this, relay.cleanupInterval, log)
    }
    // The team back-office (M15): its own port, accounts by invitation, passkeys, journal; the poles
    // register their queues on teamBackOffice.queues (contract docs/integration/back-office.md, section 7).
    val teamBackOffice: BackOffice? = when {
        backOffice == null -> null
        dataSource == null -> null.also { log.warn("Back-office: ${BackOfficeConfig.ORIGIN_VARIABLE} is set but no database is configured, back-office off.") }
        else -> BackOfficeSpace.start(this, dataSource, backOffice, agirSpace)
    }
    // The testimonies wait in the queue agir.testimonies (refusal only: the server cannot read them).
    testimonySpace?.let { teamBackOffice?.queues?.register(TestimonyQueue(it)) }
    // The demands proposed by citizens, reviewed by the team (Décider, contract of the back-office, section 7).
    proposals?.let { teamBackOffice?.queues?.register(ProposalsBackOfficeQueue(it.queue)) }
    val limits = PostLimits(relay)
    val groups = repository?.let { GroupDirectory(it, GroupIndex(it.dataSource, relay.groupLockWait)) }
    // Anonymous ballot boxes (D-110): closed on schedule, their keys and ballots then published; the
    // demands in debate get theirs, closed when they leave the debate.
    // Only a natural person votes (D-116): the editorial keys and the organisations of the directory are refused.
    val urnes = dataSource?.takeIf { urne.seeds != null }?.let {
        val organisations = VerifiedOrganisations(it, policy::isEditor)
        UrneService.create(UrneStore(it), urne, persons = PersonAccounts(it, policy::isEditor, organisations::isVerified))
    }
    if (urnes != null && dataSource != null) {
        val demands = RevendicationUrnes(dataSource, urnes, policy::isEditor)
        urnes.startClosing(this, UrneService.CLOSING_INTERVAL, log, keepers = listOf({ demands.reconcile() }))
    }
    // Room descriptions stored before the directory table existed (V4) enter it, once.
    groups?.indexMissing()?.takeIf { it > 0 }?.let { log.info("Group directory: $it description(s) indexed.") }
    install(ErrorShield)
    install(ContentNegotiation) {
        json()
    }
    backOffice?.let { install(backOfficeHeaders(it.config.port)) }
    install(DefaultHeaders) {
        // Strict security headers on every answer. HTTPS and HSTS are handled by the
        // reverse proxy (Caddy) which terminates TLS.
        header(HttpHeaders.Server, "PPF")
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("Referrer-Policy", "no-referrer")
        header("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(), usb=(), interest-cohort=()")
        header("Cross-Origin-Opener-Policy", "same-origin")
        header("Cross-Origin-Resource-Policy", "same-origin")
        header(
            "Content-Security-Policy",
            "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; " +
                "font-src 'self'; connect-src 'self'; manifest-src 'self'; " +
                "frame-ancestors 'none'; base-uri 'self'; form-action 'self'; object-src 'none'",
        )
    }
    routing {
        // The site and the public API never answer on the back-office port, nor the back-office on the
        // public port: a routing mistake on one side cannot expose the other (contract, section 3).
        publicPort(backOffice?.config?.port) {
            get("/health") {
                call.respond(
                    HealthStatus(
                        status = HealthStatus.OK,
                        service = "ppf-server",
                        version = AppInfo.VERSION,
                        environment = ServerConfig.environment,
                    ),
                )
            }
            eventsApi(repository, policy, limits, groups, proposals, agirSpace)
            groupsApi(groups)
            revendicationsApi(proposals)
            agirApi(agirSpace)
            testimonyApi(testimonySpace, testimonies.config.maxFileBytes)
            elusApi(elusService)
            territoiresApi(territoiresService)
            urneApi(urnes, urne)
            archivesApi(archives)
            agirSpace?.let { agendaPage(it.agendaSource()) }
            tilesRoutes(tiles)
            parlementRoutes() // Parliamentary open data, read-only from PPF_OPEN_DATA_DIR (D-064).
            // The pages of "Voir" and "Comprendre": contents of the editorial keys only (D-034, D-091).
            webRoutes(
                voir = voir ?: repository?.let { EditorialVoirSource(it, policy) },
                comprendre = comprendre ?: repository?.let { EditorialComprendreSource(it, policy) },
            )
        }
        if (backOffice != null && teamBackOffice != null) {
            createChild(PortSelector(backOffice.config.port)).backOfficeRoutes(teamBackOffice)
        }
    }
}
