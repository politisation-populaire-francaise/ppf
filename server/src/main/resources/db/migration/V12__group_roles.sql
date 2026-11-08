-- Roles, moderation and creation guardrails of the public rooms (NIP-29, D-131 and D-132, contract
-- docs/integration/salons-roles.md). Number provisional: the next free one when the branch enters main.
--
-- The state of each room is computed by the application from the moderation events it accepts, one
-- at a time per room, in the order of reception (put-user 9000, remove-user 9001, edit-metadata 9002,
-- delete-event 9005). These tables only hold that state; every row comes from a signed, public event
-- and holds nothing that is not already in it (public keys, reasons): no member list, no client
-- address. The events themselves stay in `events` and are served to everyone (the room's journal).

-- 1. The roles in force: the creator of a room (author of its description 39000) is its first
--    administrator; administrators name other administrators and moderators.
CREATE TABLE group_role (
    group_id   TEXT        NOT NULL CHECK (group_id ~ '^[a-z0-9._~-]{1,64}$'),
    pubkey     TEXT        NOT NULL CHECK (pubkey ~ '^[0-9a-f]{64}$'),
    role       TEXT        NOT NULL CHECK (role IN ('admin', 'moderator')),
    -- The key that named this role (the creator names itself).
    granted_by TEXT        NOT NULL CHECK (granted_by ~ '^[0-9a-f]{64}$'),
    granted_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (group_id, pubkey)
);

-- 2. The accounts excluded from a room: their messages (kind 9) are refused there.
CREATE TABLE group_ban (
    group_id  TEXT        NOT NULL CHECK (group_id ~ '^[a-z0-9._~-]{1,64}$'),
    pubkey    TEXT        NOT NULL CHECK (pubkey ~ '^[0-9a-f]{64}$'),
    banned_by TEXT        NOT NULL CHECK (banned_by ~ '^[0-9a-f]{64}$'),
    reason    TEXT        NOT NULL CHECK (length(reason) BETWEEN 1 AND 1000),
    banned_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (group_id, pubkey)
);

-- 3. The messages removed from a room: no longer served (the event 9005 stays public, with its reason).
CREATE TABLE group_removed_event (
    -- A message belongs to one room: its id alone identifies the row.
    event_id   TEXT        PRIMARY KEY CHECK (event_id ~ '^[0-9a-f]{64}$'),
    group_id   TEXT        NOT NULL CHECK (group_id ~ '^[a-z0-9._~-]{1,64}$'),
    removed_by TEXT        NOT NULL CHECK (removed_by ~ '^[0-9a-f]{64}$'),
    reason     TEXT        NOT NULL CHECK (length(reason) BETWEEN 1 AND 1000),
    removed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX group_removed_event_group_idx ON group_removed_event (group_id);

-- 4. The latest edit of a room's name and presentation (9002) by an administrator, served over the
--    description; it disappears with its event, and when the creator publishes a new description.
CREATE TABLE group_edit (
    group_id  TEXT        PRIMARY KEY CHECK (group_id ~ '^[a-z0-9._~-]{1,64}$'),
    event_id  TEXT        NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    name      TEXT        CHECK (name IS NULL OR length(name) <= 100),
    about     TEXT,
    edited_by TEXT        NOT NULL CHECK (edited_by ~ '^[0-9a-f]{64}$'),
    edited_at TIMESTAMPTZ NOT NULL
);

-- 5. The room creations held for a review by the team (queue `groups.creations` of the back-office):
--    a name close to a verified organisation's, or to a very active room elsewhere. An approved
--    creation is copied into events exactly as signed; this row is only the review file. Nothing
--    about the request is stored. A decided creation is deleted 30 days after the decision.
CREATE TABLE group_held_creation (
    -- Id of the signed description (39000).
    id          TEXT        PRIMARY KEY CHECK (id ~ '^[0-9a-f]{64}$'),
    group_id    TEXT        NOT NULL CHECK (group_id ~ '^[a-z0-9._~-]{1,64}$'),
    pubkey      TEXT        NOT NULL CHECK (pubkey ~ '^[0-9a-f]{64}$'),
    -- The signed event, as received (JSON).
    event       TEXT        NOT NULL,
    -- Why it is held: `organisation` or `active_room`, and the names it resembles (shown to the team).
    cause       TEXT        NOT NULL CHECK (cause IN ('organisation', 'active_room')),
    resembles   TEXT        NOT NULL CHECK (length(resembles) <= 2000),
    status      TEXT        NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
    received_at TIMESTAMPTZ NOT NULL,
    decided_at  TIMESTAMPTZ,
    -- Reason given by the team when it refuses a creation, shown to its author.
    reason      TEXT        CHECK (reason IS NULL OR length(reason) <= 1000),
    CHECK ((status = 'pending') = (decided_at IS NULL))
);

CREATE INDEX group_held_creation_pending_idx ON group_held_creation (received_at) WHERE status = 'pending';
CREATE INDEX group_held_creation_pubkey_idx ON group_held_creation (pubkey, received_at);

-- The rooms created before this migration: their creator becomes their first administrator.
INSERT INTO group_role (group_id, pubkey, role, granted_by, granted_at)
SELECT d.group_id, e.pubkey, 'admin', e.pubkey, e.received_at
FROM group_directory d JOIN events e ON e.id = d.event_id
WHERE NOT EXISTS (
    SELECT 1 FROM group_directory o
    WHERE o.group_id = d.group_id AND (o.created_at, o.event_id) < (d.created_at, d.event_id)
)
ON CONFLICT DO NOTHING;
