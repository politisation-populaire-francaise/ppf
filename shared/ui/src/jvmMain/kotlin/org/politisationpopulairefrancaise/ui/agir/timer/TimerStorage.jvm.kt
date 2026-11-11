package org.politisationpopulairefrancaise.ui.agir.timer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.politisationpopulairefrancaise.core.agir.timer.TimerStorage
import org.politisationpopulairefrancaise.core.identity.FileIdentityStore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * On a computer, the timer (AG-55) is a small file of the user's private application-data
 * directory, next to the identity and the settings of the lock.
 */
@Composable
actual fun rememberPlatformTimerStorage(): TimerStorage? =
    remember { FileTimerStorage(FileIdentityStore.defaultDirectory().resolve(FileTimerStorage.FILE_NAME)) }

/** A file holding the timer's data, replaced in one move so that it is never half written. */
class FileTimerStorage(private val file: Path) : TimerStorage {
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
        const val FILE_NAME: String = "minuteur.json"
    }
}
