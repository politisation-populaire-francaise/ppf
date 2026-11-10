package org.politisationpopulairefrancaise.ui.shell

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.politisationpopulairefrancaise.ui.components.SafeArea

/*
 * Where each part of the shell stands in the safe area (D-180). The backgrounds of the bars reach
 * the edges of the screen; their content and the screens stay out of the status bar, of the
 * navigation bar (three buttons or gestures) and of the display cutouts, in portrait and in
 * landscape; the screens also stay above the keyboard. Each side is taken once: what a bar took
 * is declared consumed for the screens, so that nothing inside adds it a second time.
 */

/** The top bars: their content under the status bar and beside the cutouts. Goes after the background, before the height. */
@Composable
internal fun Modifier.topBarSafeArea(): Modifier =
    windowInsetsPadding(SafeArea.current.bars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))

/** The bottom bar of phones: its entries above the navigation bar and beside the cutouts. */
@Composable
internal fun Modifier.bottomBarSafeArea(): Modifier =
    windowInsetsPadding(SafeArea.current.bars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))

/** The rail and the side bar, under a top bar: the start edge and the bottom are theirs. */
internal val sideNavigationInsets: WindowInsets
    @Composable get() = SafeArea.current.bars.only(WindowInsetsSides.Start + WindowInsetsSides.Bottom)

/** The same, for the side bar that is drawn here: goes after its background. */
@Composable
internal fun Modifier.sideNavigationSafeArea(): Modifier = windowInsetsPadding(sideNavigationInsets)

/** The menu of phones, which covers the whole height from the start edge. */
internal val menuInsets: WindowInsets
    @Composable get() = SafeArea.current.bars.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)

/** What the screens of phones keep out of on the sides the two bars do not cover (a cutout in landscape). */
internal val phoneContentInsets: WindowInsets
    @Composable get() = SafeArea.current.bars

/**
 * The screens of phones, between the two bars: [bars] is what the scaffold measured, each bar with
 * its share of the safe area. It is consumed, then the keyboard takes what it covers above the
 * bottom bar: a field and its send button stay visible, the bottom bar stays under the keyboard.
 */
@Composable
internal fun Modifier.betweenBarsSafeArea(bars: PaddingValues): Modifier =
    padding(bars).consumeWindowInsets(bars).windowInsetsPadding(SafeArea.current.keyboard)

/** The screens beside a rail or a side bar, under a top bar: the end edge, the bottom and the keyboard. */
@Composable
internal fun Modifier.besideNavigationSafeArea(): Modifier {
    val all = SafeArea.current.all
    return windowInsetsPadding(all.only(WindowInsetsSides.End + WindowInsetsSides.Bottom)).consumeWindowInsets(all)
}

/** A screen that takes the whole window of a phone, without the bars of the shell. */
@Composable
internal fun Modifier.fullWindowSafeArea(): Modifier = windowInsetsPadding(SafeArea.current.all)
