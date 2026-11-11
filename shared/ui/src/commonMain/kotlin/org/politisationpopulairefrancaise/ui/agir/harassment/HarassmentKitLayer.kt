package org.politisationpopulairefrancaise.ui.agir.harassment

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.ui.agir.manifestation.HelpState
import org.politisationpopulairefrancaise.ui.components.SystemBack

/**
 * Where the kit against harassment (AG-59) is drawn: over the whole window while
 * [HelpState.harassmentOpen], the shell's [content] kept below (its drafts survive) but hidden from
 * the screen reader, as the lock screen hides it (M33). The kit is opened from the AIDE sheet;
 * its way back, and the system's, return to the screen below.
 */
@Composable
fun HarassmentKitLayer(help: HelpState, windowClass: WindowClass, content: @Composable () -> Unit) {
    val kit = help.harassment
    val open = kit != null && help.harassmentOpen
    Box(Modifier.fillMaxSize().then(if (open) Modifier.clearAndSetSemantics { } else Modifier)) {
        content()
    }
    if (kit != null && open) {
        SystemBack(enabled = true) { help.closeHarassmentKit() }
        Box(Modifier.fillMaxSize().testTag(HarassmentTags.HOST)) {
            HarassmentKitScreen(
                state = kit,
                windowClass = windowClass,
                onBack = help::closeHarassmentKit,
                modifier = Modifier.fillMaxSize(),
                camera = help.camera,
                guide = help::guide,
            )
        }
    }
}
