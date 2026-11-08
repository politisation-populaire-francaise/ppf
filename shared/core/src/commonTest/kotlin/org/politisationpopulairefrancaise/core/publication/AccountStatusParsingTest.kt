package org.politisationpopulairefrancaise.core.publication

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The answers of the server about recovery keys and compromised accounts (S-146). */
class AccountStatusParsingTest {

    @Test
    fun theRefusalsOfRecoveryAreNamed() {
        val codes = mapOf(
            "account_compromised" to RejectionReason.ACCOUNT_COMPROMISED,
            "recovery_already_set" to RejectionReason.RECOVERY_ALREADY_SET,
            "already_compromised" to RejectionReason.ALREADY_COMPROMISED,
            "successor_taken" to RejectionReason.SUCCESSOR_TAKEN,
            "invalid_commitment" to RejectionReason.INVALID_COMMITMENT,
            "invalid_notice" to RejectionReason.INVALID_NOTICE,
        )
        codes.forEach { (code, reason) ->
            assertEquals(reason, ServerPublicationPort.rejection("e", """{"error":"$code","message":"…"}""").reason)
        }
    }

    @Test
    fun theStatusIsRead() {
        val healthy = ServerPublicationPort.accountStatus("""{"compromised":false,"since":null,"successor":null,"notice":null,"commitment":null}""")
        assertFalse(healthy.compromised)
        assertNull(healthy.commitment)
        val withKey = ServerPublicationPort.accountStatus("""{"compromised":false,"commitment":"${"a".repeat(64)}","extra":1}""")
        assertEquals("a".repeat(64), withKey.commitment)
        // A notice that does not check out by itself is dropped; the date stays.
        val forged = ServerPublicationPort.accountStatus(
            """{"compromised":true,"since":1794000000,"successor":null,"commitment":"${"b".repeat(64)}",""" +
                """"notice":{"id":"${"0".repeat(64)}","pubkey":"${"1".repeat(64)}","created_at":1,"kind":1777,"tags":[],"content":"","sig":"${"2".repeat(128)}"}}""",
        )
        assertTrue(forged.compromised)
        assertEquals(1_794_000_000L, forged.since)
        assertNull(forged.notice)
    }
}
