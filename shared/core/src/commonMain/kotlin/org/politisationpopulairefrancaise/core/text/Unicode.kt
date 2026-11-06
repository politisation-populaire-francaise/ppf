package org.politisationpopulairefrancaise.core.text

/**
 * Unicode compatibility decomposition (NFKD). BIP-39 requires it before comparing or hashing
 * recovery words: "académie" typed with a precomposed "é" must match the official wordlist,
 * which stores "e" followed by a combining accent.
 */
expect fun normalizeNfkd(text: String): String
