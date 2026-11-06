-- The Agir space on the server (contract docs/integration/agir-contrat.md, sections 2 to 5).
--
-- 1. held_content: the contents held for review by a person before publication (mobilizations
--    proposed by accounts that are neither editorial keys nor verified organisations, their calls,
--    every call for witnesses). An approved content is copied into events, exactly as its author
--    signed it; this row is only the review file.
--
--    Nothing about the request is stored: no client address, no header, no device information.
--    Retention: a decided content (approved or rejected) is deleted by the application 30 days after
--    the decision (PPF_HELD_RETENTION_DAYS); a pending one stays until the team decides.
--
-- 2. agenda_entry: the index of the published mobilizations (kind 31923, NIP-52) by date, territory,
--    type, status and organisers, for GET /api/v1/agenda and its iCalendar feed. Like event_tags and
--    group_directory, rows are written by the application with the event, in the same transaction,
--    and disappear with it (ON DELETE CASCADE): a new version of a mobilization replaces the row of
--    the old one. The table holds nothing that is not already in the signed event.

CREATE TABLE held_content (
    -- Id of the signed event (NIP-01).
    id              TEXT        PRIMARY KEY CHECK (id ~ '^[0-9a-f]{64}$'),
    -- Pseudonymous public key of the author, as in events.pubkey.
    pubkey          TEXT        NOT NULL CHECK (pubkey ~ '^[0-9a-f]{64}$'),
    -- Only addressable contents are held (31923 mobilizations, 30023 calls).
    kind            INTEGER     NOT NULL CHECK (kind BETWEEN 30000 AND 39999),
    -- The `d` tag: with the kind and the pubkey, the stable address of the content.
    identifier      TEXT        NOT NULL CHECK (length(identifier) BETWEEN 1 AND 128),
    -- kind:pubkey:d, the replacement key of EventReplacement (one pending version per address).
    address         TEXT        NOT NULL,
    -- created_at of the event, to know which version of an address is the newest.
    created_at      BIGINT      NOT NULL CHECK (created_at >= 0),
    -- The signed event, as received (JSON).
    event           TEXT        NOT NULL,
    -- The tags useful to sort the queue without parsing every event (title, type, territory, start),
    -- also in the event.
    tags            JSONB       NOT NULL,
    status          TEXT        NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
    received_at     TIMESTAMPTZ NOT NULL,
    decided_at      TIMESTAMPTZ,
    -- Reason given by the team when it rejects a content, shown to the author.
    decision_reason TEXT        CHECK (decision_reason IS NULL OR length(decision_reason) <= 1000),
    CHECK ((status = 'pending') = (decided_at IS NULL))
);

-- One pending version per address: a new version replaces the pending one.
CREATE UNIQUE INDEX held_content_pending_address_idx ON held_content (address) WHERE status = 'pending';

-- The count of pending contents per key (ceiling per key).
CREATE INDEX held_content_pending_pubkey_idx ON held_content (pubkey) WHERE status = 'pending';

-- The oldest pending contents first (review order).
CREATE INDEX held_content_pending_received_idx ON held_content (received_at, id) WHERE status = 'pending';

-- Deletion of the decided contents by decision date.
CREATE INDEX held_content_decided_at_idx ON held_content (decided_at) WHERE status <> 'pending';

CREATE TABLE agenda_entry (
    event_id      TEXT    PRIMARY KEY REFERENCES events (id) ON DELETE CASCADE,
    -- 31923:pubkey:d, the stable address (also the UID of the iCalendar feed).
    address       TEXT    NOT NULL,
    -- `start` and `end` of the event, seconds since 1970; no end means an event without duration.
    starts_at     BIGINT  NOT NULL CHECK (starts_at >= 0),
    ends_at       BIGINT  CHECK (ends_at IS NULL OR ends_at > starts_at),
    -- `ppf-territory` without accents, case nor punctuation (as the rooms, GroupNames).
    territory_key TEXT    NOT NULL,
    -- `ppf-event-type` (`autre` when unknown) and `ppf-event-status` (`prevu` when absent or unknown).
    event_type    TEXT    NOT NULL,
    status        TEXT    NOT NULL,
    -- The author and the keys named organisers (["p", key, "", "organizer"]), all public in the event.
    organizers    TEXT[]  NOT NULL
);

-- The agenda by date: events that overlap a period, sorted by start.
CREATE INDEX agenda_entry_starts_idx ON agenda_entry (starts_at, event_id);
CREATE INDEX agenda_entry_ends_idx ON agenda_entry ((COALESCE(ends_at, starts_at)));

-- Filters by territory and by organiser.
CREATE INDEX agenda_entry_territory_idx ON agenda_entry (territory_key, starts_at);
CREATE INDEX agenda_entry_organizers_idx ON agenda_entry USING GIN (organizers);
