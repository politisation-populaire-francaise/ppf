package org.politisationpopulairefrancaise.core.agir.poster

/**
 * Base45 (RFC 9285): bytes written with the 45 characters of the alphanumeric mode of QR codes
 * (digits, capital letters, space and `$%*+-./:`). Two bytes become three characters, a last single
 * byte two. A QR code stores these characters in 5.5 bits each, against 8 bits per character of the
 * byte mode, so a compressed event takes about a third less room than in Base64 (AG-61, the codes
 * of the posters). The same encoding carries the European COVID certificates.
 */
object Base45 {
    const val ALPHABET: String = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size + 1) / 2 * 3)
        var index = 0
        while (index + 1 < bytes.size) {
            val value = (bytes[index].toInt() and 0xFF) * 256 + (bytes[index + 1].toInt() and 0xFF)
            out.append(ALPHABET[value % 45]).append(ALPHABET[(value / 45) % 45]).append(ALPHABET[value / 2025])
            index += 2
        }
        if (index < bytes.size) {
            val value = bytes[index].toInt() and 0xFF
            out.append(ALPHABET[value % 45]).append(ALPHABET[value / 45])
        }
        return out.toString()
    }

    /** The bytes of [text], or null when it is not valid Base45 (unknown character, impossible length or value). */
    fun decode(text: String): ByteArray? {
        if (text.length % 3 == 1) return null
        val out = ByteArray(text.length / 3 * 2 + if (text.length % 3 == 2) 1 else 0)
        var written = 0
        var index = 0
        while (index < text.length) {
            val chunk = minOf(3, text.length - index)
            var value = 0
            var weight = 1
            for (offset in 0 until chunk) {
                val digit = ALPHABET.indexOf(text[index + offset])
                if (digit < 0) return null
                value += digit * weight
                weight *= 45
            }
            if (chunk == 3) {
                if (value > 0xFFFF) return null
                out[written++] = (value shr 8).toByte()
                out[written++] = (value and 0xFF).toByte()
            } else {
                if (value > 0xFF) return null
                out[written++] = value.toByte()
            }
            index += chunk
        }
        return out
    }
}
