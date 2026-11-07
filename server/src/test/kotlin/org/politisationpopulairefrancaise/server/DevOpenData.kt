package org.politisationpopulairefrancaise.server

import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.tools.opendata.ExampleSources
import org.politisationpopulairefrancaise.tools.opendata.ExitCodes
import org.politisationpopulairefrancaise.tools.opendata.Importer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * The packages of parliamentary open data of the development server: the example import (fictional
 * data, [ExampleSources]) run at start into a temporary folder, served at `/api/v1/parlement`.
 * Signed by the example import key when it is on this computer (`PPF_IMPORT_KEY_FILE`, by default
 * `~/.ppf/open-data-example.nsec`), so that the application trusts them; otherwise the bundled
 * example packages (already signed by that key) are served as they are.
 */
fun devOpenData(): OpenDataConfig {
    val folder = Files.createTempDirectory("ppf-open-data-dev")
    Runtime.getRuntime().addShutdownHook(Thread { folder.toFile().deleteRecursively() })
    val keyFile = System.getenv("PPF_IMPORT_KEY_FILE")?.takeIf { it.isNotBlank() }?.let(Path::of)
        ?: Path.of(System.getProperty("user.home"), ".ppf", "open-data-example.nsec")
    val imported = keyFile.isRegularFile() &&
        Importer(ExampleSources(), IdentityKeys.fromNsec(keyFile.readText().trim()), folder, log = {}).run() == ExitCodes.OK
    if (!imported) {
        val bundled = Path.of("shared/ui/src/commonMain/composeResources/files/parlement")
        if (Files.isDirectory(bundled)) bundled.listDirectoryEntries("*.json").forEach { Files.copy(it, folder.resolve(it.name)) }
    }
    println("Parliamentary open data: example packages (${if (imported) "imported at start" else "bundled copy"}) at /api/v1/parlement.")
    return OpenDataConfig(folder)
}
