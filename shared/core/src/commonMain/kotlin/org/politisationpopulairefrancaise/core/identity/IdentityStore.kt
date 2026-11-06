package org.politisationpopulairefrancaise.core.identity

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the device keeps about the user's identity: the secret key and, when the account was created
 * or restored from a phrase, the recovery words so that the user can display them again.
 * The whole record is secret; platform stores encrypt it or confine it to the user's private storage.
 * Deliberately not a data class: it must never be printed.
 */
@Serializable
class StoredIdentity(
    val version: Int = 1,
    val secretKeyHex: String,
    val recoveryWords: List<String>? = null,
    val phraseLanguage: String? = null,
) {
    fun keys(): IdentityKeys = IdentityKeys.fromHex(secretKeyHex)

    fun recoveryPhrase(): RecoveryPhrase? = recoveryWords?.let { words ->
        RecoveryPhrase.parse(words.joinToString(" "), PhraseLanguage.entries.firstOrNull { it.code == phraseLanguage })
    }

    fun encode(): ByteArray = json.encodeToString(serializer(), this).encodeToByteArray()

    override fun toString(): String = "StoredIdentity(version=$version)"

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun of(keys: IdentityKeys, phrase: RecoveryPhrase?): StoredIdentity = StoredIdentity(
            secretKeyHex = keys.secretKey().toHexString(),
            recoveryWords = phrase?.words,
            phraseLanguage = phrase?.language?.code,
        )

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
