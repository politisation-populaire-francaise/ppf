package org.politisationpopulairefrancaise.core.contact

import org.politisationpopulairefrancaise.core.crypto.secureRandomBytes

/**
 * Crockford's base 32 (https://www.crockford.com/base32.html), the alphabet of case numbers and secret
 * codes (docs/integration/contact-reclamations.md, section 4): digits and capital letters without I, L, O
 * and U, so that nothing read aloud or copied by hand can be confused. Reading is forgiving: case ignored,
 * `O` read as `0`, `I` and `L` read as `1`; spaces and dashes are ignored.
 */
object Crockford32 {
    const val ALPHABET: String = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    /** The value (0 to 31) of one typed symbol, with the equivalences of Crockford; -1 for anything else (`U` included). */
    fun valueOf(symbol: Char): Int = when (val upper = symbol.uppercaseChar()) {
        'O' -> 0
        'I', 'L' -> 1
        else -> ALPHABET.indexOf(upper)
    }

    /** [count] symbols drawn from the secure random generator: one byte per symbol, its low five bits (uniform, 256 = 8 x 32). */
    fun random(count: Int): String {
        require(count >= 1) { "At least one symbol" }
        val bytes = secureRandomBytes(count)
        try {
            return buildString(count) { bytes.forEach { append(ALPHABET[it.toInt() and 31]) } }
        } finally {
            bytes.fill(0)
        }
    }

    /**
     * The canonical symbols of what a person typed: separators (whitespace, dashes of any kind) dropped,
     * every other character mapped to its canonical symbol; null as soon as one character is not a symbol.
     */
    fun canonical(input: String): String? = buildString(input.length) {
        for (char in input) {
            if (char.isWhitespace() || char in SEPARATORS) continue
            val value = valueOf(char)
            if (value < 0) return null
            append(ALPHABET[value])
        }
    }

    /** True when [text] is made of canonical symbols only. */
    fun isCanonical(text: String): Boolean = text.all { it in ALPHABET }

    /** Hyphen-minus and the Unicode dashes a phone keyboard or a copy may produce. */
    private const val SEPARATORS: String = "-‐‑‒–—―−"
}

/**
 * The number of a case: 10 random symbols of base 32 (50 bits), shown `PPF-XXXXX-XXXXX`, never a counter.
 * Drawn by the device; if the server already knows it, it answers 409 and the device draws another one.
 * The [display] form is the canonical one: it is the identifier of the case on the server and the number
 * the derivations of [CaseSecrets] use.
 */
class CaseNumber private constructor(
    /** The 10 canonical symbols, without prefix nor dash. */
    val symbols: String,
) {
    /** `PPF-XXXXX-XXXXX`. */
    val display: String = "$PREFIX-${symbols.substring(0, 5)}-${symbols.substring(5)}"

    override fun equals(other: Any?): Boolean = other is CaseNumber && other.symbols == symbols
    override fun hashCode(): Int = symbols.hashCode()
    override fun toString(): String = display

    companion object {
        const val PREFIX: String = "PPF"
        const val SYMBOLS: Int = 10

        /** Length of the [display] form. */
        const val DISPLAY_LENGTH: Int = 15

        /** A new random number. */
        fun generate(): CaseNumber = CaseNumber(Crockford32.random(SYMBOLS))

        /**
         * Reads a number typed by a person: `PPF-ABCDE-12345`, `ppf abcde 12345`, `abcde12345` (the prefix is
         * optional; with it, 13 symbols, without it 10), with the equivalences of Crockford. Null otherwise.
         */
        fun parse(input: String): CaseNumber? {
            val symbols = Crockford32.canonical(input) ?: return null
            return when (symbols.length) {
                SYMBOLS -> CaseNumber(symbols)
                SYMBOLS + PREFIX.length -> if (symbols.startsWith(PREFIX)) CaseNumber(symbols.substring(PREFIX.length)) else null
                else -> null
            }
        }

        /** Strict reading of the [display] form only, as the API and the database use it; null for anything else. */
        fun fromDisplay(text: String): CaseNumber? {
            if (text.length != DISPLAY_LENGTH || !text.startsWith("$PREFIX-") || text[9] != '-') return null
            val symbols = text.substring(4, 9) + text.substring(10)
            return if (Crockford32.isCanonical(symbols)) CaseNumber(symbols) else null
        }
    }
}

/**
 * The secret code of a case: 20 random symbols of base 32 (100 bits), shown in five groups of four. It is
 * shown **once**, at the creation, and kept on the device in « Mes échanges »; the team cannot find it
 * again. Typing ignores case, spaces and dashes, with the equivalences of Crockford. [toString] never
 * prints it.
 */
class CaseCode private constructor(private val symbols: String) {
    /** The normalised ASCII form (20 canonical symbols), the input of the derivations of [CaseSecrets]. */
    fun normalized(): String = symbols

    /** Five groups of four symbols separated by dashes, for the one screen that shows it. */
    fun display(): String = symbols.chunked(GROUP).joinToString("-")

    override fun equals(other: Any?): Boolean = other is CaseCode && other.symbols == symbols
    override fun hashCode(): Int = symbols.hashCode()
    override fun toString(): String = "CaseCode(hidden)"

    companion object {
        const val SYMBOLS: Int = 20
        const val GROUP: Int = 4

        /** A new random code. */
        fun generate(): CaseCode = CaseCode(Crockford32.random(SYMBOLS))

        /** Reads a code typed by a person; null unless it has exactly 20 symbols once separators are dropped. */
        fun parse(input: String): CaseCode? = Crockford32.canonical(input)?.takeIf { it.length == SYMBOLS }?.let(::CaseCode)
    }
}
