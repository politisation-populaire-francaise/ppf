-- "One person, one vote" (D-110, D-112, docs/integration/une-personne-une-voix.md): anonymous votes.
--
-- The server never links a ballot to an account. It knows each account's level of voter, and
-- separately the anonymous ballots, which carry a level and a token but no account. Nothing about
-- requests is stored: no client address, no header, no device information (CLAUDE.md, section 7).
-- R1: no sequential identifier on ballots, no time finer than the hour on ballots, no time finer
-- than the day on token delivery.

-- 1. vote_account: one row per account that registered to vote (pseudonymous public key).
CREATE TABLE vote_account (
    pubkey           TEXT    PRIMARY KEY CHECK (pubkey ~ '^[0-9a-f]{64}$'),
    -- Current level (D-112).
    level            TEXT    NOT NULL CHECK (level IN ('non-verifie', 'verifie', 'elu')),
    -- Monday from which `level` counts (a change takes effect the next Monday; never the day).
    level_week       DATE    NOT NULL,
    -- The level before the last change, for the ballot boxes opened before `level_week`.
    previous_level   TEXT    NOT NULL CHECK (previous_level IN ('non-verifie', 'verifie', 'elu')),
    -- Monday of the week of registration.
    registered_week  DATE    NOT NULL,
    -- Cursor: tokens were delivered for every ballot box numbered up to this one (no list per box).
    issued_up_to     BIGINT  NOT NULL DEFAULT 0 CHECK (issued_up_to >= 0),
    -- The last delivery, kept one day so that a lost answer can be asked again without a second token:
    -- SHA-256 of the request body, the answer (evaluated elements and proofs, which reveal nothing:
    -- the blinding is perfect), its day.
    last_request     BYTEA,
    last_response    TEXT,
    last_response_day DATE
);

CREATE INDEX vote_account_level ON vote_account (level);

-- 2. urne: the ballot boxes. Public: everything here is published (GET /api/v1/urnes).
CREATE TABLE urne (
    -- Order of opening, public; the cursor of token delivery follows it.
    number        BIGINT      PRIMARY KEY CHECK (number > 0),
    -- Stable identifier, part of the derivation of the keys (no U+001F).
    id            TEXT        NOT NULL UNIQUE CHECK (length(id) BETWEEN 1 AND 400),
    -- Address of what is voted on.
    subject       TEXT        NOT NULL CHECK (length(subject) BETWEEN 1 AND 400),
    -- Allowed choices, JSON array of strings.
    choices       JSONB       NOT NULL,
    -- Version of the seed of its keys (one file per version in PPF_VOTE_SEEDS_DIR).
    seed_version  INTEGER     NOT NULL CHECK (seed_version > 0),
    opens_at      TIMESTAMPTZ NOT NULL,
    -- NULL: no natural end (a demand open to votes).
    closes_at     TIMESTAMPTZ,
    -- Monday of the week of opening: the level of each account is taken at this week (D-110, 6.4).
    opened_week   DATE        NOT NULL,
    live_results  BOOLEAN     NOT NULL,
    closed        BOOLEAN     NOT NULL DEFAULT false,
    -- 16 random bytes drawn at the opening, part of the derivation of the keys: the keys of a box are
    -- its own even if its identifier came back or a seed was shared by mistake. Never published.
    salt          BYTEA       NOT NULL CHECK (octet_length(salt) = 16),
    -- The closing statement signed by the platform (decider.urn_closure, JSON of the signed event), kept here
    -- first so that it is never lost between its signature and its publication; then published as it is.
    closure_statement TEXT,
    closure_published BOOLEAN NOT NULL DEFAULT false,
    -- Why the signing service refused the closing statement for good (urn_already_closed: signed before and
    -- lost here, an alert for the team), null otherwise.
    closure_refused   TEXT CHECK (length(closure_refused) <= 64)
);

-- 3. urne_issued: how many tokens each ballot box gave to each level (public journal, R2).
CREATE TABLE urne_issued (
    urne_number  BIGINT  NOT NULL REFERENCES urne (number) ON DELETE CASCADE,
    level        TEXT    NOT NULL CHECK (level IN ('non-verifie', 'verifie', 'elu')),
    issued       BIGINT  NOT NULL DEFAULT 0 CHECK (issued >= 0),
    PRIMARY KEY (urne_number, level)
);

-- 4. ballot: the anonymous ballots, indexed by their secret identifier `y` (never by an order of arrival).
--    Published after the closing, sorted by `y` (D-112, Q4).
CREATE TABLE ballot (
    urne_number   BIGINT      NOT NULL REFERENCES urne (number) ON DELETE CASCADE,
    -- Secret identifier chosen by the device (32 bytes): the same y with the same token replaces the ballot.
    y             BYTEA       NOT NULL CHECK (octet_length(y) = 32),
    -- VOPRF output (64 bytes) proving that a token of this level was issued for this ballot box.
    token         BYTEA       NOT NULL CHECK (octet_length(token) = 64),
    level         TEXT        NOT NULL CHECK (level IN ('non-verifie', 'verifie', 'elu')),
    choice        TEXT        NOT NULL CHECK (length(choice) BETWEEN 1 AND 100),
    -- Rounded down to the hour (R1).
    received_hour TIMESTAMPTZ NOT NULL,
    replacements  INTEGER     NOT NULL DEFAULT 0 CHECK (replacements >= 0),
    PRIMARY KEY (urne_number, y)
);

-- 5. public_vote: the votes of the certified elected officials, public by nature (owner's decision of
--    7 November 2026, D-115): the event signed with the official's own key, the latest one of each
--    official per box. Never an anonymous ballot, never added to the citizens' vote.
CREATE TABLE public_vote (
    urne_number BIGINT  NOT NULL REFERENCES urne (number) ON DELETE CASCADE,
    pubkey      TEXT    NOT NULL CHECK (pubkey ~ '^[0-9a-f]{64}$'),
    choice      TEXT    NOT NULL CHECK (length(choice) BETWEEN 1 AND 100),
    created_at  BIGINT  NOT NULL,
    event       JSONB   NOT NULL,
    PRIMARY KEY (urne_number, pubkey)
);

-- 6. In-person validation (D-112, une-personne-une-voix.md 4.3; decision of the Directeur technique of
--    8 November 2026, option B): a seized database must not link an account to the session that
--    validated it. A validation waits in vote_validation_pending until the next Monday 00:00 UTC (the
--    level takes effect that Monday anyway); one transaction then moves every pending row, in the order
--    of the keys, to vote_account, and keeps only the sealed links. No serial, no timestamp, no session
--    in clear: the link "account, session, week" is sealed in NIP-44 for the offline audit key.
CREATE TABLE vote_validation_pending (
    pubkey  TEXT  PRIMARY KEY CHECK (pubkey ~ '^[0-9a-f]{64}$'),
    -- {"ephemeral_pubkey", "payload"} (AuditSealer): only the audit key reads it.
    sealed  JSONB NOT NULL
);

-- The sealed links of the validations applied, by Monday of effect; kept one year (a session found
-- fraudulent is undone by the owner with the audit key), then deleted by the Monday batch.
CREATE TABLE vote_validation (
    week    DATE  NOT NULL,
    sealed  JSONB NOT NULL
);

CREATE INDEX vote_validation_week ON vote_validation (week);

-- The Mondays whose batch ran (at most one per Monday).
CREATE TABLE vote_validation_batch (
    week    DATE  PRIMARY KEY
);

-- 7. Certification of elected officials (DE-23, D-112): public by nature (an elected official answers as
--    such and votes publicly, D-115). Only the public reference of the mandate in the official directory
--    (ElusLookup, D-047), never an identity document, a name typed by the team or a photo.
CREATE TABLE elected_certification (
    pubkey          TEXT  PRIMARY KEY CHECK (pubkey ~ '^[0-9a-f]{64}$'),
    -- Stable identifier of the mandate in the directory served, and the two public codes that find it there
    -- (type of mandate, code of the territory): the name is read from the directory, never stored here.
    mandate         TEXT  NOT NULL CHECK (mandate ~ '^rne1:[0-9a-f]{32}$'),
    mandate_type    TEXT  NOT NULL CHECK (length(mandate_type) BETWEEN 1 AND 40),
    territory       TEXT  NOT NULL CHECK (length(territory) BETWEEN 1 AND 20),
    status          TEXT  NOT NULL CHECK (status IN ('pending', 'certified', 'refused', 'withdrawn', 'ended')),
    -- Monday of the request and of the last decision (never the day).
    requested_week  DATE  NOT NULL,
    decided_week    DATE,
    -- Shown to the account when refused or withdrawn.
    reason          TEXT  CHECK (length(reason) <= 1000)
);

CREATE INDEX elected_certification_status ON elected_certification (status);
-- One certified account per mandate.
CREATE UNIQUE INDEX elected_certification_mandate ON elected_certification (mandate) WHERE status = 'certified';

-- 8. The review of the validation sessions by the team (queue decider.validation_sessions, design 8): a
--    session with an abnormal pace leaves the queue once reviewed. Never who was validated: undoing the
--    validations of a session needs the sealed links, read offline with the audit key.
CREATE TABLE vote_session_review (
    session_id     UUID  PRIMARY KEY,
    outcome        TEXT  NOT NULL CHECK (outcome IN ('normal', 'to_undo')),
    reviewed_week  DATE  NOT NULL
);

-- 9. Contributions of citizens to a deliberation (DE-25, docs/integration/deliberation.md, section 2):
--    propositions and amendments held for the review of a person before publication, like the demands.
--    Nothing about the request; the day of reception only; decided ones deleted after 30 days (longer than
--    a phase, so that the limits per account hold during it).
CREATE TABLE deliberation_submission (
    id            TEXT  PRIMARY KEY CHECK (id ~ '^[0-9a-f]{64}$'),
    pubkey        TEXT  NOT NULL CHECK (pubkey ~ '^[0-9a-f]{64}$'),
    identifier    TEXT  NOT NULL CHECK (length(identifier) BETWEEN 1 AND 128),
    type          TEXT  NOT NULL CHECK (type IN ('proposition', 'amendement')),
    -- Address of the deliberation, and of what is amended (the deliberation itself for a proposition).
    deliberation  TEXT  NOT NULL CHECK (length(deliberation) BETWEEN 1 AND 400),
    parent        TEXT  NOT NULL CHECK (length(parent) BETWEEN 1 AND 400),
    created_at    BIGINT NOT NULL CHECK (created_at >= 0),
    -- The signed event, as received (JSON).
    event         TEXT  NOT NULL,
    status        TEXT  NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
    received_day  DATE  NOT NULL,
    decided_day   DATE,
    -- Shown to the author when refused.
    reason        TEXT  CHECK (length(reason) <= 1000),
    CHECK ((status = 'pending') = (decided_day IS NULL))
);

-- One pending version per address; the counts per account and deliberation.
CREATE UNIQUE INDEX deliberation_submission_pending ON deliberation_submission (pubkey, identifier) WHERE status = 'pending';
CREATE INDEX deliberation_submission_author ON deliberation_submission (deliberation, pubkey, type);
