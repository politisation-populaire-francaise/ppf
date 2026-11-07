package org.politisationpopulairefrancaise.ui.agir.map

import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import org.politisationpopulairefrancaise.core.agir.map.ByteArrayRangeSource
import org.politisationpopulairefrancaise.core.agir.map.ManifestRangeSource
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.core.agir.map.MapResourceRouter
import org.politisationpopulairefrancaise.core.agir.map.PmtilesReader
import org.politisationpopulairefrancaise.ui.resources.Res

/**
 * The data of the map bundled with the application (Compose resources, prepared by
 * `tools/map-data`, see its README): the low zoom background of France and the glyphs of the
 * labels. Read on the first request of the map, never before (the agenda starts nothing before its
 * screen is shown), and kept in memory for the run of the application.
 */
object BundledMap {
    const val BASEMAP_FILE: String = "files/carte/fond-france.pmtiles"
    const val GLYPHS_FOLDER: String = "files/carte/glyphes"

    /** The background, or null when this build has none (the map then shows the sea colour only). */
    suspend fun basemap(): ByteArray? = read(BASEMAP_FILE)

    suspend fun glyphs(folder: String, range: String): ByteArray? = read("$GLYPHS_FOLDER/$folder/$range.pbf")

    private suspend fun read(path: String): ByteArray? = try {
        Res.readBytes(path)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private var shared: MapResourceRouter? = null
    private var sharedServer: String? = null

    /**
     * The router of the map engine, one for the whole run (the engine is configured once). With
     * [tilesUrl] (the tiles folder of our server, `https://<domaine>/tiles/`), the manifest
     * `current.json` names the current dated file, whose tiles are read when [serverAllowed] says
     * so; without (or without manifest), only the bundled background is used.
     */
    fun router(tilesUrl: String? = null, serverAllowed: () -> Boolean = { false }): MapResourceRouter {
        shared?.takeIf { sharedServer == tilesUrl }?.let { return it }
        val server = tilesUrl?.let { PmtilesReader(ManifestRangeSource(HttpClient(), it, ::currentEpochSeconds)) }
        return MapResourceRouter(
            bundled = { basemap()?.let(::ByteArrayRangeSource) },
            glyphs = ::glyphs,
            server = server,
            serverAllowed = serverAllowed,
        ).also {
            shared = it
            sharedServer = tilesUrl
        }
    }
}
