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
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.connector
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.politisationpopulairefrancaise.core.platform.KeysResponse
import org.politisationpopulairefrancaise.core.platform.RootRequestItem
import org.politisationpopulairefrancaise.core.platform.RootRequests
import org.politisationpopulairefrancaise.core.platform.SigningRefusals
import org.politisationpopulairefrancaise.core.platform.SigningUse
import org.politisationpopulairefrancaise.core.platform.WorkingKeyStates
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeTestData.journalAfter
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeTestData.lastSeq
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeTestData.member
import org.politisationpopulairefrancaise.server.signing.FakeSignerAdmin
import org.politisationpopulairefrancaise.server.signing.SignerAdmin
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** « Clés de la plateforme » (D-141): permissions, the ceremony of the root, the revocation, the fingerprints. */
class PlatformKeysTest {
    private val clock = MutableClock(Instant.now())
    private val bo = "http://localhost:${BackOfficeTestData.PORT}"
    private val day = 86_400L

    @BeforeTest
    fun emptyTables() = BackOfficeTestData.reset()

    private fun now(): Long = clock.instant().epochSecond

    private fun fake(): FakeSignerAdmin = FakeSignerAdmin(now = ::now)

    private fun backOffice(signer: SignerAdmin?): BackOffice =
        BackOffice(config = BackOfficeTestData.CONFIG, dataSource = TestDatabase.dataSource, clock = clock, signer = signer)

    private fun ApplicationTestBuilder.start(backOffice: BackOffice): HttpClient {
        engine {
            connector {
                host = "localhost"
                port = BackOfficeTestData.PORT
            }
        }
        application {
            install(ContentNegotiation) { json() }
            install(backOfficeHeaders(BackOfficeTestData.PORT))
            routing { createChild(PortSelector(BackOfficeTestData.PORT)).backOfficeRoutes(backOffice) }
        }
        return createClient { followRedirects = false }
    }

    private suspend fun HttpClient.page(path: String, cookie: String): HttpResponse = get("$bo$path") { header(HttpHeaders.Cookie, "$SESSION_COOKIE=$cookie") }

    private suspend fun HttpClient.form(path: String, cookie: String, fields: Map<String, String> = emptyMap()): HttpResponse =
        submitForm("$bo$path", parameters {
            append(Csrf.FIELD, Csrf.tokenFor(cookie))
            fields.forEach { (key, value) -> append(key, value) }
        }) {
            header(HttpHeaders.Cookie, "$SESSION_COOKIE=$cookie")
            header("Sec-Fetch-Site", "same-origin")
        }

    /** A service with one key to certify (Voir), one in use (Comprendre), no team list yet. */
    private fun FakeSignerAdmin.withKeys(): FakeSignerAdmin = apply {
        keys = KeysResponse(
            rootConfigured = true,
            keys = listOf(
                key(SigningUse.EDITORIAL_VOIR, WorkingKeyStates.AWAITING_CERTIFICATION),
                key(SigningUse.EDITORIAL_COMPRENDRE, WorkingKeyStates.ACTIVE, current = true, notBefore = now() - 80 * day, notAfter = now() + 10 * day),
            ),
        )
    }

    @Test
    fun onlyTheAdministratorsOpenThePlatformKeys() = testApplication {
        val backOffice = backOffice(fake().withKeys())
        val client = start(backOffice)
        val editor = member(backOffice, "Éditrice", listOf(TeamRole.EDITOR))
        val admin = member(backOffice, "Admin", listOf(TeamRole.ADMIN))
        val seq = lastSeq()
        assertEquals(HttpStatusCode.Forbidden, client.page("/plateforme", editor.token).status)
        assertEquals(HttpStatusCode.Forbidden, client.form("/plateforme/ceremonie", editor.token).status)
        assertEquals(HttpStatusCode.Forbidden, client.form("/plateforme/revocation", editor.token, mapOf("cle" to "a".repeat(64), "motif" to "x")).status)
        assertEquals(HttpStatusCode.Forbidden, client.page("/plateforme/demandes/${"0".repeat(32)}", editor.token).status)
        assertFalse("href=\"/plateforme\"" in client.page("/", editor.token).bodyAsText())
        assertTrue(journalAfter(seq).none { it.action.startsWith("platform.") })
        val page = client.page("/plateforme", admin.token)
        assertEquals(HttpStatusCode.OK, page.status)
        val html = page.bodyAsText()
        assertTrue("href=\"/plateforme\" aria-current=\"page\"" in html)
        assertTrue(BackOfficeTexts.signingUse("editorial.voir") in html && BackOfficeTexts.keyState("awaiting_certification") in html)
        assertTrue(BackOfficeTexts.WARNING_NO_TEAM_LIST in html)
        assertTrue(BackOfficeTexts.WARNING_KEY_ENDS in html, "the key in use ends in 10 days")
        assertTrue("1 ${BackOfficeTexts.WARNING_AWAITING}" in html)
        assertFalse(Regex("<script(?![^>]*src=)").containsMatchIn(html) || "style=" in html)
    }

    @Test
    fun withoutASigningServiceThePageSaysSo() = testApplication {
        val backOffice = backOffice(null)
        val client = start(backOffice)
        val admin = member(backOffice, "Admin", listOf(TeamRole.ADMIN))
        assertTrue(BackOfficeTexts.PLATFORM_SIGNER_OFF in client.page("/plateforme", admin.token).bodyAsText())
        assertEquals("signer_off", client.form("/plateforme/ceremonie", admin.token).headers[HttpHeaders.Location]?.substringAfter("erreur="))
    }

    @Test
    fun theCeremonyIsDepositedAndItsPageShowsTheQrCodeAndEverythingInClear() = testApplication {
        val signer = fake().withKeys()
        val backOffice = backOffice(signer)
        val client = start(backOffice)
        val admin = member(backOffice, "Admin", listOf(TeamRole.ADMIN))
        val editor = member(backOffice, "Éditrice", listOf(TeamRole.EDITOR))
        member(backOffice, "Une seule clé", listOf(TeamRole.EDITOR), keys = 1)
        val overview = client.page("/plateforme", admin.token).bodyAsText()
        assertTrue("1 ${BackOfficeTexts.PLATFORM_CEREMONY_CERTIFICATES}" in overview)
        assertTrue("Une seule clé" in overview && BackOfficeTexts.exclusion(Exclusion.NO_USE) in overview)
        val seq = lastSeq()
        val prepared = client.form("/plateforme/ceremonie", admin.token)
        assertEquals(HttpStatusCode.Found, prepared.status)
        val location = assertNotNull(prepared.headers[HttpHeaders.Location])
        val id = Regex("^/plateforme/demandes/([0-9a-f]{32})\\?ok=ceremony_prepared$").find(location)!!.groupValues[1]
        val events = signer.rootRequests.single()
        assertEquals(listOf(EventKinds.PLATFORM_KEY_CERTIFICATE, EventKinds.PLATFORM_TEAM_LIST), events.map { it.kind })
        val team = assertIs<RootRequestItem.Team>(RootRequests.read(events[1], signer.root.publicKey.hex))
        assertEquals(listOf(editor.id.toString()), team.members.map { it.account }, "the administrator approves nothing, the single-key editor is left out")
        val line = journalAfter(seq).single { it.action == "platform.ceremony_prepared" }
        assertEquals(id, line.details["request"])
        assertEquals(RootRequests.sha256(events), line.details["sha256"])
        assertEquals("1", line.details["certificates"])
        assertEquals("1", line.details["members"])
        assertEquals(admin.id.toString(), line.actorId)

        val page = client.page(location, admin.token)
        assertEquals(HttpStatusCode.OK, page.status)
        val html = page.bodyAsText()
        val sha = RootRequests.sha256(events)
        val text = "PPF-RACINE:1:development:$id:$sha"
        assertTrue(text in html)
        assertTrue(groupedBy4(sha) in html)
        val svg = Regex("<svg.*?</svg>", RegexOption.DOT_MATCHES_ALL).find(html)!!.value
        assertEquals(text, QrSvgTest.decode(svg), "the QR code of the page reads back")
        assertFalse("style=" in html, "no inline style, even in the QR code")
        assertTrue(BackOfficeTexts.notice("ceremony_prepared")!! in html)
        // Each passkey of the listed member, by its fingerprint, the same as on their own page of keys.
        val fingerprints = backOffice.keysOf(editor.id).map { assertNotNull(it.fingerprint) }
        assertEquals(2, fingerprints.size)
        fingerprints.forEach { assertTrue(it in html, it) }
        assertTrue(Regex("^[0-9a-f]{4}( [0-9a-f]{4}){3}$").matches(fingerprints.first()))
        val keysPage = client.page("/cles", editor.token).bodyAsText()
        fingerprints.forEach { assertTrue(it in keysPage, it) }
        assertTrue(BackOfficeTexts.KEYS_FINGERPRINT_HINT in keysPage)
        assertEquals(2, Regex(Regex.escape(BackOfficeTexts.REQUEST_STATE_WAITING)).findAll(html).count())

        // The owner's phone signs and sends the events to the service: the page says what it received.
        signer.certificates += signer.signAsRoot(events[0])
        assertEquals(1, Regex(Regex.escape(BackOfficeTexts.REQUEST_STATE_RECEIVED)).findAll(client.page(location, admin.token).bodyAsText()).count())
        signer.teamListEvent = signer.signAsRoot(events[1])
        val received = client.page(location, admin.token).bodyAsText()
        assertEquals(2, Regex(Regex.escape(BackOfficeTexts.REQUEST_STATE_RECEIVED)).findAll(received).count())
        assertFalse(BackOfficeTexts.REQUEST_STATE_WAITING in received)
        // The request is listed on the page of the keys while the service keeps it, then forgotten.
        assertTrue(location.substringBefore('?') in client.page("/plateforme", admin.token).bodyAsText())
        clock.advance((RootRequests.LIFETIME_SECONDS / 60).minutes)
        assertNull(backOffice.platformKeys.prepared(id), "forgotten when the service forgets it")
    }

    @Test
    fun aRevocationNeedsAReasonAndADateThatIsNotInTheFuture() = testApplication {
        val signer = fake().withKeys()
        val backOffice = backOffice(signer)
        val client = start(backOffice)
        val admin = member(backOffice, "Admin", listOf(TeamRole.ADMIN))
        val inUse = signer.keys.keys.single { it.current }
        fun errorOf(response: HttpResponse) = response.headers[HttpHeaders.Location]?.substringAfter("erreur=", "")
        assertEquals("reason_required", errorOf(client.form("/plateforme/revocation", admin.token, mapOf("cle" to inUse.pubkey, "date" to "", "motif" to " "))))
        assertEquals("revocation_in_future", errorOf(client.form("/plateforme/revocation", admin.token, mapOf("cle" to inUse.pubkey, "date" to localInput(now() + 3_600), "motif" to "[EXEMPLE] copiée"))))
        assertEquals("bad_date", errorOf(client.form("/plateforme/revocation", admin.token, mapOf("cle" to inUse.pubkey, "date" to "hier", "motif" to "[EXEMPLE] copiée"))))
        assertEquals("unknown_platform_key", errorOf(client.form("/plateforme/revocation", admin.token, mapOf("cle" to "b".repeat(64), "motif" to "[EXEMPLE] copiée"))))
        val awaiting = signer.keys.keys.single { it.state == WorkingKeyStates.AWAITING_CERTIFICATION }
        assertEquals("key_not_certified", errorOf(client.form("/plateforme/revocation", admin.token, mapOf("cle" to awaiting.pubkey, "motif" to "[EXEMPLE] copiée"))))
        assertTrue(signer.rootRequests.isEmpty())
        val seq = lastSeq()
        val past = now() - 3 * day
        val done = client.form("/plateforme/revocation", admin.token, mapOf("cle" to inUse.pubkey, "date" to localInput(past), "motif" to "[EXEMPLE] ordinateur volé"))
        assertTrue(done.headers[HttpHeaders.Location]!!.endsWith("?ok=revocation_prepared"))
        val revocation = assertIs<RootRequestItem.Revocation>(RootRequests.read(signer.rootRequests.single().single(), signer.root.publicKey.hex))
        assertEquals(inUse.pubkey, revocation.key)
        assertEquals(past - Math.floorMod(past, 60L), revocation.revokedFrom, "the minute typed, Paris time")
        assertFalse("volé" in signer.rootRequests.single().single().tags.flatten().joinToString(), "the reason never enters the event")
        val line = journalAfter(seq).single { it.action == "platform.revocation_prepared" }
        assertEquals("[EXEMPLE] ordinateur volé", line.reason)
        assertEquals(inUse.pubkey, line.details["key"])
        assertEquals(revocation.revokedFrom.toString(), line.details["effective_from"])
        // Without a date: now.
        client.form("/plateforme/revocation", admin.token, mapOf("cle" to inUse.pubkey, "motif" to "[EXEMPLE] encore"))
        assertEquals(now(), assertIs<RootRequestItem.Revocation>(RootRequests.read(signer.rootRequests.last().single(), signer.root.publicKey.hex)).revokedFrom)
        val page = client.page(done.headers[HttpHeaders.Location]!!, admin.token).bodyAsText()
        assertTrue(BackOfficeTexts.REQUEST_REVOCATION in page && inUse.pubkey in page)
    }

    @Test
    fun aPreparationAsksForThePasskeyAgainAndARefusalOfTheServiceIsShownWithItsCode() = testApplication {
        val signer = fake().withKeys()
        val backOffice = backOffice(signer)
        val client = start(backOffice)
        val admin = member(backOffice, "Admin", listOf(TeamRole.ADMIN))
        member(backOffice, "Éditrice", listOf(TeamRole.EDITOR))
        clock.advance(6.minutes)
        val confirm = client.form("/plateforme/ceremonie", admin.token)
        assertEquals(HttpStatusCode.OK, confirm.status)
        assertTrue(BackOfficeTexts.CONFIRM_TITLE in confirm.bodyAsText())
        assertTrue(signer.rootRequests.isEmpty())
        clock.advance((-6).minutes)
        signer.refuseWith = SigningRefusals.WRONG_USES
        val seq = lastSeq()
        val refused = client.form("/plateforme/ceremonie", admin.token)
        assertEquals(HttpStatusCode.UnprocessableEntity, refused.status)
        assertTrue(SigningRefusals.WRONG_USES in refused.bodyAsText())
        assertEquals(SigningRefusals.WRONG_USES, journalAfter(seq).single { it.action == "platform.ceremony_refused" }.details["code"])
        signer.refuseWith = null
        signer.available = false
        assertEquals("signer_unavailable", client.form("/plateforme/ceremonie", admin.token).headers[HttpHeaders.Location]?.substringAfter("erreur="))
        assertTrue(BackOfficeTexts.PLATFORM_SIGNER_UNAVAILABLE in client.page("/plateforme", admin.token).bodyAsText())
        assertEquals(HttpStatusCode.NotFound, client.page("/plateforme/demandes/${"0".repeat(32)}", admin.token).status)
    }
}
