package org.politisationpopulairefrancaise.core.agir.organisations

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.core.time.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OrganisationTest {

    private val fixtures = OrganisationFixtures()
    private val accountKey = IdentityKeys.generate().publicKey.hex

    @Test
    fun buildsAndReadsBackAWholeEntry() = runTest {
        val event = fixtures.editor.sign(
            Organisation.build(
                editor = fixtures.editor.publicKey,
                slug = "ateliers-lanterne",
                name = "[EXEMPLE] Les Ateliers de la Lanterne",
                summary = "[EXEMPLE] Une association fictive.",
                type = "association",
                territory = "Lyon (69)",
                verifiedOn = "2026-10-12",
                publishedAt = 1_793_000_000,
                updatedAt = 1_794_000_000,
                presentation = "## Qui sommes-nous\n\nUne association fictive.\n",
                accountKey = accountKey,
                websites = listOf("https://example.org/lanterne"),
                phone = "04 65 71 20 14",
                email = "lanterne@example.org",
                postalAddress = "Adresse fictive, Lyon",
                geohash = "u05kq",
                origin = ContentOrigin.AI_ASSISTED,
            ),
        )
        assertTrue(event.verify())
        assertEquals(AgirFormats.TYPE_ORGANISATION, event.firstTagValue(AgirFormats.TAG_TYPE))
        val organisation = fixtures.read(event)
        assertEquals("ateliers-lanterne", organisation.slug)
        assertEquals("30023:${fixtures.editor.publicKey.hex}:ateliers-lanterne", organisation.address.toString())
        assertEquals("Les Ateliers de la Lanterne", organisation.displayName)
        assertTrue(organisation.example)
        assertEquals("association", organisation.type)
        assertEquals("Lyon (69)", organisation.territory)
        assertFalse(organisation.isNational)
        assertEquals(CivilDate(2026, 10, 12), organisation.verifiedOn)
        assertEquals(1_793_000_000, organisation.publishedAt)
        assertEquals(1_794_000_000, organisation.updatedAt)
        assertEquals(accountKey, organisation.accountKey)
        assertEquals(listOf("https://example.org/lanterne"), organisation.websites)
        assertEquals("04 65 71 20 14", organisation.phone)
        assertEquals("tel:0465712014", organisation.phoneUri)
        assertEquals("mailto:lanterne@example.org", organisation.emailUri)
        assertEquals("Adresse fictive, Lyon", organisation.postalAddress)
        assertEquals("u05kq", organisation.geohash)
        assertEquals(ContentOrigin.AI_ASSISTED, organisation.origin)
        assertEquals(fixtures.editor.publicKey.hex, organisation.editor)
        assertTrue(organisation.presentation.startsWith("## Qui sommes-nous"))
    }

    @Test
    fun onlyEntriesSignedByATrustedKeyAreAccepted() = runTest {
        val byStranger = fixtures.entry("intrus", signer = fixtures.stranger)
        assertNotNull(Organisation.parse(byStranger), "the form is valid")
        assertNull(Organisation.from(byStranger, fixtures.trust), "but the author is not trusted")

        val genuine = fixtures.entry("vraie")
        assertNotNull(Organisation.from(genuine, fixtures.trust))
        // A forged copy (content changed after signing) fails the signature check.
        val forged = genuine.copy(content = "Contenu modifié")
        assertNull(Organisation.from(forged, fixtures.trust))
        // The local database stores only checked events; its readers may skip the check.
        assertNotNull(Organisation.from(forged, fixtures.trust, checkSignature = false))
    }

    @Test
    fun aRequiredTagMissingOrInvalidRejectsTheEntry() = runTest {
        val good = fixtures.entry("complete")
        assertNotNull(Organisation.parse(good))
        for (tag in listOf("d", "title", "summary", "ppf-org-type", "ppf-territory", "ppf-verified", "published_at", "ppf-type")) {
            assertNull(Organisation.parse(resigned(good) { tags -> tags.filterNot { it[0] == tag } }), "without $tag")
        }
        assertNull(Organisation.parse(resigned(good) { tags -> tags.replace("ppf-verified", "12/10/2026") }), "date in another form")
        assertNull(Organisation.parse(resigned(good) { tags -> tags.replace("ppf-verified", "2026-02-30") }), "a date that does not exist")
        assertNull(Organisation.parse(resigned(good) { tags -> tags.replace("ppf-type", "fiche-pratique") }), "another content type")
        assertNull(Organisation.parse(resigned(good) { tags -> tags.replace("d", "Deux mots") }), "an invalid identifier")
        assertNull(Organisation.parse(resigned(good) { tags -> tags.replace("title", "x".repeat(AgirFormats.MAX_TITLE + 1)) }), "a name too long")
        assertNull(Organisation.parse(resigned(good) { tags -> tags.replace("published_at", "hier") }), "a date that is not a number")
        assertNull(Organisation.parse(fixtures.editor.sign(kind = 1, content = "note", tags = good.tags)), "another kind")
    }

    @Test
    fun anUnknownTypeIsReadAsOther() = runTest {
        val event = resigned(fixtures.entry("future")) { tags -> tags.replace("ppf-org-type", "cooperative") }
        assertEquals(Organisation.OTHER_TYPE, Organisation.parse(event)!!.type)
    }

    @Test
    fun contactsAreCleanedOnReading() = runTest {
        val event = resigned(fixtures.entry("contacts")) { tags ->
            tags + listOf(
                listOf("p", "NOT-A-KEY"),
                listOf("p", accountKey),
                listOf("r", "javascript:alert(1)"),
                listOf("r", "https://example.org/site"),
                listOf("r", "ftp://example.org/fichier"),
                listOf("r", "https://ex@mple.org@autre.example/"),
                listOf("r", "https://exаmple.org/"), // a Cyrillic "а"
                listOf("ppf-contact-phone", "06 39 98 00 00;ext=1"),
                listOf("ppf-contact-email", "contact@example.org?subject=x"),
                listOf("g", "not a geohash"),
            )
        }
        val organisation = Organisation.parse(event)!!
        assertEquals(accountKey, organisation.accountKey, "the first valid key")
        assertEquals(listOf("https://example.org/site"), organisation.websites)
        assertNull(organisation.phone)
        assertNull(organisation.email)
        assertNull(organisation.geohash)
    }

    @Test
    fun contactRulesAcceptPlainValuesOnly() {
        assertTrue(Organisation.isPhone("01 99 00 12 34"))
        assertTrue(Organisation.isPhone("+33 1 99 00 12 34"))
        assertFalse(Organisation.isPhone("01 99 +00"))
        assertFalse(Organisation.isPhone("123"))
        assertTrue(Organisation.isEmail("contact@example.org"))
        assertTrue(Organisation.isEmail("legal.team+nord@example.org"))
        assertFalse(Organisation.isEmail("contact@example"))
        assertFalse(Organisation.isEmail("a@b@example.org"))
        assertFalse(Organisation.isEmail("contact@example.org&cc=x@example.org"))
        assertTrue(Organisation.isWebsite("https://example.org"))
        assertTrue(Organisation.isWebsite("http://example.org:8080/agenda?mois=11"))
        assertFalse(Organisation.isWebsite("https://example.org:80a/"))
        assertFalse(Organisation.isWebsite("https://localhost/"))
        assertFalse(Organisation.isWebsite("data:text/html,x"))
        assertEquals("example.org", Organisation.hostOf("https://Example.org:8080/agenda"))
        assertEquals("tel:+33199001234", fixturePhoneUri("+33 1 99 00 12 34"))
    }

    @Test
    fun buildRefusesWhatReadingWouldDrop() {
        val editor = fixtures.editor.publicKey
        fun build(type: String = "syndicat", verifiedOn: String = "2026-10-12", websites: List<String> = emptyList(), accountKey: String? = null) =
            Organisation.build(editor, "slug", "Nom", "Résumé", type, "France", verifiedOn, 1, 1, "", accountKey = accountKey, websites = websites)
        build()
        assertFailsWith<IllegalArgumentException> { build(type = "parti") }
        assertFailsWith<IllegalArgumentException> { build(verifiedOn = "2026-13-01") }
        assertFailsWith<IllegalArgumentException> { build(websites = listOf("javascript:alert(1)")) }
        assertFailsWith<IllegalArgumentException> { build(accountKey = "ABC") }
    }

    @Test
    fun aNationalOrganisationActsEverywhere() = runTest {
        assertTrue(fixtures.read(fixtures.entry("nationale", territory = "France")).isNational)
        assertTrue(fixtures.read(fixtures.entry("nationale-bis", territory = " france ")).isNational)
        assertFalse(fixtures.read(fixtures.entry("locale", territory = "Nord (59)")).isNational)
    }

    private fun fixturePhoneUri(phone: String): String? = Organisation.parse(
        SignedEvent(
            id = "0".repeat(64),
            pubkey = fixtures.editor.publicKey.hex,
            createdAt = 1,
            kind = Organisation.KIND,
            tags = listOf(
                listOf("d", "x"), listOf("title", "X"), listOf("summary", "Y"), listOf("published_at", "1"),
                listOf("ppf-type", "organisation"), listOf("ppf-org-type", "autre"), listOf("ppf-territory", "France"),
                listOf("ppf-verified", "2026-10-12"), listOf("ppf-contact-phone", phone),
            ),
            content = "",
            sig = "0".repeat(128),
        ),
    )?.phoneUri

    /** The same entry with other tags, signed again by the editor. */
    private suspend fun resigned(event: SignedEvent, change: (List<List<String>>) -> List<List<String>>): SignedEvent =
        fixtures.editor.sign(kind = event.kind, content = event.content, tags = change(event.tags), createdAt = event.createdAt)

    private fun List<List<String>>.replace(name: String, value: String): List<List<String>> =
        map { if (it[0] == name) listOf(name, value) else it }
}
