package org.politisationpopulairefrancaise.ui.agir.calls

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.agir.calls.Call
import org.politisationpopulairefrancaise.core.agir.calls.CallResponseStore
import org.politisationpopulairefrancaise.core.agir.calls.ParisDateTime
import org.politisationpopulairefrancaise.core.agir.calls.ParisTime
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
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.ui.account.AccountSession
import org.politisationpopulairefrancaise.ui.account.AccountUiState
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Friday 6 November 2026, 10:00 in Paris: the bundled examples are then open, with known countdowns. */
internal val NOW: Long = ParisTime.toEpochSeconds(ParisDateTime(2026, 11, 6, 10, 0))

/** The bundled examples (content/appels), by slug. */
internal object Examples {
    const val MEETING = "assemblee-loyers-tilleuls"
    const val MARCH = "marche-hopital-proximite"
    const val PETITION = "petition-bureau-de-poste"
    const val INTERPELLATION = "interpeller-budget-transports-scolaires"
    const val VOLUNTEERS = "benevoles-aide-aux-devoirs"
    const val INFORMATION = "information-rapport-qualite-eau"
    const val WITNESSES = "temoins-rassemblement-mairie"
    const val CLOSED = "soutien-salaries-cantine-centrale"
    val OPEN = listOf(MEETING, MARCH, PETITION, INTERPELLATION, VOLUNTEERS, INFORMATION, WITNESSES)
}

/** What a test needs to look into after the screen is shown. */
internal class CallsHarness(val database: PpfDatabase) {
    val store = LocalEventStore(database) { NOW }
    val responses = CallResponseStore(database)
    val opened = mutableListOf<String>()
    var library: CallsLibrary? = null
    var session: AccountSession? = null

    val calls: CallsLibrary get() = requireNotNull(library)

    fun call(slug: String): Call = (calls.calls + calls.myCalls.map { it.call }).first { it.slug == slug }
}

/** A server that publishes ([held] false) or holds for review ([held] true) every call, and answers where held ones stand. */
internal class CallServer(var held: Boolean = true) : RemoteEvents, HeldStatusSource {
    val published = InMemoryPublicationPort()
    val received = mutableListOf<SignedEvent>()
    val decisions = mutableMapOf<String, HeldState>()
    var reachable: Boolean = true

    override suspend fun publish(event: SignedEvent): PublicationResult {
        if (!reachable) return PublicationResult.Unavailable()
        received += event
        if (held) {
            decisions.getOrPut(event.id) { HeldState(event.id, HeldStatus.PENDING, event.createdAt) }
            return PublicationResult.Accepted(event.id, heldForReview = true)
        }
        return published.publish(event)
    }

    override suspend fun fetch(filter: RemoteFilter): FetchResult = if (reachable) published.fetch(filter) else FetchResult.Unavailable()

    override suspend fun fetchByTags(tags: List<TagFilter>, filter: RemoteFilter): FetchResult =
        if (reachable) published.fetchByTags(tags, filter) else FetchResult.Unavailable()

    override suspend fun heldStatuses(ids: List<String>): HeldStatuses =
        if (reachable) HeldStatuses.Known(decisions.values.filter { it.id in ids }) else HeldStatuses.Unavailable()
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.showCalls(
    width: Int = 1000,
    height: Int = 740,
    keys: IdentityKeys? = null,
    remote: RemoteEvents? = null,
    database: PpfDatabase = JvmDatabase.inMemory(),
    isVerifiedOrganisation: (String) -> Boolean = { false },
    onCreateEvent: ((Call) -> Unit)? = null,
    onCreateDemand: ((Call) -> Unit)? = null,
    onCreateAccount: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    dark: Boolean = false,
    waitForExamples: Boolean = true,
): CallsHarness {
    val harness = CallsHarness(database)
    val uriHandler = object : UriHandler {
        override fun openUri(uri: String) {
            harness.opened += uri
        }
    }
    setContent {
        val scope = rememberCoroutineScope()
        val library = remember {
            CallsLibrary(harness.store, EditorialTrust.bundledExamples(), remote, scope, harness.responses, clock = { NOW }).also {
                harness.library = it
                it.start()
            }
        }
        val session = remember {
            AccountSession(InMemoryIdentityStore(keys?.let { StoredIdentity.of(it, null) }), scope).also {
                harness.session = it
                it.start()
            }
        }
        val accountKey = (session.state as? AccountUiState.Ready)?.publicKey?.hex
        LaunchedEffect(accountKey) { library.setAccount(accountKey) }
        CompositionLocalProvider(LocalUriHandler provides uriHandler) {
            PpfTheme(darkTheme = dark) {
                Box(Modifier.requiredSize(width.dp, height.dp)) {
                    CallsScreen(
                        library = library,
                        session = session,
                        windowClass = WindowClass.of(widthDp = width.toFloat(), heightDp = height.toFloat()),
                        onBack = onBack,
                        isVerifiedOrganisation = isVerifiedOrganisation,
                        onCreateEvent = onCreateEvent,
                        onCreateDemand = onCreateDemand,
                        onCreateAccount = onCreateAccount,
                    )
                }
            }
        }
    }
    if (waitForExamples) {
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(CallTags.CARD + Examples.PETITION).fetchSemanticsNodes().isNotEmpty() }
    }
    if (keys != null) waitUntil(timeoutMillis = 5_000) { harness.session?.state is AccountUiState.Ready }
    return harness
}
