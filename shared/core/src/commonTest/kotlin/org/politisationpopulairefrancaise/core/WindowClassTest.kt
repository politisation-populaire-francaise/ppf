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
    fun spaceIdsAreStable() {
        assertEquals(Space.EN_DIRECT, Space.fromId("en-direct"))
        assertEquals(Space.MON_ESPACE, Space.fromId("mon-espace"))
        assertEquals(null, Space.fromId("inconnu"))
        assertEquals(9, Space.primary.size)
        assertEquals(Space.FIL, Space.default)
        assertEquals(listOf(Space.FIL, Space.EN_DIRECT, Space.CARTE, Space.MESSAGES, Space.REVENDICATIONS), Space.quickAccess)
    }
}
