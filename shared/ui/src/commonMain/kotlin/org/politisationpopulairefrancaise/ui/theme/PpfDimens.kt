package org.politisationpopulairefrancaise.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The sizes of the shell and of the common components (design tokens, like [PpfPalette]): the
 * heights of the bars, the touch targets, the margins of the screens. Every screen takes its sizes
 * from here rather than from values written in place, so that one pass of density (« l'application
 * est trop grande », 11 November 2026) changes the whole application at once.
 *
 * Touch targets never go under 48 dp (WCAG 2.2, 2.5.8, and the Android accessibility guidelines).
 */
@Immutable
data class PpfDimens(
    /** The smallest side of anything that can be touched. */
    val touchTarget: Dp = 48.dp,
    /** The icon buttons of the bars and of the screens. */
    val iconButton: Dp = 48.dp,
    /** The icons of the bars and of the icon buttons. */
    val barIcon: Dp = 22.dp,
    /** The top bar of phones and tablets. */
    val topBarHeight: Dp = 56.dp,
    /** The top bar of wide windows. */
    val desktopTopBarHeight: Dp = 64.dp,
    /** The bottom bar of phones: an icon in its pill, then a label on one line. */
    val bottomBarHeight: Dp = 64.dp,
    /** The pill behind the icon of the selected entry of the bottom bar. */
    val bottomBarPillWidth: Dp = 56.dp,
    val bottomBarPillHeight: Dp = 28.dp,
    val bottomBarLabelSize: TextUnit = 12.sp,
    val bottomBarLabelLineHeight: TextUnit = 14.sp,
    /** The entries of the rail when the window is low (a phone in landscape). */
    val compactRailItemHeight: Dp = 48.dp,
    /** The side bar of wide windows. */
    val sideBarWidth: Dp = 280.dp,
    /** The wordmark of the top bars: the full name, on two or three lines (D-182). */
    val wordmarkSize: TextUnit = 14.sp,
    /** Its size when one word per line at [wordmarkSize] does not fit (small phone, large font). */
    val wordmarkSmallSize: TextUnit = 12.sp,
)

val LocalPpfDimens = staticCompositionLocalOf { PpfDimens() }
