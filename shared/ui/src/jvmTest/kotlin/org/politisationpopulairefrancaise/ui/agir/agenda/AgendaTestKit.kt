package org.politisationpopulairefrancaise.ui.agir.agenda

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaState
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaStore
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.agenda.MobilizationStatus
import org.politisationpopulairefrancaise.core.agir.agenda.MobilizationType
import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.identity.StoredIdentity
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.HeldState
import org.politisationpopulairefrancaise.core.publication.HeldStatus
import org.politisationpopulairefrancaise.core.publication.HeldStatusSource
import org.politisationpopulairefrancaise.core.publication.HeldStatuses
import org.politisationpopulairefrancaise.core.publication.InMemoryPublicationPort
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.publication.TagFilter
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.account.AccountSession
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Nodes whose test tag starts with [prefix] (the cards of the agenda, for example). */
internal fun tagStartsWith(prefix: String): SemanticsMatcher =
    SemanticsMatcher("test tag starts with $prefix") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true }

/** How many cards of mobilizations the list shows. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.cardCount(): Int = onAllNodes(tagStartsWith(AgendaTags.CARD)).fetchSemanticsNodes().size

/** Fixed instants of the tests of the agenda (local time of Paris). */
internal object AgendaClock {
    /** Friday 13 November 2026, 10 h in Paris: the bundled Paris gathering is "Demain". */
    val NOW: Long = ParisTime.toEpochSeconds(CivilDate(2026, 11, 13), 10, 0)

    fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long = ParisTime.toEpochSeconds(CivilDate(year, month, day), hour, minute)
}

/**
 * A server of the agenda as the contract describes it: the events of [verified] keys are published
 * at once (201), the others held for review (202) and served only once approved; `GET /held`
 * answers for the held ones. [online] = false makes it unreachable. It counts what it receives,
 * so that the tests can check that nothing private ever reaches it.
 */
internal class AgendaServer : RemoteEvents, HeldStatusSource {
    private val port = InMemoryPublicationPort()
    val held = LinkedHashMap<String, Pair<SignedEvent, HeldState>>()
    val verified = mutableSetOf<String>()
    var online = true
    val received = mutableListOf<SignedEvent>()
    val heldQueries = mutableListOf<List<String>>()
    var fetches = 0

    val published: List<SignedEvent> get() = port.events

    fun seed(event: SignedEvent) = runBlocking { check(port.publish(event) is PublicationResult.Accepted) }

    fun decide(id: String, status: HeldStatus, reason: String? = null) {
        val (event, state) = held.getValue(id)
        held[id] = event to state.copy(status = status, decidedAt = state.receivedAt + 60, reason = reason)
        if (status == HeldStatus.APPROVED) seed(event)
    }

    override suspend fun publish(event: SignedEvent): PublicationResult {
        if (!online) return PublicationResult.Unavailable()
        received += event
        if (event.kind == Mobilization.KIND && event.pubkey !in verified && !isCancellationOfPublished(event)) {
            if (!event.verify()) return PublicationResult.Rejected(event.id, org.politisationpopulairefrancaise.core.publication.RejectionReason.INVALID_SIGNATURE)
            held.getOrPut(event.id) { event to HeldState(event.id, HeldStatus.PENDING, receivedAt = event.createdAt) }
            return PublicationResult.Accepted(event.id, heldForReview = true)
        }
        return port.publish(event)
    }

    /**
     * The author's cancellation (decision of the owner of 2026-11-07): a newer version, by the same
     * key, of a published event, where only `ppf-event-status` became `annule`. Published at once.
     */
    private fun isCancellationOfPublished(event: SignedEvent): Boolean {
        if (event.firstTagValue("ppf-event-status") != "annule") return false
        val withoutStatus = { e: SignedEvent -> e.tags.filterNot { it[0] == "ppf-event-status" } }
        return port.events.any { old ->
            old.kind == event.kind && old.pubkey == event.pubkey && old.firstTagValue("d") == event.firstTagValue("d") &&
                old.createdAt < event.createdAt && withoutStatus(old) == withoutStatus(event) && old.content == event.content
        }
    }

    override suspend fun fetch(filter: RemoteFilter): FetchResult {
        fetches++
        return if (online) port.fetch(filter) else FetchResult.Unavailable()
    }

    override suspend fun fetchByTags(tags: List<TagFilter>, filter: RemoteFilter): FetchResult =
        if (online) port.fetchByTags(tags, filter) else FetchResult.Unavailable()

    override suspend fun heldStatuses(ids: List<String>): HeldStatuses {
        if (!online) return HeldStatuses.Unavailable()
        heldQueries += ids
        return HeldStatuses.Known(ids.mapNotNull { held[it]?.second })
    }
}

/** Mobilizations for the tests, all fictitious and marked [EXEMPLE]. */
internal class AgendaKit(val keys: IdentityKeys = IdentityKeys.generate()) {
    val signer = LocalEventSigner(keys)
    val pubkey: String get() = keys.publicKey.hex

    fun mobilization(
        identifier: String,
        title: String,
        start: Long,
        end: Long? = null,
        type: MobilizationType = MobilizationType.RASSEMBLEMENT,
        territory: String = "Villefictive",
        location: String = "Place de la Mairie, Villefictive",
        status: MobilizationStatus = MobilizationStatus.PREVU,
        organizers: List<String> = emptyList(),
        links: List<String> = emptyList(),
        description: String = "[EXEMPLE] Description de démonstration.",
        createdAt: Long = AgendaClock.NOW - 86_400,
    ): SignedEvent = runBlocking {
        signer.sign(
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
                description = description,
            ),
        )
    }
}

/** The device of a test: a database, its stores, an account or not. */
internal class AgendaDevice(withAccount: Boolean, val server: AgendaServer? = null, val keys: IdentityKeys = IdentityKeys.generate()) {
    val database: PpfDatabase = JvmDatabase.inMemory()
    val store = LocalEventStore(database)
    val state: AgendaState = AgendaStore(database)
    val identityStore = InMemoryIdentityStore(if (withAccount) StoredIdentity.of(keys, null) else null)
    val dependencies = AppDependencies(
        identityStore = identityStore,
        contentStore = store,
        remote = server,
        database = database,
    )
    val pubkey: String get() = keys.publicKey.hex
    val signer = LocalEventSigner(keys)
}

/**
 * Shows the agenda in a window of [width] x [height] dp, with the clock of the tests, and hands
 * the library to [onReady] so that the test can look inside.
 */
@Composable
internal fun TestAgenda(
    device: AgendaDevice,
    width: Int,
    height: Int,
    clock: () -> Long = { AgendaClock.NOW },
    dark: Boolean = false,
    trust: EditorialTrust = EditorialTrust.bundledExamples(),
    organisationName: (String) -> String? = { null },
    exporter: CalendarExporter = CalendarExporter { _, _, _ -> CalendarExportResult.DONE },
    onOpenLink: (String) -> Unit = {},
    onNavigate: (org.politisationpopulairefrancaise.core.Space) -> Unit = {},
    onReady: (AgendaLibrary, AccountSession) -> Unit = { _, _ -> },
) {
    val scope = rememberCoroutineScope()
    val agenda = remember { AgendaLibrary(device.store, device.state, trust, device.server, scope, clock).also { it.start() } }
    val session = remember { AccountSession(device.identityStore, scope).also { it.start() } }
    remember { onReady(agenda, session) }
    PpfTheme(darkTheme = dark) {
        Box(Modifier.requiredSize(width.dp, height.dp)) {
            AgendaScreen(
                dependencies = device.dependencies,
                session = session,
                windowClass = WindowClass.of(widthDp = width.toFloat(), heightDp = height.toFloat()),
                onNavigate = onNavigate,
                agenda = agenda,
                organisationName = organisationName,
                exporter = exporter,
                onOpenLink = onOpenLink,
            )
        }
    }
}
