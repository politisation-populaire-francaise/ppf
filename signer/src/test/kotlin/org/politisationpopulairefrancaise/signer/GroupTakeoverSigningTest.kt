package org.politisationpopulairefrancaise.signer

import org.politisationpopulairefrancaise.core.groups.RoomModeration
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.platform.DraftAuthor
import org.politisationpopulairefrancaise.core.platform.PlatformTrust
import org.politisationpopulairefrancaise.core.platform.SigningRefusals
import org.politisationpopulairefrancaise.core.platform.SigningUse
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The takeover of a room left without administrator (D-144): a human use, the member who asks in the queue
 * `groups.orphans` is the author and never approves, another member allowed for the use approves on the page
 * of the service, one approval, the form of the put-user of the rooms with the reason as content.
 */
class GroupTakeoverSigningTest {
    private val f = SignerFixture()
    private val use = SigningUse.GROUPS_TAKEOVER

    /** Two moderators of the back-office: the one who asks, and the one who approves. */
    private val mona = TestMember("mona", setOf(SigningUse.GROUPS_TAKEOVER))
    private val nadia = TestMember("nadia", setOf(SigningUse.GROUPS_TAKEOVER, SigningUse.REVENDICATIONS_STATUS))
    private val asker = DraftAuthor(account = "mona", pubkey = mona.nostr.publicKey.hex)
    private val reason = "[EXEMPLE] Le seul administrateur a été déclaré compromis ; le modérateur en place reprend le salon."

    private fun ready(config: SignerConfig = f.config()): SignerService = f.open(config).also {
        f.certifyAll(it)
        f.loadTeam(it, f.alice.member(), mona.member(), nadia.member())
    }

    private fun takeover(
        key: String,
        target: String = IdentityKeys.generate().publicKey.hex,
        tags: List<List<String>> = listOf(listOf("h", "collectif-exemple"), listOf("p", target, "admin")),
        content: String = reason,
        kind: Int = RoomModeration.PUT_USER,
        createdAt: Long = f.now(),
    ) = UnsignedEvent(key, createdAt, kind, tags, content)

    private fun SignerService.draftRefusal(event: UnsignedEvent, author: DraftAuthor? = asker): Refusal? =
        (submitDraft(f.draft(use, event, author = author)) as? DraftOutcome.Refused)?.refusal

    private fun SignerService.signRefusal(event: UnsignedEvent): String? =
        (sign(f.request(use, event)) as? SignOutcome.Refused)?.refusal?.code

    @Test
    fun theRuleOfTheUseIsHumanWithTenSignaturesAnHour() {
        val policy = SigningPolicies.of(use)
        assertEquals(Approvals.HUMAN, policy.approvals)
        assertEquals(10, policy.maxPerHour)
        assertEquals(10, SigningPolicies.DEFAULT_MAX_PER_HOUR[use])
        assertEquals(SigningPolicies.HUMAN_MAX_AGE_SECONDS, policy.maxAgeSeconds)
        assertTrue(ApprovalTexts.useLabel(use).contains("salon"))
        assertTrue(ApprovalTexts.kindLabel(RoomModeration.PUT_USER).contains("NIP-29"))
    }

    @Test
    fun aTakeoverAskedByOneMemberIsSignedAfterTheApprovalOfAnother() {
        val service = ready()
        val key = f.currentKey(service, use)!!
        val event = takeover(key)
        val submitted = assertIs<DraftOutcome.Submitted>(service.submitDraft(f.draft(use, event, author = asker)))
        assertEquals("$TEST_ORIGIN/approbation/groups.takeover/${event.idHex()}", submitted.approvalUrl)
        // Neither the author nor a member without the use is offered; nothing is signed before the approval.
        assertEquals(listOf(nadia.passkey.credential.id), assertIs<OptionsOutcome.Options>(service.approvalOptions(use.id, event.idHex())).credentialIds)
        assertEquals(SigningRefusals.AUTHOR_CANNOT_APPROVE, f.approvalRefusal(service, use, event, mona.approve(use, event)))
        assertEquals(SigningRefusals.NOT_ALLOWED_FOR_USE, f.approvalRefusal(service, use, event, f.alice.approve(use, event)))
        assertEquals(SigningRefusals.APPROVALS_MISSING, service.signRefusal(event))
        val recorded = f.approve(service, nadia, use, event)
        assertEquals(1, recorded.required)
        assertTrue(recorded.ready)
        val signed = assertIs<SignOutcome.Signed>(service.sign(f.request(use, event))).event
        assertEquals(event.idHex(), signed.id)
        val trust = PlatformTrust.of(f.rootHex, service.certificates())
        assertTrue(trust.isPlatformContent(signed, use))
        assertTrue(SigningUse.entries.filter { it != use }.none { trust.isPlatformContent(signed, it) })
        assertEquals(DraftState.Signed, service.draftState(use.id, event.idHex()))
        assertEquals(SigningRefusals.ALREADY_SIGNED, service.signRefusal(event))
        // The author named by their Nostr key alone is refused too.
        val second = takeover(key, content = "[EXEMPLE] Un autre salon")
        f.submit(service, use, second, author = DraftAuthor(pubkey = nadia.nostr.publicKey.hex))
        assertEquals(SigningRefusals.AUTHOR_CANNOT_APPROVE, f.approvalRefusal(service, use, second, nadia.approve(use, second)))
        f.approve(service, mona, use, second)
        assertIs<SignOutcome.Signed>(service.sign(f.request(use, second)))
    }

    @Test
    fun aTakeoverAlwaysNamesItsAuthorAndIsNeverSignedWithoutDraft() {
        val service = ready()
        val event = takeover(f.currentKey(service, use)!!)
        assertEquals(SigningRefusals.AUTHOR_REQUIRED, service.draftRefusal(event, author = null)?.code)
        assertEquals(SigningRefusals.AUTHOR_REQUIRED, service.draftRefusal(event, author = DraftAuthor("", ""))?.code)
        assertEquals(SigningRefusals.UNKNOWN_DRAFT, service.signRefusal(event))
        // When the author is the only member allowed for the use, nobody may approve.
        val lonely = SignerFixture()
        val alone = lonely.open()
        lonely.certifyAll(alone)
        lonely.loadTeam(alone, mona.member())
        val own = takeover(lonely.currentKey(alone, use)!!, createdAt = lonely.now())
        lonely.submit(alone, use, own, author = asker)
        assertEquals(SigningRefusals.NOT_ALLOWED_FOR_USE, assertIs<OptionsOutcome.Refused>(alone.approvalOptions(use.id, own.idHex())).refusal.code)
    }

    @Test
    fun eachRuleOfTheFormHasItsCodeAtSubmissionAndAtSignature() {
        val service = ready()
        val key = f.currentKey(service, use)!!
        val target = IdentityKeys.generate().publicKey.hex
        val room = listOf("h", "collectif-exemple")
        val admin = listOf("p", target, "admin")
        val cases = listOf(
            takeover(key, tags = listOf(admin)) to Refusal(SigningRefusals.MISSING_TAG, "h"),
            takeover(key, tags = listOf(room, listOf("h", "autre-salon"), admin)) to Refusal(SigningRefusals.BAD_TAG, "h"),
            takeover(key, tags = listOf(room)) to Refusal(SigningRefusals.MISSING_TAG, "p"),
            takeover(key, tags = listOf(room, listOf("p", target, "moderator"))) to Refusal(SigningRefusals.BAD_TAG, "p"),
            takeover(key, tags = listOf(room, listOf("p", target))) to Refusal(SigningRefusals.BAD_TAG, "p"),
            takeover(key, tags = listOf(room, admin, listOf("p", IdentityKeys.generate().publicKey.hex, "admin"))) to Refusal(SigningRefusals.BAD_TAG, "p"),
            takeover(key, tags = listOf(room, admin, listOf("alt", "Nomination"))) to Refusal(SigningRefusals.TAG_NOT_ALLOWED, "alt"),
            takeover(key, content = "   ") to Refusal(SigningRefusals.BAD_CONTENT),
            takeover(key, content = "x".repeat(501)) to Refusal(SigningRefusals.BAD_CONTENT),
            takeover(key, kind = RoomModeration.REMOVE_USER) to Refusal(SigningRefusals.KIND_NOT_ALLOWED),
            // A key of the platform never receives a role: the signing key, another working key, the root.
            takeover(key, target = key) to Refusal(SigningRefusals.BAD_TAG, "p"),
            takeover(key, target = f.currentKey(service, SigningUse.EDITORIAL_VOIR)!!) to Refusal(SigningRefusals.BAD_TAG, "p"),
            takeover(key, target = f.rootHex) to Refusal(SigningRefusals.BAD_TAG, "p"),
        )
        for ((event, expected) in cases) {
            assertEquals(expected, service.draftRefusal(event), "${event.kind} ${event.tags} ${event.content.length}")
            assertEquals(expected.code, service.signRefusal(event), "${event.kind} ${event.tags} ${event.content.length}")
        }
        // A reason of 500 characters is accepted; a key of another use never signs a takeover.
        assertIs<DraftOutcome.Submitted>(service.submitDraft(f.draft(use, takeover(key, content = "x".repeat(500)), author = asker)))
        assertEquals(SigningRefusals.WRONG_KEY, service.draftRefusal(takeover(f.currentKey(service, SigningUse.EDITORIAL_VOIR)!!))?.code)
        // An editorial key never signs a 9000, and a takeover key never signs an editorial content.
        assertEquals(SigningRefusals.KIND_NOT_ALLOWED, (service.submitDraft(f.draft(SigningUse.EDITORIAL_VOIR, takeover(key))) as DraftOutcome.Refused).refusal.code)
        assertEquals(SigningRefusals.KIND_NOT_ALLOWED, service.draftRefusal(f.editorial(key))?.code)
    }

    @Test
    fun theHourlyCeilingHolds() {
        val service = ready(f.config(maxPerHour = mapOf(use to 1)))
        val key = f.currentKey(service, use)!!
        val first = takeover(key)
        val second = takeover(key, content = "[EXEMPLE] Un autre salon")
        for (event in listOf(first, second)) {
            f.submit(service, use, event, author = asker)
            f.approve(service, nadia, use, event)
        }
        assertIs<SignOutcome.Signed>(service.sign(f.request(use, first)))
        assertEquals(SigningRefusals.RATE_LIMITED, service.signRefusal(second))
        f.clock.advance(3_601)
        assertIs<SignOutcome.Signed>(service.sign(f.request(use, second)))
    }

    @Test
    fun theConfigurationNamesTheUseLikeTheOthers() {
        val dir = java.nio.file.Files.createTempDirectory("ppf-signer-takeover")
        val kek = dir.resolve("kek").also { java.nio.file.Files.writeString(it, "cd".repeat(32)) }
        val token = dir.resolve("token").also { java.nio.file.Files.writeString(it, TEST_TOKEN) }
        val base = mapOf(
            "PPF_SIGNER_DATA_DIR" to dir.resolve("data").toString(),
            "PPF_SIGNER_KEK_FILE" to kek.toString(),
            "PPF_SIGNER_CLIENT_TOKEN_FILE" to token.toString(),
            "PPF_SIGNER_APPROVAL_ORIGIN" to TEST_ORIGIN,
            "PPF_SIGNER_RP_ID" to TEST_RP_ID,
            "PPF_SIGNER_BACKOFFICE_ORIGIN" to TEST_BACKOFFICE,
        )
        val config = SignerConfig.fromEnvironment(base + mapOf("PPF_SIGNER_USES" to "editorial.voir,groups.takeover", "PPF_SIGNER_MAX_PER_HOUR" to "groups.takeover=3"))
        assertEquals(setOf(SigningUse.EDITORIAL_VOIR, use), config.enabledUses)
        assertEquals(mapOf(use to 3), config.maxPerHour)
        assertTrue(use in SignerConfig.fromEnvironment(base).enabledUses, "enabled by default, as every use")
        assertFalse(use in SignerConfig.fromEnvironment(base + ("PPF_SIGNER_USES" to "editorial.voir")).enabledUses)
    }
}
