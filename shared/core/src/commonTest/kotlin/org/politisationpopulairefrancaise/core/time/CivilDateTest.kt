package org.politisationpopulairefrancaise.core.time

import kotlin.test.Test
import kotlin.test.assertEquals

class CivilDateTest {

    @Test
    fun convertsKnownInstants() {
        assertEquals(CivilDate(1970, 1, 1), CivilDate.fromEpochSeconds(0))
        assertEquals(CivilDate(1969, 12, 31), CivilDate.fromEpochSeconds(-1))
        assertEquals(CivilDate(2023, 11, 14), CivilDate.fromEpochSeconds(1_700_000_000))
        assertEquals(CivilDate(2026, 11, 5), CivilDate.fromEpochSeconds(1_793_880_000))
        assertEquals(CivilDate(2000, 2, 29), CivilDate.fromEpochSeconds(951_782_400))
        assertEquals(CivilDate(2027, 3, 1), CivilDate.fromEpochSeconds(1_803_859_200))
    }

    @Test
    fun formatsInFrench() {
        assertEquals("5 novembre 2026", CivilDate(2026, 11, 5).toFrench())
        assertEquals("1er janvier 2027", CivilDate(2027, 1, 1).toFrench())
        assertEquals("29 février 2000", CivilDate(2000, 2, 29).toFrench())
    }
}
