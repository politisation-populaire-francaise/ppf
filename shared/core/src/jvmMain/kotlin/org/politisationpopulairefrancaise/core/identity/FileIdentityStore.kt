package org.politisationpopulairefrancaise.core.identity

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions

/**
 * Desktop store: a file in the user's private application-data directory, readable by this
 * user only (POSIX permissions where they exist; on Windows the profile directory is already
 * private to the user). The content is not yet encrypted with a password: this is noted in the
 * threat model and an optional unlock code (S-43) is planned before the public launch.
 */
class FileIdentityStore(private val path: Path) : IdentityStore {

    override suspend fun load(): StoredIdentity? = withContext(Dispatchers.IO) {
        if (!Files.exists(path)) null else StoredIdentity.decode(Files.readAllBytes(path))
    }

    override suspend fun save(identity: StoredIdentity) {
        withContext(Dispatchers.IO) {
            val directory = path.toAbsolutePath().parent
            Files.createDirectories(directory)
            restrictToOwner(directory, isDirectory = true)
            val temp = directory.resolve("${path.fileName}.tmp")
            Files.write(temp, identity.encode())
            restrictToOwner(temp, isDirectory = false)
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            Files.deleteIfExists(path)
        }
    }

    private fun restrictToOwner(target: Path, isDirectory: Boolean) {
        try {
            Files.setPosixFilePermissions(target, PosixFilePermissions.fromString(if (isDirectory) "rwx------" else "rw-------"))
        } catch (_: UnsupportedOperationException) {
            // Windows: no POSIX permissions; %APPDATA% is already restricted to the user's account.
        }
    }

    companion object {
        const val FILE_NAME: String = "identity.json"

        /** The store at the conventional location of the current operating system. */
        fun default(): FileIdentityStore = FileIdentityStore(defaultDirectory().resolve(FILE_NAME))

        fun defaultDirectory(): Path {
            val os = System.getProperty("os.name").orEmpty().lowercase()
            val home = Path.of(System.getProperty("user.home"))
            return when {
                os.contains("win") ->
                    (System.getenv("APPDATA")?.let(Path::of) ?: home.resolve("AppData").resolve("Roaming"))
                        .resolve("PolitisationPopulaireFrancaise")
                os.contains("mac") ->
                    home.resolve("Library").resolve("Application Support").resolve("PolitisationPopulaireFrancaise")
                else ->
                    (System.getenv("XDG_DATA_HOME")?.let(Path::of) ?: home.resolve(".local").resolve("share"))
                        .resolve("politisation-populaire-francaise")
            }
        }
    }
}
