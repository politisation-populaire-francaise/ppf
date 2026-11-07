package org.politisationpopulairefrancaise.tools.opendata

import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions

/**
 * `ppf-open-data-importer keygen --out <file>`: creates a new import key (secp256k1, nsec) in a
 * file that must not exist yet, readable by its owner only where the system allows it, and prints
 * its public key, to be written in `OpenDataKeys` (PREPRODUCTION or PRODUCTION). The private key
 * never leaves the file: it is not printed.
 */
fun runKeyGen(args: List<String>): Int {
    if (args.size != 2 || args[0] != "--out") {
        System.err.println("usage: ppf-open-data-importer keygen --out <new nsec file>")
        return ExitCodes.USAGE
    }
    val file = Path.of(args[1])
    val keys = IdentityKeys.generate()
    return try {
        file.parent?.let(Files::createDirectories)
        Files.newBufferedWriter(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { it.write(keys.nsec + "\n") }
        runCatching { Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------")) }
        println("import key created in $file")
        println("public key (hex): ${keys.publicKey.hex}")
        ExitCodes.OK
    } catch (e: FileAlreadyExistsException) {
        System.err.println("key error: $file already exists, it is never overwritten")
        ExitCodes.KEY_ERROR
    } catch (e: Exception) {
        System.err.println("key error: ${e.message}")
        ExitCodes.KEY_ERROR
    }
}
