package org.politisationpopulairefrancaise.core.agir.security

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

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
