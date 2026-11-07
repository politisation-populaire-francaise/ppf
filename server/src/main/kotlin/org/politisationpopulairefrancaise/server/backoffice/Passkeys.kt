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

/** A ceremony started by the server and waiting for the browser's answer. */
sealed interface Ceremony {
    val challenge: ByteArray
    val startedAt: Instant

    /** Registration of a first key from an invitation. */
    class Enrol(override val challenge: ByteArray, override val startedAt: Instant, val invitationId: UUID, val userHandle: ByteArray) : Ceremony

    /** Registration of one more key by a signed-in member. */
    class AddKey(override val challenge: ByteArray, override val startedAt: Instant, val sessionHash: String, val accountId: UUID) : Ceremony

    /** Sign-in with any key of the team (discoverable credential). */
    class SignIn(override val challenge: ByteArray, override val startedAt: Instant) : Ceremony

    /** New verification of a key before a sensitive action. */
    class Confirm(override val challenge: ByteArray, override val startedAt: Instant, val sessionHash: String, val accountId: UUID) : Ceremony
}

/**
 * The ceremonies in progress, in memory only (one server): each has a random id and a 32-byte
 * challenge, is used once and forgotten after [lifetime]. At most [capacity] wait at once: beyond that
 * new ones are refused, which bounds what an attacker inside the tunnel can make the server keep.
 */
class Ceremonies(
    private val clock: Clock = Clock.systemUTC(),
    private val lifetime: Duration = 5.minutes,
    private val capacity: Int = 1_000,
) {
    private val waiting = ConcurrentHashMap<String, Ceremony>()

    /** Registers [make]'s ceremony under a fresh id, or returns null when too many wait already. */
    fun start(make: (challenge: ByteArray, now: Instant) -> Ceremony): Pair<String, Ceremony>? {
        val now = clock.instant()
        waiting.entries.removeIf { it.value.startedAt.plusSeconds(lifetime.inWholeSeconds).isBefore(now) }
        if (waiting.size >= capacity) return null
        val id = Tokens.generate(18)
        val ceremony = make(Tokens.randomBytes(32), now)
        waiting[id] = ceremony
        return id to ceremony
    }

    /** Takes the ceremony [id] (once): null when unknown, already used or too old. */
    fun take(id: String): Ceremony? {
        val ceremony = waiting.remove(id) ?: return null
        return ceremony.takeIf { !it.startedAt.plusSeconds(lifetime.inWholeSeconds).isBefore(clock.instant()) }
    }

    val timeoutMillis: Long get() = lifetime.inWholeMilliseconds
}
