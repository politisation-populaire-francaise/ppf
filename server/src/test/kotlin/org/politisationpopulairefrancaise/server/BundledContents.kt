package org.politisationpopulairefrancaise.server

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import java.nio.file.Files
import java.nio.file.Path

/**
 * The example contents bundled with the applications (signed by tools/content-signer, all marked
 * [EXEMPLE]), read from the repository: from its root (development server) or from server/ (tests).
 */
object BundledContents {
    const val DIRECTORY: String = "shared/ui/src/commonMain/composeResources/files/contents"

    /** fiches.json (practical guides), revendications.json (demands of the wall), fil.json (notes of the feed). */
    val FILES: List<String> = listOf("fiches.json", "revendications.json", "fil.json")

    /** The authentic events of [file]; empty when the file cannot be found. */
    fun load(file: String): List<SignedEvent> {
        val path = listOf(Path.of(DIRECTORY, file), Path.of("..", DIRECTORY, file)).firstOrNull(Files::exists) ?: return emptyList()
        return Json.decodeFromString(ListSerializer(SignedEvent.serializer()), Files.readString(path)).filter { it.verify() }
    }

    fun loadAll(): List<SignedEvent> = FILES.flatMap(::load)
}
