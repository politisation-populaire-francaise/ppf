package org.politisationpopulairefrancaise.core.archive

import fr.acinq.bitcoin.crypto.Digest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Our `.ots` reader against the example proofs of the official client (D-111): every valid file is
 * read and written back byte for byte, its digest is the hash of the file it stamps, its
 * attestations are where the official tools put them, and every invalid file is refused.
 */
class OtsExamplesTest {

    private val valid = OtsExamples.files.keys.filter { it.endsWith(".ots") && !it.startsWith("invalid/") }

    private fun read(name: String) = OtsFile.read(OtsExamples.bytes(name))

    private fun bitcoinHeights(name: String) =
        read(name).timestamp.allAttestations().mapNotNull { (it.second as? OtsAttestation.BitcoinBlockHeader)?.height }.toSet()

    private fun pendingCalendars(name: String) =
        read(name).timestamp.allAttestations().mapNotNull { (it.second as? OtsAttestation.Pending)?.calendarUri }.toSet()

    @Test
    fun everyValidExampleIsReadAndWrittenBackByteForByte() {
        assertTrue(valid.size >= 10, "the examples are there")
        valid.forEach { name ->
            val bytes = OtsExamples.bytes(name)
            assertContentEquals(bytes, OtsFile.read(bytes).toByteArray(), name)
        }
    }

    @Test
    fun eachDigestIsTheHashOfTheStampedFile() {
        valid.forEach { name ->
            val stamped = name.removeSuffix(".ots")
            val content = OtsExamples.files[stamped]?.hexToByteArray() ?: return@forEach
            val file = read(name)
            val expected = when (file.fileHash) {
                OtsOperation.Sha256 -> Digest.sha256().hash(content)
                OtsOperation.Sha1 -> Digest.sha1().hash(content)
                OtsOperation.Ripemd160 -> Digest.ripemd160().hash(content)
                else -> error("unexpected file hash in $name")
            }
            assertContentEquals(expected, file.digest, name)
        }
    }

    @Test
    fun completeProofsEndInTheirBitcoinBlocks() {
        assertEquals(setOf(358_391L), bitcoinHeights("hello-world.txt.ots"))
        assertTrue(read("hello-world.txt.ots").timestamp.isComplete)
        assertEquals(setOf(129_405L), bitcoinHeights("empty.ots"))
        assertTrue(455_605L in bitcoinHeights("different-blockchains.txt.ots"))
    }

    @Test
    fun aBadStampPointsToTheRightBlockWithTheWrongMessage() {
        // Same block as hello-world, but its proof does not lead to that block's Merkle root.
        val good = OtsBitcoinVerifier.bitcoinAttestations(read("hello-world.txt.ots").timestamp).single()
        val bad = OtsBitcoinVerifier.bitcoinAttestations(read("bad-stamp.txt.ots").timestamp).single()
        assertEquals(good.first, bad.first)
        assertFalse(good.second.contentEquals(bad.second))
    }

    @Test
    fun pendingProofsNameTheirCalendars() {
        val incomplete = pendingCalendars("incomplete.txt.ots")
        assertTrue(incomplete.isNotEmpty())
        assertFalse(read("incomplete.txt.ots").timestamp.isComplete)
        assertTrue(pendingCalendars("two-calendars.txt.ots").size >= 2)
        (1..3).forEach { assertTrue(pendingCalendars("merkle$it.txt.ots").isNotEmpty(), "merkle$it") }
        (incomplete + pendingCalendars("two-calendars.txt.ots")).forEach { assertTrue(it.startsWith("https://"), it) }
    }

    @Test
    fun unknownNotariesAreKeptNotTrusted() {
        val unknown = read("unknown-notary.txt.ots").timestamp.allAttestations().map { it.second }
        assertNotNull(unknown.firstOrNull { it is OtsAttestation.Unknown })
        val mixed = read("known-and-unknown-notary.txt.ots").timestamp.allAttestations().map { it.second }
        assertTrue(mixed.any { it is OtsAttestation.Unknown } && mixed.any { it !is OtsAttestation.Unknown })
        // The Ethereum branch of different-blockchains is kept as an unknown notary.
        assertTrue(read("different-blockchains.txt.ots").timestamp.allAttestations().any { it.second is OtsAttestation.Unknown })
    }

    @Test
    fun everyInvalidExampleIsRefused() {
        val invalid = OtsExamples.files.keys.filter { it.startsWith("invalid/") }
        assertEquals(3, invalid.size)
        invalid.forEach { name -> assertFailsWith<OtsFormatError>(name) { OtsFile.read(OtsExamples.bytes(name)) } }
    }
}
