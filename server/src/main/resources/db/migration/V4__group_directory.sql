-- The directory of the rooms (NIP-29 group descriptions, kind 39000), which any account may create
-- since D-028: one row per stored description, with its name, territory and description in the
-- normalized form used to refuse duplicates (one name per territory) and to search the rooms
-- (GroupNames: accents and case removed, punctuation turned into single spaces).
--
-- Like event_tags, rows are written by the application with the event, in the same transaction,
-- and disappear with it; the table holds nothing that is not already in the signed description
-- (no member, no client address). Descriptions stored before this migration are indexed by the
-- application at start-up, since the normalization is done in Kotlin.

CREATE TABLE group_directory (
    event_id      TEXT   PRIMARY KEY REFERENCES events (id) ON DELETE CASCADE,
    -- The `d` tag of the description: the room id.
    group_id      TEXT   NOT NULL CHECK (group_id ~ '^[a-z0-9._~-]{1,64}$'),
    -- created_at of the description: when two keys described the same id, the earliest one counts.
    created_at    BIGINT NOT NULL CHECK (created_at >= 0),
    name_key      TEXT   NOT NULL,
    territory_key TEXT   NOT NULL,
    about_key     TEXT   NOT NULL
);

-- The description that counts for an id (earliest, then lowest event id).
CREATE INDEX group_directory_group_idx ON group_directory (group_id, created_at, event_id);

-- Duplicate check: same name on the same territory; also the territory filter of the search.
CREATE INDEX group_directory_territory_name_idx ON group_directory (territory_key, name_key);

-- The listing, by name then id.
CREATE INDEX group_directory_name_idx ON group_directory (name_key COLLATE "C", group_id);
