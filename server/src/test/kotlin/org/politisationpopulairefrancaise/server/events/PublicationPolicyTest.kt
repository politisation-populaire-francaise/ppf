package org.politisationpopulairefrancaise.server.events

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.server.groups.GroupKinds
import org.politisationpopulairefrancaise.server.relay.MailboxSettings
import org.politisationpopulairefrancaise.server.relay.RelayKinds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PublicationPolicyTest {

    private val editor = IdentityKeys.generate()
    private val citizen = IdentityKeys.generate()

    @Test
    fun parsesHexAndNpubKeysInAnyOrderAndSeparator() {
        val keys = PublicationPolicy.parseKeys(" ${editor.publicKey.npub}, ${citizen.publicKey.hex.uppercase()} ;\n")
        assertEquals(setOf(editor.publicKey.hex, citizen.publicKey.hex), keys)
        assertEquals(emptySet(), PublicationPolicy.parseKeys(null))
        assertEquals(emptySet(), PublicationPolicy.parseKeys("  "))
        assertFailsWith<IllegalArgumentException> { PublicationPolicy.parseKeys("not-a-key") }
    }

    @Test
    fun onlyEditorsPublishEditorialContentsAndEphemeralEventsAreRefused() = runBlocking {
        val policy = PublicationPolicy.fromEnvironment { if (it == PublicationPolicy.EDITOR_KEYS_VARIABLE) editor.publicKey.npub else null }
        assertTrue(policy.isEditor(editor.publicKey.hex))

        val article = LocalEventSigner(editor).sign(kind = Article.KIND, content = "x", tags = listOf(listOf("d", "a")))
        assertEquals(PublicationPolicy.Decision.Allowed, policy.check(article))

        val intruder = LocalEventSigner(citizen).sign(kind = Article.KIND, content = "x", tags = listOf(listOf("d", "a")))
        val refused = assertIs<PublicationPolicy.Decision.Refused>(policy.check(intruder))
        assertEquals(403, refused.status)
        assertEquals("not_allowed", refused.error)

        val note = LocalEventSigner(citizen).sign(kind = EventKinds.TEXT_NOTE, content = "x")
        assertEquals(PublicationPolicy.Decision.Allowed, policy.check(note))

        val ephemeral = LocalEventSigner(editor).sign(kind = 20001, content = "x")
        assertEquals(400, assertIs<PublicationPolicy.Decision.Refused>(policy.check(ephemeral)).status)
    }

    @Test
    fun groupDescriptionsNeedAnEditorKeyAndMembershipEventsAreRefused() = runBlocking {
        val policy = PublicationPolicy(setOf(editor.publicKey.hex))
        val description = listOf(listOf("d", "demo"), listOf("name", "[EXEMPLE] Collectif"))
        assertEquals(PublicationPolicy.Decision.Allowed, policy.check(LocalEventSigner(editor).sign(kind = GroupKinds.METADATA, tags = description)))
        assertEquals(403, assertIs<PublicationPolicy.Decision.Refused>(policy.check(LocalEventSigner(citizen).sign(kind = GroupKinds.METADATA, tags = description))).status)
        assertEquals(403, assertIs<PublicationPolicy.Decision.Refused>(policy.check(LocalEventSigner(citizen).sign(kind = GroupKinds.ADMINS, tags = description))).status)

        for (badId in listOf(null, "", "with space", "é", "Demo", "x".repeat(65))) {
            val tags = if (badId == null) emptyList() else listOf(listOf("d", badId))
            val refused = assertIs<PublicationPolicy.Decision.Refused>(policy.check(LocalEventSigner(editor).sign(kind = GroupKinds.METADATA, tags = tags)))
            assertEquals("bad_group_id", refused.error, "id $badId")
        }
        assertEquals(PublicationPolicy.Decision.Allowed, policy.check(LocalEventSigner(editor).sign(kind = GroupKinds.METADATA, tags = listOf(listOf("d", "a-b_c.d~e9")))))
        // Exactly one id: a second d tag could point the description at someone else's group.
        val twoIds = LocalEventSigner(editor).sign(kind = GroupKinds.METADATA, tags = listOf(listOf("d", "libre"), listOf("d", "demo")))
        assertEquals("bad_group_id", assertIs<PublicationPolicy.Decision.Refused>(policy.check(twoIds)).error)
        // Flags the relay cannot honour yet.
        for (flag in listOf("private", "hidden")) {
            val flagged = LocalEventSigner(editor).sign(kind = GroupKinds.METADATA, tags = listOf(listOf("d", "demo"), listOf(flag)))
            assertEquals("unsupported_group_flag", assertIs<PublicationPolicy.Decision.Refused>(policy.check(flagged)).error, flag)
        }
        assertEquals(PublicationPolicy.Decision.Allowed, policy.check(LocalEventSigner(editor).sign(kind = GroupKinds.METADATA, tags = listOf(listOf("d", "demo"), listOf("closed"), listOf("restricted")))))

        val members = assertIs<PublicationPolicy.Decision.Refused>(policy.check(LocalEventSigner(editor).sign(kind = GroupKinds.MEMBERS, tags = listOf(listOf("d", "demo")))))
        assertEquals("members_list_refused", members.error)
        for (kind in listOf(9000, 9010, 9020, 9021, 9022)) {
            assertEquals("unsupported_kind", assertIs<PublicationPolicy.Decision.Refused>(policy.check(LocalEventSigner(editor).sign(kind = kind))).error)
        }

        val message = LocalEventSigner(citizen).sign(kind = GroupKinds.CHAT_MESSAGE, content = "x", tags = listOf(listOf("h", "demo")))
        assertEquals(PublicationPolicy.Decision.Allowed, policy.check(message))
        val orphan = LocalEventSigner(citizen).sign(kind = GroupKinds.CHAT_MESSAGE, content = "x")
        assertEquals("missing_group", assertIs<PublicationPolicy.Decision.Refused>(policy.check(orphan)).error)
        val twoGroups = LocalEventSigner(citizen).sign(kind = EventKinds.TEXT_NOTE, content = "x", tags = listOf(listOf("h", "demo"), listOf("h", "ferme")))
        assertEquals("multiple_groups", assertIs<PublicationPolicy.Decision.Refused>(policy.check(twoGroups)).error)
    }

    @Test
    fun refusesTagFloodsAndEnvelopesWithoutExactlyOneRecipient() = runBlocking {
        val policy = PublicationPolicy(emptySet())
        val signer = LocalEventSigner(citizen)
        val flood = signer.sign(kind = EventKinds.TEXT_NOTE, tags = List(PublicationPolicy.MAX_TAGS + 1) { listOf("t", "a") })
        assertEquals("too_many_tags", assertIs<PublicationPolicy.Decision.Refused>(policy.check(flood)).error)
        assertEquals(PublicationPolicy.Decision.Allowed, policy.check(signer.sign(kind = EventKinds.TEXT_NOTE, tags = List(PublicationPolicy.MAX_TAGS) { listOf("t", "a") })))

        val recipient = editor.publicKey.hex
        for (tags in listOf(emptyList(), listOf(listOf("p", recipient), listOf("p", recipient)), listOf(listOf("p", "short")), listOf(listOf("p", recipient.uppercase())))) {
            val wrap = signer.sign(kind = RelayKinds.GIFT_WRAP, content = "opaque", tags = tags)
            assertEquals("bad_recipient", assertIs<PublicationPolicy.Decision.Refused>(policy.check(wrap)).error, tags.toString())
        }
        assertEquals(PublicationPolicy.Decision.Allowed, policy.check(signer.sign(kind = RelayKinds.GIFT_WRAP, content = "opaque", tags = listOf(listOf("p", recipient, "wss://relay.example")))))
    }

    @Test
    fun encryptedEnvelopesAreAcceptedFromAnyKey() = runBlocking {
        val policy = PublicationPolicy(emptySet())
        val oneTimeKey = LocalEventSigner(IdentityKeys.generate())
        val wrap = oneTimeKey.sign(kind = RelayKinds.GIFT_WRAP, content = "opaque", tags = listOf(listOf("p", citizen.publicKey.hex)))
        assertEquals(PublicationPolicy.Decision.Allowed, policy.check(wrap))
    }

    @Test
    fun applicationDataIsAcceptedFromAnyKeyIncludingTheMailboxSettings() = runBlocking {
        val policy = PublicationPolicy(emptySet())
        val signer = LocalEventSigner(citizen)
        val mailbox = signer.sign(
            kind = MailboxSettings.KIND,
            tags = listOf(listOf("d", MailboxSettings.IDENTIFIER), listOf(MailboxSettings.RETENTION_DAYS_TAG, "180")),
        )
        assertEquals(PublicationPolicy.Decision.Allowed, policy.check(mailbox))
        // Kind 30078 is a standard kind (NIP-78): other identifiers pass too, the server simply ignores them.
        val other = signer.sign(kind = RelayKinds.APPLICATION_DATA, content = "{}", tags = listOf(listOf("d", "some-other-app")))
        assertEquals(PublicationPolicy.Decision.Allowed, policy.check(other))
    }
}
