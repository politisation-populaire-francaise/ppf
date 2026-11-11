package org.politisationpopulairefrancaise.ui.agir.federer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.agir.federer.ConvinceKitReader
import org.politisationpopulairefrancaise.core.agir.proches.InMemoryProchesStorage
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.account.rememberAccountSession
import org.politisationpopulairefrancaise.ui.agir.AgirFixtures
import org.politisationpopulairefrancaise.ui.agir.AgirRubrics
import org.politisationpopulairefrancaise.ui.agir.AgirScreen
import org.politisationpopulairefrancaise.ui.agir.AgirTags
import org.politisationpopulairefrancaise.ui.agir.RecordingUriHandler
import org.politisationpopulairefrancaise.ui.agir.TEST_WINDOW_HEIGHT
import org.politisationpopulairefrancaise.ui.agir.awaitTag
import org.politisationpopulairefrancaise.ui.agir.count
import org.politisationpopulairefrancaise.ui.agir.manifestation.ManifestationTags
import org.politisationpopulairefrancaise.ui.agir.proches.LocalProchesStorage
import org.politisationpopulairefrancaise.ui.agir.proches.ProchesTags
import org.politisationpopulairefrancaise.ui.agir.proches.rememberProchesState
import org.politisationpopulairefrancaise.ui.content.ContentLibrary
import org.politisationpopulairefrancaise.ui.content.rememberContentLibrary
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** An [EXEMPLE] kit « Convaincre autour de soi », with the blocks the real one of « Comprendre » uses. */
private val KIT = """
    ## Cinq arguments courts

    1. **[EXEMPLE] Vous n'êtes pas seul.** Une phrase sourcée (source A).
    2. **[EXEMPLE] On peut agir sans manifester.** Une autre phrase (source B).

    ::: objections
    ? « [EXEMPLE] Manifester, ça ne sert à rien. »
    > Une réponse sourcée.
    :::

    ## Le monde bouge, en une carte

    - **[EXEMPLE] Pays A, 2024** : une loi retirée.

    ## Sources

    - Une source, consultée le 2026-11-11 : https://example.org/a
""".trimIndent()

/** "Agir" alone, as the shell gives it, with a clipboard that records what is copied. */
private class FedererHarness(withKit: Boolean) {
    val dependencies: AppDependencies =
        AppDependencies.withDatabase(InMemoryIdentityStore(), JvmDatabase.inMemory(), editorialTrust = AgirFixtures.trust)
    val copied = CopyOnWriteArrayList<String>()
    lateinit var library: ContentLibrary

    init {
        if (withKit) {
            val event = runBlocking {
                AgirFixtures.editor.sign(
                    Article.build(
                        author = AgirFixtures.editor.publicKey,
                        slug = ConvinceKitReader.SLUG,
                        title = "[EXEMPLE] Convaincre autour de soi",
                        summary = null,
                        topics = listOf("mobilisation"),
                        type = ArticleType.FICHE_PRATIQUE,
                        origin = ContentOrigin.AI_ASSISTED,
                        publishedAt = 1_794_355_200,
                        updatedAt = 1_794_355_200,
                        body = KIT,
                    ),
                )
            }
            dependencies.contentStore!!.store(event)
        }
    }

    @Composable
    fun Content(width: Int, dark: Boolean = false) {
        Box(Modifier.requiredSize(width.dp, TEST_WINDOW_HEIGHT.dp)) {
            PpfTheme(darkTheme = dark) {
                CompositionLocalProvider(
                    LocalUriHandler provides RecordingUriHandler(),
                    LocalProchesStorage provides InMemoryProchesStorage(null),
                    LocalFedererClipboard provides { text: String -> copied += text },
                ) {
                    library = rememberContentLibrary(dependencies)
                    val session = rememberAccountSession(dependencies.identityStore)
                    AgirScreen(
                        dependencies = dependencies,
                        library = library,
                        session = session,
                        windowClass = WindowClass.of(width.toFloat(), TEST_WINDOW_HEIGHT.toFloat()),
                        modifier = Modifier.fillMaxSize(),
                        proches = rememberProchesState(dependencies, session),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.openFederer(harness: FedererHarness, width: Int, dark: Boolean = false) {
    setContent { harness.Content(width, dark) }
    awaitTag(AgirTags.HOME)
    waitUntil(timeoutMillis = 60_000) { harness.library.guides.isNotEmpty() }
    click(AgirTags.RUBRIC + AgirRubrics.FEDERER.id)
    awaitTag(FedererTags.SCREEN)
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.click(tag: String) {
    onNodeWithTag(tag).performScrollTo().performClick()
    waitForIdle()
}

@OptIn(ExperimentalTestApi::class)
class FedererScreenTest {

    @Test
    fun theBundledKitOfComprendreGivesCardsAndTheListOfRelativesOpensAndLeadsBack() = runComposeUiTest {
        // The real kit « Convaincre autour de soi » is bundled with the application (Comprendre, main 044415a8).
        val harness = FedererHarness(withKit = false)
        openFederer(harness, 360)
        awaitTag(FedererTags.CARD + "argument-1")
        listOf("objection-1", "monde").forEach { id -> onNodeWithTag(FedererTags.CARD + id).performScrollTo() }
        assertEquals(0, count(FedererTags.KIT_MISSING))

        // The list of relatives opens from « Fédérer » and its way back returns here, not to the home.
        click(FedererTags.PROCHES)
        awaitTag(ProchesTags.SCREEN)
        click(ProchesTags.BACK)
        awaitTag(FedererTags.SCREEN)
        click(FedererTags.BACK)
        awaitTag(AgirTags.HOME)
    }

    @Test
    fun theCardsOfTheKitAreShownOnPhonesTabletsAndComputersAndCopiedWithTheirOrigin() {
        for (width in listOf(360, 700, 1000)) {
            val harness = FedererHarness(withKit = true)
            runComposeUiTest {
                openFederer(harness, width, dark = width == 700)
                waitUntil(timeoutMillis = 20_000) { harness.library.guides.any { it.slug == ConvinceKitReader.SLUG } }
                awaitTag(FedererTags.CARD + "argument-1")
                listOf("argument-2", "objection-1", "monde").forEach { id -> onNodeWithTag(FedererTags.CARD + id).performScrollTo() }
                assertEquals(0, count(FedererTags.KIT_MISSING))

                click(FedererTags.COPY + "objection-1")
                awaitTag(FedererTags.COPIED)
                val text = harness.copied.single()
                assertTrue(text.startsWith("« [EXEMPLE] Manifester, ça ne sert à rien. »\nUne réponse sourcée."), "at $width dp: $text")
                assertTrue(ConvinceKitReader.SHARE_FOOTER in text && text.endsWith("/comprendre/fiches/convaincre-autour-de-soi"), "at $width dp")

                // The whole guide, with its sources, is read inside "Agir"; back returns to the cards.
                click(FedererTags.KIT_OPEN)
                awaitTag(ManifestationTags.GUIDE)
                click(ManifestationTags.GUIDE_BACK)
                awaitTag(FedererTags.CARD + "argument-1")
            }
        }
    }
}
