package org.politisationpopulairefrancaise.signer

import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DurableFilesTest {
    @Test
    fun directoriesCanBeEnteredByTheirOwnerOnlyAndFilesReadByTheirOwnerOnly() {
        assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE), DurableFiles.PRIVATE_DIRECTORY)
        assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), DurableFiles.PRIVATE_FILE)
        assertEquals("rwx------", PosixFilePermissions.toString(DurableFiles.PRIVATE_DIRECTORY))
        assertEquals("rw-------", PosixFilePermissions.toString(DurableFiles.PRIVATE_FILE))
    }

    @Test
    fun aWriteReplacesTheTargetAndLeavesNoTemporaryFile() {
        val dir = Files.createTempDirectory("ppf-durable")
        val target = dir.resolve("sub/fichier.json")
        DurableFiles.privateDirectory(target.parent)
        Files.write(target.resolveSibling("fichier.json.tmp"), "reste d'un arrêt brutal".toByteArray())
        DurableFiles.writeAtomically(target, "un".toByteArray())
        DurableFiles.writeAtomically(target, "deux".toByteArray())
        assertEquals("deux", String(Files.readAllBytes(target)))
        assertFalse(Files.exists(target.resolveSibling("fichier.json.tmp")))
    }

    /** On the POSIX file systems of the container (skipped on Windows): every directory and file of the service is private. */
    @Test
    fun theDataOfTheServiceIsPrivateOnPosixFileSystems() {
        val probe = Files.createTempDirectory("ppf-durable-posix")
        assumeTrue("POSIX permissions only", DurableFiles.isPosix(probe))
        val f = SignerFixture()
        val data = f.dir.resolve("donnees/signer")
        val service = SignerService.open(SignerConfig(data, f.kek, TEST_TOKEN, f.rootHex, TEST_ORIGIN, TEST_RP_ID, TEST_BACKOFFICE), f.clock)
        f.certify(service, org.politisationpopulairefrancaise.core.platform.SigningUse.OPENDATA_INDEX)
        service.loadTeamList(f.teamList(listOf(f.alice.member())))
        val directories = listOf(data.parent, data, data.resolve("keys"), data.resolve("certificates"))
        for (dir in directories) assertEquals("rwx------", permissions(dir), "$dir")
        val files = Files.walk(data).use { stream -> stream.filter { Files.isRegularFile(it) }.toList() }
        assertTrue(files.any { it.fileName.toString() == "journal.jsonl" } && files.any { it.fileName.toString() == "team-list.json" })
        for (file in files) assertEquals("rw-------", permissions(file), "$file")
        // A directory made readable by others is made private again at the next start.
        Files.setPosixFilePermissions(data.resolve("keys"), PosixFilePermissions.fromString("rwxr-xr-x"))
        SignerService.open(SignerConfig(data, f.kek, TEST_TOKEN, f.rootHex, TEST_ORIGIN, TEST_RP_ID, TEST_BACKOFFICE), f.clock)
        assertEquals("rwx------", permissions(data.resolve("keys")))
    }

    private fun permissions(path: Path): String = PosixFilePermissions.toString(Files.getPosixFilePermissions(path))
}
