package org.politisationpopulairefrancaise.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.symbol_description
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tag of the symbol of PPF, wherever it is shown. */
object SymbolTags {
    const val SYMBOL = "ppf-symbol"
}

/*
 * The symbol of PPF (design/identite, LISEZMOI, version 1.0 of 6 November 2026; D-185): a speech
 * bubble holding three silhouettes of growing size, a red dot on its upper right corner. Drawn here
 * from the same geometry as `design/identite/logo/symbole-*.svg` (a 100-unit design grid), so that it
 * stays sharp at every size and on every platform. Its colours are those of the theme: the brand
 * blue and the accent red, which are exactly the colours of the light and dark versions of the pack
 * (#2B3A8F and #C8213A on light, #9AA6F0 and #FF7083 on dark).
 */

/** The bubble with its three silhouettes cut out (even-odd). */
private const val BUBBLE = "M26,6 H74 A20,20 0 0 1 94,26 V60 A20,20 0 0 1 74,80 H37 L15.5,95 Q6,101.5 6,90 V26 A20,20 0 0 1 26,6 Z " +
    "M32,50 A6,6 0 0 1 38,56 V61 A3,3 0 0 1 35,64 H29 A3,3 0 0 1 26,61 V56 A6,6 0 0 1 32,50 Z " +
    "M26.5,41.5 A5.5,5.5 0 1 0 37.5,41.5 A5.5,5.5 0 1 0 26.5,41.5 Z " +
    "M50,42 A6,6 0 0 1 56,48 V61 A3,3 0 0 1 53,64 H47 A3,3 0 0 1 44,61 V48 A6,6 0 0 1 50,42 Z " +
    "M44.5,33.5 A5.5,5.5 0 1 0 55.5,33.5 A5.5,5.5 0 1 0 44.5,33.5 Z " +
    "M68,34 A6,6 0 0 1 74,40 V61 A3,3 0 0 1 71,64 H65 A3,3 0 0 1 62,61 V40 A6,6 0 0 1 68,34 Z " +
    "M62.5,25.5 A5.5,5.5 0 1 0 73.5,25.5 A5.5,5.5 0 1 0 62.5,25.5 Z"

/**
 * Everything but a circle of 14 around the dot: the gap between the bubble and the dot. The square
 * turns one way and the circle the other, so the circle is left out (non-zero rule).
 */
private const val GAP = "M-10,-10 H110 V110 H-10 Z M87,-1 A14,14 0 0 0 87,27 A14,14 0 0 0 87,-1 Z"

/** The red dot: what happens live. */
private const val DOT = "M78,13 A9,9 0 1 0 96,13 A9,9 0 1 0 78,13 Z"

/** The width and height of the frame of the pack (protection area included), in units of the grid. */
private const val FRAME_WIDTH = 91.6f
private const val FRAME_HEIGHT = 95.6f

/** The symbol in [bubble] and [dot] colours, in the frame of the pack (its protection area included). */
fun ppfSymbol(bubble: Color, dot: Color): ImageVector =
    ImageVector.Builder(name = "PpfSymbol", defaultWidth = FRAME_WIDTH.dp, defaultHeight = FRAME_HEIGHT.dp, viewportWidth = FRAME_WIDTH, viewportHeight = FRAME_HEIGHT)
        .apply {
            group(name = "frame", translationX = -5.2f, translationY = -3.2f) {
                group(name = "gap", clipPathData = addPathNodes(GAP)) {
                    addPath(pathData = addPathNodes(BUBBLE), pathFillType = PathFillType.EvenOdd, fill = SolidColor(bubble))
                }
                addPath(pathData = addPathNodes(DOT), fill = SolidColor(dot))
            }
        }
        .build()

/**
 * The symbol of PPF, in the colours of the current theme. [size] is its height; the LISEZMOI asks for
 * 16 pixels at least (below, it is not drawn smaller). Read by screen readers as the name of the
 * platform, unless [decorative] (when the name is written beside it).
 */
@Composable
fun PpfSymbol(size: Dp, modifier: Modifier = Modifier, decorative: Boolean = false) {
    val palette = PpfTheme.palette
    val vector = remember(palette.brand, palette.accent) { ppfSymbol(palette.brand, palette.accent) }
    val description = stringResource(Res.string.symbol_description)
    val height = maxOf(size, MIN_SYMBOL_SIZE)
    Image(
        painter = rememberVectorPainter(vector),
        contentDescription = null,
        modifier = modifier
            .size(width = height * (FRAME_WIDTH / FRAME_HEIGHT), height = height)
            .then(if (decorative) Modifier else Modifier.semantics { contentDescription = description; role = Role.Image })
            .testTag(SymbolTags.SYMBOL),
    )
}

/** The smallest size of the symbol (LISEZMOI, « Tailles minimales »). */
val MIN_SYMBOL_SIZE: Dp = 16.dp
