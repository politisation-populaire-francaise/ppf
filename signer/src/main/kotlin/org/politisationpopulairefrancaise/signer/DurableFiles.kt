package org.politisationpopulairefrancaise.signer

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

/**
 * The files of the service, readable by its own user only, and writes that survive a crash.
 *
 * On POSIX file systems (the container) directories are `rwx------` (a directory needs its `x` bit to be
 * entered, also by its owner when the service runs as an unprivileged user with every capability dropped)
 * and files `rw-------`. Both are created with these permissions from the start (never briefly readable by
 * others, whatever the umask, which can only remove bits), then set exactly. On Windows (development) the
 * permissions of the user's profile apply.
 */
internal object DurableFiles {
    val PRIVATE_DIRECTORY: Set<PosixFilePermission> = PosixFilePermissions.fromString("rwx------")
    val PRIVATE_FILE: Set<PosixFilePermission> = PosixFilePermissions.fromString("rw-------")

    fun isPosix(path: Path): Boolean = "posix" in path.fileSystem.supportedFileAttributeViews()

    /** Creates [dir] and its missing parents as private directories; makes an existing [dir] private. */
    fun privateDirectory(dir: Path) {
        val absolute = dir.toAbsolutePath()
        if (!isPosix(absolute)) {
            Files.createDirectories(absolute)
            return
        }
        val missing = generateSequence(absolute) { it.parent }.takeWhile { !Files.exists(it) }.toList().asReversed()
        for (component in missing) {
            try {
                Files.createDirectory(component, PosixFilePermissions.asFileAttribute(PRIVATE_DIRECTORY))
            } catch (e: FileAlreadyExistsException) {
                // Created meanwhile: its permissions are set below for the last component.
            }
        }
        setExactly(absolute, PRIVATE_DIRECTORY)
    }

    /**
     * Writes [bytes] to [target]: to a temporary file created private, flushed to disk, renamed over the
     * target, then the directory itself flushed (where the JDK allows it) so that the rename survives a crash.
     */
    fun writeAtomically(target: Path, bytes: ByteArray) {
        val temporary = target.resolveSibling("${target.fileName}.tmp")
        Files.deleteIfExists(temporary)
        openPrivate(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { channel ->
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
        }
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        syncDirectory(target.toAbsolutePath().parent)
    }

    /** Opens [file] with [options], creating it private when it does not exist yet. */
    fun openPrivate(file: Path, vararg options: OpenOption): FileChannel {
        val set = options.toSet()
        val channel = if (isPosix(file)) {
            FileChannel.open(file, set, PosixFilePermissions.asFileAttribute(PRIVATE_FILE))
        } else {
            FileChannel.open(file, set)
        }
        try {
            setExactly(file, PRIVATE_FILE)
        } catch (e: IOException) {
            channel.close()
            throw e
        }
        return channel
    }

    /** Flushes the entries of [dir] to disk; silently skipped where the platform cannot open a directory (Windows). */
    fun syncDirectory(dir: Path?) {
        if (dir == null) return
        try {
            FileChannel.open(dir, StandardOpenOption.READ).use { it.force(true) }
        } catch (e: IOException) {
            // Windows refuses to open a directory as a channel.
        } catch (e: UnsupportedOperationException) {
            // Idem on some file systems.
        }
    }

    /** Sets [permissions] on [path] when they differ (a correctly prepared volume is never touched). */
    private fun setExactly(path: Path, permissions: Set<PosixFilePermission>) {
        if (!isPosix(path)) return
        if (Files.getPosixFilePermissions(path) != permissions) Files.setPosixFilePermissions(path, permissions)
    }
}
