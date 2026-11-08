package org.politisationpopulairefrancaise.server.backoffice

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.engine.connector
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.server.NO_EDITORS
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.testAgir
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeTestData.auth
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeTestData.journalAfter
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeTestData.lastSeq
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeTestData.member
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.legal.LegalRegister
import org.politisationpopulairefrancaise.server.module
import org.politisationpopulairefrancaise.server.testRevendications
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/** The judicial requisition of the back-office (D-077): two people, one answer, a journal without the rows, a public counter. */
class LegalRequisitionTest {
    private val clock = MutableClock(Instant.now().truncatedTo(ChronoUnit.SECONDS))
    private val alice = LocalEventSigner(IdentityKeys.generate())
    private val bob = LocalEventSigner(IdentityKeys.generate())
    private val events = EventRepository(TestDatabase.dataSource)
    private val today: LocalDate = LocalDate.now(ZoneId.of("Europe/Paris"))

    @BeforeTest
    fun emptyTables() = BackOfficeTestData.reset()

    private fun <T> Outcome<T>.ok(): T = when (this) {
        is Outcome.Ok -> value
        is Outcome.Denied -> error("Refused: $code")
    }

    private fun Outcome<*>.code(): String = (this as Outcome.Denied).code

    private fun store(signer: LocalEventSigner, kind: Int, content: String): SignedEvent =
        runBlocking { signer.sign(kind = kind, content = content, createdAt = 1_000) }.also { events.store(it) }

    /** A search of Alice's key over the last two days and today. */
    private fun aliceSearch(): RequisitionSearch =
        RequisitionSearch.parse(alice.publicKey.npub, null, today.minusDays(2).toString(), today.toString()).ok()

    /** A new session of [member] at the clock (the old one may have ended after the clock moved). */
    private fun signIn(backOffice: BackOffice, member: TestMember): Authenticated {
        val opened = TestDatabase.dataSource.transaction { BackOfficeTestData.sessions.open(it, member.id, clock.instant()) }
        return requireNotNull(backOffice.authenticate(opened.token))
    }

    @Test
    fun theSearchIsCheckedBeforeAnythingIsAsked() {
        assertEquals("search_required", RequisitionSearch.parse(" ", "", "2026-11-01", "2026-11-07").code())
        assertEquals("bad_key", RequisitionSearch.parse("npub1nimportequoi", null, "2026-11-01", "2026-11-07").code())
        assertEquals("bad_event_id", RequisitionSearch.parse(null, "1234", "2026-11-01", "2026-11-07").code())
        assertEquals("period_required", RequisitionSearch.parse(alice.publicKey.hex, null, "2026-11-01", null).code())
        assertEquals("bad_date", RequisitionSearch.parse(alice.publicKey.hex, null, "01/11/2026", "2026-11-07").code())
        assertEquals("bad_period", RequisitionSearch.parse(alice.publicKey.hex, null, "2026-11-08", "2026-11-07").code())
        val search = RequisitionSearch.parse(alice.publicKey.npub, "A".repeat(64), "2026-11-07", "2026-11-07").ok()
        assertEquals(alice.publicKey.hex, search.pubkey)
        assertEquals("a".repeat(64), search.eventId)
        // One day of Paris time, end included.
        assertEquals(Instant.parse("2026-11-06T23:00:00Z"), search.from)
        assertEquals(Instant.parse("2026-11-07T23:00:00Z"), search.until)
        assertEquals(search, RequisitionSearch.of(search.payload()))
    }

    @Test
    fun aRequisitionNeedsTwoPeopleIsAnsweredOnceAndTheJournalNeverHoldsTheRows() {
        val note = store(alice, EventKinds.TEXT_NOTE, "[EXEMPLE] message public")
        val profile = store(alice, EventKinds.METADATA, """{"name":"[EXEMPLE] Alice","display_name":"Al"}""")
        val other = store(bob, EventKinds.TEXT_NOTE, "[EXEMPLE] message de Bob")
        val backOffice = BackOfficeTestData.backOffice(clock)
        val requester = member(backOffice, "Admin", listOf(TeamRole.ADMIN))
        val approver = member(backOffice, "Claire", listOf(TeamRole.SUPER_ADMIN))
        val third = member(backOffice, "Admin2", listOf(TeamRole.ADMIN))
        val moderator = member(backOffice, "Modo", listOf(TeamRole.MODERATOR))
        val start = lastSeq()
        val reference = "[EXEMPLE] Réquisition de l’OPJ de Lyon, n° 2026/123, du 7 novembre 2026"

        assertEquals("forbidden", backOffice.requestRequisition(auth(backOffice, moderator), reference, aliceSearch()).code())
        assertEquals("reference_required", backOffice.requestRequisition(auth(backOffice, requester), "  ", aliceSearch()).code())
        assertEquals("forbidden", backOffice.requisitions(auth(backOffice, moderator).actor).code())
        val request = backOffice.requestRequisition(auth(backOffice, requester), reference, aliceSearch()).ok()
        assertEquals(RequisitionState.WAITING, LegalRequisitions.state(request, clock.instant()))
        assertEquals(listOf(request.id), backOffice.requisitions(auth(backOffice, requester).actor).ok().map { it.id })

        // Nobody reads before a second person agrees; the requester never agrees with themself.
        assertEquals("requisition_not_approved", backOffice.answerRequisition(auth(backOffice, requester), request.id).code())
        assertEquals("own_request", backOffice.approve(auth(backOffice, requester), request.id).code())
        assertEquals("forbidden", backOffice.approve(auth(backOffice, moderator), request.id).code())
        assertTrue(backOffice.openApprovals(auth(backOffice, moderator).actor).none { it.id == request.id }, "only the holders of the permission see it")
        assertEquals(ApprovalStatus.APPROVED, backOffice.approve(auth(backOffice, approver), request.id).ok())
        val approved = assertNotNull(backOffice.approval(request.id))
        assertTrue(LegalRequisitions.mayDownload(approved, requester.id, clock.instant()))
        assertTrue(LegalRequisitions.mayDownload(approved, approver.id, clock.instant()))
        assertFalse(LegalRequisitions.mayDownload(approved, third.id, clock.instant()))

        // A third administrator may not download it; the requester does, once.
        assertEquals("forbidden", backOffice.answerRequisition(auth(backOffice, third), request.id).code())
        val answer = backOffice.answerRequisition(auth(backOffice, requester), request.id).ok()
        assertEquals(reference, answer.reference)
        assertEquals(listOf(note.id, profile.id), answer.rows.contentOperations.map { it.jsonObject["event_id"]!!.jsonPrimitive.content })
        assertEquals(listOf("[EXEMPLE] Alice"), answer.rows.pseudonyms.map { it.jsonObject["name"]!!.jsonPrimitive.content })
        assertEquals("https-api", answer.rows.contentOperations[0].jsonObject["protocol"]!!.jsonPrimitive.content)
        assertEquals("requisition_answered", backOffice.answerRequisition(auth(backOffice, approver), request.id).code())
        // The database itself refuses a second answer, whatever the application says.
        val again = runCatching { TestDatabase.dataSource.transaction { LegalRegister.answerRequisition(it, request.id) } }.exceptionOrNull()
        assertEquals("requisition_answered", (again as LegalRegister.RequisitionRefused).code)

        // The file for the authority.
        val file = Json.parseToJsonElement(LegalRequisitions.json(answer)).jsonObject
        assertEquals(LegalRequisitions.FORMAT, file["format"]!!.jsonPrimitive.content)
        assertEquals(reference, file["reference"]!!.jsonPrimitive.content)
        assertEquals(alice.publicKey.hex, file["search"]!!.jsonObject["pubkey"]!!.jsonPrimitive.content)
        assertEquals(2, file["content_operations"]!!.jsonArray.size)
        assertFalse(LegalRequisitions.json(answer).contains(other.id), "nothing about another key")
        assertFalse(LegalRequisitions.json(answer).contains("message public"), "never a content text")

        // The search left the request; the counts stay.
        val answered = assertNotNull(backOffice.approval(request.id))
        assertNull(RequisitionSearch.of(answered.payload))
        assertTrue(RequisitionSearch.FIELDS.none { it in answered.payload })
        assertEquals("2", answered.payload["operations"])
        assertEquals("1", answered.payload["pseudonyms"])
        assertEquals(RequisitionState.ANSWERED, LegalRequisitions.state(answered, clock.instant()))

        // The journal: the reference and the counts, never a key, an event id, a pseudonym nor the period.
        val lines = journalAfter(start)
        assertEquals(listOf("approval.requested", "approval.approved", LegalRequisitions.ACTION_ANSWERED), lines.map { it.action })
        val done = lines.last()
        assertEquals(reference, done.reason)
        assertEquals(mapOf("approval" to request.id.toString(), "operations" to "2", "pseudonyms" to "1"), done.details)
        assertEquals(requester.id.toString(), done.actorId)
        val journal = lines.joinToString(" ") { listOf(it.reason, it.targetId, it.details.toString()).joinToString(" ") }
        listOf(alice.publicKey.hex, note.id, profile.id, "[EXEMPLE] Alice", aliceSearch().from.epochSecond.toString()).forEach { secret ->
            assertFalse(journal.contains(secret), "the journal never holds $secret")
        }

        // The public counter.
        val month = LegalRequisitions.utc(clock.instant()).substring(0, 7)
        assertEquals(listOf(month to 1L), LegalRegister.requisitionsByMonth(TestDatabase.dataSource).map { (m, n) -> m to n }.filter { it.first == month })
    }

    @Test
    fun anAnswerNotDownloadedInTimeOrARefusalForgetsTheSearch() {
        val backOffice = BackOfficeTestData.backOffice(clock)
        val requester = member(backOffice, "Admin", listOf(TeamRole.ADMIN))
        val approver = member(backOffice, "Claire", listOf(TeamRole.SUPER_ADMIN))
        val late = backOffice.requestRequisition(auth(backOffice, requester), "[EXEMPLE] réquisition 1", aliceSearch()).ok()
        val refused = backOffice.requestRequisition(auth(backOffice, requester), "[EXEMPLE] réquisition 2", aliceSearch()).ok()
        backOffice.approve(auth(backOffice, approver), late.id).ok()
        backOffice.refuseApproval(auth(backOffice, approver), refused.id, null).ok()
        backOffice.maintain()
        assertNull(RequisitionSearch.of(backOffice.approval(refused.id)!!.payload), "a refused search is forgotten")
        assertNotNull(RequisitionSearch.of(backOffice.approval(late.id)!!.payload), "an approved one waits for its download")
        clock.advance(8.days)
        val again = signIn(backOffice, requester)
        assertEquals("requisition_lapsed", backOffice.answerRequisition(again, late.id).code())
        backOffice.maintain()
        val lapsed = backOffice.approval(late.id)!!
        assertTrue(RequisitionSearch.FIELDS.none { it in lapsed.payload })
        assertEquals(RequisitionState.LAPSED, LegalRequisitions.state(lapsed, clock.instant()))
        assertEquals("requisition_lapsed", backOffice.answerRequisition(signIn(backOffice, requester), late.id).code())
        assertEquals(emptyList(), LegalRegister.requisitionsByMonth(TestDatabase.dataSource), "nothing answered, nothing counted")
    }

    @Test
    fun askingAndDownloadingAreSensitive() {
        val backOffice = BackOfficeTestData.backOffice(clock)
        val requester = member(backOffice, "Admin", listOf(TeamRole.ADMIN))
        val approver = member(backOffice, "Claire", listOf(TeamRole.SUPER_ADMIN))
        val request = backOffice.requestRequisition(auth(backOffice, requester), "[EXEMPLE] réquisition", aliceSearch()).ok()
        backOffice.approve(auth(backOffice, approver), request.id).ok()
        clock.advance(6.minutes)
        assertEquals("confirmation_required", backOffice.requestRequisition(auth(backOffice, requester), "[EXEMPLE] autre", aliceSearch()).code())
        assertEquals("confirmation_required", backOffice.answerRequisition(auth(backOffice, requester), request.id).code())
    }

    // Over HTTP ----------------------------------------------------------------------------------------------

    private val bo = "http://localhost:${BackOfficeTestData.PORT}"

    private fun ApplicationTestBuilder.start(): HttpClient {
        engine {
            connector {
                host = "localhost"
                port = 8080
            }
            connector {
                host = "localhost"
                port = BackOfficeTestData.PORT
            }
        }
        application {
            module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY, testRevendications(), testAgir(), backOffice = BackOfficeSetup(BackOfficeTestData.CONFIG, clock))
        }
        return createClient { followRedirects = false }
    }

    private suspend fun HttpClient.page(path: String, cookie: String): HttpResponse = get("$bo$path") { header(HttpHeaders.Cookie, "$SESSION_COOKIE=$cookie") }

    private suspend fun HttpClient.form(path: String, cookie: String, fields: Map<String, String>): HttpResponse =
        submitForm("$bo$path", parameters { (fields + (Csrf.FIELD to Csrf.tokenFor(cookie))).forEach { (key, value) -> append(key, value) } }) {
            header(HttpHeaders.Cookie, "$SESSION_COOKIE=$cookie")
            header("Sec-Fetch-Site", "same-origin")
        }

    @Test
    fun theRequisitionPagesAskTwoPeopleAndGiveTheFileOnce() = testApplication {
        val client = start()
        store(alice, EventKinds.TEXT_NOTE, "[EXEMPLE] message public")
        val data = BackOfficeTestData.backOffice(clock)
        val requester = member(data, "Admin", listOf(TeamRole.ADMIN))
        val approver = member(data, "Claire", listOf(TeamRole.SUPER_ADMIN))
        val moderator = member(data, "Modo", listOf(TeamRole.MODERATOR))
        assertEquals(HttpStatusCode.Forbidden, client.page("/requisitions", moderator.token).status)
        assertFalse(client.page("/", moderator.token).bodyAsText().contains("href=\"/requisitions\""))
        val page = client.page("/requisitions", requester.token)
        assertEquals(HttpStatusCode.OK, page.status)
        val html = page.bodyAsText()
        assertTrue(html.contains("href=\"/requisitions\""))
        assertTrue(html.contains(BackOfficeTexts.REQUISITION_REFERENCE))
        assertTrue(html.contains("name=\"reference\""))

        val fields = mapOf("reference" to "[EXEMPLE] Réquisition n° 42", "cle" to alice.publicKey.hex, "du" to today.minusDays(1).toString(), "au" to today.toString())
        assertEquals("/requisitions?erreur=search_required", client.form("/requisitions", requester.token, fields - "cle").headers[HttpHeaders.Location])
        assertEquals("/requisitions?ok=requested", client.form("/requisitions", requester.token, fields).headers[HttpHeaders.Location])
        val request = data.requisitions(auth(data, requester).actor).ok().single()
        // The second person sees the search before agreeing.
        val approvals = client.page("/validations", approver.token).bodyAsText()
        assertTrue(approvals.contains(alice.publicKey.hex))
        assertTrue(approvals.contains("[EXEMPLE] Réquisition n° 42"))
        assertEquals("/validations?ok=approved", client.form("/validations/${request.id}/accord", approver.token, emptyMap()).headers[HttpHeaders.Location])
        assertTrue(client.page("/requisitions", requester.token).bodyAsText().contains("/requisitions/${request.id}/reponse"))

        val download = client.form("/requisitions/${request.id}/reponse", requester.token, emptyMap())
        assertEquals(HttpStatusCode.OK, download.status)
        assertTrue(download.headers[HttpHeaders.ContentDisposition].orEmpty().startsWith("attachment; filename=\"reponse-requisition-"))
        assertEquals("no-store", download.headers[HttpHeaders.CacheControl])
        val file = Json.parseToJsonElement(download.bodyAsText()).jsonObject
        assertEquals(1, file["content_operations"]!!.jsonArray.size)
        val second = client.form("/requisitions/${request.id}/reponse", approver.token, emptyMap())
        assertEquals("/requisitions?erreur=requisition_answered", second.headers[HttpHeaders.Location])

        // The public counter, on the public port only.
        val counter = client.get("http://localhost:8080/api/v1/transparence/requisitions").bodyAsText()
        assertTrue(counter.contains("\"answered\":1"), counter)
        assertFalse(counter.contains(alice.publicKey.hex))
        assertFalse(counter.contains("42"), "no reference")
        assertEquals(HttpStatusCode.NotFound, client.get("$bo/api/v1/transparence/requisitions").status)
    }
}
