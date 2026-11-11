package org.politisationpopulairefrancaise.core

import kotlin.test.Test
import kotlin.test.assertEquals

class WindowClassTest {

    @Test
    fun phoneWidthIsCompact() {
        assertEquals(WindowWidthClass.COMPACT, WindowWidthClass.fromWidthDp(360f))
        assertEquals(WindowWidthClass.COMPACT, WindowWidthClass.fromWidthDp(599.9f))
    }

    @Test
    fun tabletWidthIsMedium() {
        assertEquals(WindowWidthClass.MEDIUM, WindowWidthClass.fromWidthDp(600f))
        assertEquals(WindowWidthClass.MEDIUM, WindowWidthClass.fromWidthDp(839.9f))
    }

    @Test
    fun desktopWidthIsExpanded() {
        assertEquals(WindowWidthClass.EXPANDED, WindowWidthClass.fromWidthDp(840f))
        assertEquals(WindowWidthClass.EXPANDED, WindowWidthClass.fromWidthDp(1920f))
    }

    @Test
    fun heightClasses() {
        assertEquals(WindowHeightClass.COMPACT, WindowHeightClass.fromHeightDp(400f))
        assertEquals(WindowHeightClass.MEDIUM, WindowHeightClass.fromHeightDp(800f))
        assertEquals(WindowHeightClass.EXPANDED, WindowHeightClass.fromHeightDp(1000f))
    }

    @Test
    fun landscapePhoneIsCompactHeightButNotCompactWidth() {
        val landscapePhone = WindowClass.of(widthDp = 780f, heightDp = 360f)
        assertEquals(WindowWidthClass.MEDIUM, landscapePhone.width)
        assertEquals(WindowHeightClass.COMPACT, landscapePhone.height)
    }

    @Test
    fun aWidePhoneInLandscapeGetsTheRailNotTheDesktopLayout() {
        // 384 and 412 dp phones turned in landscape are wider than 840 dp, with less than 480 dp of height.
        for ((width, height) in listOf(854f to 384f, 892f to 411f, 915f to 412f)) {
            val window = WindowClass.of(widthDp = width, heightDp = height)
            assertEquals(WindowWidthClass.MEDIUM, window.width, "$width x $height")
            assertEquals(WindowHeightClass.COMPACT, window.height, "$width x $height")
        }
        // A computer window keeps the desktop layout as soon as it is 480 dp high.
        assertEquals(WindowWidthClass.EXPANDED, WindowClass.of(widthDp = 1280f, heightDp = 480f).width)
        assertEquals(WindowWidthClass.EXPANDED, WindowClass.of(widthDp = 1280f, heightDp = 800f).width)
    }

    @Test
    fun spaceIdsAreStable() {
        assertEquals(Space.EN_DIRECT, Space.fromId("en-direct"))
        assertEquals(Space.MON_ESPACE, Space.fromId("mon-espace"))
        assertEquals(null, Space.fromId("inconnu"))
        assertEquals(Space.APPELS, Space.fromId("appels"))
        // "Actu" is only a label: the feed keeps its stable id (D-215).
        assertEquals(Space.FIL, Space.fromId("fil"))
    }

    @Test
    fun theBottomBarFollowsTheOrderOfTheOwner() {
        // D-215: « En direct doit apparaitre en premier », then Actu, the map in the centre, Agir, Appels.
        assertEquals(listOf(Space.EN_DIRECT, Space.FIL, Space.CARTE, Space.AGIR, Space.APPELS), Space.quickAccess)
        assertEquals(Space.EN_DIRECT, Space.default)
        assertEquals(Space.CARTE, Space.quickAccess[2])
    }

    @Test
    fun nothingAlreadyBuiltLeavesTheNavigation() {
        // D-210: every space stays reachable, from the bars, the menu, the top bar or the avatar.
        val reachable = Space.quickAccess + Space.more + Space.MESSAGES + Space.MON_ESPACE
        assertEquals(Space.entries.toSet(), reachable.toSet())
        assertEquals(reachable.size, reachable.toSet().size, "each space appears once")
        assertEquals(listOf(Space.VOIR, Space.COMPRENDRE, Space.DECIDER, Space.REVENDICATIONS), Space.more)
        assertEquals(Space.entries.size - 1, Space.primary.size, "every space but Mon espace has its page on the site")
        assertEquals(Space.quickAccess, Space.primary.take(5), "the site follows the order of the bottom bar")
    }
}
