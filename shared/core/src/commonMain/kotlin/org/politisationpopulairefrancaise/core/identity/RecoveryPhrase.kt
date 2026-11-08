package org.politisationpopulairefrancaise.core.identity

import fr.acinq.bitcoin.DeterministicWallet
import fr.acinq.bitcoin.MnemonicCode
import org.politisationpopulairefrancaise.core.crypto.secureRandomBytes
import org.politisationpopulairefrancaise.core.text.normalizeNfkd

/** Languages of the official BIP-39 wordlists accepted for recovery phrases. French is the default. */
enum class PhraseLanguage(val code: String) {
    FRENCH("fr"),
    ENGLISH("en"),
    ;

    /** The 2048 official words, NFKD-normalized, in wordlist order. */
    val words: List<String> by lazy {
        when (this) {
            FRENCH -> BIP39_FRENCH_TEXT.lines().map { normalizeNfkd(it.trim()) }.filter { it.isNotEmpty() }
            ENGLISH -> MnemonicCode.englishWordlist
        }
    }

    internal val wordSet: Set<String> by lazy { words.toSet() }
}

/** Why a typed recovery phrase was refused; the user interface turns it into a precise message. */
class InvalidRecoveryPhraseException(
    val reason: Reason,
    /** Words that are not in the wordlist (only for [Reason.UNKNOWN_WORDS]). */
    val unknownWords: List<String> = emptyList(),
) : IllegalArgumentException("Invalid recovery phrase: $reason ${unknownWords.joinToString(" ")}".trim()) {
    enum class Reason { WORD_COUNT, UNKNOWN_WORDS, CHECKSUM }
}

/** The phrase is valid in several languages, which lead to different keys: the user must pick one. */
class AmbiguousPhraseLanguageException(val candidates: List<PhraseLanguage>) :
    IllegalArgumentException("Recovery phrase valid in several languages: ${candidates.joinToString { it.code }}")

/**
 * The recovery phrase of an identity: 12 words from an official BIP-39 wordlist, from which the
 * key pair is derived (BIP-39 seed, then BIP-32 path m/44'/1237'/account'/0/0 as in NIP-06).
 *
 * Writing these words down is the only way to recover an identity on another device, since no
 * server knows the key. The words are never logged nor included in [toString].
 */
class RecoveryPhrase private constructor(val words: List<String>, val language: PhraseLanguage) {

    /** Words separated by single spaces, the form to show and to type. */
    val text: String get() = words.joinToString(" ")

    /** Derives the identity keys; account 0 is the identity, higher accounts are reserved. */
    fun deriveKeys(account: Long = 0): IdentityKeys = derive(account, branch = 0)

    /**
     * Derives the recovery key of the account (S-146, D-134, docs/integration/compte-compromis.md):
     * path m/44'/1237'/account'/1/0, the BIP-32 branch "1" that NIP-06 does not use, so that no
     * Nostr client takes it for an account. It signs the compromise notice only ([AccountRecovery]):
     * the device publishes the commitment naming its public key, then destroys it; it comes back only
     * when the phrase is typed again.
     */
    fun deriveRecoveryKeys(account: Long = 0): IdentityKeys = derive(account, branch = 1)

    private fun derive(account: Long, branch: Int): IdentityKeys {
        require(account >= 0) { "account must be positive" }
        val seed = MnemonicCode.toSeed(words, passphrase = "")
        val master = DeterministicWallet.generate(seed)
        val child = master.derivePrivateKey("m/44'/1237'/$account'/$branch/0")
        return IdentityKeys.fromSecretKey(child.privateKey.value.toByteArray())
    }

    override fun equals(other: Any?): Boolean =
        other is RecoveryPhrase && other.language == language && other.words == words

    override fun hashCode(): Int = 31 * language.hashCode() + words.hashCode()

    override fun toString(): String = "RecoveryPhrase(${language.code}, ${words.size} words)"

    companion object {
        const val DEFAULT_WORD_COUNT: Int = 12
        private val ALLOWED_WORD_COUNTS = setOf(12, 15, 18, 21, 24)
        private val WHITESPACE = Regex("\\s+")

        /** Generates a new 12-word phrase (128 bits of entropy) from the secure random generator. */
        fun generate(language: PhraseLanguage = PhraseLanguage.FRENCH): RecoveryPhrase {
            val entropy = secureRandomBytes(16)
            return RecoveryPhrase(MnemonicCode.toMnemonics(entropy, language.words), language)
        }

        /**
         * Parses a phrase typed by a user: any spacing, any letter case, accents composed or not.
         * When [language] is null, it is detected (a phrase almost never validates in two lists).
         * @throws InvalidRecoveryPhraseException with the reason and the unknown words, if any
         * @throws AmbiguousPhraseLanguageException if several languages validate the phrase
         */
        fun parse(text: String, language: PhraseLanguage? = null): RecoveryPhrase {
            val words = normalizeNfkd(text).lowercase().split(WHITESPACE).filter { it.isNotEmpty() }
            if (words.size !in ALLOWED_WORD_COUNTS) {
                throw InvalidRecoveryPhraseException(InvalidRecoveryPhraseException.Reason.WORD_COUNT)
            }
            val languages = language?.let { listOf(it) } ?: PhraseLanguage.entries
            val knownIn = languages.filter { lang -> words.all { it in lang.wordSet } }
            if (knownIn.isEmpty()) {
                val closest = languages.minBy { lang -> words.count { it !in lang.wordSet } }
                throw InvalidRecoveryPhraseException(
                    InvalidRecoveryPhraseException.Reason.UNKNOWN_WORDS,
                    words.filter { it !in closest.wordSet }.distinct(),
                )
            }
            val valid = knownIn.filter { lang -> runCatching { MnemonicCode.validate(words, lang.words) }.isSuccess }
            return when (valid.size) {
                0 -> throw InvalidRecoveryPhraseException(InvalidRecoveryPhraseException.Reason.CHECKSUM)
                1 -> RecoveryPhrase(words, valid.single())
                else -> throw AmbiguousPhraseLanguageException(valid)
            }
        }
    }
}
