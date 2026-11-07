package org.politisationpopulairefrancaise.tools.opendata

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.InputStream
import java.nio.file.Path
import java.util.zip.ZipFile

/** The source data cannot be read as expected (exit code 4). Never quotes a value of the data. */
class UnreadableSourceException(message: String) : Exception(message)

/** A source could not be downloaded (exit code 2). */
class DownloadException(message: String) : Exception(message)

/** One file of a source: inside a zip, or among the example resources. */
class SourceEntry(val name: String, private val opener: () -> InputStream) {
    /** Reads the entry as JSON, refusing one bigger than [MAX_ENTRY_BYTES] (a zip bomb, a broken file). */
    fun json(): JsonElement = opener().use { stream ->
        val bytes = readBounded(stream, MAX_ENTRY_BYTES, name)
        try {
            lenientJson.parseToJsonElement(bytes.decodeToString())
        } catch (e: Exception) {
            throw UnreadableSourceException("$name: not a JSON document")
        }
    }

    fun open(): InputStream = opener()

    companion object {
        /** One ballot, one deputy, one legislative file: a few hundred kilobytes at most in the real files. */
        const val MAX_ENTRY_BYTES: Long = 16L * 1024 * 1024
    }
}

/** The files of a source, read one at a time (never all in memory). */
fun interface EntrySource {
    fun forEach(action: (SourceEntry) -> Unit)
}

/** The entries of a zip file on disk, in the order of the zip. */
class ZipEntries(private val file: Path) : EntrySource {
    override fun forEach(action: (SourceEntry) -> Unit) {
        val zip = try {
            ZipFile(file.toFile())
        } catch (e: Exception) {
            throw UnreadableSourceException("${file.fileName}: not a zip file")
        }
        zip.use {
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                // Entry names are only matched against fixed patterns, never used as paths on disk.
                action(SourceEntry(entry.name.replace('\\', '/')) { zip.getInputStream(entry) })
            }
        }
    }
}

/**
 * The example files bundled with the importer, under `example/<directory>/`, listed in
 * `example/files.txt` (the class path cannot be listed). [accept] lets the tests leave files out.
 */
class ResourceEntries(private val directory: String, private val accept: (String) -> Boolean = { true }) : EntrySource {
    override fun forEach(action: (SourceEntry) -> Unit) {
        ExampleFiles.list().filter { it.startsWith("$directory/") }.map { it.removePrefix("$directory/") }.filter(accept).forEach { name ->
            action(SourceEntry(name) { ExampleFiles.open("$directory/$name") })
        }
    }
}

internal object ExampleFiles {
    private const val ROOT = "/example/"

    fun list(): List<String> = open("files.txt").use { it.readBytes().decodeToString() }.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }

    fun open(name: String): InputStream =
        ExampleFiles::class.java.getResourceAsStream(ROOT + name) ?: throw UnreadableSourceException("example file missing: $name")

    fun exists(name: String): Boolean = ExampleFiles::class.java.getResource(ROOT + name) != null
}

internal val lenientJson: Json = Json { ignoreUnknownKeys = true; isLenient = false }

/** Reads at most [limit] bytes, failing beyond. */
internal fun readBounded(stream: InputStream, limit: Long, name: String): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val read = stream.read(buffer)
        if (read < 0) break
        total += read
        if (total > limit) throw UnreadableSourceException("$name: larger than $limit bytes")
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
