package org.politisationpopulairefrancaise.web

import org.politisationpopulairefrancaise.core.identity.PublicKey
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecurityPagesTest {

    private val key = PublicKey.fromHex("7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e")
    private val github = "https://github.com/ppf-collectif/ppf/security/advisories/new"
    private val now = Instant.parse("2026-11-08T13:45:12Z")

    private fun fields(text: String): List<Pair<String, String>> = text.lines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { line -> line.substringBefore(": ") to line.substringAfter(": ") }

    @Test
    fun fileHasTheRequiredFieldsInTheRightForm() {
        val text = securityTxt(SecurityContact("https://politisation-populaire-francaise.org", key, github), now)
        val fields = fields(text)
        // Contacts in order of preference: encrypted message, private report on the repository, the page.
        assertEquals(
            listOf("nostr:${key.npub}", github, "https://politisation-populaire-francaise.org/securite"),
            fields.filter { it.first == "Contact" }.map { it.second },
        )
        // Exactly one Expires, from the start of the UTC day, under a year (RFC 9116, section 2.5.5).
        assertEquals(listOf("2027-05-07T00:00:00Z"), fields.filter { it.first == "Expires" }.map { it.second })
        assertEquals("fr, en", fields.single { it.first == "Preferred-Languages" }.second)
        assertEquals("https://politisation-populaire-francaise.org/.well-known/security.txt", fields.single { it.first == "Canonical" }.second)
        assertEquals("https://politisation-populaire-francaise.org/securite", fields.single { it.first == "Policy" }.second)
        assertEquals("https://politisation-populaire-francaise.org/securite#remerciements", fields.single { it.first == "Acknowledgments" }.second)
        // Only fields RFC 9116 defines.
        val known = setOf("Contact", "Expires", "Preferred-Languages", "Canonical", "Policy", "Acknowledgments")
        assertTrue(fields.all { it.first in known }, fields.toString())
        assertTrue(text.endsWith("\n"))
    }

    @Test
    fun theSameDayGivesTheSameFile() {
        val contact = SecurityContact("https://politisation-populaire-francaise.org")
        assertEquals(securityTxt(contact, now), securityTxt(contact, Instant.parse("2026-11-08T23:59:59Z")))
    }

    @Test
    fun withoutPrivateChannelThePageIsTheOnlyContact() {
        val contact = SecurityContact("https://preprod.politisation-populaire-francaise.org")
        assertFalse(contact.hasPrivateChannel)
        val contacts = fields(securityTxt(contact, now)).filter { it.first == "Contact" }.map { it.second }
        assertEquals(listOf("https://preprod.politisation-populaire-francaise.org/securite"), contacts)
    }

    @Test
    fun malformedSettingsAreRefused() {
        assertFailsWith<IllegalArgumentException> { SecurityContact("https://politisation-populaire-francaise.org/") }
        assertFailsWith<IllegalArgumentException> { SecurityContact("https://politisation-populaire-francaise.org/securite") }
        assertFailsWith<IllegalArgumentException> { SecurityContact("https://ppf.example\nContact: mailto:x@example.org") }
        assertFailsWith<IllegalArgumentException> {
            SecurityContact("https://politisation-populaire-francaise.org", githubAdvisoryUrl = "https://example.org/report")
        }
        assertFailsWith<IllegalArgumentException> {
            SecurityContact("https://politisation-populaire-francaise.org", githubAdvisoryUrl = "https://github.com/a/b/security/advisories/new\nContact: x")
        }
    }
}
