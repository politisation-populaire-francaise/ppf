package org.politisationpopulairefrancaise.core.agir.map

/**
 * The areas and zoom levels of the map of the agenda (M1, AG-01).
 *
 * The background of the map comes in two parts, both made from OpenStreetMap data and both served
 * by PPF alone: a low zoom background bundled with the application ([BUNDLED_MAX_ZOOM] and below,
 * the whole world very roughly, mainland France, Corsica and the overseas departments in more
 * detail), always available without network; and later the detailed background of France served
 * by our own server ([SERVER_MIN_ZOOM] to [SERVER_MAX_ZOOM]), used only with a server, with
 * network and outside the data saving mode.
 */
object MapArea {
    /** Mainland France and Corsica, with a small margin. */
    val FRANCE: GeoBox = GeoBox(south = 41.25, west = -5.25, north = 51.2, east = 9.7)

    /** Where the camera looks first, and where "Recentrer sur la France" brings it back. */
    val FRANCE_CENTER: GeoPoint = GeoPoint(46.6, 2.4)

    /** The zoom of [FRANCE_CENTER] that shows the whole of mainland France on a phone. */
    const val FRANCE_ZOOM: Double = 4.6

    /** The overseas departments and regions, also in the bundled background. */
    val OVERSEAS: List<Pair<String, GeoBox>> = listOf(
        "guadeloupe" to GeoBox(15.75, -61.85, 16.55, -60.95),
        "martinique" to GeoBox(14.35, -61.25, 14.9, -60.8),
        "guyane" to GeoBox(2.1, -54.65, 5.85, -51.6),
        "la-reunion" to GeoBox(-21.42, 55.2, -20.85, 55.85),
        "mayotte" to GeoBox(-13.05, 44.95, -12.6, 45.35),
    )

    /** The bundled background covers the whole world up to this zoom (coasts and countries). */
    const val WORLD_MAX_ZOOM: Int = 4

    /** The bundled background stops at this zoom; the map still zooms further, with less detail. */
    const val BUNDLED_MAX_ZOOM: Int = 7

    /** The detailed background of our server starts here. */
    const val SERVER_MIN_ZOOM: Int = BUNDLED_MAX_ZOOM + 1

    /** The detailed background of our server stops here (streets); the map zooms two levels further. */
    const val SERVER_MAX_ZOOM: Int = 14

    /** The camera never goes below this zoom (the whole world). */
    const val MIN_ZOOM: Double = 1.5

    /** The deepest zoom without the server: the bundled background enlarged, enough to tell two towns apart. */
    const val MAX_ZOOM_BUNDLED: Double = 11.0

    /** The deepest zoom with the detailed background of the server. */
    const val MAX_ZOOM_SERVER: Double = 16.0

    /** The zoom of the camera when the user asks to see one mobilization. */
    const val POINT_ZOOM: Double = 10.0
}
