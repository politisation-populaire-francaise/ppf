package org.politisationpopulairefrancaise.server.backoffice

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
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
import io.ktor.http.parameters
import io.ktor.server.engine.connector
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.writeFully
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.publication.HeldStatus
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.server.NO_EDITORS
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.mobilization
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.testAgir
import org.politisationpopulairefrancaise.server.agir.HeldQueue
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeTestData.journalAfter
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeTestData.lastSeq
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeTestData.member
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.module
import org.politisationpopulairefrancaise.server.testRevendications
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** The back-office over HTTP: its own port only, headers, ceremonies, anti-forgery, the agir.held queue. */
class BackOfficeRoutesTest {
    private val clock = MutableClock(java.time.Instant.now())
    private val bo = "http://localhost:${BackOfficeTestData.PORT}"
    private val site = "http://localhost:8080"

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
            module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY, testRevendications(), testAgir(), backOffice = BackOfficeSetup(BackOfficeTestData.CONFIG, clock))
        }
        return createClient { followRedirects = false }
    }

    /** A back-office instance on the same database, for the data of the tests (its ceremonies are not those of the server). */
    private val data get() = BackOfficeTestData.backOffice(clock)

    private suspend fun HttpClient.json(path: String, body: String, cookie: String? = null, csrf: String? = null): HttpResponse = post("$bo$path") {
        contentType(ContentType.Application.Json)
        header(HttpHeaders.Origin, BackOfficeTestData.ORIGIN)
        header("Sec-Fetch-Site", "same-origin")
        cookie?.let { header(HttpHeaders.Cookie, "$SESSION_COOKIE=$it") }
        csrf?.let { header(Csrf.HEADER, it) }
        setBody(body)
    }

    private suspend fun HttpClient.page(path: String, cookie: String?): HttpResponse = get("$bo$path") {
        cookie?.let { header(HttpHeaders.Cookie, "$SESSION_COOKIE=$it") }
    }

    private suspend fun HttpClient.form(path: String, cookie: String, fields: Map<String, String>, site: String = "same-origin"): HttpResponse =
        submitForm("$bo$path", parameters { fields.forEach { (key, value) -> append(key, value) } }) {
            header(HttpHeaders.Cookie, "$SESSION_COOKIE=$cookie")
            header("Sec-Fetch-Site", site)
        }

    private fun HttpResponse.sessionCookie(): String {
        val cookie = headers.getAll(HttpHeaders.SetCookie).orEmpty().single { it.startsWith("$SESSION_COOKIE=") }
        listOf("Path=/", "Secure", "HttpOnly", "SameSite=Strict").forEach { assertTrue(cookie.contains(it), "$it in $cookie") }
        assertFalse(cookie.contains("Domain", ignoreCase = true), "a __Host- cookie has no Domain")
        return cookie.substringAfter('=').substringBefore(';')
    }

    @Test
    fun theBackOfficeAnswersOnlyOnItsOwnPortAndTheSiteNeverThere() = testApplication {
        val client = start()
        assertEquals(HttpStatusCode.NotFound, client.get("$site/connexion").status)
        assertEquals(HttpStatusCode.NotFound, client.get("$site/journal").status)
        assertEquals(HttpStatusCode.NotFound, client.post("$site/webauthn/connexion/options").status)
        assertEquals(HttpStatusCode.NotFound, client.get("$site/static/equipe.js").status)
        assertEquals(HttpStatusCode.OK, client.get("$site/health").status)
        assertEquals(HttpStatusCode.OK, client.get("$bo/connexion").status)
        assertEquals(HttpStatusCode.NotFound, client.get("$bo/health").status)
        assertEquals(HttpStatusCode.NotFound, client.get("$bo/api/v1/events").status)
        assertEquals(HttpStatusCode.NotFound, client.get("$bo/agenda").status)
        // Without a session, every page leads to the sign-in.
        val home = client.get("$bo/")
        assertEquals(HttpStatusCode.Found, home.status)
        assertEquals("/connexion", home.headers[HttpHeaders.Location])
    }

    @Test
    fun theBackOfficeHasTheHeadersOfTheSiteAndIsNeverCached() = testApplication {
        val client = start()
        val page = client.get("$bo/connexion")
        assertEquals("no-store", page.headers[HttpHeaders.CacheControl])
        assertEquals("noindex, nofollow", page.headers["X-Robots-Tag"])
        val csp = assertNotNull(page.headers["Content-Security-Policy"])
        assertTrue(csp.contains("script-src 'self'"))
        assertFalse(csp.contains("unsafe-inline"))
        assertEquals("DENY", page.headers["X-Frame-Options"])
        assertEquals("nosniff", page.headers["X-Content-Type-Options"])
        assertEquals("no-store", client.get("$bo/nulle-part").headers[HttpHeaders.CacheControl])
        // The site itself keeps its caches.
        assertEquals(null, client.get("$site/health").headers[HttpHeaders.CacheControl])
        val body = page.bodyAsText()
        assertTrue(body.contains("<html lang=\"fr\""))
        assertTrue(body.contains("<script src=\"/static/equipe.js\" defer=\"defer\"></script>"))
        assertFalse(Regex("<script(?![^>]*src=)").containsMatchIn(body), "no inline script")
        assertFalse(Regex("https?://").containsMatchIn(body), "no external address")
        assertFalse(body.contains("style="), "no inline style")
        val script = client.get("$bo/static/equipe.js")
        assertEquals(HttpStatusCode.OK, script.status)
        assertFalse(Regex("https?://").containsMatchIn(script.bodyAsText()), "the script calls nothing outside")
        val css = client.get("$bo/static/equipe.css").bodyAsText()
        assertTrue(css.contains("prefers-color-scheme: dark"))
        assertEquals(HttpStatusCode.OK, client.get("$bo/static/fonts/atkinson_hyperlegible_regular.ttf").status)
    }

    @Test
    fun aFirstSuperAdministratorJoinsAndSignsInWithAPasskey() = testApplication {
        val client = start()
        val invitation = data.bootstrapInvitation("Claire").let { (it as Outcome.Ok).value }
        val page = client.page("/invitation/${invitation.token}", null)
        assertEquals(HttpStatusCode.OK, page.status)
        val html = page.bodyAsText()
        assertTrue(html.contains("Claire"))
        assertTrue(html.contains("data-ceremony=\"enrol\""))
        assertEquals(HttpStatusCode.NotFound, client.page("/invitation/${Tokens.generate()}", null).status)

        val key = TestAuthenticator()
        val creation = Json.decodeFromString<CreationOptionsResponse>(client.json("/webauthn/inscription/options", """{"invitation":"${invitation.token}"}""").bodyAsText())
        assertEquals(BackOfficeTestData.RP_ID, creation.publicKey.rp.id)
        assertEquals("required", creation.publicKey.authenticatorSelection.userVerification)
        val enrolled = client.json("/webauthn/inscription", Json.encodeToString(RegistrationResponse.serializer(), key.register(creation, name = "Ordinateur")))
        assertEquals(HttpStatusCode.OK, enrolled.status, enrolled.bodyAsText())
        assertEquals("/cles?ok=welcome", Json.decodeFromString<CeremonyDone>(enrolled.bodyAsText()).redirect)
        val cookie = enrolled.sessionCookie()

        val dashboard = client.page("/", cookie)
        assertEquals(HttpStatusCode.OK, dashboard.status)
        val text = dashboard.bodyAsText()
        assertTrue(text.contains("Bonjour Claire"))
        assertTrue(text.contains(BackOfficeTexts.BACKUP_KEY_MISSING))
        // The invitation served once.
        assertEquals(HttpStatusCode.NotFound, client.page("/invitation/${invitation.token}", null).status)

        // Sign out, then sign in again with the same key.
        val keys = client.page("/cles", cookie).bodyAsText()
        val csrf = Regex("name=\"csrf\" value=\"([^\"]+)\"").find(keys)!!.groupValues[1]
        val out = client.form("/deconnexion", cookie, mapOf("csrf" to csrf))
        assertEquals(HttpStatusCode.Found, out.status)
        assertEquals(HttpStatusCode.Found, client.page("/", cookie).status)
        val request = Json.decodeFromString<RequestOptionsResponse>(client.json("/webauthn/connexion/options", "{}").bodyAsText())
        val signedIn = client.json("/webauthn/connexion", Json.encodeToString(AssertionResponse.serializer(), key.assertion(request)))
        assertEquals(HttpStatusCode.OK, signedIn.status, signedIn.bodyAsText())
        assertEquals(HttpStatusCode.OK, client.page("/", signedIn.sessionCookie()).status)
        // A refused key gets a French message for the script, and a stable code.
        val unknown = TestAuthenticator()
        val again = Json.decodeFromString<RequestOptionsResponse>(client.json("/webauthn/connexion/options", "{}").bodyAsText())
        val refused = client.json("/webauthn/connexion", Json.encodeToString(AssertionResponse.serializer(), unknown.assertion(again)))
        assertEquals(HttpStatusCode.BadRequest, refused.status)
        assertEquals(CeremonyRefused("unknown_key", BackOfficeTexts.error("unknown_key")), Json.decodeFromString<CeremonyRefused>(refused.bodyAsText()))
    }

    @Test
    fun formsNeedTheSessionTheAntiForgeryTokenAndTheSameOrigin() = testApplication {
        val client = start()
        val claire = member(data, "Claire", listOf(TeamRole.SUPER_ADMIN))
        val csrf = Csrf.tokenFor(claire.token)
        assertEquals(HttpStatusCode.Forbidden, client.form("/deconnexion", claire.token, emptyMap()).status)
        assertEquals(HttpStatusCode.Forbidden, client.form("/deconnexion", claire.token, mapOf("csrf" to Csrf.tokenFor(Tokens.generate()))).status)
        assertEquals(HttpStatusCode.Forbidden, client.form("/deconnexion", claire.token, mapOf("csrf" to csrf), site = "cross-site").status)
        val crossOrigin = client.submitForm("$bo/deconnexion", parameters { append("csrf", csrf) }) {
            header(HttpHeaders.Cookie, "$SESSION_COOKIE=${claire.token}")
            header(HttpHeaders.Origin, "https://ailleurs.example")
        }
        assertEquals(HttpStatusCode.Forbidden, crossOrigin.status)
        // JSON ceremonies of a session need the token too.
        assertEquals(HttpStatusCode.Forbidden, client.json("/webauthn/confirmation/options", "{}", cookie = claire.token).status)
        assertEquals(HttpStatusCode.OK, client.json("/webauthn/confirmation/options", "{}", cookie = claire.token, csrf = csrf).status)
        val out = client.form("/deconnexion", claire.token, mapOf("csrf" to csrf))
        assertEquals(HttpStatusCode.Found, out.status)
        assertEquals("/connexion?ok=signed_out", out.headers[HttpHeaders.Location])
        assertTrue(out.headers.getAll(HttpHeaders.SetCookie).orEmpty().any { it.startsWith("$SESSION_COOKIE=;") && it.contains("Max-Age=0") })
    }

    @Test
    fun theTeamReviewsAHeldMobilizationInTheBackOffice() = testApplication {
        val client = start()
        val citizen = LocalEventSigner(IdentityKeys.generate())
        val event = citizen.mobilization()
        val published = client.post("$site/api/v1/events") {
            contentType(ContentType.Application.Json)
            setBody(event.toJson())
        }
        assertEquals(HttpStatusCode.Accepted, published.status)
        val moderator = member(data, "Modo", listOf(TeamRole.MODERATOR))
        val csrf = Csrf.tokenFor(moderator.token)

        val dashboard = client.page("/", moderator.token).bodyAsText()
        assertTrue(dashboard.contains(BackOfficeTexts.AGIR_HELD_TITLE))
        assertTrue(dashboard.contains("[EXEMPLE] Manifestation pour les services publics"), "near its start, the mobilization is a priority")
        val list = client.page("/files/agir.held", moderator.token)
        assertEquals(HttpStatusCode.OK, list.status)
        assertTrue(list.bodyAsText().contains("/files/agir.held/${event.id}"))
        val detail = client.page("/files/agir.held/${event.id}", moderator.token).bodyAsText()
        assertTrue(detail.contains(BackOfficeTexts.AGIR_APPROVE))
        assertTrue(detail.contains(citizen.publicKey.npub))
        assertTrue(detail.contains("Départ à 14 heures"))

        // A refusal needs a reason; an editor without the permission sees nothing.
        val noReason = client.form("/files/agir.held/${event.id}", moderator.token, mapOf("csrf" to csrf, "action" to "reject", "motif" to " "))
        assertEquals("/files/agir.held/${event.id}?erreur=reason_required", noReason.headers[HttpHeaders.Location])
        val editor = member(data, "Éditrice", listOf(TeamRole.EDITOR))
        assertEquals(HttpStatusCode.NotFound, client.page("/files/agir.held", editor.token).status)

        // After 5 minutes, approving (a public publication) asks for the key again.
        clock.advance(6.minutes)
        val confirm = client.form("/files/agir.held/${event.id}", moderator.token, mapOf("csrf" to csrf, "action" to "approve"))
        assertEquals(HttpStatusCode.OK, confirm.status)
        val confirmPage = confirm.bodyAsText()
        assertTrue(confirmPage.contains(BackOfficeTexts.CONFIRM_TITLE))
        assertTrue(confirmPage.contains("name=\"action\" value=\"approve\""))
        val options = Json.decodeFromString<RequestOptionsResponse>(client.json("/webauthn/confirmation/options", "{}", moderator.token, csrf).bodyAsText())
        val confirmed = client.json("/webauthn/confirmation", Json.encodeToString(AssertionResponse.serializer(), moderator.keys[0].assertion(options, csrf)), moderator.token, csrf)
        assertEquals(HttpStatusCode.OK, confirmed.status, confirmed.bodyAsText())
        val seq = lastSeq()
        val approved = client.form("/files/agir.held/${event.id}", moderator.token, mapOf("csrf" to csrf, "action" to "approve"))
        assertEquals("/files/agir.held?ok=done", approved.headers[HttpHeaders.Location])
        assertEquals(HeldStatus.APPROVED, HeldQueue(TestDatabase.dataSource, EventRepository(TestDatabase.dataSource)).find(event.id)!!.status)
        assertNotNull(EventRepository(TestDatabase.dataSource).find(event.id), "published exactly as signed")
        val lines = journalAfter(seq)
        assertEquals(listOf("queue.agir.held.approve.requested", "queue.agir.held.approve"), lines.map { it.action })
        val line = lines.last()
        assertEquals("queue.agir.held.approve", line.action)
        assertEquals("agir.held:${event.id}", line.targetId)
        assertEquals("done", line.details["outcome"])
        assertFalse(line.details.values.any { it.contains("Départ") }, "never the text of the content")
        // Decided: no action left.
        assertTrue(client.page("/files/agir.held/${event.id}", moderator.token).bodyAsText().contains(BackOfficeTexts.ITEM_NO_ACTION))
    }

    @Test
    fun theJournalPageAndItsExportNeedAuditRead() = testApplication {
        val client = start()
        val admin = member(data, "Admin", listOf(TeamRole.ADMIN))
        val moderator = member(data, "Modo", listOf(TeamRole.MODERATOR))
        BackOfficeTestData.audit.record(AuditEntry(AuditActor.System, "team.route_test", "team_account", admin.id.toString()))
        assertEquals(HttpStatusCode.Forbidden, client.page("/journal", moderator.token).status)
        val page = client.page("/journal?action=team.route_test", admin.token)
        assertEquals(HttpStatusCode.OK, page.status)
        val html = page.bodyAsText()
        assertTrue(html.contains(BackOfficeTexts.JOURNAL_CHAIN_OK))
        assertTrue(html.contains("team.route_test"))
        assertEquals(HttpStatusCode.OK, client.page("/journal?du=pas-une-date", admin.token).status)
        val seq = lastSeq()
        val export = client.page("/journal.json?action=team.route_test", admin.token)
        assertEquals(HttpStatusCode.OK, export.status)
        assertTrue(export.headers[HttpHeaders.ContentDisposition].orEmpty().contains("attachment"))
        val lines = Json.decodeFromString<List<AuditRecord>>(export.bodyAsText())
        assertEquals(listOf("team.route_test"), lines.map { it.action })
        assertEquals(listOf("audit.exported"), journalAfter(seq).map { it.action })
        // The head of the journal is shown, to be noted or anchored outside (D-111).
        val head = assertNotNull(data.journalHead())
        assertTrue(client.page("/journal", admin.token).bodyAsText().contains(head.hash))
        assertEquals(HttpStatusCode.Forbidden, client.page("/journal.json", moderator.token).status)
    }

    @Test
    fun aNewKeyIsAddedThroughAProofOfAnExistingKey() = testApplication {
        val client = start()
        val claire = member(data, "Claire", keys = 1)
        val csrf = Csrf.tokenFor(claire.token)
        val proof = Json.decodeFromString<RequestOptionsResponse>(client.json("/webauthn/cle/preuve/options", "{}", claire.token, csrf).bodyAsText())
        assertEquals(1, proof.publicKey.allowCredentials.size)
        val assertion = claire.keys[0].assertion(proof, csrf)
        val creation = client.json("/webauthn/cle/options", Json.encodeToString(AssertionResponse.serializer(), assertion), claire.token, csrf)
        assertEquals(HttpStatusCode.OK, creation.status, creation.bodyAsText())
        val options = Json.decodeFromString<CreationOptionsResponse>(creation.bodyAsText())
        assertEquals(1, options.publicKey.excludeCredentials.size)
        val added = client.json("/webauthn/cle", Json.encodeToString(RegistrationResponse.serializer(), TestAuthenticator().register(options, "Clé USB", csrf)), claire.token, csrf)
        assertEquals(HttpStatusCode.OK, added.status, added.bodyAsText())
        assertEquals(2, data.keysOf(claire.id).size)
        // Without a proof, no registration opens.
        val bare = client.json("/webauthn/cle/options", "{}", claire.token, csrf)
        assertEquals(HttpStatusCode.BadRequest, bare.status)
    }

    @Test
    fun everyBodyIsBoundedEvenWithoutALength() = testApplication {
        val client = start()
        val claire = member(data, "Claire", listOf(TeamRole.SUPER_ADMIN))
        val big = "csrf=${Csrf.tokenFor(claire.token)}&motif=" + "a".repeat(MAX_BODY_BYTES)
        val declared = client.post("$bo/deconnexion") {
            header(HttpHeaders.Cookie, "$SESSION_COOKIE=${claire.token}")
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(big)
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, declared.status)
        val chunked = client.post("$bo/webauthn/connexion") {
            contentType(ContentType.Application.Json)
            setBody(object : io.ktor.http.content.OutgoingContent.WriteChannelContent() {
                override val contentType = ContentType.Application.Json
                override suspend fun writeTo(channel: io.ktor.utils.io.ByteWriteChannel) {
                    repeat(70) { channel.writeFully(ByteArray(1_024) { ' '.code.toByte() }) }
                }
            })
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, chunked.status)
        assertNotNull(data.authenticate(claire.token), "nothing was done")
    }

    @Test
    fun aCutTellsToRemoveTheWireGuardProfileToo() = testApplication {
        val client = start()
        val admin = member(data, "Admin", listOf(TeamRole.ADMIN))
        val moderator = member(data, "Modo", listOf(TeamRole.MODERATOR))
        val cut = client.form("/equipe/membres/${moderator.id}/coupure", admin.token, mapOf("csrf" to Csrf.tokenFor(admin.token)))
        assertEquals("/equipe/membres/${moderator.id}?ok=cut", cut.headers[HttpHeaders.Location])
        val page = client.page("/equipe/membres/${moderator.id}?ok=cut", admin.token).bodyAsText()
        assertTrue(page.contains("WireGuard"))
        assertTrue(page.contains(BackOfficeTexts.MEMBER_SUSPENDED))
    }

    @Test
    fun anInvitationIsCreatedFromTheTeamPageAndShownOnce() = testApplication {
        val client = start()
        val admin = member(data, "Admin", listOf(TeamRole.ADMIN))
        val csrf = Csrf.tokenFor(admin.token)
        val team = client.page("/equipe", admin.token).bodyAsText()
        assertTrue(team.contains(BackOfficeTexts.INVITE_BUTTON))
        assertFalse(team.contains("value=\"super_admin\""), "an administrator never proposes the super administrator role")
        val created = client.form("/equipe/invitations", admin.token, mapOf("csrf" to csrf, "pseudonyme" to "Nouvelle recrue", "roles" to "moderator"))
        assertEquals(HttpStatusCode.OK, created.status)
        val link = Regex("value=\"(http://localhost:8081/invitation/[A-Za-z0-9_-]{43})\"").find(created.bodyAsText())!!.groupValues[1]
        assertNotNull(data.findInvitation(link.substringAfterLast('/')))
        // The link is not on the team page.
        assertFalse(client.page("/equipe", admin.token).bodyAsText().contains(link.substringAfterLast('/')))
        val refused = client.form("/equipe/invitations", admin.token, mapOf("csrf" to csrf, "pseudonyme" to "x@y.fr"))
        assertEquals("/equipe?erreur=bad_pseudonym", refused.headers[HttpHeaders.Location])
    }
}
