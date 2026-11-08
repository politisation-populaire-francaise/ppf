-- Contact, claims and case follow-up (M30: S-102 to S-111; docs/integration/contact-reclamations.md, section 6; D-140).
-- Provisional number: renumbered to the next free version when the branch enters main.
--
-- The server never reads a case: the texts travel encrypted end to end (NIP-17 gift wraps) for the inbox
-- of the team or for the key of the case. It keeps the envelopes, unreadable, and the metadata of the
-- follow-up only: nature, status, dates, deadline, priority, assigned member. Never a client address, an
-- e-mail address, a phone number, a device identifier nor the account of the person: when the person links
-- their account, only the team sees it, inside the encrypted text.
--
-- Retention (docs/REGISTRE_DONNEES.md; point d'attention n° 20, S-109), applied every night by the server:
-- - tickets and contacts: deleted (case and envelopes) 3 years after closing;
-- - claims: anonymised 5 years after closing (envelopes, access_hash and case_pubkey deleted; nature, dates
--   and durations kept for the statistics), never deleted;
-- - nothing is deleted nor anonymised under an active legal hold;
-- - a case awaiting the person without news for 60 days is closed.

CREATE TABLE contact_case (
    -- PPF-XXXXX-XXXXX, 10 random symbols of Crockford's base 32 drawn by the device.
    number                TEXT        PRIMARY KEY CHECK (number ~ '^PPF-[0-9A-HJKMNP-TV-Z]{5}-[0-9A-HJKMNP-TV-Z]{5}$'),
    type                  TEXT        NOT NULL CHECK (type IN ('ticket', 'contact', 'claim')),
    subtype               TEXT        CHECK (subtype IN ('bug', 'suggestion', 'report')),
    category              TEXT        CHECK (category IN ('citizen', 'association', 'union', 'journalist', 'elected', 'institution', 'company', 'other')),
    motif                 TEXT        CHECK (motif IN ('moderation_decision', 'data_rights', 'right_of_reply', 'funds', 'team_member', 'other')),
    status                TEXT        NOT NULL CHECK (status IN ('received', 'in_progress', 'answered', 'awaiting_you', 'closed')),
    priority              TEXT        NOT NULL DEFAULT 'normal' CHECK (priority IN ('normal', 'high')),
    -- A member of the team (S-107); team accounts are never deleted, only marked removed.
    assigned_to           UUID        REFERENCES team_account (id),
    received_at           TIMESTAMPTZ NOT NULL,
    deadline_at           TIMESTAMPTZ,
    deadline_kind         TEXT        CHECK (deadline_kind IN ('legal', 'goal')),
    -- The extension of a request on one's data (RGPD, article 12, paragraph 3), with its reason.
    extended_at           TIMESTAMPTZ,
    extension_reason      TEXT        CHECK (extension_reason IS NULL OR length(extension_reason) BETWEEN 1 AND 1000),
    -- The date of the contested moderation decision, given by the device (six months to contest it).
    decision_at           TIMESTAMPTZ,
    -- The member who took the contested decision, filled by the team: they never handle the claim.
    decision_author       UUID        REFERENCES team_account (id),
    -- The member a claim about a team member concerns, filled by the team: that member never sees the case.
    concerns              UUID        REFERENCES team_account (id),
    closed_at             TIMESTAMPTZ,
    -- Legal hold (S-109): reason and review date (one year at most), set by two people.
    legal_hold_reason     TEXT        CHECK (legal_hold_reason IS NULL OR length(legal_hold_reason) BETWEEN 1 AND 1000),
    legal_hold_review_at  TIMESTAMPTZ,
    -- When the alert of the review date was posted on the dashboard (once per hold).
    legal_hold_alerted_at TIMESTAMPTZ,
    anonymized_at         TIMESTAMPTZ,
    -- SHA-256 of the access token derived from the secret code (the code and the token are never kept).
    access_hash           TEXT        CHECK (access_hash ~ '^[0-9a-f]{64}$'),
    -- The public key of the case (x only), recipient of the replies of the team.
    case_pubkey           TEXT        CHECK (case_pubkey ~ '^[0-9a-f]{64}$'),
    -- The last message of the person or change of status (the automatic closing counts from it).
    last_activity_at      TIMESTAMPTZ NOT NULL,
    CHECK ((type = 'ticket') = (subtype IS NOT NULL)),
    CHECK ((type = 'contact') = (category IS NOT NULL)),
    CHECK ((type = 'claim') = (motif IS NOT NULL)),
    CHECK ((deadline_at IS NULL) = (deadline_kind IS NULL)),
    CHECK ((status = 'closed') = (closed_at IS NOT NULL)),
    -- An anonymised claim keeps the date of its extension, not its reason (a text of the team).
    CHECK (extension_reason IS NULL OR extended_at IS NOT NULL),
    CHECK (extended_at IS NULL OR extension_reason IS NOT NULL OR anonymized_at IS NOT NULL),
    CHECK (extended_at IS NULL OR motif = 'data_rights'),
    CHECK (decision_at IS NULL OR motif = 'moderation_decision'),
    CHECK (decision_author IS NULL OR motif = 'moderation_decision'),
    CHECK (concerns IS NULL OR motif = 'team_member'),
    CHECK ((legal_hold_reason IS NULL) = (legal_hold_review_at IS NULL)),
    CHECK (legal_hold_alerted_at IS NULL OR legal_hold_reason IS NOT NULL),
    CHECK ((anonymized_at IS NULL) = (access_hash IS NOT NULL AND case_pubkey IS NOT NULL)),
    CHECK (anonymized_at IS NULL OR (type = 'claim' AND status = 'closed'))
);

-- The inbox of the team: by deadline, then by reception.
CREATE INDEX contact_case_inbox_idx ON contact_case (deadline_at NULLS LAST, received_at) WHERE status <> 'closed';
CREATE INDEX contact_case_closed_idx ON contact_case (closed_at) WHERE status = 'closed' AND anonymized_at IS NULL;

-- The envelopes of a case (NIP-59 gift wraps, kind 1059): `in` for the inbox of the team, `out` for the key
-- of the case. Never modified (trigger, and the server's role may not UPDATE them); deleted with their case
-- or when a claim is anonymised.
CREATE TABLE contact_envelope (
    id           TEXT        PRIMARY KEY CHECK (id ~ '^[0-9a-f]{64}$'),
    -- The order of reception, for `GET /api/v1/cases/{number}?after=<id>`.
    seq          BIGINT      GENERATED ALWAYS AS IDENTITY UNIQUE,
    case_number  TEXT        NOT NULL REFERENCES contact_case (number) ON DELETE CASCADE,
    direction    TEXT        NOT NULL CHECK (direction IN ('in', 'out')),
    -- The JSON of the gift wrap, 96 KiB at most.
    event        TEXT        NOT NULL CHECK (octet_length(event) <= 98304),
    received_at  TIMESTAMPTZ NOT NULL,
    -- The member of the team who wrote a reply (S-108); never shown to the person (replies are signed « l'équipe PPF »).
    team_author  UUID        REFERENCES team_account (id),
    CHECK ((direction = 'out') = (team_author IS NOT NULL))
);

CREATE INDEX contact_envelope_case_idx ON contact_envelope (case_number, seq);

CREATE FUNCTION contact_envelope_frozen() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'An envelope of a case is never modified.';
END
$$;

CREATE TRIGGER contact_envelope_no_update BEFORE UPDATE ON contact_envelope
    FOR EACH ROW EXECUTE FUNCTION contact_envelope_frozen();

-- A legal hold, or its release, waiting for a second member holding contact.legal_hold (7 days at most):
-- the two-person rule of the back-office (S-61), kept by the section because a hold carries a review date.
CREATE TABLE contact_legal_hold_request (
    case_number   TEXT        PRIMARY KEY REFERENCES contact_case (number) ON DELETE CASCADE,
    action        TEXT        NOT NULL CHECK (action IN ('hold', 'release')),
    reason        TEXT        NOT NULL CHECK (length(reason) BETWEEN 1 AND 1000),
    review_at     TIMESTAMPTZ,
    requested_by  UUID        NOT NULL REFERENCES team_account (id),
    requested_at  TIMESTAMPTZ NOT NULL,
    CHECK ((action = 'hold') = (review_at IS NOT NULL))
);
