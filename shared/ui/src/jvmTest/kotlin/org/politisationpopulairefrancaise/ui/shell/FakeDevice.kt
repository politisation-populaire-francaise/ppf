package org.politisationpopulairefrancaise.ui.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.ui.components.LocalSafeAreaOverride
import org.politisationpopulairefrancaise.ui.components.SafeArea
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/**
 * The window of a phone or a tablet with its system bars, for the tests and the screenshots of the
 * safe area (D-180). Sizes in dp: the status bar at the top; the navigation bar at the bottom (48
 * for three buttons, 16 to 24 for the gesture bar) or on the right side, where phones put their
 * three buttons in landscape; a camera hole or a notch on the left side in landscape; the keyboard.
 */
internal data class FakeDevice(
    val name: String,
    val width: Int,
    val height: Int,
    val statusBar: Int = 0,
    val navigationBar: Int = 0,
    val navigationBarRight: Int = 0,
    val cutoutLeft: Int = 0,
    val keyboard: Int = 0,
) {
    /** What the system would report for this window. */
    val safeArea: SafeArea
        get() = SafeArea(
            bars = WindowInsets(left = cutoutLeft.dp, top = statusBar.dp, right = navigationBarRight.dp, bottom = navigationBar.dp),
            keyboard = WindowInsets(bottom = keyboard.dp),
        )

    /** The edges of the safe area, in dp from the top left corner of the window. */
    val safeLeft: Int get() = cutoutLeft
    val safeTop: Int get() = statusBar
    val safeRight: Int get() = width - navigationBarRight
    val safeBottom: Int get() = height - navigationBar

    /** The top of the keyboard, or the bottom of the safe area while it is hidden. */
    val keyboardTop: Int get() = if (keyboard > 0) height - keyboard else safeBottom

    companion object {
        /** The Galaxy phones the owner's testers have, at their usual display size: 20:9 screens, three buttons by default. */
        val galaxy360 = FakeDevice("360", width = 360, height = 800, statusBar = 24, navigationBar = 48)
        val galaxy384 = FakeDevice("384", width = 384, height = 853, statusBar = 32, navigationBar = 48)
        val galaxy412Gestures = FakeDevice("412", width = 412, height = 915, statusBar = 36, navigationBar = 20)

        /** The 360 dp phone on its side (the rail): the camera hole on the left, the three buttons on the right. */
        val landscapeButtons = FakeDevice("paysage-360-boutons", width = 800, height = 360, statusBar = 24, navigationBarRight = 48, cutoutLeft = 40)

        /** The 412 dp phone on its side (wide enough for the side bar), with the gesture bar at the bottom. */
        val landscapeGestures = FakeDevice("paysage-412-gestes", width = 915, height = 412, statusBar = 24, navigationBar = 24, cutoutLeft = 36)
    }
}

/**
 * Shows [content] in the window of [device]. With [safeArea], the application is told the fake
 * system bars through [LocalSafeAreaOverride], as a phone would tell it; without, it believes that
 * nothing covers the window (a computer, or a phone before D-180). [drawBars] draws the bars and
 * the keyboard over the content, for the screenshots.
 */
@Composable
internal fun FakeDeviceWindow(
    device: FakeDevice,
    safeArea: Boolean = true,
    drawBars: Boolean = false,
    dark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val reported = remember(device, safeArea) { if (safeArea) device.safeArea else null }
    Box(Modifier.requiredSize(device.width.dp, device.height.dp)) {
        CompositionLocalProvider(LocalSafeAreaOverride provides reported) { content() }
        if (drawBars) PpfTheme(darkTheme = dark) { FakeSystemBars(device) }
    }
}

/**
 * The system's own drawings, simplified: the clock and the status icons, the three buttons or the
 * gesture bar, the camera hole and the keyboard. Each band is tinted so that a content lost under
 * it shows on a capture; a real phone leaves the bars transparent.
 */
@Composable
private fun FakeSystemBars(device: FakeDevice) {
    val ink = PpfTheme.palette.ink
    val tint = ink.copy(alpha = 0.16f)
    Box(Modifier.fillMaxSize().clearAndSetSemantics { }) {
        if (device.statusBar > 0) {
            Row(
                modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().height(device.statusBar.dp).background(tint).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("12:00", style = MaterialTheme.typography.labelMedium, color = ink)
                Spacer(Modifier.weight(1f))
                repeat(3) {
                    Box(Modifier.padding(start = 6.dp).size(width = 14.dp, height = 10.dp).background(ink, MaterialTheme.shapes.extraSmall))
                }
            }
        }
        if (device.cutoutLeft > 0) {
            Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(device.cutoutLeft.dp).background(tint), contentAlignment = Alignment.Center) {
                Box(Modifier.size(16.dp).background(ink, CircleShape))
            }
        }
        if (device.keyboard > 0) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(device.keyboard.dp).background(PpfTheme.palette.border), contentAlignment = Alignment.Center) {
                Text("Clavier", style = MaterialTheme.typography.titleMedium, color = ink)
            }
        }
        if (device.navigationBar > 0) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(device.navigationBar.dp).background(tint), contentAlignment = Alignment.Center) {
                if (device.navigationBar >= 40) ThreeButtons(ink, vertical = false) else Box(Modifier.size(width = 108.dp, height = 4.dp).background(ink, CircleShape))
            }
        }
        if (device.navigationBarRight > 0) {
            Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(device.navigationBarRight.dp).background(tint), contentAlignment = Alignment.Center) {
                ThreeButtons(ink, vertical = true)
            }
        }
    }
}

/** Recent applications, home, back: the three buttons of Samsung phones, drawn as outlines. */
@Composable
private fun ThreeButtons(ink: Color, vertical: Boolean) {
    Canvas(if (vertical) Modifier.size(width = 24.dp, height = 200.dp) else Modifier.size(width = 200.dp, height = 24.dp)) {
        val stroke = Stroke(width = 2.dp.toPx())
        val mark = 14.dp.toPx()
        val along = if (vertical) size.height else size.width
        val across = if (vertical) size.width else size.height
        fun centre(step: Int): Offset {
            val position = along * (0.14f + 0.36f * step)
            return if (vertical) Offset(across / 2, position) else Offset(position, across / 2)
        }
        // Recent applications: three bars.
        centre(0).let { c ->
            for (shift in -1..1) {
                val x = c.x + shift * mark / 3
                drawLine(ink, Offset(x, c.y - mark / 2), Offset(x, c.y + mark / 2), strokeWidth = stroke.width)
            }
        }
        // Home: a rounded square.
        centre(1).let { c ->
            drawRoundRect(ink, topLeft = Offset(c.x - mark / 2, c.y - mark / 2), size = Size(mark, mark), cornerRadius = CornerRadius(mark / 4), style = stroke)
        }
        // Back: a chevron.
        centre(2).let { c ->
            drawLine(ink, Offset(c.x + mark / 4, c.y - mark / 2), Offset(c.x - mark / 4, c.y), strokeWidth = stroke.width)
            drawLine(ink, Offset(c.x - mark / 4, c.y), Offset(c.x + mark / 4, c.y + mark / 2), strokeWidth = stroke.width)
        }
    }
}
