package org.politisationpopulairefrancaise.server.urne

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.messaging.Nip44
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.urne.Nip98
import org.politisationpopulairefrancaise.core.urne.ValidationAnswer
import org.politisationpopulairefrancaise.core.urne.ValidationProof
import org.politisationpopulairefrancaise.core.urne.VoteLevel
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.backoffice.AuditSealer
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeTestData
import org.politisationpopulairefrancaise.server.backoffice.MutableClock
import org.politisationpopulairefrancaise.server.backoffice.Outcome
import org.politisationpopulairefrancaise.server.backoffice.SealedForAudit
import org.politisationpopulairefrancaise.server.backoffice.SignedSessionRequest
import org.politisationpopulairefrancaise.server.backoffice.TeamRole
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * The in-person validation (D-112, une-personne-une-voix.md 4.3; option B of the Directeur technique):
 * a session of a validator designated by a verified organisation, the proof shown by the citizen, the
 * wait until Monday, and nothing in clear that links an account to its session.
 */
class InPersonValidationsTest {
    /** Wednesday 4 November 2026, noon (UTC): the week of Monday 2 November. */
    private val clock = MutableClock(Instant.parse("2026-11-04T12:00:00Z"))
    private val organisation = IdentityKeys.generate().publicKey.hex
    private val editor = LocalEventSigner(IdentityKeys.generate())
    private val audit = IdentityKeys.generate()
    private val url = "https://politisation-populaire-francaise.org/api/v1/urnes/validations"

    @BeforeTest
    fun emptyTables() {
        BackOfficeTestData.reset()
        UrneFixtures.reset()
    }

    private val backOffice = BackOfficeTestData.backOffice(clock, verified = setOf(organisation))
    private val store = UrneStore(TestDatabase.dataSource)
    private val service = UrneService.create(store, UrneFixtures.CONFIG, clock, PersonAccounts(null, { it == editor.publicKey.hex }, { false }))
    private val validations = InPersonValidations(store, service, backOffice.validationGate, AuditSealer(audit.publicKey), clock)

    private fun <T> Outcome<T>.ok(): T = when (this) {
        is Outcome.Ok -> value
        is Outcome.Denied -> error("Refused: $code")
    }

    private var volunteers = 0

    /** An open session of a validator, its key, at most [max] validations. */
    private fun session(max: Int): Pair<UUID, LocalEventSigner> {
        val volunteer = BackOfficeTestData.member(backOffice, "Bénévole ${++volunteers}", listOf(TeamRole.VALIDATOR), keys = 1, organisation = organisation)
        val key = LocalEventSigner(IdentityKeys.generate())
        val opened = backOffice.openValidationSession(BackOfficeTestData.auth(backOffice, volunteer), key.publicKey.hex, max, 4.hours).ok()
        return opened.id to key
    }

    private fun proof(citizen: LocalEventSigner, at: Long = clock.instant().epochSecond): SignedEvent = runBlocking { ValidationProof.sign(citizen, at) }

    private fun request(session: LocalEventSigner, proof: SignedEvent): SignedSessionRequest {
        val body = proof.toJson().encodeToByteArray()
        return SignedSessionRequest(runBlocking { Nip98.authorization(session, url, "POST", body, clock.instant().epochSecond) }, url, "POST", body)
    }

    /** A new proof each time, as the application draws one every minute (the same proof twice is a replay). */
    private fun validate(session: LocalEventSigner, citizen: LocalEventSigner): ValidationAnswer {
        clock.advance(1.seconds)
        return validations.validate(request(session, proof(citizen)))
    }

    private fun pendingRows(): List<String> = TestDatabase.dataSource.connection.use { c ->
        c.createStatement().use { s -> s.executeQuery("SELECT row_to_json(p)::text FROM vote_validation_pending p").use { r -> buildList { while (r.next()) add(r.getString(1)) } } }
    }

    private fun sealedLinks(): List<Pair<LocalDate, String>> = TestDatabase.dataSource.connection.use { c ->
        c.createStatement().use { s -> s.executeQuery("SELECT week, sealed::text FROM vote_validation").use { r -> buildList { while (r.next()) add(r.getObject(1, LocalDate::class.java) to r.getString(2)) } } }
    }

    /** What the owner reads offline with the audit key. */
    private fun unseal(json: String): Map<String, String> {
        val sealed = Json.decodeFromString(SealedForAudit.serializer(), json)
        val key = Nip44.conversationKey(audit, PublicKey.fromHex(sealed.ephemeralPubkey))
        return Json.parseToJsonElement(Nip44.decrypt(sealed.payload, key)).jsonObject.mapValues { it.value.jsonPrimitive.content }
    }

    @Test
    fun aValidationWaitsForMondayAndNothingInClearLinksTheAccountToItsSession() {
        val (sessionId, sessionKey) = session(max = 2)
        val registered = LocalEventSigner(IdentityKeys.generate())
        val newcomer = LocalEventSigner(IdentityKeys.generate())
        service.register(registered.publicKey.hex)
        assertEquals(ValidationAnswer(ValidationAnswer.PENDING, "2026-11-09"), validate(sessionKey, registered))
        assertTrue(service.status(registered.publicKey.hex).validationPending)
        assertEquals("non-verifie", service.status(registered.publicKey.hex).level, "not before Monday")
        // Shown again: nothing more counted.
        assertEquals(ValidationAnswer(ValidationAnswer.ALREADY), validate(sessionKey, registered))
        // An account that never registered is validated too (the batch registers it on Monday).
        assertEquals(ValidationAnswer.PENDING, validate(sessionKey, newcomer).status)
        assertEquals(2, backOffice.validationSession(sessionId)!!.validations)
        // The session is full: a third person waits for nothing and nothing is written.
        val third = LocalEventSigner(IdentityKeys.generate())
        assertEquals("session_full", assertFailsWith<UrneRefusal> { validate(sessionKey, third) }.code)
        assertFalse(store.isValidationPending(third.publicKey.hex))
        // In clear: the key only, never the session, the validator, a time or an order number.
        val rows = pendingRows()
        assertEquals(2, rows.size)
        rows.forEach { row -> assertFalse(row.contains(sessionId.toString()) || row.contains(sessionKey.publicKey.hex) || row.contains("2026-11-0"), row) }
        assertEquals(setOf("pubkey", "sealed"), rows.map { Json.parseToJsonElement(it).jsonObject.keys }.flatten().toSet())
        // Monday 00:00 UTC: one batch, every waiting account verified from that Monday.
        clock.now = Instant.parse("2026-11-09T00:00:30Z")
        assertEquals(2, service.applyValidationsDue())
        assertEquals(0, service.applyValidationsDue(), "once per Monday")
        val account = store.account(registered.publicKey.hex)!!
        assertEquals(VoteLevel.VERIFIE, account.level)
        assertEquals(LocalDate.parse("2026-11-09"), account.levelWeek)
        assertEquals(VoteLevel.NON_VERIFIE, account.levelAt(LocalDate.parse("2026-11-02")), "a box opened last week keeps the old level")
        assertEquals(LocalDate.parse("2026-11-09"), store.account(newcomer.publicKey.hex)!!.registeredWeek)
        assertTrue(pendingRows().isEmpty())
        // Only the owner, offline, can read who was validated by which session.
        val links = sealedLinks()
        assertEquals(listOf(LocalDate.parse("2026-11-09"), LocalDate.parse("2026-11-09")), links.map { it.first })
        val read = links.map { unseal(it.second) }
        assertEquals(setOf(registered.publicKey.hex, newcomer.publicKey.hex), read.map { it.getValue("account") }.toSet())
        assertTrue(read.all { it["session"] == sessionId.toString() && it["week"] == "2026-11-09" })
        links.forEach { assertFalse(it.second.contains(registered.publicKey.hex) || it.second.contains(sessionId.toString())) }
        // Verified now: a new proof changes nothing.
        clock.now = Instant.parse("2026-11-09T10:00:00Z")
        val (_, nextSession) = session(max = 5)
        assertEquals(ValidationAnswer.ALREADY, validate(nextSession, registered).status)
    }

    @Test
    fun theProofAndTheSessionAreChecked() {
        val (_, sessionKey) = session(max = 40)
        val citizen = LocalEventSigner(IdentityKeys.generate())
        val now = clock.instant().epochSecond
        assertEquals("expired_proof", assertFailsWith<UrneRefusal> { validations.validate(request(sessionKey, proof(citizen, now - 301))) }.code)
        val tampered = proof(citizen).copy(tags = listOf(listOf(ValidationProof.TAG, ValidationProof.PURPOSE), listOf("x", "y")))
        assertEquals("invalid_proof", assertFailsWith<UrneRefusal> { validations.validate(request(sessionKey, tampered)) }.code)
        val forged = proof(citizen).copy(sig = "0".repeat(128))
        assertEquals("invalid_proof", assertFailsWith<UrneRefusal> { validations.validate(request(sessionKey, forged)) }.code)
        // A proof is used once.
        val shown = proof(citizen)
        assertEquals(ValidationAnswer.PENDING, validations.validate(request(sessionKey, shown)).status)
        assertEquals("proof_replayed", assertFailsWith<UrneRefusal> { validations.validate(request(sessionKey, shown)) }.code)
        // Not a natural person (here an editorial key): never validated (D-116).
        assertEquals("not_a_person", assertFailsWith<UrneRefusal> { validate(sessionKey, editor) }.code)
        // A key without an open session, or a request for another address, is refused before anything.
        val stranger = LocalEventSigner(IdentityKeys.generate())
        assertEquals("no_open_session", assertFailsWith<UrneRefusal> { validate(stranger, LocalEventSigner(IdentityKeys.generate())) }.code)
        val elsewhere = request(sessionKey, proof(LocalEventSigner(IdentityKeys.generate()))).let { SignedSessionRequest(it.authorization, "$url/autre", "POST", it.body) }
        assertEquals("wrong_url", assertFailsWith<UrneRefusal> { validations.validate(elsewhere) }.code)
        // An elected account needs no validation.
        val elected = LocalEventSigner(IdentityKeys.generate())
        service.register(elected.publicKey.hex)
        store.setLevel(elected.publicKey.hex, VoteLevel.ELU, LocalDate.parse("2026-10-26"), LocalDate.parse("2026-10-26"))
        assertEquals(ValidationAnswer(ValidationAnswer.ALREADY), validate(sessionKey, elected))
    }
}
