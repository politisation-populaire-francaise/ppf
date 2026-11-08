package org.politisationpopulairefrancaise.core.identity

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the device keeps about the user's identity: the secret key and, while the user has not yet
 * checked their written copy or chose to keep it here, the recovery words.
 *
 * Since S-146 (D-134, docs/integration/compte-compromis.md) the phrase is **erased from the device
 * by default** once the user typed it back: a seized phone must not give the recovery key away.
 * [phraseVerified] tells that the user checked their copy; words still present after the check
 * were kept on purpose, after a warning (principle 10). [phraseCheckDueAt] is when the reminder
 * "Vérifier ma phrase" shows for a check postponed at signup. [pendingRecoveryKey] is the public
 * recovery key (lowercase hex) whose commitment (kind 1776) still has to reach the server; it is
 * public, so keeping it does not weaken anything. [recoveryCommitment] is the id of the commitment
 * the server holds for the account, once known.
 *
 * The whole record is secret; platform stores encrypt it or confine it to the user's private storage.
 * Deliberately not a data class: it must never be printed. Records written before S-146 read with
 * the defaults (phrase not checked, commitment unknown): the reminder then shows.
 */
@Serializable
class StoredIdentity(
    val version: Int = 1,
    val secretKeyHex: String,
    val recoveryWords: List<String>? = null,
    val phraseLanguage: String? = null,
    val phraseVerified: Boolean = false,
    val phraseCheckDueAt: Long? = null,
    val pendingRecoveryKey: String? = null,
    val recoveryCommitment: String? = null,
) {
    fun keys(): IdentityKeys = IdentityKeys.fromHex(secretKeyHex)

    fun recoveryPhrase(): RecoveryPhrase? = recoveryWords?.let { words ->
        RecoveryPhrase.parse(words.joinToString(" "), PhraseLanguage.entries.firstOrNull { it.code == phraseLanguage })
    }

    /** Whether the words are on the device although the user has not checked their copy yet. */
    val awaitsPhraseCheck: Boolean get() = recoveryWords != null && !phraseVerified

    /** A copy with some fields changed; the secret key never changes. */
    fun with(
        phrase: RecoveryPhrase? = recoveryPhrase(),
        phraseVerified: Boolean = this.phraseVerified,
        phraseCheckDueAt: Long? = this.phraseCheckDueAt,
        pendingRecoveryKey: String? = this.pendingRecoveryKey,
        recoveryCommitment: String? = this.recoveryCommitment,
    ): StoredIdentity = StoredIdentity(
        version = version,
        secretKeyHex = secretKeyHex,
        recoveryWords = phrase?.words,
        phraseLanguage = phrase?.language?.code,
        phraseVerified = phraseVerified,
        phraseCheckDueAt = phraseCheckDueAt,
        pendingRecoveryKey = pendingRecoveryKey,
        recoveryCommitment = recoveryCommitment,
    )

    fun encode(): ByteArray = json.encodeToString(serializer(), this).encodeToByteArray()

    override fun toString(): String = "StoredIdentity(version=$version)"

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /**
         * A new record. [phrase] is kept only when given; [verified] says whether the user already
         * typed it back. [pendingRecoveryKey] defaults to the recovery key of [phrase].
         */
        fun of(
            keys: IdentityKeys,
            phrase: RecoveryPhrase?,
            verified: Boolean = false,
            checkDueAt: Long? = null,
            pendingRecoveryKey: String? = phrase?.let(::recoveryKeyOf),
        ): StoredIdentity = StoredIdentity(
            secretKeyHex = keys.secretKey().toHexString(),
            recoveryWords = phrase?.words,
            phraseLanguage = phrase?.language?.code,
            phraseVerified = verified,
            phraseCheckDueAt = checkDueAt,
            pendingRecoveryKey = pendingRecoveryKey,
        )

        /** The public recovery key of [phrase] (S-146); the secret half is destroyed at once. */
        fun recoveryKeyOf(phrase: RecoveryPhrase): String {
            val recovery = phrase.deriveRecoveryKeys()
            return try {
                recovery.publicKey.hex
            } finally {
                recovery.destroy()
            }
        }

        fun decode(bytes: ByteArray): StoredIdentity = json.decodeFromString(serializer(), bytes.decodeToString())
    }
}

/**
 * Where the identity lives on the device. One implementation per platform (Android Keystore,
 * protected file on desktop, Keychain on iOS); [InMemoryIdentityStore] for tests and previews.
 * No implementation ever sends anything over the network.
 */
interface IdentityStore {
    /** The stored identity, or null when the device has no account. Throws if the record is unreadable. */
    suspend fun load(): StoredIdentity?

    suspend fun save(identity: StoredIdentity)

    /** Forgets the identity on this device. Without the recovery phrase, the account is lost. */
    suspend fun clear()
}

class InMemoryIdentityStore(initial: StoredIdentity? = null) : IdentityStore {
    /** What is currently stored; readable without a coroutine, for tests. */
    var snapshot: StoredIdentity? = initial
        private set

    override suspend fun load(): StoredIdentity? = snapshot

    override suspend fun save(identity: StoredIdentity) {
        snapshot = identity
    }

    override suspend fun clear() {
        snapshot = null
    }
}
