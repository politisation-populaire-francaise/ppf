package org.politisationpopulairefrancaise.server.urne

import kotlin.test.Test
import kotlin.test.assertEquals

/** D-156: the results of a box show from the first ballot, everywhere, unless an environment says otherwise. */
class UrneConfigDefaultsTest {
    @Test
    fun theResultsShowFromTheFirstBallotByDefault() {
        assertEquals(1, UrneConfig.fromEnvironment { null }.minDisplay)
        assertEquals(1, UrneConfig.fromEnvironment { name -> if (name == "PPF_ENVIRONMENT") "production" else null }.minDisplay)
        assertEquals(5, UrneConfig.fromEnvironment { name -> if (name == "PPF_VOTE_MIN_DISPLAY") "5" else null }.minDisplay)
    }
}
