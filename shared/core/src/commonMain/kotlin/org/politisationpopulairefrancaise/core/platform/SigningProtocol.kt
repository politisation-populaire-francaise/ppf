package org.politisationpopulairefrancaise.core.platform

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent

/**
 * The JSON exchanged between the main server and the signing service (contract
 * docs/integration/service-signature.md, section 5; D-129), shared so that both sides read and write
 * exactly the same fields.
 */
object SigningProtocol {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
}

/** An event to sign, as NIP-01 without `id` and `sig`. */
@Serializable
data class EventDraft(
    val pubkey: String,
    @SerialName("created_at") val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>> = emptyList(),
    val content: String = "",
) {
    /** The unsigned event; throws IllegalArgumentException when a field is malformed. */
    fun toUnsigned(): UnsignedEvent = UnsignedEvent(pubkey, createdAt, kind, tags, content)

    companion object {
        fun of(event: UnsignedEvent): EventDraft = EventDraft(event.pubkey, event.createdAt, event.kind, event.tags, event.content)
    }
}

/**
 * One WebAuthn assertion of a team member over [ApprovalChallenge] (all binary fields in base64url
 * without padding, as the browser gives them): the back-office collects it, the signing service checks it
 * against the passkeys of the team list signed by the root.
 */
@Serializable
data class ApprovalAssertion(
    /** The back-office account id, as in the team list. */
    val account: String,
    @SerialName("credential_id") val credentialId: String,
    @SerialName("authenticator_data") val authenticatorData: String,
    @SerialName("client_data") val clientData: String,
    val signature: String,
    @SerialName("user_handle") val userHandle: String? = null,
)

/** The author of the draft behind a content, by account id or by Nostr key (either is enough to exclude them). */
@Serializable
data class DraftAuthor(
    val account: String? = null,
    val pubkey: String? = null,
)

/**
 * The proof that the team approved one signature: no assertion for an automatic use, one for an
 * ordinary publication, two of two different accounts for a content of M17 that implicates a person
 * ([doubleValidation], told by the queue), the [author] of the draft being neither of them.
 */
@Serializable
data class ApprovalProof(
    @SerialName("double_validation") val doubleValidation: Boolean = false,
    val author: DraftAuthor? = null,
    val assertions: List<ApprovalAssertion> = emptyList(),
) {
    companion object {
        /** The proof of an automatic use: nothing. */
        val NONE: ApprovalProof = ApprovalProof()
    }
}

/** `POST /v1/sign`. */
@Serializable
data class SignRequest(
    val use: String,
    val event: EventDraft,
    val approval: ApprovalProof = ApprovalProof.NONE,
)

/** The answer of `POST /v1/sign`: `signed` with the event, or `refused` with a stable [code] of [SigningRefusals]. */
@Serializable
data class SignResponse(
    val status: String,
    val event: SignedEvent? = null,
    val code: String? = null,
    /** For some refusals, the tag or field concerned (never a value sent). */
    val detail: String? = null,
) {
    companion object {
        const val SIGNED: String = "signed"
        const val REFUSED: String = "refused"
    }
}

/** States of a working key, as `GET /v1/keys` shows them. */
object WorkingKeyStates {
    /** Certified, inside its validity, not revoked: the key used for its use now (the newest of them if several). */
    const val ACTIVE: String = "active"

    /** Created by the service, waiting for its certificate by the root. */
    const val AWAITING_CERTIFICATION: String = "awaiting_certification"

    /** Certified, but its validity starts later: the service switches to it at its `not_before`. */
    const val SCHEDULED: String = "scheduled"

    const val EXPIRED: String = "expired"
    const val REVOKED: String = "revoked"
}

/** One working key of the service: public data only, never anything secret. */
@Serializable
data class WorkingKeyInfo(
    val use: String,
    val pubkey: String,
    val state: String,
    @SerialName("created_at") val createdAt: Long,
    /** Whether the service signs with this key now. */
    val current: Boolean = false,
    @SerialName("not_before") val notBefore: Long? = null,
    @SerialName("not_after") val notAfter: Long? = null,
    @SerialName("revoked_from") val revokedFrom: Long? = null,
)

/** `GET /v1/keys`. */
@Serializable
data class KeysResponse(
    @SerialName("root_configured") val rootConfigured: Boolean,
    @SerialName("team_list") val teamList: String? = null,
    val keys: List<WorkingKeyInfo>,
)

/** The stable refusal codes of the signing service. */
object SigningRefusals {
    const val MALFORMED_REQUEST: String = "malformed_request"
    const val UNKNOWN_USE: String = "unknown_use"
    const val USE_DISABLED: String = "use_disabled"
    const val MALFORMED_EVENT: String = "malformed_event"
    const val TOO_LARGE: String = "too_large"
    const val KIND_NOT_ALLOWED: String = "kind_not_allowed"
    const val MISSING_TAG: String = "missing_tag"
    const val BAD_TAG: String = "bad_tag"
    const val TAG_NOT_ALLOWED: String = "tag_not_allowed"
    const val BAD_CONTENT: String = "bad_content"
    const val CREATED_AT_IN_FUTURE: String = "created_at_in_future"
    const val CREATED_AT_TOO_OLD: String = "created_at_too_old"
    const val NO_ROOT: String = "no_root"
    const val NO_ACTIVE_KEY: String = "no_active_key"
    const val WRONG_KEY: String = "wrong_key"
    const val KEY_NOT_CERTIFIED: String = "key_not_certified"
    const val KEY_REVOKED: String = "key_revoked"
    const val KEY_EXPIRED: String = "key_expired"
    const val OUTSIDE_CERTIFICATE: String = "outside_certificate"
    const val NO_TEAM_LIST: String = "no_team_list"
    const val UNEXPECTED_APPROVAL: String = "unexpected_approval"
    const val APPROVALS_MISSING: String = "approvals_missing"
    const val UNKNOWN_CREDENTIAL: String = "unknown_credential"
    const val ACCOUNT_MISMATCH: String = "account_mismatch"
    const val NOT_ALLOWED_FOR_USE: String = "not_allowed_for_use"
    const val ASSERTION_REFUSED: String = "assertion_refused"
    const val SAME_ACCOUNT_TWICE: String = "same_account_twice"
    const val AUTHOR_REQUIRED: String = "author_required"
    const val AUTHOR_CANNOT_APPROVE: String = "author_cannot_approve"
    const val ALREADY_SIGNED: String = "already_signed"
    const val RATE_LIMITED: String = "rate_limited"
    const val UNAUTHORIZED: String = "unauthorized"
}
