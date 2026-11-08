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
import org.politisationpopulairefrancaise.server.accounts.AccountTypes
import org.politisationpopulairefrancaise.server.accounts.CompromisedAccounts
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
import org.politisationpopulairefrancaise.server.api.accountsApi
import org.politisationpopulairefrancaise.server.api.eventsApi
import org.politisationpopulairefrancaise.server.api.groupsApi
import org.politisationpopulairefrancaise.server.archive.ArchiveService
import org.politisationpopulairefrancaise.server.archive.ArchiveSetup
import org.politisationpopulairefrancaise.server.archive.archivesApi
import org.politisationpopulairefrancaise.server.backoffice.BackOffice
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeConfig
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeSetup
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeSpace
import org.politisationpopulairefrancaise.server.backoffice.GroupCreationsQueue
import org.politisationpopulairefrancaise.server.backoffice.GroupOrphansQueue
import org.politisationpopulairefrancaise.server.backoffice.PortSelector
import org.politisationpopulairefrancaise.server.backoffice.backOfficeHeaders
import org.politisationpopulairefrancaise.server.backoffice.backOfficeRoutes
import org.politisationpopulairefrancaise.server.backoffice.publicPort
import org.politisationpopulairefrancaise.server.editorial.EditorialDrafts
import org.politisationpopulairefrancaise.server.editorial.EditorialDraftsPurge
import org.politisationpopulairefrancaise.server.editorial.EditorialDraftsSection
import org.politisationpopulairefrancaise.server.editorial.EditorialPublisher
import org.politisationpopulairefrancaise.server.editorial.EditorialQueue
import org.politisationpopulairefrancaise.server.editorial.EditorialReviews
import org.politisationpopulairefrancaise.server.editorial.UnpluggedEditorialPublisher
import org.politisationpopulairefrancaise.server.editorial.PublishedContents
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSpace
import kotlin.time.Duration.Companion.hours
import org.politisationpopulairefrancaise.server.db.Database
import org.politisationpopulairefrancaise.server.db.DatabaseConfig
import org.politisationpopulairefrancaise.server.decider.EditorialDeciderSource
import org.politisationpopulairefrancaise.server.decider.PetitionLinks
import org.politisationpopulairefrancaise.server.decider.PetitionLinksQueue
import org.politisationpopulairefrancaise.server.decider.DeliberationQueue
import org.politisationpopulairefrancaise.server.decider.DeliberationSubmissions
import org.politisationpopulairefrancaise.server.decider.deliberationApi
import org.politisationpopulairefrancaise.server.elus.ElusConfig
import org.politisationpopulairefrancaise.server.elus.ElusLookup
import org.politisationpopulairefrancaise.server.elus.ElusService
import org.politisationpopulairefrancaise.server.elus.ElusSetup
import org.politisationpopulairefrancaise.server.elus.elusApi
import org.politisationpopulairefrancaise.server.territoires.TerritoiresService
import org.politisationpopulairefrancaise.server.territoires.TerritoiresSetup
import org.politisationpopulairefrancaise.server.territoires.territoiresApi
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.export.OpenDatasets
import org.politisationpopulairefrancaise.server.export.OpenExport
import org.politisationpopulairefrancaise.server.export.exportApi
import org.politisationpopulairefrancaise.server.export.pageSource
import org.politisationpopulairefrancaise.server.groups.GroupDirectory
import org.politisationpopulairefrancaise.server.groups.GroupGuardrails
import org.politisationpopulairefrancaise.server.groups.GroupIndex
import org.politisationpopulairefrancaise.server.groups.GroupOrphans
import org.politisationpopulairefrancaise.server.groups.OrganisationNames
import org.politisationpopulairefrancaise.server.push.PushConfig
import org.politisationpopulairefrancaise.server.push.PushService
import org.politisationpopulairefrancaise.server.push.pushApi
import org.politisationpopulairefrancaise.server.legal.LegalRegister
import org.politisationpopulairefrancaise.server.legal.LegalRegisterPurge
import org.politisationpopulairefrancaise.server.legal.transparencyApi
import org.politisationpopulairefrancaise.server.relay.RelayConfig
import org.politisationpopulairefrancaise.server.relay.WrapRetention
import org.politisationpopulairefrancaise.server.revendications.AiReviewConfig
import org.politisationpopulairefrancaise.server.revendications.RevendicationProposals
import org.politisationpopulairefrancaise.server.revendications.RevendicationsConfig
import org.politisationpopulairefrancaise.server.revendications.RevendicationsSetup
import org.politisationpopulairefrancaise.server.revendications.revendicationsApi
import org.politisationpopulairefrancaise.server.voir.EditorialVoirSource
import org.politisationpopulairefrancaise.server.voir.VoirDatasets
import org.politisationpopulairefrancaise.server.comprendre.EditorialComprendreSource
import org.politisationpopulairefrancaise.server.tiles.TilesConfig
import org.politisationpopulairefrancaise.server.tiles.tilesRoutes
import org.politisationpopulairefrancaise.server.seed.ExampleSeed
import org.politisationpopulairefrancaise.server.agir.VerifiedOrganisations
import org.politisationpopulairefrancaise.server.revendications.ProposalsBackOfficeQueue
import org.politisationpopulairefrancaise.server.urne.PersonAccounts
import org.politisationpopulairefrancaise.server.urne.RevendicationUrnes
import org.politisationpopulairefrancaise.server.urne.UrneConfig
import org.politisationpopulairefrancaise.server.urne.DeliberationUrnes
import org.politisationpopulairefrancaise.server.urne.ElectedCertifications
import org.politisationpopulairefrancaise.server.urne.ElectedCertificationsQueue
import org.politisationpopulairefrancaise.server.urne.InPersonValidations
import org.politisationpopulairefrancaise.server.urne.UrneService
import org.politisationpopulairefrancaise.server.urne.UrneStore
import org.politisationpopulairefrancaise.server.urne.UrneClosureStatements
import org.politisationpopulairefrancaise.server.signing.HttpPlatformSigner
import org.politisationpopulairefrancaise.server.signing.PlatformSigner
import org.politisationpopulairefrancaise.server.signing.PlatformSignerConfig
import org.politisationpopulairefrancaise.server.urne.ValidationSessionsQueue
import org.politisationpopulairefrancaise.server.urne.urneApi
import org.politisationpopulairefrancaise.server.security.SecurityContactSetup
import org.politisationpopulairefrancaise.web.SecurityContact
import org.politisationpopulairefrancaise.web.agendaPage
import org.politisationpopulairefrancaise.web.comprendre.ComprendreSource
import org.politisationpopulairefrancaise.web.openDataPage
import org.politisationpopulairefrancaise.web.securityPages
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
 * - PPF_GROUP_YOUNG_ACCOUNT_DAYS: how long a key must be known before it creates a room named like a
 *   verified organisation (7 days by default, D-132), see [GroupGuardrails].
 * - PPF_HELD_RETENTION_DAYS, PPF_HELD_CLEANUP_INTERVAL_MINUTES: the review queue of the Agir space,
 *   see [AgirConfig].
 * - PPF_TILES_DIR: the folder of the PMTiles files of the map backgrounds, served at `/tiles/` by
 *   range (development and preproduction; in production Caddy serves them), see [TilesConfig].
 * - PPF_RNE_SOURCE, PPF_RNE_REFRESH_DAYS: the directory of elected officials (fictitious example set
 *   by default), see [ElusConfig].
 * - PPF_ARCHIVE_*: the timestamped archive (on in production only by default), see [ArchiveConfig].
 * - PPF_TESTIMONY_DIR, PPF_MODERATION_KEY, PPF_EVIDENCE_KEY and the other PPF_TESTIMONY_* and
 *   PPF_EVIDENCE_RETENTION_DAYS settings: the testimonies of the Agir space, see [TestimonySetup].
 * - PPF_PUBLIC_URL, PPF_SECURITY_NOSTR_KEY, PPF_SECURITY_GITHUB_URL: how researchers report a vulnerability
 *   (security.txt and /securite, S-92); in production one private channel is required, see [SecurityContactSetup].
 * - PPF_EXAMPLES_DIR: preproduction only, a directory of example contents (JSON arrays of signed
 *   events, all marked [EXEMPLE]) stored at startup when a database is configured; the server refuses
 *   to start when it is set in production, see [ExampleSeed].
 * - PPF_VOTE_SEEDS_DIR, PPF_VOTE_POW_REGISTER, PPF_VOTE_POW_TOKENS, PPF_VOTE_POW_BALLOT, PPF_PUBLIC_URL,
 *   PPF_VOTE_MAX_TOKENS_PER_REQUEST: the anonymous ballot boxes, see [UrneConfig] (D-110).
 * - PPF_BACKOFFICE_ORIGIN, PPF_BACKOFFICE_PORT, PPF_AUDIT_PUBLIC_KEY, PPF_BACKOFFICE_MAINTENANCE_INTERVAL_MINUTES:
 *   the team back-office, off unless PPF_BACKOFFICE_ORIGIN is set (for example
 *   `https://equipe.preprod.politisation-populaire-francaise.org`; `http://localhost:8081` in development
 *   only). Its routes answer only on PPF_BACKOFFICE_PORT (default 8081), never on PORT; see [BackOfficeConfig].
 * - PPF_VAPID_PRIVATE_KEY_FILE, PPF_PUSH_ALLOWED_HOSTS, PPF_PUSH_INTERNAL_URL, PPF_PUBLIC_URL: the push
 *   wake-ups (M31), off unless PPF_VAPID_PRIVATE_KEY_FILE is set, see [PushConfig].
 *
 * Legal register (D-077, [LegalRegister]): nothing to configure. Its retention (one year) is fixed in the
 * database and it is never read but on a judicial requisition approved by two people in the back-office.
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
    // THE platform signer (D-129): one instance for the whole server, null without PPF_SIGNER_URL.
    val platformSigner = PlatformSignerConfig.fromEnvironment()?.let { HttpPlatformSigner(it) }
    val appRole = Database.appRole()
    val dataSource = DatabaseConfig.fromEnvironment()?.let { config ->
        Database.connect(config).also { Database.prepare(it, migrate, appRole) }
    }
    if (examples != null) {
        if (dataSource == null) {
            LoggerFactory.getLogger(ExampleSeed::class.java)
                .warn("Example contents: ${ExampleSeed.DIRECTORY_VARIABLE} is set but no database is configured, no example content loaded.")
        } else {
            ExampleSeed.load(examples, EventRepository(dataSource, LegalRegister(PublicationPolicy.fromEnvironment()::isEditor)))
        }
    }
    // Checked before the server starts: a malformed security contact, or none in production, is refused (S-92).
    val securityContact = SecurityContactSetup.fromEnvironment(ServerConfig.environment, ServerConfig.port)
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
        module(dataSource, backOffice = backOffice, securityContact = securityContact, platformSigner = platformSigner)
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
 *
 * Reminders of the legal register (D-077, legal note docs/integration/lcen-conservation-identification.md):
 * - no IP address (nor port) is ever collected or logged anywhere: neither by Caddy, nor by the server, nor
 *   by any rate limiting by IP (the limits are per public key and global); this is what makes "nothing
 *   collected, nothing to keep" hold (décret n° 2021-1362, article 8);
 * - no e-mail or phone field, even optional, anywhere: it would make five years of retention of civil
 *   identity (décret n° 2021-1362, article 2);
 * - the register of level 1 ([LegalRegister]) keeps only pseudonymous data of the public contents, one year;
 *   level 2 of the note (IP sealed under an offline key) stays off unless a lawyer requires it.
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
    push: PushConfig = PushConfig.fromEnvironment(),
    /** How researchers report a vulnerability (S-92): security.txt and the page /securite. */
    securityContact: SecurityContact = SecurityContactSetup.fromEnvironment(ServerConfig.environment, ServerConfig.port),
    /**
     * THE platform signer of the server (D-129, docs/integration/service-signature.md): the separate signing
     * service when PPF_SIGNER_URL is set, null otherwise (what needs it then waits, never signs elsewhere).
     * Passed to every pole that needs it; the tests give a FakePlatformSigner.
     */
    platformSigner: PlatformSigner? = null,
    /** Where an approved editorial version goes (circuit éditorial, step 5); nothing is sent until the signing service is plugged. */
    editorialPublisher: EditorialPublisher = UnpluggedEditorialPublisher,
) {
    // Every stored public content enters the legal register, except those of the editorial keys (D-077).
    val repository = dataSource?.let { EventRepository(it, LegalRegister(policy::isEditor)) }
    // The open export (M34, S-48): the contents of the editorial keys and the tables each pole registers.
    val openDatasets = OpenDatasets().also(VoirDatasets::registerOn)
    val openExport = repository?.let { OpenExport(it, policy, openDatasets) }
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
    // The legal register keeps one year (fixed in the database); its daily purge lives with the application.
    dataSource?.let { LegalRegisterPurge(it).start(this, LegalRegisterPurge.DEFAULT_INTERVAL, log) }
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
    // The writing pages of the editorial circuit of Comprendre (step 3): a section of the back-office.
    if (teamBackOffice != null && dataSource != null && repository != null) {
        val drafts = EditorialDrafts(dataSource, teamBackOffice.clock)
        // The review (step 4): the reviewer never wrote the draft, nor shares a Nostr key with one who did.
        val reviews = EditorialReviews(dataSource, drafts, teamBackOffice.clock) { account -> teamBackOffice.member(account)?.nostrPubkey }
        teamBackOffice.sections.register(
            EditorialDraftsSection(EditorialSpace.COMPRENDRE, drafts, reviews, PublishedContents(repository, policy::isEditor), teamBackOffice.clock, editorialPublisher),
        )
        // The queue editorial.comprendre (counts, deadlines and levels of the dashboard).
        teamBackOffice.queues.register(EditorialQueue(EditorialSpace.COMPRENDRE, reviews) { account -> teamBackOffice.member(account)?.pseudonym })
        // The same circuit for Voir (step 6), on the schemas of core/voir/VoirSchemas.kt; its preview is the page of the site.
        teamBackOffice.sections.register(
            EditorialDraftsSection(EditorialSpace.VOIR, drafts, reviews, PublishedContents(repository, policy::isEditor), teamBackOffice.clock, editorialPublisher),
        )
        teamBackOffice.queues.register(EditorialQueue(EditorialSpace.VOIR, reviews) { account -> teamBackOffice.member(account)?.pseudonym })
        // A draft never submitted and unchanged for 180 days is deleted (docs/REGISTRE_DONNEES.md).
        EditorialDraftsPurge(drafts).start(this, 24.hours, log)
    }
    val limits = PostLimits(relay)
    // Rooms (NIP-29): directory, roles and moderation (D-131), guardrails of the creation (D-132).
    val groups = repository?.let {
        GroupDirectory(
            it,
            GroupIndex(it.dataSource, relay.groupLockWait),
            guardrails = GroupGuardrails(
                it.dataSource,
                OrganisationNames(it.dataSource, policy::isEditor),
                isTrusted = policy::isEditor,
                youngAccountDays = GroupGuardrails.youngAccountDaysFromEnvironment(),
            ),
        )
    }
    // Anonymous ballot boxes (D-110): closed on schedule, their keys and ballots then published; the
    // demands in debate get theirs, closed when they leave the debate.
    // Only a natural person votes (D-116): the editorial keys and the organisations of the directory are refused.
    val urneStore = dataSource?.takeIf { urne.seeds != null }?.let { UrneStore(it, LegalRegister(policy::isEditor)) }
    val urnes = urneStore?.let { store ->
        val organisations = VerifiedOrganisations(store.dataSource, policy::isEditor)
        UrneService.create(store, urne, persons = PersonAccounts(store.dataSource, policy::isEditor, organisations::isVerified))
    }
    // In-person validation (D-112, 4.3): only with the back-office (sessions of the validators) and the audit key,
    // which seals the link « account, session » (never kept in clear).
    val inPerson = if (urneStore != null && urnes != null && teamBackOffice?.auditSealer != null) {
        InPersonValidations(urneStore, urnes, teamBackOffice.validationGate, teamBackOffice.auditSealer)
    } else {
        null
    }
    // The certification of elected officials' accounts (DE-23): with the directory (D-047) and the back-office,
    // where a certifier decides after meeting the official (queue elus.certifications).
    val certifications = if (urneStore != null && urnes != null && teamBackOffice != null && elusService != null) {
        ElectedCertifications(urneStore, urnes, ElusLookup(elusService)).also { teamBackOffice.queues.register(ElectedCertificationsQueue(it, urneStore)) }
    } else {
        null
    }
    // The propositions and amendments of citizens to a deliberation, reviewed by the team (DE-25).
    val deliberationSubmissions = repository?.let { DeliberationSubmissions(it.dataSource, it, policy::isEditor) }
    deliberationSubmissions?.let { teamBackOffice?.queues?.register(DeliberationQueue(it)) }
    // The sessions of the validators with an abnormal pace, for the administrators (D-112, design 8).
    if (urneStore != null && teamBackOffice != null) teamBackOffice.queues.register(ValidationSessionsQueue(urneStore.dataSource))
    if (urnes != null && dataSource != null) {
        val demands = RevendicationUrnes(dataSource, urnes, policy::isEditor)
        // The boxes of the citizen deliberations, opened as their published phases come (DE-25).
        val deliberations = repository?.let { DeliberationUrnes(it, urnes, policy::isEditor) }
        // The official petitions attached by the team to the demands (D-115), signed for decider.pacte after an approval.
        val petitionLinks = if (repository != null && platformSigner != null) {
            PetitionLinks(repository.dataSource, repository, platformSigner).also { teamBackOffice?.queues?.register(PetitionLinksQueue(it, urnes)) }
        } else {
            null
        }
        // The closing statements signed by the platform (decider.urn_closure), when the signing service is configured.
        val closures = if (urneStore != null && repository != null && platformSigner != null) {
            UrneClosureStatements(urneStore, urnes, platformSigner, repository)
        } else {
            null
        }
        // Daily, a certified mandate absent from the updated directory ends its certification (DE-23).
        urnes.startClosing(
            this,
            UrneService.CLOSING_INTERVAL,
            log,
            keepers = listOf({ demands.reconcile() }, { deliberations?.reconcile() }, { certifications?.checkMandatesDue() }, { deliberationSubmissions?.purgeDecided() }, { closures?.runDue() }, { petitionLinks?.runDue() }),
        )
    }
    // Room descriptions stored before the directory table existed (V4) enter it, once.
    groups?.indexMissing()?.takeIf { it > 0 }?.let { log.info("Group directory: $it description(s) indexed.") }
    // Push wake-ups (M31): a constant, encrypted "synchronize" only; the worker lives with the application.
    val pushService = PushService.start(this, dataSource, push)
    // Recovery keys and compromised accounts (S-146, D-134); the OpenTimestamps stamping of the
    // commitments waits for Décider's ArchiveService (CommitmentStamper.NONE until then).
    val compromised = repository?.let { CompromisedAccounts(it) }
    // The room creations held for the team (contract docs/integration/salons-roles.md, section 3 bis);
    // a creation signed after its key was declared compromised can only be refused (S-146).
    groups?.let { directory -> teamBackOffice?.queues?.register(GroupCreationsQueue(directory, compromised)) }
    // The rooms left without administrator by a compromised account, for the team (docs/integration/salons-reprise.md).
    if (groups != null && compromised != null && repository != null) {
        teamBackOffice?.queues?.register(GroupOrphansQueue(GroupOrphans(repository.dataSource), groups, compromised))
    }
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
            eventsApi(
                repository,
                policy,
                limits,
                groups,
                proposals,
                agirSpace,
                onStored = pushService::stored,
                accountTypes = repository?.let { AccountTypes(it.dataSource) },
                compromised = compromised,
                deliberation = deliberationSubmissions,
            )
            accountsApi(compromised)
            pushApi(pushService, isCompromised = { key -> compromised?.isCompromised(key) == true })
            deliberationApi(deliberationSubmissions)
            groupsApi(groups)
            revendicationsApi(proposals)
            agirApi(agirSpace)
            testimonyApi(testimonySpace, testimonies.config.maxFileBytes)
            elusApi(elusService)
            territoiresApi(territoiresService)
            urneApi(urnes, urne, validations = inPerson, certifications = certifications)
            archivesApi(archives)
            transparencyApi(dataSource) // Requisitions answered per month only (D-077).
            exportApi(openExport) // The open export and its datasets (M34, S-48).
            openExport?.let { openDataPage(it.pageSource()) } // The page /donnees of the site.
            agirSpace?.let { agendaPage(it.agendaSource()) }
            tilesRoutes(tiles)
            parlementRoutes() // Parliamentary open data, read-only from PPF_OPEN_DATA_DIR (D-064).
            securityPages(securityContact) // security.txt (RFC 9116) and the disclosure policy (S-92).
            // The pages of "Voir" and "Comprendre": contents of the editorial keys only (D-034, D-091).
            webRoutes(
                voir = voir ?: repository?.let { EditorialVoirSource(it, policy) },
                comprendre = comprendre ?: repository?.let { EditorialComprendreSource(it, policy) },
                decider = repository?.let { EditorialDeciderSource(it, policy) },
            )
        }
        if (backOffice != null && teamBackOffice != null) {
            createChild(PortSelector(backOffice.config.port)).backOfficeRoutes(teamBackOffice)
        }
    }
}
