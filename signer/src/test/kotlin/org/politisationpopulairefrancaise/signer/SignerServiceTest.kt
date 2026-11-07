package org.politisationpopulairefrancaise.signer

import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.platform.DraftAuthor
import org.politisationpopulairefrancaise.core.platform.KeyStatus
import org.politisationpopulairefrancaise.core.platform.PlatformTrust
import org.politisationpopulairefrancaise.core.platform.SigningRefusals
import org.politisationpopulairefrancaise.core.platform.SigningUse
import org.politisationpopulairefrancaise.core.platform.TeamMember
import org.politisationpopulairefrancaise.core.platform.WorkingKeyStates
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SignerServiceTest {
    private val f = SignerFixture()

    private fun SignerService.refusal(request: org.politisationpopulairefrancaise.core.platform.SignRequest): String? =
        (sign(request) as? SignOutcome.Refused)?.refusal?.code

    private fun SignerService.signed(request: org.politisationpopulairefrancaise.core.platform.SignRequest) =
        assertIs<SignOutcome.Signed>(sign(request), "expected a signature").event

    @Test
    fun anOrdinaryPublicationNeedsOneApprovalAndGivesAValidPlatformContent() {
        val service = f.ready()
        val key = f.currentKey(service, SigningUse.EDITORIAL_VOIR)!!
        val event = f.editorial(key)
        val signed = service.signed(f.request(SigningUse.EDITORIAL_VOIR, event, listOf(f.alice.approve(SigningUse.EDITORIAL_VOIR, event))))
        assertEquals(event.idHex(), signed.id)
        assertTrue(signed.verify())
        val trust = PlatformTrust.of(f.rootHex, service.certificates())
        assertTrue(trust.isPlatformContent(signed, SigningUse.EDITORIAL_VOIR))
        assertFalse(trust.isPlatformContent(signed, SigningUse.EDITORIAL_COMPRENDRE))
        // The author of an ordinary publication may approve it alone.
        val own = f.editorial(key, title = "[EXEMPLE] Autre")
        service.signed(f.request(SigningUse.EDITORIAL_VOIR, own, listOf(f.alice.approve(SigningUse.EDITORIAL_VOIR, own)), author = DraftAuthor(account = "alice")))
    }

    @Test
    fun nothingIsSignedWithoutRootNorWithAKeyWaitingForItsCertificate() {
        val noRoot = f.open(f.config(root = null))
        val waiting = f.key(noRoot, SigningUse.OPENDATA_INDEX, WorkingKeyStates.AWAITING_CERTIFICATION)
        assertEquals(SigningRefusals.NO_ROOT, noRoot.refusal(f.request(SigningUse.OPENDATA_INDEX, index(waiting))))
        assertEquals(SigningRefusals.NO_ROOT, noRoot.loadCertificates(listOf(f.certificate(waiting, listOf(SigningUse.OPENDATA_INDEX)))).single().status)
        assertFalse(noRoot.keys().rootConfigured)

        val other = SignerFixture()
        val service = other.open()
        val key = other.key(service, SigningUse.OPENDATA_INDEX, WorkingKeyStates.AWAITING_CERTIFICATION)
        assertEquals(SigningRefusals.KEY_NOT_CERTIFIED, service.refusal(other.request(SigningUse.OPENDATA_INDEX, index(key))))
        assertEquals(SigningRefusals.NO_ACTIVE_KEY, service.refusal(other.request(SigningUse.OPENDATA_INDEX, index(IdentityKeys.generate().publicKey.hex))))
        // A certificate signed by anyone but the root changes nothing.
        val impostor = IdentityKeys.generate()
        val forged = signWith(impostor, org.politisationpopulairefrancaise.core.platform.KeyCertificate.unsigned(impostor.publicKey.hex, key, listOf(SigningUse.OPENDATA_INDEX), other.now(), other.now() + DAY, other.now()))
        assertEquals("not_a_certificate", service.loadCertificates(listOf(forged)).single().status)
        assertEquals(SigningRefusals.KEY_NOT_CERTIFIED, service.refusal(other.request(SigningUse.OPENDATA_INDEX, index(key))))
        // Certified, it signs; a key of another use is refused.
        other.certify(service, SigningUse.OPENDATA_INDEX)
        service.signed(other.request(SigningUse.OPENDATA_INDEX, index(key)))
        val archiveKey = other.key(service, SigningUse.ARCHIVES_TIMESTAMPS)
        assertEquals(SigningRefusals.WRONG_KEY, service.refusal(other.request(SigningUse.OPENDATA_INDEX, index(archiveKey, other.now() + 1))))
    }

    @Test
    fun aRevokedKeyIsRefusedAndItsSecretDestroyed() {
        val service = f.ready()
        val key = f.currentKey(service, SigningUse.OPENDATA_INDEX)!!
        f.clock.advance(60)
        val revocation = f.certificate(key, listOf(SigningUse.OPENDATA_INDEX), f.now() - 60, f.now() - 60 + 90 * DAY, status = KeyStatus.REVOKED, revokedFrom = f.now())
        assertEquals("stored", service.loadCertificates(listOf(revocation)).single().status)
        assertEquals(SigningRefusals.KEY_REVOKED, service.refusal(f.request(SigningUse.OPENDATA_INDEX, index(key))))
        val file = String(Files.readAllBytes(f.dir.resolve("keys/$key.json")))
        assertFalse("ciphertext" in file)
        assertTrue("retired_at" in file)
        // A new key waits for its certificate; the older active certificate sent again changes nothing.
        val next = f.key(service, SigningUse.OPENDATA_INDEX, WorkingKeyStates.AWAITING_CERTIFICATION)
        assertNotEquals(key, next)
        val older = f.certificate(key, listOf(SigningUse.OPENDATA_INDEX), f.now() - 60, f.now() - 60 + 90 * DAY, createdAt = f.now() - 60)
        assertEquals("superseded", service.loadCertificates(listOf(older)).single().status)
        assertEquals(WorkingKeyStates.REVOKED, service.keys().keys.single { it.pubkey == key }.state)
    }

    @Test
    fun anEventOutsideTheCertificateOrBadlyDatedIsRefused() {
        val service = f.open()
        f.certify(service, SigningUse.OPENDATA_INDEX, notBefore = f.now() - 3_600)
        val key = f.currentKey(service, SigningUse.OPENDATA_INDEX)!!
        assertEquals(SigningRefusals.OUTSIDE_CERTIFICATE, service.refusal(f.request(SigningUse.OPENDATA_INDEX, index(key, f.now() - 7_200))))
        assertEquals(SigningRefusals.CREATED_AT_IN_FUTURE, service.refusal(f.request(SigningUse.OPENDATA_INDEX, index(key, f.now() + 600))))
        assertEquals(SigningRefusals.CREATED_AT_TOO_OLD, service.refusal(f.request(SigningUse.OPENDATA_INDEX, index(key, f.now() - 2 * DAY))))
    }

    @Test
    fun aReplayedOrMisdirectedAssertionIsRefused() {
        val service = f.ready()
        val key = f.currentKey(service, SigningUse.EDITORIAL_VOIR)!!
        val event = f.editorial(key)
        val approval = f.alice.approve(SigningUse.EDITORIAL_VOIR, event)
        service.signed(f.request(SigningUse.EDITORIAL_VOIR, event, listOf(approval)))
        assertEquals(SigningRefusals.ALREADY_SIGNED, service.refusal(f.request(SigningUse.EDITORIAL_VOIR, event, listOf(approval))))
        // The approval of one event is worthless for another.
        val other = f.editorial(key, title = "[EXEMPLE] Un autre fait")
        assertEquals(SigningRefusals.ASSERTION_REFUSED, service.refusal(f.request(SigningUse.EDITORIAL_VOIR, other, listOf(approval))))
        // ... and for another use (same team member, an approval made for Comprendre).
        val forComprendre = f.alice.approve(SigningUse.EDITORIAL_COMPRENDRE, other)
        assertEquals(SigningRefusals.ASSERTION_REFUSED, service.refusal(f.request(SigningUse.EDITORIAL_VOIR, other, listOf(forComprendre))))
        // Another origin than the back-office, or no user verification.
        assertEquals(SigningRefusals.ASSERTION_REFUSED, service.refusal(f.request(SigningUse.EDITORIAL_VOIR, other, listOf(f.alice.approve(SigningUse.EDITORIAL_VOIR, other, origin = "https://autre.example")))))
        val careless = TestPasskey(userVerified = false)
        val service2 = SignerFixture().let { g ->
            val s = g.ready()
            s.loadTeamList(g.teamList(listOf(TeamMember("dan", 0, null, setOf(SigningUse.EDITORIAL_VOIR), listOf(careless.credential))), g.now() + 1))
            val k = g.currentKey(s, SigningUse.EDITORIAL_VOIR)!!
            val e = g.editorial(k)
            assertEquals(SigningRefusals.ASSERTION_REFUSED, s.refusal(g.request(SigningUse.EDITORIAL_VOIR, e, listOf(careless.approve("dan", SigningUse.EDITORIAL_VOIR, e)))))
            s
        }
        assertTrue(service2.keys().rootConfigured)
        // After a restart the journal still knows what was signed.
        val reopened = f.open()
        assertEquals(SigningRefusals.ALREADY_SIGNED, reopened.refusal(f.request(SigningUse.EDITORIAL_VOIR, event, listOf(approval))))
    }

    @Test
    fun approvalsMustComeFromListedMembersAllowedForTheUse() {
        val service = f.ready()
        val key = f.currentKey(service, SigningUse.EDITORIAL_VOIR)!!
        val event = f.editorial(key)
        assertEquals(SigningRefusals.APPROVALS_MISSING, service.refusal(f.request(SigningUse.EDITORIAL_VOIR, event)))
        val stranger = TestPasskey()
        assertEquals(SigningRefusals.UNKNOWN_CREDENTIAL, service.refusal(f.request(SigningUse.EDITORIAL_VOIR, event, listOf(stranger.approve("alice", SigningUse.EDITORIAL_VOIR, event)))))
        assertEquals(SigningRefusals.ACCOUNT_MISMATCH, service.refusal(f.request(SigningUse.EDITORIAL_VOIR, event, listOf(f.alice.passkey.approve("bob", SigningUse.EDITORIAL_VOIR, event)))))
        assertEquals(SigningRefusals.NOT_ALLOWED_FOR_USE, service.refusal(f.request(SigningUse.EDITORIAL_VOIR, event, listOf(f.carole.approve(SigningUse.EDITORIAL_VOIR, event)))))
        // A member listed from a later date may not approve yet; a newer list without a member removes them.
        val later = TestMember("erin", setOf(SigningUse.EDITORIAL_VOIR), validFrom = f.now() + DAY)
        assertEquals("stored", service.loadTeamList(f.teamList(listOf(later.member(), f.bob.member()), f.now() + 1)).status)
        assertEquals(SigningRefusals.NOT_ALLOWED_FOR_USE, service.refusal(f.request(SigningUse.EDITORIAL_VOIR, event, listOf(later.approve(SigningUse.EDITORIAL_VOIR, event)))))
        assertEquals(SigningRefusals.UNKNOWN_CREDENTIAL, service.refusal(f.request(SigningUse.EDITORIAL_VOIR, event, listOf(f.alice.approve(SigningUse.EDITORIAL_VOIR, event)))))
        assertEquals("superseded", service.loadTeamList(f.teamList(listOf(f.alice.member()), f.now() - 10)).status)
        service.signed(f.request(SigningUse.EDITORIAL_VOIR, event, listOf(f.bob.approve(SigningUse.EDITORIAL_VOIR, event))))
    }

    @Test
    fun withoutTeamListNoHumanUseIsSigned() {
        val service = f.open()
        f.certifyAll(service)
        val key = f.currentKey(service, SigningUse.EDITORIAL_VOIR)!!
        val event = f.editorial(key)
        assertEquals(SigningRefusals.NO_TEAM_LIST, service.refusal(f.request(SigningUse.EDITORIAL_VOIR, event, listOf(f.alice.approve(SigningUse.EDITORIAL_VOIR, event)))))
        val impostor = IdentityKeys.generate()
        val forged = signWith(impostor, org.politisationpopulairefrancaise.core.platform.TeamList.unsigned(impostor.publicKey.hex, listOf(f.alice.member()), f.now()))
        assertEquals("not_a_team_list", service.loadTeamList(forged).status)
    }

    @Test
    fun theDoubleValidationNeedsTwoDistinctAccountsOtherThanTheAuthor() {
        val service = f.open()
        f.certifyAll(service)
        f.loadTeam(service, f.alice.member(withSpare = true), f.bob.member(), f.carole.member())
        val key = f.currentKey(service, SigningUse.EDITORIAL_VOIR)!!
        val event = f.editorial(key, extra = listOf(listOf("a", "30023:${"b".repeat(64)}:personne-exemple", "", "personne")))
        val alice = f.alice.approve(SigningUse.EDITORIAL_VOIR, event)
        val aliceSpare = f.alice.spare.approve("alice", SigningUse.EDITORIAL_VOIR, event)
        val bob = f.bob.approve(SigningUse.EDITORIAL_VOIR, event)
        val dan = DraftAuthor(account = "dan")
        fun req(assertions: List<org.politisationpopulairefrancaise.core.platform.ApprovalAssertion>, author: DraftAuthor?) =
            f.request(SigningUse.EDITORIAL_VOIR, event, assertions, double = true, author = author)
        assertEquals(SigningRefusals.APPROVALS_MISSING, service.refusal(req(listOf(alice), dan)))
        assertEquals(SigningRefusals.AUTHOR_REQUIRED, service.refusal(req(listOf(alice, bob), null)))
        assertEquals(SigningRefusals.AUTHOR_REQUIRED, service.refusal(req(listOf(alice, bob), DraftAuthor())))
        assertEquals(SigningRefusals.SAME_ACCOUNT_TWICE, service.refusal(req(listOf(alice, aliceSpare), dan)))
        assertEquals(SigningRefusals.SAME_ACCOUNT_TWICE, service.refusal(req(listOf(alice, alice), dan)))
        assertEquals(SigningRefusals.AUTHOR_CANNOT_APPROVE, service.refusal(req(listOf(alice, bob), DraftAuthor(account = "alice"))))
        assertEquals(SigningRefusals.AUTHOR_CANNOT_APPROVE, service.refusal(req(listOf(alice, bob), DraftAuthor(pubkey = f.bob.nostr.publicKey.hex))))
        val signed = service.signed(req(listOf(alice, bob), dan))
        assertTrue(signed.verify())
        val line = service.journal(0, 1_000).last()
        assertEquals(JournalActions.SIGNED, line.action)
        assertEquals(listOf("alice", "bob"), line.approvers)
    }

    @Test
    fun automaticUsesAcceptOnlyTheirOwnFormWithoutApproval() {
        val service = f.ready()
        val indexKey = f.currentKey(service, SigningUse.OPENDATA_INDEX)!!
        assertEquals(SigningRefusals.KIND_NOT_ALLOWED, service.refusal(f.request(SigningUse.OPENDATA_INDEX, f.editorial(indexKey))))
        assertEquals(SigningRefusals.BAD_TAG, service.refusal(f.request(SigningUse.OPENDATA_INDEX, UnsignedEvent(indexKey, f.now(), EventKinds.APP_DATA, listOf(listOf("d", "ppf:mailbox")), "{}"))))
        assertEquals(SigningRefusals.BAD_CONTENT, service.refusal(f.request(SigningUse.OPENDATA_INDEX, UnsignedEvent(indexKey, f.now(), EventKinds.APP_DATA, listOf(listOf("d", "ppf:parlement-index")), "[]"))))
        assertEquals(SigningRefusals.UNEXPECTED_APPROVAL, service.refusal(f.request(SigningUse.OPENDATA_INDEX, index(indexKey), listOf(f.alice.approve(SigningUse.OPENDATA_INDEX, index(indexKey))))))
        service.signed(f.request(SigningUse.OPENDATA_INDEX, index(indexKey)))

        val stamp = f.currentKey(service, SigningUse.ARCHIVES_TIMESTAMPS)!!
        val proof = java.util.Base64.getEncoder().encodeToString(ByteArray(40) { it.toByte() })
        assertEquals(SigningRefusals.KIND_NOT_ALLOWED, service.refusal(f.request(SigningUse.ARCHIVES_TIMESTAMPS, UnsignedEvent(stamp, f.now(), EventKinds.TEXT_NOTE, emptyList(), "x"))))
        assertEquals(SigningRefusals.TAG_NOT_ALLOWED, service.refusal(f.request(SigningUse.ARCHIVES_TIMESTAMPS, UnsignedEvent(stamp, f.now(), EventKinds.OPENTIMESTAMPS, listOf(listOf("e", "c".repeat(64)), listOf("p", "c".repeat(64))), proof))))
        assertEquals(SigningRefusals.BAD_CONTENT, service.refusal(f.request(SigningUse.ARCHIVES_TIMESTAMPS, UnsignedEvent(stamp, f.now(), EventKinds.OPENTIMESTAMPS, listOf(listOf("e", "c".repeat(64))), "pas du base64"))))
        service.signed(f.request(SigningUse.ARCHIVES_TIMESTAMPS, UnsignedEvent(stamp, f.now(), EventKinds.OPENTIMESTAMPS, listOf(listOf("e", "c".repeat(64)), listOf("k", "30023")), proof)))

        val urn = f.currentKey(service, SigningUse.DECIDER_URN_CLOSURE)!!
        val closing = listOf(listOf("d", "ppf:urne-cloture:12"), listOf("ppf-urne", "12"), listOf("ppf-closing-sha256", "d".repeat(64)))
        assertEquals(SigningRefusals.BAD_TAG, service.refusal(f.request(SigningUse.DECIDER_URN_CLOSURE, UnsignedEvent(urn, f.now(), EventKinds.APP_DATA, closing.dropLast(1) + listOf(listOf("ppf-closing-sha256", "court")), "{}"))))
        assertEquals(SigningRefusals.BAD_TAG, service.refusal(f.request(SigningUse.DECIDER_URN_CLOSURE, UnsignedEvent(urn, f.now(), EventKinds.APP_DATA, listOf(listOf("d", "ppf:urne-cloture:12"), listOf("ppf-urne", "13"), closing[2]), "{}"))))
        service.signed(f.request(SigningUse.DECIDER_URN_CLOSURE, UnsignedEvent(urn, f.now(), EventKinds.APP_DATA, closing, """{"results":{}}""")))
        // A key of an automatic use never signs for another automatic use.
        assertEquals(SigningRefusals.WRONG_KEY, service.refusal(f.request(SigningUse.DECIDER_URN_CLOSURE, UnsignedEvent(indexKey, f.now() + 1, EventKinds.APP_DATA, closing, "{}"))))
    }

    @Test
    fun humanUsesCheckTheFrameOfTheirContents() {
        val service = f.ready()
        val voir = f.currentKey(service, SigningUse.EDITORIAL_VOIR)!!
        val noTitle = UnsignedEvent(voir, f.now(), EventKinds.LONG_FORM_ARTICLE, listOf(listOf("d", "x"), listOf("ppf-origin", "human")), "")
        assertEquals(SigningRefusals.MISSING_TAG, service.refusal(f.request(SigningUse.EDITORIAL_VOIR, noTitle, listOf(f.alice.approve(SigningUse.EDITORIAL_VOIR, noTitle)))))
        val organisation = f.editorial(voir, extra = emptyList()).let { UnsignedEvent(it.pubkey, it.createdAt, it.kind, it.tags.filter { t -> t[0] != "ppf-type" } + listOf(listOf("ppf-type", "organisation")), it.content) }
        assertEquals(SigningRefusals.BAD_TAG, service.refusal(f.request(SigningUse.EDITORIAL_VOIR, organisation, listOf(f.alice.approve(SigningUse.EDITORIAL_VOIR, organisation)))))
        val agir = f.currentKey(service, SigningUse.AGIR_ORGANISATIONS)!!
        val sheet = UnsignedEvent(
            agir, f.now(), EventKinds.LONG_FORM_ARTICLE,
            listOf(listOf("d", "collectif-exemple"), listOf("title", "[EXEMPLE] Collectif"), listOf("ppf-type", "organisation"), listOf("p", "e".repeat(64))),
            "[EXEMPLE]",
        )
        service.signed(f.request(SigningUse.AGIR_ORGANISATIONS, sheet, listOf(f.carole.approve(SigningUse.AGIR_ORGANISATIONS, sheet))))
    }

    @Test
    fun theHourlyCeilingHolds() {
        val service = f.ready(f.config(maxPerHour = mapOf(SigningUse.OPENDATA_INDEX to 1)))
        val key = f.currentKey(service, SigningUse.OPENDATA_INDEX)!!
        service.signed(f.request(SigningUse.OPENDATA_INDEX, index(key)))
        assertEquals(SigningRefusals.RATE_LIMITED, service.refusal(f.request(SigningUse.OPENDATA_INDEX, index(key, f.now() + 1))))
        f.clock.advance(3_601)
        service.signed(f.request(SigningUse.OPENDATA_INDEX, index(key, f.now())))
    }

    @Test
    fun aDisabledUseSignsNothingAndCreatesNoKey() {
        val service = f.open(f.config(uses = setOf(SigningUse.OPENDATA_INDEX)))
        assertEquals(listOf(SigningUse.OPENDATA_INDEX.id), service.keys().keys.map { it.use })
        assertEquals(SigningRefusals.USE_DISABLED, service.refusal(f.request(SigningUse.EDITORIAL_VOIR, f.editorial(IdentityKeys.generate().publicKey.hex))))
        assertEquals(SigningRefusals.UNKNOWN_USE, service.refusal(f.request(SigningUse.EDITORIAL_VOIR, f.editorial(IdentityKeys.generate().publicKey.hex)).copy(use = "annonces")))
    }

    @Test
    fun theNextKeyIsCreatedFifteenDaysBeforeTheEndAndTakesOverAtItsStart() {
        val service = f.open(f.config(uses = setOf(SigningUse.OPENDATA_INDEX)))
        val first = f.certify(service, SigningUse.OPENDATA_INDEX, notAfter = f.now() + 90 * DAY)
        val start = f.now()
        f.clock.advance(74 * DAY)
        service.maintain()
        assertEquals(1, service.keys().keys.size)
        f.clock.advance(DAY + 1)
        service.maintain()
        val next = f.key(service, SigningUse.OPENDATA_INDEX, WorkingKeyStates.AWAITING_CERTIFICATION)
        assertNotEquals(first, next)
        service.maintain()
        assertEquals(2, service.keys().keys.size, "only one key waits at a time")
        service.loadCertificates(listOf(f.certificate(next, listOf(SigningUse.OPENDATA_INDEX), start + 80 * DAY, start + 170 * DAY)))
        assertEquals(WorkingKeyStates.SCHEDULED, service.keys().keys.single { it.pubkey == next }.state)
        assertEquals(first, f.currentKey(service, SigningUse.OPENDATA_INDEX))
        f.clock.now = java.time.Instant.ofEpochSecond(start + 80 * DAY)
        assertEquals(next, f.currentKey(service, SigningUse.OPENDATA_INDEX))
        f.clock.now = java.time.Instant.ofEpochSecond(start + 90 * DAY)
        service.maintain()
        assertEquals(WorkingKeyStates.EXPIRED, service.keys().keys.single { it.pubkey == first }.state)
        assertFalse("ciphertext" in String(Files.readAllBytes(f.dir.resolve("keys/$first.json"))))
        service.signed(f.request(SigningUse.OPENDATA_INDEX, index(next)))
    }

    @Test
    fun keysStayEncryptedAtRestAndOnlyTheRightKeyOpensThem() {
        val service = f.open()
        val keys = service.keys().keys.map { it.pubkey }
        val files = Files.list(f.dir.resolve("keys")).use { it.toList() }
        assertEquals(keys.size, files.size)
        for (file in files) {
            val text = String(Files.readAllBytes(file))
            assertTrue("\"ciphertext\"" in text)
            assertFalse("nsec" in text)
        }
        assertEquals(keys.sorted(), f.open().keys().keys.map { it.pubkey }.sorted())
        val wrong = ByteArray(32) { 7 }
        assertFailsWith<SignerStartupException> { f.open(f.config(kek = wrong)) }
        // A key file moved under the name of another key does not open either.
        val (a, b) = files.take(2)
        Files.copy(a, b, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        assertFailsWith<SignerStartupException> { f.open() }
    }

    @Test
    fun theJournalIsChainedAndNeverHoldsContents() {
        val service = f.ready()
        val key = f.currentKey(service, SigningUse.EDITORIAL_VOIR)!!
        val event = f.editorial(key)
        service.signed(f.request(SigningUse.EDITORIAL_VOIR, event, listOf(f.alice.approve(SigningUse.EDITORIAL_VOIR, event))))
        service.refusal(f.request(SigningUse.EDITORIAL_VOIR, event, listOf(f.alice.approve(SigningUse.EDITORIAL_VOIR, event))))
        val lines = service.journal(0, 1_000)
        var previous = SignerJournal.GENESIS
        lines.forEachIndexed { index, line ->
            assertEquals(index + 1L, line.seq)
            assertEquals(previous, line.previousHash)
            assertEquals(SignerJournal.hashOf(line), line.hash)
            previous = line.hash
        }
        assertEquals(service.journalHead().hash, previous)
        val signedLine = lines.single { it.action == JournalActions.SIGNED && it.use == SigningUse.EDITORIAL_VOIR.id }
        assertEquals(event.idHex(), signedLine.event)
        assertEquals(listOf("alice"), signedLine.approvers)
        assertEquals(SigningRefusals.ALREADY_SIGNED, lines.last().code)
        val text = String(Files.readAllBytes(f.dir.resolve("journal.jsonl")))
        assertFalse("Texte confidentiel" in text)
        assertFalse("Un fait sourcé" in text)
        assertTrue(service.journal(lines.size - 1L, 10).size == 1)
        // A line changed by hand stops the service at its next start.
        val file = f.dir.resolve("journal.jsonl")
        Files.write(file, text.replace("\"code\":\"already_signed\"", "\"code\":\"rate_limited\"").toByteArray())
        val error = assertFailsWith<SignerStartupException> { f.open() }
        assertTrue("breaks the chain" in error.message!!)
        // A line removed, too.
        Files.write(file, text.lines().filterIndexed { i, _ -> i != 2 }.joinToString("\n").toByteArray())
        assertFailsWith<SignerStartupException> { f.open() }
    }

    @Test
    fun aFloodOfRefusalsIsCountedRatherThanWrittenLineByLine() {
        val service = f.ready()
        val key = f.currentKey(service, SigningUse.OPENDATA_INDEX)!!
        val before = service.journalHead().seq
        repeat(SignerService.MAX_REFUSALS_PER_HOUR + 5) {
            service.refusal(f.request(SigningUse.OPENDATA_INDEX, UnsignedEvent(key, f.now(), EventKinds.TEXT_NOTE, emptyList(), "")))
        }
        assertTrue(service.journalHead().seq - before <= SignerService.MAX_REFUSALS_PER_HOUR)
        f.clock.advance(3_601)
        service.maintain()
        val last = service.journal(service.journalHead().seq - 1, 1).single()
        assertEquals(JournalActions.REFUSALS_DROPPED, last.action)
        assertNull(last.event)
        assertTrue((last.code?.toInt() ?: 0) >= 5)
    }

    private fun index(pubkey: String, createdAt: Long = f.now()): UnsignedEvent =
        UnsignedEvent(pubkey, createdAt, EventKinds.APP_DATA, listOf(listOf("d", "ppf:parlement-index")), """{"example":true,"packages":[]}""")
}
