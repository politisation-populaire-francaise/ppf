package org.politisationpopulairefrancaise.core.identity

import fr.acinq.secp256k1.Secp256k1
import org.politisationpopulairefrancaise.core.crypto.secureRandomBytes

/** The public half of an identity: a 32-byte x-only secp256k1 key, exactly as Nostr identifies authors. */
class PublicKey(bytes: ByteArray) {
    private val value: ByteArray = bytes.copyOf()

    init {
        require(value.size == 32) { "A public key is 32 bytes long, got ${value.size}" }
    }

    val bytes: ByteArray get() = value.copyOf()

    /** Lowercase hexadecimal form, the one used inside signed events. */
    val hex: String get() = value.toHexString()

    /** Human-readable form (NIP-19), the one shown to users and shared between them. */
    val npub: String get() = Nip19.encode(Nip19.PUBLIC_KEY, value)

    /**
     * BIP-340 Schnorr verification of a 64-byte [signature] over a 32-byte [message]
     * (in practice an event id). Never throws: anything malformed is simply not valid.
     */
    fun verify(signature: ByteArray, message: ByteArray): Boolean {
        if (signature.size != 64 || message.size != 32) return false
        return runCatching { Secp256k1.verifySchnorr(signature, message, value) }.getOrDefault(false)
    }

    override fun equals(other: Any?): Boolean = other is PublicKey && value.contentEquals(other.value)
    override fun hashCode(): Int = value.contentHashCode()
    override fun toString(): String = npub

    companion object {
        fun fromHex(hex: String): PublicKey = PublicKey(hex.hexToByteArray())
        fun fromNpub(npub: String): PublicKey = PublicKey(Nip19.decode(Nip19.PUBLIC_KEY, npub))
    }
}

/**
 * A pseudonymous identity: a secp256k1 key pair generated on the device, never on the server,
 * and compatible with Nostr (cahier des charges, section 3.2). No phone number, no e-mail.
 *
 * The secret key stays inside this object. It only leaves through [nsec] or [secretKey], for the
 * user's own backup or export, and is wiped from memory by [destroy].
 */
class IdentityKeys private constructor(private val secret: ByteArray) {

    /** The public key, derived once; it stays readable after [destroy]. */
    val publicKey: PublicKey = PublicKey(Secp256k1.pubkeyCreate(secret).copyOfRange(1, 33))

    private var destroyed = false

    val isDestroyed: Boolean get() = destroyed

    /** Secret key in its shareable Nostr form ("nsec1…"). Show it only on the user's explicit request. */
    val nsec: String
        get() {
            checkAlive()
            return Nip19.encode(Nip19.SECRET_KEY, secret)
        }

    /** Copy of the raw 32-byte secret key, for encrypted storage on the device. */
    fun secretKey(): ByteArray {
        checkAlive()
        return secret.copyOf()
    }

    /**
     * BIP-340 Schnorr signature of a 32-byte [message] (an event id), with fresh auxiliary
     * randomness so that two signatures of the same message never look alike.
     */
    fun sign(message: ByteArray): ByteArray = signWithAuxiliaryRandomness(message, secureRandomBytes(32))

    internal fun signWithAuxiliaryRandomness(message: ByteArray, auxiliaryRandomness: ByteArray): ByteArray {
        checkAlive()
        require(message.size == 32) { "Only 32-byte digests are signed, got ${message.size} bytes" }
        require(auxiliaryRandomness.size == 32) { "Auxiliary randomness must be 32 bytes" }
        return Secp256k1.signSchnorr(message, secret, auxiliaryRandomness)
    }

    /** Overwrites the secret key in memory. The object can no longer sign afterwards. */
    fun destroy() {
        secret.fill(0)
        destroyed = true
    }

    private fun checkAlive() = check(!destroyed) { "These identity keys have been destroyed" }

    /** Never prints the secret. */
    override fun toString(): String = "IdentityKeys(${publicKey.npub})"

    companion object {
        /** Creates a brand new identity from the operating system's secure random generator. */
        fun generate(): IdentityKeys {
            while (true) {
                val candidate = secureRandomBytes(32)
                if (Secp256k1.secKeyVerify(candidate)) return IdentityKeys(candidate)
            }
        }

        /** Restores an identity from its raw 32-byte secret key (the array is copied). */
        fun fromSecretKey(bytes: ByteArray): IdentityKeys {
            require(bytes.size == 32 && Secp256k1.secKeyVerify(bytes)) { "Invalid secret key" }
            return IdentityKeys(bytes.copyOf())
        }

        /** Restores an identity from its "nsec1…" form. */
        fun fromNsec(nsec: String): IdentityKeys = fromSecretKey(Nip19.decode(Nip19.SECRET_KEY, nsec))

        fun fromHex(hex: String): IdentityKeys = fromSecretKey(hex.hexToByteArray())
    }
}
