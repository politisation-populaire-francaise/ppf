package org.politisationpopulairefrancaise.ui.agir.proches

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.politisationpopulairefrancaise.core.agir.proches.ProchesStorage
import org.politisationpopulairefrancaise.core.identity.FileIdentityStore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * On a computer, the list of « Mes proches à mobiliser » (AG-63) is a small file of the user's
 * private application-data directory, next to the identity, the settings of the lock and the timer.
 */
@Composable
actual fun rememberPlatformProchesStorage(): ProchesStorage? =
    remember { FileProchesStorage(FileIdentityStore.defaultDirectory().resolve(FileProchesStorage.FILE_NAME)) }

/** A file holding the list, replaced in one move so that it is never half written. */
class FileProchesStorage(private val file: Path) : ProchesStorage {
    override fun load(): String? = if (Files.isRegularFile(file)) Files.readString(file) else null

    override fun save(value: String) {
        Files.createDirectories(file.toAbsolutePath().parent)
        val temporary = file.resolveSibling(file.fileName.toString() + ".tmp")
        Files.writeString(temporary, value)
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    override fun clear() {
        Files.deleteIfExists(file)
        Files.deleteIfExists(file.resolveSibling(file.fileName.toString() + ".tmp"))
    }

    companion object {
        const val FILE_NAME: String = "proches.json"
    }
}
