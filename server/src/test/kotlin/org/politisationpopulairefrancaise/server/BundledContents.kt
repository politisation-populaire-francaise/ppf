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

    /**
     * fiches.json (practical guides), revendications.json (demands of the wall), fil.json (notes of the
     * feed), and for the Agir space agenda.json (mobilizations), appels.json (calls) and
     * organisations.json (directory), and voir.json (the blocks of "Voir": figures, facts, persons,
     * bills, movements, dossiers, replies), each loaded only if it exists.
     */
    val FILES: List<String> get() = listOf("fiches.json", "revendications.json", "fil.json") + AGIR_FILES + VOIR_FILE + COMPRENDRE_FILES

    /** The example contents of "Voir", shown by the site at /voir. */
    const val VOIR_FILE: String = "voir.json"

    /**
     * The other example contents of "Comprendre", shown by the site at /comprendre (D-091): courses,
     * fact-checks, stories, « Qui décide de ça ? » sheets, the method page and the glossary.
     */
    val COMPRENDRE_FILES: List<String> = listOf("parcours.json", "comprendre.json", "glossaire.json")

    /** The example contents of the Agir space, written by the agents of its blocks (absent until then). */
    val AGIR_FILES: List<String> = listOf("agenda.json", "appels.json", "organisations.json")

    /** The authentic events of [file]; empty when the file cannot be found. */
    fun load(file: String): List<SignedEvent> {
        val path = listOf(Path.of(DIRECTORY, file), Path.of("..", DIRECTORY, file)).firstOrNull(Files::exists) ?: return emptyList()
        return Json.decodeFromString(ListSerializer(SignedEvent.serializer()), Files.readString(path)).filter { it.verify() }
    }

    fun loadAll(): List<SignedEvent> = FILES.flatMap(::load)
}
