package org.politisationpopulairefrancaise.ui.voir.parlement

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.voir.LegislativeText
import org.politisationpopulairefrancaise.core.voir.TextStage
import org.politisationpopulairefrancaise.core.voir.application.ApplicationFormat
import org.politisationpopulairefrancaise.core.voir.application.ApplicationLoader
import org.politisationpopulairefrancaise.core.voir.application.ApplicationMeasure
import org.politisationpopulairefrancaise.core.voir.application.ApplicationPackage
import org.politisationpopulairefrancaise.core.voir.application.LawApplication
import org.politisationpopulairefrancaise.core.voir.application.MeasureState
import org.politisationpopulairefrancaise.core.voir.parlement.InMemoryParliamentStorage
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentRepository
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * « Applicomètre » (VO-36): nothing before the first import, one package per legislature downloaded on a gesture
 * with its size, the same figures for every law, the delays said from the facts of the source only, and the
 * application of its law on the sheet of a bill of "Voir".
 */
@OptIn(ExperimentalTestApi::class)
class ApplicationScreenTest {

    private val keys = ParliamentUiFixtures.Keys()

    private fun measure(order: Int, state: MeasureState, planned: String? = null, delay: Int? = null, observation: String? = null) =
        ApplicationMeasure(order = order, articles = "Article $order", subject = "[EXEMPLE] Mesure $order", observation = observation, decree = if (state == MeasureState.APPLIED) "JORFTEXT000099100001" else null, state = state, delayDays = delay, plannedMonth = planned)

    private val housing = LawApplication(
        id = "JORFTEXT000099000001",
        title = "[EXEMPLE] LOI relative au logement abordable",
        publishedOn = "2026-08-18",
        legislature = 17,
        dossier = "an-dossier:DLR5L17N90001",
        dossierPath = "exemple_logement_abordable",
        toApply = 4,
        applied = 1,
        measures = listOf(
            measure(1, MeasureState.APPLIED, delay = 40),
            measure(2, MeasureState.PENDING, planned = "2026-10", observation = "Publication envisagée en octobre 2026"),
            measure(3, MeasureState.PENDING, planned = "2027-03", observation = "Publication envisagée en mars 2027"),
            measure(4, MeasureState.PENDING),
            measure(5, MeasureState.NOT_NEEDED, observation = "[EXEMPLE] Mesure devenue sans objet"),
        ),
    )
    private val transparency = LawApplication(id = "JORFTEXT000099000002", title = "[EXEMPLE] LOI sur la transparence des rendez-vous", publishedOn = "2026-11-02", legislature = 17, directApplication = true)
    private val services = LawApplication(id = "JORFTEXT000099000003", title = "[EXEMPLE] LOI de programmation des services publics", publishedOn = "2023-05-10", legislature = 16, toApply = 3, applied = 3)

    private val packages = mapOf(
        ApplicationFormat.packageName(17) to ApplicationFormat.write(ApplicationPackage(example = true, legislature = 17, updatedOn = "2026-11-07", laws = listOf(transparency, housing))).encodeToByteArray(),
        ApplicationFormat.packageName(16) to ApplicationFormat.write(ApplicationPackage(example = true, legislature = 16, updatedOn = "2026-11-07", laws = listOf(services))).encodeToByteArray(),
    )

    private fun applicationTest(block: suspend ComposeUiTest.() -> Unit) =
        runComposeUiTest(EmptyCoroutineContext, EmptyCoroutineContext, 3.minutes, block)

    private fun ComposeUiTest.has(tag: String) = onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.waitForTag(tag: String) = waitUntil(timeoutMillis = 10_000) { has(tag) }

    private suspend fun ComposeUiTest.show(
        published: Boolean,
        extra: (@androidx.compose.runtime.Composable (ParliamentState) -> Unit)? = null,
    ): Pair<ParliamentUiFixtures.Server, () -> ParliamentState> {
        val all = ParliamentUiFixtures.packages() + if (published) packages else emptyMap()
        val server = ParliamentUiFixtures.Server(keys.sign(ParliamentUiFixtures.index(all)), all)
        var holder: ParliamentState? = null
        setContent {
            val scope = rememberCoroutineScope()
            val state = remember {
                val storage = InMemoryParliamentStorage()
                ParliamentState(
                    ParliamentRepository(server, storage, trust = keys.trust, clock = { ParliamentUiFixtures.NOW }),
                    scope,
                    application = LawApplicationState(ApplicationLoader(server, storage, clock = { ParliamentUiFixtures.NOW }), scope),
                ).also { it.open() }
            }
            holder = state
            PpfTheme(darkTheme = false) {
                Box(Modifier.requiredSize(1000.dp, 760.dp)) {
                    if (extra != null && !state.isOpen) Column { extra(state) } else if (state.isOpen) ParliamentScreen(state)
                }
            }
        }
        waitUntil(timeoutMillis = 10_000) { holder?.loading == false && holder?.opening != null }
        waitForIdle()
        return server to { holder!! }
    }

    private fun ComposeUiTest.openPage() {
        onNodeWithTag(ParliamentTags.LIST).performScrollToNode(hasTestTag(ApplicationTags.ENTRY))
        onNodeWithTag(ApplicationTags.ENTRY).performClick()
        waitForTag(ApplicationTags.PAGE)
        waitForIdle()
    }

    private fun ComposeUiTest.count(text: String) = onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    @Test
    fun beforeTheFirstImportThePageSaysSo() = applicationTest {
        val (server, _) = show(published = false)
        openPage()
        onNodeWithTag(ApplicationTags.NONE_YET).assertExists()
        assertTrue(!has(ApplicationTags.download(17)))
        assertTrue(server.requests.none { it?.startsWith(ApplicationFormat.PACKAGE_PREFIX) == true })
    }

    @Test
    fun everyLawHasTheSameFiguresAndTheDelaysComeFromTheSource() = applicationTest {
        val (server, _) = show(published = true)
        openPage()
        assertTrue(server.requests.none { it?.startsWith(ApplicationFormat.PACKAGE_PREFIX) == true }, "nothing before the gesture")
        onNodeWithTag(ApplicationTags.download(17)).assert(hasText("Télécharger la 17e législature", substring = true)).performClick()
        waitForTag(ApplicationTags.SEARCH)
        assertEquals(listOf(ApplicationFormat.packageName(17)), server.requests.filter { it?.startsWith(ApplicationFormat.PACKAGE_PREFIX) == true }, "one package, the one asked")
        // The 16th legislature stays offered, never downloaded without a gesture.
        onNodeWithTag(ApplicationTags.download(16)).assertExists()

        onNodeWithTag(ApplicationTags.PAGE).performScrollToNode(hasTestTag(ApplicationTags.law(transparency.id)))
        onNodeWithTag(ApplicationTags.law(transparency.id)).assert(hasText("Aucune mesure d’application attendue", substring = true))
        onNodeWithTag(ApplicationTags.PAGE).performScrollToNode(hasTestTag(ApplicationTags.law(housing.id)))
        onNodeWithTag(ApplicationTags.law(housing.id))
            .assert(hasText("Mesures prises : 1 sur 4 (25 %)", substring = true))
            .assert(hasText("En attente : 3 · date annoncée dépassée : 1", substring = true))
        // Never a ranking (the order, newest first, is checked in LawApplicationTest).
        listOf("classement", "meilleur", "pire", "mauvais élève").forEach { assertEquals(0, count(it), it) }

        // The laws still awaiting a measure.
        onNodeWithTag(ApplicationTags.PAGE).performScrollToNode(hasTestTag(ApplicationTags.PENDING_ONLY))
        onNodeWithTag(ApplicationTags.PENDING_ONLY).performClick()
        waitForIdle()
        onNodeWithTag(ApplicationTags.PAGE).performScrollToNode(hasTestTag(ApplicationTags.law(housing.id)))
        assertTrue(!has(ApplicationTags.law(transparency.id)))

        // One law: its measures, the months since the law, the announced month passed or not, the delay of a decree.
        onNodeWithTag(ApplicationTags.law(housing.id)).performScrollTo().performClick()
        waitForTag(ApplicationTags.LAW_PAGE)
        onNodeWithTag(ApplicationTags.measure(2)).performScrollTo()
            .assert(hasText("En attente depuis 2 mois après la publication de la loi", substring = true))
            .assert(hasText("Publication annoncée pour octobre 2026 : cette date est passée", substring = true))
        onNodeWithTag(ApplicationTags.measure(3)).performScrollTo().assert(hasText("Publication annoncée pour mars 2027", substring = true))
        assertEquals(1, count("cette date est passée"), "only the month that is over")
        onNodeWithTag(ApplicationTags.measure(1)).performScrollTo().assert(hasText("Texte publié 40 jours après la loi", substring = true))
        onNodeWithTag(ApplicationTags.FIGURES).assert(hasText("Devenues sans objet : 1", substring = true))
        onNodeWithTag(ApplicationTags.LEGIFRANCE).assertExists()
        onNodeWithTag(ApplicationTags.DOSSIER).assertExists()
        listOf("Écrire", "Contacter", "Interpeller").forEach { assertEquals(0, count(it), it) }
    }

    private fun bill(stage: TextStage) = LegislativeText(
        Article(
            SignedEvent("a".repeat(64), "b".repeat(64), 1, 30023, listOf(listOf("d", "texte-logement"), listOf("ppf-official-id", "an-dossier:DLR5L17N90001")), "", "c".repeat(128)),
            ContentAddress(30023, "b".repeat(64), "texte-logement"),
            "[EXEMPLE] Projet de loi relatif au logement abordable",
            null,
            emptyList(),
            ArticleType.TEXTE,
            ContentOrigin.HUMAN,
            null,
        ),
        null, stage, emptyList(), null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
    )

    @Test
    fun theSheetOfAPromulgatedBillShowsTheApplicationOfItsLaw() = applicationTest {
        val promulgated = bill(TextStage.PROMULGATED)
        val tabled = bill(TextStage.TABLED)
        val (_, state) = show(published = true) { parliament ->
            BillApplication(tabled, parliament)
            BillApplication(promulgated, parliament)
        }
        state().close()
        waitForIdle()
        // Before any package: one block, for the promulgated bill only, that opens the « Applicomètre ».
        assertEquals(1, onAllNodesWithTag(ApplicationTags.BILL).fetchSemanticsNodes().size)
        onNodeWithTag(ApplicationTags.BILL_OPEN).performClick()
        waitForTag(ApplicationTags.PAGE)
        onNodeWithTag(ApplicationTags.download(17)).performClick()
        waitForTag(ApplicationTags.SEARCH)

        // Back on the sheets: the figures of the law, for the bill whose file it is.
        state().close()
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) { count("Mesures prises : 1 sur 4 (25 %)") > 0 }
        assertEquals(2, onAllNodesWithTag(ApplicationTags.BILL).fetchSemanticsNodes().size, "the package knows the file: both sheets show it")
        assertEquals(2, onAllNodesWithTag(ApplicationTags.BILL_OPEN).fetchSemanticsNodes().size)
    }
}
