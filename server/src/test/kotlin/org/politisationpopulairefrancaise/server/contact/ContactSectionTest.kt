package org.politisationpopulairefrancaise.server.contact

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
import org.politisationpopulairefrancaise.core.contact.CaseDeadlines
import org.politisationpopulairefrancaise.core.contact.CaseNature
import org.politisationpopulairefrancaise.core.contact.CaseStatus
import org.politisationpopulairefrancaise.core.contact.ClaimMotif
import org.politisationpopulairefrancaise.core.contact.ContactCategory
import org.politisationpopulairefrancaise.core.contact.TicketSubtype
import org.politisationpopulairefrancaise.server.NO_EDITORS
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.testAgir
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeSetup
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeTestData
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeTestData.journalAfter
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeTestData.lastSeq
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeTestData.member
import org.politisationpopulairefrancaise.server.backoffice.Csrf
import org.politisationpopulairefrancaise.server.backoffice.MutableClock
import org.politisationpopulairefrancaise.server.backoffice.SESSION_COOKIE
import org.politisationpopulairefrancaise.server.backoffice.TeamRole
import org.politisationpopulairefrancaise.server.backoffice.TestMember
import org.politisationpopulairefrancaise.server.module
import org.politisationpopulairefrancaise.server.testRevendications
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/** The section « Échanges » of the back-office (M30, S-107), on the real PostgreSQL of the tests. */
class ContactSectionTest {
    private val clock = MutableClock(Instant.parse("2026-11-08T10:00:00Z"))
    private val bo = "http://localhost:${BackOfficeTestData.PORT}"
    private val section = "/equipe/sections/contact"
    private val space = ContactFixtures.space(clock)
    private val store = space.store()

    @BeforeTest
    fun emptyTables() = BackOfficeTestData.reset()

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
            module(
                TestDatabase.dataSource, NO_EDITORS, TEST_RELAY, testRevendications(), testAgir(),
                contact = ContactSetup(ContactFixtures.config(), clock),
                backOffice = BackOfficeSetup(BackOfficeTestData.CONFIG, clock),
            )
        }
        return createClient { followRedirects = false }
    }

    private val data get() = BackOfficeTestData.backOffice(clock)

    private suspend fun HttpClient.page(path: String, member: TestMember): HttpResponse = get("$bo$path") {
        header(HttpHeaders.Cookie, "$SESSION_COOKIE=${member.token}")
    }

    private suspend fun HttpClient.form(path: String, member: TestMember, vararg fields: Pair<String, String>): HttpResponse =
        submitForm("$bo$path", parameters {
            append("csrf", Csrf.tokenFor(member.token))
            fields.forEach { (key, value) -> append(key, value) }
        }) {
            header(HttpHeaders.Cookie, "$SESSION_COOKIE=${member.token}")
            header("Sec-Fetch-Site", "same-origin")
        }

    private fun open(nature: CaseNature, decisionAt: Long? = null): ContactFixtures.Draft {
        val draft = ContactFixtures.Draft(nature, clock.instant(), decisionAt = decisionAt)
        assertIs<CreateOutcome.Created>(space.create(draft.creation()))
        return draft
    }

    private fun caseOf(draft: ContactFixtures.Draft): StoredCase = assertNotNull(store.find(draft.number.display))

    private fun casePath(draft: ContactFixtures.Draft, sub: String = ""): String = "$section/dossiers/${draft.number.display}" + if (sub.isEmpty()) "" else "/$sub"

    private fun location(response: HttpResponse): String = assertNotNull(response.headers[HttpHeaders.Location], "redirected")

    @Test
    fun theInboxIsSortedByDeadlineThenReceptionAndMarksTheDeadlines() = testApplication {
        val client = start()
        val moderator = member(data, "Modératrice", listOf(TeamRole.MODERATOR))
        val bug = open(CaseNature.Ticket(TicketSubtype.BUG))
        clock.advance(1.minutes)
        val contact = open(CaseNature.Contact(ContactCategory.JOURNALIST))
        clock.advance(1.minutes)
        val reply = open(CaseNature.Claim(ClaimMotif.RIGHT_OF_REPLY))
        val home = client.page(section, moderator)
        assertEquals(HttpStatusCode.OK, home.status)
        val html = home.bodyAsText()
        assertTrue(html.contains(ContactTexts.SECTION_TITLE))
        val order = listOf(reply, contact, bug).map { html.indexOf(it.number.display) }
        assertTrue(order.all { it > 0 } && order == order.sorted(), "by deadline, a case without one last: $order")
        assertTrue(html.contains(ContactTexts.MARK_RIGHT_OF_REPLY))
        assertFalse(html.contains("Texte du dossier"), "the server has no text to show")
        // Two days later, then past the deadline (a session lasts less: a new member signs in each time).
        clock.advance(2.days)
        val later = member(data, "Modératrice deux", listOf(TeamRole.MODERATOR))
        assertTrue(client.page(section, later).bodyAsText().contains(ContactTexts.MARK_TWO_DAYS))
        clock.advance(2.days)
        val last = member(data, "Modératrice trois", listOf(TeamRole.MODERATOR))
        assertTrue(client.page(section, last).bodyAsText().contains(ContactTexts.MARK_OVERDUE))
        // Filters.
        val tickets = client.page("$section?type=ticket", last).bodyAsText()
        assertTrue(tickets.contains(bug.number.display) && !tickets.contains(contact.number.display))
        val journalists = client.page("$section?nature=category:journalist", last).bodyAsText()
        assertTrue(journalists.contains(contact.number.display) && !journalists.contains(bug.number.display))
        store.change(bug.number.display) { _, case -> case.copy(status = CaseStatus.CLOSED, closedAt = clock.instant()) to Unit }
        assertFalse(client.page(section, last).bodyAsText().contains(bug.number.display), "closed cases leave the default view")
        assertTrue(client.page("$section?dossiers=tous", last).bodyAsText().contains(bug.number.display))
        // An editor has no access to the section.
        val editor = member(data, "Éditeur", listOf(TeamRole.EDITOR))
        assertEquals(HttpStatusCode.Forbidden, client.page(section, editor).status)
    }

    @Test
    fun aModeratorAssignsPrioritisesAndChangesTheStatusAndEachChangeIsJournaled() = testApplication {
        val client = start()
        val moderator = member(data, "Modérateur", listOf(TeamRole.MODERATOR))
        val draft = open(CaseNature.Contact(ContactCategory.CITIZEN))
        val number = draft.number.display
        assertEquals(HttpStatusCode.OK, client.page(casePath(draft), moderator).status)
        val seq = lastSeq()
        assertTrue(location(client.form(casePath(draft, "assignation"), moderator, "membre" to "me")).endsWith("ok=assigned"))
        assertEquals(moderator.id, caseOf(draft).assignedTo)
        assertTrue(location(client.form(casePath(draft, "priorite"), moderator, "priorite" to "high")).endsWith("ok=priority"))
        assertEquals(CasePriority.HIGH, caseOf(draft).priority)
        assertTrue(location(client.form(casePath(draft, "statut"), moderator, "statut" to "answered")).endsWith("ok=status"))
        assertTrue(location(client.form(casePath(draft, "statut"), moderator, "statut" to "closed")).endsWith("ok=status"))
        assertEquals(clock.instant(), caseOf(draft).closedAt)
        assertTrue(location(client.form(casePath(draft, "statut"), moderator, "statut" to "closed")).endsWith("erreur=unchanged"))
        val lines = journalAfter(seq)
        assertEquals(
            listOf("section.contact.assigned", "section.contact.priority_changed", "section.contact.status_changed", "section.contact.status_changed"),
            lines.map { it.action },
        )
        lines.forEach { line ->
            assertEquals("contact:$number", line.targetId)
            assertEquals(moderator.id.toString(), line.actorId)
            assertEquals("contact", line.details["type"])
            assertEquals("citizen", line.details["nature"])
        }
        assertEquals(moderator.id.toString(), lines[0].details["to"])
        assertEquals(mapOf("from" to "answered", "to" to "closed"), lines[3].details.filterKeys { it == "from" || it == "to" })
        // Reopening a closed case is a change of status too.
        client.form(casePath(draft, "statut"), moderator, "statut" to "in_progress")
        assertNull(caseOf(draft).closedAt)
        // The base refuses a form without its anti-forgery token, and an unknown number answers 404.
        val forged = client.submitForm("$bo${casePath(draft, "priorite")}", parameters { append("priorite", "normal") }) {
            header(HttpHeaders.Cookie, "$SESSION_COOKIE=${moderator.token}")
            header("Sec-Fetch-Site", "same-origin")
        }
        assertEquals(HttpStatusCode.Forbidden, forged.status)
        assertEquals(CasePriority.HIGH, caseOf(draft).priority)
        assertEquals(HttpStatusCode.NotFound, client.page("$section/dossiers/PPF-00000-00000", moderator).status)
    }

    @Test
    fun claimsAboutFundsOrATeamMemberAreForAdministratorsAndNeverForTheMemberConcerned() = testApplication {
        val client = start()
        val moderator = member(data, "Modératrice", listOf(TeamRole.MODERATOR))
        val first = member(data, "Admin un", listOf(TeamRole.ADMIN))
        val second = member(data, "Admin deux", listOf(TeamRole.ADMIN))
        val funds = open(CaseNature.Claim(ClaimMotif.FUNDS))
        val behaviour = open(CaseNature.Claim(ClaimMotif.TEAM_MEMBER))
        val inboxOfModerator = client.page(section, moderator).bodyAsText()
        listOf(funds, behaviour).forEach { draft ->
            assertFalse(inboxOfModerator.contains(draft.number.display))
            assertEquals(HttpStatusCode.NotFound, client.page(casePath(draft), moderator).status)
            assertEquals(HttpStatusCode.NotFound, client.form(casePath(draft, "assignation"), moderator, "membre" to "me").status)
        }
        assertTrue(client.page(section, first).bodyAsText().let { it.contains(funds.number.display) && it.contains(behaviour.number.display) })
        // The second administrator names the first as the member concerned.
        val seq = lastSeq()
        assertTrue(location(client.form(casePath(behaviour, "mis-en-cause"), second, "membre" to first.id.toString())).endsWith("ok=concerns"))
        assertEquals(first.id, caseOf(behaviour).concerns)
        val line = journalAfter(seq).single()
        assertEquals("section.contact.concerns_set", line.action)
        assertEquals("set", line.details["concerns"])
        assertFalse(line.details.values.any { it.contains(first.id.toString()) }, "the journal never names the member concerned")
        // The first no longer sees the case, anywhere.
        assertFalse(client.page(section, first).bodyAsText().contains(behaviour.number.display))
        assertFalse(client.page("$section?dossiers=tous", first).bodyAsText().contains(behaviour.number.display))
        assertEquals(HttpStatusCode.NotFound, client.page(casePath(behaviour), first).status)
        assertEquals(HttpStatusCode.NotFound, client.form(casePath(behaviour, "mis-en-cause"), first, "membre" to "none").status)
        // Nobody can name themselves, and a member concerned cannot be assigned the case.
        assertTrue(location(client.form(casePath(behaviour, "mis-en-cause"), second, "membre" to second.id.toString())).endsWith("erreur=own_target"))
        assertTrue(location(client.form(casePath(behaviour, "assignation"), second, "membre" to first.id.toString())).endsWith("erreur=not_eligible"))
        assertTrue(location(client.form(casePath(funds, "assignation"), second, "membre" to moderator.id.toString())).endsWith("erreur=not_eligible"))
    }

    @Test
    fun theAuthorOfAContestedDecisionNeverHandlesTheClaim() = testApplication {
        val client = start()
        val reviewer = member(data, "Relectrice", listOf(TeamRole.MODERATOR))
        val author = member(data, "Auteur", listOf(TeamRole.MODERATOR))
        val claim = open(CaseNature.Claim(ClaimMotif.MODERATION_DECISION), decisionAt = clock.instant().epochSecond - 86_400)
        assertTrue(client.page(casePath(claim), reviewer).bodyAsText().contains(ContactTexts.FACT_DECISION))
        assertTrue(location(client.form(casePath(claim, "auteur-decision"), reviewer, "membre" to author.id.toString())).endsWith("ok=decision_author"))
        assertEquals(author.id, caseOf(claim).decisionAuthor)
        // The author still sees the case exists, but cannot handle it nor be given it, nor change who the author is.
        val page = client.page(casePath(claim), author).bodyAsText()
        assertFalse(page.contains(ContactTexts.STATUS_BUTTON))
        assertTrue(location(client.form(casePath(claim, "statut"), author, "statut" to "closed")).endsWith("erreur=decision_author"))
        assertTrue(location(client.form(casePath(claim, "assignation"), author, "membre" to "me")).endsWith("erreur=not_eligible"))
        assertTrue(location(client.form(casePath(claim, "assignation"), reviewer, "membre" to author.id.toString())).endsWith("erreur=not_eligible"))
        assertTrue(location(client.form(casePath(claim, "auteur-decision"), author, "membre" to "none")).endsWith("erreur=decision_author"))
        assertTrue(location(client.form(casePath(claim, "statut"), reviewer, "statut" to "answered")).endsWith("ok=status"))
        // The author of the decision is asked only on a contested decision.
        val other = open(CaseNature.Claim(ClaimMotif.OTHER))
        assertTrue(location(client.form(casePath(other, "auteur-decision"), reviewer, "membre" to author.id.toString())).endsWith("erreur=forbidden"))
    }

    @Test
    fun aRequestOnOnesDataIsExtendedOnceWithinTheFirstMonthByAnAdministrator() = testApplication {
        val client = start()
        val moderator = member(data, "Modérateur", listOf(TeamRole.MODERATOR))
        val admin = member(data, "Administratrice", listOf(TeamRole.ADMIN))
        val claim = open(CaseNature.Claim(ClaimMotif.DATA_RIGHTS))
        // A moderator sees it, but the rights on data are for the administrators.
        assertEquals(HttpStatusCode.OK, client.page(casePath(claim), moderator).status)
        assertTrue(location(client.form(casePath(claim, "statut"), moderator, "statut" to "closed")).endsWith("erreur=forbidden"))
        assertTrue(location(client.form(casePath(claim, "prolongation"), moderator, "motif" to "[EXEMPLE] x")).endsWith("erreur=forbidden"))
        assertTrue(location(client.form(casePath(claim, "prolongation"), admin, "motif" to " ")).endsWith("erreur=reason_required"))
        val seq = lastSeq()
        assertTrue(location(client.form(casePath(claim, "prolongation"), admin, "motif" to "[EXEMPLE] Demande complexe")).endsWith("ok=extended"))
        val extended = caseOf(claim)
        assertEquals(CaseDeadlines.extendedDataRights(extended.receivedAt.epochSecond), extended.deadlineAt!!.epochSecond)
        assertEquals("[EXEMPLE] Demande complexe", extended.extensionReason)
        val line = journalAfter(seq).single()
        assertEquals("section.contact.extended", line.action)
        assertEquals("[EXEMPLE] Demande complexe", line.reason)
        assertTrue(location(client.form(casePath(claim, "prolongation"), admin, "motif" to "[EXEMPLE] Encore")).endsWith("erreur=not_extendable"))
        // After the first month, too late.
        val late = open(CaseNature.Claim(ClaimMotif.DATA_RIGHTS))
        clock.advance(32.days)
        val fresh = member(data, "Administrateur", listOf(TeamRole.ADMIN))
        assertTrue(location(client.form(casePath(late, "prolongation"), fresh, "motif" to "[EXEMPLE] Trop tard")).endsWith("erreur=not_extendable"))
        // A sensitive action: an old verification of the passkey brings the confirmation page, and nothing changes.
        val other = open(CaseNature.Claim(ClaimMotif.DATA_RIGHTS))
        clock.advance(6.minutes)
        val seq2 = lastSeq()
        val confirm = client.form(casePath(other, "prolongation"), fresh, "motif" to "[EXEMPLE] Plus tard")
        assertEquals(HttpStatusCode.OK, confirm.status)
        assertTrue(confirm.bodyAsText().contains("action=\"${casePath(other, "prolongation")}\""))
        assertNull(caseOf(other).extendedAt)
        assertTrue(journalAfter(seq2).isEmpty())
    }

    @Test
    fun aLegalHoldNeedsTwoAdministrators() = testApplication {
        val client = start()
        val first = member(data, "Admin un", listOf(TeamRole.ADMIN))
        val second = member(data, "Admin deux", listOf(TeamRole.ADMIN))
        val moderator = member(data, "Modératrice", listOf(TeamRole.MODERATOR))
        val claim = open(CaseNature.Claim(ClaimMotif.OTHER))
        val today = LocalDate.ofInstant(clock.instant(), ZoneId.of("Europe/Paris"))
        val review = today.plusMonths(6).toString()
        assertFalse(client.page(casePath(claim), moderator).bodyAsText().contains(ContactTexts.HOLD_REQUEST_BUTTON))
        assertTrue(location(client.form(casePath(claim, "archive"), moderator, "motif" to "[EXEMPLE] x", "reexamen" to review)).endsWith("erreur=forbidden"))
        assertTrue(location(client.form(casePath(claim, "archive"), first, "motif" to "[EXEMPLE] Plainte", "reexamen" to today.plusYears(1).plusDays(1).toString())).endsWith("erreur=review_date"))
        assertTrue(location(client.form(casePath(claim, "archive"), first, "motif" to "[EXEMPLE] Plainte", "reexamen" to today.toString())).endsWith("erreur=review_date"))
        val seq = lastSeq()
        assertTrue(location(client.form(casePath(claim, "archive"), first, "motif" to "[EXEMPLE] Plainte déposée", "reexamen" to review)).endsWith("ok=hold_requested"))
        assertNull(caseOf(claim).legalHoldReason, "nothing until a second person agrees")
        assertNotNull(store.holdRequest(claim.number.display))
        assertTrue(client.page(section, first).bodyAsText().contains(ContactTexts.MARK_HOLD_PENDING))
        assertTrue(location(client.form(casePath(claim, "archive"), second, "motif" to "[EXEMPLE] Autre", "reexamen" to review)).endsWith("erreur=already_requested"))
        assertTrue(location(client.form(casePath(claim, "archive/accord"), first)).endsWith("erreur=own_request"))
        assertTrue(location(client.form(casePath(claim, "archive/accord"), second)).endsWith("ok=hold_confirmed"))
        val held = caseOf(claim)
        assertEquals("[EXEMPLE] Plainte déposée", held.legalHoldReason)
        assertEquals(LocalDate.parse(review).atStartOfDay(ZoneId.of("Europe/Paris")).toInstant(), held.legalHoldReviewAt)
        assertNull(store.holdRequest(claim.number.display))
        val lines = journalAfter(seq)
        assertEquals(listOf("section.contact.legal_hold_requested", "section.contact.legal_hold_confirmed"), lines.map { it.action })
        assertEquals(listOf(first.id.toString(), second.id.toString()), lines.map { it.actorId })
        assertEquals(first.id.toString(), lines[1].details["requested_by"])
        assertTrue(client.page("$section?dossiers=archives", second).bodyAsText().contains(claim.number.display))
        // Lifting it takes two people as well; a request can be withdrawn by its author or refused by another.
        assertTrue(location(client.form(casePath(claim, "archive/levee"), second, "motif" to "[EXEMPLE] Procédure close")).endsWith("ok=hold_requested"))
        assertTrue(location(client.form(casePath(claim, "archive/retrait"), first)).endsWith("erreur=forbidden"))
        assertTrue(location(client.form(casePath(claim, "archive/refus"), first)).endsWith("ok=hold_refused"))
        assertNotNull(caseOf(claim).legalHoldReason)
        client.form(casePath(claim, "archive/levee"), second, "motif" to "[EXEMPLE] Procédure close")
        assertTrue(location(client.form(casePath(claim, "archive/retrait"), second)).endsWith("ok=hold_cancelled"))
        client.form(casePath(claim, "archive/levee"), second, "motif" to "[EXEMPLE] Procédure close")
        assertTrue(location(client.form(casePath(claim, "archive/accord"), first)).endsWith("ok=hold_confirmed"))
        assertNull(caseOf(claim).legalHoldReason)
        assertTrue(location(client.form(casePath(claim, "archive/levee"), second, "motif" to "[EXEMPLE] x")).endsWith("erreur=no_hold"))
    }
}
