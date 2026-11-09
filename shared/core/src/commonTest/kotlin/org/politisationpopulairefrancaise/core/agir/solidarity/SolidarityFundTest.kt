package org.politisationpopulairefrancaise.core.agir.solidarity

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.agir.organisations.Organisation
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.time.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The entries of the directory of solidarity funds (AG-10): read only when signed by a trusted
 * editorial key, every required tag (the "neither candidate nor party" statement included), the
 * donation link reduced to what the warning "Vous quittez PPF" may open, and the manager found in
 * the directory of organisations.
 */
class SolidarityFundTest {

    private val fixtures = SolidarityFixtures()

    @Test
    fun buildsAndReadsBackAWholeFund() = runTest {
        val entry = ContentAddress(Organisation.KIND, fixtures.editor.publicKey.hex, "union-locale-lille")
        val event = fixtures.fund(
            slug = "caisse-transports-lille",
            name = "[EXEMPLE] Caisse de grève fictive des transports",
            type = SolidarityFormats.FUND_STRIKE,
            territory = "Lille (59)",
            beneficiary = "Grévistes fictifs des transports",
            managerName = "Union locale fictive",
            managerEntry = entry,
            donationLink = "https://example.org/caisse",
            sector = "Transports",
            strike = "Grève fictive de novembre",
        )
        assertTrue(event.verify())
        assertEquals(SolidarityFormats.TYPE_FUND, event.firstTagValue(AgirFormats.TAG_TYPE))
        assertEquals(SolidarityFormats.NO_PARTY_YES, event.firstTagValue(SolidarityFormats.TAG_NO_PARTY))
        assertNull(event.firstTagValue(SolidarityFormats.TAG_FUND_STATUS), "the default status stays implicit")
        val fund = fixtures.readFund(event)
        assertEquals("caisse-transports-lille", fund.slug)
        assertEquals("30023:${fixtures.editor.publicKey.hex}:caisse-transports-lille", fund.address.toString())
        assertEquals("Caisse de grève fictive des transports", fund.displayName)
        assertTrue(fund.example)
        assertEquals(SolidarityFormats.FUND_STRIKE, fund.type)
        assertEquals("Lille (59)", fund.territory)
        assertFalse(fund.isNational)
        assertEquals("Grévistes fictifs des transports", fund.beneficiary)
        assertEquals(CivilDate(2026, 11, 5), fund.verifiedOn)
        assertEquals(1_793_000_000, fund.publishedAt)
        assertEquals(1_794_000_000, fund.updatedAt)
        assertEquals("Union locale fictive", fund.managerName)
        assertEquals(entry, fund.managerEntry)
        assertEquals("https://example.org/caisse", fund.donationLink)
        assertEquals("https://example.org/caisse", fund.openableDonationLink)
        assertEquals("example.org", fund.donationHost)
        assertEquals("Transports", fund.sector)
        assertEquals("Grève fictive de novembre", fund.strike)
        assertTrue(fund.isOpen && !fund.isClosed && !fund.withdrawn)
        assertNull(fund.withdrawnOn)
        assertEquals(ContentOrigin.AI_ASSISTED, fund.origin)
        assertEquals(fixtures.editor.publicKey.hex, fund.editor)
        assertEquals("Présentation fictive.", fund.presentation)
    }

    @Test
    fun onlyFundsSignedByATrustedKeyAreAccepted() = runTest {
        val byStranger = fixtures.fund("intrus", signer = fixtures.stranger)
        assertNotNull(SolidarityFund.parse(byStranger), "the form is valid")
        assertNull(SolidarityFund.from(byStranger, fixtures.trust), "but the author is not trusted")

        val genuine = fixtures.fund("vraie")
        assertNotNull(SolidarityFund.from(genuine, fixtures.trust))
        // A forged copy (the donation page changed after signing) fails the signature check.
        val forged = genuine.copy(tags = genuine.tags.map { if (it.firstOrNull() == AgirFormats.TAG_LINK) listOf(AgirFormats.TAG_LINK, "https://example.net/arnaque") else it })
        assertNull(SolidarityFund.from(forged, fixtures.trust))
        // A directory entry of the organisations is not a fund, nor a fund an entry of the directory.
        assertNull(Organisation.parse(genuine))
    }

    @Test
    fun eachRequiredTagIsRequired() = runTest {
        val unsigned = fixtures.fundEvent("complete")
        assertNotNull(SolidarityFund.from(fixtures.editor.sign(unsigned), fixtures.trust))
        val required = listOf(
            "d", AgirFormats.TAG_TITLE, AgirFormats.TAG_SUMMARY, AgirFormats.TAG_TYPE, SolidarityFormats.TAG_FUND_TYPE,
            AgirFormats.TAG_TERRITORY, SolidarityFormats.TAG_BENEFICIARY, AgirFormats.TAG_VERIFIED, AgirFormats.TAG_PUBLISHED_AT,
            SolidarityFormats.TAG_NO_PARTY,
        )
        for (tag in required) {
            assertNull(SolidarityFund.from(fixtures.without(unsigned, tag), fixtures.trust), "without $tag")
        }
        // The statement must say "oui": nothing else counts.
        assertNull(SolidarityFund.from(fixtures.replacing(unsigned, SolidarityFormats.TAG_NO_PARTY, "non"), fixtures.trust))
        assertNull(SolidarityFund.from(fixtures.replacing(unsigned, AgirFormats.TAG_VERIFIED, "2026-02-30"), fixtures.trust), "a date that does not exist")
    }

    @Test
    fun aFundNamesItsManagerByNameOrByItsEntryInTheDirectory() = runTest {
        val entry = ContentAddress(Organisation.KIND, fixtures.editor.publicKey.hex, "legal-team")
        val both = fixtures.fundEvent("deux", managerName = "Legal team fictive", managerEntry = entry)
        val byName = fixtures.without(both, AgirFormats.TAG_ADDRESS)
        val byEntry = fixtures.without(both, SolidarityFormats.TAG_MANAGER)
        assertEquals("Legal team fictive", fixtures.readFund(byName).managerName)
        assertNull(fixtures.readFund(byName).managerEntry)
        assertEquals(entry, fixtures.readFund(byEntry).managerEntry)
        assertNull(fixtures.readFund(byEntry).managerName)
        // Neither: refused.
        val neither = fixtures.editor.sign(both.copy(tags = both.tags.filterNot { it.firstOrNull() == AgirFormats.TAG_ADDRESS || it.firstOrNull() == SolidarityFormats.TAG_MANAGER }))
        assertNull(SolidarityFund.from(neither, fixtures.trust))
        // An "a" tag that is not the address of a directory entry does not name a manager.
        val notAnEntry = fixtures.replacing(byEntry.unsigned(), AgirFormats.TAG_ADDRESS, "31923:${fixtures.editor.publicKey.hex}:une-mobilisation")
        assertNull(SolidarityFund.from(notAnEntry, fixtures.trust))
    }

    @Test
    fun anUnknownTypeIsReadAsOtherAndAnUnknownStatusAsWithdrawn() = runTest {
        val unsigned = fixtures.fundEvent("future")
        val newType = fixtures.readFund(fixtures.replacing(unsigned, SolidarityFormats.TAG_FUND_TYPE, "solidarite-paysanne"))
        assertEquals(SolidarityFormats.FUND_OTHER, newType.type)
        val newStatus = fixtures.readFund(fixtures.replacing(unsigned, SolidarityFormats.TAG_FUND_STATUS, "suspendue"))
        assertEquals(SolidarityFormats.FUND_WITHDRAWN, newStatus.status)
        assertNull(newStatus.openableDonationLink, "an unknown status never extends the trust")
        assertEquals(SolidarityFormats.FUND_OPEN, SolidarityFund.readStatus(null))
        assertEquals(SolidarityFormats.FUND_CLOSED, SolidarityFund.readStatus(" close "))
    }

    @Test
    fun aClosedOrWithdrawnFundOffersNoDonationLink() = runTest {
        val closed = fixtures.readFund(fixtures.fund("close", status = SolidarityFormats.FUND_CLOSED, donationLink = "https://example.org/close"))
        assertTrue(closed.isClosed)
        assertEquals("https://example.org/close", closed.donationLink)
        assertNull(closed.openableDonationLink)

        // 7 November 2026, 09:00 in Paris.
        val withdrawn = fixtures.readFund(fixtures.fund("retiree", status = SolidarityFormats.FUND_WITHDRAWN, donationLink = null, updatedAt = 1_794_038_400))
        assertTrue(withdrawn.withdrawn)
        assertNull(withdrawn.openableDonationLink)
        assertEquals(CivilDate(2026, 11, 7), withdrawn.withdrawnOn)
    }

    @Test
    fun theDonationLinkIsOnlyWhatTheWarningMayOpen() = runTest {
        assertTrue(SolidarityFund.isDonationLink("https://example.org/caisse"))
        assertTrue(SolidarityFund.isDonationLink("https://www.example.org/caisse?campagne=greve"))
        assertFalse(SolidarityFund.isDonationLink("http://example.org/caisse"), "https only")
        assertFalse(SolidarityFund.isDonationLink("https://example.org@arnaque.example/caisse"), "no user@ part")
        assertFalse(SolidarityFund.isDonationLink("https://ex${Char(0x0430)}mple.org/caisse"), "no letter that imitates another (Cyrillic a)")
        assertFalse(SolidarityFund.isDonationLink("javascript:alert(1)"))
        assertFalse(SolidarityFund.isDonationLink("https://example.org/une caisse"), "no space")
        assertFalse(SolidarityFund.isDonationLink("https://localhost/caisse"), "a dotted host")

        // A fund whose only link is not acceptable stays readable, without a link.
        val unsigned = fixtures.fundEvent("lien-http")
        val read = fixtures.readFund(fixtures.replacing(unsigned, AgirFormats.TAG_LINK, "http://example.org/caisse"))
        assertNull(read.donationLink)
        assertNull(read.openableDonationLink)
    }

    @Test
    fun buildRefusesWhatTheApplicationWouldNotShow() {
        val key = fixtures.editor.publicKey
        fun build(donationLink: String? = "https://example.org/x", managerName: String? = "Collectif", status: String = SolidarityFormats.FUND_OPEN, type: String = SolidarityFormats.FUND_LEGAL) =
            SolidarityFund.build(
                editor = key, slug = "test", name = "[EXEMPLE] Test", summary = "Résumé.", type = type, territory = "France",
                beneficiary = "Personnes poursuivies", verifiedOn = "2026-11-05", publishedAt = 1, updatedAt = 2, presentation = "",
                managerName = managerName, donationLink = donationLink, status = status,
            )
        build()
        assertFailsWith<IllegalArgumentException>("an open fund needs its donation page") { build(donationLink = null) }
        assertFailsWith<IllegalArgumentException>("https only") { build(donationLink = "http://example.org/x") }
        assertFailsWith<IllegalArgumentException>("a manager") { build(managerName = null) }
        assertFailsWith<IllegalArgumentException>("a known status") { build(status = "suspendue") }
        assertFailsWith<IllegalArgumentException>("a known type") { build(type = "parti") }
        // A closed fund may have lost its page.
        build(donationLink = null, status = SolidarityFormats.FUND_CLOSED)
    }

    @Test
    fun theManagerIsFoundInTheDirectoryAndItsStateSaidInWords() = runTest {
        val union = fixtures.organisation("union-locale", name = "[EXEMPLE] Union locale fictive")
        val revoked = fixtures.organisation("collectif-retire", name = "[EXEMPLE] Collectif retiré", status = "revoquee")
        val directory = listOf(union, revoked)

        val verified = fixtures.readFund(fixtures.fund("a", managerName = "Nom écrit dans la caisse", managerEntry = union.address)).managerIn(directory)
        assertEquals(FundManagerState.VERIFIED, verified.state)
        assertEquals("Union locale fictive", verified.name, "the directory's name, without the example marker")
        assertEquals(union, verified.organisation)

        val withdrawn = fixtures.readFund(fixtures.fund("b", managerEntry = revoked.address)).managerIn(directory)
        assertEquals(FundManagerState.REVOKED, withdrawn.state)

        val unlisted = fixtures.readFund(fixtures.fund("c", managerName = "Intersyndicale fictive")).managerIn(directory)
        assertEquals(FundManager("Intersyndicale fictive", null, FundManagerState.UNLISTED), unlisted)

        // An entry this device does not hold yet: no name to show, said as such.
        val unknownEntry = ContentAddress(Organisation.KIND, fixtures.editor.publicKey.hex, "pas-encore-recue")
        val unknown = fixtures.readFund(fixtures.fund("d", managerName = null, managerEntry = unknownEntry)).managerIn(directory)
        assertEquals(FundManager(null, null, FundManagerState.UNLISTED), unknown)
    }

    @Test
    fun afterARotationOfTheEditorialKeyTheManagerIsFoundByItsIdentifier() = runTest {
        val before = ContentAddress(Organisation.KIND, fixtures.editor.publicKey.hex, "legal-team")
        val republished = fixtures.organisation("legal-team", name = "[EXEMPLE] Legal team fictive", signer = fixtures.otherEditor)
        val manager = fixtures.readFund(fixtures.fund("e", managerEntry = before)).managerIn(listOf(republished))
        assertEquals(FundManagerState.VERIFIED, manager.state)
        assertEquals(republished, manager.organisation)
    }
}
