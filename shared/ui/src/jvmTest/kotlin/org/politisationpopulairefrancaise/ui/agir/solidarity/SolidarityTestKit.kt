package org.politisationpopulairefrancaise.ui.agir.solidarity

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.agir.organisations.Organisation
import org.politisationpopulairefrancaise.core.agir.solidarity.SolidarityFormats
import org.politisationpopulairefrancaise.core.agir.solidarity.SolidarityFund
import org.politisationpopulairefrancaise.core.agir.solidarity.VolunteerMission
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.account.rememberAccountSession
import org.politisationpopulairefrancaise.ui.agir.AgirFixtures
import org.politisationpopulairefrancaise.ui.agir.AgirScreen
import org.politisationpopulairefrancaise.ui.agir.RecordingUriHandler
import org.politisationpopulairefrancaise.ui.agir.organisations.OrganisationsLibrary
import org.politisationpopulairefrancaise.ui.agir.organisations.rememberOrganisationsLibrary
import org.politisationpopulairefrancaise.ui.content.rememberContentLibrary
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/**
 * Funds and missions for the tests of the solidarity rubric, built in memory (the bundled
 * solidarite.json is not signed yet, and the tests never depend on it). The managers named by their
 * entry are examples of the directory bundled with the application (content/organisations).
 */
object SolidarityExamples {
    /** "Legal team de démonstration du Nord", a bundled entry of the directory. */
    val LEGAL_TEAM: ContentAddress = ContentAddress(Organisation.KIND, EditorialTrust.EXAMPLE_EDITOR, "legal-team-nord")

    /** "Union locale fictive de Lille", a bundled entry of the directory without an account. */
    val UNION_LILLE: ContentAddress = ContentAddress(Organisation.KIND, EditorialTrust.EXAMPLE_EDITOR, "union-locale-lille")

    const val LILLE = "caisse-lille"
    const val LILLE_LINK = "https://example.org/caisse-lille"
    const val LEGAL = "caisse-juridique-nord"
    const val LIVREURS = "caisse-livreurs"
    const val CLOSED = "caisse-close-rennes"

    const val TRANSLATION = "mission-traduction"
    const val LAWYERS = "mission-juristes"
    const val WELCOME = "mission-accueil-lyon"

    suspend fun fund(
        slug: String,
        name: String,
        type: String = SolidarityFormats.FUND_STRIKE,
        territory: String = "France",
        managerName: String? = null,
        managerEntry: ContentAddress? = null,
        donationLink: String? = "https://example.org/$slug",
        status: String = SolidarityFormats.FUND_OPEN,
        signer: LocalEventSigner = AgirFixtures.editor,
        updatedAt: Long = 1_794_000_000,
    ): SignedEvent = signer.sign(
        SolidarityFund.build(
            editor = signer.publicKey,
            slug = slug,
            name = name,
            summary = "[EXEMPLE] Une caisse fictive des tests.",
            type = type,
            territory = territory,
            beneficiary = "Grévistes fictifs des tests",
            verifiedOn = "2026-11-05",
            publishedAt = 1_793_400_000,
            updatedAt = updatedAt,
            presentation = "Présentation fictive.",
            managerName = managerName,
            managerEntry = managerEntry,
            donationLink = donationLink,
            status = status,
            origin = ContentOrigin.AI_ASSISTED,
        ),
    )

    suspend fun mission(
        slug: String,
        title: String,
        skills: List<String>,
        territory: String = "France",
        remote: Boolean = true,
        location: String? = null,
        signer: LocalEventSigner = AgirFixtures.editor,
    ): SignedEvent = signer.sign(
        VolunteerMission.build(
            editor = signer.publicKey,
            slug = slug,
            title = title,
            summary = "[EXEMPLE] Une mission fictive des tests.",
            skills = skills,
            territory = territory,
            publishedAt = 1_793_400_000,
            updatedAt = 1_794_000_000,
            description = "Description fictive.",
            remote = remote,
            location = location,
            origin = ContentOrigin.HUMAN,
        ),
    )

    /** Four funds (three open: Lille, the Nord, national; one closed in Rennes) and three missions. */
    suspend fun all(): List<SignedEvent> = listOf(
        fund(LILLE, "[EXEMPLE] Caisse de grève fictive de Lille", territory = "Lille (59)", managerEntry = UNION_LILLE, donationLink = LILLE_LINK),
        fund(LEGAL, "[EXEMPLE] Caisse juridique fictive du Nord", type = SolidarityFormats.FUND_LEGAL, territory = "Nord (59)", managerEntry = LEGAL_TEAM),
        fund(LIVREURS, "[EXEMPLE] Caisse fictive des livreurs", managerName = "Intersyndicale fictive des livreurs"),
        fund(CLOSED, "[EXEMPLE] Caisse fictive close de Rennes", territory = "Rennes (35)", managerName = "Intersyndicale fictive", donationLink = null, status = SolidarityFormats.FUND_CLOSED),
        mission(TRANSLATION, "[EXEMPLE] Traduire une fiche", listOf("traduction")),
        mission(LAWYERS, "[EXEMPLE] Relire les guides juridiques", listOf("juriste")),
        mission(WELCOME, "[EXEMPLE] Accueillir au stand de Lyon", listOf("accueil"), territory = "Lyon (69)", remote = false, location = "Stand fictif, Lyon"),
    )

    suspend fun storeAll(store: LocalEventStore) {
        all().forEach { store.store(it) }
    }
}

/**
 * "Agir" alone in a window of the given size, with a solidarity rubric that reads nothing bundled:
 * the tests put their funds and missions in the local database first.
 */
class SolidarityHarness(val dependencies: AppDependencies = AgirFixtures.dependencies()) {
    val uris = RecordingUriHandler()
    lateinit var solidarity: SolidarityLibrary
    lateinit var organisations: OrganisationsLibrary

    @Composable
    fun Content(width: Int, height: Int, dark: Boolean = false) {
        Box(Modifier.requiredSize(width.dp, height.dp)) {
            PpfTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalUriHandler provides uris) {
                    val library = rememberContentLibrary(dependencies)
                    organisations = rememberOrganisationsLibrary(dependencies)
                    val scope = rememberCoroutineScope()
                    solidarity = remember(dependencies) {
                        SolidarityLibrary(dependencies.contentStore, dependencies.editorialTrust, dependencies.remote, scope, bundled = { null })
                    }
                    val session = rememberAccountSession(dependencies.identityStore)
                    AgirScreen(
                        dependencies = dependencies,
                        library = library,
                        session = session,
                        windowClass = WindowClass.of(width.toFloat(), height.toFloat()),
                        modifier = Modifier.fillMaxSize(),
                        organisations = organisations,
                        solidarity = solidarity,
                    )
                }
            }
        }
    }
}

/** Shows an item of the rubric's list (the list is lazy: only what is near the screen exists), waiting for it to come. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.showInList(tag: String) {
    waitUntil(timeoutMillis = 60_000) {
        runCatching { onNodeWithTag(SolidarityTags.LIST).performScrollToNode(hasTestTag(tag)) }.isSuccess
    }
    waitForIdle()
}
