package org.politisationpopulairefrancaise.core.agir.map

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

internal actual fun platformGunzip(bytes: ByteArray, maxOutput: Int): ByteArray? = try {
    GZIPInputStream(ByteArrayInputStream(bytes)).use { input ->
        val out = ByteArrayOutputStream(bytes.size * 3)
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            if (out.size() > maxOutput) return null
        }
        out.toByteArray()
    }
} catch (e: java.io.IOException) {
    null
}

internal actual fun platformGzip(bytes: ByteArray): ByteArray? {
    val out = ByteArrayOutputStream(bytes.size / 2 + 64)
    GZIPOutputStream(out).use { it.write(bytes) }
    return out.toByteArray()
}
