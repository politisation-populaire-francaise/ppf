-- The team back-office base (contract docs/integration/back-office.md, sections 4 to 8, 11; D-120 onward).
--
-- Accounts of the team are pseudonymous: no e-mail address, no client address, no browser or device
-- information anywhere in these tables. A person signs in with a passkey or a FIDO2 security key
-- (WebAuthn); the server keeps only the public key of the authenticator. Tokens (invitations, sessions)
-- are random 256-bit values given to the person once; only their SHA-256 is stored.
--
-- Retention (docs/REGISTRE_DONNEES.md):
-- - team_account: until the member is removed; afterwards only the identifier, the pseudonym, the dates
--   and the status stay (they are referenced by the journal and by the grants), the Nostr key is erased;
--   a suspended account (emergency cut) keeps its keys and roles, unusable until it is reinstated;
-- - team_credential: until deleted by the member, by an administrator for a suspended account, or by a
--   removal;
-- - team_invitation: 48 hours at most, deleted when used or expired;
-- - team_session: 8 hours at most, deleted when closed or expired;
-- - team_pending_approval: 7 days pending at most, deleted 30 days after the decision;
-- - team_audit_log: without limit (to be reviewed by a lawyer), never changed nor deleted;
-- - team_validation_session: counters only, kept until Décider sets their retention;
-- - team_dashboard_alert: deleted 30 days after it was dismissed.

CREATE TABLE team_account (
    id            UUID        PRIMARY KEY,
    -- Chosen by the person or the inviter; never an e-mail address (the application refuses '@').
    pseudonym     TEXT        NOT NULL CHECK (length(pseudonym) BETWEEN 2 AND 40 AND position('@' IN pseudonym) = 0),
    -- Optional Nostr public key (hex), to link the account to the identity that signs editorial contents.
    nostr_pubkey  TEXT        CHECK (nostr_pubkey ~ '^[0-9a-f]{64}$'),
    -- WebAuthn user handle: 32 random bytes, never derived from anything about the person.
    user_handle   BYTEA       NOT NULL UNIQUE CHECK (length(user_handle) = 32),
    status        TEXT        NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'suspended', 'removed')),
    created_at    TIMESTAMPTZ NOT NULL,
    -- The member who sent the invitation; NULL for the accounts created by the bootstrap command.
    invited_by    UUID        REFERENCES team_account (id),
    -- When the account was cut (suspended) or removed.
    closed_at     TIMESTAMPTZ,
    CHECK ((status = 'active') = (closed_at IS NULL))
);

-- Two active members never share a pseudonym (case ignored); a removed member's pseudonym may be reused.
CREATE UNIQUE INDEX team_account_pseudonym_idx ON team_account (lower(pseudonym)) WHERE status = 'active';

CREATE TABLE team_credential (
    id               UUID        PRIMARY KEY,
    account_id       UUID        NOT NULL REFERENCES team_account (id) ON DELETE CASCADE,
    -- The credential id chosen by the authenticator (WebAuthn allows up to 1023 bytes).
    credential_id    BYTEA       NOT NULL UNIQUE CHECK (length(credential_id) BETWEEN 1 AND 1023),
    -- Attested credential data as WebAuthn4J serializes it: credential id and COSE public key; the
    -- authenticator model identifier (AAGUID) is replaced by zeros before storage.
    attested_data    BYTEA       NOT NULL,
    sign_count       BIGINT      NOT NULL DEFAULT 0 CHECK (sign_count >= 0),
    uv_initialized   BOOLEAN     NOT NULL,
    backup_eligible  BOOLEAN     NOT NULL,
    backup_state     BOOLEAN     NOT NULL,
    -- The name the person gave to the key ("Téléphone", "Clé de secours").
    name             TEXT        NOT NULL CHECK (length(name) BETWEEN 1 AND 64),
    created_at       TIMESTAMPTZ NOT NULL,
    last_used_at     TIMESTAMPTZ
);

CREATE INDEX team_credential_account_idx ON team_credential (account_id);

CREATE TABLE team_invitation (
    id                  UUID        PRIMARY KEY,
    -- SHA-256 (hex) of the one-time token of the link; the token itself is shown once and never stored.
    token_hash          TEXT        NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    pseudonym           TEXT        NOT NULL CHECK (length(pseudonym) BETWEEN 2 AND 40 AND position('@' IN pseudonym) = 0),
    -- Roles proposed by the inviter; each one becomes a request for a second approval once the account exists.
    proposed_roles      TEXT[]      NOT NULL,
    -- The designating verified organisation (hex key), required when the role `validator` is proposed.
    organisation_pubkey TEXT        CHECK (organisation_pubkey ~ '^[0-9a-f]{64}$'),
    -- Optional end of the proposed roles (temporary access).
    roles_end_at        TIMESTAMPTZ,
    -- Created by the bootstrap command (first super administrators): the role is granted at acceptance.
    bootstrap           BOOLEAN     NOT NULL DEFAULT FALSE,
    created_by          UUID        REFERENCES team_account (id),
    created_at          TIMESTAMPTZ NOT NULL,
    expires_at          TIMESTAMPTZ NOT NULL,
    CHECK (bootstrap = (created_by IS NULL)),
    CHECK (expires_at > created_at)
);

CREATE INDEX team_invitation_expires_idx ON team_invitation (expires_at);

CREATE TABLE team_session (
    -- SHA-256 (hex) of the session token held by the cookie __Host-ppf-equipe.
    token_hash    TEXT        PRIMARY KEY CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    account_id    UUID        NOT NULL REFERENCES team_account (id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ NOT NULL,
    -- Last request: a session unused for 30 minutes is closed.
    last_seen_at  TIMESTAMPTZ NOT NULL,
    -- created_at + 8 hours at most.
    expires_at    TIMESTAMPTZ NOT NULL,
    -- Last verification of a passkey (sign-in or confirmation before a sensitive action).
    verified_at   TIMESTAMPTZ NOT NULL,
    CHECK (expires_at > created_at)
);

CREATE INDEX team_session_account_idx ON team_session (account_id);
CREATE INDEX team_session_expires_idx ON team_session (expires_at);

CREATE TABLE team_role_grant (
    id                  UUID        PRIMARY KEY,
    account_id          UUID        NOT NULL REFERENCES team_account (id),
    role                TEXT        NOT NULL CHECK (role IN ('super_admin', 'admin', 'moderator', 'editor', 'organisation_verifier', 'validator', 'elected_certifier')),
    -- Reserved for permissions limited to a space or a territory; always 'all' in version 1.
    scope               TEXT        NOT NULL DEFAULT 'all' CHECK (scope = 'all'),
    -- The verified organisation (hex key) that designated the validator (owner decision D-112); validators only.
    organisation_pubkey TEXT        CHECK (organisation_pubkey ~ '^[0-9a-f]{64}$'),
    granted_at          TIMESTAMPTZ NOT NULL,
    -- Optional end (temporary access): the grant expires by itself, the periodic task records it.
    ends_at             TIMESTAMPTZ,
    -- The member who asked and the one who approved (two-person rule); both NULL for the bootstrap.
    granted_by          UUID        REFERENCES team_account (id),
    approved_by         UUID        REFERENCES team_account (id),
    status              TEXT        NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'expired', 'revoked', 'suspended')),
    closed_at           TIMESTAMPTZ,
    CHECK ((role = 'validator') = (organisation_pubkey IS NOT NULL)),
    CHECK ((status = 'active') = (closed_at IS NULL)),
    CHECK (ends_at IS NULL OR ends_at > granted_at),
    CHECK (approved_by IS NULL OR granted_by IS NULL OR approved_by <> granted_by)
);

-- One active grant of a role per account.
CREATE UNIQUE INDEX team_role_grant_active_idx ON team_role_grant (account_id, role) WHERE status = 'active';
CREATE INDEX team_role_grant_ends_idx ON team_role_grant (ends_at) WHERE status = 'active' AND ends_at IS NOT NULL;
CREATE INDEX team_role_grant_organisation_idx ON team_role_grant (organisation_pubkey) WHERE status = 'active' AND organisation_pubkey IS NOT NULL;

CREATE TABLE team_pending_approval (
    id            UUID        PRIMARY KEY,
    -- Stable identifier of the action waiting for a second person (for example 'team.grant_role').
    action        TEXT        NOT NULL CHECK (action ~ '^[a-z0-9_]+(\.[a-z0-9_]+)+$'),
    -- The permission the second person must hold (the same one as the requester).
    permission    TEXT        NOT NULL,
    target_type   TEXT        NOT NULL,
    target_id     TEXT        NOT NULL,
    -- What is to be done, written by the application (JSON object of strings).
    payload       TEXT        NOT NULL,
    reason        TEXT        CHECK (reason IS NULL OR length(reason) <= 1000),
    requested_by  UUID        NOT NULL REFERENCES team_account (id),
    requested_at  TIMESTAMPTZ NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL,
    status        TEXT        NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'refused', 'expired', 'cancelled', 'failed')),
    decided_by    UUID        REFERENCES team_account (id),
    decided_at    TIMESTAMPTZ,
    CHECK (expires_at > requested_at),
    -- The requester never approves their own request.
    CHECK (decided_by IS NULL OR decided_by <> requested_by OR status = 'cancelled'),
    CHECK ((status = 'pending') = (decided_at IS NULL))
);

CREATE INDEX team_pending_approval_pending_idx ON team_pending_approval (requested_at) WHERE status = 'pending';
CREATE INDEX team_pending_approval_decided_idx ON team_pending_approval (decided_at) WHERE status <> 'pending';

CREATE TABLE team_audit_log (
    -- 1, 2, 3... in the order of the chain, assigned by the application under a lock.
    seq           BIGINT      PRIMARY KEY CHECK (seq >= 1),
    recorded_at   TIMESTAMPTZ NOT NULL,
    -- The member who acted, or NULL for the server itself (expiry, bootstrap) and for an unknown person
    -- (failed sign-in with an unknown key).
    actor_id      UUID,
    -- The pseudonym at the time of the action, 'système' or 'inconnu'.
    actor_label   TEXT        NOT NULL,
    action        TEXT        NOT NULL CHECK (action ~ '^[a-z0-9_]+(\.[a-z0-9_]+)+$'),
    target_type   TEXT,
    target_id     TEXT,
    reason        TEXT,
    -- A JSON object of strings, exactly as hashed (kept as text so that the hash can be computed again).
    -- Never the full text of a citizen's content, never a client address.
    details       TEXT        NOT NULL,
    previous_hash TEXT        NOT NULL CHECK (previous_hash ~ '^[0-9a-f]{64}$'),
    hash          TEXT        NOT NULL UNIQUE CHECK (hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX team_audit_log_recorded_idx ON team_audit_log (recorded_at);
CREATE INDEX team_audit_log_actor_idx ON team_audit_log (actor_id, seq);
CREATE INDEX team_audit_log_action_idx ON team_audit_log (action, seq);
CREATE INDEX team_audit_log_target_idx ON team_audit_log (target_type, target_id, seq);

-- Append only: nobody changes or erases a line, not even an administrator of the application. The hash
-- chain reveals a line removed or changed by someone who bypasses this trigger with direct access.
CREATE FUNCTION team_audit_log_refuse_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'team_audit_log is append-only: % refused', TG_OP;
END;
$$;

CREATE TRIGGER team_audit_log_no_update_delete BEFORE UPDATE OR DELETE ON team_audit_log
    FOR EACH ROW EXECUTE FUNCTION team_audit_log_refuse_change();

CREATE TRIGGER team_audit_log_no_truncate BEFORE TRUNCATE ON team_audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION team_audit_log_refuse_change();

CREATE TABLE team_validation_session (
    id              UUID        PRIMARY KEY,
    account_id      UUID        NOT NULL REFERENCES team_account (id),
    -- The validator grant the session was opened under (it carries the designating organisation).
    grant_id        UUID        NOT NULL REFERENCES team_role_grant (id),
    -- ISO week of the opening, Paris time (for example 2026-W45).
    week            TEXT        NOT NULL CHECK (week ~ '^[0-9]{4}-W[0-9]{2}$'),
    -- The Nostr public key generated by the validator's application for this session only (NIP-98).
    session_pubkey  TEXT        NOT NULL UNIQUE CHECK (session_pubkey ~ '^[0-9a-f]{64}$'),
    max_validations INTEGER     NOT NULL CHECK (max_validations BETWEEN 1 AND 40),
    -- Counter only: never the place nor the event, never who was validated.
    validations     INTEGER     NOT NULL DEFAULT 0 CHECK (validations >= 0 AND validations <= max_validations),
    opened_at       TIMESTAMPTZ NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    state           TEXT        NOT NULL DEFAULT 'open' CHECK (state IN ('open', 'closed', 'revoked', 'expired')),
    closed_at       TIMESTAMPTZ,
    CHECK (expires_at > opened_at),
    CHECK ((state = 'open') = (closed_at IS NULL))
);

-- One open session per validator at a time.
CREATE UNIQUE INDEX team_validation_session_open_idx ON team_validation_session (account_id) WHERE state = 'open';
CREATE INDEX team_validation_session_grant_idx ON team_validation_session (grant_id) WHERE state = 'open';

CREATE TABLE team_dashboard_alert (
    id            UUID        PRIMARY KEY,
    -- The space that posted it (for example 'voir').
    source        TEXT        NOT NULL CHECK (source ~ '^[a-z0-9_]+(\.[a-z0-9_]+)*$'),
    title         TEXT        NOT NULL CHECK (length(title) BETWEEN 1 AND 200),
    -- Importance level 1 to 5 (S-115).
    level         INTEGER     NOT NULL CHECK (level BETWEEN 1 AND 5),
    -- Optional link inside the back-office: a path starting with '/' and a letter or digit, no backslash.
    link          TEXT        CHECK (link IS NULL OR (link ~ '^/[A-Za-z0-9]' AND strpos(link, chr(92)) = 0 AND length(link) <= 200)),
    -- Who sees it and may dismiss it.
    permission    TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    dismissed_by  UUID        REFERENCES team_account (id),
    dismissed_at  TIMESTAMPTZ,
    CHECK ((dismissed_by IS NULL) = (dismissed_at IS NULL))
);

CREATE INDEX team_dashboard_alert_open_idx ON team_dashboard_alert (created_at) WHERE dismissed_at IS NULL;
