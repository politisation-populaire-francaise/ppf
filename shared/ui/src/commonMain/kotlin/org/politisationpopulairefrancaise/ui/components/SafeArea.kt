package org.politisationpopulairefrancaise.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * The safe area of the window (D-180): what the system draws over an application that fills the
 * whole screen, as Android imposes from its version 15 (« bord à bord ») and as iOS always did.
 * On computers nothing is drawn over the window, and every value is zero.
 *
 * The rule of the interface: a background may reach the edge of the screen, a content never does.
 * The shell applies it to its bars and to the screens (see `ShellSafeArea.kt`); a screen has
 * nothing to do. Dialogs are windows of their own, kept inside the safe area by the platform.
 */
@Stable
class SafeArea(
    /** The status bar, the navigation bar (three buttons or gestures) and the display cutouts (notch, camera hole). */
    val bars: WindowInsets,
    /** The on-screen keyboard, while it is shown. */
    val keyboard: WindowInsets = WindowInsets(0, 0, 0, 0),
) {
    /** Everything the system draws over the window: [bars] and [keyboard], the larger of the two on each side. */
    val all: WindowInsets = bars.union(keyboard)

    /** What a bottom sheet keeps its content out of: the status bar above; the navigation bar and the keyboard below. */
    val sheet: WindowInsets = all.only(WindowInsetsSides.Vertical)

    companion object {
        /** The safe area of this window: the one the system reports, or the one a test injects. */
        val current: SafeArea
            @Composable get() {
                val injected = LocalSafeAreaOverride.current
                if (injected != null) return injected
                val systemBars = WindowInsets.systemBars
                val cutouts = WindowInsets.displayCutout
                val keyboard = WindowInsets.ime
                return remember(systemBars, cutouts, keyboard) { SafeArea(systemBars.union(cutouts), keyboard) }
            }
    }
}

/**
 * The entry point of the tests and of the screenshots: a fake safe area (status bar, navigation
 * bar, cutout, keyboard) instead of the system's. The applications never provide it.
 */
val LocalSafeAreaOverride = staticCompositionLocalOf<SafeArea?> { null }

/**
 * Keeps a content above the navigation bar, like `navigationBarsPadding` but on the [SafeArea]:
 * it adds nothing where an enclosing sheet, dialog or shell already keeps out of that bar.
 */
@Composable
fun Modifier.safeAreaBottomPadding(): Modifier = windowInsetsPadding(SafeArea.current.bars.only(WindowInsetsSides.Bottom))
