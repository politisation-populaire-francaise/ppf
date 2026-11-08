package org.politisationpopulairefrancaise.tools.content

import org.politisationpopulairefrancaise.core.agir.organisations.Organisation
import org.politisationpopulairefrancaise.core.agir.solidarity.SolidarityFund
import org.politisationpopulairefrancaise.core.agir.solidarity.VolunteerMission
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The examples of content/solidarite build and read back as the application reads them, and the
 * tool refuses a fund without the "neither candidate nor party" statement, a manager that is not in
 * the directory and a donation page that is not https. Needs no secret and no signed bundle.
 */
class SolidarityContentTest {
    private val root = Path.of("").toAbsolutePath()
    private val keys = IdentityKeys.generate()
    private val author = keys.publicKey

    @Test
    fun theExamplesBuildAndReadBackAsTheApplicationReadsThem() {
        val events = solidarityEvents(root.resolve("content"), author)
        val signer = LocalEventSigner(keys)
        val signed = events.map { signer.signChecked(it) }
        val funds = signed.mapNotNull { SolidarityFund.parse(it) }
        val missions = signed.mapNotNull { VolunteerMission.parse(it) }
        assertEquals(signed.size, funds.size + missions.size, "every content is a fund or a mission")
        assertEquals(5, funds.size)
        assertEquals(6, missions.size)
        assertTrue(funds.all { it.example } && missions.all { it.example }, "every example carries [EXEMPLE]")
        assertTrue(funds.all { it.origin == ContentOrigin.AI_ASSISTED } && missions.all { it.origin == ContentOrigin.AI_ASSISTED })
        // Example links only: example.org, a domain reserved for documentation (RFC 2606).
        assertTrue(funds.mapNotNull { it.donationLink }.all { it.startsWith("https://example.org/") })
        assertTrue(funds.filter { it.isOpen }.all { it.donationLink != null }, "an open fund has its donation page")
        assertEquals(listOf("caisse-greve-cantines-rennes"), funds.filter { it.isClosed }.map { it.slug })
        assertEquals(listOf("mission-moderation-salons"), missions.filter { it.isFilled }.map { it.slug })
        // The managers named by their entry are entries of content/organisations, signed by the same key.
        val entries = funds.mapNotNull { it.managerEntry }
        assertEquals(3, entries.size)
        assertTrue(entries.all { it.kind == Organisation.KIND && it.pubkey == author.hex })
        assertTrue(funds.any { it.managerEntry == null && it.managerName != null }, "one manager is not in the directory")
        assertTrue(funds.any { it.isNational } && funds.any { !it.isNational })
        assertTrue(missions.any { it.remote } && missions.any { !it.remote && it.location != null })
    }

    private fun content(fund: String): Path {
        val content = Files.createTempDirectory("ppf-solidarite")
        val funds = Files.createDirectories(content.resolve("solidarite/caisses"))
        Files.writeString(funds.resolve("test.md"), fund)
        val organisations = Files.createDirectories(content.resolve("organisations"))
        Files.writeString(organisations.resolve("connue.md"), "---\nslug: organisation-connue\ntitle: [EXEMPLE] Connue\n---\nTexte.\n")
        return content
    }

    private fun fund(vararg more: String, noParty: String = "no_party: oui", link: String = "donation_link: https://example.org/caisse") =
        "---\nslug: caisse-test\ntitle: [EXEMPLE] Caisse de test\nsummary: Un résumé.\nfund_type: greve\nterritory: Lille (59)\n" +
            "beneficiary: Grévistes fictifs\nverified: 2026-11-05\n$noParty\n$link\norigin: human\n" +
            more.joinToString("") { "$it\n" } + "published_at: 2026-11-08\nupdated_at: 2026-11-08\n---\nTexte.\n"

    @Test
    fun aWellFormedFundBuilds() {
        val events = solidarityEvents(content(fund("manager_entry: organisation-connue")), author)
        val read = assertNotNull(SolidarityFund.parse(LocalEventSigner(keys).signChecked(events.single())))
        assertEquals("30023:${author.hex}:organisation-connue", read.managerEntry.toString())
    }

    @Test
    fun aFundWithoutTheStatementOfNeitherCandidateNorPartyIsRefused() {
        val message = assertFailsWith<IllegalStateException> { solidarityEvents(content(fund("manager: Un collectif", noParty = "no_party: non")), author) }.message.orEmpty()
        assertContains(message, "neither a candidate nor a party")
    }

    @Test
    fun aManagerOutsideTheDirectoryIsRefused() {
        val message = assertFailsWith<IllegalStateException> { solidarityEvents(content(fund("manager_entry: organisation-inconnue")), author) }.message.orEmpty()
        assertContains(message, "organisation-inconnue")
    }

    @Test
    fun aDonationPageThatIsNotHttpsIsRefused() {
        val message = assertFailsWith<IllegalStateException> {
            solidarityEvents(content(fund("manager: Un collectif", link = "donation_link: http://example.org/caisse")), author)
        }.message.orEmpty()
        assertContains(message, "donationLink")
    }

    @Test
    fun anUnknownFieldIsRefused() {
        val message = assertFailsWith<IllegalStateException> { solidarityEvents(content(fund("manager: Un collectif", "montant: 1000")), author) }.message.orEmpty()
        assertContains(message, "unknown field: montant")
    }
}
