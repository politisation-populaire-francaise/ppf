package org.politisationpopulairefrancaise.server.decider

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** DE-16, Q3: the official photos are served by PPF only, under their fingerprint, never a file that does not match it. */
class ComparatorPhotosRoutesTest {
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun aPhotoIsServedUnderItsFingerprintOnlyWhenTheBytesMatchIt() = testApplication {
        val directory = Files.createTempDirectory("ppf-photos")
        val photo = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())
        val good = sha256(photo)
        Files.write(directory.resolve("$good.jpg"), photo)
        // A file replaced by mistake under an old name is never served.
        val wrongName = sha256(byteArrayOf(9, 9, 9))
        Files.write(directory.resolve("$wrongName.jpg"), photo)
        application { routing { comparatorPhotosRoutes(ComparatorPhotosConfig(directory)) } }

        val response = client.get("/api/v1/decider/photos/$good")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContentEquals(photo, response.bodyAsBytes())
        assertEquals("public, max-age=31536000, immutable", response.headers[HttpHeaders.CacheControl])
        assertEquals("image/jpeg", response.headers[HttpHeaders.ContentType])
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/decider/photos/$wrongName").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/decider/photos/${"0".repeat(64)}").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/decider/photos/..%2F..%2Fsecret").status)
    }

    @Test
    fun withoutTheFolderThereIsNoRoute() = testApplication {
        application { routing { comparatorPhotosRoutes(ComparatorPhotosConfig(null)) } }
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/decider/photos/${"a".repeat(64)}").status)
    }
}
