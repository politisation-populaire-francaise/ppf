-- "Déclarer mon compte compromis" (S-146, D-134, docs/integration/compte-compromis.md).
-- Provisional number (Communauté branch, after V18 account types), renumbered at the entry into main.
--
-- Public keys and event ids only: the commitments (kind 1776) and the notices (kind 1777) are public
-- signed events, stored in `events` and served to everyone; these tables only say which ones count.

-- 1. The first commitment of each account to its recovery key: the only one that counts. A later
--    commitment of the same key (a thief naming their own key) is refused.
CREATE TABLE recovery_commitment (
    pubkey          TEXT        PRIMARY KEY CHECK (pubkey ~ '^[0-9a-f]{64}$'),
    recovery_pubkey TEXT        NOT NULL CHECK (recovery_pubkey ~ '^[0-9a-f]{64}$' AND recovery_pubkey <> pubkey),
    event_id        TEXT        NOT NULL UNIQUE CHECK (event_id ~ '^[0-9a-f]{64}$'),
    received_at     TIMESTAMPTZ NOT NULL
);

-- 2. The accounts declared compromised by a notice signed with their recovery key: irreversible.
--    Every later event of the key is refused. A key succeeds one other key at most (no double vote, D-134).
CREATE TABLE compromised_account (
    -- No foreign key to the commitments: a notice by the team (D-134, point 3, later) needs none.
    pubkey      TEXT        PRIMARY KEY CHECK (pubkey ~ '^[0-9a-f]{64}$'),
    notice_id   TEXT        NOT NULL UNIQUE CHECK (notice_id ~ '^[0-9a-f]{64}$'),
    -- "Compromised since", seconds since 1970, at most 30 days before the notice.
    since       BIGINT      NOT NULL CHECK (since >= 0),
    successor   TEXT        UNIQUE CHECK (successor ~ '^[0-9a-f]{64}$' AND successor <> pubkey),
    received_at TIMESTAMPTZ NOT NULL
);

-- 3. The rooms left without administrator when their last one was declared compromised: the queue
--    groups.orphans of the back-office (docs/integration/salons-reprise.md). Public identifiers only;
--    the decision of the team is set once (who decided, and why, is in the audit journal).
CREATE TABLE group_orphan (
    group_id           TEXT        PRIMARY KEY,
    compromised_pubkey TEXT        NOT NULL CHECK (compromised_pubkey ~ '^[0-9a-f]{64}$'),
    notice_id          TEXT        NOT NULL CHECK (notice_id ~ '^[0-9a-f]{64}$'),
    orphaned_at        TIMESTAMPTZ NOT NULL,
    decision           TEXT        CHECK (decision IN ('leave', 'admin')),
    decided_at         TIMESTAMPTZ,
    CHECK ((decision IS NULL) = (decided_at IS NULL))
);

CREATE INDEX group_orphan_pending ON group_orphan (orphaned_at) WHERE decided_at IS NULL;
