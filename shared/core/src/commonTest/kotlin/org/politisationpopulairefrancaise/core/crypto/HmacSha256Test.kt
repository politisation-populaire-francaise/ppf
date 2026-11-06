package org.politisationpopulairefrancaise.core.crypto

import kotlin.test.Test
import kotlin.test.assertEquals

/** HMAC-SHA-256 test cases 1 to 7 of RFC 4231 (section 4). */
class HmacSha256Test {

    private data class Vector(val key: String, val data: String, val mac: String, val truncated: Boolean = false)

    private val vectors = listOf(
        Vector(
            "0b".repeat(20),
            "4869205468657265",
            "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7",
        ),
        Vector(
            "4a656665",
            "7768617420646f2079612077616e7420666f72206e6f7468696e673f",
            "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
        ),
        Vector(
            "aa".repeat(20),
            "dd".repeat(50),
            "773ea91e36800e46854db8ebd09181a72959098b3ef8c122d9635514ced565fe",
        ),
        Vector(
            "0102030405060708090a0b0c0d0e0f10111213141516171819",
            "cd".repeat(50),
            "82558a389a443c0ea4cc819899f2083a85f0faa3e578f8077a2e3ff46729665b",
        ),
        Vector(
            "0c".repeat(20),
            "546573742057697468205472756e636174696f6e",
            "a3b6167473100ee06e0c796c2955552b",
            truncated = true,
        ),
        Vector(
            "aa".repeat(131),
            "54657374205573696e67204c6172676572205468616e20426c6f636b2d53697a65204b6579202d2048617368204b6579204669727374",
            "60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54",
        ),
        Vector(
            "aa".repeat(131),
            "5468697320697320612074657374207573696e672061206c6172676572207468616e20626c6f636b2d73697a65206b657920616e642061206c6172676572207468616e20626c6f636b2d73697a6520646174612e20546865206b6579206e6565647320746f20626520686173686564206265666f7265206265696e6720757365642062792074686520484d414320616c676f726974686d2e",
            "9b09ffa71b942fcb27635fbcd5b0e944bfdc63644f0713938a7f51535c3a35e2",
        ),
    )

    @Test
    fun rfc4231Vectors() {
        vectors.forEachIndexed { index, vector ->
            val mac = HmacSha256.compute(vector.key.hexToByteArray(), vector.data.hexToByteArray()).toHexString()
            val actual = if (vector.truncated) mac.take(32) else mac
            assertEquals(vector.mac, actual, "test case ${index + 1}")
        }
    }

    @Test
    fun severalMessagesAreTheirConcatenation() {
        val key = "4a656665".hexToByteArray()
        val whole = HmacSha256.compute(key, "what do ya want for nothing?".encodeToByteArray())
        val parts = HmacSha256.compute(key, "what do ya ".encodeToByteArray(), "want for ".encodeToByteArray(), "nothing?".encodeToByteArray())
        assertEquals(whole.toHexString(), parts.toHexString())
        assertEquals(32, whole.size)
    }
}
