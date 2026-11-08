package org.politisationpopulairefrancaise.tools.content

import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

/** The tool still refuses the faulty files of Comprendre, now through the editorial engine (D-092). */
class InvalidContentTest {
    private val author = IdentityKeys.generate().publicKey

    private fun refused(directory: String, file: String): String {
        val content = Files.createTempDirectory("ppf-content")
        val folder = Files.createDirectories(content.resolve(directory))
        Files.writeString(folder.resolve("test.md"), file)
        return assertFailsWith<IllegalArgumentException> { prepareBundles(content, author) }.message.orEmpty()
    }

    private fun front(type: String, vararg more: String) =
        "---\nslug: test\ntitle: [EXEMPLE] Test\nsummary: Un résumé.\ntype: $type\norigin: human\n${more.joinToString("") { "$it\n" }}published_at: 2026-11-07\nupdated_at: 2026-11-07\n---\n"

    @Test
    fun aCourseWithoutQuizIsRefused() {
        val message = refused("parcours", front("parcours", "duration: 5") + "## Étape\n\nTexte.\n\n::: et-maintenant\n- Agir\n:::\n\n## Sources\n- Une source\n")
        assertContains(message, "il faut au moins 1 bloc(s) « ::: quiz »")
    }

    @Test
    fun aContentOfComprendreWithoutSourcesIsRefused() {
        val message = refused("methode", front("article") + "Texte.\n")
        assertContains(message, "Texte : il manque la section « ## Sources »")
    }

    @Test
    fun aFactCheckWithAnUnknownVerdictIsRefused() {
        val message = refused("decryptages", front("decryptage", "verdict: mensonge", "claim: Une affirmation.") + "::: objections\n? O\n> R\n:::\n\n## Sources\n- S\n")
        assertContains(message, "Champ « verdict »")
    }

    @Test
    fun aSheetLinkingAnUnknownPersonIsRefused() {
        val body = "::: decideurs\n- commune | maire | La commune décide.\n:::\n\n::: et-maintenant\n- Agir\n:::\n\n## Sources\n- S\n"
        val message = refused("qui-decide", front("qui-decide", "persons: personne-inconnue") + body)
        assertContains(message, "aucun contenu ne porte l'identifiant « personne-inconnue »")
    }
}
