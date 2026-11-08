package org.politisationpopulairefrancaise.core.platform

import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.EventReplacement
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UrnClosuresTest {
    private val root = IdentityKeys.generate()
    private val rootHex = root.publicKey.hex
    private val first = IdentityKeys.generate()
    private val next = IdentityKeys.generate()
    private val start = 1_793_000_000L
    private val day = 86_400L

    private fun certify(key: IdentityKeys, notBefore: Long): SignedEvent =
        signWith(root, KeyCertificate.unsigned(rootHex, key.publicKey.hex, listOf(SigningUse.DECIDER_URN_CLOSURE), notBefore, notBefore + 90 * day, notBefore - day))

    private val trust = PlatformTrust.of(rootHex, listOf(certify(first, start), certify(next, start + 80 * day)))

    private fun closure(key: IdentityKeys, number: Int, at: Long, results: String = "{}"): SignedEvent = signWith(
        key,
        UnsignedEvent(
            key.publicKey.hex, at, EventKinds.APP_DATA,
            listOf(listOf("d", "ppf:urne-cloture:$number"), listOf("ppf-urne", "$number"), listOf("ppf-closing-sha256", "d".repeat(64))),
            results,
        ),
    )

    @Test
    fun theEarliestClosingStatementIsKeptAndALaterOneIsAConflict() {
        val real = closure(first, 12, start + day, """{"oui":10}""")
        val later = closure(first, 12, start + 2 * day, """{"oui":0}""")
        // For an addressable event the latest would normally win: never for a ballot box.
        assertTrue(EventReplacement.supersedes(later, real))
        val chosen = UrnClosures.choose(listOf(later, real, real), trust).getValue("ppf:urne-cloture:12")
        assertEquals(real.id, chosen.closure.id)
        assertEquals(listOf(later.id), chosen.conflicts.map { it.id })
        assertTrue(chosen.hasConflict)
        // Signed with the next key after a rotation (another address): still a conflict, never a replacement.
        val rotated = closure(next, 12, start + 81 * day)
        assertEquals(real.id, UrnClosures.choose(listOf(rotated, real), trust).getValue("ppf:urne-cloture:12").closure.id)
    }

    @Test
    fun onlyPlatformClosingStatementsCountAndEachBoxStandsAlone() {
        val stranger = IdentityKeys.generate()
        val forged = closure(stranger, 12, start)
        val real = closure(first, 12, start + day)
        val other = closure(first, 13, start + day)
        val chosen = UrnClosures.choose(listOf(forged, real, other), trust)
        assertEquals(setOf("ppf:urne-cloture:12", "ppf:urne-cloture:13"), chosen.keys)
        assertEquals(real.id, chosen.getValue("ppf:urne-cloture:12").closure.id)
        assertFalse(chosen.getValue("ppf:urne-cloture:12").hasConflict)
        assertTrue(UrnClosures.choose(listOf(real), PlatformTrust.of(null)).isEmpty())
    }
}
