package org.politisationpopulairefrancaise.ui.shell

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.DpRect
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.identity.RecoveryPhrase
import org.politisationpopulairefrancaise.core.identity.StoredIdentity
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.agir.manifestation.ManifestationTags
import org.politisationpopulairefrancaise.ui.notifications.NotificationCenterTags
import org.politisationpopulairefrancaise.ui.revendications.FilterPanelTags
import org.politisationpopulairefrancaise.ui.revendications.ManageViewsTags
import org.politisationpopulairefrancaise.ui.revendications.RevendicationTags
import org.politisationpopulairefrancaise.ui.revendications.propose.ProposalTags
import org.politisationpopulairefrancaise.ui.theme.LightPalette
import org.politisationpopulairefrancaise.ui.theme.PpfDimens
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The safe area of the shell (D-180), on the three layouts: with fake system bars (status bar,
 * three buttons or gesture bar, camera hole in landscape) and a fake keyboard, the background of
 * the bars reaches the edges of the window, their content and the screens stay in the safe area,
 * and no side is taken twice. Without system bars (a computer), nothing moves.
 */
@OptIn(ExperimentalTestApi::class)
class ShellSafeAreaTest {

    private val phones = listOf(
        FakeDevice.galaxy360,
        FakeDevice.galaxy384,
        FakeDevice.galaxy412Gestures,
        FakeDevice("statut-haut-gestes", width = 360, height = 800, statusBar = 48, navigationBar = 16),
    )

    /** The rail with every entry: windows from 600 dp wide and 480 dp high (tablets). */
    private val tablets = listOf(
        FakeDevice("tablette-portrait", width = 700, height = 1000, statusBar = 24, navigationBar = 48),
    )

    /** The short rail and the menu: phones on their side, less than 480 dp high, even wider than 840 dp. */
    private val lowWindows = listOf(
        FakeDevice.landscapeButtons,
        FakeDevice("paysage-360-gestes", width = 800, height = 360, statusBar = 24, navigationBar = 24, cutoutLeft = 36),
        FakeDevice.landscapeGestures,
    )

    /** The side bar: windows of 840 dp and more, at least 480 dp high. */
    private val wide = listOf(
        FakeDevice("tablette-paysage", width = 1280, height = 800, statusBar = 24, navigationBar = 48),
        FakeDevice("tablette-paysage-encoche", width = 1000, height = 740, statusBar = 32, navigationBar = 24, navigationBarRight = 48, cutoutLeft = 32),
    )

    private val dimens = PpfDimens()
    private val bottomBar = dimens.bottomBarHeight.value.toInt()

    /** The application in the window of [device], in the light theme whatever the theme of the computer that runs the test. */
    private fun onDevice(
        device: FakeDevice,
        dependencies: () -> AppDependencies = { AppDependencies.inMemory() },
        safeArea: Boolean = true,
        block: ComposeUiTest.() -> Unit,
    ) = runDesktopComposeUiTest(width = device.width, height = device.height) {
        val provided = dependencies()
        setContent { FakeDeviceWindow(device, safeArea = safeArea) { PpfTheme(darkTheme = false) { AdaptiveShell(provided) } } }
        waitForIdle()
        block()
    }

    /** The contents, the wall and the notification centre need the local database. */
    private fun withDatabase(): AppDependencies = AppDependencies.withDatabase(InMemoryIdentityStore(), JvmDatabase.inMemory())

    /** Sending a demand needs an account: the public test phrase of NIP-06, as in the tests of the form. */
    private fun withAccount(): AppDependencies {
        val phrase = RecoveryPhrase.parse("leader monkey parrot ring guide accident before fence cannon height naive bean")
        return AppDependencies(InMemoryIdentityStore(StoredIdentity.of(phrase.deriveKeys(), phrase)), contentStore = LocalEventStore(JvmDatabase.inMemory()))
    }

    private fun ComposeUiTest.bounds(tag: String): DpRect = onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun ComposeUiTest.settle() {
        waitForIdle()
        mainClock.advanceTimeBy(2_000)
        waitForIdle()
    }

    /** 30 s at most, as the other tests of the shell allow for a first composition on a loaded machine. */
    private fun ComposeUiTest.awaitTag(tag: String) =
        waitUntil(timeoutMillis = 30_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun assertDp(expected: Number, actual: Float, what: String) =
        assertTrue(abs(expected.toFloat() - actual) <= 0.6f, "$what: expected $expected dp, was $actual dp")

    /**
     * A button ends at [limit] or a few dp above it (a button drawn smaller than its touch target is
     * centred in it), never a whole system bar above: the margin was taken once.
     */
    private fun assertEndsAt(limit: Int, actual: Float, what: String) =
        assertTrue(actual <= limit + 0.6f && actual >= limit - 8, "$what: expected to end at $limit dp, ends at $actual dp")

    /** The whole of [rect] is inside the safe area of [device] (and above its keyboard when [aboveKeyboard]). */
    private fun assertInSafeArea(device: FakeDevice, rect: DpRect, what: String, aboveKeyboard: Boolean = false) {
        val bottom = if (aboveKeyboard) device.keyboardTop else device.safeBottom
        val inside = rect.left.value >= device.safeLeft - 0.6f && rect.top.value >= device.safeTop - 0.6f &&
            rect.right.value <= device.safeRight + 0.6f && rect.bottom.value <= bottom + 0.6f
        assertTrue(inside, "${device.name}, $what: $rect is outside the safe area (${device.safeLeft}, ${device.safeTop}, ${device.safeRight}, $bottom)")
    }

    /** The colour of the bars (surface0) is drawn at this point of the window. */
    private fun ComposeUiTest.assertBarBackground(device: FakeDevice, x: Int, y: Int, what: String) {
        val pixel: Color = onAllNodes(isRoot()).onFirst().captureToImage().toPixelMap()[x, y]
        assertEquals(LightPalette.surface0.toArgb(), pixel.toArgb(), "${device.name}, $what: the background of the bar does not reach ($x, $y)")
    }

    @Test
    fun phoneBarsReachTheEdgesAndKeepTheirContentInTheSafeArea() = phones.forEach { device ->
        onDevice(device) {
            onNodeWithTag(ShellTags.SHELL_COMPACT).assertExists()
            // The top bar: its 56 dp row starts right under the status bar, its background covers the status bar.
            for (tag in listOf(ShellTags.MENU_BUTTON, ManifestationTags.HELP_BUTTON, ShellTags.navItem(Space.MESSAGES), ShellTags.NOTIFICATIONS, ShellTags.navItem(Space.MON_ESPACE))) {
                val rect = bounds(tag)
                assertInSafeArea(device, rect, tag)
                assertTrue(rect.bottom.value <= device.statusBar + 56 + 0.6f, "${device.name}, $tag: below the 56 dp row of the top bar ($rect)")
            }
            assertDp(device.statusBar + 28, bounds(ShellTags.MENU_BUTTON).let { (it.top.value + it.bottom.value) / 2 }, "${device.name}, centre of the menu button")
            assertBarBackground(device, device.width / 2, 2, "status bar")

            // The bottom bar: its row ends right above the navigation bar, its background covers it.
            val bar = bounds(ShellTags.BOTTOM_BAR)
            assertDp(device.safeBottom - bottomBar, bar.top.value, "${device.name}, top of the bottom bar")
            assertDp(device.safeBottom, bar.bottom.value, "${device.name}, bottom of the bottom bar")
            for (space in Space.quickAccess) assertInSafeArea(device, bounds(ShellTags.navItem(space)), "entry ${space.id}")
            assertBarBackground(device, device.width / 2, device.height - 2, "navigation bar")

            // The screen: exactly between the two bars (one divider each), nothing taken twice.
            val content = bounds(ShellTags.CONTENT)
            assertDp(0, content.left.value, "${device.name}, left of the screen")
            assertDp(device.width, content.right.value, "${device.name}, right of the screen")
            assertDp(device.statusBar + 57, content.top.value, "${device.name}, top of the screen")
            assertDp(device.safeBottom - bottomBar - 1, content.bottom.value, "${device.name}, bottom of the screen")
        }
    }

    @Test
    fun phoneMenuStaysInTheSafeArea() = onDevice(FakeDevice.galaxy384) {
        val device = FakeDevice.galaxy384
        onNodeWithTag(ShellTags.MENU_BUTTON).performClick()
        settle()
        for (space in Space.more) assertInSafeArea(device, bounds(ShellTags.navItem(space)), "entry of the menu ${space.id}")
        val disclaimer = onNodeWithText("Plateforme citoyenne indépendante, sans lien avec l’État ni le gouvernement.").getUnclippedBoundsInRoot()
        assertDp(device.safeBottom - 12, disclaimer.bottom.value, "bottom of the disclaimer of the menu")
        // The sheet of the menu itself covers the whole height, status bar included.
        assertBarBackground(device, 40, 2, "menu under the status bar")
        assertBarBackground(device, 40, device.height - 2, "menu under the navigation bar")
    }

    @Test
    fun railLayoutKeepsBarsAndScreensInTheSafeArea() = tablets.forEach { device ->
        onDevice(device) {
            onNodeWithTag(ShellTags.SHELL_MEDIUM).assertExists()
            // The top bar, over the whole width: beside the camera hole and the side buttons, under the status bar.
            for (tag in listOf(ShellTags.NOTIFICATIONS, ShellTags.navItem(Space.MON_ESPACE))) {
                val rect = bounds(tag)
                assertInSafeArea(device, rect, tag)
                assertTrue(rect.bottom.value <= device.statusBar + 56 + 0.6f, "${device.name}, $tag: below the 56 dp row of the top bar ($rect)")
            }
            assertBarBackground(device, device.width / 2, 2, "status bar")
            assertBarBackground(device, 2, device.statusBar + 28, "top bar beside the camera hole")
            assertBarBackground(device, device.width - 2, device.statusBar + 28, "top bar beside the side buttons")

            // The rail: its background starts at the edge, its entries beside the camera hole; the last one can be
            // scrolled above the navigation bar.
            val rail = bounds(ShellTags.NAV_RAIL)
            assertDp(0, rail.left.value, "${device.name}, left of the rail")
            assertDp(device.statusBar + 57, rail.top.value, "${device.name}, top of the rail")
            // Sampled beside an entry, below the shadow of the help button.
            assertBarBackground(device, 2, device.statusBar + 57 + 150, "rail beside the camera hole")
            val help = bounds(ManifestationTags.HELP_BUTTON)
            assertInSafeArea(device, help, "help button of the rail")
            for (space in Space.quickAccess + Space.more) {
                val entry = bounds(ShellTags.navItem(space))
                assertTrue(entry.left.value >= device.safeLeft - 0.6f, "${device.name}, entry ${space.id}: under the camera hole ($entry)")
            }
            onNodeWithTag(ShellTags.NAV_RAIL).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
            waitForIdle()
            assertInSafeArea(device, bounds(ShellTags.navItem(Space.more.last())), "last entry of the rail, scrolled to its end")

            // The screen: beside the rail, under the top bar, out of the side buttons and of the navigation bar.
            val content = bounds(ShellTags.CONTENT)
            assertTrue(content.left.value >= rail.right.value, "${device.name}: the screen starts under the rail ($content, rail $rail)")
            assertDp(device.statusBar + 57, content.top.value, "${device.name}, top of the screen")
            assertDp(device.safeRight, content.right.value, "${device.name}, right of the screen")
            assertDp(device.safeBottom, content.bottom.value, "${device.name}, bottom of the screen")
        }
    }

    @Test
    fun lowWindowsShowTheFiveDailyEntriesWithoutScrolling() = lowWindows.forEach { device ->
        onDevice(device) {
            // A phone on its side, even wider than 840 dp: the top bar of phones and a short rail, never the side bar.
            onNodeWithTag(ShellTags.SHELL_MEDIUM).assertExists()
            onNodeWithTag(ShellTags.NAV_DRAWER).assertDoesNotExist()
            for (tag in listOf(ShellTags.MENU_BUTTON, ManifestationTags.HELP_BUTTON, ShellTags.navItem(Space.MESSAGES), ShellTags.NOTIFICATIONS, ShellTags.navItem(Space.MON_ESPACE))) {
                val rect = bounds(tag)
                assertInSafeArea(device, rect, tag)
                assertTrue(rect.bottom.value <= device.statusBar + 56 + 0.6f, "${device.name}, $tag: below the 56 dp row of the top bar ($rect)")
            }
            assertBarBackground(device, device.width / 2, 2, "status bar")
            assertBarBackground(device, 2, device.statusBar + 28, "top bar beside the camera hole")

            val rail = bounds(ShellTags.NAV_RAIL)
            assertDp(0, rail.left.value, "${device.name}, left of the rail")
            assertDp(device.statusBar + 57, rail.top.value, "${device.name}, top of the rail")
            // Each of the five entries whole, in the safe area, at least 48 dp high, without scrolling.
            for (space in Space.quickAccess) {
                val entry = bounds(ShellTags.navItem(space))
                assertInSafeArea(device, entry, "entry ${space.id} of the short rail")
                assertTrue(entry.bottom.value <= device.safeBottom + 0.6f, "${device.name}, entry ${space.id}: visible without scrolling ($entry)")
                assertTrue(entry.bottom.value - entry.top.value >= 48 - 0.6f, "${device.name}, entry ${space.id}: a 48 dp target ($entry)")
            }

            val content = bounds(ShellTags.CONTENT)
            assertTrue(content.left.value >= rail.right.value, "${device.name}: the screen starts under the rail ($content, rail $rail)")
            assertDp(device.statusBar + 57, content.top.value, "${device.name}, top of the screen")
            assertDp(device.safeRight, content.right.value, "${device.name}, right of the screen")
            assertDp(device.safeBottom, content.bottom.value, "${device.name}, bottom of the screen")

            // The rest is in the menu, beside the camera hole.
            onNodeWithTag(ShellTags.MENU_BUTTON).performClick()
            settle()
            for (space in Space.more) {
                val entry = bounds(ShellTags.navItem(space))
                assertTrue(entry.left.value >= device.safeLeft - 0.6f, "${device.name}, menu entry ${space.id}: under the camera hole ($entry)")
            }
        }
    }

    @Test
    fun sideBarLayoutKeepsBarsAndScreensInTheSafeArea() = wide.forEach { device ->
        onDevice(device) {
            onNodeWithTag(ShellTags.SHELL_EXPANDED).assertExists()
            for (tag in listOf(ShellTags.NOTIFICATIONS, ShellTags.navItem(Space.MON_ESPACE))) {
                val rect = bounds(tag)
                assertInSafeArea(device, rect, tag)
                assertTrue(rect.bottom.value <= device.statusBar + 64 + 0.6f, "${device.name}, $tag: below the 64 dp row of the top bar ($rect)")
            }
            assertBarBackground(device, device.width / 2, 2, "status bar")

            // The side bar: 280 dp from the edge, background included; its entries and its disclaimer in the safe area.
            val sideBar = bounds(ShellTags.NAV_DRAWER)
            assertDp(0, sideBar.left.value, "${device.name}, left of the side bar")
            assertDp(280, sideBar.right.value, "${device.name}, right of the side bar")
            assertDp(device.height, sideBar.bottom.value, "${device.name}, bottom of the side bar")
            assertBarBackground(device, 2, device.height - 2, "side bar under the navigation bar")
            assertInSafeArea(device, bounds(ManifestationTags.HELP_BUTTON), "help button of the side bar")
            for (space in Space.quickAccess + Space.more) {
                val entry = bounds(ShellTags.navItem(space))
                assertTrue(entry.left.value >= device.safeLeft - 0.6f, "${device.name}, entry ${space.id}: under the camera hole ($entry)")
            }
            // In a low window the entries scroll: the last one, scrolled to, is above the disclaimer.
            val last = ShellTags.navItem(Space.more.last())
            onNodeWithTag(last).performScrollTo()
            waitForIdle()
            assertInSafeArea(device, bounds(last), "last entry of the side bar, scrolled to")
            val disclaimer = onNodeWithText("Plateforme citoyenne indépendante, sans lien avec l’État ni le gouvernement.").getUnclippedBoundsInRoot()
            assertDp(device.safeBottom - 12, disclaimer.bottom.value, "${device.name}, bottom of the disclaimer")
            assertTrue(disclaimer.left.value >= device.safeLeft + 24 - 0.6f, "${device.name}: the disclaimer is under the camera hole ($disclaimer)")

            val content = bounds(ShellTags.CONTENT)
            assertDp(281, content.left.value, "${device.name}, left of the screen")
            assertDp(device.statusBar + 65, content.top.value, "${device.name}, top of the screen")
            assertDp(device.safeRight, content.right.value, "${device.name}, right of the screen")
            assertDp(device.safeBottom, content.bottom.value, "${device.name}, bottom of the screen")
        }
    }

    @Test
    fun keyboardShrinksTheScreenAndLeavesTheBarsInPlace() {
        // Phone: the screen ends at the top of the keyboard, the bottom bar stays under it, where it was.
        val phone = FakeDevice.galaxy384.copy(keyboard = 330)
        onDevice(phone) {
            val content = bounds(ShellTags.CONTENT)
            assertDp(phone.statusBar + 57, content.top.value, "top of the screen above the keyboard")
            assertDp(phone.keyboardTop, content.bottom.value, "bottom of the screen above the keyboard")
            assertDp(phone.safeBottom, bounds(ShellTags.BOTTOM_BAR).bottom.value, "bottom bar under the keyboard")
        }
        // Rail and side bar: the same, without a bottom bar.
        for (device in listOf(tablets.last().copy(keyboard = 380), wide.first().copy(keyboard = 340))) {
            onDevice(device) {
                assertDp(device.keyboardTop, bounds(ShellTags.CONTENT).bottom.value, "${device.name}, bottom of the screen above the keyboard")
            }
        }
    }

    @Test
    fun proposalFormStaysAboveTheKeyboard() {
        val device = FakeDevice.galaxy360.copy(keyboard = 320)
        onDevice(device, ::withAccount) {
            openSpace(Space.REVENDICATIONS)
            awaitTag(RevendicationTags.PROPOSE)
            onAllNodesWithTag(RevendicationTags.PROPOSE).onFirst().performScrollTo().performClick()
            awaitTag(ProposalTags.TITLE)
            assertInSafeArea(device, bounds(ProposalTags.SCREEN), "proposal form", aboveKeyboard = true)
            // The first field, then the send button once scrolled to: both between the top bar and the keyboard.
            for (tag in listOf(ProposalTags.TITLE, ProposalTags.SEND)) {
                onNodeWithTag(tag).performScrollTo()
                waitForIdle()
                val rect = bounds(tag)
                assertInSafeArea(device, rect, tag, aboveKeyboard = true)
                assertTrue(rect.top.value >= device.statusBar + 57 - 0.6f, "$tag: under the top bar ($rect)")
            }
        }
    }

    @Test
    fun filterSheetKeepsItsActionsAboveTheNavigationBarOnce() {
        // The two panels of the wall, in a bottom sheet on phones: their last row of actions ends one
        // padding (12 dp, then 8 dp) above the navigation bar, not two bars above it.
        val device = FakeDevice.galaxy384
        onDevice(device, ::withDatabase) {
            openSpace(Space.REVENDICATIONS)
            awaitTag(RevendicationTags.FILTERS)
            onNodeWithTag(RevendicationTags.FILTERS).performClick()
            settle()
            assertInSafeArea(device, bounds(FilterPanelTags.CLOSE), "close button of the filters")
            assertInSafeArea(device, bounds(FilterPanelTags.APPLY), "apply button of the filters")
            val lastRow = maxOf(bounds(FilterPanelTags.CLEAR).bottom.value, bounds(FilterPanelTags.SAVE_AS_VIEW).bottom.value)
            assertEndsAt(device.safeBottom - 12, lastRow, "actions of the filters")
            onNodeWithTag(FilterPanelTags.CLOSE).performClick()
            settle()

            onNodeWithTag(RevendicationTags.CUSTOMIZE).performClick()
            settle()
            assertInSafeArea(device, bounds(ManageViewsTags.CLOSE), "close button of the views")
            assertEndsAt(device.safeBottom - 8, bounds(ManageViewsTags.DONE).bottom.value, "actions of the views")
        }
        // With the keyboard (the field of the searched words): the actions rise above it.
        val typing = device.copy(keyboard = 330)
        onDevice(typing, ::withDatabase) {
            openSpace(Space.REVENDICATIONS)
            awaitTag(RevendicationTags.FILTERS)
            onNodeWithTag(RevendicationTags.FILTERS).performClick()
            settle()
            val lastRow = maxOf(bounds(FilterPanelTags.CLEAR).bottom.value, bounds(FilterPanelTags.SAVE_AS_VIEW).bottom.value)
            assertEndsAt(typing.keyboardTop - 12, lastRow, "actions of the filters above the keyboard")
            assertInSafeArea(typing, bounds(FilterPanelTags.CLOSE), "close button of the filters above the keyboard", aboveKeyboard = true)
        }
    }

    @Test
    fun notificationCentreOfPhonesStaysInTheSafeArea() {
        val device = FakeDevice.galaxy412Gestures
        onDevice(device, ::withDatabase) {
            onNodeWithTag(ShellTags.NOTIFICATIONS).performClick()
            awaitTag(NotificationCenterTags.BACK)
            // The centre takes the whole window, without the bars of the shell: it keeps out of the system bars itself.
            assertInSafeArea(device, bounds(NotificationCenterTags.BACK), "back button of the notification centre")
            val content = bounds(ShellTags.CONTENT)
            assertDp(device.statusBar, content.top.value, "top of the notification centre")
            assertDp(device.safeBottom, content.bottom.value, "bottom of the notification centre")
        }
    }

    @Test
    fun withoutSystemBarsNothingMoves() {
        // A computer, or any window the system draws nothing over: the layout of before D-180, to the dp.
        val phone = FakeDevice("sans-barres", width = 360, height = 700)
        onDevice(phone, safeArea = false) {
            val content = bounds(ShellTags.CONTENT)
            assertDp(57, content.top.value, "top of the screen")
            assertDp(700 - bottomBar - 1, content.bottom.value, "bottom of the screen")
            assertDp(700, bounds(ShellTags.BOTTOM_BAR).bottom.value, "bottom of the bottom bar")
            assertDp(28, bounds(ShellTags.MENU_BUTTON).let { (it.top.value + it.bottom.value) / 2 }, "centre of the menu button")
        }
        val tablet = FakeDevice("sans-barres", width = 800, height = 740)
        onDevice(tablet, safeArea = false) {
            val content = bounds(ShellTags.CONTENT)
            assertDp(57, content.top.value, "top of the screen beside the rail")
            assertDp(800, content.right.value, "right of the screen beside the rail")
            assertDp(740, content.bottom.value, "bottom of the screen beside the rail")
            assertDp(57, bounds(ShellTags.NAV_RAIL).top.value, "top of the rail")
        }
        val computer = FakeDevice("sans-barres", width = 1000, height = 740)
        onDevice(computer, safeArea = false) {
            val content = bounds(ShellTags.CONTENT)
            assertDp(281, content.left.value, "left of the screen beside the side bar")
            assertDp(65, content.top.value, "top of the screen beside the side bar")
            assertDp(1000, content.right.value, "right of the screen beside the side bar")
            assertDp(740, content.bottom.value, "bottom of the screen beside the side bar")
            assertDp(280, bounds(ShellTags.NAV_DRAWER).right.value, "right of the side bar")
        }
    }
}
