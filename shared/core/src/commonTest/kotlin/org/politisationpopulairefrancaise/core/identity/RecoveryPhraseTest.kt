package org.politisationpopulairefrancaise.core.identity

import fr.acinq.bitcoin.Crypto
import org.politisationpopulairefrancaise.core.text.normalizeNfkd
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class RecoveryPhraseTest {

    @Test
    fun frenchWordlistIsTheOfficialOne() {
        val words = PhraseLanguage.FRENCH.words
        assertEquals(2048, words.size)
        assertEquals(2048, words.toSet().size)
        // SHA-256 of https://github.com/bitcoin/bips/blob/master/bip-0039/french.txt (one word per line).
        val digest = Crypto.sha256((words.joinToString("\n") + "\n").encodeToByteArray()).toHexString()
        assertEquals("ebc3959ab7801a1df6bac4fa7d970652f1df76b683cd2f4003c941c63d517e59", digest)
        assertEquals("abaisser", words.first())
        assertEquals("zoologie", words.last())
    }

    @Test
    fun englishWordlistIsTheOfficialOne() {
        val words = PhraseLanguage.ENGLISH.words
        assertEquals(2048, words.size)
        val digest = Crypto.sha256((words.joinToString("\n") + "\n").encodeToByteArray()).toHexString()
        assertEquals("2f5eed53a4727b4bf8880d8f3f199efc90e58503646d9ff8eff3a2ed3b24dbda", digest)
    }

    // NIP-06 test vectors (https://github.com/nostr-protocol/nips/blob/master/06.md).
    @Test
    fun nip06TwelveWordVector() {
        val phrase = RecoveryPhrase.parse("leader monkey parrot ring guide accident before fence cannon height naive bean")
        assertEquals(PhraseLanguage.ENGLISH, phrase.language)
        val keys = phrase.deriveKeys()
        assertEquals("7f7ff03d123792d6ac594bfa67bf6d0c0ab55b6b1fdb6249303fe861f1ccba9a", keys.secretKey().toHexString())
        assertEquals("nsec10allq0gjx7fddtzef0ax00mdps9t2kmtrldkyjfs8l5xruwvh2dq0lhhkp", keys.nsec)
        assertEquals("17162c921dc4d2518f9a101db33695df1afb56ab82f5ff3e5da6eec3ca5cd917", keys.publicKey.hex)
        assertEquals("npub1zutzeysacnf9rru6zqwmxd54mud0k44tst6l70ja5mhv8jjumytsd2x7nu", keys.publicKey.npub)
    }

    @Test
    fun nip06TwentyFourWordVector() {
        val phrase = RecoveryPhrase.parse(
            "what bleak badge arrange retreat wolf trade produce cricket blur garlic valid proud rude strong choose busy staff weather area salt hollow arm fade",
        )
        val keys = phrase.deriveKeys()
        assertEquals("c15d739894c81a2fcfd3a2df85a0d2c0dbc47a280d092799f144d73d7ae78add", keys.secretKey().toHexString())
        assertEquals("nsec1c9wh8xy5eqdzln7n5t0ctgxjcrdug73gp5yj0x03gntn67h83twssdfhel", keys.nsec)
        assertEquals("npub16sdj9zv4f8sl85e45vgq9n7nsgt5qphpvmf7vk8r5hhvmdjxx4es8rq74h", keys.publicKey.npub)
    }

    @Test
    fun generatedFrenchPhraseRoundTrips() {
        val phrase = RecoveryPhrase.generate()
        assertEquals(PhraseLanguage.FRENCH, phrase.language)
        assertEquals(12, phrase.words.size)
        assertTrue(phrase.words.all { it in PhraseLanguage.FRENCH.words })

        val parsed = RecoveryPhrase.parse(phrase.text)
        assertEquals(phrase, parsed)
        assertEquals(phrase.deriveKeys().publicKey, parsed.deriveKeys().publicKey)
        assertNotEquals(RecoveryPhrase.generate(), phrase)
        assertNotEquals(phrase.deriveKeys().publicKey, phrase.deriveKeys(account = 1).publicKey)
    }

    @Test
    fun parsingIsTolerantToSpacingCaseAndAccentForm() {
        val phrase = RecoveryPhrase.generate()
        val messy = phrase.words.joinToString("  \n") { it.uppercase() } + " "
        assertEquals(phrase, RecoveryPhrase.parse(messy))

        // Accents typed in their composed form (as a keyboard produces them) must match the
        // decomposed official words. Find a generated phrase with at least one accent.
        var attempts = 0
        while (true) {
            val candidate = RecoveryPhrase.generate()
            val composed = recompose(candidate.text)
            if (composed != candidate.text) {
                assertEquals(candidate, RecoveryPhrase.parse(composed))
                break
            }
            if (++attempts > 200) fail("No accented word in 200 generated French phrases")
        }
    }

    @Test
    fun invalidPhrasesAreRefusedWithAReason() {
        val tooShort = assertFailsWith<InvalidRecoveryPhraseException> { RecoveryPhrase.parse("abandon abandon") }
        assertEquals(InvalidRecoveryPhraseException.Reason.WORD_COUNT, tooShort.reason)

        val badChecksum = assertFailsWith<InvalidRecoveryPhraseException> { RecoveryPhrase.parse("abandon ".repeat(12)) }
        assertEquals(InvalidRecoveryPhraseException.Reason.CHECKSUM, badChecksum.reason)

        val unknown = assertFailsWith<InvalidRecoveryPhraseException> {
            RecoveryPhrase.parse("leader monkey parrot ring guide accident before fence cannon height naive xyzzy")
        }
        assertEquals(InvalidRecoveryPhraseException.Reason.UNKNOWN_WORDS, unknown.reason)
        assertEquals(listOf("xyzzy"), unknown.unknownWords)

        // Forcing the wrong language reports the words that do not belong to it.
        val wrongLanguage = assertFailsWith<InvalidRecoveryPhraseException> {
            RecoveryPhrase.parse("leader monkey parrot ring guide accident before fence cannon height naive bean", PhraseLanguage.FRENCH)
        }
        assertEquals(InvalidRecoveryPhraseException.Reason.UNKNOWN_WORDS, wrongLanguage.reason)
        assertTrue("leader" in wrongLanguage.unknownWords)
    }

    @Test
    fun phraseNeverLeaksThroughToString() {
        val phrase = RecoveryPhrase.generate()
        val printed = phrase.toString()
        assertTrue(phrase.words.none { it in printed })
    }

    @Test
    fun normalizationProducesDecomposedAccents() {
        assertEquals("été", normalizeNfkd("été"))
        assertEquals("abc", normalizeNfkd("abc"))
    }

    private fun recompose(text: String): String =
        text
            .replace("é", "é").replace("è", "è").replace("ê", "ê")
            .replace("à", "à").replace("â", "â")
            .replace("î", "î").replace("ï", "ï")
            .replace("ô", "ô").replace("ù", "ù").replace("û", "û")
            .replace("ç", "ç")
}
