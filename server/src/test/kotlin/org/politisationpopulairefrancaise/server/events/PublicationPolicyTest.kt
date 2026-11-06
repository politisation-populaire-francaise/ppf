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

    /** A complete group description (D-028): id, name, territory, accepted charter. */
    private fun description(id: String = "demo", name: String? = "[EXEMPLE] Collectif", territory: String? = "Lyon", charter: String? = GroupKinds.CHARTER_VERSION): List<List<String>> =
        listOfNotNull(
            listOf("d", id),
            name?.let { listOf(GroupKinds.NAME_TAG, it) },
            territory?.let { listOf(GroupKinds.TERRITORY_TAG, it) },
            charter?.let { listOf(GroupKinds.CHARTER_TAG, it) },
        )

    @Test
    fun anyKeyDescribesAGroupProvidedItNamesATerritoryAndAcceptsTheCharter() = runBlocking {
        val policy = PublicationPolicy(setOf(editor.publicKey.hex))
        val signer = LocalEventSigner(citizen)
        assertEquals(PublicationPolicy.Decision.Allowed, policy.check(signer.sign(kind = GroupKinds.METADATA, tags = description())))
        assertEquals(PublicationPolicy.Decision.Allowed, policy.check(LocalEventSigner(editor).sign(kind = GroupKinds.METADATA, tags = description())))
        val withOptionalTags = description() + listOf(listOf("about", "[EXEMPLE] à propos"), listOf("picture", "https://example.invalid/p.png"), listOf("closed"))
        assertEquals(PublicationPolicy.Decision.Allowed, policy.check(signer.sign(kind = GroupKinds.METADATA, tags = withOptionalTags)))

        for (name in listOf(null, "", "   ", "!!! ...")) {
            val refused = assertIs<PublicationPolicy.Decision.Refused>(policy.check(signer.sign(kind = GroupKinds.METADATA, tags = description(name = name))))
            assertEquals(400, refused.status, "name $name")
            assertEquals("missing_name", refused.error, "name $name")
        }
        for (territory in listOf(null, "", " ", "-")) {
            val refused = assertIs<PublicationPolicy.Decision.Refused>(policy.check(signer.sign(kind = GroupKinds.METADATA, tags = description(territory = territory))))
            assertEquals(400, refused.status, "territory $territory")
            assertEquals("missing_territory", refused.error, "territory $territory")
        }
        for (charter in listOf(null, "", "0.9", "1.0 ", "2.0", "oui")) {
            val refused = assertIs<PublicationPolicy.Decision.Refused>(policy.check(signer.sign(kind = GroupKinds.METADATA, tags = description(charter = charter))))
            assertEquals(400, refused.status, "charter $charter")
            assertEquals("missing_charter", refused.error, "charter $charter")
        }
        // The first value of a tag is the one that counts, as everywhere else.
        val secondCharter = description(charter = "0.9") + listOf(listOf(GroupKinds.CHARTER_TAG, "1.0"))
        assertEquals("missing_charter", assertIs<PublicationPolicy.Decision.Refused>(policy.check(signer.sign(kind = GroupKinds.METADATA, tags = secondCharter))).error)

        // A description stays small: name, territory, and the whole event (content included).
        val tooLong = listOf(
            description(name = "n".repeat(GroupKinds.MAX_NAME_LENGTH + 1)),
            description(territory = "t".repeat(GroupKinds.MAX_TERRITORY_LENGTH + 1)),
            description() + listOf(listOf(GroupKinds.ABOUT_TAG, "a".repeat(GroupKinds.MAX_DESCRIPTION_BYTES))),
        )
        for (tags in tooLong) {
            val refused = assertIs<PublicationPolicy.Decision.Refused>(policy.check(signer.sign(kind = GroupKinds.METADATA, tags = tags)))
            assertEquals(400, refused.status)
            assertEquals("description_too_long", refused.error)
        }
        val bigContent = signer.sign(kind = GroupKinds.METADATA, content = "x".repeat(GroupKinds.MAX_DESCRIPTION_BYTES), tags = description())
        assertEquals("description_too_long", assertIs<PublicationPolicy.Decision.Refused>(policy.check(bigContent)).error)
        val atBounds = description(name = "n".repeat(GroupKinds.MAX_NAME_LENGTH), territory = "t".repeat(GroupKinds.MAX_TERRITORY_LENGTH))
        assertEquals(PublicationPolicy.Decision.Allowed, policy.check(signer.sign(kind = GroupKinds.METADATA, tags = atBounds)))
        // Only descriptions are bounded this way: a long note is still fine.
        assertEquals(PublicationPolicy.Decision.Allowed, policy.check(signer.sign(kind = 1, content = "x".repeat(GroupKinds.MAX_DESCRIPTION_BYTES))))

        // The companions of a description (admins, roles) stay reserved to the editorial keys for now.
        val companion = listOf(listOf("d", "demo"), listOf("p", citizen.publicKey.hex, "admin"))
        assertEquals(403, assertIs<PublicationPolicy.Decision.Refused>(policy.check(signer.sign(kind = GroupKinds.ADMINS, tags = companion))).status)
        assertEquals(PublicationPolicy.Decision.Allowed, policy.check(LocalEventSigner(editor).sign(kind = GroupKinds.ADMINS, tags = companion)))
        assertEquals(PublicationPolicy.Decision.Allowed, policy.check(LocalEventSigner(editor).sign(kind = GroupKinds.ROLES, tags = listOf(listOf("d", "demo")))))
    }

    @Test
    fun groupDescriptionsNeedOneValidIdAndMembershipEventsAreRefused() = runBlocking {
        val policy = PublicationPolicy(setOf(editor.publicKey.hex))
        for (badId in listOf("", "with space", "é", "Demo", "x".repeat(65))) {
            val refused = assertIs<PublicationPolicy.Decision.Refused>(policy.check(LocalEventSigner(editor).sign(kind = GroupKinds.METADATA, tags = description(id = badId))))
            assertEquals("bad_group_id", refused.error, "id $badId")
        }
        val noId = LocalEventSigner(citizen).sign(kind = GroupKinds.METADATA, tags = description().drop(1))
        assertEquals("bad_group_id", assertIs<PublicationPolicy.Decision.Refused>(policy.check(noId)).error)
        assertEquals(PublicationPolicy.Decision.Allowed, policy.check(LocalEventSigner(editor).sign(kind = GroupKinds.METADATA, tags = description(id = "a-b_c.d~e9"))))
        // Exactly one id: a second d tag could point the description at someone else's group.
        val twoIds = LocalEventSigner(editor).sign(kind = GroupKinds.METADATA, tags = description() + listOf(listOf("d", "libre")))
        assertEquals("bad_group_id", assertIs<PublicationPolicy.Decision.Refused>(policy.check(twoIds)).error)
        // Flags the relay cannot honour yet.
        for (flag in listOf("private", "hidden")) {
            val flagged = LocalEventSigner(editor).sign(kind = GroupKinds.METADATA, tags = description() + listOf(listOf(flag)))
            assertEquals("unsupported_group_flag", assertIs<PublicationPolicy.Decision.Refused>(policy.check(flagged)).error, flag)
        }
        assertEquals(PublicationPolicy.Decision.Allowed, policy.check(LocalEventSigner(editor).sign(kind = GroupKinds.METADATA, tags = description() + listOf(listOf("closed"), listOf("restricted")))))

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
