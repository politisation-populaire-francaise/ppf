package org.politisationpopulairefrancaise.core.agir.map

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaFixtures
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaQuery
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaTimes
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.agenda.MobilizationStatus
import org.politisationpopulairefrancaise.core.agir.agenda.MobilizationType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapPointsTest {
    private val fixtures = AgendaFixtures()
    private val now = AgendaTimes.NOW
    private val hour = AgendaTimes.HOUR

    private suspend fun event(
        id: String,
        geohash: String?,
        start: Long = AgendaTimes.SATURDAY_14H,
        type: MobilizationType = MobilizationType.RASSEMBLEMENT,
        status: MobilizationStatus = MobilizationStatus.PREVU,
        territory: String = "Villefictive",
    ): Mobilization = checkNotNull(
        Mobilization.from(
            fixtures.sign(
                Mobilization.build(
                    author = fixtures.keys.publicKey, identifier = id, createdAt = AgendaTimes.WRITTEN,
                    title = "[EXEMPLE] $id", start = start, end = start + 2 * hour, location = "Place fictive",
                    type = type, territory = territory, status = status, geohash = geohash,
                ),
            ),
        ),
    )

    @Test
    fun onlyTheMobilizationsWithAPositionArePointsAndTheOthersAreCounted() = runTest {
        val paris = event("paris", "u09wjb11y") // place de la République
        val sansLieu = event("sans-lieu", null)
        val marseille = event("marseille", "spey639hp", start = AgendaTimes.SATURDAY_14H + 24 * hour, type = MobilizationType.MANIFESTATION)
        val selection = AgendaMapPoints.of(listOf(paris, sansLieu, marseille), now)
        assertEquals(listOf("paris", "marseille"), selection.points.map { it.mobilization.identifier })
        assertEquals(listOf("sans-lieu"), selection.withoutPosition.map { it.identifier })
        val point = selection.points.first()
        assertEquals(paris.address.toString(), point.address)
        assertTrue(point.position.latitude in 48.86..48.88 && point.position.longitude in 2.35..2.37)
        assertTrue(point.position in MapArea.FRANCE)
    }

    @Test
    fun theMapShowsTheSameListAsTheAgendaWithItsFilters() = runTest {
        val all = listOf(
            event("greve", "u0nd9", type = MobilizationType.GREVE, territory = "Lille"),
            event("reunion", "u0ts2", start = AgendaTimes.SATURDAY_14H + hour, type = MobilizationType.REUNION, territory = "Strasbourg"),
            event("passe", "gbsuv", start = now - 30 * 24 * hour),
        )
        val query = AgendaQuery(types = setOf(MobilizationType.GREVE))
        val selection = AgendaMapPoints.of(query.apply(all, now), now)
        assertEquals(listOf("greve"), selection.points.map { it.mobilization.identifier })
        // The past one is not on the map of the coming mobilizations.
        assertEquals(listOf("greve", "reunion"), AgendaMapPoints.of(AgendaQuery().apply(all, now), now).points.map { it.mobilization.identifier })
    }

    @Test
    fun statesFollowTheStatusAndTheTime() = runTest {
        val cancelled = event("annule", "u09tv", status = MobilizationStatus.ANNULE)
        val postponed = event("reporte", "u09tv", status = MobilizationStatus.REPORTE)
        val ongoing = event("en-cours", "u09tv", start = now - hour)
        val planned = event("prevu", "u09tv")
        assertEquals(
            listOf(MapPointState.CANCELLED, MapPointState.POSTPONED, MapPointState.ONGOING, MapPointState.PLANNED),
            AgendaMapPoints.of(listOf(cancelled, postponed, ongoing, planned), now).points.map { it.state },
        )
    }

    @Test
    fun eventsOfOnePlaceAreGroupedAndTheBoundsHoldEveryPoint() = runTest {
        val a = event("a", "u09wjb11y")
        val b = event("b", "u09wjb161") // a few metres away
        val c = event("c", "spey639hp")
        val points = AgendaMapPoints.of(listOf(a, b, c), now).points
        assertEquals(listOf("a", "b"), AgendaMapPoints.samePlace(points, points[1]).map { it.mobilization.identifier })
        val box = assertNotNull(AgendaMapPoints.bounds(points))
        assertTrue(points.all { it.position in box })
        assertNull(AgendaMapPoints.bounds(emptyList()))
    }

    @Test
    fun theGeoJsonCarriesOnlyWhatTheMapDraws() = runTest {
        val a = event("a", "u09wjb11y")
        val b = event("b", "spey639hp", status = MobilizationStatus.ANNULE)
        val points = AgendaMapPoints.of(listOf(a, b), now).points
        val json = AgendaMapPoints.geoJson(points, selected = b.address.toString()) { "Libellé ${it.mobilization.identifier}" }
        val features = Json.parseToJsonElement(json).jsonObject["features"]!!.jsonArray
        assertEquals(2, features.size)
        val second = features[1].jsonObject
        val coordinates = second["geometry"]!!.jsonObject["coordinates"]!!.jsonArray.map { it.jsonPrimitive.content.toDouble() }
        assertTrue(coordinates[0] in 5.3..5.4 && coordinates[1] in 43.2..43.4, "longitude first, as GeoJSON wants")
        val properties = second["properties"]!!.jsonObject
        assertEquals(setOf("address", "label", "state", "selected", "order"), properties.keys)
        assertEquals("Libellé b", properties["label"]!!.jsonPrimitive.content)
        assertEquals("annule", properties["state"]!!.jsonPrimitive.content)
        assertEquals("true", properties["selected"]!!.jsonPrimitive.content)
        // The soonest point has the highest rank: it is drawn on top.
        assertTrue(features[0].jsonObject["properties"]!!.jsonObject["order"]!!.jsonPrimitive.content.toInt() > properties["order"]!!.jsonPrimitive.content.toInt())
        // Only the label written by the interface: neither the title, nor the place, nor the description.
        assertTrue("Description" !in json && "Place fictive" !in json && "[EXEMPLE]" !in json)
    }
}
