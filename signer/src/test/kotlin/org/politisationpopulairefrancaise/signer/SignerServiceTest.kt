package org.politisationpopulairefrancaise.signer

import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.platform.DraftAuthor
import org.politisationpopulairefrancaise.core.platform.KeyStatus
import org.politisationpopulairefrancaise.core.platform.PlatformTrust
import org.politisationpopulairefrancaise.core.platform.SignRequest
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

    private fun SignerService.refusal(request: SignRequest): String? =
        (sign(request) as? SignOutcome.Refused)?.refusal?.code

    private fun SignerService.signed(request: SignRequest) =
        assertIs<SignOutcome.Signed>(sign(request), "expected a signature").event

    private fun SignerService.draftRefusal(use: SigningUse, event: UnsignedEvent, double: Boolean = false, author: DraftAuthor? = f.defaultAuthor(use)): String? =
        (submitDraft(f.draft(use, event, double, author)) as? DraftOutcome.Refused)?.refusal?.code

    @Test
    fun anOrdinaryPublicationIsApprovedOnTheServicePageThenSigned() {
        val service = f.ready()
        val key = f.currentKey(service, SigningUse.EDITORIAL_VOIR)!!
        val event = f.editorial(key)
        val submitted = f.submit(service, SigningUse.EDITORIAL_VOIR, event)
        assertEquals(event.idHex(), submitted.id)
        assertEquals("$TEST_ORIGIN/approbation/editorial.voir/${event.idHex()}", submitted.approvalUrl)
        assertTrue(submitted.created)
        // Without the approval, nothing is signed.
        assertEquals(SigningRefusals.APPROVALS_MISSING, service.refusal(f.request(SigningUse.EDITORIAL_VOIR, event)))
        val recorded = f.approve(service, f.alice, SigningUse.EDITORIAL_VOIR, event)
        assertEquals(1, recorded.approvals)
        assertTrue(recorded.ready)
        val signed = service.signed(f.request(SigningUse.EDITORIAL_VOIR, event))
        assertEquals(event.idHex(), signed.id)
        assertTrue(signed.verify())
        val trust = PlatformTrust.of(f.rootHex, service.certificates())
        assertTrue(trust.isPlatformContent(signed, SigningUse.EDITORIAL_VOIR))
        assertFalse(trust.isPlatformContent(signed, SigningUse.EDITORIAL_COMPRENDRE))
        // D-142: the author of an ordinary publication never approves it, even alone: another member reads it.
        val own = f.editorial(key, title = "[EXEMPLE] Autre")
        f.submit(service, SigningUse.EDITORIAL_VOIR, own, author = DraftAuthor(account = "alice"))
        assertEquals(SigningRefusals.AUTHOR_CANNOT_APPROVE, f.approvalRefusal(service, SigningUse.EDITORIAL_VOIR, own, f.alice.approve(SigningUse.EDITORIAL_VOIR, own)))
        assertEquals(SigningRefusals.APPROVALS_MISSING, service.refusal(f.request(SigningUse.EDITORIAL_VOIR, own)))
        f.approve(service, f.bob, SigningUse.EDITORIAL_VOIR, own)
        service.signed(f.request(SigningUse.EDITORIAL_VOIR, own))
        assertEquals(DraftState.Signed, service.draftState(SigningUse.EDITORIAL_VOIR.id, own.idHex()))
    }

    @Test
    fun aSignatureRequestThatStillCarriesAnApprovalIsRefused() {
        val service = f.ready()
        val event = f.editorial(f.currentKey(service, SigningUse.EDITORIAL_VOIR)!!)
        f.submit(service, SigningUse.EDITORIAL_VOIR, event)
        f.approve(service, f.alice, SigningUse.EDITORIAL_VOIR, event)
        val refused = assertIs<SignOutcome.Refused>(service.sign(f.request(SigningUse.EDITORIAL_VOIR, event), carriesApproval = true))
        assertEquals(SigningRefusals.UNEXPECTED_APPROVAL, refused.refusal.code)
        // Without a draft a human use is never signed, even when the server says it was approved.
        val other = f.editorial(f.currentKey(service, SigningUse.EDITORIAL_VOIR)!!, title = "[EXEMPLE] Jamais soumis")
        assertEquals(SigningRefusals.UNKNOWN_DRAFT, service.refusal(f.request(SigningUse.EDITORIAL_VOIR, other)))
    }

    @Test
    fun anAssertionMadeOnTheBackOfficeOrForAnotherEventIsRefused() {
        val service = f.ready()
        val key = f.currentKey(service, SigningUse.EDITORIAL_VOIR)!!
        val event = f.editorial(key)
        val other = f.editorial(key, title = "[EXEMPLE] Un autre fait")
        f.submit(service, SigningUse.EDITORIAL_VOIR, event)
        f.submit(service, SigningUse.EDITORIAL_VOIR, other)
        // Made on the back-office origin (same passkey, same relying party): refused, whatever the challenge.
        assertEquals(SigningRefusals.ASSERTION_REFUSED, f.approvalRefusal(service, SigningUse.EDITORIAL_VOIR, event, f.alice.approve(SigningUse.EDITORIAL_VOIR, event, origin = TEST_BACKOFFICE)))
        assertEquals(SigningRefusals.ASSERTION_REFUSED, f.approvalRefusal(service, SigningUse.EDITORIAL_VOIR, event, f.alice.approve(SigningUse.EDITORIAL_VOIR, event, origin = "https://autre.example")))
        // In a frame of another site.
        val framed = f.alice.passkey.assert(org.politisationpopulairefrancaise.core.platform.ApprovalChallenge.of(SigningUse.EDITORIAL_VOIR, event.idHex()), crossOrigin = true)
        assertEquals(SigningRefusals.ASSERTION_REFUSED, f.approvalRefusal(service, SigningUse.EDITORIAL_VOIR, event, framed))
        // The approval of one event is worthless for another, and for another use.
        assertEquals(SigningRefusals.ASSERTION_REFUSED, f.approvalRefusal(service, SigningUse.EDITORIAL_VOIR, other, f.alice.approve(SigningUse.EDITORIAL_VOIR, event)))
        assertEquals(SigningRefusals.ASSERTION_REFUSED, f.approvalRefusal(service, SigningUse.EDITORIAL_VOIR, other, f.alice.approve(SigningUse.EDITORIAL_COMPRENDRE, other)))
        assertEquals(SigningRefusals.APPROVALS_MISSING, service.refusal(f.request(SigningUse.EDITORIAL_VOIR, other)))
        // Without user verification.
        val careless = TestPasskey(userVerified = false)
        val g = SignerFixture()
        val s = g.ready()
        s.loadTeamList(g.teamList(listOf(TeamMember("dan", 0, null, setOf(SigningUse.EDITORIAL_VOIR), listOf(careless.credential))), g.now() + 1))
        val e = g.editorial(g.currentKey(s, SigningUse.EDITORIAL_VOIR)!!)
        g.submit(s, SigningUse.EDITORIAL_VOIR, e)
        assertEquals(SigningRefusals.ASSERTION_REFUSED, g.approvalRefusal(s, SigningUse.EDITORIAL_VOIR, e, careless.approve(SigningUse.EDITORIAL_VOIR, e)))
        // Once signed, the event is never signed again, also after a restart.
        f.approve(service, f.alice, SigningUse.EDITORIAL_VOIR, event)
        service.signed(f.request(SigningUse.EDITORIAL_VOIR, event))
        assertEquals(SigningRefusals.ALREADY_SIGNED, service.refusal(f.request(SigningUse.EDITORIAL_VOIR, event)))
        assertEquals(SigningRefusals.ALREADY_SIGNED, f.approvalRefusal(service, SigningUse.EDITORIAL_VOIR, event, f.bob.approve(SigningUse.EDITORIAL_VOIR, event)))
        val reopened = f.open()
        assertEquals(SigningRefusals.ALREADY_SIGNED, reopened.refusal(f.request(SigningUse.EDITORIAL_VOIR, event)))
        assertEquals(SigningRefusals.ALREADY_SIGNED, reopened.draftRefusal(SigningUse.EDITORIAL_VOIR, event))
    }

    @Test
    fun approvalsMustComeFromListedMembersAllowedForTheUse() {
        val service = f.ready()
        val key = f.currentKey(service, SigningUse.EDITORIAL_VOIR)!!
        val event = f.editorial(key)
        f.submit(service, SigningUse.EDITORIAL_VOIR, event)
        val stranger = TestPasskey()
        assertEquals(SigningRefusals.UNKNOWN_CREDENTIAL, f.approvalRefusal(service, SigningUse.EDITORIAL_VOIR, event, stranger.approve(SigningUse.EDITORIAL_VOIR, event)))
        assertEquals(SigningRefusals.NOT_ALLOWED_FOR_USE, f.approvalRefusal(service, SigningUse.EDITORIAL_VOIR, event, f.carole.approve(SigningUse.EDITORIAL_VOIR, event)))
        // A credential id written in another form than canonical base64url is unknown.
        val padded = f.alice.approve(SigningUse.EDITORIAL_VOIR, event).let { it.copy(credentialId = it.credentialId + "=") }
        assertEquals(SigningRefusals.UNKNOWN_CREDENTIAL, f.approvalRefusal(service, SigningUse.EDITORIAL_VOIR, event, padded))
        // A member listed from a later date may not approve yet.
        val later = TestMember("erin", setOf(SigningUse.EDITORIAL_VOIR), validFrom = f.now() + DAY)
        assertEquals("stored", service.loadTeamList(f.teamList(listOf(later.member(), f.alice.member(), f.bob.member()), f.now() + 1)).status)
        assertEquals(SigningRefusals.NOT_ALLOWED_FOR_USE, f.approvalRefusal(service, SigningUse.EDITORIAL_VOIR, event, later.approve(SigningUse.EDITORIAL_VOIR, event)))
        // An approval recorded, then its member left out of a newer list: it no longer counts.
        f.approve(service, f.alice, SigningUse.EDITORIAL_VOIR, event)
        f.clock.advance(10)
        assertEquals("stored", service.loadTeamList(f.teamList(listOf(f.bob.member()), f.now())).status)
        assertEquals(SigningRefusals.APPROVALS_MISSING, service.refusal(f.request(SigningUse.EDITORIAL_VOIR, event)))
        assertEquals(SigningRefusals.UNKNOWN_CREDENTIAL, f.approvalRefusal(service, SigningUse.EDITORIAL_VOIR, event, f.alice.approve(SigningUse.EDITORIAL_VOIR, event)))
        assertEquals("superseded", service.loadTeamList(f.teamList(listOf(f.alice.member()), f.now() - 100)).status)
        f.approve(service, f.bob, SigningUse.EDITORIAL_VOIR, event)
        service.signed(f.request(SigningUse.EDITORIAL_VOIR, event))
    }

    @Test
    fun withoutAUsableTeamListNoHumanUseIsApproved() {
        val service = f.open()
        f.certifyAll(service)
        val key = f.currentKey(service, SigningUse.EDITORIAL_VOIR)!!
        val event = f.editorial(key)
        assertEquals(SigningRefusals.NO_TEAM_LIST, service.draftRefusal(SigningUse.EDITORIAL_VOIR, event))
        val impostor = IdentityKeys.generate()
        val forged = signWith(impostor, org.politisationpopulairefrancaise.core.platform.TeamList.unsigned(impostor.publicKey.hex, listOf(f.alice.member()), f.now(), f.now() + DAY))
        assertEquals("not_a_team_list", service.loadTeamList(forged).status)
        // A list valid one day: usable, then expired for drafts, approvals and signatures alike.
        assertEquals("stored", service.loadTeamList(f.teamList(listOf(f.alice.member(), f.bob.member()), notAfter = f.now() + DAY)).status)
        assertEquals(f.now() + DAY, service.keys().teamListNotAfter)
        f.submit(service, SigningUse.EDITORIAL_VOIR, event)
        f.approve(service, f.alice, SigningUse.EDITORIAL_VOIR, event)
        val second = f.editorial(key, title = "[EXEMPLE] Second")
        f.submit(service, SigningUse.EDITORIAL_VOIR, second)
        f.clock.advance(DAY)
        assertEquals(SigningRefusals.TEAM_LIST_EXPIRED, service.refusal(f.request(SigningUse.EDITORIAL_VOIR, event)))
        assertEquals(SigningRefusals.TEAM_LIST_EXPIRED, f.approvalRefusal(service, SigningUse.EDITORIAL_VOIR, second, f.bob.approve(SigningUse.EDITORIAL_VOIR, second)))
        assertEquals(SigningRefusals.TEAM_LIST_EXPIRED, service.draftRefusal(SigningUse.EDITORIAL_VOIR, f.editorial(key, f.now(), "[EXEMPLE] Trois")))
        // A newer list makes them usable again; the approval recorded before still counts.
        assertEquals("stored", service.loadTeamList(f.teamList(listOf(f.alice.member(), f.bob.member()))).status)
        service.signed(f.request(SigningUse.EDITORIAL_VOIR, event))
    }

    @Test
    fun theDoubleValidationNeedsTwoDistinctAccountsOtherThanTheAuthor() {
        val service = f.open()
        f.certifyAll(service)
        f.loadTeam(service, f.alice.member(withSpare = true), f.bob.member(), f.carole.member())
        val key = f.currentKey(service, SigningUse.EDITORIAL_VOIR)!!
        val event = f.editorial(key, extra = listOf(listOf("a", "30023:${"b".repeat(64)}:personne-exemple", "", "personne")))
        val use = SigningUse.EDITORIAL_VOIR
        assertEquals(SigningRefusals.AUTHOR_REQUIRED, service.draftRefusal(use, event, double = true, author = null))
        assertEquals(SigningRefusals.AUTHOR_REQUIRED, service.draftRefusal(use, event, double = true, author = DraftAuthor("", "")))
        assertEquals(SigningRefusals.MALFORMED_REQUEST, service.draftRefusal(use, event, double = true, author = DraftAuthor(pubkey = "pas une clé")))
        f.submit(service, use, event, double = true, author = DraftAuthor(account = "dan"))
        // The rule is fixed by the first submission: the same event with another rule is refused (and a single
        // approval for a content that names a person, before anything else).
        assertEquals(SigningRefusals.DOUBLE_VALIDATION_REQUIRED, service.draftRefusal(use, event))
        assertEquals(SigningRefusals.DRAFT_CONFLICT, service.draftRefusal(use, event, double = true, author = DraftAuthor(account = "erin")))
        assertFalse(f.submit(service, use, event, double = true, author = DraftAuthor(account = "dan")).created)
        val first = f.approve(service, f.alice, use, event)
        assertEquals(1, first.approvals)
        assertEquals(2, first.required)
        assertEquals(SigningRefusals.APPROVALS_MISSING, service.refusal(f.request(use, event)))
        // The same account twice, with another of its passkeys: refused.
        assertEquals(SigningRefusals.ALREADY_APPROVED, f.approvalRefusal(service, use, event, f.alice.spare.approve(use, event)))
        assertEquals(SigningRefusals.ALREADY_APPROVED, f.approvalRefusal(service, use, event, f.alice.approve(use, event)))
        f.approve(service, f.bob, use, event)
        val signed = service.signed(f.request(use, event))
        assertTrue(signed.verify())
        val line = service.journal(0, 1_000).last()
        assertEquals(JournalActions.SIGNED, line.action)
        assertEquals(listOf("alice", "bob"), line.approvers)
        assertEquals(true, line.doubleValidation)
        assertEquals("dan", line.authorAccount)
        // The author, named by account or by Nostr key, never approves a double validation.
        val second = f.editorial(key, title = "[EXEMPLE] Deuxième", extra = listOf(listOf("a", "30023:${"b".repeat(64)}:personne-exemple")))
        f.submit(service, use, second, double = true, author = DraftAuthor(pubkey = f.bob.nostr.publicKey.hex))
        assertEquals(SigningRefusals.AUTHOR_CANNOT_APPROVE, f.approvalRefusal(service, use, second, f.bob.approve(use, second)))
        // An ordinary content may ask for more than its rule; it is then fixed like any other.
        val third = f.editorial(key, title = "[EXEMPLE] Troisième")
        f.submit(service, use, third, double = true, author = DraftAuthor(account = "alice"))
        assertEquals(SigningRefusals.DRAFT_CONFLICT, service.draftRefusal(use, third, author = DraftAuthor(account = "alice")))
        assertEquals(SigningRefusals.AUTHOR_CANNOT_APPROVE, f.approvalRefusal(service, use, third, f.alice.approve(use, third)))
        // The options offer only the passkeys of members who may still approve.
        val options = assertIs<OptionsOutcome.Options>(service.approvalOptions(use.id, third.idHex()))
        assertEquals(listOf(f.bob.passkey.credential.id), options.credentialIds)
        assertEquals(TEST_RP_ID, options.rpId)
    }

    @Test
    fun theAuthorNeverApprovesTheirOwnDraftEvenInASingleValidation() {
        val service = f.ready()
        val use = SigningUse.EDITORIAL_VOIR
        val key = f.currentKey(service, use)!!
        // An editorial draft names its author, in a single validation too (D-142).
        val anonymous = f.editorial(key, title = "[EXEMPLE] Sans auteur")
        assertEquals(SigningRefusals.AUTHOR_REQUIRED, service.draftRefusal(use, anonymous, author = null))
        assertEquals(SigningRefusals.AUTHOR_REQUIRED, service.draftRefusal(SigningUse.EDITORIAL_COMPRENDRE, f.editorial(f.currentKey(service, SigningUse.EDITORIAL_COMPRENDRE)!!), author = null))
        assertEquals(SigningRefusals.AUTHOR_REQUIRED, service.draftRefusal(use, anonymous, author = DraftAuthor("", "")))
        assertEquals(JournalActions.DRAFT_REFUSED, service.journal(0, 10_000).last().action)
        // Named by account: excluded from the options, refused, the others may approve.
        val byAlice = f.editorial(key, title = "[EXEMPLE] Par alice")
        f.submit(service, use, byAlice, author = DraftAuthor(account = "alice"))
        assertEquals(listOf(f.bob.passkey.credential.id), assertIs<OptionsOutcome.Options>(service.approvalOptions(use.id, byAlice.idHex())).credentialIds)
        assertEquals(SigningRefusals.AUTHOR_CANNOT_APPROVE, f.approvalRefusal(service, use, byAlice, f.alice.approve(use, byAlice)))
        assertEquals(SigningRefusals.AUTHOR_CANNOT_APPROVE, service.journal(0, 10_000).last().code)
        assertFalse(assertIs<DraftState.Pending>(service.draftState(use.id, byAlice.idHex())).status.ready)
        f.approve(service, f.bob, use, byAlice)
        service.signed(f.request(use, byAlice))
        // Named by Nostr key: the same.
        val byBob = f.editorial(key, title = "[EXEMPLE] Par bob")
        f.submit(service, use, byBob, author = DraftAuthor(pubkey = f.bob.nostr.publicKey.hex))
        assertEquals(SigningRefusals.AUTHOR_CANNOT_APPROVE, f.approvalRefusal(service, use, byBob, f.bob.approve(use, byBob)))
        // The only member allowed is the author: nobody may approve.
        val lonely = SignerFixture()
        val alone = lonely.open()
        lonely.certifyAll(alone)
        lonely.loadTeam(alone, lonely.alice.member())
        val own = lonely.editorial(lonely.currentKey(alone, use)!!)
        lonely.submit(alone, use, own, author = DraftAuthor(account = "alice"))
        assertEquals(SigningRefusals.NOT_ALLOWED_FOR_USE, (alone.approvalOptions(use.id, own.idHex()) as OptionsOutcome.Refused).refusal.code)
        // The other human uses may still go without author in a single validation: the deciding member approves.
        val sheet = UnsignedEvent(
            f.currentKey(service, SigningUse.AGIR_ORGANISATIONS)!!, f.now(), EventKinds.LONG_FORM_ARTICLE,
            listOf(listOf("d", "organisation-sans-auteur"), listOf("title", "[EXEMPLE] Organisation"), listOf("ppf-type", "organisation")), "[EXEMPLE]",
        )
        f.submit(service, SigningUse.AGIR_ORGANISATIONS, sheet, author = null)
        f.approve(service, f.carole, SigningUse.AGIR_ORGANISATIONS, sheet)
        service.signed(f.request(SigningUse.AGIR_ORGANISATIONS, sheet))
    }

    @Test
    fun anApprovalOfTheAuthorRecordedBeforeIsNeverCountedAtSignature() {
        val service = f.ready()
        val use = SigningUse.EDITORIAL_VOIR
        val key = f.currentKey(service, use)!!
        val event = f.editorial(key, title = "[EXEMPLE] Approuvé par son auteur")
        // A journal written by an older version: the author recorded as the approval of a single validation.
        val journal = SignerJournal.open(f.dir, f.now())
        val fields = JournalFields(use = use.id, event = event.idHex(), eventCreatedAt = event.createdAt, key = key, doubleValidation = false, authorAccount = "alice")
        journal.append(f.now(), JournalActions.DRAFT_SUBMITTED, fields)
        journal.append(f.now(), JournalActions.APPROVAL_RECORDED, fields.copy(approvers = listOf("alice"), credential = f.alice.passkey.credential.id))
        journal.close()
        val reopened = f.open()
        f.submit(reopened, use, event, author = DraftAuthor(account = "alice"))
        val state = assertIs<DraftState.Pending>(reopened.draftState(use.id, event.idHex())).status
        assertTrue(state.approvals.isEmpty())
        assertFalse(state.ready)
        assertEquals(SigningRefusals.APPROVALS_MISSING, reopened.refusal(f.request(use, event)))
        f.approve(reopened, f.bob, use, event)
        reopened.signed(f.request(use, event))
        assertEquals(listOf("bob"), reopened.journal(0, 10_000).last().approvers)
    }

    @Test
    fun theServiceItselfRequiresTheDoubleValidationOfTheContentsOfM17() {
        val service = f.ready()
        val use = SigningUse.EDITORIAL_VOIR
        val key = f.currentKey(service, use)!!
        val person = "30023:${"b".repeat(64)}:personne-exemple"
        val implicating = listOf(
            f.editorial(key, title = "[EXEMPLE] Une personne", type = "personne"),
            f.editorial(key, title = "[EXEMPLE] Un fait", type = "fait"),
            f.editorial(key, title = "[EXEMPLE] Un type inconnu", type = "inconnu"),
            f.editorial(key, title = "[EXEMPLE] Un responsable", extra = listOf(listOf("a", person, "", "responsable"))),
            f.editorial(key, title = "[EXEMPLE] Une citation", extra = listOf(listOf("ppf-quote", "exemple"))),
            UnsignedEvent(key, f.now(), EventKinds.LONG_FORM_ARTICLE, listOf(listOf("d", "sans-type"), listOf("title", "[EXEMPLE] Sans type"), listOf("ppf-origin", "human")), "[EXEMPLE]"),
        )
        for (event in implicating) {
            // Whatever the queue of the main server says, a single approval is refused, at once and in the journal.
            assertEquals(SigningRefusals.DOUBLE_VALIDATION_REQUIRED, service.draftRefusal(use, event), event.tags.toString())
            val line = service.journal(0, 10_000).last()
            assertEquals(JournalActions.DRAFT_REFUSED, line.action)
            assertEquals(SigningRefusals.DOUBLE_VALIDATION_REQUIRED, line.code)
            assertEquals(false, line.doubleValidation)
            assertEquals(DraftState.Unknown, service.draftState(use.id, event.idHex()))
            // The double validation still needs the author.
            assertEquals(SigningRefusals.AUTHOR_REQUIRED, service.draftRefusal(use, event, double = true, author = null))
        }
        // Asked with its author, it goes through the two approvals.
        val event = implicating.first()
        f.submit(service, use, event, double = true, author = DraftAuthor(account = "dan"))
        assertEquals(2, f.approve(service, f.alice, use, event).required)
        assertEquals(SigningRefusals.APPROVALS_MISSING, service.refusal(f.request(use, event)))
        f.approve(service, f.bob, use, event)
        service.signed(f.request(use, event))
        // An ordinary brick of « Voir » needs one approval; the rule of M17 is not applied to the other uses.
        val ordinary = f.editorial(key, title = "[EXEMPLE] Un indicateur", type = "indicateur", extra = listOf(listOf("a", person, "", "source")))
        f.submit(service, use, ordinary)
        f.approve(service, f.alice, use, ordinary)
        service.signed(f.request(use, ordinary))
        val comprendre = f.editorial(f.currentKey(service, SigningUse.EDITORIAL_COMPRENDRE)!!, type = "fait", extra = listOf(listOf("a", person, "", "personne")))
        f.submit(service, SigningUse.EDITORIAL_COMPRENDRE, comprendre)
        f.approve(service, f.alice, SigningUse.EDITORIAL_COMPRENDRE, comprendre)
        service.signed(f.request(SigningUse.EDITORIAL_COMPRENDRE, comprendre))
    }

    @Test
    fun aDraftRecordedWithASingleApprovalForAContentOfM17IsNeverSigned() {
        val service = f.ready()
        val use = SigningUse.EDITORIAL_VOIR
        val key = f.currentKey(service, use)!!
        val event = f.editorial(key, type = "personne")
        assertEquals(SigningRefusals.DOUBLE_VALIDATION_REQUIRED, service.draftRefusal(use, event))
        // A journal that records this draft with a single approval, and that approval (an older version of the
        // service, or lines written by hand with a valid chain).
        val journal = SignerJournal.open(f.dir, f.now())
        val fields = JournalFields(use = use.id, event = event.idHex(), eventCreatedAt = event.createdAt, key = key, doubleValidation = false)
        journal.append(f.now(), JournalActions.DRAFT_SUBMITTED, fields)
        journal.append(f.now(), JournalActions.APPROVAL_RECORDED, fields.copy(approvers = listOf("alice"), credential = f.alice.passkey.credential.id))
        journal.close()
        val reopened = f.open()
        val state = assertIs<DraftState.Pending>(reopened.draftState(use.id, event.idHex())).status
        assertFalse(state.doubleValidation)
        assertEquals(listOf("alice"), state.approvals.map { it.account })
        // The signature is refused all the same, and submitting the draft again never brings its content back.
        assertEquals(SigningRefusals.DOUBLE_VALIDATION_REQUIRED, reopened.refusal(f.request(use, event)))
        assertEquals(SigningRefusals.DOUBLE_VALIDATION_REQUIRED, reopened.draftRefusal(use, event))
        assertEquals(SigningRefusals.DRAFT_CONFLICT, reopened.draftRefusal(use, event, double = true, author = DraftAuthor(account = "dan")))
        assertFalse(assertIs<DraftState.Pending>(reopened.draftState(use.id, event.idHex())).status.contentKnown)
        assertEquals(SigningRefusals.UNKNOWN_DRAFT, f.approvalRefusal(reopened, use, event, f.bob.approve(use, event)))
    }

    @Test
    fun draftsAndTheirApprovalsSurviveARestartButNotTheirContent() {
        val service = f.ready()
        val key = f.currentKey(service, SigningUse.EDITORIAL_VOIR)!!
        val use = SigningUse.EDITORIAL_VOIR
        val event = f.editorial(key, extra = listOf(listOf("a", "30023:${"b".repeat(64)}:personne-exemple")))
        f.submit(service, use, event, double = true, author = DraftAuthor(account = "dan"))
        f.approve(service, f.alice, use, event)
        val reopened = f.open()
        val state = assertIs<DraftState.Pending>(reopened.draftState(use.id, event.idHex())).status
        assertEquals(listOf("alice"), state.approvals.map { it.account })
        assertFalse(state.contentKnown)
        assertTrue(state.doubleValidation)
        // Nobody approves what the page cannot show.
        assertNull(reopened.approvalView(use.id, event.idHex())?.event)
        assertEquals(SigningRefusals.UNKNOWN_DRAFT, f.approvalRefusal(reopened, use, event, f.bob.approve(use, event)))
        // The rule survives: the server cannot lower it by submitting the draft again.
        assertEquals(SigningRefusals.DRAFT_CONFLICT, reopened.draftRefusal(use, event, author = DraftAuthor(account = "dan")))
        f.submit(reopened, use, event, double = true, author = DraftAuthor(account = "dan"))
        assertTrue(assertIs<DraftState.Pending>(reopened.draftState(use.id, event.idHex())).status.contentKnown)
        f.approve(reopened, f.bob, use, event)
        reopened.signed(f.request(use, event))
    }

    @Test
    fun aDraftWaitsSevenDaysAtMost() {
        val service = f.ready()
        val event = f.editorial(f.currentKey(service, SigningUse.EDITORIAL_VOIR)!!)
        f.submit(service, SigningUse.EDITORIAL_VOIR, event)
        f.approve(service, f.alice, SigningUse.EDITORIAL_VOIR, event)
        f.clock.advance(SignerService.DRAFT_LIFETIME_SECONDS)
        assertEquals(DraftState.Unknown, service.draftState(SigningUse.EDITORIAL_VOIR.id, event.idHex()))
        assertEquals(SigningRefusals.UNKNOWN_DRAFT, service.refusal(f.request(SigningUse.EDITORIAL_VOIR, event)))
        assertEquals(DraftState.Unknown, f.open().draftState(SigningUse.EDITORIAL_VOIR.id, event.idHex()))
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
        // A certificate naming another use than the key's, or one more, is refused.
        assertEquals("wrong_uses", service.loadCertificates(listOf(other.certificate(key, listOf(SigningUse.OPENDATA_INDEX, SigningUse.DECIDER_URN_CLOSURE)))).single().status)
        assertEquals("wrong_uses", service.loadCertificates(listOf(other.certificate(key, listOf(SigningUse.DECIDER_URN_CLOSURE)))).single().status)
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
    fun anOlderRevocationReceivedAfterANewerCertificateStillRevokesAndSurvivesARestart() {
        val service = f.open(f.config(uses = setOf(SigningUse.OPENDATA_INDEX)))
        val key = f.key(service, SigningUse.OPENDATA_INDEX, WorkingKeyStates.AWAITING_CERTIFICATION)
        val revocation = f.certificate(key, listOf(SigningUse.OPENDATA_INDEX), createdAt = f.now() - 10, status = KeyStatus.REVOKED, revokedFrom = f.now() + 3_600)
        val active = f.certificate(key, listOf(SigningUse.OPENDATA_INDEX), createdAt = f.now())
        assertEquals("stored", service.loadCertificates(listOf(active)).single().status)
        service.signed(f.request(SigningUse.OPENDATA_INDEX, index(key)))
        assertEquals("stored", service.loadCertificates(listOf(revocation)).single().status)
        assertEquals(f.now() + 3_600, service.keys().keys.single { it.pubkey == key }.revokedFrom)
        assertTrue(Files.exists(f.dir.resolve("certificates/$key.revocation.json")))
        assertEquals(2, service.certificates().size)
        f.clock.advance(3_600)
        assertEquals(SigningRefusals.KEY_REVOKED, service.refusal(f.request(SigningUse.OPENDATA_INDEX, index(key))))
        val reopened = f.open(f.config(uses = setOf(SigningUse.OPENDATA_INDEX)))
        assertEquals(WorkingKeyStates.REVOKED, reopened.keys().keys.single { it.pubkey == key }.state)
    }

    @Test
    fun theLastSignatureOfEachKeyIsReportedForItsClosingRevocation() {
        val service = f.ready()
        val key = f.currentKey(service, SigningUse.OPENDATA_INDEX)!!
        assertNull(service.keys().keys.single { it.pubkey == key }.lastSignedAt)
        service.signed(f.request(SigningUse.OPENDATA_INDEX, index(key, f.now() + 120)))
        assertEquals(f.now() + 120, service.keys().keys.single { it.pubkey == key }.lastSignedAt)
        f.clock.advance(600)
        service.signed(f.request(SigningUse.OPENDATA_INDEX, index(key, f.now() - 30)))
        assertEquals(f.now(), service.keys().keys.single { it.pubkey == key }.lastSignedAt)
        assertEquals(f.now(), f.open().keys().keys.single { it.pubkey == key }.lastSignedAt)
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
    fun automaticUsesAcceptOnlyTheirOwnFormWithoutDraft() {
        val service = f.ready()
        val indexKey = f.currentKey(service, SigningUse.OPENDATA_INDEX)!!
        assertEquals(SigningRefusals.KIND_NOT_ALLOWED, service.refusal(f.request(SigningUse.OPENDATA_INDEX, f.editorial(indexKey))))
        assertEquals(SigningRefusals.BAD_TAG, service.refusal(f.request(SigningUse.OPENDATA_INDEX, UnsignedEvent(indexKey, f.now(), EventKinds.APP_DATA, listOf(listOf("d", "ppf:mailbox")), "{}"))))
        assertEquals(SigningRefusals.BAD_CONTENT, service.refusal(f.request(SigningUse.OPENDATA_INDEX, UnsignedEvent(indexKey, f.now(), EventKinds.APP_DATA, listOf(listOf("d", "ppf:parlement-index")), "[]"))))
        assertEquals(SigningRefusals.AUTOMATIC_USE, service.draftRefusal(SigningUse.OPENDATA_INDEX, index(indexKey)))
        service.signed(f.request(SigningUse.OPENDATA_INDEX, index(indexKey)))
        val signedLine = service.journal(0, 1_000).last()
        assertEquals(false, signedLine.doubleValidation)
        assertNull(signedLine.authorAccount)

        val urn = f.currentKey(service, SigningUse.DECIDER_URN_CLOSURE)!!
        val closing = listOf(listOf("d", "ppf:urne-cloture:12"), listOf("ppf-urne", "12"), listOf("ppf-closing-sha256", "d".repeat(64)))
        assertEquals(SigningRefusals.BAD_TAG, service.refusal(f.request(SigningUse.DECIDER_URN_CLOSURE, UnsignedEvent(urn, f.now(), EventKinds.APP_DATA, closing.dropLast(1) + listOf(listOf("ppf-closing-sha256", "court")), "{}"))))
        assertEquals(SigningRefusals.BAD_TAG, service.refusal(f.request(SigningUse.DECIDER_URN_CLOSURE, UnsignedEvent(urn, f.now(), EventKinds.APP_DATA, listOf(listOf("d", "ppf:urne-cloture:12"), listOf("ppf-urne", "13"), closing[2]), "{}"))))
        service.signed(f.request(SigningUse.DECIDER_URN_CLOSURE, UnsignedEvent(urn, f.now(), EventKinds.APP_DATA, closing, """{"results":{}}""")))
        // A key of an automatic use never signs for another automatic use.
        assertEquals(SigningRefusals.WRONG_KEY, service.refusal(f.request(SigningUse.DECIDER_URN_CLOSURE, UnsignedEvent(indexKey, f.now() + 1, EventKinds.APP_DATA, closing, "{}"))))
    }

    @Test
    fun aBallotBoxIsClosedOnceForGood() {
        val service = f.ready()
        val urn = f.currentKey(service, SigningUse.DECIDER_URN_CLOSURE)!!
        fun closing(at: Long, results: String) = UnsignedEvent(
            urn, at, EventKinds.APP_DATA,
            listOf(listOf("d", "ppf:urne-cloture:7"), listOf("ppf-urne", "7"), listOf("ppf-closing-sha256", "e".repeat(64))),
            results,
        )
        service.signed(f.request(SigningUse.DECIDER_URN_CLOSURE, closing(f.now(), """{"oui":10}""")))
        val line = service.journal(0, 1_000).last()
        assertEquals("ppf:urne-cloture:7", line.d)
        assertEquals(SigningRefusals.URN_ALREADY_CLOSED, service.refusal(f.request(SigningUse.DECIDER_URN_CLOSURE, closing(f.now() + 1, """{"oui":0}"""))))
        // Long after, when the signed events are no longer remembered, the ballot box stays closed.
        f.clock.advance(10 * DAY)
        val reopened = f.open()
        assertEquals(SigningRefusals.URN_ALREADY_CLOSED, reopened.refusal(f.request(SigningUse.DECIDER_URN_CLOSURE, closing(f.now(), """{"oui":0}"""))))
        // Another ballot box is not concerned.
        val other = UnsignedEvent(urn, f.now(), EventKinds.APP_DATA, listOf(listOf("d", "ppf:urne-cloture:8"), listOf("ppf-urne", "8"), listOf("ppf-closing-sha256", "e".repeat(64))), "{}")
        reopened.signed(f.request(SigningUse.DECIDER_URN_CLOSURE, other))
    }

    @Test
    fun aTimestampProofMustBeAnOpenTimestampsProofOfItsDigest() {
        val service = f.ready()
        val stamp = f.currentKey(service, SigningUse.ARCHIVES_TIMESTAMPS)!!
        val digest = "c".repeat(64)
        fun event(content: String, tags: List<List<String>> = listOf(listOf("e", digest), listOf("k", "30023"))) =
            f.request(SigningUse.ARCHIVES_TIMESTAMPS, UnsignedEvent(stamp, f.now(), EventKinds.OPENTIMESTAMPS, tags, content))
        val b64 = java.util.Base64.getEncoder()
        assertEquals(SigningRefusals.KIND_NOT_ALLOWED, service.refusal(f.request(SigningUse.ARCHIVES_TIMESTAMPS, UnsignedEvent(stamp, f.now(), EventKinds.TEXT_NOTE, emptyList(), "x"))))
        assertEquals(SigningRefusals.TAG_NOT_ALLOWED, service.refusal(event(b64.encodeToString(otsProof(digest)), listOf(listOf("e", digest), listOf("p", digest)))))
        assertEquals(SigningRefusals.BAD_CONTENT, service.refusal(event("pas du base64")))
        // Not an OpenTimestamps file (an RFC 3161 token, DER), another digest, another hash, too large.
        assertEquals(SigningRefusals.BAD_CONTENT, service.refusal(event(b64.encodeToString(byteArrayOf(0x30, 0x82.toByte(), 0x01, 0x00) + ByteArray(80)))))
        assertEquals(SigningRefusals.BAD_CONTENT, service.refusal(event(b64.encodeToString(otsProof("d".repeat(64))))))
        assertEquals(SigningRefusals.BAD_CONTENT, service.refusal(event(b64.encodeToString(otsProof(digest, op = 0x02)))))
        assertEquals(SigningRefusals.BAD_CONTENT, service.refusal(event(b64.encodeToString(otsProof(digest).copyOf(31 + 2 + 32)))))
        assertEquals(SigningRefusals.TOO_LARGE, service.refusal(event(b64.encodeToString(otsProof(digest) + ByteArray(SigningPolicies.MAX_PROOF_BYTES)))))
        service.signed(event(b64.encodeToString(otsProof(digest))))
    }

    @Test
    fun humanUsesCheckTheFrameOfTheirContentsWhenTheDraftIsSubmitted() {
        val service = f.ready()
        val voir = f.currentKey(service, SigningUse.EDITORIAL_VOIR)!!
        val noTitle = UnsignedEvent(voir, f.now(), EventKinds.LONG_FORM_ARTICLE, listOf(listOf("d", "x"), listOf("ppf-origin", "human")), "")
        assertEquals(SigningRefusals.MISSING_TAG, service.draftRefusal(SigningUse.EDITORIAL_VOIR, noTitle))
        val organisation = f.editorial(voir).let { UnsignedEvent(it.pubkey, it.createdAt, it.kind, it.tags.filter { t -> t[0] != "ppf-type" } + listOf(listOf("ppf-type", "organisation")), it.content) }
        assertEquals(SigningRefusals.BAD_TAG, service.draftRefusal(SigningUse.EDITORIAL_VOIR, organisation))
        assertEquals(SigningRefusals.CREATED_AT_TOO_OLD, service.draftRefusal(SigningUse.EDITORIAL_VOIR, f.editorial(voir, f.now() - 9 * DAY)))
        assertEquals(SigningRefusals.WRONG_KEY, service.draftRefusal(SigningUse.EDITORIAL_VOIR, f.editorial(f.currentKey(service, SigningUse.EDITORIAL_COMPRENDRE)!!)))
        val agir = f.currentKey(service, SigningUse.AGIR_ORGANISATIONS)!!
        val sheet = UnsignedEvent(
            agir, f.now(), EventKinds.LONG_FORM_ARTICLE,
            listOf(listOf("d", "collectif-exemple"), listOf("title", "[EXEMPLE] Collectif"), listOf("ppf-type", "organisation"), listOf("p", "e".repeat(64))),
            "[EXEMPLE]",
        )
        f.submit(service, SigningUse.AGIR_ORGANISATIONS, sheet)
        f.approve(service, f.carole, SigningUse.AGIR_ORGANISATIONS, sheet)
        service.signed(f.request(SigningUse.AGIR_ORGANISATIONS, sheet))
    }

    @Test
    fun theUseOfDeciderSignsEachOfItsTypesAndRefusesAnyOther() {
        val service = f.open()
        f.certifyAll(service)
        val dora = TestMember("dora", setOf(SigningUse.DECIDER_PACTE))
        f.loadTeam(service, dora.member())
        val use = SigningUse.DECIDER_PACTE
        val key = f.currentKey(service, use)!!
        fun content(d: String, tags: List<List<String>>) = UnsignedEvent(
            key, f.now(), EventKinds.LONG_FORM_ARTICLE,
            listOf(listOf("d", d), listOf("title", "[EXEMPLE] $d"), listOf("ppf-origin", "human")) + tags,
            "[EXEMPLE]",
        )
        // The Pact, the answers, the official petitions and the final texts of a deliberation: one approval each.
        for (type in listOf("pacte", "engagement", "petition", "proposition-finale")) {
            val event = content("exemple-$type", listOf(listOf("ppf-type", type)))
            f.submit(service, use, event)
            assertEquals(1, f.approve(service, dora, use, event).required, type)
            val signed = service.signed(f.request(use, event))
            assertTrue(PlatformTrust.of(f.rootHex, service.certificates()).isPlatformContent(signed, use), type)
        }
        // An unknown type, no type or two types are refused.
        for (tags in listOf(listOf(listOf("ppf-type", "inconnu")), emptyList(), listOf(listOf("ppf-type", "petition"), listOf("ppf-type", "pacte")))) {
            assertEquals(SigningRefusals.BAD_TAG, service.draftRefusal(use, content("exemple-refuse", tags)), tags.toString())
        }
        // A type of « Décider » is never signed as an editorial content.
        assertEquals(SigningRefusals.BAD_TAG, service.draftRefusal(SigningUse.EDITORIAL_COMPRENDRE, f.editorial(f.currentKey(service, SigningUse.EDITORIAL_COMPRENDRE)!!, type = "petition")))
        assertEquals(SigningRefusals.BAD_TAG, service.draftRefusal(SigningUse.EDITORIAL_VOIR, f.editorial(f.currentKey(service, SigningUse.EDITORIAL_VOIR)!!, type = "proposition-finale")))
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
        f.submit(service, SigningUse.EDITORIAL_VOIR, event, author = DraftAuthor(account = "dan"))
        f.approve(service, f.alice, SigningUse.EDITORIAL_VOIR, event)
        service.signed(f.request(SigningUse.EDITORIAL_VOIR, event))
        service.refusal(f.request(SigningUse.EDITORIAL_VOIR, event))
        val lines = service.journal(0, 1_000)
        var previous = SignerJournal.GENESIS
        lines.forEachIndexed { index, line ->
            assertEquals(index + 1L, line.seq)
            assertEquals(previous, line.previousHash)
            assertEquals(SignerJournal.hashOf(line), line.hash)
            previous = line.hash
        }
        assertEquals(service.journalHead().hash, previous)
        val draftLine = lines.single { it.action == JournalActions.DRAFT_SUBMITTED }
        assertEquals(false, draftLine.doubleValidation)
        assertEquals("dan", draftLine.authorAccount)
        val approvalLine = lines.single { it.action == JournalActions.APPROVAL_RECORDED }
        assertEquals(listOf("alice"), approvalLine.approvers)
        assertEquals(f.alice.passkey.credential.id, approvalLine.credential)
        val signedLine = lines.single { it.action == JournalActions.SIGNED && it.use == SigningUse.EDITORIAL_VOIR.id }
        assertEquals(event.idHex(), signedLine.event)
        assertEquals(listOf("alice"), signedLine.approvers)
        assertEquals("dan", signedLine.authorAccount)
        assertEquals(SigningRefusals.ALREADY_SIGNED, lines.last().code)
        val text = String(Files.readAllBytes(f.dir.resolve("journal.jsonl")))
        assertFalse("Texte confidentiel" in text)
        assertFalse("Un texte sourcé" in text)
        assertTrue(service.journal(lines.size - 1L, 10).size == 1)
        // The new fields are inside the hash: changing one stops the service.
        val file = f.dir.resolve("journal.jsonl")
        Files.write(file, text.replace("\"author_account\":\"dan\"", "\"author_account\":\"erin\"").toByteArray())
        assertTrue("breaks the chain" in assertFailsWith<SignerStartupException> { f.open() }.message!!)
        // A line changed by hand stops the service at its next start.
        Files.write(file, text.replace("\"code\":\"already_signed\"", "\"code\":\"rate_limited\"").toByteArray())
        assertTrue("breaks the chain" in assertFailsWith<SignerStartupException> { f.open() }.message!!)
        // A line removed, too.
        Files.write(file, text.lines().filterIndexed { i, _ -> i != 2 }.joinToString("\n").toByteArray())
        assertFailsWith<SignerStartupException> { f.open() }
    }

    @Test
    fun aLastLineTornByACrashIsCutAndRecordedAnythingElseStops() {
        f.ready()
        val file = f.dir.resolve("journal.jsonl")
        val intact = Files.readAllBytes(file)
        val lines = String(intact).trimEnd('\n').split('\n').size
        // A torn last line: no line end, unreadable.
        val torn = "{\"seq\":${lines + 1},\"at\":1,\"act"
        Files.write(file, intact + torn.toByteArray())
        val reopened = f.open()
        val cut = reopened.journal(lines.toLong(), 10).first()
        assertEquals(JournalActions.TORN_LINE_CUT, cut.action)
        assertEquals(lines + 1L, cut.seq)
        assertEquals(torn.length.toString(), cut.code)
        val after = Files.readAllBytes(file)
        assertTrue(after.size > intact.size && after.last() == '\n'.code.toByte())
        // Opened again, the repaired journal is a valid chain.
        f.open()
        // A complete last line without its line end is kept and gets its line end back.
        val withEnd = Files.readAllBytes(file)
        Files.write(file, withEnd.copyOf(withEnd.size - 1))
        val kept = f.open()
        assertTrue(kept.journal(0, 10_000).none { it.action == JournalActions.TORN_LINE_CUT && it.seq > lines + 1 })
        f.open()
        // A broken line in the middle, or a torn line followed by another, stops the service.
        val good = Files.readAllBytes(file)
        val text = String(good)
        val broken = text.replaceFirst("\n", "\n{pas du json}\n")
        Files.write(file, broken.toByteArray())
        assertFailsWith<SignerStartupException> { f.open() }
        Files.write(file, good + "{\"seq\":".toByteArray() + "\n{}".toByteArray())
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

/** The start of an OpenTimestamps proof of [digest] (hex): header, version 1, file hash operation [op], digest, then a few bytes. */
internal fun otsProof(digest: String, op: Int = 0x08): ByteArray {
    val magic = byteArrayOf(0x00) + "OpenTimestamps".toByteArray() + byteArrayOf(0x00, 0x00) + "Proof".toByteArray() +
        byteArrayOf(0x00, 0xbf.toByte(), 0x89.toByte(), 0xe2.toByte(), 0xe8.toByte(), 0x84.toByte(), 0xe8.toByte(), 0x92.toByte(), 0x94.toByte())
    return magic + byteArrayOf(0x01, op.toByte()) + digest.hexToByteArray() + byteArrayOf(0xf0.toByte(), 0x10) + ByteArray(16) { 0x5a } + byteArrayOf(0x08, 0x00)
}
