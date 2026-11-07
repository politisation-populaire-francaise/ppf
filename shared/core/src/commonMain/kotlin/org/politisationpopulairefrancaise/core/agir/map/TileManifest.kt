package org.politisationpopulairefrancaise.core.agir.map

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The manifest of the detailed background on our server, `/tiles/current.json` (short cache):
 * which dated file is current (`france-20261107.pmtiles`, served with a very long cache, never
 * rewritten), its attribution, its date, its size and its SHA-256. The application reads it first,
 * then asks the tiles of that file by range. The SHA-256 is for the people who install the file
 * (`ppf-tiles install`, infrastructure); the application does not check it, since it never holds
 * the whole file, only ranges (each tile is checked by its length and its decompression).
 */
@Serializable
data class TileManifest(
    val version: Int,
    val file: String,
    val attribution: String,
    val generated: String,
    val sha256: String? = null,
    val bytes: Long? = null,
) {
    /** True for a manifest this application understands, naming a file of the tiles folder. */
    val isValid: Boolean
        get() = version == VERSION && FILE_NAME.matches(file) &&
            (sha256 == null || SHA256.matches(sha256)) && (bytes == null || bytes > 0)

    companion object {
        const val VERSION: Int = 1
        const val FILE: String = "current.json"

        /** The dated files of the tiles folder; the same rule as the development route. */
        val FILE_NAME: Regex = Regex("^[a-z0-9][a-z0-9-]{0,62}\\.pmtiles$")
        private val SHA256 = Regex("^[0-9a-f]{64}$")
        private val json = Json { ignoreUnknownKeys = true }

        /** The manifest, or null when [text] is not a valid one (the bundled background is then used alone). */
        fun parse(text: String): TileManifest? =
            runCatching { json.decodeFromString(serializer(), text) }.getOrNull()?.takeIf { it.isValid }
    }
}

/**
 * The detailed background read through the manifest: [base] is the tiles folder of our server
 * (`https://<domaine>/tiles/`). The manifest is read on the first range asked for and kept for the
 * run of the application (the reader above keeps the directories of that one file: a new dated
 * file is taken at the next start); without a valid manifest (no network, no file yet) every read
 * answers null, so the map shows the bundled background alone, and it is asked again at most once
 * a minute.
 */
class ManifestRangeSource(
    private val client: HttpClient,
    base: String,
    private val clock: () -> Long,
) : RangeSource {
    private val base = if (base.endsWith('/')) base else "$base/"
    private val lock = Mutex()
    private var current: Pair<TileManifest, HttpRangeSource>? = null
    private var checkedAt: Long = Long.MIN_VALUE

    init {
        require(this.base.startsWith("https://") || this.base.startsWith("http://localhost") || this.base.startsWith("http://127.0.0.1")) {
            "the tiles are read from our server over HTTPS (plain HTTP only on this computer)"
        }
    }

    /** The manifest in use, read if needed; null without a valid one. */
    suspend fun manifest(): TileManifest? = resolve()?.first

    override suspend fun read(offset: Long, length: Int): ByteArray? = resolve()?.second?.read(offset, length)

    private suspend fun resolve(): Pair<TileManifest, HttpRangeSource>? = lock.withLock {
        current?.let { return@withLock it }
        val now = clock()
        if (checkedAt != Long.MIN_VALUE && now - checkedAt < RETRY_SECONDS) return@withLock null
        checkedAt = now
        val manifest = fetchManifest() ?: return@withLock null
        (manifest to HttpRangeSource(client, base + manifest.file)).also { current = it }
    }

    private suspend fun fetchManifest(): TileManifest? = try {
        client.prepareGet(base + TileManifest.FILE).execute { response ->
            if (response.status.value != 200) null
            else response.readRawBytes().takeIf { it.size <= MAX_MANIFEST }?.decodeToString()?.let(TileManifest::parse)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    companion object {
        /** Without a manifest, it is asked again at most once a minute. */
        const val RETRY_SECONDS: Long = 60
        const val MAX_MANIFEST: Int = 4_096
    }
}
