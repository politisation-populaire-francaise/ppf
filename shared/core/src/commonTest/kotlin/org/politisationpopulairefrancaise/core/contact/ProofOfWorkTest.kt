package org.politisationpopulairefrancaise.core.contact

import fr.acinq.bitcoin.Crypto
import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProofOfWorkTest {

    @Test
    fun theDifficultyIsTheNumberOfLeadingZeroBits() {
        // The example of NIP-13: 000000000e9d97a1… has 36 leading zero bits.
        assertEquals(36, ProofOfWork.difficulty("000000000e9d97a1" + "f".repeat(48)))
        assertEquals(0, ProofOfWork.difficulty("f" + "0".repeat(63)))
        assertEquals(3, ProofOfWork.difficulty("1" + "f".repeat(63)))
        assertEquals(2, ProofOfWork.difficulty("2" + "f".repeat(63)))
        assertEquals(1, ProofOfWork.difficulty("7" + "f".repeat(63)))
        assertEquals(4, ProofOfWork.difficulty("08" + "f".repeat(62)))
        assertEquals(256, ProofOfWork.difficulty("0".repeat(64)))
        // Counting stops at anything that is not lowercase hexadecimal.
        assertEquals(4, ProofOfWork.difficulty("0G" + "0".repeat(62)))
        assertEquals(0, ProofOfWork.difficulty("A" + "0".repeat(63)))
        assertEquals(12, ProofOfWork.difficulty(byteArrayOf(0, 0x0f, -1)))
        assertEquals(7, ProofOfWork.difficulty(byteArrayOf(1)))
    }

    @Test
    fun theMinerReachesItsTargetAndCommitsToIt() = runTest {
        val keys = IdentityKeys.generate()
        val event = UnsignedEvent.create(keys.publicKey, 1059, 1_780_000_000, listOf(listOf("p", "b".repeat(64)), listOf("nonce", "7", "99")), "[EXEMPLE] contenu")
        val mined = ProofOfWork.mine(event, 12)
        assertTrue(ProofOfWork.difficulty(mined.idHex()) >= 12)
        assertEquals(ProofOfWork.difficulty(Crypto.sha256(mined.canonicalJson().encodeToByteArray())), ProofOfWork.difficulty(mined.idHex()))
        // The previous nonce tag is replaced; the other tags are kept in their order.
        assertEquals(listOf("p", "b".repeat(64)), mined.tags.first())
        val nonce = mined.tags.single { it.first() == ProofOfWork.TAG }
        assertEquals(ProofOfWork.NONCE_DIGITS, nonce[1].length)
        assertEquals("12", nonce[2])
        assertEquals(12, ProofOfWork.committedTarget(mined.tags))
        val signed = LocalEventSigner(keys).sign(mined)
        assertTrue(signed.verify())
        assertTrue(ProofOfWork.satisfies(signed, 12))
        assertTrue(ProofOfWork.satisfies(signed, 0))
        // Committed to 12: never enough for 13, even when lucky.
        assertFalse(ProofOfWork.satisfies(signed, 13))
        // With 0 bits, no nonce at all.
        assertNull(ProofOfWork.committedTarget(ProofOfWork.mine(event, 0).tags))
        assertFailsWith<IllegalArgumentException> { ProofOfWork.mine(event, ProofOfWork.MAX_MINED_BITS + 1) }
    }

    @Test
    fun withoutACommittedTargetTheWorkDoesNotCount() = runTest {
        val keys = IdentityKeys.generate()
        val base = UnsignedEvent.create(keys.publicKey, 1059, 1_780_000_000, listOf(listOf("p", "b".repeat(64))), "[EXEMPLE]")
        val mined = ProofOfWork.mine(base, 8)
        val target = mined.tags.single { it.first() == ProofOfWork.TAG }
        // The same work, but the tag lost its third entry, or names a lower target.
        val bare = LocalEventSigner(keys).sign(mined.copy(tags = mined.tags.map { if (it === target) it.take(2) else it }))
        assertNull(ProofOfWork.committedTarget(bare.tags))
        assertFalse(ProofOfWork.satisfies(bare, 1))
        val twice = LocalEventSigner(keys).sign(mined.copy(tags = mined.tags + listOf(target)))
        assertNull(ProofOfWork.committedTarget(twice.tags))
    }

    @Test
    fun theMinerWorksOnLongContentsAndNonAsciiTags() = runTest {
        val keys = IdentityKeys.generate()
        val event = UnsignedEvent.create(keys.publicKey, 14, 1_780_000_000, listOf(listOf("subject", "[EXEMPLE] réclamation « délai »")), "é".repeat(5_000) + "\"\n\\")
        val mined = ProofOfWork.mine(event, 10)
        assertTrue(ProofOfWork.difficulty(mined.idHex()) >= 10)
        assertEquals("[EXEMPLE] réclamation « délai »", mined.tags.first()[1])
    }
}
