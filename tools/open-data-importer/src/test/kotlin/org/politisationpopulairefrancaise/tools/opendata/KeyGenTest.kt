package org.politisationpopulairefrancaise.tools.opendata

import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.voir.parlement.OpenDataKeys
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class KeyGenTest {

    @Test
    fun aNewKeyIsWrittenOnceAndAcceptedForOfficialData() {
        val dir = Files.createTempDirectory("ppf-keygen-")
        val file = dir.resolve("import_key")
        assertEquals(ExitCodes.OK, runKeyGen(listOf("--out", file.toString())))
        val keys = IdentityKeys.fromNsec(Files.readString(file).trim())
        assertNotEquals(OpenDataKeys.EXAMPLE, keys.publicKey.hex)
        // Official mode accepts any key but the example one.
        assertTrue(loadKey(file, SourceMode.OFFICIAL).isSuccess)
        // Never overwritten.
        val before = Files.readString(file)
        assertEquals(ExitCodes.KEY_ERROR, runKeyGen(listOf("--out", file.toString())))
        assertEquals(before, Files.readString(file))
        assertEquals(ExitCodes.USAGE, runKeyGen(emptyList()))
    }

    @Test
    fun localFilesNeedTheOfficialMode() {
        assertTrue(parseOptions(listOf("--source", "example", "--from", "x", "--out", "o", "--key", "k")) { null }.isFailure)
        assertEquals("x", parseOptions(listOf("--source", "official", "--from", "x", "--out", "o", "--key", "k")) { null }.getOrThrow().from.toString())
    }
}
