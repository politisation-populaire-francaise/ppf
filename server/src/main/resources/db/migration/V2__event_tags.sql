-- Tags of every event, one row per (name, value) pair, so that events can be looked up by tag:
-- the recipient (`p`) of an encrypted envelope, the group (`h`) of a chat message, the stable
-- identifier (`d`) of a content. Rows are written by the application with the event and disappear
-- with it. The table holds nothing that is not already in events.tags; no client address.
--
-- Only tags fit for an index are kept: a name of 1 to 64 characters and a value of at most
-- 1024 bytes (the values indexed by Nostr relays are keys, ids and short identifiers).

CREATE TABLE event_tags (
    event_id TEXT NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    name     TEXT NOT NULL CHECK (length(name) BETWEEN 1 AND 64),
    value    TEXT NOT NULL CHECK (octet_length(value) <= 1024),
    PRIMARY KEY (event_id, name, value)
);

-- Lookup by tag, then by event id to join back to events.
CREATE INDEX event_tags_name_value_idx ON event_tags (name, value, event_id);

-- Encrypted envelopes (kind 1059) are deleted by reception date: give that deletion an index.
CREATE INDEX events_wrap_received_at_idx ON events (received_at) WHERE kind = 1059;

-- Index the events already stored. The CASE keeps jsonb_array_length away from non-array
-- elements (SQL does not short-circuit a WHERE), although the application only writes arrays.
INSERT INTO event_tags (event_id, name, value)
SELECT DISTINCT e.id, t ->> 0, t ->> 1
FROM events e, jsonb_array_elements(e.tags) t
WHERE CASE WHEN jsonb_typeof(t) = 'array' THEN jsonb_array_length(t) ELSE 0 END >= 2
  AND jsonb_typeof(t -> 0) = 'string'
  AND jsonb_typeof(t -> 1) = 'string'
  AND length(t ->> 0) BETWEEN 1 AND 64
  AND octet_length(t ->> 1) <= 1024;
