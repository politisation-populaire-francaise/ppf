package org.politisationpopulairefrancaise.tools.content

import org.politisationpopulairefrancaise.core.agir.followups.FollowUp
import org.politisationpopulairefrancaise.core.agir.followups.FollowUpKind
import org.politisationpopulairefrancaise.core.agir.followups.FollowUpLinkKind
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.voir.FactStatus
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * AG-62: the examples of content/suites build and read back as the application reads them, each following an
 * example of content/agenda; the tool refuses a link to an unknown slug or written as an address, a follow-up
 * dated before its mobilization, a title without [EXEMPLE], a source outside the example domains and an unknown
 * field. Needs no secret and no signed bundle.
 */
class FollowUpContentTest {
    private val root = Path.of("").toAbsolutePath()
    private val keys = IdentityKeys.generate()
    private val author = keys.publicKey

    @Test
    fun theExamplesBuildAndReadBackAsTheApplicationReadsThem() {
        val signer = LocalEventSigner(keys)
        val signed = followUpEvents(root.resolve("content"), author).map { signer.signChecked(it) }
        val trust = EditorialTrust(setOf(author.hex))
        val followUps = signed.map { assertNotNull(FollowUp.from(it, trust), it.firstTagValue("d")) }
        assertEquals(3, followUps.size)
        assertTrue(followUps.all { it.example }, "every follow-up of an example is an example")
        assertTrue(followUps.all { it.origin == ContentOrigin.AI_ASSISTED })
        // They all follow the past example of the agenda, under the key of the tool.
        val rennes = ContentAddress(31923, author.hex, "projection-debat-rennes-2026-11-03")
        assertTrue(followUps.all { it.mobilizations == listOf(rennes) })
        // Example addresses only, each with its address.
        assertTrue(followUps.flatMap { it.sources }.all { it.url!!.startsWith("https://example.org/exemple/") })
        // The established and the alleged are both shown; an answer of an elected official names its person.
        assertEquals(setOf(FactStatus.ESTABLISHED, FactStatus.ALLEGED), followUps.map { it.status }.toSet())
        val reply = followUps.single { it.kind == FollowUpKind.ELECTED_REPLY }
        assertEquals(listOf(ContentAddress(30023, author.hex, "camille-exemple")), reply.persons)
        assertEquals(listOf(ContentAddress(30023, author.hex, "transparence-agenda-elus")), reply.linksOf(FollowUpLinkKind.DEMAND))
        assertTrue(followUps.any { it.linksOf(FollowUpLinkKind.CHRONOLOGY) == listOf(ContentAddress(30023, author.hex, "chrono-49-3-ordre-public")) })
    }

    private fun content(followUp: String): Path {
        val content = Files.createTempDirectory("ppf-suites")
        Files.writeString(Files.createDirectories(content.resolve("suites")).resolve("test.md"), followUp)
        Files.writeString(
            Files.createDirectories(content.resolve("agenda")).resolve("passee.md"),
            "---\nslug: rassemblement-passe\ntitle: [EXEMPLE] Rassemblement passé\ntype: rassemblement\nstart: 2026-11-03T18:00\n" +
                "location: Place fictive\nterritory: Villefictive\ncreated_at: 2026-10-28T12:00\n---\nTexte.\n",
        )
        Files.writeString(
            Files.createDirectories(content.resolve("voir/faits")).resolve("vote.md"),
            "---\nslug: vote-exemple\ntype: fait\ntitle: [EXEMPLE] Un vote\nfact: vote\ndate: 2026-10-01\nfact-status: etabli\n---\nTexte.\n",
        )
        return content
    }

    private fun followUp(
        vararg more: String,
        title: String = "[EXEMPLE] Une annonce fictive",
        mobilization: String = "agenda:rassemblement-passe",
        date: String = "2026-11-05",
        source: String = "medias | Article (exemple) | https://example.org/exemple/article | 2026-11-05",
    ) = "---\nslug: suite-test\ntitle: $title\nsummary: Un résumé.\ntype: suite\nmobilization: $mobilization\nsuite-kind: annonce\n" +
        "date: $date\nfact-status: etabli\nsource: $source\n" + more.joinToString("") { "$it\n" } +
        "origin: ai-assisted\npublished_at: 2026-11-05\nupdated_at: 2026-11-05\n---\n[EXEMPLE] Texte.\n"

    private fun refused(file: String): String =
        assertFailsWith<IllegalArgumentException> { followUpEvents(content(file), author) }.message.orEmpty()

    @Test
    fun aWellFormedFollowUpBuildsWithTheAddressOfItsMobilization() {
        val event = followUpEvents(content(followUp()), author).single()
        assertTrue(listOf("a", "31923:${author.hex}:rassemblement-passe", "", "mobilisation") in event.tags)
    }

    @Test
    fun aMobilizationThatIsNotInTheAgendaIsRefused() {
        assertContains(refused(followUp(mobilization = "agenda:rassemblement-inconnu")), "rassemblement-inconnu")
    }

    @Test
    fun aLinkWrittenAsAnAddressIsRefused() {
        assertContains(refused(followUp(mobilization = "31923:${author.hex}:rassemblement-passe")), "agenda:<slug>")
    }

    @Test
    fun aFactOutsideTheChronologyIsRefused() {
        assertContains(refused(followUp("chronology: voir:vote-exemple")), "vote-exemple")
    }

    @Test
    fun aFollowUpDatedBeforeItsMobilizationIsRefused() {
        assertContains(refused(followUp(date = "2026-11-02")), "dated before the mobilization")
    }

    @Test
    fun aFollowUpWithoutTheExampleMarkIsRefused() {
        assertContains(refused(followUp(title = "Une annonce")), "[EXEMPLE]")
    }

    @Test
    fun aSourceOutsideTheExampleDomainsIsRefused() {
        assertContains(refused(followUp(source = "medias | Article | https://www.vrai-journal.fr/article | 2026-11-05")), "www.vrai-journal.fr")
    }

    @Test
    fun anUnknownFieldIsRefused() {
        assertContains(refused(followUp("montant: 1000")), "Champ « montant » : ce champ n'existe pas pour ce type de contenu")
    }

    @Test
    fun aFollowUpWithoutSourceIsRefused() {
        val file = followUp().lines().filterNot { it.startsWith("source:") }.joinToString("\n")
        assertContains(refused(file), "Champ « source » : il faut au moins 1 ligne(s)")
    }
}
