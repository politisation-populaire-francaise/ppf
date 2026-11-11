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
 *
 * Since version 1.2 of the contract the main server never carries an approval: it submits a draft
 * ([DraftRequest]), the team members approve it on the signing service's own page, and the server then
 * asks for the signature ([SignRequest]), which the service grants only if the approvals it recorded
 * itself satisfy the rule fixed by the draft.
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
 * The author of the draft behind a content, by back-office account id or by Nostr key (either is enough
 * to exclude them from a double validation).
 */
@Serializable
data class DraftAuthor(
    val account: String? = null,
    val pubkey: String? = null,
) {
    /** Whether the author is named at all. */
    val isNamed: Boolean get() = !account.isNullOrEmpty() || !pubkey.isNullOrEmpty()
}

/**
 * `POST /v1/drafts`: a content of a human use to be approved on the service's own page. [doubleValidation]
 * asks for two approvals of two different accounts, the [author] of the draft being neither of them. The
 * service requires it by itself for the contents that need it ([DoubleValidation.requiredFor]: the
 * contents of M17 that implicate a person) and refuses such a draft without it
 * (`double_validation_required`). The rule is fixed by the first submission of an event: a submission of
 * the same event with another rule is refused.
 */
@Serializable
data class DraftRequest(
    val use: String,
    val event: EventDraft,
    @SerialName("double_validation") val doubleValidation: Boolean = false,
    val author: DraftAuthor? = null,
)

/** The answer of `POST /v1/drafts`: `submitted` with the id and the page to approve it, or `refused` with a code. */
@Serializable
data class DraftResponse(
    val status: String,
    /** The id of the unsigned event (64 hexadecimal characters). */
    val draft: String? = null,
    /** The approval page, on the signing service's own origin: `<origin>/approbation/<use>/<id>`. */
    @SerialName("approval_url") val approvalUrl: String? = null,
    val code: String? = null,
    val detail: String? = null,
) {
    companion object {
        const val SUBMITTED: String = "submitted"
        const val REFUSED: String = "refused"
    }
}

/** One approval recorded by the signing service: the account of the member and when. */
@Serializable
data class DraftApproval(val account: String, val at: Long)

/** `GET /v1/drafts/{use}/{id}`: where the approval of a draft stands, for the back-office. */
@Serializable
data class DraftStatusResponse(
    val use: String,
    val draft: String,
    @SerialName("double_validation") val doubleValidation: Boolean,
    /** How many approvals the rule asks for: 1, or 2 for a double validation. */
    val required: Int,
    val approvals: List<DraftApproval>,
    /** Whether the approvals now satisfy the rule (the server may ask for the signature). */
    val ready: Boolean,
    /** Whether the service holds the content (after a restart, until the server submits the draft again). */
    @SerialName("content_known") val contentKnown: Boolean,
    @SerialName("submitted_at") val submittedAt: Long,
    @SerialName("expires_at") val expiresAt: Long,
    @SerialName("approval_url") val approvalUrl: String,
)

/** `POST /v1/sign`: the event of an automatic use, or of a human use whose draft the team approved. */
@Serializable
data class SignRequest(
    val use: String,
    val event: EventDraft,
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
    /**
     * The latest date among the signatures of this key and the `created_at` of the events it signed (from
     * the journal), null when it signed nothing: a closing revocation at `last_signed_at + 1` keeps every
     * content it signed and covers nothing after (contract, section 6, step 4).
     */
    @SerialName("last_signed_at") val lastSignedAt: Long? = null,
)

/** `GET /v1/keys`. */
@Serializable
data class KeysResponse(
    @SerialName("root_configured") val rootConfigured: Boolean,
    @SerialName("team_list") val teamList: String? = null,
    /** The end of the team list held (`ppf-not-after`), null when there is none or it names no valid end. */
    @SerialName("team_list_not_after") val teamListNotAfter: Long? = null,
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

    /** The team list held names no valid end, or its end is past: no approval relies on it. */
    const val TEAM_LIST_EXPIRED: String = "team_list_expired"

    /** An approval was sent to `POST /v1/sign` (refused since version 1.2: approvals are collected by the service). */
    const val UNEXPECTED_APPROVAL: String = "unexpected_approval"

    /** An automatic use is signed directly, never through a draft. */
    const val AUTOMATIC_USE: String = "automatic_use"

    /** No draft of this event is known for this use (never submitted, expired after 7 days, or already signed). */
    const val UNKNOWN_DRAFT: String = "unknown_draft"

    /** The same event was submitted before with another rule (double validation or author). */
    const val DRAFT_CONFLICT: String = "draft_conflict"

    /** The service holds as many drafts as it accepts; older ones must be signed or expire first. */
    const val TOO_MANY_DRAFTS: String = "too_many_drafts"

    const val APPROVALS_MISSING: String = "approvals_missing"
    const val UNKNOWN_CREDENTIAL: String = "unknown_credential"
    const val NOT_ALLOWED_FOR_USE: String = "not_allowed_for_use"
    const val ASSERTION_REFUSED: String = "assertion_refused"

    /** This account already approved this draft (two approvals must come from two different accounts). */
    const val ALREADY_APPROVED: String = "already_approved"

    const val AUTHOR_REQUIRED: String = "author_required"

    /**
     * The content needs a double validation ([DoubleValidation.requiredFor]: a content of M17 that
     * implicates a person) and the draft does not ask for it; also at signature, for a draft recorded
     * with a single approval.
     */
    const val DOUBLE_VALIDATION_REQUIRED: String = "double_validation_required"

    const val AUTHOR_CANNOT_APPROVE: String = "author_cannot_approve"
    const val ALREADY_SIGNED: String = "already_signed"

    /** A closing statement was already signed for this ballot box (`d`): never a second one. */
    const val URN_ALREADY_CLOSED: String = "urn_already_closed"

    const val RATE_LIMITED: String = "rate_limited"
    const val UNAUTHORIZED: String = "unauthorized"

    // Root requests (D-141, [RootRequests]).

    /** An event of a root request is not signed by the root, or is neither a certificate nor a team list. */
    const val NOT_A_ROOT_EVENT: String = "not_a_root_event"

    /** A certificate or a revocation of a root request names a key the service does not know. */
    const val UNKNOWN_KEY: String = "unknown_key"

    /** A certificate or a revocation of a root request does not name exactly one use, the use of its key. */
    const val WRONG_USES: String = "wrong_uses"

    /** The validity of a certificate is unreadable, empty or longer than 90 days. */
    const val BAD_VALIDITY: String = "bad_validity"

    /** The status of a certificate is neither `active` nor `revoquee` with its effective date. */
    const val BAD_STATUS: String = "bad_status"

    /** The team list of a root request has another `d`, or no end within 90 days of its date. */
    const val NOT_A_TEAM_LIST: String = "not_a_team_list"

    /** Two events of one root request have the same address (one key, or two team lists). */
    const val DUPLICATE_ADDRESS: String = "duplicate_address"

    /** No pending root request has this id (never made, expired after 24 hours, dropped, or lost by a restart). */
    const val UNKNOWN_REQUEST: String = "unknown_request"
}

/** `POST /v1/root-requests`: the unsigned events the root must sign, prepared by the back-office (1 to 100). */
@Serializable
data class RootRequestBody(val events: List<EventDraft>)

/**
 * The answer of `POST /v1/root-requests`: `201 {"id","sha256","expires_at"}`, or a refusal
 * `{"status":"refused","code","detail"}`.
 */
@Serializable
data class RootRequestResponse(
    /** 128 random bits in lowercase hexadecimal. */
    val id: String? = null,
    /** [RootRequests.sha256] of the events. */
    val sha256: String? = null,
    @SerialName("expires_at") val expiresAt: Long? = null,
    val status: String? = null,
    val code: String? = null,
    val detail: String? = null,
) {
    companion object {
        const val REFUSED: String = "refused"
    }
}

/** `GET /racine/demandes/{id}` on the approval origin: the pending request, as the owner's phone reads it. */
@Serializable
data class RootRequestView(
    val id: String,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("expires_at") val expiresAt: Long,
    val sha256: String,
    val events: List<EventDraft>,
)
