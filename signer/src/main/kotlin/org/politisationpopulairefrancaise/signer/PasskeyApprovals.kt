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
import org.politisationpopulairefrancaise.core.platform.ApprovalAssertion
import org.politisationpopulairefrancaise.core.platform.TeamCredential
import java.nio.ByteBuffer
import java.util.Base64

/** Checks one passkey assertion of a team member against the passkey listed for them by the root. */
fun interface ApprovalVerifier {
    /** Whether [assertion] is a valid signature by [credential] over [challenge], with user verification. */
    fun verify(assertion: ApprovalAssertion, credential: TeamCredential, challenge: ByteArray): Boolean
}

/**
 * [ApprovalVerifier] with WebAuthn4J (the library and version of the back-office, D-120): the origin and
 * relying party of the back-office, the challenge of the approval, user presence and user verification
 * required, the public key taken from the team list signed by the root (never from the request). The
 * signature counter is not tracked: approvals of different events may be presented in any order; a
 * replay is refused because an event is signed once per use.
 */
class WebAuthn4jApprovals(origin: String, private val rpId: String) : ApprovalVerifier {
    private val origin = Origin(origin)
    private val converter = ObjectConverter()
    private val manager = WebAuthnManager.createNonStrictWebAuthnManager(converter)
    private val attestedConverter = AttestedCredentialDataConverter(converter)

    override fun verify(assertion: ApprovalAssertion, credential: TeamCredential, challenge: ByteArray): Boolean {
        val credentialId = decode(credential.id) ?: return false
        val coseKey = decode(credential.publicKey) ?: return false
        val presentedId = decode(assertion.credentialId) ?: return false
        if (!presentedId.contentEquals(credentialId)) return false
        val authenticatorData = decode(assertion.authenticatorData) ?: return false
        val clientData = decode(assertion.clientData) ?: return false
        val signature = decode(assertion.signature) ?: return false
        val userHandle = assertion.userHandle?.let { decode(it) ?: return false }
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
        if (value.isEmpty() || value.length > MAX_FIELD_LENGTH) return null
        return try {
            Base64.getUrlDecoder().decode(value.trimEnd('='))
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private companion object {
        const val MAX_FIELD_LENGTH = 16_384
    }
}
