package org.politisationpopulairefrancaise.ui.components

import androidx.compose.runtime.Composable
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

/**
 * The system's back (Android button or gesture, Escape on computers) while [enabled]: it closes what
 * is open (a page, a conversation, a form) before leaving the screen. The innermost enabled handler
 * wins, so a screen's handler runs before the shell's, which brings the user back to the feed.
 */
@Composable
fun SystemBack(enabled: Boolean, onBack: () -> Unit) {
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = enabled,
        onBackCompleted = onBack,
    )
}
