package org.politisationpopulairefrancaise.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Two buttons side by side, each half of the width, when both labels fit whole in their half;
 * otherwise one above the other, each the whole width (a 360 dp phone, a large font). A label is
 * never cut. The first button comes first in both cases (left, or on top), for the reading order.
 */
@Composable
fun ButtonPair(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
) {
    Layout(contents = listOf(first, second), modifier = modifier) { (firstMeasurables, secondMeasurables), constraints ->
        val gap = spacing.roundToPx()
        val a = firstMeasurables.first()
        val b = secondMeasurables.first()
        val width = constraints.maxWidth
        val half = (width - gap) / 2
        val sideBySide = constraints.hasBoundedWidth &&
            a.maxIntrinsicWidth(constraints.maxHeight) <= half &&
            b.maxIntrinsicWidth(constraints.maxHeight) <= half
        if (sideBySide) {
            val each = Constraints(minWidth = half, maxWidth = half)
            val pa = a.measure(each)
            val pb = b.measure(each)
            val height = maxOf(pa.height, pb.height)
            layout(width, height) {
                pa.placeRelative(0, (height - pa.height) / 2)
                pb.placeRelative(half + gap, (height - pb.height) / 2)
            }
        } else {
            val full = if (constraints.hasBoundedWidth) Constraints(minWidth = width, maxWidth = width) else Constraints()
            val pa = a.measure(full)
            val pb = b.measure(full)
            val w = if (constraints.hasBoundedWidth) width else maxOf(pa.width, pb.width)
            layout(w, pa.height + gap + pb.height) {
                pa.placeRelative(0, 0)
                pb.placeRelative(0, pa.height + gap)
            }
        }
    }
}
