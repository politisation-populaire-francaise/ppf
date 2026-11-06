package org.politisationpopulairefrancaise.server.groups

import java.text.Normalizer
import java.util.Locale

/**
 * The normalized form of a group name or territory, used to detect duplicates (D-028: one name
 * per territory) and to search the directory. Two texts are the same once:
 * - accents and other diacritics are removed (Unicode decomposition, then the combining marks dropped);
 * - every character that is neither a letter nor a digit (punctuation, symbols, emoji, spaces) is
 *   replaced by a space;
 * - runs of spaces are collapsed into one and the ends trimmed;
 * - the result is lowercased (root locale, so that the outcome does not depend on the server's language).
 *
 * "Collectif  Éducation - Lyon 3e" and "collectif education lyon 3e" normalize to the same text.
 * A text made only of punctuation normalizes to the empty string.
 */
object GroupNames {
    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        val out = StringBuilder(decomposed.length)
        var pendingSpace = false
        var index = 0
        while (index < decomposed.length) {
            val codePoint = decomposed.codePointAt(index)
            index += Character.charCount(codePoint)
            when (Character.getType(codePoint).toByte()) {
                Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK -> continue
            }
            if (Character.isLetterOrDigit(codePoint)) {
                if (pendingSpace && out.isNotEmpty()) out.append(' ')
                pendingSpace = false
                out.appendCodePoint(codePoint)
            } else {
                pendingSpace = true
            }
        }
        return out.toString().lowercase(Locale.ROOT)
    }
}
