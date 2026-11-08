package org.politisationpopulairefrancaise.core.archive

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Checks of a proof against a Bitcoin header (D-111), with the genesis block of 3 January 2009,
 * whose header is public and fixed forever (python-opentimestamps tests use it too). No network.
 */
class BitcoinHeaderTest {

    private val genesisHex =
        "01000000" +
            "0000000000000000000000000000000000000000000000000000000000000000" +
            "3ba3edfd7a7b12b27ac72c3e67768f617fc81bc3888a51323a9fb8aa4b1e5e4a" +
            "29ab5f49" + "ffff001d" + "1dac2b7c"
    private val genesisHash = "000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f"

    private fun genesis() = BitcoinHeader(genesisHex.hexToByteArray())

    @Test
    fun theGenesisHeaderIsReadAsBitcoinDefinesIt() {
        val header = genesis()
        assertEquals(genesisHash, header.hashHex)
        assertEquals(1_231_006_505L, header.time) // 2009-01-03 18:15:05 UTC
        assertEquals(0x1d00ffffL, header.bits)
        assertTrue(header.meetsItsTarget())
        assertContentEquals("3ba3edfd7a7b12b27ac72c3e67768f617fc81bc3888a51323a9fb8aa4b1e5e4a".hexToByteArray(), header.merkleRoot)
        assertFailsWith<OtsFormatError> { BitcoinHeader(ByteArray(79)) }
    }

    @Test
    fun aProofEndingInTheGenesisMerkleRootIsVerified() {
        val file = OtsFile.forSha256(genesis().merkleRoot)
        file.timestamp.addAttestation(OtsAttestation.BitcoinBlockHeader(0))
        val read = OtsFile.read(file.toByteArray())
        val (height, message) = OtsBitcoinVerifier.bitcoinAttestations(read.timestamp).single()
        assertEquals(0L, height)
        assertEquals(BitcoinCheck.VERIFIED, OtsBitcoinVerifier.check(message, genesisHash.uppercase(), genesis(), genesis()))
    }

    @Test
    fun aFalseProofAWrongHeaderOrDisagreeingSourcesAreNeverVerified() {
        val root = genesis().merkleRoot
        val otherMessage = root.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertEquals(BitcoinCheck.WRONG_MERKLE_ROOT, OtsBitcoinVerifier.check(otherMessage, genesisHash, genesis(), genesis()))
        // A header changed by one bit no longer hashes to the block, nor meets its target.
        val forged = BitcoinHeader(genesisHex.hexToByteArray().also { it[79] = (it[79].toInt() xor 1).toByte() })
        assertFalse(forged.meetsItsTarget())
        assertEquals(BitcoinCheck.WRONG_BLOCK, OtsBitcoinVerifier.check(root, genesisHash, forged, forged))
        assertEquals(BitcoinCheck.SOURCES_DISAGREE, OtsBitcoinVerifier.check(root, genesisHash, genesis(), forged))
        // A forged header announced with its own hash is still refused: it was not mined.
        assertEquals(BitcoinCheck.NOT_MINED, OtsBitcoinVerifier.check(root, forged.hashHex, forged, forged))
    }
}
