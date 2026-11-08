package org.politisationpopulairefrancaise.server.decider

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.decider.ComparatorPhotos
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Where the official photos of the comparators are (`PPF_DECIDER_PHOTOS_DIR`, DE-16, Q3): files prepared by the team
 * (the same square framing, metadata removed), named by their SHA-256 and the extension of their format. Without
 * the variable, the route does not exist and the application shows no photo.
 */
class ComparatorPhotosConfig(val directory: Path?) {
    companion object {
        fun fromEnvironment(): ComparatorPhotosConfig =
            ComparatorPhotosConfig(System.getenv("PPF_DECIDER_PHOTOS_DIR")?.trim()?.takeIf { it.isNotEmpty() }?.let { Path.of(it).toAbsolutePath().normalize() })
    }
}

private val FORMATS = listOf("jpg" to ContentType.Image.JPEG, "png" to ContentType.Image.PNG, "webp" to ContentType("image", "webp"))

/**
 * `GET /api/v1/decider/photos/{sha256}`: the photo whose SHA-256 is its name, only when the bytes match it (a file
 * replaced by mistake is never served under the old name), [ComparatorPhotos.MAX_BYTES] at most. The name changes with
 * the content: a very long cache is safe. Nothing is logged (which photo is asked could tell which candidate is
 * looked at; the application asks for all of a sheet together anyway).
 */
fun Route.comparatorPhotosRoutes(config: ComparatorPhotosConfig) {
    val directory = config.directory ?: return
    get(ComparatorPhotos.PATH + "{sha256}") {
        val sha256 = call.parameters["sha256"].orEmpty()
        if (!ComparatorPhotos.isSha256(sha256)) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        val found = withContext(Dispatchers.IO) {
            FORMATS.firstNotNullOfOrNull { (extension, type) ->
                val file = directory.resolve("$sha256.$extension").normalize()
                if (!file.startsWith(directory) || !Files.isRegularFile(file) || Files.size(file) > ComparatorPhotos.MAX_BYTES) return@firstNotNullOfOrNull null
                val bytes = Files.readAllBytes(file)
                val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                if (digest == sha256) bytes to type else null
            }
        }
        if (found == null) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        call.response.header(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
        call.response.header("X-Content-Type-Options", "nosniff")
        call.respondBytes(found.first, found.second, HttpStatusCode.OK)
    }
}
