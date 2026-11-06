package org.politisationpopulairefrancaise.core.signing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventReplacementTest {

    private val pubkey = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"

    private fun event(kind: Int, createdAt: Long, id: String, tags: List<List<String>> = emptyList()) =
        SignedEvent(id = id, pubkey = pubkey, createdAt = createdAt, kind = kind, tags = tags, content = "", sig = "00")

    @Test
    fun replacementKeysFollowTheKindRanges() {
        assertNull(EventReplacement.replacementKey(event(1, 1, "a")))
        assertEquals("0:$pubkey", EventReplacement.replacementKey(event(0, 1, "a")))
        assertEquals("10002:$pubkey", EventReplacement.replacementKey(event(10002, 1, "a")))
        assertEquals("30023:$pubkey:fiche", EventReplacement.replacementKey(event(30023, 1, "a", listOf(listOf("d", "fiche")))))
        assertEquals("30023:$pubkey:", EventReplacement.replacementKey(event(30023, 1, "a")))
    }

    @Test
    fun newestWinsAndLowestIdBreaksTies() {
        assertTrue(EventReplacement.supersedes(event(0, 20, "b"), event(0, 10, "a")))
        assertFalse(EventReplacement.supersedes(event(0, 10, "a"), event(0, 20, "b")))
        assertTrue(EventReplacement.supersedes(event(0, 10, "a"), event(0, 10, "b")))
        assertFalse(EventReplacement.supersedes(event(0, 10, "b"), event(0, 10, "a")))
        assertFalse(EventReplacement.supersedes(event(0, 10, "a"), event(0, 10, "a")))
    }
}
