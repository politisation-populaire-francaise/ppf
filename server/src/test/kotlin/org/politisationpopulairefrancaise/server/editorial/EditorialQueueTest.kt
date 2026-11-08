package org.politisationpopulairefrancaise.server.editorial

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
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSpace
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.platform.SigningUse
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
import org.politisationpopulairefrancaise.server.backoffice.Permissions
import org.politisationpopulairefrancaise.server.backoffice.SESSION_COOKIE
import org.politisationpopulairefrancaise.server.backoffice.TeamRole
import org.politisationpopulairefrancaise.server.backoffice.TestMember
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.module
import org.politisationpopulairefrancaise.server.testRevendications
import java.time.Instant
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** The review of the editorial circuit (step 4): submission, decisions, queue, journal, on the real PostgreSQL of the tests. */
class EditorialQueueTest {
    private val clock = MutableClock(Instant.now())
    private val bo = "http://localhost:${BackOfficeTestData.PORT}"
    private val section = "/equipe/sections/editorial.comprendre"
    private val editorKeys = IdentityKeys.generate()
    private val policy = PublicationPolicy(setOf(editorKeys.publicKey.hex))
    private val drafts = EditorialDrafts(TestDatabase.dataSource, clock)
    private val reviews = EditorialReviews(TestDatabase.dataSource, drafts, clock)
    private val data get() = BackOfficeTestData.backOffice(clock)

    /** A fake of the signing seam of step 5: records each call, answers [answer] (an approval page by default). */
    private class RecordingPublisher(
        val answer: PublisherAnswer = PublisherAnswer.AwaitingApproval("draft-1", APPROVAL_URL, approvals = 0, required = 2),
    ) : EditorialPublisher {
        val handedOver = mutableListOf<ReadyToSign>()
        val asked = mutableListOf<ReadyToSign>()

        override suspend fun handOver(version: ReadyToSign): PublisherAnswer {
            handedOver += version
            return answer
        }

        override suspend fun state(version: ReadyToSign): PublisherAnswer {
            asked += version
            return answer
        }
    }

    private companion object {
        const val APPROVAL_URL = "https://signature.example/approbation/1"
    }

    @BeforeTest
    fun emptyTables() = BackOfficeTestData.reset()

    private fun ApplicationTestBuilder.start(publisher: EditorialPublisher? = null): HttpClient {
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
            val setup = BackOfficeSetup(BackOfficeTestData.CONFIG, clock)
            if (publisher == null) {
                module(TestDatabase.dataSource, policy, TEST_RELAY, testRevendications(), testAgir(), backOffice = setup)
            } else {
                module(TestDatabase.dataSource, policy, TEST_RELAY, testRevendications(), testAgir(), backOffice = setup, editorialPublisher = publisher)
            }
        }
        return createClient { followRedirects = false }
    }

    private suspend fun HttpClient.page(path: String, who: TestMember): HttpResponse = get("$bo$path") {
        header(HttpHeaders.Cookie, "$SESSION_COOKIE=${who.token}")
    }

    private suspend fun HttpClient.form(path: String, who: TestMember, fields: List<Pair<String, String>>): HttpResponse =
        submitForm("$bo$path", parameters { (fields + ("csrf" to Csrf.tokenFor(who.token))).forEach { (key, value) -> append(key, value) } }) {
            header(HttpHeaders.Cookie, "$SESSION_COOKIE=${who.token}")
            header("Sec-Fetch-Site", "same-origin")
        }

    /** A complete fact-check, without any problem (as in EditorialDraftsTest). */
    private fun validDecryptage(title: String = "[EXEMPLE] Une affirmation vérifiée", slug: String = "exemple-decryptage"): List<Pair<String, String>> = listOf(
        "action" to "save",
        "c.slug" to slug,
        "c.title" to title,
        "c.summary" to "[EXEMPLE] En partie seulement.",
        "c.topics" to "institutions",
        "c.published_at" to "2026-11-08",
        "c.updated_at" to "2026-11-08",
        "origin" to "human",
        "rows.verdict" to "1",
        "f.verdict.0" to "trompeur",
        "rows.claim" to "1",
        "f.claim.0" to "[EXEMPLE] Une loi est adoptée sans vote.",
        "rows.claim_context" to "1",
        "f.claim_context.0" to "",
        "body" to EditorialDraftsTest.BODY,
    )

    private fun draftIdOf(response: HttpResponse): UUID {
        val location = assertNotNull(response.headers[HttpHeaders.Location], "redirected to the draft")
        return UUID.fromString(location.substringAfter("/brouillons/").substringBefore('?'))
    }

    /** A valid draft written by [author], sent to review. */
    private suspend fun HttpClient.submitted(author: TestMember): UUID {
        val id = draftIdOf(form("$section/nouveau/decryptage", author, validDecryptage()))
        val submit = form("$section/brouillons/$id/soumettre", author, listOf("version" to "1"))
        assertEquals("$section/brouillons/$id?ok=submitted", submit.headers[HttpHeaders.Location])
        return id
    }

    private fun decision(action: String, version: Int, reason: String? = null): List<Pair<String, String>> =
        listOfNotNull("decision" to action, "version" to version.toString(), reason?.let { "motif" to it })

    @Test
    fun aDraftWithProblemsIsNotSubmittedAndTheEditorSeesWhy() = testApplication {
        val client = start()
        val alice = member(data, "Alice", listOf(TeamRole.EDITOR))
        val id = draftIdOf(client.form("$section/nouveau/decryptage", alice, validDecryptage().filter { it.first != "origin" }))
        assertFalse(client.page("$section/brouillons/$id", alice).bodyAsText().contains(EditorialTexts.SUBMIT), "no submit button for a draft with problems")
        val seq = lastSeq()
        val refused = client.form("$section/brouillons/$id/soumettre", alice, listOf("version" to "1"))
        assertEquals(HttpStatusCode.UnprocessableEntity, refused.status)
        val html = refused.bodyAsText()
        assertTrue(html.contains(EditorialTexts.format(EditorialTexts.SUBMIT_REFUSED_PROBLEMS, 1)), html)
        assertTrue(html.contains("Origine (IA ou non) : ce champ est obligatoire"), "the French messages of the engine")
        assertEquals(DraftStatus.DRAFT, drafts.find(id)!!.status)
        assertTrue(journalAfter(seq).isEmpty(), "nothing was done, nothing journaled")
    }

    @Test
    fun aValidDraftIsSubmittedAndCanNoLongerBeEdited() = testApplication {
        val client = start()
        val alice = member(data, "Alice", listOf(TeamRole.EDITOR))
        val id = draftIdOf(client.form("$section/nouveau/decryptage", alice, validDecryptage()))
        val form = client.page("$section/brouillons/$id", alice).bodyAsText()
        assertTrue(form.contains(EditorialTexts.SUBMIT))
        assertTrue(form.contains("action=\"$section/brouillons/$id/soumettre\""))
        val seq = lastSeq()
        val submit = client.form("$section/brouillons/$id/soumettre", alice, listOf("version" to "1"))
        assertEquals("$section/brouillons/$id?ok=submitted", submit.headers[HttpHeaders.Location])
        val draft = drafts.find(id)!!
        assertEquals(DraftStatus.IN_REVIEW, draft.status)
        assertEquals(1, draft.submittedVersion)
        assertNotNull(draft.submittedAt)
        val lines = journalAfter(seq)
        assertEquals(listOf("section.editorial.comprendre.submit_requested", "section.editorial.comprendre.submit"), lines.map { it.action })
        assertEquals(mapOf("version" to "1", "outcome" to "done", "status" to "in_review"), lines.last().details)
        assertEquals("editorial.comprendre:$id", lines.last().targetId)

        // A submitted draft is not editable: the form is gone, a save is refused and adds no version.
        val page = client.page("$section/brouillons/$id?ok=submitted", alice).bodyAsText()
        assertTrue(page.contains(EditorialTexts.SUBMITTED))
        assertTrue(page.contains(EditorialTexts.STATE_IN_REVIEW))
        assertFalse(page.contains("name=\"body\""), "no editing form")
        val save = client.form("$section/brouillons/$id", alice, validDecryptage(title = "[EXEMPLE] Autre titre") + ("version" to "1"))
        assertEquals(HttpStatusCode.Conflict, save.status)
        assertTrue(save.bodyAsText().contains(EditorialTexts.NOT_EDITABLE))
        assertEquals(1, drafts.find(id)!!.latestVersion)
        assertIs<SaveResult.NotEditable>(drafts.save(id, 1, DraftContent(listOf("slug" to "x"), "y\n"), 0, alice.id))
        // Submitting twice is refused too.
        assertEquals(HttpStatusCode.Conflict, client.form("$section/brouillons/$id/soumettre", alice, listOf("version" to "1")).status)
        // The list of drafts shows the state.
        val home = client.page(section, alice).bodyAsText()
        assertTrue(home.contains(EditorialTexts.COLUMN_STATUS))
        assertTrue(home.contains("En relecture"))
        assertTrue(home.contains("$section/relecture/$id"))
    }

    @Test
    fun theAuthorAWriterOrTheSameNostrKeyCannotDecide() = testApplication {
        val client = start()
        val alice = member(data, "Alice", listOf(TeamRole.EDITOR))
        val carla = member(data, "Carla", listOf(TeamRole.EDITOR))
        val twin = member(data, "Alice bis", listOf(TeamRole.EDITOR))
        val id = draftIdOf(client.form("$section/nouveau/decryptage", alice, validDecryptage()))
        // Carla writes version 2: she is a writer too.
        assertEquals(HttpStatusCode.Found, client.form("$section/brouillons/$id", carla, validDecryptage(title = "[EXEMPLE] Titre de Carla") + ("version" to "1")).status)
        assertEquals(HttpStatusCode.Found, client.form("$section/brouillons/$id/soumettre", alice, listOf("version" to "2")).status)
        // A second account linked to Alice's Nostr key.
        val key = IdentityKeys.generate().publicKey.hex
        TestDatabase.dataSource.connection.use { connection ->
            connection.prepareStatement("UPDATE team_account SET nostr_pubkey = ? WHERE id IN (?, ?)").use { update ->
                update.setString(1, key)
                update.setObject(2, alice.id)
                update.setObject(3, twin.id)
                update.executeUpdate()
            }
        }
        val review = client.page("$section/relecture/$id", alice).bodyAsText()
        assertTrue(review.contains(EditorialTexts.REVIEW_YOU_WROTE))
        assertFalse(review.contains("name=\"decision\""), "no decision form for a writer")
        listOf(alice, carla, twin).forEach { who ->
            listOf(
                decision(EditorialDraftsSection.ACTION_APPROVE, 2),
                decision(EditorialDraftsSection.ACTION_REJECT, 2, "[EXEMPLE] Motif."),
                decision(EditorialDraftsSection.ACTION_REQUEST_CHANGES, 2, "[EXEMPLE] Message."),
            ).forEach { fields ->
                val refused = client.form("$section/relecture/$id", who, fields)
                assertEquals(HttpStatusCode.Forbidden, refused.status, "${who.account.pseudonym} ${fields.first()}")
                assertTrue(refused.bodyAsText().contains(EditorialTexts.REVIEW_REFUSED_WRITER))
            }
        }
        assertEquals(DraftStatus.IN_REVIEW, drafts.find(id)!!.status)
        assertTrue(reviews.decisions(id).isEmpty())
    }

    @Test
    fun anotherEditorApprovesAndTheVersionIsHandedToTheSigningSeam() = testApplication {
        val publisher = RecordingPublisher()
        val client = start(publisher)
        val alice = member(data, "Alice", listOf(TeamRole.EDITOR))
        val bruno = member(data, "Bruno", listOf(TeamRole.EDITOR))
        val id = client.submitted(alice)
        val review = client.page("$section/relecture/$id", bruno).bodyAsText()
        listOf(EditorialTexts.REQUEST_CHANGES, EditorialTexts.REJECT, EditorialTexts.APPROVE, EditorialTexts.APPROVE_HINT, "[EXEMPLE] Une loi est adoptée sans vote.").forEach {
            assertTrue(review.contains(it), it)
        }
        assertFalse(review.contains("—"), "no em dash")
        assertFalse(review.contains("<script"), "no script")
        assertFalse(review.contains("style="), "no inline style")
        val seq = lastSeq()
        val approved = client.form("$section/relecture/$id", bruno, decision(EditorialDraftsSection.ACTION_APPROVE, 1))
        assertEquals(HttpStatusCode.OK, approved.status)
        val html = approved.bodyAsText()
        assertTrue(html.contains(EditorialTexts.DONE_READY_TO_SIGN))
        assertTrue(html.contains("<a href=\"$APPROVAL_URL\">${EditorialTexts.SIGNER_LINK}</a>"), "a plain link to the signing service's page")
        assertTrue(html.contains(EditorialTexts.format(EditorialTexts.SIGNER_AWAITING, 0, 2)))
        assertTrue(publisher.asked.isEmpty(), "the hand-over answer is shown, the state is not asked twice")
        val draft = drafts.find(id)!!
        assertEquals(DraftStatus.READY_TO_SIGN, draft.status)
        assertFalse(draft.status.editable)
        val record = reviews.decisions(id).single()
        assertEquals(ReviewDecision.READY_TO_SIGN, record.decision)
        assertEquals(bruno.id, record.reviewer)
        assertNull(record.reason, "an approval stores no text, nothing secret")
        val call = publisher.handedOver.single()
        assertEquals(ReadyToSign(EditorialSpace.COMPRENDRE, id, 1, ArticleType.DECRYPTAGE, "exemple-decryptage", null, alice.id, null, bruno.id, correction = false), call)
        assertEquals(SigningUse.EDITORIAL_COMPRENDRE, call.use)
        assertTrue(call.doubleValidation)
        // On reload the state is asked again (no background loop); the link is for who may approve, never a writer.
        val reload = client.page("$section/relecture/$id", bruno).bodyAsText()
        assertTrue(reload.contains(EditorialTexts.SIGNER_LINK))
        assertEquals(1, publisher.asked.size)
        val forAuthor = client.page("$section/relecture/$id", alice).bodyAsText()
        assertTrue(forAuthor.contains(EditorialTexts.format(EditorialTexts.SIGNER_AWAITING, 0, 2)))
        assertFalse(forAuthor.contains(EditorialTexts.SIGNER_LINK), "the author never approves")
        assertEquals(
            listOf("section.editorial.comprendre.approve_requested", "section.editorial.comprendre.approve"),
            journalAfter(seq).map { it.action },
        )
        assertEquals("ready_to_sign", journalAfter(seq).last().details["status"])
        // Decided: a second decision is refused, the page offers no form any more.
        assertEquals(HttpStatusCode.Conflict, client.form("$section/relecture/$id", bruno, decision(EditorialDraftsSection.ACTION_REJECT, 1, "[EXEMPLE] Trop tard.")).status)
        assertTrue(client.page("$section/relecture/$id", bruno).bodyAsText().contains(EditorialTexts.REVIEW_NOT_IN_REVIEW))
        assertTrue(client.page(section, alice).bodyAsText().contains("Prêt à signer"))
    }

    @Test
    fun withoutTheSigningServiceTheApprovalSaysItIsNotPluggedYet() = testApplication {
        val client = start()
        val alice = member(data, "Alice", listOf(TeamRole.EDITOR))
        val bruno = member(data, "Bruno", listOf(TeamRole.EDITOR))
        val id = client.submitted(alice)
        val approved = client.form("$section/relecture/$id", bruno, decision(EditorialDraftsSection.ACTION_APPROVE, 1))
        assertEquals(HttpStatusCode.OK, approved.status)
        assertTrue(approved.bodyAsText().contains(EditorialTexts.SIGNER_NOT_PLUGGED))
        assertEquals(DraftStatus.READY_TO_SIGN, drafts.find(id)!!.status)
        assertTrue(client.page("$section/relecture/$id", bruno).bodyAsText().contains(EditorialTexts.SIGNER_NOT_PLUGGED))
    }

    @Test
    fun withoutACertifiedKeyNothingIsSentAndAnUnsafeLinkIsNeverShown() = testApplication {
        val client = start(RecordingPublisher(PublisherAnswer.KeyUnavailable))
        val alice = member(data, "Alice", listOf(TeamRole.EDITOR))
        val bruno = member(data, "Bruno", listOf(TeamRole.EDITOR))
        val id = client.submitted(alice)
        val approved = client.form("$section/relecture/$id", bruno, decision(EditorialDraftsSection.ACTION_APPROVE, 1)).bodyAsText()
        assertTrue(approved.contains(EditorialTexts.SIGNER_UNAVAILABLE))
        assertFalse(approved.contains(EditorialTexts.SIGNER_LINK))
        assertEquals(DraftStatus.READY_TO_SIGN, drafts.find(id)!!.status, "the approval stays, nothing is published unsigned")
        listOf("javascript:alert(1)", "/relatif", "http://ailleurs.example/x", "https://a.example/\"><script>").forEach {
            assertFalse(EditorialDraftsSection.isSignerLink(it), it)
        }
        assertTrue(EditorialDraftsSection.isSignerLink(APPROVAL_URL))
        assertTrue(EditorialDraftsSection.isSignerLink("http://localhost:8090/approbation/1"))
    }

    @Test
    fun anOldPasskeyVerificationIsAskedAgainBeforeARefusalOrAnApproval() = testApplication {
        val client = start()
        val alice = member(data, "Alice", listOf(TeamRole.EDITOR))
        val bruno = member(data, "Bruno", listOf(TeamRole.EDITOR))
        val id = client.submitted(alice)
        clock.advance(6.minutes)
        val seq = lastSeq()
        val approve = client.form("$section/relecture/$id", bruno, decision(EditorialDraftsSection.ACTION_APPROVE, 1))
        assertEquals(HttpStatusCode.OK, approve.status)
        assertTrue(approve.bodyAsText().contains("action=\"$section/relecture/$id\""), "the confirmation page of the base posts the same fields again")
        assertEquals(DraftStatus.IN_REVIEW, drafts.find(id)!!.status)
        assertTrue(journalAfter(seq).isEmpty())
        // A request for changes is not sensitive.
        assertEquals(HttpStatusCode.Found, client.form("$section/relecture/$id", bruno, decision(EditorialDraftsSection.ACTION_REQUEST_CHANGES, 1, "[EXEMPLE] Précisez la source.")).status)
    }

    @Test
    fun aRequestForChangesReopensTheDraftForItsWriters() = testApplication {
        val client = start()
        val alice = member(data, "Alice", listOf(TeamRole.EDITOR))
        val bruno = member(data, "Bruno", listOf(TeamRole.EDITOR))
        val id = client.submitted(alice)
        assertEquals(HttpStatusCode.UnprocessableEntity, client.form("$section/relecture/$id", bruno, decision(EditorialDraftsSection.ACTION_REQUEST_CHANGES, 1)).status, "a message is required")
        val asked = client.form("$section/relecture/$id", bruno, decision(EditorialDraftsSection.ACTION_REQUEST_CHANGES, 1, "[EXEMPLE] Ajoutez la date du vote.\nEt sa source."))
        assertEquals("$section/relecture/$id?ok=changes_requested", asked.headers[HttpHeaders.Location])
        assertEquals(DraftStatus.CHANGES_REQUESTED, drafts.find(id)!!.status)
        // Alice sees the message on her draft, whose form is back.
        val page = client.page("$section/brouillons/$id", alice).bodyAsText()
        assertTrue(page.contains(EditorialTexts.LAST_MESSAGE))
        assertTrue(page.contains("[EXEMPLE] Ajoutez la date du vote."))
        assertTrue(page.contains("name=\"body\""), "editable again")
        // The same version cannot go back to review: a new version first.
        val again = client.form("$section/brouillons/$id/soumettre", alice, listOf("version" to "1"))
        assertEquals(HttpStatusCode.Conflict, again.status)
        assertTrue(again.bodyAsText().contains(EditorialTexts.SUBMIT_REFUSED_ALREADY_REVIEWED))
        assertEquals(HttpStatusCode.Found, client.form("$section/brouillons/$id", alice, validDecryptage(title = "[EXEMPLE] Titre précisé") + ("version" to "1")).status)
        assertEquals(HttpStatusCode.Found, client.form("$section/brouillons/$id/soumettre", alice, listOf("version" to "2")).status)
        val draft = drafts.find(id)!!
        assertEquals(DraftStatus.IN_REVIEW, draft.status)
        assertEquals(2, draft.submittedVersion)
        // A new item in the queue (the id carries the version); a decision on the old version is refused.
        val queue = EditorialQueue(EditorialSpace.COMPRENDRE, reviews)
        assertEquals(listOf("$id:v2"), queue.page(null, 10).items.map { it.id })
        assertEquals(HttpStatusCode.Conflict, client.form("$section/relecture/$id", bruno, decision(EditorialDraftsSection.ACTION_APPROVE, 1)).status)
        assertEquals(HttpStatusCode.OK, client.form("$section/relecture/$id", bruno, decision(EditorialDraftsSection.ACTION_APPROVE, 2)).status)
        assertEquals(listOf(2, 1), reviews.decisions(id).map { it.version })
    }

    @Test
    fun aRefusalNeedsAReasonAndClosesTheDraft() = testApplication {
        val client = start()
        val alice = member(data, "Alice", listOf(TeamRole.EDITOR))
        val bruno = member(data, "Bruno", listOf(TeamRole.EDITOR))
        val id = client.submitted(alice)
        val seq = lastSeq()
        listOf(null, "   ", "x".repeat(EditorialReviews.MAX_REASON + 1)).forEach { reason ->
            val refused = client.form("$section/relecture/$id", bruno, decision(EditorialDraftsSection.ACTION_REJECT, 1, reason))
            assertEquals(HttpStatusCode.UnprocessableEntity, refused.status, reason)
            assertTrue(refused.bodyAsText().contains(EditorialTexts.format(EditorialTexts.REASON_REQUIRED, EditorialTexts.number(EditorialReviews.MAX_REASON))))
        }
        assertEquals(DraftStatus.IN_REVIEW, drafts.find(id)!!.status)
        assertTrue(journalAfter(seq).isEmpty())
        assertEquals(ReviewResult.ReasonRequired, reviews.decide(id, 1, ReviewDecision.REJECTED, bruno.id, null), "the store checks it too")
        val rejected = client.form("$section/relecture/$id", bruno, decision(EditorialDraftsSection.ACTION_REJECT, 1, "[EXEMPLE] Hors du champ de l’espace."))
        assertEquals("$section/relecture/$id?ok=rejected", rejected.headers[HttpHeaders.Location])
        assertEquals(DraftStatus.REJECTED, drafts.find(id)!!.status)
        assertEquals("[EXEMPLE] Hors du champ de l’espace.", reviews.decisions(id).single().reason)
        val page = client.page("$section/brouillons/$id", alice).bodyAsText()
        assertTrue(page.contains(EditorialTexts.STATE_REJECTED))
        assertTrue(page.contains("[EXEMPLE] Hors du champ de l’espace."))
        assertEquals(HttpStatusCode.Conflict, client.form("$section/brouillons/$id", alice, validDecryptage() + ("version" to "1")).status, "closed")
        assertEquals(HttpStatusCode.Conflict, client.form("$section/brouillons/$id/soumettre", alice, listOf("version" to "1")).status)
        // A refused draft frees its address.
        assertEquals(HttpStatusCode.Found, client.form("$section/nouveau/decryptage", alice, validDecryptage()).status)
    }

    @Test
    fun aCorrectionIsDueIn48HoursAtLevel4AndANewContentIn7DaysAtLevel2() = testApplication {
        val client = start()
        val alice = member(data, "Alice", listOf(TeamRole.EDITOR))
        val bruno = member(data, "Bruno", listOf(TeamRole.EDITOR))
        val fresh = client.submitted(alice)
        val base = "a".repeat(64)
        val correctionContent = DraftContent(listOf("slug" to "exemple-corrige", "title" to "[EXEMPLE] Corrigé"), "Texte\n", ChangeKind.CORRECTION, "[EXEMPLE] Note.")
        val correction = assertIs<SaveResult.Saved>(drafts.create(EditorialSpace.COMPRENDRE, ArticleType.DECRYPTAGE, correctionContent, 0, alice.id, baseEventId = base)).draft
        assertIs<ReviewResult.Done>(reviews.submit(correction.id, 1, alice.id))
        val update = assertIs<SaveResult.Saved>(
            drafts.create(EditorialSpace.COMPRENDRE, ArticleType.DECRYPTAGE, correctionContent.copy(entries = listOf("slug" to "exemple-complete"), changeKind = ChangeKind.UPDATE), 0, alice.id, baseEventId = "b".repeat(64)),
        ).draft
        assertIs<ReviewResult.Done>(reviews.submit(update.id, 1, alice.id))
        val queue = EditorialQueue(EditorialSpace.COMPRENDRE, reviews)
        assertEquals(3, queue.pendingCount())
        val items = queue.page(null, 10).items.associateBy { it.id }
        val now = clock.instant().epochSecond
        val urgent = assertNotNull(items["${correction.id}:v1"])
        assertEquals(4, urgent.level)
        assertEquals(now + 48 * 3600, urgent.deadline)
        assertTrue(urgent.summary.orEmpty().contains(EditorialTexts.QUEUE_CORRECTION))
        listOf(fresh, update.id).forEach { id ->
            val item = assertNotNull(items["$id:v1"])
            assertEquals(2, item.level)
            assertEquals(now + 7 * 24 * 3600, item.deadline)
        }
        assertEquals("${correction.id}:v1", queue.page(null, 10).items.first().id, "by deadline")
        // The base shows the queue and its items to the editors, with a link to the review page.
        val list = client.page("/files/editorial.comprendre", bruno)
        assertEquals(HttpStatusCode.OK, list.status)
        assertTrue(list.bodyAsText().contains("[EXEMPLE] Corrigé"))
        val detail = client.page("/files/editorial.comprendre/${correction.id}:v1", bruno).bodyAsText()
        assertTrue(detail.contains(EditorialTexts.QUEUE_OPEN_REVIEW))
        assertTrue(detail.contains("href=\"$section/relecture/${correction.id}\""))
        assertTrue(queue.actions("${correction.id}:v1").isEmpty(), "decisions on the review page, never through the base with a free text")
        // The dashboard puts the correction first.
        assertTrue(client.page("/", bruno).bodyAsText().contains("[EXEMPLE] Corrigé"))
        // Decided items leave the count; an approved one stays listed, ready to sign.
        assertIs<ReviewResult.Done>(reviews.decide(fresh, 1, ReviewDecision.READY_TO_SIGN, bruno.id, null))
        assertEquals(2, queue.pendingCount())
        assertEquals("${fresh}:v1", queue.page(null, 10).items.last().id)
        assertIs<ReviewResult.Done>(reviews.decide(update.id, 1, ReviewDecision.REJECTED, bruno.id, "[EXEMPLE] Non."))
        assertFalse(queue.page(null, 10).items.any { it.id == "${update.id}:v1" })
    }

    @Test
    fun noJournalLineCarriesTheFreeTextOfAReview() = testApplication {
        val client = start()
        val alice = member(data, "Alice", listOf(TeamRole.EDITOR))
        val bruno = member(data, "Bruno", listOf(TeamRole.EDITOR))
        val seq = lastSeq()
        val first = client.submitted(alice)
        val message = "[EXEMPLE] Message libre très reconnaissable 4f2a"
        client.form("$section/relecture/$first", bruno, decision(EditorialDraftsSection.ACTION_REQUEST_CHANGES, 1, message))
        client.form("$section/brouillons/$first", alice, validDecryptage(title = "[EXEMPLE] Deuxième") + ("version" to "1"))
        client.form("$section/brouillons/$first/soumettre", alice, listOf("version" to "2"))
        val reason = "[EXEMPLE] Motif libre très reconnaissable 9c1b"
        client.form("$section/relecture/$first", bruno, decision(EditorialDraftsSection.ACTION_REJECT, 2, reason))
        val second = client.submitted(alice)
        client.form("$section/relecture/$second", bruno, decision(EditorialDraftsSection.ACTION_APPROVE, 1))
        val lines = journalAfter(seq).filter { it.action.startsWith("section.editorial.comprendre.") }
        val actions = lines.map { it.action.removePrefix("section.editorial.comprendre.") }.toSet()
        assertTrue(actions.containsAll(listOf("submit", "request_changes", "reject", "approve", "submit_requested", "request_changes_requested", "reject_requested", "approve_requested")), actions.toString())
        lines.forEach { line ->
            assertNull(line.reason, "no free text as the reason of a line: ${line.action}")
            assertTrue(line.details.keys.all { it in setOf("version", "type", "slug", "problems", "outcome", "status") }, line.details.toString())
            line.details.values.forEach { value ->
                assertFalse(value.contains("EXEMPLE"), "${line.action}: $value")
                assertTrue(Regex("^[a-z0-9_-]{1,100}$").matches(value), "${line.action}: an identifier or a code, $value")
            }
        }
        assertEquals(listOf(reason, message), reviews.decisions(first).map { it.reason }, "the free text stays in editorial_review")
    }

    @Test
    fun onlyEditorsSubmitAndReviewAndTheQueueIsTheirs() = testApplication {
        val client = start()
        val alice = member(data, "Alice", listOf(TeamRole.EDITOR))
        val moderator = member(data, "Modo", listOf(TeamRole.MODERATOR))
        val id = draftIdOf(client.form("$section/nouveau/decryptage", alice, validDecryptage()))
        assertEquals(HttpStatusCode.Forbidden, client.form("$section/brouillons/$id/soumettre", moderator, listOf("version" to "1")).status)
        assertEquals(DraftStatus.DRAFT, drafts.find(id)!!.status)
        client.form("$section/brouillons/$id/soumettre", alice, listOf("version" to "1"))
        assertEquals(HttpStatusCode.Forbidden, client.page("$section/relecture/$id", moderator).status)
        assertEquals(HttpStatusCode.Forbidden, client.form("$section/relecture/$id", moderator, decision(EditorialDraftsSection.ACTION_APPROVE, 1)).status)
        assertEquals(HttpStatusCode.NotFound, client.page("/files/editorial.comprendre", moderator).status, "the queue is hidden from who may not review")
        assertEquals(DraftStatus.IN_REVIEW, drafts.find(id)!!.status)
        // An editor who is not a writer of the draft may not submit it either.
        val bruno = member(data, "Bruno", listOf(TeamRole.EDITOR))
        assertEquals(ReviewResult.NotWriter, reviews.submit(assertIs<SaveResult.Saved>(drafts.create(EditorialSpace.COMPRENDRE, ArticleType.DECRYPTAGE, DraftContent(listOf("slug" to "autre"), "x\n"), 0, alice.id)).draft.id, 1, bruno.id))
        // The permissions: writing, reviewing and approving belong to the editor role only.
        val queue = EditorialQueue(EditorialSpace.COMPRENDRE, reviews)
        assertEquals("queue.editorial.comprendre.review", queue.viewPermission)
        TeamRole.entries.filter { it != TeamRole.SUPER_ADMIN }.forEach { role ->
            val editor = role == TeamRole.EDITOR
            listOf(Permissions.editorialWrite("comprendre"), EditorialQueue.reviewPermission(EditorialSpace.COMPRENDRE), EditorialQueue.publishPermission(EditorialSpace.COMPRENDRE)).forEach { permission ->
                assertEquals(editor, permission in role.permissions, "$role $permission")
            }
        }
        assertEquals("editorial.voir", EditorialQueue.idOf(EditorialSpace.VOIR), "the same class serves Voir, registered with its schemas")
    }
}
