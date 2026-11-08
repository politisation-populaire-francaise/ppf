package org.politisationpopulairefrancaise.core.agir.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import kotlin.io.path.listDirectoryEntries
import kotlin.system.exitProcess

/**
 * The security settings on a computer: a small file of the user's private application-data
 * directory, next to the identity, replaced in one move so that it is never half written. No
 * pepper: a computer has no Keystore usable without a new dependency (noted in the threat model).
 */
class FileDeviceSecurityStorage(private val file: Path) : DeviceSecurityStorage {
    override fun load(): String? = if (Files.isRegularFile(file)) Files.readString(file) else null

    override fun save(value: String) {
        Files.createDirectories(file.toAbsolutePath().parent)
        val temporary = file.resolveSibling(file.fileName.toString() + ".tmp")
        Files.writeString(temporary, value)
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    override fun clear() {
        Files.deleteIfExists(file)
    }

    companion object {
        const val FILE_NAME: String = "securite-appareil.json"
    }
}

/**
 * The quick erase on a computer (S-44). The application keeps everything in [directory] (the
 * local database [database], the identity, the rally point, the security settings, the crash
 * report). The database stays open while the application runs (a Windows file cannot be deleted
 * then): every row of every table is deleted through a second connection, with `secure_delete` so
 * that SQLite overwrites the freed pages, then `VACUUM` rebuilds the file without them. Every other
 * file of [directory] is deleted. The application then closes.
 *
 * Limit, said in the threat model: on an SSD or a journaling file system, copies of old blocks may
 * survive at the hardware level; only the encryption of the disk (BitLocker, FileVault, LUKS) protects them.
 */
class DesktopDeviceEraser(
    private val directory: Path,
    private val database: Path,
    private val exit: () -> Unit = { exitProcess(0) },
) : DeviceEraser {

    override suspend fun eraseAll() {
        withContext(Dispatchers.IO) {
            if (Files.isRegularFile(database)) emptyDatabase(database)
            if (Files.isDirectory(directory)) {
                // The open database and its SQLite companions (emptied above, still in use by the application).
                val base = database.toAbsolutePath().normalize()
                val keep = listOf("", "-journal", "-wal", "-shm").map { base.resolveSibling(base.fileName.toString() + it) }.toSet()
                for (entry in directory.listDirectoryEntries()) {
                    if (entry.toAbsolutePath().normalize() in keep) continue
                    if (Files.isDirectory(entry)) {
                        check(entry.toFile().deleteRecursively()) { "could not erase $entry" }
                    } else {
                        Files.deleteIfExists(entry)
                    }
                }
            }
        }
    }

    override fun closeApplication() = exit()

    companion object {
        /** Deletes every row of every table of the SQLite file [file] and rebuilds it without the freed pages. */
        fun emptyDatabase(file: Path) {
            DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}").use { connection ->
                connection.createStatement().use { it.execute("PRAGMA secure_delete = ON") }
                val tables = connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT name, sql FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'").use { rows ->
                        buildList { while (rows.next()) add(rows.getString(1) to rows.getString(2).orEmpty()) }
                    }
                }
                // Full-text tables first: their shadow tables are emptied with them.
                val (virtual, plain) = tables.partition { it.second.trimStart().startsWith("CREATE VIRTUAL", ignoreCase = true) }
                (virtual + plain).forEach { (name, _) ->
                    runCatching { connection.createStatement().use { it.execute("DELETE FROM \"" + name.replace("\"", "\"\"") + "\"") } }
                }
                val left = (virtual + plain).sumOf { (name, _) ->
                    runCatching {
                        connection.createStatement().use { it.executeQuery("SELECT count(*) FROM \"" + name.replace("\"", "\"\"") + "\"").use { rows -> rows.next(); rows.getLong(1) } }
                    }.getOrDefault(0L)
                }
                connection.createStatement().use { it.execute("VACUUM") }
                check(left == 0L) { "$left rows could not be erased" }
            }
        }
    }
}
