-- The signature and the publication of the editorial circuit (docs/integration/circuit-editorial.md,
-- section 6, step 5). Number V15, after V13 (urnes) and V14 (archives) of Décider in main.
--
-- Personal data (docs/REGISTRE_DONNEES.md): the internal identifier of the team account that withdrew an
-- approved version (pseudonymous) and the reason it wrote; no data of a citizen, never an IP address nor a
-- browser. Nothing secret: the unsigned event is the public content about to be signed, the approval page
-- is an address of the signing service, the ticket only means something to this server's process. No key,
-- no approval, no passkey ever lands here (D-129 revision).

-- One submission of an approved version to the signing service. A version may be submitted several times:
-- a submission expires after 7 days on the service, and a retry builds a new event (new created_at).
CREATE TABLE editorial_signing (
    event_id         TEXT        PRIMARY KEY CHECK (event_id ~ '^[0-9a-f]{64}$'),
    draft_id         UUID        NOT NULL,
    version          INTEGER     NOT NULL,
    -- The use of the platform signature (SigningUse.id), for example editorial.comprendre.
    use_id           TEXT        NOT NULL CHECK (use_id ~ '^[a-z][a-z0-9._-]{1,63}$'),
    -- The key of the use when the event was built (currentKey), also the author of the event.
    pubkey           TEXT        NOT NULL CHECK (pubkey ~ '^[0-9a-f]{64}$'),
    event_created_at BIGINT      NOT NULL CHECK (event_created_at > 0),
    -- The unsigned event as submitted, {pubkey, created_at, kind, tags, content}: rebuilt identically on reload.
    unsigned_event   JSONB       NOT NULL CHECK (jsonb_typeof(unsigned_event) = 'object'),
    -- The answer of submitDraft: the draft id on the service (the event id) and its approval page.
    signer_draft_id  TEXT        CHECK (signer_draft_id IS NULL OR signer_draft_id ~ '^[0-9a-f]{64}$'),
    approval_url     TEXT        CHECK (approval_url IS NULL OR length(approval_url) <= 500),
    -- submitted: waiting for the approvals or the signature; signed: signed, not yet published (kept to
    -- publish it on the next reload); published; refused (refusal_code); expired; withdrawn.
    state            TEXT        NOT NULL CHECK (state IN ('submitted', 'signed', 'published', 'refused', 'expired', 'withdrawn')),
    refusal_code     TEXT        CHECK (refusal_code IS NULL OR refusal_code ~ '^[a-z0-9_]{1,64}$'),
    -- The ticket of the signature request (PlatformSigner.request), while it is pending.
    ticket           TEXT        CHECK (ticket IS NULL OR length(ticket) <= 200),
    -- The event signed by the service, exactly as it is published.
    signed_event     JSONB       CHECK (signed_event IS NULL OR jsonb_typeof(signed_event) = 'object'),
    submitted_at     TIMESTAMPTZ NOT NULL,
    expires_at       TIMESTAMPTZ NOT NULL,
    published_at     TIMESTAMPTZ,
    CHECK ((state = 'refused') = (refusal_code IS NOT NULL)),
    CHECK (state NOT IN ('signed', 'published') OR signed_event IS NOT NULL),
    FOREIGN KEY (draft_id, version) REFERENCES editorial_draft_version (draft_id, version) ON DELETE CASCADE
);

-- At most one living submission per draft (waiting or signed).
CREATE UNIQUE INDEX editorial_signing_open_idx ON editorial_signing (draft_id) WHERE state IN ('submitted', 'signed');
CREATE INDEX editorial_signing_draft_idx ON editorial_signing (draft_id, submitted_at DESC);

-- The withdrawal of an approved version (back to draft): its reason, for the writers, never in the journal.
ALTER TABLE editorial_draft ADD COLUMN withdraw_reason TEXT CHECK (withdraw_reason IS NULL OR length(withdraw_reason) BETWEEN 1 AND 2000);
ALTER TABLE editorial_draft ADD COLUMN withdrawn_by UUID REFERENCES team_account (id);
ALTER TABLE editorial_draft ADD COLUMN withdrawn_at TIMESTAMPTZ;
ALTER TABLE editorial_draft ADD CONSTRAINT editorial_draft_withdraw_check
    CHECK ((withdraw_reason IS NULL) = (withdrawn_by IS NULL) AND (withdraw_reason IS NULL) = (withdrawn_at IS NULL));
