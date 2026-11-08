package org.politisationpopulairefrancaise.core.agir.security

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopDeviceEraserTest {

    @Test
    fun everyTableIsEmptiedAndEveryOtherFileDeletedWhileTheDatabaseStaysOpen() = runBlocking {
        val directory = Files.createTempDirectory("ppf-erase")
        val file = directory.resolve("ppf.db")
        // The application's own database, open during the erase as in the real application.
        @Suppress("UNUSED_VARIABLE") val database = JvmDatabase.open(file)
        DriverManager.getConnection("jdbc:sqlite:$file").use { connection ->
            connection.createStatement().use { it.execute("CREATE TABLE IF NOT EXISTS EraseProbe(secret TEXT)") }
            connection.createStatement().use { it.execute("INSERT INTO EraseProbe VALUES ('[EXEMPLE] message privé')") }
        }
        Files.writeString(directory.resolve("identity.json"), "{\"secretKeyHex\":\"00\"}")
        Files.writeString(directory.resolve(FileDeviceSecurityStorage.FILE_NAME), "{}")
        Files.createDirectories(directory.resolve("medias")).also { Files.writeString(it.resolve("photo.bin"), "x") }
        var closed = false
        val eraser = DesktopDeviceEraser(directory, file, exit = { closed = true })

        eraser.eraseAll()

        assertFalse(Files.exists(directory.resolve("identity.json")))
        assertFalse(Files.exists(directory.resolve(FileDeviceSecurityStorage.FILE_NAME)))
        assertFalse(Files.exists(directory.resolve("medias")))
        DriverManager.getConnection("jdbc:sqlite:$file").use { connection ->
            val tables = connection.createStatement().use { s ->
                s.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'").use { r -> buildList { while (r.next()) add(r.getString(1)) } }
            }
            assertTrue(tables.size > 5, "the schema of the application is there: $tables")
            tables.forEach { table ->
                val rows = connection.createStatement().use { s -> s.executeQuery("SELECT count(*) FROM \"$table\"").use { r -> r.next(); r.getLong(1) } }
                assertEquals(0, rows, table)
            }
        }
        // The freed pages are gone from the file.
        assertFalse(String(Files.readAllBytes(file), Charsets.ISO_8859_1).contains("message priv"))
        assertFalse(closed)
        eraser.closeApplication()
        assertTrue(closed)
        Unit
    }

    @Test
    fun theSettingsFileIsReplacedInOneMove() {
        val directory = Files.createTempDirectory("ppf-security")
        val storage = FileDeviceSecurityStorage(directory.resolve(FileDeviceSecurityStorage.FILE_NAME))
        val lock = AppLock(storage, iterations = 10)
        lock.enable("471905", "471905")
        assertTrue(AppLock(storage, iterations = 10).isLocked)
        lock.forget()
        assertFalse(Files.exists(directory.resolve(FileDeviceSecurityStorage.FILE_NAME)))
    }
}
