package org.politisationpopulairefrancaise.server.backoffice

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.messaging.Nip44
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import java.time.Instant
import java.util.Base64
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pure parts of the base: hash chain, permissions, tokens, anti-forgery, NIP-98, settings, NIP-44 sealing. */
class BackOfficeUnitTest {

    // Hash chain ---------------------------------------------------------------------------------------

    private fun chain(size: Int): List<AuditRecord> {
        var previous = AuditChain.GENESIS
        return (1..size).map { seq ->
            val details = mapOf("b" to "2", "a" to "1")
            val hash = AuditChain.hashOf(seq.toLong(), 1_000L + seq, null, "système", "team.test_action", "team_account", "id-$seq", null, AuditChain.encodeDetails(details), previous)
            AuditRecord(seq.toLong(), 1_000L + seq, null, "système", "team.test_action", "team_account", "id-$seq", null, details, previous, hash).also { previous = hash }
        }
    }

    @Test
    fun anIntactChainVerifies() {
        val records = chain(5)
        val check = AuditChain.verify(records.asSequence())
        assertEquals(AuditChain.Check.Intact(5, records.last().hash), check)
    }

    @Test
    fun aChangedLineBreaksTheChainAtThatLine() {
        val records = chain(5).toMutableList()
        records[2] = records[2].copy(reason = "ajouté après coup")
        assertEquals(AuditChain.Check.Broken(3, "hash"), AuditChain.verify(records.asSequence()))
    }

    @Test
    fun aRemovedLineBreaksTheChain() {
        val records = chain(5).filterNot { it.seq == 3L }
        assertEquals(AuditChain.Check.Broken(3, "missing_line"), AuditChain.verify(records.asSequence()))
    }

    @Test
    fun aRenumberedChainStillRevealsTheRemovedLine() {
        // Someone removes line 3 and renumbers the next ones: the previous hash of the new line 3 is wrong.
        val records = chain(5).filterNot { it.seq == 3L }.mapIndexed { index, record -> record.copy(seq = index + 1L) }
        val check = AuditChain.verify(records.asSequence())
        assertIs<AuditChain.Check.Broken>(check)
        assertEquals(3, check.seq)
    }

    @Test
    fun detailsAreCanonicalWhateverTheirOrder() {
        assertEquals(AuditChain.encodeDetails(mapOf("a" to "1", "b" to "2")), AuditChain.encodeDetails(mapOf("b" to "2", "a" to "1")))
        assertEquals("""{"a":"1","b":"2"}""", AuditChain.encodeDetails(mapOf("b" to "2", "a" to "1")))
        assertEquals(mapOf("a" to "1", "b" to "2"), AuditChain.decodeDetails("""{"a":"1","b":"2"}"""))
    }

    @Test
    fun everyFieldEntersTheHash() {
        val base = AuditChain.hashOf(1, 10, null, "x", "a.b", null, null, null, "{}", AuditChain.GENESIS)
        assertNotEquals(base, AuditChain.hashOf(2, 10, null, "x", "a.b", null, null, null, "{}", AuditChain.GENESIS))
        assertNotEquals(base, AuditChain.hashOf(1, 11, null, "x", "a.b", null, null, null, "{}", AuditChain.GENESIS))
        assertNotEquals(base, AuditChain.hashOf(1, 10, "id", "x", "a.b", null, null, null, "{}", AuditChain.GENESIS))
        assertNotEquals(base, AuditChain.hashOf(1, 10, null, "y", "a.b", null, null, null, "{}", AuditChain.GENESIS))
        assertNotEquals(base, AuditChain.hashOf(1, 10, null, "x", "a.c", null, null, null, "{}", AuditChain.GENESIS))
        assertNotEquals(base, AuditChain.hashOf(1, 10, null, "x", "a.b", "t", null, null, "{}", AuditChain.GENESIS))
        assertNotEquals(base, AuditChain.hashOf(1, 10, null, "x", "a.b", null, "t", null, "{}", AuditChain.GENESIS))
        assertNotEquals(base, AuditChain.hashOf(1, 10, null, "x", "a.b", null, null, "r", "{}", AuditChain.GENESIS))
        assertNotEquals(base, AuditChain.hashOf(1, 10, null, "x", "a.b", null, null, null, """{"k":"v"}""", AuditChain.GENESIS))
        assertNotEquals(base, AuditChain.hashOf(1, 10, null, "x", "a.b", null, null, null, "{}", "1".repeat(64)))
    }

    @Test
    fun auditEntriesRefuseFreeFormActionsAndLongReasons() {
        assertFailsWith<IllegalArgumentException> { AuditEntry(AuditActor.System, "Not An Id") }
        assertFailsWith<IllegalArgumentException> { AuditEntry(AuditActor.System, "team.x", reason = "x".repeat(1_001)) }
    }

    // Permissions --------------------------------------------------------------------------------------

    private val id = UUID.randomUUID()

    @Test
    fun rolesGiveTheirPermissionsOnly() {
        val moderator = TeamActor.of(id, "m", setOf(TeamRole.MODERATOR), credentialCount = 2)
        assertTrue(moderator.has(Permissions.queueDecide("agir.held")))
        assertTrue(moderator.has(Permissions.REPORTS_DECIDE))
        assertFalse(moderator.has(Permissions.TEAM_GRANT))
        assertFalse(moderator.has(Permissions.AUDIT_READ))
        val admin = TeamActor.of(id, "a", setOf(TeamRole.ADMIN), credentialCount = 2)
        assertTrue(admin.has(Permissions.TEAM_INVITE))
        assertTrue(admin.has(Permissions.AUDIT_READ))
        assertFalse(admin.has(Permissions.TEAM_MANAGE_ADMINS))
        assertFalse(admin.has(Permissions.queueDecide("agir.held")))
        val validator = TeamActor.of(id, "v", setOf(TeamRole.VALIDATOR), credentialCount = 1)
        assertEquals(setOf(Permissions.VALIDATION_SESSION_OPEN), validator.permissions)
        val certifier = TeamActor.of(id, "c", setOf(TeamRole.ELECTED_CERTIFIER), credentialCount = 2)
        assertEquals(setOf(Permissions.ELECTED_CERTIFY, Permissions.ELECTED_REVOKE), certifier.permissions)
    }

    @Test
    fun superAdministratorsHoldEverythingButValidationSessions() {
        val superAdmin = TeamActor.of(id, "s", setOf(TeamRole.SUPER_ADMIN), credentialCount = 2)
        assertTrue(superAdmin.has(Permissions.TEAM_MANAGE_ADMINS))
        assertTrue(superAdmin.has(Permissions.queueDecide("revendications.proposals")))
        assertTrue(superAdmin.has("queue.any.future.queue"))
        assertFalse(superAdmin.has(Permissions.VALIDATION_SESSION_OPEN))
    }

    @Test
    fun aSingleKeyLeavesOnlyTheValidationSession() {
        val alone = TeamActor.of(id, "s", setOf(TeamRole.SUPER_ADMIN, TeamRole.VALIDATOR), credentialCount = 1)
        assertTrue(alone.needsBackupKey)
        assertFalse(alone.has(Permissions.TEAM_MANAGE_ADMINS))
        assertFalse(alone.has(Permissions.queueDecide("agir.held")))
        assertTrue(alone.has(Permissions.VALIDATION_SESSION_OPEN))
    }

    @Test
    fun administrationRolesNeedTheSuperAdministratorPermission() {
        assertEquals(Permissions.TEAM_MANAGE_ADMINS, TeamRole.ADMIN.grantPermission)
        assertEquals(Permissions.TEAM_MANAGE_ADMINS, TeamRole.SUPER_ADMIN.grantPermission)
        assertEquals(Permissions.TEAM_GRANT, TeamRole.MODERATOR.grantPermission)
        assertEquals(Permissions.TEAM_GRANT, TeamRole.VALIDATOR.grantPermission)
        TeamRole.entries.forEach { assertEquals(it, TeamRole.fromId(it.id)) }
    }

    // Tokens and anti-forgery ----------------------------------------------------------------------------

    @Test
    fun tokensAre256BitsAndOnlyTheirHashIsComparable() {
        val token = Tokens.generate()
        assertEquals(43, token.length)
        assertTrue(Tokens.looksValid(token))
        assertEquals(32, Tokens.fromBase64Url(token)!!.size)
        assertEquals(64, Tokens.hash(token).length)
        assertNotEquals(Tokens.hash(token), Tokens.hash(Tokens.generate()))
        assertFalse(Tokens.looksValid("trop court"))
        assertFalse(Tokens.looksValid(token.dropLast(1) + "!"))
    }

    @Test
    fun csrfTokensBelongToOneSession() {
        val session = Tokens.generate()
        val csrf = Csrf.tokenFor(session)
        assertTrue(Csrf.isValid(session, csrf))
        assertFalse(Csrf.isValid(session, null))
        assertFalse(Csrf.isValid(session, ""))
        assertFalse(Csrf.isValid(Tokens.generate(), csrf))
        assertFalse(Csrf.isValid(session, csrf.dropLast(1) + if (csrf.last() == 'A') "B" else "A"))
        assertEquals(csrf, Csrf.tokenFor(session))
    }

    // Settings ---------------------------------------------------------------------------------------------

    @Test
    fun originsAreHttpsOrLocalInDevelopment() {
        assertEquals("https://equipe.preprod.politisation-populaire-francaise.org", BackOfficeConfig.checkedOrigin("https://equipe.preprod.politisation-populaire-francaise.org", "preproduction"))
        assertEquals("http://localhost:8081", BackOfficeConfig.checkedOrigin("http://localhost:8081", "development"))
        assertFailsWith<IllegalArgumentException> { BackOfficeConfig.checkedOrigin("http://localhost:8081", "production") }
        assertFailsWith<IllegalArgumentException> { BackOfficeConfig.checkedOrigin("http://equipe.example.org", "development") }
        assertFailsWith<IllegalArgumentException> { BackOfficeConfig.checkedOrigin("https://equipe.example.org/", "production") }
        assertFailsWith<IllegalArgumentException> { BackOfficeConfig.checkedOrigin("https://user@equipe.example.org", "production") }
        assertFailsWith<IllegalArgumentException> { BackOfficeConfig.checkedOrigin("ftp://equipe.example.org", "production") }
        assertEquals("localhost", BackOfficeConfig("http://localhost:8081").rpId)
    }

    @Test
    fun theBackOfficeIsOffWithoutOriginAndNeverOnThePublicPort() {
        assertNull(BackOfficeConfig.fromEnvironment("production", 8080) { null })
        val config = BackOfficeConfig.fromEnvironment("production", 8080) { if (it == BackOfficeConfig.ORIGIN_VARIABLE) "https://equipe.example.org" else null }
        assertEquals(8081, config?.port)
        assertFailsWith<IllegalArgumentException> {
            BackOfficeConfig.fromEnvironment("production", 8080) {
                when (it) {
                    BackOfficeConfig.ORIGIN_VARIABLE -> "https://equipe.example.org"
                    BackOfficeConfig.PORT_VARIABLE -> "8080"
                    else -> null
                }
            }
        }
        assertFailsWith<IllegalArgumentException> { BackOfficeConfig.parseAuditKey("pas une clé") }
    }

    // NIP-98 (the shared verification of core/urne/Nip98.kt, with the two properties of the back-office) ----------

    private val url = "https://equipe.example.org/api/v1/decider/validation"

    private fun verifier() = Nip98SessionVerifier(startedAt = NOW.minusSeconds(3_600))

    private fun header(event: SignedEvent): String = "Nostr " + Base64.getEncoder().encodeToString(event.toJson().toByteArray())

    private fun signed(
        keys: IdentityKeys = IdentityKeys.generate(),
        kind: Int = Nip98SessionVerifier.KIND,
        createdAt: Long = NOW.epochSecond,
        u: String = url,
        method: String = "POST",
        payload: String? = null,
        extra: List<List<String>> = emptyList(),
    ): SignedEvent = runBlocking {
        LocalEventSigner(keys).sign(kind = kind, createdAt = createdAt, tags = listOfNotNull(listOf("u", u), listOf("method", method), payload?.let { listOf("payload", it) }) + extra)
    }

    @Test
    fun aFreshSignedRequestGivesItsKey() {
        val keys = IdentityKeys.generate()
        val event = signed(keys)
        val check = verifier().check(SignedSessionRequest(header(event), url, "POST"), NOW) { it == keys.publicKey.hex }
        assertEquals(SessionRequestCheck.Signed(keys.publicKey.hex, event.id), check)
    }

    @Test
    fun malformedOrMismatchedRequestsAreRefused() {
        val verifier = verifier()
        fun code(authorization: String?, body: ByteArray? = null, method: String = "POST", at: Instant = NOW) =
            (verifier.check(SignedSessionRequest(authorization, url, method, body), at) { true } as SessionRequestCheck.Refused).code
        assertEquals("missing_authorization", code(null))
        assertEquals("missing_authorization", code("Bearer abc"))
        assertEquals("malformed_authorization", code("Nostr %%%"))
        assertEquals("malformed_authorization", code("Nostr " + Base64.getEncoder().encodeToString("{}".toByteArray())))
        assertEquals("malformed_authorization", code("Nostr " + "A".repeat(20_000)))
        assertEquals("invalid_signature", code(header(signed(kind = 1))))
        assertEquals("expired_authorization", code(header(signed(createdAt = NOW.epochSecond - 61))))
        assertEquals("expired_authorization", code(header(signed(createdAt = NOW.epochSecond + 61))))
        assertEquals("wrong_url", code(header(signed(u = "$url/autre"))))
        // Exactly one address and one method: a second tag is not ignored.
        assertEquals("wrong_url", code(header(signed(extra = listOf(listOf("u", "$url/autre"))))))
        assertEquals("wrong_method", code(header(signed(extra = listOf(listOf("method", "DELETE"))))))
        assertEquals("wrong_method", code(header(signed()), method = "GET"))
        assertEquals("wrong_payload", code(header(signed()), body = "{}".toByteArray()))
        assertEquals("wrong_payload", code(header(signed(payload = "0".repeat(64))), body = "{}".toByteArray()))
        assertEquals("wrong_payload", code(header(signed(payload = "0".repeat(64)))), "a digest without a body")
        val good = signed(payload = Tokens.sha256Hex("{}".toByteArray()))
        assertIs<SessionRequestCheck.Signed>(verifier.check(SignedSessionRequest(header(good), url, "POST", "{}".toByteArray()), NOW) { true })
        val forged = signed().copy(sig = "0".repeat(128))
        assertEquals("invalid_signature", code(header(forged)))
    }

    @Test
    fun anUnknownSignerIsRefusedBeforeItsSignatureIsChecked() {
        var asked = 0
        val forged = signed().copy(sig = "0".repeat(128))
        val check = verifier().check(SignedSessionRequest(header(forged), url, "POST"), NOW) { asked++; false }
        assertEquals(SessionRequestCheck.Refused("no_open_session"), check)
        assertEquals(1, asked)
    }

    @Test
    fun eventsOlderThanTheStartOfTheProcessAreRefused() {
        val started = Nip98SessionVerifier(startedAt = NOW)
        val before = signed(createdAt = NOW.epochSecond - 10)
        assertEquals(SessionRequestCheck.Refused("before_start"), started.check(SignedSessionRequest(header(before), url, "POST"), NOW) { true })
        assertIs<SessionRequestCheck.Signed>(started.check(SignedSessionRequest(header(signed()), url, "POST"), NOW) { true })
    }

    @Test
    fun aRequestIsAcceptedOnlyOnce() {
        val verifier = verifier()
        val request = SignedSessionRequest(header(signed()), url, "POST")
        assertIs<SessionRequestCheck.Signed>(verifier.check(request, NOW) { true })
        assertEquals(SessionRequestCheck.Refused("replayed"), verifier.check(request, NOW) { true })
    }

    // NIP-44 towards the audit key -----------------------------------------------------------------------

    @Test
    fun onlyTheAuditKeyReadsASealedText() {
        val audit = IdentityKeys.generate()
        val sealed = AuditSealer(audit.publicKey).seal("compte abc validé par la séance 42")
        val key = Nip44.conversationKey(audit, org.politisationpopulairefrancaise.core.identity.PublicKey.fromHex(sealed.ephemeralPubkey))
        assertEquals("compte abc validé par la séance 42", Nip44.decrypt(sealed.payload, key))
        val other = IdentityKeys.generate()
        val wrong = Nip44.conversationKey(other, org.politisationpopulairefrancaise.core.identity.PublicKey.fromHex(sealed.ephemeralPubkey))
        assertFailsWith<Exception> { Nip44.decrypt(sealed.payload, wrong) }
        // A fresh key pair per text: two seals of the same text share nothing.
        assertNotEquals(sealed.ephemeralPubkey, AuditSealer(audit.publicKey).seal("compte abc validé par la séance 42").ephemeralPubkey)
    }

    @Test
    fun queueItemsAndActionsAreChecked() {
        assertFailsWith<IllegalArgumentException> { QueueItem("x", "t", level = 6) }
        assertFailsWith<IllegalArgumentException> { QueueItem("", "t") }
        assertFailsWith<IllegalArgumentException> { QueueAction("Approuver !", "x", "queue.x.decide") }
        val queues = BackOfficeQueues()
        val queue = object : BackOfficeQueue {
            override val id = "test.queue"
            override val title = "[EXEMPLE] File"
            override val viewPermission = "queue.test.queue.decide"
            override fun pendingCount() = 0
            override fun page(cursor: String?, limit: Int) = QueuePage(emptyList())
            override fun render(itemId: String, html: kotlinx.html.FlowContent) = Unit
            override fun actions(itemId: String) = emptyList<QueueAction>()
            override fun perform(itemId: String, action: String, reason: String?, actor: TeamActor) = ActionOutcome.NotFound
        }
        queues.register(queue)
        assertFailsWith<IllegalArgumentException> { queues.register(queue) }
        assertEquals(listOf(queue), queues.visibleTo(TeamActor.of(id, "s", setOf(TeamRole.SUPER_ADMIN), 2)))
        assertEquals(emptyList(), queues.visibleTo(TeamActor.of(id, "m", setOf(TeamRole.MODERATOR), 2)))
    }

    @Test
    fun theWeekOfASessionIsTheIsoWeekInParis() {
        assertEquals("2026-W45", ValidationSessions.weekOf(Instant.parse("2026-11-07T10:00:00Z")))
        // Sunday 23:30 in Paris is still the week that ends.
        assertEquals("2026-W45", ValidationSessions.weekOf(Instant.parse("2026-11-08T22:30:00Z")))
        assertEquals("2026-W46", ValidationSessions.weekOf(Instant.parse("2026-11-08T23:30:00Z")))
    }

    // Ceremonies, failures, links, database settings (D-127) ------------------------------------------------

    @Test
    fun signInChallengesAreCheckedWithoutState() {
        val clock = MutableClock(NOW)
        val challenges = SignInChallenges(clock)
        val id = challenges.issue()
        assertEquals(56, Tokens.fromBase64Url(id)!!.size)
        assertNotNull(challenges.open(id))
        // Another process (another key) does not accept it; a changed byte is refused.
        assertNull(SignInChallenges(clock).open(id))
        val bytes = Tokens.fromBase64Url(id)!!
        bytes[0] = (bytes[0] + 1).toByte()
        assertNull(challenges.open(Tokens.base64Url(bytes)))
        assertTrue(challenges.markUsed(id))
        assertNull(challenges.open(id), "used once")
        assertFalse(challenges.markUsed(id))
        val old = challenges.issue()
        clock.advance(kotlin.time.Duration.parse("5m") + kotlin.time.Duration.parse("1s"))
        assertNull(challenges.open(old), "too old")
        assertNull(challenges.open("pas-un-défi"))
    }

    @Test
    fun aSlotKeepsOneCeremonyPerKey() {
        val clock = MutableClock(NOW)
        val slots = CeremonySlots<String>(clock)
        val (first, _) = slots.start("session") { challenge, at -> Ceremony.Confirm(challenge, at, id) }
        val (second, _) = slots.start("session") { challenge, at -> Ceremony.Confirm(challenge, at, id) }
        assertEquals(1, slots.size)
        assertNull(slots.take("session", first), "replaced")
        assertNull(slots.take("autre", second), "bound to its key")
        assertNotNull(slots.take("session", second))
        assertNull(slots.take("session", second), "used once")
        val (late, _) = slots.start("session") { challenge, at -> Ceremony.Confirm(challenge, at, id) }
        clock.advance(kotlin.time.Duration.parse("6m"))
        assertNull(slots.take("session", late))
        slots.start("x") { challenge, at -> Ceremony.Confirm(challenge, at, id) }
        clock.advance(kotlin.time.Duration.parse("6m"))
        slots.purge()
        assertEquals(0, slots.size)
    }

    @Test
    fun anonymousFailuresAreCountedByPeriodOfTenMinutes() {
        val clock = MutableClock(Instant.parse("2026-11-07T10:03:00Z"))
        val failures = AnonymousFailures(clock)
        repeat(3) { failures.count("unknown_key") }
        failures.count("enrolment_registration_refused")
        assertFalse(failures.hasClosed())
        assertTrue(failures.drain().isEmpty(), "the current period is not written yet")
        clock.advance(kotlin.time.Duration.parse("8m"))
        failures.count("unknown_key")
        assertTrue(failures.hasClosed())
        val start = Instant.parse("2026-11-07T10:00:00Z").epochSecond
        assertEquals(mapOf(start to mapOf("unknown_key" to 3, "enrolment_registration_refused" to 1)), failures.drain())
        assertEquals(mapOf(start + 600 to mapOf("unknown_key" to 1)), failures.drain(all = true))
        assertTrue(failures.drain(all = true).isEmpty())
    }

    @Test
    fun alertLinksStayInsideTheBackOffice() {
        assertTrue(BackOffice.isLocalLink("/journal"))
        assertTrue(BackOffice.isLocalLink("/files/agir.held/abc?x=1"))
        listOf("//ailleurs.example", "/\\ailleurs", "/.x", "https://x", "/a b", "/", "").forEach { assertFalse(BackOffice.isLocalLink(it), it) }
    }

    @Test
    fun theServerMigratesByItselfOnlyInDevelopment() {
        assertTrue(org.politisationpopulairefrancaise.server.db.Database.shouldMigrate("development") { null })
        assertFalse(org.politisationpopulairefrancaise.server.db.Database.shouldMigrate("production") { null })
        assertFalse(org.politisationpopulairefrancaise.server.db.Database.shouldMigrate("preproduction") { null })
        assertTrue(org.politisationpopulairefrancaise.server.db.Database.shouldMigrate("production") { if (it == "PPF_DATABASE_MIGRATE") "true" else null })
        assertFalse(org.politisationpopulairefrancaise.server.db.Database.shouldMigrate("development") { if (it == "PPF_DATABASE_MIGRATE") "FALSE" else null })
        assertFailsWith<IllegalArgumentException> { org.politisationpopulairefrancaise.server.db.Database.shouldMigrate("production") { "peut-être" } }
        assertEquals("ppf_app", org.politisationpopulairefrancaise.server.db.Database.appRole { if (it == "PPF_DATABASE_APP_ROLE") "ppf_app" else null })
        assertEquals("", org.politisationpopulairefrancaise.server.db.Database.appRole { null })
        assertFailsWith<IllegalArgumentException> { org.politisationpopulairefrancaise.server.db.Database.appRole { "ppf'; DROP" } }
        assertEquals("db", org.politisationpopulairefrancaise.server.db.DatabaseConfig("jdbc:postgresql://db:5432/ppf", "u", "secret-value").host)
        assertFalse(org.politisationpopulairefrancaise.server.db.DatabaseConfig("jdbc:postgresql://db:5432/ppf", "u", "secret-value").toString().contains("secret"))
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-07T10:00:00Z")
    }
}
