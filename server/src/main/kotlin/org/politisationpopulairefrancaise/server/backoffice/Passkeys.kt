package org.politisationpopulairefrancaise.server.backoffice

import com.webauthn4j.WebAuthnManager
import com.webauthn4j.converter.AttestedCredentialDataConverter
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.credential.CredentialRecordImpl
import com.webauthn4j.data.AuthenticationParameters
import com.webauthn4j.data.AuthenticationRequest
import com.webauthn4j.data.PublicKeyCredentialParameters
import com.webauthn4j.data.PublicKeyCredentialType
import com.webauthn4j.data.RegistrationParameters
import com.webauthn4j.data.RegistrationRequest
import com.webauthn4j.data.attestation.authenticator.AAGUID
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.server.ServerProperty
import com.webauthn4j.util.exception.WebAuthnException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** A passkey ceremony could not be verified; [code] is stable and never quotes what the browser sent. */
class PasskeyException(val code: String) : RuntimeException(code)

/** What the browser posts after `navigator.credentials.create` (fields in base64url). */
@Serializable
data class RegistrationResponse(
    val ceremony: String,
    @SerialName("credential_id") val credentialId: String,
    @SerialName("client_data") val clientData: String,
    @SerialName("attestation_object") val attestationObject: String,
    val name: String? = null,
    val csrf: String? = null,
)

/** What the browser posts after `navigator.credentials.get` (fields in base64url). */
@Serializable
data class AssertionResponse(
    val ceremony: String,
    @SerialName("credential_id") val credentialId: String,
    @SerialName("client_data") val clientData: String,
    @SerialName("authenticator_data") val authenticatorData: String,
    val signature: String,
    @SerialName("user_handle") val userHandle: String? = null,
    val csrf: String? = null,
)

/** The new signature counter and backup state after a successful assertion. */
data class VerifiedAssertion(val signCount: Long, val backupState: Boolean)

/**
 * Verification of passkeys and FIDO2 security keys (WebAuthn), user verification always required (a
 * code or a fingerprint on the device), no password anywhere (D-120). Behind an interface so that the
 * rest of the base does not depend on the library.
 */
interface PasskeyVerifier {
    /** Checks a registration against [challenge]; returns what to store (the authenticator model is not kept). */
    fun verifyRegistration(response: RegistrationResponse, challenge: ByteArray, name: String): NewCredential

    /** Checks an assertion of [credential] against [challenge]; refuses a signature counter that went backwards. */
    fun verifyAssertion(response: AssertionResponse, challenge: ByteArray, credential: StoredCredential): VerifiedAssertion
}

/**
 * [PasskeyVerifier] with WebAuthn4J (D-120): origin and relying party id of the back-office, challenge of
 * the ceremony, user presence and user verification required, ES256, EdDSA and RS256 keys. Attestation
 * is not asked ("none") nor trusted: the back-office does not need to know the model of the key.
 */
class WebAuthn4jPasskeys(origin: String, private val rpId: String) : PasskeyVerifier {
    private val origin = Origin(origin)
    private val converter = ObjectConverter()
    private val manager = WebAuthnManager.createNonStrictWebAuthnManager(converter)
    private val attestedConverter = AttestedCredentialDataConverter(converter)

    override fun verifyRegistration(response: RegistrationResponse, challenge: ByteArray, name: String): NewCredential {
        val attestationObject = decode(response.attestationObject)
        val clientData = decode(response.clientData)
        val credentialId = decode(response.credentialId)
        val data = try {
            manager.verify(
                RegistrationRequest(attestationObject, clientData),
                RegistrationParameters(property(challenge), ALGORITHMS, true, true),
            )
        } catch (e: WebAuthnException) {
            throw PasskeyException("registration_refused")
        }
        val authenticatorData = data.attestationObject?.authenticatorData ?: throw PasskeyException("registration_refused")
        val attested = authenticatorData.attestedCredentialData ?: throw PasskeyException("registration_refused")
        if (!attested.credentialId.contentEquals(credentialId)) throw PasskeyException("credential_mismatch")
        val minimal = AttestedCredentialData(AAGUID.ZERO, attested.credentialId, attested.coseKey)
        return NewCredential(
            credentialId = attested.credentialId,
            attestedData = attestedConverter.convert(minimal),
            signCount = authenticatorData.signCount,
            uvInitialized = authenticatorData.isFlagUV,
            backupEligible = authenticatorData.isFlagBE,
            backupState = authenticatorData.isFlagBS,
            name = name,
        )
    }

    override fun verifyAssertion(response: AssertionResponse, challenge: ByteArray, credential: StoredCredential): VerifiedAssertion {
        val credentialId = decode(response.credentialId)
        if (!credentialId.contentEquals(credential.credentialId)) throw PasskeyException("credential_mismatch")
        val record = CredentialRecordImpl(
            null,
            credential.uvInitialized,
            credential.backupEligible,
            credential.backupState,
            credential.signCount,
            attestedConverter.convert(credential.attestedData),
            null,
            null,
            null,
            null,
        )
        val data = try {
            manager.verify(
                AuthenticationRequest(
                    credentialId,
                    response.userHandle?.let(::decode),
                    decode(response.authenticatorData),
                    decode(response.clientData),
                    decode(response.signature),
                ),
                AuthenticationParameters(property(challenge), record, listOf(credential.credentialId), true, true),
            )
        } catch (e: WebAuthnException) {
            throw PasskeyException("assertion_refused")
        }
        val authenticatorData = data.authenticatorData ?: throw PasskeyException("assertion_refused")
        return VerifiedAssertion(authenticatorData.signCount, authenticatorData.isFlagBS)
    }

    private fun property(challenge: ByteArray): ServerProperty =
        ServerProperty.builder().origin(origin).rpId(rpId).challenge(DefaultChallenge(challenge)).build()

    private fun decode(value: String): ByteArray {
        if (value.length > MAX_FIELD_LENGTH) throw PasskeyException("too_large")
        return Tokens.fromBase64Url(value) ?: throw PasskeyException("bad_encoding")
    }

    companion object {
        /** ES256 (most keys), EdDSA (some security keys), RS256 (Windows Hello). */
        val ALGORITHMS: List<PublicKeyCredentialParameters> = listOf(
            PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.ES256),
            PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.EdDSA),
            PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.RS256),
        )

        /** The COSE identifiers of [ALGORITHMS], as the browser options give them. */
        val ALGORITHM_IDS: List<Long> = listOf(-7, -8, -257)

        private const val MAX_FIELD_LENGTH = 16_384
    }
}

/**
 * The COSE public key of a stored passkey, as the team list signed by the root names it (D-141): the
 * attested credential data stored at registration is the zero AAGUID (16 bytes), the length of the
 * credential id (2 bytes, big-endian), the credential id, then the COSE key; the signing service rebuilds
 * exactly this data from the team list to check an approval.
 */
object CoseKeys {
    fun of(credential: StoredCredential): ByteArray? = of(credential.attestedData, credential.credentialId)

    fun of(attestedData: ByteArray, credentialId: ByteArray): ByteArray? {
        val offset = 16 + 2 + credentialId.size
        if (attestedData.size <= offset) return null
        val length = ((attestedData[16].toInt() and 0xff) shl 8) or (attestedData[17].toInt() and 0xff)
        if (length != credentialId.size || !attestedData.copyOfRange(18, offset).contentEquals(credentialId)) return null
        return attestedData.copyOfRange(offset, attestedData.size)
    }
}

/** A ceremony started by the server and waiting for the browser's answer (sign-in excepted: [SignInChallenges]). */
sealed interface Ceremony {
    val challenge: ByteArray
    val startedAt: Instant

    /** Registration of a first key from an invitation (one pending per invitation). */
    class Enrol(override val challenge: ByteArray, override val startedAt: Instant, val invitationId: UUID, val userHandle: ByteArray) : Ceremony

    /** The verification of an existing key that opens the registration of one more (at most 60 seconds). */
    class AddKeyProof(override val challenge: ByteArray, override val startedAt: Instant, val accountId: UUID) : Ceremony

    /** Registration of one more key, opened by a verified [AddKeyProof] just before. */
    class AddKey(override val challenge: ByteArray, override val startedAt: Instant, val accountId: UUID) : Ceremony

    /** New verification of a key before a sensitive action. */
    class Confirm(override val challenge: ByteArray, override val startedAt: Instant, val accountId: UUID) : Ceremony
}

/**
 * Ceremonies kept in memory, **one pending per key** (a session, an invitation): starting a new one
 * replaces the previous one, so their number is bounded by the sessions and invitations that exist,
 * whatever an attacker sends. Each is used once and forgotten after [lifetime].
 */
class CeremonySlots<K : Any>(
    private val clock: Clock = Clock.systemUTC(),
    private val lifetime: Duration = 5.minutes,
) {
    private class Pending(val id: String, val ceremony: Ceremony)

    private val slots = ConcurrentHashMap<K, Pending>()

    /** Starts [make]'s ceremony for [key] under a fresh id, replacing any ceremony pending for it. */
    fun start(key: K, make: (challenge: ByteArray, now: Instant) -> Ceremony): Pair<String, Ceremony> {
        val id = Tokens.generate(18)
        val ceremony = make(Tokens.randomBytes(32), clock.instant())
        slots[key] = Pending(id, ceremony)
        return id to ceremony
    }

    /** Takes the ceremony [id] of [key] (once): null when unknown, replaced, already used or too old. */
    fun take(key: K, id: String): Ceremony? {
        val pending = slots[key] ?: return null
        if (!Tokens.constantTimeEquals(pending.id, id) || !slots.remove(key, pending)) return null
        return pending.ceremony.takeIf { !it.startedAt.plusSeconds(lifetime.inWholeSeconds).isBefore(clock.instant()) }
    }

    /** Forgets the ceremonies past their lifetime (periodic task). */
    fun purge() {
        val now = clock.instant()
        slots.entries.removeIf { it.value.ceremony.startedAt.plusSeconds(lifetime.inWholeSeconds).isBefore(now) }
    }

    val size: Int get() = slots.size

    val timeoutMillis: Long get() = lifetime.inWholeMilliseconds
}

/**
 * Sign-in challenges without server state (D-127): a challenge is 16 random bytes, the time it was
 * issued and an HMAC-SHA-256 of both with a key drawn at startup. Anyone may ask for one without
 * filling any memory; the server checks the HMAC and the age (5 minutes) when the answer comes back. A
 * challenge enters the set of used ones only once a signature has been verified with it (so only a
 * real member can fill that set), and leaves it when it is too old to be accepted anyway.
 */
class SignInChallenges(
    private val clock: Clock = Clock.systemUTC(),
    private val lifetime: Duration = 5.minutes,
    private val capacity: Int = 10_000,
) {
    private val key = Tokens.randomBytes(32)
    private val used = ConcurrentHashMap<String, Long>()

    /** A fresh challenge, in base64url: it is also the ceremony id of the sign-in. */
    fun issue(): String {
        val nonce = Tokens.randomBytes(NONCE)
        val time = java.nio.ByteBuffer.allocate(8).putLong(clock.instant().epochSecond).array()
        return Tokens.base64Url(nonce + time + mac(nonce + time))
    }

    /** The challenge bytes of [id] when it was issued here, less than 5 minutes ago, and never used. */
    fun open(id: String): ByteArray? {
        val bytes = Tokens.fromBase64Url(id)?.takeIf { it.size == SIZE } ?: return null
        val signed = bytes.copyOfRange(0, NONCE + 8)
        if (!java.security.MessageDigest.isEqual(mac(signed), bytes.copyOfRange(NONCE + 8, SIZE))) return null
        val issuedAt = java.nio.ByteBuffer.wrap(bytes, NONCE, 8).long
        val now = clock.instant().epochSecond
        if (issuedAt > now + 5 || now - issuedAt > lifetime.inWholeSeconds) return null
        if (used.containsKey(id)) return null
        return bytes
    }

    /** Records [id] as used after a verified signature; false when it was already used or the set is full. */
    fun markUsed(id: String): Boolean {
        val now = clock.instant().epochSecond
        used.entries.removeIf { it.value < now }
        if (used.size >= capacity) return false
        return used.putIfAbsent(id, now + lifetime.inWholeSeconds + 5) == null
    }

    val timeoutMillis: Long get() = lifetime.inWholeMilliseconds

    private fun mac(data: ByteArray): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private companion object {
        const val NONCE = 16
        const val SIZE = NONCE + 8 + 32
    }
}
