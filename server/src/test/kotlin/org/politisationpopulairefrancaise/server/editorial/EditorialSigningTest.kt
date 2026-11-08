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
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.platform.DraftAuthor
import org.politisationpopulairefrancaise.core.platform.SigningUse
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
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
import org.politisationpopulairefrancaise.server.backoffice.transaction
import org.politisationpopulairefrancaise.server.events.EventFilter
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.module
import org.politisationpopulairefrancaise.server.signing.DraftSubmission
import org.politisationpopulairefrancaise.server.signing.FakePlatformSigner
import org.politisationpopulairefrancaise.server.signing.PlatformSigner
import org.politisationpopulairefrancaise.server.signing.SigningStatus
import org.politisationpopulairefrancaise.server.signing.SigningTicket
import org.politisationpopulairefrancaise.server.testRevendications
import java.time.Instant
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

/**
 * The signature and the publication of the editorial circuit (step 5) on the [FakePlatformSigner] of the
 * tests, through the real application (`module(..., platformSigner = ...)`) and the real PostgreSQL.
 */
class EditorialSigningTest {
    private val clock = MutableClock(Instant.now())
    private val bo = "http://localhost:${BackOfficeTestData.PORT}"
    private val site = "http://localhost:8080"
    private val section = "/equipe/sections/editorial.comprendre"
    private val use = SigningUse.EDITORIAL_COMPRENDRE
    private val fake = FakePlatformSigner { clock.instant().epochSecond }
    private val platformKey = runBlocking { fake.currentKey(use) }
    private val policy = PublicationPolicy(setOf(platformKey))
    private val drafts = EditorialDrafts(TestDatabase.dataSource, clock)
    private val signing = EditorialSigning(TestDatabase.dataSource, clock)
    private val events = EventRepository(TestDatabase.dataSource)
    private val data get() = BackOfficeTestData.backOffice(clock)

    /** One `submitDraft` received by the signing service. */
    private data class Submission(val unsigned: UnsignedEvent, val doubleValidation: Boolean, val author: DraftAuthor?)

    /** The fake signing service, with the faults the tests need; records each submission. */
    private class ScriptedSigner(val fake: FakePlatformSigner) : PlatformSigner by fake {
        var keyAvailable = true
        var keyOverride: String? = null
        var pending = false
        var tamper: ((SigningStatus) -> SigningStatus)? = null
        val submissions = mutableListOf<Submission>()

        override suspend fun currentKey(use: SigningUse): String? = if (!keyAvailable) null else keyOverride ?: fake.currentKey(use)

        override suspend fun submitDraft(unsigned: UnsignedEvent, use: SigningUse, doubleValidation: Boolean, author: DraftAuthor?): DraftSubmission {
            submissions += Submission(unsigned, doubleValidation, author)
            return fake.submitDraft(unsigned, use, doubleValidation, author)
        }

        override suspend fun status(ticket: SigningTicket): SigningStatus {
            if (pending) return SigningStatus.Pending
            val status = fake.status(ticket)
            return tamper?.invoke(status) ?: status
        }
    }

    private val signer = ScriptedSigner(fake)

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
            module(TestDatabase.dataSource, policy, TEST_RELAY, testRevendications(), testAgir(), backOffice = BackOfficeSetup(BackOfficeTestData.CONFIG, clock), platformSigner = signer)
        }
        return createClient { followRedirects = false }
    }

    private suspend fun HttpClient.page(path: String, who: TestMember): HttpResponse = get("$bo$path") { header(HttpHeaders.Cookie, "$SESSION_COOKIE=${who.token}") }

    private suspend fun HttpClient.form(path: String, who: TestMember, fields: List<Pair<String, String>>): HttpResponse =
        submitForm("$bo$path", parameters { (fields + ("csrf" to Csrf.tokenFor(who.token))).forEach { (key, value) -> append(key, value) } }) {
            header(HttpHeaders.Cookie, "$SESSION_COOKIE=${who.token}")
            header("Sec-Fetch-Site", "same-origin")
        }

    private fun fields(title: String = "[EXEMPLE] Une affirmation vérifiée", slug: String = SLUG): List<Pair<String, String>> = listOf(
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

    private fun decision(action: String, version: Int, reason: String? = null): List<Pair<String, String>> =
        listOfNotNull("decision" to action, "version" to version.toString(), reason?.let { "motif" to it })

    private class Team(val alice: TestMember, val bruno: TestMember)

    private fun team() = Team(member(data, "Alice", listOf(TeamRole.EDITOR)), member(data, "Bruno", listOf(TeamRole.EDITOR)))

    /** A draft written by Alice, submitted, approved by Bruno: handed over to the signing service. */
    private suspend fun HttpClient.approved(team: Team, title: String = "[EXEMPLE] Une affirmation vérifiée", slug: String = SLUG): UUID {
        val id = draftIdOf(form("$section/nouveau/decryptage", team.alice, fields(title, slug)))
        assertEquals(HttpStatusCode.Found, form("$section/brouillons/$id/soumettre", team.alice, listOf("version" to "1")).status)
        assertEquals(HttpStatusCode.OK, form("$section/relecture/$id", team.bruno, decision(EditorialDraftsSection.ACTION_APPROVE, 1)).status)
        return id
    }

    /**
     * The approvals the signing service asks for on its own page (`required`), by accounts that did not write the
     * draft: one for an ordinary content (the reviewer of the queue confirms there), two for a content of M17.
     */
    private suspend fun approveOnSigner(eventId: String) {
        val required = assertNotNull(fake.draftStatus(use, eventId), "the signing service knows the draft").required
        listOf("relecteur", "carla").take(required).forEach { assertNull(fake.approve(use, eventId, it)) }
    }

    private fun attempt(id: UUID): SigningAttempt = signing.attempts(id).first()

    private fun storedArticles() = events.query(EventFilter(kinds = listOf(Article.KIND), limit = 100))

    @Test
    fun anApprovedVersionIsSubmittedThenSignedAndPublishedUnchanged() = testApplication {
        val client = start()
        val team = team()
        val id = client.approved(team)
        // Submitted: the unsigned event built with the key of the use, kept with its approval page.
        val first = attempt(id)
        assertEquals(SigningState.SUBMITTED, first.state)
        assertEquals(platformKey, first.unsigned.pubkey)
        assertEquals(first.unsigned.idHex(), first.eventId)
        assertEquals(SLUG, first.unsigned.tags.first { it[0] == "d" }[1])
        assertTrue(first.approvalUrl!!.startsWith(FakePlatformSigner.APPROVAL_ORIGIN))
        val status = assertNotNull(fake.draftStatus(use, first.eventId), "the signing service knows the draft")
        // An ordinary content: ONE approval in all (decision of the owner of 7 November 2026), Bruno confirming his own.
        assertFalse(status.doubleValidation, "an ordinary content asks for one approval")
        assertEquals(1, status.required)
        // The author is always named, in a single validation too, so that the signing service can refuse the author.
        val submitted = signer.submissions.single()
        assertFalse(submitted.doubleValidation)
        assertEquals(team.alice.id.toString(), submitted.author?.account)
        assertEquals(first.unsigned, submitted.unsigned)
        // Waiting: the link for Bruno, nothing published.
        val waiting = client.page("$section/relecture/$id", team.bruno).bodyAsText()
        assertTrue(waiting.contains(EditorialTexts.format(EditorialTexts.SIGNER_AWAITING, 0, 1)), waiting)
        assertTrue(waiting.contains(EditorialTexts.SIGNER_AWAITING_ONE))
        assertFalse(waiting.contains(EditorialTexts.SIGNER_AWAITING_TWO))
        assertTrue(waiting.contains("<a href=\"${first.approvalUrl}\">${EditorialTexts.SIGNER_LINK}</a>"))
        assertTrue(storedArticles().isEmpty(), "nothing published before the signature")
        assertTrue(fake.requests.isEmpty(), "no signature asked before the approvals")
        // Approved on the signing service: the next opening of the page asks the signature and publishes it.
        approveOnSigner(first.eventId)
        val published = client.page("$section/relecture/$id", team.bruno).bodyAsText()
        assertTrue(published.contains(EditorialTexts.SIGNER_PUBLISHED), published)
        assertEquals(1, fake.requests.size)
        assertEquals(first.unsigned, fake.requests.single().first, "the signature asked for exactly the event submitted")
        val stored = storedArticles().single()
        assertEquals(first.eventId, stored.id)
        assertEquals(platformKey, stored.pubkey)
        assertTrue(stored.verify())
        assertEquals(first.unsigned, stored.unsigned(), "published unchanged")
        val draft = drafts.find(id)!!
        assertEquals(DraftStatus.PUBLISHED, draft.status)
        assertEquals(SigningState.PUBLISHED, attempt(id).state)
        // A reload says published, without asking again.
        assertTrue(client.page("$section/relecture/$id", team.bruno).bodyAsText().contains(EditorialTexts.SIGNER_PUBLISHED))
        assertEquals(1, fake.requests.size)
        // The site shows it at once.
        val page = client.get("$site/comprendre/decryptages/$SLUG")
        assertEquals(HttpStatusCode.OK, page.status)
        assertTrue(page.bodyAsText().contains("[EXEMPLE] Une affirmation vérifiée"))
    }

    @Test
    fun aPendingSignatureIsFollowedOnTheNextReloadWithTheSameTicket() = testApplication {
        val client = start()
        val team = team()
        val id = client.approved(team)
        approveOnSigner(attempt(id).eventId)
        signer.pending = true
        val pending = client.page("$section/relecture/$id", team.bruno).bodyAsText()
        assertTrue(pending.contains(EditorialTexts.SIGNER_PENDING), pending)
        assertNotNull(attempt(id).ticket)
        assertTrue(storedArticles().isEmpty())
        signer.pending = false
        assertTrue(client.page("$section/relecture/$id", team.bruno).bodyAsText().contains(EditorialTexts.SIGNER_PUBLISHED))
        assertEquals(1, fake.requests.size, "the ticket is followed, the signature not asked twice")
        assertEquals(1, storedArticles().size)
    }

    @Test
    fun aRefusalIsShownAlertsTheDashboardAndARetryBuildsANewEvent() = testApplication {
        val client = start()
        val team = team()
        val id = client.approved(team)
        val first = attempt(id)
        approveOnSigner(first.eventId)
        signer.tamper = { SigningStatus.Refused("key_revoked") }
        val refused = client.page("$section/relecture/$id", team.bruno).bodyAsText()
        assertTrue(refused.contains(EditorialTexts.format(EditorialTexts.SIGNER_REFUSED, "key_revoked")), refused)
        assertTrue(refused.contains(EditorialTexts.RETRY))
        assertEquals(DraftStatus.SIGNING_REFUSED, drafts.find(id)!!.status)
        assertEquals("key_revoked", attempt(id).refusalCode)
        assertTrue(storedArticles().isEmpty(), "nothing published")
        assertTrue(client.page("/", team.bruno).bodyAsText().contains(EditorialTexts.ALERT_SIGNING_REFUSED), "an alert on the dashboard")
        // The author may neither retry nor withdraw.
        val seq = lastSeq()
        listOf(decision(EditorialDraftsSection.ACTION_RETRY, 1), decision(EditorialDraftsSection.ACTION_WITHDRAW, 1, "[EXEMPLE] Retrait.")).forEach { fields ->
            val denied = client.form("$section/relecture/$id", team.alice, fields)
            assertEquals(HttpStatusCode.Forbidden, denied.status, fields.first().second)
        }
        assertTrue(client.page("$section/relecture/$id", team.alice).bodyAsText().contains(EditorialTexts.SIGNING_YOU_WROTE))
        assertEquals(DraftStatus.SIGNING_REFUSED, drafts.find(id)!!.status)
        // Bruno retries: a new event, a created_at strictly after the first one.
        signer.tamper = null
        val retried = client.form("$section/relecture/$id", team.bruno, decision(EditorialDraftsSection.ACTION_RETRY, 1))
        assertEquals(HttpStatusCode.OK, retried.status)
        assertTrue(retried.bodyAsText().contains(EditorialTexts.DONE_RETRIED))
        val second = attempt(id)
        assertNotEquals(first.eventId, second.eventId)
        assertTrue(second.unsigned.createdAt > first.unsigned.createdAt)
        assertEquals(SigningState.SUBMITTED, second.state)
        assertEquals(DraftStatus.READY_TO_SIGN, drafts.find(id)!!.status)
        approveOnSigner(second.eventId)
        assertTrue(client.page("$section/relecture/$id", team.bruno).bodyAsText().contains(EditorialTexts.SIGNER_PUBLISHED))
        assertEquals(listOf(second.eventId), storedArticles().map { it.id })
        val actions = journalAfter(seq).map { it.action.removePrefix("section.editorial.comprendre.") }
        assertTrue(actions.containsAll(listOf("retry_signing_requested", "retry_signing")), actions.toString())
    }

    @Test
    fun aSignedEventWithAnotherIdOrAnotherKeyIsNeverPublished() = testApplication {
        val client = start()
        val team = team()
        // Another id: refused, nothing published.
        val wrongId = client.approved(team)
        approveOnSigner(attempt(wrongId).eventId)
        signer.tamper = { status -> if (status is SigningStatus.Signed) SigningStatus.Signed(status.event.copy(id = "0".repeat(64))) else status }
        assertTrue(client.page("$section/relecture/$wrongId", team.bruno).bodyAsText().contains(EditorialTexts.format(EditorialTexts.SIGNER_REFUSED, SignedEditorialPublisher.CODE_SIGNED_WRONG_ID)))
        assertEquals(DraftStatus.SIGNING_REFUSED, drafts.find(wrongId)!!.status)
        assertTrue(storedArticles().isEmpty())
        // Another key: the service returns the same content signed by a key that is not the one of the event submitted.
        val other = LocalEventSigner(IdentityKeys.generate())
        signer.tamper = { status ->
            if (status is SigningStatus.Signed) {
                val e = status.event
                SigningStatus.Signed(runBlocking { other.sign(UnsignedEvent(other.publicKey.hex, e.createdAt, e.kind, e.tags, e.content)) })
            } else {
                status
            }
        }
        val wrongKey = client.approved(team, title = "[EXEMPLE] Une autre affirmation", slug = "exemple-autre")
        val otherSlugAttempt = attempt(wrongKey)
        approveOnSigner(otherSlugAttempt.eventId)
        assertTrue(client.page("$section/relecture/$wrongKey", team.bruno).bodyAsText().contains(EditorialTexts.format(EditorialTexts.SIGNER_REFUSED, SignedEditorialPublisher.CODE_SIGNED_WRONG_KEY)))
        assertTrue(storedArticles().isEmpty(), "nothing published unsigned or signed by another key")
        // The checks themselves.
        val unsigned = otherSlugAttempt.unsigned
        val forged = other.sign(UnsignedEvent(other.publicKey.hex, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content))
        assertEquals(SignedEditorialPublisher.CODE_SIGNED_WRONG_KEY, SignedEditorialPublisher.checkSigned(forged, unsigned))
        assertEquals(SignedEditorialPublisher.CODE_SIGNED_WRONG_ID, SignedEditorialPublisher.checkSigned(forged.copy(id = "0".repeat(64)), forged.unsigned()))
        assertEquals(SignedEditorialPublisher.CODE_SIGNED_BAD_SIGNATURE, SignedEditorialPublisher.checkSigned(forged.copy(sig = "0".repeat(128)), forged.unsigned()))
        assertNull(SignedEditorialPublisher.checkSigned(forged, forged.unsigned()))
    }

    @Test
    fun afterAKeyRotationAnEventBuiltWithThePreviousKeyIsStillPublished() = testApplication {
        val client = start()
        val team = team()
        val id = client.approved(team)
        val submitted = attempt(id)
        assertEquals(platformKey, submitted.unsigned.pubkey)
        approveOnSigner(submitted.eventId)
        // The key of the use rotates after the submission; the service signs the event with the key it was built with.
        signer.keyOverride = IdentityKeys.generate().publicKey.hex
        val page = client.page("$section/relecture/$id", team.bruno).bodyAsText()
        assertTrue(page.contains(EditorialTexts.SIGNER_PUBLISHED), page)
        val stored = storedArticles().single()
        assertEquals(submitted.eventId, stored.id)
        assertEquals(platformKey, stored.pubkey)
        assertEquals(DraftStatus.PUBLISHED, drafts.find(id)!!.status)
    }

    @Test
    fun withoutAKeyNothingIsSentAndTheNextReloadSubmits() = testApplication {
        val client = start()
        val team = team()
        signer.keyAvailable = false
        val id = client.approved(team)
        assertTrue(signing.attempts(id).isEmpty(), "nothing sent")
        assertTrue(client.page("$section/relecture/$id", team.bruno).bodyAsText().contains(EditorialTexts.SIGNER_UNAVAILABLE))
        assertTrue(fake.requests.isEmpty())
        assertTrue(storedArticles().isEmpty(), "never a content published unsigned")
        assertEquals(DraftStatus.READY_TO_SIGN, drafts.find(id)!!.status)
        signer.keyAvailable = true
        val page = client.page("$section/relecture/$id", team.bruno).bodyAsText()
        assertTrue(page.contains(EditorialTexts.format(EditorialTexts.SIGNER_AWAITING, 0, 1)), page)
        assertEquals(SigningState.SUBMITTED, attempt(id).state)
    }

    @Test
    fun anExpiredSubmissionIsRetriedWithANewCreatedAt() = testApplication {
        val client = start()
        val team = team()
        val id = client.approved(team)
        val first = attempt(id)
        clock.advance(8.days)
        // A new session for Bruno, at the new time (the old one is idle).
        val bruno = fresh(team.bruno)
        val expired = client.page("$section/relecture/$id", bruno).bodyAsText()
        assertTrue(expired.contains(EditorialTexts.SIGNER_EXPIRED), expired)
        assertTrue(expired.contains(EditorialTexts.RETRY))
        assertEquals(SigningState.EXPIRED, attempt(id).state)
        // An approval arriving late changes nothing: the expired event is never signed.
        approveOnSigner(first.eventId)
        assertTrue(client.page("$section/relecture/$id", bruno).bodyAsText().contains(EditorialTexts.SIGNER_EXPIRED))
        assertTrue(fake.requests.isEmpty())
        val retried = client.form("$section/relecture/$id", bruno, decision(EditorialDraftsSection.ACTION_RETRY, 1))
        assertEquals(HttpStatusCode.OK, retried.status)
        val second = attempt(id)
        assertNotEquals(first.eventId, second.eventId)
        assertTrue(second.unsigned.createdAt > first.unsigned.createdAt, "a new created_at")
        assertEquals(first.unsigned.content, second.unsigned.content)
        assertEquals(listOf(SigningState.SUBMITTED, SigningState.EXPIRED), signing.attempts(id).map { it.state })
        approveOnSigner(second.eventId)
        assertTrue(client.page("$section/relecture/$id", bruno).bodyAsText().contains(EditorialTexts.SIGNER_PUBLISHED))
        assertEquals(listOf(second.eventId), storedArticles().map { it.id })
    }

    @Test
    fun anUpdateKeepsTheAddressNeedsItsSummaryAndEntersThePublicJournal() = testApplication {
        val client = start()
        val team = team()
        val id = client.approved(team)
        approveOnSigner(attempt(id).eventId)
        client.page("$section/relecture/$id", team.bruno)
        val original = storedArticles().single()
        // Alice updates it: the nature of the change and its summary are required before the review.
        val update = draftIdOf(client.form("$section/mise-a-jour", team.alice, listOf("slug" to SLUG)))
        assertEquals(original.id, drafts.find(update)!!.baseEventId)
        val refused = client.form("$section/brouillons/$update/soumettre", team.alice, listOf("version" to "1"))
        assertEquals(HttpStatusCode.UnprocessableEntity, refused.status)
        val refusedHtml = refused.bodyAsText()
        assertTrue(refusedHtml.contains(EditorialTexts.CHANGE_KIND_REQUIRED), refusedHtml)
        assertTrue(refusedHtml.contains(EditorialTexts.CHANGE_NOTE_REQUIRED))
        assertTrue(refusedHtml.contains("href=\"#change-note\""), "the summary of errors leads to the field")
        val summary = "[EXEMPLE] Le verdict était trop sévère."
        val saved = client.form(
            "$section/brouillons/$update", team.alice,
            fields(title = "[EXEMPLE] Une affirmation vérifiée, corrigée") + listOf("version" to "1", "change_kind" to "correction", "change_note" to summary),
        )
        assertEquals(HttpStatusCode.Found, saved.status)
        assertEquals(HttpStatusCode.Found, client.form("$section/brouillons/$update/soumettre", team.alice, listOf("version" to "2")).status)
        assertEquals(HttpStatusCode.OK, client.form("$section/relecture/$update", team.bruno, decision(EditorialDraftsSection.ACTION_APPROVE, 2)).status)
        val submitted = attempt(update).unsigned
        // The same address (kind, key, d) and a created_at strictly after the published version.
        assertEquals(original.kind, submitted.kind)
        assertEquals(original.pubkey, submitted.pubkey)
        assertEquals(SLUG, submitted.tags.first { it[0] == "d" }[1])
        assertTrue(submitted.createdAt > original.createdAt)
        val corrections = submitted.tags.filter { it[0] == SignedEditorialPublisher.TAG_CORRECTION }
        assertEquals(1, corrections.size)
        assertEquals(summary, corrections.single()[2])
        assertEquals("correction", corrections.single()[3])
        assertTrue(Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(corrections.single()[1]))
        approveOnSigner(attempt(update).eventId)
        assertTrue(client.page("$section/relecture/$update", team.bruno).bodyAsText().contains(EditorialTexts.SIGNER_PUBLISHED))
        // The address holds the new version only.
        assertEquals(listOf(attempt(update).eventId), storedArticles().map { it.id })
        // The public journal of corrections, read from the published events, without script.
        val journal = client.get("$site/corrections")
        assertEquals(HttpStatusCode.OK, journal.status)
        val html = journal.bodyAsText()
        assertTrue(html.contains(summary), html)
        assertTrue(html.contains("href=\"/comprendre/decryptages/$SLUG\""))
        assertFalse(html.contains("<script"))
        // A second update keeps the first line and adds its own.
        val again = draftIdOf(client.form("$section/mise-a-jour", team.alice, listOf("slug" to SLUG)))
        client.form("$section/brouillons/$again", team.alice, fields(title = "[EXEMPLE] Titre complété") + listOf("version" to "1", "change_kind" to "mise-a-jour", "change_note" to "[EXEMPLE] Une source ajoutée."))
        client.form("$section/brouillons/$again/soumettre", team.alice, listOf("version" to "2"))
        client.form("$section/relecture/$again", team.bruno, decision(EditorialDraftsSection.ACTION_APPROVE, 2))
        val third = attempt(again).unsigned
        assertEquals(listOf(summary, "[EXEMPLE] Une source ajoutée."), third.tags.filter { it[0] == SignedEditorialPublisher.TAG_CORRECTION }.map { it[2] })
        assertTrue(third.createdAt > submitted.createdAt)
    }

    @Test
    fun anUpdateOlderThanThePublishedVersionIsRefused() = testApplication {
        val client = start()
        val team = team()
        val id = client.approved(team)
        approveOnSigner(attempt(id).eventId)
        client.page("$section/relecture/$id", team.bruno)
        val update = draftIdOf(client.form("$section/mise-a-jour", team.alice, listOf("slug" to SLUG)))
        client.form("$section/brouillons/$update", team.alice, fields(title = "[EXEMPLE] Titre corrigé") + listOf("version" to "1", "change_kind" to "correction", "change_note" to "[EXEMPLE] Résumé."))
        client.form("$section/brouillons/$update/soumettre", team.alice, listOf("version" to "2"))
        client.form("$section/relecture/$update", team.bruno, decision(EditorialDraftsSection.ACTION_APPROVE, 2))
        val ours = attempt(update)
        // Meanwhile, a newer version of the address is published by another path (signed by the same service).
        val newer = ours.unsigned.copy(createdAt = ours.unsigned.createdAt + 1_000, content = ours.unsigned.content + "\n[EXEMPLE] Autre version.\n")
        assertIs<DraftSubmission.Submitted>(fake.submitDraft(newer, use, doubleValidation = true, author = DraftAuthor("autre")))
        approveOnSigner(newer.idHex())
        val signed = assertIs<SigningStatus.Signed>(fake.status(fake.request(newer, use))).event
        events.store(signed)
        approveOnSigner(ours.eventId)
        val page = client.page("$section/relecture/$update", team.bruno).bodyAsText()
        assertTrue(page.contains(EditorialTexts.format(EditorialTexts.SIGNER_REFUSED, SignedEditorialPublisher.CODE_STALE_UPDATE)), page)
        assertEquals(listOf(signed.id), storedArticles().map { it.id }, "the newer version stays")
    }

    @Test
    fun aWithdrawalNeedsAReasonGoesBackToDraftAndTheJournalCarriesNoFreeText() = testApplication {
        val client = start()
        val team = team()
        val seq = lastSeq()
        val id = client.approved(team)
        assertEquals(HttpStatusCode.UnprocessableEntity, client.form("$section/relecture/$id", team.bruno, decision(EditorialDraftsSection.ACTION_WITHDRAW, 1)).status)
        val reason = "[EXEMPLE] Motif de retrait très reconnaissable 7d3e"
        val withdrawn = client.form("$section/relecture/$id", team.bruno, decision(EditorialDraftsSection.ACTION_WITHDRAW, 1, reason))
        assertEquals("$section/relecture/$id?ok=withdrawn", withdrawn.headers[HttpHeaders.Location])
        val draft = drafts.find(id)!!
        assertEquals(DraftStatus.DRAFT, draft.status)
        assertEquals(reason, draft.withdrawReason, "the reason stays with the draft")
        assertEquals(SigningState.WITHDRAWN, attempt(id).state)
        // The submission is never signed now, even with its approvals.
        approveOnSigner(attempt(id).eventId)
        client.page("$section/relecture/$id", team.bruno)
        assertTrue(fake.requests.isEmpty())
        assertTrue(storedArticles().isEmpty())
        // The writers see the reason with their editable draft.
        val page = client.page("$section/brouillons/$id", team.alice).bodyAsText()
        assertTrue(page.contains(EditorialTexts.LAST_WITHDRAWAL))
        assertTrue(page.contains(reason))
        assertTrue(page.contains("name=\"body\""), "editable again")
        // The journal: identifiers and codes only.
        val lines = journalAfter(seq).filter { it.action.startsWith("section.editorial.comprendre.") }
        assertTrue(lines.any { it.action.endsWith(".withdraw") })
        lines.forEach { line ->
            assertNull(line.reason, line.action)
            line.details.values.forEach { value ->
                assertFalse(value.contains("EXEMPLE"), "${line.action}: $value")
                assertTrue(Regex("^[a-z0-9_-]{1,100}$").matches(value), "${line.action}: $value")
            }
        }
    }

    @Test
    fun theSignatureStateIsKeptOutsideTheDatabaseOfSecrets() {
        // The stored unsigned event is read back identically (same id).
        val keys = IdentityKeys.generate()
        val unsigned = UnsignedEvent(keys.publicKey.hex, 1_900_000_000, Article.KIND, listOf(listOf("d", "x"), listOf("ppf-correction", "2026-11-08", "a \"b\" \\ c\n", "correction")), "é’\n")
        assertEquals(unsigned.idHex(), EditorialSigning.decodeUnsigned(EditorialSigning.encodeUnsigned(unsigned)).idHex())
        assertEquals("approvals_missing", EditorialSigning.safeCode("approvals_missing"))
        assertEquals("a_b_c", EditorialSigning.safeCode("A b<c"))
    }

    /** A new session of [member] at the current time of the clock. */
    private fun fresh(member: TestMember): TestMember = TestDatabase.dataSource.transaction { connection ->
        TestMember(member.account, member.keys, BackOfficeTestData.sessions.open(connection, member.id, clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)).token)
    }

    private companion object {
        const val SLUG = "exemple-decryptage"
    }
}
