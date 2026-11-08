package org.politisationpopulairefrancaise.server.urne

import kotlinx.html.div
import kotlinx.html.stream.createHTML
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.urne.CertificationAnswer
import org.politisationpopulairefrancaise.core.urne.VoteLevel
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.backoffice.ActionOutcome
import org.politisationpopulairefrancaise.server.backoffice.TeamActor
import org.politisationpopulairefrancaise.server.backoffice.TeamRole
import org.politisationpopulairefrancaise.server.elus.ElusConfig
import org.politisationpopulairefrancaise.server.elus.ElusLookup
import org.politisationpopulairefrancaise.server.elus.ElusService
import org.politisationpopulairefrancaise.server.elus.ElusSetup
import org.politisationpopulairefrancaise.server.elus.ElusStore
import org.politisationpopulairefrancaise.server.elus.ExampleSource
import org.politisationpopulairefrancaise.server.elus.ImportOutcome
import org.politisationpopulairefrancaise.server.elus.Mandate
import org.politisationpopulairefrancaise.server.revendications.MutableClock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The certification of elected officials' accounts (DE-23, D-112) on the fictitious directory: the request
 * of an account dedicated to the mandate, the queue elus.certifications, and the end of a mandate.
 */
class ElectedCertificationsTest {
    /** Wednesday 4 November 2026 (UTC): next Monday is 9 November. */
    private val clock = MutableClock(Instant.parse("2026-11-04T12:00:00Z"))
    private val elus = ElusService(ElusStore(TestDatabase.dataSource), ElusSetup(ElusConfig(refreshDays = 0), clock))
    private val lookup = ElusLookup(elus)
    private val store = UrneStore(TestDatabase.dataSource)
    private val service = UrneService.create(store, UrneFixtures.CONFIG, clock)
    private val certifications = ElectedCertifications(store, service, lookup, clock)
    private val queue = ElectedCertificationsQueue(certifications, store)
    private val certifier = TeamActor.of(UUID.randomUUID(), "Certif", setOf(TeamRole.ELECTED_CERTIFIER), 2)
    private val nextMonday = LocalDate.parse("2026-11-09")

    @BeforeTest
    fun emptyTables() {
        TestDatabase.reset()
        UrneFixtures.reset()
    }

    private fun registered(): String = IdentityKeys.generate().publicKey.hex.also { service.register(it) }

    private fun body(mandate: String, type: String = "municipal", territory: String = "69123") =
        """{"mandate":"$mandate","mandateType":"$type","territory":"$territory"}"""

    private fun importDirectory() = assertIs<ImportOutcome.Imported>(elus.importNow(ExampleSource()))

    @Test
    fun aDedicatedAccountIsCertifiedInPersonThroughTheQueue() {
        importDirectory()
        val claude = lookup.find("claude demonstration").single()
        val official = registered()
        assertEquals(CertificationAnswer(CertificationAnswer.PENDING), certifications.request(official, body(claude.id)))
        assertEquals("pending", service.status(official).certification)
        assertEquals(claude.id, service.status(official).mandate)
        // While its certification waits, the account gets no anonymous token: it will vote publicly.
        assertEquals("elected_account", assertFailsWith<UrneRefusal> { service.issue(official, """{"requests":[]}""") }.code)
        // What the certifier sees: the mandate as the directory gives it, the key, nothing else.
        assertEquals(1, queue.pendingCount())
        assertEquals("[EXEMPLE] Claude Démonstration (Lyon)", queue.page(null, 10).items.single().title)
        val page = createHTML().div { queue.render(official, this) }
        assertTrue(page.contains("Mandat en cours") && page.contains(claude.id) && page.contains("2ème adjoint au Maire"), page)
        assertEquals(listOf(ElectedCertificationsQueue.CERTIFY, ElectedCertificationsQueue.REFUSE), queue.actions(official).map { it.id })
        assertTrue(queue.actions(official).first().sensitive)
        assertEquals(ActionOutcome.Done, queue.perform(official, ElectedCertificationsQueue.CERTIFY, null, certifier))
        val account = store.account(official)!!
        assertEquals(VoteLevel.ELU, account.level)
        assertEquals(nextMonday, account.levelWeek, "from next Monday")
        assertEquals(ActionOutcome.AlreadyDecided, queue.perform(official, ElectedCertificationsQueue.CERTIFY, null, certifier))
        assertEquals(listOf(ElectedCertificationsQueue.WITHDRAW), queue.actions(official).map { it.id })
        // One account per mandate; a certified account does not ask again.
        assertEquals("mandate_taken", assertFailsWith<UrneRefusal> { certifications.request(registered(), body(claude.id)) }.code)
        assertEquals("already_certified", assertFailsWith<UrneRefusal> { certifications.request(official, body(claude.id)) }.code)
        // Withdrawn, with a reason shown to the account: « non vérifié » again from next Monday.
        assertIs<ActionOutcome.Refused>(queue.perform(official, ElectedCertificationsQueue.WITHDRAW, " ", certifier))
        assertEquals(ActionOutcome.Done, queue.perform(official, ElectedCertificationsQueue.WITHDRAW, "Compte cédé", certifier))
        assertEquals(VoteLevel.NON_VERIFIE, store.account(official)!!.level)
        assertEquals("withdrawn", service.status(official).certification)
        assertEquals("Compte cédé", service.status(official).certificationReason)
    }

    @Test
    fun onlyAnAccountCreatedForAMandateOfTheDirectoryAsks() {
        importDirectory()
        val andrea = lookup.find("andrea demonstration").single()
        val territory = andrea.territory
        val official = registered()
        assertEquals("invalid_mandate", assertFailsWith<UrneRefusal> { certifications.request(official, body("rne1:xyz", territory = territory)) }.code)
        assertEquals("invalid_mandate", assertFailsWith<UrneRefusal> { certifications.request(official, body(andrea.id, type = "roi", territory = territory)) }.code)
        assertEquals("mandate_not_found", assertFailsWith<UrneRefusal> { certifications.request(official, body("rne1:" + "0".repeat(32), territory = territory)) }.code)
        assertEquals("mandate_not_found", assertFailsWith<UrneRefusal> { certifications.request(official, body(andrea.id, territory = "69123")) }.code)
        assertEquals("not_registered", assertFailsWith<UrneRefusal> { certifications.request(IdentityKeys.generate().publicKey.hex, body(andrea.id, territory = territory)) }.code)
        // A personal account, already verified, stays personal.
        val personal = registered()
        store.setLevel(personal, VoteLevel.VERIFIE, LocalDate.parse("2026-10-26"), LocalDate.parse("2026-10-26"))
        assertEquals("not_a_dedicated_account", assertFailsWith<UrneRefusal> { certifications.request(personal, body(andrea.id, territory = territory)) }.code)
        // Refused with a reason shown to the account; a new request may follow.
        certifications.request(official, body(andrea.id, territory = territory))
        assertIs<ActionOutcome.Refused>(queue.perform(official, ElectedCertificationsQueue.REFUSE, "", certifier))
        assertEquals(ActionOutcome.Done, queue.perform(official, ElectedCertificationsQueue.REFUSE, "Personne non rencontrée", certifier))
        assertEquals("refused", service.status(official).certification)
        assertEquals(VoteLevel.NON_VERIFIE, store.account(official)!!.level)
        assertEquals(CertificationAnswer.PENDING, certifications.request(official, body(andrea.id, territory = territory)).status)
    }

    @Test
    fun aMandateGoneFromTheDirectoryEndsItsCertificationButADirectoryNotServedNever() {
        val gone = "rne1:" + "1".repeat(32)
        val official = registered()
        assertEquals(CertificationWrite.RECORDED, store.requestCertification(official, gone, Mandate.MUNICIPAL.code, "69123", LocalDate.parse("2026-10-26")))
        assertTrue(store.decideCertification(official, CertificationAnswer.PENDING, CertificationAnswer.CERTIFIED, null, LocalDate.parse("2026-10-26"), VoteLevel.ELU))
        // Nothing served yet: nothing can be said, nothing ends.
        assertEquals(0, certifications.checkMandatesDue())
        assertEquals(VoteLevel.ELU, store.account(official)!!.level)
        // The next day, with a directory served that does not hold the mandate: ended.
        importDirectory()
        clock.instant = clock.instant.plusSeconds(86_400)
        assertEquals(1, certifications.checkMandatesDue())
        assertEquals(0, certifications.checkMandatesDue(), "once a day")
        assertEquals("ended", store.certification(official)!!.status)
        val account = store.account(official)!!
        assertEquals(VoteLevel.NON_VERIFIE, account.level)
        assertEquals(nextMonday, account.levelWeek)
        assertEquals(VoteLevel.ELU, account.levelAt(LocalDate.parse("2026-11-02")), "the boxes opened before keep the official's level")
    }
}
