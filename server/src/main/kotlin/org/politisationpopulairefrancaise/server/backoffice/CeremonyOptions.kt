package org.politisationpopulairefrancaise.server.backoffice

import kotlinx.serialization.Serializable

/**
 * The options the browser script gives to `navigator.credentials.create` and `.get`, in the JSON shape of
 * the WebAuthn specification (byte fields in base64url, decoded by the script). Names follow the
 * specification, hence the camel case.
 */
@Serializable
data class CreationOptionsResponse(val ceremony: String, val publicKey: PublicKeyCreationOptions)

@Serializable
data class RequestOptionsResponse(val ceremony: String, val publicKey: PublicKeyRequestOptions)

@Serializable
data class RelyingParty(val id: String, val name: String)

@Serializable
data class UserEntity(val id: String, val name: String, val displayName: String)

@Serializable
data class CredentialParameter(val type: String = "public-key", val alg: Long)

@Serializable
data class CredentialDescriptor(val type: String = "public-key", val id: String)

@Serializable
data class AuthenticatorSelection(
    val residentKey: String = "required",
    val requireResidentKey: Boolean = true,
    val userVerification: String = "required",
)

@Serializable
data class PublicKeyCreationOptions(
    val challenge: String,
    val rp: RelyingParty,
    val user: UserEntity,
    val pubKeyCredParams: List<CredentialParameter>,
    val timeout: Long,
    val excludeCredentials: List<CredentialDescriptor> = emptyList(),
    val authenticatorSelection: AuthenticatorSelection = AuthenticatorSelection(),
    val attestation: String = "none",
)

@Serializable
data class PublicKeyRequestOptions(
    val challenge: String,
    val rpId: String,
    val timeout: Long,
    val allowCredentials: List<CredentialDescriptor> = emptyList(),
    val userVerification: String = "required",
)

/** The answer of a finished ceremony: where the script sends the browser next (always a local path). */
@Serializable
data class CeremonyDone(val redirect: String)

/** A refused ceremony: a stable code and its French message, shown by the script. */
@Serializable
data class CeremonyRefused(val error: String, val message: String)
