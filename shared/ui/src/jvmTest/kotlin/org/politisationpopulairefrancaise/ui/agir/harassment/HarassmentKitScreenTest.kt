package org.politisationpopulairefrancaise.ui.agir.harassment

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.agir.camera.InMemoryProtectedVault
import org.politisationpopulairefrancaise.core.agir.camera.ProtectedVault
import org.politisationpopulairefrancaise.ui.agir.TEST_WINDOW_HEIGHT
import org.politisationpopulairefrancaise.ui.agir.awaitTag
import org.politisationpopulairefrancaise.ui.agir.count
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A clipboard and no share sheet, as on a computer: what leaves the screen is recorded, nothing is sent. */
private class RecordingSharing : ReportSharing {
    val copied = CopyOnWriteArrayList<String>()
    override fun copy(text: String) {
        copied += text
    }
    override val share: ((String) -> Unit)? = null
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.show(vault: ProtectedVault?, sharing: ReportSharing, width: Int, dark: Boolean = false): () -> HarassmentKitState {
    var state: HarassmentKitState? = null
    setContent {
        Box(Modifier.requiredSize(width.dp, TEST_WINDOW_HEIGHT.dp)) {
            PpfTheme(darkTheme = dark) {
                val scope = rememberCoroutineScope()
                val kit = remember { HarassmentKitState(vault, scope, codec = null, io = Dispatchers.Default, clock = { NOW }) }
                state = kit
                HarassmentKitScreen(kit, WindowClass.of(width.toFloat(), TEST_WINDOW_HEIGHT.toFloat()), onBack = {}, sharing = sharing)
            }
        }
    }
    return { checkNotNull(state) }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.click(tag: String) {
    onNodeWithTag(tag).performScrollTo().performClick()
    waitForIdle()
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.type(field: String, text: String) {
    onNodeWithTag(HarassmentTags.FIELD + field).performScrollTo().performTextInput(text)
    waitForIdle()
}

/** 2026-11-11 at 12:00 in Paris. */
private const val NOW = 1_794_394_800L

/**
 * AG-59 « Kit contre le harcèlement »: a piece of evidence kept in the vault with its SHA-256, read back in the
 * PHAROS template that is copied (never sent), deleted only after a confirmation; without a vault (computer), the
 * screen says where evidence is kept and the templates stay.
 */
@OptIn(ExperimentalTestApi::class)
class HarassmentKitScreenTest {

    @Test
    fun evidenceIsKeptWithItsDigestFilledIntoATemplateCopiedAndDeletedAfterConfirmation() {
        for (width in listOf(360, 700, 1000)) {
            val sharing = RecordingSharing()
            val vault = InMemoryProtectedVault()
            runComposeUiTest {
                val state = show(vault, sharing, width, dark = width == 700)
                awaitTag(HarassmentTags.SCREEN)
                awaitTag(HarassmentTags.EMPTY)

                click(HarassmentTags.ADD)
                awaitTag(HarassmentTags.FORM)
                type(HarassmentTags.PLATFORM, "[EXEMPLE] Réseau social fictif")
                // The date and the hour of the day are filled in already (the moment the evidence is noted).
                assertTrue(state().draft.seenDate.isNotBlank() && state().draft.seenTime.isNotBlank(), "at $width dp")
                type(HarassmentTags.TEXT, "[EXEMPLE] Un message menaçant recopié mot pour mot.")
                click(HarassmentTags.KEEP)
                waitUntil(timeoutMillis = 20_000) { state().evidence.isNotEmpty() }
                val kept = state().evidence.single()
                awaitTag(HarassmentTags.ITEM + kept.id)
                assertTrue(kept.digest.length == 64, "a SHA-256 in hexadecimal at $width dp")

                // The PHAROS template carries the evidence and its digest; it is copied, PPF sends nothing.
                click(HarassmentTags.TAB + "pharos")
                awaitTag(HarassmentTags.PREVIEW)
                click(HarassmentTags.COPY)
                val text = sharing.copied.last()
                assertTrue("Réseau social fictif" in text && kept.digest in text, "at $width dp: $text")

                // Deleting asks first; « Annuler » keeps it.
                click(HarassmentTags.ITEM + kept.id)
                awaitTag(HarassmentTags.DETAIL)
                click(HarassmentTags.DELETE)
                click(HarassmentTags.DELETE_CANCEL)
                assertEquals(1, state().evidence.size)
                click(HarassmentTags.DELETE)
                click(HarassmentTags.DELETE_CONFIRM)
                waitUntil(timeoutMillis = 20_000) { state().evidence.isEmpty() }
                awaitTag(HarassmentTags.EMPTY)
            }
        }
    }

    @Test
    fun withoutAVaultTheScreenSaysWhereEvidenceIsKeptAndTheTemplatesStay() = runComposeUiTest {
        show(vault = null, sharing = RecordingSharing(), width = 360)
        awaitTag(HarassmentTags.SCREEN)
        awaitTag(HarassmentTags.UNAVAILABLE)
        assertEquals(0, count(HarassmentTags.ADD))
        onNodeWithTag(HarassmentTags.REPORTS).performScrollTo()
    }
}
