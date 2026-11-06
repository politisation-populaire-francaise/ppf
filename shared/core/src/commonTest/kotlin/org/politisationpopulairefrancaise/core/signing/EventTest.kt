package org.politisationpopulairefrancaise.core.signing

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventTest {

    // A real event published on Nostr in February 2022 (fixture used by several Nostr libraries).
    private val realEventJson = """{"kind":1,"id":"dc90c95f09947507c1044e8f48bcf6350aa6bff1507dd4acfc755b9239b5c962",""" +
        """"pubkey":"3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d","created_at":1644271588,"tags":[],""" +
        """"content":"now that https://blueskyweb.org/blog/2-7-2022-overview was announced we can stop working on nostr?",""" +
        """"sig":"230e9d8f0ddaf7eb70b5f7741ccfa37e87a455c9a469282e3464e2052d3192cd63a167e196e381ef9d7e69e9ea43af2443b839974dc85d8aaab9efe1d9296524"}"""

    @Test
    fun realNostrEventIsAuthentic() {
        val event = SignedEvent.fromJson(realEventJson)
        assertEquals(1, event.kind)
        assertEquals(event.id, event.unsigned().idHex())
        assertTrue(event.verify())
    }

    @Test
    fun anyTamperingBreaksAuthenticity() {
        val event = SignedEvent.fromJson(realEventJson)
        assertFalse(event.copy(content = event.content + " ").verify())
        assertFalse(event.copy(createdAt = event.createdAt + 1).verify())
        assertFalse(event.copy(kind = 2).verify())
        assertFalse(event.copy(tags = listOf(listOf("t", "x"))).verify())
        assertFalse(event.copy(id = "0" + event.id.drop(1)).verify())
        assertFalse(event.copy(sig = event.sig.dropLast(1) + "0").verify())
        assertFalse(event.copy(pubkey = "7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e").verify())
        assertFalse(event.copy(sig = "zz" + event.sig.drop(2)).verify())
        assertFalse(event.copy(pubkey = "nope").verify())
    }

    @Test
    fun canonicalSerializationFollowsNip01() {
        val pubkey = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
        val event = UnsignedEvent(
            pubkey = pubkey,
            createdAt = 1700000000,
            kind = 30023,
            tags = listOf(listOf("d", "fiche-\"test\""), listOf("t", "énergie", "extra")),
            content = "Ligne 1\nTab\tquote \" backslash \\ é € \r \b \u000C \u0001",
        )
        assertEquals(
            "[0,\"$pubkey\",1700000000,30023," +
                "[[\"d\",\"fiche-\\\"test\\\"\"],[\"t\",\"énergie\",\"extra\"]]," +
                "\"Ligne 1\\nTab\\tquote \\\" backslash \\\\ é € \\r \\b \\f \u0001\"]",
            event.canonicalJson(),
        )
        assertEquals(64, event.idHex().length)
    }

    @Test
    fun unsignedEventRejectsNonsense() {
        val pubkey = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
        assertFailsWith<IllegalArgumentException> { UnsignedEvent(pubkey.uppercase(), 1, 1) }
        assertFailsWith<IllegalArgumentException> { UnsignedEvent(pubkey, 1, 70000) }
        assertFailsWith<IllegalArgumentException> { UnsignedEvent(pubkey, -1, 1) }
    }

    @Test
    fun localSignerProducesAuthenticEventsAndJsonRoundTrips() = runTest {
        val keys = IdentityKeys.generate()
        val signer = LocalEventSigner(keys)
        val event = signer.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] Bonjour à toutes et à tous")
        assertEquals(keys.publicKey.hex, event.pubkey)
        assertTrue(event.verify())
        assertTrue(event.createdAt > 1_700_000_000)

        val json = event.toJson()
        assertTrue(json.contains("\"created_at\":"))
        val parsed = SignedEvent.fromJson(json)
        assertEquals(event, parsed)
        assertTrue(parsed.verify())

        // Unknown fields from other implementations are ignored, not fatal.
        val extended = json.dropLast(1) + ",\"extra\":true}"
        assertEquals(event, SignedEvent.fromJson(extended))
    }

    @Test
    fun signerRefusesEventsOfSomeoneElse() = runTest {
        val signer = LocalEventSigner(IdentityKeys.generate())
        val other = UnsignedEvent.create(IdentityKeys.generate().publicKey, EventKinds.TEXT_NOTE, 1)
        assertFailsWith<IllegalArgumentException> { signer.sign(other) }
    }

    @Test
    fun tagsAndContentAddresses() = runTest {
        val signer = LocalEventSigner(IdentityKeys.generate())
        val article = signer.sign(
            kind = EventKinds.LONG_FORM_ARTICLE,
            content = "[EXEMPLE] Fiche pratique",
            tags = listOf(listOf("d", "fiche:droits:manifestation"), listOf("t", "droits"), listOf("t", "manifestation")),
        )
        assertEquals(listOf("droits", "manifestation"), article.tagValues("t"))
        assertEquals("fiche:droits:manifestation", article.firstTagValue("d"))
        assertNull(article.firstTagValue("e"))

        val address = ContentAddress.of(article)!!
        assertEquals("30023:${article.pubkey}:fiche:droits:manifestation", address.toString())
        assertEquals(address, ContentAddress.parse(address.toString()))
        assertEquals("fiche:droits:manifestation", address.identifier)

        val note = signer.sign(kind = EventKinds.TEXT_NOTE, content = "x")
        assertNull(ContentAddress.of(note))
        assertFailsWith<IllegalArgumentException> { ContentAddress(1, article.pubkey, "x") }
        assertFailsWith<IllegalArgumentException> { ContentAddress.parse("30023:abc") }
    }

    @Test
    fun kindRanges() {
        assertTrue(EventKinds.isRegular(1))
        assertTrue(EventKinds.isRegular(1984))
        assertTrue(EventKinds.isReplaceable(0))
        assertTrue(EventKinds.isReplaceable(10002))
        assertTrue(EventKinds.isEphemeral(22242))
        assertTrue(EventKinds.isAddressable(30023))
        assertFalse(EventKinds.isAddressable(1))
        assertFalse(EventKinds.isRegular(30023))
    }
}
