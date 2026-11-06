package org.politisationpopulairefrancaise.core.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** SHA-256 test cases of RFC 5869, appendix A (A.1 to A.3). */
class HkdfSha256Test {

    @Test
    fun testCase1() {
        val ikm = "0b".repeat(22).hexToByteArray()
        val salt = "000102030405060708090a0b0c".hexToByteArray()
        val info = "f0f1f2f3f4f5f6f7f8f9".hexToByteArray()
        val prk = HkdfSha256.extract(salt, ikm)
        assertEquals("077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5", prk.toHexString())
        assertEquals(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
            HkdfSha256.expand(prk, info, 42).toHexString(),
        )
        assertEquals(HkdfSha256.expand(prk, info, 42).toHexString(), HkdfSha256.derive(ikm, salt, info, 42).toHexString())
    }

    @Test
    fun testCase2WithLongerInputsAndOutput() {
        val ikm = (0x00..0x4f).map { it.toByte() }.toByteArray()
        val salt = (0x60..0xaf).map { it.toByte() }.toByteArray()
        val info = (0xb0..0xff).map { it.toByte() }.toByteArray()
        val prk = HkdfSha256.extract(salt, ikm)
        assertEquals("06a6b88c5853361a06104c9ceb35b45cef760014904671014a193f40c15fc244", prk.toHexString())
        assertEquals(
            "b11e398dc80327a1c8e7f78c596a49344f012eda2d4efad8a050cc4c19afa97c" +
                "59045a99cac7827271cb41c65e590e09da3275600c2f09b8367793a9aca3db71" +
                "cc30c58179ec3e87c14c01d5c1f3434f1d87",
            HkdfSha256.expand(prk, info, 82).toHexString(),
        )
    }

    @Test
    fun testCase3WithEmptySaltAndInfo() {
        val ikm = "0b".repeat(22).hexToByteArray()
        val prk = HkdfSha256.extract(ByteArray(0), ikm)
        assertEquals("19ef24a32c717b167f33a91d6f648bdf96596776afdb6377ac434c1c293ccb04", prk.toHexString())
        assertEquals(
            "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8",
            HkdfSha256.expand(prk, ByteArray(0), 42).toHexString(),
        )
    }

    @Test
    fun outputLengthIsBounded() {
        val prk = ByteArray(32)
        assertEquals(1, HkdfSha256.expand(prk, ByteArray(0), 1).size)
        assertEquals(HkdfSha256.MAX_OUTPUT_SIZE, HkdfSha256.expand(prk, ByteArray(0), HkdfSha256.MAX_OUTPUT_SIZE).size)
        assertFailsWith<IllegalArgumentException> { HkdfSha256.expand(prk, ByteArray(0), 0) }
        assertFailsWith<IllegalArgumentException> { HkdfSha256.expand(prk, ByteArray(0), HkdfSha256.MAX_OUTPUT_SIZE + 1) }
    }
}
