package org.politisationpopulairefrancaise.core.agir.map

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.agenda.MobilizationStatus

/** How a point is drawn: the state that changes its colour and its label, never the colour alone. */
enum class MapPointState(val tagValue: String) {
    PLANNED("prevu"),
    ONGOING("en-cours"),
    POSTPONED("reporte"),
    CANCELLED("annule"),
}

/**
 * One mobilization on the map: its address (`31923:<key>:<d>`, the key of the agenda), its
 * position read from its geohash (`g`, placed by its author: never computed by a third party),
 * and what the label shows.
 */
data class MapPoint(
    val address: String,
    val position: GeoPoint,
    /** The geohash as written, for grouping the events of one place. */
    val geohash: String,
    val state: MapPointState,
    val mobilization: Mobilization,
)

/**
 * Which mobilizations of the agenda go on the map, and in which order. The map shows exactly the
 * list of the agenda (same tab, same filters, computed on the device): the mobilizations that
 * have a position become points, the others are counted so that the screen can say how many are
 * only in the list. Nothing is sent anywhere.
 */
object AgendaMapPoints {
    /** The points of [shown] (in its order: soonest first), and the mobilizations without a position. */
    data class Selection(val points: List<MapPoint>, val withoutPosition: List<Mobilization>) {
        val isEmpty: Boolean get() = points.isEmpty()
    }

    /** Two events whose geohashes share this many characters (about 150 m) are at the same place. */
    const val SAME_PLACE_PRECISION: Int = 7

    fun of(shown: List<Mobilization>, now: Long): Selection {
        val points = ArrayList<MapPoint>()
        val without = ArrayList<Mobilization>()
        for (m in shown) {
            val hash = m.geohash
            val position = hash?.let(Geohash::decode)
            if (hash == null || position == null) {
                without += m
                continue
            }
            points += MapPoint(m.address.toString(), position, hash, stateOf(m, now), m)
        }
        return Selection(points, without)
    }

    fun stateOf(m: Mobilization, now: Long): MapPointState = when {
        m.status == MobilizationStatus.ANNULE -> MapPointState.CANCELLED
        m.status == MobilizationStatus.REPORTE -> MapPointState.POSTPONED
        m.isOngoing(now) -> MapPointState.ONGOING
        else -> MapPointState.PLANNED
    }

    /** The points at the same place as [point] (itself included), in the order of [points]. */
    fun samePlace(points: List<MapPoint>, point: MapPoint): List<MapPoint> {
        val key = point.geohash.take(SAME_PLACE_PRECISION)
        return points.filter { it.geohash.take(SAME_PLACE_PRECISION) == key }
    }

    /** The smallest box around [points]; null without point. */
    fun bounds(points: List<MapPoint>): GeoBox? {
        if (points.isEmpty()) return null
        return GeoBox(
            south = points.minOf { it.position.latitude },
            west = points.minOf { it.position.longitude },
            north = points.maxOf { it.position.latitude },
            east = points.maxOf { it.position.longitude },
        )
    }

    /** Property names of the GeoJSON features, read by the layers of the map. */
    const val PROPERTY_ADDRESS: String = "address"
    const val PROPERTY_LABEL: String = "label"
    const val PROPERTY_STATE: String = "state"
    const val PROPERTY_SELECTED: String = "selected"
    const val PROPERTY_ORDER: String = "order"

    /**
     * The GeoJSON `FeatureCollection` of [points] for the renderer: one point feature per
     * mobilization, with its address, its [label] (type and date, written by the interface), its
     * state, whether it is [selected], and its rank (the soonest is drawn on top). Nothing else of
     * the mobilization goes into the map.
     */
    fun geoJson(points: List<MapPoint>, selected: String?, label: (MapPoint) -> String): String {
        val features: JsonArray = buildJsonArray {
            points.forEachIndexed { i, p ->
                add(
                    buildJsonObject {
                        put("type", "Feature")
                        putJsonObject("geometry") {
                            put("type", "Point")
                            putJsonArray("coordinates") {
                                add(JsonPrimitive(round6(p.position.longitude)))
                                add(JsonPrimitive(round6(p.position.latitude)))
                            }
                        }
                        putJsonObject("properties") {
                            put(PROPERTY_ADDRESS, p.address)
                            put(PROPERTY_LABEL, label(p))
                            put(PROPERTY_STATE, p.state.tagValue)
                            put(PROPERTY_SELECTED, p.address == selected)
                            put(PROPERTY_ORDER, points.size - i)
                        }
                    },
                )
            }
        }
        return buildJsonObject {
            put("type", "FeatureCollection")
            put("features", features)
        }.toString()
    }

    private fun round6(value: Double): Double = kotlin.math.round(value * 1e6) / 1e6
}
