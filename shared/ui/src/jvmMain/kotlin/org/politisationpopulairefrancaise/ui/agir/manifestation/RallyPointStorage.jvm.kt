package org.politisationpopulairefrancaise.ui.agir.manifestation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.politisationpopulairefrancaise.core.agir.manifestation.RallyPointStorage
import org.politisationpopulairefrancaise.core.identity.FileIdentityStore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * On a computer, the rally point is a small file of the user's private application-data directory,
 * next to the local database and the identity.
 */
@Composable
actual fun rememberPlatformRallyPointStorage(): RallyPointStorage? =
    remember { FileRallyPointStorage(FileIdentityStore.defaultDirectory().resolve(FileRallyPointStorage.FILE_NAME)) }

/** A file holding the encoded rally point, replaced in one move so that it is never half written. */
class FileRallyPointStorage(private val file: Path) : RallyPointStorage {
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
        const val FILE_NAME: String = "point-de-ralliement.json"
    }
}
