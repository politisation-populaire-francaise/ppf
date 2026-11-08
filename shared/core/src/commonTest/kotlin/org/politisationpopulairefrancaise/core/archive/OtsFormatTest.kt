package org.politisationpopulairefrancaise.core.archive

import fr.acinq.bitcoin.crypto.Digest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The `.ots` format written by us (D-111), against the byte values of python-opentimestamps
 * (`opentimestamps/core`, read on 7 November 2026). The example files of the official client will
 * be added to these tests once their download into the repository is approved.
 */
class OtsFormatTest {

    private fun h(value: String): ByteArray = value.hexToByteArray()
    private fun sha256(bytes: ByteArray): ByteArray = Digest.sha256().hash(bytes)

    @Test
    fun aPendingAttestationIsWrittenAsInTheOfficialTests() {
        // python-opentimestamps tests: PendingAttestation("foobar") = 00 83dfe30d2ef90c8e 07 06 666f6f626172.
        val node = OtsTimestamp(h("00"))
        node.addAttestation(OtsAttestation.Pending("foobar"))
        assertContentEquals(h("0083dfe30d2ef90c8e0706666f6f626172"), node.toByteArray())
        val back = OtsTimestamp.read(node.toByteArray(), h("00"))
        assertEquals("foobar", (back.attestations.single() as OtsAttestation.Pending).calendarUri)
    }

    @Test
    fun integersAreLeb128() {
        val writer = OtsWriter().apply {
            writeVarUInt(0)
            writeVarUInt(127)
            writeVarUInt(128)
            writeVarUInt(358_391)
        }
        assertContentEquals(h("007f8001f7ef15"), writer.toByteArray())
        val reader = OtsReader(writer.toByteArray())
        assertEquals(listOf(0L, 127L, 128L, 358_391L), List(4) { reader.readVarUInt() })
        reader.requireEnd()
    }

    @Test
    fun theHeaderIsTheOfficialOne() {
        val file = OtsFile.forSha256(ByteArray(32) { 1 })
        file.timestamp.addAttestation(OtsAttestation.BitcoinBlockHeader(358_391))
        val bytes = file.toByteArray()
        assertEquals(31, OtsFile.HEADER_MAGIC.size)
        assertContentEquals("\u0000OpenTimestamps\u0000\u0000Proof\u0000".encodeToByteArray(), bytes.copyOfRange(0, 23))
        assertContentEquals(h("bf89e2e884e89294" + "01" + "08"), bytes.copyOfRange(23, 33))
        // Then the digest, then 00 + Bitcoin tag + payload (varbytes of the varuint height).
        assertContentEquals(h("00" + "0588960d73d71901" + "03" + "f7ef15"), bytes.copyOfRange(65, bytes.size))
    }

    private fun batch(): OtsBatch {
        var n = 0
        val digests = List(3) { i -> sha256(byteArrayOf(i.toByte())) }
        return OtsBatch(digests) { ByteArray(OtsBatch.NONCE_SIZE) { (n * 16 + it).toByte() }.also { n++ } }
    }

    /** What a calendar answers: its own nonce and time, then a pending attestation. */
    private fun calendarProof(root: ByteArray, uri: String): OtsTimestamp {
        val proof = OtsTimestamp(root)
        proof.addOperation(OtsOperation.Prepend(h("6730f1a2")))
            .addOperation(OtsOperation.Append(h("0102030405060708")))
            .addOperation(OtsOperation.Sha256)
            .addAttestation(OtsAttestation.Pending(uri))
        return OtsTimestamp.read(proof.toByteArray(), root)
    }

    @Test
    fun aBatchGivesEachDigestItsOwnPathToTheSameRoot() {
        val batch = batch()
        // The calendar sees only the root, never a digest.
        val leaves = batch.files.mapIndexed { i, file -> sha256(file.digest + ByteArray(16) { (i * 16 + it).toByte() }) }
        val left = sha256(leaves[0] + leaves[1])
        assertContentEquals(sha256(left + leaves[2]), batch.root)
        batch.addCalendarProof(calendarProof(batch.root, "https://alice.btc.calendar.opentimestamps.org"))
        batch.addCalendarProof(calendarProof(batch.root, "https://finney.calendar.eternitywall.com"))
        val expected = sha256(h("6730f1a2") + batch.root + h("0102030405060708"))
        batch.files.forEach { file ->
            val read = OtsFile.read(file.toByteArray())
            assertContentEquals(file.toByteArray(), read.toByteArray(), "same bytes after a round trip")
            val attested = read.timestamp.allAttestations()
            assertEquals(2, attested.size, "one pending attestation per calendar (merged, not repeated)")
            attested.forEach { (message, attestation) ->
                assertIs<OtsAttestation.Pending>(attestation)
                assertContentEquals(expected, message)
            }
            assertFalse(read.timestamp.isComplete)
        }
    }

    @Test
    fun aBitcoinAttestationCompletesTheProof() {
        val batch = batch()
        val proof = OtsTimestamp(batch.root)
        proof.addOperation(OtsOperation.Sha256).addAttestation(OtsAttestation.BitcoinBlockHeader(912_095))
        batch.addCalendarProof(proof)
        val read = OtsFile.read(batch.files[1].toByteArray())
        assertTrue(read.timestamp.isComplete)
        val (message, attestation) = read.timestamp.allAttestations().single()
        assertEquals(912_095L, (attestation as OtsAttestation.BitcoinBlockHeader).height)
        assertContentEquals(sha256(batch.root), message, "the message to compare with the block's Merkle root")
    }

    @Test
    fun unknownAttestationsAndUncomputableBranchesAreKeptByteForByte() {
        val node = OtsTimestamp(h("aa"))
        node.addAttestation(OtsAttestation.Unknown(h("0102030405060708"), h("cafe")))
        node.addOperation(OtsOperation.Keccak256).addAttestation(OtsAttestation.Pending("https://example.invalid"))
        val bytes = node.toByteArray()
        val back = OtsTimestamp.read(bytes, h("aa"))
        assertContentEquals(bytes, back.toByteArray())
        val keccakBranch = back.operations.single().second
        assertEquals(null, keccakBranch.message, "Keccak-256 is never computed here")
        assertFalse(back.isComplete)
    }

    @Test
    fun malformedProofsAreRefused() {
        val good = OtsFile.forSha256(ByteArray(32)).also { it.timestamp.addAttestation(OtsAttestation.BitcoinBlockHeader(1)) }.toByteArray()
        OtsFile.read(good)
        assertFailsWith<OtsFormatError> { OtsFile.read(good + 0) } // bytes left over
        assertFailsWith<OtsFormatError> { OtsFile.read(good.copyOf(good.size - 1)) } // cut short
        assertFailsWith<OtsFormatError> { OtsFile.read(good.copyOf().also { it[0] = 1 }) } // not the header
        assertFailsWith<OtsFormatError> { OtsFile.read(good.copyOf().also { it[31] = 2 }) } // major version 2
        assertFailsWith<OtsFormatError> { OtsFile.read(good.copyOf().also { it[32] = 0x67 }) } // Keccak-256 as file hash
        assertFailsWith<OtsFormatError> { OtsTimestamp.read(h("42"), h("00")) } // unknown operation
        assertFailsWith<OtsFormatError> { OtsTimestamp.read(h("f000" + "0083dfe30d2ef90c8e0100"), h("00")) } // empty argument
        assertFailsWith<OtsFormatError> { OtsTimestamp.read(h("0083dfe30d2ef90c8e0706666f6f2062ff"), h("00")) } // bad calendar characters
        // 300 nested SHA-256 operations: deeper than the official limit of 256.
        assertFailsWith<OtsFormatError> { OtsTimestamp.read(ByteArray(300) { 0x08 } + h("00" + "0588960d73d71901" + "0101"), h("00")) }
        // 200 of them are fine.
        OtsTimestamp.read(ByteArray(200) { 0x08 } + h("00" + "0588960d73d71901" + "0101"), h("00"))
    }

    @Test
    fun theSameOperationTwiceUnderANodeIsRefused() {
        // ff 08 <00 bitcoin(1)> 08 <00 bitcoin(1)>: SHA-256 repeated.
        val branch = "00" + "0588960d73d71901" + "0101"
        assertFailsWith<OtsFormatError> { OtsTimestamp.read(h("ff08$branch" + "08$branch"), h("00")) }
    }
}
