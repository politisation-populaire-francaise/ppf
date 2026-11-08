package org.politisationpopulairefrancaise.signer

import com.webauthn4j.WebAuthnManager
import com.webauthn4j.converter.AttestedCredentialDataConverter
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.credential.CredentialRecordImpl
import com.webauthn4j.data.AuthenticationParameters
import com.webauthn4j.data.AuthenticationRequest
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.server.ServerProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import org.politisationpopulairefrancaise.core.platform.TeamCredential
import java.nio.ByteBuffer
import java.util.Base64

/**
 * What the approval page posts after `navigator.credentials.get` (binary fields in base64url without
 * padding, as the browser gives them). No account: the service finds it from the passkey in the team list.
 */
@Serializable
data class PasskeyAssertion(
    @SerialName("credential_id") val credentialId: String,
    @SerialName("authenticator_data") val authenticatorData: String,
    @SerialName("client_data") val clientData: String,
    val signature: String,
    @SerialName("user_handle") val userHandle: String? = null,
)

/** Checks one passkey assertion of a team member against the passkey listed for them by the root. */
fun interface ApprovalVerifier {
    /** Whether [assertion] is a valid signature by [credential] over [challenge], with user verification. */
    fun verify(assertion: PasskeyAssertion, credential: TeamCredential, challenge: ByteArray): Boolean
}

/**
 * [ApprovalVerifier] with WebAuthn4J (the library and version of the back-office, D-120). The origin is
 * the service's own approval origin and nothing else: the browser writes the origin of the page into the
 * signed client data, so an assertion made on a page of the main server (the back-office) is refused even
 * though the passkeys are the back-office's (same relying party [rpId]). Also required: the challenge of
 * the approval, user presence and user verification, no cross-origin frame, and the public key taken from
 * the team list signed by the root (never from the request). The signature counter is not tracked: an
 * approval is recorded once per account and per event, and an event is signed once per use.
 */
class WebAuthn4jApprovals(origin: String, private val rpId: String) : ApprovalVerifier {
    private val origin = Origin(origin)
    private val converter = ObjectConverter()
    private val manager = WebAuthnManager.createNonStrictWebAuthnManager(converter)
    private val attestedConverter = AttestedCredentialDataConverter(converter)

    override fun verify(assertion: PasskeyAssertion, credential: TeamCredential, challenge: ByteArray): Boolean {
        val credentialId = decode(credential.id) ?: return false
        val coseKey = decode(credential.publicKey) ?: return false
        val presentedId = decode(assertion.credentialId) ?: return false
        if (!presentedId.contentEquals(credentialId)) return false
        val authenticatorData = decode(assertion.authenticatorData) ?: return false
        val clientData = decode(assertion.clientData) ?: return false
        val signature = decode(assertion.signature) ?: return false
        val userHandle = assertion.userHandle?.let { decode(it) ?: return false }
        if (madeInAFrame(clientData)) return false
        return try {
            val attested = attestedData(credentialId, coseKey)
            val record = CredentialRecordImpl(null, true, null, null, 0L, attested, null, null, null, null)
            val property = ServerProperty.builder().origin(origin).rpId(rpId).challenge(DefaultChallenge(challenge)).build()
            manager.verify(
                AuthenticationRequest(presentedId, userHandle, authenticatorData, clientData, signature),
                AuthenticationParameters(property, record, listOf(credentialId), true, true),
            )
            true
        } catch (e: RuntimeException) {
            // WebAuthnException for a refused assertion; the converters throw other runtime exceptions on bad CBOR.
            false
        }
    }

    /** Whether the browser says the page was inside a frame of another origin (`crossOrigin` or `topOrigin`). */
    private fun madeInAFrame(clientData: ByteArray): Boolean {
        val json = runCatching { CLIENT_DATA.parseToJsonElement(String(clientData, Charsets.UTF_8)) as? JsonObject }.getOrNull() ?: return true
        val crossOrigin = json["crossOrigin"]
        if (crossOrigin != null && (crossOrigin as? JsonPrimitive)?.booleanOrNull != false) return true
        return json.containsKey("topOrigin")
    }

    /** The attested credential data of WebAuthn (zero AAGUID, id, COSE key), as the back-office stores it. */
    private fun attestedData(credentialId: ByteArray, coseKey: ByteArray): AttestedCredentialData {
        val bytes = ByteBuffer.allocate(16 + 2 + credentialId.size + coseKey.size)
            .put(ByteArray(16))
            .putShort(credentialId.size.toShort())
            .put(credentialId)
            .put(coseKey)
            .array()
        return attestedConverter.convert(bytes)
    }

    private fun decode(value: String): ByteArray? {
        if (value.isEmpty() || value.length > MAX_FIELD_LENGTH || '=' in value) return null
        return try {
            Base64.getUrlDecoder().decode(value)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private companion object {
        const val MAX_FIELD_LENGTH = 16_384
        val CLIENT_DATA = Json
    }
}
