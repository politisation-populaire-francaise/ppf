package org.politisationpopulairefrancaise.server.contact

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.contact.CaseApi
import org.politisationpopulairefrancaise.core.contact.ReportReason
import org.politisationpopulairefrancaise.core.contact.ContentReports
import org.politisationpopulairefrancaise.core.contact.CaseCode
import org.politisationpopulairefrancaise.core.contact.CaseCreated
import org.politisationpopulairefrancaise.core.contact.CaseCreation
import org.politisationpopulairefrancaise.core.contact.CaseEnvelopes
import org.politisationpopulairefrancaise.core.contact.CaseFollowUp
import org.politisationpopulairefrancaise.core.contact.CaseMessage
import org.politisationpopulairefrancaise.core.contact.CaseMessageAccepted
import org.politisationpopulairefrancaise.core.contact.CaseNature
import org.politisationpopulairefrancaise.core.contact.CaseNumber
import org.politisationpopulairefrancaise.core.contact.CaseSecrets
import org.politisationpopulairefrancaise.core.contact.CaseState
import org.politisationpopulairefrancaise.core.contact.CaseStatus
import org.politisationpopulairefrancaise.core.contact.CaseType
import org.politisationpopulairefrancaise.core.contact.ClaimMotif
import org.politisationpopulairefrancaise.core.contact.ContactCategory
import org.politisationpopulairefrancaise.core.contact.ContactInbox
import org.politisationpopulairefrancaise.core.contact.DeadlineKind
import org.politisationpopulairefrancaise.core.contact.TicketSubtype
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.messaging.LocalMessagingSigner
import org.politisationpopulairefrancaise.server.NO_EDITORS
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.testAgir
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeTestData
import org.politisationpopulairefrancaise.server.backoffice.MutableClock
import org.politisationpopulairefrancaise.server.module
import org.politisationpopulairefrancaise.server.relay.RateLimiter
import org.politisationpopulairefrancaise.server.testRevendications
import java.time.Instant
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/** The public routes of the cases on the embedded PostgreSQL, with the envelopes the app will build (shared/core). */
class ContactApiTest {
    private val clock = MutableClock(Instant.parse("2026-11-08T10:00:00Z"))
    private val store = ContactStore(TestDatabase.dataSource)

    @BeforeTest
    fun emptyTables() = BackOfficeTestData.reset()

    private fun ApplicationTestBuilder.server(setup: ContactSetup = ContactSetup(ContactFixtures.config(), clock)): HttpClient {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY, testRevendications(), testAgir(), contact = setup, backOffice = null) }
        return client
    }

    /** Only the routes of the cases, with ceilings of one creation and one message a minute. */
    private fun ApplicationTestBuilder.limitedServer(space: ContactSpace) {
        application {
            install(ContentNegotiation) { json() }
            routing { contactApi(space, ContactLimits(creations = RateLimiter(1, maxTrackedKeys = 1), messages = RateLimiter(1, maxTrackedKeys = 1))) }
        }
    }

    private fun draft(nature: CaseNature = CONTACT, decisionAt: Long? = null, work: Int = ContactFixtures.WORK) =
        ContactFixtures.Draft(nature, clock.instant(), decisionAt = decisionAt, work = work)

    private suspend fun HttpClient.create(creation: CaseCreation): HttpResponse = createRaw(CaseApi.json.encodeToString(CaseCreation.serializer(), creation))

    private suspend fun HttpClient.createRaw(body: String): HttpResponse = post(CaseApi.CASES) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend fun HttpClient.state(number: String, authorization: String?, after: String? = null): HttpResponse =
        get("${CaseApi.CASES}/$number" + (after?.let { "?after=$it" } ?: "")) { authorization?.let { header(HttpHeaders.Authorization, it) } }

    private suspend fun HttpClient.followUp(number: String, authorization: String?, envelope: org.politisationpopulairefrancaise.core.signing.SignedEvent): HttpResponse =
        post("${CaseApi.CASES}/$number/messages") {
            authorization?.let { header(HttpHeaders.Authorization, it) }
            contentType(ContentType.Application.Json)
            setBody(CaseApi.json.encodeToString(CaseFollowUp.serializer(), CaseFollowUp(envelope)))
        }

    private suspend fun created(response: HttpResponse): CaseCreated = CaseApi.json.decodeFromString(CaseCreated.serializer(), response.bodyAsText())

    private suspend fun stateOf(response: HttpResponse): CaseState = CaseApi.json.decodeFromString(CaseState.serializer(), response.bodyAsText())

    private suspend fun errorOf(response: HttpResponse): String = response.bodyAsText().substringAfter("\"error\":\"").substringBefore('"')

    /** A member of the team, as a row only (the replies of the team name their author). */
    private fun teamAccount(): UUID = TestDatabase.dataSource.connection.use { connection ->
        BackOfficeTestData.accounts.create(connection, "Équipe ${UUID.randomUUID().toString().take(8)}", ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }, null, clock.instant()).id
    }

    @Test
    fun aReportKeepsItsContentAndReasonInClearAndTheReportsAreGroupedByContent() = testApplication {
        // S-38, D-234: the address of the content and the reason in clear, the comment encrypted, the author unknown.
        val client = server()
        val address = "30023:${"a".repeat(64)}:appel-exemple"
        val now = clock.instant().epochSecond
        repeat(2) {
            val prepared = runBlocking { ContentReports.prepare(address, ReportReason.HATE, "[EXEMPLE] Un commentaire.", ContactInbox.EXAMPLE, now) }
            assertEquals(HttpStatusCode.Created, client.create(prepared.creation).status)
            assertEquals(address, assertNotNull(store.find(prepared.creation.number)).reportContent)
        }
        assertEquals(listOf(ReportedContent(address, 2, listOf("haine"), clock.instant())), store.reportsByContent())
        // Only a report carries them, well formed.
        val bug = draft(CaseNature.Ticket(TicketSubtype.BUG)).creation().copy(reportContent = address, reportReason = "spam")
        val refused = client.create(bug)
        assertEquals(422, refused.status.value)
        assertEquals("bad_report", errorOf(refused))
        val badAddress = draft(CaseNature.Ticket(TicketSubtype.REPORT)).creation().copy(reportContent = "pas une adresse", reportReason = "spam")
        assertEquals("bad_report", errorOf(client.create(badAddress)))
    }

    @Test
    fun aCaseIsCreatedWithItsDeadlineAndTheSameCreationSentAgainGetsTheSameAnswer() = testApplication {
        val client = server()
        val draft = draft()
        val response = client.create(draft.creation())
        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        val answer = created(response)
        assertEquals(CaseCreated(draft.number.display, CaseStatus.RECEIVED, clock.instant().epochSecond + 15 * 86_400, DeadlineKind.GOAL), answer)
        val stored = assertNotNull(store.find(draft.number.display))
        assertEquals(draft.secrets.accessHash, stored.accessHash)
        assertEquals(draft.secrets.publicKey.hex, stored.casePubkey)
        assertEquals(1 to 0, store.envelopeCounts(draft.number.display))
        // Sent again (the network failed after the server stored it): the same answer, nothing new.
        val again = client.create(draft.creation())
        assertEquals(HttpStatusCode.OK, again.status)
        assertEquals(answer, created(again))
        assertEquals(1 to 0, store.envelopeCounts(draft.number.display))
        // The same number with another code: the device draws another number.
        val other = ContactFixtures.Draft(CONTACT, clock.instant(), number = draft.number)
        val taken = client.create(other.creation())
        assertEquals(HttpStatusCode.Conflict, taken.status)
        assertEquals("number_taken", errorOf(taken))
        // The opening envelope of a case cannot open another one.
        val stolen = client.create(draft().creation(draft.envelope))
        assertEquals(HttpStatusCode.Conflict, stolen.status)
        assertEquals("envelope_taken", errorOf(stolen))
    }

    @Test
    fun theLegalDeadlinesAndTheSixMonthsOfAContestedDecisionAreApplied() = testApplication {
        val client = server()
        val now = clock.instant().epochSecond
        val data = created(client.create(draft(CaseNature.Claim(ClaimMotif.DATA_RIGHTS)).creation()))
        // 8 November 2026, 11:00 in Paris: 8 December, same hour.
        assertEquals(Instant.parse("2026-12-08T10:00:00Z").epochSecond, data.deadlineAt)
        assertEquals(DeadlineKind.LEGAL, data.deadlineKind)
        val reply = created(client.create(draft(CaseNature.Claim(ClaimMotif.RIGHT_OF_REPLY)).creation()))
        assertEquals(now + 3 * 86_400, reply.deadlineAt)
        assertEquals(DeadlineKind.LEGAL, reply.deadlineKind)
        val bug = created(client.create(draft(CaseNature.Ticket(TicketSubtype.BUG)).creation()))
        assertNull(bug.deadlineAt)
        assertNull(bug.deadlineKind)

        val moderation = CaseNature.Claim(ClaimMotif.MODERATION_DECISION)
        val recent = client.create(draft(moderation, decisionAt = now - 30 * 86_400).creation())
        assertEquals(HttpStatusCode.Created, recent.status)
        assertEquals(DeadlineKind.GOAL, created(recent).deadlineKind)
        listOf(
            draft(moderation, decisionAt = null) to "decision_required",
            draft(moderation, decisionAt = now - 200 * 86_400) to "decision_out_of_time",
            draft(moderation, decisionAt = now + 3_600) to "decision_out_of_time",
        ).forEach { (refused, code) ->
            val response = client.create(refused.creation())
            assertEquals(HttpStatusCode.UnprocessableEntity, response.status, code)
            assertEquals(code, errorOf(response))
        }
        val withDecision = draft(CONTACT).let { it.creation().copy(decisionAt = now - 86_400) }
        assertEquals("bad_decision", errorOf(client.create(withDecision)))
    }

    @Test
    fun theRequestAndItsEnvelopeAreChecked() = testApplication {
        val client = server()
        val valid = draft()
        suspend fun expect(code: String, creation: CaseCreation) {
            val response = client.create(creation)
            assertEquals(HttpStatusCode.UnprocessableEntity, response.status, code)
            assertEquals(code, errorOf(response))
        }
        expect("not_enough_work", draft(work = 0).creation())
        val elsewhere = runBlocking {
            CaseEnvelopes.opening(valid.opening, valid.secrets.signer(), IdentityKeys.generate().publicKey, ContactFixtures.WORK, clock.instant().epochSecond)
        }
        expect("wrong_recipient", valid.creation(elsewhere))
        expect("invalid_signature", valid.creation(valid.envelope.copy(sig = "0".repeat(128))))
        expect("bad_date", ContactFixtures.Draft(CONTACT, clock.instant().minusSeconds(4 * 86_400)).creation())
        expect("bad_number", valid.creation().copy(number = "PPF-ABCDE-1234U"))
        expect("bad_nature", valid.creation().copy(type = CaseType.TICKET))
        expect("bad_nature", valid.creation().copy(type = CaseType.CLAIM, category = null, motif = ClaimMotif.FUNDS, subtype = TicketSubtype.BUG))
        expect("bad_access_hash", valid.creation().copy(accessHash = "AB".repeat(32)))
        // An x coordinate above the field size is no point of the curve.
        expect("bad_case_pubkey", valid.creation().copy(casePubkey = "f".repeat(64)))
        assertEquals(HttpStatusCode.BadRequest, client.createRaw("{\"number\":1}").status)
        assertEquals(HttpStatusCode.BadRequest, client.createRaw("not json").status)
        assertEquals(HttpStatusCode.PayloadTooLarge, client.createRaw("x".repeat(ContactSpace.MAX_CREATION_BYTES.toInt() + 1)).status)
        assertEquals(HttpStatusCode.Created, client.create(valid.creation()).status)
    }

    @Test
    fun withoutAnInboxKeyEveryRouteAnswersThatContactIsNotOpen() = testApplication {
        val closed = ContactConfig.fromEnvironment(mapOf("PPF_ENVIRONMENT" to "production")::get)
        assertFalse(closed.open)
        val client = server(ContactSetup(closed, clock))
        val draft = draft()
        listOf(
            client.create(draft.creation()),
            client.state(draft.number.display, draft.secrets.authorization),
            client.followUp(draft.number.display, draft.secrets.authorization, draft.followUp()),
        ).forEach { response ->
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertEquals("contact_not_open", errorOf(response))
        }
    }

    @Test
    fun theSettingsComeFromTheEnvironment() {
        fun config(vararg values: Pair<String, String>) = ContactConfig.fromEnvironment(mapOf(*values)::get)
        val key = IdentityKeys.generate().publicKey
        // Development without a key: the example inbox, open.
        assertEquals(ContactInbox.EXAMPLE, config().inbox)
        assertTrue(config().open && config().isExample)
        // Elsewhere, no key: closed; the example key is refused in production only.
        assertFalse(config("PPF_ENVIRONMENT" to "preproduction").open)
        assertFalse(config("PPF_ENVIRONMENT" to "production", "PPF_CONTACT_INBOX_KEY" to ContactInbox.EXAMPLE.hex).open)
        assertTrue(config("PPF_ENVIRONMENT" to "preproduction", "PPF_CONTACT_INBOX_KEY" to ContactInbox.EXAMPLE.hex).open)
        assertEquals(key, config("PPF_ENVIRONMENT" to "production", "PPF_CONTACT_INBOX_KEY" to key.npub).inbox)
        assertTrue(config("PPF_ENVIRONMENT" to "production", "PPF_CONTACT_INBOX_KEY" to key.hex.uppercase()).open)
        // A malformed key closes, even in development.
        assertFalse(config("PPF_CONTACT_INBOX_KEY" to "pas une clé").open)
        // The proof of work and the goals.
        assertEquals(16, config().workBits)
        assertEquals(20, config("PPF_CASE_POW_BITS" to "20").workBits)
        assertEquals(16, config("PPF_CASE_POW_BITS" to "99").workBits)
        val goals = config("PPF_CASE_GOAL_CONTACT_DAYS" to "10", "PPF_CASE_GOAL_FUNDS_DAYS" to "0", "PPF_CASE_GOAL_BUG_DAYS" to "7", "PPF_CASE_GOAL_OTHER_DAYS" to "999").goals
        assertEquals(10, goals.contactDays)
        assertNull(goals.fundsDays)
        assertEquals(7, goals.bugDays)
        assertEquals(30, goals.otherClaimDays)
        // The requests of verification (CO-18, D-143): 15 days unless set.
        assertEquals(15, config().goals.verificationDays)
        assertEquals(20, config("PPF_CASE_GOAL_VERIFICATION_DAYS" to "20").goals.verificationDays)
        assertNull(config("PPF_CASE_GOAL_VERIFICATION_DAYS" to "0").goals.verificationDays)
        assertEquals(15, config("PPF_CASE_GOAL_VERIFICATION_DAYS" to "400").goals.verificationDays)
        assertEquals(0, config("PPF_CONTACT_RETENTION_INTERVAL_MINUTES" to "0").retentionInterval.inWholeMinutes)
    }

    @Test
    fun theTokenOpensTheCaseAndWrongTriesLockTheNumberKnownOrNot() = testApplication {
        val client = server()
        val draft = draft()
        client.create(draft.creation())
        val number = draft.number.display
        val open = client.state(number, draft.secrets.authorization)
        assertEquals(HttpStatusCode.OK, open.status)
        assertEquals("no-store", open.headers[HttpHeaders.CacheControl])
        assertEquals(CaseState(number, CaseStatus.RECEIVED, clock.instant().epochSecond + 15 * 86_400, DeadlineKind.GOAL), stateOf(open))

        val wrong = CaseSecrets.derive(draft.number, CaseCode.generate()).authorization
        val unknown = CaseNumber.generate().display
        val refusals = listOf(
            client.state(number, wrong),
            client.state(number, null),
            client.state(number, "Bearer pas-un-jeton"),
            client.state(unknown, draft.secrets.authorization),
        )
        // The same answer for a wrong token and an unknown number.
        refusals.forEach { assertEquals(HttpStatusCode.NotFound, it.status) }
        val bodies = mutableSetOf<String>()
        for (refusal in refusals) bodies += refusal.bodyAsText()
        assertEquals(1, bodies.size)
        assertEquals(HttpStatusCode.NotFound, client.state("PPF-ABCDE", draft.secrets.authorization).status)

        // A right token forgets the wrong tries; five wrong tries in a row lock the number, the right token too.
        assertEquals(HttpStatusCode.OK, client.state(number, draft.secrets.authorization).status)
        repeat(5) { assertEquals(HttpStatusCode.NotFound, client.state(number, wrong).status) }
        val locked = client.state(number, draft.secrets.authorization)
        assertEquals(HttpStatusCode.TooManyRequests, locked.status)
        assertEquals("locked", errorOf(locked))
        assertEquals((15.minutes.inWholeSeconds).toString(), locked.headers[HttpHeaders.RetryAfter])
        assertEquals(HttpStatusCode.TooManyRequests, client.followUp(number, draft.secrets.authorization, draft.followUp()).status)
        // An unknown number locks the same way: the lock says nothing about existence.
        repeat(5) { client.state(unknown, wrong) }
        assertEquals("locked", errorOf(client.state(unknown, wrong)))
        clock.advance(15.minutes)
        assertEquals(HttpStatusCode.OK, client.state(number, draft.secrets.authorization).status)
        assertEquals(HttpStatusCode.NotFound, client.state(unknown, wrong).status)
    }

    @Test
    fun theRepliesOfTheTeamAreReadAfterTheLastOneSeen() = testApplication {
        val client = server()
        val draft = draft()
        client.create(draft.creation())
        val number = draft.number.display
        val author = teamAccount()
        val replies = (1..22).map { index ->
            draft.reply("[EXEMPLE] Réponse $index").also { assertTrue(store.addOutgoing(number, it, author, clock.instant())) }
        }
        val first = stateOf(client.state(number, draft.secrets.authorization))
        assertEquals(replies.take(20).map { it.id }, first.envelopes.map { it.id })
        assertTrue(first.more)
        // The app opens them with the key of the case, and accepts only the inbox built into it.
        assertEquals("[EXEMPLE] Réponse 1", CaseEnvelopes.openFromTeam(first.envelopes.first(), draft.secrets, ContactInbox.EXAMPLE).message.text)
        val rest = stateOf(client.state(number, draft.secrets.authorization, after = first.envelopes.last().id))
        assertEquals(replies.drop(20).map { it.id }, rest.envelopes.map { it.id })
        assertFalse(rest.more)
        assertEquals(emptyList(), stateOf(client.state(number, draft.secrets.authorization, after = replies.last().id)).envelopes)
        // The messages of the person never come back to the app.
        assertEquals(HttpStatusCode.Created, client.followUp(number, draft.secrets.authorization, draft.followUp()).status)
        assertEquals(emptyList(), stateOf(client.state(number, draft.secrets.authorization, after = replies.last().id)).envelopes)
    }

    @Test
    fun aFollowUpPutsTheCaseBackInProgressAndAClosedCaseRefusesIt() = testApplication {
        val client = server()
        val draft = draft()
        client.create(draft.creation())
        val number = draft.number.display
        // A case just received stays received.
        val first = client.followUp(number, draft.secrets.authorization, draft.followUp("[EXEMPLE] Un oubli."))
        assertEquals(HttpStatusCode.Created, first.status)
        assertEquals(CaseStatus.RECEIVED, CaseApi.json.decodeFromString(CaseMessageAccepted.serializer(), first.bodyAsText()).status)
        store.change(number) { _, case -> case.copy(status = CaseStatus.AWAITING_YOU) to Unit }
        clock.advance(1.days)
        val envelope = draft.followUp("[EXEMPLE] Voici la réponse demandée.", clock.instant())
        val answered = client.followUp(number, draft.secrets.authorization, envelope)
        assertEquals(HttpStatusCode.Created, answered.status)
        assertEquals(CaseMessageAccepted(envelope.id, CaseStatus.IN_PROGRESS), CaseApi.json.decodeFromString(CaseMessageAccepted.serializer(), answered.bodyAsText()))
        val case = assertNotNull(store.find(number))
        assertEquals(clock.instant(), case.lastActivityAt)
        assertEquals(3 to 0, store.envelopeCounts(number))
        // The same envelope again: accepted once.
        assertEquals(HttpStatusCode.OK, client.followUp(number, draft.secrets.authorization, envelope).status)
        assertEquals(3 to 0, store.envelopeCounts(number))
        // Checked like any envelope; no proof of work is asked for a follow-up.
        val misaddressed = runBlocking {
            CaseEnvelopes.followUp(CaseMessage.followUp(draft.number, "[EXEMPLE] x"), draft.secrets.signer(), draft.secrets.publicKey, clock.instant().epochSecond)
        }
        assertEquals("wrong_recipient", errorOf(client.followUp(number, draft.secrets.authorization, misaddressed)))
        // Another case cannot take an envelope already received.
        val other = draft()
        client.create(other.creation())
        assertEquals("envelope_taken", errorOf(client.followUp(other.number.display, other.secrets.authorization, envelope)))
        assertEquals(HttpStatusCode.NotFound, client.followUp(number, other.secrets.authorization, draft.followUp()).status)
        // Closed: no more messages.
        store.change(number) { _, current -> current.copy(status = CaseStatus.CLOSED, closedAt = clock.instant()) to Unit }
        val refused = client.followUp(number, draft.secrets.authorization, draft.followUp("[EXEMPLE] Encore ?", clock.instant()))
        assertEquals(HttpStatusCode.Conflict, refused.status)
        assertEquals("case_closed", errorOf(refused))
    }

    @Test
    fun theGlobalCeilingsApplyWithoutAnyKeyOfThePerson() = testApplication {
        limitedServer(ContactFixtures.space(clock))
        val first = draft()
        assertEquals(HttpStatusCode.Created, client.create(first.creation()).status)
        val second = client.create(draft().creation())
        assertEquals(HttpStatusCode.TooManyRequests, second.status)
        assertEquals("rate_limited", errorOf(second))
        assertEquals(HttpStatusCode.Created, client.followUp(first.number.display, first.secrets.authorization, first.followUp()).status)
        assertEquals(HttpStatusCode.TooManyRequests, client.followUp(first.number.display, first.secrets.authorization, first.followUp("[EXEMPLE] Encore")).status)
    }

    @Test
    fun theTablesHoldNoAddressNoContactDetailAndNoAccountOfThePerson() {
        val columns = TestDatabase.dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT column_name FROM information_schema.columns WHERE table_name IN ('contact_case', 'contact_envelope', 'contact_legal_hold_request')").use { statement ->
                val rows = statement.executeQuery()
                buildList { while (rows.next()) add(rows.getString(1)) }
            }
        }
        assertTrue(columns.contains("access_hash") && columns.contains("event"))
        listOf("ip", "address", "email", "mail", "phone", "device", "user_agent", "account").forEach { forbidden ->
            assertFalse(columns.any { it == forbidden || it.startsWith("${forbidden}_") || it.endsWith("_$forbidden") }, forbidden)
        }
    }

    @Test
    fun anAccountMayBeLinkedOnlyInsideTheEncryptedText() = testApplication {
        val client = server()
        val account = LocalMessagingSigner(IdentityKeys.generate())
        val draft = draft(CaseNature.Claim(ClaimMotif.DATA_RIGHTS))
        val linked = runBlocking { CaseEnvelopes.opening(draft.opening, account, ContactInbox.EXAMPLE, ContactFixtures.WORK, clock.instant().epochSecond) }
        assertEquals(HttpStatusCode.Created, client.create(draft.creation(linked)).status)
        // Only the inbox learns who wrote it.
        assertEquals(account.publicKey, CaseEnvelopes.open(linked, ContactFixtures.inbox).author)
        val row = TestDatabase.dataSource.connection.use { connection ->
            connection.createStatement().executeQuery("SELECT row_to_json(c)::text FROM contact_case c").use { rows -> rows.next(); rows.getString(1) }
        }
        assertFalse(row.contains(account.publicKey.hex))
    }

    private companion object {
        val CONTACT = CaseNature.Contact(ContactCategory.CITIZEN)
    }
}
