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
    salt          BYTEA       NOT NULL CHECK (octet_length(salt) = 16)
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
